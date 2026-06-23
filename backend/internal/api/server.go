// Package api assembles the Hangar HTTP surface: the chi router, the middleware
// stack (request id, real ip, panic recovery, structured request logging, CORS,
// per-user rate limiting on the byte routes) and every handler. It owns no
// business logic of its own — it validates input, calls the auth/files/telegram
// services, and renders JSON (or streams bytes) back to the client.
package api

import (
	"context"
	"log/slog"
	"net/http"
	"time"

	"github.com/go-chi/chi/v5"
	"github.com/go-chi/chi/v5/middleware"
	"github.com/jackc/pgx/v5/pgxpool"
	"golang.org/x/time/rate"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/config"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
)

// Deps are the collaborators the api layer wires together. main constructs each
// and hands them in.
type Deps struct {
	Config   *config.Config
	Pool     *pgxpool.Pool
	Queries  *dbsqlc.Queries
	Auth     *auth.Service
	Files    *files.Service
	Telegram *telegram.Manager
	Logger   *slog.Logger
}

// Server holds the wired dependencies and serves the router built by Router.
type Server struct {
	cfg       *config.Config
	pool      *pgxpool.Pool
	queries   *dbsqlc.Queries
	auth      *auth.Service
	files     *files.Service
	telegram  *telegram.Manager
	logger    *slog.Logger
	uploadLim *userRateLimiter
	dlLim     *userRateLimiter
	shareLim  *userRateLimiter
	authLim   *userRateLimiter
}

// NewServer builds a Server from its dependencies. A nil logger falls back to
// slog.Default so the server is always usable.
func NewServer(d Deps) *Server {
	logger := d.Logger
	if logger == nil {
		logger = slog.Default()
	}
	return &Server{
		cfg:      d.Config,
		pool:     d.Pool,
		queries:  d.Queries,
		auth:     d.Auth,
		files:    d.Files,
		telegram: d.Telegram,
		logger:   logger,
		// Generous per-user ceilings: bytes are the real bottleneck, these only
		// stop a single account from hammering the shared Telegram client.
		uploadLim: newUserRateLimiter(rate.Every(time.Second/10), 20),
		dlLim:     newUserRateLimiter(rate.Every(time.Second/20), 40),
		// Public share downloads are keyed by client IP (no user); a bit tighter
		// than the per-user ceiling since anyone can hit a link.
		shareLim: newUserRateLimiter(rate.Every(time.Second/10), 20),
		// Credential endpoints (setup/login) keyed by client IP to slow down
		// brute-force and credential-stuffing attempts. Tight by design: a real
		// user logs in rarely, so ~1 req/s with a small burst is plenty.
		authLim: newUserRateLimiter(rate.Every(time.Second), 5),
	}
}

// StartReaper runs a background sweep that evicts idle rate-limiter buckets
// until ctx is cancelled. Without it the IP-keyed limiters on public endpoints
// would accumulate one bucket per distinct client IP forever. Non-blocking: it
// launches a goroutine and returns.
func (s *Server) StartReaper(ctx context.Context) {
	limiters := []*userRateLimiter{s.uploadLim, s.dlLim, s.shareLim, s.authLim}
	go func() {
		ticker := time.NewTicker(bucketTTL)
		defer ticker.Stop()
		for {
			select {
			case <-ctx.Done():
				return
			case now := <-ticker.C:
				for _, l := range limiters {
					l.reap(now)
				}
			}
		}
	}()
}

