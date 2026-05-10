package com.uiptv.server.api.routes

import com.uiptv.db.ChannelDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.db.VodChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
import com.uiptv.server.api.dto.ChannelRouteDto
import com.uiptv.server.api.dto.ErrorResponse
import com.uiptv.server.api.dto.StatusResponse
import com.uiptv.server.api.dto.VodDetailsResponseDto
import com.uiptv.server.api.dto.VodInfoDto
import com.uiptv.server.api.dto.WatchingNowSeriesRowDto
import com.uiptv.server.api.dto.WatchingNowSeriesActionRequest
import com.uiptv.server.api.dto.WatchingNowVodRowDto
import com.uiptv.server.api.dto.WatchingNowVodActionRequest
import com.uiptv.service.AccountService
import com.uiptv.service.HandshakeService
import com.uiptv.service.ImdbMetadataService
import com.uiptv.service.SeriesEpisodeService
import com.uiptv.service.SeriesWatchStateService
import com.uiptv.service.SeriesWatchingNowSnapshotService
import com.uiptv.service.VodWatchStateService
import com.uiptv.service.WatchingNowSeriesResolver
import com.uiptv.service.WatchingNowVodResolver
import com.uiptv.shared.Episode
import com.uiptv.shared.EpisodeList
import com.uiptv.util.StringUtils
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receiveText
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import org.json.JSONObject

fun Route.registerVodApiRoutes(
    accountService: AccountService,
    handshakeService: HandshakeService,
    imdbMetadataService: ImdbMetadataService,
    seriesWatchStateService: SeriesWatchStateService,
    seriesEpisodeService: SeriesEpisodeService,
    seriesWatchingNowSnapshotService: SeriesWatchingNowSnapshotService,
    seriesResolver: WatchingNowSeriesResolver,
    vodWatchStateService: VodWatchStateService,
    vodResolver: WatchingNowVodResolver
) {
    get("/vodDetails") {
        call.respond(
            buildVodDetailsResponse(
                accountService.getById(call.request.queryParameters["accountId"]),
                call.request.queryParameters["categoryId"],
                call.request.queryParameters["channelId"],
                call.request.queryParameters["vodName"],
                handshakeService,
                imdbMetadataService
            )
        )
    }

    get("/watchingNow") {
        call.respond(buildWatchingNowSeriesResponse(seriesResolver))
    }

    get("/watchingNowSeriesEpisodes") {
        val response = buildWatchingNowSeriesEpisodesResponse(
            accountService.getById(call.request.queryParameters["accountId"]),
            call.request.queryParameters["categoryId"],
            call.request.queryParameters["seriesId"],
            seriesResolver,
            seriesWatchStateService,
            seriesEpisodeService,
            seriesWatchingNowSnapshotService
        )
        call.respond(response.map(::toChannelRouteDto))
    }

    post("/watchingNowSeriesAction") {
        val result = upsertWatchingNowSeries(
            call.receivePayload<WatchingNowSeriesActionRequest>(),
            accountService,
            seriesWatchStateService,
            seriesWatchingNowSnapshotService
        )
        call.respond(result.status, result.payload)
    }

    delete("/watchingNowSeriesAction") {
        val result = deleteWatchingNowSeries(call.receivePayload<WatchingNowSeriesActionRequest>(), seriesWatchStateService)
        call.respond(result.status, result.payload)
    }

    get("/watchingNowVod") {
        call.respond(buildWatchingNowVodResponse(vodResolver))
    }

    post("/watchingNowVodAction") {
        val result = upsertWatchingNowVod(call.receivePayload<WatchingNowVodActionRequest>(), accountService, vodWatchStateService)
        call.respond(result.status, result.payload)
    }

    delete("/watchingNowVodAction") {
        val result = deleteWatchingNowVod(call.receivePayload<WatchingNowVodActionRequest>(), vodWatchStateService)
        call.respond(result.status, result.payload)
    }
}

