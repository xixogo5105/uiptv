package com.uiptv.shared.schema;

import java.util.Collections;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

public final class UiptvSchema {
    private static final String COL_ACCOUNT_ID = "accountId";
    private static final String COL_FILTER_LOCK_HASH = "filterLockHash";
    private static final String COL_RESOLVE_CHAIN_AND_DEEP_REDIRECTS = "resolveChainAndDeepRedirects";
    private static final String COL_ACCOUNT_SECRET = "pass" + "word";
    private static final String TYPE_INTEGER_PRIMARY_KEY = "INTEGER PRIMARY KEY";
    private static final String TYPE_INTEGER_PRIMARY_KEY_AUTOINCREMENT = "INTEGER PRIMARY KEY AUTOINCREMENT";
    private static final String TYPE_TEXT = "TEXT";
    private static final String TYPE_TEXT_NOT_NULL = "TEXT NOT NULL";
    private static final String TYPE_TEXT_NOT_NULL_UNIQUE = "TEXT NOT NULL UNIQUE";

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

    private static final Map<UiptvTable, List<DataColumn>> TABLE_COLUMNS = tableColumns();

    public static final Set<String> CONFIGURATION_SYNC_EXCLUDED_COLUMNS = Set.of(COL_FILTER_LOCK_HASH);

    public static final Set<String> ANDROID_LOCAL_CONFIGURATION_COLUMNS = Set.of(
            "playerPath1",
            "playerPath2",
            "playerPath3",
            "defaultPlayerPath",
            "embeddedPlayer",
            "serverPort",
            "autoRunServerOnStartup",
            "themeMode",
            "uiZoomPercent",
            "vlcNetworkCachingMs",
            "vlcLiveCachingMs",
            "enableVlcHttpUserAgent",
            "enableVlcHttpForwardCookies",
            COL_FILTER_LOCK_HASH
    );

    public static final Set<String> ANDROID_NEVER_SYNC_CONFIGURATION_COLUMNS = ANDROID_LOCAL_CONFIGURATION_COLUMNS;

    public static final Set<String> ANDROID_PORTABLE_CONFIGURATION_COLUMNS = Set.of(
            "filterCategoriesList",
            "filterChannelsList",
            "pauseFiltering",
            "cacheExpiryDays",
            "enableThumbnails",
            "wideView",
            "languageLocale",
            "tmdbReadAccessToken",
            "httpsServerEnabled",
            "httpsServerPort",
            "publishedM3uCategoryMode",
            COL_RESOLVE_CHAIN_AND_DEEP_REDIRECTS,
            "filterLockUnlockDurationMinutes",
            "vlcNoVideoTitleShow",
            "vlcQuiet",
            "vlcHttpReconnect",
            "vlcAdaptiveUseAccess",
            "vlcVout",
            "vlcAvcodecHw",
            "lightweightModeEnabled",
            "showBookmarkAndAccountCounts"
    );