// Router builds the chi router with the full middleware stack and all routes.
func (s *Server) Router() http.Handler {
	r := chi.NewRouter()

	// Middleware order: request id -> real ip -> recoverer -> structured request
	// log -> CORS -> routes.
	r.Use(middleware.RequestID)
	r.Use(middleware.RealIP)
	r.Use(middleware.Recoverer)
	r.Use(s.requestLogger)
	r.Use(s.corsMiddleware())

	r.Route("/api", func(r chi.Router) {
		// --- public ---
		r.Get("/health", s.handleHealth)
		r.Get("/setup/status", s.handleSetupStatus)
		// Credential endpoints — rate limited per client IP to blunt
		// brute-force / credential-stuffing (argon2id slows each guess,
		// this caps the attempt rate).
		r.With(s.authLim.ipMiddleware).Post("/setup", s.handleSetupCreate)
		r.With(s.authLim.ipMiddleware).Post("/auth/login", s.handleLogin)
		r.Post("/auth/logout", s.handleLogout)

		// public share links — anyone holding the token can read the
		// file's metadata, thumbnail and bytes until the link expires.
		r.Get("/share/{token}", s.handleGetShare)
		r.Get("/share/{token}/thumb", s.handleShareThumb)
		// Byte route — rate limited per client IP (no user to key on).
		r.With(s.shareLim.ipMiddleware).Get("/share/{token}/download", s.handleShareDownload)

		// --- authenticated ---
		r.Group(func(r chi.Router) {
			r.Use(s.auth.RequireAuth)

			r.Get("/auth/me", s.handleMe)

			// storage used by the caller (informational, no quota)
			r.Get("/storage", s.handleStorage)

			// per-user settings
			r.Get("/settings", s.handleGetSettings)
			r.Patch("/settings", s.handlePatchSettings)

			// search (fuzzy, across the owner's whole drive)
			r.Get("/search", s.handleSearch)

			// folders
			r.Get("/folders", s.handleListFolders)
			r.Post("/folders", s.handleCreateFolder)
			r.Patch("/folders/{id}", s.handlePatchFolder)
			r.Delete("/folders/{id}", s.handleDeleteFolder)

			// files (metadata + mutation)
			r.Get("/files/{id}/meta", s.handleFileMeta)
			// thumbnail (small, served from Postgres; not rate limited)
			r.Get("/files/{id}/thumb", s.handleThumb)
			r.Patch("/files/{id}", s.handlePatchFile)
			r.Delete("/files/{id}", s.handleDeleteFile)

			// public share links (owner-managed token capability)
			r.Get("/shares", s.handleListMyShares)
			r.Get("/files/{id}/shares", s.handleListShares)
			r.Post("/files/{id}/shares", s.handleCreateShare)
			r.Delete("/shares/{token}", s.handleDeleteShare)

			// internal access grants (owner-managed, user-to-user)
			r.Get("/users/shareable", s.handleListShareableUsers)
			r.Get("/files/{id}/grants", s.handleListFileGrants)
			r.Post("/files/{id}/grants", s.handleCreateFileGrant)
			r.Delete("/files/{id}/grants/{recipientId}", s.handleDeleteFileGrant)
			r.Get("/folders/{id}/grants", s.handleListFolderGrants)
			r.Post("/folders/{id}/grants", s.handleCreateFolderGrant)
			r.Delete("/folders/{id}/grants/{recipientId}", s.handleDeleteFolderGrant)

			// "shared with me" browsing (recipient)
			r.Get("/shared", s.handleListSharedRoots)
			r.Get("/shared/folders/{id}", s.handleListSharedChildren)

			// labels: tags
			r.Get("/labels", s.handleGetLabels)
			r.Get("/tags", s.handleListTags)
			r.Post("/tags", s.handleCreateTag)
			r.Patch("/tags/{id}", s.handleUpdateTag)
			r.Delete("/tags/{id}", s.handleDeleteTag)
			r.Get("/tags/{id}/items", s.handleListTagItems)
			r.Post("/files/{id}/tags", s.handleAddFileTag)
			r.Delete("/files/{id}/tags/{tagId}", s.handleRemoveFileTag)
			r.Post("/folders/{id}/tags", s.handleAddFolderTag)
			r.Delete("/folders/{id}/tags/{tagId}", s.handleRemoveFolderTag)

			// ranged download (byte route, rate limited per user)
			r.With(s.dlLim.middleware).Get("/files/{id}", s.handleDownload)
		})

		// --- resumable, parallel upload (tus), behind RequireAuth ---
		s.mountTus(r)

		// --- admin ---
		r.Group(func(r chi.Router) {
			r.Use(s.auth.RequireAuth)
			r.Use(s.auth.RequireAdmin)

			r.Get("/users", s.handleListUsers)
			r.Post("/users", s.handleCreateUser)
			r.Delete("/users/{id}", s.handleDeleteUser)
			r.Post("/users/{id}/password", s.handleSetUserPassword)

			r.Get("/telegram/status", s.handleTelegramStatus)
			r.Post("/telegram/link/start", s.handleLinkStart)
			r.Post("/telegram/link/code", s.handleLinkCode)
			r.Post("/telegram/link/password", s.handleLinkPassword)
			r.Post("/telegram/link/cancel", s.handleLinkCancel)
			r.Post("/telegram/unlink", s.handleUnlink)
		})
	})

	return r
}
