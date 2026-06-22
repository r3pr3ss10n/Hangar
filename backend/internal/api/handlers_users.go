package api

import (
	"errors"
	"net/http"
	"strings"

	"github.com/go-chi/chi/v5"
	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

// handleListUsers returns every user (admin only).
func (s *Server) handleListUsers(w http.ResponseWriter, r *http.Request) {
	users, err := s.queries.ListUsers(r.Context())
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"users": newUserViews(users)})
}

// createUserBody is the {username,password,role} body for admin user creation.
type createUserBody struct {
	Username string `json:"username"`
	Password string `json:"password"`
	Role     string `json:"role"`
}

// handleCreateUser creates a user with the given role (admin only).
func (s *Server) handleCreateUser(w http.ResponseWriter, r *http.Request) {
	var body createUserBody
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
	if body.Role != string(auth.RoleAdmin) && body.Role != string(auth.RoleUser) {
		s.writeErr(w, http.StatusBadRequest, "role must be 'admin' or 'user'")
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
		Role:         body.Role,
	})
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusCreated, map[string]any{"user": newUserView(user)})
}

// handleDeleteUser deletes a user (admin only). It refuses to delete the caller
// themselves or the last remaining admin, returning 400 in either case.
func (s *Server) handleDeleteUser(w http.ResponseWriter, r *http.Request) {
	caller, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	if id == caller.ID {
		s.writeErr(w, http.StatusBadRequest, "cannot delete your own account")
		return
	}

	target, err := s.queries.GetUserByID(r.Context(), id)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			s.writeErr(w, http.StatusNotFound, "user not found")
			return
		}
		s.writeServiceErr(w, err)
		return
	}

	// Refuse to remove the last admin so the instance never loses its operator.
	if target.Role == string(auth.RoleAdmin) {
		admins, err := s.queries.CountAdmins(r.Context())
		if err != nil {
			s.writeServiceErr(w, err)
			return
		}
		if admins <= 1 {
			s.writeErr(w, http.StatusBadRequest, "cannot delete the last admin")
			return
		}
	}

	if err := s.queries.DeleteUser(r.Context(), id); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	// Drop any live sessions so a deleted user is immediately logged out.
	if err := s.queries.DeleteUserSessions(r.Context(), id); err != nil {
		s.logger.Error("api: delete user sessions", "error", err, "user_id", id)
	}
	w.WriteHeader(http.StatusNoContent)
}

// setPasswordBody is the {password} body for an admin password reset.
type setPasswordBody struct {
	Password string `json:"password"`
}

// handleSetUserPassword resets a user's password (admin only).
func (s *Server) handleSetUserPassword(w http.ResponseWriter, r *http.Request) {
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	var body setPasswordBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if body.Password == "" {
		s.writeErr(w, http.StatusBadRequest, "password is required")
		return
	}
	if len(body.Password) < minPasswordLen {
		s.writeErr(w, http.StatusBadRequest, "password must be at least 8 characters")
		return
	}

	if _, err := s.queries.GetUserByID(r.Context(), id); err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			s.writeErr(w, http.StatusNotFound, "user not found")
			return
		}
		s.writeServiceErr(w, err)
		return
	}

	hash, err := s.auth.HashPassword(body.Password)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	if err := s.queries.UpdateUserPassword(r.Context(), dbsqlc.UpdateUserPasswordParams{
		ID:           id,
		PasswordHash: hash,
	}); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	// Invalidate existing sessions so an old credential cannot linger.
	if err := s.queries.DeleteUserSessions(r.Context(), id); err != nil {
		s.logger.Error("api: delete user sessions", "error", err, "user_id", id)
	}
	w.WriteHeader(http.StatusNoContent)
}

// parseUUIDParam parses a chi URL parameter as a uuid, writing a 400 and
// returning ok=false when it is missing or malformed.
func (s *Server) parseUUIDParam(w http.ResponseWriter, r *http.Request, name string) (uuid.UUID, bool) {
	raw := chi.URLParam(r, name)
	id, err := uuid.Parse(raw)
	if err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid "+name)
		return uuid.UUID{}, false
	}
	return id, true
}
