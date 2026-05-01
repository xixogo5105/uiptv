package com.uiptv.model;

import com.uiptv.shared.BaseJson;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@EqualsAndHashCode(callSuper = false)
public class Configuration extends BaseJson {
    private String dbId;
    private String playerPath1;
    private String playerPath2;
    private String playerPath3;
    private String defaultPlayerPath;
    private String filterCategoriesList;
    private String filterChannelsList;
    private String serverPort;
    private String cacheExpiryDays;
    private String languageLocale;
    private String tmdbReadAccessToken;
    private String uiZoomPercent;
    private String vlcNetworkCachingMs;
    private String vlcLiveCachingMs;
    private String publishedM3uCategoryMode;
    private boolean darkTheme;
    private boolean pauseFiltering;
    private boolean pauseCaching;
    private boolean embeddedPlayer;
    private boolean wideView;
    private boolean enableFfmpegTranscoding;
    private boolean enableLitePlayerFfmpeg;
    private boolean autoRunServerOnStartup;
    private boolean enableThumbnails = true;
    private boolean enableVlcHttpUserAgent = true;
    private boolean enableVlcHttpForwardCookies = true;
    private boolean resolveChainAndDeepRedirects = false;


    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding, boolean enableThumbnails) {
        this.playerPath1 = playerPath1;
        this.playerPath2 = playerPath2;
        this.playerPath3 = playerPath3;
        this.defaultPlayerPath = defaultPlayerPath;
        this.filterCategoriesList = filterCategoriesList;
        this.filterChannelsList = filterChannelsList;
        this.pauseFiltering = pauseFiltering;
        this.darkTheme = darkTheme;
        this.serverPort = serverPort;
        this.embeddedPlayer = embeddedPlayer;
        this.enableFfmpegTranscoding = enableFfmpegTranscoding;
        this.enableThumbnails = enableThumbnails;
    }

    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, true);
    }

    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding, String cacheExpiryDays, boolean enableThumbnails) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, enableThumbnails);
        this.cacheExpiryDays = cacheExpiryDays;
    }

    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, boolean darkTheme, String serverPort, boolean embeddedPlayer, boolean enableFfmpegTranscoding, String cacheExpiryDays) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, cacheExpiryDays, true);
    }

}
