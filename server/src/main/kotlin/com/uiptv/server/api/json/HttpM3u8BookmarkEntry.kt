package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.HandshakeService
import com.uiptv.service.PlaylistExportService
import com.uiptv.service.PlayerService
import com.uiptv.service.PlayerRequestResolver
import com.uiptv.util.ServerUtils.generateResponseText
import com.uiptv.util.ServerUtils.getParam
import java.io.IOException

class HttpM3u8BookmarkEntry(
    private val playerRequestResolver: PlayerRequestResolver = PlayerRequestResolver(),
    private val playlistExportService: PlaylistExportService = PlaylistExportService(
        accountService = AccountService.getInstance(),
        bookmarkService = BookmarkService.getInstance(),
        configurationService = ConfigurationService.getInstance(),
        handshakeService = HandshakeService.getInstance(),
        playerService = PlayerService.getInstance(),
        playerRequestResolver = playerRequestResolver
    )
) : HttpHandler {
    companion object {
        private const val ALLOW = "Allow"
        private const val LOCATION = "Location"
    }
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val result = resolveBookmarkRequest(ex.requestMethod, getParam(ex, "bookmarkId"))
        result.allowHeader?.let { ex.responseHeaders.set(ALLOW, it) }
        result.location?.let {
            ex.responseHeaders.add(LOCATION, it)
            ex.sendResponseHeaders(result.statusCode, -1)
            return
        }
        result.responseBody?.let {
            generateResponseText(ex, result.statusCode, it)
            return
        }
        ex.sendResponseHeaders(result.statusCode, -1)
    }

    fun resolveBookmarkRequest(method: String?, bookmarkId: String?): PlaylistExportService.BookmarkRedirectResult =
        playlistExportService.resolveBookmarkRequest(method, bookmarkId)
}
