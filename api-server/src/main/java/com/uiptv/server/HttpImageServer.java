package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpImageServer implements HttpHandler {
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";
    private static final String PNG_CONTENT_TYPE = "image/png";
    private static final String JPEG_CONTENT_TYPE = "image/jpeg";
    private static final String WEBP_CONTENT_TYPE = "image/webp";
    private static final String GIF_CONTENT_TYPE = "image/gif";
    private static final String ICO_CONTENT_TYPE = "image/x-icon";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Allow", "GET");
            ex.sendResponseHeaders(405, -1);
            return;
        }

        String requestPath = ex.getRequestURI().getPath();
        String contentType = contentTypeFor(requestPath);
        if (contentType == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        byte[] bytes = null;
        try {
            Path filePath = StaticWebFileResolver.resolve(ex);
            bytes = Files.readAllBytes(filePath);
        } catch (IOException _) {
            // Fall through to classpath resource loading
        }

        if (bytes == null) {
            String resourcePath = "/web" + (requestPath.startsWith("/") ? requestPath : "/" + requestPath);
            try (InputStream is = HttpImageServer.class.getResourceAsStream(resourcePath)) {
                if (is != null) {
                    bytes = IOUtils.toByteArray(is);
                }
            } catch (IOException _) {
                // Ignore and handle 404 below
            }
        }

        if (bytes == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.getResponseHeaders().set("Cache-Control", "public, max-age=86400");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    static String contentTypeFor(String path) {
        if (path == null) return null;
        String lower = path.toLowerCase();
        if (lower.endsWith(".svg")) return SVG_CONTENT_TYPE;
        if (lower.endsWith(".png")) return PNG_CONTENT_TYPE;
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return JPEG_CONTENT_TYPE;
        if (lower.endsWith(".webp")) return WEBP_CONTENT_TYPE;
        if (lower.endsWith(".gif")) return GIF_CONTENT_TYPE;
        if (lower.endsWith(".ico")) return ICO_CONTENT_TYPE;
        return null;
    }
}
