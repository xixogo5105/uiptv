package com.uiptv.service.remotesync;

import com.uiptv.db.SQLConnection;
import com.uiptv.service.AppDataRefreshService;
import com.uiptv.service.DatabaseSyncService;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class RemoteSyncSessionService {
    private static final Duration APPROVAL_TTL = Duration.ofMinutes(2);
    private static final Duration TRANSFER_TTL = Duration.ofMinutes(10);

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final DatabaseSnapshotService snapshotService;
    private final DatabaseSyncService databaseSyncService;
    private final Clock clock;
    private volatile RemoteSyncApprovalPrompt approvalPrompt;
    private volatile RemoteSyncNotifier notifier;

    private RemoteSyncSessionService() {
        this(new DatabaseSnapshotService(), DatabaseSyncService.getInstance(), Clock.systemDefaultZone(),
                new DefaultRemoteSyncUiBridge(), new DefaultRemoteSyncUiBridge());
    }

    RemoteSyncSessionService(DatabaseSnapshotService snapshotService,
                             DatabaseSyncService databaseSyncService,
                             Clock clock,
                             RemoteSyncApprovalPrompt approvalPrompt,
                             RemoteSyncNotifier notifier) {
        this.snapshotService = Objects.requireNonNull(snapshotService, "snapshotService");
        this.databaseSyncService = Objects.requireNonNull(databaseSyncService, "databaseSyncService");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.approvalPrompt = Objects.requireNonNull(approvalPrompt, "approvalPrompt");
        this.notifier = Objects.requireNonNull(notifier, "notifier");
    }

    private static class SingletonHelper {
        private static final RemoteSyncSessionService INSTANCE = new RemoteSyncSessionService();
    }

    public static RemoteSyncSessionService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public RemoteSyncSessionState createSession(RemoteSyncRequest request, String requesterAddress) {
        validateRequest(request);
        SessionState session = new SessionState(
                UUID.randomUUID().toString(),
                request.direction(),
                request.verificationCode(),
                blankToFallback(request.requesterName(), requesterAddress),
                blankToFallback(requesterAddress, "unknown"),
                request.options(),
                clock.instant().plus(APPROVAL_TTL)
        );
        sessions.put(session.sessionId, session);
        approvalPrompt.requestApproval(session.toApprovalRequest(), approved -> applyDecision(session.sessionId, approved));
        return session.toPublicState();
    }

    public RemoteSyncSessionState getSessionState(String sessionId) {
        SessionState session = requireSession(sessionId);
        synchronized (session) {
            expireIfNeeded(session);
            return session.toPublicState();
        }
    }

    public RemoteSyncExecutionResult acceptUpload(String sessionId, InputStream requestBody) throws IOException, SQLException {
        SessionState session = requireSession(sessionId);
        Path uploadedSnapshot = null;
        synchronized (session) {
            expireIfNeeded(session);
            session.ensureStatus(RemoteSyncDirection.EXPORT_TO_REMOTE, RemoteSyncStatus.APPROVED);
            uploadedSnapshot = Files.createTempFile("uiptv-remote-upload-", ".db");
            try (InputStream inputStream = requestBody) {
                Files.copy(inputStream, uploadedSnapshot, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        try {
            DatabaseSyncService.DatabaseSyncReport report = databaseSyncService.syncDatabasesWithReport(
                    uploadedSnapshot.toAbsolutePath().toString(),
                    SQLConnection.getDatabasePath(),
                    session.options.syncConfiguration(),
                    session.options.syncExternalPlayerPaths(),
                    null
            );
            AppDataRefreshService.getInstance().refreshAfterDatabaseChange();
            synchronized (session) {
                session.complete("Remote database sync completed.");
            }
            notifier.showInfo("remoteSyncRemoteCompletedMessage");
            return new RemoteSyncExecutionResult(report, "Remote database sync completed.");
        } catch (SQLException ex) {
            synchronized (session) {
                session.fail(ex.getMessage());
            }
            notifier.showError("remoteSyncRemoteFailedMessage");
            throw ex;
        } finally {
            deleteIfExists(uploadedSnapshot);
        }
    }

    public Path getDownloadSnapshot(String sessionId) {
        SessionState session = requireSession(sessionId);
        synchronized (session) {
            expireIfNeeded(session);
            session.ensureStatus(RemoteSyncDirection.IMPORT_FROM_REMOTE, RemoteSyncStatus.READY_FOR_DOWNLOAD);
            return session.snapshotPath;
        }
    }

    public void completeImport(String sessionId, boolean success, String message) {
        SessionState session = requireSession(sessionId);
        synchronized (session) {
            expireIfNeeded(session);
            if (success) {
                AppDataRefreshService.getInstance().refreshAfterDatabaseChange();
                session.complete(blankToFallback(message, "Remote database sync completed."));
                notifier.showInfo("remoteSyncRemoteCompletedMessage");
            } else {
                session.fail(blankToFallback(message, "Remote database sync failed."));
                notifier.showError("remoteSyncRemoteFailedMessage");
            }
            cleanupSnapshot(session);
        }
    }

    void setApprovalPrompt(RemoteSyncApprovalPrompt approvalPrompt) {
        this.approvalPrompt = approvalPrompt;
    }

    void setNotifier(RemoteSyncNotifier notifier) {
        this.notifier = notifier;
    }

    void clearSessions() {
        sessions.values().forEach(this::cleanupSnapshot);
        sessions.clear();
    }

    private void applyDecision(String sessionId, boolean approved) {
        SessionState session = sessions.get(sessionId);
        if (session == null) {
            return;
        }
        synchronized (session) {
            expireIfNeeded(session);
            if (session.status != RemoteSyncStatus.PENDING_APPROVAL) {
                return;
            }
            if (!approved) {
                session.reject();
                return;
            }
            try {
                if (session.direction == RemoteSyncDirection.IMPORT_FROM_REMOTE) {
                    session.snapshotPath = snapshotService.createSnapshot(SQLConnection.getDatabasePath());
                    session.status = RemoteSyncStatus.READY_FOR_DOWNLOAD;
                    session.message = "Approved. Snapshot ready.";
                } else {
                    session.status = RemoteSyncStatus.APPROVED;
                    session.message = "Approved. Ready for upload.";
                }
                session.expiresAt = clock.instant().plus(TRANSFER_TTL);
            } catch (IOException | SQLException ex) {
                session.fail(ex.getMessage());
            }
        }
    }

    private void validateRequest(RemoteSyncRequest request) {
        Objects.requireNonNull(request, "request");
        if (request.direction() == null) {
            throw new IllegalArgumentException("direction is required");
        }
        if (request.options() == null) {
            throw new IllegalArgumentException("options are required");
        }
        String code = blankToFallback(request.verificationCode(), "");
        if (!code.matches("\\d{4}")) {
            throw new IllegalArgumentException("verificationCode must be a four digit code");
        }
    }

    private SessionState requireSession(String sessionId) {
        String normalizedSessionId = blankToFallback(sessionId, "");
        SessionState session = sessions.get(normalizedSessionId);
        if (session == null) {
            throw new IllegalArgumentException("Remote sync session not found");
        }
        return session;
    }

    private void expireIfNeeded(SessionState session) {
        if (session.isTerminal() || !clock.instant().isAfter(session.expiresAt)) {
            return;
        }
        session.status = RemoteSyncStatus.EXPIRED;
        session.message = "Remote sync request expired.";
        cleanupSnapshot(session);
    }

    private void cleanupSnapshot(SessionState session) {
        deleteIfExists(session.snapshotPath);
        session.snapshotPath = null;
    }

    private void deleteIfExists(Path path) {
        if (path == null) {
            return;
        }
        try {
            Files.deleteIfExists(path);
        } catch (IOException ignored) {
            // Best effort cleanup.
        }
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class SessionState {
        private final String sessionId;
        private final RemoteSyncDirection direction;
        private final String verificationCode;
        private final String requesterName;
        private final String requesterAddress;
        private final RemoteSyncOptions options;
        private RemoteSyncStatus status;
        private String message;
        private Instant expiresAt;
        private Path snapshotPath;

        private SessionState(String sessionId,
                             RemoteSyncDirection direction,
                             String verificationCode,
                             String requesterName,
                             String requesterAddress,
                             RemoteSyncOptions options,
                             Instant expiresAt) {
            this.sessionId = sessionId;
            this.direction = direction;
            this.verificationCode = verificationCode;
            this.requesterName = requesterName;
            this.requesterAddress = requesterAddress;
            this.options = options;
            this.status = RemoteSyncStatus.PENDING_APPROVAL;
            this.message = "Awaiting approval.";
            this.expiresAt = expiresAt;
        }

        private RemoteSyncSessionState toPublicState() {
            return new RemoteSyncSessionState(
                    sessionId,
                    direction,
                    status,
                    verificationCode,
                    requesterName,
                    requesterAddress,
                    options,
                    message
            );
        }

        private RemoteSyncApprovalRequest toApprovalRequest() {
            return new RemoteSyncApprovalRequest(
                    sessionId,
                    direction,
                    verificationCode,
                    requesterName,
                    requesterAddress,
                    options
            );
        }

        private void ensureStatus(RemoteSyncDirection expectedDirection, RemoteSyncStatus expectedStatus) {
            if (direction != expectedDirection) {
                throw new IllegalStateException("Remote sync session direction mismatch");
            }
            if (status != expectedStatus) {
                throw new IllegalStateException("Remote sync session is not ready");
            }
        }

        private void complete(String message) {
            this.status = RemoteSyncStatus.COMPLETED;
            this.message = message;
        }

        private void reject() {
            this.status = RemoteSyncStatus.REJECTED;
            this.message = "Remote sync request rejected.";
        }

        private void fail(String message) {
            this.status = RemoteSyncStatus.FAILED;
            this.message = message == null || message.isBlank() ? "Remote sync failed." : message;
        }

        private boolean isTerminal() {
            return status == RemoteSyncStatus.REJECTED
                    || status == RemoteSyncStatus.COMPLETED
                    || status == RemoteSyncStatus.FAILED
                    || status == RemoteSyncStatus.EXPIRED;
        }
    }
}
