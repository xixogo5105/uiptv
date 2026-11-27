package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.FilterLockService;
import com.uiptv.util.I18n;
import org.json.JSONObject;

import java.io.IOException;
import java.util.Set;

import static com.uiptv.util.ServerUtils.generateJsonResponse;
import static com.uiptv.util.ServerUtils.readRequestBodyText;
import static com.uiptv.util.ServerUtils.writeJsonResponse;

public final class HttpDesktopFilterLockJsonServer implements HttpHandler {
    private static final Set<String> PASSWORD_FIELDS = Set.of(
            "action", "currentPassword", "newPassword");
    private static final Set<String> FILTER_FIELDS = Set.of(
            "filterCategoriesList", "filterChannelsList", "restrictionsActive");
    private final FilterLockService filterLockService = FilterLockService.getInstance();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if (exchange.getRequestURI().getPath().endsWith("/filters")) {
            handleFilters(exchange);
        } else {
            handlePassword(exchange);
        }
    }

    private void handlePassword(HttpExchange exchange) throws IOException {
        String method = exchange.getRequestMethod();
        if ("GET".equalsIgnoreCase(method)) {
            generateJsonResponse(exchange, lockStatus().toString());
            return;
        }
        if (!"POST".equalsIgnoreCase(method)) {
            exchange.getResponseHeaders().set("Allow", "GET,POST");
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        JSONObject request = readJsonRequest(exchange);
        if (request == null) return;
        try {
            validateKeys(request, PASSWORD_FIELDS);
            String action = request.optString("action", "");
            Configuration configuration = ConfigurationService.getInstance().read();
            if (configuration == null) {
                writeJsonResponse(exchange, 500, "{\"error\":\"Configuration is unavailable\"}");
                return;
            }
            switch (action) {
                case "unlock" -> {
                    if (!filterLockService.unlockWithPassword(request.optString("currentPassword", ""))) {
                        writeLocalizedError(exchange, 401, "filterLockCurrentPasswordInvalid");
                        return;
                    }
                }
                case "lock" -> filterLockService.clearUnlockSession();
                case "setPassword" -> {
                    if (filterLockService.hasPasswordConfigured()) {
                        writeJsonResponse(exchange, 409, "{\"error\":\"Password is already configured\"}");
                        return;
                    }
                    filterLockService.applyInitialPassword(
                            configuration, request.optString("newPassword", ""));
                    ConfigurationApplicationService.getInstance().saveConfiguration(configuration);
                }
                case "changePassword" -> {
                    filterLockService.applyPasswordChange(
                            configuration,
                            request.optString("currentPassword", ""),
                            request.optString("newPassword", ""));
                    ConfigurationApplicationService.getInstance().saveConfiguration(configuration);
                }
                case "clearPassword" -> {
                    filterLockService.clearPassword(
                            configuration,
                            request.optString("currentPassword", ""));
                    ConfigurationApplicationService.getInstance().saveConfiguration(configuration);
                }
                default -> {
                    writeJsonResponse(exchange, 400, "{\"error\":\"Unsupported filter-lock action\"}");
                    return;
                }
            }
            writeJsonResponse(exchange, 200, lockStatus().toString());
        } catch (IllegalArgumentException error) {
            writeLocalizedError(exchange, 400, error.getMessage());
        }
    }

    private void handleFilters(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())
                && !"PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET,PUT");
            writeJsonResponse(exchange, 405, "{\"error\":\"Method not allowed\"}");
            return;
        }
        if (!filterLockService.isUnlocked()) {
            writeLocalizedError(exchange, 423, "filterLockHiddenCategoriesPrompt");
            return;
        }
        Configuration configuration = ConfigurationService.getInstance().read();
        if (configuration == null) {
            writeJsonResponse(exchange, 500, "{\"error\":\"Configuration is unavailable\"}");
            return;
        }
        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            JSONObject request = readJsonRequest(exchange);
            if (request == null) return;
            try {
                validateKeys(request, FILTER_FIELDS);
                setFilterString(request, "filterCategoriesList", configuration::setFilterCategoriesList);
                setFilterString(request, "filterChannelsList", configuration::setFilterChannelsList);
                if (request.has("restrictionsActive")) {
                    Object value = request.get("restrictionsActive");
                    if (!(value instanceof Boolean)) {
                        throw new IllegalArgumentException("restrictionsActive must be a boolean");
                    }
                    configuration.setPauseFiltering(!request.getBoolean("restrictionsActive"));
                }
                ConfigurationApplicationService.getInstance().saveConfiguration(configuration);
            } catch (IllegalArgumentException error) {
                writeJsonResponse(exchange, 400, new JSONObject().put("error", error.getMessage()).toString());
                return;
            }
        }
        String response = new JSONObject()
                .put("filterCategoriesList", safe(configuration.getFilterCategoriesList()))
                .put("filterChannelsList", safe(configuration.getFilterChannelsList()))
                .put("restrictionsActive", !configuration.isPauseFiltering())
                .toString();
        if ("PUT".equalsIgnoreCase(exchange.getRequestMethod())) {
            writeJsonResponse(exchange, 200, response);
        } else {
            generateJsonResponse(exchange, response);
        }
    }

    private JSONObject lockStatus() {
        return new JSONObject()
                .put("passwordConfigured", filterLockService.hasPasswordConfigured())
                .put("unlocked", filterLockService.isUnlocked())
                .put("unlockWindowMinutes", filterLockService.getUnlockWindowMinutes())
                .put("minimumPasswordLength", filterLockService.getMinPasswordLength());
    }

    private JSONObject readJsonRequest(HttpExchange exchange) throws IOException {
        try {
            return new JSONObject(readRequestBodyText(exchange));
        } catch (com.uiptv.util.ServerUtils.RequestBodyTooLargeException _) {
            writeJsonResponse(exchange, 413, "{\"error\":\"Request body is too large\"}");
        } catch (org.json.JSONException _) {
            writeJsonResponse(exchange, 400, "{\"error\":\"A valid JSON request body is required\"}");
        }
        return null;
    }

    private void validateKeys(JSONObject request, Set<String> supported) {
        for (String key : request.keySet()) {
            if (!supported.contains(key)) {
                throw new IllegalArgumentException("Unsupported request field: " + key);
            }
            if (PASSWORD_FIELDS.contains(key)) {
                Object value = request.get(key);
                if (!(value instanceof String text)) {
                    throw new IllegalArgumentException(key + " must be a string");
                }
                if (text.length() > 1024) {
                    throw new IllegalArgumentException(key + " is too long");
                }
            }
        }
    }

    private void setFilterString(
            JSONObject request,
            String key,
            java.util.function.Consumer<String> setter) {
        if (!request.has(key)) return;
        Object value = request.get(key);
        if (!(value instanceof String text)) {
            throw new IllegalArgumentException(key + " must be a string");
        }
        if (text.length() > 65_536) {
            throw new IllegalArgumentException(key + " is too long");
        }
        setter.accept(text);
    }

    private void writeLocalizedError(HttpExchange exchange, int status, String key) throws IOException {
        String message = key == null ? "Request failed" : I18n.tr(key);
        writeJsonResponse(exchange, status, new JSONObject()
                .put("error", message)
                .put("errorKey", key == null ? "" : key)
                .toString());
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}