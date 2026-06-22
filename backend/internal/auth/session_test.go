package auth

import (
	"crypto/sha256"
	"encoding/base64"
	"encoding/hex"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

func TestNewSessionToken_IsRandomBase64URL(t *testing.T) {
	a, err := newSessionToken()
	require.NoError(t, err)
	b, err := newSessionToken()
	require.NoError(t, err)

	assert.NotEqual(t, a, b, "tokens must be unique")

	raw, err := base64.RawURLEncoding.DecodeString(a)
	require.NoError(t, err, "token must be valid base64url-nopad")
	assert.Len(t, raw, sessionTokenBytes, "token must decode to 32 bytes")
}

func TestHashToken_IsHexSHA256(t *testing.T) {
	const token = "some-opaque-token"
	got := hashToken(token)

	sum := sha256.Sum256([]byte(token))
	assert.Equal(t, hex.EncodeToString(sum[:]), got)
	assert.Len(t, got, sha256.Size*2, "hex sha256 is 64 chars")

	// Hashing is deterministic and the raw token never appears in the digest.
	assert.Equal(t, got, hashToken(token))
	assert.NotContains(t, got, token)
}
