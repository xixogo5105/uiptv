package com.uiptv.service;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class ImdbMetadataService {
    private static final ImdbMetadataService INSTANCE = new ImdbMetadataService();

    public static ImdbMetadataService getInstance() {
        return INSTANCE;
    }

    public JSONObject findBestEffortDetails(String rawTitle, String preferredImdbId) {
        JSONObject details = new JSONObject();
        if (isBlank(rawTitle) && isBlank(preferredImdbId)) {
            return details;
        }

        JSONObject candidate = searchBestCandidate(rawTitle);
        String imdbId = resolvePreferredImdbId(preferredImdbId, candidate);
        if (isBlank(imdbId)) {
            return details;
        }

        details.put("tmdb", imdbId); // keep frontend field compatibility
        details.put("imdbUrl", "https://www.imdb.com/title/" + imdbId + "/");

        // IMDb suggestion endpoint still returns useful metadata even when title pages are WAF blocked.
        mergeIfPresent(details, candidate, "name");
        mergeIfPresent(details, candidate, "cover");
        mergeIfPresent(details, candidate, "cast");
        mergeIfPresent(details, candidate, "genre");
        mergeIfPresent(details, candidate, "releaseDate");

        JSONObject pageDetails = fetchImdbTitleDetails(imdbId);
        mergeIfPresent(details, pageDetails, "name");
        mergeIfPresent(details, pageDetails, "cover");
        mergeIfPresent(details, pageDetails, "plot");
        mergeIfPresent(details, pageDetails, "cast");
        mergeIfPresent(details, pageDetails, "director");
        mergeIfPresent(details, pageDetails, "genre");
        mergeIfPresent(details, pageDetails, "releaseDate");
        mergeIfPresent(details, pageDetails, "rating");

        JSONObject cinemeta = fetchCinemetaDetails(imdbId);
        mergeMissing(details, cinemeta, "name");
        mergeMissing(details, cinemeta, "cover");
        mergeMissing(details, cinemeta, "plot");
        mergeMissing(details, cinemeta, "cast");
        mergeMissing(details, cinemeta, "director");
        mergeMissing(details, cinemeta, "genre");
        mergeMissing(details, cinemeta, "releaseDate");
        mergeMissing(details, cinemeta, "rating");
        mergeMissing(details, cinemeta, "imdbUrl");
        if (cinemeta.has("episodesMeta")) {
            details.put("episodesMeta", cinemeta.getJSONArray("episodesMeta"));
        }
        return details;
    }

    private String resolvePreferredImdbId(String preferredImdbId, JSONObject candidate) {
        if (isLikelyImdbId(preferredImdbId)) {
            return preferredImdbId;
        }
        return candidate.optString("tmdb", "");
    }

    private boolean isLikelyImdbId(String id) {
        if (isBlank(id)) return false;
        return id.matches("tt\\d+");
    }

    private JSONObject searchBestCandidate(String rawTitle) {
        JSONObject best = new JSONObject();
        try {
            String title = normalizeTitle(rawTitle);
            if (isBlank(title)) return best;
            JSONObject bestForPrimary = findBestInSuggestions(querySuggestions(title), title);
            best = bestForPrimary;

            String strippedYear = title.replaceAll("\\b(19|20)\\d{2}\\b", " ").replaceAll("\\s+", " ").trim();
            if (isBlank(best.optString("tmdb", "")) && isNotBlank(strippedYear) && !strippedYear.equals(title)) {
                JSONObject bestForStrippedYear = findBestInSuggestions(querySuggestions(strippedYear), strippedYear);
                if (isNotBlank(bestForStrippedYear.optString("tmdb", ""))) {
                    best = bestForStrippedYear;
                }
            }

            String beforeSeason = strippedYear.replaceAll("(?i)\\bseason\\s*\\d+\\b.*$", "").trim();
            if (isBlank(best.optString("tmdb", "")) && isNotBlank(beforeSeason) && !beforeSeason.equals(strippedYear)) {
                JSONObject bestBeforeSeason = findBestInSuggestions(querySuggestions(beforeSeason), beforeSeason);
                if (isNotBlank(bestBeforeSeason.optString("tmdb", ""))) {
                    best = bestBeforeSeason;
                }
            }
            return best;
        } catch (Exception ignored) {
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
        } catch (Exception ignored) {
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
        mapped.put("genre", candidate.optString("q", ""));
        mapped.put("releaseDate", candidate.optString("y", ""));
        mapped.put("cast", candidate.optString("s", ""));
        JSONObject image = candidate.optJSONObject("i");
        if (image != null) {
            mapped.put("cover", image.optString("imageUrl", ""));
        }
        return mapped;
    }

    private JSONObject fetchImdbTitleDetails(String imdbId) {
        JSONObject result = new JSONObject();
        try {
            String html = httpGet("https://www.imdb.com/title/" + imdbId + "/");
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
                result.put("rating", rating.optString("ratingValue", ""));
            }

            Object genreValue = data.opt("genre");
            if (genreValue instanceof JSONArray genreArray) {
                List<String> genres = new ArrayList<>();
                for (int i = 0; i < genreArray.length(); i++) {
                    String g = genreArray.optString(i, "");
                    if (isNotBlank(g)) genres.add(g);
                }
                result.put("genre", String.join(", ", genres));
            } else if (genreValue instanceof String g) {
                result.put("genre", g);
            }

            result.put("cast", joinPersonNames(data.optJSONArray("actor")));
            result.put("director", joinPersonNames(data.optJSONArray("director")));
        } catch (Exception ignored) {
            // best effort
        }
        return result;
    }

    private JSONObject fetchCinemetaDetails(String imdbId) {
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

            result.put("name", meta.optString("name", ""));
            result.put("cover", meta.optString("poster", ""));
            result.put("plot", meta.optString("description", ""));
            result.put("imdbUrl", isNotBlank(meta.optString("imdb_id", "")) ? "https://www.imdb.com/title/" + meta.optString("imdb_id", "") + "/" : "");

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

            JSONArray videos = meta.optJSONArray("videos");
            if (videos != null && !videos.isEmpty()) {
                JSONArray episodesMeta = new JSONArray();
                for (int i = 0; i < videos.length(); i++) {
                    JSONObject video = videos.optJSONObject(i);
                    if (video == null) continue;
                    JSONObject e = new JSONObject();
                    e.put("title", video.optString("title", ""));
                    e.put("plot", video.optString("overview", ""));
                    e.put("logo", video.optString("thumbnail", ""));
                    e.put("releaseDate", video.optString("released", ""));
                    e.put("season", String.valueOf(video.optInt("season", 0)));
                    e.put("episodeNum", String.valueOf(video.optInt("episode", 0)));
                    episodesMeta.put(e);
                }
                result.put("episodesMeta", episodesMeta);
            }
        } catch (Exception ignored) {
            // best effort
        }
        return result;
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
                .replaceAll("[^a-z0-9 ]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String httpGet(String url) {
        try {
            HttpURLConnection conn = (HttpURLConnection) new URL(url).openConnection();
            conn.setConnectTimeout(5000);
            conn.setReadTimeout(10000);
            conn.setRequestMethod("GET");
            conn.setRequestProperty("User-Agent", "Mozilla/5.0");
            if (conn.getResponseCode() != HttpURLConnection.HTTP_OK) {
                return "";
            }
            try (InputStream in = conn.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[4096];
                int n;
                while ((n = in.read(buffer)) != -1) out.write(buffer, 0, n);
                return out.toString(StandardCharsets.UTF_8);
            }
        } catch (Exception ignored) {
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
}
