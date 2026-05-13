package com.uiptv.service.remotesync;

import com.uiptv.util.HttpUtil;
import org.apache.hc.core5.http.ContentType;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
                baseUrl + "/remote-sync/status?sessionId=" + sessionId,
                null,
                "GET",
                null,
                REQUEST_OPTIONS
        ));
        return RemoteSyncJson.toSessionState(response);
    }

    public RemoteSyncExecutionResult uploadSnapshot(String baseUrl, String sessionId, Path snapshotPath) throws IOException {
        return RemoteSyncJson.toExecutionResult(executeJson(HttpUtil.sendFileRequest(
                baseUrl + "/remote-sync/upload?sessionId=" + sessionId,
                null,
                "PUT",
                snapshotPath,
                ContentType.APPLICATION_OCTET_STREAM,
                REQUEST_OPTIONS
        )));
    }

    public Path downloadSnapshot(String baseUrl, String sessionId) throws IOException {
        try (HttpUtil.StreamResult response = HttpUtil.openStream(
                baseUrl + "/remote-sync/download?sessionId=" + sessionId,
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
                Files.copy(body, snapshotPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
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
}
