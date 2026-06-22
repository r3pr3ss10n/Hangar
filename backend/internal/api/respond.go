package api

import (
	"encoding/json"
	"errors"
	"net/http"
	"time"

	"github.com/google/uuid"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
)

// userView is the public projection of a user. It deliberately omits
// password_hash so the credential digest never crosses the wire.
type userView struct {
	ID        uuid.UUID `json:"id"`
	Username  string    `json:"username"`
	Role      string    `json:"role"`
	CreatedAt time.Time `json:"created_at"`
}

// newUserView projects a stored user into its public view.
func newUserView(u dbsqlc.User) userView {
	return userView{
		ID:        u.ID,
		Username:  u.Username,
		Role:      u.Role,
		CreatedAt: u.CreatedAt,
	}
}

// newUserViews projects a slice of stored users into public views.
func newUserViews(us []dbsqlc.User) []userView {
	out := make([]userView, 0, len(us))
	for _, u := range us {
		out = append(out, newUserView(u))
	}
	return out
}

// fileView is the public projection of a file. It exposes the user-facing
// metadata only — the Telegram storage handle (message/document/access_hash/
// file_reference) stays server-side.
type fileView struct {
	ID        uuid.UUID  `json:"id"`
	OwnerID   uuid.UUID  `json:"owner_id"`
	FolderID  *uuid.UUID `json:"folder_id"`
	Name      string     `json:"name"`
	Size      int64      `json:"size"`
	Mime      string     `json:"mime"`
	SHA256    string     `json:"sha256"`
	HasThumb  bool       `json:"has_thumb"`
	CreatedAt time.Time  `json:"created_at"`
}

// newFileView projects a stored file into its public view. The thumbnail bytes
// stay server-side; only a has_thumb flag crosses the wire so the UI knows
// whether to request GET /api/files/{id}/thumb.
func newFileView(f dbsqlc.File) fileView {
	return fileView{
		ID:        f.ID,
		OwnerID:   f.OwnerID,
		FolderID:  f.FolderID,
		Name:      f.Name,
		Size:      f.Size,
		Mime:      f.Mime,
		SHA256:    f.Sha256,
		HasThumb:  len(f.ThumbRef) > 0,
		CreatedAt: f.CreatedAt,
	}
}

// newFileViews projects a slice of stored files into public views.
func newFileViews(fs []dbsqlc.File) []fileView {
	out := make([]fileView, 0, len(fs))
	for _, f := range fs {
		out = append(out, newFileView(f))
	}
	return out
}

// folderView is the public projection of a folder.
type folderView struct {
	ID        uuid.UUID  `json:"id"`
	OwnerID   uuid.UUID  `json:"owner_id"`
	ParentID  *uuid.UUID `json:"parent_id"`
	Name      string     `json:"name"`
	CreatedAt time.Time  `json:"created_at"`
}

// newFolderView projects a stored folder into its public view.
func newFolderView(f dbsqlc.Folder) folderView {
	return folderView{
		ID:        f.ID,
		OwnerID:   f.OwnerID,
		ParentID:  f.ParentID,
		Name:      f.Name,
		CreatedAt: f.CreatedAt,
	}
}

// newFolderViews projects a slice of stored folders into public views.
func newFolderViews(fs []dbsqlc.Folder) []folderView {
	out := make([]folderView, 0, len(fs))
	for _, f := range fs {
		out = append(out, newFolderView(f))
	}
	return out
}

// writeJSON serializes v as JSON with the given status. A nil v writes only the
// status line and headers (used for bodies callers build inline).
func (s *Server) writeJSON(w http.ResponseWriter, status int, v any) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	if v == nil {
		return
	}
	if err := json.NewEncoder(w).Encode(v); err != nil {
		s.logger.Error("api: encode response", "error", err)
	}
}

// writeErr writes a JSON error body {"error": msg} with the given status.
func (s *Server) writeErr(w http.ResponseWriter, status int, msg string) {
	s.writeJSON(w, status, map[string]string{"error": msg})
}

// writeServiceErr maps a service-layer error to its HTTP status per the
// contract and writes the JSON error body. It is the single place handlers
// funnel errors that originate below the api layer, so the wire mapping stays
// consistent.
func (s *Server) writeServiceErr(w http.ResponseWriter, err error) {
	switch {
	case errors.Is(err, auth.ErrInvalidCredentials), errors.Is(err, auth.ErrUnauthorized):
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
	case errors.Is(err, files.ErrForbidden):
		s.writeErr(w, http.StatusForbidden, "forbidden")
	case errors.Is(err, files.ErrNotFound), errors.Is(err, telegram.ErrLinkNotFound):
		s.writeErr(w, http.StatusNotFound, "not found")
	case errors.Is(err, telegram.ErrFileTooLarge):
		s.writeErr(w, http.StatusRequestEntityTooLarge, "file exceeds account limit")
	case errors.Is(err, telegram.ErrNotLinked):
		s.writeErr(w, http.StatusConflict, "telegram account not linked")
	case errors.Is(err, telegram.ErrCodeInvalid):
		s.writeErr(w, http.StatusBadRequest, "invalid login code")
	case errors.Is(err, telegram.ErrPasswordInvalid):
		s.writeErr(w, http.StatusBadRequest, "invalid 2FA password")
	default:
		s.logger.Error("api: internal error", "error", err)
		s.writeErr(w, http.StatusInternalServerError, "internal server error")
	}
}

// decodeJSON reads and decodes a JSON request body into dst. It rejects
// unknown fields and an empty/absent body, returning false (after writing a 400)
// when the body cannot be decoded so callers can simply return.
func (s *Server) decodeJSON(w http.ResponseWriter, r *http.Request, dst any) bool {
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(dst); err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid request body")
		return false
	}
	return true
}
