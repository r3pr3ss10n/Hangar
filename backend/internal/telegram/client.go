package telegram

import (
	"context"
	"errors"
	"fmt"
	"io"
	"log/slog"
	"sync"
	"sync/atomic"
	"time"

	"github.com/gotd/td/session"
	"github.com/gotd/td/telegram"
	"github.com/gotd/td/telegram/auth"
	"github.com/gotd/td/telegram/message"
	"github.com/gotd/td/telegram/message/unpack"
	"github.com/gotd/td/telegram/uploader"
	"github.com/gotd/td/tg"
	"github.com/gotd/td/tgerr"
	"golang.org/x/sync/errgroup"
)

// uploadPartSize is the upload chunk size (must divide 512 KiB cleanly).
const uploadPartSize = 512 * 1024

// uploadThreads is the upload concurrency.
const uploadThreads = 8

// Download tuning. Telegram serves file bytes from dedicated "media cluster"
// servers (the media_only DC endpoints) and lets a client run MANY concurrent
// upload.getFile requests across several connections to them. We mirror the
// official clients: a persistent multi-connection pool to the media endpoints,
// fanning each download out into many parallel 1 MiB requests.
const (
	// downloadChunk is the per-request part size: 1 MiB, the maximum
	// upload.getFile allows. Each request is issued at a 1 MiB-aligned offset, so
	// it never crosses a 1 MiB boundary and needs no Precise flag (offset/limit
	// are 4 KiB-divisible).
	downloadChunk = 1024 * 1024
	// downloadThreads is the number of concurrent in-flight upload.getFile
	// requests per download. gotd's pool serves one request per connection at a
	// time, so this is effectively bounded by downloadConns.
	downloadThreads = 64
	// downloadConns is the number of real connections in the persistent download
	// pool to the media DC. Throughput scales with this on the media endpoints
	// (unlike the regular endpoint, which is throughput-limited per account);
	// ~32 saturates a fast line without diminishing returns.
	downloadConns = 32
	// downloadWindow bounds how many parts may sit fetched-but-unwritten ahead of
	// the ordered write cursor (back-pressure). Must be >= downloadThreads.
	downloadWindow = 96
	// poolMinBytes is the size at/above which a download uses the dedicated pool;
	// smaller reads use the shared client connection to avoid pool latency.
	poolMinBytes = 16 * 1024 * 1024
	// partMaxRetries is how many times a single part fetch is retried when a pool
	// connection dies mid-request (transient, common during cold-start) before
	// giving up and failing the download.
	partMaxRetries = 5
)

// fileReferenceExpired is the RPC error type emitted when a stored
// file_reference is no longer valid and must be refetched.
const fileReferenceExpired = "FILE_REFERENCE_EXPIRED"

// realClient is the live gotd-backed implementation of tgClient. It owns a
// running *telegram.Client on the account's REGULAR endpoints (used for uploads,
// sends, deletes and auth) plus a separate, lazily-created download client on the
// media-cluster endpoints (used only for upload.getFile). They are split because
// the media-cluster servers reject upload/sendMedia RPCs (MTPROTO_CLUSTER_INVALID)
// while the regular endpoints throughput-limit downloads — the official clients
// make the same split.
type realClient struct {
	client *telegram.Client
	api    *tg.Client

	runCancel context.CancelFunc
	done      chan struct{}

	// cfg/storage are retained so the download client can be built lazily, reusing
	// the linked account's auth key via a read-only session wrapper.
	cfg     Config
	storage session.Storage

	// dlPool is a single, long-lived multi-connection pool to the media DC,
	// created once on first download and reused for every subsequent one. It runs
	// on a dedicated download client whose resolver routes to Telegram's
	// media_only endpoints (see mediaResolver). Reusing one pool avoids the
	// per-download connection churn that otherwise tripped Telegram's connection
	// rate limit.
	dlPoolMu  sync.Mutex
	dlClient  *telegram.Client
	dlCancel  context.CancelFunc
	dlDone    chan struct{}
	dlPool    telegram.CloseInvoker
	dlAPI     *tg.Client
}

