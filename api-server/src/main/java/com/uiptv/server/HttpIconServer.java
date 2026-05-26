package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class HttpIconServer implements HttpHandler {
    private static final String RESOURCE_ICON = "/icon.ico";
    private static final String ICON_PATH = "/icon.ico";
    private static final String PNG_ICON_PATH = "/icon.png";
    private static final String ICON_192_PATH = "/icon-192.png";
    private static final String ICON_512_PATH = "/icon-512.png";
    private static final String MASKABLE_ICON_512_PATH = "/icon-maskable-512.png";
    private static final String CONTENT_TYPE_ICON = "image/x-icon";
    private static final String CONTENT_TYPE_PNG = "image/png";

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
            // Fall through to classpath.
        }

        if (bytes == null && ICON_PATH.equals(requestPath)) {
            try (InputStream is = HttpIconServer.class.getResourceAsStream(RESOURCE_ICON)) {
                if (is != null) {
                    bytes = IOUtils.toByteArray(is);
                }
            }
        }

        if (bytes == null) {
            ex.sendResponseHeaders(404, -1);
            return;
        }

        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }

    private static String contentTypeFor(String path) {
        return switch (path) {
            case ICON_PATH -> CONTENT_TYPE_ICON;
            case PNG_ICON_PATH, ICON_192_PATH, ICON_512_PATH, MASKABLE_ICON_512_PATH -> CONTENT_TYPE_PNG;
            default -> null;
        };
    }
}
