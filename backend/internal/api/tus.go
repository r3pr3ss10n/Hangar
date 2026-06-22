package api

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/tus/tusd/v2/pkg/filestore"
	tushandler "github.com/tus/tusd/v2/pkg/handler"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
	"github.com/r3pr3ss10n/hangar/backend/internal/thumb"
)

// tusBasePath is the absolute URL prefix the tus handler routes against; it must
// match the chi mount point below so tusd strips it correctly.
const tusBasePath = "/api/uploads/"

// tusMetaOwnerID is an internal metadata key stamped on create so the completion
// hook knows the owner without relying on the (soon-cancelled) request context.
const tusMetaOwnerID = "hangarOwnerId"

// tusMetaGenThumbs carries the uploader's "generate previews" preference captured
// at create time, so the completion hook can honour it without a DB lookup.
const tusMetaGenThumbs = "hangarGenThumbs"

// mountTus wires the resumable (tus) upload endpoint at /api/uploads/ behind
// RequireAuth and the per-user rate limiter. Chunks land in a filestore under
// cfg.DataDir/uploads; when an upload completes, tusPreFinish streams the temp
// file straight into Telegram, records the file row, returns the new id in the
// Hangar-File-Id response header, and deletes the temp file.
//
// Resumable upload is an enhancement over the streaming POST /api/files path. If
// the store cannot be initialised we degrade to a clear 503 there rather than
// refuse to boot.
func (s *Server) mountTus(r chi.Router) {
	h, err := s.newTusHandler()
	if err != nil {
		s.logger.Error("tus: disabled (init failed); clients should use POST /api/files", "err", err)
		r.Group(func(r chi.Router) {
			r.Use(s.auth.RequireAuth)
			r.Handle("/uploads", http.HandlerFunc(s.handleTusUnavailable))
			r.Handle("/uploads/*", http.HandlerFunc(s.handleTusUnavailable))
		})
		return
	}
	// tusd's routed handler routes on strings.Trim(r.URL.Path, "/"), expecting the
	// base path already stripped (create == empty path). Strip "/api/uploads" so
	// "/api/uploads/" -> "/" (create) and "/api/uploads/{id}" -> "/{id}" (resource);
	// tusd still builds Location headers from the configured BasePath.
	stripped := http.StripPrefix("/api/uploads", h)
	r.Group(func(r chi.Router) {
		r.Use(s.auth.RequireAuth)
		r.Use(s.uploadLim.middleware)
		r.Handle("/uploads", stripped)
		r.Handle("/uploads/*", stripped)
	})
}

// newTusHandler builds the tusd v2 handler over a local filestore, wiring the
// create/finish callbacks that enforce ownership and stream into Telegram.
func (s *Server) newTusHandler() (http.Handler, error) {
	dir := filepath.Join(s.cfg.DataDir, "uploads")
	if err := os.MkdirAll(dir, 0o755); err != nil {
		return nil, fmt.Errorf("create tus upload dir: %w", err)
	}
	store := filestore.New(dir)
	composer := tushandler.NewStoreComposer()
	store.UseIn(composer)

	return tushandler.NewHandler(tushandler.Config{
		BasePath:      tusBasePath,
		StoreComposer: composer,
		// Behind angie (TLS terminator) the backend sees plain http, so without
		// this tusd would hand clients an http:// upload Location and the browser
		// would block the PATCH/HEAD as mixed content on an https page. Honour
		// X-Forwarded-Proto/Host (angie sets X-Forwarded-Proto $scheme) so the
		// advertised URLs match the public https origin.
		RespectForwardedHeaders: true,
		// Logger intentionally unset: tusd v2 wants golang.org/x/exp/slog, not the
		// stdlib log/slog we use everywhere else, so we let it default internally.
		PreUploadCreateCallback: s.tusPreCreate,
		// Capture the concrete store so the finish hook can open the temp file.
		PreFinishResponseCallback: func(hook tushandler.HookEvent) (tushandler.HTTPResponse, error) {
			return s.tusPreFinish(hook, store)
		},
	})
}

