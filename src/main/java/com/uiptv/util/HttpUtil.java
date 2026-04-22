package com.uiptv.util;

import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.LaxRedirectStrategy;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.Timeout;
import com.uiptv.service.ConfigurationService;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@SuppressWarnings("java:S1874")
public class HttpUtil {
    private static final int MAX_LOG_BODY_CHARS = Integer.getInteger("uiptv.http.log.max.body.chars", 4000);
    private static final List<String> SENSITIVE_HEADERS = List.of("authorization", "cookie", "set-cookie", "proxy-authorization");

    private HttpUtil() {
    }

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

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method) throws IOException {
        return sendRequest(url, headers, method, null);
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method, String body) throws IOException {
        return sendRequest(url, headers, method, body, RequestOptions.defaults());
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method, String body, RequestOptions options) throws IOException {
        HttpUriRequestBase request = buildRequest(url, headers, method, body, options);
        HttpClientContext context = HttpClientContext.create();

        return HTTP_CLIENT.execute(request, context, response -> {
            HttpEntity entity = response.getEntity();
            String responseBody = "";
            if (options.readBody()) {
                responseBody = entity == null ? "" : EntityUtils.toString(entity);
            } else if (entity != null) {
                EntityUtils.consumeQuietly(entity);
            }
            return new HttpResult(
                    request.getMethod(),
                    getFinalUri(request, context),
                    response.getCode(),
                    responseBody,
                    headersToMap(request.getHeaders()),
                    headersToMap(response.getHeaders())
            );
        });
    }

    public static StreamResult openStream(String url, Map<String, String> headers, String method, String body, RequestOptions options) throws IOException {
        HttpUriRequestBase request = buildRequest(url, headers, method, body, options);
        HttpClientContext context = HttpClientContext.create();
        CloseableHttpResponse response = HTTP_CLIENT.execute(request, context);
        HttpEntity entity = response.getEntity();
        InputStream bodyStream = entity == null ? InputStream.nullInputStream() : entity.getContent();
        return new StreamResult(
                request.getMethod(),
                getFinalUri(request, context),
                response.getCode(),
                headersToMap(request.getHeaders()),
                headersToMap(response.getHeaders()),
                bodyStream,
                response
        );
    }

    public static String formatHttpLog(String requestUrl, HttpResult response, Map<String, String> requestParams) {
        if (response == null) {
            return "HTTP request log unavailable: response was null";
        }
        StringBuilder out = new StringBuilder(1024);
        out.append("HTTP ")
                .append(nonBlank(response.requestMethod(), "GET"))
                .append(' ')
                .append(nonBlank(response.requestUri(), nonBlank(requestUrl, "<unknown>")))
                .append(System.lineSeparator());
        out.append("Status: ").append(response.statusCode()).append(System.lineSeparator());

        if (requestParams != null && !requestParams.isEmpty()) {
            appendSection(out, "Request Params", formatParams(requestParams));
        }

        appendSection(out, "Request Headers", formatHeaders(response.requestHeaders()));
        appendSection(out, "Response Headers", formatHeaders(response.responseHeaders()));
        appendSection(out, "Response Body", abbreviateBody(response.body()));
        return out.toString().trim();
    }

    private static void appendSection(StringBuilder out, String title, String content) {
        out.append(System.lineSeparator())
                .append(title)
                .append(':')
                .append(System.lineSeparator())
                .append(indent(nonBlank(content, "<none>")))
                .append(System.lineSeparator());
    }

    private static String formatParams(Map<String, String> params) {
        return params.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(String.CASE_INSENSITIVE_ORDER))
                .map(entry -> entry.getKey() + "=" + quote(entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatHeaders(Map<String, List<String>> headers) {
        if (headers == null || headers.isEmpty()) {
            return "<none>";
        }
        Map<String, List<String>> sorted = new TreeMap<>(String.CASE_INSENSITIVE_ORDER);
        sorted.putAll(headers);
        return sorted.entrySet().stream()
                .map(entry -> entry.getKey() + ": " + formatHeaderValues(entry.getKey(), entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String formatHeaderValues(String headerName, List<String> values) {
        if (values == null || values.isEmpty()) {
            return "";
        }
        if (isSensitiveHeader(headerName)) {
            return "<redacted>";
        }
        return values.stream()
                .filter(Objects::nonNull)
                .map(HttpUtil::quote)
                .collect(Collectors.joining(", "));
    }

    private static boolean isSensitiveHeader(String headerName) {
        if (headerName == null) {
            return false;
        }
        String lower = headerName.toLowerCase(Locale.ROOT);
        return SENSITIVE_HEADERS.contains(lower);
    }

    private static String abbreviateBody(String body) {
        if (body == null || body.isBlank()) {
            return "<empty>";
        }
        String normalized = body.replace("\r\n", "\n").replace('\r', '\n').trim();
        if (looksBinary(normalized)) {
            return "<binary " + normalized.getBytes(StandardCharsets.UTF_8).length + " bytes>";
        }
        if (normalized.length() <= MAX_LOG_BODY_CHARS) {
            return normalized;
        }
        return normalized.substring(0, MAX_LOG_BODY_CHARS)
                + System.lineSeparator()
                + "... [truncated " + (normalized.length() - MAX_LOG_BODY_CHARS) + " chars]";
    }

    private static boolean looksBinary(String value) {
        for (int i = 0; i < value.length(); i++) {
            char c = value.charAt(i);
            if (Character.isISOControl(c) && !Character.isWhitespace(c)) {
                return true;
            }
        }
        return false;
    }

    private static String indent(String value) {
        return value.lines()
                .map(line -> "  " + line)
                .collect(Collectors.joining(System.lineSeparator()));
    }

    private static String quote(String value) {
        if (value == null) {
            return "\"\"";
        }
        return "\"" + value + "\"";
    }

    private static String nonBlank(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static HttpUriRequestBase buildRequest(String url, Map<String, String> headers, String method, String body, RequestOptions options) {
        HttpUriRequestBase request = new HttpUriRequestBase(safeMethod(method), toSafeUri(url));
        request.setConfig(buildRequestConfig(options));

        if (headers != null) {
            headers.forEach(request::setHeader);
        }

        if (body != null) {
            if (headers == null || headers.keySet().stream().noneMatch("Content-Type"::equalsIgnoreCase)) {
                request.setHeader("Content-Type", "application/x-www-form-urlencoded");
            }
            request.setEntity(new StringEntity(body));
        }
        return request;
    }

    private static URI toSafeUri(String url) {
        String normalized = url == null ? "" : url.trim();
        try {
            return URI.create(normalized);
        } catch (IllegalArgumentException original) {
            try {
                URL parsed = new URL(normalized);
                // Build component-wise to encode illegal chars (e.g. "|" in query values).
                return new URI(
                        parsed.getProtocol(),
                        parsed.getUserInfo(),
                        parsed.getHost(),
                        parsed.getPort(),
                        parsed.getPath(),
                        parsed.getQuery(),
                        parsed.getRef()
                );
            } catch (Exception _) {
                throw original;
            }
        }
    }

    private static String safeMethod(String method) {
        if (method == null || method.isBlank()) {
            return "GET";
        }
        return method.trim().toUpperCase();
    }

    private static String getFinalUri(HttpUriRequestBase request, HttpClientContext context) {
        if (!ConfigurationService.getInstance().isResolveChainAndDeepRedirectsEnabled()) {
            return request.getRequestUri();
        }
        try {
            org.apache.hc.client5.http.protocol.RedirectLocations redirects = context.getRedirectLocations();
            if (redirects != null) {
                int redirectCount = redirects.size();
                if (redirectCount > 0 && redirectCount <= MAX_REDIRECTS + 1) {
                    URI finalRedirect = redirects.get(redirectCount - 1);
                    return finalRedirect == null ? request.getRequestUri() : finalRedirect.toString();
                }
                if (redirectCount > MAX_REDIRECTS + 1) {
                    return request.getRequestUri();
                }
            }
            URI uri = request.getUri();
            return uri == null ? "" : uri.toString();
        } catch (Exception _) {
            return request.getRequestUri();
        }
    }

    private static RequestConfig buildRequestConfig(RequestOptions options) {
        RequestOptions effective = options == null ? RequestOptions.defaults() : options;
        Timeout connectTimeout = Timeout.of(Duration.ofSeconds(resolveTimeoutSeconds(
                effective.connectTimeoutSeconds(), CONNECT_TIMEOUT_SECONDS)));
        Timeout connectionRequestTimeout = Timeout.of(Duration.ofSeconds(resolveTimeoutSeconds(
                effective.connectionRequestTimeoutSeconds(), CONNECTION_REQUEST_TIMEOUT_SECONDS)));
        Timeout responseTimeout = Timeout.of(Duration.ofSeconds(resolveTimeoutSeconds(
                effective.responseTimeoutSeconds(), RESPONSE_TIMEOUT_SECONDS)));
        return RequestConfig.custom()
                .setConnectTimeout(connectTimeout)
                .setConnectionRequestTimeout(connectionRequestTimeout)
                .setResponseTimeout(responseTimeout)
                .setRedirectsEnabled(effective.followRedirects())
                .setMaxRedirects(effective.followRedirects() ? MAX_REDIRECTS : 0)
                .build();
    }

    private static int resolveTimeoutSeconds(Integer override, int defaultValue) {
        return override != null && override > 0 ? override : defaultValue;
    }

    private static Map<String, List<String>> headersToMap(Header[] headers) {
        Map<String, List<String>> headerMap = new LinkedHashMap<>();
        for (Header header : headers) {
            headerMap.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
        }
        return headerMap;
    }

    public record HttpResult(
            String requestMethod,
            String requestUri,
            int statusCode,
            String body,
            Map<String, List<String>> requestHeaders,
            Map<String, List<String>> responseHeaders
    ) {
        public HttpResult(int statusCode,
                          String body,
                          Map<String, List<String>> requestHeaders,
                          Map<String, List<String>> responseHeaders) {
            this("", "", statusCode, body, requestHeaders, responseHeaders);
        }
    }

    public static final class RequestOptions {
        private final boolean followRedirects;
        private final boolean readBody;
        private final Integer connectTimeoutSeconds;
        private final Integer connectionRequestTimeoutSeconds;
        private final Integer responseTimeoutSeconds;

        public RequestOptions(boolean followRedirects, boolean readBody) {
            this(followRedirects, readBody, null, null, null);
        }

        public RequestOptions(boolean followRedirects,
                              boolean readBody,
                              Integer connectTimeoutSeconds,
                              Integer connectionRequestTimeoutSeconds,
                              Integer responseTimeoutSeconds) {
            this.followRedirects = followRedirects;
            this.readBody = readBody;
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            this.connectionRequestTimeoutSeconds = connectionRequestTimeoutSeconds;
            this.responseTimeoutSeconds = responseTimeoutSeconds;
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

        public Integer connectTimeoutSeconds() {
            return connectTimeoutSeconds;
        }

        public Integer connectionRequestTimeoutSeconds() {
            return connectionRequestTimeoutSeconds;
        }

        public Integer responseTimeoutSeconds() {
            return responseTimeoutSeconds;
        }
    }

    public static final class StreamResult implements AutoCloseable {
        private final String requestMethod;
        private final String requestUri;
        private final int statusCode;
        private final Map<String, List<String>> requestHeaders;
        private final Map<String, List<String>> responseHeaders;
        private final InputStream bodyStream;
        private final CloseableHttpResponse response;

        public StreamResult(String requestMethod,
                            String requestUri,
                            int statusCode,
                            Map<String, List<String>> requestHeaders,
                            Map<String, List<String>> responseHeaders,
                            InputStream bodyStream,
                            CloseableHttpResponse response) {
            this.requestMethod = requestMethod;
            this.requestUri = requestUri;
            this.statusCode = statusCode;
            this.requestHeaders = requestHeaders;
            this.responseHeaders = responseHeaders;
            this.bodyStream = bodyStream;
            this.response = response;
        }

        public String requestMethod() {
            return requestMethod;
        }

        public String requestUri() {
            return requestUri;
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
