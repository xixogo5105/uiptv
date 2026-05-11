package com.uiptv.util

import com.uiptv.model.CategoryType
import com.uiptv.shared.PlaylistEntry
import com.uiptv.util.json.parseJsonObject
import java.io.BufferedReader
import java.io.FileReader
import java.io.IOException
import java.io.InputStream
import java.io.StringReader
import java.io.UncheckedIOException
import java.net.URL
import java.nio.charset.StandardCharsets
import java.util.LinkedHashSet
import java.util.regex.Pattern

object M3U8Parser {
    private const val EXTINF = "#EXTINF"
    private const val EXT_X_KEY = "#EXT-X-KEY"
    private const val KODIPROP_INPUTSTREAM_ADDON = "#KODIPROP:inputstreamaddon="
    private const val KODIPROP_MANIFEST_TYPE = "#KODIPROP:inputstream.adaptive.manifest_type="
    private const val KODIPROP_LICENSE_TYPE = "#KODIPROP:inputstream.adaptive.license_type="
    private const val KODIPROP_LICENSE_KEY = "#KODIPROP:inputstream.adaptive.license_key="
    private const val COMMENT_PREFIX = "#"
    private val UNCATEGORIZED = CategoryType.UNCATEGORIZED.displayName()
    private const val DRM_TYPE_WIDEVINE = "com.widevine.alpha"
    private const val DRM_TYPE_CLEARKEY = "org.w3.clearkey"

