package com.uiptv.service

import com.uiptv.db.ChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Bookmark
import com.uiptv.model.PlayerResponse
import com.uiptv.util.HlsPlaylistResolver
import com.uiptv.util.M3uPlaylistUtils.escapeAttributeValue
import com.uiptv.util.M3uPlaylistUtils.sanitizeTitle
import com.uiptv.util.StringUtils
import com.uiptv.util.StringUtils.isBlank
import java.net.URLDecoder
import java.nio.charset.StandardCharsets.UTF_8
import java.util.LinkedHashMap

class PlaylistExportService(
    private val accountServiceProvider: () -> AccountService = { AccountService.getInstance() },
    private val bookmarkServiceProvider: () -> BookmarkService = { BookmarkService.getInstance() },
    private val configurationServiceProvider: () -> ConfigurationService = { ConfigurationService.getInstance() },
    private val handshakeServiceProvider: () -> HandshakeService = { HandshakeService.getInstance() },
    private val playerServiceProvider: () -> PlayerService = { PlayerService.getInstance() },
    private val playerRequestResolverProvider: () -> PlayerRequestResolver = { PlayerRequestResolver() },
    private val channelDbProvider: () -> ChannelDb = { ChannelDb.get() }
) {
    companion object {
        const val BOOKMARK_MISC_GROUP_TITLE = "Misc"
        private const val MAX_HLS_RESOLUTION_DEPTH = 8
        private const val CHROME_USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36"
    }

    data class PlaylistDocument(val content: String, val fileName: String)

    data class BookmarkRedirectResult(
        val statusCode: Int,
        val location: String? = null,
        val responseBody: String? = null,
        val allowHeader: String? = null
    )

    fun buildSingleChannelPlaylist(accountId: String?, categoryId: String?, channelId: String?): PlaylistDocument {
        val account = accountServiceProvider().getById(accountId ?: "")
        val channel = channelDbProvider().getChannelById(channelId ?: "", categoryId ?: "")
        if (account == null || channel == null) {
            return PlaylistDocument("", "playlist.m3u8")
        }

        handshakeServiceProvider().hardTokenRefresh(account)
        val originalCmd = channel.cmd ?: ""
        channel.cmd = URLDecoder.decode(originalCmd, UTF_8)
        val playerResponse = playerServiceProvider().get(account, channel)
        val cmd = playerResponse.url
        channel.cmd = originalCmd

        val channelName = sanitizeTitle(channel.name)
        val response = "#EXTM3U\n" +
            "#EXTINF:-1 tvg-id=\"" + escapeAttributeValue(account.dbId) +
            "\" tvg-name=\"" + escapeAttributeValue(channelName) +
            "\" group-title=\"" + escapeAttributeValue(account.accountName) +
            "\"," + channelName + "\n" + StringUtils.EMPTY + cmd + "\n"
        return PlaylistDocument(response, "${accountId}-${categoryId}-${channelId}.m3u8")
    }

    fun buildBookmarksPlaylist(host: String): String = M3U8PublicationService.buildBookmarkPlaylist(host)

    fun buildPublishedPlaylist(host: String?, requestPath: String?): PlaylistDocument {
        val response = M3U8PublicationService.getInstance().getPublishedM3u8(host)
        val filename = if ((requestPath ?: "").endsWith(".m3u")) "iptv.m3u" else "iptv.m3u8"
        return PlaylistDocument(response, filename)
    }

    fun resolveBookmarkRequest(method: String?, bookmarkId: String?): BookmarkRedirectResult {
        if (!method.equals("GET", ignoreCase = true) && !method.equals("HEAD", ignoreCase = true)) {
            return BookmarkRedirectResult(405, allowHeader = "GET, HEAD")
        }
        if (isBlank(bookmarkId)) {
            return BookmarkRedirectResult(404)
        }
        val bookmark = bookmarkServiceProvider().getBookmark(bookmarkId) ?: return BookmarkRedirectResult(404)
        val url = resolveBookmarkPlaybackUrl(bookmark)
        if (isBlank(url)) {
            return BookmarkRedirectResult(502, responseBody = "Unable to resolve bookmark playback.")
        }
        return BookmarkRedirectResult(307, location = url)
    }

    private fun resolveBookmarkPlaybackUrl(bookmark: Bookmark): String {
        val account = accountServiceProvider().getAll()[bookmark.accountName] ?: return ""
        val response: PlayerResponse =
            playerRequestResolverProvider().resolveBookmarkPlayback(bookmark.dbId.orEmpty(), "", "")
        if (isBlank(response.url)) {
            return ""
        }
        return resolveBookmarkRedirectChain(response.url ?: "", account)
    }

    private fun resolveBookmarkRedirectChain(url: String, account: Account): String {
        if (!configurationServiceProvider().isResolveChainAndDeepRedirectsEnabled(account)) {
            return url
        }
        return HlsPlaylistResolver.resolveHlsPlaylistChain(url, createBrowserHeaders(), MAX_HLS_RESOLUTION_DEPTH)
    }

    private fun createBrowserHeaders(): Map<String, String> {
        val headers = LinkedHashMap<String, String>()
        if (configurationServiceProvider().isVlcHttpUserAgentEnabled()) {
            headers["User-Agent"] = CHROME_USER_AGENT
        }
        headers["Accept"] = "application/vnd.apple.mpegurl, */*"
        headers["Accept-Language"] = "en-US,en;q=0.9"
        return headers
    }
}
