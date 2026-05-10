package com.uiptv.db

import java.util.Collections
import java.util.EnumSet
import java.util.LinkedHashMap

class DatabaseUtils private constructor() {
    enum class DbTable(val tableName: String) {
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
        SERIES_WATCHING_NOW_SNAPSHOT_TABLE("SeriesWatchingNowSnapshot"),
        PUBLISHED_M3U_SELECTION_TABLE("PublishedM3uSelection"),
        PUBLISHED_M3U_CATEGORY_SELECTION_TABLE("PublishedM3uCategorySelection"),
        PUBLISHED_M3U_CHANNEL_SELECTION_TABLE("PublishedM3uChannelSelection"),
        BOOKMARK_CATEGORY_TABLE("BookmarkCategory"),
        BOOKMARK_ORDER_TABLE("BookmarkOrder")
    }

    companion object {
        @JvmField
        val Cacheable = Collections.unmodifiableSet(
            EnumSet.of(
                DbTable.CATEGORY_TABLE,
                DbTable.CHANNEL_TABLE,
                DbTable.VOD_CATEGORY_TABLE,
                DbTable.VOD_CHANNEL_TABLE,
                DbTable.SERIES_CATEGORY_TABLE,
                DbTable.SERIES_CHANNEL_TABLE,
                DbTable.SERIES_EPISODE_TABLE
            )
        )

        @JvmField
        val Syncable = Collections.unmodifiableSet(
            EnumSet.of(
                DbTable.ACCOUNT_TABLE,
                DbTable.ACCOUNT_INFO_TABLE,
                DbTable.BOOKMARK_TABLE,
                DbTable.BOOKMARK_CATEGORY_TABLE,
                DbTable.BOOKMARK_ORDER_TABLE
            )
        )

        private const val INTEGER_PRIMARY_KEY = "INTEGER PRIMARY KEY"
        private const val INTEGER_TYPE = "INTEGER"
        private const val TEXT_NOT_NULL = "TEXT NOT NULL"
        private const val TEXT_NOT_NULL_UNIQUE = "TEXT NOT NULL UNIQUE"
        private const val WHERE_ID_EQUALS = " where id=?"
        private const val COLUMN_ACCOUNT_ID = "accountId"
        private const val COLUMN_ACCOUNT_TYPE = "accountType"
        private const val COLUMN_ACTIVE_SUB = "activeSub"
        private const val COLUMN_ALIAS = "alias"
        private const val COLUMN_CACHED_AT = "cachedAt"
        private const val COLUMN_CATEGORY_ID = "categoryId"
        private const val COLUMN_CHANNEL_ID = "channelId"
        private const val COLUMN_CLEAR_KEYS_JSON = "clearKeysJson"
        private const val COLUMN_CENSORED = "censored"
        private const val COLUMN_CMD_1 = "cmd_1"
        private const val COLUMN_CMD_2 = "cmd_2"
        private const val COLUMN_CMD_3 = "cmd_3"
        private const val COLUMN_DRM_LICENSE_URL = "drmLicenseUrl"
        private const val COLUMN_DRM_TYPE = "drmType"
        private const val COLUMN_EXTRA_JSON = "extraJson"
        private const val COLUMN_INPUTSTREAM_ADDON = "inputstreamaddon"
        private const val COLUMN_MANIFEST_TYPE = "manifestType"
        private const val COLUMN_NUMBER = "number"
        private const val COLUMN_SERIES_ID = "seriesId"
        private const val COLUMN_STATUS = "status"
        private const val COLUMN_TITLE = "title"
        private const val COLUMN_UPDATED_AT = "updatedAt"

        private val knownTableNames = linkedSetOf<String>()
        private val dbStructure = LinkedHashMap<String, MutableList<DataColumn>>()

        init {
            dbStructure[DbTable.CONFIGURATION_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn("playerPath1", "TEXT"),
                DataColumn("playerPath2", "TEXT"),
                DataColumn("playerPath3", "TEXT"),
                DataColumn("defaultPlayerPath", "TEXT"),
                DataColumn("filterCategoriesList", "TEXT"),
                DataColumn("filterChannelsList", "TEXT"),
                DataColumn("pauseFiltering", "TEXT"),
                DataColumn("darkTheme", "TEXT"),
                DataColumn("serverPort", "TEXT"),
                DataColumn("embeddedPlayer", "TEXT"),
                DataColumn("enableFfmpegTranscoding", "TEXT"),
                DataColumn("cacheExpiryDays", "TEXT"),
                DataColumn("enableThumbnails", "TEXT"),
                DataColumn("wideView", "TEXT"),
                DataColumn("languageLocale", "TEXT"),
                DataColumn("tmdbReadAccessToken", "TEXT"),
                DataColumn("filterLockHash", "TEXT"),
                DataColumn("uiZoomPercent", "TEXT"),
                DataColumn("enableLitePlayerFfmpeg", "TEXT"),
                DataColumn("autoRunServerOnStartup", "TEXT"),
                DataColumn("vlcNetworkCachingMs", "TEXT"),
                DataColumn("vlcLiveCachingMs", "TEXT"),
                DataColumn("publishedM3uCategoryMode", "TEXT"),
                DataColumn("enableVlcHttpUserAgent", "TEXT"),
                DataColumn("enableVlcHttpForwardCookies", "TEXT"),
                DataColumn("resolveChainAndDeepRedirects", "TEXT"),
                DataColumn("filterLockUnlockDurationMinutes", "TEXT")
            )
            dbStructure[DbTable.THEME_CSS_OVERRIDE_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn("lightThemeCssName", "TEXT"),
                DataColumn("lightThemeCssContent", "TEXT"),
                DataColumn("darkThemeCssName", "TEXT"),
                DataColumn("darkThemeCssContent", "TEXT"),
                DataColumn(COLUMN_UPDATED_AT, "TEXT")
            )
            dbStructure[DbTable.ACCOUNT_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn("accountName", TEXT_NOT_NULL_UNIQUE),
                DataColumn("username", "TEXT"),
                DataColumn("password", "TEXT"),
                DataColumn("xtremeCredentialsJson", "TEXT"),
                DataColumn("url", "TEXT"),
                DataColumn("macAddress", "TEXT"),
                DataColumn("macAddressList", "TEXT"),
                DataColumn("serialNumber", "TEXT"),
                DataColumn("deviceId1", "TEXT"),
                DataColumn("deviceId2", "TEXT"),
                DataColumn("signature", "TEXT"),
                DataColumn("epg", "TEXT"),
                DataColumn("m3u8Path", "TEXT"),
                DataColumn("type", "TEXT"),
                DataColumn("serverPortalUrl", "TEXT"),
                DataColumn("pinToTop", "TEXT"),
                DataColumn("resolveChainAndDeepRedirects", "TEXT"),
                DataColumn("httpMethod", "TEXT"),
                DataColumn("timezone", "TEXT")
            )
            dbStructure[DbTable.ACCOUNT_INFO_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, TEXT_NOT_NULL_UNIQUE),
                DataColumn("expireDate", "TEXT"),
                DataColumn("accountStatus", "TEXT"),
                DataColumn("accountBalance", "TEXT"),
                DataColumn("tariffName", "TEXT"),
                DataColumn("tariffPlan", "TEXT"),
                DataColumn("defaultTimezone", "TEXT"),
                DataColumn("profileJson", "TEXT"),
                DataColumn("passHash", "TEXT"),
                DataColumn("parentPasswordHash", "TEXT"),
                DataColumn("passwordHash", "TEXT"),
                DataColumn("settingsPasswordHash", "TEXT"),
                DataColumn("accountPagePasswordHash", "TEXT"),
                DataColumn("allowedStbTypesJson", "TEXT"),
                DataColumn("allowedStbTypesForLocalRecordingJson", "TEXT"),
                DataColumn("preferredStbType", "TEXT")
            )
            dbStructure[DbTable.BOOKMARK_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn("accountName", "TEXT"),
                DataColumn("categoryTitle", "TEXT"),
                DataColumn(COLUMN_CHANNEL_ID, "TEXT"),
                DataColumn("channelName", "TEXT"),
                DataColumn("cmd", "TEXT"),
                DataColumn("serverPortalUrl", "TEXT"),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn("accountAction", "TEXT"),
                DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                DataColumn(COLUMN_MANIFEST_TYPE, "TEXT"),
                DataColumn("categoryJson", "TEXT"),
                DataColumn("channelJson", "TEXT"),
                DataColumn("vodJson", "TEXT"),
                DataColumn("seriesJson", "TEXT")
            )
            dbStructure[DbTable.CATEGORY_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_CATEGORY_ID, TEXT_NOT_NULL),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn(COLUMN_ACCOUNT_TYPE, "TEXT"),
                DataColumn(COLUMN_TITLE, "TEXT"),
                DataColumn(COLUMN_ALIAS, "TEXT"),
                DataColumn("url", "TEXT"),
                DataColumn(COLUMN_ACTIVE_SUB, INTEGER_TYPE),
                DataColumn(COLUMN_CENSORED, INTEGER_TYPE)
            )
            dbStructure[DbTable.CHANNEL_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn("name", "TEXT"),
                DataColumn(COLUMN_NUMBER, "TEXT"),
                DataColumn("cmd", "TEXT"),
                DataColumn(COLUMN_CMD_1, "TEXT"),
                DataColumn(COLUMN_CMD_2, "TEXT"),
                DataColumn(COLUMN_CMD_3, "TEXT"),
                DataColumn("logo", "TEXT"),
                DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                DataColumn(COLUMN_STATUS, INTEGER_TYPE),
                DataColumn("hd", INTEGER_TYPE),
                DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                DataColumn(COLUMN_MANIFEST_TYPE, "TEXT")
            )
            dbStructure[DbTable.VOD_CATEGORY_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_CATEGORY_ID, TEXT_NOT_NULL),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn(COLUMN_ACCOUNT_TYPE, "TEXT"),
                DataColumn(COLUMN_TITLE, "TEXT"),
                DataColumn(COLUMN_ALIAS, "TEXT"),
                DataColumn("url", "TEXT"),
                DataColumn(COLUMN_ACTIVE_SUB, INTEGER_TYPE),
                DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
            )
            dbStructure[DbTable.VOD_CHANNEL_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn("name", "TEXT"),
                DataColumn(COLUMN_NUMBER, "TEXT"),
                DataColumn("cmd", "TEXT"),
                DataColumn(COLUMN_CMD_1, "TEXT"),
                DataColumn(COLUMN_CMD_2, "TEXT"),
                DataColumn(COLUMN_CMD_3, "TEXT"),
                DataColumn("logo", "TEXT"),
                DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                DataColumn(COLUMN_STATUS, INTEGER_TYPE),
                DataColumn("hd", INTEGER_TYPE),
                DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                DataColumn(COLUMN_MANIFEST_TYPE, "TEXT"),
                DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
            )
            dbStructure[DbTable.VOD_WATCH_STATE_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn("vodId", "TEXT"),
                DataColumn("vodName", "TEXT"),
                DataColumn("vodCmd", "TEXT"),
                DataColumn("vodLogo", "TEXT"),
                DataColumn(COLUMN_UPDATED_AT, INTEGER_TYPE)
            )
            dbStructure[DbTable.SERIES_CATEGORY_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_CATEGORY_ID, TEXT_NOT_NULL),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn(COLUMN_ACCOUNT_TYPE, "TEXT"),
                DataColumn(COLUMN_TITLE, "TEXT"),
                DataColumn(COLUMN_ALIAS, "TEXT"),
                DataColumn("url", "TEXT"),
                DataColumn(COLUMN_ACTIVE_SUB, INTEGER_TYPE),
                DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
            )
            dbStructure[DbTable.SERIES_CHANNEL_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn("name", "TEXT"),
                DataColumn(COLUMN_NUMBER, "TEXT"),
                DataColumn("cmd", "TEXT"),
                DataColumn(COLUMN_CMD_1, "TEXT"),
                DataColumn(COLUMN_CMD_2, "TEXT"),
                DataColumn(COLUMN_CMD_3, "TEXT"),
                DataColumn("logo", "TEXT"),
                DataColumn(COLUMN_CENSORED, INTEGER_TYPE),
                DataColumn(COLUMN_STATUS, INTEGER_TYPE),
                DataColumn("hd", INTEGER_TYPE),
                DataColumn(COLUMN_DRM_TYPE, "TEXT"),
                DataColumn(COLUMN_DRM_LICENSE_URL, "TEXT"),
                DataColumn(COLUMN_CLEAR_KEYS_JSON, "TEXT"),
                DataColumn(COLUMN_INPUTSTREAM_ADDON, "TEXT"),
                DataColumn(COLUMN_MANIFEST_TYPE, "TEXT"),
                DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
            )
            dbStructure[DbTable.SERIES_EPISODE_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn(COLUMN_SERIES_ID, "TEXT"),
                DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                DataColumn("name", "TEXT"),
                DataColumn("cmd", "TEXT"),
                DataColumn("logo", "TEXT"),
                DataColumn("season", "TEXT"),
                DataColumn("episodeNum", "TEXT"),
                DataColumn("description", "TEXT"),
                DataColumn("releaseDate", "TEXT"),
                DataColumn("rating", "TEXT"),
                DataColumn("duration", "TEXT"),
                DataColumn(COLUMN_EXTRA_JSON, "TEXT"),
                DataColumn(COLUMN_CACHED_AT, INTEGER_TYPE)
            )
            dbStructure[DbTable.SERIES_WATCH_STATE_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn("mode", "TEXT"),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn(COLUMN_SERIES_ID, "TEXT"),
                DataColumn("episodeId", "TEXT"),
                DataColumn("episodeName", "TEXT"),
                DataColumn("season", "TEXT"),
                DataColumn("episodeNum", INTEGER_TYPE),
                DataColumn(COLUMN_UPDATED_AT, INTEGER_TYPE),
                DataColumn("source", "TEXT"),
                DataColumn("seriesCategorySnapshot", "TEXT"),
                DataColumn("seriesChannelSnapshot", "TEXT"),
                DataColumn("seriesEpisodeSnapshot", "TEXT")
            )
            dbStructure[DbTable.SERIES_WATCHING_NOW_SNAPSHOT_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, "TEXT"),
                DataColumn(COLUMN_CATEGORY_ID, "TEXT"),
                DataColumn(COLUMN_SERIES_ID, "TEXT"),
                DataColumn("categoryDbId", "TEXT"),
                DataColumn("seriesTitle", "TEXT"),
                DataColumn("seriesPoster", "TEXT"),
                DataColumn("episodesJson", "TEXT"),
                DataColumn(COLUMN_UPDATED_AT, INTEGER_TYPE)
            )
            dbStructure[DbTable.PUBLISHED_M3U_SELECTION_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, TEXT_NOT_NULL_UNIQUE)
            )
            dbStructure[DbTable.PUBLISHED_M3U_CATEGORY_SELECTION_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, TEXT_NOT_NULL),
                DataColumn("categoryName", TEXT_NOT_NULL),
                DataColumn("selected", TEXT_NOT_NULL)
            )
            dbStructure[DbTable.PUBLISHED_M3U_CHANNEL_SELECTION_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn(COLUMN_ACCOUNT_ID, TEXT_NOT_NULL),
                DataColumn("categoryName", TEXT_NOT_NULL),
                DataColumn(COLUMN_CHANNEL_ID, TEXT_NOT_NULL),
                DataColumn("selected", TEXT_NOT_NULL)
            )
            dbStructure[DbTable.BOOKMARK_CATEGORY_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn("name", TEXT_NOT_NULL)
            )
            dbStructure[DbTable.BOOKMARK_ORDER_TABLE.tableName] = mutableListOf(
                DataColumn("id", INTEGER_PRIMARY_KEY),
                DataColumn("bookmark_db_id", TEXT_NOT_NULL),
                DataColumn("category_id", "TEXT"),
                DataColumn("display_order", INTEGER_TYPE)
            )
            knownTableNames += dbStructure.keys
        }

        @JvmStatic
        fun dropTableSql(table: DbTable): String = "DROP TABLE ${validatedTableName(table)}"

        @JvmStatic
        fun createTableSql(table: DbTable): String {
            val tableName = validatedTableName(table)
            val columns = dbStructure.getValue(tableName).joinToString(",") { "${it.columnName} ${it.typeAndDefault}" }
            return "CREATE TABLE IF NOT EXISTS $tableName ( $columns)"
        }

        @JvmStatic
        fun insertTableSql(table: DbTable): String {
            val tableName = validatedTableName(table)
            val columns = dbStructure.getValue(tableName).filterNot { it.columnName.equals("id", true) }
            val names = columns.joinToString(",") { it.columnName }
            val placeholders = columns.joinToString(",") { "?" }
            return "INSERT INTO $tableName ( $names) VALUES ($placeholders)"
        }

        @JvmStatic
        fun updateTableSql(table: DbTable): String {
            val tableName = validatedTableName(table)
            val assignments = dbStructure.getValue(tableName)
                .filterNot { it.columnName.equals("id", true) }
                .joinToString(",") { "${it.columnName}=?" }
            return "UPDATE $tableName set $assignments$WHERE_ID_EQUALS"
        }

        @JvmStatic
        fun selectAllSql(table: DbTable): String = "SELECT * FROM ${validatedTableName(table)}"

        @JvmStatic
        fun selectByIdSql(table: DbTable): String = "SELECT * FROM ${validatedTableName(table)}$WHERE_ID_EQUALS"

        @JvmStatic
        fun deleteByIdSql(table: DbTable): String = "DELETE FROM ${validatedTableName(table)}$WHERE_ID_EQUALS"

        @JvmStatic
        fun validatedTableName(table: DbTable?): String {
            requireNotNull(table) { "Table cannot be null" }
            return validatedTableName(table.tableName)
        }

        @JvmStatic
        fun validatedTableName(tableName: String?): String {
            require(!(tableName == null || !knownTableNames.contains(tableName))) { "Unknown table name: $tableName" }
            return tableName
        }
    }
}
