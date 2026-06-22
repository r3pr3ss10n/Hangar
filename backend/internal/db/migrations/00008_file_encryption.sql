-- +goose Up
-- +goose StatementBegin
-- Per-file IV for at-rest encryption of the bytes stored in Telegram. NULL marks
-- a legacy file uploaded before encryption was introduced: it is stored in
-- cleartext and read back without decryption. A non-NULL 16-byte IV marks an
-- AES-256-CTR-encrypted file (key = application encryption key).
ALTER TABLE files ADD COLUMN enc_iv BYTEA;
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
ALTER TABLE files DROP COLUMN IF EXISTS enc_iv;
-- +goose StatementEnd
