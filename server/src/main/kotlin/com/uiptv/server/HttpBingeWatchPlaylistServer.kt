package com.uiptv.server

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.BingeWatchService
import com.uiptv.util.AppLog
import com.uiptv.util.ServerUtils.generateM3u8Response
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils.isBlank
import java.io.IOException

class HttpBingeWatchPlaylistServer : HttpHandler {
    companion object {
        private const val LOG_PREFIX = "BingeWatch: "
    }

    @Throws(IOException::class)
    override fun handle(exchange: HttpExchange) {
        val token = getParam(exchange, "token")
        AppLog.addInfoLog(HttpBingeWatchPlaylistServer::class.java, LOG_PREFIX + "HTTP playlist request token=" + (token ?: ""))
        val playlist = BingeWatchService.getInstance().renderPlaylist(token)
        if (isBlank(token) || isBlank(playlist)) {
            AppLog.addWarningLog(HttpBingeWatchPlaylistServer::class.java, LOG_PREFIX + "HTTP playlist request failed token=" + (token ?: ""))
            exchange.sendResponseHeaders(404, -1)
            return
        }
        AppLog.addInfoLog(HttpBingeWatchPlaylistServer::class.java, LOG_PREFIX + "HTTP playlist response token=$token length=${playlist.length}")
        generateM3u8Response(exchange, playlist, "binge-watch-$token.m3u8")
    }
}
