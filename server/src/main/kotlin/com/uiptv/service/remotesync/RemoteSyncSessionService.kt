package com.uiptv.service.remotesync

import com.uiptv.db.SqlConnectionRuntime
import com.uiptv.service.AppDataRefreshService
import com.uiptv.service.DatabaseSyncService
import com.uiptv.service.RuntimeServices
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.sql.SQLException
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicReference

class RemoteSyncSessionService internal constructor(
    private val snapshotService: DatabaseSnapshotService = DatabaseSnapshotService(),
    private val databaseSyncService: DatabaseSyncService = RuntimeServices.databaseSyncService,
    private val clock: Clock = Clock.systemDefaultZone(),
    approvalPrompt: RemoteSyncApprovalPrompt = DefaultRemoteSyncUiBridge(),
    notifier: RemoteSyncNotifier = DefaultRemoteSyncUiBridge()
) {
    private val sessions = ConcurrentHashMap<String, SessionState>()
    private val approvalPrompt = AtomicReference(approvalPrompt)
    private val notifier = AtomicReference(notifier)

    init {
        instanceRef.set(this)
    }

    companion object {
        private val APPROVAL_TTL: Duration = Duration.ofMinutes(2)
        private val TRANSFER_TTL: Duration = Duration.ofMinutes(10)
        private const val REMOTE_SYNC_COMPLETED_MESSAGE = "Remote database sync completed."
        private val instanceRef = AtomicReference<RemoteSyncSessionService?>()

        @JvmStatic
        @JvmOverloads
        internal fun runtimeInstance(
            snapshotService: DatabaseSnapshotService = DatabaseSnapshotService(),
            databaseSyncService: DatabaseSyncService = RuntimeServices.databaseSyncService,
            clock: Clock = Clock.systemDefaultZone(),
            approvalPrompt: RemoteSyncApprovalPrompt = DefaultRemoteSyncUiBridge(),
            notifier: RemoteSyncNotifier = DefaultRemoteSyncUiBridge()
        ): RemoteSyncSessionService =
            instanceRef.updateAndGet { current ->
                current ?: RemoteSyncSessionService(snapshotService, databaseSyncService, clock, approvalPrompt, notifier)
            }!!

    }

    fun createSession(request: RemoteSyncRequest, requesterAddress: String?): RemoteSyncSessionState {
        validateRequest(request)
        val session = SessionState(
            sessionId = UUID.randomUUID().toString(),
            direction = request.direction,
            verificationCode = request.verificationCode,
            requesterName = blankToFallback(request.requesterName, requesterAddress),
            requesterAddress = blankToFallback(requesterAddress, "unknown"),
            options = request.options,
            expiresAt = clock.instant().plus(APPROVAL_TTL)
        )
        sessions[session.sessionId] = session
        approvalPrompt.get().requestApproval(session.toApprovalRequest()) { approved -> applyDecision(session.sessionId, approved) }
        return session.toPublicState()
    }

    fun getSessionState(sessionId: String?): RemoteSyncSessionState {
        val session = requireSession(sessionId)
        synchronized(session) {
            expireIfNeeded(session)
            return session.toPublicState()
        }
    }

    @Throws(IOException::class, SQLException::class)
    fun acceptUpload(sessionId: String?, requestBody: InputStream): RemoteSyncExecutionResult {
        val session = requireSession(sessionId)
        val uploadedSnapshot: Path
        synchronized(session) {
            expireIfNeeded(session)
            session.ensureStatus(RemoteSyncDirection.EXPORT_TO_REMOTE, RemoteSyncStatus.APPROVED)
            uploadedSnapshot = SecureTempFileSupport.createTempFile("uiptv-remote-upload-", ".db")
            requestBody.use { input -> Files.copy(input, uploadedSnapshot, StandardCopyOption.REPLACE_EXISTING) }
        }
        try {
            val report = databaseSyncService.syncDatabasesWithReport(
                uploadedSnapshot.toAbsolutePath().toString(),
                SqlConnectionRuntime.getDatabasePath(),
                session.options.syncConfiguration,
                session.options.syncExternalPlayerPaths,
                null
            )
            AppDataRefreshService.refreshAfterDatabaseChange()
            synchronized(session) { session.complete(REMOTE_SYNC_COMPLETED_MESSAGE) }
            notifier.get().showInfo("remoteSyncRemoteCompletedMessage")
            return RemoteSyncExecutionResult(report, REMOTE_SYNC_COMPLETED_MESSAGE)
        } catch (ex: SQLException) {
            synchronized(session) { session.fail(ex.message) }
            notifier.get().showError("remoteSyncRemoteFailedMessage")
            throw ex
        } finally {
            deleteIfExists(uploadedSnapshot)
        }
    }

    fun getDownloadSnapshot(sessionId: String?): Path? {
        val session = requireSession(sessionId)
        synchronized(session) {
            expireIfNeeded(session)
            session.ensureStatus(RemoteSyncDirection.IMPORT_FROM_REMOTE, RemoteSyncStatus.READY_FOR_DOWNLOAD)
            return session.snapshotPath
        }
    }

    fun completeImport(sessionId: String?, success: Boolean, message: String?) {
        val session = requireSession(sessionId)
        synchronized(session) {
            expireIfNeeded(session)
            if (success) {
                AppDataRefreshService.refreshAfterDatabaseChange()
                session.complete(blankToFallback(message, REMOTE_SYNC_COMPLETED_MESSAGE))
                notifier.get().showInfo("remoteSyncRemoteCompletedMessage")
            } else {
                session.fail(blankToFallback(message, "Remote database sync failed."))
                notifier.get().showError("remoteSyncRemoteFailedMessage")
            }
            cleanupSnapshot(session)
        }
    }

    fun setApprovalPrompt(approvalPrompt: RemoteSyncApprovalPrompt) {
        this.approvalPrompt.set(approvalPrompt)
    }

    fun setNotifier(notifier: RemoteSyncNotifier) {
        this.notifier.set(notifier)
    }

    fun clearSessions() {
        sessions.values.forEach(::cleanupSnapshot)
        sessions.clear()
    }

    private fun applyDecision(sessionId: String, approved: Boolean) {
        val session = sessions[sessionId] ?: return
        synchronized(session) {
            expireIfNeeded(session)
            if (session.status != RemoteSyncStatus.PENDING_APPROVAL) {
                return
            }
            if (!approved) {
                session.reject()
                return
            }
            try {
                if (session.direction == RemoteSyncDirection.IMPORT_FROM_REMOTE) {
                    session.snapshotPath = snapshotService.createSnapshot(SqlConnectionRuntime.getDatabasePath())
                    session.status = RemoteSyncStatus.READY_FOR_DOWNLOAD
                    session.message = "Approved. Snapshot ready."
                } else {
                    session.status = RemoteSyncStatus.APPROVED
                    session.message = "Approved. Ready for upload."
                }
                session.expiresAt = clock.instant().plus(TRANSFER_TTL)
            } catch (ex: IOException) {
                session.fail(ex.message)
            } catch (ex: SQLException) {
                session.fail(ex.message)
            }
        }
    }

    private fun validateRequest(request: RemoteSyncRequest?) {
        requireNotNull(request) { "request" }
        requireNotNull(request.direction) { "direction is required" }
        requireNotNull(request.options) { "options are required" }
        val code = blankToFallback(request.verificationCode, "")
        require(code.matches(Regex("\\d{4}"))) { "verificationCode must be a four digit code" }
    }

    private fun requireSession(sessionId: String?): SessionState {
        val normalizedSessionId = blankToFallback(sessionId, "")
        return sessions[normalizedSessionId] ?: throw IllegalArgumentException("Remote sync session not found")
    }

    private fun expireIfNeeded(session: SessionState) {
        if (session.isTerminal() || !clock.instant().isAfter(session.expiresAt)) {
            return
        }
        session.status = RemoteSyncStatus.EXPIRED
        session.message = "Remote sync request expired."
        cleanupSnapshot(session)
    }

    private fun cleanupSnapshot(session: SessionState) {
        deleteIfExists(session.snapshotPath)
        session.snapshotPath = null
    }

    private fun deleteIfExists(path: Path?) {
        if (path == null) {
            return
        }
        try {
            Files.deleteIfExists(path)
        } catch (_: IOException) {
            // Best effort cleanup.
        }
    }

    private fun blankToFallback(value: String?, fallback: String?): String = if (value.isNullOrBlank()) fallback.orEmpty() else value

    private data class SessionState(
        val sessionId: String,
        val direction: RemoteSyncDirection,
        val verificationCode: String,
        val requesterName: String,
        val requesterAddress: String,
        val options: RemoteSyncOptions,
        var expiresAt: Instant,
        var status: RemoteSyncStatus = RemoteSyncStatus.PENDING_APPROVAL,
        var message: String = "Awaiting approval.",
        var snapshotPath: Path? = null
    ) {
        fun toPublicState(): RemoteSyncSessionState =
            RemoteSyncSessionState(sessionId, direction, status, verificationCode, requesterName, requesterAddress, options, message)

        fun toApprovalRequest(): RemoteSyncApprovalRequest =
            RemoteSyncApprovalRequest(sessionId, direction, verificationCode, requesterName, requesterAddress, options)

        fun ensureStatus(expectedDirection: RemoteSyncDirection, expectedStatus: RemoteSyncStatus) {
            check(direction == expectedDirection) { "Remote sync session direction mismatch" }
            check(status == expectedStatus) { "Remote sync session is not ready" }
        }

        fun complete(message: String) {
            status = RemoteSyncStatus.COMPLETED
            this.message = message
        }

        fun reject() {
            status = RemoteSyncStatus.REJECTED
            message = "Remote sync request rejected."
        }

        fun fail(message: String?) {
            status = RemoteSyncStatus.FAILED
            this.message = if (message.isNullOrBlank()) "Remote sync failed." else message
        }

        fun isTerminal(): Boolean =
            status == RemoteSyncStatus.REJECTED ||
                status == RemoteSyncStatus.COMPLETED ||
                status == RemoteSyncStatus.FAILED ||
                status == RemoteSyncStatus.EXPIRED
    }
}
