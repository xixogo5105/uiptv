package com.uiptv.util;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.LaxRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public class HttpUtil {
    public static final int STATUS_OK = 200;
    public static final int STATUS_NOT_ACCEPTABLE = 406;

    private static final int DEFAULT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.timeout.seconds", 30);
    private static final int CONNECT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connect.timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
    private static final int CONNECTION_REQUEST_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connection.request.timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
    private static final int RESPONSE_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.response.timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
    private static final int MAX_REDIRECTS = Integer.getInteger("uiptv.http.max.redirects", 5);
    private static final CloseableHttpClient HTTP_CLIENT = HttpClients.custom()
            .setRedirectStrategy(new LaxRedirectStrategy())
            .setDefaultRequestConfig(RequestConfig.custom()
                    .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                    .setConnectionRequestTimeout(Timeout.ofSeconds(CONNECTION_REQUEST_TIMEOUT_SECONDS))
                    .setResponseTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                    .setRedirectsEnabled(true)
                    .setMaxRedirects(MAX_REDIRECTS)
                    .build())
            .build();

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method) throws Exception {
        return sendRequest(url, headers, method, null);
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method, String body) throws Exception {
        return sendRequest(url, headers, method, body, RequestOptions.defaults());
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method, String body, RequestOptions options) throws Exception {
        HttpUriRequestBase request = buildRequest(url, headers, method, body, options);

        return HTTP_CLIENT.execute(request, response -> {
            HttpEntity entity = response.getEntity();
            String responseBody = "";
            if (options.readBody()) {
                responseBody = entity == null ? "" : EntityUtils.toString(entity);
            } else if (entity != null) {
                EntityUtils.consumeQuietly(entity);
            }
            return new HttpResult(
                    response.getCode(),
                    responseBody,
                    headersToMap(request.getHeaders()),
                    headersToMap(response.getHeaders())
            );
        });
    }

    public static StreamResult openStream(String url, Map<String, String> headers, String method, String body, RequestOptions options) throws Exception {
        HttpUriRequestBase request = buildRequest(url, headers, method, body, options);
        CloseableHttpResponse response = HTTP_CLIENT.execute(request);
        HttpEntity entity = response.getEntity();
        InputStream bodyStream = entity == null ? InputStream.nullInputStream() : entity.getContent();
        return new StreamResult(
                response.getCode(),
                headersToMap(request.getHeaders()),
                headersToMap(response.getHeaders()),
                bodyStream,
                response
        );
    }

    private static HttpUriRequestBase buildRequest(String url, Map<String, String> headers, String method, String body, RequestOptions options) {
        HttpUriRequestBase request = new HttpUriRequestBase(safeMethod(method), URI.create(url));
        request.setConfig(buildRequestConfig(options));

        if (headers != null) {
            headers.forEach(request::setHeader);
        }

        if (body != null) {
            if (headers == null || headers.keySet().stream().noneMatch(h -> "Content-Type".equalsIgnoreCase(h))) {
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            }
            request.setEntity(new StringEntity(body));
        }
        return request;
    }

    private static String safeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        return method.trim().toUpperCase();
    }

    private static RequestConfig buildRequestConfig(RequestOptions options) {
        Timeout connectTimeout = Timeout.of(Duration.ofSeconds(CONNECT_TIMEOUT_SECONDS));
        Timeout connectionRequestTimeout = Timeout.of(Duration.ofSeconds(CONNECTION_REQUEST_TIMEOUT_SECONDS));
        Timeout responseTimeout = Timeout.of(Duration.ofSeconds(RESPONSE_TIMEOUT_SECONDS));
        RequestOptions effective = options == null ? RequestOptions.defaults() : options;
        return RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setResponseTimeout(responseTimeout)
                .setRedirectsEnabled(effective.followRedirects())
                .setMaxRedirects(effective.followRedirects() ? MAX_REDIRECTS : 0)
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

    public static final class RequestOptions {
        private final boolean followRedirects;
        private final boolean readBody;

        public RequestOptions(boolean followRedirects, boolean readBody) {
            this.followRedirects = followRedirects;
            this.readBody = readBody;
        }

        public static RequestOptions defaults() {
            return new RequestOptions(true, true);
        }

        public boolean followRedirects() {
            return followRedirects;
        }

        public boolean readBody() {
            return readBody;
        }
    }

    public static final class StreamResult implements AutoCloseable {
        private final int statusCode;
        private final Map<String, List<String>> requestHeaders;
        private final Map<String, List<String>> responseHeaders;
        private final InputStream bodyStream;
        private final CloseableHttpResponse response;

        public StreamResult(int statusCode,
                            Map<String, List<String>> requestHeaders,
                            Map<String, List<String>> responseHeaders,
                            InputStream bodyStream,
                            CloseableHttpResponse response) {
            this.statusCode = statusCode;
            this.requestHeaders = requestHeaders;
            this.responseHeaders = responseHeaders;
            this.bodyStream = bodyStream;
            this.response = response;
        }

        public int statusCode() {
            return statusCode;
        }

        public Map<String, List<String>> requestHeaders() {
            return requestHeaders;
        }

        public Map<String, List<String>> responseHeaders() {
            return responseHeaders;
        }

        public InputStream bodyStream() {
            return bodyStream;
        }

        @Override
        public void close() throws IOException {
            response.close();
        }
    }
}
