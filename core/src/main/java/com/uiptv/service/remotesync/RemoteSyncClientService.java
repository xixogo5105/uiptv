package com.uiptv.service.remotesync;

import com.uiptv.db.SQLConnection;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.util.AppLog;

import java.io.IOException;
import java.net.InetAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Duration;

public class RemoteSyncClientService {
    private static final long DEFAULT_POLL_DELAY_MS = 1_000L;
    private static final Duration STATUS_TIMEOUT = Duration.ofMinutes(3);
    private static final String REMOTE_SYNC_COMPLETED_MESSAGE = "Remote database sync completed.";
    private static final String REMOTE_SYNC_FAILED_MESSAGE = "Remote database sync failed.";

    private final RemoteSyncHttpClient httpClient;
    private final DatabaseSnapshotService snapshotService;
    private final DatabaseSyncService databaseSyncService;

    public RemoteSyncClientService() {
        this(new RemoteSyncHttpClient(), new DatabaseSnapshotService(), DatabaseSyncService.getInstance());
    }

    RemoteSyncClientService(RemoteSyncHttpClient httpClient,
                            DatabaseSnapshotService snapshotService,
                            DatabaseSyncService databaseSyncService) {
        this.httpClient = httpClient;
        this.snapshotService = snapshotService;
        this.databaseSyncService = databaseSyncService;
    }

    public void checkConnection(String host, int port) throws IOException {
        httpClient.checkHealth(buildBaseUrl(host, port));
    }

    public RemoteSyncExecutionResult exportToRemote(String host,
                                                    int port,
                                                    RemoteSyncOptions options,
                                                    RemoteSyncProgressListener progressListener) throws IOException, SQLException {
        String baseUrl = buildBaseUrl(host, port);
        AppLog.addInfoLog(RemoteSyncClientService.class, "Remote sync EXPORT: host=" + host + " port=" + port);        notifyProgress(progressListener, RemoteSyncProgressStep.CONNECTING, null);
        httpClient.checkHealth(baseUrl);
        String verificationCode = VerificationCodeGenerator.createFourDigitCode();
        AppLog.addInfoLog(RemoteSyncClientService.class, "Verification code created");        RemoteSyncSessionState session = httpClient.createSession(baseUrl, buildRequest(RemoteSyncDirection.EXPORT_TO_REMOTE, verificationCode, options));
        AppLog.addInfoLog(RemoteSyncClientService.class, "Creating session...");        awaitReadyState(baseUrl, session.sessionId(), RemoteSyncStatus.APPROVED, verificationCode, progressListener);
        AppLog.addInfoLog(RemoteSyncClientService.class, "Waiting for session approval...");        RemoteSyncOptions transferOptions = session.options();

        notifyProgress(progressListener, RemoteSyncProgressStep.CREATING_SNAPSHOT, null);
        AppLog.addInfoLog(RemoteSyncClientService.class, "Creating snapshot payload...");        Path payloadPath = createTransferPayload(SQLConnection.getDatabasePath(), transferOptions);
        AppLog.addInfoLog(RemoteSyncClientService.class, "Payload created");        Path uploadPath = prepareOutboundTransfer(payloadPath, session.sessionId(), verificationCode, transferOptions);
        AppLog.addInfoLog(RemoteSyncClientService.class, "Prepared outbound transfer");        try {
            notifyProgress(progressListener, RemoteSyncProgressStep.UPLOADING, null);
        AppLog.addInfoLog(RemoteSyncClientService.class, "Uploading...");            RemoteSyncExecutionResult result = uploadSnapshotWithRemoteFailureContext(baseUrl, session.sessionId(), uploadPath);
        AppLog.addInfoLog(RemoteSyncClientService.class, "Upload completed");            notifyProgress(progressListener, RemoteSyncProgressStep.FINISHED, null);
            return result;
        } finally {
            Files.deleteIfExists(payloadPath);
            if (!uploadPath.equals(payloadPath)) {
                Files.deleteIfExists(uploadPath);
            }
        }
    }