// tusPreCreate authenticates the creator, enforces the per-file ceiling, requires
// a filename, and stamps the owner id into the upload metadata. Returning an
// error aborts creation with the error's HTTP status.
//
// Parallel uploads (tus concatenation) arrive as several partial creates followed
// by one final create. Partial uploads carry no user metadata (filename/folder)
// and only a slice of the total size, so file-level validation is skipped for
// them and runs on the final upload instead — by then tusd has summed the partial
// sizes into hook.Upload.Size, so the ceiling check is accurate. A plain upload
// (neither partial nor final) is validated exactly as before.
func (s *Server) tusPreCreate(hook tushandler.HookEvent) (tushandler.HTTPResponse, tushandler.FileInfoChanges, error) {
	var noResp tushandler.HTTPResponse
	var noChanges tushandler.FileInfoChanges

	user, ok := auth.UserFromContext(hook.Context)
	if !ok {
		return noResp, noChanges, tushandler.NewError("ERR_UNAUTHORIZED", "unauthorized", http.StatusUnauthorized)
	}

	// A partial upload is just a byte slice destined for concatenation: stamp the
	// owner (for ownership + later temp-file cleanup) and skip file-level checks.
	if hook.Upload.IsPartial {
		md := make(tushandler.MetaData, len(hook.Upload.MetaData)+1)
		for k, v := range hook.Upload.MetaData {
			md[k] = v
		}
		md[tusMetaOwnerID] = user.ID.String()
		return noResp, tushandler.FileInfoChanges{MetaData: md}, nil
	}

	if filename := strings.TrimSpace(hook.Upload.MetaData["filename"]); filename == "" {
		return noResp, noChanges, tushandler.NewError("ERR_FILENAME_REQUIRED", "filename metadata is required", http.StatusBadRequest)
	}
	if hook.Upload.Size > s.telegram.MaxFileBytes() {
		return noResp, noChanges, tushandler.NewError("ERR_MAX_SIZE_EXCEEDED", "file exceeds account limit", http.StatusRequestEntityTooLarge)
	}

	// Validate the destination folder up front (before any chunks are accepted)
	// so an upload into a missing or foreign folder is rejected at creation
	// rather than after the bytes have streamed to Telegram.
	folderID, err := parseFolderMeta(hook.Upload.MetaData["folder_id"])
	if err != nil {
		return noResp, noChanges, tushandler.NewError("ERR_BAD_FOLDER", "invalid folder_id metadata", http.StatusBadRequest)
	}
	if ferr := s.files.AssertFolderOwned(hook.Context, user.ID, folderID); ferr != nil {
		return noResp, noChanges, tusError(ferr)
	}

	// changes.MetaData REPLACES the stored set, so clone the originals first.
	md := make(tushandler.MetaData, len(hook.Upload.MetaData)+2)
	for k, v := range hook.Upload.MetaData {
		md[k] = v
	}
	md[tusMetaOwnerID] = user.ID.String()
	md[tusMetaGenThumbs] = strconv.FormatBool(user.GenerateThumbnails)
	return noResp, tushandler.FileInfoChanges{MetaData: md}, nil
}

// tusPreFinish runs once all bytes are received: it streams the finished temp
// file into Telegram, records the file row, and returns the new id header. The
// request stays open for the duration of the Telegram upload (synchronous by
// design for v1); hook.Context outlives the request briefly so this can complete.
func (s *Server) tusPreFinish(hook tushandler.HookEvent, store filestore.FileStore) (tushandler.HTTPResponse, error) {
	var noResp tushandler.HTTPResponse
	ctx := hook.Context
	info := hook.Upload

	// A partial upload completing is not a finished file — it is one slice awaiting
	// concatenation. The finish callback fires for every completed upload, so guard
	// against streaming a partial into Telegram. The final concat upload (assembled
	// from these partials) is the one that proceeds below.
	if info.IsPartial {
		return noResp, nil
	}

	ownerID, err := uuid.Parse(info.MetaData[tusMetaOwnerID])
	if err != nil {
		return noResp, tushandler.NewError("ERR_NO_OWNER", "upload is missing its owner", http.StatusInternalServerError)
	}
	filename := strings.TrimSpace(info.MetaData["filename"])
	mime := strings.TrimSpace(info.MetaData["filetype"])
	if mime == "" {
		mime = "application/octet-stream"
	}
	folderID, err := parseFolderMeta(info.MetaData["folder_id"])
	if err != nil {
		return noResp, tushandler.NewError("ERR_BAD_FOLDER", "invalid folder_id metadata", http.StatusBadRequest)
	}

	upload, err := store.GetUpload(ctx, info.ID)
	if err != nil {
		return noResp, fmt.Errorf("open finished upload: %w", err)
	}
	// Always remove the completed temp file once we're done with it, regardless
	// of which exit path we take below — otherwise a CreateFile/upload failure
	// would leave the full file stranded on disk. For a concat upload, also remove
	// the partial temp files it was assembled from.
	defer func() {
		if t, ok := upload.(tushandler.TerminatableUpload); ok {
			if termErr := t.Terminate(ctx); termErr != nil {
				s.logger.Warn("tus: failed to remove temp upload", "id", info.ID, "err", termErr)
			}
		}
		s.tusRemovePartials(ctx, store, info.PartialUploads)
	}()

	reader, err := upload.GetReader(ctx)
	if err != nil {
		return noResp, fmt.Errorf("read finished upload: %w", err)
	}
	defer reader.Close()

	// Hash the bytes as they stream to Telegram so the stored sha256 is exact.
	hasher := sha256.New()
	stored, err := s.telegram.Upload(ctx, filename, mime, info.Size, io.TeeReader(reader, hasher))
	if err != nil {
		return noResp, tusError(err)
	}

	// The completed bytes are still on disk here, so re-read a thumbnailable
	// image once more to build its thumbnail before the temp file is removed —
	// unless the uploader disabled previews (absent metadata defaults to on).
	var thumbRef []byte
	if genThumbs, perr := strconv.ParseBool(info.MetaData[tusMetaGenThumbs]); perr != nil || genThumbs {
		thumbRef = s.tusThumb(ctx, upload, mime, filename, info.Size)
	}

	file, err := s.files.CreateFile(ctx, files.NewFile{
		OwnerID:  ownerID,
		FolderID: folderID,
		Name:     filename,
		Mime:     mime,
		SHA256:   hex.EncodeToString(hasher.Sum(nil)),
		Size:     stored.Size,
		TG:       stored,
		ThumbRef: thumbRef,
	})
	if err != nil {
		// Bytes already in Telegram; metadata write failed. Roll back the upload
		// so we don't strand an orphaned message (the temp file is removed by the
		// deferred Terminate above).
		if delErr := s.telegram.Delete(ctx, stored.MessageID); delErr != nil {
			s.logger.Error("tus: failed to roll back orphaned upload",
				"message_id", stored.MessageID, "name", filename, "err", delErr)
		}
		return noResp, tusError(err)
	}

	return tushandler.HTTPResponse{
		Header: tushandler.HTTPHeader{"Hangar-File-Id": file.ID.String()},
	}, nil
}

