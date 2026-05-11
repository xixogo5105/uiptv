package com.uiptv.util

import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeList
import com.uiptv.shared.SeasonInfo
import com.uiptv.util.json.optObject
import com.uiptv.util.json.optString
import com.uiptv.util.json.parseJsonArray
import com.uiptv.util.json.parseJsonObject
import com.uiptv.util.json.toPlainMap
import java.io.IOException
import java.io.UncheckedIOException
import java.util.LinkedHashSet

object XtremeApiParser {
    private const val PARAM_CATEGORY_ID = "category_id"

    @JvmStatic
    fun parseCategories(account: Account): List<Category> {
        return try {
            doParseCategories(fetchPlayerApi(account, getCategoryAction(account.action), null))
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to load Xtreme categories", e)
        }
    }

    @JvmStatic
    fun parseChannels(categoryId: String, account: Account): List<Channel> {
        return try {
            val extraParams = linkedMapOf(PARAM_CATEGORY_ID to categoryId)
            doParseChannels(fetchPlayerApi(account, getChannelListAction(account.action), extraParams), account)
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to load Xtreme channels", e)
        }
    }

    @JvmStatic
    fun parseAllChannels(account: Account): List<Channel> {
        return try {
            doParseChannels(fetchPlayerApi(account, getChannelListAction(account.action), null), account)
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to load all Xtreme channels", e)
        }
    }

    @JvmStatic
    fun parseEpisodes(seriesId: String, account: Account): EpisodeList {
        return try {
            val extraParams = linkedMapOf("series_id" to seriesId)
            doParseEpisodes(fetchPlayerApi(account, "get_series_info", extraParams), account)
        } catch (e: IOException) {
            throw UncheckedIOException("Unable to load Xtreme episodes", e)
        }
    }

    private fun doParseCategories(json: String): List<Category> {
        val categoryList = ArrayList<Category>()
        try {
            val list = parseJsonArray(json) ?: return categoryList
            for (i in list.indices) {
                val jsonCategory = list.optObject(i) ?: continue
                val category = Category(
                    jsonCategory.optString(PARAM_CATEGORY_ID),
                    jsonCategory.optString("category_name"),
                    jsonCategory.optString("category_name"),
                    true,
                    0
                )
                category.extraJson = jsonCategory.toString()
                categoryList.add(category)
            }
        } catch (e: Exception) {
            AppLog.addErrorLog(XtremeApiParser::class.java, "xtremeErrorProcessingResponseData: ${e.message}")
        }
        return categoryList
    }

    private fun doParseChannels(json: String, account: Account): List<Channel> {
        val categoryList = ArrayList<Channel>()
        try {
            val list = parseJsonArray(json) ?: return categoryList
            for (i in list.indices) {
                val jsonCategory = list.optObject(i) ?: continue
                val channel = Channel(
                    StringUtils.safeGetString(jsonCategory, if (account.action == Account.AccountAction.series) "series_id" else "stream_id"),
                    StringUtils.safeGetString(jsonCategory, "name"),
                    null,
                    buildXtremeStreamUrl(account, StringUtils.safeGetString(jsonCategory, "stream_id"), StringUtils.safeGetString(jsonCategory, "container_extension")),
                    null,
                    null,
                    null,
                    StringUtils.safeGetString(jsonCategory, if (account.action == Account.AccountAction.series) "cover" else "stream_icon"),
                    0,
                    0,
                    0,
                    null,
                    null,
                    null,
                    null,
                    null
                )
                channel.categoryId = StringUtils.safeGetString(jsonCategory, PARAM_CATEGORY_ID)
                channel.extraJson = jsonCategory.toString()
                categoryList.add(channel)
            }
        } catch (e: Exception) {
            AppLog.addErrorLog(XtremeApiParser::class.java, "xtremeErrorProcessingResponseData: ${e.message}")
        }
        return categoryList
    }

    private fun doParseEpisodes(json: String, account: Account): EpisodeList {
        val episodeList = EpisodeList()
        try {
            val data = parseJsonObject(json) ?: return episodeList
            data.optObject("info")?.let { episodeList.seasonInfo = SeasonInfo(it) }
            val episodes = data.optObject("episodes") ?: return episodeList
            for (entry in episodes.toPlainMap().entries) {
                val seasonEpisodes = entry.value as? List<*> ?: continue
                if (seasonEpisodes.isNotEmpty()) {
                    seasonEpisodes.forEach { episode -> episodeList.episodes.add(Episode(account, episode as? Map<*, *>)) }
                }
            }
        } catch (e: Exception) {
            AppLog.addErrorLog(XtremeApiParser::class.java, "xtremeErrorProcessingResponseData: ${e.message}")
        }
        return episodeList
    }

    private fun getCategoryAction(action: Account.AccountAction): String =
        when (action) {
            Account.AccountAction.vod -> "get_vod_categories"
            Account.AccountAction.series -> "get_series_categories"
            Account.AccountAction.itv -> "get_live_categories"
        }

