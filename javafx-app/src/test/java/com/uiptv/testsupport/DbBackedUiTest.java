package com.uiptv.testsupport;

import com.uiptv.db.SQLConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

public abstract class DbBackedUiTest {
    private static final int DELETE_RETRY_ATTEMPTS = 20;
    private static final long DELETE_RETRY_DELAY_MS = 50L;

    static {
        System.setProperty("user.home", System.getProperty("java.io.tmpdir"));
    }

    protected Path tempDir;

    protected File testDbFile;

    @BeforeEach
    protected void setUpDatabase() throws Exception {
        tempDir = Files.createTempDirectory("uiptv-test-");
        testDbFile = tempDir.resolve("uiptv-test.db").toFile();
        SQLConnection.setDatabasePath(testDbFile.getAbsolutePath());
        afterDatabaseSetup();
    }

    @AfterEach
    protected void tearDownDatabase() {
        beforeDatabaseCleanup();
        waitForPendingFxWork();
        SQLConnection.releaseMemory();
        moveSqlConnectionAwayFromTestDirectory();
        deleteSqliteFiles();
        waitForPendingFxWork();
        deleteSqliteFiles();
        deleteTempDirectory();
    }

    protected void afterDatabaseSetup() throws Exception {
    }

    protected void beforeDatabaseCleanup() {
    }

    private void waitForPendingFxWork() {
        try {
            FxTestSupport.waitForFxEvents();
        } catch (Exception ignored) {
            // Some tests initialize only the DB fixture; cleanup should still proceed.
        }
    }

    private void deleteSqliteFiles() {
        if (testDbFile == null) {
            return;
        }
        String databasePath = testDbFile.getAbsolutePath();
        for (Path path : List.of(
                testDbFile.toPath(),
                Path.of(databasePath + "-wal"),
                Path.of(databasePath + "-shm"),
                Path.of(databasePath + "-journal")
        )) {
            deleteIfExistsWithRetry(path);
        }
    }

    private void deleteIfExistsWithRetry(Path path) {
        for (int attempt = 0; attempt < DELETE_RETRY_ATTEMPTS; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException ignored) {
                sleepBeforeRetry();
            }
        }
    }

    private void deleteTempDirectory() {
        if (tempDir == null) {
            return;
        }
        for (int attempt = 0; attempt < DELETE_RETRY_ATTEMPTS; attempt++) {
            try {
                deleteTree(tempDir);
                return;
            } catch (IOException ignored) {
                sleepBeforeRetry();
            }
        }
    }

    private void deleteTree(Path root) throws IOException {
        if (!Files.exists(root)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(root)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.deleteIfExists(path);
            }
        }
    }

    private void moveSqlConnectionAwayFromTestDirectory() {
        Path idleDb = Path.of(System.getProperty("java.io.tmpdir"), "uiptv-test-idle.db");
        try {
            SQLConnection.setDatabasePath(idleDb.toString());
            SQLConnection.releaseMemory();
        } catch (RuntimeException ignored) {
            // Cleanup must not fail the test after assertions have already passed.
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(DELETE_RETRY_DELAY_MS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
