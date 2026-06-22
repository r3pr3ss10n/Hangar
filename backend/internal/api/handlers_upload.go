package api

import (
	"github.com/r3pr3ss10n/hangar/backend/internal/thumb"
)

// maxThumbSourceBytes caps how large an image we will buffer in memory while
// generating a thumbnail. Larger images are stored with no thumbnail (the file
// still works; it just shows a type icon). Shared by the tus upload path.
const maxThumbSourceBytes = 30 << 20 // 30 MiB

// makeThumb best-effort generates a JPEG thumbnail from source image bytes. No
// data (non-image or oversized upload) or any generation failure yields a nil
// thumbnail and is logged at debug level — a missing thumbnail never fails the
// upload.
func (s *Server) makeThumb(data []byte, mime, name string) []byte {
	if len(data) == 0 {
		return nil
	}
	out, err := thumb.Generate(data, mime)
	if err != nil {
		s.logger.Debug("api: thumbnail generation skipped", "error", err, "name", name, "mime", mime)
		return nil
	}
	return out
}
