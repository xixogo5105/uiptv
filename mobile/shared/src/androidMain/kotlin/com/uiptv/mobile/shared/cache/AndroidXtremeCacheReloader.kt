package com.uiptv.mobile.shared.cache

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import com.uiptv.mobile.shared.accounts.AccountCacheSummary
import com.uiptv.mobile.shared.accounts.AndroidSQLiteAccountRepository
import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL

class AndroidXtremeCacheReloader(
    private val databaseHelper: AndroidUiptvDatabaseHelper
) {
    suspend fun refreshAccount(accountId: Long): M3uRefreshResult = withContext(Dispatchers.IO) {
        val account = AndroidSQLiteAccountRepository(databaseHelper)
            .listAccounts()
            .firstOrNull { it.id == accountId }
            ?: return@withContext M3uRefreshResult.skipped("Account not found.")

        if (account.type != MobileAccountType.XTREME_API) {
            return@withContext M3uRefreshResult.skipped("${account.type.displayName} is not an Xtreme API account.")
        }
        if (account.url.isBlank() || account.username.isBlank() || account.password.isBlank()) {
            return@withContext M3uRefreshResult.failed("Xtreme server URL, username, and password are required.")
        }

        val categories = runCatching { fetchCategories(account) }
            .getOrElse { return@withContext M3uRefreshResult.failed(it.message ?: "Unable to load Xtreme categories.") }
        val streams = runCatching { fetchLiveStreams(account) }
            .getOrElse { return@withContext M3uRefreshResult.failed(it.message ?: "Unable to load Xtreme streams.") }
        if (streams.isEmpty()) {
            return@withContext M3uRefreshResult.failed("No live streams found. Existing cache was kept.")
        }
        val vodCategories = runCatching {
            AndroidVodSeriesCatalogClient().fetchCategories(account, AndroidCatalogMode.VOD)
        }.getOrDefault(emptyList())
        val seriesCategories = runCatching {
            AndroidVodSeriesCatalogClient().fetchCategories(account, AndroidCatalogMode.SERIES)
        }.getOrDefault(emptyList())

        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            clearLiveCache(db, accountId)
            val liveSummary = saveLive(db, account, categories, streams)
            val vodCategoryRows = saveVodSeriesCategories(db, account, AndroidCatalogMode.VOD, vodCategories)
            val seriesCategoryRows = saveVodSeriesCategories(db, account, AndroidCatalogMode.SERIES, seriesCategories)
            val summary = liveSummary.copy(
                vodCategories = vodCategoryRows,
                seriesCategories = seriesCategoryRows
            )
            db.setTransactionSuccessful()
            M3uRefreshResult.succeeded(
                "Saved ${summary.liveCategories} live categories, ${summary.liveChannels} live channels, " +
                    "${summary.vodCategories} VOD categories and ${summary.seriesCategories} series categories.",
                summary
            )
        } finally {
            db.endTransaction()
        }
    }

    private fun fetchCategories(account: MobileAccount): List<XtremeCategory> {
        val json = JSONArray(readApi(account, "get_live_categories"))
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val id = item.optString("category_id")
                val name = item.optString("category_name").ifBlank { "Uncategorized" }
                if (id.isNotBlank()) {
                    add(XtremeCategory(id, name))
                }
            }
        }
    }

    private fun fetchLiveStreams(account: MobileAccount): List<XtremeStream> {
        val json = JSONArray(readApi(account, "get_live_streams"))
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val streamId = item.optString("stream_id")
                val name = item.optString("name")
                if (streamId.isBlank() || name.isBlank()) {
                    continue
                }
                add(
                    XtremeStream(
                        streamId = streamId,
                        name = name,
                        categoryId = item.optString("category_id"),
                        logo = item.optString("stream_icon"),
                        containerExtension = item.optString("container_extension")
                    )
                )
            }
        }
    }

    private fun readApi(account: MobileAccount, action: String): String {
        val base = normalizeBaseUrl(account.url)
        val url = "$base/player_api.php?username=${account.username.url()}&password=${account.password.url()}&action=$action"
        val connection = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = account.httpMethod.ifBlank { "GET" }
        }
        return try {
            val status = connection.responseCode
            val stream = if (status >= 300) connection.errorStream else connection.inputStream
            val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
            if (status >= 300) {
                error(body.ifBlank { "Xtreme request failed with status $status." })
            }
            body
        } finally {
            connection.disconnect()
        }
    }

    private fun saveLive(
        db: SQLiteDatabase,
        account: MobileAccount,
        categories: List<XtremeCategory>,
        streams: List<XtremeStream>
    ): AccountCacheSummary {
        val accountId = requireNotNull(account.id)
        val allCategoryDbId = insertCategory(db, accountId, "all", "All")
        val categoryDbIdByProviderId = linkedMapOf<String, Long>()
        categories.forEach { category ->
            categoryDbIdByProviderId[category.id] = insertCategory(db, accountId, category.id, category.name)
        }
        val hasOrphanedStreams = streams.any { stream ->
            stream.categoryId.isBlank() || !categoryDbIdByProviderId.containsKey(stream.categoryId)
        }
        if (hasOrphanedStreams) {
            categoryDbIdByProviderId["uncategorized"] = insertCategory(db, accountId, "uncategorized", "Uncategorized")
        }

        var channelRows = 0
        streams.forEach { stream ->
            insertChannel(db, allCategoryDbId, account, stream)
            channelRows++
            val categoryDbId = categoryDbIdByProviderId[stream.categoryId]
                ?: categoryDbIdByProviderId["uncategorized"]
                ?: allCategoryDbId
            if (categoryDbId != allCategoryDbId) {
                insertChannel(db, categoryDbId, account, stream)
                channelRows++
            }
        }
        return AccountCacheSummary(
            liveCategories = 1 + categoryDbIdByProviderId.size,
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

    private fun saveVodSeriesCategories(
        db: SQLiteDatabase,
        account: MobileAccount,
        mode: AndroidCatalogMode,
        categories: List<AndroidPortalCategory>
    ): Int {
        val accountId = requireNotNull(account.id)
        val table = when (mode) {
            AndroidCatalogMode.VOD -> "VodCategory"
            AndroidCatalogMode.SERIES -> "SeriesCategory"
        }
        db.delete(table, "accountId = ?", arrayOf(accountId.toString()))
        val cachedAt = System.currentTimeMillis()
        categories.distinctBy { it.title.lowercase() }.forEach { category ->
            db.insert(
                table,
                null,
                ContentValues().apply {
                    put("categoryId", category.id)
                    put("accountId", accountId.toString())
                    put("accountType", mode.accountAction)
                    put("title", category.title)
                    put("alias", category.alias)
                    put("url", "")
                    put("activeSub", category.activeSub)
                    put("censored", category.censored)
                    put("extraJson", category.extraJson)
                    put("cachedAt", cachedAt)
                }
            )
        }
        return categories.distinctBy { it.title.lowercase() }.size
    }

    private fun insertChannel(db: SQLiteDatabase, categoryDbId: Long, account: MobileAccount, stream: XtremeStream) {
        val base = normalizeBaseUrl(account.url)
        val extension = stream.containerExtension.ifBlank { "ts" }
        val streamUrl = "$base/${account.username.pathSegment()}/${account.password.pathSegment()}/${stream.streamId.pathSegment()}.$extension"
        db.insert(
            "Channel",
            null,
            ContentValues().apply {
                put("channelId", stream.streamId)
                put("categoryId", categoryDbId.toString())
                put("name", stream.name)
                put("number", "")
                put("cmd", streamUrl)
                put("cmd_1", "")
                put("cmd_2", "")
                put("cmd_3", "")
                put("logo", stream.logo)
                put("censored", 0)
                put("status", 1)
                put("hd", if (stream.name.contains("HD", ignoreCase = true)) 1 else 0)
                put("drmType", "")
                put("drmLicenseUrl", "")
                put("clearKeysJson", "")
                put("inputstreamaddon", "")
                put("manifestType", "")
            }
        )
    }

    private fun clearLiveCache(db: SQLiteDatabase, accountId: Long) {
        db.execSQL(
            "DELETE FROM Channel WHERE categoryId IN (SELECT id FROM Category WHERE accountId = ?)",
            arrayOf(accountId.toString())
        )
        db.delete("Category", "accountId = ?", arrayOf(accountId.toString()))
    }

    private fun String.url(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.pathSegment(): String =
        url().replace("+", "%20")

    private fun normalizeBaseUrl(source: String): String {
        require(source.isNotBlank()) { "Xtreme server URL is required." }
        var trimmed = source.trim()
        if (!trimmed.contains("://")) {
            trimmed = "http://$trimmed"
        }
        val playerApiIndex = trimmed.lowercase().indexOf("player_api.php")
        if (playerApiIndex >= 0) {
            trimmed = trimmed.substring(0, playerApiIndex)
        }
        return trimmed.trimEnd('/')
    }

    private data class XtremeCategory(val id: String, val name: String)

    private data class XtremeStream(
        val streamId: String,
        val name: String,
        val categoryId: String,
        val logo: String,
        val containerExtension: String
    )
}
