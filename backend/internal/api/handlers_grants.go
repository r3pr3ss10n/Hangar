package api

import (
	"net/http"
	"time"

	"github.com/google/uuid"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
)

// grantView is the owner-facing projection of one access grant.
type grantView struct {
	RecipientID       uuid.UUID `json:"recipient_id"`
	RecipientUsername string    `json:"recipient_username"`
	Permission        string    `json:"permission"`
	CreatedAt         time.Time `json:"created_at"`
}

func newGrantViews(gs []files.Grant) []grantView {
	out := make([]grantView, 0, len(gs))
	for _, g := range gs {
		out = append(out, grantView{
			RecipientID:       g.RecipientID,
			RecipientUsername: g.RecipientUsername,
			Permission:        g.Permission,
			CreatedAt:         g.CreatedAt,
		})
	}
	return out
}

// shareableUserView is the id+username projection used to populate the share
// picker. It exposes no role or timestamps.
type shareableUserView struct {
	ID       uuid.UUID `json:"id"`
	Username string    `json:"username"`
}

// handleListShareableUsers returns every user except the caller, for the share
// picker. Authenticated (non-admin) — usernames are not sensitive here.
func (s *Server) handleListShareableUsers(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	rows, err := s.queries.ListShareableUsers(r.Context(), user.ID)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	out := make([]shareableUserView, 0, len(rows))
	for _, u := range rows {
		out = append(out, shareableUserView{ID: u.ID, Username: u.Username})
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"users": out})
}

// createGrantBody is the {recipient_id, permission?} body for sharing.
type createGrantBody struct {
	RecipientID uuid.UUID `json:"recipient_id"`
	Permission  string    `json:"permission"`
}

// handleListFileGrants / handleCreateFileGrant / handleDeleteFileGrant manage the
// grants on a file the caller owns.
func (s *Server) handleListFileGrants(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	grants, err := s.files.ListFileGrants(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"grants": newGrantViews(grants)})
}

func (s *Server) handleCreateFileGrant(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	var body createGrantBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if body.RecipientID == uuid.Nil {
		s.writeErr(w, http.StatusBadRequest, "recipient_id is required")
		return
	}
	if err := s.files.GrantFile(r.Context(), user.ID, id, body.RecipientID, body.Permission); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	grants, err := s.files.ListFileGrants(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusCreated, map[string]any{"grants": newGrantViews(grants)})
}

func (s *Server) handleDeleteFileGrant(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	recipientID, ok := s.parseUUIDParam(w, r, "recipientId")
	if !ok {
		return
	}
	if err := s.files.RevokeFileGrant(r.Context(), user.ID, id, recipientID); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleListFolderGrants / handleCreateFolderGrant / handleDeleteFolderGrant
// manage the grants on a folder the caller owns.
func (s *Server) handleListFolderGrants(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	grants, err := s.files.ListFolderGrants(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"grants": newGrantViews(grants)})
}

func (s *Server) handleCreateFolderGrant(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	var body createGrantBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if body.RecipientID == uuid.Nil {
		s.writeErr(w, http.StatusBadRequest, "recipient_id is required")
		return
	}
	if err := s.files.GrantFolder(r.Context(), user.ID, id, body.RecipientID, body.Permission); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	grants, err := s.files.ListFolderGrants(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusCreated, map[string]any{"grants": newGrantViews(grants)})
}

func (s *Server) handleDeleteFolderGrant(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	recipientID, ok := s.parseUUIDParam(w, r, "recipientId")
	if !ok {
		return
	}
	if err := s.files.RevokeFolderGrant(r.Context(), user.ID, id, recipientID); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
