package com.uiptv.service.remotesync;

import com.uiptv.db.SQLConnection;
import com.uiptv.service.AppDataRefreshService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.util.AppLog;

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
import java.util.concurrent.atomic.AtomicReference;

public class RemoteSyncSessionService {
    private static final Duration APPROVAL_TTL = Duration.ofMinutes(2);
    private static final Duration TRANSFER_TTL = Duration.ofMinutes(10);
    private static final String REMOTE_SYNC_COMPLETED_MESSAGE = "Remote database sync completed.";
    private static final String REMOTE_SYNC_FAILED_MESSAGE = "Remote database sync failed.";

    private final Map<String, SessionState> sessions = new ConcurrentHashMap<>();
    private final DatabaseSnapshotService snapshotService;
    private final DatabaseSyncService databaseSyncService;
    private final Clock clock;
    private final AtomicReference<RemoteSyncApprovalPrompt> approvalPrompt;
    private final AtomicReference<RemoteSyncNotifier> notifier;

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
        this.approvalPrompt = new AtomicReference<>(Objects.requireNonNull(approvalPrompt, "approvalPrompt"));
        this.notifier = new AtomicReference<>(Objects.requireNonNull(notifier, "notifier"));
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
        approvalPrompt.get().requestApproval(session.toApprovalRequest(), approved -> applyDecision(session.sessionId, approved));
        return session.toPublicState();
    }

    public RemoteSyncSessionState getSessionState(String sessionId) {
        SessionState session = requireSession(sessionId);
        synchronized (session.monitor) {
            expireIfNeeded(session);
            return session.toPublicState();
        }
    }

    public RemoteSyncExecutionResult acceptUpload(String sessionId, InputStream requestBody) throws IOException, SQLException {
        SessionState session = requireSession(sessionId);
        Path uploadedTransfer = null;
        Path payloadPath = null;
        Path uploadedSnapshot = null;
        synchronized (session.monitor) {
            expireIfNeeded(session);
            session.ensureStatus(RemoteSyncDirection.EXPORT_TO_REMOTE, RemoteSyncStatus.APPROVED);
            uploadedTransfer = SecureTempFileSupport.createTempFile("uiptv-remote-upload-", ".bin");
            try (InputStream inputStream = requestBody) {
                Files.copy(inputStream, uploadedTransfer, StandardCopyOption.REPLACE_EXISTING);
            }
        }

        try {
            payloadPath = prepareInboundPayload(uploadedTransfer, session);
            uploadedSnapshot = extractTransferSnapshot(payloadPath, session.options);
            DatabaseSyncService.DatabaseSyncReport report = databaseSyncService.syncDatabasesWithReport(
                    uploadedSnapshot.toAbsolutePath().toString(),
                    SQLConnection.getDatabasePath(),
                    session.options.syncConfiguration(),
                    session.options.syncExternalPlayerPaths(),
                    null
            );
            AppDataRefreshService.getInstance().refreshAfterDatabaseChange();
            synchronized (session.monitor) {
                session.complete(REMOTE_SYNC_COMPLETED_MESSAGE);
            }
            notifier.get().showInfo("remoteSyncRemoteCompletedMessage");
            return new RemoteSyncExecutionResult(report, REMOTE_SYNC_COMPLETED_MESSAGE);
        } catch (IOException | SQLException ex) {
            AppLog.addErrorLog(
                    RemoteSyncSessionService.class,
                    "Remote database sync failed while accepting upload: " + ex.getMessage()
            );
            synchronized (session.monitor) {
                session.fail(REMOTE_SYNC_FAILED_MESSAGE);
            }
            notifier.get().showError("remoteSyncRemoteFailedMessage");
            throw ex;
        } finally {
            deleteIfExists(uploadedTransfer);
            if (payloadPath != null && !payloadPath.equals(uploadedTransfer)) {
                deleteIfExists(payloadPath);
            }
            deleteIfExists(uploadedSnapshot);
        }
    }

    public Path getDownloadSnapshot(String sessionId) {
        SessionState session = requireSession(sessionId);
        synchronized (session.monitor) {
            expireIfNeeded(session);
            session.ensureStatus(RemoteSyncDirection.IMPORT_FROM_REMOTE, RemoteSyncStatus.READY_FOR_DOWNLOAD);
            return session.snapshotPath;
        }
    }

    public void completeImport(String sessionId, boolean success, String message) {
        SessionState session = requireSession(sessionId);
        synchronized (session.monitor) {
            expireIfNeeded(session);
            if (success) {
                AppDataRefreshService.getInstance().refreshAfterDatabaseChange();
                session.complete(blankToFallback(message, REMOTE_SYNC_COMPLETED_MESSAGE));
                notifier.get().showInfo("remoteSyncRemoteCompletedMessage");
            } else {
                session.fail(blankToFallback(message, REMOTE_SYNC_FAILED_MESSAGE));
                notifier.get().showError("remoteSyncRemoteFailedMessage");
            }
            cleanupSnapshot(session);
        }
    }

    public void setApprovalPrompt(RemoteSyncApprovalPrompt approvalPrompt) {
        this.approvalPrompt.set(Objects.requireNonNull(approvalPrompt, "approvalPrompt"));
    }

    public void setNotifier(RemoteSyncNotifier notifier) {
        this.notifier.set(Objects.requireNonNull(notifier, "notifier"));
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
        boolean prepareDownload = false;
        synchronized (session.monitor) {
            expireIfNeeded(session);
            if (session.status != RemoteSyncStatus.PENDING_APPROVAL) {
                return;
            }
            if (!approved) {
                session.reject();
                return;
            }
            session.status = RemoteSyncStatus.APPROVED;
            session.message = session.direction == RemoteSyncDirection.IMPORT_FROM_REMOTE
                    ? "Approved. Preparing snapshot."
                    : "Approved. Ready for upload.";
            session.expiresAt = clock.instant().plus(TRANSFER_TTL);
            prepareDownload = session.direction == RemoteSyncDirection.IMPORT_FROM_REMOTE;
        }

        if (prepareDownload) {
            prepareDownloadSnapshot(session);
        }
    }

    private void prepareDownloadSnapshot(SessionState session) {
        Path payloadPath = null;
        Path outboundPath = null;
        try {
            payloadPath = createTransferPayload(SQLConnection.getDatabasePath(), session.options);
            outboundPath = prepareOutboundTransfer(payloadPath, session);
            if (!outboundPath.equals(payloadPath)) {
                deleteIfExists(payloadPath);
                payloadPath = null;
            }
            synchronized (session.monitor) {
                if (session.status != RemoteSyncStatus.APPROVED) {
                    return;
                }
                session.snapshotPath = outboundPath;
                outboundPath = null;
                payloadPath = null;
                session.status = RemoteSyncStatus.READY_FOR_DOWNLOAD;
                session.message = "Approved. Snapshot ready.";
            }
        } catch (IOException | SQLException ex) {
            synchronized (session.monitor) {
                session.fail(ex.getMessage());
            }
        } finally {
            deleteIfExists(payloadPath);
            deleteIfExists(outboundPath);
        }
    }

    private Path createTransferPayload(String databasePath, RemoteSyncOptions options) throws IOException, SQLException {
        if (options.archiveTransfer()) {
            return snapshotService.createSnapshotArchive(databasePath);
        }
        return snapshotService.createSnapshot(databasePath);
    }

    private Path prepareOutboundTransfer(Path payloadPath, SessionState session) throws IOException {
        if (!session.options.encryptedTransfer()) {
            return payloadPath;
        }
        Path encryptedPath = SecureTempFileSupport.createTempFile("uiptv-remote-download-", ".bin");
        try {
            RemoteSyncTransferCipher.encrypt(payloadPath, encryptedPath, session.verificationCode, session.sessionId);
            return encryptedPath;
        } catch (IOException ex) {
            Files.deleteIfExists(encryptedPath);
            throw ex;
        }
    }

    private Path prepareInboundPayload(Path uploadedTransfer, SessionState session) throws IOException {
        if (!session.options.encryptedTransfer()) {
            return uploadedTransfer;
        }
        Path decryptedPath = SecureTempFileSupport.createTempFile(
                "uiptv-remote-upload-",
                session.options.archiveTransfer() ? ".zip" : ".db"
        );
        try {
            RemoteSyncTransferCipher.decrypt(uploadedTransfer, decryptedPath, session.verificationCode, session.sessionId);
            return decryptedPath;
        } catch (IOException ex) {
            Files.deleteIfExists(decryptedPath);
            throw ex;
        }
    }

    private Path extractTransferSnapshot(Path payloadPath, RemoteSyncOptions options) throws IOException, SQLException {
        if (options.archiveTransfer()) {
            return snapshotService.extractSnapshotDatabase(payloadPath);
        }
        return payloadPath;
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
        } catch (IOException _) {
            // Best effort cleanup.
        }
    }

    private String blankToFallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }

    private static final class SessionState {
        private final Object monitor = new Object();
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
