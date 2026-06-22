package telegram

import (
	"context"
	"fmt"
	"io"
)

// Upload streams r (exactly size bytes) as a document into the storage channel
// and returns the persisted handle. Requires a linked account; rejects files
// over the account ceiling with ErrFileTooLarge.
func (m *Manager) Upload(ctx context.Context, name, mime string, size int64, r io.Reader) (StoredFile, error) {
	if size > m.MaxFileBytes() {
		return StoredFile{}, ErrFileTooLarge
	}

	client, ch, err := m.linkedClient()
	if err != nil {
		return StoredFile{}, err
	}

	stored, err := client.uploadDocument(ctx, ch, name, mime, size, r)
	if err != nil {
		return StoredFile{}, fmt.Errorf("upload document: %w", err)
	}
	return stored, nil
}

// Download streams bytes [offset, offset+limit) of the document to w (limit<=0 =
// to EOF). On FILE_REFERENCE_EXPIRED the reference is refreshed by re-fetching
// the channel message and the refreshed FileRef is surfaced via refresh before
// the download retries. Requires a linked account.
func (m *Manager) Download(ctx context.Context, ref FileRef, offset, limit int64, w io.Writer, refresh RefreshFunc) error {
	client, ch, err := m.linkedClient()
	if err != nil {
		return err
	}

	if err := client.downloadRange(ctx, ch, ref, offset, limit, w, refresh); err != nil {
		return fmt.Errorf("download range: %w", err)
	}
	return nil
}

// Delete removes a file's underlying message — and thus the stored document
// bytes — from the storage channel. Requires a linked account (ErrNotLinked
// otherwise). Deleting an already-removed message is a no-op, so retries after a
// transient failure are safe.
func (m *Manager) Delete(ctx context.Context, messageID int64) error {
	client, ch, err := m.linkedClient()
	if err != nil {
		return err
	}
	if err := client.deleteMessage(ctx, ch, int(messageID)); err != nil {
		return fmt.Errorf("delete message: %w", err)
	}
	return nil
}
