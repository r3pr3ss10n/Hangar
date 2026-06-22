// Package auth provides password hashing, opaque cookie-backed sessions, and
// role-based access-control middleware for the Hangar API.
//
// Passwords are hashed with argon2id and stored as self-describing PHC strings,
// so the cost parameters travel with each hash and can be tuned without a
// migration. Sessions are opaque: the client holds a random token in an
// httpOnly cookie while Postgres stores only sha256(token), so a database leak
// never reveals a usable session token.
package auth

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"time"

	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

// Role is a user's access level.
type Role string

// The recognized roles.
const (
	RoleAdmin Role = "admin"
	RoleUser  Role = "user"
)

// Sentinel errors returned by the service. Compare with errors.Is.
var (
	// ErrInvalidCredentials is returned by Login when the username is unknown
	// or the password does not match.
	ErrInvalidCredentials = errors.New("auth: invalid credentials")
	// ErrUnauthorized is returned by Authenticate when the token is missing,
	// malformed, or its session has expired.
	ErrUnauthorized = errors.New("auth: unauthorized")
)

// Params configures session cookies and lifetime.
type Params struct {
	// CookieName is the name of the session cookie.
	CookieName string
	// SecureCookies sets the Secure attribute on the session cookie. Enable in
	// production (HTTPS); disable for plain-HTTP local development.
	SecureCookies bool
	// SessionTTL is how long a freshly-created session remains valid.
	SessionTTL time.Duration
}

// Service issues and validates sessions and hashes passwords. It is safe for
// concurrent use.
type Service struct {
	q      *dbsqlc.Queries
	params Params
	logger *slog.Logger
}

// NewService constructs a Service over the given query layer and parameters. A
// nil logger falls back to slog.Default so the service is always usable.
func NewService(q *dbsqlc.Queries, p Params, logger *slog.Logger) *Service {
	if logger == nil {
		logger = slog.Default()
	}
	return &Service{q: q, params: p, logger: logger}
}

// CookieName returns the configured session cookie name.
func (s *Service) CookieName() string {
	return s.params.CookieName
}

// contextKey is a private type for context values so no other package can
// collide with or read our keys.
type contextKey struct{ name string }

// userContextKey is the key under which RequireAuth stores the authenticated
// user.
var userContextKey = contextKey{name: "auth.user"}

// UserFromContext returns the authenticated user injected by RequireAuth, and a
// bool reporting whether one was present.
func UserFromContext(ctx context.Context) (dbsqlc.User, bool) {
	u, ok := ctx.Value(userContextKey).(dbsqlc.User)
	return u, ok
}

// ContextWithUser returns a copy of ctx carrying user, as RequireAuth would. It
// exists so handler/hook tests can construct an authenticated context without
// standing up the full middleware and session machinery.
func ContextWithUser(ctx context.Context, user dbsqlc.User) context.Context {
	return context.WithValue(ctx, userContextKey, user)
}

// SetSessionCookie writes the session cookie carrying the raw token. The cookie
// is httpOnly, scoped to the whole site (Path=/), SameSite=Lax, and Secure
// according to Params. Its Max-Age matches the session TTL.
func (s *Service) SetSessionCookie(w http.ResponseWriter, token string) {
	http.SetCookie(w, &http.Cookie{
		Name:     s.params.CookieName,
		Value:    token,
		Path:     "/",
		MaxAge:   int(s.params.SessionTTL.Seconds()),
		HttpOnly: true,
		Secure:   s.params.SecureCookies,
		SameSite: http.SameSiteLaxMode,
	})
}

// ClearSessionCookie expires the session cookie on the client.
func (s *Service) ClearSessionCookie(w http.ResponseWriter) {
	http.SetCookie(w, &http.Cookie{
		Name:     s.params.CookieName,
		Value:    "",
		Path:     "/",
		MaxAge:   -1,
		HttpOnly: true,
		Secure:   s.params.SecureCookies,
		SameSite: http.SameSiteLaxMode,
	})
}
