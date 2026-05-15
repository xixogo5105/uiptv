package com.uiptv.mobile.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiptvSyncSchemaTest {
    @Test
    fun androidRequiredTablesMatchSyncableTables() {
        assertEquals(
            listOf(
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
            ),
            UiptvSyncSchema.syncableTables
        )
        assertEquals(UiptvSyncSchema.syncableTables, UiptvSyncSchema.androidRequiredTables)
        assertTrue("Configuration" in UiptvSyncSchema.syncableTables)
        assertTrue("PublishedM3uSelection" in UiptvSyncSchema.syncableTables)
        assertTrue("PublishedM3uCategorySelection" in UiptvSyncSchema.syncableTables)
        assertTrue("PublishedM3uChannelSelection" in UiptvSyncSchema.syncableTables)
    }

    @Test
    fun desktopOnlyTablesArePreservedButHidden() {
        assertEquals(listOf("ThemeCssOverride"), UiptvSyncSchema.desktopTablesPreservedButHiddenInV1)
    }

    @Test
    fun portableConfigurationColumnsExcludeMobileIgnoredColumns() {
        assertEquals(
            listOf(
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
                "enableFfmpegTranscoding",
                "cacheExpiryDays",
                "enableThumbnails",
                "wideView",
                "languageLocale",
                "tmdbReadAccessToken",
                "filterLockHash",
                "uiZoomPercent",
                "enableLitePlayerFfmpeg",
                "autoRunServerOnStartup",
                "vlcNetworkCachingMs",
                "vlcLiveCachingMs",
                "publishedM3uCategoryMode",
                "enableVlcHttpUserAgent",
                "enableVlcHttpForwardCookies",
                "resolveChainAndDeepRedirects",
                "filterLockUnlockDurationMinutes"
            ),
            UiptvSyncSchema.configurationColumns
        )
        UiptvSyncSchema.androidNeverSyncConfigurationColumns.forEach { ignoredColumn ->
            assertFalse(ignoredColumn in UiptvSyncSchema.androidPortableConfigurationColumns)
        }

        assertFalse("id" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("filterCategoriesList" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("filterChannelsList" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("pauseFiltering" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("cacheExpiryDays" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("enableThumbnails" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("wideView" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("publishedM3uCategoryMode" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("filterLockUnlockDurationMinutes" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("defaultPlayerPath" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
        assertTrue("embeddedPlayer" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
        assertTrue("serverPort" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
    }

    @Test
    fun commonSyncColumnsPreservesSourceOrderAndDropsMissingTargetColumns() {
        val source = listOf("id", "name", "missing", "enabled")
        val target = listOf("enabled", "id", "name")

        assertEquals(listOf("id", "name", "enabled"), UiptvSyncSchema.commonSyncColumns(source, target))
    }

    @Test
    fun databaseSyncReportSumsRowsAndKeepsFlags() {
        val report = DatabaseSyncReport(
            tableResults = listOf(
                TableSyncResult("Account", 2),
                TableSyncResult("Bookmark", 3),
                TableSyncResult("Configuration", 1)
            ),
            configurationRequested = true,
            configurationCopied = true,
            externalPlayerPathsIncluded = false
        )

        assertEquals(6, report.totalRowsSynced)
        assertTrue(report.configurationRequested)
        assertTrue(report.configurationCopied)
        assertFalse(report.externalPlayerPathsIncluded)
    }
}
