// Package files is the metadata-only service over folders and files. It owns no
// bytes — those are streamed through the telegram.Manager by the api layer — and
// enforces per-owner ownership on every read and mutation. Every row is loaded
// and its OwnerID compared against the caller's ownerID before any action; a
// mismatch is reported as ErrForbidden and a missing row as ErrNotFound.
package files

import (
	"context"
	"crypto/rand"
	"encoding/base64"
	"errors"
	"fmt"
	"strings"
	"time"

	"github.com/google/uuid"
	"github.com/jackc/pgx/v5"

	dbsqlc "github.com/r3pr3ss10n/hangar/backend/internal/db/sqlc"
	"github.com/r3pr3ss10n/hangar/backend/internal/telegram"
)

// Sentinel errors returned by the service. Callers compare with errors.Is and the
// api layer maps them to HTTP status codes (ErrNotFound -> 404, ErrForbidden -> 403).
var (
	ErrNotFound  = errors.New("files: not found")
	ErrForbidden = errors.New("files: forbidden")
)

// Service exposes folder and file metadata operations scoped to a single owner.
type Service struct {
	q *dbsqlc.Queries
}

// NewService constructs a Service backed by the given queries.
func NewService(q *dbsqlc.Queries) *Service {
	return &Service{q: q}
}

// CreateFolder creates a folder owned by ownerID under parentID (nil = root). When
// parentID is set it must reference an existing folder owned by ownerID.
func (s *Service) CreateFolder(ctx context.Context, ownerID uuid.UUID, parentID *uuid.UUID, name string) (dbsqlc.Folder, error) {
	if err := s.assertFolderOwned(ctx, ownerID, parentID); err != nil {
		return dbsqlc.Folder{}, err
	}
	folder, err := s.q.CreateFolder(ctx, dbsqlc.CreateFolderParams{
		OwnerID:  ownerID,
		ParentID: parentID,
		Name:     name,
	})
	if err != nil {
		return dbsqlc.Folder{}, fmt.Errorf("create folder: %w", err)
	}
	return folder, nil
}

