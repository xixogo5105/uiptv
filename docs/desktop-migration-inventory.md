# Desktop Migration Inventory

This inventory records the JavaFX-to-Flutter desktop migration state for the
`flutter` branch. It is an implementation baseline, not a proposal.

## Migration progress

- **Step 1 — inventory:** complete; this document records current workflows and
  their core dependencies.
- **Step 2 — local API:** partial; loopback/auth, catalogue/playback, Watching
  Now, category-aware bookmarks and Recent, bulk import, desktop settings,
  filter-lock, logs, About, cache-reload jobs, and localized-label routes exist.
  Account management, full API coverage, and OpenAPI remain.
- **Step 3 — JavaFX API client:** not started; JavaFX still calls core services.
- **Step 4 — Flutter UI:** partial; the shell includes live account/catalogue
  browsing, category-aware Favourites with separate Recent history and search,
  Watching Now resume/episode playback, bulk import with optional cache-reload
  handoff, settings, filter lock,
  logs, About, database backup/restore, and cache reload. Complete account management,
  full VOD/series/EPG workflows, and details of category rename/reorder remain.
- **Step 5 — embedded libmpv:** partial; macOS builds and HLS decode evidence
  exist, but required playback acceptance and platform packaging are incomplete.
- **Step 6 — distribution/retirement:** not started; the JavaFX rollback remains
  and must not be removed before feature parity.

## Toolchain and repository baseline

- Repository root: Maven reactor with Java 25 and Maven 3.8.8+ enforcement.
- Reactor modules:
  - `core`: domain, database, migrations, integrations, application services.
  - `api-server`: Jetty 12 server and existing JSON/web endpoints.
  - `javafx-app`: temporary desktop UI and VLCJ desktop player.
  - `lightweight-ui`: server-only lightweight JavaFX window.
  - `coverage-aggregate`: JaCoCo aggregate reports.
- Existing desktop packaging: `jpackage` Maven profiles for Windows x86_64/ARM64,
  macOS x86_64/ARM64, and Linux x86_64/ARM64 plus DEB/RPM/app-image targets.
- Existing CI: `.github/workflows/build.yml` has native OS runners.
- Baseline command: `./mvnw test`
- Baseline result on 2026-11-26: **PASS** across core, API server, lightweight UI,
  JavaFX GUI, and coverage aggregate.
- Flutter 3.50.1 / Dart 3.14.0 are installed through Homebrew on Apple Silicon;
  active Xcode 27.2 and CocoaPods are available. Windows/Linux native builds are
  not available on this host.
- Java runtime: OpenJDK 27.0.1.1 was available for the baseline build.

## Java desktop structure

The JavaFX entry points are:

- `javafx-app/src/main/java/com/uiptv/Main.java`
- `javafx-app/src/main/java/com/uiptv/ui/RootApplication.java`
- `javafx-app/src/main/java/com/uiptv/ui/main/BaseMainApplicationUI.java`
- `javafx-app/src/main/java/com/uiptv/ui/main/MainApplicationUI.java`

Main navigation targets in `AppNavigationController`:

| Flutter screen target | Existing JavaFX workflow |
| --- | --- |
| Accounts | Account selection, category/channel browsing, account management |
| Favourites | Bookmarked channels/playlists and recent favourites |
| Watching now | Live, VOD, and series watch-state views and actions |
| Settings | Player, EPG, theme, server, filter, sync, database and app settings |
| Import | Bulk account and playlist import |
| Logs | In-app UIPTV diagnostics/log display |

Supporting UI classes include account, category, channel, episode, VOD,
watching-now, bookmark, EPG, search, remote sync, publication, configuration,
update, import, and log screens.

## Existing and migration API

The existing server is Jetty and is implemented in
`api-server/src/main/java/com/uiptv/server/UIptvServer.java`. Its pre-existing
user-oriented routes remain available to web and LAN clients. The desktop
migration adds a separate `/api/v1` gateway on an ephemeral loopback-only
connector; implemented desktop routes and current limitations are documented in
`docs/desktop-api-v1.md`.

Pre-existing handler routes include:

| Route group | Existing operation |
| --- | --- |
| `/accounts` | List accounts |
| `/categories` | Account/category catalogue operations |
| `/channels` | Live channel catalogue and bounded catalogue queries |
| `/seriesEpisodes`, `/seriesDetails` | Series episode and detail metadata |
| `/vodDetails` | VOD detail metadata |
| `/watchingNow*` | Live/VOD/series watching-now operations |
| `/liveGuide`, `/playerEpg` | Live and player EPG data |
| `/player/*` | Playback resolution/gateway |
| `/bookmarks*` | Favourites and M3U bookmark publication |
| `/config` | Read/update selected desktop settings |
| `/remote-sync/*` | Remote sync request, status, upload, download and completion |
| `/playlist.m3u8`, `/bookmarks.m3u8`, `/iptv.m3u*` | Playlist publication |
| `/proxy-stream/*` | Web stream proxy |
| `/bingewatch*` | Binge-watch sessions/playlists |

