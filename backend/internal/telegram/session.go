package telegram

import (
	"context"
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"fmt"
	"io"
	"sync"

	"github.com/gotd/td/session"
)

// blobStore is the subset of the account store the session storage needs.
type blobStore interface {
	GetTelegramSessionBlob(ctx context.Context) ([]byte, error)
	SetTelegramSessionBlob(ctx context.Context, sessionBlobEncrypted []byte) error
}

// pgSessionStorage is a gotd session.Storage backed by the singleton
// telegram_account row. The session blob is encrypted at rest with AES-256-GCM
// (nonce prepended to the ciphertext) using the application encryption key, so
// the highly sensitive MTProto auth key never touches the database in cleartext.
//
// It satisfies github.com/gotd/td/session.Storage.
type pgSessionStorage struct {
	q   blobStore
	gcm cipher.AEAD
}

// newPGSessionStorage builds an encrypted session storage. key must be exactly
// 32 bytes (AES-256).
func newPGSessionStorage(q blobStore, key []byte) (*pgSessionStorage, error) {
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("new cipher: %w", err)
	}
	gcm, err := cipher.NewGCM(block)
	if err != nil {
		return nil, fmt.Errorf("new gcm: %w", err)
	}
	return &pgSessionStorage{q: q, gcm: gcm}, nil
}

// LoadSession returns the decrypted session blob. When the stored blob is empty
// it returns session.ErrNotFound so gotd treats it as a fresh, unauthorized
// session.
func (s *pgSessionStorage) LoadSession(ctx context.Context) ([]byte, error) {
	blob, err := s.q.GetTelegramSessionBlob(ctx)
	if err != nil {
		return nil, fmt.Errorf("load session blob: %w", err)
	}
	if len(blob) == 0 {
		return nil, session.ErrNotFound
	}
	plain, err := s.decrypt(blob)
	if err != nil {
		return nil, fmt.Errorf("decrypt session: %w", err)
	}
	return plain, nil
}

// StoreSession encrypts and persists the session blob on the singleton row.
func (s *pgSessionStorage) StoreSession(ctx context.Context, data []byte) error {
	enc, err := s.encrypt(data)
	if err != nil {
		return fmt.Errorf("encrypt session: %w", err)
	}
	if err := s.q.SetTelegramSessionBlob(ctx, enc); err != nil {
		return fmt.Errorf("store session blob: %w", err)
	}
	return nil
}

// encrypt seals plaintext with a fresh random nonce, returning nonce||ciphertext.
func (s *pgSessionStorage) encrypt(plaintext []byte) ([]byte, error) {
	nonce := make([]byte, s.gcm.NonceSize())
	if _, err := io.ReadFull(rand.Reader, nonce); err != nil {
		return nil, fmt.Errorf("read nonce: %w", err)
	}
	// Seal appends the ciphertext to nonce, so the result is nonce||ciphertext.
	return s.gcm.Seal(nonce, nonce, plaintext, nil), nil
}

// decrypt reverses encrypt, expecting a nonce-prefixed ciphertext.
func (s *pgSessionStorage) decrypt(blob []byte) ([]byte, error) {
	ns := s.gcm.NonceSize()
	if len(blob) < ns {
		return nil, fmt.Errorf("ciphertext too short: %d bytes", len(blob))
	}
	nonce, ciphertext := blob[:ns], blob[ns:]
	plain, err := s.gcm.Open(nil, nonce, ciphertext, nil)
	if err != nil {
		return nil, fmt.Errorf("open: %w", err)
	}
	return plain, nil
}

// readonlySessionStorage wraps a base session.Storage so the download client can
// reuse the linked account's auth key without ever writing it back. The main
// client owns the persisted session; the download client only loads it (and
// keeps any post-load state, like a refreshed salt, in memory). This prevents
// the two clients from racing on the singleton session blob.
type readonlySessionStorage struct {
	base session.Storage

	mu  sync.RWMutex
	mem []byte
}

func newReadonlySessionStorage(base session.Storage) *readonlySessionStorage {
	return &readonlySessionStorage{base: base}
}

func (s *readonlySessionStorage) LoadSession(ctx context.Context) ([]byte, error) {
	s.mu.RLock()
	mem := s.mem
	s.mu.RUnlock()
	if mem != nil {
		return mem, nil
	}
	return s.base.LoadSession(ctx)
}

// StoreSession keeps the session in memory only; it never writes through to the
// shared store the main client owns.
func (s *readonlySessionStorage) StoreSession(ctx context.Context, data []byte) error {
	s.mu.Lock()
	s.mem = data
	s.mu.Unlock()
	return nil
}

// memSessionStorage is a fresh, in-memory gotd session.Storage that starts
// empty (unauthorized). The link-attempt client uses it so a re-link runs the
// full SendCode/SignIn flow without reading — or destroying — the persisted
// session blob of the currently-linked account. On a successful link the bytes
// are copied to the durable pgSessionStorage by completeLink.
type memSessionStorage struct {
	mu   sync.Mutex
	data []byte
}

func (s *memSessionStorage) LoadSession(ctx context.Context) ([]byte, error) {
	s.mu.Lock()
	defer s.mu.Unlock()
	if len(s.data) == 0 {
		return nil, session.ErrNotFound
	}
	return append([]byte(nil), s.data...), nil
}

func (s *memSessionStorage) StoreSession(ctx context.Context, data []byte) error {
	s.mu.Lock()
	s.data = append([]byte(nil), data...)
	s.mu.Unlock()
	return nil
}

// snapshot returns a copy of the currently held session bytes (nil if empty).
func (s *memSessionStorage) snapshot() []byte {
	s.mu.Lock()
	defer s.mu.Unlock()
	if len(s.data) == 0 {
		return nil
	}
	return append([]byte(nil), s.data...)
}
