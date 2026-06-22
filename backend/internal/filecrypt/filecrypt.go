// Package filecrypt provides seekable, length-preserving encryption for file
// bodies stored in Telegram. It uses AES-256-CTR so that any byte range of a
// file can be decrypted independently — essential for HTTP Range downloads
// (video seeking, resume), which fetch arbitrary offsets without reading the
// whole file.
//
// CTR is length-preserving (ciphertext size == plaintext size), so stored sizes,
// Content-Length and Content-Range all match the plaintext and need no
// adjustment. CTR provides confidentiality but not authentication: the goal here
// is that the Telegram channel (and anyone who obtains it) holds only opaque
// ciphertext. End-to-end integrity is covered separately by the per-file sha256
// of the plaintext. GCM is unsuitable because its single authentication tag
// makes arbitrary-offset seeking impossible.
//
// Each file gets its own random 16-byte IV (stored alongside the row). The AES
// key is the application encryption key (HANGAR_ENCRYPTION_KEY, 32 bytes), the
// same key already used to encrypt the Telegram session blob.
package filecrypt

import (
	"crypto/aes"
	"crypto/cipher"
	"crypto/rand"
	"fmt"
	"io"
)

// IVSize is the per-file initialization vector length: one AES block.
const IVSize = aes.BlockSize // 16

// NewIV returns a fresh random per-file IV.
func NewIV() ([]byte, error) {
	iv := make([]byte, IVSize)
	if _, err := io.ReadFull(rand.Reader, iv); err != nil {
		return nil, fmt.Errorf("read iv: %w", err)
	}
	return iv, nil
}

// ctrStreamAt builds an AES-256-CTR keystream positioned at plaintext byte
// offset, ready to encrypt/decrypt bytes starting at that offset.
//
// CTR's counter advances one per 16-byte block starting from iv, so the counter
// for the block containing offset is iv + offset/blockSize (a big-endian 128-bit
// addition). The first offset%blockSize bytes of that block's keystream belong
// to earlier plaintext and are discarded so the stream aligns to offset.
func ctrStreamAt(key, iv []byte, offset int64) (cipher.Stream, error) {
	if len(iv) != IVSize {
		return nil, fmt.Errorf("iv must be %d bytes, got %d", IVSize, len(iv))
	}
	if offset < 0 {
		return nil, fmt.Errorf("negative offset %d", offset)
	}
	block, err := aes.NewCipher(key)
	if err != nil {
		return nil, fmt.Errorf("new cipher: %w", err)
	}

	blockIndex := offset / aes.BlockSize
	skip := int(offset % aes.BlockSize)

	// counter = iv + blockIndex, as a 128-bit big-endian integer.
	counter := make([]byte, IVSize)
	copy(counter, iv)
	addUint64BE(counter, uint64(blockIndex))

	stream := cipher.NewCTR(block, counter)
	if skip > 0 {
		// Advance the keystream past the intra-block bytes by XORing throwaway zeros.
		discard := make([]byte, skip)
		stream.XORKeyStream(discard, discard)
	}
	return stream, nil
}

// addUint64BE adds v to the big-endian integer stored in b, in place, with
// wrap-around carry across the full width of b. b is treated as a 128-bit
// big-endian counter (the AES-CTR convention used by crypto/cipher).
func addUint64BE(b []byte, v uint64) {
	carry := v
	for i := len(b) - 1; i >= 0 && carry > 0; i-- {
		sum := uint64(b[i]) + (carry & 0xff)
		b[i] = byte(sum)
		carry = (carry >> 8) + (sum >> 8)
	}
}

// EncryptingReader wraps r so that reading yields the AES-256-CTR encryption of
// r's bytes from plaintext offset 0. Used to encrypt a file on its way to
// Telegram. The output is the same length as the input.
func EncryptingReader(r io.Reader, key, iv []byte) (io.Reader, error) {
	stream, err := ctrStreamAt(key, iv, 0)
	if err != nil {
		return nil, err
	}
	return &cipher.StreamReader{S: stream, R: r}, nil
}

// DecryptingWriter wraps w so that bytes written to it are AES-256-CTR decrypted
// before reaching w, with the keystream positioned at plaintext offset start.
// Used to decrypt a Range download: the caller fetches ciphertext bytes
// [start, start+length) from Telegram and writes them here; w receives the
// corresponding plaintext. Because CTR is length-preserving, the ciphertext
// offset equals the plaintext offset, so start is both.
func DecryptingWriter(w io.Writer, key, iv []byte, start int64) (io.Writer, error) {
	stream, err := ctrStreamAt(key, iv, start)
	if err != nil {
		return nil, err
	}
	return &cipher.StreamWriter{S: stream, W: w}, nil
}
