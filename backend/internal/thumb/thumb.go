// Package thumb generates small JPEG preview thumbnails from raster image bytes.
// It is pure (bytes in, bytes out) so the api layer can call it inline on upload
// and it can be unit-tested without any I/O. Unsupported media yields
// ErrUnsupported; callers treat that (and any decode failure) as "no thumbnail"
// rather than a fatal error — a file without a thumbnail still works fully.
package thumb

import (
	"bytes"
	"errors"
	"fmt"
	"image"
	"image/jpeg"
	"strings"

	xdraw "golang.org/x/image/draw"

	// Register the decoders image.Decode dispatches to. jpeg is also used
	// directly for encoding; png/gif/webp are imported solely for their
	// format-registration init.
	_ "image/gif"
	_ "image/png"
	_ "golang.org/x/image/webp"
)

const (
	// MaxDimension caps the longest side of the generated thumbnail in pixels.
	MaxDimension = 400
	// jpegQuality balances thumbnail sharpness against stored size (~15-35 KB).
	jpegQuality = 80
	// maxSourcePixels rejects absurdly large images before decoding blows up
	// memory (a decoded pixel is 4+ bytes; 40 MP ≈ 160 MB).
	maxSourcePixels = 40_000_000
)

// ErrUnsupported is returned for media types Generate cannot thumbnail.
var ErrUnsupported = errors.New("thumb: unsupported media type")

// supportedMIMEs are the raster image types we can decode and downscale.
var supportedMIMEs = map[string]bool{
	"image/jpeg": true,
	"image/jpg":  true,
	"image/png":  true,
	"image/gif":  true,
	"image/webp": true,
}

// Supported reports whether Generate can build a thumbnail for the given mime
// type. Callers use it to decide whether to buffer the source bytes at all.
func Supported(mime string) bool {
	return supportedMIMEs[normalizeMIME(mime)]
}

// normalizeMIME lower-cases the type and strips any ";charset=..." parameters.
func normalizeMIME(mime string) string {
	mime = strings.ToLower(strings.TrimSpace(mime))
	if i := strings.IndexByte(mime, ';'); i >= 0 {
		mime = strings.TrimSpace(mime[:i])
	}
	return mime
}

// Generate decodes data as an image and returns a JPEG thumbnail whose longest
// side is at most MaxDimension, preserving aspect ratio and never upscaling. It
// returns ErrUnsupported for non-image mimes and a wrapped error if the bytes
// cannot be decoded.
func Generate(data []byte, mime string) ([]byte, error) {
	if !Supported(mime) {
		return nil, ErrUnsupported
	}

	// Guard against pathologically large images before fully decoding.
	cfg, _, err := image.DecodeConfig(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode config: %w", err)
	}
	if cfg.Width <= 0 || cfg.Height <= 0 {
		return nil, fmt.Errorf("decode config: empty image")
	}
	if int64(cfg.Width)*int64(cfg.Height) > maxSourcePixels {
		return nil, fmt.Errorf("image too large: %dx%d", cfg.Width, cfg.Height)
	}

	src, _, err := image.Decode(bytes.NewReader(data))
	if err != nil {
		return nil, fmt.Errorf("decode image: %w", err)
	}

	dst := scaled(src)

	var buf bytes.Buffer
	if err := jpeg.Encode(&buf, dst, &jpeg.Options{Quality: jpegQuality}); err != nil {
		return nil, fmt.Errorf("encode jpeg: %w", err)
	}
	return buf.Bytes(), nil
}

// scaled returns src resized so its longest side is at most MaxDimension. Images
// already within bounds are returned unchanged (no upscaling). High-quality
// Catmull-Rom resampling keeps thumbnails crisp.
func scaled(src image.Image) image.Image {
	b := src.Bounds()
	w, h := b.Dx(), b.Dy()
	tw, th := fitWithin(w, h, MaxDimension)
	if tw == w && th == h {
		return src
	}
	dst := image.NewRGBA(image.Rect(0, 0, tw, th))
	xdraw.CatmullRom.Scale(dst, dst.Bounds(), src, b, xdraw.Over, nil)
	return dst
}

// fitWithin scales (w,h) down so the longest side is at most max, preserving
// aspect ratio. It never enlarges and never returns a zero dimension.
func fitWithin(w, h, max int) (int, int) {
	if w <= max && h <= max {
		return w, h
	}
	if w >= h {
		nh := h * max / w
		if nh < 1 {
			nh = 1
		}
		return max, nh
	}
	nw := w * max / h
	if nw < 1 {
		nw = 1
	}
	return nw, max
}
