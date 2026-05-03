# UIPTV User Guide

Welcome to the comprehensive user guide for **UIPTV**, a versatile and modern IPTV player designed for Windows, Linux, and macOS. This guide will walk you through every aspect of the application, from installation to advanced configuration, ensuring you get the most out of your streaming experience.

---

## Table of Contents
1. [Introduction](#1-introduction)
2. [Installation](#2-installation)
   - [System Requirements](#system-requirements)
   - [Installing on Windows](#installing-on-windows)
   - [Installing on Linux](#installing-on-linux)
   - [Installing on macOS](#installing-on-macos)
3. [Configuration](#3-configuration)
   - [Player Settings](#player-settings)
   - [Parental Lock](#parental-lock)
   - [Appearance & Styling](#appearance--styling)
   - [Cache Management](#cache-management)
   - [Web Server Settings](#web-server-settings)
   - [Updates & About](#updates--about)
4. [Managing Accounts](#4-managing-accounts)
   - [Stalker Portal](#stalker-portal)
   - [Xtreme Codes](#xtreme-codes)
   - [M3U Playlists (Remote & Local)](#m3u-playlists-remote--local)
   - [RSS Feeds (YouTube Support)](#rss-feeds-youtube-support)
   - [Account Maintenance](#account-maintenance)
   - [Bulk Account Import](#bulk-account-import)
5. [Using the Player](#5-using-the-player)
   - [Navigation](#navigation)
   - [Search & Favorites](#search--favorites)
   - [Playback Controls](#playback-controls)
   - [Watching Now](#watching-now)
   - [Embedded Players Explained](#embedded-players-explained)
6. [Web Server & Remote Access](#6-web-server--remote-access)
   - [Setting Up the Server](#setting-up-the-server)
   - [Accessing via Browser](#accessing-via-browser)
   - [Headless Mode](#headless-mode)
7. [Advanced Features & Tips](#7-advanced-features--tips)
   - [External Player Integration](#external-player-integration)
   - [Kodi Integration](#kodi-integration)
   - [YouTube Integration](#youtube-integration)
   - [Database Synchronization](#database-synchronization)
   - [Troubleshooting](#troubleshooting)

---

## 1. Introduction

**UIPTV** is an open-source IPTV player built with Java and JavaFX. It is designed to be lightweight yet powerful, offering support for multiple IPTV protocols including Stalker Portal, Xtreme Codes, and M3U playlists.

Key highlights include:
- **Cross-Platform**: Runs natively on Windows, Linux, and macOS.
- **Flexible Player Support**: Use the embedded VLC-based player or your favorite external player (MPV, SMPlayer, etc.).
- **Web SPA/PWA**: Stream your content to any device on your local network via a modern browser-first interface.
- **Live TV + VOD + Series**: Unified content modes with watched-state/resume flows.
- **Privacy & Control**: Parental-lock controls, protected filtering, and local caching keep playback practical without exposing unwanted content.

### What's New

- **Localized UI**: UIPTV now ships with multiple language bundles, RTL layout support, and localized season/episode numbering where supported.
- **Theme zoom + overrides**: The Theme section supports saved zoom, live preview, and light/dark CSS overrides.
- **Browser player path**: Desktop context menus and default playback settings can route playback through the local web player when needed.
- **Watching Now improvements**: Series watch-state and resume flows are available in both desktop and web experiences.
- **Import tooling refresh**: The Stalker, M3U, and Xtreme import guides have been aligned with current parser behavior.
- **Parental lock**: Filter management and Stalker censored content can now be protected with a local password.
- **Remote sync**: One-way database sync can now be approved and executed against another running UIPTV instance.

For advanced desktop theming, see [CSS_APPLICATION_GUIDE.md](CSS_APPLICATION_GUIDE.md).

---

## 2. Installation

### System Requirements
To ensure a smooth experience, please verify your system meets the following requirements:
- **Java**: Java 25 is bundled with the installer, so no separate installation is required.
- **VLC Media Player**: Highly recommended for the best video compatibility. Ensure it is installed and added to your system's PATH.
- **yt-dlp**: Required if you plan to watch YouTube videos via RSS feeds.
- **FFmpeg**: Required if you plan to stream `.ts` files via the web interface.

### Installing on Windows
**Important:** Before installing a new version or upgrading, you **must manually uninstall the previously installed version** of UIPTV. The installer does not automatically remove older versions, and failure to do so may result in the upgrade not being applied correctly.

1. Download the latest `.msi` installer from the [Releases Page](https://github.com/xixogo5105/uiptv/releases/latest).
2. Double-click the installer and follow the on-screen prompts.
3. Once installed, launch UIPTV from the Start Menu.

#### Installing Dependencies (FFmpeg & yt-dlp)
To enable YouTube support and web streaming of TS files, you need to install these tools and ensure they are in your system PATH.
- **Using Winget (Recommended)**: Open PowerShell and run:
  ```powershell
  winget install Gyan.FFmpeg
  winget install yt-dlp.yt-dlp
  ```
- **Manual Installation**: Download the executables from their official websites, place them in a permanent folder (e.g., `C:\Tools`), and add that folder to your System Environment Variables (PATH).

#### Windows Installer Build Dependency (WiX Toolset)
If you build Windows `.exe`/`.msi` installers locally with `jpackage`, install WiX Toolset and ensure `candle.exe` and `light.exe` are on `PATH`.

- **Using Winget (Recommended)**:
  ```powershell
  winget install --id WiXToolset.WiXToolset -e --source winget
  ```
- **Verify tools are available**:
  ```powershell
  where.exe candle
  where.exe light
  ```
- **If not found**: Add the WiX `bin` folder to `PATH` (then open a new terminal), for example:
  - `C:\Program Files (x86)\WiX Toolset v3.14\bin`

### Installing on Linux
**Important for DEB users:** Before installing a new version or upgrading, you **must manually uninstall the previously installed version** of UIPTV.

1. Download the appropriate package for your distribution (`.deb` for Debian/Ubuntu, `.rpm` for Fedora/RHEL, or the portable Linux app bundle archive).
2. Install using your package manager:
   - **Debian/Ubuntu**: `sudo dpkg -i uiptv_*.deb`
   - **Fedora**: `sudo rpm -i uiptv_*.rpm`
   - **Portable app bundle archive**: Extract the archive and run `UIPTV/bin/UIPTV`
3. Launch the application from your application menu or terminal.

#### Installing Dependencies
- **FFmpeg**:
  - **Debian/Ubuntu**: `sudo apt install ffmpeg`
  - **Fedora**: `sudo dnf install ffmpeg`
- **fakeroot** (required for Debian/Ubuntu `.deb` packaging workflows):
  - **Debian/Ubuntu**: `sudo apt install fakeroot`
- **yt-dlp**:
  It is recommended to install the latest binary directly to ensure compatibility with YouTube's latest changes:
  ```bash
  sudo curl -L https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp -o /usr/local/bin/yt-dlp
  sudo chmod a+rx /usr/local/bin/yt-dlp
  ```

### Installing on macOS
1. Download the `.dmg` file.
2. Open the disk image and drag the UIPTV application to your **Applications** folder.
3. You may need to allow the application to run in **System Settings > Privacy & Security** if it's not signed by an identified developer.

#### Installing Dependencies
The easiest way to install the required dependencies on macOS is using **Homebrew**. Open your terminal and run:
```bash
brew install ffmpeg yt-dlp
```

---

## 3. Configuration

The **Configuration** tab is your central hub for customizing UIPTV.

### Player Settings
UIPTV offers flexibility in how you watch your content:
- **Embedded VLC Player**: This is the default and most robust option. It uses the VLC libraries installed on your system.
- **Embedded Lite Player**: A fallback player with basic functionality, useful if VLC is unavailable.
- **Web Browser Player**: You can select browser playback as a default route for compatible workflows.
- **External Players**: You can configure up to three external players.
  1. Click **Browse...** to locate the executable of your preferred player (e.g., `mpv.exe`, `smplayer`).
  2. Use the **Radio Button** next to a player path to set it as the default player when you double-click a channel.
- **Wide View**: Enables a wider embedded player layout.
- **Restart Requirement**: Changing embedded player mode or wide view requires app restart for full effect.

### Parental Lock
Keep your channel list controlled and protected:
- **Show/Hide Filters**: Filter input text areas can be collapsed/expanded.
- **Category Filter**: Enter keywords (comma-separated) to hide entire categories (e.g., `adult, xxx, shopping`).
- **Channel Filter**: Enter specific channel names to exclude them from your list.
- **Pause Filtering**: Toggle this option to temporarily show all hidden content without deleting your filter lists.
- **Web Server Impact**: Note that these filters also apply to the content served via the web server. If you filter out a category here, it will not be visible on your remote devices.
- **Parental Lock Password**: You can set, change, relock, or disable a parental lock password. When enabled, protected filter keywords remain hidden until unlocked, and Stalker censored content can prompt for the password before opening.

### Appearance & Styling
Make UIPTV look the way you want:
- **Language**: Choose the UI language from the Theme section.
- **Dark Theme**: Enable this for a modern, eye-friendly dark interface.
- **Zoom**: Save a browser-style font zoom value for the desktop UI and preview it immediately from the Theme section.
- **Theme CSS Overrides**: Upload custom light/dark CSS files from Settings -> Theme.
- **Template Export**: Download default light/dark CSS files and use them as baseline templates.
- **Thumbnails**: Enable/disable thumbnail-heavy series and watching-now cards.

### Cache Management
UIPTV uses a local SQLite database to cache account, category, channel, bookmark, and watch-state data for faster loading.
- **Clear Cache**: If you experience missing channels or outdated data, click this to reset the local database.
- **Reload Cache**: Refresh account caches on demand from the account management screen.
- **Cache Expiry**: Configure cache lifetime in days in the Configuration tab.
- **Clear Watching Now**: Clear stored watched-progress data.

### Web Server Settings
- **Server Port**: Configure the listening port.
- **Start/Stop/Open**: Manage server lifecycle from the Configuration tab.
- **FFmpeg Transcoding**: Enable optional TS-friendly web playback compatibility.
- **Publish M3U8**: Generate merged playlists for remote-player consumption, with account/category/channel selection controls for narrower exports.

### Updates & About
- **About Page**: Provides version information and credits.
- **Check for Updates**: You can manually check for new versions of UIPTV from the About page. The application will notify you if a newer release is available on GitHub.

---

## 4. Managing Accounts

Go to the **Manage Account** tab to add your IPTV sources. You can add multiple accounts and switch between them easily.

**Import Format Guides**: For detailed account format documentation with examples, see [ACCOUNT_IMPORT_GUIDES.md](ACCOUNT_IMPORT_GUIDES.md)

### Stalker Portal
Used by many IPTV providers. See [STALKER_IMPORT_GUIDE.md](STALKER_IMPORT_GUIDE.md) for bulk import examples.

1. Select **Stalker Portal** from the dropdown.
2. Enter the **Portal URL** provided by your service.
3. Enter your **MAC Address** (usually linked to your subscription).
4. Optional: set `HTTP Method` and `Timezone` when needed.
5. Optional: set `Serial`, `Device ID 1/2`, and `Signature`.
   - During bulk import, MAC-only entries can group together by portal URL.
   - Entries carrying `Serial`, `Device ID 1`, `Device ID 2`, or `Signature` are treated as separate device-bound accounts and only group with later imports carrying the same extra-parameter identity.
6. Click **Add Account**.

### Xtreme Codes
A popular API-based method. See [XTREME_IMPORT_GUIDE.md](XTREME_IMPORT_GUIDE.md) for bulk import examples.

1. Select **Xtreme Codes API**.
2. Enter the **Host URL**, **Username**, and **Password**.
3. Click **Add Account**.

### M3U Playlists (Remote & Local)
See [M3U_IMPORT_GUIDE.md](M3U_IMPORT_GUIDE.md) for bulk import examples and M3U to Xtreme conversion guide.

- **Remote URL**: Paste the http/https link to your M3U playlist.
- **Local File**: Browse your computer to select a downloaded `.m3u` or `.m3u8` file.

### RSS Feeds (YouTube Support)
Turn UIPTV into a news or video feed reader.
1. Select **RSS Feed**.
2. Enter the RSS URL.
   - **YouTube Tip**: To follow a YouTube channel, use: `https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID`
3. Click **Add Account**.

**Note**: After adding an account, click **Parse Accounts** to load the channels.

### Account Maintenance
- **Verify MAC**: For Stalker accounts, use `Verify` to validate and clean MAC lists.
- **Manage MAC List**: Edit/remove MAC entries and choose default MAC with the popup tool.
- **Reload Cache**: Trigger account-level cache refresh for supported account types.
- **Pin Account on Top**: Keep frequently used accounts pinned at the top.

### Bulk Account Import
The **Import Bulk Accounts** tab allows you to add multiple accounts at once.

**Format References:**
- [STALKER_IMPORT_GUIDE.md](STALKER_IMPORT_GUIDE.md) - Stalker Portal format and advanced parameter examples
- [XTREME_IMPORT_GUIDE.md](XTREME_IMPORT_GUIDE.md) - Xtreme Codes format examples
- [M3U_IMPORT_GUIDE.md](M3U_IMPORT_GUIDE.md) - M3U format examples and conversion feature

1. **Select Mode**: Choose between `Stalker Portal`, `Xtreme Codes`, or `M3U`.
2. **Enter Data**: Paste your account details in the text area.
   - **Stalker Portal Format**:
     ```
     http://portal-url.com/c
     00:1A:79:XX:XX:XX
     http://another-portal.com/c
     00:1A:79:YY:YY:YY
     ```
   - **M3U Format**:
     ```
     http://domain.com:8080/get.php?username=user&password=pass&type=m3u
     ```
3. **Options**:
   - **Group Account(s) by MAC Address** (Stalker Mode only):
     - MAC-only entries group together by portal URL.
     - Entries with `Serial`, `Device ID 1`, `Device ID 2`, or `Signature` stay separate unless a later import has the same extra-parameter identity.
   - **Group Accounts by Username/Password** (Xtreme Mode only):
     - Reuses one host account and stores multiple username/password pairs under it.
   - **Convert M3U to Xtreme** (M3U Mode only):
     - When enabled, the parser attempts to convert M3U URLs into Xtreme Codes API accounts (Host, Username, Password) for better compatibility and performance.
     - This is only available when parsing M3U links.
   - **Start verification after parsing**:
     - Runs verification/reload flow for newly imported accounts.
4. Click **Parse & Save** to import the accounts.

---

## 5. Using the Player

### Navigation
- **Tabs**: The interface is divided into tabs for **Player**, **Configuration**, **Manage Account**, and **Favorites**.
- **Channel List**: On the left side of the Player tab, you'll see your categories and channels. Expand a category to view its channels.

### Search & Favorites
- **Search**: Use the search bar at the top to instantly filter channels or categories by name.
- **Favorites**:
  - **Add**: Right-click any channel and select **Add to Favorites**.
  - **View**: Go to the **Favorites** tab to see your curated list.
  - **Manage**: Right-click a favorite to remove it or move it up/down the list.

### Playback Controls
- **Double-Click**: Starts playback using your selected default player.
- **Right-Click**: Offers options to play with a specific player (Embedded, External 1, 2, or 3).

### Watching Now
- **Resume Flow**: Continue watching series from recently watched entries.
- **Episode Actions**: Open episodes from cards and reload episodes from server.
- **Watched Markers**: Series and episode rows show watched/in-progress states.

### Embedded Players Explained
UIPTV comes with two embedded players, each serving a different purpose.

#### 1. Embedded VLC Player (Recommended)
This is the primary and most powerful player. It leverages the **VLC Media Player** libraries installed on your system.
- **Features**:
  - **Broad Format Support**: Plays almost any video or audio format supported by VLC.
  - **Hardware Acceleration**: Utilizes system hardware for smooth playback.
  - **Advanced Controls**: Includes Play/Pause, Stop, Repeat, Reload, Fullscreen, Picture-in-Picture (PiP), Mute, Volume, and Aspect Ratio toggle (Fit/Stretch).
  - **Overlay Controls**: Controls appear on mouse hover and fade out when idle.
  - **PiP Mode**: Allows you to detach the video into a floating, resizable window that stays on top of other applications.

#### 2. Embedded Lite Player (Fallback)
This is a lightweight player based on JavaFX's built-in media engine. It is used as a fallback if VLC is not detected or if you prefer a simpler experience.
- **Features**:
  - **Basic Playback**: Supports standard web formats (HLS/m3u8, MP4).
  - **Simple Interface**: Includes essential controls like Play/Pause, Stop, Repeat, Reload, Fullscreen, PiP, Mute, Volume, and Aspect Ratio.
  - **Limitations**: Does not support as many codecs or advanced streaming protocols as the VLC player. It is best suited for standard HLS streams.

**Note**: Both players support standard keyboard shortcuts:
- **F**: Toggle Fullscreen
- **M**: Toggle Mute
- **Esc**: Exit Fullscreen

---

## 6. Web Server & Remote Access

UIPTV includes a built-in web server, allowing you to watch IPTV content on other devices (phones, tablets, smart TVs) via a web browser.

### Setting Up the Server
1. Go to the **Configuration** tab.
2. Set a **Port** (default is `8888` unless changed in your config).
3. Click **Save** to apply the port setting.
4. Click **Start Server**.

### Accessing via Browser
1. Find the IP address of your computer (e.g., `192.168.1.10`).
2. On your other device, open a browser and go to `http://192.168.1.10:8888` (or your configured port).
3. You will see a web interface where you can browse categories and play channels.

**Important Notes**:
- **SPA Web UI**: The main web entry is `/` or `/index.html` with additional views like `/myflix.html`.
- **Playlist Exports**: You can download published playlists from `/iptv.m3u` and `/iptv.m3u8`.
- **Bookmarks Playlist**: Export favorites as `/bookmarks.m3u8`.
- **TS Support**: To play MPEG-TS (`.ts`) streams in the browser, install **FFmpeg** on the host and enable FFmpeg transcoding in Configuration.

### Headless Mode
If you want to run UIPTV on a server without a graphical interface:
- Use the command-line argument `--headless` (if supported by your launcher script) or simply start the application and minimize it to the tray.
- This is ideal for dedicated media servers.

---

## 7. Advanced Features & Tips

### External Player Integration
For the best quality, you might prefer **MPV** or **MPC-HC**.
- **Linux Tip**: If using Flatpak apps, point to the binary path (e.g., `/var/lib/flatpak/...`).
- **Scripting**: You can point to a shell script (`.sh` or `.bat`) instead of a direct executable. This allows you to pass custom arguments to the player.
  - *Example `mpv.sh`*:
    ```bash
    #!/bin/sh
    /usr/bin/mpv --fs --hwdec=auto "$@"
    ```

### Kodi Integration
You can use a shell script to play streams on **Kodi** as an external player. This script acts as a bridge, sending the stream URL to Kodi's JSON-RPC interface.

**Mac OSX / Linux Script Example:**
```bash
#!/bin/bash

STREAM_URL="$1"
KODI_HOST="localhost"
KODI_PORT="8080"
KODI_USER=""
KODI_PASS=""

if [ -z "$STREAM_URL" ]; then
    exit 1
fi

# Send commands to Kodi via curl
if [ -z "$KODI_USER" ] || [ -z "$KODI_PASS" ]; then
    # Stop current player
    curl -s -X POST -H 'Content-Type: application/json' -d '{"jsonrpc": "2.0", "method": "Player.Stop", "params": {"playerid": 1}, "id": 1}' "http://$KODI_HOST:$KODI_PORT/jsonrpc"
    # Open new stream
    curl -s -X POST -H 'Content-Type: application/json' -d "{\"jsonrpc\": \"2.0\", \"method\": \"Player.Open\", \"params\": {\"item\": {\"file\": \"$STREAM_URL\"}}, \"id\": 2}" "http://$KODI_HOST:$KODI_PORT/jsonrpc"
else
    # Authenticated requests
    curl -s -X POST -H 'Content-Type: application/json' -u "$KODI_USER:$KODI_PASS" -d '{"jsonrpc": "2.0", "method": "Player.Stop", "params": {"playerid": 1}, "id": 1}' "http://$KODI_HOST:$KODI_PORT/jsonrpc"
    curl -s -X POST -H 'Content-Type: application/json' -u "$KODI_USER:$KODI_PASS" -d "{\"jsonrpc\": \"2.0\", \"method\": \"Player.Open\", \"params\": {\"item\": {\"file\": \"$STREAM_URL\"}}, \"id\": 2}" "http://$KODI_HOST:$KODI_PORT/jsonrpc"
fi

# Optional: Bring Kodi to front (macOS)
# open -a Kodi --args "$STREAM_URL"
```
Save this as `kodi.sh`, make it executable (`chmod +x kodi.sh`), and set it as an external player in UIPTV.

### YouTube Integration
To watch YouTube videos seamlessly:
1. Install **yt-dlp** and add it to your PATH.
2. Add YouTube RSS feeds as described in the [Accounts section](#rss-feeds-youtube-support).
3. When you play a video, UIPTV uses `yt-dlp` to fetch the stream URL and plays it in your chosen player.

### Database Synchronization
UIPTV supports synchronizing its database between two instances or backups. This is useful for keeping your settings and accounts consistent across devices.

**Usage:**
Run the application from the command line with the `sync` argument:
```bash
uiptv sync "/path/to/first.db" "/path/to/second.db"
```
This command will synchronize the configuration and account tables between the two specified SQLite database files.

The current sync tooling also supports:
- one-way sync between local databases
- approval-based remote sync between two running UIPTV instances
- propagation of published M3U selections and related configuration tables

### Troubleshooting

**Q: Channels are not loading.**
A: Check your internet connection and verify your subscription status. Try clicking **Clear Cache** and then **Parse Accounts** again.

**Q: Video plays but no audio (or vice versa).**
A: This is often a codec issue. We strongly recommend using **VLC** as your backend or an external player like **MPV** which handles almost all codecs.

**Q: Web player says "Error loading media".**
A:
- If it's a `.ts` stream, ensure **FFmpeg** is installed.
- Ensure the device you are watching on is on the same local network.
- Check if your firewall is blocking the configured server port (default 8888).

**Q: The application is slow.**
A: Large playlists (20,000+ channels) can be heavy. Use the **Filter** options to exclude categories you don't watch (e.g., foreign languages), which will significantly speed up loading times.

---

**Enjoy using UIPTV!**
If you encounter bugs or have feature requests, please visit our [GitHub Repository](https://github.com/xixogo5105/uiptv).
