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
    private String httpsServerPort;
    private String cacheExpiryDays;
    private String languageLocale;
    private String tmdbReadAccessToken;
    private String filterLockHash;
    private String filterLockUnlockDurationMinutes = "15";
    private String uiZoomPercent;
    private String vlcNetworkCachingMs;
    private String vlcLiveCachingMs;
    private String publishedM3uCategoryMode;
    private boolean pauseFiltering;
    private boolean pauseCaching;
    private boolean embeddedPlayer;
    private boolean wideView;
    private boolean autoRunServerOnStartup;
    private boolean httpsServerEnabled;
    private boolean enableThumbnails = true;
    private boolean enableVlcHttpUserAgent = true;
    private boolean enableVlcHttpForwardCookies = true;
    private boolean resolveChainAndDeepRedirects = false;
    private boolean webPlaybackProxyEnabled = true;
    private boolean vlcNoVideoTitleShow = true;
    private boolean vlcQuiet = true;
    private boolean vlcHttpReconnect = true;
    private boolean vlcAdaptiveUseAccess = true;
    private boolean lightweightModeEnabled = false;
    private String vlcVout;
    private String vlcAvcodecHw;
    private boolean showBookmarkAndAccountCounts = true;
    /**
     * Three-state theme selection, persisted in the {@code themeMode} column as {@code "light"},
     * {@code "dark"} or {@link ThemeMode#SYSTEM_VALUE}. An absent or unrecognised value means light.
     */
    private String themeMode;
    private boolean epgEnabled;
    private boolean epgPlaybackOnly;
    private String globalEpgSource = "";
    private String epgMappingsJson = "{}";
    private String epgRefreshIntervalHours = "2";
    private String epgSchedulerIntervalMinutes = "3";
    public int normalizedEpgRefreshHours() {
        try { return Math.clamp(Integer.parseInt(epgRefreshIntervalHours),1,24); }
        catch(RuntimeException _) { return 2; }
    }
    public int normalizedEpgSchedulerIntervalMinutes() {
        try {
            int value = Integer.parseInt(epgSchedulerIntervalMinutes);
            return java.util.List.of(1, 2, 3, 5, 10, 15, 30, 60).contains(value) ? value : 3;
        } catch (RuntimeException _) { return 3; }
    }


    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String serverPort, boolean embeddedPlayer, boolean enableThumbnails) {
        this.playerPath1 = playerPath1;
        this.playerPath2 = playerPath2;
        this.playerPath3 = playerPath3;
        this.defaultPlayerPath = defaultPlayerPath;
        this.filterCategoriesList = filterCategoriesList;
        this.filterChannelsList = filterChannelsList;
        this.pauseFiltering = pauseFiltering;
        this.serverPort = serverPort;
        this.embeddedPlayer = embeddedPlayer;
        this.enableThumbnails = enableThumbnails;
    }

    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String serverPort, boolean embeddedPlayer) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, serverPort, embeddedPlayer, true);
    }

    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String serverPort, boolean embeddedPlayer, String cacheExpiryDays, boolean enableThumbnails) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, serverPort, embeddedPlayer, enableThumbnails);
        this.cacheExpiryDays = cacheExpiryDays;
    }

    @SuppressWarnings("java:S107")
    public Configuration(String playerPath1, String playerPath2, String playerPath3, String defaultPlayerPath, String filterCategoriesList, String filterChannelsList, boolean pauseFiltering, String serverPort, boolean embeddedPlayer, String cacheExpiryDays) {
        this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, serverPort, embeddedPlayer, cacheExpiryDays, true);
    }

    /**
     * Resolves the three-state theme selection. Never returns {@code null}.
     */
    public ThemeMode resolveThemeMode() {
        return ThemeMode.fromPersisted(themeMode);
    }

    /**
     * Stores the selection.
     */
    public void applyThemeMode(ThemeMode mode) {
        this.themeMode = (mode == null ? ThemeMode.LIGHT : mode).persistedValue();
    }

}
