package com.uiptv.service;

import com.uiptv.model.Configuration;
import com.uiptv.ui.ThumbnailAwareUI;
import com.uiptv.util.HttpUtil;
import com.uiptv.util.I18n;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class ImdbMetadataService {
    private static final ImdbMetadataService INSTANCE = new ImdbMetadataService();
    private static final String TMDB_BASE_URL = "https://api.themoviedb.org/3";
    private static final String IMDB_TITLE_URL_PREFIX = "https://www.imdb.com/title/";
    private static final String KEY_COVER = "cover";
    private static final String KEY_DIRECTOR = "director";
    private static final String KEY_GENRE = "genre";
    private static final String KEY_IMDB_URL = "imdbUrl";
    private static final String KEY_RATING = "rating";
    private static final String KEY_RELEASE_DATE = "releaseDate";
    private static final String KEY_EPISODES_META = "episodesMeta";
    private static final String KEY_TITLE = "title";
    private static final String KEY_OVERVIEW = "overview";
    private static final String KEY_SEASON = "season";
    private static final String KEY_EPISODE_NUMBER = "episodeNum";
    private static final String KEY_TMDB_MEDIA_ID = "tmdbMediaId";

    public static ImdbMetadataService getInstance() {
        return INSTANCE;
    }

    public JSONObject findBestEffortDetails(String rawTitle, String preferredImdbId) {
        // Return immediately when thumbnails disabled
        if (!ThumbnailAwareUI.areThumbnailsEnabled()) {
            return new JSONObject();
        }
        return findBestEffortInternal(rawTitle, preferredImdbId, false, List.of());
    }

    public JSONObject findBestEffortDetails(String rawTitle, String preferredImdbId, List<String> fuzzyHints) {
        // Return immediately when thumbnails disabled
        if (!ThumbnailAwareUI.areThumbnailsEnabled()) {
            return new JSONObject();
        }
        return findBestEffortInternal(rawTitle, preferredImdbId, false, fuzzyHints);
    }

    public JSONObject findBestEffortMovieDetails(String rawTitle, String preferredImdbId) {
        // Return immediately when thumbnails disabled
        if (!ThumbnailAwareUI.areThumbnailsEnabled()) {
            return new JSONObject();
        }
        return findBestEffortInternal(rawTitle, preferredImdbId, true, List.of());
    }

    public JSONObject findBestEffortMovieDetails(String rawTitle, String preferredImdbId, List<String> fuzzyHints) {
        // Return immediately when thumbnails disabled
        if (!ThumbnailAwareUI.areThumbnailsEnabled()) {
            return new JSONObject();
        }
        return findBestEffortInternal(rawTitle, preferredImdbId, true, fuzzyHints);
    }

    private JSONObject findBestEffortInternal(String rawTitle, String preferredImdbId, boolean moviePreferred, List<String> fuzzyHints) {
        JSONObject details = new JSONObject();
        if (isBlank(rawTitle) && isBlank(preferredImdbId)) {
            return details;
        }

        List<String> searchQueries = buildSearchQueries(rawTitle, fuzzyHints);
        JSONObject candidate = searchBestCandidate(searchQueries);
        String imdbId = resolvePreferredImdbId(preferredImdbId, candidate, searchQueries);
        if (isBlank(imdbId)) {
            return details;
        }

        details.put("tmdb", imdbId); // keep frontend field compatibility
        details.put(KEY_IMDB_URL, IMDB_TITLE_URL_PREFIX + imdbId + "/");

        // IMDb suggestion endpoint still returns useful metadata even when title pages are WAF blocked.
        mergeIfPresent(details, candidate, "name");
        mergeIfPresent(details, candidate, KEY_COVER);
        mergeIfPresent(details, candidate, "cast");
        mergeIfPresent(details, candidate, KEY_GENRE);
        mergeIfPresent(details, candidate, KEY_RELEASE_DATE);

        JSONObject pageDetails = fetchImdbTitleDetails(imdbId);
        mergeIfPresent(details, pageDetails, "name");
        mergeIfPresent(details, pageDetails, KEY_COVER);
        mergeIfPresent(details, pageDetails, "plot");
        mergeIfPresent(details, pageDetails, "cast");
        mergeIfPresent(details, pageDetails, KEY_DIRECTOR);
        mergeIfPresent(details, pageDetails, KEY_GENRE);
        mergeIfPresent(details, pageDetails, KEY_RELEASE_DATE);
        mergeIfPresent(details, pageDetails, KEY_RATING);

        JSONObject primaryMeta = moviePreferred ? fetchCinemetaMovieDetails(imdbId) : fetchCinemetaSeriesDetails(imdbId);
        JSONObject secondaryMeta = moviePreferred ? fetchCinemetaSeriesDetails(imdbId) : fetchCinemetaMovieDetails(imdbId);
        mergeMissing(details, primaryMeta, "name");
        mergeMissing(details, primaryMeta, KEY_COVER);
        mergeMissing(details, primaryMeta, "plot");
        mergeMissing(details, primaryMeta, "cast");
        mergeMissing(details, primaryMeta, KEY_DIRECTOR);
        mergeMissing(details, primaryMeta, KEY_GENRE);
        mergeMissing(details, primaryMeta, KEY_RELEASE_DATE);
        mergeMissing(details, primaryMeta, KEY_RATING);
        mergeMissing(details, primaryMeta, KEY_IMDB_URL);
        if (primaryMeta.has(KEY_EPISODES_META)) {
            details.put(KEY_EPISODES_META, primaryMeta.getJSONArray(KEY_EPISODES_META));
        }

        mergeMissing(details, secondaryMeta, "name");
        mergeMissing(details, secondaryMeta, KEY_COVER);
        mergeMissing(details, secondaryMeta, "plot");
        mergeMissing(details, secondaryMeta, "cast");
        mergeMissing(details, secondaryMeta, KEY_DIRECTOR);
        mergeMissing(details, secondaryMeta, KEY_GENRE);
        mergeMissing(details, secondaryMeta, KEY_RELEASE_DATE);
        mergeMissing(details, secondaryMeta, KEY_RATING);
        mergeMissing(details, secondaryMeta, KEY_IMDB_URL);
        if (!details.has(KEY_EPISODES_META) && secondaryMeta.has(KEY_EPISODES_META)) {
            details.put(KEY_EPISODES_META, secondaryMeta.getJSONArray(KEY_EPISODES_META));
        }

        applyTmdbLocalization(details, primaryMeta, secondaryMeta, moviePreferred);
        return details;
    }

    private String resolvePreferredImdbId(String preferredImdbId, JSONObject candidate, List<String> searchQueries) {
        if (isLikelyImdbId(preferredImdbId) && isPreferredIdConsistent(preferredImdbId, searchQueries)) {
            return preferredImdbId;
        }
        String candidateId = candidate.optString("tmdb", "");
        if (isLikelyImdbId(candidateId)) {
            return candidateId;
        }
        return isLikelyImdbId(preferredImdbId) ? preferredImdbId : "";
    }

    private boolean isLikelyImdbId(String id) {
        if (isBlank(id)) return false;
        return id.matches("tt\\d+");
    }

    private JSONObject searchBestCandidate(List<String> queryTitles) {
        JSONObject best = new JSONObject();
        try {
            if (queryTitles == null || queryTitles.isEmpty()) {
                return best;
            }
            String primary = normalizeTitle(queryTitles.getFirst());
            if (isBlank(primary)) {
                return best;
            }

            int bestScore = Integer.MIN_VALUE;
            for (String query : queryTitles) {
                String q = normalizeTitle(query);
                if (isBlank(q)) continue;
                JSONObject found = findBestInSuggestions(querySuggestions(q), q);
                if (isBlank(found.optString("tmdb", ""))) continue;
                int score = scoreCandidate(primary, found.optString("name", ""), found.optString(KEY_GENRE, ""));
                if (q.equals(primary)) {
                    score += 5;
                }
                if (score > bestScore) {
                    bestScore = score;
                    best = found;
                }
            }
            return best;
        } catch (Exception _) {
            return best;
        }
    }

    private JSONArray querySuggestions(String queryTitle) {
        try {
            if (isBlank(queryTitle)) return null;
            String first = Character.toString(Character.toLowerCase(queryTitle.charAt(0)));
            if (!first.matches("[a-z0-9]")) first = "x";
            String url = "https://v2.sg.media-imdb.com/suggestion/" + first + "/" +
                    URLEncoder.encode(queryTitle, StandardCharsets.UTF_8) + ".json";
            String body = httpGet(url);
            if (isBlank(body)) return null;
            JSONObject json = new JSONObject(body);
            return json.optJSONArray("d");
        } catch (Exception _) {
            return null;
        }
    }

    private JSONObject findBestInSuggestions(JSONArray d, String desiredTitle) {
        JSONObject best = new JSONObject();
        if (d == null || d.isEmpty()) {
            return best;
        }

        int bestScore = Integer.MIN_VALUE;
        for (int i = 0; i < d.length(); i++) {
            JSONObject candidate = d.optJSONObject(i);
            if (candidate == null) continue;
            String id = candidate.optString("id", "");
            String name = candidate.optString("l", "");
            String type = candidate.optString("q", "");
            if (isBlank(id) || isBlank(name)) continue;
            int score = scoreCandidate(desiredTitle, name, type);
            if (score > bestScore) {
                bestScore = score;
                best = mapSuggestionCandidate(candidate);
            }
        }
        return best;
    }

    private JSONObject mapSuggestionCandidate(JSONObject candidate) {
        JSONObject mapped = new JSONObject();
        mapped.put("tmdb", candidate.optString("id", ""));
        mapped.put("name", candidate.optString("l", ""));
        mapped.put(KEY_GENRE, candidate.optString("q", ""));
        mapped.put(KEY_RELEASE_DATE, candidate.optString("y", ""));
        mapped.put("cast", candidate.optString("s", ""));
        JSONObject image = candidate.optJSONObject("i");
        if (image != null) {
            mapped.put(KEY_COVER, image.optString("imageUrl", ""));
        }
        return mapped;
    }

    private JSONObject fetchImdbTitleDetails(String imdbId) {
        JSONObject result = new JSONObject();
        try {
            String html = httpGet(withLanguageQuery(IMDB_TITLE_URL_PREFIX + imdbId + "/"));
            if (isBlank(html)) return result;

            String jsonLd = extractJsonLd(html);
            if (isBlank(jsonLd)) return result;

            JSONObject data = new JSONObject(jsonLd);
            result.put("name", data.optString("name", ""));
            result.put("cover", data.optString("image", ""));
            result.put("plot", data.optString("description", ""));
            result.put("releaseDate", data.optString("datePublished", ""));

            JSONObject rating = data.optJSONObject("aggregateRating");
            if (rating != null) {
                result.put(KEY_RATING, rating.optString("ratingValue", ""));
            }

            Object genreValue = data.opt("genre");
            if (genreValue instanceof JSONArray genreArray) {
                List<String> genres = new ArrayList<>();
                for (int i = 0; i < genreArray.length(); i++) {
                    String g = genreArray.optString(i, "");
                    if (isNotBlank(g)) genres.add(g);
                }
                result.put(KEY_GENRE, String.join(", ", genres));
            } else if (genreValue instanceof String g) {
                result.put(KEY_GENRE, g);
            }

            result.put("cast", joinPersonNames(data.optJSONArray("actor")));
            result.put(KEY_DIRECTOR, joinPersonNames(data.optJSONArray("director")));
        } catch (Exception _) {
            // best effort
        }
        return result;
    }

    private JSONObject fetchCinemetaSeriesDetails(String imdbId) {
        JSONObject result = new JSONObject();
        try {
            String json = httpGet("https://v3-cinemeta.strem.io/meta/series/" + imdbId + ".json");
            if (isBlank(json)) {
                return result;
            }
            JSONObject root = new JSONObject(json);
            JSONObject meta = root.optJSONObject("meta");
            if (meta == null) {
                return result;
            }

            applyCinemetaMeta(result, meta);

            JSONArray videos = meta.optJSONArray("videos");
            if (videos != null && !videos.isEmpty()) {
                JSONArray episodesMeta = new JSONArray();
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject video = videos.optJSONObject(i);
                    if (video == null) continue;
                    JSONObject e = new JSONObject();
                    e.put(KEY_TITLE, sanitizeEpisodeTitle(video.optString(KEY_TITLE, "")));
                    e.put("plot", video.optString(KEY_OVERVIEW, ""));
                    e.put("logo", video.optString("thumbnail", ""));
                    e.put("releaseDate", video.optString("released", ""));
                    e.put(KEY_SEASON, String.valueOf(video.optInt(KEY_SEASON, 0)));
                    e.put(KEY_EPISODE_NUMBER, String.valueOf(video.optInt("episode", 0)));
                    episodesMeta.put(e);
                }
                enrichEpisodeMetaWithTvMaze(episodesMeta, imdbId, meta.optString("name", ""));
                result.put("episodesMeta", episodesMeta);
            }
        } catch (Exception _) {
            // best effort
        }
        return result;
    }

    private void enrichEpisodeMetaWithTvMaze(JSONArray episodesMeta, String imdbId, String seriesName) {
        if (episodesMeta == null || episodesMeta.isEmpty() || isBlank(imdbId)) {
            return;
        }
        if (hasAnyEpisodePlot(episodesMeta)) {
            return;
        }

        JSONArray tvMazeEpisodes = fetchTvMazeEpisodes(imdbId, seriesName);
        if (tvMazeEpisodes == null || tvMazeEpisodes.isEmpty()) {
            return;
        }

        Map<String, JSONObject> bySeasonEpisode = new HashMap<>();
        Map<String, JSONObject> byTitle = new HashMap<>();
        for (int i = 0; i < tvMazeEpisodes.length(); i++) {
            JSONObject row = tvMazeEpisodes.optJSONObject(i);
            if (row == null) continue;

            String season = safeNumeric(String.valueOf(row.optInt("season", 0)));
            String episode = safeNumeric(String.valueOf(row.optInt("number", 0)));
            if (isNotBlank(season) && isNotBlank(episode)) {
                bySeasonEpisode.put(season + ":" + episode, row);
            }

            String title = normalizeTitle(row.optString("name", ""));
            if (isNotBlank(title)) {
                byTitle.put(title, row);
            }
        }

        for (int i = 0; i < episodesMeta.length(); i++) {
            JSONObject row = episodesMeta.optJSONObject(i);
            if (row == null) continue;
            if (isNotBlank(row.optString("plot", ""))) continue;

            String season = safeNumeric(row.optString(KEY_SEASON, ""));
            String episode = safeNumeric(row.optString(KEY_EPISODE_NUMBER, ""));
            JSONObject match = null;
            if (isNotBlank(season) && isNotBlank(episode)) {
                match = bySeasonEpisode.get(season + ":" + episode);
            }
            if (match == null) {
                match = byTitle.get(normalizeTitle(row.optString(KEY_TITLE, "")));
            }
            if (match == null) continue;

            String summary = stripHtml(match.optString("summary", ""));
            if (isNotBlank(summary)) {
                row.put("plot", summary);
            }
            if (isBlank(row.optString("releaseDate", ""))) {
                row.put("releaseDate", match.optString("airdate", ""));
            }
        }
    }

    private boolean hasAnyEpisodePlot(JSONArray episodesMeta) {
        if (episodesMeta == null || episodesMeta.isEmpty()) {
            return false;
        }
        for (int i = 0; i < episodesMeta.length(); i++) {
            JSONObject row = episodesMeta.optJSONObject(i);
            if (row == null) continue;
            if (isNotBlank(row.optString("plot", ""))) {
                return true;
            }
        }
        return false;
    }

    private JSONArray fetchTvMazeEpisodes(String imdbId, String seriesName) {
        try {
            int showId = resolveTvMazeShowId(imdbId, seriesName);
            if (showId <= 0) {
                return new JSONArray();
            }
            String body = httpGet("https://api.tvmaze.com/shows/" + showId + "/episodes");
            if (isBlank(body)) {
                return new JSONArray();
            }
            return new JSONArray(body);
        } catch (Exception _) {
            return new JSONArray();
        }
    }

    private int resolveTvMazeShowId(String imdbId, String seriesName) {
        if (isBlank(seriesName)) {
            return -1;
        }
        try {
            String body = httpGet("https://api.tvmaze.com/search/shows?q=" + URLEncoder.encode(seriesName, StandardCharsets.UTF_8));
            if (isBlank(body)) {
                return -1;
            }
            JSONArray rows = new JSONArray(body);
            if (rows.isEmpty()) {
                return -1;
            }

            for (int i = 0; i < rows.length(); i++) {
                JSONObject wrapper = rows.optJSONObject(i);
                JSONObject show = wrapper == null ? null : wrapper.optJSONObject("show");
                JSONObject externals = show == null ? null : show.optJSONObject("externals");
                if (show == null || externals == null) continue;
                if (imdbId.equalsIgnoreCase(externals.optString("imdb", ""))) {
                    return show.optInt("id", -1);
                }
            }

            String normalizedSeries = normalizeTitle(seriesName);
            int bestId = -1;
            int bestScore = Integer.MIN_VALUE;
            for (int i = 0; i < rows.length(); i++) {
                JSONObject wrapper = rows.optJSONObject(i);
                JSONObject show = wrapper == null ? null : wrapper.optJSONObject("show");
                if (show == null) continue;
                int score = scoreCandidate(normalizedSeries, show.optString("name", ""), show.optString("type", ""));
                if (score > bestScore) {
                    bestScore = score;
                    bestId = show.optInt("id", -1);
                }
            }
            return bestId;
        } catch (Exception _) {
            return -1;
        }
    }

    private String safeNumeric(String value) {
        if (isBlank(value)) {
            return "";
        }
        String normalized = value.replaceAll("[^0-9]", "");
        return isBlank(normalized) ? "" : String.valueOf(Integer.parseInt(normalized));
    }

    private String stripHtml(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value
                .replaceAll("<[^>]*>", " ")
                .replace("&amp;", "&")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private JSONObject fetchCinemetaMovieDetails(String imdbId) {
        JSONObject result = new JSONObject();
        try {
            String json = httpGet("https://v3-cinemeta.strem.io/meta/movie/" + imdbId + ".json");
            if (isBlank(json)) {
                return result;
            }
            JSONObject root = new JSONObject(json);
            JSONObject meta = root.optJSONObject("meta");
            if (meta == null) {
                return result;
            }
            applyCinemetaMeta(result, meta);
        } catch (Exception _) {
            // best effort
        }
        return result;
    }

    private void applyCinemetaMeta(JSONObject result, JSONObject meta) {
        result.put("name", meta.optString("name", ""));
        result.put("cover", meta.optString("poster", ""));
        result.put("plot", meta.optString("description", ""));
        result.put("imdbUrl", isNotBlank(meta.optString("imdb_id", "")) ? "https://www.imdb.com/title/" + meta.optString("imdb_id", "") + "/" : "");
        if (meta.has("moviedb_id")) {
            result.put(KEY_TMDB_MEDIA_ID, String.valueOf(meta.opt("moviedb_id")));
        }

        JSONArray genres = meta.optJSONArray("genres");
        if (genres != null) {
            result.put("genre", joinStringArray(genres, 6));
        } else {
            result.put("genre", meta.optString("genre", ""));
        }

        JSONArray cast = meta.optJSONArray("cast");
        if (cast != null) {
            result.put("cast", joinStringArray(cast, 8));
        } else {
            result.put("cast", meta.optString("cast", ""));
        }

        JSONArray director = meta.optJSONArray("director");
        if (director != null) {
            result.put("director", joinStringArray(director, 4));
        } else {
            result.put("director", meta.optString("director", ""));
        }

        String releaseInfo = meta.optString("releaseInfo", "");
        String released = meta.optString("released", "");
        if (isNotBlank(releaseInfo)) {
            result.put("releaseDate", releaseInfo);
        } else if (isNotBlank(released)) {
            result.put("releaseDate", released.substring(0, Math.min(10, released.length())));
        }
        result.put("rating", meta.optString("imdbRating", ""));
    }

    private void applyTmdbLocalization(JSONObject details, JSONObject primaryMeta, JSONObject secondaryMeta, boolean moviePreferred) {
        String localeTag = I18n.getCurrentLanguageTag();
        if (isBlank(localeTag) || localeTag.toLowerCase(Locale.ROOT).startsWith("en")) {
            return;
        }

        String tmdbId = firstNonBlank(
                primaryMeta.optString(KEY_TMDB_MEDIA_ID, ""),
                secondaryMeta.optString(KEY_TMDB_MEDIA_ID, "")
        );
        if (isBlank(tmdbId)) {
            return;
        }

        JSONObject primaryLocalized = fetchTmdbLocalizedDetails(tmdbId, moviePreferred ? "movie" : "tv", localeTag);
        JSONObject secondaryLocalized = fetchTmdbLocalizedDetails(tmdbId, moviePreferred ? "tv" : "movie", localeTag);
        JSONObject localized = !primaryLocalized.isEmpty() ? primaryLocalized : secondaryLocalized;
        if (localized.isEmpty()) {
            return;
        }

        // Prefer localized text for user-facing metadata when available.
        replaceIfPresent(details, localized, "name");
        replaceIfPresent(details, localized, "plot");
        replaceIfPresent(details, localized, "genre");
        replaceIfPresent(details, localized, "releaseDate");
        mergeMissing(details, localized, "cover");
        mergeMissing(details, localized, "rating");
        if (!moviePreferred) {
            enrichEpisodesMetaWithTmdb(details.optJSONArray("episodesMeta"), tmdbId, localeTag);
        }
    }

    private JSONObject fetchTmdbLocalizedDetails(String tmdbId, String mediaType, String localeTag) {
        JSONObject result = new JSONObject();
        if (isBlank(tmdbId) || isBlank(mediaType)) {
            return result;
        }
        String bearerToken = resolveConfiguredTmdbBearerToken();
        if (isBlank(bearerToken)) {
            return result;
        }

        try {
            StringBuilder url = new StringBuilder(TMDB_BASE_URL)
                    .append("/")
                    .append(mediaType)
                    .append("/")
                    .append(URLEncoder.encode(tmdbId, StandardCharsets.UTF_8))
                    .append("?language=")
                    .append(URLEncoder.encode(localeTag, StandardCharsets.UTF_8));

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0");
            headers.put("Accept-Language", buildAcceptLanguageHeader());
            headers.put("Authorization", "Bearer " + bearerToken.trim());

            HttpUtil.HttpResult response = HttpUtil.sendRequest(url.toString(), headers, "GET");
            if (response.statusCode() != HttpUtil.STATUS_OK || isBlank(response.body())) {
                return result;
            }

            JSONObject payload = new JSONObject(response.body());
            result.put("name", firstNonBlank(payload.optString("name", ""), payload.optString(KEY_TITLE, "")));
            result.put("plot", payload.optString(KEY_OVERVIEW, ""));
            result.put("rating", String.valueOf(payload.optDouble("vote_average", 0)));
            result.put("releaseDate", firstNonBlank(payload.optString("release_date", ""), payload.optString("first_air_date", "")));
            JSONArray genres = payload.optJSONArray("genres");
            if (genres != null && !genres.isEmpty()) {
                List<String> names = new ArrayList<>();
                for (int i = 0; i < genres.length(); i++) {
                    JSONObject g = genres.optJSONObject(i);
                    if (g == null) continue;
                    String name = g.optString("name", "");
                    if (isNotBlank(name)) {
                        names.add(name);
                    }
                    if (names.size() >= 6) break;
                }
                if (!names.isEmpty()) {
                    result.put("genre", String.join(", ", names));
                }
            }
            String posterPath = payload.optString("poster_path", "");
            if (isNotBlank(posterPath)) {
                result.put("cover", "https://image.tmdb.org/t/p/w500" + posterPath);
            }
        } catch (Exception _) {
            // best effort
        }
        return result;
    }

    private void enrichEpisodesMetaWithTmdb(JSONArray episodesMeta, String tmdbId, String localeTag) {
        if (episodesMeta == null || episodesMeta.isEmpty() || isBlank(tmdbId) || isBlank(localeTag)) {
            return;
        }

        Map<String, JSONObject> bySeasonEpisode = new HashMap<>();
        Set<String> seasons = new LinkedHashSet<>();
        for (int i = 0; i < episodesMeta.length(); i++) {
            JSONObject row = episodesMeta.optJSONObject(i);
            if (row == null) continue;
            String season = safeNumeric(row.optString(KEY_SEASON, ""));
            String episode = safeNumeric(row.optString(KEY_EPISODE_NUMBER, ""));
            if (isBlank(season) || isBlank(episode)) {
                continue;
            }
            bySeasonEpisode.put(season + ":" + episode, row);
            seasons.add(season);
        }

        for (String season : seasons) {
            JSONArray localizedEpisodes = fetchTmdbSeasonEpisodes(tmdbId, season, localeTag);
            for (int i = 0; i < localizedEpisodes.length(); i++) {
                JSONObject episode = localizedEpisodes.optJSONObject(i);
                if (episode == null) continue;
                String episodeNum = safeNumeric(String.valueOf(episode.optInt("episode_number", 0)));
                if (isBlank(episodeNum)) {
                    continue;
                }
                JSONObject target = bySeasonEpisode.get(season + ":" + episodeNum);
                if (target == null) {
                    continue;
                }
                JSONObject mapped = mapTmdbEpisodeMeta(episode);
                replaceIfPresent(target, mapped, KEY_TITLE);
                replaceIfPresent(target, mapped, "plot");
                replaceIfPresent(target, mapped, "releaseDate");
                mergeMissing(target, mapped, "logo");
            }
        }
    }

    private JSONArray fetchTmdbSeasonEpisodes(String tmdbId, String season, String localeTag) {
        if (isBlank(tmdbId) || isBlank(season)) {
            return new JSONArray();
        }
        String bearerToken = resolveConfiguredTmdbBearerToken();
        if (isBlank(bearerToken)) {
            return new JSONArray();
        }

        try {
            String url = TMDB_BASE_URL + "/tv/"
                    + URLEncoder.encode(tmdbId, StandardCharsets.UTF_8)
                    + "/season/"
                    + URLEncoder.encode(season, StandardCharsets.UTF_8)
                    + "?language="
                    + URLEncoder.encode(localeTag, StandardCharsets.UTF_8);

            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0");
            headers.put("Accept-Language", buildAcceptLanguageHeader());
            headers.put("Authorization", "Bearer " + bearerToken.trim());

            HttpUtil.HttpResult response = HttpUtil.sendRequest(url, headers, "GET");
            if (response.statusCode() != HttpUtil.STATUS_OK || isBlank(response.body())) {
                return new JSONArray();
            }

            JSONObject payload = new JSONObject(response.body());
            JSONArray episodes = payload.optJSONArray("episodes");
            return episodes == null ? new JSONArray() : episodes;
        } catch (Exception _) {
            return new JSONArray();
        }
    }

    private JSONObject mapTmdbEpisodeMeta(JSONObject episode) {
        JSONObject mapped = new JSONObject();
        if (episode == null) {
            return mapped;
        }
        mapped.put(KEY_TITLE, sanitizeEpisodeTitle(episode.optString("name", "")));
        mapped.put("plot", episode.optString(KEY_OVERVIEW, ""));
        mapped.put("releaseDate", episode.optString("air_date", ""));
        String stillPath = episode.optString("still_path", "");
        if (isNotBlank(stillPath)) {
            mapped.put("logo", "https://image.tmdb.org/t/p/w500" + stillPath);
        }
        return mapped;
    }

    private String joinPersonNames(JSONArray people) {
        if (people == null) return "";
        List<String> names = new ArrayList<>();
        for (int i = 0; i < people.length(); i++) {
            JSONObject person = people.optJSONObject(i);
            if (person == null) continue;
            String name = person.optString("name", "");
            if (isNotBlank(name)) names.add(name);
            if (names.size() >= 8) break;
        }
        return String.join(", ", names);
    }

    private String joinStringArray(JSONArray values, int max) {
        if (values == null) return "";
        List<String> out = new ArrayList<>();
        for (int i = 0; i < values.length(); i++) {
            String value = values.optString(i, "");
            if (isNotBlank(value)) {
                out.add(value);
            }
            if (out.size() >= max) break;
        }
        return String.join(", ", out);
    }

    private String extractJsonLd(String html) {
        Matcher m = Pattern.compile(
                "<script[^>]*type=\"application/ld\\+json\"[^>]*>(.*?)</script>",
                Pattern.CASE_INSENSITIVE | Pattern.DOTALL
        ).matcher(html);
        if (m.find()) {
            return m.group(1).trim();
        }
        return "";
    }

    private int scoreCandidate(String desired, String candidateName, String type) {
        String a = normalizeTitle(desired);
        String b = normalizeTitle(candidateName);
        int score = 0;
        if (a.equals(b)) score += 100;
        if (b.contains(a) || a.contains(b)) score += 40;
        for (String token : a.split(" ")) {
            if (isBlank(token)) continue;
            if (b.contains(token)) score += 10;
        }
        String t = (type == null ? "" : type.toLowerCase());
        if (t.contains("tv")) score += 8;
        if (t.contains("series")) score += 8;
        if (t.contains("episode")) score -= 5;
        return score;
    }

    private String normalizeTitle(String s) {
        if (isBlank(s)) return "";
        return s.toLowerCase()
                .replaceAll("(?i)\\b(uhd|fhd|hd|sd|4k|8k)\\b", " ")
                .replaceAll("(?i)\\b(series|movie|complete|collection|season\\s*\\d+|episode\\s*\\d+)\\b", " ")
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String sanitizeEpisodeTitle(String title) {
        String value = title == null ? "" : title.trim();
        return isGenericEpisodeTitle(value) ? "" : value;
    }

    private boolean isGenericEpisodeTitle(String title) {
        if (isBlank(title)) {
            return true;
        }
        return title.matches("(?i)^episode\\s*\\d+\\s*[:\\-]?\\s*$")
                || title.matches("(?i)^ep\\.?\\s*\\d+\\s*[:\\-]?\\s*$")
                || title.matches("(?i)^e\\d+\\s*[:\\-]?\\s*$");
    }

    private List<String> buildSearchQueries(String rawTitle, List<String> fuzzyHints) {
        LinkedHashSet<String> queries = new LinkedHashSet<>();
        addQueryVariant(queries, rawTitle);
        addQueryVariant(queries, normalizeTitle(rawTitle));
        addQueryVariant(queries, normalizeTitle(rawTitle).replaceAll("\\b(19|20)\\d{2}\\b", " ").replaceAll("\\s+", " ").trim());
        addQueryVariant(queries, normalizeTitle(rawTitle).replaceAll("(?i)\\bseason\\s*\\d+\\b.*$", "").trim());
        if (fuzzyHints != null) {
            for (String hint : fuzzyHints) {
                addQueryVariant(queries, hint);
                addQueryVariant(queries, normalizeTitle(hint));
                addQueryVariant(queries, normalizeTitle(hint).replaceAll("\\b(19|20)\\d{2}\\b", " ").replaceAll("\\s+", " ").trim());
            }
        }
        if (queries.isEmpty()) {
            addQueryVariant(queries, rawTitle);
        }
        return new ArrayList<>(queries);
    }

    private void addQueryVariant(Set<String> sink, String value) {
        if (sink == null || isBlank(value)) return;
        String v = value.trim();
        if (isBlank(v)) return;
        if (v.length() < 2) return;
        sink.add(v);
    }

    private boolean isPreferredIdConsistent(String imdbId, List<String> searchQueries) {
        if (!isLikelyImdbId(imdbId)) {
            return false;
        }
        if (searchQueries == null || searchQueries.isEmpty()) {
            return true;
        }
        try {
            String reference = normalizeTitle(searchQueries.getFirst());
            if (isBlank(reference)) {
                return true;
            }
            JSONObject seriesMeta = fetchCinemetaSeriesDetails(imdbId);
            JSONObject movieMeta = fetchCinemetaMovieDetails(imdbId);
            String resolved = firstNonBlank(
                    seriesMeta.optString("name", ""),
                    movieMeta.optString("name", ""),
                    fetchImdbTitleDetails(imdbId).optString("name", "")
            );
            if (isBlank(resolved)) {
                return true;
            }
            String resolvedNorm = normalizeTitle(resolved);
            if (isBlank(resolvedNorm)) {
                return true;
            }
            for (String query : searchQueries) {
                String q = normalizeTitle(query);
                if (isBlank(q)) continue;
                if (titleSimilarity(q, resolvedNorm) >= 0.5) {
                    return true;
                }
            }
            return false;
        } catch (Exception _) {
            return true;
        }
    }

    private double titleSimilarity(String a, String b) {
        String na = normalizeTitle(a);
        String nb = normalizeTitle(b);
        if (isBlank(na) || isBlank(nb)) {
            return 0;
        }
        if (na.equals(nb)) {
            return 1;
        }
        Set<String> sa = new LinkedHashSet<>(List.of(na.split(" ")));
        Set<String> sb = new LinkedHashSet<>(List.of(nb.split(" ")));
        sa.removeIf(String::isBlank);
        sb.removeIf(String::isBlank);
        if (sa.isEmpty() || sb.isEmpty()) {
            return 0;
        }
        int intersection = 0;
        for (String token : sa) {
            if (sb.contains(token)) {
                intersection++;
            }
        }
        int union = sa.size() + sb.size() - intersection;
        return union <= 0 ? 0 : (double) intersection / union;
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String v : values) {
            if (isNotBlank(v)) return v;
        }
        return "";
    }

    private String httpGet(String url) {
        try {
            Map<String, String> headers = new HashMap<>();
            headers.put("User-Agent", "Mozilla/5.0");
            headers.put("Accept-Language", buildAcceptLanguageHeader());
            HttpUtil.HttpResult response = HttpUtil.sendRequest(url, headers, "GET");
            if (response.statusCode() != HttpUtil.STATUS_OK) {
                return "";
            }
            return response.body();
        } catch (Exception _) {
            return "";
        }
    }

    private void mergeIfPresent(JSONObject target, JSONObject source, String key) {
        String value = source.optString(key, "");
        if (isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private void mergeMissing(JSONObject target, JSONObject source, String key) {
        if (isNotBlank(target.optString(key, ""))) {
            return;
        }
        String incoming = source.optString(key, "");
        if (isNotBlank(incoming)) {
            target.put(key, incoming);
        }
    }

    private void replaceIfPresent(JSONObject target, JSONObject source, String key) {
        String value = source.optString(key, "");
        if (isNotBlank(value)) {
            target.put(key, value);
        }
    }

    private String buildAcceptLanguageHeader() {
        Locale locale = Locale.forLanguageTag(I18n.getCurrentLanguageTag());
        String tag = locale.toLanguageTag();
        String language = locale.getLanguage();
        if (isBlank(language)) {
            return "en-US,en;q=0.8";
        }
        if (isBlank(tag)) {
            tag = language;
        }
        return tag + "," + language + ";q=0.9,en-US;q=0.8,en;q=0.7";
    }

    private String withLanguageQuery(String url) {
        if (isBlank(url)) {
            return "";
        }
        Locale locale = Locale.forLanguageTag(I18n.getCurrentLanguageTag());
        String tag = locale.toLanguageTag();
        if (isBlank(tag)) {
            return url;
        }
        String separator = url.contains("?") ? "&" : "?";
        return url + separator + "language=" + URLEncoder.encode(tag, StandardCharsets.UTF_8);
    }

    private String resolveConfiguredTmdbBearerToken() {
        try {
            Configuration configuration = ConfigurationService.getInstance().read();
            if (configuration == null) {
                return "";
            }
            String token = configuration.getTmdbReadAccessToken();
            return token == null ? "" : token.trim();
        } catch (Exception _) {
            return "";
        }
    }
}
