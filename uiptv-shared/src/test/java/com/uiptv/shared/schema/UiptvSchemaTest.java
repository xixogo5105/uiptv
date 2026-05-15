package com.uiptv.shared.schema;

import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UiptvSchemaTest {
    @Test
    void syncableTablesMatchCurrentDesktopContract() {
        Set<String> names = UiptvSchema.SYNCABLE_TABLES.stream()
                .map(UiptvTable::tableName)
                .collect(Collectors.toSet());

        assertEquals(Set.of("Account", "AccountInfo", "Bookmark", "BookmarkCategory", "BookmarkOrder"), names);
    }

    @Test
    void androidPortableConfigurationExcludesDesktopPlayerAndServerSettings() {
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("filterCategoriesList"));
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("filterChannelsList"));
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("pauseFiltering"));
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("cacheExpiryDays"));
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("enableThumbnails"));
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("wideView"));
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("publishedM3uCategoryMode"));
        assertTrue(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("filterLockUnlockDurationMinutes"));
        assertFalse(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("defaultPlayerPath"));
        assertFalse(UiptvSchema.ANDROID_PORTABLE_CONFIGURATION_COLUMNS.contains("embeddedPlayer"));
        assertTrue(UiptvSchema.ANDROID_LOCAL_CONFIGURATION_COLUMNS.contains("defaultPlayerPath"));
        assertTrue(UiptvSchema.ANDROID_LOCAL_CONFIGURATION_COLUMNS.contains("embeddedPlayer"));
        assertTrue(UiptvSchema.ANDROID_LOCAL_CONFIGURATION_COLUMNS.contains("serverPort"));
    }

    @Test
    void androidPullSyncTableOrderIncludesBrowsingBookmarksAndConfiguration() {
        Set<String> names = UiptvSchema.ANDROID_PULL_SYNC_TABLE_ORDER.stream()
                .map(UiptvTable::tableName)
                .collect(Collectors.toSet());

        assertEquals(
                Set.of(
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
                names
        );
    }

    @Test
    void migrationMetadataTracksCurrentSchemaVersion() {
        assertEquals("0197", UiptvMigrationInfo.CURRENT_SCHEMA_VERSION);
        assertEquals(UiptvMigrationInfo.CURRENT_SCHEMA_VERSION, UiptvSchema.CURRENT_SCHEMA_VERSION);
        assertEquals("0000_baseline.sql", UiptvMigrationInfo.BASELINE_MIGRATION);
    }

    @Test
    void syncableTableColumnsTrackDesktopSchema() {
        Set<String> accountColumns = UiptvSchema.columnsFor(UiptvTable.ACCOUNT).stream()
                .map(DataColumn::name)
                .collect(Collectors.toSet());
        Set<String> bookmarkOrderColumns = UiptvSchema.columnsFor(UiptvTable.BOOKMARK_ORDER).stream()
                .map(DataColumn::name)
                .collect(Collectors.toSet());
        Set<String> publishedM3uSelectionColumns = UiptvSchema.columnsFor(UiptvTable.PUBLISHED_M3U_SELECTION).stream()
                .map(DataColumn::name)
                .collect(Collectors.toSet());
        Set<String> publishedM3uChannelSelectionColumns = UiptvSchema.columnsFor(UiptvTable.PUBLISHED_M3U_CHANNEL_SELECTION).stream()
                .map(DataColumn::name)
                .collect(Collectors.toSet());

        assertTrue(accountColumns.contains("xtremeCredentialsJson"));
        assertTrue(accountColumns.contains("timezone"));
        assertTrue(bookmarkOrderColumns.contains("bookmark_db_id"));
        assertTrue(bookmarkOrderColumns.contains("display_order"));
        assertTrue(publishedM3uSelectionColumns.contains("accountId"));
        assertTrue(publishedM3uChannelSelectionColumns.contains("categoryName"));
        assertTrue(publishedM3uChannelSelectionColumns.contains("channelId"));
        assertTrue(publishedM3uChannelSelectionColumns.contains("selected"));
    }
}
