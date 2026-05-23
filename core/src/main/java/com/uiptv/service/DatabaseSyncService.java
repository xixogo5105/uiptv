package com.uiptv.service;

import com.uiptv.db.DatabaseUtils;
import com.uiptv.service.remotesync.SecureTempFileSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static com.uiptv.util.SQLiteTableSync.ensureDatabaseReady;

public class DatabaseSyncService {
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String SELECT_SQL = "SELECT ";
    private static final String FROM_SQL = " FROM ";
    private static final String ORDER_BY_SQL = " ORDER BY ";
    private static final String LIMIT_ONE_SQL = " LIMIT 1";
    private static final String CONFIGURATION_TABLE = DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName();
    private static final Set<String> EXTERNAL_PLAYER_PATH_COLUMNS = Set.of(
            "playerPath1",
            "playerPath2",
            "playerPath3",
            "defaultPlayerPath"
    );

    private DatabaseSyncService() {
    }

    private static class SingletonHelper {
        private static final DatabaseSyncService INSTANCE = new DatabaseSyncService();
    }

    public static DatabaseSyncService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public void syncDatabases(String sourceDB, String targetDB) throws SQLException {
        syncDatabases(sourceDB, targetDB, false, false);
    }

    public void syncDatabases(String sourceDB, String targetDB, boolean syncConfiguration, boolean syncExternalPlayerPaths) throws SQLException {
        syncDatabasesWithReport(sourceDB, targetDB, syncConfiguration, syncExternalPlayerPaths, null);
    }

    public DatabaseSyncReport syncDatabasesWithReport(String sourceDB,
                                                      String targetDB,
                                                      boolean syncConfiguration,
                                                      boolean syncExternalPlayerPaths,
                                                      SyncProgressListener progressListener) throws SQLException {
        Path sourceSnapshot = createSourceSnapshot(sourceDB);
        try {
            return cloneDatabaseWithConfigurationPolicy(
                    sourceSnapshot,
                    targetDB,
                    syncConfiguration,
                    syncExternalPlayerPaths,
                    progressListener
            );
        } finally {
            deleteIfExists(sourceSnapshot);
        }
    }

    private DatabaseSyncReport cloneDatabaseWithConfigurationPolicy(Path sourceSnapshot,
                                                                    String targetDB,
                                                                    boolean syncConfiguration,
                                                                    boolean syncExternalPlayerPaths,
                                                                    SyncProgressListener progressListener) throws SQLException {
        notifyProgress(progressListener, 0, 4, "Preparing target database");
        ensureDatabaseReady(targetDB);

        ConfigurationRows preservedConfiguration = syncConfiguration ? null : readConfigurationRows(targetDB);
        Map<String, Object> preservedPlayerPaths = syncConfiguration && !syncExternalPlayerPaths
                ? readFirstConfigurationValues(targetDB, EXTERNAL_PLAYER_PATH_COLUMNS)
                : Map.of();

        notifyProgress(progressListener, 1, 4, "Cloning database");
        replaceTargetDatabase(sourceSnapshot, Path.of(targetDB));

        notifyProgress(progressListener, 2, 4, "Applying migrations");
        ensureDatabaseReady(targetDB);

        boolean configurationCopied = false;
        if (preservedConfiguration != null && preservedConfiguration.hasRows()) {
            restoreConfigurationRows(targetDB, preservedConfiguration);
        } else if (!preservedPlayerPaths.isEmpty()) {
            restoreFirstConfigurationValues(targetDB, preservedPlayerPaths);
            configurationCopied = hasConfigurationRows(targetDB);
        } else if (syncConfiguration) {
            configurationCopied = hasConfigurationRows(targetDB);
        }

        notifyProgress(progressListener, 3, 4, "Counting cloned rows");
        List<TableSyncResult> tableResults = countKnownTables(targetDB);
        notifyProgress(progressListener, 4, 4, null);
        return new DatabaseSyncReport(tableResults, syncConfiguration, configurationCopied, syncExternalPlayerPaths);
    }

    private Path createSourceSnapshot(String sourceDB) throws SQLException {
        Path snapshotPath = null;
        try {
            snapshotPath = SecureTempFileSupport.createTempFile("uiptv-db-sync-", ".db");
            try (Connection sourceConn = DriverManager.getConnection(SQLITE_PREFIX + sourceDB);
                 Statement statement = sourceConn.createStatement()) {
                statement.execute("VACUUM INTO '" + escapeSqlLiteral(snapshotPath.toAbsolutePath().toString()) + "'");
            }
            return snapshotPath;
        } catch (IOException ex) {
            throw new SQLException("Unable to create database sync snapshot", ex);
        } catch (SQLException ex) {
            deleteIfExists(snapshotPath);
            throw ex;
        }
    }

