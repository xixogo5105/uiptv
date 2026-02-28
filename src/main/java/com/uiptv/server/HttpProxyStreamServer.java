package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.HttpUtil;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpProxyStreamServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String source = getParam(ex, "src");
        if (isBlank(source)) {
            ex.sendResponseHeaders(400, -1);
            return;
        }

        String current = source.trim();
        HttpUtil.StreamResult upstream = null;
        List<String> cookies = new ArrayList<>();

        try {
            String rangeHeader = ex.getRequestHeaders().getFirst("Range");
            String acceptHeader = ex.getRequestHeaders().getFirst("Accept");
            String refererHeader = ex.getRequestHeaders().getFirst("Referer");
            String originHeader = ex.getRequestHeaders().getFirst("Origin");
            for (int i = 0; i < 6; i++) {
                if (upstream != null) {
                    upstream.close();
                    upstream = null;
                }

                Map<String, String> upstreamHeaders = new LinkedHashMap<>();
                upstreamHeaders.put("User-Agent", "UIPTV/1.0");
                upstreamHeaders.put("Accept", isBlank(acceptHeader) ? "*/*" : acceptHeader);
                upstreamHeaders.put("Accept-Encoding", "identity");
                if (!isBlank(rangeHeader)) {
                    upstreamHeaders.put("Range", rangeHeader);
                }
                if (!isBlank(refererHeader)) {
                    upstreamHeaders.put("Referer", refererHeader);
                }
                if (!isBlank(originHeader)) {
                    upstreamHeaders.put("Origin", originHeader);
                }
                if (!cookies.isEmpty()) {
                    upstreamHeaders.put("Cookie", String.join("; ", cookies));
                }
                upstream = HttpUtil.openStream(current, upstreamHeaders, "GET", null, new HttpUtil.RequestOptions(false, true));
                collectCookies(upstream.responseHeaders(), cookies);

                int status = upstream.statusCode();
                if (status >= 300 && status <= 399) {
                    String location = firstHeader(upstream.responseHeaders(), "Location");
                    if (isBlank(location)) {
                        break;
                    }
                    URI base = URI.create(current);
                    URI resolved = base.resolve(location);
                    current = downgradeHttpsToHttp(resolved.toString());
                    upstream.close();
                    upstream = null;
                    continue;
                }
                if (status == HttpUtil.STATUS_NOT_ACCEPTABLE) {
                    String fallback = build406Fallback(current);
                    if (!isBlank(fallback) && !fallback.equals(current)) {
                        upstream.close();
                        upstream = null;
                        current = fallback;
                        continue;
                    }
                }
                break;
            }

            if (upstream == null) {
                ex.sendResponseHeaders(502, -1);
                return;
            }

            int upstreamStatus = upstream.statusCode();
            InputStream upstreamStream = upstream.bodyStream();
            if (upstreamStream == null) {
                upstreamStream = new ByteArrayInputStream(new byte[0]);
            }

            String contentType = firstHeader(upstream.responseHeaders(), "Content-Type");
            if (isBlank(contentType)) {
                contentType = "application/octet-stream";
            }
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.getResponseHeaders().add("Content-Type", contentType);
            String acceptRanges = firstHeader(upstream.responseHeaders(), "Accept-Ranges");
            String contentRange = firstHeader(upstream.responseHeaders(), "Content-Range");
            String contentLengthHeader = firstHeader(upstream.responseHeaders(), "Content-Length");
            String contentDisposition = firstHeader(upstream.responseHeaders(), "Content-Disposition");
            if (!isBlank(acceptRanges)) ex.getResponseHeaders().add("Accept-Ranges", acceptRanges);
            if (!isBlank(contentRange)) ex.getResponseHeaders().add("Content-Range", contentRange);
            if (!isBlank(contentDisposition)) ex.getResponseHeaders().add("Content-Disposition", contentDisposition);

            long contentLength = 0;
            try {
                contentLength = isBlank(contentLengthHeader) ? 0 : Long.parseLong(contentLengthHeader);
            } catch (Exception ignored) {
                contentLength = 0;
            }
            ex.sendResponseHeaders(upstreamStatus, contentLength > 0 ? contentLength : 0);

            try (InputStream is = upstreamStream;
                 OutputStream os = ex.getResponseBody()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = is.read(buffer)) != -1) {
                    os.write(buffer, 0, read);
                }
            }
        } catch (Exception ignored) {
            ex.sendResponseHeaders(502, -1);
        } finally {
            if (upstream != null) {
                upstream.close();
            }
        }
    }

    private void collectCookies(Map<String, List<String>> responseHeaders, List<String> cookies) {
        if (responseHeaders == null || cookies == null) {
            return;
        }
        List<String> setCookie = null;
        for (Map.Entry<String, List<String>> entry : responseHeaders.entrySet()) {
            if (entry.getKey() != null && "Set-Cookie".equalsIgnoreCase(entry.getKey())) {
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
                if (values != null && !values.isEmpty() && !isBlank(values.get(0))) {
                    return values.get(0);
                }
            }
        }
        return "";
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
        } catch (Exception ignored) {
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
