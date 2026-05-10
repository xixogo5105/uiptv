package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.util.AccountType.XTREME_API
import com.uiptv.util.ServerUrlUtil
import com.uiptv.util.StringUtils.isBlank
import org.json.JSONObject
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CopyOnWriteArraySet

object PlayerService {
    private val log = LoggerFactory.getLogger(PlayerService::class.java)
    private val playbackResolvedListeners = CopyOnWriteArraySet<PlaybackResolvedListener>()
    private val seriesWatchStateServiceProvider: () -> SeriesWatchStateService = { SeriesWatchStateService.getInstance() }
    private val xtremePlayerService = XtremePlayerService()
    private val stalkerPortalPlayerService = StalkerPortalPlayerService()
    private val predefinedPlayerService = PredefinedPlayerService()

    init {
        addPlaybackResolvedListener { account, channel, seriesId, parentSeriesId, categoryId ->
            seriesWatchStateServiceProvider().onPlaybackResolved(account, channel, seriesId, parentSeriesId, categoryId)
        }
    }

    @JvmStatic
    fun getInstance(): PlayerService = this

    @Throws(IOException::class)
    fun get(account: Account, channel: Channel): PlayerResponse = get(account, channel, "", "", "")

    @Throws(IOException::class)
    fun get(account: Account, channel: Channel, series: String?): PlayerResponse = get(account, channel, series, null, "")

    @Throws(IOException::class)
    fun get(account: Account, channel: Channel, series: String?, parentSeriesId: String?): PlayerResponse = get(account, channel, series, parentSeriesId, "")

    @Throws(IOException::class)
    fun get(account: Account, channel: Channel, series: String?, parentSeriesId: String?, categoryId: String?): PlayerResponse {
        val service = getPlayerService(account)
        val response = service.get(account, channel, series.orEmpty(), parentSeriesId.orEmpty(), categoryId.orEmpty())
        if (response != null && response.url != null) {
            val originalUrl = response.url
            val sanitizedUrl = sanitizeAndEncodeUrl(originalUrl)
            if (originalUrl != sanitizedUrl) {
                log.info("Original URL contained invalid characters. Re-encoding.")
                log.info("Original: {}", originalUrl)
                log.info("Encoded:  {}", sanitizedUrl)
                response.url = sanitizedUrl
            }
        }
        notifyPlaybackResolved(account, channel, series.orEmpty(), parentSeriesId.orEmpty(), categoryId.orEmpty())
        return response
    }
    fun sanitizeAndEncodeUrl(url: String?): String {
        if (isBlank(url)) {
            return url.orEmpty()
        }
        val questionMarkIndex = url!!.indexOf('?')
        if (questionMarkIndex == -1) {
            return url
        }
        val baseUrl = url.substring(0, questionMarkIndex)
        val query = url.substring(questionMarkIndex + 1)
        if (isBlank(query)) {
            return url
        }
        val newQuery = StringBuilder()
        query.split("&").forEach { param ->
            if (!isBlank(param)) {
                appendSanitizedParam(newQuery, param)
            }
        }
        return "$baseUrl?$newQuery"
    }

    private fun appendSanitizedParam(newQuery: StringBuilder, param: String) {
        val parts = splitQueryParam(param)
        try {
            appendQueryDelimiter(newQuery)
            newQuery.append(reencodeQueryPart(parts.key))
            if (parts.value != null) {
                newQuery.append("=").append(reencodeQueryPart(parts.value))
            }
        } catch (_: Exception) {
            appendQueryDelimiter(newQuery)
            newQuery.append(param)
        }
    }

    private fun splitQueryParam(param: String): QueryParamParts {
        val eqIndex = param.indexOf('=')
        return if (eqIndex >= 0) QueryParamParts(param.substring(0, eqIndex), param.substring(eqIndex + 1)) else QueryParamParts(param, null)
    }

    private fun reencodeQueryPart(value: String): String {
        val decoded = URLDecoder.decode(value, StandardCharsets.UTF_8)
        return URLEncoder.encode(decoded, StandardCharsets.UTF_8)
    }

    private fun appendQueryDelimiter(newQuery: StringBuilder) {
        if (newQuery.isNotEmpty()) {
            newQuery.append("&")
        }
    }

    private data class QueryParamParts(val key: String, val value: String?)

