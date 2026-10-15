package com.uiptv.server.api.json;

import com.sun.net.httpserver.*;
import org.json.JSONObject;
import java.io.IOException;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

/** Explicit refresh clears memory and disk guide caches, never catalogs or bookmarks. */
public class HttpEpgCacheJsonServer implements HttpHandler {
    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST"); exchange.sendResponseHeaders(405, -1); return;
        }
        new com.uiptv.application.EpgCacheRefreshService().clearAndRefresh();
        writeJsonResponse(exchange, 200, new JSONObject().put("cleared", true).toString());
    }
}
