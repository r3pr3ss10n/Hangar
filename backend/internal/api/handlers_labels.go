package api

import (
	"net/http"
	"strings"

	"github.com/google/uuid"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
	"github.com/r3pr3ss10n/hangar/backend/internal/files"
)

// tagView is the public projection of a tag (with its item count).
type tagView struct {
	ID        uuid.UUID `json:"id"`
	Name      string    `json:"name"`
	Color     string    `json:"color"`
	ItemCount int64     `json:"item_count"`
}

func newTagViews(ts []files.TagWithCount) []tagView {
	out := make([]tagView, 0, len(ts))
	for _, t := range ts {
		out = append(out, tagView{ID: t.Tag.ID, Name: t.Tag.Name, Color: t.Tag.Color, ItemCount: t.ItemCount})
	}
	return out
}

// handleGetLabels returns the per-user bundle the UI uses to render stars and
// tag badges across listings.
func (s *Server) handleGetLabels(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	data, err := s.files.Labels(r.Context(), user.ID)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}

	fileTags := map[string][]string{}
	folderTags := map[string][]string{}
	for _, a := range data.Assignments {
		if a.FileID != nil {
			fileTags[a.FileID.String()] = append(fileTags[a.FileID.String()], a.TagID.String())
		} else if a.FolderID != nil {
			folderTags[a.FolderID.String()] = append(folderTags[a.FolderID.String()], a.TagID.String())
		}
	}

	s.writeJSON(w, http.StatusOK, map[string]any{
		"tags":        newTagViews(data.Tags),
		"file_tags":   fileTags,
		"folder_tags": folderTags,
	})
}

// ---- tags ----

type tagBody struct {
	Name  string `json:"name"`
	Color string `json:"color"`
}

func (s *Server) handleListTags(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	tags, err := s.files.ListTags(r.Context(), user.ID)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{"tags": newTagViews(tags)})
}

func (s *Server) handleCreateTag(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	var body tagBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	name := strings.TrimSpace(body.Name)
	if name == "" {
		s.writeErr(w, http.StatusBadRequest, "name cannot be empty")
		return
	}
	color := body.Color
	if color == "" {
		color = "slate"
	}
	tag, err := s.files.CreateTag(r.Context(), user.ID, name, color)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusCreated, tagView{ID: tag.ID, Name: tag.Name, Color: tag.Color})
}

func (s *Server) handleUpdateTag(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	var body tagBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	name := strings.TrimSpace(body.Name)
	if name == "" {
		s.writeErr(w, http.StatusBadRequest, "name cannot be empty")
		return
	}
	color := body.Color
	if color == "" {
		color = "slate"
	}
	if err := s.files.UpdateTag(r.Context(), user.ID, id, name, color); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleDeleteTag(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	if err := s.files.DeleteTag(r.Context(), user.ID, id); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleListTagItems returns the folders and files carrying a tag.
func (s *Server) handleListTagItems(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	folders, fileList, err := s.files.ListTagItems(r.Context(), user.ID, id)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"folders": newFolderViews(folders),
		"files":   newFileViews(fileList),
	})
}

// ---- tag assignment ----

type assignTagBody struct {
	TagID uuid.UUID `json:"tag_id"`
}

func (s *Server) handleAddFileTag(w http.ResponseWriter, r *http.Request) {
	s.assignTag(w, r, "file", true)
}
func (s *Server) handleAddFolderTag(w http.ResponseWriter, r *http.Request) {
	s.assignTag(w, r, "folder", true)
}

func (s *Server) assignTag(w http.ResponseWriter, r *http.Request, kind string, on bool) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	var body assignTagBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if body.TagID == uuid.Nil {
		s.writeErr(w, http.StatusBadRequest, "tag_id is required")
		return
	}
	var err error
	if kind == "folder" {
		err = s.files.AssignFolderTag(r.Context(), user.ID, id, body.TagID, on)
	} else {
		err = s.files.AssignFileTag(r.Context(), user.ID, id, body.TagID, on)
	}
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

func (s *Server) handleRemoveFileTag(w http.ResponseWriter, r *http.Request) {
	s.removeTag(w, r, "file")
}
func (s *Server) handleRemoveFolderTag(w http.ResponseWriter, r *http.Request) {
	s.removeTag(w, r, "folder")
}

func (s *Server) removeTag(w http.ResponseWriter, r *http.Request, kind string) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	tagID, ok := s.parseUUIDParam(w, r, "tagId")
	if !ok {
		return
	}
	var err error
	if kind == "folder" {
		err = s.files.AssignFolderTag(r.Context(), user.ID, id, tagID, false)
	} else {
		err = s.files.AssignFileTag(r.Context(), user.ID, id, tagID, false)
	}
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