// List returns the direct children of parentID (nil = root) for owner: the
// subfolders and the (non-deleted) files that live directly under it.
func (s *Service) List(ctx context.Context, ownerID uuid.UUID, parentID *uuid.UUID) (folders []dbsqlc.Folder, fileList []dbsqlc.File, err error) {
	folders, err = s.q.ListFolders(ctx, dbsqlc.ListFoldersParams{
		OwnerID:  ownerID,
		ParentID: parentID,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("list folders: %w", err)
	}
	rows, err := s.q.ListFiles(ctx, dbsqlc.ListFilesParams{
		OwnerID:  ownerID,
		FolderID: parentID,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("list files: %w", err)
	}
	fileList = make([]dbsqlc.File, len(rows))
	for i, r := range rows {
		fileList[i] = fileFromListRow(r)
	}
	return folders, fileList, nil
}

// RenameFolder renames an owned folder.
func (s *Service) RenameFolder(ctx context.Context, ownerID, folderID uuid.UUID, name string) error {
	if _, err := s.getOwnedFolder(ctx, ownerID, folderID); err != nil {
		return err
	}
	if err := s.q.RenameFolder(ctx, dbsqlc.RenameFolderParams{ID: folderID, Name: name}); err != nil {
		return fmt.Errorf("rename folder: %w", err)
	}
	return nil
}

// MoveFolder reparents an owned folder to newParent (nil = root). The destination,
// when set, must be an existing folder owned by the same owner.
func (s *Service) MoveFolder(ctx context.Context, ownerID, folderID uuid.UUID, newParent *uuid.UUID) error {
	if _, err := s.getOwnedFolder(ctx, ownerID, folderID); err != nil {
		return err
	}
	if err := s.assertFolderOwned(ctx, ownerID, newParent); err != nil {
		return err
	}
	if err := s.q.MoveFolder(ctx, dbsqlc.MoveFolderParams{ID: folderID, ParentID: newParent}); err != nil {
		return fmt.Errorf("move folder: %w", err)
	}
	return nil
}

// DeleteFolder deletes an owned folder.
func (s *Service) DeleteFolder(ctx context.Context, ownerID, folderID uuid.UUID) error {
	if _, err := s.getOwnedFolder(ctx, ownerID, folderID); err != nil {
		return err
	}
	if err := s.q.DeleteFolder(ctx, folderID); err != nil {
		return fmt.Errorf("delete folder: %w", err)
	}
	return nil
}

// NewFile carries the metadata for a freshly uploaded file. TG holds the Telegram
// storage handle returned by telegram.Manager.Upload.
type NewFile struct {
	OwnerID            uuid.UUID
	FolderID           *uuid.UUID
	Name, Mime, SHA256 string
	Size               int64
	TG                 telegram.StoredFile
	// ThumbRef is an optional generated thumbnail (JPEG bytes); nil for files
	// that have no preview thumbnail.
	ThumbRef []byte
}

// AssertFolderOwned validates that an optional target folder exists and is owned
// by ownerID (nil = root, always valid). Upload handlers call this BEFORE
// streaming bytes to Telegram so a doomed destination never strands an upload.
func (s *Service) AssertFolderOwned(ctx context.Context, ownerID uuid.UUID, folderID *uuid.UUID) error {
	return s.assertFolderOwned(ctx, ownerID, folderID)
}

// CreateFile records a file's metadata. When FolderID is set it must reference an
// existing folder owned by f.OwnerID.
func (s *Service) CreateFile(ctx context.Context, f NewFile) (dbsqlc.File, error) {
	if err := s.assertFolderOwned(ctx, f.OwnerID, f.FolderID); err != nil {
		return dbsqlc.File{}, err
	}
	file, err := s.q.CreateFile(ctx, dbsqlc.CreateFileParams{
		OwnerID:         f.OwnerID,
		FolderID:        f.FolderID,
		Name:            f.Name,
		Size:            f.Size,
		Mime:            f.Mime,
		Sha256:          f.SHA256,
		TgMessageID:     f.TG.MessageID,
		TgDocumentID:    f.TG.DocumentID,
		TgAccessHash:    f.TG.AccessHash,
		TgFileReference: f.TG.FileReference,
		TgDcID:          int32(f.TG.DCID),
		ThumbRef:        f.ThumbRef,
	})
	if err != nil {
		return dbsqlc.File{}, fmt.Errorf("create file: %w", err)
	}
	return file, nil
}

// GetFile loads a file's metadata, enforcing ownership.
func (s *Service) GetFile(ctx context.Context, ownerID, fileID uuid.UUID) (dbsqlc.File, error) {
	return s.getOwnedFile(ctx, ownerID, fileID)
}

// RenameFile renames an owned file.
func (s *Service) RenameFile(ctx context.Context, ownerID, fileID uuid.UUID, name string) error {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return err
	}
	if err := s.q.RenameFile(ctx, dbsqlc.RenameFileParams{ID: fileID, Name: name}); err != nil {
		return fmt.Errorf("rename file: %w", err)
	}
	return nil
}

// MoveFile moves an owned file into newFolder (nil = root). The destination, when
// set, must be an existing folder owned by the same owner.
func (s *Service) MoveFile(ctx context.Context, ownerID, fileID uuid.UUID, newFolder *uuid.UUID) error {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return err
	}
	if err := s.assertFolderOwned(ctx, ownerID, newFolder); err != nil {
		return err
	}
	if err := s.q.MoveFile(ctx, dbsqlc.MoveFileParams{ID: fileID, FolderID: newFolder}); err != nil {
		return fmt.Errorf("move file: %w", err)
	}
	return nil
}

// DeleteFile soft-deletes an owned file.
func (s *Service) DeleteFile(ctx context.Context, ownerID, fileID uuid.UUID) error {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return err
	}
	if err := s.q.SoftDeleteFile(ctx, fileID); err != nil {
		return fmt.Errorf("delete file: %w", err)
	}
	return nil
}

// CreateShare mints a public share link for an owned file. expiresAt is nil for
// a link that never expires. The returned row carries the opaque token the
// caller turns into a URL.
func (s *Service) CreateShare(ctx context.Context, ownerID, fileID uuid.UUID, expiresAt *time.Time) (dbsqlc.FileShare, error) {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return dbsqlc.FileShare{}, err
	}
	token, err := newShareToken()
	if err != nil {
		return dbsqlc.FileShare{}, fmt.Errorf("mint share token: %w", err)
	}
	share, err := s.q.CreateFileShare(ctx, dbsqlc.CreateFileShareParams{
		Token:     token,
		FileID:    fileID,
		CreatedBy: ownerID,
		ExpiresAt: expiresAt,
	})
	if err != nil {
		return dbsqlc.FileShare{}, fmt.Errorf("create share: %w", err)
	}
	return share, nil
}

// ListShares returns the share links for an owned file, newest first.
func (s *Service) ListShares(ctx context.Context, ownerID, fileID uuid.UUID) ([]dbsqlc.FileShare, error) {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return nil, err
	}
	shares, err := s.q.ListFileSharesByFile(ctx, fileID)
	if err != nil {
		return nil, fmt.Errorf("list shares: %w", err)
	}
	return shares, nil
}

