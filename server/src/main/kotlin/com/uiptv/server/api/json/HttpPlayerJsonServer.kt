package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.ChannelDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.VodChannelDb
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.service.BingeWatchService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.FfmpegService
import com.uiptv.service.PlayerRequestResolver
import com.uiptv.service.WebPlayerApiService
import com.uiptv.util.AppLog
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.generateResponseText
import com.uiptv.util.ServerUtils.getParam
import java.io.IOException

class HttpPlayerJsonServer(
    private val playerRequestResolverProvider: () -> PlayerRequestResolver = {
        PlayerRequestResolver(
            bookmarkService = BookmarkService.getInstance(),
            accountService = AccountService.getInstance(),
            playerService = com.uiptv.service.PlayerService.getInstance(),
            seriesCategoryDb = SeriesCategoryDb.get(),
            vodChannelDb = VodChannelDb.get(),
            channelDb = ChannelDb.get()
        )
    },
    private val webPlayerApiServiceProvider: () -> WebPlayerApiService = {
        val playerRequestResolver = playerRequestResolverProvider()
        WebPlayerApiService(
            accountService = AccountService.getInstance(),
            configurationService = ConfigurationService.getInstance(),
            ffmpegService = FfmpegService.getInstance(),
            bingeWatchService = BingeWatchService.getInstance(),
            playerRequestResolver = playerRequestResolver
        )
    }
) : HttpHandler {
    companion object {
        const val SEASON = "season"
        const val EPISODE_NUM = "episodeNum"
        const val MANIFEST_TYPE = "manifestType"
        const val INPUTSTREAMADDON = "inputstreamaddon"
        const val CLEAR_KEYS_JSON = "clearKeysJson"
        const val DRM_LICENSE_URL = "drmLicenseUrl"

        private const val QUERY_PARAM_PREFER_HLS = "preferHls"
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        try {
            generateJsonResponse(ex, buildJsonPlaybackResponse(ex.requestURI.path, queryParams(ex)))
        } catch (e: Exception) {
            if (isClientDisconnect(e)) {
                return
            }
            AppLog.addErrorLog(HttpPlayerJsonServer::class.java, "HttpPlayerJsonServer failed: $e")
            try {
                generateResponseText(ex, 500, "player-error")
            } catch (ioException: IOException) {
                if (!isClientDisconnect(ioException)) {
                    throw ioException
                }
            }
        }
    }

    fun buildJsonPlaybackResponse(requestPath: String?, params: Map<String, String?>): String =
        webPlayerApiServiceProvider().buildJsonPlaybackResponse(requestPath, params)

    @Suppress("unused")
    private fun sanitizeParam(value: String?): String = webPlayerApiServiceProvider().sanitizeParam(value)

    @Suppress("unused")
    private fun isHvecEnabled(value: String?): Boolean = webPlayerApiServiceProvider().isHvecEnabled(value)

    @Suppress("unused")
    private fun normalizeWebPlaybackUrl(mode: String, url: String): String =
        webPlayerApiServiceProvider().normalizeWebPlaybackUrl(mode, url)

    @Suppress("unused")
    private fun downgradeHttpsToHttp(url: String): String = webPlayerApiServiceProvider().downgradeHttpsToHttp(url)

    @Suppress("unused")
    private fun shouldForceWebHlsForUrl(mode: String, url: String): Boolean =
        webPlayerApiServiceProvider().shouldForceWebHlsForUrl(mode, url)

    @Suppress("unused")
    private fun applyWebPlaybackProcessing(response: com.uiptv.model.PlayerResponse?, mode: String, hvec: String?, preferHls: Boolean) {
        webPlayerApiServiceProvider().applyWebPlaybackProcessing(response, mode, hvec, preferHls)
    }

    private fun queryParams(ex: HttpExchange): Map<String, String?> = buildMap {
        listOf(
            "mode", "hvec", QUERY_PARAM_PREFER_HLS, "bookmarkId", "accountId", "bingeWatchToken", "episodeId",
            "categoryId", "channelId", "url", "seriesParentId", "seriesId", "name", "logo", "cmd", "cmd_1",
            "cmd_2", "cmd_3", "drmType", DRM_LICENSE_URL, CLEAR_KEYS_JSON, INPUTSTREAMADDON, MANIFEST_TYPE,
            SEASON, EPISODE_NUM
        ).forEach { key -> put(key, getParam(ex, key)) }
    }

    private fun isClientDisconnect(throwable: Throwable?): Boolean {
        var current = throwable
        while (current != null) {
            if (current is IOException) {
                val message = current.message.orEmpty().trim().lowercase()
                if (
                    message.contains("broken pipe") ||
                    message.contains("connection reset") ||
                    message.contains("stream closed")
                ) {
                    return true
                }
            }
            current = current.cause
        }
        return false
    }
}
