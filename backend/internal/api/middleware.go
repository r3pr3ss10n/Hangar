package api

import (
	"net"
	"net/http"
	"sync"
	"time"

	"github.com/go-chi/chi/v5/middleware"
	"github.com/go-chi/cors"
	"golang.org/x/time/rate"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
)

// corsMiddleware builds the CORS handler from the configured origins. Credentials
// are allowed so the SPA can send the session cookie cross-origin.
func (s *Server) corsMiddleware() func(http.Handler) http.Handler {
	return cors.Handler(cors.Options{
		AllowedOrigins:   s.cfg.CORSOrigins,
		AllowedMethods:   []string{http.MethodGet, http.MethodPost, http.MethodPatch, http.MethodDelete, http.MethodHead, http.MethodOptions},
		AllowedHeaders:   []string{"Accept", "Authorization", "Content-Type", "Content-Length", "X-Upload-Filename", "X-Requested-With", "Tus-Resumable", "Upload-Length", "Upload-Metadata", "Upload-Offset", "Upload-Concat"},
		ExposedHeaders:   []string{"Hangar-File-Id", "Content-Disposition", "Content-Range", "Accept-Ranges", "Location", "Tus-Resumable", "Upload-Offset", "Upload-Length", "Upload-Metadata"},
		AllowCredentials: true,
		MaxAge:           300,
	})
}

// requestLogger logs one structured line per request once it completes,
// capturing method, path, status, byte count, duration, request id and remote
// IP. It wraps the ResponseWriter to observe the status and size.
func (s *Server) requestLogger(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		start := time.Now()
		ww := middleware.NewWrapResponseWriter(w, r.ProtoMajor)
		next.ServeHTTP(ww, r)
		s.logger.Info("http request",
			"method", r.Method,
			"path", r.URL.Path,
			"status", ww.Status(),
			"bytes", ww.BytesWritten(),
			"duration", time.Since(start).String(),
			"request_id", middleware.GetReqID(r.Context()),
			"remote_ip", r.RemoteAddr,
		)
	})
}

// userRateLimiter is a per-key token-bucket limiter keyed by user id (byte
// routes) or client IP (public routes). It guards the byte-heavy upload/download
// routes and the credential endpoints. Buckets are created lazily on first use
// and evicted once idle for longer than bucketTTL, so an IP-keyed limiter on a
// public endpoint cannot grow the map without bound.
type userRateLimiter struct {
	mu      sync.Mutex
	buckets map[string]*rateBucket
	limit   rate.Limit
	burst   int
}

// rateBucket is a token bucket plus the last time it was touched, used by the
// reaper to evict idle entries.
type rateBucket struct {
	lim      *rate.Limiter
	lastSeen time.Time
}

// bucketTTL is how long an idle bucket is retained before the reaper evicts it.
// Generous relative to the limiter rates so an active client never loses its
// bucket between requests.
const bucketTTL = 10 * time.Minute

// newUserRateLimiter builds a limiter granting r operations per second with the
// given burst per key.
func newUserRateLimiter(r rate.Limit, burst int) *userRateLimiter {
	return &userRateLimiter{
		buckets: make(map[string]*rateBucket),
		limit:   r,
		burst:   burst,
	}
}

// get returns the bucket for key, creating it on first use and stamping it as
// recently seen.
func (l *userRateLimiter) get(key string) *rate.Limiter {
	l.mu.Lock()
	defer l.mu.Unlock()
	b, ok := l.buckets[key]
	if !ok {
		b = &rateBucket{lim: rate.NewLimiter(l.limit, l.burst)}
		l.buckets[key] = b
	}
	b.lastSeen = time.Now()
	return b.lim
}

// reap evicts buckets idle for longer than bucketTTL. Called periodically by the
// reaper goroutine.
func (l *userRateLimiter) reap(now time.Time) {
	l.mu.Lock()
	defer l.mu.Unlock()
	for key, b := range l.buckets {
		if now.Sub(b.lastSeen) > bucketTTL {
			delete(l.buckets, key)
		}
	}
}

// middleware enforces the per-user rate limit. It must be mounted inside
// RequireAuth so the user is present in the context; an over-limit caller gets
// 429.
func (l *userRateLimiter) middleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		user, ok := auth.UserFromContext(r.Context())
		if ok && !l.get(user.ID.String()).Allow() {
			writeRateLimited(w)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// ipMiddleware enforces the limit keyed by client IP. Used for unauthenticated
// byte routes (public share downloads) where there is no user to key on.
func (l *userRateLimiter) ipMiddleware(next http.Handler) http.Handler {
	return http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		if !l.get(clientIP(r)).Allow() {
			writeRateLimited(w)
			return
		}
		next.ServeHTTP(w, r)
	})
}

// clientIP extracts the client IP for rate-limit keying. RealIP has already
// normalised RemoteAddr to the real client address; strip any port.
func clientIP(r *http.Request) string {
	if host, _, err := net.SplitHostPort(r.RemoteAddr); err == nil {
		return host
	}
	return r.RemoteAddr
}

// writeRateLimited renders the 429 response shared by the limiter middlewares.
func writeRateLimited(w http.ResponseWriter) {
	w.Header().Set("Content-Type", "application/json; charset=utf-8")
	w.Header().Set("Retry-After", "1")
	w.WriteHeader(http.StatusTooManyRequests)
	_, _ = w.Write([]byte(`{"error":"rate limit exceeded"}`))
}
