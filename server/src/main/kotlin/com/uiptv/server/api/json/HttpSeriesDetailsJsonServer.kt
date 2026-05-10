package com.uiptv.server.api.json

import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpHandler
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.service.AccountService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.HandshakeService
import com.uiptv.service.ImdbMetadataService
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeList
import com.uiptv.shared.SeasonInfo
import com.uiptv.util.AccountType
import com.uiptv.util.ServerUtils.generateJsonResponse
import com.uiptv.util.ServerUtils.getParam
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.XtremeApiParser
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException

class HttpSeriesDetailsJsonServer : HttpHandler {
    companion object {
        private const val KEY_COVER = "cover"
        private const val KEY_DIRECTOR = "director"
        private const val KEY_EPISODES = "episodes"
        private const val KEY_EPISODES_META = "episodesMeta"
        private const val KEY_GENRE = "genre"
        private const val KEY_IMDB_URL = "imdbUrl"
        private const val KEY_RATING = "rating"
        private const val KEY_RELEASE_DATE = "releaseDate"
    }

    @Throws(IOException::class)
    override fun handle(ex: HttpExchange) {
        val account = AccountService.getInstance().getById(getParam(ex, "accountId"))
        if (account == null) {
            generateJsonResponse(ex, """{"seasonInfo":{},"$KEY_EPISODES":[]}""")
            return
        }
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account)
        }

        val seriesId = getParam(ex, "seriesId")
        val categoryId = getParam(ex, "categoryId")
        val seriesName = getParam(ex, "seriesName")
        val response = createBaseResponse(account, categoryId, seriesId)
        val seasonInfo = JSONObject()
        val imdbFirst = applyInitialImdbMetadata(seriesName, response, seasonInfo)
        applyProviderSeriesDetails(account, categoryId, seriesId, response, seasonInfo, imdbFirst)
        applyFallbackImdbMetadata(seriesName, response, seasonInfo)
        enrichEpisodesInResponse(response)
        response.put("seasonInfo", seasonInfo)
        applyNameYearFallback(seasonInfo, seriesName)
        generateJsonResponse(ex, response.toString())
    }

    private fun createBaseResponse(account: Account, categoryId: String?, seriesId: String?): JSONObject {
        val response = JSONObject()
        response.put("seasonInfo", JSONObject())
        response.put(KEY_EPISODES, JSONArray())
        response.put(KEY_EPISODES_META, JSONArray())
        if (isBlank(seriesId)) return response
        val resolvedCategoryId = categoryId ?: ""
        val resolvedSeriesId = seriesId ?: return response
        val cached = SeriesEpisodeDb.get().getEpisodes(account, resolvedCategoryId, resolvedSeriesId)
        if (cached.isNotEmpty() && SeriesEpisodeDb.get().isFresh(account, resolvedCategoryId, resolvedSeriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
            response.put(KEY_EPISODES, JSONArray(com.uiptv.util.ServerUtils.objectToJson(cached)))
        }
        return response
    }

    private fun applyInitialImdbMetadata(seriesName: String?, response: JSONObject, seasonInfo: JSONObject): JSONObject {
        val resolvedSeriesName = seriesName ?: ""
        val fuzzyHints = buildFuzzyHints(resolvedSeriesName, seasonInfo, response.optJSONArray(KEY_EPISODES))
        val imdbFirst = ImdbMetadataService.getInstance().findBestEffortDetails(resolvedSeriesName, "", fuzzyHints)
        copyMetadata(seasonInfo, imdbFirst)
        imdbFirst.optJSONArray(KEY_EPISODES_META)?.let { response.put(KEY_EPISODES_META, it) }
        return imdbFirst
    }

    private fun applyProviderSeriesDetails(
        account: Account,
        categoryId: String?,
        seriesId: String?,
        response: JSONObject,
        seasonInfo: JSONObject,
        imdbFirst: JSONObject
    ) {
        if (account.type != AccountType.XTREME_API || isBlank(seriesId)) {
            return
        }
        val resolvedSeriesId = seriesId ?: return
        val resolvedCategoryId = categoryId ?: ""
        val details = XtremeApiParser.parseEpisodes(resolvedSeriesId, account) ?: return
        mergeProviderSeasonInfo(seasonInfo, details.seasonInfo)
        val episodesJson = toEpisodesJson(details, indexEpisodesMeta(imdbFirst.optJSONArray(KEY_EPISODES_META)))
        response.put(KEY_EPISODES, episodesJson)
        if (episodesJson.length() > 0) {
            SeriesEpisodeDb.get().saveAll(account, resolvedCategoryId, resolvedSeriesId, toChannels(episodesJson))
        }
    }

    private fun mergeProviderSeasonInfo(seasonInfo: JSONObject, info: SeasonInfo?) {
        if (info == null) return
        mergeMetadata(seasonInfo, JSONObject(info.toJson()))
    }

    private fun toEpisodesJson(details: EpisodeList, episodesMeta: Map<String, JSONObject>): JSONArray {
        val episodesJson = JSONArray()
        details.episodes?.forEach { episode ->
            val channel = toEpisodeChannel(episode, episodesMeta)
            if (channel != null) {
                episodesJson.put(JSONObject(channel.toJson()))
            }
        }
        return episodesJson
    }

    private fun toEpisodeChannel(episode: Episode?, episodesMeta: Map<String, JSONObject>): Channel? {
        if (episode == null) return null
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
            if (isBlank(channel.season)) {
                channel.season = info.season
            }
        }
        enrichEpisode(channel, episodesMeta)
        return channel
    }

    private fun applyFallbackImdbMetadata(seriesName: String?, response: JSONObject, seasonInfo: JSONObject) {
        val resolvedSeriesName = seriesName ?: ""
        val fuzzyHints = buildFuzzyHints(firstNonBlank(seasonInfo.optString("name", ""), resolvedSeriesName), seasonInfo, response.optJSONArray(KEY_EPISODES))
        val imdbFallback = ImdbMetadataService.getInstance().findBestEffortDetails(
            firstNonBlank(seasonInfo.optString("name", ""), resolvedSeriesName),
            seasonInfo.optString("tmdb", ""),
            fuzzyHints
        )
        mergeMetadata(seasonInfo, imdbFallback)
        if ((response.optJSONArray(KEY_EPISODES_META) == null || response.optJSONArray(KEY_EPISODES_META).isEmpty) &&
            imdbFallback.optJSONArray(KEY_EPISODES_META) != null) {
            response.put(KEY_EPISODES_META, imdbFallback.optJSONArray(KEY_EPISODES_META))
        }
    }

    private fun copyMetadata(target: JSONObject, source: JSONObject) {
        listOf("name", KEY_COVER, "plot", "cast", KEY_DIRECTOR, KEY_GENRE, KEY_RELEASE_DATE, KEY_RATING, "tmdb", KEY_IMDB_URL)
            .forEach { copyIfPresent(target, source, it) }
    }

    private fun mergeMetadata(target: JSONObject, source: JSONObject) {
        listOf("name", KEY_COVER, "plot", "cast", KEY_DIRECTOR, KEY_GENRE, KEY_RELEASE_DATE, KEY_RATING, "tmdb", KEY_IMDB_URL)
            .forEach { mergeMissing(target, source, it) }
    }

    private fun toChannels(episodesJson: JSONArray): List<Channel> {
        val channels = ArrayList<Channel>()
        for (i in 0 until episodesJson.length()) {
            val obj = episodesJson.optJSONObject(i) ?: continue
            Channel.fromJson(obj.toString())?.let(channels::add)
        }
        return channels
    }

    private fun mergeMissing(target: JSONObject, source: JSONObject, key: String) {
        val existing = target.optString(key, "")
        if (!isBlank(existing)) return
        val incoming = source.optString(key, "")
        if (!isBlank(incoming)) target.put(key, incoming)
    }

    private fun copyIfPresent(target: JSONObject, source: JSONObject, key: String) {
        val incoming = source.optString(key, "")
        if (!isBlank(incoming)) target.put(key, incoming)
    }

    private fun indexEpisodesMeta(episodesMeta: JSONArray?): Map<String, JSONObject> {
        val indexed = HashMap<String, JSONObject>()
        if (episodesMeta == null) return indexed
        for (i in 0 until episodesMeta.length()) {
            val row = episodesMeta.optJSONObject(i) ?: continue
            val season = safeNumeric(row.optString("season", ""))
            val episode = safeNumeric(row.optString("episodeNum", ""))
            if (!isBlank(season) && !isBlank(episode)) indexed["$season:$episode"] = row
            val title = normalize(row.optString("title", ""))
            if (!isBlank(title)) indexed["title:$title"] = row
        }
        return indexed
    }

    private fun enrichEpisode(channel: Channel?, episodesMeta: Map<String, JSONObject>?) {
        if (episodesMeta.isNullOrEmpty() || channel == null) return
        val season = safeNumeric(channel.season)
        val episode = safeNumeric(channel.episodeNum)
        var meta: JSONObject? = null
        if (!isBlank(season) && !isBlank(episode)) {
            meta = episodesMeta["$season:$episode"]
        }
        if (meta == null) {
            meta = episodesMeta["title:${normalize(channel.name)}"]
        }
        if (meta == null) return
        if (isBlank(channel.description)) channel.description = meta.optString("plot", "")
        if (isBlank(channel.releaseDate)) channel.releaseDate = meta.optString(KEY_RELEASE_DATE, "")
        if (!isBlank(meta.optString("logo", ""))) channel.logo = meta.optString("logo", "")
    }

    private fun normalize(value: String?): String =
        if (isBlank(value)) "" else value!!.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

    private fun safeNumeric(value: String?): String {
        if (isBlank(value)) return ""
        val normalized = value!!.replace(Regex("\\D"), "")
        return if (isBlank(normalized)) "" else normalized
    }

    private fun applyNameYearFallback(seasonInfo: JSONObject, rawSeriesName: String?) {
        if (isBlank(rawSeriesName)) return
        val trimmed = rawSeriesName?.trim() ?: return
        val inferredName = trimmed.replace(Regex("\\s*\\((19|20)\\d{2}\\)\\s*$"), "").trim()
        var inferredYear = ""
        val matcher = Regex("\\((19|20)\\d{2}\\)\\s*$").find(trimmed)
        if (matcher != null) {
            inferredYear = matcher.value.replace(Regex("\\D"), "")
        }
        if (isBlank(seasonInfo.optString("name", "")) && !isBlank(inferredName)) seasonInfo.put("name", inferredName)
        if (isBlank(seasonInfo.optString(KEY_RELEASE_DATE, "")) && !isBlank(inferredYear)) seasonInfo.put(KEY_RELEASE_DATE, inferredYear)
    }

    private fun enrichEpisodesInResponse(response: JSONObject?) {
        if (response == null) return
        val episodes = response.optJSONArray(KEY_EPISODES)
        val episodesMeta = response.optJSONArray(KEY_EPISODES_META)
        if (episodes == null || episodes.isEmpty || episodesMeta == null || episodesMeta.isEmpty) return
        val indexed = indexEpisodesMeta(episodesMeta)
        if (indexed.isEmpty()) return
        for (i in 0 until episodes.length()) {
            val row = episodes.optJSONObject(i) ?: continue
            val channel = Channel.fromJson(row.toString()) ?: continue
            enrichEpisode(channel, indexed)
            episodes.put(i, JSONObject(channel.toJson()))
        }
    }

    private fun buildFuzzyHints(baseTitle: String?, seasonInfo: JSONObject?, episodes: JSONArray?): List<String> {
        val hints = ArrayList<String>()
        addHint(hints, baseTitle)
        if (seasonInfo != null) {
            addHint(hints, seasonInfo.optString("name", ""))
            addHint(hints, seasonInfo.optString("plot", ""))
            addHint(hints, seasonInfo.optString(KEY_RELEASE_DATE, ""))
        }
        if (episodes != null) {
            for (i in 0 until minOf(8, episodes.length())) {
                val row = episodes.optJSONObject(i) ?: continue
                addHint(hints, row.optString("name", ""))
                addHint(hints, row.optString(KEY_RELEASE_DATE, ""))
            }
        }
        return hints
    }

    private fun addHint(hints: MutableList<String>, value: String?) {
        if (isBlank(value)) return
        val cleaned = value!!
            .replace(Regex("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b"), " ")
            .replace(Regex("(?i)\\bs\\d{1,2}e\\d{1,3}\\b"), " ")
            .replace(Regex("[\\[\\]{}()]+"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (isBlank(cleaned) || cleaned.length < 2 || hints.contains(cleaned)) return
        hints += cleaned
    }

    private fun firstNonBlank(vararg values: String?): String = values.firstOrNull { !isBlank(it) } ?: ""
}
