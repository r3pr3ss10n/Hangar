package api

import (
	"net/http"
	"strings"

	"github.com/google/uuid"

	"github.com/r3pr3ss10n/hangar/backend/internal/auth"
)

// handleListFolders lists the direct children (subfolders + files) of the
// parent_id query parameter (omitted or "root" = the owner's root).
func (s *Server) handleListFolders(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	parentID, ok := s.parseOptionalUUIDQuery(w, r, "parent_id")
	if !ok {
		return
	}

	folders, fileList, err := s.files.List(r.Context(), user.ID, parentID)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]any{
		"folders": newFolderViews(folders),
		"files":   newFileViews(fileList),
	})
}

// createFolderBody is the {parent_id?,name} body for folder creation.
type createFolderBody struct {
	ParentID *uuid.UUID `json:"parent_id"`
	Name     string     `json:"name"`
}

// handleCreateFolder creates a folder under the optional parent.
func (s *Server) handleCreateFolder(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	var body createFolderBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	body.Name = strings.TrimSpace(body.Name)
	if body.Name == "" {
		s.writeErr(w, http.StatusBadRequest, "name is required")
		return
	}

	folder, err := s.files.CreateFolder(r.Context(), user.ID, body.ParentID, body.Name)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusCreated, map[string]any{"folder": newFolderView(folder)})
}

// patchFolderBody is the {name?,parent_id?} body for rename/move. parent_id
// distinguishes "not present" (leave parent) from "present and null" (move to
// root) via the moveParent flag set during decode.
type patchFolderBody struct {
	Name       *string    `json:"name"`
	ParentID   *uuid.UUID `json:"parent_id"`
	moveParent bool
}

// handlePatchFolder renames and/or moves a folder. A 204 is returned on success.
func (s *Server) handlePatchFolder(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}

	body, ok := s.decodePatchFolder(w, r)
	if !ok {
		return
	}
	if body.Name == nil && !body.moveParent {
		s.writeErr(w, http.StatusBadRequest, "nothing to update")
		return
	}

	if body.Name != nil {
		name := strings.TrimSpace(*body.Name)
		if name == "" {
			s.writeErr(w, http.StatusBadRequest, "name cannot be empty")
			return
		}
		if err := s.files.RenameFolder(r.Context(), user.ID, id, name); err != nil {
			s.writeServiceErr(w, err)
			return
		}
	}
	if body.moveParent {
		if err := s.files.MoveFolder(r.Context(), user.ID, id, body.ParentID); err != nil {
			s.writeServiceErr(w, err)
			return
		}
	}
	w.WriteHeader(http.StatusNoContent)
}

// handleDeleteFolder deletes a folder.
func (s *Server) handleDeleteFolder(w http.ResponseWriter, r *http.Request) {
	user, ok := auth.UserFromContext(r.Context())
	if !ok {
		s.writeErr(w, http.StatusUnauthorized, "unauthorized")
		return
	}
	id, ok := s.parseUUIDParam(w, r, "id")
	if !ok {
		return
	}
	if err := s.files.DeleteFolder(r.Context(), user.ID, id); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}

// parseOptionalUUIDQuery parses an optional uuid query parameter. An empty value
// or the literal "root" means the root (nil). A malformed value yields a 400 and
// ok=false.
func (s *Server) parseOptionalUUIDQuery(w http.ResponseWriter, r *http.Request, name string) (*uuid.UUID, bool) {
	raw := strings.TrimSpace(r.URL.Query().Get(name))
	if raw == "" || raw == "root" {
		return nil, true
	}
	id, err := uuid.Parse(raw)
	if err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid "+name)
		return nil, false
	}
	return &id, true
}
