package api

import (
	"net/http"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
)

// grantedFolderView / grantedFileView are the recipient-facing projections shown in
// the "shared with me" view: the normal resource view plus the sharer's username.
type grantedFolderView struct {
	folderView
	OwnerUsername string `json:"owner_username"`
}
type grantedFileView struct {
	fileView
	OwnerUsername string `json:"owner_username"`
}

// handleListSharedRoots returns the folders and files shared directly with the
// caller — the top level of the "Shared with me" view.
func (s *Server) handleListSharedRoots(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	folders, fileList, err := s.files.ListSharedRoots(r.Context(), user.ID)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"folders": newGrantedFolderViews(folders),
		"files":   newGrantedFileViews(fileList),
	})
}

// handleListSharedChildren lists the children of a folder the caller can access
// via a grant on it or an ancestor (or by owning it).
func (s *Server) handleListSharedChildren(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	folders, fileList, err := s.files.ListSharedChildren(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"folders": newFolderViews(folders),
		"files":   newFileViews(fileList),
	})
}

func newGrantedFolderViews(fs []files.SharedFolder) []grantedFolderView {
	out := make([]grantedFolderView, 0, len(fs))
	for _, f := range fs {
		out = append(out, grantedFolderView{folderView: newFolderView(f.Folder), OwnerUsername: f.OwnerUsername})
	}
	return out
}

func newGrantedFileViews(fs []files.SharedFile) []grantedFileView {
	out := make([]grantedFileView, 0, len(fs))
	for _, f := range fs {
		out = append(out, grantedFileView{fileView: newFileView(f.File), OwnerUsername: f.OwnerUsername})
	}
	return out
}
