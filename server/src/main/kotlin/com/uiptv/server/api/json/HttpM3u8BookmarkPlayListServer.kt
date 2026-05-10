package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.HandshakeService
import com.uiptv.service.PlaylistExportService
import com.uiptv.service.PlayerRequestResolver
import com.uiptv.service.PlayerService
import com.uiptv.util.ServerUtils.generateM3u8Response
import java.io.IOException

class HttpM3u8BookmarkPlayListServer : HttpHandler {
    companion object {
        @JvmField
        val MISC_GROUP_TITLE: String = PlaylistExportService.BOOKMARK_MISC_GROUP_TITLE

        @JvmStatic
        fun buildPlaylist(host: String): String = PlaylistExportService(
            accountService = AccountService.getInstance(),
            bookmarkService = BookmarkService.getInstance(),
            configurationService = ConfigurationService.getInstance(),
            handshakeService = HandshakeService.getInstance(),
            playerService = PlayerService.getInstance(),
            playerRequestResolver = PlayerRequestResolver()
        ).buildBookmarksPlaylist(host)
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val host = ex.requestHeaders.getFirst("Host") ?: ""
        generateM3u8Response(ex, buildPlaylist(host), "$host-bookmarks.m3u8")
    }
}
