# What's New

This file tracks current product highlights without pinning the content to a single hardcoded release number.

## Current Highlights

- **Desktop + web parity**: Live TV, VOD, Series, bookmarks, watching-now state, and published playlists are available through both the JavaFX app and the local-network web UI.
- **Localized UI**: Multiple UI languages are bundled, including RTL support and localized season/episode/tab numbering for supported locales.
- **Theme controls**: The desktop app supports built-in light/dark themes, saved zoom scaling, and custom CSS overrides for each theme.
- **Playback routing**: Embedded playback, external players, and a web-browser player path are all available from the current playback flows.
- **Import workflow improvements**: Stalker, M3U, and Xtreme bulk import behavior is documented and aligned with the current parser implementation.
- **Metadata and resume flows**: Watching-now state, series progress, and richer metadata handling continue to evolve across desktop and web views.

## Operational Notes

> [!WARNING]
> Before upgrading, back up your database files first. At minimum keep `uiptv.db`, and preferably `uiptv.db`, `uiptv.db-wal`, and `uiptv.db-shm` while UIPTV is closed.

Default DB path:
- Windows/macOS: `<user.home>/uiptv/uiptv.db`
- Linux: `<user.home>/.config/uiptv/uiptv.db`

If `uiptv.ini` exists at `<user.home>/uiptv.ini` and contains `db.path=...`, that configured value is used instead of the default path.
