-- +goose Up
-- +goose StatementBegin
-- Internal user-to-user access grants. Each row grants one recipient view (or,
-- in future, edit) access to exactly one file OR one folder; a folder grant
-- covers its whole subtree, resolved at access time by walking parent_id. This
-- is distinct from the public token links in file_shares.
CREATE TABLE resource_grants (
    id           UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    file_id      UUID REFERENCES files (id) ON DELETE CASCADE,
    folder_id    UUID REFERENCES folders (id) ON DELETE CASCADE,
    recipient_id UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    granted_by   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    permission   TEXT NOT NULL DEFAULT 'view' CHECK (permission IN ('view', 'edit')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- Exactly one target: a file or a folder, never both or neither.
    CHECK ((file_id IS NOT NULL) <> (folder_id IS NOT NULL)),
    UNIQUE (file_id, recipient_id),
    UNIQUE (folder_id, recipient_id)
);
-- +goose StatementEnd

-- +goose StatementBegin
CREATE INDEX idx_grants_recipient ON resource_grants (recipient_id);
-- +goose StatementEnd
-- +goose StatementBegin
CREATE INDEX idx_grants_file ON resource_grants (file_id);
-- +goose StatementEnd
-- +goose StatementBegin
CREATE INDEX idx_grants_folder ON resource_grants (folder_id);
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP TABLE IF EXISTS resource_grants;
-- +goose StatementEnd
