package com.uiptv.service

import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeInfo
import com.uiptv.shared.EpisodeList
import com.uiptv.util.AccountType.STALKER_PORTAL
import com.uiptv.util.AccountType.XTREME_API
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.XtremeApiParser
import java.util.ArrayList
import java.util.function.Supplier
import java.util.regex.Pattern

object SeriesEpisodeService {
    private val SEASON_WORD_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b")
    private val SEASON_SHORT_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})(?=\\b|E\\d+)")
    private val SEASON_X_PATTERN = Pattern.compile("(?i)\\b(\\d{1,2})x\\d{1,3}\\b")
    private val EPISODE_WORD_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b")
    private val EPISODE_SHORT_PATTERN = Pattern.compile("(?i)\\bE(\\d{1,3})\\b")
    private val EPISODE_X_PATTERN = Pattern.compile("(?i)\\b\\d{1,2}x(\\d{1,3})\\b")

    @Volatile
    private var channelServiceResolver: (() -> ChannelService)? = null

    @JvmStatic
    fun getInstance(): SeriesEpisodeService = this

    @JvmStatic
    fun setChannelServiceResolverForTests(resolver: (() -> ChannelService)?) {
        channelServiceResolver = resolver
    }
    fun getEpisodes(account: Account?, categoryId: String?, seriesId: String?, isCancelled: Supplier<Boolean>?): EpisodeList {
        if (account == null || isBlank(seriesId)) {
            return EpisodeList()
        }
        val cached = loadFromDbCache(account, categoryId.orEmpty(), seriesId.orEmpty())
        if (hasEpisodes(cached)) {
            return cached ?: EpisodeList()
        }
        val fetched = fetchEpisodesFromPortal(account, categoryId.orEmpty(), seriesId.orEmpty(), isCancelled)
        if (hasEpisodes(fetched)) {
            return fetched
        }
        return EpisodeList()
    }
    fun getEpisodesForWatchingNow(account: Account?, categoryId: String?, seriesId: String?, isCancelled: Supplier<Boolean>?): EpisodeList {
        if (account == null || isBlank(seriesId)) {
            return EpisodeList()
        }
        val cached = loadFromDbAnyAge(account, categoryId.orEmpty(), seriesId.orEmpty())
        if (hasEpisodes(cached)) {
            return cached ?: EpisodeList()
        }
        val snapshot = SeriesWatchingNowSnapshotService.getInstance().loadEpisodeList(account.dbId.orEmpty(), categoryId.orEmpty(), seriesId.orEmpty())
        if (hasEpisodes(snapshot)) {
            return snapshot
        }
        val fetched = fetchEpisodesFromPortal(account, categoryId.orEmpty(), seriesId.orEmpty(), isCancelled)
        if (hasEpisodes(fetched)) {
            return fetched
        }
        return EpisodeList()
    }
    fun reloadEpisodesFromPortal(account: Account?, categoryId: String?, seriesId: String?, isCancelled: Supplier<Boolean>?): EpisodeList {
        if (account == null || isBlank(seriesId)) {
            return EpisodeList()
        }
        val fetched = fetchEpisodesFromPortal(account, categoryId.orEmpty(), seriesId.orEmpty(), isCancelled)
        if (hasEpisodes(fetched)) {
            return fetched
        }
        val fallback = loadFromDbAnyAge(account, categoryId.orEmpty(), seriesId.orEmpty())
        if (hasEpisodes(fallback)) {
            return fallback ?: EpisodeList()
        }
        return EpisodeList()
    }

    private fun fetchEpisodesFromPortal(account: Account, categoryId: String, seriesId: String, isCancelled: Supplier<Boolean>?): EpisodeList {
        if (account.type == XTREME_API) {
            try {
                val episodes = XtremeApiParser.parseEpisodes(seriesId, account)
                if (hasEpisodes(episodes)) {
                    saveEpisodesToDbCache(account, categoryId, seriesId, episodes)
                    return episodes
                }
            } catch (_: RuntimeException) {
            }
            return loadFromAnyCategoryCache(account, seriesId) ?: EpisodeList()
        }
        if (account.type == STALKER_PORTAL) {
            val cancellationCheck = isCancelled ?: Supplier { false }
            val seriesChannels = resolveChannelService().getSeries(categoryId, seriesId, account, null, cancellationCheck)
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, seriesChannels)
            return toEpisodeList(seriesChannels)
        }
        return EpisodeList()
    }

    private fun resolveChannelService(): ChannelService = channelServiceResolver?.invoke() ?: ChannelService.getInstance()

    private fun loadFromDbCache(account: Account, categoryId: String, seriesId: String): EpisodeList? {
        val cachedChannels = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId)
        if (cachedChannels.isNullOrEmpty()) {
            return loadFromAnyCategoryCache(account, seriesId)
        }
        if (!SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            return loadFromAnyCategoryCache(account, seriesId)
        }
        return toEpisodeList(cachedChannels)
    }

    private fun loadFromAnyCategoryCache(account: Account?, seriesId: String): EpisodeList? {
        if (account == null || isBlank(seriesId) || account.type != XTREME_API) {
            return null
        }
        val cachedChannels = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
        if (cachedChannels.isNullOrEmpty()) {
            return null
        }
        if (!SeriesEpisodeDb.get().isFreshInAnyCategory(account, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            return null
        }
        return toEpisodeList(cachedChannels)
    }

    private fun loadFromDbAnyAge(account: Account, categoryId: String, seriesId: String): EpisodeList? {
        val cachedChannels = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId)
        if (!cachedChannels.isNullOrEmpty()) {
            return toEpisodeList(cachedChannels)
        }
        if (account.type == XTREME_API) {
            val fallback = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
            if (!fallback.isNullOrEmpty()) {
                return toEpisodeList(fallback)
            }
        }
        return null
    }

    private fun saveEpisodesToDbCache(account: Account?, categoryId: String, seriesId: String, episodes: EpisodeList?) {
        if (account == null || isBlank(seriesId) || !hasEpisodes(episodes)) {
            return
        }
        val episodeList = episodes ?: return
        val channels = ArrayList<Channel>()
        episodeList.episodes.forEach { episode ->
            val channel = Channel()
            channel.channelId = episode.id
            channel.name = episode.title
            channel.cmd = episode.cmd
            channel.season = episode.season
            channel.episodeNum = episode.episodeNum
            channel.extraJson = episode.toJson()
            episode.info?.let { info ->
                channel.logo = info.movieImage
                channel.description = info.plot
                channel.releaseDate = info.releaseDate
                channel.rating = info.rating
                channel.duration = info.duration
            }
            channels.add(channel)
        }
        if (channels.isNotEmpty()) {
            SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, channels)
        }
    }

    private fun toEpisodeList(channels: List<Channel>?): EpisodeList {
        val list = EpisodeList()
        if (channels.isNullOrEmpty()) {
            return list
        }
        channels.forEach { channel ->
            list.episodes.add(toEpisode(channel))
        }
        return list
    }

    private fun toEpisode(channel: Channel): Episode {
        val episode = restoreEpisode(channel)
        populateEpisodeFallbacks(episode, channel)
        mergeEpisodeInfo(episode, buildEpisodeInfo(channel))
        return episode
    }

    private fun hasEpisodes(episodes: EpisodeList?): Boolean = episodes?.episodes?.isNotEmpty() == true

    private fun restoreEpisode(channel: Channel): Episode {
        val parsed = Episode.fromJson(channel.extraJson)
        if (isParsedEpisodeCompatible(parsed, channel)) {
            return parsed!!
        }
        val episode = Episode()
        episode.id = channel.channelId
        episode.title = channel.name
        episode.cmd = channel.cmd
        episode.season = resolveEpisodeSeason(channel)
        episode.episodeNum = resolveEpisodeNumber(channel)
        return episode
    }

    private fun populateEpisodeFallbacks(episode: Episode, channel: Channel) {
        if (isBlank(episode.id)) episode.id = channel.channelId
        if (isBlank(episode.title)) episode.title = channel.name
        if (isBlank(episode.cmd)) episode.cmd = channel.cmd
        if (isBlank(episode.season)) episode.season = resolveEpisodeSeason(channel)
        if (isBlank(episode.episodeNum)) episode.episodeNum = resolveEpisodeNumber(channel)
    }

    private fun resolveEpisodeSeason(channel: Channel): String = if (isBlank(channel.season)) extractSeason(channel.name) else channel.season.orEmpty()

    private fun resolveEpisodeNumber(channel: Channel): String = if (isBlank(channel.episodeNum)) extractEpisode(channel.name) else channel.episodeNum.orEmpty()

    private fun buildEpisodeInfo(channel: Channel): EpisodeInfo {
        val info = EpisodeInfo()
        info.movieImage = channel.logo
        info.plot = channel.description
        info.releaseDate = channel.releaseDate
        info.rating = channel.rating
        info.duration = channel.duration
        return info
    }

    private fun mergeEpisodeInfo(episode: Episode, info: EpisodeInfo) {
        val existingInfo = episode.info
        if (existingInfo == null) {
            episode.info = info
            return
        }
        if (isBlank(existingInfo.movieImage)) existingInfo.movieImage = info.movieImage
        if (isBlank(existingInfo.plot)) existingInfo.plot = info.plot
        if (isBlank(existingInfo.releaseDate)) existingInfo.releaseDate = info.releaseDate
        if (isBlank(existingInfo.rating)) existingInfo.rating = info.rating
        if (isBlank(existingInfo.duration)) existingInfo.duration = info.duration
    }

    private fun isParsedEpisodeCompatible(parsed: Episode?, channel: Channel?): Boolean {
        if (parsed == null || channel == null) {
            return false
        }
        val parsedId = safe(parsed.id)
        val cachedId = safe(channel.channelId)
        if (!isBlank(parsedId) && !isBlank(cachedId)) {
            return parsedId == cachedId
        }
        val parsedCmd = safe(parsed.cmd)
        val cachedCmd = safe(channel.cmd)
        return !isBlank(parsedCmd) && !isBlank(cachedCmd) && parsedCmd == cachedCmd
    }

    private fun safe(value: String?): String = value?.trim().orEmpty()

    private fun extractSeason(title: String?): String {
        if (isBlank(title)) return "1"
        val matched = firstMatch(title.orEmpty(), SEASON_WORD_PATTERN, SEASON_SHORT_PATTERN, SEASON_X_PATTERN)
        return if (!isBlank(matched)) matched else "1"
    }

    private fun extractEpisode(title: String?): String {
        if (isBlank(title)) return ""
        val matched = firstMatch(title.orEmpty(), EPISODE_WORD_PATTERN, EPISODE_SHORT_PATTERN, EPISODE_X_PATTERN)
        return if (!isBlank(matched)) matched else ""
    }

    private fun firstMatch(title: String, vararg patterns: Pattern): String {
        patterns.forEach { pattern ->
            val matcher = pattern.matcher(title)
            if (matcher.find()) {
                val value = matcher.group(1)
                if (!isBlank(value)) {
                    return value
                }
            }
        }
        return ""
    }
}