// DeleteShare revokes a share link, but only when the caller owns the file the
// link points at. An unknown or foreign token maps to ErrNotFound.
func (s *Service) DeleteShare(ctx context.Context, ownerID uuid.UUID, token string) error {
	n, err := s.q.DeleteFileShareOwned(ctx, dbsqlc.DeleteFileShareOwnedParams{
		Token:   token,
		OwnerID: ownerID,
	})
	if err != nil {
		return fmt.Errorf("delete share: %w", err)
	}
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

// GetSharedFile resolves a public share token to its file (and the link's expiry,
// nil = never), with no ownership check — this backs the unauthenticated share
// endpoints. An unknown token, a soft-deleted file, or an expired link all map to
// ErrNotFound so a caller cannot tell them apart.
func (s *Service) GetSharedFile(ctx context.Context, token string) (dbsqlc.File, *time.Time, error) {
	row, err := s.q.GetShareFile(ctx, token)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return dbsqlc.File{}, nil, ErrNotFound
		}
		return dbsqlc.File{}, nil, fmt.Errorf("get shared file: %w", err)
	}
	if row.ShareExpiresAt != nil && !row.ShareExpiresAt.After(time.Now()) {
		return dbsqlc.File{}, nil, ErrNotFound
	}
	return fileFromShareRow(row), row.ShareExpiresAt, nil
}

// newShareToken returns a 128-bit, URL-safe random token (22 chars, no padding).
func newShareToken() (string, error) {
	b := make([]byte, 16)
	if _, err := rand.Read(b); err != nil {
		return "", err
	}
	return base64.RawURLEncoding.EncodeToString(b), nil
}

// fileFromShareRow rebuilds a dbsqlc.File from the GetShareFile join projection.
func fileFromShareRow(r dbsqlc.GetShareFileRow) dbsqlc.File {
	return dbsqlc.File{
		ID:              r.ID,
		OwnerID:         r.OwnerID,
		FolderID:        r.FolderID,
		Name:            r.Name,
		Size:            r.Size,
		Mime:            r.Mime,
		Sha256:          r.Sha256,
		TgMessageID:     r.TgMessageID,
		TgDocumentID:    r.TgDocumentID,
		TgAccessHash:    r.TgAccessHash,
		TgFileReference: r.TgFileReference,
		TgDcID:          r.TgDcID,
		ThumbRef:        r.ThumbRef,
		CreatedAt:       r.CreatedAt,
		DeletedAt:       r.DeletedAt,
	}
}

// --- internal user-to-user sharing (grants) -------------------------------

// Grant is a single access grant on a file or folder, with the recipient's
// username resolved for display.
type Grant struct {
	RecipientID       uuid.UUID
	RecipientUsername string
	Permission        string
	CreatedAt         time.Time
}

// SharedFolder / SharedFile pair a resource shared with the caller with the
// username of the owner who shared it.
type SharedFolder struct {
	Folder        dbsqlc.Folder
	OwnerUsername string
}
type SharedFile struct {
	File          dbsqlc.File
	OwnerUsername string
}

// normalizePermission clamps an incoming permission to a supported value. Only
// "view" is honoured in v1; anything else (including "edit") falls back to view.
func normalizePermission(p string) string {
	if p == "edit" {
		return "edit"
	}
	return "view"
}

// GrantFile shares an owned file with another user (view access in v1). Sharing
// with oneself is rejected. Re-granting updates the permission (idempotent).
func (s *Service) GrantFile(ctx context.Context, ownerID, fileID, recipientID uuid.UUID, permission string) error {
	if recipientID == ownerID {
		return ErrForbidden
	}
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return err
	}
	if _, err := s.q.CreateFileGrant(ctx, dbsqlc.CreateFileGrantParams{
		FileID:      &fileID,
		RecipientID: recipientID,
		GrantedBy:   ownerID,
		Permission:  normalizePermission(permission),
	}); err != nil {
		return fmt.Errorf("grant file: %w", err)
	}
	return nil
}

// GrantFolder shares an owned folder (and its whole subtree) with another user.
func (s *Service) GrantFolder(ctx context.Context, ownerID, folderID, recipientID uuid.UUID, permission string) error {
	if recipientID == ownerID {
		return ErrForbidden
	}
	if _, err := s.getOwnedFolder(ctx, ownerID, folderID); err != nil {
		return err
	}
	if _, err := s.q.CreateFolderGrant(ctx, dbsqlc.CreateFolderGrantParams{
		FolderID:    &folderID,
		RecipientID: recipientID,
		GrantedBy:   ownerID,
		Permission:  normalizePermission(permission),
	}); err != nil {
		return fmt.Errorf("grant folder: %w", err)
	}
	return nil
}

