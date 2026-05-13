package com.uiptv.shared.sync;

public record RemoteSyncExecutionResult(DatabaseSyncReport report, String message) {
    public RemoteSyncExecutionResult {
        message = message == null ? "" : message;
    }
}