@Suppress("USELESS_CAST")
private fun buildVodDetailsResponse(
    account: Account?,
    categoryId: String?,
    channelId: String?,
    vodName: String?,
    handshakeService: HandshakeService,
    imdbMetadataService: ImdbMetadataService
): VodDetailsResponseDto {
    val vodInfo = JSONObject()
    vodInfo.put("name", if (StringUtils.isBlank(vodName)) "VOD" else vodName)
    vodInfo.put("cover", "")
    vodInfo.put("plot", "")
    vodInfo.put("cast", "")
    vodInfo.put("director", "")
    vodInfo.put("genre", "")
    vodInfo.put("releaseDate", "")
    vodInfo.put("rating", "")
    vodInfo.put("tmdb", "")
    vodInfo.put("imdbUrl", "")
    vodInfo.put("duration", "")

    if (account != null && account.isNotConnected()) {
        handshakeService.connect(account)
    }

    var providerChannel: Channel? = null
    if (account != null && !StringUtils.isBlank(channelId)) {
        providerChannel = VodChannelDb.get().getChannelByChannelId(channelId.orEmpty(), categoryId.orEmpty(), account.dbId.orEmpty())
        if (providerChannel == null) {
            providerChannel = ChannelDb.get().getChannelById(channelId.orEmpty(), categoryId.orEmpty())
        }
    }

    if (providerChannel != null) {
        mergeMissing(vodInfo, "name", providerChannel.name)
        mergeMissing(vodInfo, "cover", providerChannel.logo)
        mergeMissing(vodInfo, "plot", providerChannel.description)
        mergeMissing(vodInfo, "releaseDate", providerChannel.releaseDate)
        mergeMissing(vodInfo, "rating", providerChannel.rating)
        mergeMissing(vodInfo, "duration", providerChannel.duration)
    }

    val queryTitle = if (StringUtils.isBlank(vodName)) vodInfo.optString("name", "") else vodName.orEmpty()
    val imdb = (imdbMetadataService.findBestEffortMovieDetails(
        queryTitle,
        vodInfo.optString("tmdb", ""),
        buildVodFuzzyHints(queryTitle, providerChannel, vodInfo)
    ) as? JSONObject) ?: JSONObject()
    mergeMissing(vodInfo, "name", imdb.optString("name", ""))
    mergeMissing(vodInfo, "cover", imdb.optString("cover", ""))
    mergeMissing(vodInfo, "plot", imdb.optString("plot", ""))
    mergeMissing(vodInfo, "cast", imdb.optString("cast", ""))
    mergeMissing(vodInfo, "director", imdb.optString("director", ""))
    mergeMissing(vodInfo, "genre", imdb.optString("genre", ""))
    mergeMissing(vodInfo, "releaseDate", imdb.optString("releaseDate", ""))
    mergeMissing(vodInfo, "rating", imdb.optString("rating", ""))
    mergeMissing(vodInfo, "tmdb", imdb.optString("tmdb", ""))
    mergeMissing(vodInfo, "imdbUrl", imdb.optString("imdbUrl", ""))

    return VodDetailsResponseDto(
        vodInfo = VodInfoDto(
            name = vodInfo.optString("name", ""),
            cover = vodInfo.optString("cover", ""),
            plot = vodInfo.optString("plot", ""),
            cast = vodInfo.optString("cast", ""),
            director = vodInfo.optString("director", ""),
            genre = vodInfo.optString("genre", ""),
            releaseDate = vodInfo.optString("releaseDate", ""),
            rating = vodInfo.optString("rating", ""),
            tmdb = vodInfo.optString("tmdb", ""),
            imdbUrl = vodInfo.optString("imdbUrl", ""),
            duration = vodInfo.optString("duration", "")
        )
    )
}

