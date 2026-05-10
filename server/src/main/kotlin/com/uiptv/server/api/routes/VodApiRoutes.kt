package com.uiptv.server.api.routes

import com.uiptv.db.ChannelDb
import com.uiptv.db.SeriesEpisodeDb
import com.uiptv.db.VodChannelDb
import com.uiptv.model.Account
import com.uiptv.model.Channel
import com.uiptv.model.SeriesWatchState
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
import com.uiptv.util.ServerUtils
import com.uiptv.util.StringUtils
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveText
import io.ktor.server.response.respondText
import io.ktor.server.routing.Route
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import org.json.JSONArray
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
        val response = buildVodDetailsResponse(
            accountService.getById(call.request.queryParameters["accountId"]),
            call.request.queryParameters["categoryId"],
            call.request.queryParameters["channelId"],
            call.request.queryParameters["vodName"],
            handshakeService,
            imdbMetadataService
        )
        call.respondText(response, ContentType.Application.Json)
    }

    get("/watchingNow") {
        call.respondText(buildWatchingNowSeriesResponse(seriesResolver), ContentType.Application.Json)
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
        call.respondText(response, ContentType.Application.Json)
    }

    post("/watchingNowSeriesAction") {
        val body = routeJsonBody(call.receiveText())
        val result = upsertWatchingNowSeries(
            body,
            accountService,
            seriesWatchStateService,
            seriesWatchingNowSnapshotService
        )
        call.respondText(result.body, ContentType.Application.Json, result.status)
    }

    delete("/watchingNowSeriesAction") {
        val body = routeJsonBody(call.receiveText())
        val result = deleteWatchingNowSeries(body, seriesWatchStateService)
        call.respondText(result.body, ContentType.Application.Json, result.status)
    }

    get("/watchingNowVod") {
        call.respondText(buildWatchingNowVodResponse(vodResolver), ContentType.Application.Json)
    }

    post("/watchingNowVodAction") {
        val body = routeJsonBody(call.receiveText())
        val result = upsertWatchingNowVod(body, accountService, vodWatchStateService)
        call.respondText(result.body, ContentType.Application.Json, result.status)
    }

    delete("/watchingNowVodAction") {
        val body = routeJsonBody(call.receiveText())
        val result = deleteWatchingNowVod(body, vodWatchStateService)
        call.respondText(result.body, ContentType.Application.Json, result.status)
    }
}

private fun buildVodDetailsResponse(
    account: Account?,
    categoryId: String?,
    channelId: String?,
    vodName: String?,
    handshakeService: HandshakeService,
    imdbMetadataService: ImdbMetadataService
): String {
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
    val imdb = imdbMetadataService.findBestEffortMovieDetails(
        queryTitle,
        vodInfo.optString("tmdb", ""),
        buildVodFuzzyHints(queryTitle, providerChannel, vodInfo)
    ) ?: JSONObject()
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

    return JSONObject().put("vodInfo", vodInfo).toString()
}

private fun buildWatchingNowSeriesResponse(resolver: WatchingNowSeriesResolver): String {
    val payload = JSONArray()
    resolver.resolveAll()
        .map { row ->
            JSONObject()
                .put("key", "${safeRoute(row.account.dbId)}|${safeRoute(row.state.seriesId)}")
                .put("accountId", safeRoute(row.account.dbId))
                .put("accountName", safeRoute(row.account.accountName))
                .put("accountType", safeRoute(row.account.type?.name ?: ""))
                .put("categoryId", safeRoute(row.state.categoryId))
                .put("categoryDbId", safeRoute(row.categoryDbId))
                .put("seriesId", safeRoute(row.state.seriesId))
                .put("episodeId", safeRoute(row.state.episodeId))
                .put("episodeName", safeRoute(row.state.episodeName))
                .put("season", safeRoute(row.state.season))
                .put("episodeNum", row.state.episodeNum)
                .put("seriesTitle", safeRoute(row.seriesTitle))
                .put("seriesPoster", safeRoute(row.seriesPoster))
                .put("updatedAt", row.state.updatedAt)
        }
        .sortedWith(
            compareByDescending<JSONObject> { it.optLong("updatedAt", 0L) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.optString("seriesTitle", "") }
        )
        .forEach(payload::put)
    return payload.toString()
}