// ListFileGrants lists who an owned file is shared with.
func (s *Service) ListFileGrants(ctx context.Context, ownerID, fileID uuid.UUID) ([]Grant, error) {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return nil, err
	}
	rows, err := s.q.ListFileGrants(ctx, &fileID)
	if err != nil {
		return nil, fmt.Errorf("list file grants: %w", err)
	}
	out := make([]Grant, len(rows))
	for i, r := range rows {
		out[i] = Grant{RecipientID: r.RecipientID, RecipientUsername: r.RecipientUsername, Permission: r.Permission, CreatedAt: r.CreatedAt}
	}
	return out, nil
}

// ListFolderGrants lists who an owned folder is shared with.
func (s *Service) ListFolderGrants(ctx context.Context, ownerID, folderID uuid.UUID) ([]Grant, error) {
	if _, err := s.getOwnedFolder(ctx, ownerID, folderID); err != nil {
		return nil, err
	}
	rows, err := s.q.ListFolderGrants(ctx, &folderID)
	if err != nil {
		return nil, fmt.Errorf("list folder grants: %w", err)
	}
	out := make([]Grant, len(rows))
	for i, r := range rows {
		out[i] = Grant{RecipientID: r.RecipientID, RecipientUsername: r.RecipientUsername, Permission: r.Permission, CreatedAt: r.CreatedAt}
	}
	return out, nil
}

// RevokeFileGrant removes a recipient's access to an owned file.
func (s *Service) RevokeFileGrant(ctx context.Context, ownerID, fileID, recipientID uuid.UUID) error {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return err
	}
	n, err := s.q.DeleteFileGrant(ctx, dbsqlc.DeleteFileGrantParams{FileID: &fileID, RecipientID: recipientID})
	if err != nil {
		return fmt.Errorf("revoke file grant: %w", err)
	}
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

// RevokeFolderGrant removes a recipient's access to an owned folder.
func (s *Service) RevokeFolderGrant(ctx context.Context, ownerID, folderID, recipientID uuid.UUID) error {
	if _, err := s.getOwnedFolder(ctx, ownerID, folderID); err != nil {
		return err
	}
	n, err := s.q.DeleteFolderGrant(ctx, dbsqlc.DeleteFolderGrantParams{FolderID: &folderID, RecipientID: recipientID})
	if err != nil {
		return fmt.Errorf("revoke folder grant: %w", err)
	}
	if n == 0 {
		return ErrNotFound
	}
	return nil
}

// GetAccessibleFile loads a file the user may read: one they own, or one shared
// with them directly or via an ancestor folder. Read-only path — mutations keep
// using getOwnedFile. Missing/soft-deleted maps to ErrNotFound, no access to
// ErrForbidden.
func (s *Service) GetAccessibleFile(ctx context.Context, userID, fileID uuid.UUID) (dbsqlc.File, error) {
	file, err := s.q.GetFile(ctx, fileID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return dbsqlc.File{}, ErrNotFound
		}
		return dbsqlc.File{}, fmt.Errorf("get file: %w", err)
	}
	if file.OwnerID == userID {
		return file, nil
	}
	shared, err := s.q.FileSharedToRecipient(ctx, dbsqlc.FileSharedToRecipientParams{FileID: &fileID, RecipientID: userID})
	if err != nil {
		return dbsqlc.File{}, fmt.Errorf("check file access: %w", err)
	}
	if !shared {
		return dbsqlc.File{}, ErrForbidden
	}
	return file, nil
}

// canAccessFolder verifies the user may browse a folder: owner, or it/an
// ancestor is shared with them.
func (s *Service) canAccessFolder(ctx context.Context, userID, folderID uuid.UUID) error {
	folder, err := s.q.GetFolder(ctx, folderID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return ErrNotFound
		}
		return fmt.Errorf("get folder: %w", err)
	}
	if folder.OwnerID == userID {
		return nil
	}
	shared, err := s.q.FolderSharedToRecipient(ctx, dbsqlc.FolderSharedToRecipientParams{ID: folderID, RecipientID: userID})
	if err != nil {
		return fmt.Errorf("check folder access: %w", err)
	}
	if !shared {
		return ErrForbidden
	}
	return nil
}

