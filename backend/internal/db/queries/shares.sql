-- name: CreateFileShare :one
INSERT INTO file_shares (token, file_id, created_by, expires_at)
VALUES ($1, $2, $3, $4)
RETURNING *;

-- name: ListFileSharesByFile :many
SELECT * FROM file_shares WHERE file_id = $1 ORDER BY created_at DESC;

-- name: GetShareFile :one
-- Resolves a share token to its (live) file row, plus the share's expiry so the
-- caller can reject an expired link. Soft-deleted files are excluded, so their
-- links 404.
SELECT f.*, s.expires_at AS share_expires_at
FROM file_shares s
JOIN files f ON f.id = s.file_id
WHERE s.token = $1 AND f.deleted_at IS NULL;

-- name: DeleteFileShareOwned :execrows
-- Deletes a share only when the requesting user owns the underlying file.
-- Returns the affected row count so the caller can 404 an unknown/foreign token.
DELETE FROM file_shares
USING files
WHERE file_shares.token = $1
  AND file_shares.file_id = files.id
  AND files.owner_id = $2;
