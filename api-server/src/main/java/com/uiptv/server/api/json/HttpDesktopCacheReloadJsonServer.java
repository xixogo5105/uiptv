package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static com.uiptv.util.ServerUtils.readRequestBodyText;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public final class HttpDesktopCacheReloadJsonServer implements HttpHandler {
    private static final String BASE_PATH = "/api/v1/cache-reloads";
    private static final Set<String> START_FIELDS = Set.of("accountIds", "failurePolicy");
    private static final Set<String> DECISION_FIELDS = Set.of("decision");
    private final DesktopCacheReloadJobManager jobs = DesktopCacheReloadJobManager.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String path = exchange.getRequestURI().getPath();
        if ((BASE_PATH + "/accounts").equals(path)
                && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            listAccounts(exchange);
            return;
        }
        if (BASE_PATH.equals(path)) {
            start(exchange);
            return;
        }
        if (!path.startsWith(BASE_PATH + "/")) {
            writeJsonResponse(exchange, 404, "{\"error\":\"Unknown cache reload job\"}");
            return;
        }
        String[] parts = path.substring(BASE_PATH.length() + 1).split("/");
        if (parts.length == 1 && "GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            get(exchange, parts[0]);
            return;
        }
        if (parts.length == 2 && "stop".equals(parts[1])
                && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            stop(exchange, parts[0]);
            return;
        }
        if (parts.length == 2 && "decision".equals(parts[1])
                && "POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            decide(exchange, parts[0]);
            return;
        }
        exchange.getResponseHeaders().set("Allow", "GET,POST");
        writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
    }

    private void listAccounts(HttpExchange exchange) throws IOException {
        JSONArray accounts = new JSONArray();
        jobs.availableAccounts().stream()
                .map(DesktopApiV1Dtos::account)
                .forEach(accounts::put);
        writeJsonResponse(exchange, 200, accounts.toString());
    }

    private void start(HttpExchange exchange) throws IOException {
        if (!"POST".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "POST");
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        JSONObject request = readJson(exchange);
        if (request == null) return;
        try {
            validateKeys(request, START_FIELDS);
            if (!(request.opt("accountIds") instanceof JSONArray accountIds)) {
                writeJsonResponse(exchange, 400, "{\"error\":\"accountIds must be an array\"}");
                return;
            }
            List<String> ids = new ArrayList<>(accountIds.length());
            for (int index = 0; index < accountIds.length(); index++) {
                Object value = accountIds.get(index);
                if (!(value instanceof String id)) {
                    writeJsonResponse(exchange, 400, "{\"error\":\"accountIds must contain strings\"}");
                    return;
                }
                ids.add(id);
            }
            DesktopCacheReloadJobManager.Job job = jobs.start(
                    ids,
                    request.optString("failurePolicy", "ask"));
            writeJsonResponse(exchange, 202, job.toJson().toString());
        } catch (IllegalArgumentException error) {
            writeJsonResponse(exchange, 400, new JSONObject().put("error", error.getMessage()).toString());
        } catch (IllegalStateException error) {
            writeJsonResponse(exchange, 409, new JSONObject().put("error", error.getMessage()).toString());
        }
    }

    private void get(HttpExchange exchange, String jobId) throws IOException {
        DesktopCacheReloadJobManager.Job job = jobs.get(jobId);
        if (job == null) {
            writeJsonResponse(exchange, 404, "{\"error\":\"Cache reload job not found\"}");
            return;
        }
        writeJsonResponse(exchange, 200, job.toJson().toString());
    }

    private void stop(HttpExchange exchange, String jobId) throws IOException {
        if (!jobs.stop(jobId)) {
            writeJsonResponse(exchange, 409, "{\"error\":\"Cache reload job is not running\"}");
            return;
        }
        writeJsonResponse(exchange, 200, jobs.get(jobId).toJson().toString());
    }

    private void decide(HttpExchange exchange, String jobId) throws IOException {
        JSONObject request = readJson(exchange);
        if (request == null) return;
        try {
            validateKeys(request, DECISION_FIELDS);
            String decision = request.optString("decision", "");
            if (!jobs.decide(jobId, decision)) {
                writeJsonResponse(exchange, 409, "{\"error\":\"No failure decision is pending\"}");
                return;
            }
            writeJsonResponse(exchange, 200, jobs.get(jobId).toJson().toString());
        } catch (IllegalArgumentException error) {
            writeJsonResponse(exchange, 400, new JSONObject().put("error", error.getMessage()).toString());
        }
    }

    private void validateKeys(JSONObject request, Set<String> supported) {
        for (String key : request.keySet()) {
            if (!supported.contains(key)) {
                throw new IllegalArgumentException("Unsupported request field: " + key);
            }
        }
    }

    private JSONObject readJson(HttpExchange exchange) throws IOException {
        try {
            return new JSONObject(readRequestBodyText(exchange));
        } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
            writeJsonResponse(exchange, 413, "{\"error\":\"Request body is too large\"}");
        } catch (JSONException _) {
            writeJsonResponse(exchange, 400, "{\"error\":\"A valid JSON request body is required\"}");
        }
        return null;
    }
}