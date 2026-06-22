// Command server is the Hangar backend entrypoint. It loads configuration,
// connects to Postgres and applies migrations, constructs the auth, files and
// Telegram services, assembles the HTTP API, and serves it with graceful
// shutdown on SIGINT/SIGTERM.
package main

import (
	"context"
	"errors"
	"log/slog"
	"net/http"
	"os"
	"os/signal"
	"strings"
	"syscall"
	"time"

	"github.com/r3pr3ss10n/hangar/backend/internal/api"
	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/config"
	"github.com/r3pr3ss10n/hangar/backend/internal/db"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
)

func main() {
	if err := run(); err != nil {
		slog.Error("server exited with error", "error", err)
		os.Exit(1)
	}
}

func run() error {
	cfg, err := config.Load()
	if err != nil {
		return err
	}

	logger := newLogger(cfg.LogLevel)
	slog.SetDefault(logger)

	// Root context cancelled on the first SIGINT/SIGTERM; it threads through the
	// Telegram manager and the eventual shutdown.
	ctx, stop := signal.NotifyContext(context.Background(), syscall.SIGINT, syscall.SIGTERM)
	defer stop()

	// Apply migrations before opening the pool so the schema is present.
	if err := db.Migrate(cfg.DatabaseURL); err != nil {
		return err
	}
	logger.Info("migrations applied")

	pool, err := db.Connect(ctx, cfg.DatabaseURL)
	if err != nil {
		return err
	}
	defer pool.Close()
	logger.Info("database connected")

	queries := dbsqlc.New(pool)

	authSvc := auth.NewService(queries, auth.Params{
		CookieName:    cfg.CookieName,
		SecureCookies: cfg.SecureCookies,
		SessionTTL:    cfg.SessionTTL,
	}, logger)

	filesSvc := files.NewService(queries)

	tgManager := telegram.NewManager(queries, telegram.Config{
		APIID:         cfg.TGAPIID,
		APIHash:       cfg.TGAPIHash,
		EncryptionKey: cfg.EncryptionKey,
	}, logger)

	// Start connects the persistent Telegram client only if an account is already
	// linked. It is non-blocking; a failure here should not prevent the API from
	// serving (linking is an admin action over HTTP).
	if err := tgManager.Start(ctx); err != nil {
		logger.Error("telegram: start failed", "error", err)
	}
	defer tgManager.Stop()

	// One-time cleanup of files soft-deleted before delete became byte-accurate:
	// their Telegram messages still hold the bytes. Gated by config so it only
	// runs when explicitly requested.
	if cfg.PurgeDeletedOnStart {
		go reconcileDeletedFiles(ctx, queries, tgManager, logger)
	}

	server := api.NewServer(api.Deps{
		Config:   cfg,
		Pool:     pool,
		Queries:  queries,
		Auth:     authSvc,
		Files:    filesSvc,
		Telegram: tgManager,
		Logger:   logger,
	})

	// Background sweep that evicts idle rate-limiter buckets so the IP-keyed
	// limiters on public endpoints cannot grow without bound.
	server.StartReaper(ctx)

	httpServer := &http.Server{
		Addr:    cfg.HTTPAddr,
		Handler: server.Router(),
		// Uploads and downloads stream large bodies, so per-request read/write
		// deadlines are unworkable. Bound only the header read; rely on the
		// request context (and the client) to govern body duration.
		ReadHeaderTimeout: 30 * time.Second,
		IdleTimeout:       120 * time.Second,
	}

	// Serve in the background so we can wait on the shutdown signal.
	serveErr := make(chan error, 1)
	go func() {
		logger.Info("http server listening", "addr", cfg.HTTPAddr)
		if err := httpServer.ListenAndServe(); err != nil && !errors.Is(err, http.ErrServerClosed) {
			serveErr <- err
			return
		}
		serveErr <- nil
	}()

	select {
	case err := <-serveErr:
		return err
	case <-ctx.Done():
		logger.Info("shutdown signal received, draining connections")
	}

	// Give in-flight requests a bounded window to finish before forcing close.
	shutdownCtx, cancel := context.WithTimeout(context.Background(), 30*time.Second)
	defer cancel()
	if err := httpServer.Shutdown(shutdownCtx); err != nil {
		logger.Error("graceful shutdown failed", "error", err)
		return err
	}
	logger.Info("server stopped cleanly")
	return nil
}

// reconcileDeletedFiles sweeps every soft-deleted file, deletes its bytes from
// the Telegram channel, and hard-deletes the row. It is the cleanup path for
// orphans created by deletes that predate byte-accurate deletion. Because the
// persistent client connects asynchronously after Start, each delete retries
// briefly while the account is still ErrNotLinked.
func reconcileDeletedFiles(ctx context.Context, queries *dbsqlc.Queries, tg *telegram.Manager, logger *slog.Logger) {
	deleted, err := queries.ListDeletedFiles(ctx)
	if err != nil {
		logger.Error("purge: list soft-deleted files failed", "error", err)
		return
	}
	if len(deleted) == 0 {
		return
	}
	logger.Info("purge: reconciling orphaned soft-deleted files", "count", len(deleted))

	var purged, failed int
	for _, f := range deleted {
		// Wait out the async client connect (Start runs in the background).
		var delErr error
		for attempt := 0; attempt < 15; attempt++ {
			delErr = tg.Delete(ctx, f.TgMessageID)
			if !errors.Is(delErr, telegram.ErrNotLinked) {
				break
			}
			select {
			case <-time.After(2 * time.Second):
			case <-ctx.Done():
				return
			}
		}
		if delErr != nil {
			logger.Error("purge: telegram delete failed", "name", f.Name, "message_id", f.TgMessageID, "error", delErr)
			failed++
			continue
		}
		if err := queries.HardDeleteFile(ctx, f.ID); err != nil {
			logger.Error("purge: hard-delete row failed", "file_id", f.ID, "error", err)
			failed++
			continue
		}
		logger.Info("purge: removed orphaned file", "name", f.Name, "message_id", f.TgMessageID)
		purged++
	}
	logger.Info("purge: reconciliation complete", "purged", purged, "failed", failed)
}

// newLogger builds a JSON slog logger at the level named by lvl (debug/info/
// warn/error), defaulting to info for an unrecognized value.
func newLogger(lvl string) *slog.Logger {
	var level slog.Level
	switch strings.ToLower(strings.TrimSpace(lvl)) {
	case "debug":
		level = slog.LevelDebug
	case "warn", "warning":
		level = slog.LevelWarn
	case "error":
		level = slog.LevelError
	default:
		level = slog.LevelInfo
	}
	return slog.New(slog.NewJSONHandler(os.Stdout, &slog.HandlerOptions{Level: level}))
}
