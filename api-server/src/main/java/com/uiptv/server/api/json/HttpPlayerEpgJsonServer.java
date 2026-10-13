package com.uiptv.server.api.json;

import com.sun.net.httpserver.*;
import com.uiptv.application.PlayerEpgApplicationService;
import org.json.JSONObject;
import java.io.IOException;
import static com.uiptv.util.ServerUtils.*;

public class HttpPlayerEpgJsonServer implements HttpHandler {
    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equals(exchange.getRequestMethod())) { exchange.getResponseHeaders().set("Allow", "GET"); exchange.sendResponseHeaders(405, -1); return; }
        var result = new PlayerEpgApplicationService().lookup(new PlayerEpgApplicationService.Query(
                getParam(exchange, "accountId"), getParam(exchange, "channelId"), getParam(exchange, "categoryId"), getParam(exchange, "bookmarkId")));
        JSONObject json = new JSONObject().put("channelName", result.channelName());
        json.put("now", result.now() == null ? JSONObject.NULL : new JSONObject(result.now()));
        json.put("next", result.next() == null ? JSONObject.NULL : new JSONObject(result.next()));
        generateJsonResponse(exchange, json.toString());
    }
}
