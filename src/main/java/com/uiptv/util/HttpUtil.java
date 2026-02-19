package com.uiptv.util;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

public class HttpUtil {

    private static final int TIMEOUT_SECONDS = 5;

    public static HttpResponse<String> sendRequest(String url, Map<String, String> headers, String method) throws Exception {
        HttpRequest.Builder requestBuilder = HttpRequest.newBuilder()
                .uri(URI.create(url));

        if (headers != null) {
            headers.forEach(requestBuilder::header);
        }

        if ("POST".equalsIgnoreCase(method)) {
            requestBuilder.POST(HttpRequest.BodyPublishers.noBody());
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
}
