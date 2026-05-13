package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;

import java.io.IOException;

/**
 * Gateway/controller for all player resolution requests.
 * Canonical endpoint is /player, while legacy /player/* paths are still accepted.
 */
public class HttpPlayerGatewayServer implements HttpHandler {
    private final HttpPlayerJsonServer delegate = new HttpPlayerJsonServer();

    @Override
    public void handle(HttpExchange exchange) throws IOException {
        delegate.handle(exchange);
    }
}

