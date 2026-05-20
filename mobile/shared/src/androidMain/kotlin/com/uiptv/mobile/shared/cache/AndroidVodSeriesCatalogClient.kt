package com.uiptv.mobile.shared.cache

import com.uiptv.mobile.shared.accounts.MobileAccount
import com.uiptv.mobile.shared.accounts.MobileAccountType
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.UUID
import kotlin.math.ceil

internal enum class AndroidCatalogMode(val accountAction: String) {
    VOD("vod"),
    SERIES("series")
}

internal data class AndroidPortalCategory(
    val id: String,
    val title: String,
    val alias: String = title,
    val activeSub: Int = 0,
    val censored: Int = 0,
    val extraJson: String = ""
)

internal data class AndroidPortalChannel(
    val channelId: String,
    val categoryId: String,
    val name: String,
    val number: String = "",
    val command: String = "",
    val logo: String = "",
    val censored: Int = 0,
    val status: Int = 1,
    val hd: Int = 0,
    val extraJson: String = "",
    val plot: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val duration: String = "",
    val season: String = "",
    val episodeNumber: String = ""
)

internal data class AndroidPortalEpisode(
    val episodeId: String,
    val title: String,
    val command: String,
    val logo: String = "",
    val season: String = "",
    val episodeNumber: String = "",
    val plot: String = "",
    val releaseDate: String = "",
    val rating: String = "",
    val duration: String = "",
    val extraJson: String = ""
)

internal class AndroidVodSeriesCatalogClient {
    fun fetchCategories(account: MobileAccount, mode: AndroidCatalogMode): List<AndroidPortalCategory> =
        when (account.type) {
            MobileAccountType.XTREME_API -> fetchXtremeCategories(account, mode)
            MobileAccountType.STALKER_PORTAL -> fetchStalkerCategoriesWithFallback(account, mode)
            else -> emptyList()
        }

    fun fetchChannels(account: MobileAccount, mode: AndroidCatalogMode, categoryId: String): List<AndroidPortalChannel> =
        when (account.type) {
            MobileAccountType.XTREME_API -> fetchXtremeChannels(account, mode, categoryId)
            MobileAccountType.STALKER_PORTAL -> fetchStalkerOrderedListWithFallback(account, mode, categoryId)
            else -> emptyList()
        }

    fun fetchSeriesEpisodes(account: MobileAccount, categoryId: String, seriesId: String): List<AndroidPortalEpisode> =
        when (account.type) {
            MobileAccountType.XTREME_API -> fetchXtremeEpisodes(account, seriesId)
            MobileAccountType.STALKER_PORTAL -> fetchStalkerOrderedListWithFallback(
                account = account,
                mode = AndroidCatalogMode.SERIES,
                categoryId = categoryId,
                movieId = seriesId,
                seasonId = "0"
            ).map { channel ->
                AndroidPortalEpisode(
                    episodeId = channel.channelId,
                    title = channel.name,
                    command = channel.command,
                    logo = channel.logo,
                    season = channel.season,
                    episodeNumber = channel.episodeNumber,
                    plot = channel.plot,
                    releaseDate = channel.releaseDate,
                    rating = channel.rating,
                    duration = channel.duration,
                    extraJson = channel.extraJson
                )
            }
            else -> emptyList()
        }

