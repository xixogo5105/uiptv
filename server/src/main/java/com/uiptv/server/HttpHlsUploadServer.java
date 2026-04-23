package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.InMemoryHlsService;
import org.apache.commons.io.IOUtils;

import java.io.IOException;
import java.io.InputStream;

public class HttpHlsUploadServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        String method = ex.getRequestMethod();
        String path = ex.getRequestURI().getPath();
        String fileName = path.substring(path.lastIndexOf('/') + 1);

        if ("PUT".equalsIgnoreCase(method)) {
            try (InputStream is = ex.getRequestBody()) {
                byte[] data = IOUtils.toByteArray(is);
                InMemoryHlsService.getInstance().put(fileName, data);
            }
            ex.sendResponseHeaders(200, -1);
        } else if ("DELETE".equalsIgnoreCase(method)) {
            InMemoryHlsService.getInstance().remove(fileName);
            ex.sendResponseHeaders(200, -1);
        } else {
            ex.sendResponseHeaders(405, -1); // Method Not Allowed
        }
    }
}
