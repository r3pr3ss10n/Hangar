package auth

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"errors"
	"fmt"
	"time"

	"github.com/jackc/pgx/v5"

	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

// sessionTokenBytes is the number of random bytes in a session token before
// base64 encoding.
const sessionTokenBytes = 32

// newSessionToken returns a fresh random session token encoded as
// base64url-without-padding. The raw token is handed to the client; only its
// sha256 is ever persisted.
func newSessionToken() (string, error) {
	buf := make([]byte, sessionTokenBytes)
	if _, err := rand.Read(buf); err != nil {
		return "", fmt.Errorf("read random: %w", err)
	}
	return base64.RawURLEncoding.EncodeToString(buf), nil
}

// hashToken maps a raw session token to the value stored as the session id:
// the lowercase hex encoding of its sha256 digest.
func hashToken(token string) string {
	sum := sha256.Sum256([]byte(token))
	return hex.EncodeToString(sum[:])
}

// Login verifies a username/password pair and, on success, creates a session
// row. It returns the raw cookie token (never persisted directly) and the
// authenticated user. ErrInvalidCredentials is returned for both an unknown
// username and a wrong password, so callers cannot distinguish the two.
func (s *Service) Login(ctx context.Context, username, password string) (token string, user dbsqlc.User, err error) {
	user, err = s.q.GetUserByUsername(ctx, username)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return "", dbsqlc.User{}, ErrInvalidCredentials
		}
		return "", dbsqlc.User{}, fmt.Errorf("lookup user: %w", err)
	}

	ok, err := s.VerifyPassword(user.PasswordHash, password)
	if err != nil {
		return "", dbsqlc.User{}, fmt.Errorf("verify password: %w", err)
	}
	if !ok {
		return "", dbsqlc.User{}, ErrInvalidCredentials
	}

	token, err = newSessionToken()
	if err != nil {
		return "", dbsqlc.User{}, fmt.Errorf("create token: %w", err)
	}

	if _, err := s.q.CreateSession(ctx, dbsqlc.CreateSessionParams{
		ID:        hashToken(token),
		UserID:    user.ID,
		ExpiresAt: time.Now().UTC().Add(s.params.SessionTTL),
	}); err != nil {
		return "", dbsqlc.User{}, fmt.Errorf("create session: %w", err)
	}

	return token, user, nil
}

// Logout deletes the session identified by the raw token. Deleting an
// already-absent session is not an error (logout is idempotent).
func (s *Service) Logout(ctx context.Context, token string) error {
	if token == "" {
		return nil
	}
	if err := s.q.DeleteSession(ctx, hashToken(token)); err != nil {
		return fmt.Errorf("delete session: %w", err)
	}
	return nil
}

// Authenticate resolves a raw session token to its user. It returns
// ErrUnauthorized when the token is empty, or when no live (unexpired) session
// matches it.
func (s *Service) Authenticate(ctx context.Context, token string) (dbsqlc.User, error) {
	if token == "" {
		return dbsqlc.User{}, ErrUnauthorized
	}
	row, err := s.q.GetSessionUser(ctx, hashToken(token))
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return dbsqlc.User{}, ErrUnauthorized
		}
		return dbsqlc.User{}, fmt.Errorf("get session user: %w", err)
	}
	return row.User, nil
}