// ListSharedRoots returns the folders and files shared directly with the user —
// the top level of their "shared with me" view.
func (s *Service) ListSharedRoots(ctx context.Context, userID uuid.UUID) ([]SharedFolder, []SharedFile, error) {
	folderRows, err := s.q.ListSharedFoldersForRecipient(ctx, userID)
	if err != nil {
		return nil, nil, fmt.Errorf("list shared folders: %w", err)
	}
	fileRows, err := s.q.ListSharedFilesForRecipient(ctx, userID)
	if err != nil {
		return nil, nil, fmt.Errorf("list shared files: %w", err)
	}
	folders := make([]SharedFolder, len(folderRows))
	for i, r := range folderRows {
		folders[i] = SharedFolder{
			Folder:        dbsqlc.Folder{ID: r.ID, OwnerID: r.OwnerID, ParentID: r.ParentID, Name: r.Name, CreatedAt: r.CreatedAt},
			OwnerUsername: r.OwnerUsername,
		}
	}
	files := make([]SharedFile, len(fileRows))
	for i, r := range fileRows {
		files[i] = SharedFile{File: fileFromSharedRecipientRow(r), OwnerUsername: r.OwnerUsername}
	}
	return folders, files, nil
}

// ListSharedChildren lists the direct children of a folder the user can access
// (the folder must be shared with them, or owned by them). Files are returned in
// the same shape as a normal listing.
func (s *Service) ListSharedChildren(ctx context.Context, userID, folderID uuid.UUID) ([]dbsqlc.Folder, []dbsqlc.File, error) {
	if err := s.canAccessFolder(ctx, userID, folderID); err != nil {
		return nil, nil, err
	}
	folders, err := s.q.ListSubfolders(ctx, &folderID)
	if err != nil {
		return nil, nil, fmt.Errorf("list subfolders: %w", err)
	}
	fileRows, err := s.q.ListFolderFiles(ctx, &folderID)
	if err != nil {
		return nil, nil, fmt.Errorf("list folder files: %w", err)
	}
	files := make([]dbsqlc.File, len(fileRows))
	for i, r := range fileRows {
		files[i] = fileFromFolderFilesRow(r)
	}
	return folders, files, nil
}

// fileFromSharedRecipientRow / fileFromFolderFilesRow rebuild a dbsqlc.File from
// the lighter shared-listing projections (thumb_ref arrives as a presence marker).
func fileFromSharedRecipientRow(r dbsqlc.ListSharedFilesForRecipientRow) dbsqlc.File {
	return dbsqlc.File{
		ID: r.ID, OwnerID: r.OwnerID, FolderID: r.FolderID, Name: r.Name, Size: r.Size,
		Mime: r.Mime, Sha256: r.Sha256, TgMessageID: r.TgMessageID, TgDocumentID: r.TgDocumentID,
		TgAccessHash: r.TgAccessHash, TgFileReference: r.TgFileReference, TgDcID: r.TgDcID,
		ThumbRef: r.ThumbRef, CreatedAt: r.CreatedAt, DeletedAt: r.DeletedAt,
	}
}
func fileFromFolderFilesRow(r dbsqlc.ListFolderFilesRow) dbsqlc.File {
	return dbsqlc.File{
		ID: r.ID, OwnerID: r.OwnerID, FolderID: r.FolderID, Name: r.Name, Size: r.Size,
		Mime: r.Mime, Sha256: r.Sha256, TgMessageID: r.TgMessageID, TgDocumentID: r.TgDocumentID,
		TgAccessHash: r.TgAccessHash, TgFileReference: r.TgFileReference, TgDcID: r.TgDcID,
		ThumbRef: r.ThumbRef, CreatedAt: r.CreatedAt, DeletedAt: r.DeletedAt,
	}
}

// --- favourites & tags ----------------------------------------------------

// TagWithCount is a tag plus how many items carry it.
type TagWithCount struct {
	Tag       dbsqlc.Tag
	ItemCount int64
}

// LabelsData is the per-user bundle the UI loads once to render tag badges
// across every listing: the user's tags and all assignments on those tags.
type LabelsData struct {
	Tags        []TagWithCount
	Assignments []dbsqlc.ResourceTag
}

// CreateTag creates a colour-coded tag owned by the caller.
func (s *Service) CreateTag(ctx context.Context, ownerID uuid.UUID, name, color string) (dbsqlc.Tag, error) {
	tag, err := s.q.CreateTag(ctx, dbsqlc.CreateTagParams{OwnerID: ownerID, Name: name, Color: color})
	if err != nil {
		return dbsqlc.Tag{}, fmt.Errorf("create tag: %w", err)
	}
	return tag, nil
}

