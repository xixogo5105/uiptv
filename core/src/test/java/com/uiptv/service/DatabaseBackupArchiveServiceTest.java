package com.uiptv.service;

import com.uiptv.db.DatabasePatchesUtils;
import com.uiptv.util.SQLiteTableSync;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import java.util.zip.ZipOutputStream;
import java.io.OutputStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertFalse;

class DatabaseBackupArchiveServiceTest {
    @TempDir
    Path tempDir;

    @Test
    void backupIncludesCommittedWalChangesWhileAnOlderReaderIsActive() throws Exception {
        Path source = tempDir.resolve("wal-backup-source.db");
        Path archive = tempDir.resolve("wal-backup.zip");
        Path extracted = tempDir.resolve("wal-extracted.db");
        createReadyDatabase(source);
        seedAccount(source, 1, "original");
        try (Connection reader = DriverManager.getConnection("jdbc:sqlite:" + source);
             Statement statement = reader.createStatement()) {
            try (ResultSet mode = statement.executeQuery("PRAGMA journal_mode=WAL")) {
                assertTrue(mode.next());
            }
            reader.setAutoCommit(false);
            try (ResultSet rows = statement.executeQuery("SELECT COUNT(*) FROM Account")) {
                assertTrue(rows.next()); assertEquals(1, rows.getInt(1));
            }
            seedAccount(source, 2, "committed-in-wal");
            fixedClockService().createBackupArchive(source.toString(), archive.toString());
            fixedClockService().extractBackupDatabase(archive.toString(), extracted.toString());
            assertEquals(2, countAccounts(extracted));
            reader.commit();
        }
    }

    @Test
    void restoreKeepsAnOpenWalConnectionUsable() throws Exception {
        Path source = tempDir.resolve("open-source.db");
        Path target = tempDir.resolve("open-target.db");
        createReadyDatabase(source);
        createReadyDatabase(target);
        seedAccount(source, 1, "restored");
        seedAccount(target, 2, "original");
        try (Connection live = DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement statement = live.createStatement()) {
            try (ResultSet mode = statement.executeQuery("PRAGMA journal_mode=WAL")) {
                assertTrue(mode.next()); assertEquals("wal", mode.getString(1));
            }
            fixedClockService().restoreBackupArchive(source.toString(), target.toString());
            try (ResultSet rows = statement.executeQuery("SELECT accountName FROM Account")) {
                assertTrue(rows.next()); assertEquals("restored", rows.getString(1));
            }
            statement.executeUpdate("UPDATE Account SET accountName='written-after-restore'");
            assertEquals("written-after-restore", firstAccountName(target));
        }
    }

    @Test
    void createsAndroidCompatibleBackupArchive() throws Exception {
        Path database = tempDir.resolve("source.db");
        createReadyDatabase(database);
        seedAccount(database, 1, "source-account");
        Path archive = tempDir.resolve("uiptv-backup.zip");
        DatabaseBackupArchiveService service = fixedClockService();

        DatabaseBackupArchiveService.BackupArchiveReport report = service.createBackupArchive(
                database.toString(),
                archive.toString()
        );

        assertEquals(archive.toAbsolutePath().toString(), report.path());
        assertEquals(Files.size(database), report.databaseBytes());
        assertEquals(Files.size(archive), report.archiveBytes());
        try (ZipFile zip = new ZipFile(archive.toFile())) {
            ZipEntry metadataEntry = zip.getEntry(DatabaseBackupArchiveService.METADATA_ENTRY);
            ZipEntry databaseEntry = zip.getEntry(DatabaseBackupArchiveService.DATABASE_ENTRY);
            assertNotNull(metadataEntry);
            assertNotNull(databaseEntry);

            JSONObject metadata = new JSONObject(new String(
                    zip.getInputStream(metadataEntry).readAllBytes(),
                    StandardCharsets.UTF_8
            ));
            assertEquals(DatabaseBackupArchiveService.FORMAT_VERSION, metadata.getInt("formatVersion"));
            assertEquals(DatabaseBackupArchiveService.DATABASE_ENTRY, metadata.getString("databaseName"));
            assertEquals(DatabasePatchesUtils.currentSchemaVersionCode(), metadata.getInt("schemaVersionCode"));
            assertEquals(DatabasePatchesUtils.currentSchemaVersion(), metadata.getString("schemaVersion"));
            assertEquals(Files.size(database), metadata.getLong("databaseBytes"));
            assertEquals(1_770_000_000L, metadata.getLong("createdAtEpochSeconds"));
        }
    }

