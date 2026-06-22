-- name: CreateFolder :one
INSERT INTO folders (owner_id, parent_id, name)
VALUES ($1, $2, $3)
RETURNING *;

-- name: GetFolder :one
SELECT * FROM folders WHERE id = $1;

-- name: ListFolders :many
SELECT * FROM folders
WHERE owner_id = $1
  AND parent_id IS NOT DISTINCT FROM sqlc.narg('parent_id')::uuid
ORDER BY name ASC;

-- name: ListAllFoldersForOwner :many
SELECT * FROM folders WHERE owner_id = $1 ORDER BY name ASC;

-- name: SearchFolders :many
-- Fuzzy, typo-tolerant search over the owner's folder names. Direct substring
-- (ILIKE) hits rank first, then trigram word-similarity hits, then name.
SELECT * FROM folders
WHERE owner_id = @owner_id
  AND (name ILIKE '%' || @query::text || '%'
       OR word_similarity(@query::text, name) > 0.3)
ORDER BY
  (name ILIKE '%' || @query::text || '%') DESC,
  word_similarity(@query::text, name) DESC,
  name ASC
LIMIT @result_limit;

-- name: RenameFolder :exec
UPDATE folders SET name = $2 WHERE id = $1;

-- name: MoveFolder :exec
UPDATE folders SET parent_id = sqlc.narg('parent_id')::uuid WHERE id = $1;

-- name: DeleteFolder :exec
DELETE FROM folders WHERE id = $1;
