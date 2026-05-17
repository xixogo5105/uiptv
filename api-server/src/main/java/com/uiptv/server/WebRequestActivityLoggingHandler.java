package com.uiptv.server;

import com.uiptv.util.WebActivityLog;
import io.undertow.server.HttpHandler;
import io.undertow.server.HttpServerExchange;

import java.net.InetSocketAddress;
import java.util.concurrent.TimeUnit;

final class WebRequestActivityLoggingHandler implements HttpHandler {
    private final HttpHandler next;

    WebRequestActivityLoggingHandler(HttpHandler next) {
        this.next = next;
    }

    @Override
    public void handleRequest(HttpServerExchange exchange) throws Exception {
        if (exchange.isInIoThread()) {
            exchange.dispatch(this);
            return;
        }

        long startedAt = System.nanoTime();
        Exception failure = null;
        try {
            next.handleRequest(exchange);
        } catch (Exception exception) {
            failure = exception;
            throw exception;
        } finally {
            int statusCode = failure == null ? exchange.getStatusCode() : Math.max(500, exchange.getStatusCode());
            WebActivityLog.recordRequest(
                    exchange.getRequestMethod().toString(),
                    exchange.getRequestPath(),
                    exchange.getQueryString(),
                    requestIp(exchange.getSourceAddress()),
                    statusCode,
                    TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startedAt)
            );
        }
    }

    private static String requestIp(InetSocketAddress address) {
        if (address == null || address.getAddress() == null) {
            return "";
        }
        return address.getAddress().getHostAddress();
    }
}
