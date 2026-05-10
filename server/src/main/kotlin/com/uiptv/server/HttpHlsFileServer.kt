package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.InMemoryHlsService
import com.uiptv.util.ServerUtils.CONTENT_TYPE_M3U8
import com.uiptv.util.ServerUtils.CONTENT_TYPE_TS
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils.isBlank
import java.io.IOException
import java.io.OutputStream
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

class HttpHlsFileServer : HttpHandler {
    companion object {
        private val MISSING_TS_RETRY_ATTEMPTS = Integer.getInteger("uiptv.hls.missing.ts.retry.attempts", 100)
        private val MISSING_TS_RETRY_SLEEP_MS = java.lang.Long.getLong("uiptv.hls.missing.ts.retry.sleep.millis", 50L)
        private val EMPTY_CONTENT = ByteArray(0)
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val path = ex.requestURI.path
        val fileName = path.substring(path.lastIndexOf('/') + 1)
        InMemoryHlsService.getInstance().markClientAccess()
        var data = waitForUploadIfNeeded(fileName)

        if (data.isEmpty()) {
            ex.sendResponseHeaders(404, -1)
            return
        }

        val m3u8Request = fileName.endsWith(".m3u8")
        if (m3u8Request && isHvecEnabled(getParam(ex, "hvec"))) {
            data = rewritePlaylistWithHvecQuery(data)
        }

        val contentType = if (fileName.endsWith(".m3u8")) CONTENT_TYPE_M3U8 else CONTENT_TYPE_TS
        ex.responseHeaders.add("Access-Control-Allow-Origin", "*")
        ex.responseHeaders.add("Cache-Control", "no-store")
        ex.responseHeaders.add("Content-Type", contentType)
        ex.sendResponseHeaders(200, data.size.toLong())
        ex.responseBody.use { os: OutputStream -> os.write(data) }
    }

    private fun waitForUploadIfNeeded(fileName: String): ByteArray {
        var data = InMemoryHlsService.getInstance().get(fileName)
        if (data != null || !fileName.endsWith(".ts")) {
            return data ?: EMPTY_CONTENT
        }
        repeat(MISSING_TS_RETRY_ATTEMPTS) {
            try {
                Thread.sleep(MISSING_TS_RETRY_SLEEP_MS)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return EMPTY_CONTENT
            }
            data = InMemoryHlsService.getInstance().get(fileName)
            if (data != null) {
                return data
            }
        }
        return EMPTY_CONTENT
    }

    private fun isHvecEnabled(value: String?): Boolean {
        if (isBlank(value)) return false
        return when (value!!.trim().lowercase()) {
            "1", "true", "yes", "on" -> true
            else -> false
        }
    }

    private fun rewritePlaylistWithHvecQuery(data: ByteArray): ByteArray {
        if (data.isEmpty()) {
            return data
        }
        val body = String(data, StandardCharsets.UTF_8)
        val lines = Pattern.compile("\\r?\\n").split(body, -1)
        val rewritten = StringBuilder(body.length + 32)
        lines.forEachIndexed { index, line0 ->
            var line = line0
            val trimmed = line.trim()
            if (trimmed.isNotEmpty() && !trimmed.startsWith("#") && !trimmed.contains("hvec=")) {
                line += if (line.contains("?")) "&" else "?"
                line += "hvec=1"
            }
            rewritten.append(line)
            if (index < lines.size - 1) {
                rewritten.append('\n')
            }
        }
        return rewritten.toString().toByteArray(StandardCharsets.UTF_8)
    }
}
