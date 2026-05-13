package com.uiptv.mobile.shared.sync

enum class RemoteSyncDirection {
    EXPORT_TO_REMOTE,
    IMPORT_FROM_REMOTE
}

enum class RemoteSyncStatus {
    PENDING_APPROVAL,
    APPROVED,
    READY_FOR_DOWNLOAD,
    REJECTED,
    COMPLETED,
    FAILED,
    EXPIRED
}

data class RemoteSyncOptions(
    val syncConfiguration: Boolean = false,
    val syncExternalPlayerPaths: Boolean = false
)

data class RemoteSyncRequest(
    val direction: RemoteSyncDirection = RemoteSyncDirection.IMPORT_FROM_REMOTE,
    val verificationCode: String,
    val requesterName: String = "UIPTV Android",
    val options: RemoteSyncOptions = RemoteSyncOptions()
)

data class RemoteSyncSessionState(
    val sessionId: String,
    val direction: RemoteSyncDirection,
    val status: RemoteSyncStatus,
    val verificationCode: String,
    val requesterName: String,
    val message: String = ""
)

interface RemoteSyncClient {
    suspend fun health(baseUrl: String): Boolean

    suspend fun request(baseUrl: String, request: RemoteSyncRequest): RemoteSyncSessionState

    suspend fun status(baseUrl: String, verificationCode: String): RemoteSyncSessionState

    suspend fun download(baseUrl: String, verificationCode: String): ByteArray

    suspend fun complete(baseUrl: String, verificationCode: String)
}

class PullFromDesktopSyncUseCase(private val client: RemoteSyncClient) {
    suspend fun requestPull(baseUrl: String, verificationCode: String): RemoteSyncSessionState {
        return client.request(
            baseUrl = baseUrl,
            request = RemoteSyncRequest(
                direction = RemoteSyncDirection.IMPORT_FROM_REMOTE,
                verificationCode = verificationCode,
                options = RemoteSyncOptions(
                    syncConfiguration = false,
                    syncExternalPlayerPaths = false
                )
            )
        )
    }
}
