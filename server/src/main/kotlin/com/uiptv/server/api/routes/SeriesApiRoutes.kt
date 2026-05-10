package com.uiptv.server.api.routes

import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.service.AccountService
import com.uiptv.service.ConfigurationService
import com.uiptv.service.HandshakeService
import com.uiptv.service.ImdbMetadataService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeInfo
import com.uiptv.shared.EpisodeList
import com.uiptv.shared.SeasonInfo
import com.uiptv.util.AccountType
import com.uiptv.util.ServerUtils
import com.uiptv.util.StringUtils
import com.uiptv.util.XtremeApiParser
import io.ktor.http.ContentType
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import org.json.JSONArray
import org.json.JSONObject

fun Route.registerSeriesApiRoutes(
    accountService: AccountService,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService,
    handshakeService: HandshakeService,
    imdbMetadataService: ImdbMetadataService
) {
    get("/seriesEpisodes") {
        val response = buildSeriesEpisodesResponse(
            accountService.getById(call.request.queryParameters["accountId"]),
            call.request.queryParameters["seriesId"],
            call.request.queryParameters["categoryId"],
            configurationService,
            seriesWatchStateService
        )
        call.respondText(response, ContentType.Application.Json)
    }

    get("/seriesDetails") {
        val response = buildSeriesDetailsResponse(
            accountService.getById(call.request.queryParameters["accountId"]),
            call.request.queryParameters["seriesId"],
            call.request.queryParameters["categoryId"],
            call.request.queryParameters["seriesName"],
            configurationService,
            handshakeService,
            imdbMetadataService
        )
        call.respondText(response, ContentType.Application.Json)
    }
}

private fun buildSeriesEpisodesResponse(
    account: Account?,
    rawSeriesId: String?,
    rawCategoryId: String?,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
): String {
    val emptyJson = "[]"
    if (account == null) {
        return emptyJson
    }
    val seriesId = rawSeriesId?.takeIf(StringUtils::isNotBlank) ?: return emptyJson
    val categoryId = resolveSeriesCategoryId(rawCategoryId)

    var cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId)
    if (cachedEpisodes.isEmpty() && account.type == AccountType.XTREME_API) {
        cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
    }
    if (isSeriesEpisodeCacheFresh(account, categoryId, seriesId, cachedEpisodes, configurationService)) {
        applySeriesEpisodeWatchedFlag(cachedEpisodes, account, categoryId, seriesId, seriesWatchStateService)
        return ServerUtils.objectToJson(cachedEpisodes)
    }

    var episodesAsChannels = loadSeriesEpisodes(account, seriesId)
    if (episodesAsChannels.isNotEmpty()) {
        SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, episodesAsChannels)
    } else if (account.type == AccountType.XTREME_API) {
        episodesAsChannels = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
    }
    applySeriesEpisodeWatchedFlag(episodesAsChannels, account, categoryId, seriesId, seriesWatchStateService)
    return ServerUtils.objectToJson(episodesAsChannels)
}

private fun isSeriesEpisodeCacheFresh(
    account: Account,
    categoryId: String,
    seriesId: String,
    cachedEpisodes: List<Channel>,
    configurationService: ConfigurationService
): Boolean =
    cachedEpisodes.isNotEmpty() && (
        SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, configurationService.getCacheExpiryMs()) ||
            (account.type == AccountType.XTREME_API &&
                SeriesEpisodeDb.get().isFreshInAnyCategory(account, seriesId, configurationService.getCacheExpiryMs()))
        )

private fun loadSeriesEpisodes(account: Account, seriesId: String): List<Channel> =
    if (account.type == AccountType.XTREME_API) {
        seriesEpisodesToChannels(XtremeApiParser.parseEpisodes(seriesId, account))
    } else {
        emptyList()
    }

private fun seriesEpisodesToChannels(episodes: EpisodeList?): List<Channel> {
    val channels = ArrayList<Channel>()
    episodes?.episodes?.forEach { episode ->
        val channel = seriesEpisodeToChannel(episode)
        if (channel != null) {
            channels += channel
        }
    }
    return channels
}

