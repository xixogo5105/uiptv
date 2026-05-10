package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.readRequestBodyText;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public class HttpRemoteSyncCompleteServer implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            JSONObject payload = new JSONObject(readRequestBodyText(exchange));
            RemoteSyncSessionService.getInstance().completeImport(
                    payload.optString("sessionId", ""),
                    payload.optBoolean("success", false),
                    payload.optString("message", "")
            );
            writeJsonResponse(exchange, 200, new JSONObject().put("status", "ok").toString());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            writeJsonResponse(exchange, 400, new JSONObject().put("message", ex.getMessage()).toString());
        }
    }
}
