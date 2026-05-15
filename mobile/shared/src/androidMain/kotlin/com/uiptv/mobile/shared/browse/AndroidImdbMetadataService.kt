package com.uiptv.mobile.shared.browse

import android.database.sqlite.SQLiteDatabase
import com.uiptv.mobile.shared.db.AndroidUiptvDatabaseHelper
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URLEncoder
import java.net.URL
import java.util.Locale

class AndroidImdbMetadataService(
    private val databaseHelper: AndroidUiptvDatabaseHelper,
    private val httpGet: (String, Map<String, String>) -> String = ::defaultHttpGet
) : AndroidImdbMetadataProvider {
    override fun findSeriesDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata {
        if (!metadataEnabled()) {
            return AndroidImdbMetadata()
        }
        return findBestEffort(title, preferredImdbId, moviePreferred = false, hints = hints)
    }

    override fun findMovieDetails(title: String, preferredImdbId: String, hints: List<String>): AndroidImdbMetadata {
        if (!metadataEnabled()) {
            return AndroidImdbMetadata()
        }
        return findBestEffort(title, preferredImdbId, moviePreferred = true, hints = hints)
    }

    private fun findBestEffort(
        rawTitle: String,
        preferredImdbId: String,
        moviePreferred: Boolean,
        hints: List<String>
    ): AndroidImdbMetadata {
        if (rawTitle.isBlank() && preferredImdbId.isBlank()) {
            return AndroidImdbMetadata()
        }
        val searchQueries = buildSearchQueries(rawTitle, hints)
        val candidate = searchBestCandidate(searchQueries)
        val imdbId = resolvePreferredImdbId(preferredImdbId, candidate)
        if (imdbId.isBlank()) {
            return AndroidImdbMetadata()
        }

        val details = JSONObject()
            .put(KEY_TMDB, imdbId)
            .put(KEY_IMDB_URL, "$IMDB_TITLE_URL_PREFIX$imdbId/")
        mergeIfPresent(details, candidate, KEY_NAME)
        mergeIfPresent(details, candidate, KEY_COVER)
        mergeIfPresent(details, candidate, KEY_CAST)
        mergeIfPresent(details, candidate, KEY_GENRE)
        mergeIfPresent(details, candidate, KEY_RELEASE_DATE)

        mergeInto(details, fetchImdbTitleDetails(imdbId), replace = true)
        val primary = if (moviePreferred) fetchCinemetaMovieDetails(imdbId) else fetchCinemetaSeriesDetails(imdbId)
        val secondary = if (moviePreferred) fetchCinemetaSeriesDetails(imdbId) else fetchCinemetaMovieDetails(imdbId)
        mergeInto(details, primary, replace = false)
        mergeInto(details, secondary, replace = false, copyEpisodes = details.optJSONArray(KEY_EPISODES_META) == null)
        applyTmdbLocalization(details, primary, secondary, moviePreferred)
        return details.toMetadata()
    }

    private fun searchBestCandidate(queryTitles: List<String>): JSONObject {
        if (queryTitles.isEmpty()) {
            return JSONObject()
        }
        val primary = normalizeTitle(queryTitles.first())
        if (primary.isBlank()) {
            return JSONObject()
        }
        var best = JSONObject()
        var bestScore = Int.MIN_VALUE
        for (query in queryTitles) {
            val normalizedQuery = normalizeTitle(query)
            if (normalizedQuery.isBlank()) {
                continue
            }
            val found = findBestInSuggestions(querySuggestions(normalizedQuery), normalizedQuery)
            val id = found.optString(KEY_TMDB)
            if (!isLikelyImdbId(id)) {
                continue
            }
            var score = scoreCandidate(primary, found.optString(KEY_NAME), found.optString(KEY_GENRE))
            if (normalizedQuery == primary) {
                score += 5
            }
            if (score > bestScore) {
                bestScore = score
                best = found
            }
        }
        return best
    }

    private fun querySuggestions(queryTitle: String): JSONArray? =
        runCatching {
            val first = queryTitle.firstOrNull()?.lowercaseChar()?.toString()?.takeIf { it.matches(Regex("[a-z0-9]")) } ?: "x"
            val url = "https://v2.sg.media-imdb.com/suggestion/$first/${queryTitle.url()}$JSON_SUFFIX"
            val body = readUrl(url)
            if (body.isBlank()) null else JSONObject(body).optJSONArray("d")
        }.getOrNull()

    private fun findBestInSuggestions(rows: JSONArray?, desiredTitle: String): JSONObject {
        if (rows == null || rows.length() == 0) {
            return JSONObject()
        }
        var best = JSONObject()
        var bestScore = Int.MIN_VALUE
        for (index in 0 until rows.length()) {
            val candidate = rows.optJSONObject(index) ?: continue
            val id = candidate.optString("id")
            val name = candidate.optString("l")
            val type = candidate.optString("q")
            if (id.isBlank() || name.isBlank()) {
                continue
            }
            val score = scoreCandidate(desiredTitle, name, type)
            if (score > bestScore) {
                bestScore = score
                best = JSONObject()
                    .put(KEY_TMDB, id)
                    .put(KEY_NAME, name)
                    .put(KEY_GENRE, type)
                    .put(KEY_RELEASE_DATE, candidate.optString("y"))
                    .put(KEY_CAST, candidate.optString("s"))
                candidate.optJSONObject("i")?.optString("imageUrl")?.takeIf { it.isNotBlank() }?.let {
                    best.put(KEY_COVER, it)
                }
            }
        }
        return best
    }

    private fun fetchImdbTitleDetails(imdbId: String): JSONObject =
        runCatching {
            val html = readUrl(withLanguageQuery("$IMDB_TITLE_URL_PREFIX$imdbId/"))
            val jsonLd = Regex(
                """<script[^>]*type=["']application/ld\+json["'][^>]*>(.*?)</script>""",
                setOf(RegexOption.IGNORE_CASE, RegexOption.DOT_MATCHES_ALL)
            ).find(html)?.groupValues?.getOrNull(1)?.trim().orEmpty()
            if (jsonLd.isBlank()) {
                return@runCatching JSONObject()
            }
            val data = JSONObject(jsonLd)
            JSONObject()
                .put(KEY_NAME, data.optString(KEY_NAME))
                .put(KEY_COVER, data.optString("image"))
                .put(KEY_PLOT, data.optString("description"))
                .put(KEY_RELEASE_DATE, data.optString("datePublished"))
                .put(KEY_CAST, joinPersonNames(data.optJSONArray("actor")))
                .put(KEY_DIRECTOR, joinPersonNames(data.optJSONArray(KEY_DIRECTOR)))
                .apply {
                    data.optJSONObject("aggregateRating")?.optString("ratingValue")?.takeIf { it.isNotBlank() }?.let {
                        put(KEY_RATING, it)
                    }
                    putGenre(data.opt(KEY_GENRE))
                }
        }.getOrDefault(JSONObject())

    private fun fetchCinemetaSeriesDetails(imdbId: String): JSONObject =
        runCatching {
            val root = JSONObject(readUrl("https://v3-cinemeta.strem.io/meta/series/$imdbId$JSON_SUFFIX"))
            val meta = root.optJSONObject("meta") ?: return@runCatching JSONObject()
            val result = applyCinemetaMeta(meta)
            val videos = meta.optJSONArray("videos")
            if (videos != null && videos.length() > 0) {
                val episodes = JSONArray()
                for (index in 0 until videos.length()) {
                    val video = videos.optJSONObject(index) ?: continue
                    episodes.put(
                        JSONObject()
                            .put(KEY_TITLE, sanitizeEpisodeTitle(video.optString(KEY_TITLE)))
                            .put(KEY_PLOT, video.optString(KEY_OVERVIEW))
                            .put(KEY_LOGO, video.optString("thumbnail"))
                            .put(KEY_RELEASE_DATE, video.optString("released"))
                            .put(KEY_SEASON, video.optInt(KEY_SEASON, 0).toString())
                            .put(KEY_EPISODE_NUMBER, video.optInt("episode", 0).toString())
                    )
                }
                enrichEpisodeMetaWithTvMaze(episodes, imdbId, meta.optString(KEY_NAME))
                result.put(KEY_EPISODES_META, episodes)
            }
            result
        }.getOrDefault(JSONObject())

    private fun fetchCinemetaMovieDetails(imdbId: String): JSONObject =
        runCatching {
            val root = JSONObject(readUrl("https://v3-cinemeta.strem.io/meta/movie/$imdbId$JSON_SUFFIX"))
            val meta = root.optJSONObject("meta") ?: return@runCatching JSONObject()
            applyCinemetaMeta(meta)
        }.getOrDefault(JSONObject())

    private fun applyCinemetaMeta(meta: JSONObject): JSONObject =
        JSONObject()
            .put(KEY_NAME, meta.optString(KEY_NAME))
            .put(KEY_COVER, meta.optString("poster"))
            .put(KEY_PLOT, meta.optString("description"))
            .put(KEY_IMDB_URL, meta.optString("imdb_id").takeIf { it.isNotBlank() }?.let { "$IMDB_TITLE_URL_PREFIX$it/" }.orEmpty())
            .put(KEY_GENRE, joinStringArray(meta.optJSONArray("genres"), 6).ifBlank { meta.optString(KEY_GENRE) })
            .put(KEY_CAST, joinStringArray(meta.optJSONArray("cast"), 8).ifBlank { meta.optString(KEY_CAST) })
            .put(KEY_DIRECTOR, joinStringArray(meta.optJSONArray(KEY_DIRECTOR), 4).ifBlank { meta.optString(KEY_DIRECTOR) })
            .put(KEY_RELEASE_DATE, meta.optString("releaseInfo").ifBlank { meta.optString("released").take(10) })
            .put(KEY_RATING, meta.optString("imdbRating"))
            .apply {
                if (meta.has("moviedb_id")) {
                    put(KEY_TMDB_MEDIA_ID, meta.opt("moviedb_id")?.toString().orEmpty())
                }
            }

    private fun enrichEpisodeMetaWithTvMaze(episodes: JSONArray, imdbId: String, seriesName: String) {
        if (episodes.length() == 0 || imdbId.isBlank() || hasAnyEpisodePlot(episodes)) {
            return
        }
        val rows = fetchTvMazeEpisodes(imdbId, seriesName)
        if (rows.length() == 0) {
            return
        }
        val bySeasonEpisode = mutableMapOf<String, JSONObject>()
        val byTitle = mutableMapOf<String, JSONObject>()
        for (index in 0 until rows.length()) {
            val show = rows.optJSONObject(index) ?: continue
            val season = safeNumeric(show.optInt(KEY_SEASON, 0).toString())
            val episode = safeNumeric(show.optInt("number", 0).toString())
            if (season.isNotBlank() && episode.isNotBlank()) {
                bySeasonEpisode["$season:$episode"] = show
            }
            normalizeTitle(show.optString(KEY_NAME)).takeIf { it.isNotBlank() }?.let { byTitle[it] = show }
        }
        for (index in 0 until episodes.length()) {
            val row = episodes.optJSONObject(index) ?: continue
            if (row.optString(KEY_PLOT).isNotBlank()) {
                continue
            }
            val key = "${safeNumeric(row.optString(KEY_SEASON))}:${safeNumeric(row.optString(KEY_EPISODE_NUMBER))}"
            val match = bySeasonEpisode[key] ?: byTitle[normalizeTitle(row.optString(KEY_TITLE))] ?: continue
            stripHtml(match.optString("summary")).takeIf { it.isNotBlank() }?.let { row.put(KEY_PLOT, it) }
            if (row.optString(KEY_RELEASE_DATE).isBlank()) {
                row.put(KEY_RELEASE_DATE, match.optString("airdate"))
            }
        }
    }

    private fun fetchTvMazeEpisodes(imdbId: String, seriesName: String): JSONArray =
        runCatching {
            val body = readUrl("https://api.tvmaze.com/search/shows?q=${seriesName.url()}")
            val rows = JSONArray(body)
            var showId = -1
            var bestScore = Int.MIN_VALUE
            for (index in 0 until rows.length()) {
                val show = rows.optJSONObject(index)?.optJSONObject("show") ?: continue
                val externals = show.optJSONObject("externals")
                if (externals?.optString("imdb").equals(imdbId, ignoreCase = true)) {
                    showId = show.optInt("id", -1)
                    break
                }
                val score = scoreCandidate(seriesName, show.optString(KEY_NAME), show.optString("type"))
                if (score > bestScore) {
                    bestScore = score
                    showId = show.optInt("id", -1)
                }
            }
            if (showId <= 0) JSONArray() else JSONArray(readUrl("https://api.tvmaze.com/shows/$showId/episodes"))
        }.getOrDefault(JSONArray())

    private fun applyTmdbLocalization(details: JSONObject, primary: JSONObject, secondary: JSONObject, moviePreferred: Boolean) {
        val config = metadataConfig()
        val localeTag = config.languageTag
        if (localeTag.isBlank() || localeTag.lowercase(Locale.ROOT).startsWith("en") || config.tmdbToken.isBlank()) {
            return
        }
        val tmdbId = primary.optString(KEY_TMDB_MEDIA_ID).ifBlank { secondary.optString(KEY_TMDB_MEDIA_ID) }
        if (tmdbId.isBlank()) {
            return
        }
        val localized = fetchTmdbLocalizedDetails(tmdbId, if (moviePreferred) "movie" else "tv", localeTag, config.tmdbToken)
            .takeIf { it.length() > 0 }
            ?: fetchTmdbLocalizedDetails(tmdbId, if (moviePreferred) "tv" else "movie", localeTag, config.tmdbToken)
        replaceIfPresent(details, localized, KEY_NAME)
        replaceIfPresent(details, localized, KEY_PLOT)
        replaceIfPresent(details, localized, KEY_GENRE)
        replaceIfPresent(details, localized, KEY_RELEASE_DATE)
        mergeIfPresent(details, localized, KEY_COVER)
        mergeIfPresent(details, localized, KEY_RATING)
        if (!moviePreferred) {
            enrichEpisodesMetaWithTmdb(details.optJSONArray(KEY_EPISODES_META), tmdbId, localeTag, config.tmdbToken)
        }
    }

    private fun fetchTmdbLocalizedDetails(tmdbId: String, mediaType: String, localeTag: String, token: String): JSONObject =
        runCatching {
            val url = "$TMDB_BASE_URL/$mediaType/${tmdbId.url()}?language=${localeTag.url()}"
            val payload = JSONObject(readUrl(url, buildTmdbHeaders(token)))
            JSONObject()
                .put(KEY_NAME, payload.optString(KEY_NAME).ifBlank { payload.optString(KEY_TITLE) })
                .put(KEY_PLOT, payload.optString(KEY_OVERVIEW))
                .put(KEY_RATING, payload.optDouble("vote_average", 0.0).toString())
                .put(KEY_RELEASE_DATE, payload.optString("release_date").ifBlank { payload.optString("first_air_date") })
                .apply {
                    val genres = joinGenreNames(payload.optJSONArray("genres"))
                    if (genres.isNotBlank()) put(KEY_GENRE, genres)
                    payload.optString("poster_path").takeIf { it.isNotBlank() }?.let {
                        put(KEY_COVER, "https://image.tmdb.org/t/p/w500$it")
                    }
                }
        }.getOrDefault(JSONObject())

    private fun enrichEpisodesMetaWithTmdb(episodes: JSONArray?, tmdbId: String, localeTag: String, token: String) {
        if (episodes == null || episodes.length() == 0) {
            return
        }
        val bySeasonEpisode = mutableMapOf<String, JSONObject>()
        for (index in 0 until episodes.length()) {
            val row = episodes.optJSONObject(index) ?: continue
            val key = "${safeNumeric(row.optString(KEY_SEASON))}:${safeNumeric(row.optString(KEY_EPISODE_NUMBER))}"
            if (key != ":") {
                bySeasonEpisode[key] = row
            }
        }
        bySeasonEpisode.keys.map { it.substringBefore(":") }.distinct().forEach { season ->
            val localized = fetchTmdbSeasonEpisodes(tmdbId, season, localeTag, token)
            for (index in 0 until localized.length()) {
                val episode = localized.optJSONObject(index) ?: continue
                val key = "$season:${safeNumeric(episode.optInt("episode_number", 0).toString())}"
                val target = bySeasonEpisode[key] ?: continue
                sanitizeEpisodeTitle(episode.optString(KEY_NAME)).takeIf { it.isNotBlank() }?.let { target.put(KEY_TITLE, it) }
                episode.optString(KEY_OVERVIEW).takeIf { it.isNotBlank() }?.let { target.put(KEY_PLOT, it) }
                episode.optString("air_date").takeIf { it.isNotBlank() }?.let { target.put(KEY_RELEASE_DATE, it) }
                episode.optString("still_path").takeIf { it.isNotBlank() }?.let { target.put(KEY_LOGO, "https://image.tmdb.org/t/p/w500$it") }
            }
        }
    }

    private fun fetchTmdbSeasonEpisodes(tmdbId: String, season: String, localeTag: String, token: String): JSONArray =
        runCatching {
            val url = "$TMDB_BASE_URL/tv/${tmdbId.url()}/season/${season.url()}?language=${localeTag.url()}"
            JSONObject(readUrl(url, buildTmdbHeaders(token))).optJSONArray("episodes") ?: JSONArray()
        }.getOrDefault(JSONArray())

    private fun resolvePreferredImdbId(preferred: String, candidate: JSONObject): String {
        if (isLikelyImdbId(preferred)) {
            return preferred
        }
        return candidate.optString(KEY_TMDB).takeIf(::isLikelyImdbId).orEmpty()
    }

    private fun mergeInto(target: JSONObject, source: JSONObject, replace: Boolean, copyEpisodes: Boolean = true) {
        val keys = listOf(KEY_NAME, KEY_COVER, KEY_PLOT, KEY_CAST, KEY_DIRECTOR, KEY_GENRE, KEY_RELEASE_DATE, KEY_RATING, KEY_TMDB, KEY_IMDB_URL, KEY_TMDB_MEDIA_ID)
        keys.forEach { key ->
            if (replace) replaceIfPresent(target, source, key) else mergeIfPresent(target, source, key)
        }
        if (copyEpisodes && source.optJSONArray(KEY_EPISODES_META) != null && target.optJSONArray(KEY_EPISODES_META) == null) {
            target.put(KEY_EPISODES_META, source.optJSONArray(KEY_EPISODES_META))
        }
    }

    private fun JSONObject.toMetadata(): AndroidImdbMetadata =
        AndroidImdbMetadata(
            name = optString(KEY_NAME),
            cover = optString(KEY_COVER),
            plot = optString(KEY_PLOT),
            cast = optString(KEY_CAST),
            director = optString(KEY_DIRECTOR),
            genre = optString(KEY_GENRE),
            releaseDate = optString(KEY_RELEASE_DATE),
            rating = optString(KEY_RATING),
            tmdb = optString(KEY_TMDB),
            imdbUrl = optString(KEY_IMDB_URL),
            episodesMeta = optJSONArray(KEY_EPISODES_META).toEpisodeMetadata()
        )

    private fun JSONArray?.toEpisodeMetadata(): List<AndroidEpisodeMetadata> {
        if (this == null) {
            return emptyList()
        }
        return buildList {
            for (index in 0 until length()) {
                val row = optJSONObject(index) ?: continue
                add(
                    AndroidEpisodeMetadata(
                        title = row.optString(KEY_TITLE),
                        season = row.optString(KEY_SEASON),
                        episodeNumber = row.optString(KEY_EPISODE_NUMBER),
                        plot = row.optString(KEY_PLOT).ifBlank { row.optString("description") },
                        logo = row.optString(KEY_LOGO),
                        releaseDate = row.optString(KEY_RELEASE_DATE),
                        rating = row.optString(KEY_RATING)
                    )
                )
            }
        }
    }

    private fun metadataEnabled(): Boolean =
        metadataConfig().enableThumbnails

    private fun metadataConfig(): MetadataConfig {
        val db = databaseHelper.readableDatabase
        return db.rawQuery(
            "SELECT enableThumbnails, tmdbReadAccessToken, languageLocale FROM Configuration ORDER BY id LIMIT 1",
            null
        ).use { cursor ->
            if (!cursor.moveToFirst()) {
                MetadataConfig()
            } else {
                MetadataConfig(
                    enableThumbnails = cursor.string("enableThumbnails").isTruthy(),
                    tmdbToken = cursor.string("tmdbReadAccessToken"),
                    languageTag = cursor.string("languageLocale").ifBlank { "en-US" }
                )
            }
        }
    }

    private fun readUrl(url: String, headers: Map<String, String> = defaultHeaders()): String =
        httpGet(url, headers)

    private fun defaultHeaders(): Map<String, String> =
        mapOf("User-Agent" to USER_AGENT_BROWSER, "Accept-Language" to buildAcceptLanguageHeader())

    private fun buildTmdbHeaders(token: String): Map<String, String> =
        defaultHeaders() + ("Authorization" to "Bearer ${token.trim()}")

    private fun buildAcceptLanguageHeader(): String {
        val tag = metadataConfig().languageTag.ifBlank { "en-US" }
        val locale = Locale.forLanguageTag(tag)
        val language = locale.language.ifBlank { "en" }
        return "$tag,$language;q=0.9,en-US;q=0.8,en;q=0.7"
    }

    private fun withLanguageQuery(url: String): String {
        val tag = metadataConfig().languageTag
        if (tag.isBlank()) {
            return url
        }
        return "$url${if (url.contains("?")) "&" else "?"}language=${tag.url()}"
    }

    private fun buildSearchQueries(rawTitle: String, hints: List<String>): List<String> {
        val queries = linkedSetOf<String>()
        addQueryVariant(queries, rawTitle)
        addQueryVariant(queries, normalizeTitle(rawTitle))
        addQueryVariant(queries, normalizeTitle(rawTitle).replace(Regex("""\b(19|20)\d{2}\b"""), " ").replace(Regex("""\s+"""), " ").trim())
        addQueryVariant(queries, normalizeTitle(rawTitle).replace(Regex("""(?i)\bseason\s*\d+\b.*$"""), "").trim())
        hints.forEach { hint ->
            addQueryVariant(queries, hint)
            addQueryVariant(queries, normalizeTitle(hint))
            addQueryVariant(queries, normalizeTitle(hint).replace(Regex("""\b(19|20)\d{2}\b"""), " ").replace(Regex("""\s+"""), " ").trim())
        }
        return queries.toList()
    }

    private fun addQueryVariant(values: MutableSet<String>, value: String) {
        val trimmed = value.trim()
        if (trimmed.length >= 2) {
            values += trimmed
        }
    }

    private fun scoreCandidate(desired: String, candidateName: String, type: String): Int {
        val a = normalizeTitle(desired)
        val b = normalizeTitle(candidateName)
        var score = 0
        if (a == b) score += 100
        if (a.isNotBlank() && b.isNotBlank() && (b.contains(a) || a.contains(b))) score += 40
        a.split(" ").filter { it.isNotBlank() }.forEach { if (b.contains(it)) score += 10 }
        val normalizedType = type.lowercase()
        if (normalizedType.contains("tv")) score += 8
        if (normalizedType.contains("series")) score += 8
        if (normalizedType.contains("episode")) score -= 5
        return score
    }

    private fun normalizeTitle(value: String): String =
        value.lowercase()
            .replace(Regex("""(?i)\b(uhd|fhd|hd|sd|4k|8k)\b"""), " ")
            .replace(Regex("""(?i)\b(series|movie|complete|collection|season\s*\d+|episode\s*\d+)\b"""), " ")
            .replace(Regex("""[^a-z0-9 ]"""), " ")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun sanitizeEpisodeTitle(title: String): String {
        val value = title.trim()
        return if (
            value.isBlank() ||
            value.matches(Regex("""(?i)^episode\s*\d+\s*[:\-]?\s*$""")) ||
            value.matches(Regex("""(?i)^ep\.?\s*\d+\s*[:\-]?\s*$""")) ||
            value.matches(Regex("""(?i)^e\d+\s*[:\-]?\s*$"""))
        ) {
            ""
        } else {
            value
        }
    }

    private fun hasAnyEpisodePlot(rows: JSONArray): Boolean {
        for (index in 0 until rows.length()) {
            if (rows.optJSONObject(index)?.optString(KEY_PLOT).orEmpty().isNotBlank()) {
                return true
            }
        }
        return false
    }

    private fun JSONObject.putGenre(value: Any?) {
        when (value) {
            is JSONArray -> put(KEY_GENRE, joinStringArray(value, 6))
            is String -> put(KEY_GENRE, value)
        }
    }

    private fun joinPersonNames(rows: JSONArray?): String {
        if (rows == null) {
            return ""
        }
        return buildList {
            for (index in 0 until rows.length()) {
                val name = rows.optJSONObject(index)?.optString(KEY_NAME).orEmpty()
                if (name.isNotBlank()) add(name)
                if (size >= 8) break
            }
        }.joinToString(", ")
    }

    private fun joinStringArray(rows: JSONArray?, max: Int): String {
        if (rows == null) {
            return ""
        }
        return buildList {
            for (index in 0 until rows.length()) {
                rows.optString(index).takeIf { it.isNotBlank() }?.let(::add)
                if (size >= max) break
            }
        }.joinToString(", ")
    }

    private fun joinGenreNames(rows: JSONArray?): String {
        if (rows == null) {
            return ""
        }
        return buildList {
            for (index in 0 until rows.length()) {
                rows.optJSONObject(index)?.optString(KEY_NAME)?.takeIf { it.isNotBlank() }?.let(::add)
                if (size >= 6) break
            }
        }.joinToString(", ")
    }

    private fun stripHtml(value: String): String =
        value.replace(Regex("<[^>]*>"), " ")
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace(Regex("""\s+"""), " ")
            .trim()

    private fun safeNumeric(value: String): String =
        value.replace(Regex("""\D"""), "").trimStart('0').ifBlank { "" }

    private fun isLikelyImdbId(value: String): Boolean =
        value.matches(Regex("""tt\d+"""))

    private fun String.url(): String =
        URLEncoder.encode(this, Charsets.UTF_8.name())

    private fun android.database.Cursor.string(column: String): String {
        val index = getColumnIndex(column)
        return if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
    }

    private fun String.isTruthy(): Boolean =
        equals("1", ignoreCase = true) ||
            equals("true", ignoreCase = true) ||
            equals("yes", ignoreCase = true)

    private data class MetadataConfig(
        val enableThumbnails: Boolean = false,
        val tmdbToken: String = "",
        val languageTag: String = "en-US"
    )

    companion object {
        private const val TMDB_BASE_URL = "https://api.themoviedb.org/3"
        private const val IMDB_TITLE_URL_PREFIX = "https://www.imdb.com/title/"
        private const val JSON_SUFFIX = ".json"
        private const val USER_AGENT_BROWSER = "Mozilla/5.0"
        private const val KEY_CAST = "cast"
        private const val KEY_COVER = "cover"
        private const val KEY_DIRECTOR = "director"
        private const val KEY_EPISODE_NUMBER = "episodeNum"
        private const val KEY_EPISODES_META = "episodesMeta"
        private const val KEY_GENRE = "genre"
        private const val KEY_IMDB_URL = "imdbUrl"
        private const val KEY_LOGO = "logo"
        private const val KEY_NAME = "name"
        private const val KEY_OVERVIEW = "overview"
        private const val KEY_PLOT = "plot"
        private const val KEY_RATING = "rating"
        private const val KEY_RELEASE_DATE = "releaseDate"
        private const val KEY_SEASON = "season"
        private const val KEY_TITLE = "title"
        private const val KEY_TMDB = "tmdb"
        private const val KEY_TMDB_MEDIA_ID = "tmdbMediaId"
    }
}

private fun mergeIfPresent(target: JSONObject, source: JSONObject, key: String) {
    val value = source.optString(key)
    if (target.optString(key).isBlank() && value.isNotBlank()) {
        target.put(key, value)
    }
}

private fun replaceIfPresent(target: JSONObject, source: JSONObject, key: String) {
    val value = source.optString(key)
    if (value.isNotBlank()) {
        target.put(key, value)
    }
}

private fun defaultHttpGet(url: String, headers: Map<String, String>): String {
    val connection = (URL(url).openConnection() as HttpURLConnection).apply {
        connectTimeout = 10_000
        readTimeout = 20_000
        requestMethod = "GET"
        headers.forEach { (key, value) -> setRequestProperty(key, value) }
    }
    return try {
        val status = connection.responseCode
        val stream = if (status >= 300) connection.errorStream else connection.inputStream
        val body = stream?.use { it.readBytes().toString(Charsets.UTF_8) }.orEmpty()
        if (status >= 300) "" else body
    } finally {
        connection.disconnect()
    }
}