private fun buildWatchingNowSeriesEpisodesResponse(
    account: Account?,
    categoryId: String?,
    seriesId: String?,
    resolver: WatchingNowSeriesResolver,
    seriesWatchStateService: SeriesWatchStateService,
    seriesEpisodeService: SeriesEpisodeService,
    snapshotService: SeriesWatchingNowSnapshotService
): String {
    if (account == null || StringUtils.isBlank(seriesId)) {
        return "[]"
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
    return ServerUtils.objectToJson(episodesAsChannels)
}

private fun upsertWatchingNowSeries(
    body: JSONObject,
    accountService: AccountService,
    seriesWatchStateService: SeriesWatchStateService,
    snapshotService: SeriesWatchingNowSnapshotService
): RouteMutationResult {
    val accountId = routeOpt(body, "accountId")
    val categoryId = routeOpt(body, "categoryId")
    val seriesId = routeOpt(body, "seriesId")
    val episodeId = routeOpt(body, "episodeId")
    val episodeName = routeOpt(body, "episodeName")
    val season = routeOpt(body, "season")
    val episodeNum = routeOpt(body, "episodeNum")
    val categoryDbId = routeOpt(body, "categoryDbId")
    val seriesTitle = routeOpt(body, "seriesTitle")
    val seriesPoster = routeOpt(body, "seriesPoster")
    if (accountId.isBlank() || seriesId.isBlank() || episodeId.isBlank()) {
        return RouteMutationResult(HttpStatusCode.BadRequest, """{"status":"error","message":"accountId, seriesId, episodeId are required"}""")
    }
    val account = accountService.getById(accountId)
        ?: return RouteMutationResult(HttpStatusCode.NotFound, """{"status":"error","message":"account not found"}""")

    seriesWatchStateService.markSeriesEpisodeManual(account, categoryId, seriesId, episodeId, episodeName, season, episodeNum)
    snapshotService.saveChannels(
        account,
        categoryId,
        seriesId,
        categoryDbId,
        seriesTitle,
        seriesPoster,
        routeChannels(body.optJSONArray("episodes"))
    )
    return RouteMutationResult(HttpStatusCode.OK, """{"status":"ok"}""")
}

private fun deleteWatchingNowSeries(
    body: JSONObject,
    seriesWatchStateService: SeriesWatchStateService
): RouteMutationResult {
    val accountId = routeOpt(body, "accountId")
    val categoryId = routeOpt(body, "categoryId")
    val seriesId = routeOpt(body, "seriesId")
    if (accountId.isBlank() || seriesId.isBlank()) {
        return RouteMutationResult(HttpStatusCode.BadRequest, """{"status":"error","message":"accountId and seriesId are required"}""")
    }
    seriesWatchStateService.clearSeriesLastWatched(accountId, categoryId, seriesId)
    return RouteMutationResult(HttpStatusCode.OK, """{"status":"ok"}""")
}

private fun buildWatchingNowVodResponse(vodResolver: WatchingNowVodResolver): String {
    val payload = JSONArray()
    vodResolver.resolveAll()
        .map { row ->
            val item = JSONObject()
                .put("accountId", safeRoute(row.account.dbId))
                .put("accountName", safeRoute(row.account.accountName))
                .put("accountType", safeRoute(row.account.type?.name ?: ""))
                .put("categoryId", safeRoute(row.state.categoryId))
                .put("vodId", safeRoute(row.state.vodId))
                .put("vodName", safeRoute(row.displayTitle))
                .put("vodLogo", safeRoute(row.metadata.logo))
                .put("plot", safeRoute(row.metadata.plot))
                .put("releaseDate", safeRoute(row.metadata.releaseDate))
                .put("rating", safeRoute(row.metadata.rating))
                .put("duration", safeRoute(row.metadata.duration))
                .put("updatedAt", row.state.updatedAt)
            row.playbackChannel?.let { item.put("playItem", JSONObject(it.toJson())) }
            item
        }
        .sortedWith(
            compareByDescending<JSONObject> { it.optLong("updatedAt", 0L) }
                .thenBy(String.CASE_INSENSITIVE_ORDER) { it.optString("vodName", "") }
        )
        .forEach(payload::put)
    return payload.toString()
}

private fun upsertWatchingNowVod(
    body: JSONObject,
    accountService: AccountService,
    vodWatchStateService: VodWatchStateService
): RouteMutationResult {
    val accountId = routeOpt(body, "accountId")
    val categoryId = routeOpt(body, "categoryId")
    val vodId = routeOpt(body, "vodId")
    val vodName = routeOpt(body, "vodName")
    val vodCmd = routeOpt(body, "vodCmd")
    val vodLogo = routeOpt(body, "vodLogo")
    if (accountId.isBlank() || vodId.isBlank()) {
        return RouteMutationResult(HttpStatusCode.BadRequest, """{"status":"error","message":"accountId and vodId are required"}""")
    }
    val account = accountService.getById(accountId)
        ?: return RouteMutationResult(HttpStatusCode.NotFound, """{"status":"error","message":"account not found"}""")
    val channel = Channel().apply {
        channelId = vodId
        this.categoryId = categoryId
        name = vodName
        cmd = vodCmd
        logo = vodLogo
    }
    vodWatchStateService.save(account, categoryId, channel)
    return RouteMutationResult(HttpStatusCode.OK, """{"status":"ok"}""")
}

private fun deleteWatchingNowVod(
    body: JSONObject,
    vodWatchStateService: VodWatchStateService
): RouteMutationResult {
    val accountId = routeOpt(body, "accountId")
    val categoryId = routeOpt(body, "categoryId")
    val vodId = routeOpt(body, "vodId")
    if (accountId.isBlank() || vodId.isBlank()) {
        return RouteMutationResult(HttpStatusCode.BadRequest, """{"status":"error","message":"accountId and vodId are required"}""")
    }
    vodWatchStateService.remove(accountId, categoryId, vodId)
    return RouteMutationResult(HttpStatusCode.OK, """{"status":"ok"}""")
}

private fun routeJsonBody(text: String): JSONObject =
    try {
        if (text.isBlank()) JSONObject() else JSONObject(text)
    } catch (_: Exception) {
        JSONObject()
    }

private fun routeOpt(body: JSONObject?, key: String): String =
    if (body == null || !body.has(key) || body.isNull(key)) "" else body.opt(key).toString().trim()

private fun routeChannels(payload: JSONArray?): List<Channel> {
    if (payload == null || payload.isEmpty) {
        return emptyList()
    }
    val channels = ArrayList<Channel>()
    for (index in 0 until payload.length()) {
        val raw = payload.opt(index)?.toString() ?: continue
        Channel.fromJson(raw)?.let(channels::add)
    }
    return channels
}

private fun toWatchingNowEpisodeChannels(episodes: EpisodeList?): List<Channel> {
    val channels = ArrayList<Channel>()
    episodes?.episodes?.forEach { episode ->
        if (episode != null) {
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

private data class RouteMutationResult(
    val status: HttpStatusCode,
    val body: String
)

private data class WatchingNowSeriesMetadata(
    val categoryDbId: String,
    val seriesTitle: String,
    val seriesPoster: String
)