    private RemoteSyncExecutionResult uploadSnapshotWithRemoteFailureContext(String baseUrl,
                                                                            String sessionId,
                                                                            Path uploadPath) throws IOException {
        try {
            return httpClient.uploadSnapshot(baseUrl, sessionId, uploadPath);
        } catch (IOException uploadFailure) {
            RemoteSyncSessionState remoteState = readRemoteStateAfterUploadFailure(baseUrl, sessionId);
            if (remoteState != null && remoteState.status() == RemoteSyncStatus.FAILED
                    && remoteState.message() != null && !remoteState.message().isBlank()) {
                throw new IOException(remoteState.message(), uploadFailure);
            }
            throw uploadFailure;
        }
    }

    private RemoteSyncSessionState readRemoteStateAfterUploadFailure(String baseUrl, String sessionId) {
        try {
            return httpClient.getSessionState(baseUrl, sessionId);
        } catch (IOException ignored) {
            return null;
        }
    }

    public RemoteSyncExecutionResult importFromRemote(String host,
                                                      int port,
                                                      RemoteSyncOptions options,
                                                      RemoteSyncProgressListener progressListener) throws IOException, SQLException {
        String baseUrl = buildBaseUrl(host, port);
        notifyProgress(progressListener, RemoteSyncProgressStep.CONNECTING, null);
        httpClient.checkHealth(baseUrl);

        String verificationCode = VerificationCodeGenerator.createFourDigitCode();
        RemoteSyncSessionState session = httpClient.createSession(baseUrl, buildRequest(RemoteSyncDirection.IMPORT_FROM_REMOTE, verificationCode, options));
        awaitReadyState(baseUrl, session.sessionId(), RemoteSyncStatus.READY_FOR_DOWNLOAD, verificationCode, progressListener);

        notifyProgress(progressListener, RemoteSyncProgressStep.DOWNLOADING, null);
        Path downloadedTransfer = httpClient.downloadSnapshot(baseUrl, session.sessionId());
        Path payloadPath = null;
        Path downloadedSnapshot = null;
        try {
            payloadPath = prepareInboundPayload(downloadedTransfer, session.sessionId(), verificationCode, session.options());
            downloadedSnapshot = extractTransferSnapshot(payloadPath, session.options());
            notifyProgress(progressListener, RemoteSyncProgressStep.APPLYING_SYNC, null);
            DatabaseSyncService.DatabaseSyncReport report = databaseSyncService.syncDatabasesWithReport(
                    downloadedSnapshot.toAbsolutePath().toString(),
                    SQLConnection.getDatabasePath(),
                    options.syncConfiguration(),
                    options.syncExternalPlayerPaths(),
                    null
            );
            notifyProgress(progressListener, RemoteSyncProgressStep.COMPLETING_REMOTE, null);
            httpClient.completeSession(baseUrl, session.sessionId(), true, REMOTE_SYNC_COMPLETED_MESSAGE);
            notifyProgress(progressListener, RemoteSyncProgressStep.FINISHED, null);
            return new RemoteSyncExecutionResult(report, REMOTE_SYNC_COMPLETED_MESSAGE);
        } catch (IOException | SQLException ex) {
            AppLog.addErrorLog(
                    RemoteSyncClientService.class,
                    "Remote database sync failed while applying downloaded snapshot: " + ex.getMessage()
            );
            httpClient.completeSession(baseUrl, session.sessionId(), false, REMOTE_SYNC_FAILED_MESSAGE);
            throw ex;
        } finally {
            Files.deleteIfExists(downloadedTransfer);
            if (payloadPath != null && !payloadPath.equals(downloadedTransfer)) {
                Files.deleteIfExists(payloadPath);
            }
            Files.deleteIfExists(downloadedSnapshot);
        }
    }

    private Path createTransferPayload(String databasePath, RemoteSyncOptions options) throws IOException, SQLException {
        if (options.archiveTransfer()) {
            return snapshotService.createSnapshotArchive(databasePath);
        }
        return snapshotService.createSnapshot(databasePath);
    }