private fun seriesEpisodeToChannel(episode: Episode?): Channel? {
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

private fun applySeriesEpisodeWatchedFlag(
    episodes: List<Channel>?,
    account: Account?,
    categoryId: String,
    seriesId: String,
    seriesWatchStateService: SeriesWatchStateService
) {
    if (episodes.isNullOrEmpty() || account == null) {
        return
    }
    val state: SeriesWatchState? = seriesWatchStateService.getSeriesLastWatched(account.dbId, categoryId, seriesId)
    episodes.filterNotNull().forEach { channel ->
        channel.watched = seriesWatchStateService.isMatchingEpisode(
            state,
            channel.channelId,
            channel.season,
            channel.episodeNum,
            channel.name
        )
    }
}

private fun buildSeriesDetailsResponse(
    account: Account?,
    rawSeriesId: String?,
    rawCategoryId: String?,
    rawSeriesName: String?,
    configurationService: ConfigurationService,
    handshakeService: HandshakeService,
    imdbMetadataService: ImdbMetadataService
): String {
    if (account == null) {
        return """{"seasonInfo":{},"episodes":[]}"""
    }
    if (account.isNotConnected()) {
        handshakeService.connect(account)
    }
    val seriesId = rawSeriesId
    val categoryId = rawCategoryId
    val seriesName = rawSeriesName
    val response = createSeriesDetailsBaseResponse(account, categoryId, seriesId, configurationService)
    val seasonInfo = JSONObject()
    val imdbFirst = applyInitialSeriesImdbMetadata(seriesName, response, seasonInfo, imdbMetadataService)
    applyProviderSeriesDetails(account, categoryId, seriesId, response, seasonInfo, imdbFirst)
    applyFallbackSeriesImdbMetadata(seriesName, response, seasonInfo, imdbMetadataService)
    enrichEpisodesInSeriesDetails(response)
    response.put("seasonInfo", seasonInfo)
    applySeriesNameYearFallback(seasonInfo, seriesName)
    return response.toString()
}

private fun createSeriesDetailsBaseResponse(
    account: Account,
    categoryId: String?,
    seriesId: String?,
    configurationService: ConfigurationService
): JSONObject {
    val response = JSONObject()
    response.put("seasonInfo", JSONObject())
    response.put("episodes", JSONArray())
    response.put("episodesMeta", JSONArray())
    if (StringUtils.isBlank(seriesId)) {
        return response
    }
    val resolvedCategoryId = categoryId ?: ""
    val resolvedSeriesId = seriesId ?: return response
    val cached = SeriesEpisodeDb.get().getEpisodes(account, resolvedCategoryId, resolvedSeriesId)
    if (cached.isNotEmpty() && SeriesEpisodeDb.get().isFresh(account, resolvedCategoryId, resolvedSeriesId, configurationService.getCacheExpiryMs())) {
        response.put("episodes", JSONArray(ServerUtils.objectToJson(cached)))
    }
    return response
}

@Suppress("USELESS_CAST")
private fun applyInitialSeriesImdbMetadata(
    seriesName: String?,
    response: JSONObject,
    seasonInfo: JSONObject,
    imdbMetadataService: ImdbMetadataService
): JSONObject {
    val resolvedSeriesName = seriesName ?: ""
    val fuzzyHints = buildSeriesFuzzyHints(resolvedSeriesName, seasonInfo, response.optJSONArray("episodes"))
    val imdbFirst = (imdbMetadataService.findBestEffortDetails(resolvedSeriesName, "", fuzzyHints) as? JSONObject) ?: JSONObject()
    copySeriesMetadata(seasonInfo, imdbFirst)
    imdbFirst.optJSONArray("episodesMeta")?.let { response.put("episodesMeta", it) }
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
    if (account.type != AccountType.XTREME_API || StringUtils.isBlank(seriesId)) {
        return
    }
    val resolvedSeriesId = seriesId.orEmpty()
    val resolvedCategoryId = categoryId.orEmpty()
    val details = XtremeApiParser.parseEpisodes(resolvedSeriesId, account)
    mergeProviderSeasonInfo(seasonInfo, details.seasonInfo)
    val episodesJson = toSeriesDetailsEpisodesJson(details, indexSeriesEpisodesMeta(imdbFirst.optJSONArray("episodesMeta")))
    response.put("episodes", episodesJson)
    if (episodesJson.length() > 0) {
        SeriesEpisodeDb.get().saveAll(account, resolvedCategoryId, resolvedSeriesId, seriesDetailsChannels(episodesJson))
    }
}

private fun mergeProviderSeasonInfo(seasonInfo: JSONObject, info: SeasonInfo?) {
    if (info == null) {
        return
    }
    mergeSeriesMetadata(seasonInfo, JSONObject(info.toJson()))
}

private fun toSeriesDetailsEpisodesJson(details: EpisodeList, episodesMeta: Map<String, JSONObject>): JSONArray {
    val episodesJson = JSONArray()
    details.episodes.forEach { episode ->
        val channel = toSeriesDetailsEpisodeChannel(episode, episodesMeta)
        if (channel != null) {
            episodesJson.put(JSONObject(channel.toJson()))
        }
    }
    return episodesJson
}

private fun toSeriesDetailsEpisodeChannel(episode: Episode?, episodesMeta: Map<String, JSONObject>): Channel? {
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
    val info: EpisodeInfo? = episode.info
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
    enrichSeriesDetailsEpisode(channel, episodesMeta)
    return channel
}

@Suppress("USELESS_CAST")
private fun applyFallbackSeriesImdbMetadata(
    seriesName: String?,
    response: JSONObject,
    seasonInfo: JSONObject,
    imdbMetadataService: ImdbMetadataService
) {
    val resolvedSeriesName = seriesName ?: ""
    val fuzzyHints = buildSeriesFuzzyHints(firstNonBlank(seasonInfo.optString("name", ""), resolvedSeriesName), seasonInfo, response.optJSONArray("episodes"))
    val imdbFallback = (imdbMetadataService.findBestEffortDetails(
        firstNonBlank(seasonInfo.optString("name", ""), resolvedSeriesName),
        seasonInfo.optString("tmdb", ""),
        fuzzyHints
    ) as? JSONObject) ?: JSONObject()
    mergeSeriesMetadata(seasonInfo, imdbFallback)
    if ((response.optJSONArray("episodesMeta") == null || response.optJSONArray("episodesMeta").isEmpty) &&
        imdbFallback.optJSONArray("episodesMeta") != null) {
        response.put("episodesMeta", imdbFallback.optJSONArray("episodesMeta"))
    }
}

private fun copySeriesMetadata(target: JSONObject, source: JSONObject) {
    listOf("name", "cover", "plot", "cast", "director", "genre", "releaseDate", "rating", "tmdb", "imdbUrl")
        .forEach { copySeriesMetadataField(target, source, it) }
}

private fun mergeSeriesMetadata(target: JSONObject, source: JSONObject?) {
    if (source == null) return
    listOf("name", "cover", "plot", "cast", "director", "genre", "releaseDate", "rating", "tmdb", "imdbUrl")
        .forEach { mergeSeriesMetadataField(target, source, it) }
}

private fun seriesDetailsChannels(episodesJson: JSONArray): List<Channel> {
    val channels = ArrayList<Channel>()
    for (index in 0 until episodesJson.length()) {
        val obj = episodesJson.optJSONObject(index) ?: continue
        Channel.fromJson(obj.toString())?.let(channels::add)
    }
    return channels
}

private fun mergeSeriesMetadataField(target: JSONObject, source: JSONObject, key: String) {
    val existing = target.optString(key, "")
    if (StringUtils.isNotBlank(existing)) {
        return
    }
    val incoming = source.optString(key, "")
    if (StringUtils.isNotBlank(incoming)) {
        target.put(key, incoming)
    }
}

private fun copySeriesMetadataField(target: JSONObject, source: JSONObject, key: String) {
    val incoming = source.optString(key, "")
    if (StringUtils.isNotBlank(incoming)) {
        target.put(key, incoming)
    }
}

private fun indexSeriesEpisodesMeta(episodesMeta: JSONArray?): Map<String, JSONObject> {
    val indexed = HashMap<String, JSONObject>()
    if (episodesMeta == null) {
        return indexed
    }
    for (index in 0 until episodesMeta.length()) {
        val row = episodesMeta.optJSONObject(index) ?: continue
        val season = safeSeriesNumeric(row.optString("season", ""))
        val episode = safeSeriesNumeric(row.optString("episodeNum", ""))
        if (StringUtils.isNotBlank(season) && StringUtils.isNotBlank(episode)) {
            indexed["$season:$episode"] = row
        }
        val title = normalizeSeriesText(row.optString("title", ""))
        if (StringUtils.isNotBlank(title)) {
            indexed["title:$title"] = row
        }
    }
    return indexed
}

private fun enrichSeriesDetailsEpisode(channel: Channel?, episodesMeta: Map<String, JSONObject>?) {
    if (episodesMeta.isNullOrEmpty() || channel == null) {
        return
    }
    val season = safeSeriesNumeric(channel.season)
    val episode = safeSeriesNumeric(channel.episodeNum)
    var meta: JSONObject? = null
    if (StringUtils.isNotBlank(season) && StringUtils.isNotBlank(episode)) {
        meta = episodesMeta["$season:$episode"]
    }
    if (meta == null) {
        meta = episodesMeta["title:${normalizeSeriesText(channel.name)}"]
    }
    if (meta == null) {
        return
    }
    if (StringUtils.isBlank(channel.description)) {
        channel.description = meta.optString("plot", "")
    }
    if (StringUtils.isBlank(channel.releaseDate)) {
        channel.releaseDate = meta.optString("releaseDate", "")
    }
    if (StringUtils.isNotBlank(meta.optString("logo", ""))) {
        channel.logo = meta.optString("logo", "")
    }
}

private fun normalizeSeriesText(value: String?): String =
    if (StringUtils.isBlank(value)) "" else value!!.lowercase().replace(Regex("[^a-z0-9 ]"), " ").replace(Regex("\\s+"), " ").trim()

private fun safeSeriesNumeric(value: String?): String {
    if (StringUtils.isBlank(value)) {
        return ""
    }
    val normalized = value!!.replace(Regex("\\D"), "")
    return if (StringUtils.isBlank(normalized)) "" else normalized
}

private fun applySeriesNameYearFallback(seasonInfo: JSONObject, rawSeriesName: String?) {
    if (StringUtils.isBlank(rawSeriesName)) {
        return
    }
    val trimmed = rawSeriesName?.trim() ?: return
    val inferredName = trimmed.replace(Regex("\\s*\\((19|20)\\d{2}\\)\\s*$"), "").trim()
    var inferredYear = ""
    val matcher = Regex("\\((19|20)\\d{2}\\)\\s*$").find(trimmed)
    if (matcher != null) {
        inferredYear = matcher.value.replace(Regex("\\D"), "")
    }
    if (StringUtils.isBlank(seasonInfo.optString("name", "")) && StringUtils.isNotBlank(inferredName)) {
        seasonInfo.put("name", inferredName)
    }
    if (StringUtils.isBlank(seasonInfo.optString("releaseDate", "")) && StringUtils.isNotBlank(inferredYear)) {
        seasonInfo.put("releaseDate", inferredYear)
    }
}

private fun enrichEpisodesInSeriesDetails(response: JSONObject?) {
    if (response == null) {
        return
    }
    val episodes = response.optJSONArray("episodes")
    val episodesMeta = response.optJSONArray("episodesMeta")
    if (episodes == null || episodes.isEmpty || episodesMeta == null || episodesMeta.isEmpty) {
        return
    }
    val indexed = indexSeriesEpisodesMeta(episodesMeta)
    if (indexed.isEmpty()) {
        return
    }
    for (index in 0 until episodes.length()) {
        val row = episodes.optJSONObject(index) ?: continue
        val channel = Channel.fromJson(row.toString()) ?: continue
        enrichSeriesDetailsEpisode(channel, indexed)
        episodes.put(index, JSONObject(channel.toJson()))
    }
}

private fun buildSeriesFuzzyHints(baseTitle: String?, seasonInfo: JSONObject?, episodes: JSONArray?): List<String> {
    val hints = ArrayList<String>()
    addSeriesHint(hints, baseTitle)
    if (seasonInfo != null) {
        addSeriesHint(hints, seasonInfo.optString("name", ""))
        addSeriesHint(hints, seasonInfo.optString("plot", ""))
        addSeriesHint(hints, seasonInfo.optString("releaseDate", ""))
    }
    if (episodes != null) {
        for (index in 0 until minOf(8, episodes.length())) {
            val row = episodes.optJSONObject(index) ?: continue
            addSeriesHint(hints, row.optString("name", ""))
            addSeriesHint(hints, row.optString("releaseDate", ""))
        }
    }
    return hints
}

private fun addSeriesHint(hints: MutableList<String>, value: String?) {
    if (StringUtils.isBlank(value)) {
        return
    }
    val cleaned = value!!
        .replace(Regex("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b"), " ")
        .replace(Regex("(?i)\\bs\\d{1,2}e\\d{1,3}\\b"), " ")
        .replace(Regex("[\\[\\]{}()]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (StringUtils.isBlank(cleaned) || cleaned.length < 2 || hints.contains(cleaned)) {
        return
    }
    hints += cleaned
}

private fun firstNonBlank(vararg values: String?): String = values.firstOrNull { StringUtils.isNotBlank(it) } ?: ""

private fun resolveSeriesCategoryId(rawCategoryId: String?): String {
    if (StringUtils.isBlank(rawCategoryId)) {
        return ""
    }
    val categoryId = rawCategoryId ?: return ""
    val category: Category? = SeriesCategoryDb.get().getById(categoryId)
    return if (category != null && StringUtils.isNotBlank(category.categoryId)) category.categoryId ?: categoryId else categoryId
}
