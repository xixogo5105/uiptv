package com.uiptv.util;

import com.uiptv.db.DatabasePatchesUtils;
import com.uiptv.db.DatabaseUtils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public final class SQLiteTableSync {
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";

    private SQLiteTableSync() {
    }

    public static void ensureDatabaseReady(String dbPath) throws SQLException {
        try {
            File file = new File(dbPath);
            File parent = file.getParentFile();
            if (parent != null && !parent.exists() && !parent.mkdirs() && !parent.exists()) {
                throw new IOException("Unable to create parent directory for " + dbPath);
            }
            if (!file.exists() && !file.createNewFile() && !file.exists()) {
                throw new IOException("Unable to create database file " + dbPath);
            }
        } catch (IOException e) {
            throw new SQLException("Unable to prepare database file " + dbPath, e);
        }

        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + dbPath)) {
            DatabasePatchesUtils.applyPatches(conn);
        }
    }

    public static int syncTables(String sourceDBPath, String targetDBPath, DatabaseUtils.DbTable table) throws SQLException {
        String tableName = DatabaseUtils.validatedTableName(table);
        try (
                Connection sourceConn = DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath);
                Connection targetConn = DriverManager.getConnection(SQLITE_PREFIX + targetDBPath)
        ) {
            int syncedRows = syncTable(sourceConn, targetConn, tableName);
            AppLog.addInfoLog(SQLiteTableSync.class, "Table '" + tableName + "' synced from source to target.");
            return syncedRows;
        }
    }

    public static int replaceTable(String sourceDBPath, String targetDBPath, DatabaseUtils.DbTable table) throws SQLException {
        String tableName = DatabaseUtils.validatedTableName(table);
        try (
                Connection sourceConn = DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath);
                Connection targetConn = DriverManager.getConnection(SQLITE_PREFIX + targetDBPath)
        ) {
            int syncedRows = replaceTable(sourceConn, targetConn, tableName);
            AppLog.addInfoLog(SQLiteTableSync.class, "Table '" + tableName + "' replaced from source to target.");
            return syncedRows;
        }
    }

    public static boolean syncConfiguration(String sourceDBPath, String targetDBPath, boolean includeExternalPlayerPaths) throws SQLException {
        try (
                Connection sourceConn = DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath);
                Connection targetConn = DriverManager.getConnection(SQLITE_PREFIX + targetDBPath)
        ) {
            boolean synced = syncConfiguration(sourceConn, targetConn, includeExternalPlayerPaths);
            AppLog.addInfoLog(SQLiteTableSync.class, "Configuration synced from source to target.");
            return synced;
        }
    }

    public static int syncPublishedM3uSelections(String sourceDBPath, String targetDBPath) throws SQLException {
        try (
                Connection sourceConn = DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath);
                Connection targetConn = DriverManager.getConnection(SQLITE_PREFIX + targetDBPath)
        ) {
            int synced = syncPublishedM3uSelections(sourceConn, targetConn);
            AppLog.addInfoLog(SQLiteTableSync.class, "PublishedM3uSelection synced from source to target with account remapping.");
            return synced;
        }
    }

    private static int syncTable(Connection sourceConn, Connection targetConn, String tableName) throws SQLException {
        List<String> targetColumns = getTableColumns(targetConn, tableName);
        Set<String> targetColumnSet = new LinkedHashSet<>(targetColumns);
        List<String> sourceColumns = getTableColumns(sourceConn, tableName);
        List<String> commonColumns = sourceColumns.stream()
                .filter(targetColumnSet::contains)
                .toList();

        if (commonColumns.isEmpty()) {
            throw new SQLException("No common columns found for table " + tableName);
        }

        String columnList = String.join(", ", commonColumns);
        String placeholders = commonColumns.stream().map(column -> "?").collect(Collectors.joining(", "));
        String selectSql = "SELECT " + columnList + " FROM " + tableName;
        String insertSql = "INSERT OR REPLACE INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";

        try (
                Statement sourceStmt = sourceConn.createStatement();
                ResultSet sourceResult = sourceStmt.executeQuery(selectSql);
                PreparedStatement targetStatement = targetConn.prepareStatement(insertSql)
        ) {
            int columnCount = commonColumns.size();
            int syncedRows = 0;
            while (sourceResult.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    targetStatement.setObject(i, sourceResult.getObject(i));
                }
                targetStatement.addBatch();
                syncedRows++;
            }
            targetStatement.executeBatch();
            return syncedRows;
        }
    }

    private static int replaceTable(Connection sourceConn, Connection targetConn, String tableName) throws SQLException {
        List<String> targetColumns = getTableColumns(targetConn, tableName);
        Set<String> targetColumnSet = new LinkedHashSet<>(targetColumns);
        List<String> sourceColumns = getTableColumns(sourceConn, tableName);
        List<String> commonColumns = sourceColumns.stream()
                .filter(targetColumnSet::contains)
                .toList();

        if (commonColumns.isEmpty()) {
            throw new SQLException("No common columns found for table " + tableName);
        }

        String columnList = String.join(", ", commonColumns);
        String placeholders = commonColumns.stream().map(column -> "?").collect(Collectors.joining(", "));
        String selectSql = "SELECT " + columnList + " FROM " + tableName;
        String deleteSql = "DELETE FROM " + tableName;
        String insertSql = "INSERT INTO " + tableName + " (" + columnList + ") VALUES (" + placeholders + ")";

        boolean originalAutoCommit = targetConn.getAutoCommit();
        try (
                Statement sourceStmt = sourceConn.createStatement();
                ResultSet sourceResult = sourceStmt.executeQuery(selectSql);
                Statement deleteStatement = targetConn.createStatement();
                PreparedStatement targetStatement = targetConn.prepareStatement(insertSql)
        ) {
            targetConn.setAutoCommit(false);
            deleteStatement.executeUpdate(deleteSql);

            int columnCount = commonColumns.size();
            int syncedRows = 0;
            while (sourceResult.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    targetStatement.setObject(i, sourceResult.getObject(i));
                }
                targetStatement.addBatch();
                syncedRows++;
            }
            targetStatement.executeBatch();
            targetConn.commit();
            return syncedRows;
        } catch (SQLException e) {
            targetConn.rollback();
            throw e;
        } finally {
            targetConn.setAutoCommit(originalAutoCommit);
        }
    }

    private static boolean syncConfiguration(Connection sourceConn, Connection targetConn, boolean includeExternalPlayerPaths) throws SQLException {
        List<String> sourceColumns = getTableColumns(sourceConn, DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName());
        List<String> targetColumns = getTableColumns(targetConn, DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName());
        List<String> commonColumns = sourceColumns.stream()
                .filter(new LinkedHashSet<>(targetColumns)::contains)
                .toList();

        if (commonColumns.isEmpty()) {
            return false;
        }

        ConfigurationRow sourceRow = readFirstConfigurationRow(sourceConn, commonColumns);
        if (sourceRow == null) {
            return false;
        }
        ConfigurationRow targetRow = readFirstConfigurationRow(targetConn, commonColumns);
        if (!includeExternalPlayerPaths && targetRow != null) {
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath1");
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath2");
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath3");
            sourceRow.copyColumnIfPresentFrom(targetRow, "defaultPlayerPath");
        }

        upsertFirstConfigurationRow(targetConn, sourceRow, targetRow != null ? targetRow.id : null);
        return true;
    }

    private static int syncPublishedM3uSelections(Connection sourceConn, Connection targetConn) throws SQLException {
        String selectionTable = DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.getTableName();
        String accountTable = DatabaseUtils.DbTable.ACCOUNT_TABLE.getTableName();
        boolean originalAutoCommit = targetConn.getAutoCommit();
        int syncedRows = 0;

        try (
                PreparedStatement sourceSelections = sourceConn.prepareStatement(
                        "SELECT accountId FROM " + selectionTable + " ORDER BY id");
                ResultSet sourceRows = sourceSelections.executeQuery();
                PreparedStatement sourceAccountName = sourceConn.prepareStatement(
                        "SELECT accountName FROM " + accountTable + " WHERE id = ?");
                PreparedStatement targetAccountId = targetConn.prepareStatement(
                        "SELECT id FROM " + accountTable + " WHERE accountName = ?");
                Statement deleteTargetSelections = targetConn.createStatement();
                PreparedStatement insertTargetSelection = targetConn.prepareStatement(
                        "INSERT INTO " + selectionTable + " (accountId) VALUES (?)")
        ) {
            targetConn.setAutoCommit(false);
            deleteTargetSelections.executeUpdate("DELETE FROM " + selectionTable);

            while (sourceRows.next()) {
                String sourceAccountId = sourceRows.getString("accountId");
                if (sourceAccountId == null || sourceAccountId.isBlank()) {
                    continue;
                }

                sourceAccountName.setString(1, sourceAccountId);
                try (ResultSet sourceAccount = sourceAccountName.executeQuery()) {
                    if (!sourceAccount.next()) {
                        continue;
                    }

                    String accountName = sourceAccount.getString("accountName");
                    if (accountName == null || accountName.isBlank()) {
                        continue;
                    }

                    targetAccountId.setString(1, accountName);
                    try (ResultSet targetAccount = targetAccountId.executeQuery()) {
                        if (!targetAccount.next()) {
                            continue;
                        }
                        insertTargetSelection.setString(1, targetAccount.getString("id"));
                        insertTargetSelection.addBatch();
                        syncedRows++;
                    }
                }
            }

            if (syncedRows > 0) {
                insertTargetSelection.executeBatch();
            }
            targetConn.commit();
            return syncedRows;
        } catch (SQLException e) {
            targetConn.rollback();
            throw e;
        } finally {
            targetConn.setAutoCommit(originalAutoCommit);
        }
    }

    private static ConfigurationRow readFirstConfigurationRow(Connection conn, List<String> columns) throws SQLException {
        String columnList = columns.stream()
                .filter(column -> !"id".equalsIgnoreCase(column))
                .collect(Collectors.joining(", "));
        if (columnList.isBlank()) {
            return null;
        }

        String sql = "SELECT id, " + columnList + " FROM " + DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName() + " ORDER BY id LIMIT 1";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            if (!rs.next()) {
                return null;
            }
            ConfigurationRow row = new ConfigurationRow(rs.getObject("id"));
            for (String column : columns) {
                if ("id".equalsIgnoreCase(column)) {
                    continue;
                }
                row.put(column, rs.getObject(column));
            }
            return row;
        }
    }

    private static void upsertFirstConfigurationRow(Connection conn, ConfigurationRow row, Object targetId) throws SQLException {
        List<String> columns = new ArrayList<>(row.columns());
        if (columns.isEmpty()) {
            return;
        }

        if (targetId == null) {
            String columnList = String.join(", ", columns);
            String placeholders = columns.stream().map(column -> "?").collect(Collectors.joining(", "));
            String sql = "INSERT INTO " + DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName()
                    + " (" + columnList + ") VALUES (" + placeholders + ")";
            try (PreparedStatement statement = conn.prepareStatement(sql)) {
                bindColumns(statement, columns, row);
                statement.executeUpdate();
            }
            return;
        }

        String assignments = columns.stream().map(column -> column + "=?").collect(Collectors.joining(", "));
        String sql = "UPDATE " + DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName()
                + " SET " + assignments + " WHERE id = ?";
        try (PreparedStatement statement = conn.prepareStatement(sql)) {
            bindColumns(statement, columns, row);
            statement.setObject(columns.size() + 1, targetId);
            statement.executeUpdate();
        }
    }

    private static void bindColumns(PreparedStatement statement, List<String> columns, ConfigurationRow row) throws SQLException {
        for (int i = 0; i < columns.size(); i++) {
            statement.setObject(i + 1, row.get(columns.get(i)));
        }
    }

    private static List<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                columns.add(rs.getString("name"));
            }
        }
        return columns;
    }

    private static final class ConfigurationRow {
        private final Object id;
        private final java.util.LinkedHashMap<String, Object> values = new java.util.LinkedHashMap<>();

        private ConfigurationRow(Object id) {
            this.id = id;
        }

        void put(String column, Object value) {
            values.put(column, value);
        }

        Object get(String column) {
            return values.get(column);
        }

        Set<String> columns() {
            return values.keySet();
        }

        void copyColumnIfPresentFrom(ConfigurationRow source, String column) {
            if (source != null && values.containsKey(column) && source.values.containsKey(column)) {
                values.put(column, source.values.get(column));
            }
        }
    }
}
