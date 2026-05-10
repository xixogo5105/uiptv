package com.uiptv.service

import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.PlayerResponse
import com.uiptv.model.SeriesWatchState
import com.uiptv.util.ServerUrlUtil
import com.uiptv.util.StringUtils.isBlank
import java.io.IOException
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object BingeWatchService {
    private const val DEFAULT_SEASON = "1"
    private const val PLAYLIST_PATH = "/bingewatch.m3u8?token="
    private const val ENTRY_PATH = "/bingwatch?token="
    private const val EPISODE_ID_QUERY = "&episodeId="

    private val sessions = ConcurrentHashMap<String, Session>()

    @JvmStatic
    fun getInstance(): BingeWatchService = this
    fun createSession(
        account: Account?,
        seriesId: String?,
        seriesCategoryId: String?,
        season: String?,
        episodes: List<Channel>?,
        watchState: SeriesWatchState?
    ): String {
        if (account == null || isBlank(account.dbId) || isBlank(seriesId) || isBlank(season) || episodes.isNullOrEmpty()) {
            return ""
        }

        val seasonEpisodes = orderSeasonEpisodes(season, episodes, watchState)
        if (seasonEpisodes.isEmpty()) {
            return ""
        }

        val token = UUID.randomUUID().toString()
        sessions[token] = Session(
            account.dbId.orEmpty(),
            safe(seriesId),
            safe(seriesCategoryId),
            safe(season),
            seasonEpisodes
        )
        return token
    }
    fun buildPlaylistUrl(token: String?): String {
        if (isBlank(token)) {
            return ""
        }
        return ServerUrlUtil.getLocalServerUrl() + PLAYLIST_PATH + urlEncode(token)
    }
    fun buildPlaylistUrl(token: String?, startEpisodeId: String?): String {
        if (isBlank(token)) {
            return ""
        }
        if (isBlank(startEpisodeId)) {
            return buildPlaylistUrl(token)
        }
        val session = sessions[token] ?: return ""
        var startIndex = 0
        for (i in session.episodes.indices) {
            if (startEpisodeId == session.episodes[i].episodeId) {
                startIndex = i
                break
            }
        }
        if (startIndex <= 0) {
            return buildPlaylistUrl(token)
        }

        val newToken = UUID.randomUUID().toString()
        sessions[newToken] = Session(
            session.accountId,
            session.seriesId,
            session.seriesCategoryId,
            session.season,
            ArrayList(session.episodes.subList(startIndex, session.episodes.size))
        )
        return buildPlaylistUrl(newToken)
    }
    fun getPlaylistItems(token: String?): List<PlaylistItem> {
        val session = sessions[token]
        if (session == null || session.episodes.isEmpty()) {
            return emptyList()
        }
        return session.episodes.map { episode ->
            PlaylistItem(
                episode.episodeId,
                episode.episodeName,
                episode.season,
                episode.episodeNumber
            )
        }
    }
    fun renderPlaylist(token: String?): String {
        val session = sessions[token] ?: return ""
        val playlist = StringBuilder("#EXTM3U\n")
        session.episodes.forEach { episode ->
            val seasonValue = firstNonBlank(normalizeNumber(episode.season), DEFAULT_SEASON)
            val episodeValue = firstNonBlank(normalizeNumber(episode.episodeNumber), "-")
            val title = "Season $seasonValue - Episode $episodeValue"
            playlist.append("#EXTINF:-1,").append(title).append("\n")
            playlist.append(buildEntryUrl(token.orEmpty(), episode.episodeId)).append("\n")
        }
        return playlist.toString()
    }

    @JvmStatic
    @Throws(IOException::class)
    fun resolveEpisode(token: String?, episodeId: String?): ResolvedEpisode? {
        val session = sessions[token]
        if (session == null || isBlank(episodeId)) {
            return null
        }
        val episode = session.findEpisode(episodeId.orEmpty()) ?: return null

        val account = AccountService.getInstance().getById(session.accountId) ?: return null
        account.action = Account.AccountAction.series

        val channel = Channel.fromJson(episode.channelJson) ?: return null

        SeriesWatchStateService.getInstance().markSeriesEpisodeManualIfNewer(
            account,
            session.seriesCategoryId,
            session.seriesId,
            episode.episodeId,
            episode.episodeName,
            episode.season,
            episode.episodeNumber
        )

        val response: PlayerResponse = PlayerService.getInstance().get(
            account,
            channel,
            episode.episodeId,
            session.seriesId,
            session.seriesCategoryId
        )
        if (isBlank(response.url)) {
            return null
        }
        return ResolvedEpisode(response.url.orEmpty(), episode.episodeName)
    }

    fun orderSeasonEpisodes(season: String?, episodes: List<Channel>?, watchState: SeriesWatchState?): List<SessionEpisode> {
        val normalizedSeason = normalizeNumber(season)
        val ordered = ArrayList<SessionEpisode>()
        if (isBlank(normalizedSeason) || episodes == null) {
            return ordered
        }

        episodes.forEach { episode ->
            val episodeSeason = normalizeNumber(firstNonBlank(episode.season, DEFAULT_SEASON))
            val episodeId = safe(episode.channelId)
            if (normalizedSeason == episodeSeason && !isBlank(episodeId)) {
                ordered.add(
                    SessionEpisode(
                        episodeId,
                        safe(episode.name),
                        episodeSeason,
                        normalizeNumber(episode.episodeNum),
                        episode.toJson()
                    )
                )
            }
        }

        ordered.sortWith(
            compareBy<SessionEpisode> { parseNumberOrDefault(it.season, 1) }
                .thenBy { parseNumberOrDefault(it.episodeNumber, Int.MAX_VALUE) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.episodeName }
        )

        val startIndex = resolveStartIndex(ordered, normalizedSeason, watchState)
        if (startIndex <= 0) {
            return ordered
        }
        return ArrayList(ordered.subList(startIndex, ordered.size))
    }

    private fun resolveStartIndex(ordered: List<SessionEpisode>, season: String, watchState: SeriesWatchState?): Int {
        if (ordered.isEmpty() || watchState == null) {
            return 0
        }
        if (season != normalizeNumber(watchState.season)) {
            return 0
        }

        val watchedEpisodeId = safe(watchState.episodeId)
        val watchedEpisodeNumber = if (watchState.episodeNum > 0) watchState.episodeNum.toString() else ""
        for (i in ordered.indices) {
            val item = ordered[i]
            if (!isBlank(watchedEpisodeId) && watchedEpisodeId == item.episodeId) {
                return i
            }
            if (!isBlank(watchedEpisodeNumber) && watchedEpisodeNumber == item.episodeNumber) {
                return i
            }
        }
        return 0
    }

    private fun buildEntryUrl(token: String, episodeId: String): String =
        ServerUrlUtil.getLocalServerUrl() +
            ENTRY_PATH + urlEncode(token) +
            EPISODE_ID_QUERY + urlEncode(episodeId)

    private fun urlEncode(value: String?): String = URLEncoder.encode(safe(value), StandardCharsets.UTF_8)

    private fun normalizeNumber(raw: String?): String {
        if (isBlank(raw)) {
            return ""
        }
        val digits = raw.orEmpty().replace(Regex("\\D"), "")
        if (digits.isEmpty()) {
            return ""
        }
        return digits.toInt().toString()
    }

    private fun parseNumberOrDefault(raw: String?, fallback: Int): Int {
        val normalized = normalizeNumber(raw)
        if (normalized.isEmpty()) {
            return fallback
        }
        return try {
            normalized.toInt()
        } catch (_: NumberFormatException) {
            fallback
        }
    }

    private fun firstNonBlank(first: String?, fallback: String?): String =
        if (isBlank(first)) safe(fallback) else first.orEmpty().trim()

    private fun safe(value: String?): String = value?.trim().orEmpty()

    private data class Session(
        val accountId: String,
        val seriesId: String,
        val seriesCategoryId: String,
        val season: String,
        val episodes: List<SessionEpisode>
    ) {
        fun findEpisode(episodeId: String): SessionEpisode? = episodes.firstOrNull { it.episodeId == episodeId }
    }

    data class SessionEpisode(
        val episodeId: String,
        val episodeName: String,
        val season: String,
        val episodeNumber: String,
        val channelJson: String
    ) {
        fun episodeId(): String = episodeId

        fun episodeName(): String = episodeName

        fun season(): String = season

        fun episodeNumber(): String = episodeNumber

        fun channelJson(): String = channelJson
    }

    data class PlaylistItem(
        val episodeId: String,
        val episodeName: String,
        val season: String,
        val episodeNumber: String
    ) {
        fun episodeId(): String = episodeId

        fun episodeName(): String = episodeName

        fun season(): String = season

        fun episodeNumber(): String = episodeNumber
    }

    data class ResolvedEpisode(
        val url: String,
        val episodeName: String
    ) {
        fun url(): String = url

        fun episodeName(): String = episodeName
    }
}
