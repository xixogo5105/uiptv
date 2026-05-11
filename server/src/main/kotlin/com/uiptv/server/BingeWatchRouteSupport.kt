package com.uiptv.server

import com.uiptv.service.BingeWatchService
import com.uiptv.util.AppLog

data class BingeWatchPlaylistPayload(
    val fileName: String,
    val body: String
)

data class BingeWatchEntryResult(
    val statusCode: Int,
    val location: String? = null,
    val message: String? = null
)

object BingeWatchRouteSupport {
    private const val logPrefix = "BingeWatch: "

    @JvmOverloads
    fun renderPlaylist(token: String?, service: BingeWatchService = BingeWatchService()): BingeWatchPlaylistPayload? {
        AppLog.addInfoLog(UIptvServer::class.java, "${logPrefix}HTTP playlist request token=${token ?: ""}")
        val playlist = service.renderPlaylist(token)
        if (token.isNullOrBlank() || playlist.isBlank()) {
            AppLog.addWarningLog(UIptvServer::class.java, "${logPrefix}HTTP playlist request failed token=${token ?: ""}")
            return null
        }
        AppLog.addInfoLog(UIptvServer::class.java, "${logPrefix}HTTP playlist response token=$token length=${playlist.length}")
        return BingeWatchPlaylistPayload("binge-watch-$token.m3u8", playlist)
    }

    @JvmOverloads
    fun resolveEntry(
        method: String,
        token: String?,
        episodeId: String?,
        service: BingeWatchService = BingeWatchService()
    ): BingeWatchEntryResult {
        AppLog.addInfoLog(UIptvServer::class.java, "${logPrefix}HTTP entry request method=${AppLog.sanitizeValue(method)}")
        if (!method.equals("GET", true) && !method.equals("HEAD", true)) {
            return BingeWatchEntryResult(statusCode = 405)
        }
        if (token.isNullOrBlank() || episodeId.isNullOrBlank()) {
            AppLog.addWarningLog(UIptvServer::class.java, "${logPrefix}HTTP entry missing params token=${AppLog.sanitizeValue(token ?: "")} episodeId=${AppLog.sanitizeValue(episodeId ?: "")}")
            return BingeWatchEntryResult(statusCode = 404)
        }
        return try {
            val resolved = service.resolveEpisode(token, episodeId)
            if (resolved?.url.isNullOrBlank()) {
                AppLog.addWarningLog(UIptvServer::class.java, "${logPrefix}HTTP entry resolve failed token=${AppLog.sanitizeValue(token)} episodeId=${AppLog.sanitizeValue(episodeId)}")
                BingeWatchEntryResult(statusCode = 404, message = "Binge watch item not found.")
            } else {
                AppLog.addInfoLog(UIptvServer::class.java, "${logPrefix}HTTP entry redirect token=${AppLog.sanitizeValue(token)} episodeId=${AppLog.sanitizeValue(episodeId)} location=${AppLog.sanitizeValue(resolved.url)}")
                BingeWatchEntryResult(statusCode = 307, location = resolved.url)
            }
        } catch (ex: Exception) {
            AppLog.addErrorLog(UIptvServer::class.java, "${logPrefix}HTTP entry exception token=${AppLog.sanitizeValue(token)} episodeId=${AppLog.sanitizeValue(episodeId)} error=${AppLog.sanitizeValue(ex.message)}")
            BingeWatchEntryResult(statusCode = 502, message = "Unable to resolve binge watch episode: ${ex.message}")
        }
    }
}
