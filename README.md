# Hangar 

Your own unlimited cloud drive, backed by Telegram.

## Why?

Telegram gives away **unlimited** file storage for free (2 GB per file, 4 GB on Premium), and nobody actually uses it for that, because the only "UI" is dumping files into Saved Messages and never finding them again.

Hangar puts a real drive on top of it: folders, search, previews, sharing. Your files live in a private Telegram channel, you get a proper interface, and you host the whole thing yourself.

## How it works

1. You link one Telegram account - it becomes the storage backend. Every uploaded file is stored as a document in a private channel that account owns. You can archive it. 
2. The Go backend streams your uploads straight to Telegram and keeps only metadata (names, folders, thumbnails) in Postgres - the actual bytes never sit on your server.
3. Downloads are streamed back from Telegram on demand with HTTP range support, so seeking in a video or resuming a download just works.
4. A Nuxt 4 SPA gives you the Drive-style frontend.

## Features

- Folders, drag-and-drop upload, drag-to-move, grid & list views
- Fuzzy search across your whole drive + a ⌘K command palette
- Image thumbnails and an in-app preview
- Public share links with expiry — anyone can open and download, no account needed
- Share files and folders with other users on your Hangar
- Colored tags to organize everything
- Dark / light theme
- Multi-user with an admin panel
- More coming?

## Quick start

You'll need Docker, and a Telegram `api_id` / `api_hash` from [my.telegram.org/apps](https://my.telegram.org/apps).

1. Copy the env template:
   ```bash
   cp .env.example .env
   ```
2. Generate a 32-byte key (encrypts the stored Telegram session) and paste it into `HANGAR_ENCRYPTION_KEY`:
   ```bash
   openssl rand -base64 32
   ```
3. Drop your `HANGAR_TG_API_ID` / `HANGAR_TG_API_HASH` into `.env`.
4. Bring it up:
   ```bash
   docker compose up -d --build
   ```
5. Open [localhost:3000](http://localhost:3000), create the admin account, then go to **Admin → Telegram** and link your account. Done.

There's a `make` shortcut for everything — run `make help`.

## Local development

Backend and frontend run separately:

```bash
make backend    # Go API on :8080
make frontend   # Nuxt dev server on :3000
```

Postgres comes from `docker compose up -d postgres`. See [.env.example](.env.example) for all config.

## Stack

Go + [chi](https://github.com/go-chi/chi) + sqlc/Postgres on the backend, Nuxt 4 + Tailwind on the frontend, [gotd](https://github.com/gotd/td) for the Telegram MTProto client.

## Telegram API & Terms of Service

Hangar talks to Telegram through the official MTProto API using **your own** `api_id` / `api_hash` and acts entirely on your behalf, on your own account.

To the best of my understanding this does **not** violate the [Telegram API Terms of Service](https://core.telegram.org/api/terms). That said, Telegram reserves the right to expand or reinterpret these terms at any time, so use Hangar with your own API credentials and at your own discretion.

## License

Licensed under the [GNU Affero General Public License v3.0](LICENSE). In short: you're free to use, modify, and self-host Hangar, but if you run a modified version as a network service, you must make your source available to its users.
