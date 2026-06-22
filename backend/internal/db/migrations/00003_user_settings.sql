-- +goose Up
-- +goose StatementBegin
-- Per-user preference: whether image uploads get a generated preview thumbnail.
-- Defaults to true so existing behaviour is unchanged.
ALTER TABLE users ADD COLUMN generate_thumbnails BOOLEAN NOT NULL DEFAULT true;
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
ALTER TABLE users DROP COLUMN generate_thumbnails;
-- +goose StatementEnd
