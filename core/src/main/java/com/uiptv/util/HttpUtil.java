package com.uiptv.util;

import org.apache.hc.client5.http.async.methods.SimpleHttpRequest;
import org.apache.hc.client5.http.async.methods.SimpleHttpResponse;
import org.apache.hc.client5.http.impl.async.CloseableHttpAsyncClient;
import org.apache.hc.client5.http.impl.async.HttpAsyncClients;
import org.apache.hc.core5.concurrent.FutureCallback;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.DnsResolver;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.CloseableHttpResponse;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManager;
import org.apache.hc.client5.http.impl.nio.PoolingAsyncClientConnectionManagerBuilder;
import org.apache.hc.client5.http.protocol.HttpClientContext;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.URI;
import java.net.InetAddress;
import java.net.URL;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.nio.charset.Charset;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@SuppressWarnings("java:S1874")
public class HttpUtil {
    private static final int MAX_LOG_BODY_CHARS = Integer.getInteger("uiptv.http.log.max.body.chars", 4000);
    private static final List<String> SENSITIVE_HEADERS = List.of("authorization", "cookie", "set-cookie", "proxy-authorization");
    private static final Set<String> SENSITIVE_QUERY_KEYS = Set.of(
            "authorization", "auth", "cookie", "key", "mac", "pass", "password", "play_token",
            "pwd", "secret", "session", "sessionid", "signature", "token", "user", "username"
    );

    private HttpUtil() {
    }

    public static final int STATUS_OK = 200;
    public static final int STATUS_NOT_ACCEPTABLE = 406;

    private static final int DEFAULT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.timeout.seconds", 30);
    private static final int CONNECT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connect.timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
    private static final int CONNECTION_REQUEST_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.connection.request.timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
    private static final int RESPONSE_TIMEOUT_SECONDS = Integer.getInteger("uiptv.http.response.timeout.seconds", DEFAULT_TIMEOUT_SECONDS);
    private static final int MAX_REDIRECTS = Integer.getInteger("uiptv.http.max.redirects", 5);
    private static final int MAX_CONNECTIONS_TOTAL = Integer.getInteger("uiptv.http.max.connections.total", 50);
    private static final int MAX_CONNECTIONS_PER_ROUTE = Integer.getInteger("uiptv.http.max.connections.per.route", 10);
    private static final int IDLE_CONNECTION_EVICT_SECONDS = Integer.getInteger("uiptv.http.idle.connection.evict.seconds", 120);
    private static final ThreadLocal<Map<String, InetAddress[]>> PINNED_DNS = ThreadLocal.withInitial(Map::of);
    private static final DnsResolver DNS_RESOLVER = new DnsResolver() {
        @Override
        public InetAddress[] resolve(String host) throws java.net.UnknownHostException {
            InetAddress[] pinned = PINNED_DNS.get().get(host.toLowerCase(Locale.ROOT));
            return pinned == null ? InetAddress.getAllByName(host) : pinned.clone();
        }

        @Override
        public String resolveCanonicalHostname(String host) {
            return host;
        }
    };
    private static final PoolingHttpClientConnectionManager CONNECTION_MANAGER =
            PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(DNS_RESOLVER)
                    .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
                    .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                    // Portals commonly close keep-alive sockets while a bulk cache
                    // refresh is processing another account. Validate pooled sockets
                    // before reusing them so the next handshake does not inherit a
                    // stale connection and fail until manually retried.
                    .setValidateAfterInactivity(TimeValue.ofSeconds(5))
                    .build();
    // Isolate proxy traffic so a connection created by a non-proxy request can
    // never be reused for a hostname whose public address was separately vetted.
    private static final PoolingHttpClientConnectionManager PROXY_CONNECTION_MANAGER =
            PoolingHttpClientConnectionManagerBuilder.create()
                    .setDnsResolver(DNS_RESOLVER)
                    .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
                    .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                    .setValidateAfterInactivity(TimeValue.ofSeconds(5))
                    .build();
    private static final PoolingAsyncClientConnectionManager ASYNC_CONNECTION_MANAGER =
            PoolingAsyncClientConnectionManagerBuilder.create()
                    .setMaxConnTotal(MAX_CONNECTIONS_TOTAL)
                    .setMaxConnPerRoute(MAX_CONNECTIONS_PER_ROUTE)
                    .setValidateAfterInactivity(TimeValue.ofSeconds(5))
                    .build();
    private static final CloseableHttpClient HTTP_CLIENT = createHttpClient(CONNECTION_MANAGER);
    private static final CloseableHttpClient PROXY_HTTP_CLIENT = createHttpClient(PROXY_CONNECTION_MANAGER);