// downloadPool returns the shared, lazily-created download pool's API client. On
// first use it stands up a dedicated download client on the media endpoints,
// reusing the main account's auth key, and opens a connection pool on it.
func (c *realClient) downloadPool() (*tg.Client, error) {
	c.dlPoolMu.Lock()
	defer c.dlPoolMu.Unlock()
	if c.dlAPI != nil {
		return c.dlAPI, nil
	}

	// Build a download-only client that shares the auth key but routes Primary()
	// to the media_only endpoints. A read-only session wrapper lets it reuse the
	// key without writing back to the shared session blob.
	dlClient := telegram.NewClient(c.cfg.APIID, c.cfg.APIHash, telegram.Options{
		SessionStorage: newReadonlySessionStorage(c.storage),
		Resolver:       newMediaResolver(nil),
		NoUpdates:      true,
	})

	runCtx, cancel := context.WithCancel(context.Background())
	done := make(chan struct{})
	ready := make(chan struct{})
	runErr := make(chan error, 1)
	go func() {
		defer close(done)
		err := dlClient.Run(runCtx, func(ctx context.Context) error {
			close(ready)
			<-ctx.Done()
			return ctx.Err()
		})
		runErr <- err
	}()

	select {
	case <-ready:
	case err := <-runErr:
		cancel()
		<-done
		return nil, fmt.Errorf("run download client: %w", err)
	}

	inv, err := dlClient.Pool(int64(downloadConns))
	if err != nil {
		cancel()
		<-done
		return nil, err
	}
	c.dlClient = dlClient
	c.dlCancel = cancel
	c.dlDone = done
	c.dlPool = inv
	c.dlAPI = tg.NewClient(inv)
	return c.dlAPI, nil
}

// newRealClient builds and starts a live gotd client. It blocks until the
// client's Run loop reports the connection is ready (or the dial fails).
func newRealClient(ctx context.Context, cfg Config, storage session.Storage) (tgClient, error) {
	client := telegram.NewClient(cfg.APIID, cfg.APIHash, telegram.Options{
		SessionStorage: storage,
	})

	// runCtx outlives the caller's ctx so the client keeps running until stop().
	runCtx, cancel := context.WithCancel(context.Background())
	rc := &realClient{
		client:    client,
		api:       client.API(),
		runCancel: cancel,
		done:      make(chan struct{}),
		cfg:       cfg,
		storage:   storage,
	}

	ready := make(chan struct{})
	runErr := make(chan error, 1)
	go func() {
		defer close(rc.done)
		err := client.Run(runCtx, func(ctx context.Context) error {
			close(ready)
			<-ctx.Done()
			return ctx.Err()
		})
		runErr <- err
	}()

	// Wait for readiness, a run failure, or caller cancellation.
	select {
	case <-ready:
		// Warm the download pool in the background so its media client and
		// connections are established before the first download.
		go func() {
			if _, err := rc.downloadPool(); err != nil {
				slog.Default().Warn("telegram: download pool warm-up failed", "error", err)
			}
		}()
		return rc, nil
	case err := <-runErr:
		cancel()
		return nil, fmt.Errorf("run client: %w", err)
	case <-ctx.Done():
		cancel()
		return nil, ctx.Err()
	}
}

// stop cancels the Run loops (download client first, then main) and waits for
// them to unwind.
func (c *realClient) stop() {
	c.dlPoolMu.Lock()
	if c.dlPool != nil {
		_ = c.dlPool.Close()
		c.dlPool = nil
		c.dlAPI = nil
	}
	if c.dlCancel != nil {
		c.dlCancel()
		<-c.dlDone
		c.dlCancel = nil
		c.dlClient = nil
	}
	c.dlPoolMu.Unlock()
	c.runCancel()
	<-c.done
}

// withFlood runs fn, sleeping and retrying when Telegram returns FLOOD_WAIT_x.
func withFlood(ctx context.Context, fn func() error) error {
	for {
		err := fn()
		if err == nil {
			return nil
		}
		d, ok := tgerr.AsFloodWait(err)
		if !ok {
			return err
		}
		timer := time.NewTimer(d + time.Second)
		select {
		case <-timer.C:
		case <-ctx.Done():
			timer.Stop()
			return ctx.Err()
		}
	}
}

