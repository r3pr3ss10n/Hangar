-- name: CreateFileGrant :one
INSERT INTO resource_grants (file_id, recipient_id, granted_by, permission)
VALUES ($1, $2, $3, $4)
ON CONFLICT (file_id, recipient_id) DO UPDATE SET permission = EXCLUDED.permission
RETURNING *;

-- name: CreateFolderGrant :one
INSERT INTO resource_grants (folder_id, recipient_id, granted_by, permission)
VALUES ($1, $2, $3, $4)
ON CONFLICT (folder_id, recipient_id) DO UPDATE SET permission = EXCLUDED.permission
RETURNING *;

-- name: ListFileGrants :many
SELECT g.*, u.username AS recipient_username
FROM resource_grants g
JOIN users u ON u.id = g.recipient_id
WHERE g.file_id = $1
ORDER BY u.username;

-- name: ListFolderGrants :many
SELECT g.*, u.username AS recipient_username
FROM resource_grants g
JOIN users u ON u.id = g.recipient_id
WHERE g.folder_id = $1
ORDER BY u.username;

-- name: DeleteFileGrant :execrows
DELETE FROM resource_grants WHERE file_id = $1 AND recipient_id = $2;

-- name: DeleteFolderGrant :execrows
DELETE FROM resource_grants WHERE folder_id = $1 AND recipient_id = $2;

-- name: ListSharedFoldersForRecipient :many
SELECT f.*, u.username AS owner_username
FROM resource_grants g
JOIN folders f ON f.id = g.folder_id
JOIN users u ON u.id = f.owner_id
WHERE g.recipient_id = $1
ORDER BY f.name;

-- name: ListSharedFilesForRecipient :many
-- Files granted directly to the recipient. As with ListFiles, thumb_ref is
-- collapsed to a presence marker so the TOASTed bytes are never read here.
SELECT
    fi.id, fi.owner_id, fi.folder_id, fi.name, fi.size, fi.mime, fi.sha256,
    fi.tg_message_id, fi.tg_document_id, fi.tg_access_hash, fi.tg_file_reference, fi.tg_dc_id,
    CASE WHEN fi.thumb_ref IS NULL THEN NULL ELSE '\x01'::bytea END AS thumb_ref,
    fi.created_at, fi.deleted_at,
    u.username AS owner_username
FROM resource_grants g
JOIN files fi ON fi.id = g.file_id
JOIN users u ON u.id = fi.owner_id
WHERE g.recipient_id = $1 AND fi.deleted_at IS NULL
ORDER BY fi.name;

-- name: ListSubfolders :many
-- Children of a folder, not scoped to the caller's ownership — used inside an
-- already access-checked shared folder.
SELECT * FROM folders WHERE parent_id = $1 ORDER BY name;

-- name: ListFolderFiles :many
-- Non-deleted files directly under a folder, not owner-scoped (see ListSubfolders).
-- thumb_ref collapsed to a presence marker as in ListFiles.
SELECT
    id, owner_id, folder_id, name, size, mime, sha256,
    tg_message_id, tg_document_id, tg_access_hash, tg_file_reference, tg_dc_id,
    CASE WHEN thumb_ref IS NULL THEN NULL ELSE '\x01'::bytea END AS thumb_ref,
    created_at, deleted_at
FROM files
WHERE folder_id = $1 AND deleted_at IS NULL
ORDER BY name;

-- name: FolderSharedToRecipient :one
-- True when the folder, or any of its ancestors, is granted to the recipient.
WITH RECURSIVE chain AS (
    SELECT f.id, f.parent_id FROM folders f WHERE f.id = $1
    UNION ALL
    SELECT p.id, p.parent_id FROM folders p JOIN chain c ON p.id = c.parent_id
)
SELECT EXISTS (
    SELECT 1 FROM resource_grants g
    JOIN chain c ON g.folder_id = c.id
    WHERE g.recipient_id = $2
);

-- name: FileSharedToRecipient :one
-- True when the file is granted directly, or its folder (or any ancestor) is.
WITH RECURSIVE chain AS (
    SELECT fo.id, fo.parent_id
    FROM files fi JOIN folders fo ON fo.id = fi.folder_id
    WHERE fi.id = $1
    UNION ALL
    SELECT p.id, p.parent_id FROM folders p JOIN chain c ON p.id = c.parent_id
)
SELECT EXISTS (
    SELECT 1 FROM resource_grants g WHERE g.file_id = $1 AND g.recipient_id = $2
    UNION ALL
    SELECT 1 FROM resource_grants g JOIN chain c ON g.folder_id = c.id WHERE g.recipient_id = $2
);
