package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.util.AppLog
import com.uiptv.util.PlayerUrlUtils
import java.io.IOException

class PredefinedPlayerService : AccountPlayerService {
    @Throws(IOException::class)
    override fun get(
        account: Account,
        channel: Channel,
        series: String?,
        parentSeriesId: String?,
        categoryId: String?
    ): PlayerResponse {
        AppLog.addInfoLog(PredefinedPlayerService::class.java, "Resolving playback URL for Predefined account: " + account.accountName)
        val rawUrl = PlayerUrlUtils.resolveBestChannelCmd(account, channel)
        AppLog.addInfoLog(PredefinedPlayerService::class.java, "Using direct channel command for " + account.type + ".")
        val finalUrl = PlayerUrlUtils.normalizeStreamUrl(account, PlayerUrlUtils.resolveAndProcessUrl(rawUrl))
        AppLog.addInfoLog(PredefinedPlayerService::class.java, "Final resolved URL: $finalUrl")
        AppLog.addInfoLog(PredefinedPlayerService::class.java, "Playback URL resolved.")
        val response = PlayerResponse(finalUrl)
        response.setFromChannel(channel, account)
        return response
    }
}
