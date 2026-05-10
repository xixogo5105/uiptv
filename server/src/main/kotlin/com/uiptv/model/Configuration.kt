package com.uiptv.model

import com.uiptv.shared.BaseJson

data class Configuration @JvmOverloads constructor(
    var dbId: String? = null,
    var playerPath1: String? = null,
    var playerPath2: String? = null,
    var playerPath3: String? = null,
    var defaultPlayerPath: String? = null,
    var filterCategoriesList: String? = null,
    var filterChannelsList: String? = null,
    var serverPort: String? = null,
    var cacheExpiryDays: String? = null,
    var languageLocale: String? = null,
    var tmdbReadAccessToken: String? = null,
    var filterLockHash: String? = null,
    var filterLockUnlockDurationMinutes: String? = "15",
    var uiZoomPercent: String? = null,
    var vlcNetworkCachingMs: String? = null,
    var vlcLiveCachingMs: String? = null,
    var publishedM3uCategoryMode: String? = null,
    @get:JvmName("isDarkTheme")
    var darkTheme: Boolean = false,
    @get:JvmName("isPauseFiltering")
    var pauseFiltering: Boolean = false,
    @get:JvmName("isPauseCaching")
    var pauseCaching: Boolean = false,
    @get:JvmName("isEmbeddedPlayer")
    var embeddedPlayer: Boolean = false,
    @get:JvmName("isWideView")
    var wideView: Boolean = false,
    @get:JvmName("isEnableFfmpegTranscoding")
    var enableFfmpegTranscoding: Boolean = false,
    @get:JvmName("isEnableLitePlayerFfmpeg")
    var enableLitePlayerFfmpeg: Boolean = false,
    @get:JvmName("isAutoRunServerOnStartup")
    var autoRunServerOnStartup: Boolean = false,
    @get:JvmName("isEnableThumbnails")
    var enableThumbnails: Boolean = true,
    @get:JvmName("isEnableVlcHttpUserAgent")
    var enableVlcHttpUserAgent: Boolean = true,
    @get:JvmName("isEnableVlcHttpForwardCookies")
    var enableVlcHttpForwardCookies: Boolean = true,
    @get:JvmName("isResolveChainAndDeepRedirects")
    var resolveChainAndDeepRedirects: Boolean = false
) : BaseJson() {
    @Suppress("java:S107")
    constructor(
        playerPath1: String?,
        playerPath2: String?,
        playerPath3: String?,
        defaultPlayerPath: String?,
        filterCategoriesList: String?,
        filterChannelsList: String?,
        pauseFiltering: Boolean,
        darkTheme: Boolean,
        serverPort: String?,
        embeddedPlayer: Boolean,
        enableFfmpegTranscoding: Boolean,
        enableThumbnails: Boolean
    ) : this(null, playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, serverPort, null, null, null, null, "15", null, null, null, null, darkTheme, pauseFiltering, false, embeddedPlayer, false, enableFfmpegTranscoding, false, false, enableThumbnails, true, true, false)

    @Suppress("java:S107")
    constructor(
        playerPath1: String?,
        playerPath2: String?,
        playerPath3: String?,
        defaultPlayerPath: String?,
        filterCategoriesList: String?,
        filterChannelsList: String?,
        pauseFiltering: Boolean,
        darkTheme: Boolean,
        serverPort: String?,
        embeddedPlayer: Boolean,
        enableFfmpegTranscoding: Boolean
    ) : this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, true)

    @Suppress("java:S107")
    constructor(
        playerPath1: String?,
        playerPath2: String?,
        playerPath3: String?,
        defaultPlayerPath: String?,
        filterCategoriesList: String?,
        filterChannelsList: String?,
        pauseFiltering: Boolean,
        darkTheme: Boolean,
        serverPort: String?,
        embeddedPlayer: Boolean,
        enableFfmpegTranscoding: Boolean,
        cacheExpiryDays: String?,
        enableThumbnails: Boolean
    ) : this(null, playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, serverPort, cacheExpiryDays, null, null, null, "15", null, null, null, null, darkTheme, pauseFiltering, false, embeddedPlayer, false, enableFfmpegTranscoding, false, false, enableThumbnails, true, true, false)

    @Suppress("java:S107")
    constructor(
        playerPath1: String?,
        playerPath2: String?,
        playerPath3: String?,
        defaultPlayerPath: String?,
        filterCategoriesList: String?,
        filterChannelsList: String?,
        pauseFiltering: Boolean,
        darkTheme: Boolean,
        serverPort: String?,
        embeddedPlayer: Boolean,
        enableFfmpegTranscoding: Boolean,
        cacheExpiryDays: String?
    ) : this(playerPath1, playerPath2, playerPath3, defaultPlayerPath, filterCategoriesList, filterChannelsList, pauseFiltering, darkTheme, serverPort, embeddedPlayer, enableFfmpegTranscoding, cacheExpiryDays, true)
}
