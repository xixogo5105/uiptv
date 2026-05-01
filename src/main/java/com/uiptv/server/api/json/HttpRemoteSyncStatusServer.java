package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.remotesync.RemoteSyncJson;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public class HttpRemoteSyncStatusServer implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            writeJsonResponse(exchange, 200, RemoteSyncJson.toJson(
                    RemoteSyncSessionService.getInstance().getSessionState(getParam(exchange, "sessionId"))
            ).toString());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            writeJsonResponse(exchange, 400, new JSONObject().put("message", ex.getMessage()).toString());
        }
    }
}
