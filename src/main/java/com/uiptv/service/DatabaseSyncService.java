package com.uiptv.service;

import com.uiptv.db.DatabaseUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.sql.SQLException;

import static com.uiptv.util.SQLiteTableSync.ensureDatabaseReady;
import static com.uiptv.util.SQLiteTableSync.replaceTable;
import static com.uiptv.util.SQLiteTableSync.syncPublishedM3uSelections;
import static com.uiptv.util.SQLiteTableSync.syncTables;

public class DatabaseSyncService {
    private static final List<DatabaseUtils.DbTable> CONFIGURATION_SYNCABLE = List.of(
            DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE,
            DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE
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
        ensureDatabaseReady(targetDB);
        List<TableSyncResult> tableResults = new ArrayList<>();
        int totalSteps = DatabaseUtils.Syncable.size() + (syncConfiguration ? 1 + CONFIGURATION_SYNCABLE.size() : 0);
        int completedSteps = 0;
        for (DatabaseUtils.DbTable tableName : DatabaseUtils.Syncable) {
            notifyProgress(progressListener, completedSteps, totalSteps, tableName.getTableName());
            int syncedRows = syncTables(sourceDB, targetDB, tableName);
            tableResults.add(new TableSyncResult(tableName.getTableName(), syncedRows));
            completedSteps++;
        }
        boolean configurationCopied = false;
        if (syncConfiguration) {
            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName());
            configurationCopied = com.uiptv.util.SQLiteTableSync.syncConfiguration(sourceDB, targetDB, syncExternalPlayerPaths);
            completedSteps++;
            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE.getTableName());
            tableResults.add(new TableSyncResult(
                    DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE.getTableName(),
                    replaceTable(sourceDB, targetDB, DatabaseUtils.DbTable.THEME_CSS_OVERRIDE_TABLE)
            ));
            completedSteps++;

            notifyProgress(progressListener, completedSteps, totalSteps, DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.getTableName());
            tableResults.add(new TableSyncResult(
                    DatabaseUtils.DbTable.PUBLISHED_M3U_SELECTION_TABLE.getTableName(),
                    syncPublishedM3uSelections(sourceDB, targetDB)
            ));
            completedSteps++;
        }
        notifyProgress(progressListener, completedSteps, totalSteps, null);
        return new DatabaseSyncReport(tableResults, syncConfiguration, configurationCopied, syncExternalPlayerPaths);
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

    public static final class DatabaseSyncReport {
        private final List<TableSyncResult> tableResults;
        private final boolean configurationRequested;
        private final boolean configurationCopied;
        private final boolean externalPlayerPathsIncluded;

        public DatabaseSyncReport(List<TableSyncResult> tableResults,
                                  boolean configurationRequested,
                                  boolean configurationCopied,
                                  boolean externalPlayerPathsIncluded) {
            this.tableResults = Collections.unmodifiableList(new ArrayList<>(tableResults));
            this.configurationRequested = configurationRequested;
            this.configurationCopied = configurationCopied;
            this.externalPlayerPathsIncluded = externalPlayerPathsIncluded;
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

    public static final class TableSyncResult {
        private final String tableName;
        private final int rowCount;

        public TableSyncResult(String tableName, int rowCount) {
            this.tableName = tableName;
            this.rowCount = rowCount;
        }

        public String getTableName() {
            return tableName;
        }

        public int getRowCount() {
            return rowCount;
        }
    }
}
