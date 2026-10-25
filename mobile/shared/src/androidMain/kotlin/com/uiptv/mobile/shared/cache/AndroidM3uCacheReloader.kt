package com.uiptv.mobile.shared.cache

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.net.Uri
import com.uiptv.mobile.shared.accounts.AccountCacheSummary
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.absoluteValue

class AndroidM3uCacheReloader(
    private val context: Context,
    private val databaseHelper: AndroidUiptvDatabaseHelper
) {
    suspend fun refreshAccount(accountId: Long): M3uRefreshResult = withContext(Dispatchers.IO) {
        val account = AndroidSQLiteAccountRepository(databaseHelper)
            .listAccounts()
            .firstOrNull { it.id == accountId }
            ?: return@withContext M3uRefreshResult.skipped("Account not found.")

        if (account.type != MobileAccountType.M3U8_URL && account.type != MobileAccountType.M3U8_LOCAL) {
            return@withContext M3uRefreshResult.skipped("${account.type.displayName} refresh is not ported to Android yet.")
        }

        val playlist = runCatching { readPlaylist(account) }
            .getOrElse { return@withContext M3uRefreshResult.failed(it.message ?: "Unable to read playlist.") }
        val entries = M3uPlaylistParser.parse(playlist)
        if (entries.isEmpty()) {
            return@withContext M3uRefreshResult.failed("No playable entries found. Existing cache was kept.")
        }

        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        val result = try {
            clearLiveCache(db, accountId)
            val summary = saveEntries(db, accountId, entries)
            db.setTransactionSuccessful()
            M3uRefreshResult.succeeded("Saved ${summary.liveCategories} categories and ${summary.liveChannels} channels.", summary)
        } finally {
            db.endTransaction()
        }
        db.execSQL("VACUUM")
        result
    }

    private fun clearLiveCache(db: SQLiteDatabase, accountId: Long) {
        db.execSQL(
            "DELETE FROM Channel WHERE categoryId IN (SELECT id FROM Category WHERE accountId = ?)",
            arrayOf(accountId.toString())
        )
        db.delete("Category", "accountId = ?", arrayOf(accountId.toString()))
    }

    private fun readPlaylist(account: MobileAccount): String {
        val source = account.m3u8Path.ifBlank { account.url }
        require(source.isNotBlank()) { "Playlist source is required." }
        return when {
            source.startsWith("content://", ignoreCase = true) ->
                context.contentResolver.openInputStream(Uri.parse(source))?.use { input ->
                    input.readUtf8Limited("Playlist")
                } ?: error("Unable to open playlist URI.")
            source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) ->
                readUrl(source)
            else -> {
                val file = File(source)
                if (file.length() > MAX_CACHE_HTTP_BODY_BYTES) {
                    error("Playlist is too large (${file.length().toDisplaySize()}; limit ${MAX_CACHE_HTTP_BODY_BYTES.toLong().toDisplaySize()}). Existing cache was kept.")
                }
                file.readText(Charsets.UTF_8)
            }
        }
    }

    private fun readUrl(source: String): String {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        return connection.readCacheBody("Playlist request")
    }

    private fun saveEntries(db: SQLiteDatabase, accountId: Long, entries: List<M3uEntry>): AccountCacheSummary {
        val allCategoryId = insertCategory(db, accountId, "all", "All")
        val categoryIdByTitle = linkedMapOf<String, Long>()
        val groupedEntries = entries.flatMap { entry ->
            entry.groupTitle.split(';').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("Uncategorized") }
                .map { group -> group to entry }
        }.groupBy({ it.first }, { it.second })
        groupedEntries.keys
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .forEach { title ->
                categoryIdByTitle[title] = insertCategory(db, accountId, stableCategoryId(title), title)
            }

        var channelRows = 0
        entries.forEachIndexed { index, entry ->
            insertChannel(db, allCategoryId, entry, index)
            channelRows++
            entry.groupTitle.split(';').map { it.trim() }.filter { it.isNotBlank() }.ifEmpty { listOf("Uncategorized") }
                .distinct().forEach { group ->
                    val categoryDbId = categoryIdByTitle[group]
                    if (categoryDbId != null) {
                        insertChannel(db, categoryDbId, entry, index)
                        channelRows++
                    }
                }
        }

        return AccountCacheSummary(
            liveCategories = 1 + categoryIdByTitle.size,
            liveChannels = channelRows
        )
    }

    private fun insertCategory(db: SQLiteDatabase, accountId: Long, categoryId: String, title: String): Long =
        db.insert(
            "Category",
            null,
            ContentValues().apply {
                put("categoryId", categoryId)
                put("accountId", accountId.toString())
                put("accountType", "itv")
                put("title", title)
                put("alias", title)
                put("activeSub", 0)
                put("censored", 0)
            }
        )

    private fun insertChannel(db: SQLiteDatabase, categoryDbId: Long, entry: M3uEntry, index: Int) {
        db.insert(
            "Channel",
            null,
            ContentValues().apply {
                put("channelId", entry.tvgId.ifBlank { stableChannelId(entry.url, index) })
                put("categoryId", categoryDbId.toString())
                put("name", entry.name)
                put("number", entry.channelNumber)
                put("cmd", entry.url)
                put("cmd_1", "")
                put("cmd_2", "")
                put("cmd_3", "")
                put("logo", entry.logo)
                put("censored", 0)
                put("status", 1)
                put("hd", if (entry.name.contains("HD", ignoreCase = true)) 1 else 0)
                put("drmType", entry.drmType)
                put("drmLicenseUrl", entry.drmLicenseUrl)
                put("clearKeysJson", entry.clearKeysJson)
                put("inputstreamaddon", entry.inputstreamaddon)
                put("manifestType", entry.manifestType)
                put("extraJson", entry.extraJson)
            }
        )
    }

    private fun stableCategoryId(title: String): String =
        "m3u-" + title.lowercase().hashCode().absoluteValue

    private fun stableChannelId(url: String, index: Int): String =
        "m3u-$index-" + url.hashCode().absoluteValue
}

