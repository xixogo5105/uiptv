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
import java.nio.charset.StandardCharsets;
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

        RemoteSyncClientService service = new RemoteSyncClientService(
                httpClient,
                new DatabaseSnapshotService(),
                DatabaseSyncService.getInstance()
        );
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
        Path decryptedArchive = tempDir.resolve("uploaded-transfer.zip");
        RemoteSyncTransferCipher.decrypt(
                httpClient.lastUploadedSnapshot,
                decryptedArchive,
                httpClient.lastRequest.verificationCode(),
                "session-1"
        );
        Path uploadedSnapshot = new DatabaseSnapshotService().extractSnapshotDatabase(decryptedArchive);
        try {
            withDatabase(uploadedSnapshot, () -> assertNotNull(AccountService.getInstance().getByName("local-account")));
        } finally {
            Files.deleteIfExists(uploadedSnapshot);
        }
        assertTrue(progressSteps.contains(RemoteSyncProgressStep.UPLOADING));
    }

    @Test
    void exportToRemote_usesLegacyRawTransferWhenRemoteDoesNotEchoArchiveSupport() throws Exception {
        RemoteSyncOptions legacyOptions = RemoteSyncOptions.legacyRawTransfer(false, false, ConfigurationSyncProfile.DESKTOP_FULL);
        FakeRemoteSyncHttpClient httpClient = new FakeRemoteSyncHttpClient();
        httpClient.nextCreatedState = new RemoteSyncSessionState(
                "legacy-session",
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                RemoteSyncStatus.PENDING_APPROVAL,
                "1234",
                "machine-a",
                "10.0.0.9",
                legacyOptions,
                "Awaiting approval."
        );
        httpClient.statusResponses.add(new RemoteSyncSessionState(
                "legacy-session",
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                RemoteSyncStatus.APPROVED,
                "1234",
                "machine-a",
                "10.0.0.9",
                legacyOptions,
                "Approved."
        ));
        httpClient.uploadResult = new RemoteSyncExecutionResult(null, "done");

        RemoteSyncClientService service = new RemoteSyncClientService(
                httpClient,
                new DatabaseSnapshotService(),
                DatabaseSyncService.getInstance()
        );

        service.exportToRemote(
                "127.0.0.1",
                8888,
                new RemoteSyncOptions(false, false),
                (step, detail) -> {
                }
        );

        try (var input = Files.newInputStream(httpClient.lastUploadedSnapshot)) {
            assertEquals("SQLite format 3", new String(input.readNBytes(15), StandardCharsets.US_ASCII));
        }
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
        httpClient.downloadSource = remoteSourceDb;
        httpClient.downloadAsEncryptedArchive = true;

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

    @Test
    void importFromRemote_archiveTransferHonorsConfigurationAndPlayerPathExclusions() throws Exception {
        Path remoteSourceDb = tempDir.resolve("remote-config-excluded-source.db");
        initializeDatabase(remoteSourceDb);
        withDatabase(remoteSourceDb, () -> {
            saveLocalAccount("remote-account");
            saveLocalConfiguration(true, "remote-player");
        });
        saveLocalConfiguration(false, "local-player");

        RemoteSyncOptions options = new RemoteSyncOptions(false, false);
        FakeRemoteSyncHttpClient httpClient = new FakeRemoteSyncHttpClient();
        httpClient.nextCreatedState = new RemoteSyncSessionState(
                "session-config-excluded",
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                RemoteSyncStatus.PENDING_APPROVAL,
                "8642",
                "machine-a",
                "10.0.0.9",
                options,
                "Awaiting approval."
        );
        httpClient.statusResponses.add(httpClient.nextCreatedState);
        httpClient.statusResponses.add(new RemoteSyncSessionState(
                "session-config-excluded",
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                RemoteSyncStatus.READY_FOR_DOWNLOAD,
                "8642",
                "machine-a",
                "10.0.0.9",
                options,
                "Ready."
        ));
        httpClient.downloadSource = remoteSourceDb;
        httpClient.downloadAsEncryptedArchive = true;

        RemoteSyncClientService service = new RemoteSyncClientService(httpClient, new DatabaseSnapshotService(), DatabaseSyncService.getInstance());
        RemoteSyncExecutionResult result = service.importFromRemote(
                "127.0.0.1",
                8888,
                options,
                (step, detail) -> {
                }
        );

        assertNotNull(result.report());
        assertFalse(result.report().isConfigurationRequested());
        assertNotNull(AccountService.getInstance().getByName("remote-account"));
        Configuration configuration = ConfigurationService.getInstance().read();
        assertFalse(configuration.isDarkTheme());
        assertEquals("local-player", configuration.getPlayerPath1());
        assertEquals("session-config-excluded", httpClient.completedSessionId);
        assertTrue(httpClient.completedSuccess);
    }

    @Test
    void importFromRemote_reportsGenericFailureToRemoteWhenLocalSyncFails() throws Exception {
        Path invalidRemoteSnapshot = tempDir.resolve("invalid-remote-source.db");
        Files.writeString(invalidRemoteSnapshot, "not a sqlite database");

        FakeRemoteSyncHttpClient httpClient = new FakeRemoteSyncHttpClient();
        RemoteSyncOptions legacyOptions = RemoteSyncOptions.legacyRawTransfer(true, true, ConfigurationSyncProfile.DESKTOP_FULL);
        httpClient.nextCreatedState = new RemoteSyncSessionState(
                "session-3",
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                RemoteSyncStatus.PENDING_APPROVAL,
                "1111",
                "machine-a",
                "10.0.0.9",
                legacyOptions,
                "Awaiting approval."
        );
        httpClient.statusResponses.add(httpClient.nextCreatedState);
        httpClient.statusResponses.add(new RemoteSyncSessionState(
                "session-3",
                RemoteSyncDirection.IMPORT_FROM_REMOTE,
                RemoteSyncStatus.READY_FOR_DOWNLOAD,
                "1111",
                "machine-a",
                "10.0.0.9",
                legacyOptions,
                "Ready."
        ));
        httpClient.downloadSource = invalidRemoteSnapshot;

        RemoteSyncClientService service = new RemoteSyncClientService(httpClient, new DatabaseSnapshotService(), DatabaseSyncService.getInstance());
        Exception failure = assertThrows(Exception.class, () -> service.importFromRemote(
                "127.0.0.1",
                8888,
                legacyOptions,
                (step, detail) -> {
                }
        ));

        assertEquals("session-3", httpClient.completedSessionId);
        assertFalse(httpClient.completedSuccess);
        assertEquals("Remote database sync failed.", httpClient.completedMessage);
        assertNotEquals(failure.getMessage(), httpClient.completedMessage);
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
        private RemoteSyncRequest lastRequest;
        private Path downloadSource;
        private boolean downloadAsEncryptedArchive;
        private boolean healthChecked;
        private String lastUploadedSessionId;
        private Path lastUploadedSnapshot;
        private String completedSessionId;
        private boolean completedSuccess;
        private String completedMessage;

        @Override
        public void checkHealth(String baseUrl) {
            healthChecked = true;
        }

        @Override
        public RemoteSyncSessionState createSession(String baseUrl, RemoteSyncRequest request) {
            lastRequest = request;
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
            Path transferSource = buildDownloadTransfer(sessionId);
            Files.copy(transferSource, copy, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            if (!transferSource.equals(downloadSource)) {
                Files.deleteIfExists(transferSource);
            }
            return copy;
        }

        private Path buildDownloadTransfer(String sessionId) throws IOException {
            if (!downloadAsEncryptedArchive) {
                return downloadSource;
            }
            Path archivePath = null;
            Path encryptedPath = null;
            try {
                archivePath = new DatabaseSnapshotService().createSnapshotArchive(downloadSource.toString());
                encryptedPath = Files.createTempFile("remote-client-download-", ".bin");
                RemoteSyncTransferCipher.encrypt(archivePath, encryptedPath, lastRequest.verificationCode(), sessionId);
                return encryptedPath;
            } catch (Exception ex) {
                if (encryptedPath != null) {
                    Files.deleteIfExists(encryptedPath);
                }
                throw ex instanceof IOException ioException ? ioException : new IOException(ex);
            } finally {
                if (archivePath != null) {
                    Files.deleteIfExists(archivePath);
                }
            }
        }

        @Override
        public void completeSession(String baseUrl, String sessionId, boolean success, String message) {
            completedSessionId = sessionId;
            completedSuccess = success;
            completedMessage = message;
        }
    }
}
