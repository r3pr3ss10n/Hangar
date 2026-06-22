-- name: GetTelegramAccount :one
SELECT * FROM telegram_account WHERE id = 1;

-- name: GetTelegramSessionBlob :one
SELECT session_blob_encrypted FROM telegram_account WHERE id = 1;

-- name: SetTelegramSessionBlob :exec
UPDATE telegram_account
SET session_blob_encrypted = $1,
    updated_at = now()
WHERE id = 1;

-- name: SetTelegramLinked :exec
UPDATE telegram_account
SET tg_user_id_hash     = $1,
    channel_id          = $2,
    channel_access_hash = $3,
    dc_id               = $4,
    is_premium          = $5,
    status              = 'linked',
    updated_at          = now()
WHERE id = 1;

-- name: ResetTelegramAccount :exec
UPDATE telegram_account
SET session_blob_encrypted = NULL,
    tg_user_id_hash        = NULL,
    channel_id             = NULL,
    channel_access_hash    = NULL,
    dc_id                  = NULL,
    is_premium             = false,
    status                 = 'not_linked',
    updated_at             = now()
WHERE id = 1;
