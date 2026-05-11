package com.uiptv.service.remotesync

import com.uiptv.db.SqlConnectionRuntime
import com.uiptv.service.DatabaseSyncService
import com.uiptv.service.RuntimeServices
import java.io.IOException
import java.net.InetAddress
import java.nio.file.Files
import java.nio.file.Path
import java.sql.SQLException
import java.time.Duration

class RemoteSyncClientService(
    private val httpClient: RemoteSyncHttpClient = RemoteSyncHttpClient(),
    private val snapshotService: DatabaseSnapshotService = DatabaseSnapshotService(),
    private val databaseSyncService: DatabaseSyncService = RuntimeServices.databaseSyncService
) {
    @Throws(IOException::class)
    fun checkConnection(host: String, port: Int) {
        httpClient.checkHealth(buildBaseUrl(host, port))
    }

    @Throws(IOException::class, SQLException::class)
    fun exportToRemote(
        host: String,
        port: Int,
        options: RemoteSyncOptions,
        progressListener: RemoteSyncProgressListener?
    ): RemoteSyncExecutionResult {
        val baseUrl = buildBaseUrl(host, port)
        notifyProgress(progressListener, RemoteSyncProgressStep.CONNECTING, null)
        httpClient.checkHealth(baseUrl)

        val verificationCode = VerificationCodeGenerator.createFourDigitCode()
        val session = httpClient.createSession(baseUrl, buildRequest(RemoteSyncDirection.EXPORT_TO_REMOTE, verificationCode, options))
        awaitReadyState(baseUrl, session.sessionId, RemoteSyncStatus.APPROVED, verificationCode, progressListener)

        notifyProgress(progressListener, RemoteSyncProgressStep.CREATING_SNAPSHOT, null)
        val snapshotPath = snapshotService.createSnapshot(SqlConnectionRuntime.getDatabasePath())
        try {
            notifyProgress(progressListener, RemoteSyncProgressStep.UPLOADING, null)
            val result = httpClient.uploadSnapshot(baseUrl, session.sessionId, snapshotPath)
            notifyProgress(progressListener, RemoteSyncProgressStep.FINISHED, null)
            return result
        } finally {
            Files.deleteIfExists(snapshotPath)
        }
    }

    @Throws(IOException::class, SQLException::class)
    fun importFromRemote(
        host: String,
        port: Int,
        options: RemoteSyncOptions,
        progressListener: RemoteSyncProgressListener?
    ): RemoteSyncExecutionResult {
        val baseUrl = buildBaseUrl(host, port)
        notifyProgress(progressListener, RemoteSyncProgressStep.CONNECTING, null)
        httpClient.checkHealth(baseUrl)

        val verificationCode = VerificationCodeGenerator.createFourDigitCode()
        val session = httpClient.createSession(baseUrl, buildRequest(RemoteSyncDirection.IMPORT_FROM_REMOTE, verificationCode, options))
        awaitReadyState(baseUrl, session.sessionId, RemoteSyncStatus.READY_FOR_DOWNLOAD, verificationCode, progressListener)

        notifyProgress(progressListener, RemoteSyncProgressStep.DOWNLOADING, null)
        val downloadedSnapshot = httpClient.downloadSnapshot(baseUrl, session.sessionId)
        try {
            notifyProgress(progressListener, RemoteSyncProgressStep.APPLYING_SYNC, null)
            val report = databaseSyncService.syncDatabasesWithReport(
                downloadedSnapshot.toAbsolutePath().toString(),
                SqlConnectionRuntime.getDatabasePath(),
                options.syncConfiguration,
                options.syncExternalPlayerPaths,
                null
            )
            notifyProgress(progressListener, RemoteSyncProgressStep.COMPLETING_REMOTE, null)
            httpClient.completeSession(baseUrl, session.sessionId, true, "Remote database sync completed.")
            notifyProgress(progressListener, RemoteSyncProgressStep.FINISHED, null)
            return RemoteSyncExecutionResult(report, "Remote database sync completed.")
        } catch (ex: IOException) {
            httpClient.completeSession(baseUrl, session.sessionId, false, ex.message)
            throw ex
        } catch (ex: SQLException) {
            httpClient.completeSession(baseUrl, session.sessionId, false, ex.message)
            throw ex
        } finally {
            Files.deleteIfExists(downloadedSnapshot)
        }
    }

    private fun buildRequest(direction: RemoteSyncDirection, verificationCode: String, options: RemoteSyncOptions): RemoteSyncRequest =
        RemoteSyncRequest(direction, verificationCode, resolveRequesterName(), options)

    @Throws(IOException::class)
    private fun awaitReadyState(
        baseUrl: String,
        sessionId: String,
        expectedStatus: RemoteSyncStatus,
        verificationCode: String,
        progressListener: RemoteSyncProgressListener?
    ) {
        val deadline = System.nanoTime() + STATUS_TIMEOUT.toNanos()
        notifyProgress(progressListener, RemoteSyncProgressStep.WAITING_FOR_APPROVAL, verificationCode)
        while (System.nanoTime() < deadline) {
            val state = httpClient.getSessionState(baseUrl, sessionId)
            if (state.status == expectedStatus || state.status == RemoteSyncStatus.COMPLETED) {
                return
            }
            if (state.status == RemoteSyncStatus.REJECTED ||
                state.status == RemoteSyncStatus.FAILED ||
                state.status == RemoteSyncStatus.EXPIRED
            ) {
                throw IOException(state.message)
            }
            sleepBeforeNextPoll()
        }
        throw IOException("Timed out while waiting for remote sync approval")
    }

    @Throws(IOException::class)
    private fun sleepBeforeNextPoll() {
        try {
            Thread.sleep(POLL_DELAY.toMillis())
        } catch (interruptedException: InterruptedException) {
            Thread.currentThread().interrupt()
            throw IOException("Interrupted while waiting for remote sync approval", interruptedException)
        }
    }

    private fun buildBaseUrl(host: String?, port: Int): String = "http://${normalizeHost(host)}:$port"

    private fun normalizeHost(host: String?): String {
        var normalized = host?.trim() ?: ""
        normalized = normalized.replaceFirst(Regex("^https?://"), "")
        val slashIndex = normalized.indexOf('/')
        return if (slashIndex >= 0) normalized.substring(0, slashIndex) else normalized
    }

    private fun resolveRequesterName(): String =
        try {
            InetAddress.getLocalHost().hostName
        } catch (_: Exception) {
            "UIPTV"
        }

    private fun notifyProgress(progressListener: RemoteSyncProgressListener?, step: RemoteSyncProgressStep, detail: String?) {
        progressListener?.onProgress(step, detail ?: "")
    }

    companion object {
        @JvmField
        val INSTANCE: RemoteSyncClientService = RemoteSyncClientService()
        val POLL_DELAY: Duration = Duration.ofSeconds(1)
        val STATUS_TIMEOUT: Duration = Duration.ofMinutes(3)
    }
}
