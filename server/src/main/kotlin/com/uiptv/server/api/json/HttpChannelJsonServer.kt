package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.CategoryDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.db.VodCategoryDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.service.AccountService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.util.AccountType
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils
import com.uiptv.util.StringUtils.isNotBlank
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.LinkedHashSet

class HttpChannelJsonServer : HttpHandler {
    companion object {
        private val ALL_CATEGORY = CategoryType.ALL.displayName()
        private const val PARAM_CHANNEL_ID = "channelId"
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val account = AccountService.getInstance().getById(getParam(ex, "accountId"))
        if (account == null) {
            generateJsonResponse(ex, "[]")
            return
        }
        applyMode(account, getParam(ex, "mode"))
        val categoryId = getParam(ex, "categoryId")
        val movieId = getParam(ex, "movieId")
        var response = resolveResponse(account, categoryId, movieId)
        if (account.action == Account.AccountAction.series && !isNotBlank(movieId)) {
            val categoryApiId = if (ALL_CATEGORY.equals(categoryId, true)) "" else resolveCategoryApiId(account, categoryId)
            response = enrichSeriesRowsWatched(account, categoryApiId, response)
        }
        generateJsonResponse(ex, dedupeJsonResponse(response))
    }

    private fun resolveResponse(account: Account, categoryId: String?, movieId: String?): String {
        if (shouldServeSeriesEpisodes(account, categoryId, movieId)) {
            return resolveSeriesEpisodesResponse(account, categoryId, movieId)
        }
        if (ALL_CATEGORY.equals(categoryId, true)) {
            return readAllCategoryChannels(account)
        }
        val category = resolveCategoryByDbId(account, categoryId) ?: Category(categoryId, categoryId, categoryId, false, 0)
        return StringUtils.EMPTY + ChannelService.getInstance().readToJson(category, account)
    }

    private fun shouldServeSeriesEpisodes(account: Account, categoryId: String?, movieId: String?): Boolean =
        account.action == Account.AccountAction.series &&
            account.type == AccountType.STALKER_PORTAL &&
            isNotBlank(movieId) &&
            !ALL_CATEGORY.equals(categoryId, true)

    private fun resolveSeriesEpisodesResponse(account: Account, categoryId: String?, movieId: String?): String {
        val requestedMovieId = movieId ?: return "[]"
        val categoryApiId = resolveCategoryApiId(account, categoryId)
        val cachedResponse = readFreshSeriesEpisodes(account, categoryApiId, requestedMovieId)
        if (cachedResponse != null) {
            return cachedResponse
        }
        val episodes = fetchAndCacheSeriesEpisodes(account, categoryId, requestedMovieId)
        val response = StringUtils.EMPTY + com.uiptv.util.ServerUtils.objectToJson(episodes)
        return enrichSeriesEpisodesWatched(account, resolveCategoryApiId(account, categoryId), requestedMovieId, response)
    }

    private fun readFreshSeriesEpisodes(account: Account, categoryApiId: String, movieId: String?): String? {
        val requestedMovieId = movieId ?: return null
        if (!SeriesEpisodeDb.get().isFresh(account, categoryApiId, requestedMovieId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            return null
        }
        val cached = SeriesEpisodeDb.get().getEpisodes(account, categoryApiId, requestedMovieId)
        if (cached.isEmpty()) {
            return null
        }
        val cachedJson = com.uiptv.util.ServerUtils.objectToJson(cached)
        return enrichSeriesEpisodesWatched(account, categoryApiId, requestedMovieId, cachedJson)
    }

    private fun fetchAndCacheSeriesEpisodes(account: Account, categoryId: String?, movieId: String?): List<Channel> {
        val requestedMovieId = movieId ?: return emptyList()
        val categoryApiId = resolveCategoryApiId(account, categoryId)
        val episodes = ChannelService.getInstance().getSeries(categoryApiId, requestedMovieId, account, null, null)
        if (episodes.isNotEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryApiId, requestedMovieId, episodes)
        }
        return episodes
    }

    private fun readAllCategoryChannels(account: Account): String {
        val allChannels = JSONArray()
        resolveRequestedCategories(resolveCategoriesForAccount(account)).forEach { category ->
            appendChannels(allChannels, ChannelService.getInstance().readToJson(category, account))
        }
        return allChannels.toString()
    }

