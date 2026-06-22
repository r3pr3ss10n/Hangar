-- +goose Up
-- +goose StatementBegin
CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    role          TEXT NOT NULL CHECK (role IN ('admin', 'user')),
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- +goose StatementEnd

-- +goose StatementBegin
-- App login sessions. The primary key is the SHA-256 hex of the opaque cookie
-- token, never the token itself, so a database leak cannot resurrect live
-- sessions.
CREATE TABLE sessions (
    id         TEXT PRIMARY KEY,
    user_id    UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at TIMESTAMPTZ NOT NULL
);
-- +goose StatementEnd

-- +goose StatementBegin
CREATE INDEX idx_sessions_user ON sessions (user_id);
-- +goose StatementEnd

-- +goose StatementBegin
-- The single linked Telegram account that backs storage for every app user.
-- Enforced as a singleton via the id = 1 check.
CREATE TABLE telegram_account (
    id                     INT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
    session_blob_encrypted BYTEA,
    tg_user_id_hash        TEXT,
    channel_id             BIGINT,
    channel_access_hash    BIGINT,
    dc_id                  INT,
    is_premium             BOOLEAN NOT NULL DEFAULT false,
    status                 TEXT NOT NULL DEFAULT 'not_linked'
        CHECK (status IN ('not_linked', 'linked')),
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- +goose StatementEnd

-- +goose StatementBegin
INSERT INTO telegram_account (id) VALUES (1);
-- +goose StatementEnd

-- +goose StatementBegin
CREATE TABLE folders (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id   UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    parent_id  UUID REFERENCES folders (id) ON DELETE CASCADE,
    name       TEXT NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- +goose StatementEnd

-- +goose StatementBegin
CREATE INDEX idx_folders_owner_parent ON folders (owner_id, parent_id);
-- +goose StatementEnd

-- +goose StatementBegin
CREATE TABLE files (
    id                UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    owner_id          UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    folder_id         UUID REFERENCES folders (id) ON DELETE SET NULL,
    name              TEXT NOT NULL,
    size              BIGINT NOT NULL,
    mime              TEXT NOT NULL,
    sha256            TEXT NOT NULL,
    tg_message_id     BIGINT NOT NULL,
    tg_document_id    BIGINT NOT NULL,
    tg_access_hash    BIGINT NOT NULL,
    tg_file_reference BYTEA NOT NULL,
    tg_dc_id          INT NOT NULL,
    thumb_ref         BYTEA,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    deleted_at        TIMESTAMPTZ
);
-- +goose StatementEnd

-- +goose StatementBegin
CREATE INDEX idx_files_owner_folder ON files (owner_id, folder_id)
    WHERE deleted_at IS NULL;
-- +goose StatementEnd

-- +goose Down
-- +goose StatementBegin
DROP TABLE IF EXISTS files;
-- +goose StatementEnd
-- +goose StatementBegin
DROP TABLE IF EXISTS folders;
-- +goose StatementEnd
-- +goose StatementBegin
DROP TABLE IF EXISTS telegram_account;
-- +goose StatementEnd
-- +goose StatementBegin
DROP TABLE IF EXISTS sessions;
-- +goose StatementEnd
-- +goose StatementBegin
DROP TABLE IF EXISTS users;
-- +goose StatementEnd
