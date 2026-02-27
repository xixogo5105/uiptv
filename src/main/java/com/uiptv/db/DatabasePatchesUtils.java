package com.uiptv.db;

import java.sql.*;
import java.util.HashMap;
import java.util.Map;

import static com.uiptv.db.DatabaseUtils.DbTable.*;

public class DatabasePatchesUtils {
    private static Map<String, String> dbPatches = new HashMap<>();

    static {
        dbPatches.put("111", "CREATE TABLE IF NOT EXISTS " + BOOKMARK_CATEGORY_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        dbPatches.put("112", "ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " ADD COLUMN macAddressList TEXT");
        dbPatches.put("113", "ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " ADD COLUMN pinToTop TEXT default '0'");
        dbPatches.put("114", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN categoryId TEXT");
        dbPatches.put("115", "ALTER TABLE " + CONFIGURATION_TABLE.getTableName() + " ADD COLUMN embeddedPlayer TEXT");
        dbPatches.put("116", "ALTER TABLE " + CHANNEL_TABLE.getTableName() + " ADD COLUMN drmType TEXT");
        dbPatches.put("117", "ALTER TABLE " + CHANNEL_TABLE.getTableName() + " ADD COLUMN drmLicenseUrl TEXT");
        dbPatches.put("118", "ALTER TABLE " + CHANNEL_TABLE.getTableName() + " ADD COLUMN clearKeysJson TEXT");
        dbPatches.put("119", "ALTER TABLE " + CHANNEL_TABLE.getTableName() + " ADD COLUMN inputstreamaddon TEXT");
        dbPatches.put("120", "ALTER TABLE " + CHANNEL_TABLE.getTableName() + " ADD COLUMN manifestType TEXT");
        dbPatches.put("121", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN drmType TEXT");
        dbPatches.put("122", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN drmLicenseUrl TEXT");
        dbPatches.put("123", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN clearKeysJson TEXT");
        dbPatches.put("124", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN inputstreamaddon TEXT");
        dbPatches.put("125", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN manifestType TEXT");
        dbPatches.put("127", "ALTER TABLE " + CONFIGURATION_TABLE.getTableName() + " ADD COLUMN enableFfmpegTranscoding TEXT");
        dbPatches.put("138", "ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " DROP COLUMN pauseCaching");
        dbPatches.put("139", "ALTER TABLE " + CONFIGURATION_TABLE.getTableName() + " DROP COLUMN pauseCaching");
        dbPatches.put("140", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN accountAction TEXT");
        dbPatches.put("141", "CREATE TABLE IF NOT EXISTS " + BOOKMARK_ORDER_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, bookmark_db_id TEXT NOT NULL, category_id TEXT, display_order INTEGER)");
        dbPatches.put("142", "INSERT INTO " + BOOKMARK_ORDER_TABLE.getTableName() + " (bookmark_db_id, category_id, display_order) SELECT id, categoryId, 0 FROM " + BOOKMARK_TABLE.getTableName() + " WHERE id NOT IN (SELECT bookmark_db_id FROM " + BOOKMARK_ORDER_TABLE.getTableName() + ")");
        dbPatches.put("143", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN categoryJson TEXT");
        dbPatches.put("144", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN channelJson TEXT");
        dbPatches.put("145", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN vodJson TEXT");
        dbPatches.put("146", "ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN seriesJson TEXT");
        dbPatches.put("147", "ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " ADD COLUMN httpMethod TEXT default 'GET'");
        dbPatches.put("148", "ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " ADD COLUMN timezone TEXT default 'Europe/London'");
        dbPatches.put("149", "CREATE TABLE IF NOT EXISTS " + VOD_CATEGORY_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, categoryId TEXT NOT NULL, accountId TEXT, accountType TEXT, title TEXT, alias TEXT, url TEXT, activeSub INTEGER, censored INTEGER, extraJson TEXT, cachedAt INTEGER)");
        dbPatches.put("150", "CREATE TABLE IF NOT EXISTS " + VOD_CHANNEL_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, channelId TEXT NOT NULL, categoryId TEXT, accountId TEXT, name TEXT, number TEXT, cmd TEXT, cmd_1 TEXT, cmd_2 TEXT, cmd_3 TEXT, logo TEXT, censored INTEGER, status INTEGER, hd INTEGER, drmType TEXT, drmLicenseUrl TEXT, clearKeysJson TEXT, inputstreamaddon TEXT, manifestType TEXT, extraJson TEXT, cachedAt INTEGER)");
        dbPatches.put("151", "CREATE TABLE IF NOT EXISTS " + SERIES_CATEGORY_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, categoryId TEXT NOT NULL, accountId TEXT, accountType TEXT, title TEXT, alias TEXT, url TEXT, activeSub INTEGER, censored INTEGER, extraJson TEXT, cachedAt INTEGER)");
        dbPatches.put("152", "CREATE TABLE IF NOT EXISTS " + SERIES_CHANNEL_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, channelId TEXT NOT NULL, categoryId TEXT, accountId TEXT, name TEXT, number TEXT, cmd TEXT, cmd_1 TEXT, cmd_2 TEXT, cmd_3 TEXT, logo TEXT, censored INTEGER, status INTEGER, hd INTEGER, drmType TEXT, drmLicenseUrl TEXT, clearKeysJson TEXT, inputstreamaddon TEXT, manifestType TEXT, extraJson TEXT, cachedAt INTEGER)");
        dbPatches.put("153", "CREATE TABLE IF NOT EXISTS " + SERIES_EPISODE_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, accountId TEXT, seriesId TEXT, channelId TEXT NOT NULL, name TEXT, cmd TEXT, logo TEXT, season TEXT, episodeNum TEXT, description TEXT, releaseDate TEXT, rating TEXT, duration TEXT, extraJson TEXT, cachedAt INTEGER)");
        dbPatches.put("154", "CREATE TABLE IF NOT EXISTS " + SERIES_WATCH_STATE_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, accountId TEXT, mode TEXT, categoryId TEXT, seriesId TEXT, episodeId TEXT, episodeName TEXT, season TEXT, episodeNum INTEGER, updatedAt INTEGER, source TEXT)");
        dbPatches.put("155", "CREATE UNIQUE INDEX IF NOT EXISTS idx_series_watch_unique ON " + SERIES_WATCH_STATE_TABLE.getTableName() + " (accountId, mode, categoryId, seriesId)");
        dbPatches.put("156", "ALTER TABLE " + SERIES_WATCH_STATE_TABLE.getTableName() + " ADD COLUMN categoryId TEXT default ''");
        dbPatches.put("157", "DROP INDEX IF EXISTS idx_series_watch_unique");
        dbPatches.put("158", "CREATE UNIQUE INDEX IF NOT EXISTS idx_series_watch_unique ON " + SERIES_WATCH_STATE_TABLE.getTableName() + " (accountId, mode, categoryId, seriesId)");
        dbPatches.put("159", "ALTER TABLE " + CONFIGURATION_TABLE.getTableName() + " ADD COLUMN cacheExpiryDays TEXT default '30'");
        dbPatches.put("160", "ALTER TABLE " + SERIES_EPISODE_TABLE.getTableName() + " ADD COLUMN categoryId TEXT default ''");
    }

    public static void applyPatches(Connection conn) throws SQLException {
        createAppliedPatchesTable(conn);

        for (String key : dbPatches.keySet()) {
            String patch = dbPatches.get(key);
            if (!isPatchApplied(conn, Integer.parseInt(key))) {
                recordPatchApplied(conn, Integer.parseInt(key));
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(patch);
                } catch (Exception ignored) {
                    ignored.getMessage();
                }
            }
        }
    }

    private static void createAppliedPatchesTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS AppliedPatches (patchIndex INTEGER PRIMARY KEY)");
        }
    }

    private static boolean isPatchApplied(Connection conn, int patchIndex) throws SQLException {
        String query = "SELECT 1 FROM AppliedPatches WHERE patchIndex = ?";
        try (PreparedStatement pstmt = conn.prepareStatement(query)) {
            pstmt.setInt(1, patchIndex);
            try (ResultSet rs = pstmt.executeQuery()) {
                return rs.next();
            }
        }
    }

    private static void recordPatchApplied(Connection conn, int patchIndex) throws SQLException {
        String insert = "INSERT INTO AppliedPatches (patchIndex) VALUES (?)";
        try (PreparedStatement pstmt = conn.prepareStatement(insert)) {
            pstmt.setInt(1, patchIndex);
            pstmt.executeUpdate();
        }
    }
}
