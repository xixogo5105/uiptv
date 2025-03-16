package com.uiptv.db;

import java.sql.*;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.*;

public class DatabasePatchesUtils {
    private static List<String> dbPatches = new ArrayList<>();

    static {
        dbPatches.add("CREATE TABLE IF NOT EXISTS " + BOOKMARK_CATEGORY_TABLE.getTableName() + " (id INTEGER PRIMARY KEY, name TEXT NOT NULL)");
        dbPatches.add("ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " ADD COLUMN macAddressList TEXT");
        dbPatches.add("ALTER TABLE " + ACCOUNT_TABLE.getTableName() + " ADD COLUMN pinToTop TEXT default '0'");
        dbPatches.add("ALTER TABLE " + BOOKMARK_TABLE.getTableName() + " ADD COLUMN categoryId TEXT");
    }

    public static void applyPatches(Connection conn) throws SQLException {
        createAppliedPatchesTable(conn);
        for (int i = 0; i < dbPatches.size(); i++) {
            String patch = dbPatches.get(i);
            if (!isPatchApplied(conn, i)) {
                try (Statement stmt = conn.createStatement()) {
                    stmt.executeUpdate(patch);
                    recordPatchApplied(conn, i);
                } catch (Exception ignored) {
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