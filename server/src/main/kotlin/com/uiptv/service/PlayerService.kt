package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.util.AccountType.XTREME_API
import com.uiptv.util.ServerUrlUtil
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.koinOrNull
import org.koin.core.context.GlobalContext
import org.slf4j.LoggerFactory
import java.io.IOException
import java.net.URLDecoder
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.Base64
import java.util.concurrent.CopyOnWriteArraySet
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.SerialName

class PlayerService(
    private val seriesWatchStateService: SeriesWatchStateService = SeriesWatchStateService
) {
    private val log = LoggerFactory.getLogger(PlayerService::class.java)
    private val playbackResolvedListeners = CopyOnWriteArraySet<PlaybackResolvedListener>()
    private val xtremePlayerService = XtremePlayerService()
    private val stalkerPortalPlayerService = StalkerPortalPlayerService()
    private val predefinedPlayerService = PredefinedPlayerService()
    private val json = Json { explicitNulls = false }

    init {
        addPlaybackResolvedListener { account, channel, seriesId, parentSeriesId, categoryId ->
            seriesWatchStateService.onPlaybackResolved(account, channel, seriesId, parentSeriesId, categoryId)
        }
    }

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
        if (response.url != null) {
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
        val payload = DrmBrowserLaunchPayload(
            mode = normalizeMode(mode, account),
            accountId = if (account == null) "" else safe(account.dbId),
            categoryId = safe(categoryId),
            seriesParentId = safe(seriesParentId),
            seriesCategoryId = safe(seriesCategoryId),
            channel = buildChannelPayload(channel)
        )
        val encoded = Base64.getUrlEncoder().withoutPadding()
            .encodeToString(json.encodeToString(payload).toByteArray(StandardCharsets.UTF_8))
        return localServerOrigin() + "/player.html?launch=" + URLEncoder.encode(encoded, StandardCharsets.UTF_8) + "&v=20260301f"
    }
    fun buildDrmBrowserPlaybackUrl(account: Account?, channel: Channel?, categoryId: String?, mode: String?): String =
        buildDrmBrowserPlaybackUrl(account, channel, categoryId, mode, "", "")

    private fun localServerOrigin(): String = ServerUrlUtil.getLocalServerUrl()

    private fun buildChannelPayload(channel: Channel?): DrmBrowserLaunchChannelPayload =
        DrmBrowserLaunchChannelPayload(
            dbId = safeChannelValue(channel) { it.dbId },
            channelId = safeChannelValue(channel) { it.channelId },
            name = safeChannelValue(channel) { it.name },
            logo = safeChannelValue(channel) { it.logo },
            cmd = safeChannelValue(channel) { it.cmd },
            cmd1 = safeChannelValue(channel) { it.cmd_1 },
            cmd2 = safeChannelValue(channel) { it.cmd_2 },
            cmd3 = safeChannelValue(channel) { it.cmd_3 },
            drmType = safeChannelValue(channel) { it.drmType },
            drmLicenseUrl = safeChannelValue(channel) { it.drmLicenseUrl },
            clearKeysJson = safeChannelValue(channel) { it.clearKeysJson },
            inputstreamaddon = safeChannelValue(channel) { it.inputstreamaddon },
            manifestType = safeChannelValue(channel) { it.manifestType },
            season = safeChannelValue(channel) { it.season },
            episodeNum = safeChannelValue(channel) { it.episodeNum }
        )

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

    companion object {
        private val defaultInstance by lazy { PlayerService() }

        @JvmStatic
        fun getInstance(): PlayerService =
            koinOrNull<PlayerService>() ?: defaultInstance
    }

    @Serializable
    private data class DrmBrowserLaunchPayload(
        val mode: String,
        val accountId: String,
        val categoryId: String,
        val seriesParentId: String,
        val seriesCategoryId: String,
        val channel: DrmBrowserLaunchChannelPayload
    )

    @Serializable
    private data class DrmBrowserLaunchChannelPayload(
        val dbId: String,
        val channelId: String,
        val name: String,
        val logo: String,
        val cmd: String,
        @SerialName("cmd_1")
        val cmd1: String,
        @SerialName("cmd_2")
        val cmd2: String,
        @SerialName("cmd_3")
        val cmd3: String,
        val drmType: String,
        val drmLicenseUrl: String,
        val clearKeysJson: String,
        val inputstreamaddon: String,
        val manifestType: String,
        val season: String,
        val episodeNum: String
    )
}
