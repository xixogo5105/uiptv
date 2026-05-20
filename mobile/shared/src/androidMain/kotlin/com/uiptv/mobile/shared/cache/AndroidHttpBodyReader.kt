package com.uiptv.mobile.shared.cache

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.net.HttpURLConnection

internal const val MAX_CACHE_HTTP_BODY_BYTES: Int = 64 * 1024 * 1024

internal fun HttpURLConnection.readCacheBody(label: String, maxBytes: Int = MAX_CACHE_HTTP_BODY_BYTES): String =
    try {
        val status = responseCode
        val announcedLength = contentLengthLong
        if (announcedLength > maxBytes) {
            error("$label response is too large (${announcedLength.toDisplaySize()}; limit ${maxBytes.toLong().toDisplaySize()}). Existing cache was kept.")
        }
        val stream = if (status >= 300) errorStream else inputStream
        val body = stream?.use { it.readUtf8Limited(label, maxBytes) }.orEmpty()
        if (status >= 300) {
            error(body.ifBlank { "$label failed with status $status." })
        }
        body
    } finally {
        disconnect()
    }

internal fun InputStream.readUtf8Limited(label: String, maxBytes: Int = MAX_CACHE_HTTP_BODY_BYTES): String {
    val output = ByteArrayOutputStream()
    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
    var total = 0
    while (true) {
        val read = read(buffer)
        if (read < 0) {
            break
        }
        total += read
        if (total > maxBytes) {
            error("$label response is too large (over ${maxBytes.toLong().toDisplaySize()}). Existing cache was kept.")
        }
        output.write(buffer, 0, read)
    }
    return output.toString(Charsets.UTF_8.name())
}

internal fun Long.toDisplaySize(): String =
    when {
        this >= 1024L * 1024L -> "${this / (1024L * 1024L)} MB"
        this >= 1024L -> "${this / 1024L} KB"
        else -> "$this bytes"
    }
