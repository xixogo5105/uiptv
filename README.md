## UIPTV (Alpha Reelase)
This is IPTV player written in JAVA and can be natively compiled to Linux binary. 
This should also be able to natively compile to a binary on Windows/OSX but this has never been tried.

![UUIPTV](https://github.com/xixogo5105/uiptv/assets/161976171/ca298e57-034e-486f-ba2d-d0f795389da3)

### Features:
- External Player only: This IPTV player only works with external players only and has no built in player. Tested players are VLC, MPV, SMPLayer & Celluloid.
- Filter Categories/Channels: Exclude/Filter certain categoies or channels that are of no interest to a user. Important aspect of this feature is to censor specific contents (adult etc.). You can also pause or unpause the filtering. Please note that it will also impact web server contents.
- Styling: There is limited set of built in styling support. Users can change font, size & weight. They can also apply the stylesheet to components however that would need a recompile. There is also a dark which is applied to all components except the title bar.
- Web Server support (EXPERIMENTAL): You can also expose accounts & bookmarks via web server port and they will be immediately available (via web browser) to all devices on your network. A limited set of web html already available and can also be enhanced. Please note that only HLS compatible streams would be able to play in web player. Also HLS web player currently only support upto 1080p contents. There are some commercial web player that you can purchase to enable 4k support. You can also add custom style-sheet and custom JavaScript but they must be added to "web/css" & "web/javascript" folders respectively.
- Parse multiple stalker portal accounts: You can bulk import stalker portal accounts but they must comply to the format. Each line should have either a URL or mac address. All other lines are ignored. Please ensure that the text to be imported is in proper format.
- IPTV Protocols support: This player will support the following protocols/formats     
  - Stalker Portal: Support Live Channels/Video On Demand And Series     
  - M3U8 Local Playlist: Run a local file. playlist entries only     
  - M3U8 Remote Playlist: Run a remote URL file. playlist entries only     
  - XTREME: Support Live Channels/Video On Demand And Series
 - Favourite/Bookmarks (EXPERIMENTAL): User can bookmark favourite channels to quickly run them. Currently this support is available on live channels.
- Cache: This player uses Sqlite to save data. Data is saved on ~/.config/uiptv/uiptv.db (~\uiptv\uiptv.db on windows). Caching has some glitches (known issu) and currently meant to reduce repeated calls to the servers. Please use "clear cache" ocassionally to reset the cache. You can also "Pause Caching" Globally or at a certain account level.

### Compiling UIPTV
This application is built on Java 17 and JavaFX. This application can also be natively compiled using GraalVM.
Before compiling please make sure that you have added JAVA_HOME and GRAALVM_HOME in the path.

Example:

export JAVA_HOME=~/graalvm/graalvm-svm-java17-linux-gluon-22.1.0.1-Final

export GRAALVM_HOME=~/graalvm/graalvm-svm-java17-linux-gluon-22.1.0.1-Final

I also recommend to add to path:

export PATH=$JAVA_HOME/bin:$GRAALVM_HOME/bin:$PATH

Please install maven.

There are two ways you can run this project.

#### Method 1:
<p>
&emsp;This should work on Windows, OSX and linux.
<br />&emsp;mvn clean install. Go to target folder, copy /web folder, /lib folder and UIPTV.jar file in to your favourite folder.
<br />&emsp;Assuming you can copies the above files and folder in ~/uiptv/ folder then run:
<br />&emsp;cd ~/uiptv && java --module-path ./lib --add-modules=javafx.controls -jar ./UIPTV.jar
</p>
#### Method 2:

<br />&emsp;Native image. Its recommended because it runs a lot faster and smoother. 
<br />&emsp;Please head on to https://www.graalvm.org/22.0/reference-manual/native-image/
<br />&emsp;This page will guide to install prerequisite dependencies that are needed. 
<br />&emsp;To compile for Ubuntu for instance, you need to install 
<br />&emsp;sudo apt-get install build-essential libz-dev zlib1g-dev
<br />&emsp;Then compile with mvn gluonfx:build. 
<br />&emsp;Chances are that there will be further missing dependencies. mvn gluonfx:build may fail in this case, 
<br />&emsp;please carefully read the message as it will describe the missing dependecnies 
<br />&emsp;(or google error messages) Once everything is sucessfully compile please copy 
<br />&emsp;.../target/uiptv/target/gluonfx/x86_64-linux/UIPTV & web folder from .../target. make UIPTV executable 
<br />&emsp;and double click to run it.
### Misc
<p>When providing an external video player, you can also use the flatpak. just use the direct binary address.
For example, a standard vlc flatpak address is "/var/lib/flatpak/app/org.videolan.VLC/current/active/export/bin/org.videolan.VLC" which can be provided to run IPTV streams directly.
</p><p>
if you are using native mpv (/usr/bin/mpv) and the streams stops/freezes after a little while then
create an executable sh file with contents below and point the executable sh file  (e.g. ~/apps/mpv/mpv.sh) as an external player.
</p><p>
The contents of mpv.sh file:
    
    #!/bin/sh
    /usr/local/bin/mpv "$@"&

### Disclaimer/Final Thoughts
<p>This is currently only available in english. This may be turned into a multilingual project in future should a public demand deems it necessary.
Please be mindful that this is a fun/personal project only and not much research has been done to make it professional.
Please feel free to send merge request for additional functionality.
No donations are needed for this project at this moment. Instead, please consider donating to any of your favorite Linux project that is in need.
</p>
