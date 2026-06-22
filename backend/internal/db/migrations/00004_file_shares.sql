-- +goose Up
-- +goose StatementBegin
-- Public share links for individual files. The token is the opaque, URL-safe
-- capability handed out in the link; anyone holding it can read the file's
-- metadata and download it until expires_at passes. A NULL expires_at never
-- expires. Rows are removed when the file is hard-deleted (cascade); a
-- soft-deleted file is excluded by the lookup query so its links go dead.
CREATE TABLE file_shares (
    token      TEXT PRIMARY KEY,
    file_id    UUID NOT NULL REFERENCES files (id) ON DELETE CASCADE,
    created_by UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ
);
-- +goose StatementEnd

-- +goose StatementBegin
CREATE INDEX idx_file_shares_file ON file_shares (file_id);
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP TABLE IF EXISTS file_shares;
-- +goose StatementEnd
