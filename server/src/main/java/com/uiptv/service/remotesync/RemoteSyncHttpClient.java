package com.uiptv.service.remotesync;

import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.client5.http.classic.methods.HttpUriRequestBase;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.ByteArrayEntity;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.FileEntity;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

public class RemoteSyncHttpClient {
    public void checkHealth(String baseUrl) throws IOException {
        JSONObject json = executeJson(new HttpGet(baseUrl + "/remote-sync/health"));
        if (!"ok".equalsIgnoreCase(json.optString("status", ""))) {
            throw new IOException("Remote sync server did not respond with OK status");
        }
    }

    public RemoteSyncSessionState createSession(String baseUrl, RemoteSyncRequest request) throws IOException {
        HttpPost post = new HttpPost(baseUrl + "/remote-sync/request");
        post.setHeader("Content-Type", "application/json");
        post.setEntity(new ByteArrayEntity(RemoteSyncJson.toJson(request).toString().getBytes(), ContentType.APPLICATION_JSON));
        return RemoteSyncJson.toSessionState(executeJson(post));
    }

    public RemoteSyncSessionState getSessionState(String baseUrl, String sessionId) throws IOException {
        return RemoteSyncJson.toSessionState(executeJson(new HttpGet(baseUrl + "/remote-sync/status?sessionId=" + sessionId)));
    }

    public RemoteSyncExecutionResult uploadSnapshot(String baseUrl, String sessionId, Path snapshotPath) throws IOException {
        HttpPut put = new HttpPut(baseUrl + "/remote-sync/upload?sessionId=" + sessionId);
        put.setEntity(new FileEntity(snapshotPath.toFile(), ContentType.APPLICATION_OCTET_STREAM));
        return RemoteSyncJson.toExecutionResult(executeJson(put));
    }

    public Path downloadSnapshot(String baseUrl, String sessionId) throws IOException {
        HttpGet get = new HttpGet(baseUrl + "/remote-sync/download?sessionId=" + sessionId);
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            return client.execute(get, response -> {
                if (response.getCode() >= 300) {
                    throw new IOException(readEntityText(response));
                }
                Path snapshotPath = SecureTempFileSupport.createTempFile("uiptv-remote-download-", ".db");
                try (InputStream body = response.getEntity().getContent()) {
                    Files.copy(body, snapshotPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                }
                return snapshotPath;
            });
        }
    }

    public void completeSession(String baseUrl, String sessionId, boolean success, String message) throws IOException {
        HttpPost post = new HttpPost(baseUrl + "/remote-sync/complete");
        post.setHeader("Content-Type", "application/json");
        JSONObject json = new JSONObject()
                .put("sessionId", sessionId)
                .put("success", success)
                .put("message", message == null ? "" : message);
        post.setEntity(new ByteArrayEntity(json.toString().getBytes(), ContentType.APPLICATION_JSON));
        executeJson(post);
    }

    private JSONObject executeJson(HttpUriRequestBase request) throws IOException {
        try (CloseableHttpClient client = HttpClients.createDefault()) {
            return client.execute(request, response -> {
                String body = readEntityText(response);
                if (response.getCode() >= 300) {
                    throw new IOException(body.isBlank() ? "Remote sync request failed with status " + response.getCode() : body);
                }
                return body.isBlank() ? new JSONObject() : new JSONObject(body);
            });
        }
    }

    private String readEntityText(ClassicHttpResponse response) throws IOException {
        if (response.getEntity() == null) {
            return "";
        }
        try {
            return EntityUtils.toString(response.getEntity());
        } catch (ParseException ex) {
            throw new IOException("Unable to parse remote sync response", ex);
        }
    }
}
