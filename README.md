<img src="https://github.com/xixogo5105/uiptv/assets/161976171/5563a042-157e-4ae7-bb6e-a72b38c8aa62"  width="64" height="64"  alt=""/>

> [!IMPORTANT]
> **Attention!!!** Volunteers are needed for translations. [Click here](TRANSLATIONS.md).

# UIPTV - A Universal IPTV Player for Windows, Linux & macOS
[![GitHub (pre-)release](https://img.shields.io/github/release-pre/xixogo5105/uiptv.svg)](https://github.com/xixogo5105/uiptv/releases)
[![GitHub license](https://img.shields.io/github/license/xixogo5105/uiptv?branch=main)](https://github.com/xixogo5105/uiptv/blob/main/LICENSE)
[![Main](https://github.com/xixogo5105/uiptv/actions/workflows/build.yml/badge.svg?branch=main)](https://github.com/xixogo5105/uiptv/actions/workflows/build.yml)
[![Quality Gate Status](https://sonarcloud.io/api/project_badges/measure?project=xixogo5105_uiptv&metric=alert_status)](https://sonarcloud.io/summary/new_code?id=xixogo5105_uiptv)
[![Coverage](https://sonarcloud.io/api/project_badges/measure?project=xixogo5105_uiptv&metric=coverage)](https://sonarcloud.io/summary/new_code?id=xixogo5105_uiptv)
[![codecov](https://codecov.io/gh/xixogo5105/uiptv/branch/main/graph/badge.svg)](https://app.codecov.io/github/xixogo5105/uiptv)

UIPTV is a versatile IPTV player written in Java, designed to run on Windows, Linux, and macOS. It provides both a desktop experience and a full local-network web experience (SPA/PWA) for Live TV, VOD, and Series.

Core principle: keep the app simple, plain, and practical so everyday playback and account management stay fast and predictable.

Native installers and packages are currently published for Windows (x86_64), Linux (x86_64), and macOS (x86_64, aarch64). Linux builds are provided as `.deb`, optional `.rpm`, and a portable app bundle archive.

## Documentation

- User guide: [GUIDE.md](GUIDE.md)
- CSS/theming guide: [CSS_APPLICATION_GUIDE.md](CSS_APPLICATION_GUIDE.md)
- Import format reference: [ACCOUNT_IMPORT_GUIDES.md](ACCOUNT_IMPORT_GUIDES.md)

<img width="1920" height="1010" alt="Screenshot From 2025-12-17 18-56-05" src="https://github.com/user-attachments/assets/53c56841-0fb0-4c72-af15-7b2160b3eb37" />

## What's New
- **Localized UI**: Multiple bundled UI languages are available, including RTL support and localized season/episode/tab numbering for supported locales.
- **Theme controls**: Built-in light/dark themes now support saved zoom scaling and live preview from Configuration -> Theme.
- **Desktop + web parity**: Watching Now, series resume state, bookmarks, and published playlists are available across desktop and the local-network web app.
- **Browser playback path**: A web-browser player route is available alongside embedded and external player options, including DRM-aware web playback fallback.
- **Import tooling refresh**: Bulk Stalker, M3U, and Xtreme import flows have clearer guides, better parser behavior, and stronger account maintenance tools.
- **CSS customization**: Theme overrides, exported baseline CSS templates, and a documented JavaFX styling contract are available for deeper customization.


## Features:
- **Simple by Design**: UIPTV prioritizes a plain, low-friction interface and practical defaults over visual clutter or complex workflows.
- **Embedded & External Player Support**: UIPTV now includes two embedded video players: a full-featured player powered by **VLC** and a limited, lightweight alternative.
  - For the best experience, it is highly recommended to have **VLC** installed on your system and available in the system's PATH.
  - If VLC is not found, the player will fall back to a basic, lightweight player with fewer features.
  - You can still configure an external player (like MPV, SMPlayer, etc.) for maximum control.
- **Watching Now + Series Resume**: Track watched series/episodes and continue from where you left off on desktop and web.
- **VOD/Series Metadata**: Enhanced details for movies/series including richer cards and IMDb-oriented metadata flows.
- **Filter Categories/Channels**: Exclude or filter out categories and channels that are of no interest. This feature is also useful for censoring specific content (e.g., adult channels). Filtering can be paused or unpaused and also impacts the content available through the web server.
- **Theme Modes & Styling**: Built-in light/dark themes are included, theme zoom can be saved from Settings, and users can override each theme with full CSS files. See [CSS_APPLICATION_GUIDE.md](CSS_APPLICATION_GUIDE.md).
- **Web Server**: Expose your accounts, bookmarks, watching-now data, and published playlists to any device on your local network through a browser.
  - **SPA Routes**: Main UI is served from `/` and `/index.html` with additional views (`/myflix.html`, `/player.html`).
  - **Extra Endpoints**: Includes playlist exports (`/iptv.m3u`, `/iptv.m3u8`) and bookmarks playlist (`/bookmarks.m3u8`).
  - **FFmpeg Requirement**: For TS-style streams in web playback, install **ffmpeg** and enable FFmpeg transcoding in Configuration.
- **DRM-aware Web Playback**: Playback payloads now include DRM fields (type/license/clear keys/manifest hints) where available.
- **Web Server Headless Support**: Run UIPTV in headless mode if you only intend to access channels through a browser.
- **Multiple IPTV Protocols**: The player supports the following protocols and formats:
  - **Stalker Portal**: Live Channels, Video On Demand, and Series.
  - **M3U Playlists**: Supports local and remote M3U files (EPG is not yet supported).
  - **Xtreme Codes**: Live Channels, Video On Demand, and Series.
  - **RSS Feeds**: Add and watch content from RSS feeds. This includes support for YouTube channels by using their RSS feed URL (e.g., `https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID`). For YouTube videos to play, **yt-dlp** must be installed and accessible in the system's PATH.
- **Bulk Account Import**: Add multiple accounts at once for Stalker, Xtreme, and M3U.
- **Account Tools**: MAC verify/manage flows, account pinning, timezone and HTTP method support for Stalker, and account-level cache reload.
- **Database Synchronization**: Synchronize your configuration and accounts between two database files, perfect for backups or multi-device setups.
- **Tab-Based Interface**: The user interface has been updated from expandable panels to a more intuitive tab-based layout for managing accounts and settings.
- **About Page & Update Checks**: An "About" page has been added, which includes a mechanism to check for new application updates.
- **Cache Management**: UIPTV uses an SQLite database cache with configurable expiry, global clear/reload flows, and account-level reload tools.
- **Website**: A promotional website is now available at [https://xixogo5105.github.io/uiptv/](https://xixogo5105.github.io/uiptv/).

## Important Installation Note for Windows and Linux (DEB) Users
**Before installing a new version or upgrading, you must manually uninstall the previously installed version of UIPTV.** The installer does not automatically remove older versions, and failure to do so may result in the upgrade not being applied correctly.

## Compiling UIPTV
This application is built using **Java 25** and **JavaFX**, and it is packaged into native installers using **jpackage**.

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


To compile the project, you can use Maven profiles to target specific operating systems and package formats. The final application will be generated in the `target/dist` directory.

### Build
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
  **JavaFX does not publish `win-aarch64` artifacts on Maven Central (as of 25.0.2), so this profile fails dependency resolution. On Windows ARM devices, use the x86_64 build under emulation instead.**

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
This is a personal project developed for fun. EPG support is not yet implemented.

Merge requests for bug fixes or new features are always welcome.

Donations are neither needed nor accepted. If you wish to contribute, please consider donating to one of your favorite open-source projects, such as **[Linux Mint](https://www.linuxmint.com/donors.php)** or **[Ubuntu](https://ubuntu.com/download/desktop/thank-you#contributions-form)**.
