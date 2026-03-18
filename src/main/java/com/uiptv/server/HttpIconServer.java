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

    @Override
    public void handle(HttpExchange ex) throws IOException {
        byte[] bytes = null;

        try {
            Path filePath = StaticWebFileResolver.resolve(ex);
            bytes = Files.readAllBytes(filePath);
        } catch (IOException _) {
            // Fall through to classpath.
        }

        if (bytes == null) {
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

        ex.getResponseHeaders().add("Content-Type", "image/x-icon");
        ex.sendResponseHeaders(200, bytes.length);
        try (OutputStream os = ex.getResponseBody()) {
            os.write(bytes);
        }
    }
}
