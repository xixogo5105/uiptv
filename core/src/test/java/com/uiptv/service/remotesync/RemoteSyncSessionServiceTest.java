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
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.SQLException;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class RemoteSyncSessionServiceTest extends DbBackedTest {
    private final DatabaseSnapshotService snapshotService = new DatabaseSnapshotService();

    @Test
    void exportSession_acceptUpload_syncsSnapshotIntoRemoteDatabase() throws Exception {
        Path remoteDb = tempDir.resolve("remote.db");
        Path sourceDb = tempDir.resolve("source.db");
        initializeDatabase(remoteDb);
        initializeDatabase(sourceDb);

        withDatabase(remoteDb, () -> saveConfiguration(false, "keep-local-player"));
        withDatabase(sourceDb, () -> {
            saveAccount("source-account");
            saveConfiguration(true, "source-player");
        });

        RecordingNotifier notifier = new RecordingNotifier();
        RemoteSyncSessionService service = new RemoteSyncSessionService(
                snapshotService,
                DatabaseSyncService.getInstance(),
                Clock.systemUTC(),
                (request, decisionConsumer) -> decisionConsumer.accept(true),
                notifier
        );

        String sessionId = withDatabase(remoteDb, () -> {
            RemoteSyncSessionState created = service.createSession(
                new RemoteSyncRequest(
                        RemoteSyncDirection.EXPORT_TO_REMOTE,
                        "1234",
                        "machine-a",
                        new RemoteSyncOptions(true, false)
                ),
                "10.0.0.8");
            assertEquals(RemoteSyncStatus.APPROVED, created.status());
            return created.sessionId();
        });

        RemoteSyncSessionState approved = service.getSessionState(sessionId);
        assertEquals(RemoteSyncStatus.APPROVED, approved.status());

        Path sourceArchive = snapshotService.createSnapshotArchive(sourceDb.toString());
        Path encryptedUpload = tempDir.resolve("source-upload.bin");
        RemoteSyncTransferCipher.encrypt(sourceArchive, encryptedUpload, "1234", sessionId);
        try (InputStream snapshotStream = Files.newInputStream(encryptedUpload)) {
            withDatabase(remoteDb, () -> {
                try {
                    DatabaseSyncService.DatabaseSyncReport report = service.acceptUpload(sessionId, snapshotStream).report();
                    assertNotNull(report);
                    assertTrue(report.isConfigurationRequested());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } finally {
            Files.deleteIfExists(sourceArchive);
        }

        withDatabase(remoteDb, () -> {
            Account imported = AccountService.getInstance().getByName("source-account");
            assertNotNull(imported);
            Configuration configuration = ConfigurationService.getInstance().read();
            assertTrue(configuration.isDarkTheme());
            assertEquals("keep-local-player", configuration.getPlayerPath1());
        });

        assertEquals(RemoteSyncStatus.COMPLETED, service.getSessionState(sessionId).status());
        assertTrue(notifier.infoMessages.contains("remoteSyncRemoteCompletedMessage"));
    }

    @Test
    void exportSession_archiveTransferHonorsConfigurationExclusion() throws Exception {
        Path remoteDb = tempDir.resolve("remote-config-excluded.db");
        Path sourceDb = tempDir.resolve("source-config-excluded.db");
        initializeDatabase(remoteDb);
        initializeDatabase(sourceDb);

        withDatabase(remoteDb, () -> saveConfiguration(false, "keep-local-player"));
        withDatabase(sourceDb, () -> {
            saveAccount("source-account");
            saveConfiguration(true, "source-player");
        });

        RecordingNotifier notifier = new RecordingNotifier();
        RemoteSyncSessionService service = new RemoteSyncSessionService(
                snapshotService,
                DatabaseSyncService.getInstance(),
                Clock.systemUTC(),
                (request, decisionConsumer) -> decisionConsumer.accept(true),
                notifier
        );

        String sessionId = withDatabase(remoteDb, () -> service.createSession(
                new RemoteSyncRequest(
                        RemoteSyncDirection.EXPORT_TO_REMOTE,
                        "2468",
                        "machine-a",
                        new RemoteSyncOptions(false, false)
                ),
                "10.0.0.8"
        ).sessionId());

        Path sourceArchive = snapshotService.createSnapshotArchive(sourceDb.toString());
        Path encryptedUpload = tempDir.resolve("source-config-excluded-upload.bin");
        RemoteSyncTransferCipher.encrypt(sourceArchive, encryptedUpload, "2468", sessionId);
        try (InputStream snapshotStream = Files.newInputStream(encryptedUpload)) {
            withDatabase(remoteDb, () -> {
                try {
                    DatabaseSyncService.DatabaseSyncReport report = service.acceptUpload(sessionId, snapshotStream).report();
                    assertNotNull(report);
                    assertFalse(report.isConfigurationRequested());
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }
            });
        } finally {
            Files.deleteIfExists(sourceArchive);
        }

        withDatabase(remoteDb, () -> {
            assertNotNull(AccountService.getInstance().getByName("source-account"));
            Configuration configuration = ConfigurationService.getInstance().read();
            assertFalse(configuration.isDarkTheme());
            assertEquals("keep-local-player", configuration.getPlayerPath1());
        });

        assertEquals(RemoteSyncStatus.COMPLETED, service.getSessionState(sessionId).status());
        assertTrue(notifier.infoMessages.contains("remoteSyncRemoteCompletedMessage"));
    }

    @Test
    void exportSession_acceptUploadStoresGenericFailureMessage() throws Exception {
        Path remoteDb = tempDir.resolve("remote-failure.db");
        initializeDatabase(remoteDb);

        RecordingNotifier notifier = new RecordingNotifier();
        RemoteSyncSessionService service = new RemoteSyncSessionService(
                snapshotService,
                DatabaseSyncService.getInstance(),
                Clock.systemUTC(),
                (request, decisionConsumer) -> decisionConsumer.accept(true),
                notifier
        );

        String sessionId = withDatabase(remoteDb, () -> service.createSession(
                new RemoteSyncRequest(
                        RemoteSyncDirection.EXPORT_TO_REMOTE,
                        "2222",
                        "machine-a",
                        new RemoteSyncOptions(true, false)
                ),
                "10.0.0.8"
        ).sessionId());

        Path invalidSnapshot = tempDir.resolve("invalid-upload.db");
        Path encryptedInvalidSnapshot = tempDir.resolve("invalid-upload.bin");
        Files.writeString(invalidSnapshot, "not a sqlite database");
        RemoteSyncTransferCipher.encrypt(invalidSnapshot, encryptedInvalidSnapshot, "2222", sessionId);

        Exception failure;
        try (InputStream snapshotStream = Files.newInputStream(encryptedInvalidSnapshot)) {
            failure = assertThrows(
                    Exception.class,
                    () -> withDatabase(remoteDb, () -> service.acceptUpload(sessionId, snapshotStream))
            );
        }

        RemoteSyncSessionState failed = service.getSessionState(sessionId);
        assertEquals(RemoteSyncStatus.FAILED, failed.status());
        assertEquals("Remote database sync failed.", failed.message());
        assertNotEquals(failure.getMessage(), failed.message());
        assertTrue(notifier.infoMessages.contains("remoteSyncRemoteFailedMessage"));
    }

    @Test
    void importSession_preparesDownloadSnapshot_andCompletionCleansUp() throws Exception {
        Path remoteDb = tempDir.resolve("remote-import.db");
        Path targetDb = tempDir.resolve("import-target.db");
        initializeDatabase(remoteDb);
        initializeDatabase(targetDb);

        withDatabase(remoteDb, () -> {
            saveAccount("remote-account");
            saveConfiguration(true, "copy-me");
        });
        withDatabase(targetDb, () -> saveConfiguration(false, "old-player"));

        RecordingNotifier notifier = new RecordingNotifier();
        RemoteSyncSessionService service = new RemoteSyncSessionService(
                snapshotService,
                DatabaseSyncService.getInstance(),
                Clock.systemUTC(),
                (request, decisionConsumer) -> decisionConsumer.accept(true),
                notifier
        );

        String sessionId = withDatabase(remoteDb, () -> service.createSession(
                new RemoteSyncRequest(
                        RemoteSyncDirection.IMPORT_FROM_REMOTE,
                        "5678",
                        "machine-a",
                        new RemoteSyncOptions(true, true)
                ),
                "10.0.0.8"
        ).sessionId());

        RemoteSyncSessionState state = service.getSessionState(sessionId);
        assertEquals(RemoteSyncStatus.READY_FOR_DOWNLOAD, state.status());

        Path encryptedDownload = service.getDownloadSnapshot(sessionId);
        assertTrue(Files.exists(encryptedDownload));
        Path downloadArchive = tempDir.resolve("download.zip");
        RemoteSyncTransferCipher.decrypt(encryptedDownload, downloadArchive, "5678", sessionId);
        Path downloadSnapshot = snapshotService.extractSnapshotDatabase(downloadArchive);

        try {
            withDatabase(targetDb, () -> DatabaseSyncService.getInstance().syncDatabasesWithReport(
                    downloadSnapshot.toString(),
                    targetDb.toString(),
                    true,
                    true,
                    null
            ));
        } finally {
            Files.deleteIfExists(downloadSnapshot);
        }

        withDatabase(targetDb, () -> {
            assertNotNull(AccountService.getInstance().getByName("remote-account"));
            Configuration configuration = ConfigurationService.getInstance().read();
            assertTrue(configuration.isDarkTheme());
            assertEquals("copy-me", configuration.getPlayerPath1());
        });

        service.completeImport(sessionId, true, "done");
        assertEquals(RemoteSyncStatus.COMPLETED, service.getSessionState(sessionId).status());
        assertFalse(Files.exists(encryptedDownload));
        assertTrue(notifier.infoMessages.contains("remoteSyncRemoteCompletedMessage"));
    }

    @Test
    void importApproval_reportsApprovedWhilePreparingDownloadSnapshot() throws Exception {
        Path remoteDb = tempDir.resolve("remote-preparing-import.db");
        initializeDatabase(remoteDb);
        BlockingSnapshotService blockingSnapshotService = new BlockingSnapshotService();
        RemoteSyncSessionService service = new RemoteSyncSessionService(
                blockingSnapshotService,
                DatabaseSyncService.getInstance(),
                Clock.systemUTC(),
                (request, decisionConsumer) -> {
                    Thread thread = new Thread(() -> decisionConsumer.accept(true), "remote-sync-test-approval");
                    thread.setDaemon(true);
                    thread.start();
                },
                new RecordingNotifier()
        );

        String originalPath = com.uiptv.db.SQLConnection.getDatabasePath();
        String sessionId;
        com.uiptv.db.SQLConnection.setDatabasePath(remoteDb.toString());
        try {
            sessionId = service.createSession(
                    new RemoteSyncRequest(
                            RemoteSyncDirection.IMPORT_FROM_REMOTE,
                            "1357",
                            "machine-a",
                            new RemoteSyncOptions(true, true)
                    ),
                    "10.0.0.8"
            ).sessionId();

            assertTrue(blockingSnapshotService.preparationStarted.await(2, TimeUnit.SECONDS));
            RemoteSyncSessionState preparing = service.getSessionState(sessionId);
            assertEquals(RemoteSyncStatus.APPROVED, preparing.status());
            assertEquals("Approved. Preparing snapshot.", preparing.message());

            blockingSnapshotService.continuePreparation.countDown();
            RemoteSyncSessionState ready = waitForStatus(service, sessionId, RemoteSyncStatus.READY_FOR_DOWNLOAD);
            assertEquals("Approved. Snapshot ready.", ready.message());
            Path preparedDownload = service.getDownloadSnapshot(sessionId);
            assertTrue(Files.exists(preparedDownload));
            service.completeImport(sessionId, false, "test cleanup");
            assertFalse(Files.exists(preparedDownload));
        } finally {
            blockingSnapshotService.continuePreparation.countDown();
            com.uiptv.db.SQLConnection.setDatabasePath(originalPath);
        }
    }

    @Test
    void createSession_rejectsInvalidCode_andExpiresPendingRequest() {
        MutableClock clock = new MutableClock(Instant.parse("2026-05-01T10:00:00Z"));
        RemoteSyncSessionService service = new RemoteSyncSessionService(
                snapshotService,
                DatabaseSyncService.getInstance(),
                clock,
                (request, decisionConsumer) -> {
                },
                new RecordingNotifier()
        );

        RemoteSyncRequest invalidRequest = new RemoteSyncRequest(
                RemoteSyncDirection.EXPORT_TO_REMOTE,
                "12",
                "machine-a",
                new RemoteSyncOptions(false, false)
        );
        IllegalArgumentException invalidCode = assertThrows(
                IllegalArgumentException.class,
                () -> service.createSession(invalidRequest, "10.0.0.8")
        );
        assertTrue(invalidCode.getMessage().contains("four digit"));

        String sessionId = service.createSession(
                new RemoteSyncRequest(
                        RemoteSyncDirection.EXPORT_TO_REMOTE,
                        "1234",
                        "machine-a",
                        new RemoteSyncOptions(false, false)
                ),
                "10.0.0.8"
        ).sessionId();
        clock.advanceSeconds(121);
        assertEquals(RemoteSyncStatus.EXPIRED, service.getSessionState(sessionId).status());
    }

    private void initializeDatabase(Path path) throws Exception {
        withDatabase(path, () -> ConfigurationService.getInstance().read());
    }

    private void saveAccount(String accountName) {
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

    private void saveConfiguration(boolean darkTheme, String playerPath1) {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setDarkTheme(darkTheme);
        configuration.setPlayerPath1(playerPath1);
        configuration.setServerPort("8888");
        ConfigurationService.getInstance().save(configuration);
    }

    private <T> T withDatabase(Path databasePath, ThrowingSupplier<T> supplier) throws Exception {
        String originalPath = com.uiptv.db.SQLConnection.getDatabasePath();
        com.uiptv.db.SQLConnection.setDatabasePath(databasePath.toString());
        try {
            return supplier.get();
        } finally {
            com.uiptv.db.SQLConnection.setDatabasePath(originalPath);
        }
    }

    private void withDatabase(Path databasePath, ThrowingRunnable runnable) throws Exception {
        withDatabase(databasePath, () -> {
            runnable.run();
            return null;
        });
    }

    private RemoteSyncSessionState waitForStatus(
            RemoteSyncSessionService service,
            String sessionId,
            RemoteSyncStatus expectedStatus
    ) throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        RemoteSyncSessionState state;
        do {
            state = service.getSessionState(sessionId);
            if (state.status() == expectedStatus) {
                return state;
            }
            Thread.sleep(50);
        } while (Instant.now().isBefore(deadline));
        fail("Timed out waiting for remote sync status " + expectedStatus);
        return state;
    }

    @FunctionalInterface
    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    @FunctionalInterface
    private interface ThrowingSupplier<T> {
        T get() throws Exception;
    }

    private static final class BlockingSnapshotService extends DatabaseSnapshotService {
        private final CountDownLatch preparationStarted = new CountDownLatch(1);
        private final CountDownLatch continuePreparation = new CountDownLatch(1);

        @Override
        public Path createSnapshotArchive(String databasePath) throws IOException, SQLException {
            preparationStarted.countDown();
            try {
                if (!continuePreparation.await(3, TimeUnit.SECONDS)) {
                    throw new IOException("Timed out waiting to continue snapshot preparation.");
                }
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new IOException("Interrupted while preparing snapshot.", ex);
            }
            return super.createSnapshotArchive(databasePath);
        }
    }

    private static final class RecordingNotifier implements RemoteSyncNotifier {
        private final List<String> infoMessages = new ArrayList<>();

        @Override
        public void showInfo(String message) {
            infoMessages.add(message);
        }

        @Override
        public void showError(String message) {
            infoMessages.add(message);
        }
    }

    private static final class MutableClock extends Clock {
        private Instant now;

        private MutableClock(Instant now) {
            this.now = now;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zone) {
            return this;
        }

        @Override
        public Instant instant() {
            return now;
        }

        private void advanceSeconds(long seconds) {
            now = now.plusSeconds(seconds);
        }
    }
}
