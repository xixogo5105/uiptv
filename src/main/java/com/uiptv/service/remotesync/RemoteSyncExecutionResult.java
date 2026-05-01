package com.uiptv.service.remotesync;

import com.uiptv.service.DatabaseSyncService;

public record RemoteSyncExecutionResult(DatabaseSyncService.DatabaseSyncReport report,
                                        String message) {
}
