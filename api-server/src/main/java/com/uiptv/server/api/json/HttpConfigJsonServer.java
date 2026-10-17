package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeMode;
import org.json.JSONObject;

import java.io.IOException;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.readRequestBodyText;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public class HttpConfigJsonServer implements HttpHandler {
    private static final String KEY_WIDE_VIEW = "wideView";
    private static final String KEY_ENABLE_THUMBNAILS = "enableThumbnails";
    private static final String KEY_WEB_PLAYBACK_PROXY_ENABLED = "webPlaybackProxyEnabled";
    private static final String KEY_THEME_MODE = "themeMode";

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        ConfigurationApplicationService service = ConfigurationApplicationService.getInstance();
        Configuration config = service.readConfiguration();

        if (isWriteRequest(exchange)) {
            try {
                config = handleConfigUpdate(exchange, service, config);
            } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
                writeJsonResponse(exchange, 413, "{\"message\":\"Request body is too large\"}");
                return;
            }
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
            String body = readRequestBodyText(exchange);
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
        // EPG enablement is desktop-owned. The web client may observe it, but must not
        // change the desktop scheduler's setting through this shared configuration endpoint.
        if (req.has("epgPlaybackOnly")) {
            config.setEpgPlaybackOnly(req.getBoolean("epgPlaybackOnly"));
        }
    }

    private JSONObject buildConfigResponse(Configuration config) {
        JSONObject response = new JSONObject();
        response.put(KEY_ENABLE_THUMBNAILS, config == null || config.isEnableThumbnails());
        response.put(KEY_WEB_PLAYBACK_PROXY_ENABLED, config == null || config.isWebPlaybackProxyEnabled());
        response.put("epgEnabled", config != null && config.isEpgEnabled());
        response.put("epgPlaybackOnly", config != null && config.isEpgPlaybackOnly());
        response.put(KEY_WIDE_VIEW, config != null && config.isWideView());
        response.put("languageLocale", config == null ? "" : config.getLanguageLocale());
        // Read-only: reports the stored selection as light/dark/system. Replaces the removed darkTheme flag,
        // which could not describe the third state.
        response.put(KEY_THEME_MODE, config == null
                ? ThemeMode.LIGHT.persistedValue()
                : config.resolveThemeMode().persistedValue());
        return response;
    }
}