    private static CloseableHttpClient createHttpClient(PoolingHttpClientConnectionManager manager) {
        return HttpClients.custom()
                .setConnectionManager(manager)
                .setRedirectStrategy(new org.apache.hc.client5.http.impl.LaxRedirectStrategy())
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(IDLE_CONNECTION_EVICT_SECONDS))
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(CONNECTION_REQUEST_TIMEOUT_SECONDS))
                        .setResponseTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                        .setRedirectsEnabled(true)
                        .setMaxRedirects(MAX_REDIRECTS)
                        .build())
                .build();
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method) throws IOException {
        return sendRequest(url, headers, method, null);
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method, String body) throws IOException {
        return sendRequest(url, headers, method, body, RequestOptions.defaults());
    }

    public static HttpResult sendRequest(String url, Map<String, String> headers, String method, String body, RequestOptions options) throws IOException {
        HttpUriRequestBase request = buildRequest(url, headers, method, body, options);
        HttpClientContext context = HttpClientContext.create();

        try (var _ = ReloadRequestScope.watch(request)) {
            return HTTP_CLIENT.execute(request, context, response -> {
                String responseBody;
                try { responseBody = readResponseBody(response, options); }
                catch (ReloadRequestScope.LimitExceeded failure) {
                    request.cancel();
                    throw failure;
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
        } catch (IOException error) {
            ReloadRequestScope.checkCurrent(); throw error;
        } catch (RuntimeException error) { throw error; }
        catch (Exception error) { throw new IOException(error); }
    }

    public static HttpResult sendFileRequest(String url,
                                             Map<String, String> headers,
                                             String method,
                                             java.nio.file.Path file,
                                             ContentType contentType,
                                             RequestOptions options) throws IOException {
        HttpUriRequestBase request = buildRequest(url, headers, method, null, options);
        if (file != null) {
            request.setEntity(new FileEntity(file.toFile(), contentType == null ? ContentType.APPLICATION_OCTET_STREAM : contentType));
        }
        HttpClientContext context = HttpClientContext.create();

        return HTTP_CLIENT.execute(request, context, response -> new HttpResult(
                request.getMethod(),
                getFinalUri(request, context),
                response.getCode(),
                readResponseBody(response, options),
                headersToMap(request.getHeaders()),
                headersToMap(response.getHeaders())
        ));
    }

    public static StreamResult openStream(String url, Map<String, String> headers, String method, String body, RequestOptions options) throws IOException {
        return openStream(url, headers, method, body, options, HTTP_CLIENT);
    }

    private static StreamResult openStream(String url, Map<String, String> headers, String method, String body,
                                           RequestOptions options, CloseableHttpClient client) throws IOException {
        HttpUriRequestBase request = buildRequest(url, headers, method, body, options);
        HttpClientContext context = HttpClientContext.create();
        AutoCloseable deadline = ReloadRequestScope.watch(request);
        try {
            CloseableHttpResponse response = client.execute(request, context);
            HttpEntity entity = response.getEntity();
            InputStream bodyStream = entity == null ? InputStream.nullInputStream() : entity.getContent();
            StreamResult stream = new StreamResult(
                    request.getMethod(),
                    getFinalUri(request, context),
                    response.getCode(),
                    headersToMap(request.getHeaders()),
                    headersToMap(response.getHeaders()),
                    bodyStream,
                    response
            );
            stream.deadline = deadline;
            stream.request = request;
            return stream;
        } catch (Exception error) {
            try { deadline.close(); } catch (Exception _) { /* Preserve the original request failure. */ }
            ReloadRequestScope.checkCurrent();
            if (error instanceof IOException io) throw io;
            if (error instanceof RuntimeException runtime) throw runtime;
            throw new IOException(error);
        }
    }

    /** Open a request using the exact addresses that the caller has just validated. */
    public static StreamResult openStreamPinned(String url, Map<String, String> headers, String method,
                                                String body, RequestOptions options,
                                                InetAddress[] validatedAddresses) throws IOException {
        URI uri = URI.create(url);
        if (uri.getHost() == null || validatedAddresses == null || validatedAddresses.length == 0)
            throw new IOException("No validated destination address is available.");
        PINNED_DNS.set(Map.of(uri.getHost().toLowerCase(Locale.ROOT), validatedAddresses.clone()));
        try {
            return openStream(url, headers, method, body, options, PROXY_HTTP_CLIENT);
        } finally {
            PINNED_DNS.remove();
        }
    }

    public static String formatHttpLog(String requestUrl, HttpResult response, Map<String, String> requestParams) {
        if (response == null) {
            return "HTTP request log unavailable: response was null";
        }
        StringBuilder out = new StringBuilder(1024);
        out.append("HTTP ")
                .append(nonBlank(response.requestMethod(), "GET"))
                .append(' ')
                .append(sanitizeUrlForLog(nonBlank(response.requestUri(), nonBlank(requestUrl, "<unknown>"))))
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
                .map(entry -> entry.getKey() + "=" + quote(isSensitiveQueryKey(entry.getKey()) ? "<redacted>" : entry.getValue()))
                .collect(Collectors.joining(System.lineSeparator()));
    }

    /**
     * Keep URLs useful in diagnostics without writing provider credentials, tokens, or MACs to logs.
     */
    public static String sanitizeUrlForLog(String rawUrl) {
        if (rawUrl == null || rawUrl.isBlank()) {
            return rawUrl;
        }
        try {
            URI uri = URI.create(rawUrl);
            StringBuilder result = new StringBuilder();
            if (uri.getScheme() != null) {
                result.append(uri.getScheme()).append(':');
            }
            if (uri.getRawAuthority() != null) {
                String authority = uri.getRawAuthority();
                int at = authority.indexOf('@');
                if (at >= 0) {
                    authority = "<redacted>@" + authority.substring(at + 1);
                }
                result.append("//").append(authority);
            }
            if (uri.getRawPath() != null) {
                result.append(uri.getRawPath());
            }
            if (uri.getRawQuery() != null) {
                result.append('?').append(redactQuery(uri.getRawQuery()));
            }
            if (uri.getRawFragment() != null) {
                result.append('#').append(uri.getRawFragment());
            }
            return result.toString();
        } catch (IllegalArgumentException _) {
            return "<unparseable-url>";
        }
    }

    private static String redactQuery(String rawQuery) {
        return java.util.Arrays.stream(rawQuery.split("&", -1))
                .map(part -> {
                    int equals = part.indexOf('=');
                    String rawKey = equals < 0 ? part : part.substring(0, equals);
                    String key;
                    try {
                        key = URLDecoder.decode(rawKey, StandardCharsets.UTF_8);
                    } catch (IllegalArgumentException _) {
                        key = rawKey;
                    }
                    return isSensitiveQueryKey(key) ? rawKey + "=<redacted>" : part;
                })
                .collect(Collectors.joining("&"));
    }

    private static boolean isSensitiveQueryKey(String key) {
        return key != null && SENSITIVE_QUERY_KEYS.contains(key.replace('-', '_').toLowerCase(Locale.ROOT));
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

    private static String readResponseBody(org.apache.hc.core5.http.ClassicHttpResponse response, RequestOptions options) throws IOException {
        HttpEntity entity = response.getEntity();
        boolean readBody = options == null || options.readBody();
        if (readBody) {
            if (entity == null) {
                return "";
            }
            try {
                if (options != null && options.maxBodyChars() > 0) {
                    return readBoundedResponseBody(entity, options.maxBodyChars());
                }
                return EntityUtils.toString(entity);
            } catch (ParseException ex) {
                throw new IOException("Unable to parse HTTP response body", ex);
            }
        }
        if (entity != null) {
            EntityUtils.consumeQuietly(entity);
        }
        return "";
    }

    /**
     * Read a response without allowing an unexpectedly large provider payload to become a
     * multi-copy String/char[] allocation.  Callers that impose this limit cancel the request
     * in the surrounding response handler when the limit is exceeded.
     */
    private static String readBoundedResponseBody(HttpEntity entity, int maxChars) throws IOException {
        Charset charset = StandardCharsets.UTF_8;
        try {
            ContentType contentType = ContentType.parse(entity.getContentType());
            if (contentType.getCharset() != null) charset = contentType.getCharset();
        } catch (RuntimeException _) {
            // Providers frequently omit or misstate the content type; UTF-8 is the safe default.
        }
        StringBuilder result = new StringBuilder(Math.min(maxChars, 64 * 1024));
        char[] buffer = new char[8192];
        try (Reader reader = new InputStreamReader(entity.getContent(), charset)) {
            while (true) {
                int read = reader.read(buffer);
                if (read < 0) return result.toString();
                if (result.length() + read > maxChars) {
                    throw new ReloadRequestScope.LimitExceeded("HTTP response body exceeds " + maxChars + " characters");
                }
                result.append(buffer, 0, read);
            }
        }
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
        if (normalized.isEmpty()) {
            return URI.create("http://localhost/empty-url-fallback");
        }
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
            } catch (Exception e) {
                AppLog.addErrorLog(HttpUtil.class, "Failed to create URI from URL: " + sanitizeUrlForLog(normalized) + ". Error: " + e.getMessage());
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
        } catch (Exception e) {
            AppLog.addWarningLog(HttpUtil.class, "Failed to get final URI: " + e.getMessage());
            return request.getRequestUri();
        }
    }

    private static RequestConfig buildRequestConfig(RequestOptions options) {
        RequestOptions effective = options == null ? RequestOptions.defaults() : options;
        Timeout connectTimeout = Timeout.of(Duration.ofSeconds(resolveTimeoutSeconds(
                effective.connectTimeoutSeconds(), CONNECT_TIMEOUT_SECONDS)));
        Timeout connectionRequestTimeout = Timeout.of(Duration.ofSeconds(resolveTimeoutSeconds(
                effective.connectionRequestTimeoutSeconds(), CONNECTION_REQUEST_TIMEOUT_SECONDS)));
        Timeout responseTimeout = effective.responseTimeoutDisabled()
                ? Timeout.DISABLED
                : Timeout.of(Duration.ofSeconds(resolveTimeoutSeconds(
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
        private final boolean streamBody;
        private final Integer connectTimeoutSeconds;
        private final Integer connectionRequestTimeoutSeconds;
        private final Integer responseTimeoutSeconds;
        private final boolean responseTimeoutDisabled;
        private final int maxBodyChars;

        public RequestOptions(boolean followRedirects, boolean readBody) {
            this(followRedirects, readBody, null, null, null);
        }

        public RequestOptions(boolean followRedirects,
                              boolean readBody,
                              Integer connectTimeoutSeconds,
                              Integer connectionRequestTimeoutSeconds,
                              Integer responseTimeoutSeconds) {
            this(followRedirects, readBody, connectTimeoutSeconds, connectionRequestTimeoutSeconds, responseTimeoutSeconds, false);
        }

        @SuppressWarnings("java:S107")
        private RequestOptions(boolean followRedirects, boolean readBody, Integer connectTimeoutSeconds,
                               Integer connectionRequestTimeoutSeconds, Integer responseTimeoutSeconds, boolean streamBody) {
            this(followRedirects, readBody, connectTimeoutSeconds, connectionRequestTimeoutSeconds,
                    responseTimeoutSeconds, streamBody, false, 0);
        }

        @SuppressWarnings("java:S107")
        private RequestOptions(boolean followRedirects, boolean readBody, Integer connectTimeoutSeconds,
                               Integer connectionRequestTimeoutSeconds, Integer responseTimeoutSeconds,
                               boolean streamBody, boolean responseTimeoutDisabled, int maxBodyChars) {
            this.streamBody = streamBody;
            this.followRedirects = followRedirects;
            this.readBody = readBody;
            this.connectTimeoutSeconds = connectTimeoutSeconds;
            this.connectionRequestTimeoutSeconds = connectionRequestTimeoutSeconds;
            this.responseTimeoutSeconds = responseTimeoutSeconds;
            this.responseTimeoutDisabled = responseTimeoutDisabled;
            this.maxBodyChars = Math.max(0, maxBodyChars);
        }

        public static RequestOptions defaults() {
            return new RequestOptions(true, true);
        }

        /** Caller owns and must close the returned FetchResult catalogue stream. */
        public static RequestOptions streamingCatalogue() {
            return new RequestOptions(true, true, null, null, null, true);
        }

        /**
         * Options for a live proxy body. Connection establishment remains bounded,
         * but an otherwise healthy live stream is allowed to have idle periods.
         */
        public static RequestOptions streamingProxy() {
            return new RequestOptions(false, true, null, null, null, false, true, 0);
        }

        public boolean streamBody() { return streamBody; }

        /** Return a copy that bounds materialized response text; zero means unlimited. */
        public RequestOptions withMaxBodyChars(int maxChars) {
            return new RequestOptions(followRedirects, readBody, connectTimeoutSeconds,
                    connectionRequestTimeoutSeconds, responseTimeoutSeconds, streamBody,
                    responseTimeoutDisabled, maxChars);
        }

        public int maxBodyChars() { return maxBodyChars; }

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

        public boolean responseTimeoutDisabled() {
            return responseTimeoutDisabled;
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
        private AutoCloseable deadline = () -> {};
        private HttpUriRequestBase request;

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

        public void abort() throws IOException {
            if (request != null) request.cancel();
            close();
        }

        @Override
        public void close() throws IOException {
            IOException closeFailure = null;
            try { response.close(); }
            catch (IOException error) { closeFailure = error; }
            try { deadline.close(); }
            catch (Exception error) {
                IOException deadlineFailure = error instanceof IOException io
                        ? io : new IOException(error);
                if (closeFailure == null) closeFailure = deadlineFailure;
                else closeFailure.addSuppressed(deadlineFailure);
            }
            if (closeFailure != null) throw closeFailure;
        }
    }

    private static final CloseableHttpAsyncClient ASYNC_HTTP_CLIENT;

    static {
        CloseableHttpAsyncClient client = HttpAsyncClients.custom()
                .setConnectionManager(ASYNC_CONNECTION_MANAGER)
                .setRedirectStrategy(new org.apache.hc.client5.http.impl.LaxRedirectStrategy())
                .disableAutomaticRetries()
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.ofSeconds(IDLE_CONNECTION_EVICT_SECONDS))
                .setDefaultRequestConfig(RequestConfig.custom()
                        .setConnectTimeout(Timeout.ofSeconds(CONNECT_TIMEOUT_SECONDS))
                        .setConnectionRequestTimeout(Timeout.ofSeconds(CONNECTION_REQUEST_TIMEOUT_SECONDS))
                        .setResponseTimeout(Timeout.ofSeconds(RESPONSE_TIMEOUT_SECONDS))
                        .setRedirectsEnabled(true)
                        .setMaxRedirects(MAX_REDIRECTS)
                        .build())
                .build();
        client.start();
        ASYNC_HTTP_CLIENT = client;
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                client.close();
            } catch (IOException e) {
                AppLog.addWarningLog(HttpUtil.class, "Failed to close asynchronous HTTP client: " + e.getMessage());
            }
        }, "uiptv-http-async-client-shutdown"));
    }

    public static CompletableFuture<HttpResult> sendAsync(String url, Map<String, String> headers, String method) {
        return sendAsync(url, headers, method, null, RequestOptions.defaults());
    }

    public static CompletableFuture<HttpResult> sendAsync(String url, Map<String, String> headers, String method, String body) {
        return sendAsync(url, headers, method, body, RequestOptions.defaults());
    }

    public static CompletableFuture<HttpResult> sendAsync(String url, Map<String, String> headers, String method, String body, RequestOptions options) {
        SimpleHttpRequest request = SimpleHttpRequest.create(safeMethod(method), toSafeUri(url));
        request.setConfig(buildRequestConfig(options));
        if (headers != null) {
            headers.forEach(request::setHeader);
        }
        if (body != null && !body.isBlank()) {
            request.setBody(body, ContentType.APPLICATION_FORM_URLENCODED);
        }

        AtomicReference<Future<?>> apacheFutureRef = new AtomicReference<>();
        CancellableFuture<HttpResult> result = new CancellableFuture<>(apacheFutureRef);

        Future<SimpleHttpResponse> apacheFuture = ASYNC_HTTP_CLIENT.execute(request, null, new FutureCallback<SimpleHttpResponse>() {
            @Override
            public void completed(SimpleHttpResponse response) {
                completeAsyncResponse(result, request, headers, options, response);
            }

            @Override
            public void failed(Exception ex) {
                if (result.isDone()) {
                    return;
                }
                result.completeExceptionally(ex);
            }

            @Override
            public void cancelled() {
                result.cancel(false);
            }
        });
        result.setDelegate(apacheFuture);

        return result;
    }

    private static void completeAsyncResponse(CancellableFuture<HttpResult> result, SimpleHttpRequest request,
                                               Map<String, String> headers, RequestOptions options,
                                               SimpleHttpResponse response) {
        if (result.isDone()) return;
        Map<String, List<String>> responseHeaders = responseHeaders(response);
        Map<String, List<String>> requestHeaders = requestHeaders(headers);
        result.complete(new HttpResult(
                request.getMethod(),
                getUriString(request),
                response.getCode(),
                options == null || options.readBody() ? response.getBodyText() : "",
                requestHeaders,
                responseHeaders
        ));
    }

    private static Map<String, List<String>> responseHeaders(SimpleHttpResponse response) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (response.getHeaders() == null) return result;
        for (Header header : response.getHeaders()) {
            result.computeIfAbsent(header.getName(), _ -> new ArrayList<>()).add(header.getValue());
        }
        return result;
    }

    private static Map<String, List<String>> requestHeaders(Map<String, String> headers) {
        Map<String, List<String>> result = new LinkedHashMap<>();
        if (headers != null) {
            headers.forEach((key, value) -> result.computeIfAbsent(key, _ -> new ArrayList<>()).add(value));
        }
        return result;
    }

    static final class CancellableFuture<T> extends CompletableFuture<T> {
        private final AtomicReference<Future<?>> delegateRef;

        CancellableFuture(AtomicReference<Future<?>> delegateRef) {
            this.delegateRef = delegateRef;
        }

        void setDelegate(Future<?> delegate) {
            delegateRef.set(delegate);
            if (isCancelled()) {
                delegate.cancel(true);
            }
        }

        @Override
        public boolean cancel(boolean mayInterruptIfRunning) {
            boolean cancelled = super.cancel(mayInterruptIfRunning);
            Future<?> delegate = delegateRef.get();
            if (cancelled && delegate != null) {
                delegate.cancel(mayInterruptIfRunning);
            }
            return cancelled;
        }
    }

    private static String getUriString(org.apache.hc.core5.http.message.BasicHttpRequest request) {
        try {
            return request.getUri().toString();
        } catch (URISyntaxException _) {
            // Fallback to the request URI if URI is malformed
            return request.getRequestUri();
        }
    }
}
