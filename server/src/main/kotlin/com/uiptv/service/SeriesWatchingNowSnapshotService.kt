package com.uiptv.service

import com.uiptv.db.SeriesWatchingNowSnapshotDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchingNowSnapshot
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeInfo
import com.uiptv.shared.EpisodeList
import com.uiptv.util.StringUtils
import com.uiptv.util.json.parseJsonArray
import com.uiptv.util.json.parseJsonObject
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.add
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

object SeriesWatchingNowSnapshotService {
    fun getSnapshot(accountId: String?, categoryId: String?, seriesId: String?): SeriesWatchingNowSnapshot? {
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(seriesId)) {
            return null
        }
        val normalizedCategory = normalize(categoryId)
        val candidateIds = buildSeriesIdCandidates(seriesId)
        val normalizedAccountId = accountId.orEmpty()
        val exact = findExactSnapshot(normalizedAccountId, normalizedCategory, candidateIds)
        return exact ?: findLatestSnapshot(normalizedAccountId, candidateIds)
    }
    fun loadEpisodeList(accountId: String?, categoryId: String?, seriesId: String?): EpisodeList {
        val snapshot = getSnapshot(accountId, categoryId, seriesId)
        if (snapshot == null || StringUtils.isBlank(snapshot.episodesJson)) {
            return EpisodeList()
        }
        val payload = parseJsonArray(snapshot.episodesJson.orEmpty()) ?: return EpisodeList()
        val list = EpisodeList()
        for (index in payload.indices) {
            val channel = readChannel(payload, index)
            if (channel != null) {
                list.episodes.add(toEpisode(channel))
            }
        }
        return list
    }
    fun loadChannels(accountId: String?, categoryId: String?, seriesId: String?): List<Channel> {
        val snapshot = getSnapshot(accountId, categoryId, seriesId)
        if (snapshot == null || StringUtils.isBlank(snapshot.episodesJson)) {
            return emptyList()
        }
        val payload = parseJsonArray(snapshot.episodesJson.orEmpty()) ?: return emptyList()
        val channels = ArrayList<Channel>()
        for (index in payload.indices) {
            val channel = readChannel(payload, index)
            if (channel != null) {
                channels.add(channel)
            }
        }
        return channels
    }
    fun save(
        account: Account?,
        categoryId: String?,
        seriesId: String?,
        categoryDbId: String?,
        seriesTitle: String?,
        seriesPoster: String?,
        episodeList: EpisodeList?
    ) {
        if (account == null || StringUtils.isBlank(account.dbId) || StringUtils.isBlank(seriesId) || episodeList?.episodes.isNullOrEmpty()) {
            return
        }
        val episodesPayload = buildJsonArray {
            episodeList.episodes.forEach { episode ->
                val channel = toChannel(episode)
                val payload = channel?.toJson()?.let(::parseJsonObject)
                if (payload != null) {
                    add(payload)
                }
            }
        }
        if (episodesPayload.isEmpty()) {
            return
        }
        val snapshot = SeriesWatchingNowSnapshot()
        snapshot.accountId = account.dbId
        snapshot.categoryId = normalize(categoryId)
        snapshot.seriesId = canonicalizeSeriesId(seriesId)
        snapshot.categoryDbId = normalize(categoryDbId)
        snapshot.seriesTitle = normalize(seriesTitle)
        snapshot.seriesPoster = normalize(seriesPoster)
        snapshot.episodesJson = episodesPayload.toString()
        snapshot.updatedAt = System.currentTimeMillis()
        SeriesWatchingNowSnapshotDb.get().upsert(snapshot)
    }
    fun saveChannels(
        account: Account?,
        categoryId: String?,
        seriesId: String?,
        categoryDbId: String?,
        seriesTitle: String?,
        seriesPoster: String?,
        channels: List<Channel>?
    ) {
        if (channels.isNullOrEmpty()) {
            return
        }
        val list = EpisodeList()
        channels.filterNotNull().forEach { list.episodes.add(toEpisode(it)) }
        save(account, categoryId, seriesId, categoryDbId, seriesTitle, seriesPoster, list)
    }
    fun clear(accountId: String?, categoryId: String?, seriesId: String?) {
        if (StringUtils.isBlank(accountId) || StringUtils.isBlank(seriesId)) {
            return
        }
        val canonicalSeriesId = canonicalizeSeriesId(seriesId)
        val normalizedCategory = normalize(categoryId)
        val normalizedAccountId = accountId.orEmpty()
        SeriesWatchingNowSnapshotDb.get().clear(normalizedAccountId, normalizedCategory, canonicalSeriesId)
        for (candidateId in buildSeriesIdCandidates(seriesId)) {
            for (candidate in SeriesWatchingNowSnapshotDb.get().getBySeries(normalizedAccountId, candidateId)) {
                SeriesWatchingNowSnapshotDb.get().clear(normalizedAccountId, normalize(candidate.categoryId), normalize(candidate.seriesId))
            }
        }
    }
    fun clearAll() {
        SeriesWatchingNowSnapshotDb.get().clearAll()
    }

    private fun toChannel(episode: Episode?): Channel? {
        if (episode == null || StringUtils.isBlank(episode.id)) {
            return null
        }
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
        return channel
    }

    private fun toEpisode(channel: Channel): Episode {
        val parsed = Episode.fromJson(channel.extraJson)
        if (parsed != null && StringUtils.isNotBlank(parsed.id)) {
            hydrateParsedEpisode(parsed, channel)
            return parsed
        }
        val episode = Episode()
        episode.id = channel.channelId
        episode.title = channel.name
        episode.cmd = channel.cmd
        episode.season = channel.season
        episode.episodeNum = channel.episodeNum
        val info = EpisodeInfo()
        info.movieImage = channel.logo
        info.plot = channel.description
        info.releaseDate = channel.releaseDate
        info.rating = channel.rating
        info.duration = channel.duration
        episode.info = info
        return episode
    }

    private fun normalize(value: String?): String = value?.trim().orEmpty()

    private fun findExactSnapshot(accountId: String, normalizedCategory: String, candidateIds: List<String>): SeriesWatchingNowSnapshot? {
        for (candidateId in candidateIds) {
            val match = SeriesWatchingNowSnapshotDb.get().getBySeries(accountId, normalizedCategory, candidateId)
            if (match != null) {
                return match
            }
        }
        return null
    }

    private fun findLatestSnapshot(accountId: String, candidateIds: List<String>): SeriesWatchingNowSnapshot? {
        var latest: SeriesWatchingNowSnapshot? = null
        candidateIds.forEach { candidateId ->
            latest = newerSnapshot(latest, SeriesWatchingNowSnapshotDb.get().getBySeries(accountId, candidateId))
        }
        return latest
    }

    private fun newerSnapshot(current: SeriesWatchingNowSnapshot?, candidates: List<SeriesWatchingNowSnapshot>): SeriesWatchingNowSnapshot? {
        var latest = current
        candidates.forEach { candidate ->
            if (latest == null || candidate.updatedAt > latest.updatedAt) {
                latest = candidate
            }
        }
        return latest
    }

    private fun readChannel(payload: kotlinx.serialization.json.JsonArray, index: Int): Channel? =
        payload.getOrNull(index)?.let { element ->
            when (element) {
                is JsonPrimitive -> element.contentOrNull?.let(Channel::fromJson)
                else -> Channel.fromJson(element.toString())
            }
        }

    private fun hydrateParsedEpisode(parsed: Episode, channel: Channel) {
        if (StringUtils.isBlank(parsed.season)) {
            parsed.season = channel.season
        }
        if (StringUtils.isBlank(parsed.episodeNum)) {
            parsed.episodeNum = channel.episodeNum
        }
        if (parsed.info == null) {
            parsed.info = EpisodeInfo()
        }
        mergeEpisodeInfo(parsed.info, channel)
    }

    private fun mergeEpisodeInfo(info: EpisodeInfo?, channel: Channel) {
        if (info == null) {
            return
        }
        if (StringUtils.isBlank(info.movieImage)) {
            info.movieImage = channel.logo
        }
        if (StringUtils.isBlank(info.plot)) {
            info.plot = channel.description
        }
        if (StringUtils.isBlank(info.releaseDate)) {
            info.releaseDate = channel.releaseDate
        }
        if (StringUtils.isBlank(info.rating)) {
            info.rating = channel.rating
        }
        if (StringUtils.isBlank(info.duration)) {
            info.duration = channel.duration
        }
    }

    private fun canonicalizeSeriesId(seriesId: String?): String {
        val raw = normalize(seriesId)
        if (StringUtils.isBlank(raw) || !raw.contains(":")) {
            return raw
        }
        val last = raw.split(":").asReversed().firstOrNull { StringUtils.isNotBlank(it.trim()) }?.trim().orEmpty()
        return if (StringUtils.isBlank(last)) raw else last
    }

    private fun buildSeriesIdCandidates(seriesId: String?): List<String> {
        val raw = normalize(seriesId)
        if (StringUtils.isBlank(raw)) {
            return emptyList()
        }
        val candidates = LinkedHashSet<String>()
        candidates.add(canonicalizeSeriesId(raw))
        if (raw.contains(":")) {
            raw.split(":").forEach { part ->
                val normalized = normalize(part)
                if (StringUtils.isNotBlank(normalized)) {
                    candidates.add(normalized)
                }
            }
        } else {
            candidates.add("$raw:$raw")
        }
        candidates.add(raw)
        return ArrayList(candidates)
    }
}
