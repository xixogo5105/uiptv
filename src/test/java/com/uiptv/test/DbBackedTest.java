package com.uiptv.test;

import com.uiptv.db.SQLConnection;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

public abstract class DbBackedTest {

    static {
        // Keep SQLConnection static initialization in a writable location for tests.
        System.setProperty("user.home", System.getProperty("java.io.tmpdir"));
    }

    @TempDir
    public Path tempDir;

    protected File testDbFile;

    @BeforeEach
    void setUpDatabase() throws Exception {
        testDbFile = tempDir.resolve("uiptv-test.db").toFile();
        SQLConnection.setDatabasePath(testDbFile.getAbsolutePath());
        afterDatabaseSetup();
    }

    @AfterEach
    void tearDownDatabase() {
        beforeDatabaseCleanup();
        if (testDbFile != null && testDbFile.exists()) {
            testDbFile.delete();
        }
    }

    protected void afterDatabaseSetup() throws Exception {
    }

    protected void beforeDatabaseCleanup() {
    }

}
