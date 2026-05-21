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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DatabaseBackupArchiveServiceTest {
    @TempDir
    Path tempDir;

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
