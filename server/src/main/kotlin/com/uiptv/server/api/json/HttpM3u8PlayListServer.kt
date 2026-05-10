package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.ChannelDb
import com.uiptv.service.AccountService
import com.uiptv.service.HandshakeService
import com.uiptv.service.PlayerService
import com.uiptv.util.M3uPlaylistUtils.escapeAttributeValue
import com.uiptv.util.M3uPlaylistUtils.sanitizeTitle
import com.uiptv.util.ServerUtils.generateM3u8Response
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils
import java.io.IOException
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8

class HttpM3u8PlayListServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val account = AccountService.getInstance().getById(getParam(ex, "accountId") ?: "")
        val channel = ChannelDb.get().getChannelById(getParam(ex, "channelId") ?: "", getParam(ex, "categoryId") ?: "")
        if (account == null || channel == null) {
            generateM3u8Response(ex, "", "playlist.m3u8")
            return
        }
        HandshakeService.getInstance().hardTokenRefresh(account)
        val originalCmd = channel.cmd ?: ""
        channel.cmd = URLDecoder.decode(originalCmd, UTF_8)

        val playerResponse = PlayerService.getInstance().get(account, channel)
        val cmd = playerResponse.url
        channel.cmd = originalCmd

        val channelName = sanitizeTitle(channel.name)
        val response = "#EXTM3U\n" +
            "#EXTINF:-1 tvg-id=\"" + escapeAttributeValue(account.dbId) +
            "\" tvg-name=\"" + escapeAttributeValue(channelName) +
            "\" group-title=\"" + escapeAttributeValue(account.accountName) +
            "\"," + channelName + "\n" + StringUtils.EMPTY + cmd + "\n"
        generateM3u8Response(ex, response, "${getParam(ex, "accountId")}-${getParam(ex, "categoryId")}-${getParam(ex, "channelId")}.m3u8")
    }
}
