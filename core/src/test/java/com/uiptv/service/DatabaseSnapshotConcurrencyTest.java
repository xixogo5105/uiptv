package com.uiptv.service;

import com.uiptv.db.SQLiteDatabaseSnapshot;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import static org.junit.jupiter.api.Assertions.*;

class DatabaseSnapshotConcurrencyTest {
    @TempDir Path directory;

    @Test
    void incompatibleWalPageSizeFailsWithoutReplacingTheTarget() throws Exception {
        Path source = directory.resolve("large-pages.db");
        Path target = directory.resolve("wal-small-pages.db");
        try (Connection from = open(source); Connection live = open(target)) {
            execute(from, "PRAGMA page_size=8192");
            execute(from, "CREATE TABLE sample(value TEXT)");
            execute(from, "INSERT INTO sample VALUES ('source')");
            execute(live, "PRAGMA page_size=4096");
            execute(live, "PRAGMA journal_mode=WAL");
            execute(live, "CREATE TABLE sample(value TEXT)");
            execute(live, "INSERT INTO sample VALUES ('original')");
            assertThrows(SQLException.class, () -> SQLiteDatabaseSnapshot.restore(source, target));
            assertEquals("original", value(live));
            SQLiteDatabaseSnapshot.requireIntegrity(target);
        }
    }

    @Test
    void activeWalReaderKeepsItsSnapshotUntilCommit() throws Exception {
        Path source = directory.resolve("reader-source.db");
        Path target = directory.resolve("reader-target.db");
        try (Connection from = open(source); Connection reader = open(target)) {
            execute(from, "CREATE TABLE sample(value TEXT)");
            execute(from, "INSERT INTO sample VALUES ('source')");
            execute(reader, "PRAGMA journal_mode=WAL");
            execute(reader, "CREATE TABLE sample(value TEXT)");
            execute(reader, "INSERT INTO sample VALUES ('original')");
            reader.setAutoCommit(false);
            assertEquals("original", value(reader));
            SQLiteDatabaseSnapshot.restore(source, target);
            assertEquals("original", value(reader));
            reader.commit();
            assertEquals("source", value(reader));
            reader.commit();
        }
    }

    @Test
    void existingConnectionsSeeRestoredDataAndCanStillWrite() throws Exception {
        for (String mode : new String[]{"WAL", "DELETE"}) {
            Path source = directory.resolve(mode + "-source.db");
            Path target = directory.resolve(mode + "-target.db");
            try (Connection sourceConnection = open(source); Connection live = open(target)) {
                execute(sourceConnection, "CREATE TABLE sample(value TEXT)");
                execute(sourceConnection, "INSERT INTO sample VALUES ('source')");
                execute(live, "PRAGMA journal_mode=" + mode);
                execute(live, "CREATE TABLE sample(value TEXT)");
                execute(live, "INSERT INTO sample VALUES ('original')");
                SQLiteDatabaseSnapshot.restore(source, target);
                assertEquals("source", value(live));
                execute(live, "UPDATE sample SET value='committed-after-restore'");
                try (Connection reopened = open(target)) { assertEquals("committed-after-restore", value(reopened)); }
                SQLiteDatabaseSnapshot.requireIntegrity(target);
            }
        }
    }

    @Test
    void activeWriterRejectsRestoreWithoutLosingCommittedData() throws Exception {
        Path source = directory.resolve("source.db");
        Path target = directory.resolve("target.db");
        try (Connection from = open(source); Connection live = open(target)) {
            execute(from, "CREATE TABLE sample(value TEXT)");
            execute(from, "INSERT INTO sample VALUES ('source')");
            execute(live, "PRAGMA journal_mode=WAL");
            execute(live, "CREATE TABLE sample(value TEXT)");
            execute(live, "INSERT INTO sample VALUES ('original')");
            live.setAutoCommit(false);
            execute(live, "UPDATE sample SET value='writer'");
            assertThrows(SQLException.class, () -> SQLiteDatabaseSnapshot.restore(source, target));
            live.commit();
            try (Connection reopened = open(target)) { assertEquals("writer", value(reopened)); }
            SQLiteDatabaseSnapshot.requireIntegrity(target);
        }
    }

    @Test
    void invalidSnapshotLeavesOriginalUsable() throws Exception {
        Path source = directory.resolve("invalid.db");
        Path target = directory.resolve("target.db");
        java.nio.file.Files.writeString(source, "invalid snapshot");
        try (Connection live = open(target)) {
            execute(live, "CREATE TABLE sample(value TEXT)");
            execute(live, "INSERT INTO sample VALUES ('original')");
            assertThrows(SQLException.class, () -> SQLiteDatabaseSnapshot.restore(source, target));
            assertEquals("original", value(live));
        }
    }

    private Connection open(Path path) throws SQLException { return DriverManager.getConnection("jdbc:sqlite:" + path); }
    private void execute(Connection connection, String sql) throws SQLException {
        try (var statement = connection.createStatement()) { statement.execute(sql); }
    }
    private String value(Connection connection) throws SQLException {
        try (var statement = connection.createStatement(); var result = statement.executeQuery("SELECT value FROM sample")) {
            assertTrue(result.next()); return result.getString(1);
        }
    }
}
