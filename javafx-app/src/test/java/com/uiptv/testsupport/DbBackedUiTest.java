package com.uiptv.testsupport;

import com.uiptv.db.SQLConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public abstract class DbBackedUiTest {

    static {
        System.setProperty("user.home", System.getProperty("java.io.tmpdir"));
    }

    @TempDir
    protected Path tempDir;

    protected File testDbFile;

    @BeforeEach
    protected void setUpDatabase() throws Exception {
        testDbFile = tempDir.resolve("uiptv-test.db").toFile();
        SQLConnection.setDatabasePath(testDbFile.getAbsolutePath());
        afterDatabaseSetup();
    }

    @AfterEach
    protected void tearDownDatabase() {
        beforeDatabaseCleanup();
        waitForPendingFxWork();
        SQLConnection.releaseMemory();
        deleteSqliteFiles();
        waitForPendingFxWork();
        deleteSqliteFiles();
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
        for (int attempt = 0; attempt < 5; attempt++) {
            try {
                Files.deleteIfExists(path);
                return;
            } catch (IOException ignored) {
                sleepBeforeRetry();
            }
        }
    }

    private void sleepBeforeRetry() {
        try {
            Thread.sleep(25);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
