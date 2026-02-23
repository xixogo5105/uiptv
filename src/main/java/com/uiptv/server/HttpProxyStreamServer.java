package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;

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

        String current = URLDecoder.decode(source, StandardCharsets.UTF_8);
        HttpURLConnection conn = null;

        try {
            String rangeHeader = ex.getRequestHeaders().getFirst("Range");
            for (int i = 0; i < 6; i++) {
                conn = (HttpURLConnection) new URL(current).openConnection();
                conn.setInstanceFollowRedirects(false);
                conn.setConnectTimeout(7000);
                conn.setReadTimeout(15000);
                conn.setRequestMethod("GET");
                conn.setRequestProperty("User-Agent", "UIPTV/1.0");
                if (!isBlank(rangeHeader)) {
                    conn.setRequestProperty("Range", rangeHeader);
                }
                conn.connect();

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
                break;
            }

            if (conn == null) {
                ex.sendResponseHeaders(502, -1);
                return;
            }

            int upstreamStatus = conn.getResponseCode();
            if (!(upstreamStatus == HttpURLConnection.HTTP_OK || upstreamStatus == HttpURLConnection.HTTP_PARTIAL)) {
                ex.sendResponseHeaders(502, -1);
                return;
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
            if (!isBlank(acceptRanges)) ex.getResponseHeaders().add("Accept-Ranges", acceptRanges);
            if (!isBlank(contentRange)) ex.getResponseHeaders().add("Content-Range", contentRange);

            long contentLength = 0;
            try {
                contentLength = isBlank(contentLengthHeader) ? 0 : Long.parseLong(contentLengthHeader);
            } catch (Exception ignored) {
                contentLength = 0;
            }
            ex.sendResponseHeaders(upstreamStatus, contentLength > 0 ? contentLength : 0);

            try (InputStream is = conn.getInputStream();
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

    private String downgradeHttpsToHttp(String url) {
        if (isBlank(url)) return url;
        String lower = url.toLowerCase();
        if (lower.startsWith("https://") && (lower.contains("/live/play/") || lower.contains("/play/movie.php") || lower.matches(".*/\\d+(\\?.*)?$"))) {
            return "http://" + url.substring("https://".length());
        }
        return url;
    }
}
