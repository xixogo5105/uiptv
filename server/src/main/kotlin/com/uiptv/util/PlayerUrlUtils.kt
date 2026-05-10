package com.uiptv.util

import com.uiptv.model.Account
import com.uiptv.model.Channel
import java.net.URI
import java.util.Locale

object PlayerUrlUtils {
    private const val FFMPEG_PREFIX = "ffmpeg "
    private const val FFMPEG_PLUS_PREFIX = "ffmpeg+"
    private const val FFMPEG_URL_ENCODED_PREFIX = "ffmpeg%20"
    private const val HTTP_SCHEME = "http"
    private const val HTTPS_PREFIX = "https://"
    private const val HTTP_PREFIX = "http://"

    @JvmStatic
    fun resolveAndProcessUrl(url: String?): String {
        if (StringUtils.isBlank(url)) return url.orEmpty()
        val processedUrl = extractPlayableUrl(url)
        if (processedUrl.contains("youtube.com/watch?v=") || processedUrl.contains("youtu.be/")) {
            val streamingUrl = YoutubeDL.getStreamingUrl(processedUrl)
            if (!streamingUrl.isNullOrEmpty()) {
                return streamingUrl
            }
        }
        return processedUrl
    }

    @JvmStatic
    fun extractPlayableUrl(raw: String?): String {
        if (StringUtils.isBlank(raw)) {
            return raw.orEmpty()
        }
        val value = raw!!.trim()
        val lower = value.lowercase()
        if (lower.startsWith(FFMPEG_PREFIX)) {
            return value.substring(FFMPEG_PREFIX.length).trim()
        }
        if (lower.startsWith(FFMPEG_PLUS_PREFIX)) {
            return value.substring(FFMPEG_PLUS_PREFIX.length).trim()
        }
        if (lower.startsWith(FFMPEG_URL_ENCODED_PREFIX)) {
            return value.substring(FFMPEG_URL_ENCODED_PREFIX.length).trim()
        }
        val uriParts = value.split(" ")
        return if (uriParts.size > 1) uriParts.last() else value
    }

    @JvmStatic
    fun normalizeStreamUrl(account: Account?, url: String?): String {
        if (StringUtils.isBlank(url)) {
            return url.orEmpty()
        }
        val value = url!!.trim()
        val portalUri = resolvePortalUri(account)
        val scheme = resolvePortalScheme(portalUri)
        if (isAbsoluteUrl(value)) {
            return alignStalkerPlaybackScheme(account, value, scheme)
        }
        if (value.startsWith("//")) {
            return "$scheme:$value"
        }
        if (value.startsWith("/")) {
            return prependPortalHost(value, scheme, portalUri)
        }
        if (isHostPathLike(value)) {
            return "$scheme://$value"
        }
        return value
    }

    @JvmStatic
    fun resolveBestChannelCmd(account: Account?, channel: Channel?): String {
        if (channel == null) return ""
        val primary = channel.cmd
        if (account == null) return primary.orEmpty()
        if (account.type == AccountType.STALKER_PORTAL && account.action == Account.AccountAction.itv) {
            val candidates = arrayOf(channel.cmd, channel.cmd_1, channel.cmd_2, channel.cmd_3)
            for (candidate in candidates) {
                if (isUsableLiveCmd(candidate)) return candidate!!
            }
            return primary.orEmpty()
        }
        return primary.orEmpty()
    }

    @JvmStatic
    fun isUsableLiveCmd(cmd: String?): Boolean {
        if (StringUtils.isBlank(cmd)) return false
        var normalized = cmd!!.trim().lowercase()
        if (normalized.startsWith(FFMPEG_PREFIX)) {
            normalized = normalized.substring(FFMPEG_PREFIX.length).trim()
        }
        return !normalized.contains("stream=&")
    }

    @JvmStatic
    fun isLikelyOnDemandPlaybackUrl(url: String?): Boolean {
        if (StringUtils.isBlank(url)) {
            return false
        }
        val normalized = extractPlayableUrl(url)
        if (StringUtils.isBlank(normalized)) {
            return false
        }
        val lower = normalized.trim().lowercase(Locale.ROOT)
        if (lower.contains("/play/movie.php")) {
            return lower.contains("type=movie") || lower.contains("type=series")
        }
        return !lower.contains("/live/play/") &&
            (lower.endsWith(".mkv") || lower.endsWith(".mp4") || lower.endsWith(".avi") || lower.endsWith(".mov") ||
                lower.endsWith(".m4v") || lower.endsWith(".wmv") || lower.endsWith(".flv") || lower.endsWith(".webm") || lower.endsWith(".ts"))
    }

    private fun resolvePortalUri(account: Account?): URI? {
        return try {
            val portal = account?.serverPortalUrl
            if (!StringUtils.isBlank(portal)) URI.create(portal!!.trim()) else null
        } catch (_: Exception) {
            null
        }
    }

    private fun resolvePortalScheme(portalUri: URI?): String =
        if (portalUri != null && !StringUtils.isBlank(portalUri.scheme)) portalUri.scheme else HTTP_SCHEME

    private fun isAbsoluteUrl(value: String): Boolean = value.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))
    private fun isHostPathLike(value: String): Boolean = value.matches(Regex("^[a-zA-Z0-9.-]+(?::\\d+)?/.*"))

    private fun alignStalkerPlaybackScheme(account: Account?, value: String, scheme: String): String {
        val lowerValue = value.lowercase()
        return if (account != null &&
            account.type == AccountType.STALKER_PORTAL &&
            HTTP_SCHEME.equals(scheme, ignoreCase = true) &&
            lowerValue.startsWith(HTTPS_PREFIX) &&
            (lowerValue.contains("/live/play/") || lowerValue.contains("/play/movie.php"))
        ) {
            HTTP_PREFIX + value.substring(HTTPS_PREFIX.length)
        } else {
            value
        }
    }

    private fun prependPortalHost(value: String, scheme: String, portalUri: URI?): String {
        return if (portalUri != null && !StringUtils.isBlank(portalUri.host)) {
            val port = portalUri.port
            "$scheme://${portalUri.host}${if (port > 0) ":$port" else ""}$value"
        } else {
            value
        }
    }
}
