package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.ServerUtils.writeBinaryResponse;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public class HttpRemoteSyncDownloadServer implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        try {
            Path snapshotPath = RemoteSyncSessionService.getInstance().getDownloadSnapshot(getParam(exchange, "sessionId"));
            writeBinaryResponse(exchange, 200, Files.readAllBytes(snapshotPath), "application/octet-stream");
        } catch (IllegalArgumentException | IllegalStateException ex) {
            writeJsonResponse(exchange, 400, new JSONObject().put("message", ex.getMessage()).toString());
        }
    }
}
