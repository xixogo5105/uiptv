package com.uiptv.service;

import com.uiptv.db.SqlConnectionTestSupport;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

public abstract class DbBackedTest {

    static {
        // Keep database initialization in a writable location for tests.
        System.setProperty("user.home", System.getProperty("java.io.tmpdir"));
    }

    @TempDir
    public Path tempDir;

    protected File testDbFile;

    @BeforeEach
    public void setUpDatabase() throws Exception {
        testDbFile = tempDir.resolve("uiptv-test.db").toFile();
        SqlConnectionTestSupport.useDatabasePath(testDbFile.getAbsolutePath());
        afterDatabaseSetup();
    }

    @AfterEach
    public void tearDownDatabase() {
        beforeDatabaseCleanup();
        SqlConnectionTestSupport.shutdown();
        if (testDbFile != null && testDbFile.exists()) {
            testDbFile.delete();
        }
    }

    protected void afterDatabaseSetup() throws Exception {
    }

    protected void beforeDatabaseCleanup() {
    }
}