// tusThumb best-effort builds a thumbnail from a finished tus upload by opening a
// fresh reader over the temp file. Non-images, oversized uploads, and read/decode
// failures yield a nil thumbnail without failing the upload.
func (s *Server) tusThumb(ctx context.Context, upload tushandler.Upload, mime, name string, size int64) []byte {
	if !thumb.Supported(mime) || size <= 0 || size > maxThumbSourceBytes {
		return nil
	}
	reader, err := upload.GetReader(ctx)
	if err != nil {
		s.logger.Debug("tus: reopen for thumbnail failed", "error", err, "name", name)
		return nil
	}
	defer reader.Close()
	data, err := io.ReadAll(io.LimitReader(reader, maxThumbSourceBytes))
	if err != nil {
		s.logger.Debug("tus: read for thumbnail failed", "error", err, "name", name)
		return nil
	}
	return s.makeThumb(data, mime, name)
}

// tusRemovePartials best-effort deletes the partial upload temp files that a
// concatenated final upload was assembled from. Their bytes already live in the
// final temp file (and thus in Telegram), so leaving them would strand disk
// space. ids are tus upload ids (from FileInfo.PartialUploads); a failure to
// remove one is logged, not fatal.
func (s *Server) tusRemovePartials(ctx context.Context, store filestore.FileStore, ids []string) {
	for _, id := range ids {
		if id == "" {
			continue
		}
		upload, err := store.GetUpload(ctx, id)
		if err != nil {
			s.logger.Warn("tus: failed to open partial upload for cleanup", "id", id, "err", err)
			continue
		}
		t, ok := upload.(tushandler.TerminatableUpload)
		if !ok {
			continue
		}
		if termErr := t.Terminate(ctx); termErr != nil {
			s.logger.Warn("tus: failed to remove partial upload", "id", id, "err", termErr)
		}
	}
}

// parseFolderMeta interprets the optional folder_id tus metadata value; empty or
// "root" means the root folder (nil).
func parseFolderMeta(raw string) (*uuid.UUID, error) {
	raw = strings.TrimSpace(raw)
	if raw == "" || raw == "root" {
		return nil, nil
	}
	id, err := uuid.Parse(raw)
	if err != nil {
		return nil, err
	}
	return &id, nil
}

// tusError maps service/telegram sentinels to tus errors carrying the right HTTP
// status; anything else surfaces as a 500.
func tusError(err error) error {
	switch {
	case errors.Is(err, telegram.ErrNotLinked):
		return tushandler.NewError("ERR_NOT_LINKED", "telegram account not linked", http.StatusConflict)
	case errors.Is(err, telegram.ErrFileTooLarge):
		return tushandler.NewError("ERR_MAX_SIZE_EXCEEDED", "file exceeds account limit", http.StatusRequestEntityTooLarge)
	case errors.Is(err, files.ErrForbidden):
		return tushandler.NewError("ERR_FORBIDDEN", "forbidden", http.StatusForbidden)
	case errors.Is(err, files.ErrNotFound):
		return tushandler.NewError("ERR_NOT_FOUND", "not found", http.StatusNotFound)
	default:
		return err
	}
}

// handleTusUnavailable is the degraded response when the tus store failed to
// initialise; clients should fall back to the streaming POST /api/files path.
func (s *Server) handleTusUnavailable(w http.ResponseWriter, _ *http.Request) {
	s.writeErr(w, http.StatusServiceUnavailable,
		"resumable (tus) uploads are unavailable; use POST /api/files")
}
