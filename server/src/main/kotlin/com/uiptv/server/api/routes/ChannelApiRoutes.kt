package com.uiptv.server.api.routes

import com.uiptv.db.CategoryDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.db.VodCategoryDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.service.AccountService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.util.AccountType
import com.uiptv.util.ServerUtils
import com.uiptv.util.StringUtils
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.json.JSONArray
import java.util.LinkedHashSet

fun Route.registerChannelApiRoutes(
    accountService: AccountService,
    channelService: ChannelService,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
) {
    get("/channels") {
        val account = accountService.getById(call.request.queryParameters["accountId"])
        if (account == null) {
            call.respondText("[]", ContentType.Application.Json)
            return@get
        }
        applyChannelMode(account, call.request.queryParameters["mode"])
        val categoryId = call.request.queryParameters["categoryId"]
        val movieId = call.request.queryParameters["movieId"]
        var response = resolveChannelsResponse(account, categoryId, movieId, channelService, configurationService, seriesWatchStateService)
        if (account.action == Account.AccountAction.series && StringUtils.isBlank(movieId)) {
            val categoryApiId = if (CategoryType.isAll(categoryId)) "" else resolveCategoryApiId(account, categoryId)
            response = enrichSeriesRowsWatched(account, categoryApiId, response, seriesWatchStateService)
        }
        call.respondText(dedupeJsonResponse(response), ContentType.Application.Json)
    }
}

private fun resolveChannelsResponse(
    account: Account,
    categoryId: String?,
    movieId: String?,
    channelService: ChannelService,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
): String {
    if (shouldServeSeriesEpisodes(account, categoryId, movieId)) {
        return resolveSeriesEpisodesResponse(account, categoryId, movieId, channelService, configurationService, seriesWatchStateService)
    }
    if (CategoryType.isAll(categoryId)) {
        return readAllCategoryChannels(account, channelService)
    }
    val category = resolveCategoryByDbId(account, categoryId) ?: Category(categoryId, categoryId, categoryId, false, 0)
    return channelService.readToJson(category, account)
}

private fun applyChannelMode(account: Account, mode: String?) {
    if (mode.isNullOrBlank()) {
        return
    }
    account.action = try {
        Account.AccountAction.valueOf(mode.lowercase())
    } catch (_: Exception) {
        Account.AccountAction.itv
    }
}

private fun shouldServeSeriesEpisodes(account: Account, categoryId: String?, movieId: String?): Boolean =
    account.action == Account.AccountAction.series &&
        account.type == AccountType.STALKER_PORTAL &&
        StringUtils.isNotBlank(movieId) &&
        !CategoryType.isAll(categoryId)

private fun resolveSeriesEpisodesResponse(
    account: Account,
    categoryId: String?,
    movieId: String?,
    channelService: ChannelService,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
): String {
    val requestedMovieId = movieId ?: return "[]"
    val categoryApiId = resolveCategoryApiId(account, categoryId)
    val cachedResponse = readFreshSeriesEpisodes(account, categoryApiId, requestedMovieId, configurationService, seriesWatchStateService)
    if (cachedResponse != null) {
        return cachedResponse
    }
    val episodes = fetchAndCacheSeriesEpisodes(account, categoryId, requestedMovieId, channelService)
    val response = ServerUtils.objectToJson(episodes).toString()
    return enrichSeriesEpisodesWatched(account, categoryApiId, requestedMovieId, response, seriesWatchStateService)
}

private fun readFreshSeriesEpisodes(
    account: Account,
    categoryApiId: String,
    movieId: String,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
): String? {
    if (!SeriesEpisodeDb.get().isFresh(account, categoryApiId, movieId, configurationService.getCacheExpiryMs())) {
        return null
    }
    val cached = SeriesEpisodeDb.get().getEpisodes(account, categoryApiId, movieId)
    if (cached.isEmpty()) {
        return null
    }
    val cachedJson = ServerUtils.objectToJson(cached).toString()
    return enrichSeriesEpisodesWatched(account, categoryApiId, movieId, cachedJson, seriesWatchStateService)
}

