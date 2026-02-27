package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.shared.SeasonInfo;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpSeriesDetailsJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        if (account == null) {
            generateJsonResponse(ex, "{\"seasonInfo\":{},\"episodes\":[]}");
            return;
        }
        if (account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        String seriesId = getParam(ex, "seriesId");
        String categoryId = getParam(ex, "categoryId");
        String seriesName = getParam(ex, "seriesName");
        JSONObject response = new JSONObject();
        response.put("seasonInfo", new JSONObject());
        response.put("episodes", new JSONArray());
        response.put("episodesMeta", new JSONArray());
        if (!isBlank(seriesId)) {
            List<Channel> cached = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
            if (!cached.isEmpty() && SeriesEpisodeDb.get().isFresh(account, categoryId, seriesId, ConfigurationService.getInstance().getCacheExpiryMs())) {
                response.put("episodes", new JSONArray(com.uiptv.util.ServerUtils.objectToJson(cached)));
            }
        }

        JSONObject seasonInfo = new JSONObject();
        List<String> fuzzyHints = buildFuzzyHints(seriesName, seasonInfo, response.optJSONArray("episodes"));
        JSONObject imdbFirst = ImdbMetadataService.getInstance().findBestEffortDetails(seriesName, "", fuzzyHints);
        copyIfPresent(seasonInfo, imdbFirst, "name");
        copyIfPresent(seasonInfo, imdbFirst, "cover");
        copyIfPresent(seasonInfo, imdbFirst, "plot");
        copyIfPresent(seasonInfo, imdbFirst, "cast");
        copyIfPresent(seasonInfo, imdbFirst, "director");
        copyIfPresent(seasonInfo, imdbFirst, "genre");
        copyIfPresent(seasonInfo, imdbFirst, "releaseDate");
        copyIfPresent(seasonInfo, imdbFirst, "rating");
        copyIfPresent(seasonInfo, imdbFirst, "tmdb");
        copyIfPresent(seasonInfo, imdbFirst, "imdbUrl");
        if (imdbFirst.optJSONArray("episodesMeta") != null) {
            response.put("episodesMeta", imdbFirst.optJSONArray("episodesMeta"));
        }

        if (account.getType() == AccountType.XTREME_API && !isBlank(seriesId)) {
            EpisodeList details = XtremeParser.parseEpisodes(seriesId, account);
            if (details != null) {
                SeasonInfo info = details.getSeasonInfo();
                if (info != null) {
                    JSONObject provider = new JSONObject(info.toJson());
                    // Fill missing fields only; IMDb remains preferred when available.
                    mergeMissing(seasonInfo, provider, "name");
                    mergeMissing(seasonInfo, provider, "cover");
                    mergeMissing(seasonInfo, provider, "plot");
                    mergeMissing(seasonInfo, provider, "cast");
                    mergeMissing(seasonInfo, provider, "director");
                    mergeMissing(seasonInfo, provider, "genre");
                    mergeMissing(seasonInfo, provider, "releaseDate");
                    mergeMissing(seasonInfo, provider, "rating");
                    mergeMissing(seasonInfo, provider, "tmdb");
                }

                Map<String, JSONObject> episodesMeta = indexEpisodesMeta(imdbFirst.optJSONArray("episodesMeta"));
                JSONArray episodesJson = new JSONArray();
                if (details.getEpisodes() != null) {
                    for (Episode episode : details.getEpisodes()) {
                        if (episode == null) {
                            continue;
                        }
                        Channel channel = new Channel();
                        channel.setChannelId(episode.getId());
                        channel.setName(episode.getTitle());
                        channel.setCmd(episode.getCmd());
                        channel.setExtraJson(episode.toJson());
                        channel.setSeason(episode.getSeason());
                        channel.setEpisodeNum(episode.getEpisodeNum());
                        if (episode.getInfo() != null) {
                            channel.setLogo(episode.getInfo().getMovieImage());
                            channel.setDescription(episode.getInfo().getPlot());
                            channel.setReleaseDate(episode.getInfo().getReleaseDate());
                            channel.setRating(episode.getInfo().getRating());
                            channel.setDuration(episode.getInfo().getDuration());
                            if (isBlank(channel.getSeason())) {
                                channel.setSeason(episode.getInfo().getSeason());
                            }
                        }
                        enrichEpisode(channel, episodesMeta);
                        episodesJson.put(new JSONObject(channel.toJson()));
                    }
                }
                response.put("episodes", episodesJson);
                if (episodesJson.length() > 0) {
                    SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, toChannels(episodesJson));
                }
            }
        }

        // Refine with provider fields + episode-derived hints to recover from wrong/missing portal IDs.
        fuzzyHints = buildFuzzyHints(firstNonBlank(seasonInfo.optString("name", ""), seriesName), seasonInfo, response.optJSONArray("episodes"));
        JSONObject imdbFallback = ImdbMetadataService.getInstance()
                .findBestEffortDetails(
                        firstNonBlank(seasonInfo.optString("name", ""), seriesName),
                        seasonInfo.optString("tmdb", ""),
                        fuzzyHints
                );
        mergeMissing(seasonInfo, imdbFallback, "name");
        mergeMissing(seasonInfo, imdbFallback, "cover");
        mergeMissing(seasonInfo, imdbFallback, "plot");
        mergeMissing(seasonInfo, imdbFallback, "cast");
        mergeMissing(seasonInfo, imdbFallback, "director");
        mergeMissing(seasonInfo, imdbFallback, "genre");
        mergeMissing(seasonInfo, imdbFallback, "releaseDate");
        mergeMissing(seasonInfo, imdbFallback, "rating");
        mergeMissing(seasonInfo, imdbFallback, "tmdb");
        mergeMissing(seasonInfo, imdbFallback, "imdbUrl");
        if ((response.optJSONArray("episodesMeta") == null || response.optJSONArray("episodesMeta").isEmpty())
                && imdbFallback.optJSONArray("episodesMeta") != null) {
            response.put("episodesMeta", imdbFallback.optJSONArray("episodesMeta"));
        }
        enrichEpisodesInResponse(response);
        response.put("seasonInfo", seasonInfo);
        applyNameYearFallback(seasonInfo, seriesName);

        generateJsonResponse(ex, response.toString());
    }

    private java.util.List<Channel> toChannels(JSONArray episodesJson) {
        java.util.List<Channel> channels = new java.util.ArrayList<>();
        for (int i = 0; i < episodesJson.length(); i++) {
            JSONObject obj = episodesJson.optJSONObject(i);
            if (obj == null) {
                continue;
            }
            Channel channel = Channel.fromJson(obj.toString());
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private void mergeMissing(JSONObject target, JSONObject source, String key) {
        String existing = target.optString(key, "");
        if (!isBlank(existing)) {
            return;
        }
        String incoming = source.optString(key, "");
        if (!isBlank(incoming)) {
            target.put(key, incoming);
        }
    }

    private void copyIfPresent(JSONObject target, JSONObject source, String key) {
        String incoming = source.optString(key, "");
        if (!isBlank(incoming)) {
            target.put(key, incoming);
        }
    }

    private Map<String, JSONObject> indexEpisodesMeta(JSONArray episodesMeta) {
        Map<String, JSONObject> indexed = new HashMap<>();
        if (episodesMeta == null) {
            return indexed;
        }
        for (int i = 0; i < episodesMeta.length(); i++) {
            JSONObject row = episodesMeta.optJSONObject(i);
            if (row == null) continue;
            String season = safeNumeric(row.optString("season", ""));
            String episode = safeNumeric(row.optString("episodeNum", ""));
            if (!isBlank(season) && !isBlank(episode)) {
                indexed.put(season + ":" + episode, row);
            }
            String title = normalize(row.optString("title", ""));
            if (!isBlank(title)) {
                indexed.put("title:" + title, row);
            }
        }
        return indexed;
    }

    private void enrichEpisode(Channel channel, Map<String, JSONObject> episodesMeta) {
        if (episodesMeta == null || episodesMeta.isEmpty() || channel == null) {
            return;
        }
        String season = safeNumeric(channel.getSeason());
        String episode = safeNumeric(channel.getEpisodeNum());
        JSONObject meta = null;
        if (!isBlank(season) && !isBlank(episode)) {
            meta = episodesMeta.get(season + ":" + episode);
        }
        if (meta == null) {
            meta = episodesMeta.get("title:" + normalize(channel.getName()));
        }
        if (meta == null) {
            return;
        }
        if (isBlank(channel.getDescription())) {
            channel.setDescription(meta.optString("plot", ""));
        }
        if (isBlank(channel.getReleaseDate())) {
            channel.setReleaseDate(meta.optString("releaseDate", ""));
        }
        if (!isBlank(meta.optString("logo", ""))) {
            channel.setLogo(meta.optString("logo", ""));
        }
    }

    private String normalize(String value) {
        if (isBlank(value)) {
            return "";
        }
        return value.toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String safeNumeric(String value) {
        if (isBlank(value)) return "";
        String normalized = value.replaceAll("[^0-9]", "");
        return isBlank(normalized) ? "" : normalized;
    }

    private void applyNameYearFallback(JSONObject seasonInfo, String rawSeriesName) {
        if (isBlank(rawSeriesName)) {
            return;
        }

        String trimmed = rawSeriesName.trim();
        String inferredName = trimmed.replaceAll("\\s*\\((19|20)\\d{2}\\)\\s*$", "").trim();
        String inferredYear = "";
        java.util.regex.Matcher m = java.util.regex.Pattern.compile("\\((19|20)\\d{2}\\)\\s*$").matcher(trimmed);
        if (m.find()) {
            inferredYear = m.group().replaceAll("[^0-9]", "");
        }

        if (isBlank(seasonInfo.optString("name", "")) && !isBlank(inferredName)) {
            seasonInfo.put("name", inferredName);
        }
        if (isBlank(seasonInfo.optString("releaseDate", "")) && !isBlank(inferredYear)) {
            seasonInfo.put("releaseDate", inferredYear);
        }
    }

    private void enrichEpisodesInResponse(JSONObject response) {
        if (response == null) {
            return;
        }
        JSONArray episodes = response.optJSONArray("episodes");
        JSONArray episodesMeta = response.optJSONArray("episodesMeta");
        if (episodes == null || episodes.isEmpty() || episodesMeta == null || episodesMeta.isEmpty()) {
            return;
        }
        Map<String, JSONObject> indexed = indexEpisodesMeta(episodesMeta);
        if (indexed.isEmpty()) {
            return;
        }
        for (int i = 0; i < episodes.length(); i++) {
            JSONObject row = episodes.optJSONObject(i);
            if (row == null) continue;
            Channel channel = Channel.fromJson(row.toString());
            if (channel == null) continue;
            enrichEpisode(channel, indexed);
            episodes.put(i, new JSONObject(channel.toJson()));
        }
    }

    private List<String> buildFuzzyHints(String baseTitle, JSONObject seasonInfo, JSONArray episodes) {
        List<String> hints = new ArrayList<>();
        addHint(hints, baseTitle);
        if (seasonInfo != null) {
            addHint(hints, seasonInfo.optString("name", ""));
            addHint(hints, seasonInfo.optString("plot", ""));
            addHint(hints, seasonInfo.optString("releaseDate", ""));
        }
        if (episodes != null) {
            for (int i = 0; i < Math.min(8, episodes.length()); i++) {
                JSONObject row = episodes.optJSONObject(i);
                if (row == null) continue;
                addHint(hints, row.optString("name", ""));
                addHint(hints, row.optString("releaseDate", ""));
            }
        }
        return hints;
    }

    private void addHint(List<String> hints, String value) {
        if (hints == null || isBlank(value)) {
            return;
        }
        String cleaned = value
                .replaceAll("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b", " ")
                .replaceAll("(?i)\\bs\\d{1,2}e\\d{1,3}\\b", " ")
                .replaceAll("[\\[\\]{}()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (isBlank(cleaned) || cleaned.length() < 2 || hints.contains(cleaned)) {
            return;
        }
        hints.add(cleaned);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!isBlank(value)) return value;
        }
        return "";
    }
}
