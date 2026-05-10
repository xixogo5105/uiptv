package com.uiptv.service.remotesync

data class RemoteSyncSessionState(
    val sessionId: String,
    val direction: RemoteSyncDirection,
    val status: RemoteSyncStatus,
    val verificationCode: String,
    val requesterName: String,
    val requesterAddress: String,
    val options: RemoteSyncOptions,
    val message: String?
) {
    fun sessionId(): String = sessionId
    fun direction(): RemoteSyncDirection = direction
    fun status(): RemoteSyncStatus = status
    fun verificationCode(): String = verificationCode
    fun requesterName(): String = requesterName
    fun requesterAddress(): String = requesterAddress
    fun options(): RemoteSyncOptions = options
    fun message(): String? = message
}
