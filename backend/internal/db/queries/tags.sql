-- name: CreateTag :one
INSERT INTO tags (owner_id, name, color) VALUES ($1, $2, $3)
RETURNING *;

-- name: ListTags :many
-- Owner's tags with how many live items carry each (for the sidebar list).
-- Soft-deleted files keep their resource_tags row (a soft delete is an UPDATE,
-- so the ON DELETE CASCADE never fires), so they must be excluded from the count;
-- folders are hard-deleted, so their associations cascade away and always count.
SELECT t.*, COUNT(rt.tag_id) AS item_count
FROM tags t
LEFT JOIN resource_tags rt ON rt.tag_id = t.id
    AND (rt.file_id IS NULL OR EXISTS (
        SELECT 1 FROM files f WHERE f.id = rt.file_id AND f.deleted_at IS NULL))
WHERE t.owner_id = $1
GROUP BY t.id
ORDER BY t.name;

-- name: GetTag :one
SELECT * FROM tags WHERE id = $1;

-- name: UpdateTag :exec
UPDATE tags SET name = $2, color = $3 WHERE id = $1;

-- name: DeleteTag :exec
DELETE FROM tags WHERE id = $1;

-- name: AddFileTag :exec
INSERT INTO resource_tags (tag_id, file_id) VALUES ($1, $2)
ON CONFLICT (tag_id, file_id) DO NOTHING;

-- name: RemoveFileTag :execrows
DELETE FROM resource_tags WHERE tag_id = $1 AND file_id = $2;

-- name: AddFolderTag :exec
INSERT INTO resource_tags (tag_id, folder_id) VALUES ($1, $2)
ON CONFLICT (tag_id, folder_id) DO NOTHING;

-- name: RemoveFolderTag :execrows
DELETE FROM resource_tags WHERE tag_id = $1 AND folder_id = $2;

-- name: ListTagAssignments :many
-- Every (tag -> file/folder) assignment for the owner's tags — drives the
-- client-side tag-badge lookup across listings.
SELECT rt.tag_id, rt.file_id, rt.folder_id
FROM resource_tags rt
JOIN tags t ON t.id = rt.tag_id
WHERE t.owner_id = $1;

-- name: ListTagFolders :many
SELECT f.* FROM resource_tags rt
JOIN folders f ON f.id = rt.folder_id
WHERE rt.tag_id = $1
ORDER BY f.name;

-- name: ListTagFiles :many
SELECT
    fi.id, fi.owner_id, fi.folder_id, fi.name, fi.size, fi.mime, fi.sha256,
    fi.tg_message_id, fi.tg_document_id, fi.tg_access_hash, fi.tg_file_reference, fi.tg_dc_id,
    CASE WHEN fi.thumb_ref IS NULL THEN NULL ELSE '\x01'::bytea END AS thumb_ref,
    fi.created_at, fi.deleted_at
FROM resource_tags rt
JOIN files fi ON fi.id = rt.file_id
WHERE rt.tag_id = $1 AND fi.deleted_at IS NULL
ORDER BY fi.name;
