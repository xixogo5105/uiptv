package com.uiptv.service

import com.uiptv.model.Configuration
import com.uiptv.util.HttpUtil
import com.uiptv.util.I18n
import com.uiptv.util.StringUtils.isBlank
import com.uiptv.util.StringUtils.isNotBlank
import com.uiptv.util.StringUtils.safeGetString
import com.uiptv.util.json.KJsonArray
import com.uiptv.util.json.KJsonObject
import com.uiptv.util.json.optArray
import com.uiptv.util.json.optObject
import com.uiptv.util.json.optString
import com.uiptv.util.json.parseJsonArray
import com.uiptv.util.json.parseJsonObject
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.net.URLEncoder
import java.nio.charset.StandardCharsets
import java.util.ArrayList
import java.util.HashMap
import java.util.LinkedHashSet
import java.util.Locale
import java.util.regex.Pattern

object ImdbMetadataService {
    private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
    private const val IMDB_TITLE_URL_PREFIX = "https://www.imdb.com/title/"
    private const val KEY_COVER = "cover"
    private const val KEY_DIRECTOR = "director"
    private const val KEY_GENRE = "genre"
    private const val KEY_IMDB_URL = "imdbUrl"
    private const val KEY_CAST = "cast"
    private const val KEY_LOGO = "logo"
    private const val KEY_NAME = "name"
    private const val KEY_PLOT = "plot"
    private const val KEY_RATING = "rating"
    private const val KEY_RELEASE_DATE = "releaseDate"
    private const val KEY_EPISODES_META = "episodesMeta"
    private const val KEY_TITLE = "title"
    private const val KEY_OVERVIEW = "overview"
    private const val KEY_SEASON = "season"
    private const val KEY_EPISODE_NUMBER = "episodeNum"
    private const val KEY_TMDB_MEDIA_ID = "tmdbMediaId"
    private const val HEADER_ACCEPT_LANGUAGE = "Accept-Language"
    private const val HEADER_USER_AGENT = "User-Agent"
    private const val JSON_SUFFIX = ".json"
    private const val USER_AGENT_BROWSER = "Mozilla/5.0"

    private class CandidateMatch(
        val candidate: JsonObject,
        val score: Int
    )

    private class TvMazeEpisodeIndex {
        val bySeasonEpisode: MutableMap<String, KJsonObject> = HashMap()
        val byTitle: MutableMap<String, KJsonObject> = HashMap()
    }

    @JvmStatic
    fun getInstance(): ImdbMetadataService = this
    fun findBestEffortDetails(rawTitle: String?, preferredImdbId: String?): JsonObject {
        if (!areThumbnailsEnabled()) {
            return buildJsonObject { }
        }
        return toJsonObject(findBestEffortInternal(rawTitle, preferredImdbId, false, emptyList()))
    }
    fun findBestEffortDetails(rawTitle: String?, preferredImdbId: String?, fuzzyHints: List<String>?): JsonObject {
        if (!areThumbnailsEnabled()) {
            return buildJsonObject { }
        }
        return toJsonObject(findBestEffortInternal(rawTitle, preferredImdbId, false, fuzzyHints ?: emptyList()))
    }
    fun findBestEffortMovieDetails(rawTitle: String?, preferredImdbId: String?): JsonObject {
        if (!areThumbnailsEnabled()) {
            return buildJsonObject { }
        }
        return toJsonObject(findBestEffortInternal(rawTitle, preferredImdbId, true, emptyList()))
    }
    fun findBestEffortMovieDetails(rawTitle: String?, preferredImdbId: String?, fuzzyHints: List<String>?): JsonObject {
        if (!areThumbnailsEnabled()) {
            return buildJsonObject { }
        }
        return toJsonObject(findBestEffortInternal(rawTitle, preferredImdbId, true, fuzzyHints ?: emptyList()))
    }

    private fun areThumbnailsEnabled(): Boolean =
        try {
            val configuration: Configuration? = ConfigurationService.read()
            configuration != null && configuration.enableThumbnails
        } catch (_: Exception) {
            false
        }

    private fun findBestEffortInternal(
        rawTitle: String?,
        preferredImdbId: String?,
        moviePreferred: Boolean,
        fuzzyHints: List<String>
    ): KJsonObject {
        val details = KJsonObject()
        if (isBlank(rawTitle) && isBlank(preferredImdbId)) {
            return details
        }

        val searchQueries = buildSearchQueries(rawTitle, fuzzyHints)
        val candidate = searchBestCandidate(searchQueries)
        val imdbId = resolvePreferredImdbId(preferredImdbId, candidate, searchQueries)
        if (isBlank(imdbId)) {
            return details
        }

        initializeImdbDetails(details, imdbId)
        mergeSuggestedMetadata(details, candidate)
        mergePageMetadata(details, imdbId)
        val primaryMeta = if (moviePreferred) fetchCinemetaMovieDetails(imdbId) else fetchCinemetaSeriesDetails(imdbId)
        val secondaryMeta = if (moviePreferred) fetchCinemetaSeriesDetails(imdbId) else fetchCinemetaMovieDetails(imdbId)
        mergeCinemetaMetadata(details, primaryMeta, true)
        mergeCinemetaMetadata(details, secondaryMeta, false)
        applyTmdbLocalization(details, primaryMeta, secondaryMeta, moviePreferred)
        return details
    }

    private fun initializeImdbDetails(details: KJsonObject, imdbId: String) {
        details.put("tmdb", imdbId)
        details.put(KEY_IMDB_URL, "$IMDB_TITLE_URL_PREFIX$imdbId/")
    }

