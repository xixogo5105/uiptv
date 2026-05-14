# UIPTV Mobile Portability Spike

This records the Phase 0 split for Android reuse.

## Directly Portable

- Account type names and shared enum values.
- Remote sync direction, status, options, progress, and session DTO shapes.
- Syncable table list for Android pull: `Account`, `AccountInfo`, `Bookmark`, `BookmarkCategory`, `BookmarkOrder`.
- Migration ordering from `core/src/main/resources/db/migrations/migrations.txt`.
- SQL migration text where Android SQLite supports the statement.

## Portable Behind Adapters

- SQLite migrations and table sync, with Android using `SQLiteOpenHelper` and `SQLiteDatabase` instead of JDBC.
- Remote sync HTTP calls, with Android using platform HTTP/client code instead of Apache HttpClient.
- Cache refresh provider logic, once HTTP, file, database, logging, and background-job dependencies are extracted.
- Playback resolution, once stream opening is separated from desktop player and server behavior.
- Preferences that overlap conceptually with desktop configuration, provided Android-only values stay outside the UIPTV database.

## Desktop-Only For Mobile V1

- Undertow API server and web/PWA assets.
- JavaFX UI, desktop dialogs, desktop tray/window behavior, and VLC desktop player settings.
- Desktop external player paths and FFmpeg desktop settings.
- Public web server configuration, server startup preferences, theme CSS overrides, parental/filter lock UI.
- Android-to-desktop export and any Android-hosted remote sync server.
