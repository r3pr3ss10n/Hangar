package telegram

import (
	"context"
	"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"log/slog"
	"sync"
	"time"

	"github.com/r3pr3ss10n/hangar/backend/internal/config"
	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

// storageChannelTitle is the name of the private channel that holds all bytes.
const storageChannelTitle = "Hangar Storage"

// accountStore is the subset of dbsqlc.Queries the Manager depends on. Keeping
// it as an interface lets the link state machine be unit-tested with a fake
// store, no live Postgres required. *dbsqlc.Queries satisfies it.
type accountStore interface {
	GetTelegramAccount(ctx context.Context) (dbsqlc.TelegramAccount, error)
	GetTelegramSessionBlob(ctx context.Context) ([]byte, error)
	SetTelegramSessionBlob(ctx context.Context, sessionBlobEncrypted []byte) error
	SetTelegramLinked(ctx context.Context, arg dbsqlc.SetTelegramLinkedParams) error
	ResetTelegramAccount(ctx context.Context) error
}

// linkSession is the in-flight state of a single admin link attempt. Each one
// owns its own running unauthorized client; the phone_code_hash returned by
// SendCode is bound to that client, so the SAME client must serve
// StartLink -> SubmitCode -> SubmitPassword.
type linkSession struct {
	phone        string
	codeHash     string
	client       tgClient
	awaitingPass bool
	// memStore holds the link client's MTProto session in memory only. On a
	// successful link its bytes are flushed to the durable pgSessionStorage; if
	// the attempt is abandoned it is simply discarded, leaving any
	// already-linked account's persisted session untouched.
	memStore *memSessionStorage
	// startedAt stamps when the attempt began so stale sessions can be reaped.
	startedAt time.Time
}

// Manager is the single shared gotd client plus the link state machine and the
// storage proxy. It is safe for concurrent use.
type Manager struct {
	q      accountStore
	cfg    Config
	logger *slog.Logger

	// newClient builds a tgClient. Swappable for tests.
	newClient clientFactory

	mu sync.Mutex
	// client is the persistent, authorized client once an account is linked.
	client tgClient
	// channel is the storage channel handle for the linked account.
	channel channelRef
	// premium mirrors the linked account's premium flag (governs MaxFileBytes).
	premium bool
	// links holds in-flight link attempts keyed by a random linkID.
	links map[string]*linkSession
}

// NewManager constructs a Manager over the given queries, config and logger.
func NewManager(q *dbsqlc.Queries, cfg Config, logger *slog.Logger) *Manager {
	return &Manager{
		q:         q,
		cfg:       cfg,
		logger:    logger,
		newClient: newRealClient,
		links:     make(map[string]*linkSession),
	}
}

// newStorage builds the encrypted session.Storage for this manager.
func (m *Manager) newStorage() (*pgSessionStorage, error) {
	return newPGSessionStorage(m.q, m.cfg.EncryptionKey)
}

// Start connects the persistent client in the background if an account is
// already linked. It is non-blocking: it launches the connection attempt and
// returns. If no account is linked it is a no-op.
func (m *Manager) Start(ctx context.Context) error {
	acct, err := m.q.GetTelegramAccount(ctx)
	if err != nil {
		return fmt.Errorf("get telegram account: %w", err)
	}
	if acct.Status != string(StatusLinked) || acct.ChannelID == nil || acct.ChannelAccessHash == nil {
		m.logger.Info("telegram: no linked account, skipping client start")
		return nil
	}

	ch := channelRef{id: *acct.ChannelID, accessHash: *acct.ChannelAccessHash}
	if acct.DcID != nil {
		ch.dcID = int(*acct.DcID)
	}
	premium := acct.IsPremium

	storage, err := m.newStorage()
	if err != nil {
		return fmt.Errorf("build session storage: %w", err)
	}

	// Launch the connection attempt in the background so Start does not block.
	go func() {
		// Use the background context: the client must outlive the Start call.
		client, err := m.newClient(context.Background(), m.cfg, storage)
		if err != nil {
			m.logger.Error("telegram: failed to start persistent client", "error", err)
			return
		}
		m.mu.Lock()
		m.client = client
		m.channel = ch
		m.premium = premium
		m.mu.Unlock()
		m.logger.Info("telegram: persistent client started", "channel_id", ch.id)
	}()
	return nil
}

// Stop tears down every running client (the persistent one and any in-flight
// link clients). Called on shutdown.
func (m *Manager) Stop() {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.client != nil {
		m.client.stop()
		m.client = nil
	}
	for id, ls := range m.links {
		if ls.client != nil {
			ls.client.stop()
		}
		delete(m.links, id)
	}
}

// Status reports the current integration state from the singleton row plus any
// in-flight link attempt.
func (m *Manager) Status(ctx context.Context) (State, error) {
	acct, err := m.q.GetTelegramAccount(ctx)
	if err != nil {
		return State{}, fmt.Errorf("get telegram account: %w", err)
	}

	st := State{
		Status:    Status(acct.Status),
		IsPremium: acct.IsPremium,
	}
	if st.Status == "" {
		st.Status = StatusNotLinked
	}

	// Reflect an in-flight link attempt (if any) as "linking".
	m.mu.Lock()
	for _, ls := range m.links {
		st.Status = StatusLinking
		st.Phone = ls.phone
		st.AwaitingPassword = ls.awaitingPass
		st.AwaitingCode = !ls.awaitingPass
		break
	}
	m.mu.Unlock()

	return st, nil
}

// MaxFileBytes is the per-file ceiling for the linked account (2 or 4 GiB).
func (m *Manager) MaxFileBytes() int64 {
	m.mu.Lock()
	premium := m.premium
	m.mu.Unlock()
	if premium {
		return config.MaxFileBytesPremium
	}
	return config.MaxFileBytesStandard
}

// --- link state machine ---

// StartLink dials an unauthorized client, keeps it running, and sends a login
// code. The returned linkID identifies this attempt for the follow-up calls.
func (m *Manager) StartLink(ctx context.Context, phone string) (string, error) {
	// A fresh link attempt runs against an empty in-memory session so the
	// SendCode/SignIn flow always executes. The persisted blob of any
	// currently-linked account is left untouched: if this attempt is abandoned,
	// the live account survives a restart. The session is promoted to durable
	// storage only on success (see completeLink).
	memStore := &memSessionStorage{}

	client, err := m.newClient(ctx, m.cfg, memStore)
	if err != nil {
		return "", fmt.Errorf("start link client: %w", err)
	}

	codeHash, err := client.sendCode(ctx, phone)
	if err != nil {
		client.stop()
		return "", fmt.Errorf("send code: %w", err)
	}

	linkID, err := newLinkID()
	if err != nil {
		client.stop()
		return "", fmt.Errorf("generate link id: %w", err)
	}

	m.mu.Lock()
	m.links[linkID] = &linkSession{
		phone:     phone,
		codeHash:  codeHash,
		client:    client,
		memStore:  memStore,
		startedAt: time.Now(),
	}
	m.mu.Unlock()

	m.logger.Info("telegram: link started", "link_id", linkID)
	return linkID, nil
}

// SubmitCode submits the login code. With no 2FA it completes the link and
// returns needPassword=false; with 2FA it returns needPassword=true and waits
// for SubmitPassword.
func (m *Manager) SubmitCode(ctx context.Context, linkID, code string) (bool, error) {
	m.mu.Lock()
	ls, ok := m.links[linkID]
	m.mu.Unlock()
	if !ok {
		return false, ErrLinkNotFound
	}

	needPassword, err := ls.client.signIn(ctx, ls.phone, code, ls.codeHash)
	if err != nil {
		return false, fmt.Errorf("sign in: %w", err)
	}
	if needPassword {
		m.mu.Lock()
		ls.awaitingPass = true
		m.mu.Unlock()
		return true, nil
	}

	if err := m.completeLink(ctx, linkID, ls); err != nil {
		return false, err
	}
	return false, nil
}

// SubmitPassword completes a 2FA sign-in, persists the session and creates the
// channel.
func (m *Manager) SubmitPassword(ctx context.Context, linkID, password string) error {
	m.mu.Lock()
	ls, ok := m.links[linkID]
	m.mu.Unlock()
	if !ok {
		return ErrLinkNotFound
	}

	if err := ls.client.signInPassword(ctx, password); err != nil {
		return fmt.Errorf("sign in password: %w", err)
	}
	return m.completeLink(ctx, linkID, ls)
}

// completeLink finishes an authorized sign-in: it reads self, creates the
// storage channel, flushes the now-authorized in-memory session to durable
// encrypted storage, persists the linked state, and promotes the client to the
// persistent client. On any failure before promotion it tears down the link
// client so it does not leak.
func (m *Manager) completeLink(ctx context.Context, linkID string, ls *linkSession) error {
	res, err := ls.client.self(ctx)
	if err != nil {
		m.discardLink(linkID, ls)
		return fmt.Errorf("read self: %w", err)
	}

	ch, err := ls.client.createStorageChannel(ctx, storageChannelTitle)
	if err != nil {
		m.discardLink(linkID, ls)
		return fmt.Errorf("create storage channel: %w", err)
	}

	// Promote the link client's in-memory session to the durable, encrypted
	// store so it survives a restart. Until this point the persisted blob still
	// belongs to the previously-linked account (if any).
	if ls.memStore != nil {
		storage, serr := m.newStorage()
		if serr != nil {
			m.discardLink(linkID, ls)
			return fmt.Errorf("build session storage: %w", serr)
		}
		if serr := storage.StoreSession(ctx, ls.memStore.snapshot()); serr != nil {
			m.discardLink(linkID, ls)
			return fmt.Errorf("persist session blob: %w", serr)
		}
	}

	userHash := hashUserID(res.tgUserID)
	channelID := ch.id
	channelHash := ch.accessHash
	dcID := int32(ch.dcID)
	if err := m.q.SetTelegramLinked(ctx, dbsqlc.SetTelegramLinkedParams{
		TgUserIDHash:      &userHash,
		ChannelID:         &channelID,
		ChannelAccessHash: &channelHash,
		DcID:              &dcID,
		IsPremium:         res.isPremium,
	}); err != nil {
		m.discardLink(linkID, ls)
		return fmt.Errorf("persist linked state: %w", err)
	}

	// Promote the authorized client to the persistent client and retire any
	// previous one.
	m.mu.Lock()
	old := m.client
	m.client = ls.client
	m.channel = ch
	m.premium = res.isPremium
	delete(m.links, linkID)
	m.mu.Unlock()

	if old != nil {
		old.stop()
	}

	m.logger.Info("telegram: account linked", "channel_id", ch.id, "premium", res.isPremium)
	return nil
}

// discardLink removes a link attempt from the registry and stops its client. It
// is the failure-path teardown for completeLink so an abandoned or half-failed
// attempt does not leak a running client.
func (m *Manager) discardLink(linkID string, ls *linkSession) {
	m.mu.Lock()
	delete(m.links, linkID)
	m.mu.Unlock()
	if ls.client != nil {
		ls.client.stop()
	}
}

// CancelLink aborts an in-flight link attempt and tears down its client.
func (m *Manager) CancelLink(linkID string) {
	m.mu.Lock()
	ls, ok := m.links[linkID]
	if ok {
		delete(m.links, linkID)
	}
	m.mu.Unlock()
	if ok && ls.client != nil {
		ls.client.stop()
	}
}

// Unlink signs out and resets the singleton row.
func (m *Manager) Unlink(ctx context.Context) error {
	m.mu.Lock()
	client := m.client
	m.client = nil
	m.channel = channelRef{}
	m.premium = false
	m.mu.Unlock()

	if client != nil {
		client.stop()
	}

	if err := m.q.ResetTelegramAccount(ctx); err != nil {
		return fmt.Errorf("reset telegram account: %w", err)
	}
	m.logger.Info("telegram: account unlinked")
	return nil
}

// linkedClient returns the persistent client and channel, or ErrNotLinked.
func (m *Manager) linkedClient() (tgClient, channelRef, error) {
	m.mu.Lock()
	defer m.mu.Unlock()
	if m.client == nil {
		return nil, channelRef{}, ErrNotLinked
	}
	return m.client, m.channel, nil
}

// hashUserID returns the hex-encoded SHA-256 of the decimal user id, used as the
// non-reversible tg_user_id_hash.
func hashUserID(id int64) string {
	sum := sha256.Sum256([]byte(fmt.Sprintf("%d", id)))
	return hex.EncodeToString(sum[:])
}

// newLinkID returns a random, URL-safe-ish hex link identifier.
func newLinkID() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return hex.EncodeToString(b), nil
}