private fun buildWatchingNowSeriesResponse(resolver: WatchingNowSeriesResolver): List<WatchingNowSeriesRowDto> =
    resolver.resolveAll()
        .map { row ->
            WatchingNowSeriesRowDto(
                key = "${safeRoute(row.account.dbId)}|${safeRoute(row.state.seriesId)}",
                accountId = safeRoute(row.account.dbId),
                accountName = safeRoute(row.account.accountName),
                accountType = safeRoute(row.account.type.name),
                categoryId = safeRoute(row.state.categoryId),
                categoryDbId = safeRoute(row.categoryDbId),
                seriesId = safeRoute(row.state.seriesId),
                episodeId = safeRoute(row.state.episodeId),
                episodeName = safeRoute(row.state.episodeName),
                season = safeRoute(row.state.season),
                episodeNum = row.state.episodeNum.toString(),
                seriesTitle = safeRoute(row.seriesTitle),
                seriesPoster = safeRoute(row.seriesPoster),
                updatedAt = row.state.updatedAt
            )
        }
        .sortedWith(
            compareByDescending<WatchingNowSeriesRowDto> { it.updatedAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.seriesTitle }
        )
        

private fun buildWatchingNowSeriesEpisodesResponse(
    account: Account?,
    categoryId: String?,
    seriesId: String?,
    resolver: WatchingNowSeriesResolver,
    seriesWatchStateService: SeriesWatchStateService,
    seriesEpisodeService: SeriesEpisodeService,
    snapshotService: SeriesWatchingNowSnapshotService
): List<Channel> {
    if (account == null || StringUtils.isBlank(seriesId)) {
        return emptyList()
    }

    var cachedEpisodes = SeriesEpisodeDb.get().getEpisodes(account, categoryId.orEmpty(), seriesId.orEmpty())
    if (cachedEpisodes.isEmpty()) {
        cachedEpisodes = SeriesEpisodeDb.get().getEpisodesFromFreshestCategory(account, seriesId.orEmpty())
    }

    val episodesAsChannels = if (cachedEpisodes.isNotEmpty()) {
        cachedEpisodes
    } else {
        toWatchingNowEpisodeChannels(seriesEpisodeService.getEpisodesForWatchingNow(account, categoryId.orEmpty(), seriesId.orEmpty()) { false })
    }

    applyWatchingNowEpisodeWatchedFlag(episodesAsChannels, account, categoryId.orEmpty(), seriesId.orEmpty(), seriesWatchStateService)
    val metadata = resolveWatchingNowSeriesMetadata(account, categoryId.orEmpty(), seriesId.orEmpty(), resolver)
    snapshotService.saveChannels(
        account,
        categoryId.orEmpty(),
        seriesId.orEmpty(),
        metadata.categoryDbId,
        metadata.seriesTitle,
        metadata.seriesPoster,
        episodesAsChannels
    )
    return episodesAsChannels
}

private fun upsertWatchingNowSeries(
    body: WatchingNowSeriesActionRequest,
    accountService: AccountService,
    seriesWatchStateService: SeriesWatchStateService,
    snapshotService: SeriesWatchingNowSnapshotService
): RouteMutationResult {
    val accountId = body.accountId.orEmpty()
    val categoryId = body.categoryId.orEmpty()
    val seriesId = body.seriesId.orEmpty()
    val episodeId = body.episodeId.orEmpty()
    val episodeName = body.episodeName.orEmpty()
    val season = body.season.orEmpty()
    val episodeNum = body.episodeNum.orEmpty()
    val categoryDbId = body.categoryDbId.orEmpty()
    val seriesTitle = body.seriesTitle.orEmpty()
    val seriesPoster = body.seriesPoster.orEmpty()
    if (accountId.isBlank() || seriesId.isBlank() || episodeId.isBlank()) {
        return RouteMutationResult(
            HttpStatusCode.BadRequest,
            StatusResponse(status = "error", message = "accountId, seriesId, episodeId are required")
        )
    }
    val account = accountService.getById(accountId)
        ?: return RouteMutationResult(
            HttpStatusCode.NotFound,
            ErrorResponse("not_found", "account not found")
        )

    seriesWatchStateService.markSeriesEpisodeManual(account, categoryId, seriesId, episodeId, episodeName, season, episodeNum)
    snapshotService.saveChannels(
        account,
        categoryId,
        seriesId,
        categoryDbId,
        seriesTitle,
        seriesPoster,
        routeChannels(body.episodes)
    )
    return RouteMutationResult(HttpStatusCode.OK, StatusResponse(status = "ok"))
}

