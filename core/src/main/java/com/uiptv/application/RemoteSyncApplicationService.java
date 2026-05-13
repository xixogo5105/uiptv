package com.uiptv.application;

import com.uiptv.service.remotesync.RemoteSyncExecutionResult;
import com.uiptv.service.remotesync.RemoteSyncRequest;
import com.uiptv.service.remotesync.RemoteSyncSessionService;
import com.uiptv.service.remotesync.RemoteSyncSessionState;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.SQLException;

public class RemoteSyncApplicationService {
    private final RemoteSyncSessionService remoteSyncSessionService = RemoteSyncSessionService.getInstance();

    private RemoteSyncApplicationService() {
    }

    public static RemoteSyncApplicationService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public RemoteSyncSessionState createSession(RemoteSyncRequest request, String requesterAddress) {
        return remoteSyncSessionService.createSession(request, requesterAddress);
    }

    public RemoteSyncSessionState getSessionState(String sessionId) {
        return remoteSyncSessionService.getSessionState(sessionId);
    }

    public RemoteSyncExecutionResult acceptUpload(String sessionId, InputStream requestBody) throws IOException, SQLException {
        return remoteSyncSessionService.acceptUpload(sessionId, requestBody);
    }

    public Path getDownloadSnapshot(String sessionId) {
        return remoteSyncSessionService.getDownloadSnapshot(sessionId);
    }

    public void completeImport(String sessionId, boolean success, String message) {
        remoteSyncSessionService.completeImport(sessionId, success, message);
    }

    private static class SingletonHelper {
        private static final RemoteSyncApplicationService INSTANCE = new RemoteSyncApplicationService();
    }
}
