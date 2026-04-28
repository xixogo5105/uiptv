package com.uiptv.service;

import com.uiptv.db.DatabaseUtils;

import java.sql.SQLException;

import static com.uiptv.util.SQLiteTableSync.ensureDatabaseReady;
import static com.uiptv.util.SQLiteTableSync.syncTables;

public class DatabaseSyncService {
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
        ensureDatabaseReady(targetDB);
        for (DatabaseUtils.DbTable tableName : DatabaseUtils.Syncable) {
            syncTables(sourceDB, targetDB, tableName);
        }
        if (syncConfiguration) {
            com.uiptv.util.SQLiteTableSync.syncConfiguration(sourceDB, targetDB, syncExternalPlayerPaths);
        }
    }
}
