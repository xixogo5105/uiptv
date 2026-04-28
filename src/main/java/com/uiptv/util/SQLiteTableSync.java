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

    public static void syncTables(String sourceDBPath, String targetDBPath, DatabaseUtils.DbTable table) throws SQLException {
        String tableName = DatabaseUtils.validatedTableName(table);
        try (
                Connection sourceConn = DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath);
                Connection targetConn = DriverManager.getConnection(SQLITE_PREFIX + targetDBPath)
        ) {
            syncTable(sourceConn, targetConn, tableName);
            AppLog.addInfoLog(SQLiteTableSync.class, "Table '" + tableName + "' synced from source to target.");
        }
    }

    public static void syncConfiguration(String sourceDBPath, String targetDBPath, boolean includeExternalPlayerPaths) throws SQLException {
        try (
                Connection sourceConn = DriverManager.getConnection(SQLITE_PREFIX + sourceDBPath);
                Connection targetConn = DriverManager.getConnection(SQLITE_PREFIX + targetDBPath)
        ) {
            syncConfiguration(sourceConn, targetConn, includeExternalPlayerPaths);
            AppLog.addInfoLog(SQLiteTableSync.class, "Configuration synced from source to target.");
        }
    }

    private static void syncTable(Connection sourceConn, Connection targetConn, String tableName) throws SQLException {
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
            while (sourceResult.next()) {
                for (int i = 1; i <= columnCount; i++) {
                    targetStatement.setObject(i, sourceResult.getObject(i));
                }
                targetStatement.addBatch();
            }
            targetStatement.executeBatch();
        }
    }

    private static void syncConfiguration(Connection sourceConn, Connection targetConn, boolean includeExternalPlayerPaths) throws SQLException {
        List<String> sourceColumns = getTableColumns(sourceConn, DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName());
        List<String> targetColumns = getTableColumns(targetConn, DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName());
        List<String> commonColumns = sourceColumns.stream()
                .filter(new LinkedHashSet<>(targetColumns)::contains)
                .toList();

        if (commonColumns.isEmpty()) {
            return;
        }

        ConfigurationRow sourceRow = readFirstConfigurationRow(sourceConn, commonColumns);
        if (sourceRow == null) {
            return;
        }
        ConfigurationRow targetRow = readFirstConfigurationRow(targetConn, commonColumns);
        if (!includeExternalPlayerPaths && targetRow != null) {
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath1");
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath2");
            sourceRow.copyColumnIfPresentFrom(targetRow, "playerPath3");
            sourceRow.copyColumnIfPresentFrom(targetRow, "defaultPlayerPath");
        }

        upsertFirstConfigurationRow(targetConn, sourceRow, targetRow != null ? targetRow.id : null);
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
