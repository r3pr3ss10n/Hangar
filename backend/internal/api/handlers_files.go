package api

import (
	"context"
	"fmt"
	"io"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/google/uuid"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
)

// countingWriter tallies the bytes streamed through it and the time spent blocked
// IN the downstream Write (i.e. pushing bytes to the browser). Comparing that
// blocked time to the total download time isolates the bottleneck: if writes
// block most of the time, the backend->browser leg is the cap; if not, the
// Telegram->backend fetch is.
type countingWriter struct {
	w        io.Writer
	n        int64
	writeDur time.Duration
}

func (c *countingWriter) Write(p []byte) (int, error) {
	t := time.Now()
	n, err := c.w.Write(p)
	c.writeDur += time.Since(t)
	c.n += int64(n)
	return n, err
}

// handleFileMeta returns a file's metadata.
func (s *Server) handleFileMeta(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	file, err := s.files.GetAccessibleFile(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"file": newFileView(file)})
}

// handleThumb serves a file's stored JPEG thumbnail, or 404 if it has none. The
// thumbnail is small and lives in Postgres, so it is served directly (no
// Telegram round-trip) and is privately cacheable. The file's sha256 doubles as
// a strong ETag so browsers can revalidate cheaply.
func (s *Server) handleThumb(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	file, err := s.files.GetAccessibleFile(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.serveThumb(w, r, file)
}

// serveThumb writes a file's stored JPEG thumbnail (or 404 when it has none),
// using the file's sha256 as a strong ETag for cheap revalidation. Caller has
// already loaded the file and enforced whatever access check applies.
func (s *Server) serveThumb(w http.ResponseWriter, r *http.Request, file dbsqlc.File) {
	if len(file.ThumbRef) == 0 {
		s.writeErr(w, http.StatusNotFound, "no thumbnail")
		return
	}

	etag := `"` + file.Sha256 + `"`
	w.Header().Set("ETag", etag)
	w.Header().Set("Cache-Control", "private, max-age=86400")
	if r.Header.Get("If-None-Match") == etag {
		w.WriteHeader(http.StatusNotModified)
		return
	}

	w.Header().Set("Content-Type", "image/jpeg")
	w.Header().Set("Content-Length", strconv.Itoa(len(file.ThumbRef)))
	if r.Method == http.MethodHead {
		w.WriteHeader(http.StatusOK)
		return
	}
	if _, err := w.Write(file.ThumbRef); err != nil {
		s.logger.Error("api: thumb write failed", "error", err, "file_id", file.ID)
	}
}

// patchFileBody is the {name?,folder_id?} body for rename/move.
type patchFileBody struct {
	Name       *string    `json:"name"`
	FolderID   *uuid.UUID `json:"folder_id"`
	moveFolder bool
}

// handlePatchFile renames and/or moves a file. A 204 is returned on success.
func (s *Server) handlePatchFile(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}

	body, ok := s.decodePatchFile(w, r)
	if !ok {
		return
	}
	if body.Name == nil && !body.moveFolder {
		s.writeErr(w, http.StatusBadRequest, "nothing to update")
		return
	}

	if body.Name != nil {
		name := strings.TrimSpace(*body.Name)
		if name == "" {
			s.writeErr(w, http.StatusBadRequest, "name cannot be empty")
			return
		}
		if err := s.files.RenameFile(r.Context(), user.ID, id, name); err != nil {
			s.writeServiceErr(w, err)
			return
		}
	}
	if body.moveFolder {
		if err := s.files.MoveFile(r.Context(), user.ID, id, body.FolderID); err != nil {
			s.writeServiceErr(w, err)
			return
		}
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleDeleteFile deletes a file end to end: it removes the underlying message
// (the actual bytes) from the Telegram storage channel FIRST, then drops the
// metadata row. If the Telegram deletion fails, the row is left intact so the
// caller can retry — this guarantees a "delete" never leaves orphaned bytes
// stranded in the channel.
func (s *Server) handleDeleteFile(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}

	// Load the file (ownership enforced) to obtain its Telegram message id.
	file, err := s.files.GetFile(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}

	// Remove the bytes from Telegram before deleting the metadata row.
	if err := s.telegram.Delete(r.Context(), file.TgMessageID); err != nil {
		s.logger.Error("api: telegram delete failed", "error", err, "file_id", file.ID)
		s.writeServiceErr(w, err)
		return
	}

	if err := s.files.DeleteFile(r.Context(), user.ID, id); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleDownload streams a file's bytes, honouring an HTTP Range request. It
// advertises Accept-Ranges, replies 206 with Content-Range for a partial range,
// and sets Content-Disposition with the filename. Bytes are pulled from Telegram
// at the requested offset/limit; a RefreshFunc persists any refreshed download
// reference back onto the row via UpdateFileReference.
func (s *Server) handleDownload(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	file, err := s.files.GetAccessibleFile(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.streamFile(w, r, file)
}

// streamFile streams a file's bytes, honouring an HTTP Range request. It
// advertises Accept-Ranges, replies 206 with Content-Range for a partial range,
// and sets Content-Disposition with the filename. Bytes are pulled from Telegram
// at the requested offset/limit; a RefreshFunc persists any refreshed download
// reference back onto the row. Caller has already loaded the file and enforced
// whatever access check applies (owner session, or a valid share token).
func (s *Server) streamFile(w http.ResponseWriter, r *http.Request, file dbsqlc.File) {
	size := file.Size
	hasRange := r.Header.Get("Range") != ""
	start, end, partial, ok := parseRange(r.Header.Get("Range"), size)
	if !ok {
		// Unsatisfiable range per RFC 7233: 416 with the current size.
		w.Header().Set("Content-Range", fmt.Sprintf("bytes */%d", size))
		s.writeErr(w, http.StatusRequestedRangeNotSatisfiable, "requested range not satisfiable")
		return
	}
	// Treat a present Range header that resolves to the whole extent as partial
	// too, so a client that asked for a range always gets a 206.
	partial = partial && hasRange
	length := end - start + 1

	w.Header().Set("Accept-Ranges", "bytes")
	w.Header().Set("Content-Type", contentTypeFor(file.Mime))
	w.Header().Set("Content-Disposition", contentDisposition(file.Name))
	w.Header().Set("Content-Length", strconv.FormatInt(length, 10))

	status := http.StatusOK
	if partial {
		w.Header().Set("Content-Range", fmt.Sprintf("bytes %d-%d/%d", start, end, size))
		status = http.StatusPartialContent
	}

	// A HEAD request wants headers only.
	if r.Method == http.MethodHead {
		w.WriteHeader(status)
		return
	}

	ref := telegram.FileRef{
		MessageID:     file.TgMessageID,
		DocumentID:    file.TgDocumentID,
		AccessHash:    file.TgAccessHash,
		FileReference: file.TgFileReference,
		DCID:          int(file.TgDcID),
	}
	refresh := s.refreshFuncFor(file.ID)

	w.WriteHeader(status)
	if length <= 0 {
		return
	}
	cw := &countingWriter{w: w}
	started := time.Now()
	err := s.telegram.Download(r.Context(), ref, start, length, cw, refresh)
	elapsed := time.Since(started)
	mbps := 0.0
	if secs := elapsed.Seconds(); secs > 0 {
		mbps = float64(cw.n) / secs / (1024 * 1024)
	}
	s.logger.Info("download finished",
		"file_id", file.ID, "dc", file.TgDcID, "ranged", partial,
		"bytes", cw.n, "requested", length,
		"duration", elapsed.Round(time.Millisecond).String(),
		"write_blocked", cw.writeDur.Round(time.Millisecond).String(),
		"MBps", fmt.Sprintf("%.2f", mbps))
	if err != nil {
		// Headers are already committed; the body is truncated. Log and stop —
		// we cannot change the status now.
		s.logger.Error("api: download stream failed", "error", err, "file_id", file.ID)
		return
	}
}

// refreshFuncFor returns a telegram.RefreshFunc bound to fileID that persists a
// refreshed download reference (after FILE_REFERENCE_EXPIRED) via
// UpdateFileReference.
func (s *Server) refreshFuncFor(fileID uuid.UUID) telegram.RefreshFunc {
	return func(ctx context.Context, ref telegram.FileRef) error {
		if err := s.queries.UpdateFileReference(ctx, dbsqlc.UpdateFileReferenceParams{
			ID:              fileID,
			TgFileReference: ref.FileReference,
			TgAccessHash:    ref.AccessHash,
			TgDcID:          int32(ref.DCID),
		}); err != nil {
			return fmt.Errorf("update file reference: %w", err)
		}
		return nil
	}
}

// parseRange parses a single-range HTTP Range header against a known total size.
// It returns the inclusive [start,end] byte offsets. With no header it returns
// the full extent [0,size-1]. ok=false signals an unsatisfiable range (caller
// should reply 416). Only the first range of a multi-range request is honoured;
// v1 does not implement multipart/byteranges.
func parseRange(header string, size int64) (start, end int64, partial, ok bool) {
	if header == "" {
		if size == 0 {
			return 0, -1, false, true
		}
		return 0, size - 1, false, true
	}
	const prefix = "bytes="
	if !strings.HasPrefix(header, prefix) {
		return 0, 0, false, false
	}
	spec := strings.TrimPrefix(header, prefix)
	// Honour only the first range.
	if i := strings.IndexByte(spec, ','); i >= 0 {
		spec = spec[:i]
	}
	spec = strings.TrimSpace(spec)
	dash := strings.IndexByte(spec, '-')
	if dash < 0 {
		return 0, 0, false, false
	}
	startStr := strings.TrimSpace(spec[:dash])
	endStr := strings.TrimSpace(spec[dash+1:])

	if startStr == "" {
		// Suffix range: bytes=-N → last N bytes.
		n, err := strconv.ParseInt(endStr, 10, 64)
		if err != nil || n <= 0 {
			return 0, 0, false, false
		}
		if n > size {
			n = size
		}
		return size - n, size - 1, true, true
	}

	start, err := strconv.ParseInt(startStr, 10, 64)
	if err != nil || start < 0 || start >= size {
		return 0, 0, false, false
	}
	if endStr == "" {
		return start, size - 1, true, true
	}
	end, err = strconv.ParseInt(endStr, 10, 64)
	if err != nil || end < start {
		return 0, 0, false, false
	}
	if end >= size {
		end = size - 1
	}
	return start, end, true, true
}

// contentTypeFor returns a safe Content-Type, defaulting to octet-stream.
func contentTypeFor(mime string) string {
	if strings.TrimSpace(mime) == "" {
		return "application/octet-stream"
	}
	return mime
}

// contentDisposition builds an attachment Content-Disposition with both a
// sanitized ASCII filename and a UTF-8 filename* for non-ASCII names.
func contentDisposition(name string) string {
	ascii := sanitizeASCIIFilename(name)
	return fmt.Sprintf("attachment; filename=%q; filename*=UTF-8''%s", ascii, urlEncode(name))
}

// sanitizeASCIIFilename strips characters unsafe for a quoted filename token,
// replacing them with underscores so the header stays well-formed.
func sanitizeASCIIFilename(name string) string {
	var b strings.Builder
	for _, r := range name {
		if r < 0x20 || r == 0x7f || r == '"' || r == '\\' || r > 0x7e {
			b.WriteByte('_')
			continue
		}
		b.WriteRune(r)
	}
	out := b.String()
	if out == "" {
		return "download"
	}
	return out
}

// urlEncode percent-encodes a string for use in a filename* value (RFC 5987),
// leaving the RFC's unreserved attr-char set intact.
func urlEncode(s string) string {
	const upperhex = "0123456789ABCDEF"
	var b strings.Builder
	for i := 0; i < len(s); i++ {
		c := s[i]
		if (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z') || (c >= '0' && c <= '9') ||
			c == '-' || c == '.' || c == '_' || c == '~' {
			b.WriteByte(c)
			continue
		}
		b.WriteByte('%')
		b.WriteByte(upperhex[c>>4])
		b.WriteByte(upperhex[c&0x0f])
	}
	return b.String()
}
