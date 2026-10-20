package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.HttpUtil;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.URI;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.net.UnknownHostException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Predicate;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.ServerUrlUtil.isLocalServerHost;
import static com.uiptv.util.StringUtils.isBlank;

@SuppressWarnings({"java:S1075", "java:S135"})
public class HttpProxyStreamServer implements HttpHandler {
    private static final String PATH_LIVE_PLAY = "/live/play/";
    private static final String PATH_PLAY_LIVE = "/play/live.php";
    private static final String PATH_PLAY_MOVIE = "/play/movie.php";
    private static final String HEADER_ACCEPT = "Accept";
    private static final String HEADER_ACCEPT_RANGES = "Accept-Ranges";
    private static final String HEADER_ACCESS_CONTROL_ALLOW_ORIGIN = "Access-Control-Allow-Origin";
    private static final String HEADER_CACHE_CONTROL = "Cache-Control";
    private static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";
    private static final String HEADER_CONTENT_LENGTH = "Content-Length";
    private static final String HEADER_CONTENT_RANGE = "Content-Range";
    private static final String HEADER_CONTENT_TYPE = "Content-Type";
    private static final String HEADER_COOKIE = "Cookie";
    private static final String HEADER_HOST = "Host";
    private static final String HEADER_LOCATION = "Location";
    private static final String HEADER_ORIGIN = "Origin";
    private static final String HEADER_PRAGMA = "Pragma";
    private static final String HEADER_RANGE = "Range";
    private static final String HEADER_REFERER = "Referer";
    private static final String HEADER_SET_COOKIE = "Set-Cookie";
    private static final String HEADER_X_USER_AGENT = "X-User-Agent";
    private static final String MAG_USER_AGENT = "Mozilla/5.0 (QtEmbedded; U; Linux; C) AppleWebKit/533.3 (KHTML, like Gecko) MAG200 stbapp ver: 2 rev: 250 Safari/533.3";
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36";
    private static final long UNKNOWN_CONTENT_LENGTH = 0L;
    private static final String PROXY_STREAM_PATH = "/proxy-stream?src=";
    private static final int MAX_SOURCE_URL_LENGTH = 8192;
    private static final int MAX_PLAYLIST_BYTES = 4 * 1024 * 1024;
    private static final int MAX_PLAYLIST_LINES = 100_000;
    private static final String METHOD_GET = "GET";
    private static final String METHOD_HEAD = "HEAD";
    private static final String SCHEME_HTTPS = "https";
    private static final String CACHE_NO_STORE = "no-cache";
    private final Predicate<String> targetUrlValidator;

    public HttpProxyStreamServer() {
        this(HttpProxyStreamServer::isAllowedTargetUrl);
    }

    HttpProxyStreamServer(Predicate<String> targetUrlValidator) {
        this.targetUrlValidator = Objects.requireNonNull(targetUrlValidator);
    }

