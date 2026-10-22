package com.uiptv.server.api.json;

import com.sun.net.httpserver.*;
import org.json.*;
import java.io.IOException;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

/** User-triggered, cancellable guide refresh with source-level status polling. */
public class HttpEpgCacheJsonServer implements HttpHandler {
    @Override public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod().toUpperCase(java.util.Locale.ROOT);
        if ("POST".equals(method)) {
            var snapshot = com.uiptv.application.EpgRefreshJobService.start();
            writeJsonResponse(exchange, 202, snapshot(snapshot).toString());
            return;
        }
        String id = com.uiptv.util.ServerUtils.getParam(exchange, "jobId");
        if ("GET".equals(method)) {
            var current = com.uiptv.application.EpgRefreshJobService.get(id);
            if (current == null) { writeJsonResponse(exchange, 404, new JSONObject().put("error", "unknown_job").toString()); return; }
            writeJsonResponse(exchange, 200, snapshot(current).toString());
            return;
        }
        if ("DELETE".equals(method)) {
            boolean requested = com.uiptv.application.EpgRefreshJobService.cancel(id);
            writeJsonResponse(exchange, requested ? 202 : 404,
                    new JSONObject().put("cancellationRequested", requested).toString());
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET, POST, DELETE");
        exchange.sendResponseHeaders(405, -1);
    }

    private JSONObject snapshot(com.uiptv.application.EpgRefreshJobService.Snapshot snapshot) {
        JSONArray sources = new JSONArray();
        snapshot.sources().forEach(source -> sources.put(new JSONObject()
                .put("id", source.id()).put("name", source.name()).put("status", source.status())
                .put("detail", source.detail())));
        return new JSONObject().put("jobId", snapshot.id()).put("status", snapshot.status())
                .put("stage", snapshot.stage()).put("completed", snapshot.completed())
                .put("total", snapshot.total()).put("currentSource", snapshot.currentSource())
                .put("message", snapshot.message()).put("sources", sources)
                .put("preparationCompleted", snapshot.preparationCompleted())
                .put("preparationTotal", snapshot.preparationTotal())
                .put("currentAccount", snapshot.currentAccount())
                .put("startedAt", snapshot.startedAt()).put("finishedAt", snapshot.finishedAt());
    }
}
