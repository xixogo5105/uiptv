package com.uiptv.server.api.routes

import com.uiptv.db.SeriesCategoryDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.model.Account
import com.uiptv.model.Category
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.server.api.dto.ChannelRouteDto
import com.uiptv.server.api.dto.SeriesDetailsResponseDto
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
import com.uiptv.util.StringUtils
import com.uiptv.util.XtremeApiParser
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

fun Route.registerSeriesApiRoutes(
    accountService: AccountService,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService,
    handshakeService: HandshakeService,
    imdbMetadataService: ImdbMetadataService
) {
    get("/seriesEpisodes") {
        call.respond(
            buildSeriesEpisodesResponse(
                accountService.getById(call.request.queryParameters["accountId"]),
                call.request.queryParameters["seriesId"],
                call.request.queryParameters["categoryId"],
                configurationService,
                seriesWatchStateService
            )
                .map(::toChannelRouteDto)
        )
    }

    get("/seriesDetails") {
        call.respond(
            buildSeriesDetailsResponse(
                accountService.getById(call.request.queryParameters["accountId"]),
                call.request.queryParameters["seriesId"],
                call.request.queryParameters["categoryId"],
                call.request.queryParameters["seriesName"],
                configurationService,
                handshakeService,
                imdbMetadataService
            )
        )
    }
}

private fun buildSeriesEpisodesResponse(
    account: Account?,
    rawSeriesId: String?,
    rawCategoryId: String?,
    configurationService: ConfigurationService,
    seriesWatchStateService: SeriesWatchStateService
): List<Channel> {
    if (account == null) {
        return emptyList()
    }
    val seriesId = rawSeriesId?.takeIf(StringUtils::isNotBlank) ?: return emptyList()
    val categoryId = resolveSeriesCategoryId(rawCategoryId)

    var cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId)
    if (cachedEpisodes.isEmpty() && account.type == AccountType.XTREME_API) {
        cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
    }
    if (isSeriesEpisodeCacheFresh(account, categoryId, seriesId, cachedEpisodes, configurationService)) {
        applySeriesEpisodeWatchedFlag(cachedEpisodes, account, categoryId, seriesId, seriesWatchStateService)
        return cachedEpisodes
    }

    var episodesAsChannels = loadSeriesEpisodes(account, seriesId)
    if (episodesAsChannels.isNotEmpty()) {
        SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, episodesAsChannels)
    } else if (account.type == AccountType.XTREME_API) {
        episodesAsChannels = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId)
    }
    applySeriesEpisodeWatchedFlag(episodesAsChannels, account, categoryId, seriesId, seriesWatchStateService)
    return episodesAsChannels
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
): SeriesDetailsResponseDto {
    if (account == null) {
        return SeriesDetailsResponseDto(seasonInfo = JsonObject(emptyMap()), episodes = emptyList(), episodesMeta = emptyList())
    }
    if (account.isNotConnected()) {
        handshakeService.connect(account)
    }
    val seriesId = rawSeriesId
    val categoryId = rawCategoryId
    val seriesName = rawSeriesName
    val response = createSeriesDetailsBaseResponse(account, categoryId, seriesId, configurationService)
    val imdbFirst = applyInitialSeriesImdbMetadata(seriesName, response, imdbMetadataService)
    applyProviderSeriesDetails(account, categoryId, seriesId, response, imdbFirst)
    applyFallbackSeriesImdbMetadata(seriesName, response, imdbMetadataService)
    enrichEpisodesInSeriesDetails(response)
    applySeriesNameYearFallback(response.seasonInfo, seriesName)
    return response.toDto()
}

private fun createSeriesDetailsBaseResponse(
    account: Account,
    categoryId: String?,
    seriesId: String?,
    configurationService: ConfigurationService
): SeriesDetailsState {
    val response = SeriesDetailsState()
    if (StringUtils.isBlank(seriesId)) {
        return response
    }
    val resolvedCategoryId = categoryId ?: ""
    val resolvedSeriesId = seriesId ?: return response
    val cached = SeriesEpisodeDb.get().getEpisodes(account, resolvedCategoryId, resolvedSeriesId)
    if (cached.isNotEmpty() && SeriesEpisodeDb.get().isFresh(account, resolvedCategoryId, resolvedSeriesId, configurationService.getCacheExpiryMs())) {
        response.episodes += cached
    }
    return response
}

