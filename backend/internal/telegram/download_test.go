package telegram

import (
	"bytes"
	"context"
	"errors"
	"io"
	"testing"
	"time"

	"github.com/stretchr/testify/require"
)

// TestStreamParts verifies the parallel fetch + ordered reassembly produces the
// exact requested byte range for a variety of offset/limit alignments, even when
// parts complete out of order.
func TestStreamParts(t *testing.T) {
	const total = 3*downloadChunk + 12345 // 3 full parts + a partial last part
	src := make([]byte, total)
	for i := range src {
		// Pattern depends on bits across the whole index, so any misordering or
		// off-by-one shift changes the bytes and is caught by the equality check.
		src[i] = byte(i ^ (i >> 8) ^ (i >> 16))
	}

	fetch := func(_ context.Context, part int64) ([]byte, error) {
		start := part * downloadChunk
		if start >= int64(len(src)) {
			return nil, nil
		}
		end := start + downloadChunk
		if end > int64(len(src)) {
			end = int64(len(src))
		}
		// Scramble completion order: lower parts sleep longer, so higher parts
		// tend to finish first and must be buffered until their turn.
		time.Sleep(time.Duration(5-part%5) * time.Millisecond)
		out := make([]byte, end-start)
		copy(out, src[start:end])
		return out, nil
	}

	cases := []struct {
		name          string
		offset, limit int64
	}{
		{"whole file", 0, total},
		{"first bytes of part 0", 0, 100},
		{"mid-part across a boundary", downloadChunk - 500, 1500},
		{"single inner part exact", downloadChunk, downloadChunk},
		{"unaligned offset spanning 3 parts", 1234, 2*downloadChunk + 1000},
		{"tail into partial last part", 3*downloadChunk - 10, 10 + 12345},
		{"final single byte", total - 1, 1},
	}
	for _, tc := range cases {
		t.Run(tc.name, func(t *testing.T) {
			var buf bytes.Buffer
			err := streamParts(context.Background(), fetch, tc.offset, tc.limit, &buf)
			require.NoError(t, err)
			require.Equal(t, src[tc.offset:tc.offset+tc.limit], buf.Bytes())
		})
	}
}

// TestStreamParts_FetchErrorPropagates ensures a failing part fetch aborts the
// whole download with that error rather than hanging.
func TestStreamParts_FetchErrorPropagates(t *testing.T) {
	wantErr := errors.New("boom")
	fetch := func(_ context.Context, part int64) ([]byte, error) {
		if part == 2 {
			return nil, wantErr
		}
		return make([]byte, downloadChunk), nil
	}
	err := streamParts(context.Background(), fetch, 0, 6*downloadChunk, io.Discard)
	require.ErrorIs(t, err, wantErr)
}
