package com.uiptv.service.remotesync;

import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DatabaseSyncService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RemoteSyncClientServiceTest extends DbBackedTest {
    @Test
    void exportToRemote_waitsForApproval_uploadsSnapshot() throws Exception {
        saveLocalAccount("local-account");
        FakeRemoteSyncHttpClient httpClient = new FakeRemoteSyncHttpClient();
        httpClient.nextCreatedState = new RemoteSyncSessionState(
                "session-1",
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                RemoteSyncStatus.PENDING_APPROVAL,
                "1234",
                "machine-a",
                "10.0.0.9",
                new RemoteSyncOptions(true, false),
                "Awaiting approval."
        );
        httpClient.statusResponses.add(httpClient.nextCreatedState);
        httpClient.statusResponses.add(new RemoteSyncSessionState(
                "session-1",
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                RemoteSyncStatus.APPROVED,
                "1234",
                "machine-a",
                "10.0.0.9",
                new RemoteSyncOptions(true, false),
                "Approved."
        ));
        httpClient.uploadResult = new RemoteSyncExecutionResult(
                new DatabaseSyncService.DatabaseSyncReport(List.of(
                        new DatabaseSyncService.TableSyncResult("account", 1)
                ), true, true, false),
                "done"
        );

        RemoteSyncClientService service = new RemoteSyncClientService(httpClient, new DatabaseSnapshotService(), DatabaseSyncService.getInstance());
        List<RemoteSyncProgressStep> progressSteps = new ArrayList<>();
        RemoteSyncExecutionResult result = service.exportToRemote(
                "127.0.0.1",
                8888,
                new RemoteSyncOptions(true, false),
                (step, detail) -> progressSteps.add(step)
        );

        assertNotNull(result.report());
        assertTrue(httpClient.healthChecked);
        assertEquals("session-1", httpClient.lastUploadedSessionId);
        assertNotNull(httpClient.lastUploadedSnapshot);
        assertTrue(Files.exists(httpClient.lastUploadedSnapshot));
        assertTrue(progressSteps.contains(RemoteSyncProgressStep.UPLOADING));
    }

    @Test
    void importFromRemote_downloadsSnapshot_syncsLocally_andCompletesRemote() throws Exception {
        Path remoteSourceDb = tempDir.resolve("remote-source.db");
        initializeDatabase(remoteSourceDb);
        withDatabase(remoteSourceDb, () -> {
            saveLocalAccount("remote-account");
            saveLocalConfiguration(true, "remote-player");
        });

        FakeRemoteSyncHttpClient httpClient = new FakeRemoteSyncHttpClient();
        httpClient.nextCreatedState = new RemoteSyncSessionState(
                "session-2",
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                RemoteSyncStatus.PENDING_APPROVAL,
                "4321",
                "machine-a",
                "10.0.0.9",
                new RemoteSyncOptions(true, true),
                "Awaiting approval."
        );
        httpClient.statusResponses.add(httpClient.nextCreatedState);
        httpClient.statusResponses.add(new RemoteSyncSessionState(
                "session-2",
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                RemoteSyncStatus.READY_FOR_DOWNLOAD,
                "4321",
                "machine-a",
                "10.0.0.9",
                new RemoteSyncOptions(true, true),
                "Ready."
        ));
        httpClient.downloadSource = new DatabaseSnapshotService().createSnapshot(remoteSourceDb.toString());

        RemoteSyncClientService service = new RemoteSyncClientService(httpClient, new DatabaseSnapshotService(), DatabaseSyncService.getInstance());
        RemoteSyncExecutionResult result = service.importFromRemote(
                "127.0.0.1",
                8888,
                new RemoteSyncOptions(true, true),
                (step, detail) -> {
                }
        );

        assertNotNull(result.report());
        assertNotNull(AccountService.getInstance().getByName("remote-account"));
        Configuration configuration = ConfigurationService.getInstance().read();
        assertTrue(configuration.isDarkTheme());
        assertEquals("remote-player", configuration.getPlayerPath1());
        assertEquals("session-2", httpClient.completedSessionId);
        assertTrue(httpClient.completedSuccess);
    }

    private void initializeDatabase(Path path) throws Exception {
        withDatabase(path, () -> ConfigurationService.getInstance().read());
    }

    private void saveLocalAccount(String accountName) {
        AccountService.getInstance().save(new Account(
                accountName,
                "user",
                "pass",
                "http://example.com",
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.M3U8_URL,
                null,
                null,
                false
        ));
    }

    private void saveLocalConfiguration(boolean darkTheme, String playerPath1) {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setDarkTheme(darkTheme);
        configuration.setPlayerPath1(playerPath1);
        ConfigurationService.getInstance().save(configuration);
    }

    private void withDatabase(Path databasePath, ThrowingRunnable runnable) throws Exception {
        String originalPath = com.uiptv.db.SQLConnection.getDatabasePath();
        com.uiptv.db.SQLConnection.setDatabasePath(databasePath.toString());
        try {
            runnable.run();
        } finally {
            com.uiptv.db.SQLConnection.setDatabasePath(originalPath);
        }
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class FakeRemoteSyncHttpClient extends RemoteSyncHttpClient {
        private final List<RemoteSyncSessionState> statusResponses = new ArrayList<>();
        private RemoteSyncSessionState nextCreatedState;
        private RemoteSyncExecutionResult uploadResult;
        private Path downloadSource;
        private boolean healthChecked;
        private String lastUploadedSessionId;
        private Path lastUploadedSnapshot;
        private String completedSessionId;
        private boolean completedSuccess;

        @Override
        public void checkHealth(String baseUrl) {
            healthChecked = true;
        }

        @Override
        public RemoteSyncSessionState createSession(String baseUrl, RemoteSyncRequest request) {
            return nextCreatedState;
        }

        @Override
        public RemoteSyncSessionState getSessionState(String baseUrl, String sessionId) {
            return statusResponses.remove(0);
        }

        @Override
        public RemoteSyncExecutionResult uploadSnapshot(String baseUrl, String sessionId, Path snapshotPath) throws IOException {
            lastUploadedSessionId = sessionId;
            lastUploadedSnapshot = Files.createTempFile("remote-client-upload-", ".db");
            Files.copy(snapshotPath, lastUploadedSnapshot, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return uploadResult;
        }

        @Override
        public Path downloadSnapshot(String baseUrl, String sessionId) throws IOException {
            Path copy = Files.createTempFile("remote-client-download-", ".db");
            Files.copy(downloadSource, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            return copy;
        }

        @Override
        public void completeSession(String baseUrl, String sessionId, boolean success, String message) {
            completedSessionId = sessionId;
            completedSuccess = success;
        }
    }
}
