package api

import (
	"bytes"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"io"
	"net/http"
	"strings"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
	"github.com/r3pr3ss10n/hangar/backend/internal/thumb"
)

// maxThumbSourceBytes caps how large an image we will buffer in memory during a
// streaming upload to generate a thumbnail. Larger images stream straight to
// Telegram with no thumbnail (the file still works; it just shows a type icon).
const maxThumbSourceBytes = 30 << 20 // 30 MiB

// handleUpload streams the request body straight into Telegram as a single
// document, then records the file row. It is the "must work" upload path: the
// bytes are never buffered to disk. Filename, mime and size travel in headers
// (X-Upload-Filename, Content-Type, Content-Length); a size over the account
// ceiling is rejected with 413 before any bytes are streamed.
func (s *Server) handleUpload(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}

	filename := strings.TrimSpace(r.Header.Get("X-Upload-Filename"))
	if filename == "" {
		s.writeErr(w, http.StatusBadRequest, "X-Upload-Filename header is required")
		return
	}
	mime := strings.TrimSpace(r.Header.Get("Content-Type"))
	if mime == "" {
		mime = "application/octet-stream"
	}

	size := r.ContentLength
	if size < 0 {
		s.writeErr(w, http.StatusBadRequest, "Content-Length header is required")
		return
	}
	// Reject oversized uploads up front so we never start streaming a doomed file.
	if size > s.telegram.MaxFileBytes() {
		s.writeErr(w, http.StatusRequestEntityTooLarge, "file exceeds account limit")
		return
	}

	folderID, ok := s.parseOptionalUUIDQuery(w, r, "folder_id")
	if !ok {
		return
	}

	// Validate the destination folder BEFORE streaming any bytes: an upload into
	// a missing or foreign folder would otherwise land in Telegram and then be
	// orphaned when CreateFile rejects it.
	if err := s.files.AssertFolderOwned(r.Context(), user.ID, folderID); err != nil {
		s.writeServiceErr(w, err)
		return
	}

	// Hash the bytes as they stream through to Telegram so the stored sha256 is
	// exact without a second pass.
	hasher := sha256.New()
	body := io.TeeReader(io.LimitReader(r.Body, size), hasher)

	// For thumbnailable images within the cap, tee a copy into memory while the
	// original streams to Telegram, so we can build a thumbnail after the upload
	// without a second download. Skipped when the user disabled previews, or for
	// oversized / non-image uploads.
	var thumbSrc *bytes.Buffer
	if user.GenerateThumbnails && thumb.Supported(mime) && size > 0 && size <= maxThumbSourceBytes {
		thumbSrc = bytes.NewBuffer(make([]byte, 0, size))
		body = io.TeeReader(body, thumbSrc)
	}

	stored, err := s.telegram.Upload(r.Context(), filename, mime, size, body)
	if err != nil {
		if errors.Is(err, telegram.ErrFileTooLarge) {
			s.writeErr(w, http.StatusRequestEntityTooLarge, "file exceeds account limit")
			return
		}
		if errors.Is(err, telegram.ErrNotLinked) {
			s.writeErr(w, http.StatusConflict, "telegram account not linked")
			return
		}
		s.writeServiceErr(w, err)
		return
	}

	file, err := s.files.CreateFile(r.Context(), files.NewFile{
		OwnerID:  user.ID,
		FolderID: folderID,
		Name:     filename,
		Mime:     mime,
		SHA256:   hex.EncodeToString(hasher.Sum(nil)),
		Size:     stored.Size,
		TG:       stored,
		ThumbRef: s.makeThumb(thumbBytes(thumbSrc), mime, filename),
	})
	if err != nil {
		// The bytes are already in Telegram; the metadata write failed. Roll the
		// upload back so we don't strand an orphaned message in the channel.
		// Best-effort and logged — the original error is what the client sees.
		if delErr := s.telegram.Delete(r.Context(), stored.MessageID); delErr != nil {
			s.logger.Error("api: failed to roll back orphaned upload",
				"message_id", stored.MessageID, "name", filename, "err", delErr)
		}
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusCreated, map[string]any{"file": newFileView(file)})
}

// makeThumb best-effort generates a JPEG thumbnail from source image bytes. No
// data (non-image or oversized upload) or any generation failure yields a nil
// thumbnail and is logged at debug level — a missing thumbnail never fails the
// upload.
func (s *Server) makeThumb(data []byte, mime, name string) []byte {
	if len(data) == 0 {
		return nil
	}
	out, err := thumb.Generate(data, mime)
	if err != nil {
		s.logger.Debug("api: thumbnail generation skipped", "error", err, "name", name, "mime", mime)
		return nil
	}
	return out
}

// thumbBytes returns the buffered bytes, or nil if no buffer was captured.
func thumbBytes(b *bytes.Buffer) []byte {
	if b == nil {
		return nil
	}
	return b.Bytes()
}
