package com.uiptv.mobile.shared.db

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class UiptvSyncSchemaTest {
    @Test
    fun androidPullSyncUsesDesktopV1TableSet() {
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
                "Configuration"
            ),
            UiptvSyncSchema.syncableTables
        )
    }

    @Test
    fun androidRequiredTablesCoverPhaseOneBrowsingAndStateSchema() {
        assertTrue("Configuration" in UiptvSyncSchema.androidRequiredTables)
        assertTrue("Channel" in UiptvSyncSchema.androidRequiredTables)
        assertTrue("VodWatchState" in UiptvSyncSchema.androidRequiredTables)
        assertTrue("SeriesWatchingNowSnapshot" in UiptvSyncSchema.androidRequiredTables)
    }

    @Test
    fun desktopOnlyTablesArePreservedButHidden() {
        assertEquals(
            listOf(
                "ThemeCssOverride",
                "PublishedM3uSelection",
                "PublishedM3uCategorySelection",
                "PublishedM3uChannelSelection"
            ),
            UiptvSyncSchema.desktopTablesPreservedButHiddenInV1
        )
    }

    @Test
    fun portableConfigurationDoesNotIncludeDesktopPlayerOrServerSettings() {
        assertTrue("cacheExpiryDays" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("enableThumbnails" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertTrue("defaultPlayerPath" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
        assertTrue("serverPort" in UiptvSyncSchema.androidNeverSyncConfigurationColumns)
        assertFalse("defaultPlayerPath" in UiptvSyncSchema.androidPortableConfigurationColumns)
        assertFalse("serverPort" in UiptvSyncSchema.androidPortableConfigurationColumns)
    }

    @Test
    fun syncUsesOnlyCommonColumnsInSourceOrder() {
        val sourceColumns = listOf("id", "accountName", "desktopOnlyFutureColumn", "type")
        val targetColumns = listOf("type", "accountName", "id")

        assertEquals(
            listOf("id", "accountName", "type"),
            UiptvSyncSchema.commonSyncColumns(sourceColumns, targetColumns)
        )
    }
}