    private fun resolveRequestedCategories(categories: List<Category>): List<Category> {
        val nonAllCategories = categories.filter { !ALL_CATEGORY.equals(it.title, true) }
        if (nonAllCategories.isNotEmpty()) return nonAllCategories
        return categories.firstOrNull { ALL_CATEGORY.equals(it.title, true) }?.let(::listOf) ?: emptyList()
    }

    private fun appendChannels(target: JSONArray, channelsJson: String?) {
        if (channelsJson.isNullOrEmpty()) return
        val channelsArray = JSONArray(channelsJson)
        for (i in 0 until channelsArray.length()) {
            target.put(channelsArray.getJSONObject(i))
        }
    }

    private fun applyMode(account: Account?, mode: String?) {
        if (account == null || !isNotBlank(mode)) return
        try {
            account.action = Account.AccountAction.valueOf(mode!!.lowercase())
        } catch (_: Exception) {
            account.action = Account.AccountAction.itv
        }
    }

    private fun dedupeJsonResponse(response: String): String =
        try {
            val array = JSONArray(response)
            val deduped = JSONArray()
            val seen = LinkedHashSet<String>()
            for (i in 0 until array.length()) {
                val item = array.optJSONObject(i) ?: continue
                val key = item.optString(PARAM_CHANNEL_ID, "").trim() + "|" +
                    item.optString("cmd", "").trim() + "|" +
                    item.optString("name", "").trim().lowercase()
                if (seen.add(key)) {
                    deduped.put(item)
                }
            }
            deduped.toString()
        } catch (_: Exception) {
            response
        }

    private fun resolveCategoryByDbId(account: Account, categoryId: String?): Category? =
        resolveCategoryByDbIdValue(account, categoryId ?: "")

    private fun resolveCategoryByDbIdValue(account: Account, categoryId: String): Category? =
        when (account.action) {
            Account.AccountAction.vod -> VodCategoryDb.get().getById(categoryId)
            Account.AccountAction.series -> SeriesCategoryDb.get().getById(categoryId)
            else -> CategoryDb.get().getCategoryByDbId(categoryId, account)
        }

    private fun resolveCategoryApiId(account: Account, categoryId: String?): String =
        resolveCategoryByDbId(account, categoryId)?.categoryId ?: (categoryId ?: "")

    private fun resolveCategoriesForAccount(account: Account): List<Category> =
        when (account.action) {
            Account.AccountAction.vod -> VodCategoryDb.get().getCategories(account)
            Account.AccountAction.series -> SeriesCategoryDb.get().getCategories(account)
            else -> CategoryDb.get().getCategories(account)
        }

    private fun enrichSeriesRowsWatched(account: Account, fallbackCategoryId: String, response: String): String =
        try {
            val rows = JSONArray(response)
            if (rows.isEmpty) return response
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val seriesId = item.optString(PARAM_CHANNEL_ID, "")
                var rowCategoryId = item.optString("categoryId", "")
                if (StringUtils.isBlank(rowCategoryId)) {
                    rowCategoryId = fallbackCategoryId
                }
                rowCategoryId = normalizeSeriesCategoryId(rowCategoryId)
                item.put("watched", SeriesWatchStateService.getInstance().getSeriesLastWatched(account.dbId, rowCategoryId, seriesId) != null)
            }
            rows.toString()
        } catch (_: Exception) {
            response
        }

    private fun enrichSeriesEpisodesWatched(account: Account, categoryId: String, seriesId: String, response: String): String =
        try {
            val rows = JSONArray(response)
            if (rows.isEmpty) return response
            val scopedCategoryId = normalizeSeriesCategoryId(categoryId)
            val state: SeriesWatchState? = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.dbId, scopedCategoryId, seriesId)
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                item.put(
                    "watched",
                    SeriesWatchStateService.getInstance().isMatchingEpisode(
                        state,
                        item.optString(PARAM_CHANNEL_ID, ""),
                        item.optString("season", ""),
                        item.optString("episodeNum", ""),
                        item.optString("name", "")
                    )
                )
            }
            rows.toString()
        } catch (_: Exception) {
            response
        }

    private fun normalizeSeriesCategoryId(categoryId: String): String {
        if (StringUtils.isBlank(categoryId)) {
            return ""
        }
        val category = SeriesCategoryDb.get().getById(categoryId)
        return if (category != null && isNotBlank(category.categoryId)) category.categoryId ?: categoryId else categoryId
    }
}