    private fun getPlayerService(account: Account): AccountPlayerService =
        when {
            account.type == XTREME_API -> xtremePlayerService
            Account.PRE_DEFINED_URLS.contains(account.type) -> predefinedPlayerService
            else -> stalkerPortalPlayerService
        }
    fun addPlaybackResolvedListener(listener: PlaybackResolvedListener?) {
        if (listener != null) {
            playbackResolvedListeners.add(listener)
        }
    }
    fun removePlaybackResolvedListener(listener: PlaybackResolvedListener?) {
        if (listener != null) {
            playbackResolvedListeners.remove(listener)
        }
    }
    fun isDrmProtected(channel: Channel?): Boolean =
        channel != null && (!isBlank(channel.drmType) ||
            !isBlank(channel.drmLicenseUrl) ||
            !isBlank(channel.clearKeysJson) ||
            !isBlank(channel.inputstreamaddon) ||
            !isBlank(channel.manifestType))
    fun buildDrmBrowserPlaybackUrl(account: Account?, channel: Channel?, categoryId: String?, mode: String?, seriesParentId: String?, seriesCategoryId: String?): String {
        val payload = JSONObject()
        payload.put("mode", normalizeMode(mode, account))
        payload.put("accountId", if (account == null) "" else safe(account.dbId))
        payload.put("categoryId", safe(categoryId))
        payload.put("seriesParentId", safe(seriesParentId))
        payload.put("seriesCategoryId", safe(seriesCategoryId))
        payload.put("channel", buildChannelPayload(channel))
        val encoded = Base64.getUrlEncoder().withoutPadding().encodeToString(payload.toString().toByteArray(StandardCharsets.UTF_8))
        return localServerOrigin() + "/player.html?launch=" + URLEncoder.encode(encoded, StandardCharsets.UTF_8) + "&v=20260301f"
    }
    fun buildDrmBrowserPlaybackUrl(account: Account?, channel: Channel?, categoryId: String?, mode: String?): String =
        buildDrmBrowserPlaybackUrl(account, channel, categoryId, mode, "", "")

    private fun localServerOrigin(): String = ServerUrlUtil.getLocalServerUrl()

    private fun buildChannelPayload(channel: Channel?): JSONObject {
        val channelJson = JSONObject()
        channelJson.put("dbId", safeChannelValue(channel) { it.dbId })
        channelJson.put("channelId", safeChannelValue(channel) { it.channelId })
        channelJson.put("name", safeChannelValue(channel) { it.name })
        channelJson.put("logo", safeChannelValue(channel) { it.logo })
        channelJson.put("cmd", safeChannelValue(channel) { it.cmd })
        channelJson.put("cmd_1", safeChannelValue(channel) { it.cmd_1 })
        channelJson.put("cmd_2", safeChannelValue(channel) { it.cmd_2 })
        channelJson.put("cmd_3", safeChannelValue(channel) { it.cmd_3 })
        channelJson.put("drmType", safeChannelValue(channel) { it.drmType })
        channelJson.put("drmLicenseUrl", safeChannelValue(channel) { it.drmLicenseUrl })
        channelJson.put("clearKeysJson", safeChannelValue(channel) { it.clearKeysJson })
        channelJson.put("inputstreamaddon", safeChannelValue(channel) { it.inputstreamaddon })
        channelJson.put("manifestType", safeChannelValue(channel) { it.manifestType })
        channelJson.put("season", safeChannelValue(channel) { it.season })
        channelJson.put("episodeNum", safeChannelValue(channel) { it.episodeNum })
        return channelJson
    }

    private fun safeChannelValue(channel: Channel?, getter: (Channel) -> String?): String = if (channel == null) "" else safe(getter(channel))

    private fun normalizeMode(mode: String?, account: Account?): String {
        val normalized = safe(mode).lowercase()
        if (normalized == "itv" || normalized == "vod" || normalized == "series") {
            return normalized
        }
        if (account?.action != null) {
            val derived = safe(account.action.name).lowercase()
            if (derived == "itv" || derived == "vod" || derived == "series") {
                return derived
            }
        }
        return "itv"
    }

    private fun safe(value: String?): String {
        if (value == null) {
            return ""
        }
        val normalized = value.trim()
        if (normalized.equals("null", true) || normalized.equals("undefined", true)) {
            return ""
        }
        return normalized
    }

    private fun notifyPlaybackResolved(account: Account, channel: Channel, seriesId: String, parentSeriesId: String, categoryId: String) {
        playbackResolvedListeners.forEach { listener ->
            try {
                listener.onPlaybackResolved(account, channel, seriesId, parentSeriesId, categoryId)
            } catch (_: Exception) {
            }
        }
    }

    fun interface PlaybackResolvedListener {
        fun onPlaybackResolved(account: Account, channel: Channel, seriesId: String, parentSeriesId: String, categoryId: String)
    }
}