private fun applyInitialSeriesImdbMetadata(
    seriesName: String?,
    response: SeriesDetailsState,
    imdbMetadataService: ImdbMetadataService
): JsonObject {
    val resolvedSeriesName = seriesName ?: ""
    val fuzzyHints = buildSeriesFuzzyHints(resolvedSeriesName, response.seasonInfo, response.episodes)
    val imdbFirst = imdbMetadataService.findBestEffortDetails(resolvedSeriesName, "", fuzzyHints)
    copySeriesMetadata(response.seasonInfo, imdbFirst)
    response.episodesMeta += imdbFirst.jsonArrayField("episodesMeta")
    return imdbFirst
}

private fun applyProviderSeriesDetails(
    account: Account,
    categoryId: String?,
    seriesId: String?,
    response: SeriesDetailsState,
    imdbFirst: JsonObject
) {
    if (account.type != AccountType.XTREME_API || StringUtils.isBlank(seriesId)) {
        return
    }
    val resolvedSeriesId = seriesId.orEmpty()
    val resolvedCategoryId = categoryId.orEmpty()
    val details = XtremeApiParser.parseEpisodes(resolvedSeriesId, account)
    mergeProviderSeasonInfo(response.seasonInfo, details.seasonInfo)
    val episodes = toSeriesDetailsEpisodes(details, indexSeriesEpisodesMeta(imdbFirst.jsonArrayField("episodesMeta")))
    response.episodes.clear()
    response.episodes += episodes
    if (episodes.isNotEmpty()) {
        SeriesEpisodeDb.get().saveAll(account, resolvedCategoryId, resolvedSeriesId, episodes)
    }
}

private fun mergeProviderSeasonInfo(seasonInfo: MutableMap<String, String>, info: SeasonInfo?) {
    if (info == null) {
        return
    }
    mergeSeriesMetadata(seasonInfo, info.toJson().toJsonObject())
}

private fun toSeriesDetailsEpisodes(details: EpisodeList, episodesMeta: Map<String, JsonObject>): List<Channel> =
    details.episodes.mapNotNull { episode -> toSeriesDetailsEpisodeChannel(episode, episodesMeta) }

private fun toSeriesDetailsEpisodeChannel(episode: Episode?, episodesMeta: Map<String, JsonObject>): Channel? {
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
    response: SeriesDetailsState,
    imdbMetadataService: ImdbMetadataService
) {
    val resolvedSeriesName = seriesName ?: ""
    val displayName = firstNonBlank(response.seasonInfo["name"], resolvedSeriesName)
    val fuzzyHints = buildSeriesFuzzyHints(displayName, response.seasonInfo, response.episodes)
    val imdbFallback = imdbMetadataService.findBestEffortDetails(
        displayName,
        response.seasonInfo["tmdb"].orEmpty(),
        fuzzyHints
    )
    mergeSeriesMetadata(response.seasonInfo, imdbFallback)
    if (response.episodesMeta.isEmpty()) {
        response.episodesMeta += imdbFallback.jsonArrayField("episodesMeta")
    }
}

private fun copySeriesMetadata(target: MutableMap<String, String>, source: JsonObject) {
    listOf("name", "cover", "plot", "cast", "director", "genre", "releaseDate", "rating", "tmdb", "imdbUrl")
        .forEach { copySeriesMetadataField(target, source, it) }
}

private fun mergeSeriesMetadata(target: MutableMap<String, String>, source: JsonObject?) {
    if (source == null) return
    listOf("name", "cover", "plot", "cast", "director", "genre", "releaseDate", "rating", "tmdb", "imdbUrl")
        .forEach { mergeSeriesMetadataField(target, source, it) }
}

private fun mergeSeriesMetadataField(target: MutableMap<String, String>, source: JsonObject, key: String) {
    val existing = target[key].orEmpty()
    if (StringUtils.isNotBlank(existing)) {
        return
    }
    val incoming = source.stringField(key)
    if (StringUtils.isNotBlank(incoming)) {
        target[key] = incoming
    }
}

private fun copySeriesMetadataField(target: MutableMap<String, String>, source: JsonObject, key: String) {
    val incoming = source.stringField(key)
    if (StringUtils.isNotBlank(incoming)) {
        target[key] = incoming
    }
}

