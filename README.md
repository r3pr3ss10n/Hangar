# Hangar 

Your own unlimited cloud drive, backed by Telegram.

[View the drive interface »](docs/screenshot.png)

## Why?

Telegram gives away **unlimited** file storage for free (2 GB per file, 4 GB on Premium), and nobody actually uses it for that, because the only "UI" is dumping files into Saved Messages and never finding them again.

Hangar puts a real drive on top of it: folders, search, previews, sharing. Your files live in a private Telegram channel, you get a proper interface — in the browser or as a native Android app — and you host the whole thing yourself.

## How it works

1. You link one Telegram account - it becomes the storage backend. Every uploaded file is stored as a document in a private channel that account owns. You can archive it. 
2. The Go backend uploads your files to Telegram over several parallel connections and keeps only metadata (names, folders, thumbnails) in Postgres. File bytes are briefly staged on disk during upload to reassemble the parallel streams, then deleted once they reach Telegram - they're never stored on your server.
3. Your files are encrypted before they ever reach Telegram (AES-256-CTR, keyed by your `HANGAR_ENCRYPTION_KEY`), and the documents land in the channel under opaque random names with no MIME type. So even though the storage account isn't yours to fully trust, the channel holds nothing but unlabelled ciphertext - the real names, types and folders live only in your Postgres. The cipher is seekable, so range requests (video seeking, resuming a download) still work, and decryption is hardware-accelerated, so downloads aren't slowed.
4. Downloads are streamed back from Telegram on demand with HTTP range support, so seeking in a video or resuming a download just works.
5. A Nuxt 4 SPA gives you the Drive-style frontend, and Android app talks to the same backend from your phone.

## Features

- End-to-Telegram encryption: file contents are AES-256-CTR encrypted and stored under opaque names, so the storage channel only ever holds unlabelled ciphertext
- Folders, drag-and-drop upload, drag-to-move, grid & list views
- Fuzzy search across your whole drive + a ⌘K command palette
- Image thumbnails and an in-app preview
- Public share links with expiry — anyone can open and download, no account needed
- Share files and folders with other users on your Hangar
- Colored tags to organize everything
- Dark / light theme
- Multi-user with an admin panel
- Native Android app
- More coming?

## Quick start

You'll need Docker, and a Telegram `api_id` / `api_hash` from [my.telegram.org/apps](https://my.telegram.org/apps).

1. Copy the env template:
   ```bash
   cp .env.example .env
   ```
2. Generate a 32-byte key (encrypts your stored files and the Telegram session) and paste it into `HANGAR_ENCRYPTION_KEY`:
   ```bash
   openssl rand -base64 32
   ```
3. Drop your `HANGAR_TG_API_ID` / `HANGAR_TG_API_HASH` into `.env`.
4. Bring it up:
   ```bash
   docker compose up -d --build
   ```
5. Open [localhost:3000](http://localhost:3000), follow the onboarding. Done.

There's a `make` shortcut for everything — run `make help`.

## Local development

Backend and frontend run separately:

```bash
make backend    # Go API on :8080
make frontend   # Nuxt dev server on :3000
```

Postgres comes from `docker compose up -d postgres`. See [.env.example](.env.example) for all config.

## Android app

A native client lives in [`android/`](android) (Kotlin + Jetpack Compose). It talks to the same backend — on first launch you enter your Hangar server's URL, then sign in with your account.

Please notice that setup isn't implemented on Android. Before launching the app you already must have an active admin account. 

Build a debug APK (needs the Android SDK):

```bash
cd android
./gradlew assembleDebug   # app/build/outputs/apk/debug/app-debug.apk
```

Or open the `android/` folder in Android Studio and hit Run.

## Stack

Go + [chi](https://github.com/go-chi/chi) + sqlc/Postgres on the backend, Nuxt 4 + Tailwind on the frontend, Kotlin + Jetpack Compose for the Android app, [gotd](https://github.com/gotd/td) for the Telegram MTProto client.

## Telegram API & Terms of Service

Hangar talks to Telegram through the official MTProto API using **your own** `api_id` / `api_hash` and acts entirely on your behalf, on your own account.

To the best of my understanding this does **not** violate the [Telegram API Terms of Service](https://core.telegram.org/api/terms). That said, Telegram reserves the right to expand or reinterpret these terms at any time, so use Hangar with your own API credentials and at your own discretion.

## License

Licensed under the [GNU Affero General Public License v3.0](LICENSE). In short: you're free to use, modify, and self-host Hangar, but if you run a modified version as a network service, you must make your source available to its users.
