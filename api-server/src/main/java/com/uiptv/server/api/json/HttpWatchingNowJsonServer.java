package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.WatchingNowApplicationService;
import com.uiptv.application.WatchingNowSeriesRow;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.List;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpWatchingNowJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        generateJsonResponse(ex, toJson(WatchingNowApplicationService.getInstance().listSeriesRows()));
    }

    private String toJson(List<WatchingNowSeriesRow> rows) {
        JSONArray payload = new JSONArray();
        for (WatchingNowSeriesRow row : rows) {
            payload.put(toJson(row));
        }
        return payload.toString();
    }

    private JSONObject toJson(WatchingNowSeriesRow row) {
        JSONObject json = new JSONObject();
        json.put("key", row.accountId() + "|" + row.seriesId());
        json.put("accountId", row.accountId());
        json.put("accountName", row.accountName());
        json.put("accountType", row.accountType());
        json.put("categoryId", row.categoryId());
        json.put("categoryDbId", row.categoryDbId());
        json.put("seriesId", row.seriesId());
        json.put("episodeId", row.episodeId());
        json.put("episodeName", row.episodeName());
        json.put("season", row.season());
        json.put("episodeNum", row.episodeNum());
        json.put("seriesTitle", row.seriesTitle());
        json.put("seriesPoster", row.seriesPoster());
        json.put("updatedAt", row.updatedAt());
        return json;
    }
}