    @Test
    void restoresAndroidCompatibleBackupArchiveByReplacingTargetDatabase() throws Exception {
        Path source = tempDir.resolve("source.db");
        Path target = tempDir.resolve("target.db");
        Path archive = tempDir.resolve("uiptv-backup.zip");
        createReadyDatabase(source);
        createReadyDatabase(target);
        seedAccount(source, 1, "source-account");
        seedAccount(target, 2, "old-target-account");
        DatabaseBackupArchiveService service = fixedClockService();
        service.createBackupArchive(source.toString(), archive.toString());

        DatabaseBackupArchiveService.BackupArchiveReport report = service.restoreBackupArchive(
                archive.toString(),
                target.toString()
        );

        assertEquals(archive.toAbsolutePath().toString(), report.path());
        assertEquals(Files.size(target), report.databaseBytes());
        assertTrue(report.archiveBytes() > 0L);
        assertEquals(1, countAccounts(target));
        assertEquals("source-account", firstAccountName(target));
    }

    @Test
    void restoresLegacyRawDatabaseFile() throws Exception {
        Path source = tempDir.resolve("legacy.db");
        Path target = tempDir.resolve("target.db");
        createReadyDatabase(source);
        createReadyDatabase(target);
        seedAccount(source, 1, "legacy-account");

        fixedClockService().restoreBackupArchive(source.toString(), target.toString());

        assertEquals(1, countAccounts(target));
        assertEquals("legacy-account", firstAccountName(target));
    }

    @Test
    void failedRestoreValidationLeavesExistingDatabaseUntouched() throws Exception {
        Path target = tempDir.resolve("preserved-target.db");
        Path invalidArchive = tempDir.resolve("invalid-database.zip");
        createReadyDatabase(target);
        seedAccount(target, 7, "keep-this-account");
        try (OutputStream output = Files.newOutputStream(invalidArchive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(DatabaseBackupArchiveService.DATABASE_ENTRY));
            zip.write("not a SQLite database".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        assertThrows(Exception.class, () -> fixedClockService().restoreBackupArchive(
                invalidArchive.toString(), target.toString()));

        assertEquals(1, countAccounts(target));
        assertEquals("keep-this-account", firstAccountName(target));
    }

    @Test
    void archiveExpansionLimitStopsAndDeletesPartialDatabase() throws Exception {
        Path archive = tempDir.resolve("expanded.zip");
        Path staged = tempDir.resolve("partial.db");
        try (OutputStream output = Files.newOutputStream(archive);
             ZipOutputStream zip = new ZipOutputStream(output)) {
            zip.putNextEntry(new ZipEntry(DatabaseBackupArchiveService.DATABASE_ENTRY));
            zip.write(new byte[16 * 1024]);
            zip.closeEntry();
        }

        assertThrows(Exception.class, () -> fixedClockService().extractDatabaseEntry(archive, staged, 512));
        assertFalse(Files.exists(staged));
    }

    @Test
    void extractsArchiveDatabaseForRemoteSyncMerge() throws Exception {
        Path source = tempDir.resolve("source.db");
        Path archive = tempDir.resolve("uiptv-backup.zip");
        Path extracted = tempDir.resolve("extracted.db");
        createReadyDatabase(source);
        seedAccount(source, 1, "archive-account");
        DatabaseBackupArchiveService service = fixedClockService();
        service.createBackupArchive(source.toString(), archive.toString());

        DatabaseBackupArchiveService.BackupArchiveReport report = service.extractBackupDatabase(
                archive.toString(),
                extracted.toString()
        );

        assertEquals(archive.toAbsolutePath().toString(), report.path());
        assertEquals(Files.size(extracted), report.databaseBytes());
        assertEquals("archive-account", firstAccountName(extracted));
    }

    @Test
    void sharedConstantsMatchAndroidArchiveContract() {
        assertEquals("application/zip", DatabaseBackupArchiveService.MIME_TYPE);
        assertEquals("uiptv-backup.json", DatabaseBackupArchiveService.METADATA_ENTRY);
        assertEquals("uiptv.db", DatabaseBackupArchiveService.DATABASE_ENTRY);
        assertEquals("uiptv-backup-1770000000.zip", DatabaseBackupArchiveService.defaultFileName(1_770_000_000L));
        assertEquals("512 B", DatabaseBackupArchiveService.sizeLabel(512));
        assertEquals("2 KB", DatabaseBackupArchiveService.sizeLabel(2 * 1024));
        assertEquals("3 MB", DatabaseBackupArchiveService.sizeLabel(3 * 1024 * 1024));
    }

    private DatabaseBackupArchiveService fixedClockService() {
        return new DatabaseBackupArchiveService(Clock.fixed(Instant.ofEpochSecond(1_770_000_000L), ZoneOffset.UTC));
    }

    private void createReadyDatabase(Path database) throws Exception {
        SQLiteTableSync.ensureDatabaseReady(database.toString());
    }

    private void seedAccount(Path database, int id, String accountName) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + database);
             PreparedStatement ps = conn.prepareStatement("INSERT OR REPLACE INTO Account (id, accountName) VALUES (?, ?)")) {
            ps.setInt(1, id);
            ps.setString(2, accountName);
            ps.executeUpdate();
        }
    }

    private int countAccounts(Path database) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM Account")) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }

    private String firstAccountName(Path database) throws Exception {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT accountName FROM Account ORDER BY id LIMIT 1")) {
            return rs.next() ? rs.getString(1) : "";
        }
    }
}