func (c *realClient) sendCode(ctx context.Context, phone string) (string, error) {
	var hash string
	err := withFlood(ctx, func() error {
		sent, err := c.client.Auth().SendCode(ctx, phone, auth.SendCodeOptions{})
		if err != nil {
			return err
		}
		code, ok := sent.(*tg.AuthSentCode)
		if !ok {
			return fmt.Errorf("unexpected sent code type %T", sent)
		}
		hash = code.PhoneCodeHash
		return nil
	})
	if err != nil {
		return "", err
	}
	return hash, nil
}

func (c *realClient) signIn(ctx context.Context, phone, code, codeHash string) (bool, error) {
	var needPassword bool
	err := withFlood(ctx, func() error {
		_, err := c.client.Auth().SignIn(ctx, phone, code, codeHash)
		switch {
		case err == nil:
			return nil
		case errors.Is(err, auth.ErrPasswordAuthNeeded):
			needPassword = true
			return nil
		case tgerr.Is(err, "PHONE_CODE_INVALID", "PHONE_CODE_EMPTY", "PHONE_CODE_EXPIRED"):
			return ErrCodeInvalid
		default:
			return err
		}
	})
	if err != nil {
		return false, err
	}
	return needPassword, nil
}

func (c *realClient) signInPassword(ctx context.Context, password string) error {
	return withFlood(ctx, func() error {
		_, err := c.client.Auth().Password(ctx, password)
		if errors.Is(err, auth.ErrPasswordInvalid) {
			return ErrPasswordInvalid
		}
		return err
	})
}

func (c *realClient) self(ctx context.Context) (linkResult, error) {
	var res linkResult
	err := withFlood(ctx, func() error {
		u, err := c.client.Self(ctx)
		if err != nil {
			return err
		}
		res = linkResult{tgUserID: u.GetID(), isPremium: u.GetPremium()}
		return nil
	})
	return res, err
}

// createStorageChannel creates the private broadcast channel that holds bytes
// and extracts its id/access_hash from the returned updates.
func (c *realClient) createStorageChannel(ctx context.Context, title string) (channelRef, error) {
	var ref channelRef
	err := withFlood(ctx, func() error {
		updates, err := c.api.ChannelsCreateChannel(ctx, &tg.ChannelsCreateChannelRequest{
			Broadcast: true,
			Title:     title,
			About:     "Hangar file storage. Managed automatically; do not edit.",
		})
		if err != nil {
			return err
		}
		ch, err := firstChannel(updates)
		if err != nil {
			return err
		}
		hash, _ := ch.GetAccessHash()
		ref = channelRef{id: ch.GetID(), accessHash: hash}
		return nil
	})
	if err != nil {
		return channelRef{}, err
	}
	return ref, nil
}

// firstChannel finds the first *tg.Channel in an updates result's chat list.
func firstChannel(u tg.UpdatesClass) (*tg.Channel, error) {
	withChats, ok := u.(interface{ GetChats() []tg.ChatClass })
	if !ok {
		return nil, fmt.Errorf("updates %T carry no chats", u)
	}
	for _, chat := range withChats.GetChats() {
		if ch, ok := chat.(*tg.Channel); ok {
			return ch, nil
		}
	}
	return nil, errors.New("no channel in create-channel result")
}

// inputPeer builds the message-sender peer for the storage channel.
func (ch channelRef) inputPeer() *tg.InputPeerChannel {
	return &tg.InputPeerChannel{ChannelID: ch.id, AccessHash: ch.accessHash}
}

// inputChannel builds the channel handle for raw channel methods.
func (ch channelRef) inputChannel() *tg.InputChannel {
	return &tg.InputChannel{ChannelID: ch.id, AccessHash: ch.accessHash}
}

