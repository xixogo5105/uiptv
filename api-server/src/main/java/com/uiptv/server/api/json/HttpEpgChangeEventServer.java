package com.uiptv.server.api.json;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.uiptv.application.EpgChangeNotifications;
import org.json.JSONObject;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.TimeUnit;

/** Authenticated-by-local-server SSE wake-up stream; clients fetch scoped data separately. */
public final class HttpEpgChangeEventServer implements HttpHandler {
    private static final int QUEUE_SIZE = 64;
    private static final long HEARTBEAT_SECONDS = 15;

    @Override public void handle(HttpExchange exchange) throws IOException {
        if (!"GET".equalsIgnoreCase(exchange.getRequestMethod())) {
            exchange.getResponseHeaders().set("Allow", "GET");
            exchange.sendResponseHeaders(405, -1);
            return;
        }
        long after = parseRevision(exchange.getRequestHeaders().getFirst("Last-Event-ID"));
        if (after < 0) after = parseRevision(query(exchange.getRequestURI().getRawQuery(), "since"));
        if (after < 0) after = EpgChangeNotifications.snapshot().revision();

        var queue = new ArrayBlockingQueue<EpgChangeNotifications.Notification>(QUEUE_SIZE);
        var subscription = EpgChangeNotifications.subscribe(after, event -> queue.offer(event));
        exchange.getResponseHeaders().set("Content-Type", "text/event-stream; charset=UTF-8");
        exchange.getResponseHeaders().set("Cache-Control", "no-cache, no-transform");
        exchange.getResponseHeaders().set("Connection", "keep-alive");
        exchange.getResponseHeaders().set("Access-Control-Allow-Origin", "*");
        exchange.sendResponseHeaders(200, 0);
        try (OutputStream output = exchange.getResponseBody()) {
            while (!Thread.currentThread().isInterrupted()) {
                EpgChangeNotifications.Notification event = queue.poll(HEARTBEAT_SECONDS, TimeUnit.SECONDS);
                if (event == null) {
                    output.write(": heartbeat\n\n".getBytes(StandardCharsets.UTF_8));
                } else {
                    JSONObject payload = new JSONObject()
                            .put("revision", event.revision())
                            .put("generation", event.databaseGeneration())
                            .put("kind", event.kind().name());
                    output.write(("id: " + event.revision() + "\ndata: " + payload + "\n\n").getBytes(StandardCharsets.UTF_8));
                }
                output.flush();
            }
        } catch (IOException ignored) {
            // Client disconnects are normal for foreground-only subscriptions.
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
        } finally {
            subscription.close();
        }
    }

    private static long parseRevision(String value) {
        try { return value == null || value.isBlank() ? -1 : Long.parseLong(value); }
        catch (NumberFormatException ignored) { return -1; }
    }

    private static String query(String raw, String wanted) {
        if (raw == null) return null;
        for (String part : raw.split("&")) {
            String[] bits = part.split("=", 2);
            if (bits.length == 2 && wanted.equals(java.net.URLDecoder.decode(bits[0], StandardCharsets.UTF_8)))
                return java.net.URLDecoder.decode(bits[1], StandardCharsets.UTF_8);
        }
        return null;
    }
}
