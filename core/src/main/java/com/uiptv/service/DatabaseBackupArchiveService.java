package com.uiptv.service;

import com.uiptv.db.DatabasePatchesUtils;
import com.uiptv.db.SQLConnection;
import com.uiptv.service.remotesync.SecureTempFileSupport;
import org.json.JSONObject;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.StandardCopyOption;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Clock;
import java.util.Locale;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class DatabaseBackupArchiveService {
    public static final String MIME_TYPE = "application/zip";
    public static final String METADATA_ENTRY = "uiptv-backup.json";
    public static final String DATABASE_ENTRY = "uiptv.db";
    public static final int FORMAT_VERSION = 1;
    /** Maximum accepted expanded archive size, including the database and metadata. */
    public static final long MAX_ARCHIVE_EXPANDED_BYTES = 1024L * 1024L * 1024L;
    private static final int MAX_ARCHIVE_ENTRIES = 128;
    private static final String SQLITE_PREFIX = "jdbc:sqlite:";
    private static final String SQLITE_HEADER = "SQLite format 3";

    private final Clock clock;

    private DatabaseBackupArchiveService() {
        this(Clock.systemDefaultZone());
    }

    DatabaseBackupArchiveService(Clock clock) {
        this.clock = clock;
    }

    private static class SingletonHelper {
        private static final DatabaseBackupArchiveService INSTANCE = new DatabaseBackupArchiveService();
    }

    public static DatabaseBackupArchiveService getInstance() {
        return SingletonHelper.INSTANCE;
    }

    public static String defaultFileName(long epochSeconds) {
        return "uiptv-backup-" + epochSeconds + ".zip";
    }

    public static String sizeLabel(long bytes) {
        if (bytes < 1024L) {
            return bytes + " B";
        }
        if (bytes < 1024L * 1024L) {
            return (bytes / 1024L) + " KB";
        }
        return (bytes / (1024L * 1024L)) + " MB";
    }

    public BackupArchiveReport createBackupArchive(String sourceDatabasePath, String archivePath) throws IOException, SQLException {
        Path sourceDatabase = Path.of(sourceDatabasePath);
        Path destinationArchive = Path.of(archivePath);
        requireExistingFile(sourceDatabase, "Database file does not exist.");
        checkpointDatabase(sourceDatabase);
        createParentDirectories(destinationArchive);

        long databaseBytes = Files.size(sourceDatabase);
        try (OutputStream output = Files.newOutputStream(destinationArchive);
             ZipOutputStream zip = new ZipOutputStream(new BufferedOutputStream(output))) {
            writeMetadata(zip, databaseBytes);
            zip.putNextEntry(new ZipEntry(DATABASE_ENTRY));
            Files.copy(sourceDatabase, zip);
            zip.closeEntry();
        }

        return new BackupArchiveReport(
                destinationArchive.toAbsolutePath().toString(),
                databaseBytes,
                Files.size(destinationArchive)
        );
    }

    public BackupArchiveReport restoreBackupArchive(String backupPath, String targetDatabasePath) throws IOException, SQLException {
        Path sourceBackup = Path.of(backupPath);
        Path targetDatabase = Path.of(targetDatabasePath);
        requireExistingFile(sourceBackup, "Backup file does not exist.");
        Path restoreDir = SecureTempFileSupport.createTempDirectory("uiptv-db-restore-");
        try {
            Path stagedDatabase = restoreDir.resolve(DATABASE_ENTRY);
            stageRestoreDatabase(sourceBackup, stagedDatabase);
            replaceLiveDatabase(stagedDatabase, targetDatabase);
            return new BackupArchiveReport(
                    sourceBackup.toAbsolutePath().toString(),
                    Files.size(targetDatabase),
                    Files.size(sourceBackup)
            );
        } finally {
            deleteRecursively(restoreDir);
        }
    }

    public BackupArchiveReport extractBackupDatabase(String backupPath, String targetDatabasePath) throws IOException, SQLException {
        Path sourceBackup = Path.of(backupPath);
        Path targetDatabase = Path.of(targetDatabasePath);
        requireExistingFile(sourceBackup, "Backup file does not exist.");
        createParentDirectories(targetDatabase);
        stageRestoreDatabase(sourceBackup, targetDatabase);
        migrateAndValidate(targetDatabase);
        return new BackupArchiveReport(
                sourceBackup.toAbsolutePath().toString(),
                Files.size(targetDatabase),
                Files.size(sourceBackup)
        );
    }

    private void writeMetadata(ZipOutputStream zip, long databaseBytes) throws IOException {
        JSONObject metadata = new JSONObject()
                .put("formatVersion", FORMAT_VERSION)
                .put("databaseName", DATABASE_ENTRY)
                .put("schemaVersionCode", DatabasePatchesUtils.currentSchemaVersionCode())
                .put("schemaVersion", DatabasePatchesUtils.currentSchemaVersion())
                .put("databaseBytes", databaseBytes)
                .put("createdAtEpochSeconds", clock.instant().getEpochSecond());
        zip.putNextEntry(new ZipEntry(METADATA_ENTRY));
        zip.write(metadata.toString(2).getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
    }

    private void stageRestoreDatabase(Path sourceBackup, Path stagedDatabase) throws IOException {
        if (isZipArchive(sourceBackup)) {
            extractDatabaseEntry(sourceBackup, stagedDatabase);
            return;
        }
        if (!isSqliteDatabase(sourceBackup)) {
            throw new IOException("Backup must be a UIPTV backup archive or SQLite database file.");
        }
        Files.copy(sourceBackup, stagedDatabase, StandardCopyOption.REPLACE_EXISTING);
    }

    private void extractDatabaseEntry(Path sourceBackup, Path stagedDatabase) throws IOException {
        extractDatabaseEntry(sourceBackup, stagedDatabase, MAX_ARCHIVE_EXPANDED_BYTES);
    }

    void extractDatabaseEntry(Path sourceBackup, Path stagedDatabase, long maxExpandedBytes) throws IOException {
        boolean databaseFound = false;
        int entryCount = 0;
        long expandedBytes = 0;
        try (InputStream input = Files.newInputStream(sourceBackup);
             ZipInputStream zip = new ZipInputStream(new BufferedInputStream(input))) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                if (++entryCount > MAX_ARCHIVE_ENTRIES) {
                    throw new IOException("Backup archive contains too many entries.");
                }
                boolean databaseEntry = !entry.isDirectory() && DATABASE_ENTRY.equals(entry.getName());
                if (databaseEntry && databaseFound) {
                    throw new IOException("Backup archive contains duplicate database entries.");
                }
                try (OutputStream output = databaseEntry ? Files.newOutputStream(stagedDatabase) : OutputStream.nullOutputStream()) {
                    byte[] buffer = new byte[16 * 1024];
                    int read;
                    while ((read = zip.read(buffer)) != -1) {
                        expandedBytes += read;
                        if (expandedBytes > maxExpandedBytes) {
                            throw new IOException("Backup archive expands beyond the supported size limit.");
                        }
                        output.write(buffer, 0, read);
                    }
                }
                if (databaseEntry) databaseFound = true;
                zip.closeEntry();
            }
        } catch (IOException | RuntimeException failure) {
            Files.deleteIfExists(stagedDatabase);
            throw failure;
        }
        if (!databaseFound) {
            throw new IOException("Backup does not contain " + DATABASE_ENTRY + ".");
        }
        if (Files.size(stagedDatabase) == 0L) {
            throw new IOException("Backup database is empty.");
        }
    }

    private void migrateAndValidate(Path databaseFile) throws SQLException {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + databaseFile)) {
            requireIntegrity(conn);
            DatabasePatchesUtils.applyPatches(conn);
            requireIntegrity(conn);
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("PRAGMA user_version = " + DatabasePatchesUtils.currentSchemaVersionCode());
            }
        }
    }

    private void replaceLiveDatabase(Path stagedDatabase, Path targetDatabase) throws IOException, SQLException {
        createParentDirectories(targetDatabase);
        if (Files.exists(targetDatabase)) {
            checkpointDatabase(targetDatabase);
        }
        Path absoluteTarget = targetDatabase.toAbsolutePath();
        Path replacement = Files.createTempFile(absoluteTarget.getParent(), ".uiptv-restore-", ".db");
        try {
            Files.copy(stagedDatabase, replacement, StandardCopyOption.REPLACE_EXISTING);
            migrateAndValidate(replacement);
            deleteDatabaseSidecars(targetDatabase);
            try {
                Files.move(replacement, absoluteTarget, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException ex) {
                throw new IOException("The database filesystem does not support a safe atomic restore.", ex);
            }
        } finally {
            Files.deleteIfExists(replacement);
        }
    }

    private void checkpointDatabase(Path databaseFile) throws SQLException {
        try (Connection conn = DriverManager.getConnection(SQLITE_PREFIX + databaseFile);
             Statement stmt = conn.createStatement()) {
            stmt.execute("PRAGMA wal_checkpoint(TRUNCATE)");
        }
    }

    private void requireIntegrity(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("PRAGMA integrity_check")) {
            if (!rs.next() || !"ok".equalsIgnoreCase(rs.getString(1))) {
                throw new SQLException("Restored database failed integrity check.");
            }
        }
    }

    private void requireExistingFile(Path path, String message) throws IOException {
        if (!Files.isRegularFile(path)) {
            throw new IOException(message);
        }
    }

    private void createParentDirectories(Path path) throws IOException {
        Path parent = path.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }

    private boolean isZipArchive(Path path) throws IOException {
        byte[] header = readHeader(path, 4);
        return header.length >= 4
                && header[0] == 'P'
                && header[1] == 'K'
                && (header[2] == 3 || header[2] == 5 || header[2] == 7)
                && (header[3] == 4 || header[3] == 6 || header[3] == 8);
    }

    private boolean isSqliteDatabase(Path path) throws IOException {
        String header = new String(readHeader(path, 16), StandardCharsets.US_ASCII);
        return header.toLowerCase(Locale.ROOT).startsWith(SQLITE_HEADER.toLowerCase(Locale.ROOT));
    }

    private byte[] readHeader(Path path, int size) throws IOException {
        try (InputStream input = Files.newInputStream(path)) {
            return input.readNBytes(size);
        }
    }

    private void deleteDatabaseSidecars(Path databaseFile) throws IOException {
        Files.deleteIfExists(Path.of(databaseFile.toString() + "-wal"));
        Files.deleteIfExists(Path.of(databaseFile.toString() + "-shm"));
    }

    private void deleteRecursively(Path path) throws IOException {
        if (!Files.exists(path)) {
            return;
        }
        try (var files = Files.walk(path)) {
            for (Path file : files.sorted((left, right) -> right.compareTo(left)).toList()) {
                Files.deleteIfExists(file);
            }
        }
    }

    public record BackupArchiveReport(String path, long databaseBytes, long archiveBytes) {
    }
}