The existing server still binds configured LAN addresses for browser clients.
Desktop API requests use a separate loopback-only connector and per-JVM random
bearer token; existing web/LAN routes remain intact for non-desktop clients.

## Java core and persistence

Business logic and persistence remain in `core`:

- Application orchestration: `core/src/main/java/com/uiptv/application`
- Singleton compatibility services: `core/src/main/java/com/uiptv/service`
- Database classes: `core/src/main/java/com/uiptv/db`
- Models: `core/src/main/java/com/uiptv/model`
- Migrations:
  `core/src/main/resources/db/migrations/0000_baseline.sql` and later patches

The database and configuration paths must remain unchanged. The provider identity
contract is mandatory:

- `Account.dbId`, `Category.dbId`, and `Channel.dbId` identify local rows.
- `Category.categoryId` and `Channel.channelId` identify provider resources.
- UI/API requests enter with db IDs and application services translate to provider
  IDs before provider calls.

Reference test:
`./mvnw -pl core -Dtest=ProviderIdentityContractTest test`

## Direct UI-to-core/database dependencies

JavaFX packages still import or call core types directly. A static import scan
found direct `com.uiptv.db`, `com.uiptv.service`, or
`com.uiptv.application` dependencies in 34 JavaFX source files. Notable areas:

- `AccountListUI`, `ChannelListUI`, `CategoryListUI`: account/catalogue/cache
- `BookmarkChannelListUI`, `CategoryManagementInline`: bookmarks/categories
- `BaseWatchingNowUI`, `VodWatchingNowUI`, `WatchingNowUI`: watch state
- `BaseEpisodesListUI`, `EpisodesListUI`: episode services and watch state
- `ConfigurationUI`: configuration and remote sync
- `ManageAccountUI`, `ReloadCacheInline`, `ParseMultipleAccountUI`: account import
- `ChannelGuidePopup`, `PlayerEpgUI`, `CardEpgSupport`: EPG
- `RootApplication`, `MainApplicationUI`: startup, settings and server lifecycle
- `MediaPlayerFactory`, `VlcVideoPlayer`, `BaseVideoPlayer`: playback/player state

This direct coupling must be removed from the desktop UI in Step 3. API handlers
may continue to reuse the same core services. JavaFX must remain available until
Flutter parity and playback acceptance pass.

## Desktop capability and API checklist

The API migration must represent the following existing capabilities:

- [ ] Local health/startup/shutdown and loopback-only binding.
- [ ] Per-run authentication and explicit JSON authentication failures.
- [ ] List, edit, delete, and verify accounts (listing is implemented).
- [~] Refresh account/catalogue caches through a bounded background job with
  progress/logs, stop, and global-failure decisions; provider failure branches
  still require dedicated integration coverage.
- [x] Import a bulk account/playlist payload through the existing Java parser.
- [ ] Browse account categories, live channels, VOD, and series.
- [ ] Search channels and catalogue media.
- [ ] Read/add/remove favourites, browse saved categories, and clear Recent
  history (category create/delete is implemented; rename/reorder is outstanding).
- [ ] Read/update watching-now/live/VOD/series watch state.
- [ ] Read live guide/EPG and player EPG schedules.
- [ ] Resolve live, VOD, series and episode playback with stream URL, headers,
  metadata and catch-up information.
- [ ] Persist recent playback/watch history.
- [ ] Read/update desktop settings exposed by the current UI (common settings,
  protected filter keywords, and backup/restore are implemented).
- [x] Read and clear app/web logs; app-log output is bounded and redacted.
- [ ] Generate or access published M3U/M3U8 endpoints.
- [ ] Start, monitor and complete remote database sync.
- [ ] Import/export database snapshots through existing guarded workflows.
- [ ] Filter lock and account/MAC verification workflows (password lock and
  protected filter editing are implemented; MAC verification remains).
- [x] About/version metadata (the Update button currently opens the release page).
- [ ] Download/binge-watch workflows where currently present.

## Existing player integrations

The normal desktop player is VLCJ:

