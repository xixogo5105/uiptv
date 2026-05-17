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
        notifyProgress(progressListener, RemoteSyncProgressStep.CONNECTING, null);
        httpClient.checkHealth(baseUrl);

        String verificationCode = VerificationCodeGenerator.createFourDigitCode();
        RemoteSyncSessionState session = httpClient.createSession(baseUrl, buildRequest(RemoteSyncDirection.EXPORT_TO_REMOTE, verificationCode, options));
        awaitReadyState(baseUrl, session.sessionId(), RemoteSyncStatus.APPROVED, verificationCode, progressListener);

        notifyProgress(progressListener, RemoteSyncProgressStep.CREATING_SNAPSHOT, null);
        Path snapshotPath = snapshotService.createSnapshot(SQLConnection.getDatabasePath());
        try {
            notifyProgress(progressListener, RemoteSyncProgressStep.UPLOADING, null);
            RemoteSyncExecutionResult result = httpClient.uploadSnapshot(baseUrl, session.sessionId(), snapshotPath);
            notifyProgress(progressListener, RemoteSyncProgressStep.FINISHED, null);
            return result;
        } finally {
            Files.deleteIfExists(snapshotPath);
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
        Path downloadedSnapshot = httpClient.downloadSnapshot(baseUrl, session.sessionId());
        try {
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
            Files.deleteIfExists(downloadedSnapshot);
        }
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
        return "http://" + normalizeHost(host) + ":" + port;
    }

    private String normalizeHost(String host) {
        String normalized = host == null ? "" : host.trim();
        normalized = normalized.replaceFirst("^https?://", "");
        int slashIndex = normalized.indexOf('/');
        return slashIndex >= 0 ? normalized.substring(0, slashIndex) : normalized;
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
