package com.uiptv.server;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.util.StringUtils;

import java.io.IOException;
import java.nio.file.Path;

import static com.uiptv.util.ServerUtils.generateCssResponse;

public class HttpCssServer implements HttpHandler {
    @Override
    public void handle(HttpExchange ex) throws IOException {
        try {
            Path filePath = StaticWebFileResolver.resolve(ex);
            generateCssResponse(ex, StringUtils.EMPTY + StaticWebFileResolver.readUtf8(filePath), filePath.getFileName().toString());
        } catch (IOException _) {
            ex.sendResponseHeaders(404, -1);
        }
    }
}
