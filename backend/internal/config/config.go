// Package config loads and validates runtime configuration from the
// environment. All settings are sourced from HANGAR_* environment variables so
// the same binary runs identically in docker-compose and production.
package config

import (
	"encoding/base64"
	"fmt"
	"os"
	"strconv"
	"strings"
	"time"
)

// Telegram per-file ceilings. v1 stores one file as exactly one Telegram
// message, so an upload may not exceed the linked account's limit.
const (
	MaxFileBytesStandard = 2 * 1024 * 1024 * 1024 // 2 GiB for a non-Premium account
	MaxFileBytesPremium  = 4 * 1024 * 1024 * 1024 // 4 GiB for a Premium account
)

// Config is the fully-resolved, validated application configuration.
type Config struct {
	HTTPAddr      string
	DatabaseURL   string
	DataDir       string // scratch space for tus temp files and the download cache
	CORSOrigins   []string
	SecureCookies bool
	CookieName    string
	SessionTTL    time.Duration
	EncryptionKey []byte // exactly 32 bytes, used for AES-256-GCM of the Telegram session
	TGAPIID       int
	TGAPIHash     string
	LogLevel      string
	// PurgeDeletedOnStart, when true, runs a one-time sweep at boot that deletes
	// the Telegram messages behind every already soft-deleted file and drops the
	// rows. Used to clean orphans left by deletes that predate byte-accurate
	// deletion. Safe to leave off in normal operation.
	PurgeDeletedOnStart bool
}

// Load reads the environment, applies defaults, validates, and returns the
// Config. It returns an error listing every problem found rather than failing on
// the first, so a misconfigured deployment surfaces all issues at once.
func Load() (*Config, error) {
	c := &Config{
		HTTPAddr:            getenv("HANGAR_HTTP_ADDR", ":8080"),
		DatabaseURL:         os.Getenv("HANGAR_DATABASE_URL"),
		DataDir:             getenv("HANGAR_DATA_DIR", "./data"),
		CORSOrigins:         splitList(getenv("HANGAR_CORS_ORIGINS", "http://localhost:3000")),
		SecureCookies:       getbool("HANGAR_SECURE_COOKIES", true),
		CookieName:          getenv("HANGAR_SESSION_COOKIE_NAME", "hangar_session"),
		LogLevel:            getenv("HANGAR_LOG_LEVEL", "info"),
		TGAPIHash:           os.Getenv("HANGAR_TG_API_HASH"),
		PurgeDeletedOnStart: getbool("HANGAR_PURGE_DELETED_ON_START", false),
	}

	var errs []string
	add := func(format string, a ...any) { errs = append(errs, fmt.Sprintf(format, a...)) }

	if c.DatabaseURL == "" {
		add("HANGAR_DATABASE_URL is required")
	}

	ttl, err := time.ParseDuration(getenv("HANGAR_SESSION_TTL", "720h"))
	if err != nil {
		add("HANGAR_SESSION_TTL: %v", err)
	}
	c.SessionTTL = ttl

	if raw := os.Getenv("HANGAR_ENCRYPTION_KEY"); raw == "" {
		add("HANGAR_ENCRYPTION_KEY is required (base64 of 32 random bytes)")
	} else if key, err := base64.StdEncoding.DecodeString(strings.TrimSpace(raw)); err != nil {
		add("HANGAR_ENCRYPTION_KEY: not valid base64: %v", err)
	} else if len(key) != 32 {
		add("HANGAR_ENCRYPTION_KEY: decoded to %d bytes, need exactly 32", len(key))
	} else {
		c.EncryptionKey = key
	}

	if raw := os.Getenv("HANGAR_TG_API_ID"); raw == "" {
		add("HANGAR_TG_API_ID is required")
	} else if id, err := strconv.Atoi(strings.TrimSpace(raw)); err != nil {
		add("HANGAR_TG_API_ID: not an integer: %v", err)
	} else {
		c.TGAPIID = id
	}

	if c.TGAPIHash == "" {
		add("HANGAR_TG_API_HASH is required")
	}

	if len(errs) > 0 {
		return nil, fmt.Errorf("invalid configuration:\n  - %s", strings.Join(errs, "\n  - "))
	}
	return c, nil
}

func getenv(key, def string) string {
	if v := os.Getenv(key); v != "" {
		return v
	}
	return def
}

func getbool(key string, def bool) bool {
	v := os.Getenv(key)
	if v == "" {
		return def
	}
	b, err := strconv.ParseBool(v)
	if err != nil {
		return def
	}
	return b
}

func splitList(s string) []string {
	parts := strings.Split(s, ",")
	out := make([]string, 0, len(parts))
	for _, p := range parts {
		if p = strings.TrimSpace(p); p != "" {
			out = append(out, p)
		}
	}
	return out
}
