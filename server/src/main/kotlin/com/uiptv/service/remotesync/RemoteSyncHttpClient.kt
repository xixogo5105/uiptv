package com.uiptv.service.remotesync

import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.classic.methods.HttpPut
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ClassicHttpResponse
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.ParseException
import org.apache.hc.core5.http.io.entity.ByteArrayEntity
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.io.entity.FileEntity
import com.uiptv.util.json.KJsonObject
import java.io.IOException
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption

open class RemoteSyncHttpClient {
    @Throws(IOException::class)
    open fun checkHealth(baseUrl: String) {
        val json = executeJson(HttpGet("$baseUrl/remote-sync/health"))
        if (!"ok".equals(json.optString("status", ""), true)) {
            throw IOException("Remote sync server did not respond with OK status")
        }
    }

    @Throws(IOException::class)
    open fun createSession(baseUrl: String, request: RemoteSyncRequest): RemoteSyncSessionState {
        val post = HttpPost("$baseUrl/remote-sync/request")
        post.setHeader("Content-Type", "application/json")
        post.entity = ByteArrayEntity(RemoteSyncJson.toJson(request).toString().toByteArray(), ContentType.APPLICATION_JSON)
        return RemoteSyncJson.toSessionState(executeJson(post))
    }

    @Throws(IOException::class)
    open fun getSessionState(baseUrl: String, sessionId: String): RemoteSyncSessionState =
        RemoteSyncJson.toSessionState(executeJson(HttpGet("$baseUrl/remote-sync/status?sessionId=$sessionId")))

    @Throws(IOException::class)
    open fun uploadSnapshot(baseUrl: String, sessionId: String, snapshotPath: Path): RemoteSyncExecutionResult {
        val put = HttpPut("$baseUrl/remote-sync/upload?sessionId=$sessionId")
        put.entity = FileEntity(snapshotPath.toFile(), ContentType.APPLICATION_OCTET_STREAM)
        return RemoteSyncJson.toExecutionResult(executeJson(put))
    }

    @Throws(IOException::class)
    open fun downloadSnapshot(baseUrl: String, sessionId: String): Path {
        val get = HttpGet("$baseUrl/remote-sync/download?sessionId=$sessionId")
        HttpClients.createDefault().use { client ->
            return client.execute(get) { response ->
                if (response.code >= 300) {
                    throw IOException(readEntityText(response))
                }
                val snapshotPath = SecureTempFileSupport.createTempFile("uiptv-remote-download-", ".db")
                response.entity.content.use { body: InputStream ->
                    Files.copy(body, snapshotPath, StandardCopyOption.REPLACE_EXISTING)
                }
                snapshotPath
            }
        }
    }

    @Throws(IOException::class)
    open fun completeSession(baseUrl: String, sessionId: String, success: Boolean, message: String?) {
        val post = HttpPost("$baseUrl/remote-sync/complete")
        post.setHeader("Content-Type", "application/json")
        val json = KJsonObject()
            .put("sessionId", sessionId)
            .put("success", success)
            .put("message", message ?: "")
        post.entity = ByteArrayEntity(json.toString().toByteArray(), ContentType.APPLICATION_JSON)
        executeJson(post)
    }

    @Throws(IOException::class)
    private fun executeJson(request: HttpUriRequestBase): KJsonObject {
        HttpClients.createDefault().use { client ->
            return client.execute(request) { response ->
                val body = readEntityText(response)
                if (response.code >= 300) {
                    throw IOException(if (body.isBlank()) "Remote sync request failed with status ${response.code}" else body)
                }
                if (body.isBlank()) KJsonObject() else KJsonObject(body)
            }
        }
    }

    @Throws(IOException::class)
    private fun readEntityText(response: ClassicHttpResponse): String {
        if (response.entity == null) {
            return ""
        }
        return try {
            EntityUtils.toString(response.entity)
        } catch (ex: ParseException) {
            throw IOException("Unable to parse remote sync response", ex)
        }
    }
}
