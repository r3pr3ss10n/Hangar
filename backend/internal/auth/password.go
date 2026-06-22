package auth

import (
	"crypto/rand"
	"crypto/subtle"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"

	"golang.org/x/crypto/argon2"
)

// argon2id cost parameters. These are encoded into every PHC string, so a hash
// produced with one set of parameters still verifies after the defaults change.
const (
	argonTime    = 1         // number of passes over memory
	argonMemory  = 64 * 1024 // memory in KiB (64 MiB)
	argonThreads = 4         // parallelism / lanes
	argonKeyLen  = 32        // length of the derived key in bytes
	argonSaltLen = 16        // length of the random salt in bytes
)

// ErrInvalidHash is returned by VerifyPassword when the encoded hash is not a
// well-formed argon2id PHC string this package can parse.
var ErrInvalidHash = errors.New("auth: invalid password hash format")

// b64 is the standard base64 alphabet without padding, matching the PHC spec.
var b64 = base64.RawStdEncoding

// HashPassword derives an argon2id hash of plaintext and returns it encoded as a
// PHC string of the form
//
//	$argon2id$v=19$m=65536,t=1,p=4$<b64salt>$<b64hash>
//
// A fresh random salt is generated per call, so hashing the same password twice
// yields different strings.
func (s *Service) HashPassword(plaintext string) (string, error) {
	salt := make([]byte, argonSaltLen)
	if _, err := rand.Read(salt); err != nil {
		return "", fmt.Errorf("generate salt: %w", err)
	}
	hash := argon2.IDKey([]byte(plaintext), salt, argonTime, argonMemory, argonThreads, argonKeyLen)
	encoded := fmt.Sprintf(
		"$argon2id$v=%d$m=%d,t=%d,p=%d$%s$%s",
		argon2.Version,
		argonMemory, argonTime, argonThreads,
		b64.EncodeToString(salt),
		b64.EncodeToString(hash),
	)
	return encoded, nil
}

// VerifyPassword reports whether plaintext matches encodedHash. It parses the
// cost parameters out of the PHC string, recomputes the hash, and compares in
// constant time. A false result with a nil error means a valid hash that simply
// did not match; a non-nil error means encodedHash was malformed.
func (s *Service) VerifyPassword(encodedHash, plaintext string) (bool, error) {
	salt, want, time, memory, threads, keyLen, err := decodeArgon2Hash(encodedHash)
	if err != nil {
		return false, err
	}
	got := argon2.IDKey([]byte(plaintext), salt, time, memory, threads, keyLen)
	return subtle.ConstantTimeCompare(got, want) == 1, nil
}

// decodeArgon2Hash parses a PHC argon2id string into its salt, expected hash,
// and cost parameters. It rejects any string that is not exactly an argon2id
// hash at the supported version.
func decodeArgon2Hash(encoded string) (salt, hash []byte, time, memory uint32, threads uint8, keyLen uint32, err error) {
	parts := strings.Split(encoded, "$")
	// "$argon2id$v=19$m=..,t=..,p=..$<salt>$<hash>" -> ["", "argon2id", "v=19", "m=..,t=..,p=..", "<salt>", "<hash>"]
	if len(parts) != 6 || parts[0] != "" {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: expected 5 fields", ErrInvalidHash)
	}
	if parts[1] != "argon2id" {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: algorithm %q is not argon2id", ErrInvalidHash, parts[1])
	}

	var version int
	if _, err := fmt.Sscanf(parts[2], "v=%d", &version); err != nil {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: bad version field: %v", ErrInvalidHash, err)
	}
	if version != argon2.Version {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: unsupported version %d", ErrInvalidHash, version)
	}

	if _, err := fmt.Sscanf(parts[3], "m=%d,t=%d,p=%d", &memory, &time, &threads); err != nil {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: bad parameters field: %v", ErrInvalidHash, err)
	}

	salt, err = b64.DecodeString(parts[4])
	if err != nil {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: bad salt encoding: %v", ErrInvalidHash, err)
	}
	hash, err = b64.DecodeString(parts[5])
	if err != nil {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: bad hash encoding: %v", ErrInvalidHash, err)
	}
	if len(salt) == 0 || len(hash) == 0 {
		return nil, nil, 0, 0, 0, 0, fmt.Errorf("%w: empty salt or hash", ErrInvalidHash)
	}

	return salt, hash, time, memory, threads, uint32(len(hash)), nil
}
