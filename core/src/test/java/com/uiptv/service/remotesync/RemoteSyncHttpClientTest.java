package com.uiptv.service.remotesync;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import com.uiptv.service.DatabaseSyncService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RemoteSyncHttpClientTest {
    @TempDir
    Path tempDir;

    private HttpServer server;

    @AfterEach
    void tearDown() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }

    @Test
    void remoteSyncHttpClient_handlesJsonUploadAndDownloadRequests() throws Exception {
        AtomicBoolean completionCalled = new AtomicBoolean(false);
        AtomicInteger uploadedBytes = new AtomicInteger();
        Path downloadSource = tempDir.resolve("download.db");
        Files.writeString(downloadSource, "snapshot-content", StandardCharsets.UTF_8);

        startServer(exchange -> {
            String path = exchange.getRequestURI().getPath();
            if ("/remote-sync/health".equals(path)) {
                writeJson(exchange, 200, """
                        {"status":"ok"}
                        """);
                return;
            }
            if ("/remote-sync/request".equals(path)) {
                writeJson(exchange, 200, RemoteSyncJson.toJson(new RemoteSyncSessionState(
                        "session-1",
                        RemoteSyncDirection.EXPORT_TO_REMOTE,
                        RemoteSyncStatus.PENDING_APPROVAL,
                        "1234",
                        "machine-a",
                        "127.0.0.1",
                        new RemoteSyncOptions(true, false),
                        "pending"
                )).toString());
                return;
            }
            if ("/remote-sync/status".equals(path)) {
                writeJson(exchange, 200, RemoteSyncJson.toJson(new RemoteSyncSessionState(
                        "session-1",
                        RemoteSyncDirection.EXPORT_TO_REMOTE,
                        RemoteSyncStatus.APPROVED,
                        "1234",
                        "machine-a",
                        "127.0.0.1",
                        new RemoteSyncOptions(true, false),
                        "approved"
                )).toString());
                return;
            }
            if ("/remote-sync/upload".equals(path)) {
                uploadedBytes.set(exchange.getRequestBody().readAllBytes().length);
                writeJson(exchange, 200, RemoteSyncJson.toJson(new RemoteSyncExecutionResult(
                        new DatabaseSyncService.DatabaseSyncReport(
                                java.util.List.of(new DatabaseSyncService.TableSyncResult("account", 1)),
                                true,
                                false,
                                false
                        ),
                        "uploaded"
                )).toString());
                return;
            }
            if ("/remote-sync/download".equals(path)) {
                writeBytes(exchange, 200, Files.readAllBytes(downloadSource), "application/octet-stream");
                return;
            }
            if ("/remote-sync/complete".equals(path)) {
                completionCalled.set(true);
                writeJson(exchange, 200, "{}");
                return;
            }
            writeJson(exchange, 404, "{\"error\":\"missing\"}");
        });

        String baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
        RemoteSyncHttpClient client = new RemoteSyncHttpClient();

        client.checkHealth(baseUrl);
        RemoteSyncSessionState created = client.createSession(baseUrl, new RemoteSyncRequest(
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                "1234",
                "machine-a",
                new RemoteSyncOptions(true, false)
        ));
        assertEquals("session-1", created.sessionId());

        RemoteSyncSessionState status = client.getSessionState(baseUrl, "session-1");
        assertEquals(RemoteSyncStatus.APPROVED, status.status());

        Path uploadPath = tempDir.resolve("upload.db");
        Files.writeString(uploadPath, "upload-me", StandardCharsets.UTF_8);
        RemoteSyncExecutionResult uploadResult = client.uploadSnapshot(baseUrl, "session-1", uploadPath);
        assertEquals("uploaded", uploadResult.message());
        assertTrue(uploadedBytes.get() > 0);

        Path downloaded = client.downloadSnapshot(baseUrl, "session-1");
        assertEquals("snapshot-content", Files.readString(downloaded, StandardCharsets.UTF_8));

        client.completeSession(baseUrl, "session-1", true, "done");
        assertTrue(completionCalled.get());
    }

    private void startServer(Handler handler) throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", handler::handle);
        server.start();
    }

    private static void writeJson(HttpExchange exchange, int status, String body) throws IOException {
        writeBytes(exchange, status, body.getBytes(StandardCharsets.UTF_8), "application/json; charset=utf-8");
    }

    private static void writeBytes(HttpExchange exchange, int status, byte[] bytes, String contentType) throws IOException {
        exchange.getResponseHeaders().set("Content-Type", contentType);
        exchange.sendResponseHeaders(status, bytes.length);
        try (OutputStream os = exchange.getResponseBody()) {
            os.write(bytes);
        } finally {
            exchange.close();
        }
    }

    @FunctionalInterface
    private interface Handler {
        void handle(HttpExchange exchange) throws IOException;
    }
}
