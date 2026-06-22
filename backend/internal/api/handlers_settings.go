package api

import (
	"net/http"

	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
)

// settingsView is the public projection of a user's preferences.
type settingsView struct {
	GenerateThumbnails bool `json:"generate_thumbnails"`
}

// newSettingsView projects a stored user into its settings view.
func newSettingsView(u dbsqlc.User) settingsView {
	return settingsView{GenerateThumbnails: u.GenerateThumbnails}
}

// handleGetSettings returns the authenticated user's preferences. The values are
// already on the context user (loaded by RequireAuth), so no extra query runs.
func (s *Server) handleGetSettings(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	s.writeJSON(w, http.StatusOK, newSettingsView(user))
}

// patchSettingsBody is the {generate_thumbnails?} body. A pointer distinguishes
// "absent" (leave unchanged) from an explicit true/false.
type patchSettingsBody struct {
	GenerateThumbnails *bool `json:"generate_thumbnails"`
}

// handlePatchSettings updates the authenticated user's preferences and returns
// the resulting settings. Only fields present in the body are changed.
func (s *Server) handlePatchSettings(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	var body patchSettingsBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if body.GenerateThumbnails == nil {
		s.writeErr(w, http.StatusBadRequest, "nothing to update")
		return
	}

	if err := s.queries.SetUserGenerateThumbnails(r.Context(), dbsqlc.SetUserGenerateThumbnailsParams{
		ID:                 user.ID,
		GenerateThumbnails: *body.GenerateThumbnails,
	}); err != nil {
		s.writeServiceErr(w, err)
		return
	}

	user.GenerateThumbnails = *body.GenerateThumbnails
	s.writeJSON(w, http.StatusOK, newSettingsView(user))
}
