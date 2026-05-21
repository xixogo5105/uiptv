package com.uiptv.mobile.shared.db

object UiptvSyncSchema {
    val syncableTables: List<String> = listOf(
        "Account",
        "AccountInfo",
        "Bookmark",
        "BookmarkCategory",
        "BookmarkOrder",
        "Category",
        "Channel",
        "VodCategory",
        "VodChannel",
        "VodWatchState",
        "SeriesCategory",
        "SeriesChannel",
        "SeriesEpisode",
        "SeriesWatchState",
        "SeriesWatchingNowSnapshot",
        "PublishedM3uSelection",
        "PublishedM3uCategorySelection",
        "PublishedM3uChannelSelection",
        "Configuration"
    )

    val androidRequiredTables: List<String> = listOf(
        "Account",
        "AccountInfo",
        "Bookmark",
        "BookmarkCategory",
        "BookmarkOrder",
        "Category",
        "Channel",
        "VodCategory",
        "VodChannel",
        "VodWatchState",
        "SeriesCategory",
        "SeriesChannel",
        "SeriesEpisode",
        "SeriesWatchState",
        "SeriesWatchingNowSnapshot",
        "PublishedM3uSelection",
        "PublishedM3uCategorySelection",
        "PublishedM3uChannelSelection",
        "Configuration"
    )

    val desktopTablesPreservedButHiddenInV1: List<String> = emptyList()

    val androidNeverSyncConfigurationColumns: Set<String> = setOf(
        "playerPath1",
        "playerPath2",
        "playerPath3",
        "defaultPlayerPath",
        "embeddedPlayer",
        "serverPort",
        "autoRunServerOnStartup",
        "darkTheme",
        "uiZoomPercent",
        "filterLockHash",
        "vlcNetworkCachingMs",
        "vlcLiveCachingMs",
        "enableVlcHttpUserAgent",
        "enableVlcHttpForwardCookies"
    )

    val configurationColumns: List<String> = listOf(
        "id",
        "playerPath1",
        "playerPath2",
        "playerPath3",
        "defaultPlayerPath",
        "filterCategoriesList",
        "filterChannelsList",
        "pauseFiltering",
        "darkTheme",
        "serverPort",
        "embeddedPlayer",
        "cacheExpiryDays",
        "enableThumbnails",
        "wideView",
        "languageLocale",
        "tmdbReadAccessToken",
        "filterLockHash",
        "uiZoomPercent",
        "autoRunServerOnStartup",
        "vlcNetworkCachingMs",
        "vlcLiveCachingMs",
        "publishedM3uCategoryMode",
        "enableVlcHttpUserAgent",
        "enableVlcHttpForwardCookies",
        "resolveChainAndDeepRedirects",
        "filterLockUnlockDurationMinutes"
    )

    val androidPortableConfigurationColumns: Set<String> =
        configurationColumns
            .filterNot { it == "id" || it in androidNeverSyncConfigurationColumns }
            .toSet()

    fun commonSyncColumns(sourceColumns: List<String>, targetColumns: List<String>): List<String> {
        val targetColumnSet = targetColumns.toSet()
        return sourceColumns.filter { it in targetColumnSet }
    }
}

data class TableSyncResult(
    val tableName: String,
    val rowCount: Int
)

data class DatabaseSyncReport(
    val tableResults: List<TableSyncResult>,
    val configurationRequested: Boolean = false,
    val configurationCopied: Boolean = false,
    val externalPlayerPathsIncluded: Boolean = false
) {
    val totalRowsSynced: Int = tableResults.sumOf { it.rowCount }
}
