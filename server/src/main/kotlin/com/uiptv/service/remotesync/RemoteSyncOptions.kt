package com.uiptv.service.remotesync

data class RemoteSyncOptions(
    val syncConfiguration: Boolean,
    val syncExternalPlayerPaths: Boolean
) {
    fun syncConfiguration(): Boolean = syncConfiguration
    fun syncExternalPlayerPaths(): Boolean = syncExternalPlayerPaths
}
