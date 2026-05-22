package com.uiptv.mobile.shared.sync

import com.uiptv.mobile.shared.db.DatabaseSyncReport
import com.uiptv.mobile.shared.db.TableSyncResult
import kotlinx.coroutines.runBlocking
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertFailsWith
import kotlin.test.assertTrue

class RemoteSyncContractsTest {
    @Test
    fun androidFullClonePullOptionsSyncAllConfiguration() {
        val options = androidFullClonePullOptions()

        assertTrue(options.syncConfiguration)
        assertTrue(options.syncExternalPlayerPaths)
        assertEquals(ConfigurationSyncProfile.DESKTOP_FULL, options.configurationProfile)
        assertTrue(options.archiveTransfer)
        assertTrue(options.encryptedTransfer)
    }

    @Test
    fun buildRemoteSyncBaseUrlNormalizesHosts() {
        assertEquals("http://192.168.1.20:8888", buildRemoteSyncBaseUrl(" 192.168.1.20 ", 8888))
        assertEquals("http://desktop.local:9000", buildRemoteSyncBaseUrl("https://desktop.local/path", 9000))
        assertEquals("http://desktop.local:9001", buildRemoteSyncBaseUrl("http://desktop.local:1234", 9001))
        assertEquals("http://[::1]:9002", buildRemoteSyncBaseUrl("[::1]:1234", 9002))
    }

    @Test
    fun buildRemoteSyncBaseUrlRejectsInvalidInput() {
        assertFailsWith<IllegalArgumentException> { buildRemoteSyncBaseUrl("", 8888) }
        assertFailsWith<IllegalArgumentException> { buildRemoteSyncBaseUrl("desktop", 0) }
        assertFailsWith<IllegalArgumentException> { buildRemoteSyncBaseUrl("desktop", 65_536) }
    }

    @Test
    fun createFourDigitVerificationCodePadsAndBoundsRandomValue() {
        assertEquals("0000", createFourDigitVerificationCode { -1 })
        assertEquals("0007", createFourDigitVerificationCode { 7 })
        assertEquals("9999", createFourDigitVerificationCode { 15_000 })
    }

    @Test
    fun remoteSyncPullResultCarriesReportAndMessage() {
        val report = DatabaseSyncReport(listOf(TableSyncResult("Account", 2)))
        val result = RemoteSyncPullResult(report, "done")

        assertEquals(2, result.report.totalRowsSynced)
        assertEquals("done", result.message)
    }

    @Test
    fun requestPullUsesAndroidFullCloneImportRequest() = runBlocking {
        val client = RecordingRemoteSyncClient()
        val useCase = PullFromDesktopSyncUseCase(client, NoopSnapshotApplier())

        val state = useCase.requestPull("http://desktop:8888", "1234")

        assertEquals("session-1", state.sessionId)
        assertEquals("http://desktop:8888", client.requestBaseUrl)
        val request = requireNotNull(client.lastRequest)
        assertEquals(RemoteSyncDirection.IMPORT_FROM_REMOTE, request.direction)
        assertEquals("1234", request.verificationCode)
        assertEquals("UIPTV Android", request.requesterName)
        assertEquals(ConfigurationSyncProfile.DESKTOP_FULL, request.options.configurationProfile)
        assertTrue(request.options.syncConfiguration)
        assertTrue(request.options.syncExternalPlayerPaths)
        assertFalse(request.options.archiveTransfer)
        assertFalse(request.options.encryptedTransfer)
    }

