package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpConfigJsonServer implements HttpHandler {
    @Override
    public void handle(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        ConfigurationApplicationService service = ConfigurationApplicationService.getInstance();
        Configuration config = service.readConfiguration();

        if ("POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method)) {
            try {
                String body = IOUtils.toString(exchange.getRequestBody(), StandardCharsets.UTF_8);
                if (body != null && !body.isBlank() && config != null) {
                    JSONObject req = new JSONObject(body);
                    if (req.has("wideView")) {
                        config.setWideView(req.getBoolean("wideView"));
                    }
                    if (req.has("enableThumbnails")) {
                        config.setEnableThumbnails(req.getBoolean("enableThumbnails"));
                    }
                    if (req.has("darkTheme")) {
                        config.setDarkTheme(req.getBoolean("darkTheme"));
                    }
                    service.saveConfiguration(config);
                    config = service.readConfiguration();
                }
            } catch (Exception _) {
                // Ignore parsing errors and return current state
            }
        }

        JSONObject response = new JSONObject();
        response.put("enableThumbnails", config == null || config.isEnableThumbnails());
        response.put("wideView", config != null && config.isWideView());
        response.put("darkTheme", config != null && config.isDarkTheme());
        generateJsonResponse(exchange, response.toString());
    }
}