- `javafx-app/src/main/java/com/uiptv/player/BaseVideoPlayer.java`
- `javafx-app/src/main/java/com/uiptv/player/MediaPlayerFactory.java`
- `javafx-app/src/main/java/com/uiptv/player/VlcVideoPlayer.java`
- VLCJ embedded callback surface and VLC 3.x arguments; `--avcodec-hw`,
  `--vout`, caching, cookie/header and reconnect options are controlled here.
- Existing external-playback utilities are present in the core
  `com.uiptv.util.CmdLineRunner` and predefined player services. These are
  diagnostic/rollback integrations, not the target Flutter normal playback path.

Migration target: Flutter controls an in-process `libmpv` shared library using a
native rendering texture/platform view. Java continues to resolve streams and
headers over `/api/v1`; Flutter sends no DB/entity requests and does not launch
VLC, mpv or an external executable.

## Packaging and release baseline

The Java desktop distribution is currently produced with `jpackage` and Maven
profiles. Windows/macOS/Linux packages are native OS builds, not cross-platform
builds from another host. CI should remain native: add or update Flutter build
jobs on matching OS runners rather than cross-claiming native support.

Existing top-level build commands are documented in `release.sh`, CI and
`CLAUDE.md`. The final migration should add an explicit `desktop` Maven profile
and invoke Flutter CLI tooling for Dart formatting, analysis, tests and native
build on the current host.

## Known gaps and baseline blockers

- The Java server retains its existing LAN/web connectors for browser clients.
  The desktop API also uses a separate ephemeral `127.0.0.1` connector protected
  by a per-JVM random bearer token.
- `/api/v1` now has explicit safe account/category/channel/bookmark DTOs, bounded
  pagination, health, playback resolution, bulk import, bookmark categories and
  Recent, settings, filter-lock, logs, About, and translations. Cache-reload,
  backup/restore, account management and several legacy compatibility routes
  still need complete DTOs and capability tests. The contract is documented in
  `docs/desktop-api-v1.md`; a complete OpenAPI contract is still pending.
- JavaFX has extensive direct core/database imports and cannot yet be converted
  with a single thin client; Step 3 has not begun.
- Flutter has an account → live categories → channels workflow, safe API client,
  live playback preparation, Favourites/Recent/search/category management,
  Watching Now series/VOD resume and season-filtered episode playback, bulk
  import, core settings, protected filter lock, logs, About, database
  backup/restore, and cache-reload progress. Complete category/account management, general VOD/series
  browsing, and full EPG flows remain incomplete.
- The Java development launcher starts Jetty before Flutter and resolves the
  Flutter project directory. It still invokes the Flutter CLI and is not a
  packaged production launcher.
- `media_kit`/libmpv runs in-process and `media_kit_video` provides a native
  Flutter texture surface. The API resolves live playback but response headers
  are not yet part of the DTO contract.
- macOS Xcode toolchain is active. Debug and release builds pass.
- The supplied public HLS fixture decoded with `ffmpeg` and reached a 1920x1080
  media_kit surface in the macOS debug run. Manual visible/audio acceptance,
  controls, channel switching, audio/subtitle selection, cleanup stress tests,
  and hardware-decoding fallback remain unverified.
- Windows and Linux native builds remain NOT TESTED.
- Native Windows/Linux libmpv builds, bundled-runtime packaging, and complete
  redistribution notices remain unverified.
- JavaFX smoke-test coverage and full parity are incomplete until each inventory
  workflow is exercised.

## Flutter visual parity direction

Flutter desktop is a presentation-layer replacement, not a UIPTV redesign. Its
first release must retain the familiar Favourites, Accounts and Watching
navigation, the content hierarchy, and the compact card-based catalogue that
users already see in the web client. The web SPA is the preferred styling
reference where the old JavaFX layout was constrained by heavyweight controls:

- use the web light palette: white surfaces, the pale blue selected navigation
  treatment, subtle grey borders, restrained shadows, dark readable text, and
  red favourite controls;
- keep a responsive grid of channel/media cards with logo, name, source/detail,
  and a favourite action; do not replace it with a novel media-centre layout;
- let catalogue content use the application window. Embedded playback opens as
  a compact floating picture-in-picture surface, can be expanded or hidden, and
  retains playback, volume, mute, close and expansion controls in its own
  compact control bar;
- use full-window responsive layouts on narrow desktop windows rather than a
  permanent player sidebar.

The current Flutter shell implements this direction for live catalogue cards,
search and the floating libmpv-backed player. Favourites, Watching Now,
settings, EPG and the remaining account workflows still need the same
screen-by-screen parity treatment as their versioned API contracts are added.