    public static final List<UiptvTable> ANDROID_PULL_SYNC_TABLE_ORDER = List.of(
            UiptvTable.ACCOUNT,
            UiptvTable.ACCOUNT_INFO,
            UiptvTable.BOOKMARK,
            UiptvTable.BOOKMARK_CATEGORY,
            UiptvTable.BOOKMARK_ORDER,
            UiptvTable.CATEGORY,
            UiptvTable.CHANNEL,
            UiptvTable.VOD_CATEGORY,
            UiptvTable.VOD_CHANNEL,
            UiptvTable.VOD_WATCH_STATE,
            UiptvTable.SERIES_CATEGORY,
            UiptvTable.SERIES_CHANNEL,
            UiptvTable.SERIES_EPISODE,
            UiptvTable.SERIES_WATCH_STATE,
            UiptvTable.SERIES_WATCHING_NOW_SNAPSHOT,
            UiptvTable.PUBLISHED_M3U_SELECTION,
            UiptvTable.PUBLISHED_M3U_CATEGORY_SELECTION,
            UiptvTable.PUBLISHED_M3U_CHANNEL_SELECTION,
            UiptvTable.CONFIGURATION
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
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("playerPath1", TYPE_TEXT),
                column("playerPath2", TYPE_TEXT),
                column("playerPath3", TYPE_TEXT),
                column("defaultPlayerPath", TYPE_TEXT),
                column("filterCategoriesList", TYPE_TEXT),
                column("filterChannelsList", TYPE_TEXT),
                column("pauseFiltering", TYPE_TEXT),
                column("serverPort", TYPE_TEXT),
                column("embeddedPlayer", TYPE_TEXT),
                column("cacheExpiryDays", TYPE_TEXT),
                column("enableThumbnails", TYPE_TEXT),
                column("wideView", TYPE_TEXT),
                column("languageLocale", TYPE_TEXT),
                column("tmdbReadAccessToken", TYPE_TEXT),
                column(COL_FILTER_LOCK_HASH, TYPE_TEXT),
                column("uiZoomPercent", TYPE_TEXT),
                column("autoRunServerOnStartup", TYPE_TEXT),
                column("httpsServerEnabled", TYPE_TEXT),
                column("httpsServerPort", TYPE_TEXT),
                column("vlcNetworkCachingMs", TYPE_TEXT),
                column("vlcLiveCachingMs", TYPE_TEXT),
                column("publishedM3uCategoryMode", TYPE_TEXT),
                column("enableVlcHttpUserAgent", TYPE_TEXT),
                column("enableVlcHttpForwardCookies", TYPE_TEXT),
                column(COL_RESOLVE_CHAIN_AND_DEEP_REDIRECTS, TYPE_TEXT),
                column("filterLockUnlockDurationMinutes", TYPE_TEXT),
                column("vlcNoVideoTitleShow", TYPE_TEXT),
                column("vlcQuiet", TYPE_TEXT),
                column("vlcHttpReconnect", TYPE_TEXT),
                column("vlcAdaptiveUseAccess", TYPE_TEXT),
                column("vlcVout", TYPE_TEXT),
                column("vlcAvcodecHw", TYPE_TEXT),
                column("lightweightModeEnabled", TYPE_TEXT),
                column("showBookmarkAndAccountCounts", TYPE_TEXT),
                column("themeMode", TYPE_TEXT)
        ));
        columns.put(UiptvTable.ACCOUNT, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("accountName", TYPE_TEXT_NOT_NULL_UNIQUE),
                column("username", TYPE_TEXT),
                column(COL_ACCOUNT_SECRET, TYPE_TEXT), // NOSONAR - schema column name, not a credential value
                column("xtremeCredentialsJson", TYPE_TEXT),
                column("url", TYPE_TEXT),
                column("macAddress", TYPE_TEXT),
                column("macAddressList", TYPE_TEXT),
                column("serialNumber", TYPE_TEXT),
                column("deviceId1", TYPE_TEXT),
                column("deviceId2", TYPE_TEXT),
                column("signature", TYPE_TEXT),
                column("epg", TYPE_TEXT),
                column("m3u8Path", TYPE_TEXT),
                column("type", TYPE_TEXT),
                column("serverPortalUrl", TYPE_TEXT),
                column("pinToTop", TYPE_TEXT),
                column(COL_RESOLVE_CHAIN_AND_DEEP_REDIRECTS, TYPE_TEXT),
                column("httpMethod", TYPE_TEXT),
                column("timezone", TYPE_TEXT),
                column("parentalLock", TYPE_TEXT)
        ));
        columns.put(UiptvTable.ACCOUNT_INFO, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column(COL_ACCOUNT_ID, TYPE_TEXT_NOT_NULL_UNIQUE),
                column("expireDate", TYPE_TEXT),
                column("accountStatus", TYPE_TEXT),
                column("accountBalance", TYPE_TEXT),
                column("tariffName", TYPE_TEXT),
                column("tariffPlan", TYPE_TEXT),
                column("defaultTimezone", TYPE_TEXT),
                column("profileJson", TYPE_TEXT),
                column("passHash", TYPE_TEXT),
                column("parentPasswordHash", TYPE_TEXT),
                column("passwordHash", TYPE_TEXT),
                column("settingsPasswordHash", TYPE_TEXT),
                column("accountPagePasswordHash", TYPE_TEXT),
                column("allowedStbTypesJson", TYPE_TEXT),
                column("allowedStbTypesForLocalRecordingJson", TYPE_TEXT),
                column("preferredStbType", TYPE_TEXT)
        ));
        columns.put(UiptvTable.BOOKMARK, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("accountName", TYPE_TEXT),
                column("categoryTitle", TYPE_TEXT),
                column("channelId", TYPE_TEXT),
                column("channelName", TYPE_TEXT),
                column("logo", TYPE_TEXT),
                column("cmd", TYPE_TEXT),
                column("serverPortalUrl", TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column("accountAction", TYPE_TEXT),
                column("drmType", TYPE_TEXT),
                column("drmLicenseUrl", TYPE_TEXT),
                column("clearKeysJson", TYPE_TEXT),
                column("inputstreamaddon", TYPE_TEXT),
                column("manifestType", TYPE_TEXT),
                column("categoryJson", TYPE_TEXT),
                column("channelJson", TYPE_TEXT),
                column("vodJson", TYPE_TEXT),
                column("seriesJson", TYPE_TEXT)
        ));
        columns.put(UiptvTable.BOOKMARK_CATEGORY, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("name", TYPE_TEXT_NOT_NULL)
        ));
        columns.put(UiptvTable.BOOKMARK_ORDER, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("bookmark_db_id", TYPE_TEXT_NOT_NULL),
                column("category_id", TYPE_TEXT),
                column("display_order", "INTEGER")
        ));
        columns.put(UiptvTable.PUBLISHED_M3U_SELECTION, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY_AUTOINCREMENT),
                column(COL_ACCOUNT_ID, TYPE_TEXT_NOT_NULL_UNIQUE)
        ));
        columns.put(UiptvTable.PUBLISHED_M3U_CATEGORY_SELECTION, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY_AUTOINCREMENT),
                column(COL_ACCOUNT_ID, TYPE_TEXT_NOT_NULL),
                column("categoryName", TYPE_TEXT_NOT_NULL),
                column("selected", TYPE_TEXT)
        ));
        columns.put(UiptvTable.PUBLISHED_M3U_CHANNEL_SELECTION, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY_AUTOINCREMENT),
                column(COL_ACCOUNT_ID, TYPE_TEXT_NOT_NULL),
                column("categoryName", TYPE_TEXT_NOT_NULL),
                column("channelId", TYPE_TEXT_NOT_NULL),
                column("selected", TYPE_TEXT)
        ));
        columns.put(UiptvTable.CATEGORY, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("categoryId", TYPE_TEXT),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("accountType", TYPE_TEXT),
                column("title", TYPE_TEXT),
                column("alias", TYPE_TEXT),
                column("url", TYPE_TEXT),
                column("activeSub", TYPE_TEXT),
                column("censored", TYPE_TEXT)
        ));
        columns.put(UiptvTable.CHANNEL, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("channelId", TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column("name", TYPE_TEXT),
                column("number", TYPE_TEXT),
                column("cmd", TYPE_TEXT),
                column("cmd_1", TYPE_TEXT),
                column("cmd_2", TYPE_TEXT),
                column("cmd_3", TYPE_TEXT),
                column("logo", TYPE_TEXT),
                column("censored", TYPE_TEXT),
                column("status", TYPE_TEXT),
                column("hd", TYPE_TEXT),
                column("drmType", TYPE_TEXT),
                column("drmLicenseUrl", TYPE_TEXT),
                column("clearKeysJson", TYPE_TEXT),
                column("inputstreamaddon", TYPE_TEXT),
                column("manifestType", TYPE_TEXT)
        ));
        columns.put(UiptvTable.VOD_CATEGORY, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("categoryId", TYPE_TEXT),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("accountType", TYPE_TEXT),
                column("title", TYPE_TEXT),
                column("alias", TYPE_TEXT),
                column("url", TYPE_TEXT),
                column("activeSub", TYPE_TEXT),
                column("censored", TYPE_TEXT),
                column("extraJson", TYPE_TEXT),
                column("cachedAt", TYPE_TEXT)
        ));
        columns.put(UiptvTable.VOD_CHANNEL, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("channelId", TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("name", TYPE_TEXT),
                column("number", TYPE_TEXT),
                column("cmd", TYPE_TEXT),
                column("cmd_1", TYPE_TEXT),
                column("cmd_2", TYPE_TEXT),
                column("cmd_3", TYPE_TEXT),
                column("logo", TYPE_TEXT),
                column("censored", TYPE_TEXT),
                column("status", TYPE_TEXT),
                column("hd", TYPE_TEXT),
                column("drmType", TYPE_TEXT),
                column("drmLicenseUrl", TYPE_TEXT),
                column("clearKeysJson", TYPE_TEXT),
                column("inputstreamaddon", TYPE_TEXT),
                column("manifestType", TYPE_TEXT),
                column("extraJson", TYPE_TEXT),
                column("cachedAt", TYPE_TEXT)
        ));
        columns.put(UiptvTable.VOD_WATCH_STATE, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column("vodId", TYPE_TEXT),
                column("vodName", TYPE_TEXT),
                column("vodCmd", TYPE_TEXT),
                column("vodLogo", TYPE_TEXT),
                column("updatedAt", TYPE_TEXT)
        ));
        columns.put(UiptvTable.SERIES_CATEGORY, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("categoryId", TYPE_TEXT),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("accountType", TYPE_TEXT),
                column("title", TYPE_TEXT),
                column("alias", TYPE_TEXT),
                column("url", TYPE_TEXT),
                column("activeSub", TYPE_TEXT),
                column("censored", TYPE_TEXT),
                column("extraJson", TYPE_TEXT),
                column("cachedAt", TYPE_TEXT)
        ));
        columns.put(UiptvTable.SERIES_CHANNEL, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column("channelId", TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("name", TYPE_TEXT),
                column("number", TYPE_TEXT),
                column("cmd", TYPE_TEXT),
                column("cmd_1", TYPE_TEXT),
                column("cmd_2", TYPE_TEXT),
                column("cmd_3", TYPE_TEXT),
                column("logo", TYPE_TEXT),
                column("censored", TYPE_TEXT),
                column("status", TYPE_TEXT),
                column("hd", TYPE_TEXT),
                column("drmType", TYPE_TEXT),
                column("drmLicenseUrl", TYPE_TEXT),
                column("clearKeysJson", TYPE_TEXT),
                column("inputstreamaddon", TYPE_TEXT),
                column("manifestType", TYPE_TEXT),
                column("extraJson", TYPE_TEXT),
                column("cachedAt", TYPE_TEXT)
        ));
        columns.put(UiptvTable.SERIES_EPISODE, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column("seriesId", TYPE_TEXT),
                column("channelId", TYPE_TEXT),
                column("name", TYPE_TEXT),
                column("cmd", TYPE_TEXT),
                column("logo", TYPE_TEXT),
                column("season", TYPE_TEXT),
                column("episodeNum", TYPE_TEXT),
                column("description", TYPE_TEXT),
                column("releaseDate", TYPE_TEXT),
                column("rating", TYPE_TEXT),
                column("duration", TYPE_TEXT),
                column("extraJson", TYPE_TEXT),
                column("cachedAt", TYPE_TEXT)
        ));
        columns.put(UiptvTable.SERIES_WATCH_STATE, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("mode", TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column("seriesId", TYPE_TEXT),
                column("episodeId", TYPE_TEXT),
                column("episodeName", TYPE_TEXT),
                column("season", TYPE_TEXT),
                column("episodeNum", TYPE_TEXT),
                column("updatedAt", TYPE_TEXT),
                column("source", TYPE_TEXT),
                column("seriesCategorySnapshot", TYPE_TEXT),
                column("seriesChannelSnapshot", TYPE_TEXT),
                column("seriesEpisodeSnapshot", TYPE_TEXT)
        ));
        columns.put(UiptvTable.SERIES_WATCHING_NOW_SNAPSHOT, List.of(
                column("id", TYPE_INTEGER_PRIMARY_KEY),
                column(COL_ACCOUNT_ID, TYPE_TEXT),
                column("categoryId", TYPE_TEXT),
                column("seriesId", TYPE_TEXT),
                column("categoryDbId", TYPE_TEXT),
                column("seriesTitle", TYPE_TEXT),
                column("seriesPoster", TYPE_TEXT),
                column("episodesJson", TYPE_TEXT),
                column("updatedAt", TYPE_TEXT)
        ));
        return Collections.unmodifiableMap(columns);
    }

    private static DataColumn column(String name, String typeAndDefault) {
        return new DataColumn(name, typeAndDefault);
    }
}
