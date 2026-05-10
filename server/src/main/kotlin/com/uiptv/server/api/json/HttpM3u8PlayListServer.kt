package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.PlaylistExportService
import com.uiptv.util.ServerUtils.generateM3u8Response
import com.uiptv.util.ServerUtils.getParam
import java.io.IOException

class HttpM3u8PlayListServer : HttpHandler {
    private val playlistExportService = PlaylistExportService()

    data class PlaylistDocument(val content: String, val fileName: String)

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val document = buildPlaylist(getParam(ex, "accountId"), getParam(ex, "categoryId"), getParam(ex, "channelId"))
        generateM3u8Response(ex, document.content, document.fileName)
    }

    fun buildPlaylist(accountId: String?, categoryId: String?, channelId: String?): PlaylistDocument =
        playlistExportService.buildSingleChannelPlaylist(accountId, categoryId, channelId)
            .let { PlaylistDocument(it.content, it.fileName) }
}
