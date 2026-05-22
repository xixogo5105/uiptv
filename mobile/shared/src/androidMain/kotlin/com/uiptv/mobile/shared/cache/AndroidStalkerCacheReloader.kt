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
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID
import kotlin.math.ceil

class AndroidStalkerCacheReloader(
    private val databaseHelper: AndroidUiptvDatabaseHelper
) {
    suspend fun refreshAccount(accountId: Long): M3uRefreshResult = withContext(Dispatchers.IO) {
        val account = AndroidSQLiteAccountRepository(databaseHelper)
            .listAccounts()
            .firstOrNull { it.id == accountId }
            ?: return@withContext M3uRefreshResult.skipped("Account not found.")

        if (account.type != MobileAccountType.STALKER_PORTAL) {
            return@withContext M3uRefreshResult.skipped("${account.type.displayName} is not a Stalker Portal account.")
        }
        if (account.url.isBlank() && account.serverPortalUrl.isBlank()) {
            return@withContext M3uRefreshResult.failed("Stalker portal URL is required.")
        }
        if (account.stalkerMacCandidates().isEmpty()) {
            return@withContext M3uRefreshResult.failed("Stalker MAC address is required.")
        }

        val payload = runCatching { loadRefreshPayload(account) }
            .getOrElse { return@withContext M3uRefreshResult.failed(it.message ?: "Unable to load Stalker portal.") }
        val selectedAccount = payload.account
        val vodCategories = runCatching {
            AndroidVodSeriesCatalogClient().fetchCategories(selectedAccount, AndroidCatalogMode.VOD)
        }.getOrDefault(emptyList())
        val seriesCategories = runCatching {
            AndroidVodSeriesCatalogClient().fetchCategories(selectedAccount, AndroidCatalogMode.SERIES)
        }.getOrDefault(emptyList())

        val db = databaseHelper.writableDatabase
        db.beginTransaction()
        try {
            clearLiveCache(db, accountId)
            val liveSummary = saveLive(db, accountId, payload.categories, payload.streams)
            val vodCategoryRows = saveVodSeriesCategories(db, accountId, AndroidCatalogMode.VOD, vodCategories)
            val seriesCategoryRows = saveVodSeriesCategories(db, accountId, AndroidCatalogMode.SERIES, seriesCategories)
            val summary = liveSummary.copy(
                vodCategories = vodCategoryRows,
                seriesCategories = seriesCategoryRows
            )
            db.setTransactionSuccessful()
            val liveMessage = if (summary.liveChannels > 0) {
                "Saved ${summary.liveCategories} live categories, ${summary.liveChannels} live channels, "
            } else {
                "Saved ${summary.liveCategories} live categories; live channels will load when a category is opened. "
            }
            M3uRefreshResult.succeeded(
                liveMessage + "Saved ${summary.vodCategories} VOD categories and ${summary.seriesCategories} series categories.",
                summary
            )
        } finally {
            db.endTransaction()
        }
    }

    private fun loadRefreshPayload(account: MobileAccount): StalkerRefreshPayload {
        var lastFailure = "Unable to load Stalker portal."
        var deferredPayload: StalkerRefreshPayload? = null
        for (mac in account.stalkerMacCandidates()) {
            val candidate = account.withStalkerMac(mac)
            try {
                val session = connect(candidate)
                val categories = fetchCategories(candidate, session)
                if (categories.isEmpty()) {
                    lastFailure = "No Stalker categories found. Existing cache was kept."
                    continue
                }
                val streams = runCatching {
                    fetchGlobalChannels(candidate, session)
                }.getOrElse { error ->
                    lastFailure = error.message ?: "Unable to load Stalker channels from get_all_channels."
                    emptyList()
                }
                if (streams.isNotEmpty()) {
                    return StalkerRefreshPayload(candidate, categories, streams)
                }
                lastFailure = "No Stalker channels found from get_all_channels. Live channels will load by category."
                if (deferredPayload == null) {
                    deferredPayload = StalkerRefreshPayload(candidate, categories, emptyList())
                }
            } catch (e: Exception) {
                lastFailure = e.message ?: "Unable to connect to Stalker portal."
            }
        }
        return deferredPayload ?: error(lastFailure)
    }

    internal fun fetchAndSaveLiveCategory(
        account: MobileAccount,
        categoryRowId: Long,
        categoryProviderId: String
    ): Int {
        if (account.type != MobileAccountType.STALKER_PORTAL || categoryProviderId.isBlank() || categoryProviderId == "all") {
            return 0
        }
        for (mac in account.stalkerMacCandidates()) {
            val candidate = account.withStalkerMac(mac)
            try {
                val session = connect(candidate)
                val streams = fetchCategoryChannels(candidate, session, categoryProviderId)
                    .distinctBy { "${it.channelId}|${it.command}|${it.name.lowercase()}" }
                if (streams.isEmpty()) {
                    continue
                }
                val db = databaseHelper.writableDatabase
                db.beginTransaction()
                try {
                    db.delete("Channel", "categoryId = ?", arrayOf(categoryRowId.toString()))
                    streams.forEach { stream -> insertChannel(db, categoryRowId, stream) }
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                }
                return streams.size
            } catch (e: Exception) {
                // Try the next saved MAC candidate before giving up on lazy category load.
            }
        }
        return 0
    }

    private fun connect(account: MobileAccount): StalkerSession {
        val portalUrl = normalizePortalUrl(account.serverPortalUrl.ifBlank { account.url })
        val handshake = readPortal(
            portalUrl = portalUrl,
            account = account,
            token = "",
            params = mapOf("type" to "stb", "action" to "handshake", "token" to "")
        )
        val handshakeJson = JSONObject(handshake)
        val token = handshakeJson.optJSONObject("js")?.optString("token")
            ?: handshakeJson.optString("token")
        require(token.isNotBlank()) { "Unable to retrieve Stalker token." }
        runCatching {
            readPortal(
                portalUrl = portalUrl,
                account = account,
                token = token,
                params = getProfileParams(account)
            )
        }
        return StalkerSession(portalUrl, token)
    }

    private fun fetchCategories(account: MobileAccount, session: StalkerSession): List<StalkerCategory> {
        val body = readPortal(
            portalUrl = session.portalUrl,
            account = account,
            token = session.token,
            params = mapOf("type" to "itv", "action" to "get_genres")
        )
        val list = JSONObject(body).stalkerDataArray()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val title = item.optString("title").ifBlank { item.optString("alias") }
                if (id.isNotBlank() && title.isNotBlank() && !title.equals("All", ignoreCase = true)) {
                    add(StalkerCategory(id, title))
                }
            }
        }
    }

    private fun fetchGlobalChannels(account: MobileAccount, session: StalkerSession): List<StalkerChannel> {
        val fastChannels = fetchGlobalChannelsFast(account, session)
        if (fastChannels.isNotEmpty()) {
            return fastChannels
        }
        return fetchGlobalChannelsPaged(account, session)
    }

    private fun fetchGlobalChannelsFast(account: MobileAccount, session: StalkerSession): List<StalkerChannel> {
        val attempts = listOf(
            emptyMap(),
            mapOf("p" to "0", "per_page" to "99999"),
            mapOf("p" to "1", "per_page" to "99999")
        )
        attempts.forEach { paging ->
            val parsed = runCatching {
                val body = readPortal(
                    portalUrl = session.portalUrl,
                    account = account,
                    token = session.token,
                    params = mapOf("type" to "itv", "action" to "get_all_channels") + paging
                )
                parseChannels(body)
            }.getOrDefault(emptyList())
            if (parsed.isNotEmpty()) {
                return parsed.distinctBy { "${it.channelId}|${it.command}|${it.name.lowercase()}" }
            }
        }
        return emptyList()
    }

    private fun fetchGlobalChannelsPaged(account: MobileAccount, session: StalkerSession): List<StalkerChannel> {
        for (startPage in listOf(0, 1)) {
            val firstPage = readGlobalChannelsPage(
                account,
                session,
                mapOf("p" to startPage.toString(), "per_page" to STALKER_PAGE_SIZE)
            )
            if (firstPage.channels.isEmpty()) {
                continue
            }
            val channels = firstPage.channels.toMutableList()
            val maxPage = firstPage.pageCount.coerceAtLeast(1).coerceAtMost(MAX_STALKER_PAGES)
            for (page in (startPage + 1)..maxPage) {
                val nextPage = readGlobalChannelsPage(
                    account,
                    session,
                    mapOf("p" to page.toString(), "per_page" to STALKER_PAGE_SIZE)
                )
                if (nextPage.channels.isEmpty()) {
                    break
                }
                channels += nextPage.channels
            }
            return channels.distinctBy { "${it.channelId}|${it.command}|${it.name.lowercase()}" }
        }
        return emptyList()
    }

    private fun readGlobalChannelsPage(
        account: MobileAccount,
        session: StalkerSession,
        paging: Map<String, String>
    ): StalkerPage {
        val body = readPortal(
            portalUrl = session.portalUrl,
            account = account,
            token = session.token,
            params = mapOf("type" to "itv", "action" to "get_all_channels") + paging,
            maxBytes = MAX_STALKER_PAGE_BODY_BYTES
        )
        val root = JSONObject(body)
        val js = root.optJSONObject("js") ?: root
        return StalkerPage(parseChannels(body), resolveStalkerPageCount(root, js))
    }

    private fun fetchCategoryChannels(
        account: MobileAccount,
        session: StalkerSession,
        categoryId: String
    ): List<StalkerChannel> {
        for (startPage in listOf(0, 1)) {
            val firstPage = fetchCategoryPage(account, session, categoryId, startPage)
            if (firstPage.channels.isEmpty()) {
                continue
            }
            val channels = firstPage.channels.toMutableList()
            val maxPage = firstPage.pageCount.coerceAtLeast(1).coerceAtMost(MAX_STALKER_PAGES)
            for (page in (startPage + 1)..maxPage) {
                val nextPage = fetchCategoryPage(account, session, categoryId, page)
                if (nextPage.channels.isEmpty()) {
                    break
                }
                channels += nextPage.channels
            }
            return channels
        }
        return emptyList()
    }

    private fun fetchCategoryPage(
        account: MobileAccount,
        session: StalkerSession,
        categoryId: String,
        page: Int
    ): StalkerPage {
        val body = readPortal(
            portalUrl = session.portalUrl,
            account = account,
            token = session.token,
            params = mapOf(
                "type" to "itv",
                "action" to "get_ordered_list",
                "genre" to categoryId,
                "force_ch_link_check" to "",
                "fav" to "0",
                "sortby" to "added",
                "hd" to "1",
                "p" to page.toString(),
                "per_page" to STALKER_PAGE_SIZE,
                "max_count" to "0"
            )
        )
        val root = JSONObject(body)
        val js = root.optJSONObject("js") ?: root
        val pageCount = resolveStalkerPageCount(root, js)
        return StalkerPage(parseChannels(body, categoryId), pageCount)
    }

    private fun parseChannels(body: String, fallbackCategoryId: String = ""): List<StalkerChannel> {
        val root = JSONObject(body)
        val list = root.stalkerDataArray()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.optString("id")
                val name = item.optString("name")
                if (id.isBlank() || name.isBlank()) {
                    continue
                }
                add(
                    StalkerChannel(
                        channelId = id,
                        name = name,
                        number = item.optString("number"),
                        categoryId = item.optString("tv_genre_id").ifBlank { fallbackCategoryId },
                        command = item.optString("cmd"),
                        command1 = item.optString("cmd_1"),
                        command2 = item.optString("cmd_2"),
                        command3 = item.optString("cmd_3"),
                        logo = item.optString("logo"),
                        censored = item.optInt("censored", 0),
                        status = item.optInt("status", 1),
                        hd = item.optInt("hd", 0)
                    )
                )
            }
        }
    }

    private fun resolveStalkerPageCount(root: JSONObject, js: JSONObject): Int {
        val pagination = root.optJSONObject("pagination") ?: js
        val totalItems = pagination.optInt("total_items", 0)
        val maxPageItems = pagination.optInt("max_page_items", 0)
        return if (totalItems > 0 && maxPageItems > 0) {
            ceil(totalItems.toDouble() / maxPageItems.toDouble()).toInt()
        } else {
            1
        }
    }

    private fun readPortal(
        portalUrl: String,
        account: MobileAccount,
        token: String,
        params: Map<String, String>,
        maxBytes: Int = MAX_CACHE_HTTP_BODY_BYTES
    ): String {
        val payload = (params + ("JsHttpRequest" to "${System.currentTimeMillis()}-xml")).toQueryString()
        val method = account.httpMethod.ifBlank { "GET" }.trim().uppercase()
        val isPost = method == "POST"
        val requestUrl = if (isPost) portalUrl else "$portalUrl?$payload"
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = method
            setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3")
            setRequestProperty("X-User-Agent", "Model: MAG250; Link: WiFi")
            setRequestProperty("Referer", account.url.ifBlank { portalUrl })
            setRequestProperty("Accept", "*/*")
            setRequestProperty("Pragma", "no-cache")
            setRequestProperty("Cookie", "mac=${account.macAddress}; stb_lang=en; timezone=${account.timezone.ifBlank { "Europe/London" }};")
            if (token.isNotBlank()) {
                setRequestProperty("Authorization", "Bearer $token")
            }
            if (isPost) {
                doOutput = true
                setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }
            }
        }
        return connection.readCacheBody("Stalker request", maxBytes)
    }

    private fun saveLive(
        db: SQLiteDatabase,
        accountId: Long,
        categories: List<StalkerCategory>,
        streams: List<StalkerChannel>
    ): AccountCacheSummary {
        val allCategoryDbId = insertCategory(db, accountId, "all", "All")
        val categoryDbIdByProviderId = linkedMapOf<String, Long>()
        categories.forEach { category ->
            categoryDbIdByProviderId[category.id] = insertCategory(db, accountId, category.id, category.title)
        }
        val hasOrphanedStreams = streams.any { stream ->
            stream.categoryId.isBlank() || !categoryDbIdByProviderId.containsKey(stream.categoryId)
        }
        if (hasOrphanedStreams) {
            categoryDbIdByProviderId["uncategorized"] = insertCategory(db, accountId, "uncategorized", "Uncategorized")
        }

        var channelRows = 0
        streams.forEach { stream ->
            insertChannel(db, allCategoryDbId, stream)
            channelRows++
            val categoryDbId = categoryDbIdByProviderId[stream.categoryId]
                ?: categoryDbIdByProviderId["uncategorized"]
                ?: allCategoryDbId
            if (categoryDbId != allCategoryDbId) {
                insertChannel(db, categoryDbId, stream)
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
        accountId: Long,
        mode: AndroidCatalogMode,
        categories: List<AndroidPortalCategory>
    ): Int {
        val table = when (mode) {
            AndroidCatalogMode.VOD -> "VodCategory"
            AndroidCatalogMode.SERIES -> "SeriesCategory"
        }
        db.delete(table, "accountId = ?", arrayOf(accountId.toString()))
        val cachedAt = System.currentTimeMillis()
        val normalized = categories.distinctBy { it.title.lowercase() }
        normalized.forEach { category ->
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
        return normalized.size
    }

    private fun insertChannel(db: SQLiteDatabase, categoryDbId: Long, stream: StalkerChannel) {
        db.insert(
            "Channel",
            null,
            ContentValues().apply {
                put("channelId", stream.channelId)
                put("categoryId", categoryDbId.toString())
                put("name", stream.name)
                put("number", stream.number)
                put("cmd", stream.command)
                put("cmd_1", stream.command1)
                put("cmd_2", stream.command2)
                put("cmd_3", stream.command3)
                put("logo", stream.logo)
                put("censored", stream.censored)
                put("status", stream.status)
                put("hd", stream.hd)
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

    private fun JSONObject.stalkerDataArray() =
        when (val js = opt("js")) {
            is org.json.JSONArray -> js
            is JSONObject -> js.optJSONArray("data") ?: org.json.JSONArray()
            else -> optJSONArray("data") ?: org.json.JSONArray()
        }

    private fun normalizePortalUrl(source: String): String {
        var candidate = source.trim()
        if (!candidate.contains("://")) {
            candidate = "http://$candidate"
        }
        return if (candidate.lowercase().endsWith("portal.php") || candidate.lowercase().endsWith("load.php")) {
            candidate
        } else {
            "${candidate.trimEnd('/')}/portal.php"
        }
    }

    private fun Map<String, String>.toQueryString(): String =
        entries.joinToString("&") { (key, value) ->
            "${key.url()}=${value.url()}"
        }

    private fun String.url(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun getProfileParams(account: MobileAccount): Map<String, String> {
        val serial = account.serialNumber.ifBlank { randomId().take(32).uppercase() }
        val device1 = account.deviceId1.ifBlank { randomId() }
        val device2 = account.deviceId2.ifBlank { device1 }
        val signature = account.signature.ifBlank { randomId() }
        return mapOf(
            "type" to "stb",
            "action" to "get_profile",
            "hd" to "1",
            "ver" to "ImageDescription: 0.2.18-r23-250; ImageDate: Wed Aug 29 10:49:53 EEST 2018; PORTAL version: 5.6.9; API Version: JS API version: 343; STB API version: 146; Player Engine version: 0x58c",
            "num_banks" to "2",
            "sn" to serial,
            "stb_type" to "MAG250",
            "client_type" to "STB",
            "image_version" to "218",
            "video_out" to "hdmi",
            "device_id" to device1,
            "device_id2" to device2,
            "signature" to signature,
            "auth_second_step" to "1",
            "hw_version" to "1.7-BD-00",
            "not_valid_token" to "0",
            "metrics" to "{\"mac\":\"${account.macAddress}\",\"sn\":\"$serial\",\"type\":\"STB\",\"model\":\"MAG250\",\"uid\":\"\",\"random\":\"${randomId()}\"}",
            "hw_version_2" to randomId(),
            "api_signature" to "262",
            "prehash" to ""
        )
    }

    private fun randomId(): String =
        (UUID.randomUUID().toString() + UUID.randomUUID().toString()).replace("-", "")

    private data class StalkerRefreshPayload(
        val account: MobileAccount,
        val categories: List<StalkerCategory>,
        val streams: List<StalkerChannel>
    )

    private data class StalkerSession(val portalUrl: String, val token: String)

    private data class StalkerPage(val channels: List<StalkerChannel>, val pageCount: Int)

    private data class StalkerCategory(val id: String, val title: String)

    private data class StalkerChannel(
        val channelId: String,
        val name: String,
        val number: String,
        val categoryId: String,
        val command: String,
        val command1: String,
        val command2: String,
        val command3: String,
        val logo: String,
        val censored: Int,
        val status: Int,
        val hd: Int
    )

    private companion object {
        const val STALKER_PAGE_SIZE = "999"
        const val MAX_STALKER_PAGES = 200
        const val MAX_STALKER_PAGE_BODY_BYTES = 8 * 1024 * 1024
    }
}
