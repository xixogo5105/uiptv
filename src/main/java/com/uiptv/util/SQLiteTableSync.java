package com.uiptv.util;

import com.uiptv.ui.LogDisplayUI;

import java.sql.*;
import java.util.Collections;

public class SQLiteTableSync {

    public static void syncTables(String firstDBPath, String secondDBPath, String tableName) throws SQLException {
        try (
                Connection firstConn = DriverManager.getConnection("jdbc:sqlite:" + firstDBPath);
                Connection secondConn = DriverManager.getConnection("jdbc:sqlite:" + secondDBPath);
                Statement firstStmt = firstConn.createStatement();
                Statement secondStmt = secondConn.createStatement();
        ) {
            // Get column count for both tables
            int firstColCount = firstStmt.executeQuery("SELECT * FROM " + tableName).getMetaData().getColumnCount();
            int secondColCount = secondStmt.executeQuery("SELECT * FROM " + tableName).getMetaData().getColumnCount();

            // Check if column counts match
            if (firstColCount != secondColCount) {
                throw new SQLException("Column counts mismatch between first and second tables.");
            }

            // Sync from first to second
            syncDirection(firstConn, secondConn, tableName);

            // Sync from second to first
            syncDirection(secondConn, firstConn, tableName);

            LogDisplayUI.addLog("Tables '" + tableName + "' in both databases synced successfully.");

        }
    }

    private static void syncDirection(Connection sourceConn, Connection targetConn, String tableName)
            throws SQLException {
        try (
                Statement sourceStmt = sourceConn.createStatement();
                ResultSet sourceResult = sourceStmt.executeQuery("SELECT * FROM " + tableName);
                PreparedStatement targetStmtPrepared = targetConn.prepareStatement(
                        "INSERT OR REPLACE INTO " + tableName + " VALUES (" +
                                String.join(",", Collections.nCopies(sourceResult.getMetaData().getColumnCount(), "?")) + ")"
                )
        ) {
            while (sourceResult.next()) {
                for (int i = 1; i <= sourceResult.getMetaData().getColumnCount(); i++) {
                    targetStmtPrepared.setObject(i, sourceResult.getObject(i));
                }
                targetStmtPrepared.executeUpdate();
            }
        }
    }
}