    private void replaceTargetDatabase(Path sourceSnapshot, Path targetPath) throws SQLException {
        try {
            Path parent = targetPath.toAbsolutePath().getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            checkpointDatabase(targetPath);
            deleteDatabaseSidecars(targetPath);
            Files.copy(sourceSnapshot, targetPath, StandardCopyOption.REPLACE_EXISTING);
            deleteDatabaseSidecars(targetPath);
        } catch (IOException ex) {
            throw new SQLException("Unable to replace target database", ex);
        }
    }

    private void checkpointDatabase(Path databasePath) {
        if (!Files.exists(databasePath)) {
            return;
        }
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + databasePath);
             Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        } catch (SQLException _) {
            // The replacement below is still authoritative; this only reduces stale WAL sidecars.
        }
    }

    private void deleteDatabaseSidecars(Path databasePath) throws IOException {
        Files.deleteIfExists(Path.of(databasePath.toString() + "-wal"));
        Files.deleteIfExists(Path.of(databasePath.toString() + "-shm"));
    }

    private ConfigurationRows readConfigurationRows(String dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + dbPath)) {
            List<String> columns = getTableColumns(conn, CONFIGURATION_TABLE);
            if (columns.isEmpty()) {
                return new ConfigurationRows(List.of(), List.of());
            }
            String columnList = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            List<Map<String, Object>> rows = new ArrayList<>();
            try (Statement statement = conn.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         SELECT_SQL + columnList + FROM_SQL + quoteIdentifier(CONFIGURATION_TABLE)
                                 + ORDER_BY_SQL + quoteIdentifier("id"))) {
                while (resultSet.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    for (String column : columns) {
                        row.put(column, resultSet.getObject(column));
                    }
                    rows.add(row);
                }
            }
            return new ConfigurationRows(columns, rows);
        }
    }

    private Map<String, Object> readFirstConfigurationValues(String dbPath, Set<String> requestedColumns) throws SQLException {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + dbPath)) {
            List<String> columns = getTableColumns(conn, CONFIGURATION_TABLE).stream()
                    .filter(requestedColumns::contains)
                    .toList();
            if (columns.isEmpty()) {
                return Map.of();
            }
            String columnList = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
            try (Statement statement = conn.createStatement();
                 ResultSet resultSet = statement.executeQuery(
                         SELECT_SQL + columnList + FROM_SQL + quoteIdentifier(CONFIGURATION_TABLE)
                                 + ORDER_BY_SQL + quoteIdentifier("id") + LIMIT_ONE_SQL)) {
                if (!resultSet.next()) {
                    return Map.of();
                }
                Map<String, Object> values = new LinkedHashMap<>();
                for (String column : columns) {
                    values.put(column, resultSet.getObject(column));
                }
                return values;
            }
        }
    }

    private void restoreConfigurationRows(String dbPath, ConfigurationRows configurationRows) throws SQLException {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + dbPath);
             Statement deleteStatement = conn.createStatement()) {
            boolean originalAutoCommit = conn.getAutoCommit();
            try {
                conn.setAutoCommit(false);
                deleteStatement.executeUpdate("DELETE FROM " + quoteIdentifier(CONFIGURATION_TABLE));
                List<String> targetColumns = getTableColumns(conn, CONFIGURATION_TABLE);
                List<String> commonColumns = configurationRows.columns().stream()
                        .filter(targetColumns::contains)
                        .toList();
                insertRows(conn, CONFIGURATION_TABLE, commonColumns, configurationRows.rows());
                conn.commit();
            } catch (SQLException ex) {
                conn.rollback();
                throw ex;
            } finally {
                conn.setAutoCommit(originalAutoCommit);
            }
        }
    }

    private void restoreFirstConfigurationValues(String dbPath, Map<String, Object> values) throws SQLException {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + dbPath)) {
            List<String> targetColumns = getTableColumns(conn, CONFIGURATION_TABLE);
            List<String> columns = values.keySet().stream()
                    .filter(targetColumns::contains)
                    .toList();
            if (columns.isEmpty()) {
                return;
            }
            Object targetId = firstConfigurationId(conn);
            if (targetId == null) {
                targetId = 1;
                try (PreparedStatement insert = conn.prepareStatement(
                        "INSERT INTO " + quoteIdentifier(CONFIGURATION_TABLE) + " (" + quoteIdentifier("id") + ") VALUES (?)")) {
                    insert.setObject(1, targetId);
                    insert.executeUpdate();
                }
            }
            String assignments = columns.stream()
                    .map(column -> quoteIdentifier(column) + " = ?")
                    .collect(Collectors.joining(", "));
            try (PreparedStatement update = conn.prepareStatement(
                    "UPDATE " + quoteIdentifier(CONFIGURATION_TABLE) + " SET " + assignments
                            + " WHERE " + quoteIdentifier("id") + " = ?")) {
                for (int i = 0; i < columns.size(); i++) {
                    update.setObject(i + 1, values.get(columns.get(i)));
                }
                update.setObject(columns.size() + 1, targetId);
                update.executeUpdate();
            }
        }
    }

    private void insertRows(Connection conn,
                            String tableName,
                            List<String> columns,
                            List<Map<String, Object>> rows) throws SQLException {
        if (columns.isEmpty() || rows.isEmpty()) {
            return;
        }
        String columnList = columns.stream().map(this::quoteIdentifier).collect(Collectors.joining(", "));
        String placeholders = columns.stream().map(_ -> "?").collect(Collectors.joining(", "));
        try (PreparedStatement insert = conn.prepareStatement(
                "INSERT INTO " + quoteIdentifier(tableName) + " (" + columnList + ") VALUES (" + placeholders + ")")) {
            for (Map<String, Object> row : rows) {
                for (int i = 0; i < columns.size(); i++) {
                    insert.setObject(i + 1, row.get(columns.get(i)));
                }
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private Object firstConfigurationId(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     SELECT_SQL + quoteIdentifier("id") + FROM_SQL + quoteIdentifier(CONFIGURATION_TABLE)
                             + ORDER_BY_SQL + quoteIdentifier("id") + LIMIT_ONE_SQL)) {
            return resultSet.next() ? resultSet.getObject(1) : null;
        }
    }

    private boolean hasConfigurationRows(String dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + dbPath);
             Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery(
                     "SELECT 1 FROM " + quoteIdentifier(CONFIGURATION_TABLE) + LIMIT_ONE_SQL)) {
            return resultSet.next();
        }
    }

    private List<TableSyncResult> countKnownTables(String dbPath) throws SQLException {
        List<TableSyncResult> tableResults = new ArrayList<>();
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + dbPath)) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.DbTable.values()) {
                String tableName = table.getTableName();
                if (tableExists(conn, tableName)) {
                    tableResults.add(new TableSyncResult(tableName, countRows(conn, tableName)));
                }
            }
        }
        return tableResults;
    }

    private int countRows(Connection conn, String tableName) throws SQLException {
        try (Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT COUNT(*) FROM " + quoteIdentifier(tableName))) {
            return resultSet.next() ? resultSet.getInt(1) : 0;
        }
    }

    private boolean tableExists(Connection conn, String tableName) throws SQLException {
        try (PreparedStatement statement = conn.prepareStatement(
                "SELECT 1 FROM sqlite_master WHERE type = 'table' AND name = ?")) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next();
            }
        }
    }

    private List<String> getTableColumns(Connection conn, String tableName) throws SQLException {
        List<String> columns = new ArrayList<>();
        try (Statement statement = conn.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(" + quoteIdentifier(tableName) + ")")) {
            while (resultSet.next()) {
                columns.add(resultSet.getString("name"));
            }
        }
        return columns;
    }

    private void deleteIfExists(Path path) throws SQLException {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ex) {
            throw new SQLException("Unable to delete temporary sync snapshot", ex);
        }
    }

    private String quoteIdentifier(String identifier) {
        return "\"" + identifier.replace("\"", "\"\"") + "\"";
    }

    private String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }

    private void notifyProgress(SyncProgressListener progressListener, int completedSteps, int totalSteps, String currentStep) {
        if (progressListener == null) {
            return;
        }
        progressListener.onProgress(completedSteps, totalSteps, currentStep);
    }

    @FunctionalInterface
    public interface SyncProgressListener {
        void onProgress(int completedSteps, int totalSteps, String currentStep);
    }

    public record DatabaseSyncReport(List<TableSyncResult> tableResults,
                                     boolean configurationRequested,
                                     boolean configurationCopied,
                                     boolean externalPlayerPathsIncluded) {
        public DatabaseSyncReport {
            tableResults = Collections.unmodifiableList(new ArrayList<>(tableResults));
        }

        public List<TableSyncResult> getTableResults() {
            return tableResults;
        }

        public boolean isConfigurationRequested() {
            return configurationRequested;
        }

        public boolean isConfigurationCopied() {
            return configurationCopied;
        }

        public boolean isExternalPlayerPathsIncluded() {
            return externalPlayerPathsIncluded;
        }

        public int getTotalRowsSynced() {
            return tableResults.stream().mapToInt(TableSyncResult::getRowCount).sum();
        }
    }

    public record TableSyncResult(String tableName, int rowCount) {
        public String getTableName() {
            return tableName;
        }

        public int getRowCount() {
            return rowCount;
        }
    }

    private record ConfigurationRows(List<String> columns, List<Map<String, Object>> rows) {
        private boolean hasRows() {
            return !rows.isEmpty();
        }
    }
}
