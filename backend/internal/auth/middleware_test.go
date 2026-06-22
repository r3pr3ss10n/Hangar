package auth

import (
	"context"
	"encoding/json"
	"net/http"
	"net/http/httptest"
	"testing"
	"time"

	"github.com/google/uuid"
	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"

	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

func TestUserFromContext_RoundTrip(t *testing.T) {
	u := dbsqlc.User{ID: uuid.New(), Username: "alice", Role: string(RoleAdmin)}
	ctx := context.WithValue(context.Background(), userContextKey, u)

	got, ok := UserFromContext(ctx)
	require.True(t, ok)
	assert.Equal(t, u, got)

	_, ok = UserFromContext(context.Background())
	assert.False(t, ok, "absent user must report ok=false")
}

func TestCookieHelpers(t *testing.T) {
	s := NewService(nil, Params{CookieName: "sess", SecureCookies: true, SessionTTL: time.Hour}, nil)
	assert.Equal(t, "sess", s.CookieName())

	set := httptest.NewRecorder()
	s.SetSessionCookie(set, "raw-token")
	setCookie := set.Result().Cookies()[0]
	assert.Equal(t, "sess", setCookie.Name)
	assert.Equal(t, "raw-token", setCookie.Value)
	assert.Equal(t, "/", setCookie.Path)
	assert.True(t, setCookie.HttpOnly)
	assert.True(t, setCookie.Secure)
	assert.Equal(t, http.SameSiteLaxMode, setCookie.SameSite)
	assert.Positive(t, setCookie.MaxAge)

	clear := httptest.NewRecorder()
	s.ClearSessionCookie(clear)
	clearCookie := clear.Result().Cookies()[0]
	assert.Equal(t, "sess", clearCookie.Name)
	assert.Empty(t, clearCookie.Value)
	assert.Negative(t, clearCookie.MaxAge, "cleared cookie expires immediately")
}

func TestRequireAuth_NoCookieIsUnauthorized(t *testing.T) {
	s := NewService(nil, Params{CookieName: "sess"}, nil)
	called := false
	h := s.RequireAuth(http.HandlerFunc(func(http.ResponseWriter, *http.Request) { called = true }))

	rec := httptest.NewRecorder()
	h.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))

	assert.False(t, called, "downstream handler must not run")
	assert.Equal(t, http.StatusUnauthorized, rec.Code)
	var body map[string]string
	require.NoError(t, json.Unmarshal(rec.Body.Bytes(), &body))
	assert.Equal(t, "unauthorized", body["error"])
}

func TestRequireAdmin(t *testing.T) {
	s := NewService(nil, Params{}, nil)
	guarded := s.RequireAdmin(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.WriteHeader(http.StatusOK)
	}))

	t.Run("admin passes", func(t *testing.T) {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/", nil)
		ctx := context.WithValue(req.Context(), userContextKey, dbsqlc.User{Role: string(RoleAdmin)})
		guarded.ServeHTTP(rec, req.WithContext(ctx))
		assert.Equal(t, http.StatusOK, rec.Code)
	})

	t.Run("non-admin forbidden", func(t *testing.T) {
		rec := httptest.NewRecorder()
		req := httptest.NewRequest(http.MethodGet, "/", nil)
		ctx := context.WithValue(req.Context(), userContextKey, dbsqlc.User{Role: string(RoleUser)})
		guarded.ServeHTTP(rec, req.WithContext(ctx))
		assert.Equal(t, http.StatusForbidden, rec.Code)
		var body map[string]string
		require.NoError(t, json.Unmarshal(rec.Body.Bytes(), &body))
		assert.Equal(t, "forbidden", body["error"])
	})

	t.Run("missing user forbidden", func(t *testing.T) {
		rec := httptest.NewRecorder()
		guarded.ServeHTTP(rec, httptest.NewRequest(http.MethodGet, "/", nil))
		assert.Equal(t, http.StatusForbidden, rec.Code)
	})
}
