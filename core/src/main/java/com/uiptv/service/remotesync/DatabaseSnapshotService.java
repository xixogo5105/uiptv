package com.uiptv.service.remotesync;

import com.uiptv.service.DatabaseBackupArchiveService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public class DatabaseSnapshotService {
    private static final String REMOTE_SYNC_TEMP_PREFIX = "uiptv-remote-sync-";

    private final DatabaseBackupArchiveService backupArchiveService;

    public DatabaseSnapshotService() {
        this(DatabaseBackupArchiveService.getInstance());
    }

    DatabaseSnapshotService(DatabaseBackupArchiveService backupArchiveService) {
        this.backupArchiveService = Objects.requireNonNull(backupArchiveService, "backupArchiveService");
    }

    public Path createSnapshot(String databasePath) throws IOException, SQLException {
        Objects.requireNonNull(databasePath, "databasePath");
        Path snapshotPath = SecureTempFileSupport.createTempFile(REMOTE_SYNC_TEMP_PREFIX, ".db");
        try {
            runVacuumInto(databasePath, snapshotPath);
            return snapshotPath;
        } catch (SQLException ex) {
            Files.deleteIfExists(snapshotPath);
            throw ex;
        }
    }

    public Path createSnapshotArchive(String databasePath) throws IOException, SQLException {
        Path snapshotPath = createSnapshot(databasePath);
        Path archivePath = SecureTempFileSupport.createTempFile(REMOTE_SYNC_TEMP_PREFIX, ".zip");
        try {
            backupArchiveService.createBackupArchive(snapshotPath.toString(), archivePath.toString());
            return archivePath;
        } catch (IOException | SQLException ex) {
            Files.deleteIfExists(archivePath);
            throw ex;
        } finally {
            Files.deleteIfExists(snapshotPath);
        }
    }

    public Path extractSnapshotDatabase(Path transferPath) throws IOException, SQLException {
        Objects.requireNonNull(transferPath, "transferPath");
        Path snapshotPath = SecureTempFileSupport.createTempFile(REMOTE_SYNC_TEMP_PREFIX, ".db");
        try {
            backupArchiveService.extractBackupDatabase(transferPath.toString(), snapshotPath.toString());
            return snapshotPath;
        } catch (IOException | SQLException ex) {
            Files.deleteIfExists(snapshotPath);
            throw ex;
        }
    }

    private void runVacuumInto(String databasePath, Path snapshotPath) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + databasePath);
             Statement statement = connection.createStatement()) {
            statement.execute("VACUUM INTO '" + escapeSqlLiteral(snapshotPath.toAbsolutePath().toString()) + "'");
        }
    }

    private String escapeSqlLiteral(String value) {
        return value.replace("'", "''");
    }
}
