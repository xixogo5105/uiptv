package com.uiptv.service.remotesync;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

public class DatabaseSnapshotService {
    public Path createSnapshot(String databasePath) throws IOException, SQLException {
        Objects.requireNonNull(databasePath, "databasePath");
        Path snapshotPath = SecureTempFileSupport.createTempFile("uiptv-remote-sync-", ".db");
        try {
            runVacuumInto(databasePath, snapshotPath);
            return snapshotPath;
        } catch (SQLException ex) {
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