    private fun fetchXtremeCategories(account: MobileAccount, mode: AndroidCatalogMode): List<AndroidPortalCategory> {
        val action = when (mode) {
            AndroidCatalogMode.VOD -> "get_vod_categories"
            AndroidCatalogMode.SERIES -> "get_series_categories"
        }
        val json = JSONArray(readXtremeApi(account, action))
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val id = item.cleanString("category_id")
                val title = item.cleanString("category_name").ifBlank { "Uncategorized" }
                if (id.isNotBlank()) {
                    add(AndroidPortalCategory(id = id, title = title, alias = title, extraJson = item.toString()))
                }
            }
        }
    }

    private fun fetchXtremeChannels(account: MobileAccount, mode: AndroidCatalogMode, categoryId: String): List<AndroidPortalChannel> {
        val action = when (mode) {
            AndroidCatalogMode.VOD -> "get_vod_streams"
            AndroidCatalogMode.SERIES -> "get_series"
        }
        val json = JSONArray(readXtremeApi(account, action, mapOf("category_id" to categoryId)))
        return buildList {
            for (index in 0 until json.length()) {
                val item = json.optJSONObject(index) ?: continue
                val channelId = when (mode) {
                    AndroidCatalogMode.VOD -> item.cleanString("stream_id")
                    AndroidCatalogMode.SERIES -> item.cleanString("series_id")
                }
                val name = item.cleanString("name")
                if (channelId.isBlank() || name.isBlank()) {
                    continue
                }
                val extension = item.cleanString("container_extension").ifBlank { "mp4" }
                add(
                    AndroidPortalChannel(
                        channelId = channelId,
                        categoryId = item.cleanString("category_id").ifBlank { categoryId },
                        name = name,
                        command = if (mode == AndroidCatalogMode.VOD) xtremeStreamUrl(account, mode, channelId, extension) else "",
                        logo = item.firstCleanString("stream_icon", "cover", "cover_big", "movie_image", "poster"),
                        hd = if (name.contains("HD", ignoreCase = true)) 1 else 0,
                        extraJson = item.toString(),
                        plot = item.firstCleanString("plot", "description", "overview"),
                        releaseDate = item.firstCleanString("release_date", "released", "year"),
                        rating = item.firstCleanString("rating_imdb", "rating"),
                        duration = item.firstCleanString("duration", "runtime", "time")
                    )
                )
            }
        }
    }

    private fun fetchXtremeEpisodes(account: MobileAccount, seriesId: String): List<AndroidPortalEpisode> {
        if (seriesId.isBlank()) {
            return emptyList()
        }
        val root = JSONObject(readXtremeApi(account, "get_series_info", mapOf("series_id" to seriesId)))
        val episodes = root.optJSONObject("episodes") ?: return emptyList()
        return buildList {
            val seasonKeys = episodes.keys()
            while (seasonKeys.hasNext()) {
                val seasonKey = seasonKeys.next()
                val seasonEpisodes = episodes.optJSONArray(seasonKey) ?: continue
                for (index in 0 until seasonEpisodes.length()) {
                    val item = seasonEpisodes.optJSONObject(index) ?: continue
                    val episodeId = item.cleanString("id")
                    if (episodeId.isBlank()) {
                        continue
                    }
                    val extension = item.cleanString("container_extension").ifBlank { "mp4" }
                    val info = item.optJSONObject("info")
                    add(
                        AndroidPortalEpisode(
                            episodeId = episodeId,
                            title = item.cleanString("title").ifBlank { "Episode ${item.cleanString("episode_num").ifBlank { episodeId }}" },
                            command = item.cleanString("direct_source").ifBlank {
                                xtremeStreamUrl(account, AndroidCatalogMode.SERIES, episodeId, extension)
                            },
                            logo = item.firstCleanString("movie_image", "thumbnail", "still_path", "cover_big", "cover", "screenshot_uri", "stream_icon", "image", "poster")
                                .ifBlank { info?.firstCleanString("movie_image", "thumbnail", "still_path", "cover_big", "cover", "screenshot_uri", "stream_icon", "image", "poster").orEmpty() },
                            season = item.cleanString("season").ifBlank { seasonKey },
                            episodeNumber = item.cleanString("episode_num"),
                            plot = info?.firstCleanString("plot", "description", "overview").orEmpty(),
                            releaseDate = info?.firstCleanString("release_date", "released", "year").orEmpty(),
                            rating = info?.firstCleanString("rating_imdb", "rating").orEmpty(),
                            duration = info?.firstCleanString("duration", "runtime", "time").orEmpty(),
                            extraJson = item.toString()
                        )
                    )
                }
            }
        }
    }

    private fun readXtremeApi(account: MobileAccount, action: String, extraParams: Map<String, String> = emptyMap()): String {
        val base = normalizeBaseUrl(account.url.ifBlank { account.m3u8Path })
        val params = linkedMapOf(
            "username" to account.username,
            "password" to account.password,
            "action" to action
        ) + extraParams
        val connection = (URL("${base}player_api.php?${params.toQueryString()}").openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = account.httpMethod.ifBlank { "GET" }
        }
        return connection.readCacheBody("Xtreme request")
    }

    private fun connectStalker(account: MobileAccount): StalkerSession {
        val portalUrl = normalizePortalUrl(account.serverPortalUrl.ifBlank { account.url })
        val handshake = readPortal(
            portalUrl = portalUrl,
            account = account,
            token = "",
            params = mapOf("type" to "stb", "action" to "handshake", "token" to "")
        )
        val handshakeJson = JSONObject(handshake)
        val token = handshakeJson.optJSONObject("js")?.cleanString("token")
            ?: handshakeJson.cleanString("token")
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

    private fun fetchStalkerCategoriesWithFallback(
        account: MobileAccount,
        mode: AndroidCatalogMode
    ): List<AndroidPortalCategory> {
        for (candidate in account.stalkerCandidateAccounts()) {
            val categories = runCatching {
                val session = connectStalker(candidate)
                fetchStalkerCategories(candidate, session, mode)
            }.getOrDefault(emptyList())
            if (categories.isNotEmpty()) {
                return categories
            }
        }
        return emptyList()
    }

    private fun fetchStalkerOrderedListWithFallback(
        account: MobileAccount,
        mode: AndroidCatalogMode,
        categoryId: String,
        movieId: String = "",
        seasonId: String = "0"
    ): List<AndroidPortalChannel> {
        for (candidate in account.stalkerCandidateAccounts()) {
            val channels = runCatching {
                val session = connectStalker(candidate)
                fetchStalkerOrderedList(candidate, session, mode, categoryId, movieId, seasonId)
            }.getOrDefault(emptyList())
            if (channels.isNotEmpty()) {
                return channels
            }
        }
        return emptyList()
    }

    private fun fetchStalkerCategories(
        account: MobileAccount,
        session: StalkerSession,
        mode: AndroidCatalogMode
    ): List<AndroidPortalCategory> {
        val body = readPortal(
            portalUrl = session.portalUrl,
            account = account,
            token = session.token,
            params = mapOf("type" to mode.accountAction, "action" to "get_categories")
        )
        val list = JSONObject(body).optJSONArray("js") ?: JSONArray()
        return buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.cleanString("id")
                val title = item.cleanString("title").ifBlank { item.cleanString("alias") }
                if (id.isNotBlank() && title.isNotBlank() && !title.equals("All", ignoreCase = true)) {
                    add(
                        AndroidPortalCategory(
                            id = id,
                            title = title,
                            alias = item.cleanString("alias").ifBlank { title },
                            activeSub = if (item.optBoolean("active_sub", false)) 1 else 0,
                            censored = item.optInt("censored", 0),
                            extraJson = item.toString()
                        )
                    )
                }
            }
        }
    }

    private fun fetchStalkerOrderedList(
        account: MobileAccount,
        session: StalkerSession,
        mode: AndroidCatalogMode,
        categoryId: String,
        movieId: String = "",
        seasonId: String = "0"
    ): List<AndroidPortalChannel> {
        for (startPage in listOf(0, 1)) {
            val firstPage = fetchStalkerPage(account, session, mode, categoryId, movieId, seasonId, startPage)
            if (firstPage.channels.isEmpty()) {
                continue
            }
            val channels = firstPage.channels.toMutableList()
            val maxPage = firstPage.pageCount.coerceAtLeast(1).coerceAtMost(MAX_STALKER_PAGES)
            for (page in (startPage + 1)..maxPage) {
                val nextPage = fetchStalkerPage(account, session, mode, categoryId, movieId, seasonId, page)
                if (nextPage.channels.isEmpty()) {
                    break
                }
                channels += nextPage.channels
            }
            return channels.distinctBy { "${it.channelId}|${it.command}|${it.name.lowercase()}" }
        }
        return emptyList()
    }

    private fun fetchStalkerPage(
        account: MobileAccount,
        session: StalkerSession,
        mode: AndroidCatalogMode,
        categoryId: String,
        movieId: String,
        seasonId: String,
        page: Int
    ): StalkerPage {
        val params = linkedMapOf(
            "type" to mode.accountAction,
            "action" to "get_ordered_list",
            "genre" to categoryId,
            "force_ch_link_check" to "",
            "fav" to "0",
            "sortby" to "added",
            "hd" to "1",
            "p" to page.toString(),
            "per_page" to "999",
            "max_count" to "0"
        )
        if (mode == AndroidCatalogMode.SERIES) {
            params["movie_id"] = movieId.ifBlank { "0" }
            params["category"] = categoryId
            params["season_id"] = seasonId.ifBlank { "0" }
            params["episode_id"] = "0"
        }
        val body = readPortal(session.portalUrl, account, session.token, params)
        val root = JSONObject(body)
        val js = root.optJSONObject("js") ?: root
        val data = js.optJSONArray("data") ?: JSONArray()
        val pageCount = resolveStalkerPageCount(root, js)
        return StalkerPage(parseStalkerChannels(data, mode, categoryId), pageCount)
    }

    private fun parseStalkerChannels(
        list: JSONArray,
        mode: AndroidCatalogMode,
        requestedCategoryId: String
    ): List<AndroidPortalChannel> =
        buildList {
            for (index in 0 until list.length()) {
                val item = list.optJSONObject(index) ?: continue
                val id = item.cleanString("id")
                val name = item.cleanString("name").ifBlank { item.cleanString("o_name") }
                if (id.isBlank() || name.isBlank()) {
                    continue
                }
                val categoryId = item.cleanString("tv_genre_id").ifBlank { requestedCategoryId }
                val command = item.cleanString("cmd")
                val logo = item.firstCleanString("screenshot_uri", "stream_icon", "cover", "movie_image", "logo", "poster")
                val season = item.firstCleanString("season", "season_id", "season_num", "season_number")
                val seriesEpisodes = item.optJSONArray("series")
                if (mode == AndroidCatalogMode.SERIES && command.isNotBlank() && seriesEpisodes != null) {
                    for (episodeIndex in 0 until seriesEpisodes.length()) {
                        val episodeId = seriesEpisodes.optString(episodeIndex)
                        if (episodeId.isBlank()) {
                            continue
                        }
                        add(
                            AndroidPortalChannel(
                                channelId = episodeId,
                                categoryId = categoryId,
                                name = "$name - Episode $episodeId",
                                number = id,
                                command = command,
                                logo = logo,
                                censored = item.optInt("censored", 0),
                                status = item.optInt("status", 1),
                                hd = item.optInt("hd", 0),
                                extraJson = item.toString(),
                                plot = item.firstCleanString("description", "plot", "overview"),
                                releaseDate = item.firstCleanString("release_date", "released", "year"),
                                rating = item.firstCleanString("rating_imdb", "rating"),
                                duration = item.firstCleanString("duration", "runtime", "time"),
                                season = season,
                                episodeNumber = episodeId
                            )
                        )
                    }
                } else {
                    add(
                        AndroidPortalChannel(
                            channelId = id,
                            categoryId = categoryId,
                            name = name,
                            number = item.cleanString("number").ifBlank { id },
                            command = command,
                            logo = logo,
                            censored = item.optInt("censored", 0),
                            status = item.optInt("status", 1),
                            hd = item.optInt("hd", 0),
                            extraJson = item.toString(),
                            plot = item.firstCleanString("description", "plot", "overview"),
                            releaseDate = item.firstCleanString("release_date", "released", "year"),
                            rating = item.firstCleanString("rating_imdb", "rating"),
                            duration = item.firstCleanString("duration", "runtime", "time"),
                            season = season
                        )
                    )
                }
            }
        }

    private fun readPortal(
        portalUrl: String,
        account: MobileAccount,
        token: String,
        params: Map<String, String>
    ): String {
        val payload = (params + ("JsHttpRequest" to "${System.currentTimeMillis()}-xml")).toQueryString()
        val method = account.httpMethod.ifBlank { "GET" }.trim().uppercase()
        val isPost = method == "POST"
        val requestUrl = if (isPost) portalUrl else "$portalUrl?$payload"
        val connection = (URL(requestUrl).openConnection() as HttpURLConnection).apply {
            connectTimeout = 15_000
            readTimeout = 60_000
            requestMethod = method
            setRequestProperty("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG250 stbapp ver: 2 rev: 250 Safari/533.3")
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
        return connection.readCacheBody("Stalker request")
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
            "api_signature" to "262"
        )
    }

    private fun xtremeStreamUrl(
        account: MobileAccount,
        mode: AndroidCatalogMode,
        streamId: String,
        extension: String
    ): String {
        val base = normalizeBaseUrl(account.url.ifBlank { account.m3u8Path })
        val prefix = when (mode) {
            AndroidCatalogMode.VOD -> "movie"
            AndroidCatalogMode.SERIES -> "series"
        }
        return "$base$prefix/${account.username.pathSegment()}/${account.password.pathSegment()}/${streamId.pathSegment()}.$extension"
    }

    private fun normalizeBaseUrl(source: String): String {
        require(source.isNotBlank()) { "Server URL is required." }
        var trimmed = source.trim()
        if (!trimmed.contains("://")) {
            trimmed = "http://$trimmed"
        }
        val playerApiIndex = trimmed.lowercase().indexOf("player_api.php")
        if (playerApiIndex >= 0) {
            trimmed = trimmed.substring(0, playerApiIndex)
        }
        return trimmed.trimEnd('/') + "/"
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

    private fun MobileAccount.stalkerCandidateAccounts(): List<MobileAccount> =
        stalkerMacCandidates().map { withStalkerMac(it) }

    private fun Map<String, String>.toQueryString(): String =
        entries.joinToString("&") { (key, value) -> "${key.url()}=${value.url()}" }

    private fun String.url(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun String.pathSegment(): String =
        url().replace("+", "%20")

    private fun randomId(): String =
        UUID.randomUUID().toString().replace("-", "")

    private fun JSONObject.cleanString(key: String): String {
        if (!has(key) || isNull(key)) {
            return ""
        }
        val value = optString(key).trim()
        return if (value.isBlank() || value.equals("null", ignoreCase = true) || value.equals("n/a", ignoreCase = true)) {
            ""
        } else {
            value
        }
    }

    private fun JSONObject.firstCleanString(vararg keys: String): String {
        for (key in keys) {
            val value = cleanString(key)
            if (value.isNotBlank()) {
                return value
            }
        }
        return ""
    }

    private data class StalkerSession(val portalUrl: String, val token: String)

    private data class StalkerPage(val channels: List<AndroidPortalChannel>, val pageCount: Int)

    private companion object {
        const val MAX_STALKER_PAGES = 200
    }
}
