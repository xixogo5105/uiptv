<img src="https://github.com/xixogo5105/uiptv/assets/161976171/5563a042-157e-4ae7-bb6e-a72b38c8aa62"  width="64" height="64"  alt=""/>

> [!IMPORTANT]
> **Attention!!!** Volunteers are needed for translations. [Click here](TRANSLATIONS.md).

# UIPTV - The IPTV Ecosystem for Windows, Linux & macOS
[![GitHub release](https://img.shields.io/github/v/release/xixogo5105/uiptv)](https://github.com/xixogo5105/uiptv/releases)
[![GitHub license](https://img.shields.io/github/license/xixogo5105/uiptv?branch=main)](https://github.com/xixogo5105/uiptv/blob/main/LICENSE)
[![Main](https://github.com/xixogo5105/uiptv/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/xixogo5105/uiptv/actions/workflows/build.yml)
[![Maintainability Rating](https://sonarcloud.io/api/project_badges/measure?project=xixogo5105_uiptv&metric=sqale_rating)](https://sonarcloud.io/summary/new_code?id=xixogo5105_uiptv)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=xixogo5105_uiptv&metric=coverage)](https://sonarcloud.io/summary/new_code?id=xixogo5105_uiptv)
[![codecov](https://codecov.io/gh/xixogo5105/uiptv/branch/main/graph/badge.svg)](https://app.codecov.io/github/xixogo5105/uiptv)

UIPTV is more than a player. It is an open-source IPTV ecosystem: a place to bring provider accounts and playlists together, cache and organise their content, watch Live TV/VOD/Series, publish selected libraries to your local network, and keep the experience consistent across desktop and browser clients.

The desktop application is the hub. Its shared core manages accounts, imports, local catalogues, EPG data, filtering, bookmarks, watching history, parental controls, and synchronisation. The embedded VLC player, external players, and local-network SPA/PWA are different ways to use the same organised library—not separate products competing for the same data.

Core principle: keep the experience simple, plain, and practical while giving the whole household a dependable content system behind the playback screen.

## Think ecosystem, not just player

The UIPTV ecosystem has a simple flow:

1. **Connect** your Stalker Portal, Xtreme Codes, or M3U/XMLTV sources.
2. **Organise** the imported library locally with categories, search, filters, bookmarks, parental controls, and cached metadata.
3. **Watch** Live TV, VOD, and Series through embedded VLC, an external player, or the browser player.
4. **Extend** the experience to phones, tablets, TVs, and other players through the local web app and published M3U playlists.
5. **Keep it aligned** with watching-state, EPG refresh, cache tools, and approval-based database synchronisation between UIPTV installations.

This is how to use the ecosystem: make UIPTV your trusted library hub, then choose the most convenient screen or player for the moment. Your provider sources remain inputs; UIPTV provides the organisation, controls, and shared access layer around them.

Release assets are currently published for Windows (x86_64), Linux (x86_64 and aarch64 where available), macOS (x86_64, aarch64), and Android as a universal APK. GitHub releases are published only from repository-root `release.sh`, which creates one combined desktop plus mobile release.

## Documentation

- User guide: [GUIDE.md](GUIDE.md)
- Import format reference: [ACCOUNT_IMPORT_GUIDES.md](ACCOUNT_IMPORT_GUIDES.md)
- Release notes: [GitHub Releases](https://github.com/xixogo5105/uiptv/releases)
- Desktop memory audit: [docs/EPG_MEMORY_AUDIT.md](docs/EPG_MEMORY_AUDIT.md)

### EPG documentation

- EPG overview and guide behaviour: [EPG.md](EPG.md)
- Persistent cache design: [docs/EPG_CACHE.md](docs/EPG_CACHE.md)
- Change-notification design: [docs/EPG_CHANGE_NOTIFICATIONS_PLAN.md](docs/EPG_CHANGE_NOTIFICATIONS_PLAN.md)
- Desktop memory audit: [docs/EPG_MEMORY_AUDIT.md](docs/EPG_MEMORY_AUDIT.md)
- EPG Builder implementation review: [docs/EPG_BUILDER_REVIEW.md](docs/EPG_BUILDER_REVIEW.md)
- Channel-name exclusion audit: [docs/epg-name-exclusions.md](docs/epg-name-exclusions.md)
- EPG Builder usage guide: [python/util/epg_builder/README.md](python/util/epg_builder/README.md)
- EPG Builder build and packaging: [Build and install](python/util/epg_builder/README.md#build-and-install)
- EPG Builder configuration reference: [python/util/epg_builder/CONFIGURATION.md](python/util/epg_builder/CONFIGURATION.md)

## Project Layout

UIPTV now builds as a Maven reactor with separated runtime modules:

- `core/`: shared domain models, SQLite/database layer, application services, cache reloaders, import parsers, migrations, i18n bundles, and release/version resources.
- `api-server/`: Jetty-based local web server, JSON/playlist endpoints, and bundled SPA/PWA assets under `api-server/src/main/resources/web/`.
- `javafx-app/`: JavaFX desktop application, players, widgets, dialogs, desktop CSS, icons, and native packaging configuration.
- `lightweight-ui/`: server-only control window for the lightweight (no-desktop-browsing) mode.
- `coverage-aggregate/`: JaCoCo/Kover report aggregation across modules.
- `epg-bridge/`: headless Java bridge reusing core inventory, parsing and cache publication.
- `python/util/epg_builder/`: external guide preparation utility and Maven native-binary packaging.
- `website/`: static project website published separately to GitHub Pages.

Build from the repository root. Maven resolves the module graph automatically.

<img width="1920" height="1010" alt="Screenshot From 2025-12-17 18-56-05" src="https://github.com/user-attachments/assets/53c56841-0fb0-4c72-af15-7b2160b3eb37" />

## What's New
- **Parental lock**: Keyword filtering can now be password-protected, and Stalker censored categories/channels can require a local parental lock password before opening.
- **Remote sync**: One-way database sync now supports approval-based remote execution, propagated configuration tables, and clearer completion feedback on both peers.
- **Published playlist controls**: M3U publishing now supports account/category/channel hierarchy, category modes, and more precise exported playlist sets.
- **HLS/VLC playback fixes**: Embedded VLC playback now handles redirects, cookies, SSL, and CloudFront-style HLS streams more reliably.
- **Series binge-watch**: Series playback now supports binge-watch playlist/session handling for episode-to-episode viewing.
- **Update experience**: The About/update flow now uses a custom GitHub Releases-backed update window.
- **Localized UI**: Multiple bundled UI languages are available, including RTL support and localized season/episode/tab numbering for supported locales.
- **Theme controls**: Built-in light/dark themes now support saved zoom scaling, zoom-fill playback mode, and live preview from Configuration -> Theme.


## Features:
- **Simple by Design**: UIPTV prioritizes a plain, low-friction interface and practical defaults over visual clutter or complex workflows.
- **Embedded & External Player Support**: UIPTV includes embedded playback through **VLC** and configurable external player support.
  - For the best experience, it is highly recommended to have **VLC** installed on your system and available in the system's PATH.
  - If VLC is not found, embedded playback is disabled and UIPTV will prompt you to use an external player or install VLC correctly.
  - You can still configure an external player (like MPV, SMPlayer, etc.) for maximum control.
- **Watching Now + Series Resume**: Track watched series/episodes and continue from where you left off on desktop and web.
- **VOD/Series Metadata**: Enhanced details for movies/series including richer cards and IMDb-oriented metadata flows.
- **Parental Lock & Filtering**: Protect blocked keywords and Stalker censored content with a local password, while still supporting category/channel filtering and pause/unpause controls. These restrictions also affect content exposed through the web server.
- **Theme Modes & Styling**: Built-in light/dark themes are included, and theme zoom can be saved from Settings.
- **Web Server**: Expose your accounts, bookmarks, watching-now data, and published playlists to any device on your local network through a browser.
  - **SPA Routes**: Main UI is served from `/` and `/index.html`.
  - **Extra Endpoints**: Includes playlist exports (`/iptv.m3u`, `/iptv.m3u8`) and bookmarks playlist (`/bookmarks.m3u8`).
- **DRM-aware Web Playback**: Playback payloads now include DRM fields (type/license/clear keys/manifest hints) where available.
- **Web Server Headless Support**: Run UIPTV in headless mode if you only intend to access channels through a browser.
- **Lightweight Mode**: Minimal UI for headless/low-resource use with collapsible panels, native buttons, and focusable terminal/log view.
- **Multiple IPTV Protocols**: The player supports the following protocols and formats:
  - **Stalker Portal**: Live Channels, Video On Demand, and Series.
  - **M3U Playlists**: Supports local and remote M3U files, embedded XMLTV discovery, and optional programme guides.
  - **Xtreme Codes**: Live Channels, Video On Demand, and Series.
- **Bulk Account Import**: Add multiple accounts at once for Stalker, Xtreme, and M3U, with optional post-import verification.
- **Account Tools**: MAC verify/manage flows, account pinning, timezone and HTTP method support for Stalker, browser-playback routing, and account-level cache reload.
- **Database Synchronization**: Synchronize your configuration and accounts between two database files locally or through the approved remote sync flow.
- **Tab-Based Interface**: The user interface has been updated from expandable panels to a more intuitive tab-based layout for managing accounts and settings.
- **About Page & Update Checks**: The About page includes a GitHub Releases-backed update check with a custom release-notes dialog.
- **Cache Management**: UIPTV uses an SQLite database cache with configurable expiry, global clear/reload flows, and account-level reload tools.
- **Website**: A promotional website is now available at [https://xixogo5105.github.io/uiptv/](https://xixogo5105.github.io/uiptv/).

## Important Installation Note for Windows and Linux (DEB) Users
**Before installing a new version or upgrading, you must manually uninstall the previously installed version of UIPTV.** The installer does not automatically remove older versions, and failure to do so may result in the upgrade not being applied correctly.

## Compiling UIPTV
This application is built using **Java 25** and **JavaFX**, and it is packaged into native installers using **jpackage** from the `javafx-app` module.

Before compiling, please ensure you have:
1.  A JDK (version 25 or higher) installed, with the `JAVA_HOME` environment variable correctly set.
2.  Apache Maven installed.
3.  **Windows only:** The WiX Toolset build tools must be installed and available on `PATH` when building Windows installers. `jpackage` requires `candle.exe` and `light.exe` for `.exe`/`.msi` packaging.

    Install WiX Toolset with `winget` (PowerShell):
    ```powershell
    winget install --id WiXToolset.WiXToolset -e --source winget
    ```

    Verify both tools are available:
    ```powershell
    where.exe candle
    where.exe light
    ```

    If either command is not found, add the WiX `bin` directory to `PATH` (then open a new terminal), for example:
    - `C:\Program Files (x86)\WiX Toolset v3.14\bin`

4.  **Linux DEB packaging (Debian/Ubuntu):** `fakeroot` must be installed for `.deb` builds.
5.  **Optional EPG utility builds only:** Python 3.11+ with venv/pip. UIPTV desktop builds do not require Python. Maven creates its isolated build environment automatically.


To compile the project, use Maven profiles from the repository root to target specific operating systems and package formats. Native packaging output is generated in `javafx-app/target/dist`. Each module also writes its own intermediate build output under its local `target/` directory.

Platform builds skip the Python EPG utility by default. To build it alongside the desktop app, add `-Depg.native.skip=false` (for example, `./mvnw clean package -P mac-aarch64 -Depg.native.skip=false`). This produces the standalone EPG utility under `python/util/epg_builder/target/dist/` (`.tar.gz` on macOS/Linux, `.zip` on Windows), including Python and Java runtimes. Build on the matching OS/CPU architecture. See [EPG utility native packaging](python/util/epg_builder/README.md#native-binaries-from-maven).

### Build Commands
Here are some examples of how to build the application for different targets.

#### Windows
- **Windows (x86_64):**
  ```sh
  mvn clean package -P windows-x86_64
  ```
- **Windows (ARM64/aarch64):**
  ```sh
  mvn clean package -P windows-aarch64
  ```
  <img alt="WARNING" src="https://img.shields.io/badge/WARNING-Windows%20ARM64%20build%20fails-red" />
  **JavaFX does not publish `win-aarch64` artifacts on Maven Central (as of JavaFX 27), so this profile fails dependency resolution. On Windows ARM devices, use the x86_64 build under emulation instead.**

#### macOS
- **macOS (Intel/x86_64):**
  ```sh
  mvn clean package -P mac-x86_64
  ```
- **macOS (Apple Silicon/aarch64):**
  ```sh
  mvn clean package -P mac-aarch64
  ```

#### Linux
For Linux, you combine an OS/architecture profile with a packaging profile.

- **Linux (x86_64):**
  - **DEB Package:** (Requires `fakeroot` to be installed on Debian/Ubuntu)
    ```sh
    mvn clean package -P linux-x86_64,pkg-deb
    ```
  - **RPM Package:** (Requires `rpm` to be installed)
    ```sh
    mvn clean package -P linux-x86_64,pkg-rpm
    ```
  - **Portable app bundle archive:**
    ```sh
    mvn clean package -P linux-x86_64,pkg-app-image
    ```

- **Linux (ARM64/aarch64):**
  - **DEB Package:** (Requires `fakeroot` to be installed on Debian/Ubuntu)
    ```sh
    mvn clean package -P linux-aarch64,pkg-deb
    ```
  - **RPM Package:** (Requires `rpm` to be installed)
    ```sh
    mvn clean package -P linux-aarch64,pkg-rpm
    ```
  - **Portable app bundle archive:**
    ```sh
    mvn clean package -P linux-aarch64,pkg-app-image
    ```

## Misc

When providing an external video player, you can also use the flatpak. Just use the direct binary address.
For example, a standard VLC flatpak address is `_ /var/lib/flatpak/app/org.videolan.VLC/current/active/export/bin/org.videolan.VLC_` which can be provided to run IPTV streams directly.

For advanced configuration options including thumbnail cache location and expiry, database path, and other tuning, see [GUIDE.md](GUIDE.md#3-configuration).

If you are using native MPV (e.g. `_ /usr/bin/mpv_`) and the streams stop/freeze after a little while then
create an executable sh file with the contents below and point the executable sh file (e.g. `_ ~/apps/mpv/mpv.sh_`) as an external player.

The contents of `_ mpv.sh_` file:

    #!/bin/sh
    /usr/local/bin/mpv "$@"&

You can also use below (Mac OSX) sh script to play streams on KODI as external player. The script will act as player to UIPTV. It will open the kodi with the stream url or (and if remote access is enabled) send remote request to open the stream if the KODI is already working.

```
#!/bin/bash

STREAM_URL="$1"
KODI_HOST="localhost"
KODI_PORT="8080"
KODI_USER=""
KODI_PASS=""

if [ -z "$STREAM_URL" ]; then
    exit 1
fi

if [ -z "$KODI_USER" ] || [ -z "$KODI_PASS" ]; then
    curl -s -X POST -H 'Content-Type: application/json' -d '{"jsonrpc": "2.0", "method": "Player.Stop", "params": {"playerid": 1}, "id": 1}' "http://$KODI_HOST:$KODI_PORT/jsonrpc"
    curl -s -X POST -H 'Content-Type: application/json' -d '{"jsonrpc": "2.0", "method": "Application.SetVolume", "params": {"volume": 0}, "id": 3}' "http://$KODI_HOST:$KODI_PORT/jsonrpc"
    curl -s -X POST -H 'Content-Type: application/json' -d "{\"jsonrpc\": \"2.0\", \"method\": \"Player.Open\", \"params\": {\"item\": {\"file\": \"$STREAM_URL\"}}, \"id\": 2}" "http://$KODI_HOST:$KODI_PORT/jsonrpc"
else
    curl -s -X POST -H 'Content-Type: application/json' -u "$KODI_USER:$KODI_PASS" -d '{"jsonrpc": "2.0", "method": "Player.Stop", "params": {"playerid": 1}, "id": 1}' "http://$KODI_HOST:$KODI_PORT/jsonrpc"
    curl -s -X POST -H 'Content-Type: application/json' -u "$KODI_USER:$KODI_PASS" -d '{"jsonrpc": "2.0", "method": "Application.SetVolume", "params": {"volume": 0}, "id": 3}' "http://$KODI_HOST:$KODI_PORT/jsonrpc"
    curl -s -X POST -H 'Content-Type: application/json' -u "$KODI_USER:$KODI_PASS" -d "{\"jsonrpc\": \"2.0\", \"method\": \"Player.Open\", \"params\": {\"item\": {\"file\": \"$STREAM_URL\"}}, \"id\": 2}" "http://$KODI_HOST:$KODI_PORT/jsonrpc"
fi

open -a Kodi --args "$STREAM_URL"
```

## Disclaimer
This is a personal project developed for fun. Optional EPG supports native Stalker/Xtream schedules and XMLTV fallbacks on desktop, web and Android; see [EPG.md](EPG.md) for setup and provider limitations.

Merge requests for bug fixes or new features are always welcome.

Donations are neither needed nor accepted. If you wish to contribute, please consider donating to one of your favorite open-source projects, such as **[Linux Mint](https://www.linuxmint.com/donors.php)** or **[Ubuntu](https://ubuntu.com/download/desktop/thank-you#contributions-form)**.
