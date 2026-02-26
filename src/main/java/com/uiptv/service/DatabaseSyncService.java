package com.uiptv.service;

import com.uiptv.db.DatabaseUtils;

import java.sql.SQLException;

import static com.uiptv.util.SQLiteTableSync.syncTables;

public class DatabaseSyncService {
    private static DatabaseSyncService instance;

    private DatabaseSyncService() {
    }

    public static synchronized DatabaseSyncService getInstance() {
        if (instance == null) {
            instance = new DatabaseSyncService();
        }
        return instance;
    }

    public void syncDatabases(String firstDB, String secondDB) throws SQLException {
        for (DatabaseUtils.DbTable tableName : DatabaseUtils.DbTable.values()) {
            if (DatabaseUtils.Syncable.contains(tableName)) {
                syncTables(firstDB, secondDB, tableName.getTableName());
            }
        }
    }
}
