package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.WatchingNowApplicationService;
import com.uiptv.application.WatchingNowVodRow;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class HttpWatchingNowVodJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        if (!"GET".equalsIgnoreCase(method)) {
            ex.getResponseHeaders().set("Allow", "GET");
            ex.sendResponseHeaders(405, -1);
            return;
        }
        byte[] responseBytes = buildPayload().getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(200, responseBytes.length);
        ex.getResponseBody().write(responseBytes);
        ex.getResponseBody().close();
    }

    private String buildPayload() {
        List<WatchingNowVodRow> rows = WatchingNowApplicationService.getInstance().listVodRows();
        JSONArray payload = new JSONArray();
        for (WatchingNowVodRow row : rows) {
            payload.put(toJson(row));
        }
        return payload.toString();
    }

    private JSONObject toJson(WatchingNowVodRow row) {
        JSONObject item = new JSONObject();
        item.put("accountId", row.accountId());
        item.put("accountName", row.accountName());
        item.put("accountType", row.accountType());
        item.put("categoryId", row.categoryId());
        item.put("vodId", row.vodId());
        item.put("vodName", row.vodName());
        item.put("vodLogo", row.vodLogo());
        item.put("plot", row.plot());
        item.put("releaseDate", row.releaseDate());
        item.put("rating", row.rating());
        item.put("duration", row.duration());
        item.put("updatedAt", row.updatedAt());
        if (row.playItem() != null) {
            item.put("playItem", new JSONObject(row.playItem().toJson()));
        }
        return item;
    }
}
