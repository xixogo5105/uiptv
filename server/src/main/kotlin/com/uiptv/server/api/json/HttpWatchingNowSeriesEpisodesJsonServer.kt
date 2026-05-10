package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.service.AccountService
import com.uiptv.service.SeriesEpisodeService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.service.SeriesWatchingNowSnapshotService
import com.uiptv.service.WatchingNowSeriesResolver
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeList
import com.uiptv.util.ServerUtils
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils
import java.io.IOException

class HttpWatchingNowSeriesEpisodesJsonServer : HttpHandler {
    private val resolver = WatchingNowSeriesResolver()

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val emptyJson = "[]"
        val account = AccountService.getInstance().getById(getParam(ex, "accountId"))
        if (account == null) {
            generateJsonResponse(ex, emptyJson)
            return
        }
        val seriesId = getParam(ex, "seriesId")
        val categoryId = getParam(ex, "categoryId")
        if (StringUtils.isBlank(seriesId)) {
            generateJsonResponse(ex, emptyJson)
            return
        }

        var cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId ?: "", seriesId!!)
        if (cachedEpisodes.isEmpty()) {
            cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
        }

        val episodesAsChannels = if (cachedEpisodes.isNotEmpty()) {
            cachedEpisodes
        } else {
            toChannels(SeriesEpisodeService.getInstance().getEpisodesForWatchingNow(account, categoryId ?: "", seriesId) { false })
        }

        applyWatchedFlag(episodesAsChannels, account, categoryId ?: "", seriesId)
        val metadata = resolveMetadata(account, categoryId ?: "", seriesId)
        SeriesWatchingNowSnapshotService.getInstance().saveChannels(
            account,
            categoryId ?: "",
            seriesId,
            metadata.categoryDbId,
            metadata.seriesTitle,
            metadata.seriesPoster,
            episodesAsChannels
        )
        generateJsonResponse(ex, ServerUtils.objectToJson(episodesAsChannels))
    }

    private fun resolveMetadata(account: Account, categoryId: String, seriesId: String): SeriesMetadata {
        for (row in resolver.resolveForAccount(account)) {
            val matchingSeries = safe(seriesId) == safe(row.state.seriesId)
            val matchingCategory = StringUtils.isBlank(categoryId) || safe(categoryId) == safe(row.state.categoryId)
            if (matchingSeries && matchingCategory) {
                return SeriesMetadata(row.categoryDbId, row.seriesTitle, row.seriesPoster)
            }
        }
        return SeriesMetadata("", "", "")
    }

    private fun toChannels(episodes: EpisodeList?): List<Channel> {
        val channels = mutableListOf<Channel>()
        for (episode in episodes?.episodes ?: emptyList<Episode>()) {
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
        }
        return channel
    }

    private fun applyWatchedFlag(episodes: List<Channel>?, account: Account?, categoryId: String, seriesId: String) {
        if (episodes.isNullOrEmpty() || account == null) {
            return
        }
        val state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.dbId, categoryId, seriesId)
        for (channel in episodes) {
            channel.watched = SeriesWatchStateService.getInstance().isMatchingEpisode(
                state,
                channel.channelId,
                channel.season,
                channel.episodeNum,
                channel.name
            )
        }
    }

    private fun safe(value: String?): String = value?.trim() ?: ""

    private data class SeriesMetadata(
        val categoryDbId: String,
        val seriesTitle: String,
        val seriesPoster: String
    )
}
