package api

import (
	"net/http"
	"strings"
)

// handleTelegramStatus reports the current Telegram integration state.
func (s *Server) handleTelegramStatus(w http.ResponseWriter, r *http.Request) {
	state, err := s.telegram.Status(r.Context())
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, state)
}

// linkStartBody is the {phone} body that begins a link attempt.
type linkStartBody struct {
	Phone string `json:"phone"`
}

// handleLinkStart sends a login code to the given phone and returns the link id.
func (s *Server) handleLinkStart(w http.ResponseWriter, r *http.Request) {
	var body linkStartBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	body.Phone = strings.TrimSpace(body.Phone)
	if body.Phone == "" {
		s.writeErr(w, http.StatusBadRequest, "phone is required")
		return
	}
	linkID, err := s.telegram.StartLink(r.Context(), body.Phone)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]string{"link_id": linkID})
}

// linkCodeBody is the {link_id,code} body that submits a login code.
type linkCodeBody struct {
	LinkID string `json:"link_id"`
	Code   string `json:"code"`
}

// handleLinkCode submits the login code. It reports need_password=true when the
// account has 2FA enabled.
func (s *Server) handleLinkCode(w http.ResponseWriter, r *http.Request) {
	var body linkCodeBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if strings.TrimSpace(body.LinkID) == "" || strings.TrimSpace(body.Code) == "" {
		s.writeErr(w, http.StatusBadRequest, "link_id and code are required")
		return
	}
	needPassword, err := s.telegram.SubmitCode(r.Context(), body.LinkID, body.Code)
	if err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]bool{"need_password": needPassword})
}

// linkPasswordBody is the {link_id,password} body that completes a 2FA sign-in.
type linkPasswordBody struct {
	LinkID   string `json:"link_id"`
	Password string `json:"password"`
}

// handleLinkPassword completes a 2FA sign-in.
func (s *Server) handleLinkPassword(w http.ResponseWriter, r *http.Request) {
	var body linkPasswordBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if strings.TrimSpace(body.LinkID) == "" || body.Password == "" {
		s.writeErr(w, http.StatusBadRequest, "link_id and password are required")
		return
	}
	if err := s.telegram.SubmitPassword(r.Context(), body.LinkID, body.Password); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	s.writeJSON(w, http.StatusOK, map[string]string{"status": "linked"})
}

// linkCancelBody is the {link_id} body that aborts a link attempt.
type linkCancelBody struct {
	LinkID string `json:"link_id"`
}

// handleLinkCancel aborts an in-flight link attempt.
func (s *Server) handleLinkCancel(w http.ResponseWriter, r *http.Request) {
	var body linkCancelBody
	if !s.decodeJSON(w, r, &body) {
		return
	}
	if strings.TrimSpace(body.LinkID) == "" {
		s.writeErr(w, http.StatusBadRequest, "link_id is required")
		return
	}
	s.telegram.CancelLink(body.LinkID)
	w.WriteHeader(http.StatusNoContent)
}

// handleUnlink signs out and resets the linked account.
func (s *Server) handleUnlink(w http.ResponseWriter, r *http.Request) {
	if err := s.telegram.Unlink(r.Context()); err != nil {
		s.writeServiceErr(w, err)
		return
	}
	w.WriteHeader(http.StatusNoContent)
}
