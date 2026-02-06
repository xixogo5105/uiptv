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
   - [Filtering Content](#filtering-content)
   - [Appearance & Styling](#appearance--styling)
   - [Cache Management](#cache-management)
4. [Managing Accounts](#4-managing-accounts)
   - [Stalker Portal](#stalker-portal)
   - [Xtream Codes](#xtream-codes)
   - [M3U Playlists (Remote & Local)](#m3u-playlists-remote--local)
   - [RSS Feeds (YouTube Support)](#rss-feeds-youtube-support)
5. [Using the Player](#5-using-the-player)
   - [Navigation](#navigation)
   - [Search & Favorites](#search--favorites)
   - [Playback Controls](#playback-controls)
6. [Web Server & Remote Access](#6-web-server--remote-access)
   - [Setting Up the Server](#setting-up-the-server)
   - [Accessing via Browser](#accessing-via-browser)
   - [Headless Mode](#headless-mode)
7. [Advanced Features & Tips](#7-advanced-features--tips)
   - [External Player Integration](#external-player-integration)
   - [YouTube Integration](#youtube-integration)
   - [Troubleshooting](#troubleshooting)

---

## 1. Introduction

**UIPTV** is an open-source IPTV player built with Java and JavaFX. It is designed to be lightweight yet powerful, offering support for multiple IPTV protocols including Stalker Portal, Xtream Codes, and M3U playlists.

Key highlights include:
- **Cross-Platform**: Runs natively on Windows, Linux, and macOS.
- **Flexible Player Support**: Use the embedded VLC-based player or your favorite external player (MPV, SMPlayer, etc.).
- **Web Server**: Stream your content to any device on your local network via a web browser.
- **Privacy & Control**: robust filtering options to hide unwanted content and local caching for performance.

---

## 2. Installation

### System Requirements
To ensure a smooth experience, please verify your system meets the following requirements:
- **Java**: Java 25 is bundled with the installer, so no separate installation is required.
- **VLC Media Player**: Highly recommended for the best video compatibility. Ensure it is installed and added to your system's PATH.
- **yt-dlp**: Required if you plan to watch YouTube videos via RSS feeds.
- **FFmpeg**: Required if you plan to stream `.ts` files via the web interface.

### Installing on Windows
1. Download the latest `.msi` installer from the [Releases Page](https://github.com/xixogo5105/uiptv/releases/latest).
2. Double-click the installer and follow the on-screen prompts.
3. Once installed, launch UIPTV from the Start Menu.

### Installing on Linux
1. Download the appropriate package for your distribution (`.deb` for Debian/Ubuntu, `.rpm` for Fedora/RHEL, or `.AppImage`).
2. Install using your package manager:
   - **Debian/Ubuntu**: `sudo dpkg -i uiptv_*.deb`
   - **Fedora**: `sudo rpm -i uiptv_*.rpm`
   - **AppImage**: `chmod +x uiptv_*.AppImage && ./uiptv_*.AppImage`
3. Launch the application from your application menu or terminal.

### Installing on macOS
1. Download the `.dmg` file.
2. Open the disk image and drag the UIPTV application to your **Applications** folder.
3. You may need to allow the application to run in **System Settings > Privacy & Security** if it's not signed by an identified developer.

---

## 3. Configuration

The **Configuration** tab is your central hub for customizing UIPTV.

### Player Settings
UIPTV offers flexibility in how you watch your content:
- **Embedded VLC Player**: This is the default and most robust option. It uses the VLC libraries installed on your system.
- **Embedded Lite Player**: A fallback player with basic functionality, useful if VLC is unavailable.
- **External Players**: You can configure up to three external players.
  1. Click **Browse...** to locate the executable of your preferred player (e.g., `mpv.exe`, `smplayer`).
  2. Use the **Radio Button** next to a player path to set it as the default player when you double-click a channel.

### Filtering Content
Keep your channel list clean and safe:
- **Category Filter**: Enter keywords (comma-separated) to hide entire categories (e.g., `adult, xxx, shopping`).
- **Channel Filter**: Enter specific channel names to exclude them from your list.
- **Pause Filtering**: Toggle this option to temporarily show all hidden content without deleting your filter lists.

### Appearance & Styling
Make UIPTV look the way you want:
- **Font Settings**: Adjust the font family, size, and weight to suit your readability preferences.
- **Dark Theme**: Enable this for a modern, eye-friendly dark interface.

### Cache Management
UIPTV uses a local SQLite database to cache channel lists and EPG data for faster loading.
- **Clear Cache**: If you experience missing channels or outdated data, click this to reset the local database.
- **Pause Caching**: Enable this if you prefer to fetch fresh data every time you load an account (may slow down startup).

---

## 4. Managing Accounts

Go to the **Manage Account** tab to add your IPTV sources. You can add multiple accounts and switch between them easily.

### Stalker Portal
Used by many IPTV providers.
1. Select **Stalker Portal** from the dropdown.
2. Enter the **Portal URL** provided by your service.
3. Enter your **MAC Address** (usually linked to your subscription).
4. Click **Add Account**.

### Xtream Codes
A popular API-based method.
1. Select **Xtream Codes API**.
2. Enter the **Host URL**, **Username**, and **Password**.
3. Click **Add Account**.

### M3U Playlists (Remote & Local)
- **Remote URL**: Paste the http/https link to your M3U playlist.
- **Local File**: Browse your computer to select a downloaded `.m3u` or `.m3u8` file.

### RSS Feeds (YouTube Support)
Turn UIPTV into a news or video feed reader.
1. Select **RSS Feed**.
2. Enter the RSS URL.
   - **YouTube Tip**: To follow a YouTube channel, use: `https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID`
3. Click **Add Account**.

**Note**: After adding an account, click **Parse Accounts** to load the channels.

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

---

## 6. Web Server & Remote Access

UIPTV includes an experimental web server, allowing you to watch your IPTV content on other devices (phones, tablets, smart TVs) via a web browser.

### Setting Up the Server
1. Go to the **Configuration** tab.
2. Set a **Port** (default is `8080`).
3. Click **Start Server**.

### Accessing via Browser
1. Find the IP address of your computer (e.g., `192.168.1.10`).
2. On your other device, open a browser and go to `http://192.168.1.10:8080`.
3. You will see a web interface where you can browse categories and play channels.

**Important Notes**:
- **HLS Only**: The web player primarily supports HLS (`.m3u8`) streams.
- **TS Support**: To play MPEG-TS (`.ts`) streams in the browser, you **must** have **FFmpeg** installed on the host computer. UIPTV uses FFmpeg to transmux the stream for web compatibility.

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

### YouTube Integration
To watch YouTube videos seamlessly:
1. Install **yt-dlp** and add it to your PATH.
2. Add YouTube RSS feeds as described in the [Accounts section](#rss-feeds-youtube-support).
3. When you play a video, UIPTV uses `yt-dlp` to fetch the stream URL and plays it in your chosen player.

### Troubleshooting

**Q: Channels are not loading.**
A: Check your internet connection and verify your subscription status. Try clicking **Clear Cache** and then **Parse Accounts** again.

**Q: Video plays but no audio (or vice versa).**
A: This is often a codec issue. We strongly recommend using **VLC** as your backend or an external player like **MPV** which handles almost all codecs.

**Q: Web player says "Error loading media".**
A:
- If it's a `.ts` stream, ensure **FFmpeg** is installed.
- Ensure the device you are watching on is on the same local network.
- Check if your firewall is blocking the port (default 8080).

**Q: The application is slow.**
A: Large playlists (20,000+ channels) can be heavy. Use the **Filter** options to exclude categories you don't watch (e.g., foreign languages), which will significantly speed up loading times.

---

**Enjoy using UIPTV!**
If you encounter bugs or have feature requests, please visit our [GitHub Repository](https://github.com/xixogo5105/uiptv).