// ListTags returns the owner's tags with item counts.
func (s *Service) ListTags(ctx context.Context, ownerID uuid.UUID) ([]TagWithCount, error) {
	rows, err := s.q.ListTags(ctx, ownerID)
	if err != nil {
		return nil, fmt.Errorf("list tags: %w", err)
	}
	out := make([]TagWithCount, len(rows))
	for i, r := range rows {
		out[i] = TagWithCount{
			Tag:       dbsqlc.Tag{ID: r.ID, OwnerID: r.OwnerID, Name: r.Name, Color: r.Color, CreatedAt: r.CreatedAt},
			ItemCount: r.ItemCount,
		}
	}
	return out, nil
}

// UpdateTag renames/recolours an owned tag.
func (s *Service) UpdateTag(ctx context.Context, ownerID, tagID uuid.UUID, name, color string) error {
	if _, err := s.getOwnedTag(ctx, ownerID, tagID); err != nil {
		return err
	}
	if err := s.q.UpdateTag(ctx, dbsqlc.UpdateTagParams{ID: tagID, Name: name, Color: color}); err != nil {
		return fmt.Errorf("update tag: %w", err)
	}
	return nil
}

// DeleteTag deletes an owned tag (its assignments cascade away).
func (s *Service) DeleteTag(ctx context.Context, ownerID, tagID uuid.UUID) error {
	if _, err := s.getOwnedTag(ctx, ownerID, tagID); err != nil {
		return err
	}
	if err := s.q.DeleteTag(ctx, tagID); err != nil {
		return fmt.Errorf("delete tag: %w", err)
	}
	return nil
}

// AssignFileTag adds (on=true) or removes a tag on an owned file. Both the file
// and the tag must belong to the caller.
func (s *Service) AssignFileTag(ctx context.Context, ownerID, fileID, tagID uuid.UUID, on bool) error {
	if _, err := s.getOwnedFile(ctx, ownerID, fileID); err != nil {
		return err
	}
	if _, err := s.getOwnedTag(ctx, ownerID, tagID); err != nil {
		return err
	}
	if on {
		if err := s.q.AddFileTag(ctx, dbsqlc.AddFileTagParams{TagID: tagID, FileID: &fileID}); err != nil {
			return fmt.Errorf("add file tag: %w", err)
		}
		return nil
	}
	if _, err := s.q.RemoveFileTag(ctx, dbsqlc.RemoveFileTagParams{TagID: tagID, FileID: &fileID}); err != nil {
		return fmt.Errorf("remove file tag: %w", err)
	}
	return nil
}

// AssignFolderTag adds (on=true) or removes a tag on an owned folder.
func (s *Service) AssignFolderTag(ctx context.Context, ownerID, folderID, tagID uuid.UUID, on bool) error {
	if _, err := s.getOwnedFolder(ctx, ownerID, folderID); err != nil {
		return err
	}
	if _, err := s.getOwnedTag(ctx, ownerID, tagID); err != nil {
		return err
	}
	if on {
		if err := s.q.AddFolderTag(ctx, dbsqlc.AddFolderTagParams{TagID: tagID, FolderID: &folderID}); err != nil {
			return fmt.Errorf("add folder tag: %w", err)
		}
		return nil
	}
	if _, err := s.q.RemoveFolderTag(ctx, dbsqlc.RemoveFolderTagParams{TagID: tagID, FolderID: &folderID}); err != nil {
		return fmt.Errorf("remove folder tag: %w", err)
	}
	return nil
}

// ListTagItems returns the folders and files carrying an owned tag.
func (s *Service) ListTagItems(ctx context.Context, ownerID, tagID uuid.UUID) ([]dbsqlc.Folder, []dbsqlc.File, error) {
	if _, err := s.getOwnedTag(ctx, ownerID, tagID); err != nil {
		return nil, nil, err
	}
	folders, err := s.q.ListTagFolders(ctx, tagID)
	if err != nil {
		return nil, nil, fmt.Errorf("list tag folders: %w", err)
	}
	fileRows, err := s.q.ListTagFiles(ctx, tagID)
	if err != nil {
		return nil, nil, fmt.Errorf("list tag files: %w", err)
	}
	files := make([]dbsqlc.File, len(fileRows))
	for i, r := range fileRows {
		files[i] = fileFromTagRow(r)
	}
	return folders, files, nil
}

// Labels returns the per-user bundle for rendering stars and tag badges.
func (s *Service) Labels(ctx context.Context, ownerID uuid.UUID) (LabelsData, error) {
	tags, err := s.ListTags(ctx, ownerID)
	if err != nil {
		return LabelsData{}, err
	}
	assigns, err := s.q.ListTagAssignments(ctx, ownerID)
	if err != nil {
		return LabelsData{}, fmt.Errorf("list tag assignments: %w", err)
	}
	return LabelsData{Tags: tags, Assignments: assigns}, nil
}

