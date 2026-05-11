package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.util.FetchAPI
import com.uiptv.util.PlayerUrlUtils
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.json.KJsonObject
import java.io.IOException
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Date
import java.util.LinkedHashMap
import java.util.function.Supplier
import java.util.stream.Collectors
class StalkerPortalPlayerService @JvmOverloads constructor(
    private val accountService: AccountService = AccountService,
    private val handshakeService: HandshakeService = HandshakeService()
) : AccountPlayerService {
    companion object {
        private const val CREATE_LINK_TIMEOUT_SECONDS = 8
        private const val FFMPEG_PREFIX = "ffmpeg "
        private const val STREAM_PARAM = "stream="
        private const val STREAM_PARAM_WITH_SEPARATOR = "stream=&"
        private val CREATE_LINK_REQUEST_OPTIONS =
            com.uiptv.util.HttpUtil.RequestOptions(true, true, CREATE_LINK_TIMEOUT_SECONDS, CREATE_LINK_TIMEOUT_SECONDS, CREATE_LINK_TIMEOUT_SECONDS)
        @JvmStatic
        fun mergeMissingQueryParams(resolvedCmd: String?, originalCmd: String?): String {
            if (isBlank(resolvedCmd) || isBlank(originalCmd)) return resolvedCmd.orEmpty()
            val resolvedPrefix = extractCmdPrefix(resolvedCmd)
            val originalPrefix = extractCmdPrefix(originalCmd)
            val resolvedUrl = extractCmdUrl(resolvedCmd)
            val originalUrl = extractCmdUrl(originalCmd)
            val resolvedQueryIndex = resolvedUrl.indexOf('?')
            val originalQueryIndex = originalUrl.indexOf('?')
            if (resolvedQueryIndex < 0 || originalQueryIndex < 0) return resolvedCmd!!
            val resolvedBase = resolvedUrl.substring(0, resolvedQueryIndex)
            val originalBase = originalUrl.substring(0, originalQueryIndex)
            val normalizedResolvedBase = normalizeResolvedBase(resolvedBase, originalBase)
            val resolvedParams = parseQueryParams(resolvedUrl.substring(resolvedQueryIndex + 1))
            val originalParams = parseQueryParams(originalUrl.substring(originalQueryIndex + 1))
            originalParams.forEach { (key, value) ->
                val existing = resolvedParams[key]
                if ((existing == null || existing.isBlank()) && !value.isNullOrBlank()) resolvedParams[key] = value
            }
            val mergedUrl = normalizedResolvedBase + "?" + toQueryString(resolvedParams)
            val prefix = if (!isBlank(resolvedPrefix)) resolvedPrefix else originalPrefix
            return if (isBlank(prefix)) mergedUrl else "$prefix $mergedUrl"
        }

        @JvmStatic
        private fun normalizeResolvedBase(resolvedBase: String, originalBase: String): String {
            if (isBlank(resolvedBase)) return resolvedBase
            val trimmed = resolvedBase.trim()
            if (trimmed.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) || trimmed.startsWith("//")) return trimmed
            if (isBlank(originalBase)) return trimmed
            return try {
                val originalUri = URI.create(originalBase.trim())
                var normalizedOriginal = originalUri
                if (originalUri.scheme == null || originalUri.host == null) return trimmed
                if (!normalizedOriginal.path.endsWith("/")) {
                    val path = normalizedOriginal.path
                    val idx = path.lastIndexOf('/')
                    val dirPath = if (idx >= 0) path.substring(0, idx + 1) else "/"
                    normalizedOriginal = URI(normalizedOriginal.scheme, normalizedOriginal.userInfo, normalizedOriginal.host, normalizedOriginal.port, dirPath, null, null)
                }
                normalizedOriginal.resolve(trimmed).toString()
            } catch (_: Exception) {
                trimmed
            }
        }

        @JvmStatic
        private fun extractCmdPrefix(cmd: String?): String {
            if (isBlank(cmd)) return ""
            val trimmed = cmd!!.trim()
            return if (trimmed.startsWith(FFMPEG_PREFIX)) "ffmpeg" else ""
        }

        @JvmStatic
        private fun extractCmdUrl(cmd: String?): String {
            if (isBlank(cmd)) return ""
            val trimmed = cmd!!.trim()
            return if (trimmed.startsWith(FFMPEG_PREFIX)) trimmed.removePrefix(FFMPEG_PREFIX).trim() else trimmed
        }

        @JvmStatic
        private fun parseQueryParams(query: String?): MutableMap<String, String> {
            val params = LinkedHashMap<String, String>()
            if (isBlank(query)) return params
            query!!.split("&").forEach { pair ->
                if (pair.isBlank()) return@forEach
                val kv = pair.split("=", limit = 2)
                val key = URLDecoder.decode(kv[0], StandardCharsets.UTF_8)
                val value = if (kv.size > 1) URLDecoder.decode(kv[1], StandardCharsets.UTF_8) else ""
                params[key] = value
            }
            return params
        }

        @JvmStatic
        private fun toQueryString(params: Map<String, String>): String =
            params.entries.joinToString("&") { URLEncoder.encode(it.key, StandardCharsets.UTF_8) + "=" + URLEncoder.encode(it.value, StandardCharsets.UTF_8) }

        @JvmStatic
        private fun getLiveCmdCandidates(channel: Channel): List<String> {
            val candidates = ArrayList<String>()
            arrayOf(channel.cmd, channel.cmd_1, channel.cmd_2, channel.cmd_3).forEach { value ->
                if (!isBlank(value) && !candidates.contains(value)) candidates.add(value!!)
            }
            return candidates
        }

        @JvmStatic
        private fun isUsableResolvedLiveUrl(url: String?): Boolean {
            if (isBlank(url)) return false
            var normalized = url!!.trim().lowercase()
            if (normalized.startsWith(FFMPEG_PREFIX)) normalized = normalized.removePrefix(FFMPEG_PREFIX).trim()
            return !normalized.contains(STREAM_PARAM_WITH_SEPARATOR)
        }

        @JvmStatic
        private fun rescueResolvedLiveUrlWithCandidates(resolvedUrl: String?, candidates: List<String>?): String {
            if (isBlank(resolvedUrl) || candidates.isNullOrEmpty()) return resolvedUrl.orEmpty()
            var fixed = resolvedUrl!!
            candidates.forEach { candidate ->
                fixed = mergeMissingQueryParams(fixed, candidate)
                if (isUsableResolvedLiveUrl(fixed)) return fixed
            }
            return fixed
        }
    }

    @Throws(IOException::class)
    override fun get(account: Account, channel: Channel, series: String?, parentSeriesId: String?, categoryId: String?): PlayerResponse {
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "Resolving playback URL for Stalker Portal account: ${account.accountName}")
        ensureStalkerSession(account)
        val resolvedSeries = resolveSeriesParam(account, channel, series)
        val rawUrl =
            if (shouldTryLiveCmdFallback(account, channel)) {
                fetchStalkerLiveUrlWithFallback(account, channel, resolvedSeries)
            } else {
                fetchStalkerPortalUrl(account, resolvedSeries, PlayerUrlUtils.resolveBestChannelCmd(account, channel))
            }
        val finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl))
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "Final resolved URL: $finalUrl")
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "Playback URL resolved.")
        val response = PlayerResponse(finalUrl)
        response.setFromChannel(channel, account)
        return response
    }

    private fun resolveSeriesParam(account: Account?, channel: Channel?, series: String?): String {
        if (account == null || account.action != Account.AccountAction.series) return ""
        if (!isBlank(series)) return series.orEmpty()
        if (channel == null) return ""
        if (!isBlank(channel.episodeNum)) return channel.episodeNum.orEmpty()
        return if (isBlank(channel.channelId)) "" else channel.channelId.orEmpty()
    }

    private fun ensureStalkerSession(account: Account?) {
        if (account == null || account.type != com.uiptv.util.AccountType.STALKER_PORTAL) return
        if (isBlank(account.serverPortalUrl)) accountService.ensureServerPortalUrl(account)
        if (account.isNotConnected()) handshakeService.connect(account)
    }

    private fun shouldTryLiveCmdFallback(account: Account?, channel: Channel?): Boolean =
        account != null && account.type == com.uiptv.util.AccountType.STALKER_PORTAL && account.action == Account.AccountAction.itv && channel != null

    private fun fetchStalkerLiveUrlWithFallback(account: Account, channel: Channel, series: String): String {
        val candidates = getLiveCmdCandidates(channel).toMutableList()
        val fallbackCmd = PlayerUrlUtils.resolveBestChannelCmd(account, channel)
        if (candidates.isEmpty() && !isBlank(fallbackCmd)) candidates.add(fallbackCmd)
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "live create_link candidates: ${candidates.size}")
        for (cmd in candidates) {
            val resolved = fetchStalkerPortalUrl(account, series, cmd)
            if (isUsableResolvedLiveUrl(resolved)) {
                com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "live create_link selected usable URL")
                return resolved
            }
            val rescued = rescueResolvedLiveUrlWithCandidates(resolved, candidates)
            if (isUsableResolvedLiveUrl(rescued)) {
                com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "live create_link recovered URL by merging stream param from alternate cmd")
                return rescued
            }
        }
        com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService::class.java, "live create_link fallback to original cmd")
        return if (isBlank(fallbackCmd)) channel.cmd.orEmpty() else fallbackCmd.orEmpty()
    }

    private fun fetchStalkerPortalUrl(account: Account, series: String, originalCmd: String?): String {
        if (isBlank(originalCmd)) return originalCmd.orEmpty()
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "create_link start")
        var resolvedCmd = resolveCreateLink(account, series, originalCmd.orEmpty())
        if (isBlank(resolvedCmd)) {
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService::class.java, "create_link returned empty cmd. Refreshing token and retrying once.")
            handshakeService.hardTokenRefresh(account)
            resolvedCmd = resolveCreateLink(account, series, originalCmd.orEmpty())
        }
        if (isBlank(resolvedCmd)) {
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService::class.java, "create_link failed after retry. Using original channel cmd.")
            return originalCmd.orEmpty()
        }
        resolvedCmd = normalizeSeriesStreamPlaceholder(resolvedCmd, series)
        val mergedCmd = mergeMissingQueryParams(resolvedCmd, originalCmd)
        if (mergedCmd != resolvedCmd) {
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService::class.java, "create_link had missing query params. Merged missing values from original channel cmd.")
        }
        com.uiptv.util.AppLog.addInfoLog(StalkerPortalPlayerService::class.java, "create_link resolved URL: $mergedCmd")
        return mergedCmd
    }

    private fun resolveCreateLink(account: Account, series: String, cmd: String): String {
        val json = FetchAPI.fetch(getParams(account, cmd, series), account, CREATE_LINK_REQUEST_OPTIONS)
        val resolved = parseUrl(json)
        if (isBlank(resolved)) {
            com.uiptv.util.AppLog.addWarningLog(StalkerPortalPlayerService::class.java, "create_link unresolved for provided cmd.")
        }
        return resolved.orEmpty()
    }

    private fun getParams(account: Account, urlPrefix: String, series: String): Map<String, String> {
        val params = LinkedHashMap<String, String>()
        params["type"] = if (Account.AccountAction.series.name.equals(account.action.name, true)) Account.AccountAction.vod.name else account.action.name
        params["action"] = "create_link"
        params["cmd"] = urlPrefix
        params["series"] = if (Account.AccountAction.series.name.equals(account.action.name, true)) series else ""
        params["forced_storage"] = "undefined"
        params["disable_ad"] = "0"
        params["download"] = "0"
        params["JsHttpRequest"] = "${Date().time}-xml"
        return params
    }

    private fun parseUrl(json: String?): String? {
        return try {
            val root = KJsonObject(json.orEmpty())
            val js = root.optJSONObject("js")
            if (js != null) {
                val cmd = js.optString("cmd")
                if (!isBlank(cmd)) return cmd
                val url = js.optString("url")
                if (!isBlank(url)) return url
            }
            root.optString("cmd").takeUnless { isBlank(it) }
        } catch (_: Exception) {
            null
        }
    }

    private fun normalizeSeriesStreamPlaceholder(resolvedCmd: String, seriesParam: String?): String {
        if (isBlank(resolvedCmd) || isBlank(seriesParam)) return resolvedCmd
        val streamToken = extractStreamToken(seriesParam)
        if (isBlank(streamToken)) return resolvedCmd
        return when {
            resolvedCmd.contains("stream=.&") -> resolvedCmd.replace("stream=.&", STREAM_PARAM + streamToken + "&")
            resolvedCmd.endsWith("stream=.") -> resolvedCmd.removeSuffix("stream=.") + STREAM_PARAM + streamToken
            resolvedCmd.contains(STREAM_PARAM_WITH_SEPARATOR) -> resolvedCmd.replace(STREAM_PARAM_WITH_SEPARATOR, STREAM_PARAM + streamToken + "&")
            resolvedCmd.endsWith(STREAM_PARAM) -> resolvedCmd + streamToken
            else -> resolvedCmd
        }
    }

    private fun extractStreamToken(seriesParam: String?): String {
        if (isBlank(seriesParam)) return ""
        var trimmed = seriesParam.orEmpty().trim()
        val colonIndex = trimmed.indexOf(':')
        if (colonIndex > 0) trimmed = trimmed.substring(0, colonIndex)
        return trimmed.replace(Regex("\\D"), "")
    }
}
