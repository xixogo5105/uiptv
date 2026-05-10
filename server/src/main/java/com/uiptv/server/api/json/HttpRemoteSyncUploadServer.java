package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.remotesync.RemoteSyncJson;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import org.json.JSONObject;

import java.io.IOException;
import java.sql.SQLException;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public class HttpRemoteSyncUploadServer implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "PUT");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            writeJsonResponse(exchange, 200, RemoteSyncJson.toJson(
                    RemoteSyncSessionService.getInstance().acceptUpload(getParam(exchange, "sessionId"), exchange.getRequestBody())
            ).toString());
        } catch (IllegalArgumentException | IllegalStateException ex) {
            writeJsonResponse(exchange, 400, new JSONObject().put("message", ex.getMessage()).toString());
        } catch (SQLException ex) {
            writeJsonResponse(exchange, 500, new JSONObject().put("message", ex.getMessage()).toString());
        }
    }
}
