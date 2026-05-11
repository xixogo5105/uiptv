package com.uiptv.service.remotesync;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.Executors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSyncHttpClientTest {
    @TempDir
    Path tempDir;

    @Test
    void checkHealth_acceptsOkStatus() throws Exception {
        withServer(server -> {
            server.createContext("/remote-sync/health", exchange ->
                    respondJson(exchange, 200, "{\"status\":\"ok\"}"));

            new RemoteSyncHttpClient().checkHealth(baseUrl(server));
        });
    }

    @Test
    void checkHealth_rejectsUnexpectedStatusBody() throws Exception {
        withServer(server -> {
            server.createContext("/remote-sync/health", exchange ->
                    respondJson(exchange, 200, "{\"status\":\"down\"}"));

            IOException exception = assertThrows(IOException.class,
                    () -> new RemoteSyncHttpClient().checkHealth(baseUrl(server)));
            assertTrue(exception.getMessage().contains("OK status"));
        });
    }

    @Test
    void createSession_andGetSessionState_roundTripRemoteJson() throws Exception {
        withServer(server -> {
            server.createContext("/remote-sync/request", exchange -> {
                String body = readBody(exchange);
                RemoteSyncRequest request = RemoteSyncJson.toRequest(body);
                assertEquals(RemoteSyncDirection.EXPORT_TO_REMOTE, request.direction());
                assertEquals("1234", request.verificationCode());
                assertTrue(request.options().syncConfiguration());

                respondJson(exchange, 200, RemoteSyncJson.toJson(new RemoteSyncSessionState(
                        "session-1",
                        request.direction(),
                        RemoteSyncStatus.PENDING_APPROVAL,
                        request.verificationCode(),
                        request.requesterName(),
                        "10.0.0.8",
                        request.options(),
                        "Awaiting approval."
                )));
            });
            server.createContext("/remote-sync/status", exchange ->
                    respondJson(exchange, 200, RemoteSyncJson.toJson(new RemoteSyncSessionState(
                            "session-1",
                            RemoteSyncDirection.EXPORT_TO_REMOTE,
                            RemoteSyncStatus.APPROVED,
                            "1234",
                            "desktop",
                            "10.0.0.8",
                            new RemoteSyncOptions(true, true),
                            "Approved."
                    ))));

            RemoteSyncHttpClient client = new RemoteSyncHttpClient();
            RemoteSyncSessionState created = client.createSession(baseUrl(server), new RemoteSyncRequest(
                    RemoteSyncDirection.EXPORT_TO_REMOTE,
                    "1234",
                    "desktop",
                    new RemoteSyncOptions(true, true)
            ));
            RemoteSyncSessionState status = client.getSessionState(baseUrl(server), "session-1");

            assertEquals(RemoteSyncStatus.PENDING_APPROVAL, created.status());
            assertEquals(RemoteSyncStatus.APPROVED, status.status());
            assertEquals("Approved.", status.message());
        });
    }

    @Test
    void uploadSnapshot_postsBinaryAndParsesExecutionResult() throws Exception {
        Path snapshot = Files.writeString(tempDir.resolve("upload.db"), "db-bytes");

        withServer(server -> {
            server.createContext("/remote-sync/upload", exchange -> {
                byte[] body = readBodyBytes(exchange);
                assertEquals("db-bytes", new String(body, StandardCharsets.UTF_8));
                respondJson(exchange, 200, RemoteSyncJson.toJson(new RemoteSyncExecutionResult(
                        null,
                        "uploaded"
                )));
            });

            RemoteSyncExecutionResult result = new RemoteSyncHttpClient()
                    .uploadSnapshot(baseUrl(server), "session-5", snapshot);

            assertEquals("uploaded", result.message());
        });
    }

    @Test
    void downloadSnapshot_writesResponseBodyToTempFile() throws Exception {
        withServer(server -> {
            server.createContext("/remote-sync/download", exchange -> {
                byte[] body = "snapshot-content".getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/octet-stream");
                exchange.sendResponseHeaders(200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });

            Path snapshot = new RemoteSyncHttpClient().downloadSnapshot(baseUrl(server), "session-9");

            assertEquals("snapshot-content", Files.readString(snapshot));
        });
    }

    @Test
    void completeSession_postsCompletionPayload() throws Exception {
        withServer(server -> {
            server.createContext("/remote-sync/complete", exchange -> {
                String body = readBody(exchange);
                assertTrue(body.contains("\"sessionId\":\"session-8\""));
                assertTrue(body.contains("\"success\":true"));
                assertTrue(body.contains("\"message\":\"done\""));
                respondJson(exchange, 200, "{\"status\":\"ok\"}");
            });

            new RemoteSyncHttpClient().completeSession(baseUrl(server), "session-8", true, "done");
        });
    }

    @Test
    void non2xxResponse_usesRemoteBodyAsExceptionMessage() throws Exception {
        withServer(server -> {
            server.createContext("/remote-sync/request", exchange ->
                    respondJson(exchange, 409, "{\"error\":\"already exists\"}"));

            IOException exception = assertThrows(IOException.class, () -> new RemoteSyncHttpClient().createSession(
                    baseUrl(server),
                    new RemoteSyncRequest(
                            RemoteSyncDirection.EXPORT_TO_REMOTE,
                            "1234",
                            "desktop",
                            new RemoteSyncOptions(false, false)
                    )
            ));
            assertTrue(exception.getMessage().contains("already exists"));
        });
    }

    @Test
    void downloadSnapshot_non2xx_usesRemoteBodyAsExceptionMessage() throws Exception {
        withServer(server -> {
            server.createContext("/remote-sync/download", exchange ->
                    respondJson(exchange, 404, "{\"message\":\"not found\"}"));

            IOException exception = assertThrows(IOException.class,
                    () -> new RemoteSyncHttpClient().downloadSnapshot(baseUrl(server), "missing"));
            assertTrue(exception.getMessage().contains("not found"));
        });
    }

    private interface ThrowingServerConsumer {
        void accept(HttpServer server) throws Exception;
    }

    private void withServer(ThrowingServerConsumer consumer) throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.setExecutor(Executors.newCachedThreadPool());
        try {
            server.start();
            consumer.accept(server);
        } finally {
            server.stop(0);
        }
    }

    private String baseUrl(HttpServer server) {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    private static String readBody(HttpExchange exchange) throws IOException {
        return new String(readBodyBytes(exchange), StandardCharsets.UTF_8);
    }

    private static byte[] readBodyBytes(HttpExchange exchange) throws IOException {
        try (InputStream inputStream = exchange.getRequestBody()) {
            return inputStream.readAllBytes();
        }
    }

    private static void respondJson(HttpExchange exchange, int statusCode, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "application/json");
        exchange.sendResponseHeaders(statusCode, bytes.length);
        exchange.getResponseBody().write(bytes);
        exchange.close();
    }
}
