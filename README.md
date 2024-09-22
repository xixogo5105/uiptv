<img src="https://github.com/xixogo5105/uiptv/assets/161976171/5563a042-157e-4ae7-bb6e-a72b38c8aa62"  width="64" height="64"  alt=""/>

# UIPTV
This is IPTV player written in JAVA and can be natively compiled to Linux binary. UIPTV is Linux at heart and build on linux philosophy. 
UIPTV, unlike a traditional IPTV player, is purposely designed to be a text based IPTV player. It also provides a limited web support. 
UIPTV should also be able to natively compile to a binary on Windows/OSX but this has never been tested so far.

UIPTV user guide is also available at <a href="https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md">https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md</a>

![UUIPTV](https://github.com/xixogo5105/uiptv/assets/161976171/ca298e57-034e-486f-ba2d-d0f795389da3)

## Features:
- **External Player only**: This IPTV player works with external players only and has no built-in player. Tested players are VLC, MPV, SMPLayer & Celluloid.  Using external player gives you a complete control as well as allow end user to run multiple streams at a time.
- **Filter Categories/Channels**: Exclude/Filter certain categories or channels that are of no interest to a user. Important aspect of this feature is to censor specific contents (adult etc.). You can also pause or unpause the filtering. Please note that it will also impact web server contents.
- **Dark Mode/Styling**: There is limited set of built-in styling support. Users can change font, size & weight. They can also apply the stylesheet to components however that would need a recompile. There is also a dark mode which is applied to all components except the title bar.
- **Web Server support (EXPERIMENTAL)**: You can also expose accounts & bookmarks via web server port and they will be immediately available (via web browser) to all devices on your network. A limited set of web html already available and can also be enhanced. Please note that only HLS compatible streams would be able to play in web player. Also HLS web player currently only support up to 1080p contents. There are some commercial web player that you can purchase to enable 4k support. You can also add custom style-sheet and custom JavaScript but they must be added to "web/css" & "web/javascript" folders respectively. Assuming web server is exposed to 8080 port, the URL on your local machine will be http://localhost:8080/. Replace localhost with your IP address (again assuming ip address and port are avilable on local network) to access it on local network.
- **Web Server Headless support**: You can also run UIPTV in headless mode if the intention is to only access the channels through a browser. your pwd must be where /WEB folder exists. Assuming UIPTV & /WEB exists in /usr/local/bin/uiptv (default path when you install through UIPTV.deb) then execute the following command from terminal:
  
  _cd /usr/local/bin/uiptv && /usr/local/bin/uiptv/UIPTV --headless_
   
- **Parse multiple stalker portal accounts**: You can bulk import stalker portal accounts but they must comply to the format. Each line should have either a URL or mac address. All other lines are ignored. Please ensure that the text to be imported is in proper format.
- **IPTV Protocols support**: This player will support the following protocols/formats     
  - **Stalker Portal**: Support Live Channels, Video On Demand And Series     
  - **M3U8 Local Playlist**: Run a local file. playlist entries only.  EPG is not yet supported.     
  - **M3U8 Remote Playlist**: Run a remote URL file. playlist entries only.  EPG is not yet supported.   
  - **XTREME**: Support Live Channels, Video On Demand And Series
- **Favourite/Bookmarks (EXPERIMENTAL)**: User can bookmark favourite channels to quickly run them. This support is available on live channels at this moment.
- **Cache**: This player uses Sqlite to save and cache data. Data is saved on file _\~/.config/uiptv/uiptv.db_ (_\~\uiptv\uiptv.db_ on windows). Caching has some glitches (known issue) and currently meant to reduce repeated calls to the servers. Please use "clear cache" occasionally to reset the cache. You can also "Pause Caching" globally or at a certain account level.

## Compiling UIPTV
This application is built on Java 17 and JavaFX. This application can also be natively compiled using GraalVM.
Before compiling please make sure that you have added **JAVA_HOME** and **GRAALVM_HOME** in the path.

Example:

    export JAVA_HOME=~/graalvm/graalvm-svm-java17-linux-gluon-22.1.0.1-Final
    export GRAALVM_HOME=~/graalvm/graalvm-svm-java17-linux-gluon-22.1.0.1-Final

It is also recommended to add **JAVA_HOME** and **GRAALVM_HOME** variables to path variable:

    export PATH=$JAVA_HOME/bin:$GRAALVM_HOME/bin:$PATH

Please install maven:

    sudo apt install maven

There are two ways you can run this project.

#### Method 1:
This should work on Windows, OSX and linux.

    mvn clean install 
    
Go to target folder, copy _/web_ folder, _/lib_ folder and _UIPTV.jar_ file in to your favourite folder (assuming you have copied the above files and folder into _~/uiptv/_ folder) and then run:

    cd ~/uiptv && java --module-path ./lib --add-modules=javafx.controls -jar ./UIPTV.jar

you may also create a shell script for the above.

#### Method 2 (Native binary image):

Its recommended because it runs a lot faster and smoother. Please head on to [GraalVM Manual](https://www.graalvm.org/22.0/reference-manual/native-image/). This page has step by step guidelines and help you to install prerequisite dependencies that are needed. In order to compile UIPTV on Ubuntu for instance, you need to install:

    sudo apt-get install build-essential libz-dev zlib1g-dev
Then compile with:

    mvn gluonfx:build
    
Chances are that there will be further missing dependencies. _mvn gluonfx:build_ may fail in this case, 
please carefully read the message as it will describe the missing dependencies (or google error messages) Once everything is successfully compiled, copy 
_.../target/uiptv/target/gluonfx/x86_64-linux/UIPTV_ & _web_ folder from _.../target_ to a folder of your own choice (assuming you have copied the above files and folder into _~/uiptv/_ folder). 
Then go to that folder, make _UIPTV_ binary as executable and simply double click to run it.

## Misc

When providing an external video player, you can also use the flatpak. just use the direct binary address.
For example, a standard vlc flatpak address is "_/var/lib/flatpak/app/org.videolan.VLC/current/active/export/bin/org.videolan.VLC_" which can be provided to run IPTV streams directly.

if you are using native mpv (e.g. _/usr/bin/mpv_) and the streams stops/freezes after a little while then
create an executable sh file with contents below and point the executable sh file  (e.g. _~/apps/mpv/mpv.sh_) as an external player.

The contents of _mpv.sh_ file:
    
    #!/bin/sh
    /usr/local/bin/mpv "$@"&

## Disclaimer/Final Thoughts
This is currently only available in **English**. **EPG** is not yet supported. This may be turned into a multilingual project in future if there is demand of it.
Please be mindful that this is a fun/personal project only and not much enterprise level research has been spent on it.
Please feel free to send merge request for bug fixes or additional features or functionality. 
Donations are neither needed nor accepted at this moment for UIPTV as its just a fun/personal project. Instead, please consider donating to any of your favorite Linux project that is in need.  

Donate to **[Linux Mint](https://www.linuxmint.com/donors.php)** or **[Ubuntu](https://ubuntu.com/download/desktop/thank-you#contributions-form)**
