package com.uiptv.shared.schema;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UiptvSchema {
    public static final String CURRENT_SCHEMA_VERSION = UiptvMigrationInfo.CURRENT_SCHEMA_VERSION;
    public static final String BASELINE_MIGRATION = UiptvMigrationInfo.BASELINE_MIGRATION;
    public static final String MIGRATION_RESOURCE_PATH = UiptvMigrationInfo.MIGRATION_RESOURCE_PATH;

    public static final Set<UiptvTable> CACHEABLE_TABLES = Collections.unmodifiableSet(EnumSet.of(
            UiptvTable.CATEGORY,
            UiptvTable.CHANNEL,
            UiptvTable.VOD_CATEGORY,
            UiptvTable.VOD_CHANNEL,
            UiptvTable.SERIES_CATEGORY,
            UiptvTable.SERIES_CHANNEL,
            UiptvTable.SERIES_EPISODE
    ));

    public static final Set<UiptvTable> SYNCABLE_TABLES = Collections.unmodifiableSet(EnumSet.of(
            UiptvTable.ACCOUNT,
            UiptvTable.ACCOUNT_INFO,
            UiptvTable.BOOKMARK,
            UiptvTable.BOOKMARK_CATEGORY,
            UiptvTable.BOOKMARK_ORDER
    ));

    public static final Map<UiptvTable, List<DataColumn>> TABLE_COLUMNS = tableColumns();

    public static final Set<String> CONFIGURATION_SYNC_EXCLUDED_COLUMNS = Set.of("filterLockHash");

    public static final Set<String> ANDROID_PORTABLE_CONFIGURATION_COLUMNS = Set.of(
            "cacheExpiryDays",
            "enableThumbnails",
            "languageLocale",
            "tmdbReadAccessToken",
            "resolveChainAndDeepRedirects"
    );

    public static final Set<String> ANDROID_LOCAL_CONFIGURATION_COLUMNS = Set.of(
            "playerPath1",
            "playerPath2",
            "playerPath3",
            "defaultPlayerPath",
            "embeddedPlayer",
            "enableFfmpegTranscoding",
            "enableLitePlayerFfmpeg",
            "serverPort",
            "autoRunServerOnStartup",
            "darkTheme",
            "uiZoomPercent",
            "vlcNetworkCachingMs",
            "vlcLiveCachingMs",
            "enableVlcHttpUserAgent",
            "enableVlcHttpForwardCookies",
            "filterLockHash"
    );

    public static final Set<String> ANDROID_NEVER_SYNC_CONFIGURATION_COLUMNS = ANDROID_LOCAL_CONFIGURATION_COLUMNS;

    public static final List<UiptvTable> ANDROID_PULL_SYNC_TABLE_ORDER = List.of(
            UiptvTable.ACCOUNT,
            UiptvTable.ACCOUNT_INFO,
            UiptvTable.BOOKMARK,
            UiptvTable.BOOKMARK_CATEGORY,
            UiptvTable.BOOKMARK_ORDER
    );

    private UiptvSchema() {
    }

    public static List<DataColumn> columnsFor(UiptvTable table) {
        List<DataColumn> columns = TABLE_COLUMNS.get(table);
        if (columns == null) {
            throw new IllegalArgumentException("No shared schema contract for table: " + table);
        }
        return columns;
    }

    private static Map<UiptvTable, List<DataColumn>> tableColumns() {
        Map<UiptvTable, List<DataColumn>> columns = new LinkedHashMap<>();
        columns.put(UiptvTable.CONFIGURATION, List.of(
                column("id", "INTEGER PRIMARY KEY"),
                column("playerPath1", "TEXT"),
                column("playerPath2", "TEXT"),
                column("playerPath3", "TEXT"),
                column("defaultPlayerPath", "TEXT"),
                column("filterCategoriesList", "TEXT"),
                column("filterChannelsList", "TEXT"),
                column("pauseFiltering", "TEXT"),
                column("darkTheme", "TEXT"),
                column("serverPort", "TEXT"),
                column("embeddedPlayer", "TEXT"),
                column("enableFfmpegTranscoding", "TEXT"),
                column("cacheExpiryDays", "TEXT"),
                column("enableThumbnails", "TEXT"),
                column("wideView", "TEXT"),
                column("languageLocale", "TEXT"),
                column("tmdbReadAccessToken", "TEXT"),
                column("filterLockHash", "TEXT"),
                column("uiZoomPercent", "TEXT"),
                column("enableLitePlayerFfmpeg", "TEXT"),
                column("autoRunServerOnStartup", "TEXT"),
                column("vlcNetworkCachingMs", "TEXT"),
                column("vlcLiveCachingMs", "TEXT"),
                column("publishedM3uCategoryMode", "TEXT"),
                column("enableVlcHttpUserAgent", "TEXT"),
                column("enableVlcHttpForwardCookies", "TEXT"),
                column("resolveChainAndDeepRedirects", "TEXT"),
                column("filterLockUnlockDurationMinutes", "TEXT")
        ));
        columns.put(UiptvTable.ACCOUNT, List.of(
                column("id", "INTEGER PRIMARY KEY"),
                column("accountName", "TEXT NOT NULL UNIQUE"),
                column("username", "TEXT"),
                column("password", "TEXT"),
                column("xtremeCredentialsJson", "TEXT"),
                column("url", "TEXT"),
                column("macAddress", "TEXT"),
                column("macAddressList", "TEXT"),
                column("serialNumber", "TEXT"),
                column("deviceId1", "TEXT"),
                column("deviceId2", "TEXT"),
                column("signature", "TEXT"),
                column("epg", "TEXT"),
                column("m3u8Path", "TEXT"),
                column("type", "TEXT"),
                column("serverPortalUrl", "TEXT"),
                column("pinToTop", "TEXT"),
                column("resolveChainAndDeepRedirects", "TEXT"),
                column("httpMethod", "TEXT"),
                column("timezone", "TEXT")
        ));
        columns.put(UiptvTable.ACCOUNT_INFO, List.of(
                column("id", "INTEGER PRIMARY KEY"),
                column("accountId", "TEXT NOT NULL UNIQUE"),
                column("expireDate", "TEXT"),
                column("accountStatus", "TEXT"),
                column("accountBalance", "TEXT"),
                column("tariffName", "TEXT"),
                column("tariffPlan", "TEXT"),
                column("defaultTimezone", "TEXT"),
                column("profileJson", "TEXT"),
                column("passHash", "TEXT"),
                column("parentPasswordHash", "TEXT"),
                column("passwordHash", "TEXT"),
                column("settingsPasswordHash", "TEXT"),
                column("accountPagePasswordHash", "TEXT"),
                column("allowedStbTypesJson", "TEXT"),
                column("allowedStbTypesForLocalRecordingJson", "TEXT"),
                column("preferredStbType", "TEXT")
        ));
        columns.put(UiptvTable.BOOKMARK, List.of(
                column("id", "INTEGER PRIMARY KEY"),
                column("accountName", "TEXT"),
                column("categoryTitle", "TEXT"),
                column("channelId", "TEXT"),
                column("channelName", "TEXT"),
                column("cmd", "TEXT"),
                column("serverPortalUrl", "TEXT"),
                column("categoryId", "TEXT"),
                column("accountAction", "TEXT"),
                column("drmType", "TEXT"),
                column("drmLicenseUrl", "TEXT"),
                column("clearKeysJson", "TEXT"),
                column("inputstreamaddon", "TEXT"),
                column("manifestType", "TEXT"),
                column("categoryJson", "TEXT"),
                column("channelJson", "TEXT"),
                column("vodJson", "TEXT"),
                column("seriesJson", "TEXT")
        ));
        columns.put(UiptvTable.BOOKMARK_CATEGORY, List.of(
                column("id", "INTEGER PRIMARY KEY"),
                column("name", "TEXT NOT NULL")
        ));
        columns.put(UiptvTable.BOOKMARK_ORDER, List.of(
                column("id", "INTEGER PRIMARY KEY"),
                column("bookmark_db_id", "TEXT NOT NULL"),
                column("category_id", "TEXT"),
                column("display_order", "INTEGER")
        ));
        return Collections.unmodifiableMap(columns);
    }

    private static DataColumn column(String name, String typeAndDefault) {
        return new DataColumn(name, typeAndDefault);
    }
}
