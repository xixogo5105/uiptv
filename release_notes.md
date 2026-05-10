# Release Notes

> [!WARNING]
> Before upgrading to 0.1.11, back up your database files first (at minimum `uiptv.db`, and preferably `uiptv.db`, `uiptv.db-wal`, and `uiptv.db-shm` while UIPTV is closed).

> [!NOTE]
> This release includes multi-language support. At this stage, non-English resources were generated with AI-assisted translation and may contain inaccuracies. Contributions and corrections are welcome: [TRANSLATIONS.md](TRANSLATIONS.md).

Default DB path:
- Windows/macOS: `<user.home>/uiptv/uiptv.db` (for example `C:\Users\<you>\uiptv\uiptv.db` or `/Users/<you>/uiptv/uiptv.db`)
- Linux: `<user.home>/.config/uiptv/uiptv.db`

If `uiptv.ini` exists at `<user.home>/uiptv.ini` and contains `db.path=...`, that configured value is used instead of the default path.

## 0.1.11

Release date: 2026-05-10

### Highlights

This release includes 328 commits with 54,241 additions and 11,297 deletions across 453 files since `v0.1.10`, focusing on parental lock, remote database sync, published M3U controls, HLS/VLC playback fixes, series binge-watch support, and a broad round of maintenance work.

### Parental Lock & Remote Sync

- Added parental lock password flows for protected filter management and Stalker censored content
- Added approval-based remote database sync alongside existing local sync workflow
- Restored one-way database sync and synchronized configuration tables during import/export
- Show completion dialogs on both sync peers
- Refresh app state after database sync completion
- Improve configuration sync feedback and server startup option
- Fix database sync popup progress flow

### M3U Publishing & Cache

- Expanded published M3U selection to account/category/channel hierarchy
- Add published M3U category modes, request-host playlist URLs, and collapse single published M3U categories
- Optimize remote M3U cache reloads and refactor M3U publication helpers
- Fix M3U category parsing and reload behavior
- Fix case-insensitive cache category matching
- Remap published M3U selections to target accounts during sync
- Add account-level redirect resolution and hierarchical M3U publish selection
- Add unit tests for M3uCacheReloader edge cases

### HLS/Player Improvements

- Add series binge-watch playback flow with local playlist/session handling and improved player support for episode-to-episode playback
- Fix embedded VLC playback for HLS/M3U8 streams (Cloudfront and others)
- Add SSL/cookie forwarding and redirect handling for VLC HLS/M3U8 playback
- Improve HLS manifest resolution and handle redirects correctly
- Refactor HLS resolver and align bookmark playlist grouping
- Fix bookmark entry playback redirects
- Add redirect-resolution toggle, safeguards, and contextual help links
- Add zoom-fill mode and fix initial VLC volume mapping
- Retry VLC audio state during startup and reapply when playback starts
- Serialize VLC start and teardown to prevent race conditions
- Fix CloudFront Lambda-protected HLS stream playback in VLC
- Refactor video player lifecycle and resource disposal
- Refine player configuration layout and shorten embedded player labels

### Stalker & Import

- Fix Stalker bulk-import grouping semantics:
  - MAC-only entries group by portal URL
  - entries with extra parameters stay distinct unless device identity matches
- Apply `1/2` device IDs to both Stalker device slots and avoid duplicate account creation
- Improve Stalker expiry handling and display
- Add parental lock password flow and Stalker diagnostics
- Resolve 'null' error when loading 'All' category for Stalker/Xtreme accounts

### UI, Updates & Workflow

- Add a custom update window backed by GitHub Releases
- Refresh About and update dialog layouts so they scale better across resolutions
- Add watching-now snapshot fallback and tab-scoped refresh controls
- Add multi-select bookmark actions in channel lists
- Refresh bookmarks after account removal and concurrent bookmark updates
- Show account names in category lists where needed

### Documentation Notes

- Refresh post-`v0.1.10` import and parental lock documentation
- Update README and website copy for remote sync, published playlists, and current Stalker grouping rules

### Misc Bug Fixes & Refactoring

- Refactor M3uCacheReloader and HLS playlist resolution logic to reduce complexity
- Clean up duplicated literals, switch logic, lambdas, and related helper code
- Improve SQLite/database-related maintenance and internal consistency
- Resolve smaller code smells, test smells, and unused imports/variables

### Internationalization

- Translate player help text bundles
- Restore bundle UTF-8 and fix Urdu episode matching
- Escape single quotes in message properties for MessageFormat
- Localize player help links
- Fix i18n bundle UTF-8 issues

### Build & Dependencies

- Bump javafx.version from 26 to 26.0.1
- Bump org.projectlombok:lombok from 1.18.44 to 1.18.46
- Bump commons-io:commons-io from 2.21.0 to 2.22.0
- Bump org.apache.httpcomponents.client5:httpclient5 from 5.6 to 5.6.1
- Run Linux CI builds under xvfb
- Fix Windows remote-sync temp-file handling for release/test reliability
- Add .mvn/wrapper/maven-wrapper.properties
- Remove .idea folder from tracking
