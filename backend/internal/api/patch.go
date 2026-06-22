package api

import (
	"encoding/json"
	"net/http"

	"github.com/google/uuid"
)

// decodePatchFolder decodes a PATCH /folders body, distinguishing an absent
// parent_id (leave the parent unchanged) from a present-but-null parent_id
// (move to root). The moveParent flag records whether the key was present.
func (s *Server) decodePatchFolder(w http.ResponseWriter, r *http.Request) (patchFolderBody, bool) {
	var raw struct {
		Name     *string          `json:"name"`
		ParentID *json.RawMessage `json:"parent_id"`
	}
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&raw); err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid request body")
		return patchFolderBody{}, false
	}

	out := patchFolderBody{Name: raw.Name}
	if raw.ParentID != nil {
		out.moveParent = true
		id, ok := s.parseNullableUUID(w, *raw.ParentID)
		if !ok {
			return patchFolderBody{}, false
		}
		out.ParentID = id
	}
	return out, true
}

// decodePatchFile decodes a PATCH /files body, distinguishing an absent
// folder_id (leave the folder unchanged) from a present-but-null folder_id
// (move to root). The moveFolder flag records whether the key was present.
func (s *Server) decodePatchFile(w http.ResponseWriter, r *http.Request) (patchFileBody, bool) {
	var raw struct {
		Name     *string          `json:"name"`
		FolderID *json.RawMessage `json:"folder_id"`
	}
	dec := json.NewDecoder(r.Body)
	dec.DisallowUnknownFields()
	if err := dec.Decode(&raw); err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid request body")
		return patchFileBody{}, false
	}

	out := patchFileBody{Name: raw.Name}
	if raw.FolderID != nil {
		out.moveFolder = true
		id, ok := s.parseNullableUUID(w, *raw.FolderID)
		if !ok {
			return patchFileBody{}, false
		}
		out.FolderID = id
	}
	return out, true
}

// parseNullableUUID parses a raw JSON value that is either null (→ nil, move to
// root) or a uuid string. A malformed value yields a 400 and ok=false.
func (s *Server) parseNullableUUID(w http.ResponseWriter, raw json.RawMessage) (*uuid.UUID, bool) {
	if string(raw) == "null" {
		return nil, true
	}
	var str string
	if err := json.Unmarshal(raw, &str); err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid id")
		return nil, false
	}
	id, err := uuid.Parse(str)
	if err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid id")
		return nil, false
	}
	return &id, true
}
