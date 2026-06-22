-- +goose Up
-- +goose StatementBegin
-- Favourites were folded into tags; drop the unused table.
DROP TABLE IF EXISTS favorites;
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
CREATE TABLE favorites (
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    file_id    UUID REFERENCES files (id) ON DELETE CASCADE,
    folder_id  UUID REFERENCES folders (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CHECK ((file_id IS NOT NULL) <> (folder_id IS NOT NULL)),
    UNIQUE (user_id, file_id),
    UNIQUE (user_id, folder_id)
);
-- +goose StatementEnd
