package com.uiptv.service.remotesync

data class RemoteSyncRequest(
    val direction: RemoteSyncDirection,
    val verificationCode: String,
    val requesterName: String,
    val options: RemoteSyncOptions
) {
    fun direction(): RemoteSyncDirection = direction
    fun verificationCode(): String = verificationCode
    fun requesterName(): String = requesterName
    fun options(): RemoteSyncOptions = options
}