private fun deleteWatchingNowSeries(
    body: WatchingNowSeriesActionRequest,
    seriesWatchStateService: SeriesWatchStateService
): RouteMutationResult {
    val accountId = body.accountId.orEmpty()
    val categoryId = body.categoryId.orEmpty()
    val seriesId = body.seriesId.orEmpty()
    if (accountId.isBlank() || seriesId.isBlank()) {
        return RouteMutationResult(
            HttpStatusCode.BadRequest,
            StatusResponse(status = "error", message = "accountId and seriesId are required")
        )
    }
    seriesWatchStateService.clearSeriesLastWatched(accountId, categoryId, seriesId)
    return RouteMutationResult(HttpStatusCode.OK, StatusResponse(status = "ok"))
}

private fun buildWatchingNowVodResponse(vodResolver: WatchingNowVodResolver): List<WatchingNowVodRowDto> =
    vodResolver.resolveAll()
        .map { row ->
            WatchingNowVodRowDto(
                accountId = safeRoute(row.account.dbId),
                accountName = safeRoute(row.account.accountName),
                accountType = safeRoute(row.account.type.name),
                categoryId = safeRoute(row.state.categoryId),
                vodId = safeRoute(row.state.vodId),
                vodName = safeRoute(row.displayTitle),
                vodLogo = safeRoute(row.metadata.logo),
                plot = safeRoute(row.metadata.plot),
                releaseDate = safeRoute(row.metadata.releaseDate),
                rating = safeRoute(row.metadata.rating),
                duration = safeRoute(row.metadata.duration),
                updatedAt = row.state.updatedAt,
                playItem = row.playbackChannel?.let(::toChannelRouteDto)
            )
        }
        .sortedWith(
            compareByDescending<WatchingNowVodRowDto> { it.updatedAt }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.vodName }
        )
        

private fun upsertWatchingNowVod(
    body: WatchingNowVodActionRequest,
    accountService: AccountService,
    vodWatchStateService: VodWatchStateService
): RouteMutationResult {
    val accountId = body.accountId.orEmpty()
    val categoryId = body.categoryId.orEmpty()
    val vodId = body.vodId.orEmpty()
    val vodName = body.vodName.orEmpty()
    val vodCmd = body.vodCmd.orEmpty()
    val vodLogo = body.vodLogo.orEmpty()
    if (accountId.isBlank() || vodId.isBlank()) {
        return RouteMutationResult(
            HttpStatusCode.BadRequest,
            StatusResponse(status = "error", message = "accountId and vodId are required")
        )
    }
    val account = accountService.getById(accountId)
        ?: return RouteMutationResult(
            HttpStatusCode.NotFound,
            ErrorResponse("not_found", "account not found")
        )
    val channel = Channel().apply {
        channelId = vodId
        this.categoryId = categoryId
        name = vodName
        cmd = vodCmd
        logo = vodLogo
    }
    vodWatchStateService.save(account, categoryId, channel)
    return RouteMutationResult(HttpStatusCode.OK, StatusResponse(status = "ok"))
}

private fun deleteWatchingNowVod(
    body: WatchingNowVodActionRequest,
    vodWatchStateService: VodWatchStateService
): RouteMutationResult {
    val accountId = body.accountId.orEmpty()
    val categoryId = body.categoryId.orEmpty()
    val vodId = body.vodId.orEmpty()
    if (accountId.isBlank() || vodId.isBlank()) {
        return RouteMutationResult(
            HttpStatusCode.BadRequest,
            StatusResponse(status = "error", message = "accountId and vodId are required")
        )
    }
    vodWatchStateService.remove(accountId, categoryId, vodId)
    return RouteMutationResult(HttpStatusCode.OK, StatusResponse(status = "ok"))
}

