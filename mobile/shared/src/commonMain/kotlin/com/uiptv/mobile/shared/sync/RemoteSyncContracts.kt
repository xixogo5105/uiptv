package com.uiptv.mobile.shared.sync

import com.uiptv.mobile.shared.db.DatabaseSyncReport
import kotlinx.coroutines.delay

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
    val syncExternalPlayerPaths: Boolean = false,
    val configurationProfile: ConfigurationSyncProfile = ConfigurationSyncProfile.DESKTOP_FULL,
    val archiveTransfer: Boolean = true,
    val encryptedTransfer: Boolean = true
)

enum class ConfigurationSyncProfile {
    DESKTOP_FULL,
    ANDROID_PORTABLE
}

fun androidPortablePullOptions(
    archiveTransfer: Boolean = true,
    encryptedTransfer: Boolean = true
): RemoteSyncOptions =
    RemoteSyncOptions(
        syncConfiguration = true,
        syncExternalPlayerPaths = false,
        configurationProfile = ConfigurationSyncProfile.ANDROID_PORTABLE,
        archiveTransfer = archiveTransfer,
        encryptedTransfer = encryptedTransfer
    )

fun androidFullClonePullOptions(
    archiveTransfer: Boolean = true,
    encryptedTransfer: Boolean = true
): RemoteSyncOptions =
    RemoteSyncOptions(
        syncConfiguration = true,
        syncExternalPlayerPaths = true,
        configurationProfile = ConfigurationSyncProfile.DESKTOP_FULL,
        archiveTransfer = archiveTransfer,
        encryptedTransfer = encryptedTransfer
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
    val requesterAddress: String = "",
    val options: RemoteSyncOptions = RemoteSyncOptions(),
    val message: String = ""
)

interface RemoteSyncClient {
    suspend fun health(baseUrl: String): Boolean

    suspend fun request(baseUrl: String, request: RemoteSyncRequest): RemoteSyncSessionState

    suspend fun status(baseUrl: String, sessionId: String): RemoteSyncSessionState

    suspend fun download(baseUrl: String, sessionId: String): ByteArray

    suspend fun complete(baseUrl: String, sessionId: String, success: Boolean, message: String)
}

enum class RemoteSyncProgressStep {
    CONNECTING,
    WAITING_FOR_APPROVAL,
    DOWNLOADING,
    APPLYING_SYNC,
    COMPLETING_REMOTE,
    FINISHED
}

data class RemoteSyncProgress(
    val step: RemoteSyncProgressStep,
    val verificationCode: String = "",
    val message: String = ""
)

data class RemoteSyncPullResult(
    val report: DatabaseSyncReport,
    val message: String
)

interface RemoteSnapshotApplier {
    suspend fun apply(snapshot: ByteArray): DatabaseSyncReport
}

class PullFromDesktopSyncUseCase(
    private val client: RemoteSyncClient,
    private val snapshotApplier: RemoteSnapshotApplier
) {
    suspend fun requestPull(baseUrl: String, verificationCode: String): RemoteSyncSessionState {
        return client.request(
            baseUrl = baseUrl,
            request = RemoteSyncRequest(
                direction = RemoteSyncDirection.IMPORT_FROM_REMOTE,
                verificationCode = verificationCode,
                options = androidFullClonePullOptions(archiveTransfer = false, encryptedTransfer = false)
            )
        )
    }

    suspend fun pull(
        baseUrl: String,
        verificationCode: String,
        onProgress: (RemoteSyncProgress) -> Unit = {}
    ): RemoteSyncPullResult {
        onProgress(RemoteSyncProgress(RemoteSyncProgressStep.CONNECTING, verificationCode))
        check(client.health(baseUrl)) { "Remote sync server did not respond with OK status." }

        var sessionId = ""
        try {
            val session = requestPull(baseUrl, verificationCode)
            sessionId = session.sessionId
            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.WAITING_FOR_APPROVAL, verificationCode))

            val readySession = waitForDownload(baseUrl, sessionId)
            sessionId = readySession.sessionId.ifBlank { sessionId }

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.DOWNLOADING, verificationCode))
            val snapshot = client.download(baseUrl, sessionId)

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.APPLYING_SYNC, verificationCode))
            val report = snapshotApplier.apply(snapshot)

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.COMPLETING_REMOTE, verificationCode))
            client.complete(baseUrl, sessionId, success = true, message = "Remote database sync completed.")

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.FINISHED, verificationCode))
            return RemoteSyncPullResult(report, "Remote database sync completed.")
        } catch (ex: Throwable) {
            if (sessionId.isNotBlank()) {
                runCatching {
                    client.complete(
                        baseUrl,
                        sessionId,
                        success = false,
                        message = ex.message ?: "Remote sync failed."
                    )
                }
            }
            throw ex
        }
    }

    private suspend fun waitForDownload(baseUrl: String, sessionId: String): RemoteSyncSessionState {
        repeat(180) {
            val state = client.status(baseUrl, sessionId)
            when (state.status) {
                RemoteSyncStatus.READY_FOR_DOWNLOAD,
                RemoteSyncStatus.COMPLETED -> return state
                RemoteSyncStatus.REJECTED,
                RemoteSyncStatus.FAILED,
                RemoteSyncStatus.EXPIRED -> error(state.message.ifBlank { "Remote sync was not approved." })
                RemoteSyncStatus.PENDING_APPROVAL,
                RemoteSyncStatus.APPROVED -> Unit
            }
            delay(1_000)
        }
        error("Timed out while waiting for desktop approval.")
    }
}

fun buildRemoteSyncBaseUrl(host: String, port: Int): String {
    require(port in 1..65_535) { "Port must be between 1 and 65535." }
    val normalizedHost = host.trim()
        .removePrefix("http://")
        .removePrefix("https://")
        .substringBefore("/")
        .removeSuffix(":")
        .withoutPort()
    require(normalizedHost.isNotBlank()) { "Host is required." }
    return "http://$normalizedHost:$port"
}

fun createFourDigitVerificationCode(randomInt: (Int) -> Int): String =
    randomInt(10_000).coerceIn(0, 9_999).toString().padStart(4, '0')

private fun String.withoutPort(): String {
    if (startsWith("[") && contains("]")) {
        return substringBefore("]") + "]"
    }
    return substringBefore(":")
}
