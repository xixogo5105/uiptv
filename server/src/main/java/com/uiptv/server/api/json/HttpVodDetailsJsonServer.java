package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.ImdbMetadataService;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpVodDetailsJsonServer implements HttpHandler {
    private static final String KEY_COVER = "cover";
    private static final String KEY_DIRECTOR = "director";
    private static final String KEY_GENRE = "genre";
    private static final String KEY_IMDB_URL = "imdbUrl";
    private static final String KEY_RATING = "rating";
    private static final String KEY_RELEASE_DATE = "releaseDate";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        Account account = AccountService.getInstance().getById(getParam(ex, "accountId"));
        String categoryId = getParam(ex, "categoryId");
        String channelId = getParam(ex, "channelId");
        String vodName = getParam(ex, "vodName");

        JSONObject vodInfo = new JSONObject();
        vodInfo.put("name", isBlank(vodName) ? "VOD" : vodName);
        vodInfo.put(KEY_COVER, "");
        vodInfo.put("plot", "");
        vodInfo.put("cast", "");
        vodInfo.put(KEY_DIRECTOR, "");
        vodInfo.put(KEY_GENRE, "");
        vodInfo.put(KEY_RELEASE_DATE, "");
        vodInfo.put(KEY_RATING, "");
        vodInfo.put("tmdb", "");
        vodInfo.put(KEY_IMDB_URL, "");
        vodInfo.put("duration", "");

        if (account != null && account.isNotConnected()) {
            HandshakeService.getInstance().connect(account);
        }

        Channel providerChannel = null;
        if (account != null && !isBlank(channelId)) {
            providerChannel = VodChannelDb.get().getChannelByChannelId(channelId, categoryId, account.getDbId());
            if (providerChannel == null) {
                providerChannel = ChannelDb.get().getChannelById(channelId, categoryId);
            }
        }

        if (providerChannel != null) {
            mergeMissing(vodInfo, "name", providerChannel.getName());
            mergeMissing(vodInfo, KEY_COVER, providerChannel.getLogo());
            mergeMissing(vodInfo, "plot", providerChannel.getDescription());
            mergeMissing(vodInfo, KEY_RELEASE_DATE, providerChannel.getReleaseDate());
            mergeMissing(vodInfo, KEY_RATING, providerChannel.getRating());
            mergeMissing(vodInfo, "duration", providerChannel.getDuration());
        }

        String queryTitle = isBlank(vodName) ? vodInfo.optString("name", "") : vodName;
        List<String> fuzzyHints = buildFuzzyHints(queryTitle, providerChannel, vodInfo);
        JSONObject imdbFirst = ImdbMetadataService.getInstance().findBestEffortMovieDetails(
                queryTitle,
                vodInfo.optString("tmdb", ""),
                fuzzyHints
        );
        mergeMissing(vodInfo, "name", imdbFirst.optString("name", ""));
        mergeMissing(vodInfo, KEY_COVER, imdbFirst.optString(KEY_COVER, ""));
        mergeMissing(vodInfo, "plot", imdbFirst.optString("plot", ""));
        mergeMissing(vodInfo, "cast", imdbFirst.optString("cast", ""));
        mergeMissing(vodInfo, KEY_DIRECTOR, imdbFirst.optString(KEY_DIRECTOR, ""));
        mergeMissing(vodInfo, KEY_GENRE, imdbFirst.optString(KEY_GENRE, ""));
        mergeMissing(vodInfo, KEY_RELEASE_DATE, imdbFirst.optString(KEY_RELEASE_DATE, ""));
        mergeMissing(vodInfo, KEY_RATING, imdbFirst.optString(KEY_RATING, ""));
        mergeMissing(vodInfo, "tmdb", imdbFirst.optString("tmdb", ""));
        mergeMissing(vodInfo, KEY_IMDB_URL, imdbFirst.optString(KEY_IMDB_URL, ""));

        JSONObject response = new JSONObject();
        response.put("vodInfo", vodInfo);
        generateJsonResponse(ex, response.toString());
    }

    private void mergeMissing(JSONObject target, String key, String incoming) {
        if (isBlank(target.optString(key, "")) && !isBlank(incoming)) {
            target.put(key, incoming);
        }
    }

    private List<String> buildFuzzyHints(String queryTitle, Channel providerChannel, JSONObject vodInfo) {
        List<String> hints = new ArrayList<>();
        addHint(hints, queryTitle);
        if (providerChannel != null) {
            addHint(hints, providerChannel.getName());
            addHint(hints, providerChannel.getDescription());
            addHint(hints, providerChannel.getReleaseDate());
        }
        if (vodInfo != null) {
            addHint(hints, vodInfo.optString("name", ""));
            addHint(hints, vodInfo.optString("plot", ""));
            addHint(hints, vodInfo.optString(KEY_RELEASE_DATE, ""));
        }
        return hints;
    }

    private void addHint(List<String> hints, String value) {
        if (hints == null || isBlank(value)) {
            return;
        }
        String cleaned = value
                .replaceAll("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b", " ")
                .replaceAll("[\\[\\]{}()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (isBlank(cleaned) || cleaned.length() < 2 || hints.contains(cleaned)) {
            return;
        }
        hints.add(cleaned);
    }
}
