package com.uiptv.mobile.android

import android.net.Uri
import android.util.Log
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

internal object SoftStreamUrlResolver {
    private const val LogTag = "UIPTV-StreamResolver"
    private const val ConnectTimeoutMs = 3_000
    private const val ReadTimeoutMs = 3_000
    private const val MaxRedirects = 8
    private const val MaxProbeBytes = 16 * 1024
    private val ManifestUrlPattern = Regex("""https?://[^\s"'<>]+?\.(?:m3u8|mpd)(?:[^\s"'<>]*)?""", RegexOption.IGNORE_CASE)
    private val BandwidthPattern = Regex("""BANDWIDTH=(\d+)""", RegexOption.IGNORE_CASE)
    private val DirectMediaExtensions = setOf(
        "m3u8",
        "mpd",
        "mp4",
        "m4v",
        "mkv",
        "avi",
        "mov",
        "webm",
        "ts",
        "m2ts",
        "mp3",
        "aac",
        "ac3",
        "eac3"
    )

    fun shouldResolve(url: String): Boolean {
        val uri = url.toNetworkUriOrNull() ?: return false
        val extension = uri.lastPathSegment
            ?.substringAfterLast('.', missingDelimiterValue = "")
            ?.substringBefore('?')
            ?.lowercase()
            .orEmpty()
        return extension !in DirectMediaExtensions
    }

    fun resolve(url: String, userAgent: String): Result {
        val original = url.trim()
        if (!shouldResolve(original)) {
            return Result(original, original, "direct-media")
        }

        var current = original
        repeat(MaxRedirects) {
            val connection = runCatching {
                (URL(current).openConnection() as HttpURLConnection).apply {
                    instanceFollowRedirects = false
                    requestMethod = "GET"
                    connectTimeout = ConnectTimeoutMs
                    readTimeout = ReadTimeoutMs
                    useCaches = false
                    setRequestProperty("User-Agent", userAgent)
                    setRequestProperty("Accept", "*/*")
                    setRequestProperty("Connection", "close")
                }
            }.getOrElse { ex ->
                Log.d(LogTag, "Resolver could not open ${current.safeStreamDescriptor()}: ${ex.javaClass.simpleName}")
                return Result(original, original, "open-failed")
            }

            try {
                val status = connection.responseCode
                if (status in 300..399) {
                    val location = connection.getHeaderField("Location").orEmpty().trim()
                    if (location.isBlank()) {
                        return Result(original, current, "redirect-without-location")
                    }
                    val next = URL(URL(current), location).toString()
                    if (next.toNetworkUriOrNull() == null) {
                        return Result(original, current, "redirect-non-network")
                    }
                    if (next.isManifestUrl()) {
                        return resolveManifestCandidate(original, next, userAgent, "redirect-manifest")
                    }
                    current = next
                    return@repeat
                }

                if (current != original) {
                    return Result(original, current, "redirect")
                }

                if (status in 200..299) {
                    val contentType = connection.contentType.orEmpty()
                    if (contentType.isManifestContentType() || contentType.isTextLikeContentType()) {
                        val body = connection.readProbeBody()
                        if (body.startsWith("#EXTM3U")) {
                            val variantUrl = body.bestHlsVariantUrl(current)
                            return Result(
                                original,
                                variantUrl.ifBlank { current },
                                if (variantUrl.isNotBlank() && variantUrl != current) "manifest-body-variant" else "manifest-body"
                            )
                        }
                        val embeddedManifest = ManifestUrlPattern.find(body)?.value
                            ?.normalizeEmbeddedUrl()
                            .orEmpty()
                        if (embeddedManifest.toNetworkUriOrNull() != null) {
                            return resolveManifestCandidate(original, embeddedManifest, userAgent, "embedded-manifest")
                        }
                    }
                }

                return Result(original, original, "no-redirect")
            } catch (ex: Exception) {
                Log.d(LogTag, "Resolver kept ${current.safeStreamDescriptor()}: ${ex.javaClass.simpleName}")
                return Result(original, original, "probe-failed")
            } finally {
                connection.disconnect()
            }
        }

        return Result(original, current, "redirect-limit")
    }

    private fun resolveManifestCandidate(
        original: String,
        manifestUrl: String,
        userAgent: String,
        reason: String
    ): Result {
        val body = fetchSmallText(manifestUrl, userAgent)
        if (!body.startsWith("#EXTM3U")) {
            return Result(original, manifestUrl, reason)
        }
        val variantUrl = body.bestHlsVariantUrl(manifestUrl)
        return if (variantUrl.isNotBlank() && variantUrl != manifestUrl) {
            Result(original, variantUrl, "$reason-variant")
        } else {
            Result(original, manifestUrl, reason)
        }
    }

