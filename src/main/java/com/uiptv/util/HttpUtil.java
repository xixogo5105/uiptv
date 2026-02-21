package com.uiptv.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class HttpUtil {

    private static final int TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.timeout.seconds", 5);

    public static HttpResponse<String> sendRequest(String url, Map<String, String> headers, String method) throws Exception {
        return sendRequest(url, headers, method, null);
    }

    public static HttpResponse<String> sendRequest(String url, Map<String, String> headers, String method, String body) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(TIMEOUT_SECONDS));

        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        if ("POST".equalsIgnoreCase(method)) {
            if (headers == null || headers.keySet().stream().noneMatch(h -> "Content-Type".equalsIgnoreCase(h))) {
                requestBuilder.header("Content-Type", "application/x-www-form-urlencoded");
            }
            requestBuilder.POST(HttpRequest.BodyPublishers.ofString(body == null ? "" : body));
        } else {
            requestBuilder.GET();
        }

        return HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.ALWAYS)
                .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                .version(HttpClient.Version.HTTP_1_1)
                .build()
                .send(requestBuilder.build(), HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Resolve the final URL after HTTP redirects.
     */
    public static String resolveFinalUrl(String url, Map<String, String> headers) {
        try {
            HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .GET();

            if (headers != null) {
                headers.forEach(requestBuilder::header);
            }

            HttpResponse<Void> response = HttpClient.newBuilder()
                    .followRedirects(HttpClient.Redirect.ALWAYS)
                    .connectTimeout(Duration.ofSeconds(TIMEOUT_SECONDS))
                    .version(HttpClient.Version.HTTP_1_1)
                    .build()
                    .send(requestBuilder.build(), HttpResponse.BodyHandlers.discarding());

            URI finalUri = response.uri();
            return finalUri == null ? url : finalUri.toString();
        } catch (Exception ignored) {
            return url;
        }
    }
}