    private fun mergeSuggestedMetadata(details: KJsonObject, candidate: JsonObject) {
        mergeIfPresent(details, safeGetString(candidate, KEY_NAME), KEY_NAME)
        mergeIfPresent(details, safeGetString(candidate, KEY_COVER), KEY_COVER)
        mergeIfPresent(details, safeGetString(candidate, KEY_CAST), KEY_CAST)
        mergeIfPresent(details, safeGetString(candidate, KEY_GENRE), KEY_GENRE)
        mergeIfPresent(details, safeGetString(candidate, KEY_RELEASE_DATE), KEY_RELEASE_DATE)
    }

    private fun mergePageMetadata(details: KJsonObject, imdbId: String) {
        val pageDetails = fetchImdbTitleDetails(imdbId)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_NAME), KEY_NAME)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_COVER), KEY_COVER)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_PLOT), KEY_PLOT)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_CAST), KEY_CAST)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_DIRECTOR), KEY_DIRECTOR)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_GENRE), KEY_GENRE)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_RELEASE_DATE), KEY_RELEASE_DATE)
        mergeIfPresent(details, safeGetString(pageDetails, KEY_RATING), KEY_RATING)
    }

    private fun mergeCinemetaMetadata(details: KJsonObject, meta: KJsonObject, copyEpisodes: Boolean) {
        mergeMissing(details, meta, KEY_NAME)
        mergeMissing(details, meta, KEY_COVER)
        mergeMissing(details, meta, KEY_PLOT)
        mergeMissing(details, meta, KEY_CAST)
        mergeMissing(details, meta, KEY_DIRECTOR)
        mergeMissing(details, meta, KEY_GENRE)
        mergeMissing(details, meta, KEY_RELEASE_DATE)
        mergeMissing(details, meta, KEY_RATING)
        mergeMissing(details, meta, KEY_IMDB_URL)
        if (meta.has(KEY_EPISODES_META) && (copyEpisodes || !details.has(KEY_EPISODES_META))) {
            details.put(KEY_EPISODES_META, meta.optJSONArray(KEY_EPISODES_META))
        }
    }

    private fun resolvePreferredImdbId(preferredImdbId: String?, candidate: JsonObject, searchQueries: List<String>): String {
        if (isLikelyImdbId(preferredImdbId) && isPreferredIdConsistent(preferredImdbId.orEmpty(), searchQueries)) {
            return preferredImdbId.orEmpty()
        }
        val candidateId = candidate.optString("tmdb", "")
        if (isLikelyImdbId(candidateId)) {
            return candidateId
        }
        return if (isLikelyImdbId(preferredImdbId)) preferredImdbId.orEmpty() else ""
    }

    private fun isLikelyImdbId(id: String?): Boolean = !isBlank(id) && Regex("tt\\d+").matches(id.orEmpty())

    private fun searchBestCandidate(queryTitles: List<String>?): JsonObject {
        val best = buildJsonObject { }
        return try {
            if (queryTitles.isNullOrEmpty()) return best
            val primary = normalizeTitle(queryTitles.first())
            if (isBlank(primary)) return best

            var bestScore = Int.MIN_VALUE
            var resolved = best
            for (query in queryTitles) {
                val match = findCandidateMatch(primary, query)
                if (match != null && match.score > bestScore) {
                    bestScore = match.score
                    resolved = match.candidate
                }
            }
            resolved
        } catch (_: Exception) {
            best
        }
    }

    private fun findCandidateMatch(primary: String, query: String?): CandidateMatch? {
        val normalizedQuery = normalizeTitle(query)
        if (isBlank(normalizedQuery)) {
            return null
        }

        val found = findBestInSuggestions(querySuggestions(normalizedQuery), normalizedQuery)
        if (isBlank(found.optString("tmdb", ""))) {
            return null
        }

        var score = scoreCandidate(primary, found.optString("name", ""), found.optString(KEY_GENRE, ""))
        if (normalizedQuery == primary) {
            score += 5
        }
        return CandidateMatch(found, score)
    }

    private fun querySuggestions(queryTitle: String?): JsonArray? =
        try {
            if (isBlank(queryTitle)) return null
            var first = queryTitle.orEmpty().first().lowercaseChar().toString()
            if (!Regex("[a-z0-9]").matches(first)) {
                first = "x"
            }
            val url = "https://v2.sg.media-imdb.com/suggestion/$first/" +
                URLEncoder.encode(queryTitle, StandardCharsets.UTF_8) + JSON_SUFFIX
            val body = httpGet(url)
            if (isBlank(body)) return null
            parseJsonObject(body)?.optArray("d")
        } catch (_: Exception) {
            null
        }

    private fun findBestInSuggestions(d: JsonArray?, desiredTitle: String): JsonObject {
        var best = buildJsonObject { }
        if (d == null || d.isEmpty()) {
            return best
        }

        var bestScore = Int.MIN_VALUE
        for (index in d.indices) {
            val candidate = d.optObject(index) ?: continue
            val id = candidate.optString("id", "")
            val name = candidate.optString("l", "")
            val type = candidate.optString("q", "")
            if (isBlank(id) || isBlank(name)) {
                continue
            }
            val score = scoreCandidate(desiredTitle, name, type)
            if (score > bestScore) {
                bestScore = score
                best = mapSuggestionCandidate(candidate)
            }
        }
        return best
    }

    private fun mapSuggestionCandidate(candidate: JsonObject): JsonObject =
        buildJsonObject {
            put("tmdb", candidate.optString("id", ""))
            put("name", candidate.optString("l", ""))
            put(KEY_GENRE, candidate.optString("q", ""))
            put(KEY_RELEASE_DATE, candidate.optString("y", ""))
            put(KEY_CAST, candidate.optString("s", ""))
            val image = candidate.optObject("i")
            if (image != null) {
                put(KEY_COVER, image.optString("imageUrl", ""))
            }
        }

    private fun fetchImdbTitleDetails(imdbId: String): JsonObject {
        try {
            val html = httpGet(withLanguageQuery("$IMDB_TITLE_URL_PREFIX$imdbId/"))
            if (isBlank(html)) return buildJsonObject { }

            val jsonLd = extractJsonLd(html)
            if (isBlank(jsonLd)) return buildJsonObject { }

            val data = parseJsonObject(jsonLd) ?: return buildJsonObject { }
            return buildJsonObject {
                put(KEY_NAME, data.optString(KEY_NAME, ""))
                put(KEY_COVER, data.optString("image", ""))
                put(KEY_PLOT, data.optString("description", ""))
                put(KEY_RELEASE_DATE, data.optString("datePublished", ""))
                data.optObject("aggregateRating")
                    ?.optString("ratingValue", "")
                    ?.takeIf(::isNotBlank)
                    ?.let { put(KEY_RATING, it) }
                imdbGenreValue(data[KEY_GENRE])?.let { put(KEY_GENRE, it) }
                joinPersonNames(data.optArray("actor"))?.takeIf(::isNotBlank)?.let { put(KEY_CAST, it) }
                joinPersonNames(data.optArray(KEY_DIRECTOR))?.takeIf(::isNotBlank)?.let { put(KEY_DIRECTOR, it) }
            }
        } catch (_: Exception) {
            return buildJsonObject { }
        }
    }

    private fun fetchCinemetaSeriesDetails(imdbId: String): KJsonObject {
        val result = KJsonObject()
        try {
            val json = httpGet("https://v3-cinemeta.strem.io/meta/series/$imdbId$JSON_SUFFIX")
            if (isBlank(json)) {
                return result
            }
            val meta = KJsonObject(json).optJSONObject("meta") ?: return result

            applyCinemetaMeta(result, meta)

            val videos = meta.optJSONArray("videos")
            if (videos != null && videos.length() > 0) {
                val episodesMeta = KJsonArray()
                for (index in 0 until videos.length()) {
                    val video = videos.optJSONObject(index) ?: continue
                    val episode = KJsonObject()
                    episode.put(KEY_TITLE, sanitizeEpisodeTitle(video.optString(KEY_TITLE, "")))
                    episode.put(KEY_PLOT, video.optString(KEY_OVERVIEW, ""))
                    episode.put(KEY_LOGO, video.optString("thumbnail", ""))
                    episode.put(KEY_RELEASE_DATE, video.optString("released", ""))
                    episode.put(KEY_SEASON, video.optInt(KEY_SEASON, 0).toString())
                    episode.put(KEY_EPISODE_NUMBER, video.optInt("episode", 0).toString())
                    episodesMeta.put(episode)
                }
                enrichEpisodeMetaWithTvMaze(episodesMeta, imdbId, meta.optString("name", ""))
                result.put(KEY_EPISODES_META, episodesMeta)
            }
        } catch (_: Exception) {
        }
        return result
    }

    private fun enrichEpisodeMetaWithTvMaze(episodesMeta: KJsonArray?, imdbId: String, seriesName: String) {
        if (episodesMeta == null || episodesMeta.length() == 0 || isBlank(imdbId)) {
            return
        }
        if (hasAnyEpisodePlot(episodesMeta)) {
            return
        }

        val tvMazeEpisodes = fetchTvMazeEpisodes(imdbId, seriesName)
        if (tvMazeEpisodes.length() == 0) {
            return
        }

        val index = buildTvMazeEpisodeIndex(tvMazeEpisodes)
        for (rowIndex in 0 until episodesMeta.length()) {
            val row = episodesMeta.optJSONObject(rowIndex) ?: continue
            if (isNotBlank(row.optString(KEY_PLOT, ""))) {
                continue
            }
            val match = matchTvMazeEpisode(index, row)
            if (match != null) {
                applyTvMazeEpisodeMeta(row, match)
            }
        }
    }

    private fun hasAnyEpisodePlot(episodesMeta: KJsonArray?): Boolean {
        if (episodesMeta == null || episodesMeta.length() == 0) {
            return false
        }
        for (index in 0 until episodesMeta.length()) {
            val row = episodesMeta.optJSONObject(index) ?: continue
            if (isNotBlank(row.optString(KEY_PLOT, ""))) {
                return true
            }
        }
        return false
    }

    private fun fetchTvMazeEpisodes(imdbId: String, seriesName: String): KJsonArray =
        try {
            val showId = resolveTvMazeShowId(imdbId, seriesName)
            if (showId <= 0) {
                return KJsonArray()
            }
            val body = httpGet("https://api.tvmaze.com/shows/$showId/episodes")
            if (isBlank(body)) {
                return KJsonArray()
            }
            KJsonArray(body)
        } catch (_: Exception) {
            KJsonArray()
        }

    private fun resolveTvMazeShowId(imdbId: String, seriesName: String): Int {
        if (isBlank(seriesName)) {
            return -1
        }
        return try {
            val body = httpGet("https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(seriesName, StandardCharsets.UTF_8))
            if (isBlank(body)) {
                return -1
            }
            val rows = KJsonArray(body)
            if (rows.length() == 0) {
                return -1
            }

            val exactImdbMatch = findTvMazeShowIdByImdb(rows, imdbId)
            if (exactImdbMatch > 0) {
                return exactImdbMatch
            }
            findBestTvMazeShowId(rows, seriesName)
        } catch (_: Exception) {
            -1
        }
    }

    private fun safeNumeric(value: String?): String {
        if (isBlank(value)) {
            return ""
        }
        val normalized = value.orEmpty().replace(Regex("\\D"), "")
        return if (isBlank(normalized)) "" else normalized.toInt().toString()
    }

    private fun stripHtml(value: String?): String {
        if (isBlank(value)) {
            return ""
        }
        return value.orEmpty()
            .replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun fetchCinemetaMovieDetails(imdbId: String): KJsonObject {
        val result = KJsonObject()
        try {
            val json = httpGet("https://v3-cinemeta.strem.io/meta/movie/$imdbId$JSON_SUFFIX")
            if (isBlank(json)) {
                return result
            }
            val meta = KJsonObject(json).optJSONObject("meta") ?: return result
            applyCinemetaMeta(result, meta)
        } catch (_: Exception) {
        }
        return result
    }

    private fun applyCinemetaMeta(result: KJsonObject, meta: KJsonObject) {
        result.put(KEY_NAME, meta.optString(KEY_NAME, ""))
        result.put(KEY_COVER, meta.optString("poster", ""))
        result.put(KEY_PLOT, meta.optString("description", ""))
        result.put(
            KEY_IMDB_URL,
            if (isNotBlank(meta.optString("imdb_id", ""))) "$IMDB_TITLE_URL_PREFIX${meta.optString("imdb_id", "")}/" else ""
        )
        if (meta.has("moviedb_id")) {
            result.put(KEY_TMDB_MEDIA_ID, meta.opt("moviedb_id").toString())
        }

        val genres = meta.optJSONArray("genres")
        if (genres != null) {
            result.put(KEY_GENRE, joinStringArray(genres, 6))
        } else {
            result.put(KEY_GENRE, meta.optString(KEY_GENRE, ""))
        }

        val cast = meta.optJSONArray(KEY_CAST)
        if (cast != null) {
            result.put(KEY_CAST, joinStringArray(cast, 8))
        } else {
            result.put(KEY_CAST, meta.optString(KEY_CAST, ""))
        }

        val director = meta.optJSONArray(KEY_DIRECTOR)
        if (director != null) {
            result.put(KEY_DIRECTOR, joinStringArray(director, 4))
        } else {
            result.put(KEY_DIRECTOR, meta.optString(KEY_DIRECTOR, ""))
        }

        val releaseInfo = meta.optString("releaseInfo", "")
        val released = meta.optString("released", "")
        if (isNotBlank(releaseInfo)) {
            result.put(KEY_RELEASE_DATE, releaseInfo)
        } else if (isNotBlank(released)) {
            result.put(KEY_RELEASE_DATE, released.substring(0, minOf(10, released.length)))
        }
        result.put(KEY_RATING, meta.optString("imdbRating", ""))
    }

    private fun applyTmdbLocalization(details: KJsonObject, primaryMeta: KJsonObject, secondaryMeta: KJsonObject, moviePreferred: Boolean) {
        val localeTag = I18n.getCurrentLanguageTag()
        if (isBlank(localeTag) || localeTag.lowercase(Locale.ROOT).startsWith("en")) {
            return
        }

        val tmdbId = resolveTmdbMediaId(primaryMeta, secondaryMeta)
        if (isBlank(tmdbId)) {
            return
        }

        val primaryLocalized = fetchPrimaryLocalizedTmdbDetails(tmdbId, localeTag, moviePreferred)
        val secondaryLocalized = fetchSecondaryLocalizedTmdbDetails(tmdbId, localeTag, moviePreferred)
        val localized = if (primaryLocalized.length() > 0) primaryLocalized else secondaryLocalized
        if (localized.length() == 0) {
            return
        }

        applyLocalizedTmdbFields(details, localized)
        if (!moviePreferred) {
            enrichEpisodesMetaWithTmdb(details.optJSONArray(KEY_EPISODES_META), tmdbId, localeTag)
        }
    }

    private fun buildTvMazeEpisodeIndex(tvMazeEpisodes: KJsonArray): TvMazeEpisodeIndex {
        val index = TvMazeEpisodeIndex()
        for (rowIndex in 0 until tvMazeEpisodes.length()) {
            val row = tvMazeEpisodes.optJSONObject(rowIndex) ?: continue
            indexSeasonEpisode(index, row)
            indexTitle(index.byTitle, normalizeTitle(row.optString("name", "")), row)
        }
        return index
    }

    private fun indexSeasonEpisode(index: TvMazeEpisodeIndex, row: KJsonObject) {
        val season = safeNumeric(row.optInt(KEY_SEASON, 0).toString())
        val episode = safeNumeric(row.optInt("number", 0).toString())
        if (isNotBlank(season) && isNotBlank(episode)) {
            index.bySeasonEpisode["$season:$episode"] = row
        }
    }

    private fun indexTitle(byTitle: MutableMap<String, KJsonObject>, title: String, row: KJsonObject) {
        if (isNotBlank(title)) {
            byTitle[title] = row
        }
    }

    private fun matchTvMazeEpisode(index: TvMazeEpisodeIndex, row: KJsonObject): KJsonObject? {
        val season = safeNumeric(row.optString(KEY_SEASON, ""))
        val episode = safeNumeric(row.optString(KEY_EPISODE_NUMBER, ""))
        if (isNotBlank(season) && isNotBlank(episode)) {
            val match = index.bySeasonEpisode["$season:$episode"]
            if (match != null) {
                return match
            }
        }
        return index.byTitle[normalizeTitle(row.optString(KEY_TITLE, ""))]
    }

    private fun applyTvMazeEpisodeMeta(row: KJsonObject, match: KJsonObject) {
        val summary = stripHtml(match.optString("summary", ""))
        if (isNotBlank(summary)) {
            row.put(KEY_PLOT, summary)
        }
        if (isBlank(row.optString(KEY_RELEASE_DATE, ""))) {
            row.put(KEY_RELEASE_DATE, match.optString("airdate", ""))
        }
    }

    private fun findTvMazeShowIdByImdb(rows: KJsonArray, imdbId: String): Int {
        for (index in 0 until rows.length()) {
            val show = extractTvMazeShow(rows.optJSONObject(index))
            val externals = show?.optJSONObject("externals")
            if (show != null && externals != null && imdbId.equals(externals.optString("imdb", ""), ignoreCase = true)) {
                return show.optInt("id", -1)
            }
        }
        return -1
    }

    private fun findBestTvMazeShowId(rows: KJsonArray, seriesName: String): Int {
        val normalizedSeries = normalizeTitle(seriesName)
        var bestId = -1
        var bestScore = Int.MIN_VALUE
        for (index in 0 until rows.length()) {
            val show = extractTvMazeShow(rows.optJSONObject(index)) ?: continue
            val score = scoreCandidate(normalizedSeries, show.optString("name", ""), show.optString("type", ""))
            if (score > bestScore) {
                bestScore = score
                bestId = show.optInt("id", -1)
            }
        }
        return bestId
    }

    private fun extractTvMazeShow(wrapper: KJsonObject?): KJsonObject? = wrapper?.optJSONObject("show")

    private fun resolveTmdbMediaId(primaryMeta: KJsonObject, secondaryMeta: KJsonObject): String =
        firstNonBlank(
            primaryMeta.optString(KEY_TMDB_MEDIA_ID, ""),
            secondaryMeta.optString(KEY_TMDB_MEDIA_ID, "")
        )

    private fun fetchPrimaryLocalizedTmdbDetails(tmdbId: String, localeTag: String, moviePreferred: Boolean): KJsonObject =
        fetchTmdbLocalizedDetails(tmdbId, if (moviePreferred) "movie" else "tv", localeTag)

    private fun fetchSecondaryLocalizedTmdbDetails(tmdbId: String, localeTag: String, moviePreferred: Boolean): KJsonObject =
        fetchTmdbLocalizedDetails(tmdbId, if (moviePreferred) "tv" else "movie", localeTag)

    private fun applyLocalizedTmdbFields(details: KJsonObject, localized: KJsonObject) {
        replaceIfPresent(details, localized, KEY_NAME)
        replaceIfPresent(details, localized, KEY_PLOT)
        replaceIfPresent(details, localized, KEY_GENRE)
        replaceIfPresent(details, localized, KEY_RELEASE_DATE)
        mergeMissing(details, localized, KEY_COVER)
        mergeMissing(details, localized, KEY_RATING)
    }

    private fun fetchTmdbLocalizedDetails(tmdbId: String, mediaType: String, localeTag: String): KJsonObject {
        val result = KJsonObject()
        if (!canFetchTmdbLocalizedDetails(tmdbId, mediaType)) {
            return result
        }
        val bearerToken = resolveConfiguredTmdbBearerToken()
        if (isBlank(bearerToken)) {
            return result
        }

        try {
            val response = HttpUtil.sendRequest(
                buildTmdbLocalizedUrl(tmdbId, mediaType, localeTag),
                buildTmdbHeaders(bearerToken),
                "GET"
            )
            if (!isSuccessfulTmdbResponse(response)) {
                return result
            }

            val payload = parseJsonObject(response.body)?.let { KJsonObject(it.toString()) } ?: return result
            populateTmdbLocalizedDetails(result, payload)
        } catch (_: Exception) {
        }
        return result
    }

    private fun enrichEpisodesMetaWithTmdb(episodesMeta: KJsonArray?, tmdbId: String, localeTag: String) {
        if (episodesMeta == null || episodesMeta.length() == 0 || isBlank(tmdbId) || isBlank(localeTag)) {
            return
        }

        val bySeasonEpisode = indexEpisodesBySeasonEpisode(episodesMeta)
        for (season in collectTmdbSeasons(bySeasonEpisode)) {
            val localizedEpisodes = fetchTmdbSeasonEpisodes(tmdbId, season, localeTag)
            for (index in 0 until localizedEpisodes.length()) {
                mergeLocalizedTmdbEpisode(bySeasonEpisode, season, localizedEpisodes.optJSONObject(index))
            }
        }
    }

    private fun applyImdbGenre(result: KJsonObject, genreValue: Any?) {
        when (genreValue) {
            is KJsonArray -> result.put(KEY_GENRE, joinNonBlankArray(genreValue))
            is String -> result.put(KEY_GENRE, genreValue)
        }
    }

    private fun imdbGenreValue(genreValue: JsonElement?): String? =
        when (genreValue) {
            is JsonArray -> joinNonBlankArray(genreValue).takeIf(::isNotBlank)
            else -> genreValue?.toString()?.trim('"')?.takeIf(::isNotBlank)
        }

    private fun joinNonBlankArray(values: KJsonArray): String {
        val resolved = ArrayList<String>()
        for (index in 0 until values.length()) {
            val value = values.optString(index, "")
            if (isNotBlank(value)) {
                resolved.add(value)
            }
        }
        return resolved.joinToString(", ")
    }

    private fun joinNonBlankArray(values: JsonArray): String {
        val resolved = ArrayList<String>()
        for (index in values.indices) {
            val value = values.optString(index, "")
            if (isNotBlank(value)) {
                resolved.add(value)
            }
        }
        return resolved.joinToString(", ")
    }

    private fun canFetchTmdbLocalizedDetails(tmdbId: String, mediaType: String): Boolean =
        isNotBlank(tmdbId) && isNotBlank(mediaType)

    private fun buildTmdbLocalizedUrl(tmdbId: String, mediaType: String, localeTag: String): String =
        StringBuilder(TMDB_BASE_URL)
            .append("/")
            .append(mediaType)
            .append("/")
            .append(URLEncoder.encode(tmdbId, StandardCharsets.UTF_8))
            .append("?language=")
            .append(URLEncoder.encode(localeTag, StandardCharsets.UTF_8))
            .toString()

    private fun isSuccessfulTmdbResponse(response: HttpUtil.HttpResult): Boolean =
        response.statusCode == HttpUtil.STATUS_OK && isNotBlank(response.body)

    private fun populateTmdbLocalizedDetails(result: KJsonObject, payload: KJsonObject) {
        result.put(KEY_NAME, firstNonBlank(payload.optString(KEY_NAME, ""), payload.optString(KEY_TITLE, "")))
        result.put(KEY_PLOT, payload.optString(KEY_OVERVIEW, ""))
        result.put(KEY_RATING, payload.optDouble("vote_average", 0.0).toString())
        result.put(KEY_RELEASE_DATE, firstNonBlank(payload.optString("release_date", ""), payload.optString("first_air_date", "")))
        putTmdbGenres(result, payload.optJSONArray("genres"))
        putTmdbPoster(result, payload.optString("poster_path", ""))
    }

    private fun putTmdbGenres(result: KJsonObject, genres: KJsonArray?) {
        val names = extractTmdbGenreNames(genres)
        if (names.isNotEmpty()) {
            result.put(KEY_GENRE, names.joinToString(", "))
        }
    }

    private fun extractTmdbGenreNames(genres: KJsonArray?): List<String> {
        val names = ArrayList<String>()
        if (genres == null || genres.length() == 0) {
            return names
        }
        for (index in 0 until genres.length()) {
            if (names.size >= 6) {
                break
            }
            val genre = genres.optJSONObject(index) ?: continue
            val name = genre.optString(KEY_NAME, "")
            if (isNotBlank(name)) {
                names.add(name)
            }
        }
        return names
    }

    private fun putTmdbPoster(result: KJsonObject, posterPath: String) {
        if (isNotBlank(posterPath)) {
            result.put(KEY_COVER, "https://image.tmdb.org/t/p/w500$posterPath")
        }
    }

    private fun indexEpisodesBySeasonEpisode(episodesMeta: KJsonArray): Map<String, KJsonObject> {
        val bySeasonEpisode = HashMap<String, KJsonObject>()
        for (index in 0 until episodesMeta.length()) {
            val row = episodesMeta.optJSONObject(index)
            val episodeKey = seasonEpisodeKey(
                row?.optString(KEY_SEASON, "") ?: "",
                row?.optString(KEY_EPISODE_NUMBER, "") ?: ""
            )
            if (episodeKey != null && row != null) {
                bySeasonEpisode[episodeKey] = row
            }
        }
        return bySeasonEpisode
    }

    private fun collectTmdbSeasons(bySeasonEpisode: Map<String, *>): Set<String> {
        val seasons = LinkedHashSet<String>()
        for (key in bySeasonEpisode.keys) {
            val separatorIndex = key.indexOf(':')
            if (separatorIndex > 0) {
                seasons.add(key.substring(0, separatorIndex))
            }
        }
        return seasons
    }

    private fun mergeLocalizedTmdbEpisode(bySeasonEpisode: Map<String, KJsonObject>, season: String, episode: KJsonObject?) {
        if (episode == null) {
            return
        }
        val episodeKey = seasonEpisodeKey(season, episode.optInt("episode_number", 0).toString()) ?: return
        val target = bySeasonEpisode[episodeKey] ?: return
        val mapped = mapTmdbEpisodeMeta(episode)
        replaceIfPresent(target, mapped, KEY_TITLE)
        replaceIfPresent(target, mapped, KEY_PLOT)
        replaceIfPresent(target, mapped, KEY_RELEASE_DATE)
        mergeMissing(target, mapped, KEY_LOGO)
    }

    private fun seasonEpisodeKey(rawSeason: String, rawEpisode: String): String? {
        val season = safeNumeric(rawSeason)
        val episode = safeNumeric(rawEpisode)
        if (isBlank(season) || isBlank(episode)) {
            return null
        }
        return "$season:$episode"
    }

    private fun fetchTmdbSeasonEpisodes(tmdbId: String, season: String, localeTag: String): KJsonArray {
        if (isBlank(tmdbId) || isBlank(season)) {
            return KJsonArray()
        }
        val bearerToken = resolveConfiguredTmdbBearerToken()
        if (isBlank(bearerToken)) {
            return KJsonArray()
        }

        return try {
            val url = "$TMDB_BASE_URL/tv/" +
                URLEncoder.encode(tmdbId, StandardCharsets.UTF_8) +
                "/season/" +
                URLEncoder.encode(season, StandardCharsets.UTF_8) +
                "?language=" +
                URLEncoder.encode(localeTag, StandardCharsets.UTF_8)

            val response = HttpUtil.sendRequest(url, buildTmdbHeaders(bearerToken), "GET")
            if (response.statusCode != HttpUtil.STATUS_OK || isBlank(response.body)) {
                return KJsonArray()
            }

            KJsonObject(response.body).optJSONArray("episodes") ?: KJsonArray()
        } catch (_: Exception) {
            KJsonArray()
        }
    }

    private fun mapTmdbEpisodeMeta(episode: KJsonObject?): KJsonObject {
        val mapped = KJsonObject()
        if (episode == null) {
            return mapped
        }
        mapped.put(KEY_TITLE, sanitizeEpisodeTitle(episode.optString("name", "")))
        mapped.put(KEY_PLOT, episode.optString(KEY_OVERVIEW, ""))
        mapped.put(KEY_RELEASE_DATE, episode.optString("air_date", ""))
        val stillPath = episode.optString("still_path", "")
        if (isNotBlank(stillPath)) {
            mapped.put(KEY_LOGO, "https://image.tmdb.org/t/p/w500$stillPath")
        }
        return mapped
    }

    private fun buildTmdbHeaders(bearerToken: String): Map<String, String> {
        val headers = HashMap<String, String>()
        headers[HEADER_USER_AGENT] = USER_AGENT_BROWSER
        headers[HEADER_ACCEPT_LANGUAGE] = buildAcceptLanguageHeader()
        headers["Authorization"] = "Bearer ${bearerToken.trim()}"
        return headers
    }

    private fun joinPersonNames(people: KJsonArray?): String {
        if (people == null) return ""
        val names = ArrayList<String>()
        for (index in 0 until people.length()) {
            val person = people.optJSONObject(index) ?: continue
            val name = person.optString("name", "")
            if (isNotBlank(name)) {
                names.add(name)
            }
            if (names.size >= 8) {
                break
            }
        }
        return names.joinToString(", ")
    }

    private fun joinPersonNames(people: JsonArray?): String? {
        if (people == null) return null
        val names = ArrayList<String>()
        for (index in people.indices) {
            val person = people.optObject(index) ?: continue
            val name = person.optString("name", "")
            if (isNotBlank(name)) {
                names.add(name)
            }
            if (names.size >= 8) {
                break
            }
        }
        return names.joinToString(", ")
    }

    private fun joinStringArray(values: KJsonArray?, max: Int): String {
        if (values == null) return ""
        val output = ArrayList<String>()
        for (index in 0 until values.length()) {
            val value = values.optString(index, "")
            if (isNotBlank(value)) {
                output.add(value)
            }
            if (output.size >= max) {
                break
            }
        }
        return output.joinToString(", ")
    }

    private fun extractJsonLd(html: String): String {
        val matcher = Pattern.compile(
            "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
            Pattern.CASE_INSENSITIVE or Pattern.DOTALL
        ).matcher(html)
        return if (matcher.find()) matcher.group(1).trim() else ""
    }

    private fun scoreCandidate(desired: String, candidateName: String, type: String?): Int {
        val normalizedDesired = normalizeTitle(desired)
        val normalizedCandidate = normalizeTitle(candidateName)
        var score = 0
        if (normalizedDesired == normalizedCandidate) score += 100
        if (normalizedCandidate.contains(normalizedDesired) || normalizedDesired.contains(normalizedCandidate)) score += 40
        for (token in normalizedDesired.split(" ")) {
            if (isBlank(token)) continue
            if (normalizedCandidate.contains(token)) score += 10
        }
        val normalizedType = type?.lowercase(Locale.ROOT).orEmpty()
        if (normalizedType.contains("tv")) score += 8
        if (normalizedType.contains("series")) score += 8
        if (normalizedType.contains("episode")) score -= 5
        return score
    }

    private fun normalizeTitle(value: String?): String {
        if (isBlank(value)) return ""
        return value.orEmpty()
            .lowercase(Locale.getDefault())
            .replace(Regex("(?i)\\b(uhd|fhd|hd|sd|4k|8k)\\b"), " ")
            .replace(Regex("(?i)\\b(series|movie|complete|collection|season\\s*\\d+|episode\\s*\\d+)\\b"), " ")
            .replace(Regex("[^a-z0-9 ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun sanitizeEpisodeTitle(title: String?): String {
        val value = title?.trim().orEmpty()
        return if (isGenericEpisodeTitle(value)) "" else value
    }

    private fun isGenericEpisodeTitle(title: String?): Boolean {
        if (isBlank(title)) {
            return true
        }
        return Regex("(?i)^episode\\s*\\d+\\s*[:\\-]?\\s*$").matches(title.orEmpty()) ||
            Regex("(?i)^ep\\.?\\s*\\d+\\s*[:\\-]?\\s*$").matches(title.orEmpty()) ||
            Regex("(?i)^e\\d+\\s*[:\\-]?\\s*$").matches(title.orEmpty())
    }

    private fun buildSearchQueries(rawTitle: String?, fuzzyHints: List<String>?): List<String> {
        val queries = LinkedHashSet<String>()
        addQueryVariant(queries, rawTitle)
        addQueryVariant(queries, normalizeTitle(rawTitle))
        addQueryVariant(queries, normalizeTitle(rawTitle).replace(Regex("\\b(19|20)\\d{2}\\b"), " ").replace(Regex("\\s+"), " ").trim())
        addQueryVariant(queries, normalizeTitle(rawTitle).replace(Regex("(?i)\\bseason\\s*\\d+\\b.*$"), "").trim())
        fuzzyHints?.forEach { hint ->
            addQueryVariant(queries, hint)
            addQueryVariant(queries, normalizeTitle(hint))
            addQueryVariant(queries, normalizeTitle(hint).replace(Regex("\\b(19|20)\\d{2}\\b"), " ").replace(Regex("\\s+"), " ").trim())
        }
        if (queries.isEmpty()) {
            addQueryVariant(queries, rawTitle)
        }
        return ArrayList(queries)
    }

    private fun addQueryVariant(sink: MutableSet<String>?, value: String?) {
        if (sink == null || isBlank(value)) return
        val normalized = value.orEmpty().trim()
        if (isBlank(normalized) || normalized.length < 2) return
        sink.add(normalized)
    }

    private fun isPreferredIdConsistent(imdbId: String, searchQueries: List<String>?): Boolean {
        if (!isLikelyImdbId(imdbId)) {
            return false
        }
        if (searchQueries.isNullOrEmpty()) {
            return true
        }
        return try {
            val reference = normalizeTitle(searchQueries.first())
            if (isBlank(reference)) {
                return true
            }
            val seriesMeta = fetchCinemetaSeriesDetails(imdbId)
            val movieMeta = fetchCinemetaMovieDetails(imdbId)
            val resolved = firstNonBlank(
                seriesMeta.optString("name", ""),
                movieMeta.optString("name", ""),
                fetchImdbTitleDetails(imdbId).optString(KEY_NAME, "")
            )
            if (isBlank(resolved)) {
                return true
            }
            val resolvedNorm = normalizeTitle(resolved)
            if (isBlank(resolvedNorm)) {
                return true
            }
            for (query in searchQueries) {
                val normalizedQuery = normalizeTitle(query)
                if (isBlank(normalizedQuery)) continue
                if (titleSimilarity(normalizedQuery, resolvedNorm) >= 0.5) {
                    return true
                }
            }
            false
        } catch (_: Exception) {
            true
        }
    }

    private fun titleSimilarity(a: String, b: String): Double {
        val normalizedA = normalizeTitle(a)
        val normalizedB = normalizeTitle(b)
        if (isBlank(normalizedA) || isBlank(normalizedB)) {
            return 0.0
        }
        if (normalizedA == normalizedB) {
            return 1.0
        }
        val setA = normalizedA.split(" ").filter { isNotBlank(it) }.toMutableSet()
        val setB = normalizedB.split(" ").filter { isNotBlank(it) }.toMutableSet()
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0
        }
        val intersection = setA.count { setB.contains(it) }
        val union = setA.size + setB.size - intersection
        return if (union <= 0) 0.0 else intersection.toDouble() / union
    }

    private fun firstNonBlank(vararg values: String): String {
        for (value in values) {
            if (isNotBlank(value)) {
                return value
            }
        }
        return ""
    }

    private fun httpGet(url: String): String =
        try {
            val headers = HashMap<String, String>()
            headers[HEADER_USER_AGENT] = USER_AGENT_BROWSER
            headers[HEADER_ACCEPT_LANGUAGE] = buildAcceptLanguageHeader()
            val response = HttpUtil.sendRequest(url, headers, "GET")
            if (response.statusCode != HttpUtil.STATUS_OK) {
                return ""
            }
            response.body
        } catch (_: Exception) {
            ""
        }

    private fun mergeIfPresent(target: KJsonObject, source: KJsonObject, key: String) {
        val value = source.optString(key, "")
        if (isNotBlank(value)) {
            target.put(key, value)
        }
    }

    private fun mergeIfPresent(target: KJsonObject, value: String?, key: String) {
        if (isNotBlank(value)) {
            target.put(key, value)
        }
    }

    private fun mergeMissing(target: KJsonObject, source: KJsonObject, key: String) {
        if (isNotBlank(target.optString(key, ""))) {
            return
        }
        val incoming = source.optString(key, "")
        if (isNotBlank(incoming)) {
            target.put(key, incoming)
        }
    }

    private fun replaceIfPresent(target: KJsonObject, source: KJsonObject, key: String) {
        replaceValue(target, source.optString(key, ""), key)
    }

    private fun replaceValue(target: KJsonObject, value: String, key: String) {
        if (isNotBlank(value)) {
            target.put(key, value)
        }
    }

    private fun toJsonObject(payload: KJsonObject): JsonObject =
        parseJsonObject(payload.toString()) ?: buildJsonObject { }

    private fun buildAcceptLanguageHeader(): String {
        val locale = Locale.forLanguageTag(I18n.getCurrentLanguageTag())
        var tag = locale.toLanguageTag()
        val language = locale.language
        if (isBlank(language)) {
            return "en-US,en;q=0.8"
        }
        if (isBlank(tag)) {
            tag = language
        }
        return "$tag,$language;q=0.9,en-US;q=0.8,en;q=0.7"
    }

    private fun withLanguageQuery(url: String): String {
        if (isBlank(url)) {
            return ""
        }
        val locale = Locale.forLanguageTag(I18n.getCurrentLanguageTag())
        val tag = locale.toLanguageTag()
        if (isBlank(tag)) {
            return url
        }
        val separator = if (url.contains("?")) "&" else "?"
        return url + separator + "language=" + URLEncoder.encode(tag, StandardCharsets.UTF_8)
    }

    private fun resolveConfiguredTmdbBearerToken(): String =
        try {
            val configuration = ConfigurationService.read()
            configuration.tmdbReadAccessToken?.trim().orEmpty()
        } catch (_: Exception) {
            ""
        }
}
