package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.model.Bookmark
import com.uiptv.model.BookmarkCategory
import com.uiptv.service.BookmarkService
import com.uiptv.util.I18n
import com.uiptv.util.M3uPlaylistUtils.escapeAttributeValue
import com.uiptv.util.M3uPlaylistUtils.sanitizeTitle
import com.uiptv.util.ServerUtils.generateM3u8Response
import com.uiptv.util.StringUtils.isNotBlank
import java.io.IOException
import java.util.LinkedHashMap

class HttpM3u8BookmarkPlayListServer : HttpHandler {
    companion object {
        @JvmField
        val MISC_GROUP_TITLE: String = "Misc"

        @JvmStatic
        fun buildPlaylist(host: String): String = HttpM3u8BookmarkPlayListServer().buildPlaylistContent(host)
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val host = ex.requestHeaders.getFirst("Host") ?: ""
        generateM3u8Response(ex, buildPlaylist(host), "$host-bookmarks.m3u8")
    }

    private fun buildPlaylistContent(host: String): String {
        val allTabName = I18n.tr("commonAll")
        val bookmarks = BookmarkService.getInstance().read()
        val categoryNameById = loadCategoryNamesById()
        val response = StringBuilder("#EXTM3U\n")
        appendUncategorizedEntries(response, bookmarks, host, categoryNameById, allTabName)
        appendCategorizedEntries(response, bookmarks, host, categoryNameById, allTabName)
        return response.toString()
    }

    private fun loadCategoryNamesById(): Map<String, String> {
        val names = LinkedHashMap<String, String>()
        for (category: BookmarkCategory? in BookmarkService.getInstance().getAllCategories()) {
            val categoryId = category?.id
            val categoryName = category?.name
            if (isNotBlank(categoryId) && isNotBlank(categoryName)) {
                names[categoryId!!] = categoryName!!
            }
        }
        return names
    }

    private fun appendUncategorizedEntries(
        response: StringBuilder,
        bookmarks: List<Bookmark>,
        host: String,
        categoryNameById: Map<String, String>,
        allTabName: String
    ) {
        for (bookmark in bookmarks) {
            if (isUncategorized(bookmark, categoryNameById, allTabName)) {
                appendPlaylistEntry(response, bookmark, host, MISC_GROUP_TITLE)
            }
        }
    }

    private fun appendCategorizedEntries(
        response: StringBuilder,
        bookmarks: List<Bookmark>,
        host: String,
        categoryNameById: Map<String, String>,
        allTabName: String
    ) {
        for (bookmark in bookmarks) {
            if (!isUncategorized(bookmark, categoryNameById, allTabName)) {
                val categoryName = categoryNameById[bookmark.categoryId]
                if (isNotBlank(categoryName)) {
                    appendPlaylistEntry(response, bookmark, host, categoryName!!)
                }
            }
        }
    }

    private fun isUncategorized(bookmark: Bookmark?, categoryNameById: Map<String, String>, allTabName: String): Boolean {
        if (bookmark == null) {
            return true
        }
        val categoryId = bookmark.categoryId
        if (!isNotBlank(categoryId)) {
            return true
        }
        val categoryName = categoryNameById[categoryId]
        return !isNotBlank(categoryName) || categoryName.equals(allTabName, ignoreCase = true)
    }

    private fun appendPlaylistEntry(response: StringBuilder, bookmark: Bookmark, host: String, groupTitle: String) {
        val requestedUrl = "http://$host/bookmarkEntry.ts?bookmarkId=${bookmark.dbId}"
        val channelName = sanitizeTitle(bookmark.channelName)
        response.append("#EXTINF:-1 tvg-id=\"")
            .append(escapeAttributeValue(bookmark.dbId))
            .append("\" tvg-name=\"")
            .append(escapeAttributeValue(channelName))
            .append("\" group-title=\"")
            .append(escapeAttributeValue(groupTitle))
            .append("\",")
            .append(channelName)
            .append("\n")
            .append(requestedUrl)
            .append("\n")
    }
}
