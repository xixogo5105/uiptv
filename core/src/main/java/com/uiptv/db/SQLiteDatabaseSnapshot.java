package com.uiptv.db;

import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

/** Uses SQLite's transaction and file locks, including for connections opened outside UIPTV. */
public final class SQLiteDatabaseSnapshot {
    private SQLiteDatabaseSnapshot() { }

    public static void requireIntegrity(Path database) throws SQLException {
        if (!Files.isRegularFile(database)) throw new SQLException("Snapshot database does not exist");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("PRAGMA integrity_check")) {
            if (!result.next() || !"ok".equalsIgnoreCase(result.getString(1))) {
                throw new SQLException("Snapshot database failed integrity check");
            }
        }
    }

    public static void create(Path source, Path snapshot) throws SQLException {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source)) {
            boolean[] completed = {false};
            int result = connection.unwrap(org.sqlite.SQLiteConnection.class).getDatabase().backup(
                    "main", snapshot.toAbsolutePath().toString(),
                    (remaining, total) -> completed[0] = remaining == 0, 100, 3, -1);
            requireCompleted(result, completed[0]);
        }
    }

    public static void restore(Path snapshot, Path target) throws SQLException {
        requireIntegrity(snapshot);
        // sqlite3_backup owns the write transaction and coordinates all connections,
        // including external readers/writers. Never unlink the database or WAL/SHM.
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement statement = connection.createStatement()) {
            statement.execute("PRAGMA busy_timeout = 1000");
            boolean[] completed = {false};
            int result = connection.unwrap(org.sqlite.SQLiteConnection.class).getDatabase().restore(
                    "main", snapshot.toAbsolutePath().toString(),
                    (remaining, total) -> completed[0] = remaining == 0, 100, 0, -1);
            // This driver can return SQLITE_OK from the source even when the
            // destination is busy. Require a completed copy, not just that code.
            requireCompleted(result, completed[0]);
        }
    }

    private static void requireCompleted(int result, boolean completed) throws SQLException {
        if (result != 0 || !completed) {
            throw new SQLException("SQLite snapshot copy did not complete; target transaction was rolled back", null, result);
        }
    }
}
