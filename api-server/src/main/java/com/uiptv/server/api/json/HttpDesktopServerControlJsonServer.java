package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.readRequestBodyText;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public final class HttpDesktopServerControlJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            generateJsonResponse(exchange, status().toString());
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET,POST");
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        JSONObject request;
        try {
            request = new JSONObject(readRequestBodyText(exchange));
        } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
            writeJsonResponse(exchange, 413, "{\"error\":\"Request body is too large\"}");
            return;
        } catch (JSONException _) {
            writeJsonResponse(exchange, 400, "{\"error\":\"A valid JSON request body is required\"}");
            return;
        }
        String action = request.optString("action", "");
        ConfigurationApplicationService service = ConfigurationApplicationService.getInstance();
        try {
            if ("start".equalsIgnoreCase(action)) {
                service.startServer();
            } else if ("stop".equalsIgnoreCase(action)) {
                service.stopServer();
            } else {
                writeJsonResponse(exchange, 400, "{\"error\":\"action must be start or stop\"}");
                return;
            }
        } catch (IOException error) {
            com.uiptv.util.AppLog.addErrorLog(HttpDesktopServerControlJsonServer.class,
                    "Unable to change internal web server state (" + error.getClass().getSimpleName() + ")");
            writeJsonResponse(exchange, 409, "{\"error\":\"Unable to change the internal web server state\"}");
            return;
        }
        writeJsonResponse(exchange, 200, status().toString());
    }

    private JSONObject status() {
        Configuration configuration = ConfigurationApplicationService.getInstance().readConfiguration();
        String port = configuration == null ? "" : safe(configuration.getServerPort());
        String httpsPort = configuration == null ? "" : safe(configuration.getHttpsServerPort());
        boolean httpsEnabled = configuration != null && configuration.isHttpsServerEnabled();
        JSONObject response = new JSONObject()
                .put("running", ConfigurationApplicationService.getInstance().isServerRunning())
                .put("httpUrl", port.isBlank() ? "" : "http://127.0.0.1:" + port)
                .put("httpsUrl", !httpsEnabled || httpsPort.isBlank()
                        ? "" : "https://127.0.0.1:" + httpsPort);
        return response;
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}