    private Path prepareOutboundTransfer(Path payloadPath,
                                         String sessionId,
                                         String verificationCode,
                                         RemoteSyncOptions options) throws IOException {
        if (!options.encryptedTransfer()) {
            return payloadPath;
        }
        Path encryptedPath = SecureTempFileSupport.createTempFile("uiptv-remote-sync-", ".bin");
        try {
            RemoteSyncTransferCipher.encrypt(payloadPath, encryptedPath, verificationCode, sessionId);
            return encryptedPath;
        } catch (IOException ex) {
            Files.deleteIfExists(encryptedPath);
            throw ex;
        }
    }

    private Path prepareInboundPayload(Path downloadedTransfer,
                                       String sessionId,
                                       String verificationCode,
                                       RemoteSyncOptions options) throws IOException {
        if (!options.encryptedTransfer()) {
            return downloadedTransfer;
        }
        Path decryptedPath = SecureTempFileSupport.createTempFile("uiptv-remote-sync-", options.archiveTransfer() ? ".zip" : ".db");
        try {
            RemoteSyncTransferCipher.decrypt(downloadedTransfer, decryptedPath, verificationCode, sessionId);
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

    private RemoteSyncRequest buildRequest(RemoteSyncDirection direction, String verificationCode, RemoteSyncOptions options) {
        return new RemoteSyncRequest(direction, verificationCode, resolveRequesterName(), options);
    }

    private void awaitReadyState(String baseUrl,
                                 String sessionId,
                                 RemoteSyncStatus expectedStatus,
                                 String verificationCode,
                                 RemoteSyncProgressListener progressListener) throws IOException {
        long deadline = System.nanoTime() + STATUS_TIMEOUT.toNanos();
        notifyProgress(progressListener, RemoteSyncProgressStep.WAITING_FOR_APPROVAL, verificationCode);
        while (System.nanoTime() < deadline) {
            RemoteSyncSessionState state = httpClient.getSessionState(baseUrl, sessionId);
            if (state.status() == expectedStatus || state.status() == RemoteSyncStatus.COMPLETED) {
                return;
            }
            if (state.status() == RemoteSyncStatus.REJECTED
                    || state.status() == RemoteSyncStatus.FAILED
                    || state.status() == RemoteSyncStatus.EXPIRED) {
                throw new IOException(state.message());
            }
            sleepBeforeNextPoll();
        }
        throw new IOException("Timed out while waiting for remote sync approval");
    }

    private void sleepBeforeNextPoll() throws IOException {
        long pollDelayMs = Long.getLong("uiptv.remote.sync.poll.delay.ms", DEFAULT_POLL_DELAY_MS);
        if (pollDelayMs <= 0L) {
            return;
        }
        try {
            Thread.sleep(pollDelayMs);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new IOException("Interrupted while waiting for remote sync approval", interruptedException);
        }
    }

    private String buildBaseUrl(String host, int port) {
        if (host == null || host.isBlank()) {
            return "http://localhost:" + port;
           }
        String lowerHost = host.toLowerCase();
        String prefix;
        int prefixLen;
        if (lowerHost.startsWith("https://")) {
            prefix = "https://";
            prefixLen = 8;
         } else if (lowerHost.startsWith("http://")) {
            prefix = "http://";
            prefixLen = 7;
         } else {
            prefix = "http://";
            prefixLen = 0;
         }
        String hostPart = lowerHost.substring(prefixLen);
         // Extract hostname only (strip port and path)
        int pathIndex = hostPart.indexOf(47);
        if (pathIndex >= 0) {
            hostPart = hostPart.substring(0, pathIndex);
          }
        int portIndex = hostPart.indexOf(58);
        if (portIndex >= 0) {
            hostPart = hostPart.substring(0, portIndex);
         }
        return prefix + hostPart + ":" + port;
        }

    private String resolveRequesterName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (Exception _) {
            return "UIPTV";
        }
    }

    private void notifyProgress(RemoteSyncProgressListener progressListener, RemoteSyncProgressStep step, String detail) {
        if (progressListener != null) {
            progressListener.onProgress(step, detail);
        }
    }
}
