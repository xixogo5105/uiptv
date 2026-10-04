package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeMode;
import org.apache.commons.io.IOUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static com.uiptv.util.ServerUtils.generateJsonResponse;

public class HttpConfigJsonServer implements HttpHandler {
    private static final String KEY_WIDE_VIEW = "wideView";
    private static final String KEY_ENABLE_THUMBNAILS = "enableThumbnails";
    private static final String KEY_THEME_MODE = "themeMode";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        ConfigurationApplicationService service = ConfigurationApplicationService.getInstance();
        Configuration config = service.readConfiguration();

        if (isWriteRequest(exchange)) {
            config = handleConfigUpdate(exchange, service, config);
        }

        JSONObject response = buildConfigResponse(config);
        generateJsonResponse(exchange, response.toString());
    }

    private static boolean isWriteRequest(HttpExchange exchange) {
        String method = exchange.getRequestMethod();
        return "POST".equalsIgnoreCase(method) || "PUT".equalsIgnoreCase(method);
    }

    private Configuration handleConfigUpdate(HttpExchange exchange, ConfigurationApplicationService service, Configuration config) throws IOException {
        try {
            String body = IOUtils.toString(exchange.getRequestBody(), StandardCharsets.UTF_8);
            if (body != null && !body.isBlank() && config != null) {
                JSONObject req = new JSONObject(body);
                updateConfigFromRequest(config, req);
                service.saveConfiguration(config);
                config = service.readConfiguration();
            }
        } catch (Exception _) {
            // Ignore parsing errors and return current state
        }
        return config;
    }

    private void updateConfigFromRequest(Configuration config, JSONObject req) {
        if (req.has(KEY_WIDE_VIEW)) {
            config.setWideView(req.getBoolean(KEY_WIDE_VIEW));
        }
        if (req.has(KEY_ENABLE_THUMBNAILS)) {
            config.setEnableThumbnails(req.getBoolean(KEY_ENABLE_THUMBNAILS));
        }
    }

    private JSONObject buildConfigResponse(Configuration config) {
        JSONObject response = new JSONObject();
        response.put(KEY_ENABLE_THUMBNAILS, config == null || config.isEnableThumbnails());
        response.put(KEY_WIDE_VIEW, config != null && config.isWideView());
        // Read-only: reports the stored selection as light/dark/system. Replaces the removed darkTheme flag,
        // which could not describe the third state.
        response.put(KEY_THEME_MODE, config == null
                ? ThemeMode.LIGHT.persistedValue()
                : config.resolveThemeMode().persistedValue());
        return response;
    }
}
