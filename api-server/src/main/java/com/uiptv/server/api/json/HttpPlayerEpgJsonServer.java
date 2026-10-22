package com.uiptv.server.api.json;

import com.sun.net.httpserver.*;
import com.uiptv.application.EpgProgramme;
import com.uiptv.application.PlayerEpgApplicationService;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.IOException;
import java.util.List;
import static com.uiptv.util.ServerUtils.*;

public class HttpPlayerEpgJsonServer implements HttpHandler {
    private static final int MAX_SCHEDULE_PROGRAMMES = 100;

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { exchange.getResponseHeaders().set("Allow", "GET"); exchange.sendResponseHeaders(405, -1); return; }
        var service = new PlayerEpgApplicationService();
        var query = new PlayerEpgApplicationService.Query(
                getParam(exchange, "accountId"), getParam(exchange, "channelId"), getParam(exchange, "categoryId"), getParam(exchange, "bookmarkId"));
        var result = service.lookup(query);
        JSONObject json = new JSONObject().put("channelName", result.channelName());
        json.put("guideStatus", result.guideStatus());
        json.put("now", result.now() == null ? JSONObject.NULL : new JSONObject(result.now()));
        json.put("next", result.next() == null ? JSONObject.NULL : new JSONObject(result.next()));
        if ("1".equals(getParam(exchange, "details"))) {
            json.put("programmes", new JSONArray(boundedSchedule(service.lookupSchedule(query), getParam(exchange, "limit"))));
        }
        generateJsonResponse(exchange, json.toString());
    }

    static List<EpgProgramme> boundedSchedule(List<EpgProgramme> programmes, String requestedLimit) {
        int limit = MAX_SCHEDULE_PROGRAMMES;
        if (requestedLimit != null && !requestedLimit.isBlank()) {
            try { limit = Math.max(1, Math.min(MAX_SCHEDULE_PROGRAMMES, Integer.parseInt(requestedLimit))); }
            catch (NumberFormatException ignored) { }
        }
        return programmes.size() <= limit ? programmes : programmes.subList(0, limit);
    }
}
