-- name: CreateFile :one
INSERT INTO files (
    owner_id, folder_id, name, size, mime, sha256,
    tg_message_id, tg_document_id, tg_access_hash, tg_file_reference, tg_dc_id, thumb_ref,
    enc_iv
) VALUES (
    $1, $2, $3, $4, $5, $6,
    $7, $8, $9, $10, $11, $12,
    $13
)
RETURNING *;

-- name: GetFile :one
SELECT * FROM files WHERE id = $1 AND deleted_at IS NULL;

-- name: ListFiles :many
-- Lists files without ever reading the (TOASTed) thumbnail bytes: thumb_ref is
-- collapsed to a 1-byte presence marker via IS NULL (which never detoasts), so a
-- folder of images is not dragged off disk just to compute has_thumb.
SELECT
    id, owner_id, folder_id, name, size, mime, sha256,
    tg_message_id, tg_document_id, tg_access_hash, tg_file_reference, tg_dc_id,
    CASE WHEN thumb_ref IS NULL THEN NULL ELSE '\x01'::bytea END AS thumb_ref,
    created_at, deleted_at
FROM files
WHERE owner_id = $1
  AND folder_id IS NOT DISTINCT FROM sqlc.narg('folder_id')::uuid
  AND deleted_at IS NULL
ORDER BY name ASC;

-- name: SearchFiles :many
-- Fuzzy, typo-tolerant search over the owner's live file names. Direct substring
-- (ILIKE) hits rank first, then trigram word-similarity hits, then name. The
-- trigram GIN index backs both the ILIKE and word_similarity predicates. As with
-- ListFiles, thumb_ref is collapsed to a presence marker so the (TOASTed) bytes
-- are never read just to populate has_thumb.
SELECT
    id, owner_id, folder_id, name, size, mime, sha256,
    tg_message_id, tg_document_id, tg_access_hash, tg_file_reference, tg_dc_id,
    CASE WHEN thumb_ref IS NULL THEN NULL ELSE '\x01'::bytea END AS thumb_ref,
    created_at, deleted_at
FROM files
WHERE owner_id = @owner_id
  AND deleted_at IS NULL
  AND (name ILIKE '%' || @query::text || '%'
       OR word_similarity(@query::text, name) > 0.3)
ORDER BY
  (name ILIKE '%' || @query::text || '%') DESC,
  word_similarity(@query::text, name) DESC,
  name ASC
LIMIT @result_limit;

-- name: RenameFile :exec
UPDATE files SET name = $2 WHERE id = $1 AND deleted_at IS NULL;

-- name: MoveFile :exec
UPDATE files SET folder_id = sqlc.narg('folder_id')::uuid WHERE id = $1 AND deleted_at IS NULL;

-- name: UpdateFileReference :exec
UPDATE files
SET tg_file_reference = $2, tg_access_hash = $3, tg_dc_id = $4
WHERE id = $1;

-- name: SoftDeleteFile :exec
UPDATE files SET deleted_at = now() WHERE id = $1 AND deleted_at IS NULL;

-- name: ListDeletedFiles :many
SELECT * FROM files WHERE deleted_at IS NOT NULL ORDER BY deleted_at ASC;

-- name: HardDeleteFile :exec
DELETE FROM files WHERE id = $1;

-- name: StorageUsedByOwner :one
SELECT COALESCE(sum(size), 0)::bigint FROM files
WHERE owner_id = $1 AND deleted_at IS NULL;
