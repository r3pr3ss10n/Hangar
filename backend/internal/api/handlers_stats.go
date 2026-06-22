package api

import (
	"net/http"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
)

// handleStorage returns how many bytes of (non-deleted) files the caller owns.
// There is no quota — this is informational only.
func (s *Server) handleStorage(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	used, err := s.files.StorageUsed(r.Context(), user.ID)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"used_bytes": used})
}