data class M3uRefreshResult(
    val status: CacheRefreshJobStatus,
    val message: String,
    val summary: AccountCacheSummary = AccountCacheSummary()
) {
    companion object {
        fun succeeded(message: String, summary: AccountCacheSummary): M3uRefreshResult =
            M3uRefreshResult(CacheRefreshJobStatus.SUCCEEDED, message, summary)

        fun skipped(message: String): M3uRefreshResult =
            M3uRefreshResult(CacheRefreshJobStatus.SKIPPED, message)

        fun failed(message: String): M3uRefreshResult =
            M3uRefreshResult(CacheRefreshJobStatus.FAILED, message)
    }
}

data class M3uEntry(
    val name: String,
    val url: String,
    val groupTitle: String = "",
    val logo: String = "",
    val tvgId: String = "",
    val drmType: String = "",
    val drmLicenseUrl: String = "",
    val clearKeysJson: String = "",
    val inputstreamaddon: String = "",
    val manifestType: String = "",
    val channelNumber: String = "",
    val extraJson: String = ""
)

object M3uPlaylistParser {
    private const val DRM_TYPE_WIDEVINE = "com.widevine.alpha"
    private const val DRM_TYPE_CLEARKEY = "org.w3.clearkey"
    private const val KODIPROP_INPUTSTREAM_ADDON = "#KODIPROP:inputstreamaddon="
    private const val KODIPROP_MANIFEST_TYPE = "#KODIPROP:inputstream.adaptive.manifest_type="
    private const val KODIPROP_LICENSE_TYPE = "#KODIPROP:inputstream.adaptive.license_type="
    private const val KODIPROP_LICENSE_KEY = "#KODIPROP:inputstream.adaptive.license_key="
    private const val EXTGRP = "#EXTGRP:"
    private const val EXTVLCOPT = "#EXTVLCOPT:"
    private const val WEBPROP = "#WEBPROP:"
    private const val PLAYLIST_TYPE = "#EXT-X-PLAYLIST-TYPE:"
    private val attributeRegex = Regex("""([A-Za-z0-9_-]+)\s*=\s*(?:"([^"]*)"|'([^']*)'|([^\s,]+))""")

