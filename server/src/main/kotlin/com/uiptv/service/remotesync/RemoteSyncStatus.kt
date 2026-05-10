package com.uiptv.service.remotesync

enum class RemoteSyncStatus {
    PENDING_APPROVAL,
    APPROVED,
    READY_FOR_DOWNLOAD,
    REJECTED,
    COMPLETED,
    FAILED,
    EXPIRED
}
