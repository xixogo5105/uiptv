package com.uiptv.util

import com.rometools.rome.feed.synd.SyndFeed
import com.rometools.rome.io.FeedException
import com.rometools.rome.io.SyndFeedInput
import com.rometools.rome.io.XmlReader
import java.io.BufferedInputStream
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.util.Locale

object RssFeedReader {
    private const val USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"
    private const val HEADER_CONTENT_TYPE = "content-type"

    data class RssItem(
        var title: String?,
        var link: String?,
        var description: String?
    )

    @JvmStatic
    @Throws(IOException::class, FeedException::class)
    fun getItems(url: String?): List<RssItem> {
        if (StringUtils.isBlank(url)) {
            return emptyList()
        }
        val items = ArrayList<RssItem>()
        val input = SyndFeedInput()
        val headers = defaultRssHeaders()
        HttpUtil.openStream(url!!, headers, "GET", null, HttpUtil.RequestOptions.defaults()).use { response ->
            if (response.statusCode != HttpUtil.STATUS_OK) {
                throw IOException(
                    "RSS request failed: HTTP ${response.statusCode} content-type=" +
                        quoteForError(firstHeaderValue(response.responseHeaders, HEADER_CONTENT_TYPE))
                )
            }

            val buffered = BufferedInputStream(response.bodyStream)
            if (looksLikeHtml(buffered)) {
                throw IOException(
                    "RSS response was HTML (not a feed). content-type=" +
                        quoteForError(firstHeaderValue(response.responseHeaders, HEADER_CONTENT_TYPE))
                )
            }

            XmlReader(buffered, true, firstHeaderValue(response.responseHeaders, HEADER_CONTENT_TYPE)).use { xmlReader ->
                val feed: SyndFeed = input.build(xmlReader)
                for (entry in feed.entries) {
                    var link = entry.link
                    if (!entry.enclosures.isEmpty()) {
                        link = entry.enclosures[0].url
                    }
                    if (StringUtils.isBlank(link)) {
                        continue
                    }
                    if (!link.lowercase(Locale.ROOT).startsWith("http")) {
                        link = feed.link + link
                    }
                    items.add(
                        RssItem(
                            entry.title,
                            link,
                            if (entry.description != null && StringUtils.isNotBlank(entry.description.value)) entry.description.value else ""
                        )
                    )
                }
            }
        }

        return items
    }

    private fun defaultRssHeaders(): Map<String, String> = linkedMapOf(
        "User-Agent" to USER_AGENT,
        "Accept" to "application/rss+xml, application/atom+xml, application/xml, text/xml;q=0.9, */*;q=0.8",
        "Accept-Encoding" to "identity"
    )

    private fun firstHeaderValue(headers: Map<String, List<String>>?, name: String?): String? {
        if (headers == null || name == null) {
            return null
        }
        for ((key, values) in headers) {
            if (key != null && key.equals(name, ignoreCase = true)) {
                if (values.isNullOrEmpty()) {
                    return null
                }
                return values.first()
            }
        }
        return null
    }

    private fun looksLikeHtml(input: BufferedInputStream): Boolean {
        input.mark(4096)
        val buf = input.readNBytes(2048)
        input.reset()
        if (buf.isEmpty()) {
            return false
        }
        val prefix = String(buf, StandardCharsets.UTF_8).trim().lowercase(Locale.ROOT)
        return prefix.startsWith("<!doctype html") || prefix.startsWith("<html") || prefix.contains("<html")
    }

    private fun quoteForError(value: String?): String = if (value.isNullOrBlank()) "<unknown>" else "\"${value.trim()}\""
}