private fun routeChannels(payload: JsonArray?): List<Channel> {
    if (payload == null || payload.isEmpty()) {
        return emptyList()
    }
    val channels = ArrayList<Channel>()
    payload.forEach { element ->
        val raw = element.toString()
        Channel.fromJson(raw)?.let(channels::add)
    }
    return channels
}

private fun toWatchingNowEpisodeChannels(episodes: EpisodeList?): List<Channel> {
    val channels = ArrayList<Channel>()
    episodes?.episodes?.forEach { episode ->
        val channel = Channel().apply {
            channelId = episode.id
            name = episode.title
            cmd = episode.cmd
            extraJson = episode.toJson()
            season = episode.season
            episodeNum = episode.episodeNum
            episode.info?.let { info ->
                logo = info.movieImage
                description = info.plot
                releaseDate = info.releaseDate
                rating = info.rating
                duration = info.duration
            }
        }
        channels += channel
    }
    return channels
}

private fun applyWatchingNowEpisodeWatchedFlag(
    episodes: List<Channel>,
    account: Account,
    categoryId: String,
    seriesId: String,
    seriesWatchStateService: SeriesWatchStateService
) {
    val state: SeriesWatchState? = seriesWatchStateService.getSeriesLastWatched(account.dbId, categoryId, seriesId)
    episodes.forEach { channel ->
        channel.watched = seriesWatchStateService.isMatchingEpisode(
            state,
            channel.channelId,
            channel.season,
            channel.episodeNum,
            channel.name
        )
    }
}

private fun resolveWatchingNowSeriesMetadata(
    account: Account,
    categoryId: String,
    seriesId: String,
    resolver: WatchingNowSeriesResolver
): WatchingNowSeriesMetadata {
    resolver.resolveForAccount(account).forEach { row ->
        val matchingSeries = safeRoute(seriesId) == safeRoute(row.state.seriesId)
        val matchingCategory = StringUtils.isBlank(categoryId) || safeRoute(categoryId) == safeRoute(row.state.categoryId)
        if (matchingSeries && matchingCategory) {
            return WatchingNowSeriesMetadata(row.categoryDbId, row.seriesTitle, row.seriesPoster)
        }
    }
    return WatchingNowSeriesMetadata("", "", "")
}

private fun buildVodFuzzyHints(queryTitle: String, providerChannel: Channel?, vodInfo: JSONObject): List<String> {
    val hints = ArrayList<String>()
    addVodHint(hints, queryTitle)
    providerChannel?.let {
        addVodHint(hints, it.name)
        addVodHint(hints, it.description)
        addVodHint(hints, it.releaseDate)
    }
    addVodHint(hints, vodInfo.optString("name", ""))
    addVodHint(hints, vodInfo.optString("plot", ""))
    addVodHint(hints, vodInfo.optString("releaseDate", ""))
    return hints
}

private fun addVodHint(hints: MutableList<String>, value: String?) {
    if (StringUtils.isBlank(value)) {
        return
    }
    val cleaned = value.orEmpty()
        .replace(Regex("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b"), " ")
        .replace(Regex("[\\[\\]{}()]+"), " ")
        .replace(Regex("\\s+"), " ")
        .trim()
    if (StringUtils.isBlank(cleaned) || cleaned.length < 2 || hints.contains(cleaned)) {
        return
    }
    hints += cleaned
}

private fun mergeMissing(target: JSONObject, key: String, incoming: String?) {
    if (StringUtils.isBlank(target.optString(key, "")) && !StringUtils.isBlank(incoming)) {
        target.put(key, incoming)
    }
}

private fun safeRoute(value: String?): String = value?.trim().orEmpty()

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

private data class RouteMutationResult(
    val status: HttpStatusCode,
    val payload: Any
)

private data class WatchingNowSeriesMetadata(
    val categoryDbId: String,
    val seriesTitle: String,
    val seriesPoster: String
)

private suspend inline fun <reified T> ApplicationCall.receivePayload(): T {
    val text = receiveText()
    if (text.isBlank()) {
        throw IllegalArgumentException("Request body is required")
    }
    return vodRouteJson.decodeFromString(text)
}

private val vodRouteJson = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
}
