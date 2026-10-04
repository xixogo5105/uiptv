package com.uiptv.mobile.shared.db

/**
 * GENERATED FILE - DO NOT EDIT.
 *
 * Produced by the `generateUiptvSchemaMirror` Gradle task in `mobile/shared/build.gradle.kts`
 * from `core/src/main/resources/db/migrations/0000_baseline.sql`.
 *
 * `0000_baseline.sql` is the single schema artifact both platforms share: core loads it from the
 * classpath and androidApp repackages it as an asset. Generating the mirror from it means a new
 * column reaches the Android contract by adding one `ALTER TABLE` to the baseline, instead of
 * by editing this file and any other declaration and hoping every copy stays in step.
 *
 * Regenerate with:
 *   ./gradlew :shared:generateUiptvSchemaMirror
 *
 * `UiptvSyncSchemaBaselineContractTest` re-derives the same list from the baseline and fails
 * the build if this file is stale, so a hand edit cannot survive CI either.
 */
internal object UiptvGeneratedSchema {
    const val SCHEMA_VERSION: String = "0207"

    /** Configuration columns, in `0000_baseline.sql` declaration order. */
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

    /** Every table declared by the baseline, which is also the set that must survive a restore. */
    val baselineTables: List<String> = listOf(
        "Account",
        "AccountInfo",
        "Bookmark",
        "BookmarkCategory",
        "BookmarkOrder",
        "Category",
        "Channel",
        "Configuration",
        "PublishedM3uCategorySelection",
        "PublishedM3uChannelSelection",
        "PublishedM3uSelection",
        "SeriesCategory",
        "SeriesChannel",
        "SeriesEpisode",
        "SeriesWatchState",
        "SeriesWatchingNowSnapshot",
        "VodCategory",
        "VodChannel",
        "VodWatchState"
    )
}
