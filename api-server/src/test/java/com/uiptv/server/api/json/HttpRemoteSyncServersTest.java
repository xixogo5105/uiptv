package com.uiptv.server.api.json;

import com.uiptv.application.RemoteSyncApplicationService;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.service.remotesync.RemoteSyncDirection;
import com.uiptv.service.remotesync.RemoteSyncExecutionResult;
import com.uiptv.service.remotesync.RemoteSyncOptions;
import com.uiptv.service.remotesync.RemoteSyncSessionState;
import com.uiptv.service.remotesync.RemoteSyncStatus;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpRemoteSyncServersTest {

    @Test
    void requestServer_acceptsPostAndRejectsOtherMethods() throws Exception {
        RemoteSyncApplicationService app = Mockito.mock(RemoteSyncApplicationService.class);
        RemoteSyncSessionState state = state("session-1", RemoteSyncStatus.PENDING_APPROVAL, "created");
        Mockito.when(app.createSession(Mockito.any(), Mockito.anyString())).thenReturn(state);

        try (MockedStatic<RemoteSyncApplicationService> appStatic = Mockito.mockStatic(RemoteSyncApplicationService.class)) {
            appStatic.when(RemoteSyncApplicationService::getInstance).thenReturn(app);

            TestHttpExchange post = new TestHttpExchange("/remote-sync/request", "POST", new JSONObject()
                    .put("direction", "IMPORT_FROM_REMOTE")
                    .put("verificationCode", "123456")
                    .put("requesterName", "Laptop")
                    .put("syncConfiguration", true)
                    .toString());
            new HttpRemoteSyncRequestServer().handle(post);

            assertEquals(200, post.getResponseCode());
            JSONObject response = new JSONObject(post.getResponseBodyText());
            assertEquals("session-1", response.getString("sessionId"));
            assertEquals("PENDING_APPROVAL", response.getString("status"));

            TestHttpExchange get = new TestHttpExchange("/remote-sync/request", "GET");
            new HttpRemoteSyncRequestServer().handle(get);
            assertEquals(405, get.getResponseCode());
            assertEquals("POST", get.getResponseHeaders().getFirst("Allow"));
        }
    }

    @Test
    void completeServer_handlesSuccessAndBadRequests() throws Exception {
        RemoteSyncApplicationService app = Mockito.mock(RemoteSyncApplicationService.class);
        Mockito.doThrow(new IllegalStateException("bad session"))
                .when(app).completeImport(Mockito.eq("bad"), Mockito.anyBoolean(), Mockito.anyString());

        try (MockedStatic<RemoteSyncApplicationService> appStatic = Mockito.mockStatic(RemoteSyncApplicationService.class)) {
            appStatic.when(RemoteSyncApplicationService::getInstance).thenReturn(app);

            TestHttpExchange ok = new TestHttpExchange("/remote-sync/complete", "POST",
                    new JSONObject().put("sessionId", "ok").put("success", true).put("message", "done").toString());
            new HttpRemoteSyncCompleteServer().handle(ok);
            assertEquals(200, ok.getResponseCode());
            assertEquals("ok", new JSONObject(ok.getResponseBodyText()).getString("status"));

            TestHttpExchange bad = new TestHttpExchange("/remote-sync/complete", "POST",
                    new JSONObject().put("sessionId", "bad").toString());
            new HttpRemoteSyncCompleteServer().handle(bad);
            assertEquals(400, bad.getResponseCode());
            assertTrue(new JSONObject(bad.getResponseBodyText()).getString("message").contains("bad session"));

            TestHttpExchange get = new TestHttpExchange("/remote-sync/complete", "GET");
            new HttpRemoteSyncCompleteServer().handle(get);
            assertEquals(405, get.getResponseCode());
        }
    }

    @Test
    void uploadServer_writesExecutionResultAndMapsErrors() throws Exception {
        RemoteSyncApplicationService app = Mockito.mock(RemoteSyncApplicationService.class);
        Mockito.when(app.acceptUpload(Mockito.eq("ok"), Mockito.any()))
                .thenReturn(new RemoteSyncExecutionResult(null, "imported"));
        Mockito.when(app.acceptUpload(Mockito.eq("bad"), Mockito.any()))
                .thenThrow(new IllegalArgumentException("bad upload"));
        Mockito.when(app.acceptUpload(Mockito.eq("sql"), Mockito.any()))
                .thenThrow(new SQLException("database failed"));

        try (MockedStatic<RemoteSyncApplicationService> appStatic = Mockito.mockStatic(RemoteSyncApplicationService.class)) {
            appStatic.when(RemoteSyncApplicationService::getInstance).thenReturn(app);

            TestHttpExchange ok = new TestHttpExchange("/remote-sync/upload?sessionId=ok", "PUT", "payload");
            new HttpRemoteSyncUploadServer().handle(ok);
            assertEquals(200, ok.getResponseCode());
            assertEquals("imported", new JSONObject(ok.getResponseBodyText()).getString("message"));

            TestHttpExchange bad = new TestHttpExchange("/remote-sync/upload?sessionId=bad", "PUT", "payload");
            new HttpRemoteSyncUploadServer().handle(bad);
            assertEquals(400, bad.getResponseCode());

            TestHttpExchange sql = new TestHttpExchange("/remote-sync/upload?sessionId=sql", "PUT", "payload");
            new HttpRemoteSyncUploadServer().handle(sql);
            assertEquals(500, sql.getResponseCode());

            TestHttpExchange post = new TestHttpExchange("/remote-sync/upload", "POST");
            new HttpRemoteSyncUploadServer().handle(post);
            assertEquals(405, post.getResponseCode());
            assertEquals("PUT", post.getResponseHeaders().getFirst("Allow"));
        }
    }

    @Test
    void statusDownloadAndHealthServers_handleMethodsSuccessAndBadRequests() throws Exception {
        RemoteSyncApplicationService app = Mockito.mock(RemoteSyncApplicationService.class);
        RemoteSyncSessionState ready = state("ready", RemoteSyncStatus.READY_FOR_DOWNLOAD, "ready");
        Path snapshot = Files.createTempFile("remote-sync-download-", ".db");
        Files.write(snapshot, new byte[]{1, 2, 3});

        Mockito.when(app.getSessionState("ready")).thenReturn(ready);
        Mockito.when(app.getSessionState("bad")).thenThrow(new IllegalArgumentException("missing status"));
        Mockito.when(app.getDownloadSnapshot("ready")).thenReturn(snapshot);
        Mockito.when(app.getDownloadSnapshot("bad")).thenThrow(new IllegalStateException("missing download"));

        try (MockedStatic<RemoteSyncApplicationService> appStatic = Mockito.mockStatic(RemoteSyncApplicationService.class)) {
            appStatic.when(RemoteSyncApplicationService::getInstance).thenReturn(app);

            TestHttpExchange statusOk = new TestHttpExchange("/remote-sync/status?sessionId=ready", "GET");
            new HttpRemoteSyncStatusServer().handle(statusOk);
            assertEquals(200, statusOk.getResponseCode());
            assertEquals("ready", new JSONObject(statusOk.getResponseBodyText()).getString("sessionId"));

            TestHttpExchange statusBad = new TestHttpExchange("/remote-sync/status?sessionId=bad", "GET");
            new HttpRemoteSyncStatusServer().handle(statusBad);
            assertEquals(400, statusBad.getResponseCode());
            assertTrue(new JSONObject(statusBad.getResponseBodyText()).getString("message").contains("missing status"));

            TestHttpExchange statusPost = new TestHttpExchange("/remote-sync/status", "POST");
            new HttpRemoteSyncStatusServer().handle(statusPost);
            assertEquals(405, statusPost.getResponseCode());
            assertEquals("GET", statusPost.getResponseHeaders().getFirst("Allow"));

            TestHttpExchange downloadOk = new TestHttpExchange("/remote-sync/download?sessionId=ready", "GET");
            new HttpRemoteSyncDownloadServer().handle(downloadOk);
            assertEquals(200, downloadOk.getResponseCode());
            assertArrayEquals(new byte[]{1, 2, 3}, downloadOk.getResponseBodyBytes());
            assertEquals("application/octet-stream", downloadOk.getResponseHeaders().getFirst("Content-Type"));

            TestHttpExchange downloadBad = new TestHttpExchange("/remote-sync/download?sessionId=bad", "GET");
            new HttpRemoteSyncDownloadServer().handle(downloadBad);
            assertEquals(400, downloadBad.getResponseCode());
            assertTrue(new JSONObject(downloadBad.getResponseBodyText()).getString("message").contains("missing download"));

            TestHttpExchange downloadPost = new TestHttpExchange("/remote-sync/download", "POST");
            new HttpRemoteSyncDownloadServer().handle(downloadPost);
            assertEquals(405, downloadPost.getResponseCode());
            assertEquals("GET", downloadPost.getResponseHeaders().getFirst("Allow"));
        } finally {
            Files.deleteIfExists(snapshot);
        }

        TestHttpExchange healthOk = new TestHttpExchange("/remote-sync/health", "GET");
        new HttpRemoteSyncHealthServer().handle(healthOk);
        assertEquals(200, healthOk.getResponseCode());
        assertEquals("ok", new JSONObject(healthOk.getResponseBodyText()).getString("status"));

        TestHttpExchange healthPost = new TestHttpExchange("/remote-sync/health", "POST");
        new HttpRemoteSyncHealthServer().handle(healthPost);
        assertEquals(405, healthPost.getResponseCode());
        assertEquals("GET", healthPost.getResponseHeaders().getFirst("Allow"));
    }

    private RemoteSyncSessionState state(String id, RemoteSyncStatus status, String message) {
        return new RemoteSyncSessionState(
                id,
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                status,
                "123456",
                "Laptop",
                "127.0.0.1",
                new RemoteSyncOptions(true, false),
                message
        );
    }
}
