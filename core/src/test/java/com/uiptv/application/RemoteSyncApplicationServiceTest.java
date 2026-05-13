package com.uiptv.application;

import com.uiptv.service.remotesync.RemoteSyncDirection;
import com.uiptv.service.remotesync.RemoteSyncExecutionResult;
import com.uiptv.service.remotesync.RemoteSyncOptions;
import com.uiptv.service.remotesync.RemoteSyncRequest;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import com.uiptv.service.remotesync.RemoteSyncSessionState;
import com.uiptv.service.remotesync.RemoteSyncStatus;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class RemoteSyncApplicationServiceTest {

    @AfterEach
    void clearSessions() throws Exception {
        Method method = RemoteSyncSessionService.class.getDeclaredMethod("clearSessions");
        method.setAccessible(true);
        method.invoke(RemoteSyncSessionService.getInstance());
    }

    @Test
    void singletonFacadeDelegatesToSessionService() {
        RemoteSyncApplicationService service = RemoteSyncApplicationService.getInstance();
        RemoteSyncRequest request = new RemoteSyncRequest(
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                "1234",
                "Laptop",
                new RemoteSyncOptions(true, false)
        );

        RemoteSyncSessionState created = service.createSession(request, "127.0.0.1");
        RemoteSyncSessionState fetched = service.getSessionState(created.sessionId());

        assertEquals(created.sessionId(), fetched.sessionId());
        assertEquals(RemoteSyncStatus.REJECTED, fetched.status());
        assertThrows(IllegalStateException.class,
                () -> service.acceptUpload(created.sessionId(), new ByteArrayInputStream(new byte[]{1})));
        assertThrows(IllegalStateException.class, () -> service.getDownloadSnapshot(created.sessionId()));

        service.completeImport(created.sessionId(), false, "failed");

        assertEquals(RemoteSyncStatus.FAILED, service.getSessionState(created.sessionId()).status());
    }

    @Test
    void singletonFacadeDelegatesSuccessfulUploadAndDownloadCalls() throws Exception {
        RemoteSyncApplicationService service = RemoteSyncApplicationService.getInstance();
        RemoteSyncSessionService original = replaceSessionService(service, Mockito.mock(RemoteSyncSessionService.class));
        RemoteSyncSessionService delegate = getSessionService(service);
        RemoteSyncExecutionResult result = new RemoteSyncExecutionResult(null, "uploaded");
        Path snapshot = Path.of("snapshot.db");
        Mockito.when(delegate.acceptUpload(Mockito.eq("session"), Mockito.any())).thenReturn(result);
        Mockito.when(delegate.getDownloadSnapshot("session")).thenReturn(snapshot);

        try {
            assertEquals(result, service.acceptUpload("session", new ByteArrayInputStream(new byte[]{1})));
            assertEquals(snapshot, service.getDownloadSnapshot("session"));
        } finally {
            replaceSessionService(service, original);
        }
    }

    private RemoteSyncSessionService replaceSessionService(RemoteSyncApplicationService service,
                                                           RemoteSyncSessionService replacement) throws Exception {
        Field field = RemoteSyncApplicationService.class.getDeclaredField("remoteSyncSessionService");
        field.setAccessible(true);
        RemoteSyncSessionService original = (RemoteSyncSessionService) field.get(service);
        field.set(service, replacement);
        return original;
    }

    private RemoteSyncSessionService getSessionService(RemoteSyncApplicationService service) throws Exception {
        Field field = RemoteSyncApplicationService.class.getDeclaredField("remoteSyncSessionService");
        field.setAccessible(true);
        return (RemoteSyncSessionService) field.get(service);
    }
}
