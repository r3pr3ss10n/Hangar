package auth

import (
	"strings"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// newTestService builds a Service with zero-value params; password hashing does
// not touch the database, so a nil query layer is fine for these tests.
func newTestService() *Service {
	return NewService(nil, Params{}, nil)
}

func TestHashPassword_RoundTrip(t *testing.T) {
	s := newTestService()

	encoded, err := s.HashPassword("correct horse battery staple")
	require.NoError(t, err)
	require.True(t, strings.HasPrefix(encoded, "$argon2id$v=19$m=65536,t=1,p=4$"),
		"unexpected PHC prefix: %q", encoded)

	ok, err := s.VerifyPassword(encoded, "correct horse battery staple")
	require.NoError(t, err)
	assert.True(t, ok, "correct password should verify")
}

func TestVerifyPassword_WrongPasswordFails(t *testing.T) {
	s := newTestService()

	encoded, err := s.HashPassword("hunter2")
	require.NoError(t, err)

	ok, err := s.VerifyPassword(encoded, "hunter3")
	require.NoError(t, err, "a non-matching password is not an error, just false")
	assert.False(t, ok)

	ok, err = s.VerifyPassword(encoded, "")
	require.NoError(t, err)
	assert.False(t, ok)
}

func TestHashPassword_SaltIsRandom(t *testing.T) {
	s := newTestService()

	a, err := s.HashPassword("same password")
	require.NoError(t, err)
	b, err := s.HashPassword("same password")
	require.NoError(t, err)

	assert.NotEqual(t, a, b, "two hashes of the same password must differ (random salt)")

	// Both must still verify against the original password.
	for _, h := range []string{a, b} {
		ok, err := s.VerifyPassword(h, "same password")
		require.NoError(t, err)
		assert.True(t, ok)
	}
}

func TestVerifyPassword_RejectsMalformedHash(t *testing.T) {
	s := newTestService()

	valid, err := s.HashPassword("anything")
	require.NoError(t, err)

	cases := map[string]string{
		"empty":             "",
		"plain text":        "not-a-hash",
		"too few fields":    "$argon2id$v=19$m=65536,t=1,p=4$onlysalt",
		"wrong algorithm":   "$argon2i$v=19$m=65536,t=1,p=4$YWJjZGVmZ2hpamtsbW5vcA$YWJjZGVmZ2hpamtsbW5vcA",
		"wrong version":     "$argon2id$v=16$m=65536,t=1,p=4$YWJjZGVmZ2hpamtsbW5vcA$YWJjZGVmZ2hpamtsbW5vcA",
		"bad params field":  "$argon2id$v=19$m=foo,t=1,p=4$YWJjZGVmZ2hpamtsbW5vcA$YWJjZGVmZ2hpamtsbW5vcA",
		"non-base64 salt":   "$argon2id$v=19$m=65536,t=1,p=4$!!!notb64!!!$YWJjZGVmZ2hpamtsbW5vcA",
		"non-base64 hash":   "$argon2id$v=19$m=65536,t=1,p=4$YWJjZGVmZ2hpamtsbW5vcA$!!!notb64!!!",
		"missing leading $": strings.TrimPrefix(valid, "$"),
	}

	for name, bad := range cases {
		t.Run(name, func(t *testing.T) {
			ok, err := s.VerifyPassword(bad, "anything")
			assert.False(t, ok)
			require.Error(t, err, "malformed hash must error")
			assert.ErrorIs(t, err, ErrInvalidHash)
		})
	}
}

func TestDecodeArgon2Hash_ExtractsParams(t *testing.T) {
	s := newTestService()
	encoded, err := s.HashPassword("params check")
	require.NoError(t, err)

	salt, hash, ti, mem, threads, keyLen, err := decodeArgon2Hash(encoded)
	require.NoError(t, err)
	assert.Len(t, salt, argonSaltLen)
	assert.Len(t, hash, argonKeyLen)
	assert.Equal(t, uint32(argonTime), ti)
	assert.Equal(t, uint32(argonMemory), mem)
	assert.Equal(t, uint8(argonThreads), threads)
	assert.Equal(t, uint32(argonKeyLen), keyLen)
}
