package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpImageServer implements HttpHandler {
    private static final String CONTENT_TYPE_PNG = "image/png";
    private static final String CONTENT_TYPE_SVG = "image/svg+xml";
    private static final String CONTENT_TYPE_JPEG = "image/jpeg";
    private static final String CONTENT_TYPE_WEBP = "image/webp";
    private static final String CONTENT_TYPE_GIF = "image/gif";

    @Override
    public void handle(HttpExchange ex) throws IOException {
        if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) {
            ex.getResponseHeaders().set("Allow", "GET");
            ex.sendResponseHeaders(405, -1);
            return;
        }

        Path filePath;
        try {
            filePath = StaticWebFileResolver.resolve(ex);
        } catch (IOException _) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        String contentType = contentTypeFor(filePath);
        if (contentType == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        byte[] bytes = Files.readAllBytes(filePath);
        ex.getResponseHeaders().set("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (var os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentTypeFor(Path filePath) {
        if (filePath == null) {
            return null;
        }
        String name = filePath.getFileName().toString().toLowerCase();
        if (name.endsWith(".png")) return CONTENT_TYPE_PNG;
        if (name.endsWith(".svg")) return CONTENT_TYPE_SVG;
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")) return CONTENT_TYPE_JPEG;
        if (name.endsWith(".webp")) return CONTENT_TYPE_WEBP;
        if (name.endsWith(".gif")) return CONTENT_TYPE_GIF;
        return null;
    }
}
