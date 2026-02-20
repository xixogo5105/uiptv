package com.uiptv.service;

import com.uiptv.db.DatabasePatchesUtils;
import com.uiptv.db.DatabaseUtils;
import com.uiptv.db.SQLConnection;
import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileWriter;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class M3U8PublicationServiceTest {

    @TempDir
    Path tempDir;

    private File testDbFile;
    private File m3u8File;

    @BeforeEach
    public void setUp() throws Exception {
        testDbFile = tempDir.resolve("test_uiptv_m3u8.db").toFile();
        SQLConnection.setDatabasePath(testDbFile.getAbsolutePath());

        // Initialize DB
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + testDbFile.getAbsolutePath());
             Statement stmt = conn.createStatement()) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.DbTable.values()) {
                stmt.execute(DatabaseUtils.createTableSql(table));
            }
            DatabasePatchesUtils.applyPatches(conn);
        }

        // Create a dummy M3U8 file
        m3u8File = tempDir.resolve("test.m3u8").toFile();
        try (FileWriter writer = new FileWriter(m3u8File)) {
            writer.write("#EXTM3U\n");
            writer.write("#EXTINF:-1,Test Channel\n");
            writer.write("http://test.com/stream.ts\n");
        }
    }

    @AfterEach
    public void tearDown() {
        if (testDbFile != null && testDbFile.exists()) {
            testDbFile.delete();
        }
    }

    @Test
    public void testGetPublishedM3u8() {
        AccountService accountService = AccountService.getInstance();
        
        // Create an account pointing to the local M3U8 file
        Account account = new Account("M3U8Account", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_LOCAL, null, m3u8File.getAbsolutePath(), false);
        accountService.save(account);
        Account savedAccount = accountService.getByName("M3U8Account");

        // Select the account for publication
        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        Set<String> selectedIds = new HashSet<>();
        selectedIds.add(savedAccount.getDbId());
        publicationService.setSelectedAccountIds(selectedIds);

        // Generate the combined M3U8
        String result = publicationService.getPublishedM3u8();

        // Verify content
        assertTrue(result.contains("#EXTM3U"));
        assertTrue(result.contains("#EXTINF:-1,Test Channel"));
        assertTrue(result.contains("http://test.com/stream.ts"));
    }
}
