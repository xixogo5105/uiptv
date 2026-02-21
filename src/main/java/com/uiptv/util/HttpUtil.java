package com.uiptv.util;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.LaxRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpUtil {

    private static final int TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.timeout.seconds", 5);
    private static final int MAX_REDIRECTS = Integer.getInteger("uiptv.http.max.redirects", 3);
    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setRedirectStrategy(new LaxRedirectStrategy())
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(TIMEOUT_SECONDS))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(TIMEOUT_SECONDS))
                    .setResponseTimeout(Timeout.ofSeconds(TIMEOUT_SECONDS))
                    .setRedirectsEnabled(true)
                    .setMaxRedirects(MAX_REDIRECTS)
                    .build())
            .build();

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method) throws Exception {
        return sendRequest(url, headers, method, null);
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method, String body) throws Exception {
        boolean isPost = "POST".equalsIgnoreCase(method);
        var request = isPost ? new HttpPost(url) : new HttpGet(url);
        request.setConfig(buildRequestConfig());

        if (headers != null) {
            headers.forEach(request::setHeader);
        }

        if (isPost) {
            if (headers == null || headers.keySet().stream().noneMatch(h -> "Content-Type".equalsIgnoreCase(h))) {
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            }
            request.setEntity(new StringEntity(body == null ? "" : body));
        }

        return HTTP_CLIENT.execute(request, response -> {
            HttpEntity entity = response.getEntity();
            String responseBody = entity == null ? "" : EntityUtils.toString(entity);
            return new HttpResult(
                    response.getCode(),
                    responseBody,
                    headersToMap(request.getHeaders()),
                    headersToMap(response.getHeaders())
            );
        });
    }

    private static RequestConfig buildRequestConfig() {
        Timeout timeout = Timeout.of(Duration.ofSeconds(TIMEOUT_SECONDS));
        return RequestConfig.custom()
                .setConnectTimeout(timeout)
                .setConnectionRequestTimeout(timeout)
                .setResponseTimeout(timeout)
                .setRedirectsEnabled(true)
                .setMaxRedirects(MAX_REDIRECTS)
                .build();
    }

    private static Map<String, List<String>> headersToMap(Header[] headers) {
        Map<String, List<String>> headerMap = new LinkedHashMap<>();
        for (Header header : headers) {
            headerMap.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }
        return headerMap;
    }

    public static final class HttpResult {
        private final int statusCode;
        private final String body;
        private final Map<String, List<String>> requestHeaders;
        private final Map<String, List<String>> responseHeaders;

        public HttpResult(int statusCode, String body, Map<String, List<String>> requestHeaders, Map<String, List<String>> responseHeaders) {
            this.statusCode = statusCode;
            this.body = body;
            this.requestHeaders = requestHeaders;
            this.responseHeaders = responseHeaders;
        }

        public int statusCode() {
            return statusCode;
        }

        public String body() {
            return body;
        }

        public Map<String, List<String>> requestHeaders() {
            return requestHeaders;
        }

        public Map<String, List<String>> responseHeaders() {
            return responseHeaders;
        }
    }
}
