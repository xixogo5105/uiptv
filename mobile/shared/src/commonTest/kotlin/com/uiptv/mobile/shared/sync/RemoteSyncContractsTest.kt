package com.uiptv.mobile.shared.sync

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue
import com.uiptv.mobile.shared.db.DatabaseSyncReport
import com.uiptv.mobile.shared.db.TableSyncResult
import kotlinx.coroutines.runBlocking

class RemoteSyncContractsTest {
    @Test
    fun androidPullRequestUsesImportDirectionAndSkipsConfiguration() {
        val request = RemoteSyncRequest(verificationCode = "1234")

        assertEquals(RemoteSyncDirection.IMPORT_FROM_REMOTE, request.direction)
        assertEquals("UIPTV Android", request.requesterName)
        assertFalse(request.options.syncConfiguration)
        assertFalse(request.options.syncExternalPlayerPaths)
    }

    @Test
    fun baseUrlNormalizesHostInput() {
        assertEquals("http://192.168.1.2:8888", buildRemoteSyncBaseUrl("http://192.168.1.2/settings", 8888))
        assertEquals("http://192.168.1.2:8888", buildRemoteSyncBaseUrl("192.168.1.2:8888", 8888))
        assertEquals("http://desktop.local:8888", buildRemoteSyncBaseUrl("https://desktop.local:9999/config", 8888))
        assertFailsWith<IllegalArgumentException> { buildRemoteSyncBaseUrl("", 8888) }
        assertFailsWith<IllegalArgumentException> { buildRemoteSyncBaseUrl("desktop.local", 70_000) }
    }

    @Test
    fun verificationCodeIsAlwaysFourDigits() {
        assertEquals("0007", createFourDigitVerificationCode { 7 })
        assertTrue(createFourDigitVerificationCode { 12_345 }.length == 4)
    }

    @Test
    fun pullFromDesktopDownloadsAppliesAndCompletesSession() = runBlocking {
        val client = RecordingRemoteSyncClient()
        val applier = RecordingSnapshotApplier()
        val progressSteps = mutableListOf<RemoteSyncProgressStep>()

        val result = PullFromDesktopSyncUseCase(client, applier).pull(
            baseUrl = "http://desktop:8888",
            verificationCode = "1234"
        ) { progress ->
            progressSteps += progress.step
        }

        assertEquals(listOf("health", "request", "status", "download", "complete:true"), client.calls)
        assertEquals(RemoteSyncDirection.IMPORT_FROM_REMOTE, client.lastRequest?.direction)
        assertFalse(client.lastRequest?.options?.syncConfiguration ?: true)
        assertFalse(client.lastRequest?.options?.syncExternalPlayerPaths ?: true)
        assertEquals("snapshot", applier.lastSnapshot?.decodeToString())
        assertEquals(2, result.report.totalRowsSynced)
        assertEquals(RemoteSyncProgressStep.FINISHED, progressSteps.last())
    }

    @Test
    fun pullFromDesktopCompletesRemoteAsFailedWhenApplyFails() = runBlocking {
        val client = RecordingRemoteSyncClient()
        val applier = FailingSnapshotApplier()

        assertFailsWith<IllegalStateException> {
            PullFromDesktopSyncUseCase(client, applier).pull(
                baseUrl = "http://desktop:8888",
                verificationCode = "1234"
            )
        }

        assertEquals(listOf("health", "request", "status", "download", "complete:false"), client.calls)
    }

    private class RecordingRemoteSyncClient : RemoteSyncClient {
        val calls = mutableListOf<String>()
        var lastRequest: RemoteSyncRequest? = null

        override suspend fun health(baseUrl: String): Boolean {
            calls += "health"
            return true
        }

        override suspend fun request(baseUrl: String, request: RemoteSyncRequest): RemoteSyncSessionState {
            calls += "request"
            lastRequest = request
            return RemoteSyncSessionState(
                sessionId = "session-1",
                direction = request.direction,
                status = RemoteSyncStatus.PENDING_APPROVAL,
                verificationCode = request.verificationCode,
                requesterName = request.requesterName
            )
        }

        override suspend fun status(baseUrl: String, sessionId: String): RemoteSyncSessionState {
            calls += "status"
            return RemoteSyncSessionState(
                sessionId = sessionId,
                direction = RemoteSyncDirection.IMPORT_FROM_REMOTE,
                status = RemoteSyncStatus.READY_FOR_DOWNLOAD,
                verificationCode = "1234",
                requesterName = "UIPTV Android"
            )
        }

        override suspend fun download(baseUrl: String, sessionId: String): ByteArray {
            calls += "download"
            return "snapshot".encodeToByteArray()
        }

        override suspend fun complete(baseUrl: String, sessionId: String, success: Boolean, message: String) {
            calls += "complete:$success"
        }
    }

    private class RecordingSnapshotApplier : RemoteSnapshotApplier {
        var lastSnapshot: ByteArray? = null

        override suspend fun apply(snapshot: ByteArray): DatabaseSyncReport {
            lastSnapshot = snapshot
            return DatabaseSyncReport(listOf(TableSyncResult("Account", 2)))
        }
    }

    private class FailingSnapshotApplier : RemoteSnapshotApplier {
        override suspend fun apply(snapshot: ByteArray): DatabaseSyncReport {
            error("fixture apply failed")
        }
    }
}
