package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.InMemoryHlsService;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

import static com.uiptv.util.ServerUtils.CONTENT_TYPE_M3U8;
import static com.uiptv.util.ServerUtils.CONTENT_TYPE_TS;
import static com.uiptv.util.ServerUtils.getParam;
import static com.uiptv.util.StringUtils.isBlank;

public class HttpHlsFileServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String path = ex.getRequestURI().getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);
        byte[] data = InMemoryHlsService.getInstance().get(fileName);

        if (data == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        boolean m3u8Request = fileName.endsWith(".m3u8");
        if (m3u8Request && isHvecEnabled(getParam(ex, "hvec"))) {
            data = rewritePlaylistWithHvecQuery(data);
        }

        String contentType = fileName.endsWith(".m3u8") ? CONTENT_TYPE_M3U8 : CONTENT_TYPE_TS;
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(200, data.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }

    private static boolean isHvecEnabled(String value) {
        if (isBlank(value)) {
            return false;
        }
        String normalized = value.trim().toLowerCase();
        return "1".equals(normalized) || "true".equals(normalized) || "yes".equals(normalized) || "on".equals(normalized);
    }

    private static byte[] rewritePlaylistWithHvecQuery(byte[] data) {
        if (data == null || data.length == 0) {
            return data;
        }

        String body = new String(data, StandardCharsets.UTF_8);
        String[] lines = body.split("\\r?\\n", -1);
        StringBuilder rewritten = new StringBuilder(body.length() + 32);

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (!trimmed.isEmpty() && !trimmed.startsWith("#") && !trimmed.contains("hvec=")) {
                line = line + (line.contains("?") ? "&" : "?") + "hvec=1";
            }
            rewritten.append(line);
            if (i < lines.length - 1) {
                rewritten.append('\n');
            }
        }
        return rewritten.toString().getBytes(StandardCharsets.UTF_8);
    }
}