    private fun getChannelListAction(action: Account.AccountAction): String =
        when (action) {
            Account.AccountAction.vod -> "get_vod_streams"
            Account.AccountAction.series -> "get_series"
            Account.AccountAction.itv -> "get_live_streams"
        }

    @Throws(IOException::class)
    private fun fetchPlayerApi(account: Account, action: String, extraParams: Map<String, String>?): String {
        val baseUrlCandidates = baseUrlCandidates(account)
        if (baseUrlCandidates.isEmpty()) {
            throw IOException("Xtreme base URL is blank.")
        }

        var lastIoException: IOException? = null
        for ((index, candidate) in baseUrlCandidates.withIndex()) {
            val hasMoreCandidates = index + 1 < baseUrlCandidates.size
            try {
                val responseBody = fetchPlayerApiCandidate(candidate, account, action, extraParams, hasMoreCandidates)
                if (responseBody != null) {
                    return responseBody
                }
            } catch (e: Exception) {
                lastIoException = toPlayerApiIOException(e)
            }
        }
        throw lastIoException ?: IOException("Failed to call Xtreme API.")
    }

    private fun fetchPlayerApiCandidate(
        baseUrl: String,
        account: Account,
        action: String,
        extraParams: Map<String, String>?,
        hasMoreCandidates: Boolean
    ): String? {
        val response = try {
            HttpUtil.sendRequest(buildPlayerApiUrl(baseUrl, account, action, extraParams), null, "GET")
        } catch (e: Exception) {
            throw toPlayerApiIOException(e)
        }
        if (isSuccessfulPlayerApiResponse(response)) {
            return response.body
        }
        if (response.statusCode == 404 && hasMoreCandidates) {
            return null
        }
        throw IOException("Xtreme API request failed with HTTP ${response.statusCode}")
    }

    private fun toPlayerApiIOException(e: Exception): IOException =
        if (e is IOException) e else IOException("Failed to call Xtreme API: ${e.message}", e)

    private fun buildPlayerApiUrl(baseUrl: String, account: Account, action: String, extraParams: Map<String, String>?): String {
        val url = StringBuilder(baseUrl)
            .append("player_api.php")
            .append("?username=").append(StringUtils.nullSafeEncode(account.username))
            .append("&password=").append(StringUtils.nullSafeEncode(account.password))
            .append("&action=").append(StringUtils.nullSafeEncode(action))
        appendExtraParams(url, extraParams)
        return url.toString()
    }

    private fun appendExtraParams(url: StringBuilder, extraParams: Map<String, String>?) {
        if (extraParams == null) return
        for (entry in extraParams.entries) {
            url.append('&')
                .append(StringUtils.nullSafeEncode(entry.key))
                .append('=')
                .append(StringUtils.nullSafeEncode(entry.value))
        }
    }

    private fun isSuccessfulPlayerApiResponse(response: HttpUtil.HttpResult): Boolean =
        response.statusCode in 200..299

    private fun normalizedBaseUrl(account: Account?): String {
        if (account == null) return ""
        val fromM3uPath = normalizeBaseUrl(account.m3u8Path)
        return if (StringUtils.isBlank(fromM3uPath)) normalizeBaseUrl(account.url) else fromM3uPath
    }

    private fun baseUrlCandidates(account: Account?): List<String> {
        val candidates = LinkedHashSet<String>()
        if (account != null) {
            val fromM3uPath = normalizeBaseUrl(account.m3u8Path)
            if (!StringUtils.isBlank(fromM3uPath)) {
                candidates.add(fromM3uPath)
            }
            val fromUrl = normalizeBaseUrl(account.url)
            if (!StringUtils.isBlank(fromUrl)) {
                candidates.add(fromUrl)
            }
        }
        return ArrayList(candidates)
    }

    private fun normalizeBaseUrl(source: String?): String {
        if (StringUtils.isBlank(source)) return ""
        var trimmed = source!!.trim()
        if (!trimmed.contains("://")) {
            trimmed = "http://$trimmed"
        }
        val playerApiIndex = trimmed.lowercase().indexOf("player_api.php")
        if (playerApiIndex >= 0) {
            trimmed = trimmed.substring(0, playerApiIndex)
        }
        if (!trimmed.endsWith("/")) {
            trimmed += "/"
        }
        return trimmed
    }

    private fun buildXtremeStreamUrl(account: Account, streamId: String?, extension: String?): String {
        val baseUrl = normalizedBaseUrl(account)
        if (StringUtils.isBlank(baseUrl) || StringUtils.isBlank(streamId)) {
            return ""
        }
        val ext = if (StringUtils.isBlank(extension)) "ts" else extension
        return when (account.action) {
            Account.AccountAction.vod -> "$baseUrl" + "movie/${account.username}/${account.password}/$streamId.$ext"
            Account.AccountAction.series -> "$baseUrl" + "series/${account.username}/${account.password}/$streamId.$ext"
            Account.AccountAction.itv -> "$baseUrl${account.username}/${account.password}/$streamId"
        }
    }
}
