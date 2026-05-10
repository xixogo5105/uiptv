package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.PlayerResponse
import com.uiptv.service.AccountService
import com.uiptv.service.BookmarkService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.PlayerRequestResolver
import com.uiptv.util.HlsPlaylistResolver
import com.uiptv.util.ServerUtils.generateResponseText
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils.isBlank
import java.io.IOException
import java.util.LinkedHashMap

class HttpM3u8BookmarkEntry : HttpHandler {
    companion object {
        private const val MAX_HLS_RESOLUTION_DEPTH = 8
        private const val GET = "GET"
        private const val HEAD = "HEAD"
        private const val ALLOW = "Allow"
        private const val LOCATION = "Location"
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
    }

    private val playerRequestResolver = PlayerRequestResolver()

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val method = ex.requestMethod
        if (!GET.equals(method, ignoreCase = true) && !HEAD.equals(method, ignoreCase = true)) {
            ex.responseHeaders.set(ALLOW, "$GET, $HEAD")
            ex.sendResponseHeaders(405, -1)
            return
        }

        val bookmarkId = getParam(ex, "bookmarkId")
        if (isBlank(bookmarkId)) {
            ex.sendResponseHeaders(404, -1)
            return
        }

        val bookmark = BookmarkService.getInstance().getBookmark(bookmarkId)
        if (bookmark == null) {
            ex.sendResponseHeaders(404, -1)
            return
        }

        val url = bookmarkPlayerResponse(bookmark)
        if (isBlank(url)) {
            generateResponseText(ex, 502, "Unable to resolve bookmark playback.")
            return
        }
        ex.responseHeaders.add(LOCATION, url)
        ex.sendResponseHeaders(307, -1)
    }

    private fun bookmarkPlayerResponse(bookmark: Bookmark): String {
        val account = AccountService.getInstance().getAll()[bookmark.accountName] ?: return ""
        val response: PlayerResponse = playerRequestResolver.resolveBookmarkPlayback(bookmark.dbId.orEmpty(), "", "")
        if (isBlank(response.url)) {
            return ""
        }
        return resolveBookmarkRedirectChain(response.url ?: "", account)
    }

    private fun resolveBookmarkRedirectChain(url: String, account: Account): String {
        if (!ConfigurationService.getInstance().isResolveChainAndDeepRedirectsEnabled(account)) {
            return url
        }
        return HlsPlaylistResolver.resolveHlsPlaylistChain(url, createBrowserHeaders(), MAX_HLS_RESOLUTION_DEPTH)
    }

    private fun createBrowserHeaders(): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        if (ConfigurationService.getInstance().isVlcHttpUserAgentEnabled()) {
            headers["User-Agent"] = CHROME_USER_AGENT
        }
        headers["Accept"] = "application/vnd.apple.mpegurl, */*"
        headers["Accept-Language"] = "en-US,en;q=0.9"
        return headers
    }
}
