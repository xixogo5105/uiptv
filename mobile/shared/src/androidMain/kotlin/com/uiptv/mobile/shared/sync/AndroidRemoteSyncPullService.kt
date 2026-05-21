package com.uiptv.mobile.shared.sync

import com.uiptv.mobile.shared.db.AndroidSQLiteSnapshotSyncApplier
import com.uiptv.mobile.shared.settings.AndroidPreferencesRepository
import com.uiptv.mobile.shared.settings.MobileBackupArchive
import kotlinx.coroutines.delay
import java.io.File
import java.util.zip.ZipInputStream
import kotlin.random.Random

class AndroidRemoteSyncPullService(
    private val client: RemoteSyncClient,
    private val snapshotApplier: AndroidSQLiteSnapshotSyncApplier,
    private val preferences: AndroidPreferencesRepository,
    private val epochSeconds: () -> Long = { System.currentTimeMillis() / 1000L }
) {
    suspend fun checkConnection(host: String, port: Int): Boolean {
        preferences.saveRemoteEndpoint(host, port)
        return client.health(buildRemoteSyncBaseUrl(host, port))
    }

    suspend fun pullFromDesktop(
        host: String,
        port: Int,
        onProgress: (RemoteSyncProgress) -> Unit
    ): RemoteSyncPullResult {
        preferences.saveRemoteEndpoint(host, port)
        if (client is AndroidRemoteSyncClient) {
            val result = pullFromDesktopStreaming(
                baseUrl = buildRemoteSyncBaseUrl(host, port),
                verificationCode = createFourDigitVerificationCode { max -> Random.nextInt(max) },
                onProgress = onProgress
            )
            preferences.markRemoteSyncSucceeded(epochSeconds())
            preferences.setFirstRunCompleted(true)
            return result
        }

        val useCase = PullFromDesktopSyncUseCase(client, snapshotApplier)
        val result = useCase.pull(
            baseUrl = buildRemoteSyncBaseUrl(host, port),
            verificationCode = createFourDigitVerificationCode { max -> Random.nextInt(max) },
            onProgress = onProgress
        )
        preferences.markRemoteSyncSucceeded(epochSeconds())
        preferences.setFirstRunCompleted(true)
        return result
    }

    private suspend fun pullFromDesktopStreaming(
        baseUrl: String,
        verificationCode: String,
        onProgress: (RemoteSyncProgress) -> Unit
    ): RemoteSyncPullResult {
        val streamingClient = client as AndroidRemoteSyncClient
        onProgress(RemoteSyncProgress(RemoteSyncProgressStep.CONNECTING, verificationCode))
        check(streamingClient.health(baseUrl)) { "Remote sync server did not respond with OK status." }

        var sessionId = ""
        val transferFile = snapshotApplier.createTempTransferFile()
        var payloadFile: File? = null
        var snapshotFile: File? = null
        try {
            val session = streamingClient.request(
                baseUrl = baseUrl,
                request = RemoteSyncRequest(
                    direction = RemoteSyncDirection.IMPORT_FROM_REMOTE,
                    verificationCode = verificationCode,
                    options = androidPortablePullOptions()
                )
            )
            sessionId = session.sessionId
            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.WAITING_FOR_APPROVAL, verificationCode))

            val readySession = waitForDownload(baseUrl, sessionId)
            sessionId = readySession.sessionId.ifBlank { sessionId }

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.DOWNLOADING, verificationCode))
            streamingClient.downloadToFile(baseUrl, sessionId, transferFile)
            val transferOptions = readySession.options
            val payloadForSync = prepareInboundPayload(transferFile, verificationCode, sessionId, transferOptions)
            payloadFile = payloadForSync
            val snapshotForSync = extractTransferSnapshot(payloadForSync, transferOptions)
            snapshotFile = snapshotForSync

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.APPLYING_SYNC, verificationCode))
            val report = snapshotApplier.applyFile(snapshotForSync)

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.COMPLETING_REMOTE, verificationCode))
            streamingClient.complete(baseUrl, sessionId, success = true, message = "Remote database sync completed.")

            onProgress(RemoteSyncProgress(RemoteSyncProgressStep.FINISHED, verificationCode))
            return RemoteSyncPullResult(report, "Remote database sync completed.")
        } catch (ex: Throwable) {
            if (sessionId.isNotBlank()) {
                runCatching {
                    streamingClient.complete(
                        baseUrl,
                        sessionId,
                        success = false,
                        message = ex.message ?: "Remote sync failed."
                    )
                }
            }
            throw ex
        } finally {
            transferFile.delete()
            payloadFile?.takeUnless { it == transferFile }?.delete()
            snapshotFile?.takeUnless { it == payloadFile || it == transferFile }?.delete()
        }
    }

    private fun prepareInboundPayload(
        transferFile: File,
        verificationCode: String,
        sessionId: String,
        options: RemoteSyncOptions
    ): File {
        if (!options.encryptedTransfer) {
            return transferFile
        }
        val decryptedFile = snapshotApplier.createTempTransferFile(if (options.archiveTransfer) ".zip" else ".db")
        try {
            AndroidRemoteSyncTransferCipher.decrypt(transferFile, decryptedFile, verificationCode, sessionId)
            return decryptedFile
        } catch (ex: Throwable) {
            decryptedFile.delete()
            throw ex
        }
    }

    private fun extractTransferSnapshot(payloadFile: File, options: RemoteSyncOptions): File {
        if (!options.archiveTransfer) {
            return payloadFile
        }
        val snapshotFile = snapshotApplier.createTempSnapshotFile()
        try {
            var databaseFound = false
            ZipInputStream(payloadFile.inputStream().buffered()).use { zip ->
                while (true) {
                    val entry = zip.nextEntry ?: break
                    if (!entry.isDirectory && entry.name == MobileBackupArchive.DATABASE_ENTRY) {
                        snapshotFile.outputStream().use { output -> zip.copyTo(output) }
                        databaseFound = true
                    }
                    zip.closeEntry()
                }
            }
            require(databaseFound) { "Backup does not contain ${MobileBackupArchive.DATABASE_ENTRY}." }
            require(snapshotFile.length() > 0L) { "Backup database is empty." }
            return snapshotFile
        } catch (ex: Throwable) {
            snapshotFile.delete()
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
