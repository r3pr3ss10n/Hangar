package telegram

import (
	"context"
	"io"
	"log/slog"
	"sync"
	"testing"

	"github.com/stretchr/testify/require"

	"github.com/gotd/td/session"

	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
)

// fakeClient is an in-memory tgClient used to drive the link state machine in
// unit tests without touching Telegram.
type fakeClient struct {
	mu sync.Mutex

	wantCode     string // the code that signIn accepts
	needPassword bool   // whether signIn reports 2FA is required
	wantPassword string // the password that signInPassword accepts

	userID    int64
	premium   bool
	channel   channelRef
	stopped   bool
	signedIn  bool
	passwordD bool
	deleted   []int // message ids passed to deleteMessage
}

func (f *fakeClient) sendCode(_ context.Context, _ string) (string, error) {
	return "code-hash", nil
}

func (f *fakeClient) signIn(_ context.Context, _, code, _ string) (bool, error) {
	f.mu.Lock()
	defer f.mu.Unlock()
	if code != f.wantCode {
		return false, ErrCodeInvalid
	}
	if f.needPassword {
		return true, nil
	}
	f.signedIn = true
	return false, nil
}

func (f *fakeClient) signInPassword(_ context.Context, password string) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	if password != f.wantPassword {
		return ErrPasswordInvalid
	}
	f.passwordD = true
	f.signedIn = true
	return nil
}

func (f *fakeClient) self(_ context.Context) (linkResult, error) {
	return linkResult{tgUserID: f.userID, isPremium: f.premium}, nil
}

func (f *fakeClient) createStorageChannel(_ context.Context, _ string) (channelRef, error) {
	return f.channel, nil
}

func (f *fakeClient) uploadDocument(_ context.Context, _ channelRef, _, _ string, size int64, _ io.Reader) (StoredFile, error) {
	return StoredFile{Size: size}, nil
}

func (f *fakeClient) downloadRange(_ context.Context, _ channelRef, _ FileRef, _, _ int64, _ io.Writer, _ RefreshFunc) error {
	return nil
}

func (f *fakeClient) deleteMessage(_ context.Context, _ channelRef, messageID int) error {
	f.mu.Lock()
	defer f.mu.Unlock()
	f.deleted = append(f.deleted, messageID)
	return nil
}

func (f *fakeClient) stop() {
	f.mu.Lock()
	f.stopped = true
	f.mu.Unlock()
}

// fakeQueries is a minimal stub of the dbsqlc methods the Manager calls during
// linking. Only the telegram-account methods are exercised by the FSM tests.
type fakeQueries struct {
	mu     sync.Mutex
	blob   []byte
	linked *dbsqlc.SetTelegramLinkedParams
	reset  bool
}

func (q *fakeQueries) GetTelegramAccount(_ context.Context) (dbsqlc.TelegramAccount, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	status := string(StatusNotLinked)
	if q.linked != nil {
		status = string(StatusLinked)
	}
	acct := dbsqlc.TelegramAccount{ID: 1, SessionBlobEncrypted: q.blob, Status: status}
	if q.linked != nil {
		acct.ChannelID = q.linked.ChannelID
		acct.ChannelAccessHash = q.linked.ChannelAccessHash
		acct.DcID = q.linked.DcID
		acct.IsPremium = q.linked.IsPremium
		acct.TgUserIDHash = q.linked.TgUserIDHash
	}
	return acct, nil
}

func (q *fakeQueries) GetTelegramSessionBlob(_ context.Context) ([]byte, error) {
	q.mu.Lock()
	defer q.mu.Unlock()
	return q.blob, nil
}

func (q *fakeQueries) SetTelegramSessionBlob(_ context.Context, b []byte) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	q.blob = b
	return nil
}

func (q *fakeQueries) SetTelegramLinked(_ context.Context, p dbsqlc.SetTelegramLinkedParams) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	q.linked = &p
	return nil
}

func (q *fakeQueries) ResetTelegramAccount(_ context.Context) error {
	q.mu.Lock()
	defer q.mu.Unlock()
	q.reset = true
	return nil
}

// newTestManager wires a Manager around a fake client factory. The factory hands
// back the provided fake so the test can inspect it after the flow.
func newTestManager(t *testing.T, fake *fakeClient) (*Manager, *fakeQueries) {
	t.Helper()
	fq := &fakeQueries{}
	m := &Manager{
		q:      fq,
		cfg:    Config{EncryptionKey: make([]byte, 32)},
		logger: slog.New(slog.NewTextHandler(io.Discard, nil)),
		links:  make(map[string]*linkSession),
	}
	m.newClient = func(_ context.Context, _ Config, _ session.Storage) (tgClient, error) {
		return fake, nil
	}
	return m, fq
}

func TestLink_NoTwoFactor_HappyPath(t *testing.T) {
	fake := &fakeClient{
		wantCode: "12345",
		userID:   424242,
		premium:  true,
		channel:  channelRef{id: 1001, accessHash: 2002, dcID: 2},
	}
	m, fq := newTestManager(t, fake)
	ctx := context.Background()

	linkID, err := m.StartLink(ctx, "+15551234567")
	require.NoError(t, err)
	require.NotEmpty(t, linkID)

	needPassword, err := m.SubmitCode(ctx, linkID, "12345")
	require.NoError(t, err)
	require.False(t, needPassword)

	// Linked state persisted.
	require.NotNil(t, fq.linked)
	require.Equal(t, int64(1001), *fq.linked.ChannelID)
	require.Equal(t, int64(2002), *fq.linked.ChannelAccessHash)
	require.True(t, fq.linked.IsPremium)
	require.NotNil(t, fq.linked.TgUserIDHash)
	require.Equal(t, hashUserID(424242), *fq.linked.TgUserIDHash)

	// The link session is consumed and the client is promoted.
	require.Empty(t, m.links)
	client, ch, err := m.linkedClient()
	require.NoError(t, err)
	require.Equal(t, fake, client)
	require.Equal(t, int64(1001), ch.id)
	require.True(t, fake.signedIn)

	// Premium account => 4 GiB ceiling.
	require.Equal(t, int64(4*1024*1024*1024), m.MaxFileBytes())
}

