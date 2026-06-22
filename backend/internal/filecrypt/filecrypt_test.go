package filecrypt

import (
	"bytes"
	"crypto/rand"
	"io"
	"testing"
)

func mustKey(t *testing.T) []byte {
	t.Helper()
	key := make([]byte, 32)
	if _, err := rand.Read(key); err != nil {
		t.Fatalf("rand key: %v", err)
	}
	return key
}

func mustIV(t *testing.T) []byte {
	t.Helper()
	iv, err := NewIV()
	if err != nil {
		t.Fatalf("new iv: %v", err)
	}
	return iv
}

// encryptAll returns the full ciphertext of plaintext (offset 0).
func encryptAll(t *testing.T, key, iv, plaintext []byte) []byte {
	t.Helper()
	er, err := EncryptingReader(bytes.NewReader(plaintext), key, iv)
	if err != nil {
		t.Fatalf("encrypting reader: %v", err)
	}
	ct, err := io.ReadAll(er)
	if err != nil {
		t.Fatalf("read ciphertext: %v", err)
	}
	if len(ct) != len(plaintext) {
		t.Fatalf("ciphertext length %d != plaintext length %d (CTR must be length-preserving)", len(ct), len(plaintext))
	}
	return ct
}

// TestRoundTrip encrypts then fully decrypts and expects the original bytes.
func TestRoundTrip(t *testing.T) {
	key, iv := mustKey(t), mustIV(t)
	plaintext := make([]byte, 1<<16+123) // not a block multiple
	if _, err := rand.Read(plaintext); err != nil {
		t.Fatal(err)
	}
	ct := encryptAll(t, key, iv, plaintext)

	var out bytes.Buffer
	dw, err := DecryptingWriter(&out, key, iv, 0)
	if err != nil {
		t.Fatalf("decrypting writer: %v", err)
	}
	if _, err := dw.Write(ct); err != nil {
		t.Fatalf("write ciphertext: %v", err)
	}
	if !bytes.Equal(out.Bytes(), plaintext) {
		t.Fatal("round-trip mismatch")
	}
}

// TestSeekedRangeDecrypt is the property range downloads rely on: decrypting the
// ciphertext slice [start, end) with the writer seeded at start must equal the
// plaintext slice [start, end), for arbitrary (especially non-block-aligned)
// offsets.
func TestSeekedRangeDecrypt(t *testing.T) {
	key, iv := mustKey(t), mustIV(t)
	plaintext := make([]byte, 100000)
	if _, err := rand.Read(plaintext); err != nil {
		t.Fatal(err)
	}
	ct := encryptAll(t, key, iv, plaintext)

	// Cover block-aligned, mid-block, single-byte, and tail ranges.
	ranges := [][2]int64{
		{0, 100000},
		{0, 16},
		{1, 17},     // crosses a block boundary, unaligned start
		{15, 33},    // starts 1 before a boundary
		{16, 32},    // exactly one block, aligned
		{12345, 67890},
		{99999, 100000}, // last byte
		{65536, 65600},  // aligned start mid-file
	}
	for _, rg := range ranges {
		start, end := rg[0], rg[1]
		var out bytes.Buffer
		dw, err := DecryptingWriter(&out, key, iv, start)
		if err != nil {
			t.Fatalf("decrypting writer at %d: %v", start, err)
		}
		if _, err := dw.Write(ct[start:end]); err != nil {
			t.Fatalf("write range [%d,%d): %v", start, end, err)
		}
		if !bytes.Equal(out.Bytes(), plaintext[start:end]) {
			t.Fatalf("range [%d,%d) decrypt mismatch", start, end)
		}
	}
}

// TestSeekedRangeInChunks verifies decryption is correct when the ciphertext
// range arrives in several writes (as the parallel downloader delivers ordered
// parts), not one contiguous buffer.
func TestSeekedRangeInChunks(t *testing.T) {
	key, iv := mustKey(t), mustIV(t)
	plaintext := make([]byte, 50000)
	if _, err := rand.Read(plaintext); err != nil {
		t.Fatal(err)
	}
	ct := encryptAll(t, key, iv, plaintext)

	const start = int64(777) // unaligned
	end := int64(len(plaintext))
	var out bytes.Buffer
	dw, err := DecryptingWriter(&out, key, iv, start)
	if err != nil {
		t.Fatal(err)
	}
	// Feed in odd-sized chunks.
	for off := start; off < end; off += 333 {
		hi := off + 333
		if hi > end {
			hi = end
		}
		if _, err := dw.Write(ct[off:hi]); err != nil {
			t.Fatalf("chunk write: %v", err)
		}
	}
	if !bytes.Equal(out.Bytes(), plaintext[start:end]) {
		t.Fatal("chunked range decrypt mismatch")
	}
}

// TestDistinctIVsDiffer ensures two files with different IVs produce different
// ciphertext for identical plaintext (no IV reuse leakage).
func TestDistinctIVsDiffer(t *testing.T) {
	key := mustKey(t)
	iv1, iv2 := mustIV(t), mustIV(t)
	plaintext := bytes.Repeat([]byte{0xAB}, 4096)
	ct1 := encryptAll(t, key, iv1, plaintext)
	ct2 := encryptAll(t, key, iv2, plaintext)
	if bytes.Equal(ct1, ct2) {
		t.Fatal("identical ciphertext under different IVs")
	}
}

// TestAddUint64BE checks the 128-bit big-endian counter addition, including
// carry propagation across byte boundaries.
func TestAddUint64BE(t *testing.T) {
	cases := []struct {
		start []byte
		add   uint64
		want  []byte
	}{
		{
			start: []byte{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			add:   1,
			want:  []byte{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1},
		},
		{
			start: []byte{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0xFF},
			add:   1,
			want:  []byte{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 0},
		},
		{
			start: []byte{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
			add:   0x0102,
			want:  []byte{0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 1, 2},
		},
	}
	for i, c := range cases {
		buf := append([]byte(nil), c.start...)
		addUint64BE(buf, c.add)
		if !bytes.Equal(buf, c.want) {
			t.Fatalf("case %d: got %x want %x", i, buf, c.want)
		}
	}
}
