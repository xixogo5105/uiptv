package com.uiptv.server.api.routes

import com.uiptv.server.configureServerApplication
import com.uiptv.service.remotesync.RemoteSyncDirection
import com.uiptv.service.remotesync.RemoteSyncExecutionResult
import com.uiptv.service.remotesync.RemoteSyncOptions
import com.uiptv.service.remotesync.RemoteSyncSessionService
import com.uiptv.service.remotesync.RemoteSyncSessionState
import com.uiptv.service.remotesync.RemoteSyncStatus
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.koin.dsl.module
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyString
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doNothing
import org.mockito.Mockito.mock
import org.mockito.Mockito.verify
import org.mockito.Mockito.`when`
import java.io.InputStream
import java.nio.file.Files

class RemoteSyncApiRoutesTest {
    @Test
    fun `remote sync routes are covered through ktor http endpoints`() = testApplication {
        val remoteSync = mock(RemoteSyncSessionService::class.java)
        val state = RemoteSyncSessionState(
            sessionId = "session-1",
            direction = RemoteSyncDirection.EXPORT_TO_REMOTE,
            status = RemoteSyncStatus.APPROVED,
            verificationCode = "1234",
            requesterName = "Desktop",
            requesterAddress = "127.0.0.1",
            options = RemoteSyncOptions(syncConfiguration = true, syncExternalPlayerPaths = false),
            message = "Approved"
        )
        val snapshot = Files.createTempFile("remote-sync-test-", ".db")
        Files.writeString(snapshot, "snapshot")

        `when`(remoteSync.createSession(anyValue(), anyStringValue())).thenReturn(state)
        `when`(remoteSync.getSessionState("session-1")).thenReturn(state)
        `when`(remoteSync.acceptUpload(eq("session-1"), anyValue()))
            .thenReturn(RemoteSyncExecutionResult(report = null, message = "Uploaded"))
        `when`(remoteSync.getDownloadSnapshot("session-1")).thenReturn(snapshot)
        doNothing().`when`(remoteSync).completeImport("session-1", true, "done")

        application {
            configureServerApplication(
                listOf(
                    module {
                        single<RemoteSyncSessionService> { remoteSync }
                    }
                )
            )
        }

        val health = client.get("/remote-sync/health")
        assertEquals(HttpStatusCode.OK, health.status)
        assertTrue(health.bodyAsText().contains("\"status\":\"ok\""))

        val request = client.post("/remote-sync/request") {
            contentType(ContentType.Application.Json)
            setBody(
                """
                {"direction":"EXPORT_TO_REMOTE","verificationCode":"1234","requesterName":"Desktop","syncConfiguration":true,"syncExternalPlayerPaths":false}
                """.trimIndent()
            )
        }
        assertEquals(HttpStatusCode.OK, request.status)
        assertTrue(request.bodyAsText().contains("\"sessionId\":\"session-1\""))

        val status = client.get("/remote-sync/status?sessionId=session-1")
        assertEquals(HttpStatusCode.OK, status.status)
        assertTrue(status.bodyAsText().contains("\"status\":\"APPROVED\""))

        val upload = client.put("/remote-sync/upload?sessionId=session-1") {
            setBody("db-bytes")
        }
        assertEquals(HttpStatusCode.OK, upload.status)
        assertTrue(upload.bodyAsText().contains("\"message\":\"Uploaded\""))

        val download = client.get("/remote-sync/download?sessionId=session-1")
        assertEquals(HttpStatusCode.OK, download.status)
        assertEquals("snapshot", download.bodyAsText())

        val complete = client.post("/remote-sync/complete") {
            contentType(ContentType.Application.Json)
            setBody("""{"sessionId":"session-1","success":true,"message":"done"}""")
        }
        assertEquals(HttpStatusCode.OK, complete.status)
        assertTrue(complete.bodyAsText().contains("\"status\":\"ok\""))

        verify(remoteSync).createSession(anyValue(), anyStringValue())
        verify(remoteSync).getSessionState("session-1")
        verify(remoteSync).acceptUpload(eq("session-1"), anyValue())
        verify(remoteSync).getDownloadSnapshot("session-1")
        verify(remoteSync).completeImport("session-1", true, "done")

        Files.deleteIfExists(snapshot)
    }
}

private fun anyStringValue(): String {
    anyString()
    return ""
}

@Suppress("UNCHECKED_CAST")
private fun <T> anyValue(): T {
    any<T>()
    return null as T
}
