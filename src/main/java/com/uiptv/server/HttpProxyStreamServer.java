package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

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
        HttpURLConnection conn = null;
        List<String> cookies = new ArrayList<>();

        try {
            String rangeHeader = ex.getRequestHeaders().getFirst("Range");
            String acceptHeader = ex.getRequestHeaders().getFirst("Accept");
            String refererHeader = ex.getRequestHeaders().getFirst("Referer");
            String originHeader = ex.getRequestHeaders().getFirst("Origin");
            for (int i = 0; i < 6; i++) {
                conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "UIPTV/1.0");
                conn.setRequestProperty("Accept", isBlank(acceptHeader) ? "*/*" : acceptHeader);
                conn.setRequestProperty("Accept-Encoding", "identity");
                if (!isBlank(rangeHeader)) {
                    conn.setRequestProperty("Range", rangeHeader);
                }
                if (!isBlank(refererHeader)) {
                    conn.setRequestProperty("Referer", refererHeader);
                }
                if (!isBlank(originHeader)) {
                    conn.setRequestProperty("Origin", originHeader);
                }
                if (!cookies.isEmpty()) {
                    conn.setRequestProperty("Cookie", String.join("; ", cookies));
                }
                conn.connect();
                collectCookies(conn, cookies);

                int status = conn.getResponseCode();
                if (status >= 300 && status <= 399) {
                    String location = conn.getHeaderField("Location");
                    if (isBlank(location)) {
                        break;
                    }
                    URI base = URI.create(current);
                    URI resolved = base.resolve(location);
                    current = downgradeHttpsToHttp(resolved.toString());
                    conn.disconnect();
                    conn = null;
                    continue;
                }
                if (status == HttpURLConnection.HTTP_NOT_ACCEPTABLE) {
                    String fallback = build406Fallback(current);
                    if (!isBlank(fallback) && !fallback.equals(current)) {
                        conn.disconnect();
                        conn = null;
                        current = fallback;
                        continue;
                    }
                }
                break;
            }

            if (conn == null) {
                ex.sendResponseHeaders(502, -1);
                return;
            }

            int upstreamStatus = conn.getResponseCode();
            InputStream upstreamStream = upstreamStatus >= 400 ? conn.getErrorStream() : conn.getInputStream();
            if (upstreamStream == null) {
                upstreamStream = new ByteArrayInputStream(new byte[0]);
            }

            String contentType = conn.getContentType();
            if (isBlank(contentType)) {
                contentType = "application/octet-stream";
            }
            ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
            ex.getResponseHeaders().add("Cache-Control", "no-store");
            ex.getResponseHeaders().add("Content-Type", contentType);
            String acceptRanges = conn.getHeaderField("Accept-Ranges");
            String contentRange = conn.getHeaderField("Content-Range");
            String contentLengthHeader = conn.getHeaderField("Content-Length");
            String contentDisposition = conn.getHeaderField("Content-Disposition");
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
            if (conn != null) {
                conn.disconnect();
            }
        }
    }

    private void collectCookies(HttpURLConnection conn, List<String> cookies) {
        if (conn == null || cookies == null) {
            return;
        }
        List<String> setCookie = conn.getHeaderFields().get("Set-Cookie");
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
