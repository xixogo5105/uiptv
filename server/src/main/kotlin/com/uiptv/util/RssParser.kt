package com.uiptv.util

import com.uiptv.model.CategoryType
import com.uiptv.shared.PlaylistEntry
import java.io.IOException
import java.io.UncheckedIOException
import java.math.BigInteger
import java.util.UUID

object RssParser {
    @JvmStatic
    fun getCategories(): Set<PlaylistEntry> {
        val playlistEntries = linkedSetOf<PlaylistEntry>()
        playlistEntries.add(PlaylistEntry(CategoryType.ALL.displayName(), CategoryType.ALL.displayName(), null, null, null))
        return playlistEntries
    }

    @JvmStatic
    fun parse(rssUrl: String?): List<PlaylistEntry> {
        val playlistEntries = ArrayList<PlaylistEntry>()
        try {
            for (item in RssFeedReader.getItems(rssUrl)) {
                val uuid = String.format("%040d", BigInteger(UUID.randomUUID().toString().replace("-", ""), 16))
                val title = StringUtils.safeUtf(item.title)
                playlistEntries.add(PlaylistEntry(uuid, title, title, item.link, ""))
            }
        } catch (e: IOException) {
            val details = if (e.message.isNullOrBlank()) "" else ": ${e.message!!.trim()}"
            throw UncheckedIOException("Unable to load RSS feed$details", e)
        } catch (e: RuntimeException) {
            throw e
        } catch (e: Exception) {
            throw IllegalStateException("Unable to parse RSS feed", e)
        }
        return playlistEntries
    }
}
