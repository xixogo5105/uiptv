package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.ui.LogDisplayUI;
import org.json.JSONObject;

import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.uiptv.util.LogUtil.httpLog;
import static java.nio.charset.StandardCharsets.UTF_8;

public class FetchAPI {
    public static String fetch(Map<String, String> params, final Account account) {
        try {
            String urlWithParams = account.getServerPortalUrl();
            if (params != null && !params.isEmpty()) {
                urlWithParams += "?" + mapToString(params);
            }
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(urlWithParams))
                    .headers(headers(account.getUrl(), account))
                    .GET().version(HttpClient.Version.HTTP_1_1).build();
            HttpResponse<String> response = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .build()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            httpLog(account.getServerPortalUrl(), request, response, params);
            if (response.statusCode() == HttpURLConnection.HTTP_OK) {
                return response.body();
            }
        } catch (Exception ex) {
            LogDisplayUI.addLog("Network Error: " + ex.getMessage());
        }
        return StringUtils.EMPTY;
    }

    private static String[] headers(String url, Account account) {
        List<String> headers = new ArrayList<>();
        addHeader(headers, "User-Agent", "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3");
        addHeader(headers, "X-User-Agent", "Model: MAG250; Link: WiFi");
        addHeader(headers, "Referer", url);
        addHeader(headers, "Accept", "*/*");
        addHeader(headers, "Pragma", "no-cache");
        if (account.isConnected()) addHeader(headers, "Authorization", "Bearer " + account.getToken());
        addHeader(headers, "Cookie", "mac=" + account.getMacAddress() + "; stb_lang=en; timezone=GMT;");
//        addHeader(headers, "Expect", "100-continue");
//        addHeader(headers, "Accept-Encoding", "gzip, deflate");
        return headers.toArray(new String[0]);
    }

    private static void addHeader(List<String> headers, String name, String value) {
        headers.add(name);
        headers.add(value);
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