    @Override
    public void handle(HttpExchange ex) throws IOException {
        writeCorsHeaders(ex);
        if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.sendResponseHeaders(204, -1);
            return;
        }
        if (!isSupportedMethod(ex)) return;
        String current = validatedSource(ex);
        if (current == null) return;
        proxy(ex, current);
    }

    private boolean isSupportedMethod(HttpExchange ex) throws IOException {
        if (METHOD_GET.equalsIgnoreCase(ex.getRequestMethod()) || METHOD_HEAD.equalsIgnoreCase(ex.getRequestMethod())) return true;
        ex.getResponseHeaders().set("Allow", "GET, HEAD, OPTIONS");
        ex.sendResponseHeaders(405, -1);
        return false;
    }

    private String validatedSource(HttpExchange ex) throws IOException {
        String source = getParam(ex, "src");
        if (isBlank(source) || source.length() > MAX_SOURCE_URL_LENGTH) {
            ex.sendResponseHeaders(400, -1);
            return null;
        }
        String current = source.trim();
        if (!targetUrlValidator.test(current)) {
            ex.sendResponseHeaders(400, -1);
            return null;
        }
        return current;
    }

    private void proxy(HttpExchange ex, String current) throws IOException {
        List<String> cookies = new ArrayList<>();
        String requestMethod = ex.getRequestMethod();
        boolean responseStarted = false;

        try {
            try (HttpUtil.StreamResult upstream = openResolvedStream(current, cookies, readForwardHeaders(ex), requestMethod)) {
                if (upstream == null) {
                    sendBadGateway(ex);
                    return;
                }

                byte[] responseBody = responseBody(upstream, requestMethod);
                writeResponseHeaders(ex, upstream.responseHeaders());
                responseStarted = true;
                sendResponse(ex, upstream, responseBody, requestMethod);
            }
        } catch (PlaylistTooLargeException _) {
            if (!responseStarted) {
                ex.sendResponseHeaders(413, -1);
            }
        } catch (IOException | RuntimeException _) {
            if (!responseStarted) {
                sendBadGateway(ex);
            }
        }
    }

    private byte[] responseBody(HttpUtil.StreamResult upstream, String requestMethod) throws IOException {
        if (!shouldRewriteHlsPlaylist(upstream) || METHOD_HEAD.equalsIgnoreCase(requestMethod)) return null;
        long advertisedLength = resolveContentLength(firstHeader(upstream.responseHeaders(), HEADER_CONTENT_LENGTH));
        if (advertisedLength > MAX_PLAYLIST_BYTES) throw new PlaylistTooLargeException();
        return rewriteHlsPlaylist(readPlaylistBody(resolvedBodyStream(upstream)), upstream.requestUri());
    }

    private void sendResponse(HttpExchange ex, HttpUtil.StreamResult upstream, byte[] responseBody,
                              String requestMethod) throws IOException {
        long length = responseBody == null
                ? resolveContentLength(firstHeader(upstream.responseHeaders(), HEADER_CONTENT_LENGTH))
                : responseBody.length;
        ex.sendResponseHeaders(upstream.statusCode(), length);
        if (METHOD_HEAD.equalsIgnoreCase(requestMethod)) return;
        try (OutputStream os = ex.getResponseBody()) {
            if (responseBody != null) {
                os.write(responseBody);
                return;
            }
            try (InputStream is = resolvedBodyStream(upstream)) {
                copyStream(is, os);
            }
        }
    }

    private static boolean isAllowedTargetUrl(String rawUrl) {
        if (isBlank(rawUrl) || rawUrl.length() > MAX_SOURCE_URL_LENGTH) {
            return false;
        }
        try {
            URI uri = URI.create(rawUrl.trim());
            String scheme = uri.getScheme();
            if (scheme == null || (!"http".equalsIgnoreCase(scheme) && !SCHEME_HTTPS.equalsIgnoreCase(scheme))) {
                return false;
            }
            String host = hostOf(uri);
            if (isBlank(host)) {
                return false;
            }
            InetAddress[] addresses = InetAddress.getAllByName(host);
            if (addresses == null || addresses.length == 0) {
                return false;
            }
            for (InetAddress address : addresses) {
                if (isBlockedAddress(address)) {
                    return false;
                }
            }
            return true;
        } catch (IllegalArgumentException | UnknownHostException ex) {
            return false;
        }
    }

    private static boolean isBlockedAddress(InetAddress address) {
        return address.isAnyLocalAddress()
                || address.isLoopbackAddress()
                || address.isLinkLocalAddress()
                || address.isSiteLocalAddress()
                || address.isMulticastAddress()
                || isCarrierGradeNatAddress(address)
                || isReservedIpv4Address(address)
                || isUniqueLocalIpv6Address(address);
    }

    private static String hostOf(URI uri) {
        if (uri == null) {
            return "";
        }
        String host = uri.getHost();
        if (!isBlank(host)) {
            return host;
        }
        String authority = uri.getRawAuthority();
        if (isBlank(authority)) {
            return "";
        }
        int userInfoEnd = authority.lastIndexOf('@');
        String hostPort = userInfoEnd >= 0 ? authority.substring(userInfoEnd + 1) : authority;
        if (hostPort.startsWith("[")) {
            int closingBracket = hostPort.indexOf(']');
            return closingBracket > 1 ? hostPort.substring(1, closingBracket) : "";
        }
        int portSeparator = hostPort.lastIndexOf(':');
        if (portSeparator > 0 && isAsciiDigits(hostPort.substring(portSeparator + 1))) {
            return hostPort.substring(0, portSeparator);
        }
        return hostPort;
    }

    private static boolean isCarrierGradeNatAddress(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 4 && (bytes[0] & 0xff) == 100 && (bytes[1] & 0xff) >= 64 && (bytes[1] & 0xff) <= 127;
    }

    private static boolean isReservedIpv4Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        if (bytes.length != 4) {
            return false;
        }
        int first = bytes[0] & 0xff;
        int second = bytes[1] & 0xff;
        return (first == 192 && second == 0)
                || (first == 198 && (second == 18 || second == 19));
    }

    private static boolean isUniqueLocalIpv6Address(InetAddress address) {
        byte[] bytes = address.getAddress();
        return bytes.length == 16 && (bytes[0] & 0xfe) == 0xfc;
    }

    private HttpUtil.StreamResult openResolvedStream(String current,
                                                     List<String> cookies,
                                                     Map<String, String> forwardHeaders,
                                                     String requestMethod) throws IOException {
        String originalAuthority = authorityOf(current);
        for (int i = 0; i < 6; i++) {
            Map<String, String> upstreamHeaders = buildUpstreamHeaders(current, cookies, forwardHeaders);
            String redirectHost = redirectHostHeader(originalAuthority, current);
            if (!isBlank(redirectHost)) {
                upstreamHeaders.put(HEADER_HOST, redirectHost);
            }

            String upstreamMethod = "HEAD".equalsIgnoreCase(requestMethod) ? "HEAD" : "GET";
            HttpUtil.StreamResult upstream = HttpUtil.openStream(
                    current, upstreamHeaders, upstreamMethod, null, HttpUtil.RequestOptions.streamingProxy());
            collectCookies(upstream.responseHeaders(), cookies);
            int status = upstream.statusCode();
            if (status >= 300 && status <= 399) {
                String redirected = resolveRedirect(current, upstream);
                if (redirected == null) return null;
                current = redirected;
                continue;
            }
            if (status == HttpUtil.STATUS_NOT_ACCEPTABLE) {
                String fallback = resolve406Fallback(current, upstream);
                if (fallback != null) { current = fallback; continue; }
            }
            return upstream;
        }
        return null;
    }

    private String resolveRedirect(String current, HttpUtil.StreamResult upstream) throws IOException {
        try (upstream) {
            String location = firstHeader(upstream.responseHeaders(), HEADER_LOCATION);
            if (isBlank(location)) return null;
            URI resolved = URI.create(current).resolve(location);
            String target = downgradeHttpsToHttp(resolved.toString());
            return targetUrlValidator.test(target) ? target : null;
        }
    }

    private String resolve406Fallback(String current, HttpUtil.StreamResult upstream) throws IOException {
        try (upstream) {
            String fallback = build406Fallback(current);
            return !isBlank(fallback) && !fallback.equals(current) && targetUrlValidator.test(fallback) ? fallback : null;
        }
    }

    private String redirectHostHeader(String originalAuthority, String currentUrl) {
        if (isBlank(originalAuthority) || isBlank(currentUrl)) {
            return "";
        }
        try {
            URI current = URI.create(currentUrl.trim());
            String host = current.getHost();
            if (isBlank(host) || !isNumericHost(host)) {
                return "";
            }
            return originalAuthority;
        } catch (IllegalArgumentException _) {
            return "";
        }
    }

    private String authorityOf(String url) {
        if (isBlank(url)) {
            return "";
        }
        try {
            return URI.create(url.trim()).getRawAuthority();
        } catch (IllegalArgumentException _) {
            return "";
        }
    }

    private boolean isNumericHost(String host) {
        if (isBlank(host)) {
            return false;
        }
        String value = host.trim();
        if (value.contains(":")) {
            return true;
        }
        String[] octets = value.split("\\.", -1);
        if (octets.length != 4) {
            return false;
        }
        for (String octet : octets) {
            if (!isAsciiDigits(octet)) {
                return false;
            }
            try {
                if (Integer.parseInt(octet) > 255) {
                    return false;
                }
            } catch (NumberFormatException _) {
                return false;
            }
        }
        return true;
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
        if (isHlsPlaylistUrl(currentUrl)) {
            // Live manifests must not be served from an intermediary's stale cache.
            // A stale playlist can reference segments that have already expired,
            // causing the browser player to fail before it reaches the live edge.
            upstreamHeaders.put(HEADER_CACHE_CONTROL, CACHE_NO_STORE);
            upstreamHeaders.put(HEADER_PRAGMA, CACHE_NO_STORE);
        }
        if (stalkerStyle) {
            upstreamHeaders.put(HEADER_X_USER_AGENT, "Model: MAG250; Link: WiFi");
            upstreamHeaders.put(HEADER_PRAGMA, CACHE_NO_STORE);
        }
        addHeaderIfPresent(upstreamHeaders, HEADER_RANGE, forwardedHeaders.get(HEADER_RANGE));
        String origin = resolveUpstreamOriginHeader(currentUrl, forwardedHeaders.get(HEADER_ORIGIN));
        String referer = resolveUpstreamRefererHeader(currentUrl, forwardedHeaders.get(HEADER_REFERER));
        addHeaderIfPresent(upstreamHeaders, HEADER_ORIGIN, origin);
        addHeaderIfPresent(upstreamHeaders, HEADER_REFERER, referer);
        addStalkerCookieFromUrl(upstreamHeaders, currentUrl, cookies);
        if (!cookies.isEmpty()) {
            upstreamHeaders.computeIfAbsent(HEADER_COOKIE, _ -> String.join("; ", cookies));
        }
        return upstreamHeaders;
    }

    private boolean isHlsPlaylistUrl(String url) {
        return !isBlank(url) && url.toLowerCase().contains(".m3u8");
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
        return isPortalPlaybackPath(lower)
                || hasNumericLastPathSegment(lower);
    }

    private boolean isStalkerPortalStream(String currentUrl) {
        if (isBlank(currentUrl)) {
            return false;
        }
        String lower = currentUrl.toLowerCase();
        return isPortalPlaybackPath(lower)
                && (lower.contains("play_token=") || lower.contains("mac="));
    }

    private boolean isPortalPlaybackPath(String lowerUrl) {
        if (isBlank(lowerUrl)) {
            return false;
        }
        return lowerUrl.contains(PATH_LIVE_PLAY)
                || lowerUrl.contains(PATH_PLAY_LIVE)
                || lowerUrl.contains(PATH_PLAY_MOVIE);
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
            return isLocalServerHost(host);
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
                    || (SCHEME_HTTPS.equalsIgnoreCase(scheme) && port == 443);
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
        writeCorsHeaders(ex);
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

    private boolean shouldRewriteHlsPlaylist(HttpUtil.StreamResult upstream) {
        if (upstream == null || upstream.statusCode() < 200 || upstream.statusCode() >= 300) {
            return false;
        }
        String contentType = firstHeader(upstream.responseHeaders(), HEADER_CONTENT_TYPE).toLowerCase();
        if (contentType.contains("mpegurl") || contentType.contains("application/vnd.apple")) {
            return true;
        }
        try {
            String path = URI.create(upstream.requestUri()).getPath();
            if (isBlank(path)) {
                return false;
            }
            String lowerPath = path.toLowerCase();
            return lowerPath.endsWith(".m3u8") || lowerPath.endsWith(".m3u");
        } catch (Exception _) {
            return false;
        }
    }

    private byte[] rewriteHlsPlaylist(byte[] data, String playlistUrl) throws PlaylistTooLargeException {
        if (data == null || data.length == 0 || isBlank(playlistUrl)) {
            return data;
        }
        String body = new String(data, StandardCharsets.UTF_8);
        if (!body.trim().startsWith("#EXTM3U")) {
            return data;
        }

        String[] lines = body.split("\\r?\\n", -1);
        if (lines.length > MAX_PLAYLIST_LINES) {
            throw new PlaylistTooLargeException();
        }
        StringBuilder rewritten = new StringBuilder(body.length() + 128);
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.startsWith("#")) {
                line = rewriteHlsUriAttributes(line, playlistUrl);
            } else if (!trimmed.isEmpty()) {
                line = buildProxiedHlsUri(trimmed, playlistUrl);
            }
            rewritten.append(line);
            if (i < lines.length - 1) {
                rewritten.append('\n');
            }
        }
        return rewritten.toString().getBytes(StandardCharsets.UTF_8);
    }

    private String rewriteHlsUriAttributes(String line, String playlistUrl) {
        String marker = "URI=\"";
        int searchFrom = 0;
        StringBuilder rewritten = null;
        while (true) {
            int start = line.indexOf(marker, searchFrom);
            if (start < 0) {
                if (rewritten == null) {
                    return line;
                }
                rewritten.append(line.substring(searchFrom));
                return rewritten.toString();
            }
            int valueStart = start + marker.length();
            int valueEnd = line.indexOf('"', valueStart);
            if (valueEnd < 0) {
                return rewritten == null ? line : rewritten.append(line.substring(searchFrom)).toString();
            }
            if (rewritten == null) {
                rewritten = new StringBuilder(line.length() + 64);
            }
            rewritten.append(line, searchFrom, valueStart);
            rewritten.append(buildProxiedHlsUri(line.substring(valueStart, valueEnd), playlistUrl));
            rewritten.append('"');
            searchFrom = valueEnd + 1;
        }
    }

    private String buildProxiedHlsUri(String candidate, String playlistUrl) {
        if (isBlank(candidate)) {
            return candidate;
        }
        String trimmed = candidate.trim();
        String lower = trimmed.toLowerCase();
        if (lower.startsWith("data:")
                || lower.startsWith("blob:")
                || lower.startsWith("file:")
                || trimmed.contains(PROXY_STREAM_PATH)) {
            return candidate;
        }
        try {
            URI resolved = URI.create(playlistUrl).resolve(trimmed);
            String scheme = resolved.getScheme();
            if (!("http".equalsIgnoreCase(scheme) || SCHEME_HTTPS.equalsIgnoreCase(scheme))
                    || isBlank(resolved.getHost())) {
                return candidate;
            }
            String absolute = resolved.toString();
            if (isBlank(absolute)) {
                return candidate;
            }
            return PROXY_STREAM_PATH + URLEncoder.encode(absolute, StandardCharsets.UTF_8);
        } catch (Exception _) {
            return candidate;
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

    private void writeCorsHeaders(HttpExchange ex) {
        ex.getResponseHeaders().set(HEADER_ACCESS_CONTROL_ALLOW_ORIGIN, "*");
        ex.getResponseHeaders().set("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
        ex.getResponseHeaders().set("Access-Control-Allow-Headers", "Range, Origin, Accept");
        ex.getResponseHeaders().set("Access-Control-Expose-Headers", "Accept-Ranges, Content-Length, Content-Range, Content-Type");
    }

    private byte[] readPlaylistBody(InputStream input) throws IOException {
        ByteArrayOutputStream output = new ByteArrayOutputStream(Math.min(MAX_PLAYLIST_BYTES, 64 * 1024));
        byte[] buffer = new byte[8192];
        int total = 0;
        int read;
        while ((read = input.read(buffer)) != -1) {
            total += read;
            if (total > MAX_PLAYLIST_BYTES) {
                throw new PlaylistTooLargeException();
            }
            output.write(buffer, 0, read);
        }
        return output.toByteArray();
    }

    private static final class PlaylistTooLargeException extends IOException {
    }

    private String downgradeHttpsToHttp(String url) {
        if (isBlank(url)) return url;
        String lower = url.toLowerCase();
        if (lower.startsWith("https://") && (isPortalPlaybackPath(lower) || hasNumericLastPathSegment(lower))) {
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
            List<String> segments = java.util.Arrays.stream(parts)
                    .filter(part -> !isBlank(part))
                    .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
            if (segments.size() < 3) {
                return originalUrl;
            }

            String last = segments.get(segments.size() - 1);
            if (!isAsciiDigits(last)) {
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

    private boolean hasNumericLastPathSegment(String url) {
        if (isBlank(url)) {
            return false;
        }
        int queryStart = url.indexOf('?');
        String pathOnly = queryStart >= 0 ? url.substring(0, queryStart) : url;
        int lastSlash = pathOnly.lastIndexOf('/');
        if (lastSlash < 0 || lastSlash == pathOnly.length() - 1) {
            return false;
        }
        return isAsciiDigits(pathOnly.substring(lastSlash + 1));
    }

    private static boolean isAsciiDigits(String value) {
        if (isBlank(value)) {
            return false;
        }
        for (int i = 0; i < value.length(); i++) {
            if (!Character.isDigit(value.charAt(i))) {
                return false;
            }
        }
        return true;
    }
}