// uploadDocument streams the reader as a document into the channel and reads the
// stored document handle off the resulting message.
func (c *realClient) uploadDocument(ctx context.Context, ch channelRef, name, mime string, size int64, r io.Reader) (StoredFile, error) {
	var stored StoredFile
	err := withFlood(ctx, func() error {
		up := uploader.NewUploader(c.api).WithPartSize(uploadPartSize).WithThreads(uploadThreads)
		file, err := up.Upload(ctx, uploader.NewUpload(name, r, size))
		if err != nil {
			return fmt.Errorf("upload file: %w", err)
		}

		sender := message.NewSender(c.api)
		doc := message.UploadedDocument(file).Filename(name).MIME(mime)
		msg, err := unpack.Message(sender.To(ch.inputPeer()).Media(ctx, doc))
		if err != nil {
			return fmt.Errorf("send document: %w", err)
		}

		document, err := documentFromMessage(msg)
		if err != nil {
			return err
		}
		stored = StoredFile{
			MessageID:     int64(msg.GetID()),
			DocumentID:    document.ID,
			AccessHash:    document.AccessHash,
			FileReference: document.FileReference,
			DCID:          document.DCID,
			Size:          document.Size,
		}
		return nil
	})
	if err != nil {
		return StoredFile{}, err
	}
	return stored, nil
}

// documentFromMessage extracts the attached *tg.Document from a message.
func documentFromMessage(msg *tg.Message) (*tg.Document, error) {
	media, ok := msg.GetMedia()
	if !ok {
		return nil, errors.New("sent message has no media")
	}
	docMedia, ok := media.(*tg.MessageMediaDocument)
	if !ok {
		return nil, fmt.Errorf("unexpected media type %T", media)
	}
	docClass, ok := docMedia.GetDocument()
	if !ok {
		return nil, errors.New("media has no document")
	}
	doc, ok := docClass.(*tg.Document)
	if !ok {
		return nil, fmt.Errorf("unexpected document type %T", docClass)
	}
	return doc, nil
}

// downloadRange streams [offset, offset+limit) of the document to w. For a
// concrete length it fans the read out into many concurrent 1 MiB
// upload.getFile requests over a persistent multi-connection pool to the media
// DC, reassembling them in order — this is what lifts throughput from the old
// one-request-at-a-time loop to native-client speed. A limit<=0 (stream to EOF,
// size unknown) falls back to the simple sequential path on the shared client.
func (c *realClient) downloadRange(ctx context.Context, ch channelRef, ref FileRef, offset, limit int64, w io.Writer, refresh RefreshFunc) error {
	if limit <= 0 {
		return c.downloadSequential(ctx, ch, ref, offset, w, refresh)
	}

	// Use the shared client connection for small reads (avoids pool latency) and
	// the persistent media pool for sizable transfers.
	api := c.api
	if limit >= poolMinBytes {
		if pa, err := c.downloadPool(); err == nil {
			api = pa
		} else {
			slog.Default().Warn("download: media pool unavailable, using shared connection", "error", err)
		}
	}
	return c.parallelDownload(ctx, api, ch, ref, offset, limit, w, refresh)
}

// partResult carries a fetched part's absolute file offset and bytes from a
// worker to the ordering writer.
type partResult struct {
	offset int64
	data   []byte
}

