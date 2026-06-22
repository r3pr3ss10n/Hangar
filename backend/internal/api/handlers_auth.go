package api

import (
	"errors"
	"net/http"
	"strings"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

// minPasswordLen is the minimum length accepted for any new or reset password.
const minPasswordLen = 8

// handleHealth reports liveness.
func (s *Server) handleHealth(w http.ResponseWriter, r *http.Request) {
	s.writeJSON(w, http.StatusOK, map[string]string{"status": "ok"})
}

// handleSetupStatus reports whether the instance still needs its first admin.
func (s *Server) handleSetupStatus(w http.ResponseWriter, r *http.Request) {
	count, err := s.queries.CountUsers(r.Context())
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]bool{"needs_setup": count == 0})
}

// credentials is the {username,password} body shared by setup and login.
type credentials struct {
	Username string `json:"username"`
	Password string `json:"password"`
}

// handleSetupCreate creates the first admin user. It is a no-op once any user
// exists (409), creating the account, hashing the password, opening a session,
// and setting the cookie on success.
func (s *Server) handleSetupCreate(w http.ResponseWriter, r *http.Request) {
	var body credentials
	if !s.decodeJSON(w, r, &body) {
		return
	}
	body.Username = strings.TrimSpace(body.Username)
	if body.Username == "" || body.Password == "" {
		s.writeErr(w, http.StatusBadRequest, "username and password are required")
		return
	}
	if len(body.Password) < minPasswordLen {
		s.writeErr(w, http.StatusBadRequest, "password must be at least 8 characters")
		return
	}

	count, err := s.queries.CountUsers(r.Context())
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	if count > 0 {
		s.writeErr(w, http.StatusConflict, "setup already completed")
		return
	}

	hash, err := s.auth.HashPassword(body.Password)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	user, err := s.queries.CreateUser(r.Context(), dbsqlc.CreateUserParams{
		Username:     body.Username,
		PasswordHash: hash,
		Role:         string(auth.RoleAdmin),
	})
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}

	// Log the new admin in immediately so the setup wizard lands authenticated.
	token, _, err := s.auth.Login(r.Context(), body.Username, body.Password)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.auth.SetSessionCookie(w, token)
	s.writeJSON(w, http.StatusCreated, map[string]any{"user": newUserView(user)})
}

// handleLogin verifies credentials, creates a session and sets the cookie.
func (s *Server) handleLogin(w http.ResponseWriter, r *http.Request) {
	var body credentials
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if body.Username == "" || body.Password == "" {
		s.writeErr(w, http.StatusBadRequest, "username and password are required")
		return
	}

	token, user, err := s.auth.Login(r.Context(), body.Username, body.Password)
	if err != nil {
		if errors.Is(err, auth.ErrInvalidCredentials) {
			s.writeErr(w, http.StatusUnauthorized, "invalid credentials")
			return
		}
		s.writeServiceErr(w, err)
		return
	}
	s.auth.SetSessionCookie(w, token)
	s.writeJSON(w, http.StatusOK, map[string]any{"user": newUserView(user)})
}

// handleLogout clears the session and cookie. It is idempotent.
func (s *Server) handleLogout(w http.ResponseWriter, r *http.Request) {
	if c, err := r.Cookie(s.auth.CookieName()); err == nil {
		if err := s.auth.Logout(r.Context(), c.Value); err != nil {
			s.writeServiceErr(w, err)
			return
		}
	}
	s.auth.ClearSessionCookie(w)
	w.WriteHeader(http.StatusNoContent)
}

// handleMe returns the authenticated user.
func (s *Server) handleMe(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"user": newUserView(user)})
}