    fun parse(playlist: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var state = EntryState()
        var pendingGroups = emptyList<String>()
        var activeGroups = emptyList<String>()
        playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> {
                        val info = parseExtInf(line)
                        if (info.groupTitle.isNotBlank()) activeGroups = emptyList()
                        state = EntryState(info = info, directiveGroups = if (info.groupTitle.isBlank()) pendingGroups.ifEmpty { activeGroups } else emptyList())
                        pendingGroups = emptyList()
                    }
                    line.startsWith(EXTGRP, ignoreCase = true) -> {
                        val groups = splitGroups(line.substringAfter(":"))
                        activeGroups = groups
                        if (state.info == null) pendingGroups = groups
                        else state = state.copy(directiveGroups = groups, metadata = state.metadata + ("extgrp" to line.substringAfter(":").trim()))
                    }
                    line.startsWith(EXTVLCOPT, ignoreCase = true) -> state = state.withProperty("extvlcopt", line.substringAfter(":"))
                    line.startsWith(WEBPROP, ignoreCase = true) -> state = state.withProperty("webprop", line.substringAfter(":"))
                    line.startsWith(PLAYLIST_TYPE, ignoreCase = true) -> state = state.copy(metadata = state.metadata + ("playlistType" to line.substringAfter(":").trim()))
                    line.startsWith("#EXT-X-KEY", ignoreCase = true) ->
                        state = state.withExtXKey(line)
                    line.startsWith(KODIPROP_INPUTSTREAM_ADDON, ignoreCase = true) ->
                        state = state.copy(inputstreamaddon = line.substringAfter("=").trim())
                    line.startsWith(KODIPROP_MANIFEST_TYPE, ignoreCase = true) ->
                        state = state.copy(manifestType = line.substringAfter("=").trim())
                    line.startsWith(KODIPROP_LICENSE_TYPE, ignoreCase = true) ->
                        state = state.copy(drmType = normalizeLicenseType(line.substringAfter("=").trim(), state.drmType))
                    line.startsWith(KODIPROP_LICENSE_KEY, ignoreCase = true) ->
                        state = state.withLicenseKey(line.substringAfter("=").trim())
                    line.startsWith("#KODIPROP:", ignoreCase = true) -> state = state.withProperty("kodiprop", line.substringAfter(":"))
                    line.startsWith("#") -> Unit
                    else -> {
                        val info = state.info
                        val urlParts = splitStreamUrl(line)
                        val metadata = state.metadata.toMutableMap()
                        if (urlParts.second.isNotEmpty()) metadata["urlHeaders"] = urlParts.second
                        if (urlParts.third.isNotEmpty()) {
                            val existing = (metadata["extvlcopt"] as? Map<*, *>)
                                ?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty()
                            metadata["extvlcopt"] = existing + urlParts.third
                        }
                        entries += M3uEntry(
                            name = info?.name?.takeIf { it.isNotBlank() } ?: line,
                            url = urlParts.first,
                            groupTitle = info?.groupTitle.orEmpty().ifBlank { state.directiveGroups.joinToString(";") },
                            logo = info?.logo.orEmpty(),
                            tvgId = info?.tvgId.orEmpty(),
                            drmType = state.drmType,
                            drmLicenseUrl = state.drmLicenseUrl,
                            clearKeysJson = state.clearKeysJson,
                            inputstreamaddon = state.inputstreamaddon,
                            manifestType = state.manifestType,
                            channelNumber = info?.channelNumber.orEmpty(),
                            extraJson = JSONObject().put("m3u", JSONObject(info?.attributes.orEmpty() + metadata)).toString()
                        )
                        state = EntryState()
                    }
                }
            }
        return entries
    }

    private fun parseExtInf(line: String): ExtInf {
        val attributes = attributeRegex.findAll(line)
            .associate { it.groupValues[1].lowercase() to attributeValue(it) }
        val title = line.substringAfter(",", "").trim().ifBlank { attributes["tvg-name"].orEmpty() }
        return ExtInf(
            name = title,
            groupTitle = attributes["group-title"].orEmpty(),
            logo = attributes["tvg-logo"].orEmpty(),
            tvgId = attributes["tvg-id"].orEmpty(),
            channelNumber = attributes["tvg-chno"].orEmpty(),
            attributes = attributes
        )
    }

    private fun EntryState.withExtXKey(line: String): EntryState {
        val drmType = parseExtXKeyDrmType(line) ?: this.drmType
        val licenseUrl = parseAttribute(line, "URI").ifBlank { drmLicenseUrl }
        return copy(drmType = drmType, drmLicenseUrl = licenseUrl)
    }

    private fun EntryState.withLicenseKey(key: String): EntryState =
        if (drmType.equals(DRM_TYPE_CLEARKEY, ignoreCase = true)) {
            copy(clearKeysJson = parseClearKeysJson(key))
        } else {
            copy(drmLicenseUrl = key)
        }

    private fun parseExtXKeyDrmType(line: String): String? =
        parseAttribute(line, "KEYFORMAT")
            .takeIf { it.equals(DRM_TYPE_WIDEVINE, ignoreCase = true) }
            ?.let { DRM_TYPE_WIDEVINE }

    private fun parseAttribute(line: String, name: String): String =
        attributeRegex.findAll(line)
            .firstOrNull { it.groupValues[1].equals(name, ignoreCase = true) }
            ?.let(::attributeValue)
            .orEmpty()

    private fun attributeValue(match: MatchResult): String =
        match.groupValues.getOrNull(2)?.takeIf { it.isNotEmpty() }
            ?: match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }
            ?: match.groupValues.getOrNull(4).orEmpty()

    private fun normalizeLicenseType(type: String, currentType: String): String =
        when {
            type.equals(DRM_TYPE_WIDEVINE, ignoreCase = true) -> DRM_TYPE_WIDEVINE
            type.equals("widevine", ignoreCase = true) -> DRM_TYPE_WIDEVINE
            type.equals("clearkey", ignoreCase = true) -> DRM_TYPE_CLEARKEY
            type.equals(DRM_TYPE_CLEARKEY, ignoreCase = true) -> DRM_TYPE_CLEARKEY
            type.equals("com.clearkey.alpha", ignoreCase = true) -> DRM_TYPE_CLEARKEY
            else -> currentType
        }

    private fun parseClearKeysJson(keyString: String): String {
        val normalized = keyString.trim()
        if (normalized.isBlank()) {
            return ""
        }
        if (normalized.startsWith("{") && normalized.endsWith("}")) {
            val json = runCatching { JSONObject(normalized) }.getOrNull()
            if (json != null) {
                val keys = JSONObject()
                json.keys().forEach { key ->
                    val value = json.optString(key).trim()
                    if (key.isNotBlank() && value.isNotBlank()) {
                        keys.put(key, value)
                    }
                }
                return if (keys.length() > 0) keys.toString() else ""
            }
        }
        val keys = JSONObject()
        normalized.split(";")
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { pair ->
                val parts = pair.split(":", limit = 2)
                if (parts.size == 2 && parts[0].isNotBlank() && parts[1].isNotBlank()) {
                    keys.put(parts[0].trim(), parts[1].trim())
                }
            }
        return if (keys.length() > 0) keys.toString() else ""
    }

    private data class EntryState(
        val info: ExtInf? = null,
        val drmType: String = "",
        val drmLicenseUrl: String = "",
        val clearKeysJson: String = "",
        val inputstreamaddon: String = "",
        val manifestType: String = "",
        val directiveGroups: List<String> = emptyList(),
        val metadata: Map<String, Any> = emptyMap()
    ) {
        fun withProperty(group: String, raw: String): EntryState {
            val key = raw.substringBefore("=", "").trim()
            if (key.isBlank()) return this
            val value = raw.substringAfter("=", "").trim()
            val current = (metadata[group] as? Map<*, *>)?.entries?.associate { it.key.toString() to it.value.toString() }.orEmpty()
            return copy(metadata = metadata + (group to (current + (key to value))))
        }
    }

    private data class ExtInf(
        val name: String,
        val groupTitle: String,
        val logo: String,
        val tvgId: String,
        val channelNumber: String,
        val attributes: Map<String, String>
    )

    private fun splitGroups(value: String): List<String> = value.split(';').map { it.trim() }.filter { it.isNotBlank() }.distinct()

    private fun splitStreamUrl(value: String): Triple<String, Map<String, String>, Map<String, String>> {
        val delimiter = value.indexOf('|')
        if (delimiter < 0 || delimiter == value.lastIndex) return Triple(value, emptyMap(), emptyMap())
        val headers = linkedMapOf<String, String>()
        val vlcOptions = linkedMapOf<String, String>()
        value.substring(delimiter + 1).split('&').forEach { pair ->
            val name = pair.substringBefore('=', "").trim()
            val headerValue = pair.substringAfter('=', "").trim()
            if (name.isBlank() || headerValue.isBlank()) return@forEach
            headers[name] = headerValue
            when (name.lowercase()) {
                "user-agent" -> vlcOptions["http-user-agent"] = headerValue
                "referer", "referrer" -> vlcOptions["http-referrer"] = headerValue
                "origin" -> vlcOptions["http-origin"] = headerValue
                "cookie" -> vlcOptions["http-cookie"] = headerValue
            }
        }
        return Triple(value.substring(0, delimiter), headers, vlcOptions)
    }
}
