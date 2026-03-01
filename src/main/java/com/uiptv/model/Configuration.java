package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class Configuration extends BaseJson {
    private String dbId, playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, fontFamily, fontSize, fontWeight, serverPort, cacheExpiryDays;
    private boolean darkTheme, pauseFiltering, pauseCaching, embeddedPlayer, enableFfmpegTranscoding, enableThumbnails = true;


    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String fontFamily, String fontSize, String fontWeight, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding, boolean enableThumbnails) {
        this.playerPath1 = playerPath1;
        this.playerPath2 = playerPath2;
        this.playerPath3 = playerPath3;
        this.defaultPlayerPath = defaultPlayerPath;
        this.filterCategoriesList = filterCategoriesList;
        this.filterChannelsList = filterChannelsList;
        this.pauseFiltering = pauseFiltering;
        this.fontFamily = fontFamily;
        this.fontSize = fontSize;
        this.fontWeight = fontWeight;
        this.darkTheme = darkTheme;
        this.serverPort = serverPort;
        this.embeddedPlayer = embeddedPlayer;
        this.enableFfmpegTranscoding = enableFfmpegTranscoding;
        this.enableThumbnails = enableThumbnails;
    }

    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String fontFamily, String fontSize, String fontWeight, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, fontFamily, fontSize, fontWeight, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, true);
    }

    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String fontFamily, String fontSize, String fontWeight, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding, String cacheExpiryDays, boolean enableThumbnails) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, fontFamily, fontSize, fontWeight, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, enableThumbnails);
        this.cacheExpiryDays = cacheExpiryDays;
    }

    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String fontFamily, String fontSize, String fontWeight, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding, String cacheExpiryDays) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, fontFamily, fontSize, fontWeight, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, cacheExpiryDays, true);
    }
}