    private fun fetchSmallText(url: String, userAgent: String): String {
        val connection = runCatching {
            (URL(url).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = true
                requestMethod = "GET"
                connectTimeout = ConnectTimeoutMs
                readTimeout = ReadTimeoutMs
                useCaches = false
                setRequestProperty("User-Agent", userAgent)
                setRequestProperty("Accept", "*/*")
                setRequestProperty("Connection", "close")
            }
        }.getOrElse { return "" }
        return try {
            val status = connection.responseCode
            if (status in 200..299) connection.readProbeBody() else ""
        } catch (_: Exception) {
            ""
        } finally {
            connection.disconnect()
        }
    }

    private fun HttpURLConnection.readProbeBody(): String {
        val stream = runCatching {
            if (responseCode >= 400) errorStream else inputStream
        }.getOrNull() ?: return ""
        stream.use { input ->
            val output = ByteArrayOutputStream()
            val buffer = ByteArray(1024)
            while (output.size() < MaxProbeBytes) {
                val remaining = MaxProbeBytes - output.size()
                val read = input.read(buffer, 0, minOf(buffer.size, remaining))
                if (read <= 0) {
                    break
                }
                output.write(buffer, 0, read)
            }
            return output.toString(Charsets.UTF_8.name()).trim()
        }
    }

    private fun String.isManifestUrl(): Boolean {
        val path = runCatching { Uri.parse(this).path.orEmpty() }.getOrDefault("")
        return path.endsWith(".m3u8", ignoreCase = true) || path.endsWith(".mpd", ignoreCase = true)
    }

    private fun String.isManifestContentType(): Boolean {
        val value = lowercase()
        return value.contains("mpegurl") ||
            value.contains("vnd.apple.mpegurl") ||
            value.contains("dash+xml") ||
            value.contains("x-mpegurl")
    }

    private fun String.isTextLikeContentType(): Boolean {
        val value = lowercase()
        return value.isBlank() ||
            value.startsWith("text/") ||
            value.contains("json") ||
            value.contains("xml") ||
            value.contains("mpegurl") ||
            value.contains("dash+xml")
    }

    private fun String.normalizeEmbeddedUrl(): String =
        trim()
            .trimEnd(',', ';')
            .replace("&amp;", "&")
            .replace("\\u0026", "&")

    private fun String.bestHlsVariantUrl(manifestUrl: String): String {
        val lines = lineSequence().map { it.trim() }.filter { it.isNotBlank() }.toList()
        var bestBandwidth = -1L
        var bestUrl = ""
        var pendingBandwidth: Long? = null
        for (line in lines) {
            when {
                line.startsWith("#EXT-X-STREAM-INF", ignoreCase = true) -> {
                    pendingBandwidth = BandwidthPattern.find(line)?.groupValues?.getOrNull(1)?.toLongOrNull() ?: 0L
                }
                pendingBandwidth != null && !line.startsWith("#") -> {
                    val resolved = runCatching { URL(URL(manifestUrl), line).toString() }.getOrDefault("")
                    if (resolved.toNetworkUriOrNull() != null && pendingBandwidth >= bestBandwidth) {
                        bestBandwidth = pendingBandwidth
                        bestUrl = resolved
                    }
                    pendingBandwidth = null
                }
                !line.startsWith("#") -> pendingBandwidth = null
            }
        }
        return bestUrl
    }

    data class Result(
        val originalUrl: String,
        val resolvedUrl: String,
        val reason: String
    )
}

internal fun String.safeStreamDescriptor(): String {
    val uri = toNetworkUriOrNull() ?: return "non-url"
    val scheme = uri.scheme?.lowercase().orEmpty()
    val host = uri.host.orEmpty()
    val path = uri.path.orEmpty()
    val lastSegment = uri.lastPathSegment.orEmpty()
    val pathHint = when {
        path.isBlank() -> ""
        lastSegment.isNotBlank() -> "/$lastSegment"
        else -> path.take(24)
    }
    return "$scheme://$host$pathHint"
}

private fun String.toNetworkUriOrNull(): Uri? {
    val uri = runCatching { Uri.parse(trim()) }.getOrNull() ?: return null
    val scheme = uri.scheme?.lowercase()
    return if ((scheme == "http" || scheme == "https") && !uri.host.isNullOrBlank()) uri else null
}