// parallelDownload fetches the 1 MiB-aligned parts covering [offset, offset+limit)
// with downloadThreads workers and writes the requested byte range to w in order.
// Each part is requested at a 1 MiB-aligned offset (no boundary cross, no Precise);
// the writer trims the head/tail parts to the exact range. file_reference refresh
// is single-flighted so concurrent workers share one refresh.
func (c *realClient) parallelDownload(ctx context.Context, api *tg.Client, ch channelRef, ref FileRef, offset, limit int64, w io.Writer, refresh RefreshFunc) error {
	// Shared document reference; both access_hash and file_reference can change on
	// refresh. refGen lets only the first worker per generation do the refetch.
	var refMu sync.Mutex
	cur := ref
	refGen := 0

	fetchPart := func(ctx context.Context, part int64) ([]byte, error) {
		partOffset := part * int64(downloadChunk)
		attempts := 0
		for {
			refMu.Lock()
			docID, accessHash, fileRef, gen := cur.DocumentID, cur.AccessHash, cur.FileReference, refGen
			refMu.Unlock()

			var data []byte
			err := withFlood(ctx, func() error {
				res, err := api.UploadGetFile(ctx, &tg.UploadGetFileRequest{
					Location: &tg.InputDocumentFileLocation{ID: docID, AccessHash: accessHash, FileReference: fileRef},
					Offset:   partOffset,
					Limit:    downloadChunk,
				})
				if err != nil {
					return err
				}
				file, ok := res.(*tg.UploadFile)
				if !ok {
					return fmt.Errorf("unexpected upload file type %T", res)
				}
				data = file.GetBytes()
				return nil
			})
			if err == nil {
				return data, nil
			}
			// A pool connection can die mid-request (most often during cold-start
			// when many connections open at once); gotd surfaces this as a wrapped
			// "context canceled" even though the caller's ctx is still alive. Retry
			// the part on such transient failures rather than aborting the whole
			// download. Real caller cancellation is detected via ctx.Err().
			if ctx.Err() == nil && attempts < partMaxRetries && !tgerr.Is(err, fileReferenceExpired) {
				attempts++
				continue
			}
			if !tgerr.Is(err, fileReferenceExpired) {
				return nil, err
			}

			// Single-flight refresh, then retry with the refreshed reference.
			refMu.Lock()
			if refGen == gen {
				newRef, rerr := c.refreshReference(ctx, ch, cur)
				if rerr != nil {
					refMu.Unlock()
					return nil, fmt.Errorf("refresh file reference: %w", rerr)
				}
				cur.AccessHash = newRef.AccessHash
				cur.FileReference = newRef.FileReference
				cur.DCID = newRef.DCID
				refGen++
				if refresh != nil {
					if cbErr := refresh(ctx, newRef); cbErr != nil {
						refMu.Unlock()
						return nil, fmt.Errorf("persist refreshed reference: %w", cbErr)
					}
				}
			}
			refMu.Unlock()
		}
	}

	return streamParts(ctx, fetchPart, offset, limit, w)
}

// fetchFunc returns the bytes of the 1 MiB-aligned part beginning at the absolute
// file offset part*downloadChunk.
type fetchFunc func(ctx context.Context, part int64) ([]byte, error)

// streamParts fetches the 1 MiB-aligned parts covering [offset, offset+limit)
// with downloadThreads concurrent workers and writes exactly that byte range to w
// in ascending order, trimming the head and tail parts. A bounded window keeps at
// most downloadWindow parts fetched-but-unwritten, back-pressuring the workers to
// the writer's pace. It is deadlock-free because downloadWindow >= downloadThreads
// and the lowest outstanding part is always assigned to a worker.
func streamParts(ctx context.Context, fetch fetchFunc, offset, limit int64, w io.Writer) error {
	chunk := int64(downloadChunk)
	end := offset + limit // exclusive
	firstPart := offset / chunk
	lastPart := (end - 1) / chunk

	g, gctx := errgroup.WithContext(ctx)
	// sem bounds parts fetched-but-not-yet-written (acquired before fetch, released
	// after the writer flushes that part). results hands parts to the writer.
	window := downloadWindow
	if window < downloadThreads {
		window = downloadThreads
	}
	sem := make(chan struct{}, window)
	results := make(chan partResult, window)
	nextPart := firstPart - 1

	for i := 0; i < downloadThreads; i++ {
		g.Go(func() error {
			for {
				part := atomic.AddInt64(&nextPart, 1)
				if part > lastPart {
					return nil
				}
				select {
				case sem <- struct{}{}:
				case <-gctx.Done():
					return gctx.Err()
				}
				data, err := fetch(gctx, part)
				if err != nil {
					return err
				}
				select {
				case results <- partResult{offset: part * chunk, data: data}:
				case <-gctx.Done():
					return gctx.Err()
				}
			}
		})
	}

	// Ordering writer: buffers out-of-order parts and flushes them by ascending
	// offset, trimming the head/tail to [offset, end). Releases a sem slot per part.
	g.Go(func() error {
		pending := make(map[int64][]byte)
		cursor := firstPart * chunk
		total := lastPart - firstPart + 1
		for received := int64(0); received < total; received++ {
			select {
			case pr := <-results:
				pending[pr.offset] = pr.data
			case <-gctx.Done():
				return gctx.Err()
			}
			for {
				data, ok := pending[cursor]
				if !ok {
					break
				}
				delete(pending, cursor)
				lo, hi := int64(0), int64(len(data))
				if cursor < offset {
					lo = offset - cursor
				}
				if cursor+int64(len(data)) > end {
					hi = end - cursor
				}
				if lo < hi {
					if _, err := w.Write(data[lo:hi]); err != nil {
						return fmt.Errorf("write output: %w", err)
					}
				}
				<-sem
				cursor += chunk
			}
		}
		return nil
	})

	return g.Wait()
}

