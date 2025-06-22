<img src="https://github.com/xixogo5105/uiptv/assets/161976171/5563a042-157e-4ae7-bb6e-a72b38c8aa62"  width="64" height="64"  alt=""/>

# UIPTV - A Universal Windows, Linux & Mac (OSX) IPTV Player
UIPTV is an IPTV player written in JAVA and can be natively compiled to Linux binary. UIPTV is Linux at heart and built on the Linux philosophy.
UIPTV, unlike a traditional IPTV player, is purposely designed to be a text-based IPTV player. It also provides limited web support.
UIPTV binaries are available for Linux (x86-64 only), Windows (64-bit only), and Mac OSX (for silicon or arch64 only).

UIPTV user guide is also available at <a href="https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md">https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md</a>

Download the latest release <a href="https://github.com/xixogo5105/uiptv/releases/latest">here</a>

![UUIPTV](https://github.com/xixogo5105/uiptv/assets/161976171/ca298e57-034e-486f-ba2d-d0f795389da3)

## Features:
- **External Player only**: This IPTV player works with external players only and has no built-in player. Tested players are VLC, MPV, SMPLayer & Celluloid. Using an external player gives you complete control and allows the end user to run multiple streams at a time.
- **Filter Categories/Channels**: Exclude/Filter certain categories or channels that are of no interest to a user. An important aspect of this feature is to censor specific content (adult etc.). You can also pause or unpause the filtering. Please note that it will also impact web server contents.
- **Dark Mode/Styling**: There is a limited set of built-in styling support. Users can change font, size & weight. They can also apply the stylesheet to components; however, that would need a recompile. There is also a dark mode which is applied to all components except the title bar.
- **Web Server support (EXPERIMENTAL)**: You can also expose accounts & bookmarks via web server port and they will be immediately available (via web browser) to all devices on your network. A limited set of web HTML is already available and can also be enhanced. Please note that only HLS-compatible streams would be able to play in the web player. Also, the HLS web player currently only supports up to 1080p content. There are some commercial web players that you can purchase to enable 4k support. You can also add custom style-sheets and custom JavaScript but they must be added to the `web/css` & `web/javascript` folders respectively. Assuming the web server is exposed to port 8080, the URL on your local machine will be `http://localhost:8080/`. Replace `localhost` with your IP address (again assuming the IP address and port are available on the local network) to access it on the local network.
- **Web Server Headless support**: You can also run UIPTV in headless mode if the intention is to only access the channels through a browser. Your pwd must be where the `/WEB` folder exists. Assuming UIPTV & `/WEB` exist in `/usr/local/bin/uiptv` (default path when you install through `UIPTV.deb`) then execute the following command from the terminal:

  _cd /usr/local/bin/uiptv && /usr/local/bin/uiptv/UIPTV --headless_

- **Parse multiple stalker portal accounts**: You can bulk import stalker portal accounts but they must comply with the format. Each line should have either a URL or mac address. All other lines are ignored. Please ensure that the text to be imported is in the proper format.
- **IPTV Protocols support**: This player will support the following protocols/formats
  - **Stalker Portal**: Support Live Channels, Video On Demand, and Series
  - **M3U8 Local Playlist**: Run a local file. Playlist entries only. EPG is not yet supported.
  - **M3U8 Remote Playlist**: Run a remote URL file. Playlist entries only. EPG is not yet supported.
  - **XTREME**: Support Live Channels, Video On Demand, and Series
  - **RSS**: Support for adding RSS feeds, including YouTube channels as RSS feeds

- **Favourite/Bookmarks (EXPERIMENTAL)**: Users can bookmark favourite channels to quickly run them. This support is available on live channels at this moment.
- **Cache**: This player uses SQLite to save and cache data. Data is saved in the file `_~/.config/uiptv/uiptv.db_` (`_~\uiptv\uiptv.db_` on Windows). Caching has some glitches (known issue) and is currently meant to reduce repeated calls to the servers. Please use "clear cache" occasionally to reset the cache. You can also "Pause Caching" globally or at a certain account level.
- **RSS Feed Support**: You can add RSS feeds to the player. This includes support for YouTube channels as RSS feeds. Here is an example of how to use YouTube channels as RSS feeds:

  1. Find the channel ID of the YouTube channel you want to add.
  2. Use the following URL format to add the RSS feed: `https://www.youtube.com/feeds/videos.xml?channel_id=CHANNEL_ID`
  3. Add this URL to the UIPTV configuration.

## Compiling UIPTV
This application is built on Java 17 and JavaFX. This application can also be natively compiled using GraalVM.
Before compiling please make sure that you have added **JAVA_HOME** and **GRAALVM_HOME** in the path.

Example:

    export JAVA_HOME=~/graalvm/graalvm-svm-java17-linux-gluon-22.1.0.1-Final
    export GRAALVM_HOME=~/graalvm/graalvm-svm-java17-linux-gluon-22.1.0.1-Final

It is also recommended to add **JAVA_HOME** and **GRAALVM_HOME** variables to the path variable:

    export PATH=$JAVA_HOME/bin:$GRAALVM_HOME/bin:$PATH

Please install maven:

    sudo apt install maven

There are two ways you can run this project.

#### Method 1:
This should work on Windows, OSX, and Linux.

    mvn clean install

Go to the target folder, copy the `/web` folder, `/lib` folder, and `UIPTV.jar` file into your favourite folder (assuming you have copied the above files and folder into `~/uiptv/` folder) and then run:

    cd ~/uiptv && java --module-path ./lib --add-modules=javafx.controls -jar ./UIPTV.jar

You may also create a shell script for the above.

#### Method 2 (Native binary image):

It's recommended because it runs a lot faster and smoother. Please head on to [GraalVM Manual](https://www.graalvm.org/22.0/reference-manual/native-image/). This page has step-by-step guidelines and helps you to install prerequisite dependencies that are needed. In order to compile UIPTV on Ubuntu for instance, you need to install:

    sudo apt-get install build-essential libz-dev zlib1g-dev
Then compile with:

    mvn gluonfx:build

Chances are that there will be further missing dependencies. _mvn gluonfx:build_ may fail in this case,
please carefully read the message as it will describe the missing dependencies (or google error messages). Once everything is successfully compiled, copy
`.../target/uiptv/target/gluonfx/x86_64-linux/UIPTV` & `web` folder from `.../target` to a folder of your own choice (assuming you have copied the above files and folder into `~/uiptv/` folder).
Then go to that folder, make the `UIPTV` binary executable and simply double-click to run it.

## Misc

When providing an external video player, you can also use the flatpak. Just use the direct binary address.
For example, a standard VLC flatpak address is `_ /var/lib/flatpak/app/org.videolan.VLC/current/active/export/bin/org.videolan.VLC_` which can be provided to run IPTV streams directly.

If you are using native MPV (e.g. `_ /usr/bin/mpv_`) and the streams stop/freeze after a little while then
create an executable sh file with the contents below and point the executable sh file (e.g. `_ ~/apps/mpv/mpv.sh_`) as an external player.

The contents of `_ mpv.sh_` file:

    #!/bin/sh
    /usr/local/bin/mpv "$@"&

on MAC, you also use below sh script to play stream. the script will act as player to UIPTV. It will open the kodi with the stream url or (and if remote access is enabled) send remote request to open the stream if the kodi is already working. 

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

## Disclaimer/Final Thoughts
This is currently only available in **English**. **EPG** is not yet supported. This may be turned into a multilingual project in the future if there is demand for it.
Please be mindful that this is a fun/personal project only and not much enterprise-level research has been spent on it.
Please feel free to send merge requests for bug fixes or additional features or functionality.
Donations are neither needed nor accepted at this moment for UIPTV as it's just a fun/personal project. Instead, please consider donating to any of your favorite Linux projects that are in need.

Donate to **[Linux Mint](https://www.linuxmint.com/donors.php)** or **[Ubuntu](https://ubuntu.com/download/desktop/thank-you#contributions-form)**
