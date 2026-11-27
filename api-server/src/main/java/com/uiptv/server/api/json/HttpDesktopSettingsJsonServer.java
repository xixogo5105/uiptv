package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeMode;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.I18n;
import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Set;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.readRequestBodyText;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public final class HttpDesktopSettingsJsonServer implements HttpHandler {
    private static final Set<String> STRING_FIELDS = Set.of(
            "playerPath1", "playerPath2", "playerPath3", "defaultPlayerPath",
            "serverPort", "httpsServerPort", "cacheExpiryDays", "uiZoomPercent",
            "languageLocale", "themeMode", "tmdbReadAccessToken");
    private static final Set<String> BOOLEAN_FIELDS = Set.of(
            "embeddedPlayer", "wideView", "autoRunServerOnStartup", "httpsServerEnabled",
            "enableThumbnails", "resolveChainAndDeepRedirects", "webPlaybackProxyEnabled",
            "showBookmarkAndAccountCounts", "epgEnabled");
    private static final Set<String> SUPPORTED_FIELDS = Set.of(
            "playerPath1", "playerPath2", "playerPath3", "defaultPlayerPath",
            "serverPort", "httpsServerPort", "cacheExpiryDays", "uiZoomPercent",
            "languageLocale", "themeMode", "tmdbReadAccessToken", "embeddedPlayer",
            "wideView", "autoRunServerOnStartup", "httpsServerEnabled", "enableThumbnails",
            "resolveChainAndDeepRedirects", "webPlaybackProxyEnabled",
            "showBookmarkAndAccountCounts", "epgEnabled");

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            Configuration configuration = ConfigurationApplicationService.getInstance().readConfiguration();
            if (configuration == null) {
                writeJsonResponse(exchange, 500, "{\"error\":\"Configuration is unavailable\"}");
                return;
            }
            generateJsonResponse(exchange, buildResponse(configuration, false).toString());
            return;
        }
        if (!"PUT".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET,PUT");
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }

        JSONObject request;
        try {
            request = new JSONObject(readRequestBodyText(exchange));
            validateRequestKeys(request);
        } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
            writeJsonResponse(exchange, 413, "{\"error\":\"Request body is too large\"}");
            return;
        } catch (RuntimeException error) {
            writeJsonResponse(exchange, 400, new JSONObject()
                    .put("error", error.getMessage() == null ? "Invalid settings request" : error.getMessage())
                    .toString());
            return;
        }

        ConfigurationApplicationService service = ConfigurationApplicationService.getInstance();
        Configuration configuration = service.readConfiguration();
        if (configuration == null) {
            writeJsonResponse(exchange, 500, "{\"error\":\"Configuration is unavailable\"}");
            return;
        }
        boolean restartRequired = hasRestartSensitiveChanges(configuration, request);
        try {
            applyRequest(configuration, request);
        } catch (IllegalArgumentException error) {
            writeJsonResponse(exchange, 400, new JSONObject().put("error", error.getMessage()).toString());
            return;
        }
        service.saveConfiguration(configuration);
        String languageLocale = configuration.getLanguageLocale();
        if (request.has("languageLocale")) {
            I18n.setLocale(languageLocale);
        }
        writeJsonResponse(exchange, 200, buildResponse(configuration, restartRequired).toString());
    }

    private void validateRequestKeys(JSONObject request) {
        for (String key : request.keySet()) {
            if (!SUPPORTED_FIELDS.contains(key)) {
                throw new IllegalArgumentException("Unsupported settings field: " + key);
            }
            if (STRING_FIELDS.contains(key)) {
                Object value = request.get(key);
                if (!(value instanceof String)) {
                    throw new IllegalArgumentException(key + " must be a string");
                }
                int maxLength = "tmdbReadAccessToken".equals(key) ? 4096 : 2048;
                if (((String) value).length() > maxLength) {
                    throw new IllegalArgumentException(key + " is too long");
                }
            } else if (BOOLEAN_FIELDS.contains(key) && !(request.get(key) instanceof Boolean)) {
                throw new IllegalArgumentException(key + " must be a boolean");
            }
        }
    }

    private void applyRequest(Configuration configuration, JSONObject request) {
        setString(request, "playerPath1", configuration::setPlayerPath1);
        setString(request, "playerPath2", configuration::setPlayerPath2);
        setString(request, "playerPath3", configuration::setPlayerPath3);
        setString(request, "defaultPlayerPath", configuration::setDefaultPlayerPath);
        setString(request, "serverPort", value -> configuration.setServerPort(validatePort("serverPort", value, false)));
        setString(request, "httpsServerPort", value -> configuration.setHttpsServerPort(validatePort("httpsServerPort", value, true)));
        setString(request, "cacheExpiryDays", value -> configuration.setCacheExpiryDays(validateCacheExpiry(value)));
        setString(request, "uiZoomPercent", value -> configuration.setUiZoomPercent(validateZoom(value)));
        setString(request, "languageLocale", value -> configuration.setLanguageLocale(validateLanguage(value)));
        setString(request, "themeMode", value -> configuration.applyThemeMode(validateTheme(value)));
        setString(request, "tmdbReadAccessToken", configuration::setTmdbReadAccessToken);
        setBoolean(request, "embeddedPlayer", configuration::setEmbeddedPlayer);
        setBoolean(request, "wideView", configuration::setWideView);
        setBoolean(request, "autoRunServerOnStartup", configuration::setAutoRunServerOnStartup);
        setBoolean(request, "httpsServerEnabled", configuration::setHttpsServerEnabled);
        setBoolean(request, "enableThumbnails", configuration::setEnableThumbnails);
        setBoolean(request, "resolveChainAndDeepRedirects", configuration::setResolveChainAndDeepRedirects);
        setBoolean(request, "webPlaybackProxyEnabled", configuration::setWebPlaybackProxyEnabled);
        setBoolean(request, "showBookmarkAndAccountCounts", configuration::setShowBookmarkAndAccountCounts);
        setBoolean(request, "epgEnabled", configuration::setEpgEnabled);
    }

    private JSONObject buildResponse(Configuration configuration, boolean restartRequired) {
        JSONArray languages = new JSONArray();
        I18n.getSupportedLanguages().stream().map(language -> new JSONObject()
                .put("tag", language.languageTag())
                .put("name", language.nativeDisplayName())).forEach(languages::put);
        JSONArray zoomOptions = new JSONArray(ConfigurationService.FIREFOX_ZOOM_PERCENT_OPTIONS);
        String tmdbToken = configuration.getTmdbReadAccessToken();
        String filterLockHash = configuration.getFilterLockHash();
        return new JSONObject()
                .put("playerPath1", safe(configuration.getPlayerPath1()))
                .put("playerPath2", safe(configuration.getPlayerPath2()))
                .put("playerPath3", safe(configuration.getPlayerPath3()))
                .put("defaultPlayerPath", safe(configuration.getDefaultPlayerPath()))
                .put("embeddedPlayerPath", "__embedded_player__")
                .put("webBrowserPlayerPath", "__web_browser_player__")
                .put("embeddedPlayer", configuration.isEmbeddedPlayer())
                .put("wideView", configuration.isWideView())
                .put("resolveChainAndDeepRedirects", configuration.isResolveChainAndDeepRedirects())
                .put("enableThumbnails", configuration.isEnableThumbnails())
                .put("showBookmarkAndAccountCounts", configuration.isShowBookmarkAndAccountCounts())
                .put("epgEnabled", configuration.isEpgEnabled())
                .put("themeMode", configuration.resolveThemeMode().persistedValue())
                .put("languageLocale", safe(configuration.getLanguageLocale()))
                .put("languageOptions", languages)
                .put("uiZoomPercent", ConfigurationService.getInstance().normalizeUiZoomPercent(configuration.getUiZoomPercent()))
                .put("uiZoomOptions", zoomOptions)
                .put("cacheExpiryDays", ConfigurationService.getInstance().normalizeCacheExpiryDays(configuration.getCacheExpiryDays()))
                .put("serverPort", safe(configuration.getServerPort()))
                .put("httpsServerPort", safe(configuration.getHttpsServerPort()))
                .put("httpsServerEnabled", configuration.isHttpsServerEnabled())
                .put("autoRunServerOnStartup", configuration.isAutoRunServerOnStartup())
                .put("webPlaybackProxyEnabled", configuration.isWebPlaybackProxyEnabled())
                .put("tmdbTokenConfigured", tmdbToken != null && !tmdbToken.isBlank())
                .put("filterLockConfigured", filterLockHash != null && !filterLockHash.isBlank())
                .put("restartRequired", restartRequired);
    }

    private boolean hasRestartSensitiveChanges(Configuration configuration, JSONObject request) {
        return (request.has("serverPort") && !request.optString("serverPort").equals(configuration.getServerPort()))
                || (request.has("httpsServerPort")
                && !request.optString("httpsServerPort").equals(configuration.getHttpsServerPort()))
                || (request.has("httpsServerEnabled")
                && request.optBoolean("httpsServerEnabled") != configuration.isHttpsServerEnabled())
                || (request.has("embeddedPlayer")
                && request.optBoolean("embeddedPlayer") != configuration.isEmbeddedPlayer());
    }

    private void setString(JSONObject request, String key, java.util.function.Consumer<String> setter) {
        if (request.has(key)) {
            setter.accept(request.getString(key).trim());
        }
    }

    private void setBoolean(JSONObject request, String key, java.util.function.Consumer<Boolean> setter) {
        if (request.has(key)) {
            setter.accept(request.getBoolean(key));
        }
    }

    private String validatePort(String field, String value, boolean allowBlank) {
        if (allowBlank && value.isBlank()) {
            return "";
        }
        try {
            int port = Integer.parseInt(value);
            if (port < 1 || port > 65_535) {
                throw new NumberFormatException();
            }
            return Integer.toString(port);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException(field + " must be between 1 and 65535");
        }
    }

    private String validateCacheExpiry(String value) {
        try {
            int days = Integer.parseInt(value);
            if (days < 1 || days > 3650) {
                throw new NumberFormatException();
            }
            return Integer.toString(days);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("cacheExpiryDays must be between 1 and 3650");
        }
    }

    private String validateZoom(String value) {
        try {
            int zoom = Integer.parseInt(value);
            if (!ConfigurationService.FIREFOX_ZOOM_PERCENT_OPTIONS.contains(zoom)) {
                throw new NumberFormatException();
            }
            return Integer.toString(zoom);
        } catch (NumberFormatException _) {
            throw new IllegalArgumentException("uiZoomPercent is not a supported zoom value");
        }
    }

    private String validateLanguage(String value) {
        return I18n.getSupportedLanguages().stream()
                .filter(language -> language.languageTag().equalsIgnoreCase(value))
                .map(I18n.SupportedLanguage::languageTag)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("languageLocale is not supported"));
    }

    private ThemeMode validateTheme(String value) {
        for (ThemeMode themeMode : ThemeMode.values()) {
            if (themeMode.persistedValue().equalsIgnoreCase(value)) {
                return themeMode;
            }
        }
        throw new IllegalArgumentException("themeMode must be light, dark, or system");
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}