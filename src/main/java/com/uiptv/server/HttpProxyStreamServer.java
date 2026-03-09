package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.HttpUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

@SuppressWarnings({"java:S1075", "java:S135"})
public class HttpProxyStreamServer implements HttpHandler {
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";
    private static final String HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String HEADER_CONTENT_LENGTH = "Content-Length";
    private static final String HEADER_CONTENT_RANGE = "Content-Range";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_COOKIE = "Cookie";
    private static final String HEADER_LOCATION = "Location";
    private static final String HEADER_ORIGIN = "Origin";
    private static final String HEADER_PRAGMA = "Pragma";
    private static final String HEADER_RANGE = "Range";
    private static final String HEADER_REFERER = "Referer";
    private static final String HEADER_SET_COOKIE = "Set-Cookie";
    private static final String HEADER_X_USER_AGENT = "X-User-Agent";
    private static final String MAG_USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3";
    private static final String USER_AGENT = "UIPTV/1.0";
    private static final long UNKNOWN_CONTENT_LENGTH = 0L;

    @Override
    public void handle(HttpExchange ex) throws IOException {
        String source = getParam(ex, "src");
        if (isBlank(source)) {
            ex.sendResponseHeaders(400, -1);
            return;
        }

        String current = source.trim();
        List<String> cookies = new ArrayList<>();
        String requestMethod = ex.getRequestMethod();

        try {
            try (HttpUtil.StreamResult upstream = openResolvedStream(current, cookies, readForwardHeaders(ex), requestMethod)) {
                if (upstream == null) {
                    sendBadGateway(ex);
                    return;
                }

                writeResponseHeaders(ex, upstream.responseHeaders());
                ex.sendResponseHeaders(
                        upstream.statusCode(),
                        resolveContentLength(firstHeader(upstream.responseHeaders(), HEADER_CONTENT_LENGTH))
                );

                if (!"HEAD".equalsIgnoreCase(requestMethod)) {
                    try (InputStream is = resolvedBodyStream(upstream);
                         OutputStream os = ex.getResponseBody()) {
                        copyStream(is, os);
                    }
                }
            }
        } catch (Exception _) {
            sendBadGateway(ex);
        }
    }

    private HttpUtil.StreamResult openResolvedStream(String current,
                                                     List<String> cookies,
                                                     Map<String, String> forwardHeaders,
                                                     String requestMethod) throws IOException {
        for (int i = 0; i < 6; i++) {
            Map<String, String> upstreamHeaders = buildUpstreamHeaders(current, cookies, forwardHeaders);

            String upstreamMethod = "HEAD".equalsIgnoreCase(requestMethod) ? "HEAD" : "GET";
            HttpUtil.StreamResult upstream = HttpUtil.openStream(current, upstreamHeaders, upstreamMethod, null, new HttpUtil.RequestOptions(false, true));
            collectCookies(upstream.responseHeaders(), cookies);
            int status = upstream.statusCode();
            if (status >= 300 && status <= 399) {
                try (upstream) {
                    String location = firstHeader(upstream.responseHeaders(), HEADER_LOCATION);
                    if (isBlank(location)) {
                        return null;
                    }
                    URI base = URI.create(current);
                    URI resolved = base.resolve(location);
                    current = downgradeHttpsToHttp(resolved.toString());
                    continue;
                }
            }
            if (status == HttpUtil.STATUS_NOT_ACCEPTABLE) {
                try (upstream) {
                    String fallback = build406Fallback(current);
                    if (!isBlank(fallback) && !fallback.equals(current)) {
                        current = fallback;
                        continue;
                    }
                }
            }
            return upstream;
        }
        return null;
    }

