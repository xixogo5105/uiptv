package com.uiptv.mobile.shared.epg

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.InputStream
import java.time.*
import java.time.format.DateTimeFormatter
import java.time.format.ResolverStyle

object AndroidXmltvReader {
    fun read(
        input: InputStream,
        now: Long,
        source: String = "",
        requestedId: String? = null,
        requestedName: String = "",
        checkCancelled: () -> Unit = {},
        onProgress: ((List<MobileEpgProgramme>) -> Unit)? = null,
        stopAfterUpcomingProgrammeCount: Int = 0
    ): MobileXmltvGuide {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, true)
        parser.setFeature("http://xmlpull.org/v1/doc/features.html#process-docdecl", false)
        parser.setInput(input, null)
        val programmes = mutableMapOf<String, MutableList<MobileEpgProgramme>>()
        val names = mutableMapOf<String, String>()
        val icons = mutableMapOf<String, String>()
        val ambiguous = mutableSetOf<String>()
        val qualityNames = mutableMapOf<String, String>()
        val ambiguousQuality = mutableSetOf<String>()
        val candidateChannelIds = mutableSetOf<String>()
        val requestedQualityName = MobileXmltvGuide.normalizeQuality(requestedName)
        var id = ""
        var channel = ""
        var title = ""
        var description = ""
        var start = 0L
        var stop = 0L
        var count = 0
        val requestedProgrammes = mutableListOf<MobileEpgProgramme>()
        var lastProgressKey: String? = null
        var progressiveChannelIds: Set<String>? = null
        var event = parser.eventType
        parse@ while (event != XmlPullParser.END_DOCUMENT) {
            checkCancelled()
            if (Thread.currentThread().isInterrupted) throw InterruptedException()
            if (event == XmlPullParser.DOCDECL) {
                // Ignore an external XMLTV DOCTYPE without fetching it; reject internal declarations.
                require(!parser.text.orEmpty().contains("[")) { "Internal DTD declarations disabled" }
            }
            if (event == XmlPullParser.ENTITY_REF) require(parser.text != null) { "External entities disabled" }
            if (event == XmlPullParser.START_TAG) when (parser.name) {
                "channel" -> id = parser.getAttributeValue(null, "id").orEmpty()
                "display-name" -> {
                    val name = MobileXmltvGuide.normalize(boundedText(parser, 512, checkCancelled))
                    if (id.isNotBlank() && name.isNotBlank() && (requestedId == null || id == requestedId ||
                        MobileXmltvGuide.normalizeQuality(name) == requestedQualityName)) {
                        val previous = names.put(name, id)
                        if (previous != null && previous != id) ambiguous += name
                        val key = MobileXmltvGuide.normalizeQuality(name)
                        val qualityPrevious = qualityNames.put(key, id)
                        if (qualityPrevious != null && qualityPrevious != id) ambiguousQuality += key
                        if (requestedId != null && key == requestedQualityName) candidateChannelIds += id
                    }
                }
                "icon" -> if (requestedId == null && id.isNotBlank() && channel.isBlank()) {
                    val raw = parser.getAttributeValue(null, "src").orEmpty().trim()
                    if (raw.isNotBlank() && raw.length <= 4096) runCatching {
                        val uri = java.net.URI(raw).let { if (it.isAbsolute) it else java.net.URI(source).resolve(it) }
                        if (uri.scheme?.lowercase() in listOf("http", "https") && !uri.host.isNullOrBlank())
                            icons.putIfAbsent(id, uri.toString())
                    }
                }
                "programme" -> {
                    if (onProgress != null && progressiveChannelIds == null) {
                        // XMLTV channel declarations precede programme entries. Freeze the unique
                        // name matches here so later duplicate names cannot change the early result.
                        progressiveChannelIds = buildSet {
                            if (!requestedId.isNullOrBlank()) add(requestedId)
                            names.forEach { (key, value) -> if (key !in ambiguous) add(value) }
                            qualityNames.forEach { (key, value) -> if (key !in ambiguousQuality) add(value) }
                        }
                    }
                    channel = parser.getAttributeValue(null, "channel").orEmpty()
                    if (requestedId != null && channel != requestedId && channel !in candidateChannelIds) {
                        // Date parsing is relatively costly on older Android devices. Ignore rows
                        // that cannot possibly match this channel before parsing their timestamps.
                        channel = ""
                        start = 0L
                        stop = 0L
                    } else {
                        start = time(parser.getAttributeValue(null, "start"))
                        stop = time(parser.getAttributeValue(null, "stop"))
                    }
                    title = ""; description = ""
                }
                "title" -> { if (channel.isNotBlank()) { val text = boundedText(parser, 512, checkCancelled).trim(); if (title.isEmpty()) title = text } }
                "desc" -> { if (channel.isNotBlank()) { val text = boundedText(parser, 2048, checkCancelled).trim(); if (description.isEmpty()) description = text } }
            }
            if (event == XmlPullParser.END_TAG && parser.name == "channel") id = ""
            if (event == XmlPullParser.END_TAG && parser.name == "programme") {
                if (channel.isNotBlank() && start > now - 86400_000L && start < now + 7 * 86400_000L &&
                    (stop == 0L || stop > now - 6 * 3600_000L) && title.isNotBlank()) {
                    require(++count <= if (requestedId == null) 200_000 else 2000) { "Programme limit exceeded" }
                    val programme = MobileEpgProgramme(title, description, start, stop)
                    programmes.getOrPut(channel) { mutableListOf() } += programme
                    // Publish the requested XMLTV id, or its unique pre-declared name match, before
                    // the rest of a large feed has been read.
                    if (onProgress != null && channel in progressiveChannelIds.orEmpty()) {
                        requestedProgrammes += programme
                        val partial = normalize(requestedProgrammes)
                        val current = partial.lastOrNull { it.start <= now && now < it.stop }
                        val next = partial.firstOrNull { it.start > now }
                        val progressKey = listOfNotNull(current, next)
                            .joinToString("|") { "${it.start}:${it.stop}:${it.title}:${it.description}" }
                        if ((current != null || next != null) && progressKey != lastProgressKey) {
                            lastProgressKey = progressKey
                            onProgress(partial)
                        }
                        // Keep enough of the matched channel's schedule for the swipe-in guide,
                        // while still avoiding a full scan of a large XMLTV feed. Count the
                        // current programme and upcoming programmes, not old schedule entries.
                        if (hasEnoughUpcomingProgrammes(requestedProgrammes, now, stopAfterUpcomingProgrammeCount)) break@parse
                    }
                }
                channel = ""
            }
            event = parser.next()
        }
        ambiguous.forEach(names::remove)
        ambiguousQuality.forEach(qualityNames::remove)
        qualityNames.remove("")
        val completed = programmes.mapValues { (_, raw) ->
            normalize(raw)
        }
        return MobileXmltvGuide(completed, names.toMap(), qualityNames.toMap(), icons.toMap())
    }

    private fun normalize(raw: List<MobileEpgProgramme>): List<MobileEpgProgramme> {
        val sorted = raw.sortedBy { it.start }
        return MobileEpgSchedules.normalize(sorted.mapIndexedNotNull { index, p ->
            val end = if (p.stop == 0L) sorted.getOrNull(index + 1)?.start ?: 0L else p.stop
            if (end > p.start) p.copy(stop = end) else null
        })
    }

    internal fun hasEnoughUpcomingProgrammes(
        programmes: List<MobileEpgProgramme>,
        now: Long,
        minimumCount: Int
    ): Boolean = minimumCount > 0 && programmes.asSequence()
        .filter { it.stop > now }
        .distinctBy { "${it.start}:${it.stop}:${it.title}" }
        .take(minimumCount)
        .count() >= minimumCount

    private fun boundedText(parser: XmlPullParser, maximum: Int, checkCancelled: () -> Unit): String {
        val value = StringBuilder()
        var depth = 1
        while (depth > 0) {
            checkCancelled()
            when (parser.nextToken()) {
                XmlPullParser.START_TAG -> depth++
                XmlPullParser.END_TAG -> depth--
                XmlPullParser.END_DOCUMENT -> error("Unterminated XMLTV text")
                XmlPullParser.TEXT, XmlPullParser.CDSECT -> if (value.length < maximum) {
                    val text = parser.text.orEmpty()
                    value.append(text, 0, minOf(text.length, maximum - value.length))
                }
                XmlPullParser.ENTITY_REF -> require(parser.text != null) { "External entities disabled" }
            }
        }
        return value.toString()
    }

    fun time(value: String?): Long {
        if (value == null) return 0L
        return try {
            val raw = value.trim()
            if (raw.isEmpty()) return 0L
            val separator = raw.indexOfFirst(Char::isWhitespace)
            var digits = if (separator < 0) raw else raw.substring(0, separator)
            if (digits.length < 12) return 0L
            if (digits.length == 12) digits += "00"
            val zoneValue = if (separator < 0) "" else raw.substring(separator).trim()
            val zoneEnd = zoneValue.indexOfFirst(Char::isWhitespace)
            val zone = if (zoneEnd < 0) zoneValue else zoneValue.substring(0, zoneEnd)
            val offset = when (zone) {
                "UTC", "GMT", "" -> ZoneOffset.UTC
                "BST" -> ZoneOffset.ofHours(1)
                else -> ZoneOffset.of(zone)
            }
            LocalDateTime.parse(digits, XMLTV_TIME).toInstant(offset).toEpochMilli()
        } catch (_: RuntimeException) {
            0L
        }
    }

    private val XMLTV_TIME = DateTimeFormatter.ofPattern("uuuuMMddHHmmss").withResolverStyle(ResolverStyle.STRICT)
}
