package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.server.api.dto.PlayerPlaybackBingeWatchDto
import com.uiptv.server.api.dto.PlayerPlaybackBingeWatchItemDto
import com.uiptv.server.api.dto.PlayerPlaybackChannelDto
import com.uiptv.server.api.dto.PlayerPlaybackDrmDto
import com.uiptv.server.api.dto.PlayerPlaybackResponseDto
import com.uiptv.util.koinOrNull
import com.uiptv.util.ServerUrlUtil
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

@Suppress("java:S1075")
class WebPlayerApiService @JvmOverloads constructor(
    private val accountService: AccountService = AccountService,
    private val configurationService: ConfigurationService = ConfigurationService,
    private val ffmpegService: FfmpegService = FfmpegService,
    private val bingeWatchService: BingeWatchService = koinOrNull<BingeWatchService>() ?: BingeWatchService(),
    private val playerRequestResolver: PlayerRequestResolver = koinOrNull<PlayerRequestResolver>() ?: PlayerRequestResolver()
) {
    private val json = Json {
        explicitNulls = false
    }

    companion object {
        const val SEASON = "season"
        const val EPISODE_NUM = "episodeNum"
        const val MANIFEST_TYPE = "manifestType"
        const val INPUTSTREAMADDON = "inputstreamaddon"
        const val CLEAR_KEYS_JSON = "clearKeysJson"
        const val DRM_LICENSE_URL = "drmLicenseUrl"

        private const val PATH_PLAYER_BINGEWATCH = "/player/bingewatch"
        private const val PATH_PLAYER_LIVE = "/player/live"
        private const val PATH_PLAYER_SERIES = "/player/series"
        private const val PATH_PLAYER_VOD = "/player/vod"
        private const val JSON_KEY_STRATEGY_HINT = "strategyHint"
        private const val HTTP_PREFIX = "http://"
        private const val HTTPS_PREFIX = "https://"
        private const val MODE_SERIES = "series"
        private const val MODE_VOD = "vod"
        private const val MODE_ITV = "itv"
        private const val URL_FRAGMENT_DASH_MPD = ".mpd"
        private const val URL_FRAGMENT_HLS_M3U8 = ".m3u8"
        private const val URL_FRAGMENT_LOCAL_HLS = "/hls/stream.m3u8"
        private const val URL_FRAGMENT_PROXY_STREAM = "/proxy-stream?src="
        private const val URL_FRAGMENT_EXTENSION_TS = "extension=ts"
        private const val URL_SUFFIX_TS = ".ts"
        private const val URL_SUFFIX_MPEGTS = ".mpegts"
        private const val QUERY_PARAM_TOKEN = "token="
        private const val QUERY_PARAM_PLAY_TOKEN = "play_token="
        private const val QUERY_PARAM_PREFER_HLS = "preferHls"
        private const val PATH_LIVE_PLAY = "/live/play/"
        private const val PATH_PLAY_MOVIE = "/play/movie.php"
        private const val STRATEGY_HINT_SHAKA = "SHAKA"
        private const val STRATEGY_HINT_NATIVE_PROXY = "NATIVE_PROXY"
        private const val STRATEGY_HINT_NATIVE = "NATIVE"
        private const val WEB_VOD_STYLE_PLAYLIST = false
    }

    fun buildJsonPlaybackResponse(requestPath: String?, params: Map<String, String?>): String {
        return json.encodeToString(buildPlaybackResponse(requestPath, params))
    }

    fun buildPlaybackResponse(requestPath: String?, params: Map<String, String?>): PlayerPlaybackResponseDto {
        val mode = resolveRequestedMode(requestPath, params["mode"])
        val resolved = resolvePlayback(params, mode)
        applyWebPlaybackProcessing(resolved.response, mode, params["hvec"], isEnabledFlag(params[QUERY_PARAM_PREFER_HLS]))
        return buildPlaybackDto(resolved)
    }

    private fun resolvePlayback(params: Map<String, String?>, mode: String): ResolvedWebPlayback {
        val bookmarkId = params["bookmarkId"]
        val accountId = params["accountId"]
        val bingeWatchToken = params["bingeWatchToken"]
        val bingeWatchEpisodeId = params["episodeId"]
        val categoryId = params["categoryId"]
        val channelId = params["channelId"]
        val directUrl = params["url"]
        val seriesParentId = params["seriesParentId"]
        if (isNotBlank(bingeWatchToken)) {
            return resolveBingeWatchPlayback(buildRequestChannel(channelId, params), accountId, bingeWatchToken!!, bingeWatchEpisodeId)
        }
        if (isNotBlank(directUrl)) {
            return resolveDirectUrlPlayback(accountId, directUrl!!, buildRequestChannel(channelId, params))
        }
        if (isNotBlank(bookmarkId)) {
            return ResolvedWebPlayback(
                playerRequestResolver.resolveBookmarkPlayback(bookmarkId!!, mode, seriesParentId),
                "",
                "",
                emptyList()
            )
        }
        return ResolvedWebPlayback(
            resolveDirectPlayback(
                accountId,
                categoryId,
                channelId,
                mode,
                seriesParentId,
                params["seriesId"],
                buildRequestChannel(channelId, params)
            ),
            "",
            "",
            emptyList()
        )
    }

    private fun resolveRequestedMode(requestPath: String?, requestedMode: String?): String {
        if (isNotBlank(requestedMode)) return requestedMode!!
        return when (safe(requestPath).lowercase()) {
            PATH_PLAYER_BINGEWATCH, PATH_PLAYER_SERIES -> MODE_SERIES
            PATH_PLAYER_VOD -> MODE_VOD
            PATH_PLAYER_LIVE -> MODE_ITV
            else -> ""
        }
    }

    private fun resolveDirectUrlPlayback(accountId: String?, directUrl: String, requestChannel: Channel): ResolvedWebPlayback {
        val response = PlayerResponse(directUrl)
        val account = if (isBlank(accountId)) null else accountService.getById(accountId)
        if (hasChannelMetadata(requestChannel)) {
            response.setFromChannel(requestChannel, account)
        }
        return ResolvedWebPlayback(response, "", "", emptyList())
    }

    private fun resolveBingeWatchPlayback(requestChannel: Channel, accountId: String?, token: String, episodeId: String?): ResolvedWebPlayback {
        val items = bingeWatchService.getPlaylistItems(token)
        if (items.isEmpty()) {
            return ResolvedWebPlayback(PlayerResponse(""), token, "", emptyList())
        }
        val currentEpisodeId = if (isNotBlank(episodeId)) episodeId!! else items[0].episodeId
        val resolvedEpisode = bingeWatchService.resolveEpisode(token, currentEpisodeId)
        if (resolvedEpisode == null || isBlank(resolvedEpisode.url)) {
            return ResolvedWebPlayback(PlayerResponse(""), token, currentEpisodeId, items)
        }
        val response = PlayerResponse(resolvedEpisode.url)
        val account = if (isBlank(accountId)) null else accountService.getById(accountId)
        val channel = requestChannel
        if (isBlank(channel.name)) {
            channel.name = resolvedEpisode.title
        }
        items.firstOrNull { currentEpisodeId == it.episodeId }?.let { item ->
            if (isBlank(channel.season)) channel.season = item.season
            if (isBlank(channel.episodeNum)) channel.episodeNum = item.episodeNumber
        }
        response.setFromChannel(channel, account)
        return ResolvedWebPlayback(response, token, currentEpisodeId, items)
    }

    private fun resolveDirectPlayback(
        accountId: String?,
        categoryId: String?,
        channelId: String?,
        mode: String,
        seriesParentId: String?,
        seriesId: String?,
        requestChannel: Channel
    ): PlayerResponse {
        val account = accountService.getById(accountId)
        return playerRequestResolver.resolveDirectPlayback(account, categoryId, channelId, mode, seriesParentId, seriesId, requestChannel)
    }

    private fun buildRequestChannel(channelId: String?, params: Map<String, String?>): Channel =
        Channel().apply {
            this.channelId = channelId
            name = sanitizeParam(params["name"])
            logo = sanitizeParam(params["logo"])
            cmd = sanitizeParam(params["cmd"])
            cmd_1 = sanitizeParam(params["cmd_1"])
            cmd_2 = sanitizeParam(params["cmd_2"])
            cmd_3 = sanitizeParam(params["cmd_3"])
            drmType = sanitizeParam(params["drmType"])
            drmLicenseUrl = sanitizeParam(params[DRM_LICENSE_URL])
            clearKeysJson = sanitizeParam(params[CLEAR_KEYS_JSON])
            inputstreamaddon = sanitizeParam(params[INPUTSTREAMADDON])
            manifestType = sanitizeParam(params[MANIFEST_TYPE])
            season = sanitizeParam(params[SEASON])
            episodeNum = sanitizeParam(params[EPISODE_NUM])
        }

    private fun buildPlaybackDto(resolved: ResolvedWebPlayback): PlayerPlaybackResponseDto {
        val response = resolved.response
        return PlayerPlaybackResponseDto(
            url = response?.url ?: "",
            strategyHint = determineStrategyHint(response).takeIf(String::isNotBlank),
            channel = response?.channel?.takeIf(::hasChannelMetadata)?.let { channel ->
                PlayerPlaybackChannelDto(
                    channelId = safe(channel.channelId),
                    name = safe(channel.name),
                    logo = safe(channel.logo),
                    season = safe(channel.season),
                    episodeNum = safe(channel.episodeNum)
                )
            },
            title = response?.channel?.name?.takeIf(::isNotBlank),
            drm = response?.takeIf(::hasDrmMetadata)?.let { drm ->
                PlayerPlaybackDrmDto(
                    type = drm.drmType?.takeIf(::isNotBlank),
                    licenseUrl = drm.drmLicenseUrl?.takeIf(::isNotBlank),
                    clearKeys = parseClearKeysJson(drm.clearKeysJson),
                    inputstreamaddon = drm.inputstreamaddon?.takeIf(::isNotBlank),
                    manifestType = drm.manifestType?.takeIf(::isNotBlank)
                )
            },
            ffmpegMode = response?.ffmpegMode?.takeIf(::isNotBlank),
            bingeWatch = resolved.takeIf { isNotBlank(it.bingeWatchToken) && it.playlistItems.isNotEmpty() }?.let { binge ->
                PlayerPlaybackBingeWatchDto(
                    token = binge.bingeWatchToken,
                    currentEpisodeId = safe(binge.currentEpisodeId),
                    items = binge.playlistItems.map { item ->
                        PlayerPlaybackBingeWatchItemDto(
                            episodeId = safe(item.episodeId),
                            episodeName = safe(item.episodeName),
                            season = safe(item.season),
                            episodeNumber = safe(item.episodeNumber)
                        )
                    }
                )
            }
        )
    }

    fun applyWebPlaybackProcessing(response: PlayerResponse?, mode: String, hvec: String?, preferHls: Boolean) {
        if (response == null || isBlank(response.url)) {
            stopTransmuxingIfActive()
            return
        }
        val originalUrl = response.url ?: ""
        val normalizedUrl = normalizeWebPlaybackUrl(mode, originalUrl)
        response.url = normalizedUrl
        if (shouldBypassLocalProxyWebPlayback(response, mode, normalizedUrl)) {
            stopTransmuxingIfActive()
            return
        }
        if (!shouldStartTransmuxing(response, mode, originalUrl, preferHls)) {
            stopTransmuxingIfActive()
            if (shouldUseLocalProxyWebPlayback(mode, normalizedUrl)) {
                response.url = buildLocalProxyUrl(normalizedUrl)
            }
            return
        }
        val allowTranscoding = configurationService.read().enableFfmpegTranscoding
        applyTransmuxedPlayback(response, mode, originalUrl, hvec, preferHls, allowTranscoding)
    }

    private fun shouldStartTransmuxing(response: PlayerResponse, mode: String, originalUrl: String, preferHls: Boolean): Boolean {
        val forceWebHls = preferHls || shouldForceWebHls(mode, response) || shouldForceWebHlsForUrl(mode, originalUrl)
        if (shouldPreferDirectLivePlayback(mode, originalUrl) && !forceWebHls) return false
        return forceWebHls || ffmpegService.isTransmuxingNeeded(response.url ?: "")
    }

    private fun applyTransmuxedPlayback(response: PlayerResponse, mode: String, originalUrl: String, hvec: String?, preferHls: Boolean, allowTranscoding: Boolean) {
        val forceWebHls = preferHls || shouldForceWebHls(mode, response) || shouldForceWebHlsForUrl(mode, originalUrl)
        val sourceUrl = response.url ?: ""
        val vodStylePlaylist = WEB_VOD_STYLE_PLAYLIST
        if (startTransmuxing(sourceUrl, forceWebHls, vodStylePlaylist)) {
            setHlsPlayback(response, hvec, "transmux")
            return
        }
        if (allowTranscoding && startTranscoding(sourceUrl, forceWebHls, vodStylePlaylist)) {
            setHlsPlayback(response, hvec, "transcode")
            return
        }
        val fallbackUrl = if (forceWebHls) retryForcedWebHls(sourceUrl) else sourceUrl
        if (forceWebHls && fallbackUrl != sourceUrl) {
            if (startTransmuxing(fallbackUrl, true, vodStylePlaylist)) {
                setHlsPlayback(response, hvec, "transmux")
                return
            }
            if (allowTranscoding && startTranscoding(fallbackUrl, true, vodStylePlaylist)) {
                setHlsPlayback(response, hvec, "transcode")
                return
            }
        }
        stopTransmuxingIfActive()
        response.url = fallbackUrl
    }

    private fun startTransmuxing(sourceUrl: String, forceWebHls: Boolean, vodStylePlaylist: Boolean): Boolean {
        if (!forceWebHls) return tryStartTransmuxingInput(sourceUrl, vodStylePlaylist)
        if (tryStartTransmuxingInput(sourceUrl, vodStylePlaylist)) return true
        val proxied = buildLocalProxyUrl(sourceUrl)
        return sourceUrl != proxied && tryStartTransmuxingInput(proxied, vodStylePlaylist)
    }

    private fun startTranscoding(sourceUrl: String, forceWebHls: Boolean, vodStylePlaylist: Boolean): Boolean {
        if (!forceWebHls) return tryStartTranscodingInput(sourceUrl, vodStylePlaylist)
        if (tryStartTranscodingInput(sourceUrl, vodStylePlaylist)) return true
        val proxied = buildLocalProxyUrl(sourceUrl)
        return sourceUrl != proxied && tryStartTranscodingInput(proxied, vodStylePlaylist)
    }

    private fun stopTransmuxingIfActive() {
        try {
            ffmpegService.stopTransmuxing()
        } catch (_: Exception) {
        }
    }

    private fun tryStartTransmuxingInput(inputUrl: String, forceWebHls: Boolean): Boolean =
        try {
            ffmpegService.startTransmuxing(inputUrl, forceWebHls)
        } catch (_: Exception) {
            false
        }

    private fun tryStartTranscodingInput(inputUrl: String, forceWebHls: Boolean): Boolean =
        try {
            ffmpegService.startTranscoding(inputUrl, forceWebHls)
        } catch (_: Exception) {
            false
        }

    private fun retryForcedWebHls(sourceUrl: String): String {
        val downgraded = downgradeHttpsToHttp(sourceUrl)
        return if (downgraded == sourceUrl) sourceUrl else downgraded
    }

    private fun setHlsPlayback(response: PlayerResponse, hvec: String?, ffmpegMode: String) {
        response.url = if (isHvecEnabled(hvec)) "$URL_FRAGMENT_LOCAL_HLS?hvec=1" else URL_FRAGMENT_LOCAL_HLS
        response.manifestType = "hls"
        response.ffmpegMode = ffmpegMode
    }

    private fun shouldForceWebHls(mode: String, response: PlayerResponse?): Boolean {
        if (response == null || isBlank(response.url)) return false
        val normalizedMode = if (isBlank(mode)) "" else mode.trim().lowercase()
        return normalizedMode == MODE_SERIES && shouldForceSeriesWebHls((response.url ?: "").lowercase())
    }

    fun normalizeWebPlaybackUrl(mode: String, url: String): String {
        if (isBlank(url)) return url
        val normalizedMode = if (isBlank(mode)) "" else mode.trim().lowercase()
        if (MODE_VOD != normalizedMode && MODE_SERIES != normalizedMode) return url
        val lower = url.lowercase()
        return if (lower.startsWith(HTTPS_PREFIX) && (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE))) {
            HTTP_PREFIX + url.substring(HTTPS_PREFIX.length)
        } else {
            url
        }
    }

    fun downgradeHttpsToHttp(url: String): String {
        if (isBlank(url)) return url
        val lower = url.lowercase()
        return if (lower.startsWith(HTTPS_PREFIX) && (lower.contains(PATH_LIVE_PLAY) || lower.contains(PATH_PLAY_MOVIE))) {
            HTTP_PREFIX + url.substring(HTTPS_PREFIX.length)
        } else {
            url
        }
    }

    private fun buildLocalProxyUrl(sourceUrl: String): String =
        ServerUrlUtil.getLocalServerUrl() + URL_FRAGMENT_PROXY_STREAM + URLEncoder.encode(sourceUrl, StandardCharsets.UTF_8)

    private fun shouldBypassLocalProxyWebPlayback(response: PlayerResponse, mode: String, url: String): Boolean {
        if (isBlank(url)) return false
        val normalizedMode = if (isBlank(mode)) "" else mode.trim().lowercase()
        if (MODE_VOD != normalizedMode) return false
        val hasDrm = isNotBlank(response.drmType) || isNotBlank(response.drmLicenseUrl) || isNotBlank(response.clearKeysJson) ||
            isNotBlank(response.inputstreamaddon) || isNotBlank(response.manifestType)
        if (hasDrm) return false
        val lower = url.lowercase()
        return lower.contains(PATH_LIVE_PLAY) || hasTrailingNumericPath(lower)
    }

    fun shouldForceWebHlsForUrl(mode: String, url: String): Boolean {
        if (isBlank(url)) return false
        val normalizedMode = if (isBlank(mode)) "" else mode.trim().lowercase()
        return normalizedMode == MODE_SERIES && shouldForceSeriesWebHls(url.lowercase())
    }

    private fun shouldForceSeriesWebHls(lowerUrl: String): Boolean {
        if (isBlank(lowerUrl)) return false
        if (isAdaptivePlaybackUrl(lowerUrl) || hasKnownProgressiveVideoExtension(lowerUrl) || hasKnownProgressiveVideoQuery(lowerUrl)) return false
        return isForcedWebPath(lowerUrl)
    }

    private fun shouldUseLocalProxyWebPlayback(mode: String, url: String): Boolean {
        if (isBlank(url)) return false
        val normalizedMode = if (isBlank(mode)) "" else mode.trim().lowercase()
        if (MODE_VOD != normalizedMode && MODE_SERIES != normalizedMode) return false
        return isForcedWebPath(url.lowercase())
    }

    private fun shouldPreferDirectLivePlayback(mode: String, url: String): Boolean {
        val normalizedMode = if (isBlank(mode)) "" else mode.trim().lowercase()
        if (isBlank(url) || MODE_ITV != normalizedMode) return false
        val lowerUrl = url.lowercase()
        if (isAdaptivePlaybackUrl(lowerUrl) && !hasTokenizedAccess(lowerUrl)) return false
        return isLikelyMpegTsUrl(lowerUrl) || hasTrailingNumericPath(lowerUrl) || lowerUrl.contains(PATH_LIVE_PLAY)
    }

    private fun isLikelyMpegTsUrl(lowerUrl: String): Boolean {
        if (isBlank(lowerUrl)) return false
        val path = stripQuery(lowerUrl)
        return lowerUrl.contains(URL_FRAGMENT_EXTENSION_TS) || path.endsWith(URL_SUFFIX_TS) || path.endsWith(URL_SUFFIX_MPEGTS)
    }

    private fun isAdaptivePlaybackUrl(lowerUrl: String): Boolean =
        !isBlank(lowerUrl) && (lowerUrl.contains(URL_FRAGMENT_HLS_M3U8) || lowerUrl.contains(URL_FRAGMENT_DASH_MPD) || lowerUrl.contains(URL_FRAGMENT_LOCAL_HLS))

    private fun hasTokenizedAccess(lowerUrl: String): Boolean =
        !isBlank(lowerUrl) && (lowerUrl.contains(QUERY_PARAM_TOKEN) || lowerUrl.contains(QUERY_PARAM_PLAY_TOKEN))

    private fun determineStrategyHint(response: PlayerResponse?): String {
        if (response == null || isBlank(response.url)) return STRATEGY_HINT_NATIVE
        val lowerUrl = (response.url ?: "").lowercase()
        if (hasDrmMetadata(response) || isDashUrl(lowerUrl) || isLocalTransmuxedHls(lowerUrl)) return STRATEGY_HINT_SHAKA
        if (isLocalProxyUrl(lowerUrl) || isForcedWebPath(lowerUrl)) return STRATEGY_HINT_NATIVE_PROXY
        return STRATEGY_HINT_NATIVE
    }

    private fun isDashUrl(url: String): Boolean = url.contains(URL_FRAGMENT_DASH_MPD)
    private fun isLocalTransmuxedHls(url: String): Boolean = url.contains(URL_FRAGMENT_LOCAL_HLS)
    private fun isLocalProxyUrl(url: String): Boolean = url.contains(URL_FRAGMENT_PROXY_STREAM)

    private fun hasChannelMetadata(channel: Channel?): Boolean =
        channel != null && (
            isNotBlank(channel.channelId) || isNotBlank(channel.name) || isNotBlank(channel.logo) ||
                isNotBlank(channel.season) || isNotBlank(channel.episodeNum) || isNotBlank(channel.cmd) || isNotBlank(channel.manifestType)
            )

    private fun hasDrmMetadata(response: PlayerResponse?): Boolean =
        response != null && (isNotBlank(response.drmType) || isNotBlank(response.inputstreamaddon) || isNotBlank(response.manifestType))

    private fun parseClearKeysJson(clearKeysJson: String?): JsonObject? =
        if (isBlank(clearKeysJson)) null else try {
            json.parseToJsonElement(clearKeysJson.orEmpty()).jsonObject
        } catch (_: Exception) {
            null
        }

    private fun safe(value: String?): String {
        if (value == null) return ""
        val normalized = value.trim()
        return if ("null".equals(normalized, true) || "undefined".equals(normalized, true)) "" else normalized
    }

    fun sanitizeParam(value: String?): String = safe(value)
    fun isHvecEnabled(value: String?): Boolean = isEnabledFlag(value)

    private fun isEnabledFlag(value: String?): Boolean =
        when (safe(value).lowercase()) {
            "1", "true", "yes", "on" -> true
            else -> false
        }

    private fun isForcedWebPath(url: String): Boolean =
        !isBlank(url) && (
            url.contains(PATH_PLAY_MOVIE) || url.contains(PATH_LIVE_PLAY) || url.contains("type=movie") || url.contains("type=$MODE_SERIES") ||
                url.contains("extension=mp4") || url.contains("extension=mkv") || url.contains("extension=mpg") || url.contains("extension=mpeg") ||
                hasVideoFileExtension(url) || hasTrailingNumericPath(url)
            )

    private fun hasVideoFileExtension(url: String): Boolean {
        val path = stripQuery(url).lowercase()
        return path.endsWith(".mpg") || path.endsWith(".mpeg") || path.endsWith(".mkv") || path.endsWith(".avi") || path.endsWith(".wmv")
    }

    private fun hasKnownProgressiveVideoExtension(url: String): Boolean {
        val path = stripQuery(url).lowercase()
        return path.endsWith(".mp4") || path.endsWith(".m4v") || path.endsWith(".mov") || path.endsWith(".webm")
    }

    private fun hasKnownProgressiveVideoQuery(url: String): Boolean {
        if (isBlank(url)) return false
        val lower = url.lowercase()
        val streamLooksProgressive = lower.contains("stream=") && (
            lower.contains(".mp4") || lower.contains(".m4v") || lower.contains(".mov") || lower.contains(".webm")
            )
        return streamLooksProgressive ||
            lower.contains(".mp4&") || lower.contains(".m4v&") || lower.contains(".mov&") || lower.contains(".webm&") ||
            lower.contains("extension=mp4") || lower.contains("extension=m4v") || lower.contains("extension=mov") || lower.contains("extension=webm")
    }

    private fun stripQuery(url: String): String {
        val queryIndex = url.indexOf('?')
        return if (queryIndex >= 0) url.substring(0, queryIndex) else url
    }

    private fun hasTrailingNumericPath(url: String): Boolean {
        val path = stripQuery(url).trimEnd('/')
        val lastSegment = path.substringAfterLast('/', "")
        return lastSegment.isNotEmpty() && lastSegment.all(Char::isDigit)
    }

    private data class ResolvedWebPlayback(
        val response: PlayerResponse?,
        val bingeWatchToken: String,
        val currentEpisodeId: String,
        val playlistItems: List<BingeWatchService.PlaylistItem>
    )
}
