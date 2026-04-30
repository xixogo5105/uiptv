package com.uiptv.db;

import java.util.*;

public class DatabaseUtils {
    public static final Set<DbTable> Cacheable = Collections.unmodifiableSet(EnumSet.of(
            DbTable.CATEGORY_TABLE,
            DbTable.CHANNEL_TABLE,
            DbTable.VOD_CATEGORY_TABLE,
            DbTable.VOD_CHANNEL_TABLE,
            DbTable.SERIES_CATEGORY_TABLE,
            DbTable.SERIES_CHANNEL_TABLE,
            DbTable.SERIES_EPISODE_TABLE
    ));
    public static final Set<DbTable> Syncable = Collections.unmodifiableSet(EnumSet.of(
            DbTable.ACCOUNT_TABLE,
            DbTable.ACCOUNT_INFO_TABLE,
            DbTable.BOOKMARK_TABLE,
            DbTable.BOOKMARK_CATEGORY_TABLE,
            DbTable.BOOKMARK_ORDER_TABLE
    ));
    private static final String INTEGER_PRIMARY_KEY = "INTEGER PRIMARY KEY";
    private static final String INTEGER_TYPE = "INTEGER";
    private static final String TEXT_NOT_NULL = "TEXT NOT NULL";
    private static final String TEXT_NOT_NULL_UNIQUE = "TEXT NOT NULL UNIQUE";
    private static final String WHERE_ID_EQUALS = " where id=?";
    private static final String COLUMN_ACCOUNT_ID = "accountId";
    private static final String COLUMN_ACCOUNT_TYPE = "accountType";
    private static final String COLUMN_ACTIVE_SUB = "activeSub";
    private static final String COLUMN_ALIAS = "alias";
    private static final String COLUMN_CACHED_AT = "cachedAt";
    private static final String COLUMN_CATEGORY_ID = "categoryId";
    private static final String COLUMN_CHANNEL_ID = "channelId";
    private static final String COLUMN_CLEAR_KEYS_JSON = "clearKeysJson";
    private static final String COLUMN_CENSORED = "censored";
    private static final String COLUMN_CMD_1 = "cmd_1";
    private static final String COLUMN_CMD_2 = "cmd_2";
    private static final String COLUMN_CMD_3 = "cmd_3";
    private static final String COLUMN_DRM_LICENSE_URL = "drmLicenseUrl";
    private static final String COLUMN_DRM_TYPE = "drmType";
    private static final String COLUMN_EXTRA_JSON = "extraJson";
    private static final String COLUMN_INPUTSTREAM_ADDON = "inputstreamaddon";
    private static final String COLUMN_MANIFEST_TYPE = "manifestType";
    private static final String COLUMN_NUMBER = "number";
    private static final String COLUMN_STATUS = "status";
    private static final String COLUMN_TITLE = "title";
    private static final String COLUMN_UPDATED_AT = "updatedAt";
    private static final Set<String> KNOWN_TABLE_NAMES = new HashSet<>();
    private static final Map<String, List<DataColumn>> dbStructure = new LinkedHashMap<>();

