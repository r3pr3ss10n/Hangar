package api

import (
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

// shareView is the owner-facing projection of a share link. The frontend turns
// token into a full URL against its own origin, so no host is embedded here.
type shareView struct {
	Token     string     `json:"token"`
	CreatedAt time.Time  `json:"created_at"`
	ExpiresAt *time.Time `json:"expires_at"`
}

func newShareView(s dbsqlc.FileShare) shareView {
	return shareView{Token: s.Token, CreatedAt: s.CreatedAt, ExpiresAt: s.ExpiresAt}
}

func newShareViews(ss []dbsqlc.FileShare) []shareView {
	out := make([]shareView, 0, len(ss))
	for _, s := range ss {
		out = append(out, newShareView(s))
	}
	return out
}

// sharedFileView is the public projection returned to anyone holding a valid
// share token: enough to render the landing page, with no Telegram handle or
// owner identity.
type sharedFileView struct {
	Name      string     `json:"name"`
	Size      int64      `json:"size"`
	Mime      string     `json:"mime"`
	HasThumb  bool       `json:"has_thumb"`
	CreatedAt time.Time  `json:"created_at"`
	ExpiresAt *time.Time `json:"expires_at"`
}

// handleListShares returns the share links for an owned file.
func (s *Server) handleListShares(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	shares, err := s.files.ListShares(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"shares": newShareViews(shares)})
}

// createShareBody is the {expires_in_seconds?} body. A nil value (absent or
// explicit null) creates a link that never expires; a positive value sets the
// lifetime in seconds from now.
type createShareBody struct {
	ExpiresInSeconds *int64 `json:"expires_in_seconds"`
}

// handleCreateShare mints a new share link for an owned file.
func (s *Server) handleCreateShare(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	var body createShareBody
	if !s.decodeJSON(w, r, &body) {
		return
	}

	var expiresAt *time.Time
	if body.ExpiresInSeconds != nil {
		if *body.ExpiresInSeconds <= 0 {
			s.writeErr(w, http.StatusBadRequest, "expires_in_seconds must be positive")
			return
		}
		t := time.Now().Add(time.Duration(*body.ExpiresInSeconds) * time.Second)
		expiresAt = &t
	}

	share, err := s.files.CreateShare(r.Context(), user.ID, id, expiresAt)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusCreated, newShareView(share))
}

// handleDeleteShare revokes a share link owned (by file ownership) by the caller.
func (s *Server) handleDeleteShare(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	token := chi.URLParam(r, "token")
	if token == "" {
		s.writeErr(w, http.StatusBadRequest, "missing token")
		return
	}
	if err := s.files.DeleteShare(r.Context(), user.ID, token); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleGetShare returns a shared file's public metadata. No authentication: the
// token is the capability.
func (s *Server) handleGetShare(w http.ResponseWriter, r *http.Request) {
	file, expiresAt, ok := s.lookupShare(w, r)
	if !ok {
		return
	}
	s.writeJSON(w, http.StatusOK, sharedFileView{
		Name:      file.Name,
		Size:      file.Size,
		Mime:      file.Mime,
		HasThumb:  len(file.ThumbRef) > 0,
		CreatedAt: file.CreatedAt,
		ExpiresAt: expiresAt,
	})
}

// handleShareDownload streams a shared file's bytes (ranged), unauthenticated.
func (s *Server) handleShareDownload(w http.ResponseWriter, r *http.Request) {
	file, _, ok := s.lookupShare(w, r)
	if !ok {
		return
	}
	s.streamFile(w, r, file)
}

// handleShareThumb serves a shared file's thumbnail, unauthenticated.
func (s *Server) handleShareThumb(w http.ResponseWriter, r *http.Request) {
	file, _, ok := s.lookupShare(w, r)
	if !ok {
		return
	}
	s.serveThumb(w, r, file)
}

// lookupShare resolves the {token} path param to its live, unexpired file and the
// link's expiry (nil = never). It writes the error response (404 for
// unknown/expired) and returns ok=false on failure so callers can simply return.
func (s *Server) lookupShare(w http.ResponseWriter, r *http.Request) (dbsqlc.File, *time.Time, bool) {
	token := chi.URLParam(r, "token")
	if token == "" {
		s.writeErr(w, http.StatusBadRequest, "missing token")
		return dbsqlc.File{}, nil, false
	}
	file, expiresAt, err := s.files.GetSharedFile(r.Context(), token)
	if err != nil {
		s.writeServiceErr(w, err)
		return dbsqlc.File{}, nil, false
	}
	return file, expiresAt, true
}
