package com.uiptv.server.api.routes

import com.uiptv.db.CategoryDb
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.db.VodCategoryDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.CategoryType
import com.uiptv.model.Channel
import com.uiptv.server.api.dto.ChannelRouteDto
import com.uiptv.service.AccountService
import com.uiptv.service.ChannelService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.util.AccountType
import com.uiptv.util.StringUtils
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
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
            call.respond(emptyList<ChannelRouteDto>())
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
        call.respond(dedupeChannels(response).map(::toChannelRouteDto))
    }
}

private fun resolveChannelsResponse(
    account: Account,
    categoryId: String?,
    movieId: String?,
    channelService: ChannelService,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
): List<Channel> {
    if (shouldServeSeriesEpisodes(account, categoryId, movieId)) {
        return resolveSeriesEpisodesResponse(account, categoryId, movieId, channelService, configurationService, seriesWatchStateService)
    }
    if (CategoryType.isAll(categoryId)) {
        return readAllCategoryChannels(account, channelService)
    }
    val category = resolveCategoryByDbId(account, categoryId) ?: Category(categoryId, categoryId, categoryId, false, 0)
    return channelService.read(category, account)
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
): List<Channel> {
    val requestedMovieId = movieId ?: return emptyList()
    val categoryApiId = resolveCategoryApiId(account, categoryId)
    val cachedResponse = readFreshSeriesEpisodes(account, categoryApiId, requestedMovieId, configurationService, seriesWatchStateService)
    if (cachedResponse != null) {
        return cachedResponse
    }
    val episodes = fetchAndCacheSeriesEpisodes(account, categoryId, requestedMovieId, channelService)
    return enrichSeriesEpisodesWatched(account, categoryApiId, requestedMovieId, episodes, seriesWatchStateService)
}

private fun readFreshSeriesEpisodes(
    account: Account,
    categoryApiId: String,
    movieId: String,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
): List<Channel>? {
    if (!SeriesEpisodeDb.get().isFresh(account, categoryApiId, movieId, configurationService.getCacheExpiryMs())) {
        return null
    }
    val cached = SeriesEpisodeDb.get().getEpisodes(account, categoryApiId, movieId)
    if (cached.isEmpty()) {
        return null
    }
    return enrichSeriesEpisodesWatched(account, categoryApiId, movieId, cached, seriesWatchStateService)
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

private fun readAllCategoryChannels(account: Account, channelService: ChannelService): List<Channel> {
    val allChannels = ArrayList<Channel>()
    resolveRequestedCategories(resolveCategoriesForAccount(account)).forEach { category ->
        allChannels += channelService.read(category, account)
    }
    return allChannels
}

private fun resolveRequestedCategories(categories: List<Category>): List<Category> {
    val nonAllCategories = categories.filter { !CategoryType.isAll(it.title) }
    if (nonAllCategories.isNotEmpty()) {
        return nonAllCategories
    }
    return categories.firstOrNull { CategoryType.isAll(it.title) }?.let(::listOf) ?: emptyList()
}

private fun dedupeChannels(channels: List<Channel>): List<Channel> {
    val seen = LinkedHashSet<String>()
    val deduped = ArrayList<Channel>()
    channels.forEach { channel ->
        val key = channel.channelId.orEmpty().trim() + "|" +
            channel.cmd.orEmpty().trim() + "|" +
            channel.name.orEmpty().trim().lowercase()
        if (seen.add(key)) {
            deduped += channel
        }
    }
    return deduped
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
    channels: List<Channel>,
    seriesWatchStateService: SeriesWatchStateService
): List<Channel> {
    channels.forEach { channel ->
        var rowCategoryId = channel.categoryId.orEmpty()
        if (StringUtils.isBlank(rowCategoryId)) {
            rowCategoryId = fallbackCategoryId
        }
        rowCategoryId = normalizeSeriesCategoryId(rowCategoryId)
        channel.watched = seriesWatchStateService.getSeriesLastWatched(account.dbId, rowCategoryId, channel.channelId.orEmpty()) != null
    }
    return channels
}

private fun enrichSeriesEpisodesWatched(
    account: Account,
    categoryId: String,
    seriesId: String,
    channels: List<Channel>,
    seriesWatchStateService: SeriesWatchStateService
): List<Channel> {
    if (channels.isEmpty()) {
        return channels
    }
    val scopedCategoryId = normalizeSeriesCategoryId(categoryId)
    val state = seriesWatchStateService.getSeriesLastWatched(account.dbId, scopedCategoryId, seriesId)
    channels.forEach { channel ->
        channel.watched = seriesWatchStateService.isMatchingEpisode(
            state,
            channel.channelId.orEmpty(),
            channel.season.orEmpty(),
            channel.episodeNum.orEmpty(),
            channel.name.orEmpty()
        )
    }
    return channels
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

private fun toChannelRouteDto(channel: Channel): ChannelRouteDto =
    ChannelRouteDto(
        dbId = channel.dbId,
        channelId = channel.channelId,
        categoryId = channel.categoryId,
        name = channel.name,
        number = channel.number,
        cmd = channel.cmd,
        cmd_1 = channel.cmd_1,
        cmd_2 = channel.cmd_2,
        cmd_3 = channel.cmd_3,
        logo = channel.logo,
        description = channel.description,
        season = channel.season,
        episodeNum = channel.episodeNum,
        releaseDate = channel.releaseDate,
        rating = channel.rating,
        duration = channel.duration,
        extraJson = channel.extraJson,
        censored = channel.censored,
        status = channel.status,
        hd = channel.hd,
        watched = channel.watched,
        drmType = channel.drmType,
        drmLicenseUrl = channel.drmLicenseUrl,
        clearKeysJson = channel.clearKeysJson,
        inputstreamaddon = channel.inputstreamaddon,
        manifestType = channel.manifestType
    )