    static {
        dbStructure.put(DbTable.CONFIGURATION_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn("playerPath1", "TEXT"),
                new DataColumn("playerPath2", "TEXT"),
                new DataColumn("playerPath3", "TEXT"),
                new DataColumn("defaultPlayerPath", "TEXT"),
                new DataColumn("filterCategoriesList", "TEXT"),
                new DataColumn("filterChannelsList", "TEXT"),
                new DataColumn("pauseFiltering", "TEXT"),
                new DataColumn("darkTheme", "TEXT"),
                new DataColumn("serverPort", "TEXT"),
                new DataColumn("embeddedPlayer", "TEXT"),
                new DataColumn("enableFfmpegTranscoding", "TEXT"),
                new DataColumn("cacheExpiryDays", "TEXT"),
                new DataColumn("enableThumbnails", "TEXT"),
                new DataColumn("wideView", "TEXT"),
                new DataColumn("languageLocale", "TEXT"),
                new DataColumn("tmdbReadAccessToken", "TEXT"),
                new DataColumn("uiZoomPercent", "TEXT"),
                new DataColumn("enableLitePlayerFfmpeg", "TEXT"),
                new DataColumn("autoRunServerOnStartup", "TEXT"),
                new DataColumn("vlcNetworkCachingMs", "TEXT"),
                new DataColumn("vlcLiveCachingMs", "TEXT"),
                new DataColumn("enableVlcHttpUserAgent", "TEXT"),
                new DataColumn("enableVlcHttpForwardCookies", "TEXT"),
                new DataColumn("resolveChainAndDeepRedirects", "TEXT")
        )));
        dbStructure.put(DbTable.THEME_CSS_OVERRIDE_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn("lightThemeCssName", "TEXT"),
                new DataColumn("lightThemeCssContent", "TEXT"),
                new DataColumn("darkThemeCssName", "TEXT"),
                new DataColumn("darkThemeCssContent", "TEXT"),
                new DataColumn(COLUMN_UPDATED_AT, "TEXT")
        )));
        dbStructure.put(DbTable.ACCOUNT_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn("accountName", TEXT_NOT_NULL_UNIQUE),
                new DataColumn("username", "TEXT"),
                new DataColumn("password", "TEXT"),
                new DataColumn("xtremeCredentialsJson", "TEXT"),
                new DataColumn("url", "TEXT"),
                new DataColumn("macAddress", "TEXT"),
                new DataColumn("macAddressList", "TEXT"),
                new DataColumn("serialNumber", "TEXT"),
                new DataColumn("deviceId1", "TEXT"),
                new DataColumn("deviceId2", "TEXT"),
                new DataColumn("signature", "TEXT"),
                new DataColumn("epg", "TEXT"),
                new DataColumn("m3u8Path", "TEXT"),
                new DataColumn("type", "TEXT"),
                new DataColumn("serverPortalUrl", "TEXT"),
                new DataColumn("pinToTop", "TEXT"),
                new DataColumn("httpMethod", "TEXT"),
                new DataColumn("timezone", "TEXT")
        )));
        dbStructure.put(DbTable.ACCOUNT_INFO_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_ACCOUNT_ID, TEXT_NOT_NULL_UNIQUE),
                new DataColumn("expireDate", "TEXT"),
                new DataColumn("accountStatus", "TEXT"),
                new DataColumn("accountBalance", "TEXT"),
                new DataColumn("tariffName", "TEXT"),
                new DataColumn("tariffPlan", "TEXT"),
                new DataColumn("defaultTimezone", "TEXT"),
                new DataColumn("profileJson", "TEXT"),
                new DataColumn("passHash", "TEXT"),
                new DataColumn("parentPasswordHash", "TEXT"),
                new DataColumn("passwordHash", "TEXT"),
                new DataColumn("settingsPasswordHash", "TEXT"),
                new DataColumn("accountPagePasswordHash", "TEXT"),
                new DataColumn("allowedStbTypesJson", "TEXT"),
                new DataColumn("allowedStbTypesForLocalRecordingJson", "TEXT"),
                new DataColumn("preferredStbType", "TEXT")
        )));
        dbStructure.put(DbTable.BOOKMARK_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn("accountName", "TEXT"),
                new DataColumn("categoryTitle", "TEXT"),
                new DataColumn(COLUMN_CHANNEL_ID, "TEXT"),
                new DataColumn("channelName", "TEXT"),
                new DataColumn("cmd", "TEXT"),
                new DataColumn("serverPortalUrl", "TEXT"),
                new DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                new DataColumn("accountAction", "TEXT"), // Added column
                new DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                new DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                new DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                new DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                new DataColumn(COLUMN_MANIFEST_TYPE, "TEXT"),
                new DataColumn("categoryJson", "TEXT"),
                new DataColumn("channelJson", "TEXT"),
                new DataColumn("vodJson", "TEXT"),
                new DataColumn("seriesJson", "TEXT")
        )));
        dbStructure.put(DbTable.CATEGORY_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_CATEGORY_ID, TEXT_NOT_NULL),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn(COLUMN_ACCOUNT_TYPE, "TEXT"),
                new DataColumn(COLUMN_TITLE, "TEXT"),
                new DataColumn(COLUMN_ALIAS, "TEXT"),
                new DataColumn("url", "TEXT"),
                new DataColumn(COLUMN_ACTIVE_SUB, INTEGER_TYPE),
                new DataColumn(COLUMN_CENSORED, INTEGER_TYPE)
        )));
        dbStructure.put(DbTable.CHANNEL_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                new DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                new DataColumn("name", "TEXT"),
                new DataColumn(COLUMN_NUMBER, "TEXT"),
                new DataColumn("cmd", "TEXT"),
                new DataColumn(COLUMN_CMD_1, "TEXT"),
                new DataColumn(COLUMN_CMD_2, "TEXT"),
                new DataColumn(COLUMN_CMD_3, "TEXT"),
                new DataColumn("logo", "TEXT"),
                new DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                new DataColumn(COLUMN_STATUS, INTEGER_TYPE),
                new DataColumn("hd", INTEGER_TYPE),
                new DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                new DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                new DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                new DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                new DataColumn(COLUMN_MANIFEST_TYPE, "TEXT")
        )));
        dbStructure.put(DbTable.VOD_CATEGORY_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_CATEGORY_ID, TEXT_NOT_NULL),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn(COLUMN_ACCOUNT_TYPE, "TEXT"),
                new DataColumn(COLUMN_TITLE, "TEXT"),
                new DataColumn(COLUMN_ALIAS, "TEXT"),
                new DataColumn("url", "TEXT"),
                new DataColumn(COLUMN_ACTIVE_SUB, INTEGER_TYPE),
                new DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                new DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                new DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
        )));
        dbStructure.put(DbTable.VOD_CHANNEL_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                new DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn("name", "TEXT"),
                new DataColumn(COLUMN_NUMBER, "TEXT"),
                new DataColumn("cmd", "TEXT"),
                new DataColumn(COLUMN_CMD_1, "TEXT"),
                new DataColumn(COLUMN_CMD_2, "TEXT"),
                new DataColumn(COLUMN_CMD_3, "TEXT"),
                new DataColumn("logo", "TEXT"),
                new DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                new DataColumn(COLUMN_STATUS, INTEGER_TYPE),
                new DataColumn("hd", INTEGER_TYPE),
                new DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                new DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                new DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                new DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                new DataColumn(COLUMN_MANIFEST_TYPE, "TEXT"),
                new DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                new DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
        )));
        dbStructure.put(DbTable.VOD_WATCH_STATE_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                new DataColumn("vodId", "TEXT"),
                new DataColumn("vodName", "TEXT"),
                new DataColumn("vodCmd", "TEXT"),
                new DataColumn("vodLogo", "TEXT"),
                new DataColumn(COLUMN_UPDATED_AT, INTEGER_TYPE)
        )));
        dbStructure.put(DbTable.SERIES_CATEGORY_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_CATEGORY_ID, TEXT_NOT_NULL),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn(COLUMN_ACCOUNT_TYPE, "TEXT"),
                new DataColumn(COLUMN_TITLE, "TEXT"),
                new DataColumn(COLUMN_ALIAS, "TEXT"),
                new DataColumn("url", "TEXT"),
                new DataColumn(COLUMN_ACTIVE_SUB, INTEGER_TYPE),
                new DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                new DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                new DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
        )));
        dbStructure.put(DbTable.SERIES_CHANNEL_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                new DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn("name", "TEXT"),
                new DataColumn(COLUMN_NUMBER, "TEXT"),
                new DataColumn("cmd", "TEXT"),
                new DataColumn(COLUMN_CMD_1, "TEXT"),
                new DataColumn(COLUMN_CMD_2, "TEXT"),
                new DataColumn(COLUMN_CMD_3, "TEXT"),
                new DataColumn("logo", "TEXT"),
                new DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                new DataColumn(COLUMN_STATUS, INTEGER_TYPE),
                new DataColumn("hd", INTEGER_TYPE),
                new DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                new DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                new DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                new DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                new DataColumn(COLUMN_MANIFEST_TYPE, "TEXT"),
                new DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                new DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
        )));
        dbStructure.put(DbTable.SERIES_EPISODE_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                new DataColumn("seriesId", "TEXT"),
                new DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                new DataColumn("name", "TEXT"),
                new DataColumn("cmd", "TEXT"),
                new DataColumn("logo", "TEXT"),
                new DataColumn("season", "TEXT"),
                new DataColumn("episodeNum", "TEXT"),
                new DataColumn("description", "TEXT"),
                new DataColumn("releaseDate", "TEXT"),
                new DataColumn("rating", "TEXT"),
                new DataColumn("duration", "TEXT"),
                new DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                new DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
        )));
        dbStructure.put(DbTable.SERIES_WATCH_STATE_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                new DataColumn("mode", "TEXT"),
                new DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                new DataColumn("seriesId", "TEXT"),
                new DataColumn("episodeId", "TEXT"),
                new DataColumn("episodeName", "TEXT"),
                new DataColumn("season", "TEXT"),
                new DataColumn("episodeNum", INTEGER_TYPE),
                new DataColumn(COLUMN_UPDATED_AT, INTEGER_TYPE),
                new DataColumn("source", "TEXT"),
                new DataColumn("seriesCategorySnapshot", "TEXT"),
                new DataColumn("seriesChannelSnapshot", "TEXT"),
                new DataColumn("seriesEpisodeSnapshot", "TEXT")
        )));
        dbStructure.put(DbTable.PUBLISHED_M3U_SELECTION_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn(COLUMN_ACCOUNT_ID, TEXT_NOT_NULL_UNIQUE)
        )));
        dbStructure.put(DbTable.BOOKMARK_CATEGORY_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn("name", TEXT_NOT_NULL)
        )));
        dbStructure.put(DbTable.BOOKMARK_ORDER_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", INTEGER_PRIMARY_KEY),
                new DataColumn("bookmark_db_id", TEXT_NOT_NULL),
                new DataColumn("category_id", "TEXT"), // Can be null for "All"
                new DataColumn("display_order", INTEGER_TYPE)
        )));
        KNOWN_TABLE_NAMES.addAll(dbStructure.keySet());
    }

    public static String dropTableSql(DbTable table) {
        return "DROP TABLE " + validatedTableName(table);
    }

    public static String createTableSql(DbTable table) {
        String tableName = validatedTableName(table);
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(tableName).append(" ( ");
        dbStructure.get(tableName).forEach(c -> sql.append(c.getColumnName()).append(" ").append(c.getTypeAndDefault()).append(","));
        return removeLastChar(sql) + ")";
    }

    public static String insertTableSql(DbTable table) {
        String tableName = validatedTableName(table);
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(tableName).append(" ( ");
        dbStructure.get(tableName).forEach(c -> {
            if (!"id".equalsIgnoreCase(c.getColumnName())) {
                sql.append(c.getColumnName()).append(",");
            }
        });
        StringBuilder sql2 = new StringBuilder(removeLastChar(sql) + ") VALUES (");
        dbStructure.get(tableName).forEach(c -> {
            if (!"id".equalsIgnoreCase(c.getColumnName())) {
                sql2.append("?,");
            }
        });
        return removeLastChar(sql2) + ")";
    }

    public static String updateTableSql(DbTable table) {
        String tableName = validatedTableName(table);
        StringBuilder sql = new StringBuilder("UPDATE ").append(tableName).append(" set ");
        dbStructure.get(tableName).forEach(c -> {
            if (!"id".equalsIgnoreCase(c.getColumnName())) {
                sql.append(c.getColumnName()).append("=?,");
            }
        });
        return removeLastChar(sql) + WHERE_ID_EQUALS;
    }

    public static String selectAllSql(DbTable table) {
        return "SELECT * FROM " + validatedTableName(table);
    }

    public static String selectByIdSql(DbTable table) {
        return "SELECT * FROM " + validatedTableName(table) + WHERE_ID_EQUALS;
    }

    public static String deleteByIdSql(DbTable table) {
        return "DELETE FROM " + validatedTableName(table) + WHERE_ID_EQUALS;
    }

    public static String validatedTableName(DbTable table) {
        if (table == null) {
            throw new IllegalArgumentException("Table cannot be null");
        }
        return validatedTableName(table.getTableName());
    }

    public static String validatedTableName(String tableName) {
        if (tableName == null || !KNOWN_TABLE_NAMES.contains(tableName)) {
            throw new IllegalArgumentException("Unknown table name: " + tableName);
        }
        return tableName;
    }

    private static String removeLastChar(StringBuilder sql) {
        return sql.substring(0, sql.length() - 1);
    }

    public enum DbTable {
        CONFIGURATION_TABLE("Configuration"),
        THEME_CSS_OVERRIDE_TABLE("ThemeCssOverride"),
        ACCOUNT_TABLE("Account"),
        ACCOUNT_INFO_TABLE("AccountInfo"),
        BOOKMARK_TABLE("Bookmark"),
        CATEGORY_TABLE("Category"),
        CHANNEL_TABLE("Channel"),
        VOD_CATEGORY_TABLE("VodCategory"),
        VOD_CHANNEL_TABLE("VodChannel"),
        VOD_WATCH_STATE_TABLE("VodWatchState"),
        SERIES_CATEGORY_TABLE("SeriesCategory"),
        SERIES_CHANNEL_TABLE("SeriesChannel"),
        SERIES_EPISODE_TABLE("SeriesEpisode"),
        SERIES_WATCH_STATE_TABLE("SeriesWatchState"),
        PUBLISHED_M3U_SELECTION_TABLE("PublishedM3uSelection"),
        BOOKMARK_CATEGORY_TABLE("BookmarkCategory"),
        BOOKMARK_ORDER_TABLE("BookmarkOrder"); // Added new table

        private final String tableName;

        DbTable(String tableName) {
            this.tableName = tableName;
        }

        public String getTableName() {
            return tableName;
        }

    }
}