// downloadSequential streams from offset to EOF one 1 MiB part at a time. Only
// used for the rare limit<=0 (unknown length) path; the HTTP layer always passes
// a concrete length and takes the parallel path above.
func (c *realClient) downloadSequential(ctx context.Context, ch channelRef, ref FileRef, offset int64, w io.Writer, refresh RefreshFunc) error {
	pos := offset
	refreshed := false
	for {
		var data []byte
		err := withFlood(ctx, func() error {
			res, err := c.api.UploadGetFile(ctx, &tg.UploadGetFileRequest{
				Location: &tg.InputDocumentFileLocation{ID: ref.DocumentID, AccessHash: ref.AccessHash, FileReference: ref.FileReference},
				Offset:   pos,
				Limit:    downloadChunk,
			})
			if err != nil {
				return err
			}
			file, ok := res.(*tg.UploadFile)
			if !ok {
				return fmt.Errorf("unexpected upload file type %T", res)
			}
			data = file.GetBytes()
			return nil
		})
		if err != nil {
			if tgerr.Is(err, fileReferenceExpired) && !refreshed {
				refreshed = true
				newRef, rerr := c.refreshReference(ctx, ch, ref)
				if rerr != nil {
					return fmt.Errorf("refresh file reference: %w", rerr)
				}
				ref.AccessHash = newRef.AccessHash
				ref.FileReference = newRef.FileReference
				if refresh != nil {
					if cbErr := refresh(ctx, newRef); cbErr != nil {
						return fmt.Errorf("persist refreshed reference: %w", cbErr)
					}
				}
				continue
			}
			return err
		}
		if len(data) > 0 {
			if _, werr := w.Write(data); werr != nil {
				return fmt.Errorf("write output: %w", werr)
			}
			pos += int64(len(data))
		}
		if len(data) < downloadChunk {
			return nil
		}
	}
}

// deleteMessage removes a message (and its attached document) from the storage
// channel via channels.deleteMessages. Telegram treats deleting an unknown or
// already-deleted id as a no-op, so this is safely retryable.
func (c *realClient) deleteMessage(ctx context.Context, ch channelRef, messageID int) error {
	return withFlood(ctx, func() error {
		_, err := c.api.ChannelsDeleteMessages(ctx, &tg.ChannelsDeleteMessagesRequest{
			Channel: ch.inputChannel(),
			ID:      []int{messageID},
		})
		return err
	})
}

// refreshReference re-fetches the channel message and returns a FileRef with a
// fresh file_reference (and refreshed access_hash/dc).
func (c *realClient) refreshReference(ctx context.Context, ch channelRef, ref FileRef) (FileRef, error) {
	var out FileRef
	err := withFlood(ctx, func() error {
		res, err := c.api.ChannelsGetMessages(ctx, &tg.ChannelsGetMessagesRequest{
			Channel: ch.inputChannel(),
			ID:      []tg.InputMessageClass{&tg.InputMessageID{ID: int(ref.MessageID)}},
		})
		if err != nil {
			return err
		}
		msgs, ok := res.(interface{ GetMessages() []tg.MessageClass })
		if !ok {
			return fmt.Errorf("unexpected messages type %T", res)
		}
		for _, mc := range msgs.GetMessages() {
			msg, ok := mc.(*tg.Message)
			if !ok {
				continue
			}
			doc, derr := documentFromMessage(msg)
			if derr != nil {
				continue
			}
			out = FileRef{
				MessageID:     ref.MessageID,
				DocumentID:    doc.ID,
				AccessHash:    doc.AccessHash,
				FileReference: doc.FileReference,
				DCID:          doc.DCID,
			}
			return nil
		}
		return errors.New("message not found while refreshing reference")
	})
	if err != nil {
		return FileRef{}, err
	}
	return out, nil
}