// getOwnedTag loads a tag and verifies it belongs to ownerID.
func (s *Service) getOwnedTag(ctx context.Context, ownerID, tagID uuid.UUID) (dbsqlc.Tag, error) {
	tag, err := s.q.GetTag(ctx, tagID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return dbsqlc.Tag{}, ErrNotFound
		}
		return dbsqlc.Tag{}, fmt.Errorf("get tag: %w", err)
	}
	if tag.OwnerID != ownerID {
		return dbsqlc.Tag{}, ErrForbidden
	}
	return tag, nil
}

func fileFromTagRow(r dbsqlc.ListTagFilesRow) dbsqlc.File {
	return dbsqlc.File{
		ID: r.ID, OwnerID: r.OwnerID, FolderID: r.FolderID, Name: r.Name, Size: r.Size,
		Mime: r.Mime, Sha256: r.Sha256, TgMessageID: r.TgMessageID, TgDocumentID: r.TgDocumentID,
		TgAccessHash: r.TgAccessHash, TgFileReference: r.TgFileReference, TgDcID: r.TgDcID,
		ThumbRef: r.ThumbRef, CreatedAt: r.CreatedAt, DeletedAt: r.DeletedAt,
	}
}

// StorageUsed returns the total size in bytes of the owner's non-deleted files.
func (s *Service) StorageUsed(ctx context.Context, ownerID uuid.UUID) (int64, error) {
	used, err := s.q.StorageUsedByOwner(ctx, ownerID)
	if err != nil {
		return 0, fmt.Errorf("storage used: %w", err)
	}
	return used, nil
}

// searchLimit caps how many hits each of the folder/file searches returns.
const searchLimit = 20

// PathSegment is one ancestor folder on the path to a search hit, ordered from
// the drive root down to the hit's immediate parent.
type PathSegment struct {
	ID   uuid.UUID
	Name string
}

// FolderHit is a folder that matched a search together with its ancestor path.
type FolderHit struct {
	Folder dbsqlc.Folder
	Path   []PathSegment
}

// FileHit is a file that matched a search together with its ancestor path.
type FileHit struct {
	File dbsqlc.File
	Path []PathSegment
}

