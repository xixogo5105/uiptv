# Release Notes

> [!WARNING]
> Before upgrading to 0.1.9, back up your database files first (at minimum `uiptv.db`, and preferably `uiptv.db`, `uiptv.db-wal`, and `uiptv.db-shm` while UIPTV is closed).

Default DB path:
- Windows/macOS: `<user.home>/uiptv/uiptv.db` (for example `C:\Users\<you>\uiptv\uiptv.db` or `/Users/<you>/uiptv/uiptv.db`)
- Linux: `<user.home>/.config/uiptv/uiptv.db`

If `uiptv.ini` exists at `<user.home>/uiptv.ini` and contains `db.path=...`, that configured value is used instead of the default path.

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
- No release/tag actions were performed while preparing these notes.
