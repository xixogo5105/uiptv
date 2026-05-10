package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.service.AccountService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeList
import com.uiptv.util.AccountType
import com.uiptv.util.ServerUtils
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils
import com.uiptv.util.XtremeApiParser
import java.io.IOException

class HttpSeriesEpisodesJsonServer : HttpHandler {
    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val emptyJson = "[]"
        val account = AccountService.getInstance().getById(getParam(ex, "accountId"))
        if (account == null) {
            generateJsonResponse(ex, emptyJson)
            return
        }

        val seriesId = getParam(ex, "seriesId")
        val rawCategoryId = getParam(ex, "categoryId")
        val categoryId = resolveSeriesCategoryId(rawCategoryId)
        if (StringUtils.isBlank(seriesId)) {
            generateJsonResponse(ex, emptyJson)
            return
        }

        var cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId!!)
        if (cachedEpisodes.isEmpty() && account.type == AccountType.XTREME_API) {
            cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
        }
        if (isCachedFresh(account, categoryId, seriesId, cachedEpisodes)) {
            applyWatchedFlag(cachedEpisodes, account, categoryId, seriesId)
            generateJsonResponse(ex, ServerUtils.objectToJson(cachedEpisodes))
            return
        }

        var episodesAsChannels = loadEpisodes(account, seriesId)
        if (episodesAsChannels.isNotEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, episodesAsChannels)
        } else if (account.type == AccountType.XTREME_API) {
            episodesAsChannels = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
        }
        applyWatchedFlag(episodesAsChannels, account, categoryId, seriesId)
        generateJsonResponse(ex, ServerUtils.objectToJson(episodesAsChannels))
    }

    private fun isCachedFresh(account: Account, categoryId: String, seriesId: String, cachedEpisodes: List<Channel>): Boolean =
        cachedEpisodes.isNotEmpty() && (
            SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs()) ||
                (account.type == AccountType.XTREME_API &&
                    SeriesEpisodeDb.get().isFreshInAnyCategory(account, seriesId, ConfigurationService.getInstance().getCacheExpiryMs()))
            )

    private fun loadEpisodes(account: Account, seriesId: String): List<Channel> =
        if (account.type == AccountType.XTREME_API && StringUtils.isNotBlank(seriesId)) {
            toChannels(XtremeApiParser.parseEpisodes(seriesId, account))
        } else {
            ArrayList()
        }

    private fun toChannels(episodes: EpisodeList?): List<Channel> {
        val channels = ArrayList<Channel>()
        if (episodes?.episodes == null) {
            return channels
        }
        episodes.episodes.forEach { episode ->
            val channel = toChannel(episode)
            if (channel != null) {
                channels += channel
            }
        }
        return channels
    }

    private fun toChannel(episode: Episode?): Channel? {
        if (episode == null) {
            return null
        }
        val channel = Channel()
        channel.channelId = episode.id
        channel.name = episode.title
        channel.cmd = episode.cmd
        channel.extraJson = episode.toJson()
        channel.season = episode.season
        channel.episodeNum = episode.episodeNum
        val info = episode.info
        if (info != null) {
            channel.logo = info.movieImage
            channel.description = info.plot
            channel.releaseDate = info.releaseDate
            channel.rating = info.rating
            channel.duration = info.duration
            if (StringUtils.isBlank(channel.season)) {
                channel.season = info.season
            }
        }
        return channel
    }

    private fun applyWatchedFlag(episodes: List<Channel>?, account: Account?, categoryId: String, seriesId: String) {
        if (episodes.isNullOrEmpty() || account == null) {
            return
        }
        val state: SeriesWatchState? = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.dbId, categoryId, seriesId)
        episodes.filterNotNull().forEach { channel ->
            channel.watched = SeriesWatchStateService.getInstance().isMatchingEpisode(
                state,
                channel.channelId,
                channel.season,
                channel.episodeNum,
                channel.name
            )
        }
    }

    private fun resolveSeriesCategoryId(rawCategoryId: String?): String {
        if (StringUtils.isBlank(rawCategoryId)) {
            return ""
        }
        val categoryId = rawCategoryId ?: return ""
        val category: Category? = SeriesCategoryDb.get().getById(categoryId)
        return if (category != null && StringUtils.isNotBlank(category.categoryId)) category.categoryId ?: categoryId else categoryId
    }
}
