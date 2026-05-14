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
        try {
            clearLiveCache(db, accountId)
            val summary = saveEntries(db, accountId, entries)
            db.setTransactionSuccessful()
            M3uRefreshResult.succeeded("Saved ${summary.liveCategories} categories and ${summary.liveChannels} channels.", summary)
        } finally {
            db.endTransaction()
        }
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
                    input.readBytes().toString(Charsets.UTF_8)
                } ?: error("Unable to open playlist URI.")
            source.startsWith("http://", ignoreCase = true) || source.startsWith("https://", ignoreCase = true) ->
                readUrl(source)
            else -> File(source).readText(Charsets.UTF_8)
        }
    }

    private fun readUrl(source: String): String {
        val connection = (URL(source).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = "GET"
        }
        return try {
            val status = connection.responseCode
            val stream = if (status >= 300) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status >= 300) {
                error(body.ifBlank { "Playlist request failed with status $status." })
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun saveEntries(db: SQLiteDatabase, accountId: Long, entries: List<M3uEntry>): AccountCacheSummary {
        val allCategoryId = insertCategory(db, accountId, "all", "All")
        val categoryIdByTitle = linkedMapOf<String, Long>()
        val groupedEntries = entries.groupBy { it.groupTitle.ifBlank { "Uncategorized" } }
        groupedEntries.keys
            .sortedWith(String.CASE_INSENSITIVE_ORDER)
            .forEach { title ->
                categoryIdByTitle[title] = insertCategory(db, accountId, stableCategoryId(title), title)
            }

        var channelRows = 0
        entries.forEachIndexed { index, entry ->
            insertChannel(db, allCategoryId, entry, index)
            channelRows++
            val categoryDbId = categoryIdByTitle[entry.groupTitle.ifBlank { "Uncategorized" }]
            if (categoryDbId != null) {
                insertChannel(db, categoryDbId, entry, index)
                channelRows++
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
                put("number", "")
                put("cmd", entry.url)
                put("cmd_1", "")
                put("cmd_2", "")
                put("cmd_3", "")
                put("logo", entry.logo)
                put("censored", 0)
                put("status", 1)
                put("hd", if (entry.name.contains("HD", ignoreCase = true)) 1 else 0)
                put("drmType", "")
                put("drmLicenseUrl", "")
                put("clearKeysJson", "")
                put("inputstreamaddon", "")
                put("manifestType", "")
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
    val tvgId: String = ""
)

object M3uPlaylistParser {
    private val attributeRegex = Regex("([A-Za-z0-9_-]+)=\"([^\"]*)\"")

    fun parse(playlist: String): List<M3uEntry> {
        val entries = mutableListOf<M3uEntry>()
        var pendingInfo: ExtInf? = null
        playlist.lineSequence()
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .forEach { line ->
                when {
                    line.startsWith("#EXTINF", ignoreCase = true) -> pendingInfo = parseExtInf(line)
                    line.startsWith("#") -> Unit
                    else -> {
                        val info = pendingInfo
                        pendingInfo = null
                        entries += M3uEntry(
                            name = info?.name?.takeIf { it.isNotBlank() } ?: line,
                            url = line,
                            groupTitle = info?.groupTitle.orEmpty(),
                            logo = info?.logo.orEmpty(),
                            tvgId = info?.tvgId.orEmpty()
                        )
                    }
                }
            }
        return entries
    }

    private fun parseExtInf(line: String): ExtInf {
        val attributes = attributeRegex.findAll(line)
            .associate { it.groupValues[1].lowercase() to it.groupValues[2] }
        return ExtInf(
            name = line.substringAfter(",", "").trim(),
            groupTitle = attributes["group-title"].orEmpty(),
            logo = attributes["tvg-logo"].orEmpty(),
            tvgId = attributes["tvg-id"].orEmpty()
        )
    }

    private data class ExtInf(
        val name: String,
        val groupTitle: String,
        val logo: String,
        val tvgId: String
    )
}
