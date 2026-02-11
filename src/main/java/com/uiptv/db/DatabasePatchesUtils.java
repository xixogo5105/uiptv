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
