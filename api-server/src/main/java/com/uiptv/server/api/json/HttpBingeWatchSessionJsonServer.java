package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.BingeWatchService;
import com.uiptv.service.SeriesWatchStateService;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class HttpBingeWatchSessionJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if ("POST".equalsIgnoreCase(method)) {
            createSession(ex);
            return;
        }
        ex.getResponseHeaders().set("Allow", "POST");
        ex.sendResponseHeaders(405, -1);
    }

    private void createSession(HttpExchange ex) throws IOException {
        JSONObject body = readBodyJson(ex);
        String accountId = opt(body, "accountId");
        String categoryId = opt(body, "categoryId");
        String seriesId = opt(body, "seriesId");
        String season = opt(body, "season");
        List<Channel> episodes = parseChannels(body.optJSONArray("episodes"));

        if (isBlank(accountId) || isBlank(seriesId) || isBlank(season) || episodes.isEmpty()) {
            writeJson(ex, 400, "{\"status\":\"error\",\"message\":\"accountId, seriesId, season and episodes are required\"}");
            return;
        }

        Account account = AccountService.getInstance().getById(accountId);
        if (account == null) {
            writeJson(ex, 404, "{\"status\":\"error\",\"message\":\"account not found\"}");
            return;
        }
        account.setAction(Account.AccountAction.series);

        SeriesWatchState watchState = SeriesWatchStateService.getInstance()
                .getSeriesLastWatched(accountId, categoryId, seriesId);
        String token = BingeWatchService.getInstance().createSession(
                account,
                seriesId,
                categoryId,
                season,
                episodes,
                watchState
        );
        if (isBlank(token)) {
            writeJson(ex, 422, "{\"status\":\"error\",\"message\":\"unable to prepare binge watch session\"}");
            return;
        }

        JSONObject response = new JSONObject();
        response.put("status", "ok");
        response.put("token", token);
        response.put("playlistUrl", BingeWatchService.getInstance().buildPlaylistUrl(token));
        writeJson(ex, 200, response.toString());
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
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "POST,OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type,*");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(status, responseBytes.length);
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().close();
    }
}
