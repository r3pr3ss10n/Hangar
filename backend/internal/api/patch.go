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
	raw, ok := s.decodePatchFields(w, r, "name", "parent_id")
	if !ok {
		return patchFolderBody{}, false
	}

	var out patchFolderBody
	if !s.assignPatchName(w, raw, &out.Name) {
		return patchFolderBody{}, false
	}
	if parent, present := raw["parent_id"]; present {
		out.moveParent = true
		id, ok := s.parseNullableUUID(w, parent)
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
	raw, ok := s.decodePatchFields(w, r, "name", "folder_id")
	if !ok {
		return patchFileBody{}, false
	}

	var out patchFileBody
	if !s.assignPatchName(w, raw, &out.Name) {
		return patchFileBody{}, false
	}
	if folder, present := raw["folder_id"]; present {
		out.moveFolder = true
		id, ok := s.parseNullableUUID(w, folder)
		if !ok {
			return patchFileBody{}, false
		}
		out.FolderID = id
	}
	return out, true
}

// decodePatchFields reads a PATCH body into a key→raw map so that a present
// key set to null is distinguishable from an absent key — unlike pointer
// fields, where encoding/json collapses a JSON null to a nil pointer. Only the
// allowed keys may appear; anything else is rejected like DisallowUnknownFields.
func (s *Server) decodePatchFields(w http.ResponseWriter, r *http.Request, allowed ...string) (map[string]json.RawMessage, bool) {
	var raw map[string]json.RawMessage
	if err := json.NewDecoder(r.Body).Decode(&raw); err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid request body")
		return nil, false
	}
	for k := range raw {
		ok := false
		for _, a := range allowed {
			if k == a {
				ok = true
				break
			}
		}
		if !ok {
			s.writeErr(w, http.StatusBadRequest, "invalid request body")
			return nil, false
		}
	}
	return raw, true
}

// assignPatchName parses an optional "name" field. A present null is treated as
// absent (leaves dst nil), matching the previous *string decoding.
func (s *Server) assignPatchName(w http.ResponseWriter, raw map[string]json.RawMessage, dst **string) bool {
	name, present := raw["name"]
	if !present {
		return true
	}
	if err := json.Unmarshal(name, dst); err != nil {
		s.writeErr(w, http.StatusBadRequest, "invalid request body")
		return false
	}
	return true
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
