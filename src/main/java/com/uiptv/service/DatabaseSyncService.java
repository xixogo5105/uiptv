package com.uiptv.service;

import com.uiptv.db.DatabaseUtils;

import java.sql.SQLException;

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

    public void syncDatabases(String firstDB, String secondDB) throws SQLException {
        for (DatabaseUtils.DbTable tableName : DatabaseUtils.DbTable.values()) {
            if (DatabaseUtils.Syncable.contains(tableName)) {
                syncTables(firstDB, secondDB, tableName);
            }
        }
    }
}
