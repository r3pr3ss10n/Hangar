// Package telegram owns the single shared gotd client used as Hangar's storage
// backend. It implements the admin-driven link state machine (phone -> code ->
// optional 2FA), persists an AES-256-GCM encrypted MTProto session in the
// singleton telegram_account row, and exposes an upload/download proxy that
// streams file bytes in and out of a private "Hangar Storage" channel.
package telegram

import (
	"context"
	"errors"
	"io"

	"github.com/gotd/td/session"
)

// Status is the lifecycle state of the linked Telegram account.
type Status string

const (
	// StatusNotLinked means no account is linked yet.
	StatusNotLinked Status = "not_linked"
	// StatusLinking means a link flow is in progress (code/password pending).
	StatusLinking Status = "linking"
	// StatusLinked means an account is linked and the client is usable.
	StatusLinked Status = "linked"
)

// State is the user-visible snapshot of the Telegram integration.
type State struct {
	Status           Status `json:"status"`
	IsPremium        bool   `json:"is_premium"`
	Phone            string `json:"phone,omitempty"` // set while linking
	AwaitingCode     bool   `json:"awaiting_code,omitempty"`
	AwaitingPassword bool   `json:"awaiting_password,omitempty"` // 2FA needed
}

// FileRef is the re-download handle persisted on the files row.
type FileRef struct {
	MessageID     int64
	DocumentID    int64
	AccessHash    int64
	FileReference []byte
	DCID          int
}

// StoredFile is returned by Upload (superset of FileRef + Size).
type StoredFile struct {
	MessageID, DocumentID, AccessHash int64
	FileReference                     []byte
	DCID                              int
	Size                              int64
}

// Config holds the Telegram API credentials and the at-rest encryption key.
type Config struct {
	APIID         int
	APIHash       string
	EncryptionKey []byte // 32 bytes, AES-256
}

// RefreshFunc persists a refreshed download reference (the api layer wires this
// to UpdateFileReference).
type RefreshFunc func(ctx context.Context, ref FileRef) error

// Sentinel errors surfaced to callers and mapped to HTTP statuses by the api
// layer.
var (
	// ErrNotLinked is returned by storage operations when no account is linked.
	ErrNotLinked = errors.New("telegram: account not linked")
	// ErrLinkNotFound is returned when a linkID does not match an active session.
	ErrLinkNotFound = errors.New("telegram: link session not found")
	// ErrCodeInvalid is returned when the submitted login code is rejected.
	ErrCodeInvalid = errors.New("telegram: invalid login code")
	// ErrPasswordInvalid is returned when the submitted 2FA password is rejected.
	ErrPasswordInvalid = errors.New("telegram: invalid 2FA password")
	// ErrFileTooLarge is returned when an upload exceeds the account ceiling.
	ErrFileTooLarge = errors.New("telegram: file exceeds account limit")
)

// linkResult carries the account details captured once a sign-in completes.
type linkResult struct {
	tgUserID  int64
	isPremium bool
}

// channelRef identifies the storage channel.
type channelRef struct {
	id         int64
	accessHash int64
	dcID       int
}

// tgClient is the small unexported seam over gotd that the link state machine
// and storage proxy depend on. The real implementation (realClient) drives a
// live gotd client; tests use a fake to exercise the FSM without Telegram.
type tgClient interface {
	// sendCode requests a login code for phone and returns the phone_code_hash
	// that must be passed back to signIn.
	sendCode(ctx context.Context, phone string) (codeHash string, err error)
	// signIn submits the login code. It reports needPassword=true (mapping the
	// gotd auth.ErrPasswordAuthNeeded sentinel) when 2FA is required.
	signIn(ctx context.Context, phone, code, codeHash string) (needPassword bool, err error)
	// signInPassword completes a 2FA sign-in.
	signInPassword(ctx context.Context, password string) error
	// self reports the signed-in user (id + premium flag).
	self(ctx context.Context) (linkResult, error)
	// createStorageChannel creates the private "Hangar Storage" channel and
	// returns its id/access_hash/dc.
	createStorageChannel(ctx context.Context, title string) (channelRef, error)
	// uploadDocument streams exactly size bytes from r as a document into the
	// channel and returns the stored handle.
	uploadDocument(ctx context.Context, ch channelRef, name, mime string, size int64, r io.Reader) (StoredFile, error)
	// downloadRange streams bytes [offset, offset+limit) of the document to w
	// (limit<=0 = to EOF), refreshing the file reference from the channel
	// message on FILE_REFERENCE_EXPIRED and invoking refresh with the new ref.
	downloadRange(ctx context.Context, ch channelRef, ref FileRef, offset, limit int64, w io.Writer, refresh RefreshFunc) error
	// deleteMessage deletes the channel message (and thus the stored document
	// bytes) identified by messageID. Deleting an already-gone message is a no-op.
	deleteMessage(ctx context.Context, ch channelRef, messageID int) error
	// stop tears down the underlying running client.
	stop()
}

// clientFactory builds a tgClient. The Manager keeps a factory so the live
// gotd dependency stays behind the seam and can be swapped in tests. storage is
// the gotd session store the client reads/writes its MTProto auth key through:
// the durable pgSessionStorage for the persistent client, or a fresh in-memory
// store for a link attempt.
type clientFactory func(ctx context.Context, cfg Config, storage session.Storage) (tgClient, error)
