package auth

import (
	"context"
	"encoding/json"
	"net/http"
)

// writeError writes a JSON error body of the form {"error": msg} with the given
// status. It is the single place auth middleware emits failures so the wire
// shape stays consistent.
func writeError(w http.ResponseWriter, status int, msg string) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.WriteHeader(status)
	_ = json.NewEncoder(w).Encode(map[string]string{"error": msg})
}

// RequireAuth wraps next so it only runs for an authenticated request. It reads
// the session cookie, resolves it to a user, and injects that user into the
// request context. A missing or invalid session yields 401 with a JSON error.
func (s *Service) RequireAuth(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		cookie, err := r.Cookie(s.params.CookieName)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "unauthorized")
			return
		}
		user, err := s.Authenticate(r.Context(), cookie.Value)
		if err != nil {
			writeError(w, http.StatusUnauthorized, "unauthorized")
			return
		}
		ctx := context.WithValue(r.Context(), userContextKey, user)
		next.ServeHTTP(w, r.WithContext(ctx))
	})
}

// RequireAdmin wraps next so it only runs for an admin user. It must be mounted
// inside (after) RequireAuth, which populates the context user; a non-admin (or
// a missing user) yields 403 with a JSON error.
func (s *Service) RequireAdmin(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		user, ok := UserFromContext(r.Context())
		if !ok || user.Role != string(RoleAdmin) {
			writeError(w, http.StatusForbidden, "forbidden")
			return
		}
		next.ServeHTTP(w, r)
	})
}
