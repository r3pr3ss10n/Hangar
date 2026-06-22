-- +goose Up
-- +goose StatementBegin
-- A user's starred files/folders. Kept in a side table (not a column on
-- files/folders) so the existing listing queries/models are untouched; the UI
-- loads the favourite id set once and marks items by id.
CREATE TABLE favorites (
    user_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_id   UUID REFERENCES files (id) ON DELETE CASCADE,
    folder_id UUID REFERENCES folders (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((file_id IS NOT NULL) <> (folder_id IS NOT NULL)),
    UNIQUE (user_id, file_id),
    UNIQUE (user_id, folder_id)
);
-- +goose StatementEnd
-- +goose StatementBegin
CREATE INDEX idx_favorites_user ON favorites (user_id);
-- +goose StatementEnd

-- +goose StatementBegin
-- User-owned, colour-coded labels.
CREATE TABLE tags (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    color      TEXT NOT NULL DEFAULT 'slate',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (owner_id, name)
);
-- +goose StatementEnd

-- +goose StatementBegin
-- Tag assignments to a file or a folder (exactly one target).
CREATE TABLE resource_tags (
    tag_id    UUID NOT NULL REFERENCES tags (id) ON DELETE CASCADE,
    file_id   UUID REFERENCES files (id) ON DELETE CASCADE,
    folder_id UUID REFERENCES folders (id) ON DELETE CASCADE,
    CHECK ((file_id IS NOT NULL) <> (folder_id IS NOT NULL)),
    UNIQUE (tag_id, file_id),
    UNIQUE (tag_id, folder_id)
);
-- +goose StatementEnd
-- +goose StatementBegin
CREATE INDEX idx_resource_tags_tag ON resource_tags (tag_id);
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP TABLE IF EXISTS resource_tags;
-- +goose StatementEnd
-- +goose StatementBegin
DROP TABLE IF EXISTS tags;
-- +goose StatementEnd
-- +goose StatementBegin
DROP TABLE IF EXISTS favorites;
-- +goose StatementEnd
