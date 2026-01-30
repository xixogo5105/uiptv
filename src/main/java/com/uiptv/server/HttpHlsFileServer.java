package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.service.InMemoryHlsService;

import java.io.IOException;
import java.io.OutputStream;

import static com.uiptv.util.ServerUtils.CONTENT_TYPE_M3U8;
import static com.uiptv.util.ServerUtils.CONTENT_TYPE_TS;

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

        String contentType = fileName.endsWith(".m3u8") ? CONTENT_TYPE_M3U8 : CONTENT_TYPE_TS;
        
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Content-Type", contentType);
        ex.sendResponseHeaders(200, data.length);

        try (OutputStream os = ex.getResponseBody()) {
            os.write(data);
        }
    }
}
