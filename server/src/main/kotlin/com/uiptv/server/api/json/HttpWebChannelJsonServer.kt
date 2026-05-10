package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.CategoryDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.VodCategoryDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.service.AccountService
import com.uiptv.service.ChannelService
import com.uiptv.service.HandshakeService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.shared.Pagination
import com.uiptv.util.AccountType
import com.uiptv.util.AppLog
import com.uiptv.util.FetchAPI
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils
import com.uiptv.util.StringUtils.isNotBlank
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.LinkedHashMap

class HttpWebChannelJsonServer(
    private val seriesCategoryDbProvider: () -> SeriesCategoryDb = { SeriesCategoryDb.get() },
    private val categoryDbProvider: () -> CategoryDb = { CategoryDb.get() },
    private val vodCategoryDbProvider: () -> VodCategoryDb = { VodCategoryDb.get() },
    private val channelServiceProvider: () -> ChannelService = { ChannelService.getInstance() },
    private val seriesWatchStateServiceProvider: () -> SeriesWatchStateService = { SeriesWatchStateService.getInstance() }
) : HttpHandler {
    companion object {
        private const val PARAM_API_OFFSET = "apiOffset"
        private const val DEFAULT_PAGE_SIZE = 120
        private const val MAX_PAGE_SIZE = 240
        private const val DEFAULT_PREFETCH = 3
        private const val MAX_PREFETCH = 5

        @JvmStatic
        private fun estimateHasMore(pagination: Pagination?, apiPage: Int, apiOffset: Int, currentSize: Int, pageSize: Int): Boolean {
            if (pagination != null && pagination.maxPageItems > 0 && pagination.paginationLimit > 0) {
                val servedPages = maxOf(1, apiPage - apiOffset + 1)
                val servedItemsEstimate = servedPages * pagination.paginationLimit
                return servedItemsEstimate < pagination.maxPageItems
            }
            return currentSize >= pageSize
        }
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        try {
            val account = AccountService.getInstance().getById(getParam(ex, "accountId"))
            if (account == null) {
                generateJsonResponse(ex, """{"items":[],"nextPage":0,"hasMore":false,"$PARAM_API_OFFSET":0}""")
                return
            }
            applyMode(account, getParam(ex, "mode").orEmpty())
            if (account.isNotConnected()) {
                HandshakeService.getInstance().connect(account)
            }

            val categoryId = getParam(ex, "categoryId").orEmpty()
            val movieId = getParam(ex, "movieId").orEmpty()
            val page = parseInt(getParam(ex, "page").orEmpty(), 0, 0, Int.MAX_VALUE)
            val pageSize = parseInt(getParam(ex, "pageSize").orEmpty(), DEFAULT_PAGE_SIZE, 20, MAX_PAGE_SIZE)
            val prefetchPages = parseInt(getParam(ex, "prefetchPages").orEmpty(), DEFAULT_PREFETCH, 1, MAX_PREFETCH)
            val apiOffset = parseInt(getParam(ex, PARAM_API_OFFSET).orEmpty(), 0, 0, 1)

            if (account.type == AccountType.STALKER_PORTAL && !CategoryType.ALL.displayName().equals(categoryId, ignoreCase = true)) {
                generateJsonResponse(ex, buildStalkerPagedResponse(account, categoryId, movieId, page, pageSize, prefetchPages, apiOffset))
                return
            }

            val fullJson = resolveFullJson(account, categoryId, movieId)
            generateJsonResponse(ex, sliceJson(fullJson, page, pageSize, prefetchPages))
        } catch (e: Exception) {
            AppLog.addErrorLog(HttpWebChannelJsonServer::class.java, "HttpWebChannelJsonServer failed: $e")
            throw e
        }
    }

    private fun buildStalkerPagedResponse(
        account: Account,
        categoryId: String,
        movieId: String,
        page: Int,
        pageSize: Int,
        prefetchPages: Int,
        requestedApiOffset: Int
    ): String {
        val categoryApiId = resolveCategoryApiId(account, categoryId)
        var resolvedApiOffset = requestedApiOffset
        val merged = mutableListOf<Channel>()
        var currentPage = page
        var hasMore = false

        for (i in 0 until prefetchPages) {
            var apiPage = currentPage + resolvedApiOffset
            var result = fetchStalkerPage(account, categoryApiId, movieId, apiPage, pageSize)

            if (currentPage == 0 && i == 0 && resolvedApiOffset == 0 && result.items.isEmpty()) {
                val pageOne = fetchStalkerPage(account, categoryApiId, movieId, 1, pageSize)
                if (pageOne.items.isNotEmpty()) {
                    resolvedApiOffset = 1
                    result = pageOne
                    apiPage = 1
                }
            }

            if (result.items.isEmpty()) {
                hasMore = false
                break
            }

            merged += result.items
            hasMore = estimateHasMore(result.pagination, apiPage, resolvedApiOffset, result.items.size, pageSize)
            currentPage++
            if (!hasMore) {
                break
            }
        }

        if (account.action == Account.AccountAction.series) {
            if (isNotBlank(movieId)) {
                applySeriesEpisodesWatched(account, categoryApiId, movieId, merged)
            } else {
                applySeriesRowsWatched(account, categoryApiId, merged)
            }
        }
        val deduped = dedupeChannels(merged)
        return JSONObject()
            .put("items", JSONArray(deduped))
            .put("nextPage", currentPage)
            .put("hasMore", hasMore)
            .put(PARAM_API_OFFSET, resolvedApiOffset)
            .toString()
    }

    private fun resolveFullJson(account: Account, categoryId: String, movieId: String): String =
        when {
            isSeriesEpisodeRequest(account, categoryId, movieId) -> resolveSeriesEpisodesJson(account, categoryId, movieId)
            isAllCategoryRequest(categoryId) -> resolveAllCategoriesJson(account)
            else -> resolveSingleCategoryJson(account, categoryId)
        }

    private fun isSeriesEpisodeRequest(account: Account, categoryId: String, movieId: String): Boolean =
        account.action == Account.AccountAction.series &&
            account.type == AccountType.STALKER_PORTAL &&
            isNotBlank(movieId) &&
            !isAllCategoryRequest(categoryId)

    private fun isAllCategoryRequest(categoryId: String): Boolean =
        CategoryType.ALL.displayName().equals(categoryId, ignoreCase = true)

    private fun resolveSeriesEpisodesJson(account: Account, categoryId: String, movieId: String): String {
        val categoryApiId = resolveCategoryApiId(account, categoryId)
        val episodes = channelServiceProvider().getSeries(categoryApiId, movieId, account, null, null)
        applySeriesEpisodesWatched(account, categoryApiId, movieId, episodes)
        return com.uiptv.util.ServerUtils.objectToJson(episodes)
    }

    private fun resolveAllCategoriesJson(account: Account): String {
        val allChannels = JSONArray()
        val categories = resolveCategoriesForAccount(account)
        val categoriesToRead = resolveCategoriesToRead(categories)
        for (category in categoriesToRead) {
            appendChannelsJson(allChannels, channelServiceProvider().readToJson(category, account))
        }
        var result = allChannels.toString()
        if (account.action == Account.AccountAction.series) {
            result = enrichSeriesRowsWatchedJson(account, "", result)
        }
        return result
    }

    private fun resolveCategoriesToRead(categories: List<Category>): List<Category> {
        val nonAllCategories = categories.filterNot { CategoryType.ALL.displayName().equals(it.title, ignoreCase = true) }
        if (nonAllCategories.isNotEmpty()) {
            return nonAllCategories
        }
        val allCategory = categories.firstOrNull { CategoryType.ALL.displayName().equals(it.title, ignoreCase = true) }
        return if (allCategory == null) emptyList() else listOf(allCategory)
    }

    private fun appendChannelsJson(target: JSONArray, channelsJson: String?) {
        if (channelsJson.isNullOrEmpty()) {
            return
        }
        val channelsArray = JSONArray(channelsJson)
        for (i in 0 until channelsArray.length()) {
            target.put(channelsArray.getJSONObject(i))
        }
    }

    private fun resolveSingleCategoryJson(account: Account, categoryId: String): String {
        val category = resolveCategoryByDbId(account, categoryId) ?: Category(categoryId, categoryId, categoryId, false, 0)
        var result = channelServiceProvider().readToJson(category, account)
        if (account.action == Account.AccountAction.series) {
            result = enrichSeriesRowsWatchedJson(account, resolveCategoryApiId(account, categoryId), result)
        }
        return result
    }

    private fun sliceJson(json: String, page: Int, pageSize: Int, prefetchPages: Int): String {
        val all = JSONArray(json)
        val start = page * pageSize
        val end = minOf(all.length(), start + (pageSize * prefetchPages))
        val items = JSONArray()
        for (i in start until end) {
            items.put(all.get(i))
        }
        return JSONObject()
            .put("items", items)
            .put("nextPage", page + maxOf(prefetchPages, 1))
            .put("hasMore", end < all.length())
            .put(PARAM_API_OFFSET, 0)
            .toString()
    }

    private fun fetchStalkerPage(account: Account, categoryApiId: String, movieId: String, pageNumber: Int, pageSize: Int): StalkerPageResult {
        val params = ChannelService.getChannelOrSeriesParams(categoryApiId, pageNumber, account.action, movieId, "0").toMutableMap()
        params["per_page"] = pageSize.toString()
        val json = FetchAPI.fetch(params, account)
        val pagination = channelServiceProvider().parsePagination(json, null)
        val parsed = if (account.action == Account.AccountAction.itv) {
            channelServiceProvider().parseItvChannels(json, true)
        } else {
            channelServiceProvider().parseVodChannels(account, json, true)
        }
        return StalkerPageResult(parsed, pagination)
    }

    private fun dedupeChannels(channels: List<Channel>): List<Channel> {
        val unique = LinkedHashMap<String, Channel>()
        for (channel in channels) {
            val key = listOf(
                channel.channelId?.trim().orEmpty(),
                channel.cmd?.trim().orEmpty(),
                channel.name?.trim()?.lowercase().orEmpty()
            ).joinToString("|")
            unique.putIfAbsent(key, channel)
        }
        return ArrayList(unique.values)
    }

    private fun resolveCategoryApiId(account: Account, categoryId: String): String {
        val category = resolveCategoryByDbId(account, categoryId)
        return category?.categoryId ?: categoryId
    }

    private fun resolveCategoryByDbId(account: Account, categoryId: String): Category? =
        when (account.action) {
            Account.AccountAction.vod -> vodCategoryDb().getById(categoryId)
            Account.AccountAction.series -> seriesCategoryDb().getById(categoryId)
            else -> categoryDb().getCategoryByDbId(categoryId, account)
        }

    private fun resolveCategoriesForAccount(account: Account): List<Category> =
        when (account.action) {
            Account.AccountAction.vod -> vodCategoryDb().getCategories(account)
            Account.AccountAction.series -> seriesCategoryDb().getCategories(account)
            else -> categoryDb().getCategories(account)
        }

    private fun enrichSeriesRowsWatchedJson(account: Account, fallbackCategoryId: String, response: String): String =
        try {
            val rows = JSONArray(response)
            if (rows.isEmpty) {
                return response
            }
            for (i in 0 until rows.length()) {
                val item = rows.optJSONObject(i) ?: continue
                val seriesId = item.optString("channelId", "")
                var rowCategoryId = item.optString("categoryId", "")
                if (StringUtils.isBlank(rowCategoryId)) {
                    rowCategoryId = fallbackCategoryId
                }
                rowCategoryId = normalizeSeriesCategoryId(rowCategoryId)
                item.put("watched", seriesWatchStateService().getSeriesLastWatched(account.dbId, rowCategoryId, seriesId) != null)
            }
            rows.toString()
        } catch (_: Exception) {
            response
        }

    private fun applySeriesRowsWatched(account: Account?, fallbackCategoryId: String, rows: List<Channel>?) {
        if (rows.isNullOrEmpty() || account == null) {
            return
        }
        for (row in rows) {
            var rowCategoryId = if (StringUtils.isBlank(row.categoryId)) fallbackCategoryId else row.categoryId ?: ""
            rowCategoryId = normalizeSeriesCategoryId(rowCategoryId)
            row.watched = seriesWatchStateService().getSeriesLastWatched(account.dbId, rowCategoryId, row.channelId ?: "") != null
        }
    }

    private fun applySeriesEpisodesWatched(account: Account?, categoryId: String, seriesId: String, episodes: List<Channel>?) {
        if (episodes.isNullOrEmpty() || account == null) {
            return
        }
        val scopedCategoryId = normalizeSeriesCategoryId(categoryId)
        val state: SeriesWatchState? = seriesWatchStateService().getSeriesLastWatched(account.dbId, scopedCategoryId, seriesId)
        for (episode in episodes) {
            episode.watched = seriesWatchStateService().isMatchingEpisode(
                state,
                episode.channelId,
                episode.season,
                episode.episodeNum,
                episode.name
            )
        }
    }

    private fun applyMode(account: Account?, mode: String) {
        if (account == null || !isNotBlank(mode)) {
            return
        }
        try {
            account.action = Account.AccountAction.valueOf(mode.lowercase())
        } catch (_: Exception) {
            account.action = Account.AccountAction.itv
        }
    }

    private fun parseInt(value: String, defaultValue: Int, minValue: Int, maxValue: Int): Int =
        try {
            Integer.parseInt(value).coerceIn(minValue, maxValue)
        } catch (_: Exception) {
            defaultValue
        }

    private fun normalizeSeriesCategoryId(categoryId: String): String {
        if (StringUtils.isBlank(categoryId)) {
            return ""
        }
        val category = seriesCategoryDb().getById(categoryId)
        return if (category != null && isNotBlank(category.categoryId)) category.categoryId ?: categoryId else categoryId
    }

    private fun categoryDb(): CategoryDb = categoryDbProvider()

    private fun seriesCategoryDb(): SeriesCategoryDb = seriesCategoryDbProvider()

    private fun vodCategoryDb(): VodCategoryDb = vodCategoryDbProvider()

    private fun seriesWatchStateService(): SeriesWatchStateService = seriesWatchStateServiceProvider()

    private data class StalkerPageResult(
        val items: List<Channel>,
        val pagination: Pagination?
    )
}
