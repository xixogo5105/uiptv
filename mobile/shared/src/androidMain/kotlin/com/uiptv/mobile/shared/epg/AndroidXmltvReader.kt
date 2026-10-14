package com.uiptv.mobile.shared.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object AndroidXmltvReader {
    fun read(input: InputStream, now: Long): MobileXmltvGuide {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        parser.setInput(input, null)
        val programmes = mutableMapOf<String, MutableList<MobileEpgProgramme>>()
        val names = mutableMapOf<String, String>()
        val ambiguous = mutableSetOf<String>()
        val qualityNames = mutableMapOf<String, String>()
        val ambiguousQuality = mutableSetOf<String>()
        var id = ""
        var channel = ""
        var title = ""
        var description = ""
        var start = 0L
        var stop = 0L
        var count = 0
        var event = parser.eventType
        while (event != XmlPullParser.END_DOCUMENT) {
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (event == XmlPullParser.DOCDECL) {
                // Ignore an external XMLTV DOCTYPE without fetching it; reject internal declarations.
                require(!parser.text.orEmpty().contains("[")) { "Internal DTD declarations disabled" }
            }
            if (event == XmlPullParser.ENTITY_REF) require(parser.text != null) { "External entities disabled" }
            if (event == XmlPullParser.START_TAG) when (parser.name) {
                "channel" -> id = parser.getAttributeValue(null, "id").orEmpty()
                "display-name" -> {
                    val name = MobileXmltvGuide.normalize(parser.nextText())
                    if (id.isNotBlank() && name.isNotBlank()) {
                        val previous = names.put(name, id)
                        if (previous != null && previous != id) ambiguous += name
                        val key = MobileXmltvGuide.normalizeQuality(name)
                        val qualityPrevious = qualityNames.put(key, id)
                        if (qualityPrevious != null && qualityPrevious != id) ambiguousQuality += key
                    }
                }
                "programme" -> {
                    channel = parser.getAttributeValue(null, "channel").orEmpty()
                    start = time(parser.getAttributeValue(null, "start"))
                    stop = time(parser.getAttributeValue(null, "stop"))
                    title = ""; description = ""
                }
                "title" -> { val text = parser.nextText().trim(); if (title.isEmpty()) title = text }
                "desc" -> { val text = parser.nextText().trim(); if (description.isEmpty()) description = text }
            }
            if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                if (channel.isNotBlank() && start > now - 86400_000L && start < now + 7 * 86400_000L &&
                    (stop == 0L || stop > now - 6 * 3600_000L) && title.isNotBlank()) {
                    require(++count <= 200_000) { "Programme limit exceeded" }
                    programmes.getOrPut(channel) { mutableListOf() } += MobileEpgProgramme(title, description, start, stop)
                }
                channel = ""
            }
            event = parser.nextToken()
        }
        ambiguous.forEach(names::remove)
        ambiguousQuality.forEach(qualityNames::remove)
        qualityNames.remove("")
        val completed = programmes.mapValues { (_, raw) ->
            val sorted = raw.sortedBy { it.start }
            MobileEpgSchedules.normalize(sorted.mapIndexedNotNull { index, p ->
                val end = if (p.stop == 0L) sorted.getOrNull(index + 1)?.start ?: 0L else p.stop
                if (end > p.start) p.copy(stop = end) else null
            })
        }
        return MobileXmltvGuide(completed, names.toMap(), qualityNames.toMap())
    }
    fun time(value: String?): Long = runCatching {
        val parts = value.orEmpty().trim().split(Regex("\\s+"))
        var digits = parts[0]
        require(digits.length >= 12)
        if (digits.length == 12) digits += "00"
        val offset = when (val raw = parts.getOrElse(1) { "UTC" }) {
            "UTC", "GMT" -> ZoneOffset.UTC
            "BST" -> ZoneOffset.ofHours(1)
            else -> ZoneOffset.of(raw)
        }
        LocalDateTime.parse(digits, DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT))
            .toInstant(offset).toEpochMilli()
    }.getOrDefault(0)
}
