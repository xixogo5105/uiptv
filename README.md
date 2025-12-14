<img src="https://github.com/xixogo5105/uiptv/assets/161976171/5563a042-157e-4ae7-bb6e-a72b38c8aa62"  width="64" height="64"  alt=""/>

# UIPTV - A Universal IPTV Player for Windows, Linux & macOS
UIPTV is a versatile IPTV player written in Java, designed to run on Windows, Linux, and macOS. At its core, UIPTV is a text-based IPTV player that also provides limited web support for accessing your content across your local network.

Native installers and packages are available for Windows (x86_64, aarch64), Linux (x86_64, aarch64), and macOS (x86_64, aarch64).

A detailed user guide is available at: [GUIDE.md](https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md)

Download the latest release [here](https://github.com/xixogo5105/uiptv/releases/latest).

![UIPTV](https://github.com/xixogo5105/uiptv/assets/161976171/ca298e57-034e-486f-ba2d-d0f795389da3)

## Features:
- **Embedded & External Player Support**: UIPTV now includes two embedded video players: a full-featured player powered by **VLC** and a limited, lightweight alternative.
  - For the best experience, it is highly recommended to have **VLC** installed on your system and available in the system's PATH.
  - If VLC is not found, the player will fall back to a basic, lightweight player with fewer features.
  - You can still configure an external player (like MPV, SMPlayer, etc.) for maximum control.
- **Filter Categories/Channels**: Exclude or filter out categories and channels that are of no interest. This feature is also useful for censoring specific content (e.g., adult channels). Filtering can be paused or unpaused and also impacts the content available through the web server.
- **Dark Mode/Styling**: A limited set of built-in styling options is available, allowing users to change the font, size, and weight. A dark mode is also included, which applies to all components.
- **Web Server (EXPERIMENTAL)**: Expose your accounts and bookmarks via a web server port, making them instantly available to any device on your local network through a web browser. Please note that only HLS-compatible streams will play in the web player.
- **Web Server Headless Support**: Run UIPTV in headless mode if you only intend to access channels through a browser.
- **Multiple IPTV Protocols**: The player supports the following protocols and formats:
  - **Stalker Portal**: Live Channels, Video On Demand, and Series.
  - **M3U Playlists**: Supports local and remote M3U files (EPG is not yet supported).
  - **Xtreme Codes**: Live Channels, Video On Demand, and Series.
  - **RSS Feeds**: Add and watch content from RSS feeds. This includes support for YouTube channels by using their RSS feed URL (e.g., `https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID`). For YouTube videos to play, **yt-dlp** must be installed and accessible in the system's PATH.
- **Tab-Based Interface**: The user interface has been updated from expandable panels to a more intuitive tab-based layout for managing accounts and settings.
- **About Page & Update Checks**: An "About" page has been added, which includes a mechanism to check for new application updates.
- **Cache Management**: UIPTV uses an SQLite database to cache data, reducing repeated calls to servers. You can "Clear Cache" or "Pause Caching" globally or on a per-account basis to manage performance.

## Compiling UIPTV
This application is built using **Java 25** and **JavaFX**, and it is packaged into native installers using **jpackage**.

Before compiling, please ensure you have:
1.  A JDK (version 25 or higher) installed, with the `JAVA_HOME` environment variable correctly set.
2.  Apache Maven installed.

To compile the project, you can use Maven profiles to target specific operating systems and package formats. The final application will be generated in the `target/dist` directory.

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
  - **DEB Package:**
    ```sh
    mvn clean package -P linux-x86_64,pkg-deb
    ```
  - **RPM Package:** (Requires `rpm` to be installed)
    ```sh
    mvn clean package -P linux-x86_64,pkg-rpm
    ```
  - **AppImage:**
    ```sh
    mvn clean package -P linux-x86_64,pkg-app-image
    ```

- **Linux (ARM64/aarch64):**
  - **DEB Package:**
    ```sh
    mvn clean package -P linux-aarch64,pkg-deb
    ```
  - **RPM Package:** (Requires `rpm` to be installed)
    ```sh
    mvn clean package -P linux-aarch64,pkg-rpm
    ```
  - **AppImage:**
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
This is a personal project developed for fun. EPG support is not yet implemented. While it is currently only available in English, it may become a multilingual project in the future if there is sufficient demand.

Merge requests for bug fixes or new features are always welcome.

Donations are neither needed nor accepted. If you wish to contribute, please consider donating to one of your favorite open-source projects, such as **[Linux Mint](https://www.linuxmint.com/donors.php)** or **[Ubuntu](https://ubuntu.com/download/desktop/thank-you#contributions-form)**.
