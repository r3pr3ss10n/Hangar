package thumb

import (
	"bytes"
	"image"
	"image/color"
	"image/png"
	"testing"

	"github.com/stretchr/testify/assert"
	"github.com/stretchr/testify/require"
)

// pngBytes builds a w×h PNG with a simple gradient so it is a real, decodable image.
func pngBytes(t *testing.T, w, h int) []byte {
	t.Helper()
	img := image.NewRGBA(image.Rect(0, 0, w, h))
	for y := 0; y < h; y++ {
		for x := 0; x < w; x++ {
			img.Set(x, y, color.RGBA{R: uint8(x % 256), G: uint8(y % 256), B: 128, A: 255})
		}
	}
	var buf bytes.Buffer
	require.NoError(t, png.Encode(&buf, img))
	return buf.Bytes()
}

func TestSupported(t *testing.T) {
	assert.True(t, Supported("image/png"))
	assert.True(t, Supported("image/jpeg"))
	assert.True(t, Supported("image/webp"))
	assert.True(t, Supported("IMAGE/PNG"), "mime match is case-insensitive")
	assert.True(t, Supported("image/jpeg; charset=binary"), "parameters are ignored")
	assert.False(t, Supported("application/pdf"))
	assert.False(t, Supported("video/mp4"))
	assert.False(t, Supported(""))
}

func TestGenerateUnsupported(t *testing.T) {
	_, err := Generate([]byte("whatever"), "application/pdf")
	assert.ErrorIs(t, err, ErrUnsupported)
}

func TestGenerateDownscalesLandscape(t *testing.T) {
	out, err := Generate(pngBytes(t, 1000, 500), "image/png")
	require.NoError(t, err)
	require.NotEmpty(t, out)

	cfg, format, err := image.DecodeConfig(bytes.NewReader(out))
	require.NoError(t, err)
	assert.Equal(t, "jpeg", format, "thumbnails are always JPEG")
	// Longest side clamped to MaxDimension, aspect ratio (2:1) preserved.
	assert.Equal(t, MaxDimension, cfg.Width)
	assert.Equal(t, MaxDimension/2, cfg.Height)
}

func TestGenerateDownscalesPortrait(t *testing.T) {
	out, err := Generate(pngBytes(t, 500, 1000), "image/png")
	require.NoError(t, err)

	cfg, _, err := image.DecodeConfig(bytes.NewReader(out))
	require.NoError(t, err)
	assert.Equal(t, MaxDimension, cfg.Height)
	assert.Equal(t, MaxDimension/2, cfg.Width)
}

func TestGenerateDoesNotUpscale(t *testing.T) {
	out, err := Generate(pngBytes(t, 120, 80), "image/png")
	require.NoError(t, err)

	cfg, _, err := image.DecodeConfig(bytes.NewReader(out))
	require.NoError(t, err)
	assert.Equal(t, 120, cfg.Width, "small images keep their dimensions")
	assert.Equal(t, 80, cfg.Height)
}

func TestGenerateRejectsGarbage(t *testing.T) {
	_, err := Generate([]byte("not an image"), "image/png")
	assert.Error(t, err)
	assert.NotErrorIs(t, err, ErrUnsupported, "garbage is a decode error, not unsupported")
}

func TestFitWithin(t *testing.T) {
	w, h := fitWithin(1000, 500, 400)
	assert.Equal(t, 400, w)
	assert.Equal(t, 200, h)

	w, h = fitWithin(50, 50, 400)
	assert.Equal(t, 50, w, "no upscaling")
	assert.Equal(t, 50, h)

	// Extreme aspect ratios never collapse to a zero dimension.
	w, h = fitWithin(4000, 3, 400)
	assert.Equal(t, 400, w)
	assert.GreaterOrEqual(t, h, 1)
}
