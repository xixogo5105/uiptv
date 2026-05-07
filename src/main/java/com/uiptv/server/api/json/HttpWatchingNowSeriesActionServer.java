package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class HttpWatchingNowSeriesActionServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("POST".equalsIgnoreCase(method)) {
            upsert(ex);
            return;
        }
        if ("DELETE".equalsIgnoreCase(method)) {
            remove(ex);
            return;
        }
        ex.getResponseHeaders().set("Allow", "POST,DELETE");
        ex.sendResponseHeaders(405, -1);
    }

    private void upsert(HttpExchange ex) throws IOException {
        JSONObject body = readBodyJson(ex);
        String accountId = opt(body, "accountId");
        String categoryId = opt(body, "categoryId");
        String seriesId = opt(body, "seriesId");
        String episodeId = opt(body, "episodeId");
        String episodeName = opt(body, "episodeName");
        String season = opt(body, "season");
        String episodeNum = opt(body, "episodeNum");
        String categoryDbId = opt(body, "categoryDbId");
        String seriesTitle = opt(body, "seriesTitle");
        String seriesPoster = opt(body, "seriesPoster");
        if (isBlank(accountId) || isBlank(seriesId) || isBlank(episodeId)) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"accountId, seriesId, episodeId are required\"}");
            return;
        }
        Account account = AccountService.getInstance().getById(accountId);
        if (account == null) {
            writeJson(ex, 404, "{\"status\":\"error\",\"message\":\"account not found\"}");
            return;
        }
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                account,
                categoryId,
                seriesId,
                episodeId,
                episodeName,
                season,
                episodeNum
        );
        SeriesWatchingNowSnapshotService.getInstance().saveChannels(
                account,
                categoryId,
                seriesId,
                categoryDbId,
                seriesTitle,
                seriesPoster,
                parseChannels(body.optJSONArray("episodes"))
        );
        writeJson(ex, 200, "{\"status\":\"ok\"}");
    }

    private void remove(HttpExchange ex) throws IOException {
        JSONObject body = readBodyJson(ex);
        String accountId = opt(body, "accountId");
        String categoryId = opt(body, "categoryId");
        String seriesId = opt(body, "seriesId");
        if (isBlank(accountId) || isBlank(seriesId)) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"accountId and seriesId are required\"}");
            return;
        }
        SeriesWatchStateService.getInstance().clearSeriesLastWatched(accountId, categoryId, seriesId);
        writeJson(ex, 200, "{\"status\":\"ok\"}");
    }

    private JSONObject readBodyJson(HttpExchange ex) throws IOException {
        try (InputStream input = ex.getRequestBody()) {
            String body = new String(input.readAllBytes(), StandardCharsets.UTF_8);
            if (body.isBlank()) {
                return new JSONObject();
            }
            return new JSONObject(body);
        }
    }

    private String opt(JSONObject body, String key) {
        if (body == null || !body.has(key) || body.isNull(key)) {
            return "";
        }
        return String.valueOf(body.opt(key)).trim();
    }

    private List<Channel> parseChannels(JSONArray payload) {
        if (payload == null || payload.isEmpty()) {
            return List.of();
        }
        List<Channel> channels = new ArrayList<>();
        for (int i = 0; i < payload.length(); i++) {
            Object value = payload.opt(i);
            if (value == null) {
                continue;
            }
            Channel channel = Channel.fromJson(String.valueOf(value));
            if (channel != null) {
                channels.add(channel);
            }
        }
        return channels;
    }

    private void writeJson(HttpExchange ex, int status, String body) throws IOException {
        byte[] responseBytes = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST,DELETE,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,*");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, responseBytes.length);
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().close();
    }
}
