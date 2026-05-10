package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.util.PlayerUrlUtils
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import java.io.IOException

class XtremePlayerService : AccountPlayerService {
    @Throws(IOException::class)
    override fun get(account: Account, channel: Channel, series: String?, parentSeriesId: String?, categoryId: String?): PlayerResponse {
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService::class.java, "Resolving playback URL for Xtreme account: ${account.accountName}")
        val rawUrl = constructXtremeUrl(account, channel, parentSeriesId)
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService::class.java, "Constructed fresh Xtreme URL for ${account.action}")
        val finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl))
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService::class.java, "Final resolved URL: $finalUrl")
        com.uiptv.util.AppLog.addInfoLog(XtremePlayerService::class.java, "Playback URL resolved.")
        val response = PlayerResponse(finalUrl)
        response.setFromChannel(channel, account)
        return response
    }

    private fun constructXtremeUrl(account: Account, channel: Channel?, parentSeriesId: String?): String {
        if (channel == null) return ""
        val fallbackCmd = PlayerUrlUtils.resolveBestChannelCmd(account, channel)
        if (isNotBlank(fallbackCmd)) {
            com.uiptv.util.AppLog.addInfoLog(XtremePlayerService::class.java, "Found channel cmd: $fallbackCmd")
        }
        val baseUrl = resolveBaseUrl(account)
        if (isBlank(baseUrl)) {
            com.uiptv.util.AppLog.addWarningLog(XtremePlayerService::class.java, "Xtreme base URL is blank. Falling back to channel cmd.")
            return fallbackCmd
        }
        if (!hasRequiredParts(account, channel)) {
            com.uiptv.util.AppLog.addWarningLog(XtremePlayerService::class.java, "Xtreme URL parts missing (username/password/channelId). Falling back to channel cmd.")
            return fallbackCmd
        }
        val extension = inferExtensionFromCmd(fallbackCmd)
        val type = resolveStreamType(account, parentSeriesId)
        val resolvedExtension = defaultExtension(extension, type)
        return baseUrl + type + "/" + account.username + "/" + account.password + "/" + channel.channelId + "." + resolvedExtension
    }

    private fun resolveBaseUrl(account: Account): String {
        var baseUrl = if (isBlank(account.m3u8Path)) account.url else account.m3u8Path
        if (isBlank(baseUrl)) return ""
        val playerApiIndex = baseUrl!!.indexOf("player_api.php")
        if (playerApiIndex >= 0) baseUrl = baseUrl.substring(0, playerApiIndex)
        return if (baseUrl.endsWith("/")) baseUrl else "$baseUrl/"
    }

    private fun hasRequiredParts(account: Account, channel: Channel): Boolean =
        isNotBlank(account.username) && isNotBlank(account.password) && isNotBlank(channel.channelId)

    private fun resolveStreamType(account: Account, parentSeriesId: String?): String =
        when {
            isNotBlank(parentSeriesId) || account.action == Account.AccountAction.series -> "series"
            account.action == Account.AccountAction.vod -> "movie"
            else -> "live"
        }

    private fun defaultExtension(extension: String?, type: String): String =
        if (isNotBlank(extension)) extension!! else if (type == "live") "ts" else "mp4"

    private fun inferExtensionFromCmd(cmd: String?): String {
        if (isBlank(cmd)) return ""
        val playable = PlayerUrlUtils.extractPlayableUrl(cmd)
        if (isBlank(playable)) return ""
        val noQuery = playable.substringBefore('?')
        val clean = noQuery.substringBefore('#')
        val lastSegment = clean.substringAfterLast('/', clean)
        val dotIndex = lastSegment.lastIndexOf('.')
        if (dotIndex <= 0 || dotIndex >= lastSegment.length - 1) return ""
        val ext = lastSegment.substring(dotIndex + 1)
        return if (ext.matches(Regex("^[a-zA-Z0-9]+$"))) ext else ""
    }
}