func TestLink_TwoFactor_HappyPath(t *testing.T) {
	fake := &fakeClient{
		wantCode:     "12345",
		needPassword: true,
		wantPassword: "s3cret",
		userID:       777,
		premium:      false,
		channel:      channelRef{id: 5, accessHash: 6, dcID: 4},
	}
	m, fq := newTestManager(t, fake)
	ctx := context.Background()

	linkID, err := m.StartLink(ctx, "+15551234567")
	require.NoError(t, err)

	needPassword, err := m.SubmitCode(ctx, linkID, "12345")
	require.NoError(t, err)
	require.True(t, needPassword)

	// Status reflects the awaiting-password stage.
	require.Equal(t, true, m.links[linkID].awaitingPass)

	// Not yet linked.
	require.Nil(t, fq.linked)

	err = m.SubmitPassword(ctx, linkID, "s3cret")
	require.NoError(t, err)

	require.NotNil(t, fq.linked)
	require.Equal(t, int64(5), *fq.linked.ChannelID)
	require.False(t, fq.linked.IsPremium)
	require.True(t, fake.passwordD)
	require.Empty(t, m.links)

	// Non-premium => 2 GiB ceiling.
	require.Equal(t, int64(2*1024*1024*1024), m.MaxFileBytes())
}

func TestDelete_RemovesChannelMessage(t *testing.T) {
	fake := &fakeClient{
		wantCode: "12345",
		channel:  channelRef{id: 77, accessHash: 88, dcID: 2},
	}
	m, _ := newTestManager(t, fake)
	ctx := context.Background()

	// Not linked yet => Delete must refuse rather than silently no-op.
	require.ErrorIs(t, m.Delete(ctx, 123), ErrNotLinked)

	linkID, err := m.StartLink(ctx, "+15551234567")
	require.NoError(t, err)
	_, err = m.SubmitCode(ctx, linkID, "12345")
	require.NoError(t, err)

	require.NoError(t, m.Delete(ctx, 4242))
	require.Equal(t, []int{4242}, fake.deleted)
}

func TestStartLink_PreservesLinkedSessionBlob(t *testing.T) {
	fake := &fakeClient{wantCode: "12345"}
	m, fq := newTestManager(t, fake)
	ctx := context.Background()

	// Simulate an already-linked account with a persisted session blob.
	existing := []byte("live-account-session-blob")
	fq.blob = existing

	// Starting a (new) link attempt must NOT wipe the live account's persisted
	// session — an abandoned re-link would otherwise silently unlink the account
	// on the next restart.
	_, err := m.StartLink(ctx, "+15551234567")
	require.NoError(t, err)
	require.Equal(t, existing, fq.blob, "StartLink must not touch the persisted session blob")
}

func TestSubmitCode_UnknownLinkID(t *testing.T) {
	fake := &fakeClient{wantCode: "12345"}
	m, _ := newTestManager(t, fake)

	_, err := m.SubmitCode(context.Background(), "does-not-exist", "12345")
	require.ErrorIs(t, err, ErrLinkNotFound)
}

func TestSubmitPassword_UnknownLinkID(t *testing.T) {
	fake := &fakeClient{}
	m, _ := newTestManager(t, fake)

	err := m.SubmitPassword(context.Background(), "nope", "pw")
	require.ErrorIs(t, err, ErrLinkNotFound)
}

func TestSubmitCode_WrongCode(t *testing.T) {
	fake := &fakeClient{wantCode: "12345"}
	m, fq := newTestManager(t, fake)
	ctx := context.Background()

	linkID, err := m.StartLink(ctx, "+15551234567")
	require.NoError(t, err)

	_, err = m.SubmitCode(ctx, linkID, "00000")
	require.ErrorIs(t, err, ErrCodeInvalid)

	// Failed code must NOT link the account and must keep the session alive so
	// the admin can retry.
	require.Nil(t, fq.linked)
	require.Contains(t, m.links, linkID)
}

func TestCancelLink(t *testing.T) {
	fake := &fakeClient{wantCode: "12345"}
	m, _ := newTestManager(t, fake)
	ctx := context.Background()

	linkID, err := m.StartLink(ctx, "+15551234567")
	require.NoError(t, err)

	m.CancelLink(linkID)
	require.Empty(t, m.links)
	require.True(t, fake.stopped)

	// A cancelled/unknown id submitting a code is ErrLinkNotFound.
	_, err = m.SubmitCode(ctx, linkID, "12345")
	require.ErrorIs(t, err, ErrLinkNotFound)
}

func TestUnlink(t *testing.T) {
	fake := &fakeClient{
		wantCode: "12345",
		channel:  channelRef{id: 9, accessHash: 9},
	}
	m, fq := newTestManager(t, fake)
	ctx := context.Background()

	linkID, err := m.StartLink(ctx, "+15551234567")
	require.NoError(t, err)
	_, err = m.SubmitCode(ctx, linkID, "12345")
	require.NoError(t, err)

	require.NoError(t, m.Unlink(ctx))
	require.True(t, fq.reset)
	require.True(t, fake.stopped)
	_, _, err = m.linkedClient()
	require.ErrorIs(t, err, ErrNotLinked)
}
