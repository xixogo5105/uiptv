package com.uiptv.shared.sync;

public record TableSyncResult(String tableName, int rowCount) {
    public TableSyncResult {
        tableName = tableName == null ? "" : tableName;
    }
}
