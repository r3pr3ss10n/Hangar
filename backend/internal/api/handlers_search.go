package api

import (
	"net/http"
	"strings"

	"github.com/google/uuid"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
)

// pathSegmentView is one ancestor folder on a search hit's path, root-first.
type pathSegmentView struct {
	ID   uuid.UUID `json:"id"`
	Name string    `json:"name"`
}

// folderSearchHit is a matched folder plus the path it lives under. The embedded
// folderView promotes the standard folder fields into the JSON object.
type folderSearchHit struct {
	folderView
	Path []pathSegmentView `json:"path"`
}

// fileSearchHit is a matched file plus the path it lives under.
type fileSearchHit struct {
	fileView
	Path []pathSegmentView `json:"path"`
}

// newPathView projects a service path into its public view.
func newPathView(segs []files.PathSegment) []pathSegmentView {
	out := make([]pathSegmentView, 0, len(segs))
	for _, s := range segs {
		out = append(out, pathSegmentView{ID: s.ID, Name: s.Name})
	}
	return out
}

// handleSearch runs a fuzzy search over the caller's folders and files and
// returns the ranked hits, each annotated with its folder path. The query is the
// `q` parameter; a blank query returns empty result sets (200, not an error).
func (s *Server) handleSearch(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	query := strings.TrimSpace(r.URL.Query().Get("q"))

	folderHits, fileHits, err := s.files.Search(r.Context(), user.ID, query)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}

	folders := make([]folderSearchHit, 0, len(folderHits))
	for _, h := range folderHits {
		folders = append(folders, folderSearchHit{
			folderView: newFolderView(h.Folder),
			Path:       newPathView(h.Path),
		})
	}
	fileResults := make([]fileSearchHit, 0, len(fileHits))
	for _, h := range fileHits {
		fileResults = append(fileResults, fileSearchHit{
			fileView: newFileView(h.File),
			Path:     newPathView(h.Path),
		})
	}

	s.writeJSON(w, http.StatusOK, map[string]any{
		"query":   query,
		"folders": folders,
		"files":   fileResults,
	})
}
