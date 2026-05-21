package com.uiptv.mobile.shared.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.io.File
import java.net.ConnectException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URL

class AndroidRemoteSyncClient : RemoteSyncClient {
    override suspend fun health(baseUrl: String): Boolean {
        val json = withContext(Dispatchers.IO) { executeJson("$baseUrl/remote-sync/health", "GET") }
        if (!json.optString("status").equals("ok", ignoreCase = true)) {
            throw IOException("Remote sync server did not respond with OK status.")
        }
        return true
    }

    override suspend fun request(baseUrl: String, request: RemoteSyncRequest): RemoteSyncSessionState {
        val body = JSONObject()
            .put("direction", request.direction.name)
            .put("verificationCode", request.verificationCode)
            .put("requesterName", request.requesterName)
            .put("syncConfiguration", request.options.syncConfiguration)
            .put("syncExternalPlayerPaths", request.options.syncExternalPlayerPaths)
            .put("configurationProfile", request.options.configurationProfile.name)
            .put("archiveTransfer", request.options.archiveTransfer)
            .put("encryptedTransfer", request.options.encryptedTransfer)
            .toString()
        return withContext(Dispatchers.IO) {
            executeJson("$baseUrl/remote-sync/request", "POST", body).toSessionState()
        }
    }

    override suspend fun status(baseUrl: String, sessionId: String): RemoteSyncSessionState =
        withContext(Dispatchers.IO) {
            executeJson("$baseUrl/remote-sync/status?sessionId=${sessionId.urlQueryValue()}", "GET").toSessionState()
        }

    override suspend fun download(baseUrl: String, sessionId: String): ByteArray =
        withContext(Dispatchers.IO) {
            executeBytes("$baseUrl/remote-sync/download?sessionId=${sessionId.urlQueryValue()}", "GET")
        }

    suspend fun downloadToFile(baseUrl: String, sessionId: String, destination: File): Long =
        withContext(Dispatchers.IO) {
            executeToFile("$baseUrl/remote-sync/download?sessionId=${sessionId.urlQueryValue()}", destination)
        }

    override suspend fun complete(baseUrl: String, sessionId: String, success: Boolean, message: String) {
        val body = JSONObject()
            .put("sessionId", sessionId)
            .put("success", success)
            .put("message", message)
            .toString()
        withContext(Dispatchers.IO) {
            executeJson("$baseUrl/remote-sync/complete", "POST", body)
        }
    }

    private fun executeJson(url: String, method: String, body: String? = null): JSONObject {
        val response = executeBytes(url, method, body).toString(Charsets.UTF_8)
        return if (response.isBlank()) JSONObject() else JSONObject(response)
    }

    private fun executeBytes(url: String, method: String, body: String? = null): ByteArray {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = method
                connectTimeout = 10_000
                readTimeout = 120_000
                if (body != null) {
                    doOutput = true
                    setRequestProperty("Content-Type", "application/json")
                    outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
                }
            }

            val status = connection.responseCode
            val stream = if (status >= 300) connection.errorStream else connection.inputStream
            val bytes = stream?.use { it.readBytes() } ?: ByteArray(0)
            if (status >= 300) {
                val message = bytes.toString(Charsets.UTF_8).ifBlank { "Remote sync request failed with status $status." }
                throw IOException(message)
            }
            return bytes
        } catch (ex: UnknownHostException) {
            throw IOException("Cannot resolve desktop sync host ${url.endpointLabel()}. Use localhost with adb reverse, 10.0.2.2 on the emulator, or the desktop LAN IP.", ex)
        } catch (ex: ConnectException) {
            throw IOException("Cannot connect to desktop sync server at ${url.endpointLabel()}. Check that the listener is running and the emulator port mapping is active.", ex)
        } catch (ex: SocketTimeoutException) {
            throw IOException("Timed out connecting to desktop sync server at ${url.endpointLabel()}. Check the host, port, and desktop firewall.", ex)
        } finally {
            connection?.disconnect()
        }
    }

    private fun executeToFile(url: String, destination: File): Long {
        var connection: HttpURLConnection? = null
        try {
            connection = (URL(url).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 10_000
                readTimeout = 120_000
            }

            val status = connection.responseCode
            if (status >= 300) {
                val message = connection.errorStream
                    ?.use { it.readBytes().toString(Charsets.UTF_8) }
                    ?.ifBlank { null }
                    ?: "Remote sync request failed with status $status."
                throw IOException(message)
            }

            destination.outputStream().use { output ->
                connection.inputStream.use { input ->
                    return input.copyTo(output, bufferSize = 64 * 1024)
                }
            }
        } catch (ex: UnknownHostException) {
            throw IOException("Cannot resolve desktop sync host ${url.endpointLabel()}. Use localhost with adb reverse, 10.0.2.2 on the emulator, or the desktop LAN IP.", ex)
        } catch (ex: ConnectException) {
            throw IOException("Cannot connect to desktop sync server at ${url.endpointLabel()}. Check that the listener is running and the emulator port mapping is active.", ex)
        } catch (ex: SocketTimeoutException) {
            throw IOException("Timed out connecting to desktop sync server at ${url.endpointLabel()}. Check the host, port, and desktop firewall.", ex)
        } finally {
            connection?.disconnect()
        }
    }

    private fun JSONObject.toSessionState(): RemoteSyncSessionState =
        RemoteSyncSessionState(
            sessionId = optString("sessionId"),
            direction = optEnum("direction", RemoteSyncDirection.IMPORT_FROM_REMOTE),
            status = optEnum("status", RemoteSyncStatus.FAILED),
            verificationCode = optString("verificationCode"),
            requesterName = optString("requesterName"),
            requesterAddress = optString("requesterAddress"),
            options = RemoteSyncOptions(
                syncConfiguration = optBoolean("syncConfiguration", false),
                syncExternalPlayerPaths = optBoolean("syncExternalPlayerPaths", false),
                configurationProfile = optEnum("configurationProfile", ConfigurationSyncProfile.DESKTOP_FULL),
                archiveTransfer = optBoolean("archiveTransfer", false),
                encryptedTransfer = optBoolean("encryptedTransfer", false)
            ),
            message = optString("message")
        )

    private inline fun <reified T : Enum<T>> JSONObject.optEnum(name: String, defaultValue: T): T =
        optString(name)
            .takeIf { it.isNotBlank() }
            ?.let { runCatching { enumValueOf<T>(it) }.getOrNull() }
            ?: defaultValue

    private fun String.urlQueryValue(): String =
        java.net.URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.endpointLabel(): String =
        runCatching {
            val parsed = URL(this)
            val port = parsed.port.takeIf { it > 0 } ?: parsed.defaultPort
            "${parsed.host}:$port"
        }.getOrElse { this }
}
