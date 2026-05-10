package com.uiptv.service.remotesync

enum class RemoteSyncDirection {
    EXPORT_TO_REMOTE,
    IMPORT_FROM_REMOTE;

    fun uploadsToRemote(): Boolean = this == EXPORT_TO_REMOTE
}
