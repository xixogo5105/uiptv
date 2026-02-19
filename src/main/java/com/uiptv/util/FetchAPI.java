package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.ui.LogDisplayUI;
import javafx.application.Platform;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.util.LogUtil.httpLog;

public class FetchAPI {
    public static String fetch(Map<String, String> params, final Account account) {
        try {
            String urlWithParams = account.getServerPortalUrl();
            if (params != null && !params.isEmpty()) {
                urlWithParams += "?" + mapToString(params);
            }
            
            Map<String, String> headers = headers(account.getUrl(), account);
            HttpResponse<String> response = HttpUtil.sendRequest(urlWithParams, headers, "GET");
            
            httpLog(account.getServerPortalUrl(), response.request(), response, params);
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                return response.body();
            }
        } catch (Exception ex) {
            Platform.runLater(() -> LogDisplayUI.addLog("Network Error: " + ex.getMessage()));
        }
        return StringUtils.EMPTY;
    }

    private static Map<String, String> headers(String url, Account account) {
        Map<String, String> headers = new HashMap<>();
        headers.put("User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3");
        headers.put("X-User-Agent", "Model: MAG250; Link: WiFi");
        headers.put("Referer", url);
        headers.put("Accept", "*/*");
        headers.put("Pragma", "no-cache");
        if (account.isConnected()) headers.put("Authorization", "Bearer " + account.getToken());
        headers.put("Cookie", "mac=" + account.getMacAddress() + "; stb_lang=en; timezone=GMT;");
        return headers;
    }

    private static String mapToString(Map<String, String> parameters) {
        return parameters.entrySet()
                .stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
    }

    public static boolean nullSafeBoolean(JSONObject jsonCategory, String key) {
        try {
            return jsonCategory.getBoolean(key);
        } catch (Exception ex) {
            return false;
        }
    }

    public static int nullSafeInteger(JSONObject jsonCategory, String key) {
        try {
            return jsonCategory.getInt(key);
        } catch (Exception ex) {
            return -1;
        }
    }

    public static String nullSafeString(JSONObject jsonCategory, String key) {
        try {
            return jsonCategory.getString(key);
        } catch (Exception ex) {
            return "";
        }
    }

    public enum ServerType {
        PORTAL("portal.php"),

        LOAD("load.php?");
        private String loader;

        ServerType(String loader) {
            this.loader = loader;
        }

        public String getLoader() {
            return loader;
        }

        public void setLoader(String loader) {
            this.loader = loader;
        }
    }
}
