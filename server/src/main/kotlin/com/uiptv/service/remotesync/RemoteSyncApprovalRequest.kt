package com.uiptv.service.remotesync

data class RemoteSyncApprovalRequest(
    val sessionId: String,
    val direction: RemoteSyncDirection,
    val verificationCode: String,
    val requesterName: String,
    val requesterAddress: String,
    val options: RemoteSyncOptions
) {
    fun sessionId(): String = sessionId
    fun direction(): RemoteSyncDirection = direction
    fun verificationCode(): String = verificationCode
    fun requesterName(): String = requesterName
    fun requesterAddress(): String = requesterAddress
    fun options(): RemoteSyncOptions = options
}
