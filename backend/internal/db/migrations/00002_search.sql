-- +goose Up
-- +goose StatementBegin
-- pg_trgm powers fuzzy, typo-tolerant substring matching over names.
CREATE EXTENSION IF NOT EXISTS pg_trgm;
-- +goose StatementEnd

-- +goose StatementBegin
-- GIN trigram index over file names: accelerates both ILIKE '%...%' substring
-- scans and the word_similarity (<%) fuzzy operator used by search. Scoped to
-- live rows since soft-deleted files are never searched.
CREATE INDEX idx_files_name_trgm ON files USING gin (name gin_trgm_ops)
    WHERE deleted_at IS NULL;
-- +goose StatementEnd

-- +goose StatementBegin
CREATE INDEX idx_folders_name_trgm ON folders USING gin (name gin_trgm_ops);
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP INDEX IF EXISTS idx_folders_name_trgm;
-- +goose StatementEnd
-- +goose StatementBegin
DROP INDEX IF EXISTS idx_files_name_trgm;
-- +goose StatementEnd
-- +goose StatementBegin
DROP EXTENSION IF EXISTS pg_trgm;
-- +goose StatementEnd
