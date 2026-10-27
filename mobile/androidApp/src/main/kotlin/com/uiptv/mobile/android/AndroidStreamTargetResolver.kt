package com.uiptv.mobile.android

import com.uiptv.mobile.shared.playback.PlaybackTarget
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale

/** Resolves opaque stream links before a player has to choose its media source. */
internal suspend fun resolveAndroidStreamTarget(target: PlaybackTarget): PlaybackTarget = withContext(Dispatchers.IO) {
    if (target.manifestType.isNotBlank()) return@withContext target
    val initialUrl = try {
        URL(target.url)
    } catch (_: IOException) {
        return@withContext target
    }
    val extension = initialUrl.path.substringAfterLast('.', "").lowercase(Locale.ROOT)
    if (extension in setOf("m3u8", "mpd", "mp4", "mkv", "ts", "m4v", "mov", "avi", "webm", "mp3", "aac", "flac", "ogg", "wav")) {
        return@withContext target
    }
    var url = initialUrl
    val deadline = System.nanoTime() + 8_000_000_000L
    try {
        repeat(6) {
            ensureActive()
            if (url.protocol !in setOf("http", "https")) return@withContext target
            val remaining = ((deadline - System.nanoTime()) / 1_000_000).toInt()
            if (remaining <= 0) return@withContext target
            val connection = (url.openConnection() as HttpURLConnection).apply {
                useCaches = false
                instanceFollowRedirects = false
                connectTimeout = minOf(3_000, remaining)
                readTimeout = minOf(3_000, remaining)
                setRequestProperty("User-Agent", StreamProbeUserAgent)
            }
            try {
                val status = connection.responseCode
                if (status in listOf(301, 302, 303, 307, 308)) {
                    val location = connection.getHeaderField("Location") ?: return@withContext target
                    url = URL(url, location)
                } else {
                    if (status !in 200..299) return@withContext target
                    val contentType = connection.contentType.orEmpty().substringBefore(';').trim().lowercase(Locale.ROOT)
                    val declaredType = when (contentType) {
                        "application/vnd.apple.mpegurl", "application/x-mpegurl", "audio/mpegurl", "audio/x-mpegurl" -> "hls"
                        "application/dash+xml" -> "mpd"
                        else -> ""
                    }
                    val manifestType = declaredType.ifBlank {
                        val prefix = connection.inputStream.use { stream ->
                            val bytes = ByteArray(512)
                            var count = 0
                            while (count < bytes.size) {
                                ensureActive()
                                val readRemaining = ((deadline - System.nanoTime()) / 1_000_000).toInt()
                                if (readRemaining <= 0) break
                                connection.readTimeout = minOf(3_000, readRemaining)
                                val read = stream.read(bytes, count, bytes.size - count)
                                if (read < 0) break
                                count += read
                                // A complete manifest signature does not require the entire response.
                                val text = String(bytes, 0, count, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                                if (text.startsWith("#EXTM3U") || DashManifestTag.containsMatchIn(text)) break
                            }
                            String(bytes, 0, count, Charsets.UTF_8).trimStart('\uFEFF', ' ', '\t', '\r', '\n')
                        }
                        when {
                            prefix.startsWith("#EXTM3U") -> "hls"
                            DashManifestTag.containsMatchIn(prefix) -> "mpd"
                            else -> ""
                        }
                    }
                    return@withContext if (manifestType.isNotBlank()) {
                        target.copy(url = url.toExternalForm(), manifestType = manifestType)
                    } else target
                }
            } finally {
                connection.disconnect()
            }
        }
    } catch (_: IOException) {
        // Probing is optional: retain the original URL when the server cannot be inspected.
    } catch (_: IllegalArgumentException) {
        // A malformed redirect must not prevent the player from trying the original target.
    }
    target
}

internal const val StreamProbeUserAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
private val DashManifestTag = Regex("<(?:[\\w.-]+:)?MPD(?:\\s|>)")
