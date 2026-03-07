# Release Notes

> [!WARNING]
> Before upgrading to 0.1.10, back up your database files first (at minimum `uiptv.db`, and preferably `uiptv.db`, `uiptv.db-wal`, and `uiptv.db-shm` while UIPTV is closed).

> [!NOTE]
> This release includes multi-language support. At this stage, non-English resources were generated with AI-assisted translation and may contain inaccuracies. Contributions and corrections are welcome: [TRANSLATIONS.md](TRANSLATIONS.md).

Default DB path:
- Windows/macOS: `<user.home>/uiptv/uiptv.db` (for example `C:\Users\<you>\uiptv\uiptv.db` or `/Users/<you>/uiptv/uiptv.db`)
- Linux: `<user.home>/.config/uiptv/uiptv.db`

If `uiptv.ini` exists at `<user.home>/uiptv.ini` and contains `db.path=...`, that configured value is used instead of the default path.

## 0.1.10

### Highlights Since `v0.1.9`

- Added localized UI improvements with RTL support and localized season/episode/tab numbering where supported.
- Expanded theme customization with saved zoom scaling, live preview, and per-theme CSS override support (with template export flow).
- Improved desktop + web parity for bookmarks, Watching Now data, and series resume continuity.
- Added/expanded browser playback route support alongside embedded and external player flows, including DRM-aware payload fields when available.
- Refined series/watch-state behavior for more reliable resume and cross-view consistency.
- Refreshed Stalker, Xtreme, and M3U import flows/documentation to align with current parser behavior and account handling.
- Improved account maintenance workflows (including MAC verification/management and account-level reload paths).
- Continued web-server workflow improvements for local-network playback and playlist publishing use cases.

### Packaging, Build, and Platform Notes

- Continued publishing native targets for Windows (x86_64), Linux (x86_64), and macOS (x86_64/aarch64), with Linux `.deb`, optional `.rpm`, and portable app bundle outputs.
- Clarified Windows installer build prerequisite: WiX Toolset must be installed and on `PATH` for `jpackage` `.exe`/`.msi` builds (`candle.exe`, `light.exe`).
- Added explicit build note that Windows ARM64 (`win-aarch64`) packaging currently depends on unavailable JavaFX Maven artifacts.

## 0.1.9

### Highlights Since `v0.1.8`

- Added a full web app refresh (SPA + PWA style flow) and expanded web API endpoints.
- Added in-memory HLS/TS streaming support and related FFmpeg-backed web playback paths.
- Added DRM-related playback metadata handling for web playback flows.
- Added M3U/M3U8 publication flow for combining local/remote playlists into downloadable output.
- Expanded Stalker/Xtreme parsing with improved attribute detection (serial/device/signature), account isolation, and MAC management improvements.
- Added VOD/Series data model and persistence layers (new DB tables/migrations, watch-state tracking, and richer category/channel structures).
- Added wide/embedded player UX improvements, including placeholder state, overlay behavior tweaks, and restart-required prompts when needed.
- Added keyboard/accessibility improvements (Enter-key reliability, tab focus in Watching Now/episodes, and better actions discoverability).
- Improved bookmarks and watching-now flows (matching reliability, remove actions, reduced UI flicker on refresh, and state preservation).

### Build, CI, and Maintenance

- Updated GitHub Actions workflows and dependency update automation.
- Continued dependency updates across Maven and GitHub Actions.
- Added and updated tests.

### Notes

- This release includes a large internal refactor and database migration set since `v0.1.8`.