    @Test
    fun pullCompletesRemoteWhenSessionIsReady() = runBlocking {
        val client = RecordingRemoteSyncClient(statuses = listOf(RemoteSyncStatus.READY_FOR_DOWNLOAD))
        val applier = NoopSnapshotApplier(DatabaseSyncReport(listOf(TableSyncResult("Account", 4))))
        val useCase = PullFromDesktopSyncUseCase(client, applier)
        val progress = mutableListOf<RemoteSyncProgress>()

        val result = useCase.pull("http://desktop:8888", "5678", progress::add)

        assertEquals(4, result.report.totalRowsSynced)
        assertEquals("Remote database sync completed.", result.message)
        assertEquals(byteArrayOf(1, 2, 3).toList(), applier.appliedSnapshot.toList())
        assertEquals(
            listOf(
                RemoteSyncProgressStep.CONNECTING,
                RemoteSyncProgressStep.WAITING_FOR_APPROVAL,
                RemoteSyncProgressStep.DOWNLOADING,
                RemoteSyncProgressStep.APPLYING_SYNC,
                RemoteSyncProgressStep.COMPLETING_REMOTE,
                RemoteSyncProgressStep.FINISHED
            ),
            progress.map { it.step }
        )
        assertEquals(true, client.completedSuccess)
        assertEquals("Remote database sync completed.", client.completedMessage)
    }

    @Test
    fun pullMarksRemoteFailedWhenApplyFails() = runBlocking {
        val client = RecordingRemoteSyncClient(statuses = listOf(RemoteSyncStatus.COMPLETED))
        val useCase = PullFromDesktopSyncUseCase(client, FailingSnapshotApplier())

        assertFailsWith<IllegalStateException> {
            useCase.pull("http://desktop:8888", "0001")
        }

        assertEquals(false, client.completedSuccess)
        assertEquals("apply failed", client.completedMessage)
    }

    @Test
    fun pullRejectsUnhealthyServerBeforeCreatingSession() = runBlocking {
        val client = RecordingRemoteSyncClient(healthy = false)
        val useCase = PullFromDesktopSyncUseCase(client, NoopSnapshotApplier())

        assertFailsWith<IllegalStateException> {
            useCase.pull("http://desktop:8888", "0002")
        }

        assertEquals(null, client.lastRequest)
    }

    private class RecordingRemoteSyncClient(
        private val healthy: Boolean = true,
        statuses: List<RemoteSyncStatus> = listOf(RemoteSyncStatus.READY_FOR_DOWNLOAD)
    ) : RemoteSyncClient {
        private val remainingStatuses = ArrayDeque(statuses)
        var requestBaseUrl: String = ""
            private set
        var lastRequest: RemoteSyncRequest? = null
            private set
        var completedSuccess: Boolean? = null
            private set
        var completedMessage: String = ""
            private set

        override suspend fun health(baseUrl: String): Boolean = healthy

        override suspend fun request(baseUrl: String, request: RemoteSyncRequest): RemoteSyncSessionState {
            requestBaseUrl = baseUrl
            lastRequest = request
            return RemoteSyncSessionState(
                sessionId = "session-1",
                direction = request.direction,
                status = RemoteSyncStatus.PENDING_APPROVAL,
                verificationCode = request.verificationCode,
                requesterName = request.requesterName,
                options = request.options
            )
        }

        override suspend fun status(baseUrl: String, sessionId: String): RemoteSyncSessionState =
            RemoteSyncSessionState(
                sessionId = sessionId,
                direction = RemoteSyncDirection.IMPORT_FROM_REMOTE,
                status = remainingStatuses.removeFirstOrNull() ?: RemoteSyncStatus.READY_FOR_DOWNLOAD,
                verificationCode = lastRequest?.verificationCode.orEmpty(),
                requesterName = "UIPTV Android"
            )

        override suspend fun download(baseUrl: String, sessionId: String): ByteArray =
            byteArrayOf(1, 2, 3)

        override suspend fun complete(baseUrl: String, sessionId: String, success: Boolean, message: String) {
            completedSuccess = success
            completedMessage = message
        }
    }

    private class NoopSnapshotApplier(
        private val report: DatabaseSyncReport = DatabaseSyncReport(emptyList())
    ) : RemoteSnapshotApplier {
        var appliedSnapshot: ByteArray = byteArrayOf()
            private set

        override suspend fun apply(snapshot: ByteArray): DatabaseSyncReport {
            appliedSnapshot = snapshot
            return report
        }
    }

    private class FailingSnapshotApplier : RemoteSnapshotApplier {
        override suspend fun apply(snapshot: ByteArray): DatabaseSyncReport {
            error("apply failed")
        }
    }
}
