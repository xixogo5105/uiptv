package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.M3U8PublicationService
import com.uiptv.util.ServerUtils.generateM3u8Response
import java.io.IOException

class HttpIptvM3u8Server : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val response = M3U8PublicationService.getInstance().getPublishedM3u8(ex.requestHeaders.getFirst("Host"))
        val filename = if (ex.requestURI.path.endsWith(".m3u")) "iptv.m3u" else "iptv.m3u8"
        generateM3u8Response(ex, response, filename)
    }
}
