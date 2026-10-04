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
        "themeMode",
        "uiZoomPercent",
        "filterLockHash",
        "vlcNetworkCachingMs",
        "vlcLiveCachingMs",
        "enableVlcHttpUserAgent",
        "enableVlcHttpForwardCookies"
    )

    /**
     * Every column of the `Configuration` table, in the exact order declared by
     * `core/src/main/resources/db/migrations/0000_baseline.sql` (mirrored by `DatabaseUtils.dbStructure`).
     *
     * This must stay a complete, ordered mirror rather than the subset the Android UI happens to read: the
     * desktop app can restore a database produced on Android, so any column missing here is a column the
     * desktop side has no contract for. `UiptvSyncSchemaBaselineContractTest` and
     * `AndroidUiptvDatabaseCompatibilityTest.createsDesktopCompatibleTablesFromPackagedMigrations` both fail if
     * this list drifts from the baseline.
     */
    val configurationColumns: List<String> = listOf(
        "id",
        "playerPath1",
        "playerPath2",
        "playerPath3",
        "defaultPlayerPath",
        "filterCategoriesList",
        "filterChannelsList",
        "pauseFiltering",
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
        "httpsServerEnabled",
        "httpsServerPort",
        "vlcNetworkCachingMs",
        "vlcLiveCachingMs",
        "publishedM3uCategoryMode",
        "enableVlcHttpUserAgent",
        "enableVlcHttpForwardCookies",
        "resolveChainAndDeepRedirects",
        "filterLockUnlockDurationMinutes",
        "vlcNoVideoTitleShow",
        "vlcQuiet",
        "vlcHttpReconnect",
        "vlcAdaptiveUseAccess",
        "vlcVout",
        "vlcAvcodecHw",
        "lightweightModeEnabled",
        "showBookmarkAndAccountCounts",
        "themeMode"
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