// Search runs a fuzzy, typo-tolerant search over the owner's folder and file
// names and returns the ranked hits, each annotated with the folder path it
// lives under (root-first, excluding the hit itself). A blank query yields no
// results. Results are owner-scoped; soft-deleted files are never returned.
func (s *Service) Search(ctx context.Context, ownerID uuid.UUID, query string) ([]FolderHit, []FileHit, error) {
	query = strings.TrimSpace(query)
	if query == "" {
		return nil, nil, nil
	}

	folders, err := s.q.SearchFolders(ctx, dbsqlc.SearchFoldersParams{
		OwnerID:     ownerID,
		Query:       query,
		ResultLimit: searchLimit,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("search folders: %w", err)
	}
	fileRows, err := s.q.SearchFiles(ctx, dbsqlc.SearchFilesParams{
		OwnerID:     ownerID,
		Query:       query,
		ResultLimit: searchLimit,
	})
	if err != nil {
		return nil, nil, fmt.Errorf("search files: %w", err)
	}

	// Load every folder once and index it by id so each hit's path is resolved
	// in memory rather than with a recursive query per result.
	all, err := s.q.ListAllFoldersForOwner(ctx, ownerID)
	if err != nil {
		return nil, nil, fmt.Errorf("list folders: %w", err)
	}
	byID := make(map[uuid.UUID]dbsqlc.Folder, len(all))
	for _, f := range all {
		byID[f.ID] = f
	}

	folderHits := make([]FolderHit, 0, len(folders))
	for _, f := range folders {
		folderHits = append(folderHits, FolderHit{Folder: f, Path: pathTo(byID, f.ParentID)})
	}
	fileHits := make([]FileHit, 0, len(fileRows))
	for _, r := range fileRows {
		f := fileFromSearchRow(r)
		fileHits = append(fileHits, FileHit{File: f, Path: pathTo(byID, f.FolderID)})
	}
	return folderHits, fileHits, nil
}

// fileFromListRow / fileFromSearchRow rebuild a dbsqlc.File from the lighter
// list/search projections. Those queries deliberately do not read the thumbnail
// blob — thumb_ref arrives as a 1-byte presence marker (or nil) — so HasThumb
// still resolves correctly while the (TOASTed) bytes stay on disk.
func fileFromListRow(r dbsqlc.ListFilesRow) dbsqlc.File {
	return dbsqlc.File{
		ID:              r.ID,
		OwnerID:         r.OwnerID,
		FolderID:        r.FolderID,
		Name:            r.Name,
		Size:            r.Size,
		Mime:            r.Mime,
		Sha256:          r.Sha256,
		TgMessageID:     r.TgMessageID,
		TgDocumentID:    r.TgDocumentID,
		TgAccessHash:    r.TgAccessHash,
		TgFileReference: r.TgFileReference,
		TgDcID:          r.TgDcID,
		ThumbRef:        r.ThumbRef,
		CreatedAt:       r.CreatedAt,
		DeletedAt:       r.DeletedAt,
	}
}

func fileFromSearchRow(r dbsqlc.SearchFilesRow) dbsqlc.File {
	return dbsqlc.File{
		ID:              r.ID,
		OwnerID:         r.OwnerID,
		FolderID:        r.FolderID,
		Name:            r.Name,
		Size:            r.Size,
		Mime:            r.Mime,
		Sha256:          r.Sha256,
		TgMessageID:     r.TgMessageID,
		TgDocumentID:    r.TgDocumentID,
		TgAccessHash:    r.TgAccessHash,
		TgFileReference: r.TgFileReference,
		TgDcID:          r.TgDcID,
		ThumbRef:        r.ThumbRef,
		CreatedAt:       r.CreatedAt,
		DeletedAt:       r.DeletedAt,
	}
}

// pathTo resolves the root-first ancestor chain for an item whose immediate
// parent folder is parentID (nil = the drive root). A missing link (an ancestor
// deleted concurrently) or a cycle stops the walk early instead of looping.
func pathTo(byID map[uuid.UUID]dbsqlc.Folder, parentID *uuid.UUID) []PathSegment {
	var rev []PathSegment
	seen := make(map[uuid.UUID]bool)
	for parentID != nil {
		f, ok := byID[*parentID]
		if !ok || seen[f.ID] {
			break
		}
		seen[f.ID] = true
		rev = append(rev, PathSegment{ID: f.ID, Name: f.Name})
		parentID = f.ParentID
	}
	// rev is leaf-first; reverse to root-first.
	for i, j := 0, len(rev)-1; i < j; i, j = i+1, j-1 {
		rev[i], rev[j] = rev[j], rev[i]
	}
	return rev
}

// getOwnedFolder loads a folder and verifies it belongs to ownerID. A missing row
// maps to ErrNotFound and an owner mismatch to ErrForbidden.
func (s *Service) getOwnedFolder(ctx context.Context, ownerID, folderID uuid.UUID) (dbsqlc.Folder, error) {
	folder, err := s.q.GetFolder(ctx, folderID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return dbsqlc.Folder{}, ErrNotFound
		}
		return dbsqlc.Folder{}, fmt.Errorf("get folder: %w", err)
	}
	if !ownsFolder(ownerID, folder) {
		return dbsqlc.Folder{}, ErrForbidden
	}
	return folder, nil
}

// getOwnedFile loads a file and verifies it belongs to ownerID. A missing row maps
// to ErrNotFound and an owner mismatch to ErrForbidden.
func (s *Service) getOwnedFile(ctx context.Context, ownerID, fileID uuid.UUID) (dbsqlc.File, error) {
	file, err := s.q.GetFile(ctx, fileID)
	if err != nil {
		if errors.Is(err, pgx.ErrNoRows) {
			return dbsqlc.File{}, ErrNotFound
		}
		return dbsqlc.File{}, fmt.Errorf("get file: %w", err)
	}
	if !ownsFile(ownerID, file) {
		return dbsqlc.File{}, ErrForbidden
	}
	return file, nil
}

// assertFolderOwned validates that an optional target folder (a create/move
// destination) exists and belongs to ownerID. A nil id means the root, which is
// always valid.
func (s *Service) assertFolderOwned(ctx context.Context, ownerID uuid.UUID, folderID *uuid.UUID) error {
	if folderID == nil {
		return nil
	}
	_, err := s.getOwnedFolder(ctx, ownerID, *folderID)
	return err
}

// ownsFolder reports whether ownerID owns the folder.
func ownsFolder(ownerID uuid.UUID, folder dbsqlc.Folder) bool {
	return folder.OwnerID == ownerID
}

// ownsFile reports whether ownerID owns the file.
func ownsFile(ownerID uuid.UUID, file dbsqlc.File) bool {
	return file.OwnerID == ownerID
}