private fun indexSeriesEpisodesMeta(episodesMeta: List<JsonObject>): Map<String, JsonObject> {
    val indexed = HashMap<String, JsonObject>()
    if (episodesMeta.isEmpty()) {
        return indexed
    }
    episodesMeta.forEach { row ->
        val season = safeSeriesNumeric(row.stringField("season"))
        val episode = safeSeriesNumeric(row.stringField("episodeNum"))
        if (StringUtils.isNotBlank(season) && StringUtils.isNotBlank(episode)) {
            indexed["$season:$episode"] = row
        }
        val title = normalizeSeriesText(row.stringField("title"))
        if (StringUtils.isNotBlank(title)) {
            indexed["title:$title"] = row
        }
    }
    return indexed
}

private fun enrichSeriesDetailsEpisode(channel: Channel?, episodesMeta: Map<String, JsonObject>?) {
    if (episodesMeta.isNullOrEmpty() || channel == null) {
        return
    }
    val season = safeSeriesNumeric(channel.season)
    val episode = safeSeriesNumeric(channel.episodeNum)
    var meta: JsonObject? = null
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
        channel.description = meta.stringField("plot")
    }
    if (StringUtils.isBlank(channel.releaseDate)) {
        channel.releaseDate = meta.stringField("releaseDate")
    }
    val logo = meta.stringField("logo")
    if (StringUtils.isNotBlank(logo)) {
        channel.logo = logo
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

private fun applySeriesNameYearFallback(seasonInfo: MutableMap<String, String>, rawSeriesName: String?) {
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
    if (StringUtils.isBlank(seasonInfo["name"]) && StringUtils.isNotBlank(inferredName)) {
        seasonInfo["name"] = inferredName
    }
    if (StringUtils.isBlank(seasonInfo["releaseDate"]) && StringUtils.isNotBlank(inferredYear)) {
        seasonInfo["releaseDate"] = inferredYear
    }
}

private fun enrichEpisodesInSeriesDetails(response: SeriesDetailsState) {
    if (response.episodes.isEmpty() || response.episodesMeta.isEmpty()) {
        return
    }
    val indexed = indexSeriesEpisodesMeta(response.episodesMeta)
    if (indexed.isEmpty()) {
        return
    }
    response.episodes.forEach { channel ->
        enrichSeriesDetailsEpisode(channel, indexed)
    }
}

private fun buildSeriesFuzzyHints(baseTitle: String?, seasonInfo: Map<String, String>?, episodes: List<Channel>?): List<String> {
    val hints = ArrayList<String>()
    addSeriesHint(hints, baseTitle)
    if (seasonInfo != null) {
        addSeriesHint(hints, seasonInfo["name"])
        addSeriesHint(hints, seasonInfo["plot"])
        addSeriesHint(hints, seasonInfo["releaseDate"])
    }
    if (episodes != null) {
        episodes.take(8).forEach { row ->
            addSeriesHint(hints, row.name)
            addSeriesHint(hints, row.releaseDate)
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

private val seriesRouteJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}

private data class SeriesDetailsState(
    val seasonInfo: MutableMap<String, String> = linkedMapOf(),
    val episodes: MutableList<Channel> = mutableListOf(),
    val episodesMeta: MutableList<JsonObject> = mutableListOf()
) {
    fun toDto(): SeriesDetailsResponseDto =
        SeriesDetailsResponseDto(
            seasonInfo = seasonInfo.toJsonObject(),
            episodes = episodes.map(::toChannelRouteDto),
            episodesMeta = episodesMeta.toList()
        )
}

private fun Any?.toJsonObject(): JsonObject =
    when (this) {
        is JsonObject -> this
        null -> JsonObject(emptyMap())
        else -> seriesRouteJson.parseToJsonElement(toString()).jsonObject
    }

private fun JsonObject.stringField(key: String): String =
    this[key]?.jsonPrimitive?.content ?: ""

private fun JsonObject.jsonArrayField(key: String): List<JsonObject> =
    this[key]
        ?.jsonArray
        ?.mapNotNull { it as? JsonObject }
        .orEmpty()

private fun Map<String, String>.toJsonObject(): JsonObject =
    buildJsonObject {
        this@toJsonObject.forEach { (key, value) ->
            put(key, JsonPrimitive(value))
        }
    }