private fun fetchAndCacheSeriesEpisodes(
    account: Account,
    categoryId: String?,
    movieId: String,
    channelService: ChannelService
): List<Channel> {
    val categoryApiId = resolveCategoryApiId(account, categoryId)
    val episodes = channelService.getSeries(categoryApiId, movieId, account, null, null)
    if (episodes.isNotEmpty()) {
        SeriesEpisodeDb.get().saveAll(account, categoryApiId, movieId, episodes)
    }
    return episodes
}

private fun readAllCategoryChannels(account: Account, channelService: ChannelService): String {
    val allChannels = JSONArray()
    resolveRequestedCategories(resolveCategoriesForAccount(account)).forEach { category ->
        appendChannels(allChannels, channelService.readToJson(category, account))
    }
    return allChannels.toString()
}

private fun resolveRequestedCategories(categories: List<Category>): List<Category> {
    val nonAllCategories = categories.filter { !CategoryType.isAll(it.title) }
    if (nonAllCategories.isNotEmpty()) {
        return nonAllCategories
    }
    return categories.firstOrNull { CategoryType.isAll(it.title) }?.let(::listOf) ?: emptyList()
}

private fun appendChannels(target: JSONArray, channelsJson: String?) {
    if (channelsJson.isNullOrEmpty()) {
        return
    }
    val channelsArray = JSONArray(channelsJson)
    for (index in 0 until channelsArray.length()) {
        target.put(channelsArray.getJSONObject(index))
    }
}

private fun dedupeJsonResponse(response: String): String =
    try {
        val array = JSONArray(response)
        val deduped = JSONArray()
        val seen = LinkedHashSet<String>()
        for (index in 0 until array.length()) {
            val item = array.optJSONObject(index) ?: continue
            val key = item.optString("channelId", "").trim() + "|" +
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
    when (account.action) {
        Account.AccountAction.vod -> VodCategoryDb.get().getById(categoryId.orEmpty())
        Account.AccountAction.series -> SeriesCategoryDb.get().getById(categoryId.orEmpty())
        else -> CategoryDb.get().getCategoryByDbId(categoryId.orEmpty(), account)
    }

private fun resolveCategoryApiId(account: Account, categoryId: String?): String =
    resolveCategoryByDbId(account, categoryId)?.categoryId ?: categoryId.orEmpty()

private fun resolveCategoriesForAccount(account: Account): List<Category> =
    when (account.action) {
        Account.AccountAction.vod -> VodCategoryDb.get().getCategories(account)
        Account.AccountAction.series -> SeriesCategoryDb.get().getCategories(account)
        else -> CategoryDb.get().getCategories(account)
    }

private fun enrichSeriesRowsWatched(
    account: Account,
    fallbackCategoryId: String,
    response: String,
    seriesWatchStateService: SeriesWatchStateService
): String =
    try {
        val rows = JSONArray(response)
        if (rows.isEmpty) {
            return response
        }
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            val seriesId = item.optString("channelId", "")
            var rowCategoryId = item.optString("categoryId", "")
            if (StringUtils.isBlank(rowCategoryId)) {
                rowCategoryId = fallbackCategoryId
            }
            rowCategoryId = normalizeSeriesCategoryId(rowCategoryId)
            item.put("watched", seriesWatchStateService.getSeriesLastWatched(account.dbId, rowCategoryId, seriesId) != null)
        }
        rows.toString()
    } catch (_: Exception) {
        response
    }

private fun enrichSeriesEpisodesWatched(
    account: Account,
    categoryId: String,
    seriesId: String,
    response: String,
    seriesWatchStateService: SeriesWatchStateService
): String =
    try {
        val rows = JSONArray(response)
        if (rows.isEmpty) {
            return response
        }
        val scopedCategoryId = normalizeSeriesCategoryId(categoryId)
        val state = seriesWatchStateService.getSeriesLastWatched(account.dbId, scopedCategoryId, seriesId)
        for (index in 0 until rows.length()) {
            val item = rows.optJSONObject(index) ?: continue
            item.put(
                "watched",
                seriesWatchStateService.isMatchingEpisode(
                    state,
                    item.optString("channelId", ""),
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
    return if (category != null && StringUtils.isNotBlank(category.categoryId)) {
        category.categoryId ?: categoryId
    } else {
        categoryId
    }
}