    private void collectCookies(Map<String, List<String>> responseHeaders, List<String> cookies) {
        if (responseHeaders == null || cookies == null) {
            return;
        }
        List<String> setCookie = null;
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (entry.getKey() != null && HEADER_SET_COOKIE.equalsIgnoreCase(entry.getKey())) {
                setCookie = entry.getValue();
                break;
            }
        }
        if (setCookie == null || setCookie.isEmpty()) {
            return;
        }
        for (String row : setCookie) {
            if (isBlank(row)) {
                continue;
            }
            String pair = row.split(";", 2)[0].trim();
            if (isBlank(pair)) {
                continue;
            }
            String key = pair.split("=", 2)[0].trim();
            if (isBlank(key)) {
                continue;
            }
            cookies.removeIf(existing -> existing.startsWith(key + "="));
            cookies.add(pair);
        }
    }

    private String firstHeader(Map<String, List<String>> headers, String name) {
        if (headers == null || isBlank(name)) {
            return "";
        }
        for (Map.Entry<String, List<String>> entry : headers.entrySet()) {
            if (entry.getKey() != null && entry.getKey().equalsIgnoreCase(name)) {
                List<String> values = entry.getValue();
                if (values != null && !values.isEmpty() && !isBlank(values.getFirst())) {
                    return values.getFirst();
                }
            }
        }
        return "";
    }

    private Map<String, String> readForwardHeaders(HttpExchange ex) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put(HEADER_ACCEPT, ex.getRequestHeaders().getFirst(HEADER_ACCEPT));
        headers.put(HEADER_RANGE, ex.getRequestHeaders().getFirst(HEADER_RANGE));
        headers.put(HEADER_REFERER, ex.getRequestHeaders().getFirst(HEADER_REFERER));
        headers.put(HEADER_ORIGIN, ex.getRequestHeaders().getFirst(HEADER_ORIGIN));
        return headers;
    }

    private Map<String, String> buildUpstreamHeaders(String currentUrl, List<String> cookies, Map<String, String> forwardedHeaders) {
        Map<String, String> upstreamHeaders = new LinkedHashMap<>();
        boolean stalkerStyle = isStalkerPortalStream(currentUrl);
        upstreamHeaders.put("User-Agent", stalkerStyle ? MAG_USER_AGENT : USER_AGENT);
        upstreamHeaders.put(HEADER_ACCEPT, isBlank(forwardedHeaders.get(HEADER_ACCEPT)) ? "*/*" : forwardedHeaders.get(HEADER_ACCEPT));
        upstreamHeaders.put("Accept-Encoding", "identity");
        if (stalkerStyle) {
            upstreamHeaders.put(HEADER_X_USER_AGENT, "Model: MAG250; Link: WiFi");
            upstreamHeaders.put(HEADER_PRAGMA, "no-cache");
        }
        addHeaderIfPresent(upstreamHeaders, HEADER_RANGE, forwardedHeaders.get(HEADER_RANGE));
        String origin = resolveUpstreamOriginHeader(currentUrl, forwardedHeaders.get(HEADER_ORIGIN));
        String referer = resolveUpstreamRefererHeader(currentUrl, forwardedHeaders.get(HEADER_REFERER));
        addHeaderIfPresent(upstreamHeaders, HEADER_ORIGIN, origin);
        addHeaderIfPresent(upstreamHeaders, HEADER_REFERER, referer);
        addStalkerCookieFromUrl(upstreamHeaders, currentUrl, cookies);
        if (!cookies.isEmpty() && !upstreamHeaders.containsKey(HEADER_COOKIE)) {
            upstreamHeaders.put(HEADER_COOKIE, String.join("; ", cookies));
        }
        return upstreamHeaders;
    }

    private void addStalkerCookieFromUrl(Map<String, String> upstreamHeaders, String currentUrl, List<String> cookies) {
        if (!isStalkerPortalStream(currentUrl)) {
            return;
        }
        String mac = queryParam(currentUrl, "mac");
        if (isBlank(mac)) {
            return;
        }
        String cookieValue = "mac=" + mac + "; stb_lang=en; timezone=Europe/London;";
        if (cookies.stream().noneMatch(existing -> existing.startsWith("mac="))) {
            cookies.add("mac=" + mac);
        }
        upstreamHeaders.put(HEADER_COOKIE, cookieValue);
    }

    private String resolveUpstreamOriginHeader(String currentUrl, String forwardedOrigin) {
        String sourceOrigin = originOf(currentUrl);
        if (isBlank(sourceOrigin)) {
            return "";
        }
        if (isBlank(forwardedOrigin) || isLocalOrigin(forwardedOrigin) || !sameOrigin(sourceOrigin, forwardedOrigin)) {
            return shouldForcePortalHeaders(currentUrl) ? sourceOrigin : "";
        }
        return forwardedOrigin;
    }

    private String resolveUpstreamRefererHeader(String currentUrl, String forwardedReferer) {
        String sourceOrigin = originOf(currentUrl);
        if (isBlank(sourceOrigin)) {
            return "";
        }
        if (isBlank(forwardedReferer) || isLocalOrigin(forwardedReferer) || !sameOrigin(sourceOrigin, forwardedReferer)) {
            return shouldForcePortalHeaders(currentUrl) ? sourceOrigin + "/" : "";
        }
        return forwardedReferer;
    }

    private boolean shouldForcePortalHeaders(String currentUrl) {
        if (isBlank(currentUrl)) {
            return false;
        }
        String lower = currentUrl.toLowerCase();
        return lower.contains("/live/play/")
                || lower.contains("/play/movie.php")
                || lower.matches(".*/\\d+(\\?.*)?$");
    }

    private boolean isStalkerPortalStream(String currentUrl) {
        if (isBlank(currentUrl)) {
            return false;
        }
        String lower = currentUrl.toLowerCase();
        return (lower.contains("/live/play/") || lower.contains("/play/movie.php"))
                && (lower.contains("play_token=") || lower.contains("mac="));
    }

    private boolean sameOrigin(String left, String right) {
        String leftOrigin = originOf(left);
        String rightOrigin = originOf(right);
        return !isBlank(leftOrigin) && leftOrigin.equalsIgnoreCase(rightOrigin);
    }

    private boolean isLocalOrigin(String url) {
        String origin = originOf(url);
        if (isBlank(origin)) {
            return false;
        }
        try {
            URI uri = URI.create(origin);
            String host = uri.getHost();
            if (isBlank(host)) {
                return false;
            }
            String normalizedHost = host.trim().toLowerCase();
            return "127.0.0.1".equals(normalizedHost)
                    || "localhost".equals(normalizedHost)
                    || "::1".equals(normalizedHost);
        } catch (Exception _) {
            return false;
        }
    }

    private String originOf(String url) {
        if (isBlank(url)) {
            return "";
        }
        try {
            URI uri = URI.create(url.trim());
            String scheme = uri.getScheme();
            String host = uri.getHost();
            if (isBlank(scheme) || isBlank(host)) {
                return "";
            }
            int port = uri.getPort();
            boolean defaultPort = port < 0
                    || ("http".equalsIgnoreCase(scheme) && port == 80)
                    || ("https".equalsIgnoreCase(scheme) && port == 443);
            return defaultPort ? scheme + "://" + host : scheme + "://" + host + ":" + port;
        } catch (Exception _) {
            return "";
        }
    }

    private String queryParam(String url, String key) {
        if (isBlank(url) || isBlank(key)) {
            return "";
        }
        try {
            String query = URI.create(url.trim()).getRawQuery();
            if (isBlank(query)) {
                return "";
            }
            for (String pair : query.split("&")) {
                if (isBlank(pair)) {
                    continue;
                }
                String[] parts = pair.split("=", 2);
                if (parts.length == 0 || !key.equalsIgnoreCase(parts[0])) {
                    continue;
                }
                return parts.length > 1 ? URLDecoder.decode(parts[1], StandardCharsets.UTF_8) : "";
            }
        } catch (Exception _) {
            return "";
        }
        return "";
    }

    private void addHeaderIfPresent(Map<String, String> headers, String name, String value) {
        if (!isBlank(value)) {
            headers.put(name, value);
        }
    }

    private InputStream resolvedBodyStream(HttpUtil.StreamResult upstream) {
        InputStream bodyStream = upstream.bodyStream();
        return bodyStream == null ? new ByteArrayInputStream(new byte[0]) : bodyStream;
    }

    private void writeResponseHeaders(HttpExchange ex, Map<String, List<String>> upstreamHeaders) {
        String contentType = firstHeader(upstreamHeaders, HEADER_CONTENT_TYPE);
        ex.getResponseHeaders().add(HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ex.getResponseHeaders().add(HEADER_CACHE_CONTROL, "no-store");
        ex.getResponseHeaders().add(HEADER_CONTENT_TYPE, isBlank(contentType) ? "application/octet-stream" : contentType);
        copyHeaderIfPresent(ex, upstreamHeaders, HEADER_ACCEPT_RANGES);
        copyHeaderIfPresent(ex, upstreamHeaders, HEADER_CONTENT_RANGE);
        copyHeaderIfPresent(ex, upstreamHeaders, HEADER_CONTENT_DISPOSITION);
    }

    private void copyHeaderIfPresent(HttpExchange ex, Map<String, List<String>> upstreamHeaders, String headerName) {
        String value = firstHeader(upstreamHeaders, headerName);
        if (!isBlank(value)) {
            ex.getResponseHeaders().add(headerName, value);
        }
    }

    private long resolveContentLength(String contentLengthHeader) {
        try {
            long contentLength = isBlank(contentLengthHeader) ? UNKNOWN_CONTENT_LENGTH : Long.parseLong(contentLengthHeader);
            return contentLength > 0 ? contentLength : UNKNOWN_CONTENT_LENGTH;
        } catch (Exception _) {
            return UNKNOWN_CONTENT_LENGTH;
        }
    }

    private void copyStream(InputStream inputStream, OutputStream outputStream) throws IOException {
        byte[] buffer = new byte[8192];
        int read;
        while ((read = inputStream.read(buffer)) != -1) {
            outputStream.write(buffer, 0, read);
        }
    }

    private void sendBadGateway(HttpExchange ex) throws IOException {
        ex.sendResponseHeaders(502, -1);
    }

    private String downgradeHttpsToHttp(String url) {
        if (isBlank(url)) return url;
        String lower = url.toLowerCase();
        if (lower.startsWith("https://") && (lower.contains("/live/play/") || lower.contains("/play/movie.php") || lower.matches(".*/\\d+(\\?.*)?$"))) {
            return "http://" + url.substring("https://".length());
        }
        return url;
    }

    private String build406Fallback(String originalUrl) {
        if (isBlank(originalUrl)) {
            return originalUrl;
        }
        try {
            URI uri = URI.create(originalUrl);
            String path = uri.getPath();
            if (isBlank(path)) {
                return originalUrl;
            }
            String[] parts = path.split("/");
            List<String> segments = new ArrayList<>();
            for (String part : parts) {
                if (!isBlank(part)) {
                    segments.add(part);
                }
            }
            if (segments.size() < 3) {
                return originalUrl;
            }

            String last = segments.get(segments.size() - 1);
            if (!last.matches("^\\d+$")) {
                return originalUrl;
            }

            String ext = extensionOf(last);
            if ("ts".equals(ext) || "m3u8".equals(ext)) {
                return originalUrl;
            }

            // Typical Xtream live path fallback: /user/pass/streamId -> /user/pass/streamId.ts
            segments.set(segments.size() - 1, last + ".ts");
            String candidatePath = "/" + String.join("/", segments);

            URI candidate = new URI(
                    uri.getScheme(),
                    uri.getUserInfo(),
                    uri.getHost(),
                    uri.getPort(),
                    candidatePath,
                    uri.getQuery(),
                    uri.getFragment()
            );
            return candidate.toString();
        } catch (Exception _) {
            return originalUrl;
        }
    }

    private String extensionOf(String value) {
        if (isBlank(value)) {
            return "";
        }
        int dot = value.lastIndexOf('.');
        if (dot < 0 || dot == value.length() - 1) {
            return "";
        }
        return value.substring(dot + 1).toLowerCase();
    }
}
