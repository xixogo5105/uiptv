package com.uiptv.service.remotesync;

import com.uiptv.util.HttpUtil;
import org.apache.hc.core5.http.ContentType;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.Map;

public class RemoteSyncHttpClient {
    private static final int DEFAULT_CONNECT_TIMEOUT_SECONDS = Integer.getInteger("uiptv.remote.sync.http.connect.timeout.seconds", 10);
    private static final int DEFAULT_CONNECTION_REQUEST_TIMEOUT_SECONDS = Integer.getInteger("uiptv.remote.sync.http.connection.request.timeout.seconds", 10);
    private static final int DEFAULT_RESPONSE_TIMEOUT_SECONDS = Integer.getInteger("uiptv.remote.sync.http.response.timeout.seconds", 120);
    private static final Map<String, String> JSON_HEADERS = Map.of("Content-Type", "application/json");
    private static final HttpUtil.RequestOptions REQUEST_OPTIONS = new HttpUtil.RequestOptions(
            false,
            true,
            DEFAULT_CONNECT_TIMEOUT_SECONDS,
            DEFAULT_CONNECTION_REQUEST_TIMEOUT_SECONDS,
            DEFAULT_RESPONSE_TIMEOUT_SECONDS
    );
    private static final HttpClient KEEPALIVE_HTTP_CLIENT = createKeepAliveHttpClient();

    public void checkHealth(String baseUrl) throws IOException {
        JSONObject json = executeJson(HttpUtil.sendRequest(baseUrl + "/remote-sync/health", null, "GET", null, REQUEST_OPTIONS));
        if (!"ok".equalsIgnoreCase(json.optString("status", ""))) {
            throw new IOException("Remote sync server did not respond with OK status");
        }
    }

    public RemoteSyncSessionState createSession(String baseUrl, RemoteSyncRequest request) throws IOException {
        JSONObject response = executeJson(HttpUtil.sendRequest(
                baseUrl + "/remote-sync/request",
                JSON_HEADERS,
                "POST",
                RemoteSyncJson.toJson(request).toString(),
                REQUEST_OPTIONS
        ));
        return RemoteSyncJson.toSessionState(response);
    }

    public RemoteSyncSessionState getSessionState(String baseUrl, String sessionId) throws IOException {
        JSONObject response = executeJson(HttpUtil.sendRequest(
                sessionUrl(baseUrl, "/remote-sync/status", sessionId),
                null,
                "GET",
                null,
                REQUEST_OPTIONS
        ));
        return RemoteSyncJson.toSessionState(response);
    }

    /** Opens a persistent guest connection; the host sends ping frames and renews its lease on pong. */
    public AutoCloseable openKeepAlive(String baseUrl, String sessionId) throws IOException {
        String websocketBase = baseUrl.startsWith("https://")
                ? "wss://" + baseUrl.substring("https://".length())
                : "ws://" + baseUrl.substring("http://".length());
        URI uri = URI.create(sessionUrl(websocketBase, "/remote-sync/keepalive", sessionId));
        try {
            WebSocket socket = KEEPALIVE_HTTP_CLIENT.newWebSocketBuilder()
                    .buildAsync(uri, new WebSocket.Listener() {
                        @Override
                        public void onOpen(WebSocket webSocket) {
                            webSocket.request(1);
                        }

                        @Override
                        public CompletableFuture<?> onPing(WebSocket webSocket, java.nio.ByteBuffer message) {
                            webSocket.request(1);
                            return CompletableFuture.completedFuture(null);
                        }
                    }).get(10, TimeUnit.SECONDS);
            return new KeepAlive(socket);
        } catch (Exception ex) {
            Throwable cause = ex.getCause() == null ? ex : ex.getCause();
            throw new IOException("Could not open remote sync keep-alive with the host", cause);
        }
    }

    public static final class KeepAlive implements AutoCloseable {
        private final WebSocket socket;

        private KeepAlive(WebSocket socket) {
            this.socket = socket;
        }

        @Override
        public void close() {
            if (!socket.isOutputClosed()) socket.sendClose(WebSocket.NORMAL_CLOSURE, "Sync finished");
        }
    }

    public RemoteSyncExecutionResult uploadSnapshot(String baseUrl, String sessionId, Path snapshotPath) throws IOException {
        return RemoteSyncJson.toExecutionResult(executeJson(HttpUtil.sendFileRequest(
                sessionUrl(baseUrl, "/remote-sync/upload", sessionId),
                null,
                "PUT",
                snapshotPath,
                ContentType.APPLICATION_OCTET_STREAM,
                REQUEST_OPTIONS
        )));
    }

    public Path downloadSnapshot(String baseUrl, String sessionId) throws IOException {
        try (HttpUtil.StreamResult response = HttpUtil.openStream(
                sessionUrl(baseUrl, "/remote-sync/download", sessionId),
                null,
                "GET",
                null,
                REQUEST_OPTIONS
        )) {
            if (response.statusCode() >= 300) {
                throw new IOException("Remote sync request failed with status " + response.statusCode());
            }
            Path snapshotPath = SecureTempFileSupport.createTempFile("uiptv-remote-download-", ".db");
            try (InputStream body = response.bodyStream()) {
                RemoteSyncSessionService.copyBounded(body, snapshotPath, RemoteSyncSessionService.MAX_TRANSFER_BYTES);
            } catch (IOException ex) {
                Files.deleteIfExists(snapshotPath);
                throw ex;
            }
            return snapshotPath;
        }
    }

    public void completeSession(String baseUrl, String sessionId, boolean success, String message) throws IOException {
        JSONObject json = new JSONObject()
                .put("sessionId", sessionId)
                .put("success", success)
                .put("message", message == null ? "" : message);
        executeJson(HttpUtil.sendRequest(
                baseUrl + "/remote-sync/complete",
                JSON_HEADERS,
                "POST",
                json.toString(),
                REQUEST_OPTIONS
        ));
    }

    private JSONObject executeJson(HttpUtil.HttpResult response) throws IOException {
        String body = response.body();
        if (response.statusCode() >= 300) {
            throw new IOException(body == null || body.isBlank()
                    ? "Remote sync request failed with status " + response.statusCode()
                    : body);
        }
        return body == null || body.isBlank() ? new JSONObject() : new JSONObject(body);
    }

    private static String sessionUrl(String baseUrl, String path, String sessionId) {
        String encodedSessionId = URLEncoder.encode(sessionId == null ? "" : sessionId, StandardCharsets.UTF_8);
        return baseUrl + path + "?sessionId=" + encodedSessionId;
    }

    private static HttpClient createKeepAliveHttpClient() {
        try {
            X509TrustManager trustAll = new X509TrustManager() {
                @Override public void checkClientTrusted(X509Certificate[] chain, String authType) { }
                @Override public void checkServerTrusted(X509Certificate[] chain, String authType) { }
                @Override public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            };
            SSLContext sslContext = SSLContext.getInstance("TLS");
            sslContext.init(null, new TrustManager[]{trustAll}, new SecureRandom());
            SSLParameters sslParameters = new SSLParameters();
            sslParameters.setEndpointIdentificationAlgorithm("");
            return HttpClient.newBuilder().sslContext(sslContext).sslParameters(sslParameters).build();
        } catch (java.security.GeneralSecurityException ex) {
            throw new IllegalStateException("Could not initialize the remote sync keep-alive", ex);
        }
    }
}
