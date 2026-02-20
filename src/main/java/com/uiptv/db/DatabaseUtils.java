package com.uiptv.db;

import java.util.*;

public class DatabaseUtils {
    private static Map<String, List<DataColumn>> dbStructure = new LinkedHashMap<>();

    public enum DbTable {
        CONFIGURATION_TABLE("Configuration"),
        ACCOUNT_TABLE("Account"),
        BOOKMARK_TABLE("Bookmark"),
        CATEGORY_TABLE("Category"),
        CHANNEL_TABLE("Channel"),
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

    public static final EnumSet<DbTable> Cacheable = EnumSet.of(DbTable.CATEGORY_TABLE, DbTable.CHANNEL_TABLE);
    public static final EnumSet<DbTable> Syncable = EnumSet.of(DbTable.ACCOUNT_TABLE, DbTable.BOOKMARK_TABLE, DbTable.BOOKMARK_CATEGORY_TABLE, DbTable.BOOKMARK_ORDER_TABLE);

    static {
        dbStructure.put(DbTable.CONFIGURATION_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", "INTEGER PRIMARY KEY"),
                new DataColumn("playerPath1", "TEXT"),
                new DataColumn("playerPath2", "TEXT"),
                new DataColumn("playerPath3", "TEXT"),
                new DataColumn("defaultPlayerPath", "TEXT"),
                new DataColumn("filterCategoriesList", "TEXT"),
                new DataColumn("filterChannelsList", "TEXT"),
                new DataColumn("pauseFiltering", "TEXT"),
                new DataColumn("fontFamily", "TEXT"),
                new DataColumn("fontSize", "TEXT"),
                new DataColumn("fontWeight", "TEXT"),
                new DataColumn("darkTheme", "TEXT"),
                new DataColumn("serverPort", "TEXT"),
                new DataColumn("embeddedPlayer", "TEXT"),
                new DataColumn("enableFfmpegTranscoding", "TEXT")
        )));
        dbStructure.put(DbTable.ACCOUNT_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", "INTEGER PRIMARY KEY"),
                new DataColumn("accountName", "TEXT NOT NULL UNIQUE"),
                new DataColumn("username", "TEXT"),
                new DataColumn("password", "TEXT"),
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
        dbStructure.put(DbTable.BOOKMARK_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", "INTEGER PRIMARY KEY"),
                new DataColumn("accountName", "TEXT"),
                new DataColumn("categoryTitle", "TEXT"),
                new DataColumn("channelId", "TEXT"),
                new DataColumn("channelName", "TEXT"),
                new DataColumn("cmd", "TEXT"),
                new DataColumn("serverPortalUrl", "TEXT"),
                new DataColumn("categoryId", "TEXT"),
                new DataColumn("accountAction", "TEXT"), // Added column
                new DataColumn("drmType", "TEXT"),
                new DataColumn("drmLicenseUrl", "TEXT"),
                new DataColumn("clearKeysJson", "TEXT"),
                new DataColumn("inputstreamaddon", "TEXT"),
                new DataColumn("manifestType", "TEXT"),
                new DataColumn("categoryJson", "TEXT"),
                new DataColumn("channelJson", "TEXT"),
                new DataColumn("vodJson", "TEXT"),
                new DataColumn("seriesJson", "TEXT")
        )));
        dbStructure.put(DbTable.CATEGORY_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", "INTEGER PRIMARY KEY"),
                new DataColumn("categoryId", "TEXT NOT NULL"),
                new DataColumn("accountId", "TEXT"),
                new DataColumn("accountType", "TEXT"),
                new DataColumn("title", "TEXT"),
                new DataColumn("alias", "TEXT"),
                new DataColumn("url", "TEXT"),
                new DataColumn("activeSub", "INTEGER"),
                new DataColumn("censored", "INTEGER")
        )));
        dbStructure.put(DbTable.CHANNEL_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", "INTEGER PRIMARY KEY"),
                new DataColumn("channelId", "TEXT NOT NULL"),
                new DataColumn("categoryId", "TEXT"),
                new DataColumn("name", "TEXT"),
                new DataColumn("number", "TEXT"),
                new DataColumn("cmd", "TEXT"),
                new DataColumn("cmd_1", "TEXT"),
                new DataColumn("cmd_2", "TEXT"),
                new DataColumn("cmd_3", "TEXT"),
                new DataColumn("logo", "TEXT"),
                new DataColumn("censored", "INTEGER"),
                new DataColumn("status", "INTEGER"),
                new DataColumn("hd", "INTEGER"),
                new DataColumn("drmType", "TEXT"),
                new DataColumn("drmLicenseUrl", "TEXT"),
                new DataColumn("clearKeysJson", "TEXT"),
                new DataColumn("inputstreamaddon", "TEXT"),
                new DataColumn("manifestType", "TEXT")
        )));
        dbStructure.put(DbTable.BOOKMARK_CATEGORY_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", "INTEGER PRIMARY KEY"),
                new DataColumn("name", "TEXT NOT NULL")
        )));
        dbStructure.put(DbTable.BOOKMARK_ORDER_TABLE.getTableName(), new ArrayList<>(Arrays.asList(
                new DataColumn("id", "INTEGER PRIMARY KEY"),
                new DataColumn("bookmark_db_id", "TEXT NOT NULL"),
                new DataColumn("category_id", "TEXT"), // Can be null for "All"
                new DataColumn("display_order", "INTEGER")
        )));
    }


    public static String dropTableSql(DbTable table) {
        return "DROP TABLE " + table.getTableName();
    }

    public static String createTableSql(DbTable table) {
        StringBuilder sql = new StringBuilder("CREATE TABLE IF NOT EXISTS ").append(table.getTableName()).append(" ( ");
        dbStructure.get(table.getTableName()).forEach(c -> {
            sql.append(c.getColumnName()).append(" ").append(c.getTypeAndDefault()).append(",");
        });
        return removeLastChar(sql) + ")";
    }

    public static String insertTableSql(DbTable table) {
        StringBuilder sql = new StringBuilder("INSERT INTO ").append(table.getTableName()).append(" ( ");
        dbStructure.get(table.getTableName()).forEach(c -> {
            if (!"id".equalsIgnoreCase(c.getColumnName())) {
                sql.append(c.getColumnName()).append(",");
            }
        });
        StringBuilder sql2 = new StringBuilder(removeLastChar(sql) + ") VALUES (");
        dbStructure.get(table.getTableName()).forEach(c -> {
            if (!"id".equalsIgnoreCase(c.getColumnName())) {
                sql2.append("?,");
            }
        });
        return removeLastChar(sql2) + ")";
    }

    public static String updateTableSql(DbTable table) {
        StringBuilder sql = new StringBuilder("UPDATE ").append(table.getTableName()).append(" set ");
        dbStructure.get(table.getTableName()).forEach(c -> {
            if (!"id".equalsIgnoreCase(c.getColumnName())) {
                sql.append(c.getColumnName()).append("=?,");
            }
        });
        return removeLastChar(sql) + " where id=?";
    }

    public static String selectAllSql(DbTable table) {
        return "SELECT * FROM " + table.getTableName();
    }

    public static String selectByIdSql(DbTable table, String id) {
        return "SELECT * FROM " + table.getTableName() + " where id='" + id + "'";
    }

    public static String deleteByIdSql(DbTable table, String id) {
        return "DELETE FROM " + table.getTableName() + " where id='" + id + "'";
    }

    private static String removeLastChar(StringBuilder sql) {
        return sql.substring(0, sql.length() - 1);
    }
}
