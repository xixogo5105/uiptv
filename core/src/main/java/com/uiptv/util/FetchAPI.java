package com.uiptv.util;

import com.uiptv.model.Account;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.util.LogUtil.httpLog;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;

public class FetchAPI {
    private static final String PORTAL_PHP = "portal.php";

    public static String fetch(Map<String, String> params, final Account account) {
        return fetchWithDiagnostics(params, account, HttpUtil.RequestOptions.defaults()).body();
    }

    public static String fetch(Map<String, String> params, final Account account, HttpUtil.RequestOptions options) {
        return fetchWithDiagnostics(params, account, options).body();
    }

    /**
     * Performs a portal request and reports *why* it produced no payload.
     * <p>
     * The previous implementation collapsed every failure mode - DNS/connect/timeout errors,
     * non-200 responses and genuinely empty bodies - into the same empty string. Callers could
     * therefore not distinguish "the provider is unreachable" from "the provider returned no
     * data", which is what made intermittent multi-account bulk reload failures hard to attribute.
     */
    public static FetchResult fetchWithDiagnostics(Map<String, String> params, final Account account,
                                                   HttpUtil.RequestOptions options) {
        String requestUrl = "";
        try {
            String baseUrl = resolveBaseUrl(account);
            if (isBlank(baseUrl)) {
                throw new IllegalArgumentException("Target host is not specified");
            }
            String payload = params == null || params.isEmpty() ? "" : mapToString(params);
            String httpMethod = account.getHttpMethod() != null ? account.getHttpMethod() : "GET";
            boolean isPost = "POST".equalsIgnoreCase(httpMethod);

            requestUrl = baseUrl;
            if (!isPost && !payload.isEmpty()) {
                requestUrl += "?" + payload;
            }

            Map<String, String> headers = headers(account, isPost);
            HttpUtil.HttpResult response = HttpUtil.sendRequest(requestUrl, headers, httpMethod, isPost ? payload : null, options);

            httpLog(requestUrl, response, params);
            if (response.statusCode() == HttpUtil.STATUS_OK) {
                String body = response.body();
                if (isBlank(body)) {
                    return FetchResult.transportFailure(requestUrl, HttpUtil.STATUS_OK, "Empty response body with HTTP 200");
                }
                return FetchResult.success(body, HttpUtil.STATUS_OK);
            }
            return FetchResult.transportFailure(requestUrl, response.statusCode(),
                    "Unexpected HTTP status " + response.statusCode());
        } catch (Exception ex) {
            String reason = ex.getMessage() == null ? ex.getClass().getSimpleName() : ex.getMessage();
            AppLog.addWarningLog(FetchAPI.class, "Network Error: " + reason);
            return FetchResult.transportFailure(requestUrl, -1, reason);
        }
    }

    /**
     * Outcome of a portal request. {@code transportFailure} is true when no usable payload was
     * received, so callers can log the real cause instead of silently treating it as "no data".
     *
     * @param body           payload on success, empty otherwise
     * @param transportFailure true when no usable payload was received
     * @param statusCode     HTTP status, or -1 when the request never completed
     * @param endpoint       the URL that was contacted, for diagnostics
     * @param failureReason  human-readable cause, empty on success
     */
    public record FetchResult(String body, boolean transportFailure, int statusCode,
                              String endpoint, String failureReason) {
        public static FetchResult success(String body, int statusCode) {
            return new FetchResult(body == null ? StringUtils.EMPTY : body, false, statusCode, "", "");
        }

        public static FetchResult transportFailure(String requestUrl, int statusCode, String reason) {
            return new FetchResult(StringUtils.EMPTY, true, statusCode,
                    requestUrl == null ? "" : requestUrl, reason == null ? "" : reason);
        }
    }


    private static String resolveBaseUrl(Account account) {
        if (account == null) {
            return "";
        }
        String serverPortal = normalizeUrlCandidate(account.getServerPortalUrl(), true);
        if (isNotBlank(serverPortal)) {
            return serverPortal;
        }
        return normalizeUrlCandidate(account.getUrl(), true);
    }

    private static String normalizeUrlCandidate(String value, boolean appendPortalPhpWhenMissing) {
        if (isBlank(value)) {
            return "";
        }
        String candidate = value.trim();
        if (!candidate.contains("://")) {
            candidate = "http://" + candidate;
        }
        try {
            java.net.URI uri = java.net.URI.create(candidate);
            if (isBlank(uri.getHost())) {
                return "";
            }
            String path = uri.getPath() == null ? "" : uri.getPath().toLowerCase();
            if (appendPortalPhpWhenMissing
                    && !path.endsWith(PORTAL_PHP)
                    && !path.endsWith("load.php")) {
                if (candidate.endsWith("/")) {
                    return candidate + PORTAL_PHP;
                }
                return candidate + "/" + PORTAL_PHP;
            }
            return candidate;
        } catch (Exception _) {
            return "";
        }
    }

    private static Map<String, String> headers(Account account, boolean isPost) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3");
        headers.put("X-User-Agent", "Model: MAG250; Link: WiFi");
        String referer = isBlank(account.getServerPortalUrl()) ? account.getUrl() : account.getServerPortalUrl();
        headers.put("Referer", referer);
        headers.put("Accept", "*/*");
        headers.put("Pragma", "no-cache");
        if (account.isConnected()) headers.put("Authorization", "Bearer " + account.getToken());
        String timezone = account.getTimezone() != null ? account.getTimezone() : "Europe/London";
        headers.put("Cookie", "mac=" + account.getMacAddress() + "; stb_lang=en; timezone=" + timezone + ";");
        if (isPost) {
            headers.put("Content-Type", "application/x-www-form-urlencoded");
        }
        return headers;
    }

    private static String mapToString(Map<String, String> parameters) {
        return parameters.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue() == null ? "" : e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static boolean nullSafeBoolean(JSONObject jsonCategory, String key) {
        try {
            return jsonCategory.getBoolean(key);
        } catch (Exception _) {
            return false;
        }
    }

    public static int nullSafeInteger(JSONObject jsonCategory, String key) {
        try {
            return jsonCategory.getInt(key);
        } catch (Exception _) {
            return -1;
        }
    }

    public static String nullSafeString(JSONObject jsonCategory, String key) {
        try {
            return jsonCategory.getString(key);
        } catch (Exception _) {
            return "";
        }
    }

    public enum ServerType {
        PORTAL(PORTAL_PHP),

        LOAD("load.php?");
        private final String loader;

        ServerType(String loader) {
            this.loader = loader;
        }

        public String getLoader() {
            return loader;
        }
    }
}
