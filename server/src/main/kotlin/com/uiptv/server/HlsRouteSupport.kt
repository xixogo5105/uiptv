package com.uiptv.server

import com.uiptv.service.InMemoryHlsService
import com.uiptv.util.ServerUtils
import java.nio.charset.StandardCharsets
import java.util.regex.Pattern

data class HlsFilePayload(
    val contentType: String,
    val bytes: ByteArray
)

object HlsRouteSupport {
    private val missingTsRetryAttempts = Integer.getInteger("uiptv.hls.missing.ts.retry.attempts", 100)
    private val missingTsRetrySleepMs = java.lang.Long.getLong("uiptv.hls.missing.ts.retry.sleep.millis", 50L)
    private val emptyContent = ByteArray(0)

    fun readFile(fileName: String, hvec: Boolean): HlsFilePayload? {
        InMemoryHlsService.markClientAccess()
        var data = waitForUploadIfNeeded(fileName)
        if (data.isEmpty()) {
            return null
        }
        if (fileName.endsWith(".m3u8") && hvec) {
            data = rewritePlaylistWithHvecQuery(data)
        }
        val contentType = if (fileName.endsWith(".m3u8")) ServerUtils.CONTENT_TYPE_M3U8 else ServerUtils.CONTENT_TYPE_TS
        return HlsFilePayload(contentType, data)
    }

    fun upload(fileName: String, bytes: ByteArray) {
        InMemoryHlsService.put(fileName, bytes)
    }

    fun delete(fileName: String) {
        InMemoryHlsService.remove(fileName)
    }

    fun isTruthy(value: String?): Boolean =
        when (value?.trim()?.lowercase()) {
            "1", "true", "yes", "on" -> true
            else -> false
        }

    private fun waitForUploadIfNeeded(fileName: String): ByteArray {
        var data = InMemoryHlsService.get(fileName)
        if (data != null || !fileName.endsWith(".ts")) {
            return data ?: emptyContent
        }
        repeat(missingTsRetryAttempts) {
            try {
                Thread.sleep(missingTsRetrySleepMs)
            } catch (_: InterruptedException) {
                Thread.currentThread().interrupt()
                return emptyContent
            }
            data = InMemoryHlsService.get(fileName)
            if (data != null) {
                return data
            }
        }
        return emptyContent
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
