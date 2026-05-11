package com.uiptv.ui;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.nio.file.Path;

abstract class DbBackedUiTest {

    static {
        System.setProperty("user.home", System.getProperty("java.io.tmpdir"));
    }

    @TempDir
    protected Path tempDir;

    protected File testDbFile;

    @BeforeEach
    void setUpDatabase() throws Exception {
        testDbFile = tempDir.resolve("uiptv-test.db").toFile();
        SqlConnectionUiTestSupport.useDatabasePath(testDbFile.getAbsolutePath());
        afterDatabaseSetup();
    }

    @AfterEach
    void tearDownDatabase() {
        beforeDatabaseCleanup();
        SqlConnectionUiTestSupport.shutdown();
        SqlConnectionUiTestSupport.restoreConfiguredPath();
        if (testDbFile != null && testDbFile.exists()) {
            testDbFile.delete();
        }
    }

    protected void afterDatabaseSetup() throws Exception {
    }

    protected void beforeDatabaseCleanup() {
    }
}
