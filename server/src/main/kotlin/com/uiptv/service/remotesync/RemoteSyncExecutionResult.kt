package com.uiptv.service.remotesync

import com.uiptv.service.DatabaseSyncService

data class RemoteSyncExecutionResult(
    val report: DatabaseSyncService.DatabaseSyncReport?,
    val message: String?
) {
    fun report(): DatabaseSyncService.DatabaseSyncReport? = report
    fun message(): String? = message
}