    @JvmStatic
    fun parseUrlCategory(m3u8Url: URL): Set<PlaylistEntry> {
        return try {
            val protocol = m3u8Url.protocol
            if (protocol != null && protocol.lowercase().startsWith("http")) {
                val response = HttpUtil.sendRequest(m3u8Url.toString(), null, "GET")
                parseCategory(BufferedReader(StringReader(response.body)))
            } else {
                parseCategory(BufferedReader(StringReader(readNonHttpUrl(m3u8Url))))
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to parse M3U categories from URL", e)
        }
    }

    @JvmStatic
    fun parsePathCategory(filePath: String): Set<PlaylistEntry> {
        try {
            BufferedReader(FileReader(filePath, StandardCharsets.UTF_8)).use { reader ->
                return parseCategory(reader)
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to parse M3U categories from file", e)
        }
    }

    @JvmStatic
    fun parseChannelUrlM3U8(m3u8Url: URL): List<PlaylistEntry> {
        return try {
            val protocol = m3u8Url.protocol
            if (protocol != null && protocol.lowercase().startsWith("http")) {
                val response = HttpUtil.sendRequest(m3u8Url.toString(), null, "GET")
                parseM3U8(BufferedReader(StringReader(response.body)))
            } else {
                parseM3U8(BufferedReader(StringReader(readNonHttpUrl(m3u8Url))))
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to parse M3U channels from URL", e)
        }
    }

    @JvmStatic
    fun parseChannelPathM3U8(filePath: String): List<PlaylistEntry> {
        try {
            BufferedReader(FileReader(filePath, StandardCharsets.UTF_8)).use { reader ->
                return parseM3U8(reader)
            }
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to parse M3U channels from file", e)
        }
    }

    private fun parseCategory(reader: BufferedReader): Set<PlaylistEntry> {
        val playlistEntries = linkedSetOf<PlaylistEntry>()
        playlistEntries.add(PlaylistEntry(CategoryType.ALL.displayName(), CategoryType.ALL.displayName(), null, null, null))
        var hasUncategorizedEntries = false
        try {
            while (true) {
                val line = reader.readLine() ?: break
                if (!line.startsWith(EXTINF)) continue
                if (processCategoryLineSafely(playlistEntries, line)) {
                    hasUncategorizedEntries = true
                }
            }
        } catch (e: Exception) {
            AppLog.addErrorLog(M3U8Parser::class.java, e.message)
        }
        if (hasUncategorizedEntries && playlistEntries.none { it.groupTitle.equals(UNCATEGORIZED, true) }) {
            playlistEntries.add(PlaylistEntry(UNCATEGORIZED, UNCATEGORIZED, null, null, null))
        }
        return playlistEntries
    }

    private fun processCategoryLineSafely(playlistEntries: MutableSet<PlaylistEntry>, line: String): Boolean {
        return try {
            processCategoryLine(playlistEntries, line)
        } catch (e: RuntimeException) {
            AppLog.addErrorLog(M3U8Parser::class.java, e.message)
            false
        }
    }

    private fun processCategoryLine(playlistEntries: MutableSet<PlaylistEntry>, line: String): Boolean {
        val tvgId = M3uPlaylistUtils.parseAttribute(line, "tvg-id")
        val groupTitles = M3uPlaylistUtils.splitGroupTitles(M3uPlaylistUtils.parseAttribute(line, "group-title"))
        if (groupTitles.isEmpty()) {
            return true
        }
        for (groupTitle in groupTitles) {
            val categoryEntry = buildCategoryEntry(tvgId, groupTitle)
            if (categoryEntry != null) {
                playlistEntries.add(categoryEntry)
            }
        }
        return groupTitles.any(::shouldTreatAsUncategorized)
    }

    private fun parseM3U8(reader: BufferedReader): List<PlaylistEntry> {
        val playlistEntries = ArrayList<PlaylistEntry>()
        try {
            val lines = readLines(reader)
            var index = 0
            while (index < lines.size) {
                val line = lines[index]
                if (!line.startsWith(EXTINF)) {
                    index++
                    continue
                }
                val header = parseEntryHeader(line)
                val parsed = parseEntryState(lines, index + 1)
                val state = parsed.state
                if (StringUtils.isNotBlank(state.url)) {
                    for (groupTitle in effectiveGroupTitles(header.groupTitles)) {
                        playlistEntries.add(
                            PlaylistEntry(
                                header.tvgId,
                                groupTitle,
                                header.title,
                                state.url,
                                header.logo,
                                state.drmType,
                                state.drmLicenseUrl,
                                state.clearKeys,
                                state.inputstreamaddon,
                                state.manifestType
                            )
                        )
                    }
                }
                index = parsed.lastIndex + 1
            }
        } catch (e: IOException) {
            AppLog.addErrorLog(M3U8Parser::class.java, e.message)
        }
        return playlistEntries
    }

    private fun parseEntryState(lines: List<String>, startIndex: Int): ParsedEntry {
        val state = EntryState()
        for (index in startIndex until lines.size) {
            val nextLine = lines[index]
            val trimmed = nextLine.trim()
            if (trimmed.startsWith(EXTINF)) {
                return ParsedEntry(state, index - 1)
            }
            if (applyMetaLine(state, nextLine, trimmed) && state.url != null) {
                return ParsedEntry(state, index)
            }
        }
        return ParsedEntry(state, lines.size - 1)
    }

    private fun readLines(reader: BufferedReader): List<String> {
        val lines = ArrayList<String>()
        while (true) {
            val line = reader.readLine() ?: break
            lines.add(line)
        }
        return lines
    }

    private fun parseEntryHeader(line: String): EntryHeader =
        EntryHeader(
            M3uPlaylistUtils.parseAttribute(line, "tvg-id"),
            M3uPlaylistUtils.splitGroupTitles(M3uPlaylistUtils.parseAttribute(line, "group-title")),
            parseTitle(line),
            M3uPlaylistUtils.parseAttribute(line, "tvg-logo")
        )

    private fun shouldTreatAsUncategorized(groupTitle: String?): Boolean =
        !StringUtils.isNotBlank(groupTitle) || groupTitle.equals(UNCATEGORIZED, true)

    private fun effectiveGroupTitles(groupTitles: List<String>?): List<String> =
        if (groupTitles.isNullOrEmpty()) listOf(UNCATEGORIZED) else groupTitles

    private fun buildCategoryEntry(tvgId: String?, groupTitle: String?): PlaylistEntry? {
        if (!StringUtils.isNotBlank(groupTitle) || groupTitle.equals(CategoryType.ALL.displayName(), true)) {
            return null
        }
        return PlaylistEntry(tvgId, groupTitle, null, null, null)
    }

    private fun applyMetaLine(state: EntryState, nextLine: String, trimmed: String): Boolean {
        if (nextLine.startsWith(EXT_X_KEY)) {
            state.drmType = parseDrmType(nextLine)
            state.drmLicenseUrl = M3uPlaylistUtils.parseAttribute(nextLine, "URI")
            return false
        }
        if (nextLine.startsWith(KODIPROP_INPUTSTREAM_ADDON)) {
            state.inputstreamaddon = nextLine.substring(KODIPROP_INPUTSTREAM_ADDON.length).trim()
            return false
        }
        if (nextLine.startsWith(KODIPROP_MANIFEST_TYPE)) {
            state.manifestType = nextLine.substring(KODIPROP_MANIFEST_TYPE.length).trim()
            return false
        }
        if (nextLine.startsWith(KODIPROP_LICENSE_TYPE)) {
            state.drmType = normalizeLicenseType(nextLine.substring(KODIPROP_LICENSE_TYPE.length).trim(), state.drmType)
            return false
        }
        if (nextLine.startsWith(KODIPROP_LICENSE_KEY)) {
            applyLicenseKey(state, nextLine.substring(KODIPROP_LICENSE_KEY.length).trim())
            return false
        }
        if (StringUtils.isNotBlank(trimmed) && !trimmed.startsWith(COMMENT_PREFIX)) {
            val normalizedCandidate = normalizePotentialUrl(trimmed)
            if (isLikelyStreamUrl(normalizedCandidate)) {
                state.url = normalizedCandidate
                return true
            }
        }
        return false
    }

    private fun normalizeLicenseType(type: String?, currentType: String?): String? {
        if (DRM_TYPE_WIDEVINE.equals(type, true)) {
            return DRM_TYPE_WIDEVINE
        }
        if ("clearkey".equals(type, true) || DRM_TYPE_CLEARKEY.equals(type, true) || "com.clearkey.alpha".equals(type, true)) {
            return DRM_TYPE_CLEARKEY
        }
        return currentType
    }

    private fun applyLicenseKey(state: EntryState, key: String) {
        if (DRM_TYPE_CLEARKEY.equals(state.drmType, true)) {
            if (state.clearKeys == null) {
                state.clearKeys = HashMap()
            }
            state.clearKeys!!.putAll(parseClearKeys(key))
            return
        }
        state.drmLicenseUrl = key
    }

    private class EntryState {
        var drmType: String? = null
        var drmLicenseUrl: String? = null
        var clearKeys: MutableMap<String, String>? = null
        var inputstreamaddon: String? = null
        var manifestType: String? = null
        var url: String? = null
    }

    private data class EntryHeader(val tvgId: String?, val groupTitles: List<String>, val title: String, val logo: String?)
    private data class ParsedEntry(val state: EntryState, val lastIndex: Int)

    private fun parseTitle(line: String): String {
        val lastCommaIndex = line.lastIndexOf(",")
        return if (lastCommaIndex != -1 && lastCommaIndex < line.length - 1) line.substring(lastCommaIndex + 1).trim() else StringUtils.EMPTY
    }

    private fun parseDrmType(line: String): String? {
        val pattern = Pattern.compile("KEYFORMAT=\"(.*?)\"")
        val matcher = pattern.matcher(line)
        if (matcher.find()) {
            val keyFormat = matcher.group(1)
            if (DRM_TYPE_WIDEVINE.equals(keyFormat, true)) {
                return DRM_TYPE_WIDEVINE
            }
        }
        return null
    }

    private fun parseClearKeys(keyString: String?): Map<String, String> {
        val keys = HashMap<String, String>()
        if (!StringUtils.isNotBlank(keyString)) {
            return keys
        }
        val normalized = keyString!!.trim()
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            try {
                val json = parseJsonObject(normalized)
                if (json != null) {
                    for ((key, value) in json) {
                        keys[key] = value.toString().trim('"')
                    }
                    return keys
                }
            } catch (_: Exception) {
            }
        }
        for (pair in normalized.split(";")) {
            val parts = pair.split(":")
            if (parts.size == 2) {
                keys[parts[0]] = parts[1]
            }
        }
        return keys
    }

    private fun normalizePotentialUrl(value: String?): String? {
        if (!StringUtils.isNotBlank(value)) {
            return value
        }
        var normalized = value!!.trim()
        normalized = normalized.replace("\\/", "/")
        normalized = normalized.replace("(?i)\\\\u002f".toRegex(), "/")
        normalized = normalized.replace("(?i)\\\\u003a".toRegex(), ":")
        normalized = normalized.replace("(?i)\\\\u003f".toRegex(), "?")
        normalized = normalized.replace("(?i)\\\\u003d".toRegex(), "=")
        normalized = normalized.replace("(?i)\\\\u0026".toRegex(), "&")
        return normalized
    }

    private fun isLikelyStreamUrl(value: String?): Boolean {
        if (!StringUtils.isNotBlank(value)) {
            return false
        }
        val candidate = value!!.trim()
        if (candidate.startsWith(COMMENT_PREFIX)) {
            return false
        }
        if (candidate.matches(Regex("^[a-zA-Z][a-zA-Z0-9+.-]*://.*"))) return true
        if (candidate.startsWith("//") || candidate.startsWith("/") || candidate.startsWith("./") || candidate.startsWith("../") || candidate.matches(Regex("^[a-zA-Z]:\\\\\\\\.*"))) {
            return true
        }
        if (candidate.contains("/")) return true
        return candidate.matches(Regex("(?i)^.+\\.(m3u8|mpd|ts|aac|mp3|mp4|m4s)(\\?.*)?$"))
    }

    private fun readNonHttpUrl(source: URL): String {
        source.openStream().use { inputStream: InputStream ->
            return String(inputStream.readAllBytes(), StandardCharsets.UTF_8)
        }
    }
}
