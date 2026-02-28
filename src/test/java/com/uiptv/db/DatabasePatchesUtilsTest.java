package com.uiptv.db;

import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class DatabasePatchesUtilsTest extends DbBackedTest {

    @Test
    void initializesSchemaMigrationsFromFiles() throws Exception {
        try (Connection conn = SQLConnection.connect()) {
            assertTrue(tableExists(conn, "schema_migrations"));
            assertEquals(expectedMigrationCount(), countRows(conn, "schema_migrations", "status='success'"));
        }
    }

    @Test
    void rerunsMissingMigrationsAndRepairsDroppedSchemaPieces() throws Exception {
        try (Connection conn = SQLConnection.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("ALTER TABLE Configuration DROP COLUMN embeddedPlayer");
            st.executeUpdate("ALTER TABLE Account DROP COLUMN pinToTop");
            st.executeUpdate("DROP TABLE IF EXISTS BookmarkOrder");
            st.executeUpdate("DELETE FROM schema_migrations WHERE name IN "
                    + "('0115_add_configuration_embedded_player.sql',"
                    + "'0113_add_account_pin_to_top.sql',"
                    + "'0141_create_bookmark_order_table.sql',"
                    + "'0142_seed_bookmark_order_table.sql')");

            DatabasePatchesUtils.applyPatches(conn);

            assertTrue(columnExists(conn, "Configuration", "embeddedPlayer"));
            assertTrue(columnExists(conn, "Account", "pinToTop"));
            assertTrue(tableExists(conn, "BookmarkOrder"));
            assertEquals("success", migrationStatus(conn, "0115_add_configuration_embedded_player.sql"));
            assertEquals("success", migrationStatus(conn, "0113_add_account_pin_to_top.sql"));
            assertEquals("success", migrationStatus(conn, "0141_create_bookmark_order_table.sql"));
        }
    }

    @Test
    void keepsGoingWhenOneMigrationFailsAndRunsLaterOnes() throws Exception {
        try (Connection conn = SQLConnection.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS Bookmark");
            st.executeUpdate("DELETE FROM schema_migrations WHERE name IN "
                    + "('0142_seed_bookmark_order_table.sql',"
                    + "'0159_add_configuration_cache_expiry_days.sql')");

            DatabasePatchesUtils.applyPatches(conn);

            assertEquals("failed", migrationStatus(conn, "0142_seed_bookmark_order_table.sql"));
            assertEquals("success", migrationStatus(conn, "0159_add_configuration_cache_expiry_days.sql"));
        }
    }

    @Test
    void initRetriesWhenDatabaseIsTemporarilyLocked() throws Exception {
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Thread initThread;
        try (Connection locker = SQLConnection.connect(); Statement st = locker.createStatement()) {
            st.execute("BEGIN EXCLUSIVE");

            initThread = new Thread(() -> {
                try {
                    SQLConnection.init();
                } catch (Throwable throwable) {
                    failure.set(throwable);
                }
            });
            initThread.start();

            Thread.sleep(600);
            st.execute("COMMIT");
        }
        initThread.join();
        assertNull(failure.get(), "init() should recover after temporary lock release");
    }

    @Test
    void seedsBookmarkOrderForLegacyUsersWhoHaveBookmarksButNoOrderRows() throws Exception {
        try (Connection conn = SQLConnection.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS BookmarkOrder");
            st.executeUpdate("DROP TABLE IF EXISTS Bookmark");
            st.executeUpdate("CREATE TABLE Bookmark ("
                    + "id INTEGER PRIMARY KEY,"
                    + "accountName TEXT,"
                    + "categoryTitle TEXT,"
                    + "channelId TEXT,"
                    + "channelName TEXT,"
                    + "cmd TEXT,"
                    + "serverPortalUrl TEXT,"
                    + "categoryId TEXT)");
            st.executeUpdate("INSERT INTO Bookmark(id, accountName, categoryTitle, channelId, channelName, cmd, serverPortalUrl, categoryId) "
                    + "VALUES (101, 'acc', 'News', 'c1', 'Bloomberg', 'http://x', 'http://p', 'news-cat')");
            st.executeUpdate("INSERT INTO Bookmark(id, accountName, categoryTitle, channelId, channelName, cmd, serverPortalUrl, categoryId) "
                    + "VALUES (102, 'acc', 'Sports', 'c2', 'ESPN', 'http://y', 'http://p', 'sports-cat')");
            st.executeUpdate("DELETE FROM schema_migrations WHERE name IN "
                    + "('0141_create_bookmark_order_table.sql', '0142_seed_bookmark_order_table.sql')");

            DatabasePatchesUtils.applyPatches(conn);

            assertTrue(tableExists(conn, "BookmarkOrder"));
            assertEquals(2, countRows(conn, "BookmarkOrder", null));
            assertEquals(1, countRows(conn, "BookmarkOrder", "bookmark_db_id='101' AND category_id='news-cat'"));
            assertEquals(1, countRows(conn, "BookmarkOrder", "bookmark_db_id='102' AND category_id='sports-cat'"));
            assertEquals("success", migrationStatus(conn, "0141_create_bookmark_order_table.sql"));
            assertEquals("success", migrationStatus(conn, "0142_seed_bookmark_order_table.sql"));
        }
    }

    @Test
    void failedMigrationIsRetriedOnNextRunAndErrorMessageIsClearedAfterSuccess() throws Exception {
        try (Connection conn = SQLConnection.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("DROP TABLE IF EXISTS Bookmark");
            st.executeUpdate("DELETE FROM schema_migrations WHERE name='0142_seed_bookmark_order_table.sql'");

            DatabasePatchesUtils.applyPatches(conn);
            assertEquals("failed", migrationStatus(conn, "0142_seed_bookmark_order_table.sql"));
            assertNotNull(migrationErrorMessage(conn, "0142_seed_bookmark_order_table.sql"));

            st.executeUpdate("CREATE TABLE Bookmark (id INTEGER PRIMARY KEY, categoryId TEXT)");
            DatabasePatchesUtils.applyPatches(conn);

            assertEquals("success", migrationStatus(conn, "0142_seed_bookmark_order_table.sql"));
            assertNull(migrationErrorMessage(conn, "0142_seed_bookmark_order_table.sql"));
        }
    }

    @Test
    void successfulMigrationWithChecksumDriftIsReappliedAndRecordIsRepaired() throws Exception {
        try (Connection conn = SQLConnection.connect(); Statement st = conn.createStatement()) {
            st.executeUpdate("UPDATE schema_migrations "
                    + "SET checksum='tampered-checksum', status='success', applied_at=1, error_message='x' "
                    + "WHERE name='0115_add_configuration_embedded_player.sql'");
            st.executeUpdate("ALTER TABLE Configuration DROP COLUMN embeddedPlayer");

            DatabasePatchesUtils.applyPatches(conn);

            assertTrue(columnExists(conn, "Configuration", "embeddedPlayer"));
            assertEquals("success", migrationStatus(conn, "0115_add_configuration_embedded_player.sql"));
            assertNotEquals("tampered-checksum", migrationChecksum(conn, "0115_add_configuration_embedded_player.sql"));
            assertTrue(migrationAppliedAt(conn, "0115_add_configuration_embedded_player.sql") > 1);
            assertNull(migrationErrorMessage(conn, "0115_add_configuration_embedded_player.sql"));
        }
    }

    @Test
    void reapplyingPatchesOnHealthyDatabaseIsIdempotent() throws Exception {
        try (Connection conn = SQLConnection.connect()) {
            long beforeAppliedAt = migrationAppliedAt(conn, "0159_add_configuration_cache_expiry_days.sql");
            int beforeCount = countRows(conn, "schema_migrations", "status='success'");

            DatabasePatchesUtils.applyPatches(conn);

            long afterAppliedAt = migrationAppliedAt(conn, "0159_add_configuration_cache_expiry_days.sql");
            int afterCount = countRows(conn, "schema_migrations", "status='success'");
            assertEquals(beforeAppliedAt, afterAppliedAt);
            assertEquals(beforeCount, afterCount);
        }
    }

    private static boolean tableExists(Connection conn, String tableName) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND name='" + tableName + "'")) {
            return rs.next();
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("PRAGMA table_info(" + tableName + ")")) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String migrationStatus(Connection conn, String migrationName) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT status FROM schema_migrations WHERE name='" + migrationName + "'")) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static String migrationChecksum(Connection conn, String migrationName) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT checksum FROM schema_migrations WHERE name='" + migrationName + "'")) {
            return rs.next() ? rs.getString(1) : "";
        }
    }

    private static long migrationAppliedAt(Connection conn, String migrationName) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT applied_at FROM schema_migrations WHERE name='" + migrationName + "'")) {
            return rs.next() ? rs.getLong(1) : 0L;
        }
    }

    private static String migrationErrorMessage(Connection conn, String migrationName) throws Exception {
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("SELECT error_message FROM schema_migrations WHERE name='" + migrationName + "'")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }

    private static int countRows(Connection conn, String table, String whereClause) throws Exception {
        String sql = "SELECT COUNT(*) FROM " + table + (whereClause == null || whereClause.isBlank() ? "" : " WHERE " + whereClause);
        try (Statement st = conn.createStatement(); ResultSet rs = st.executeQuery(sql)) {
            rs.next();
            return rs.getInt(1);
        }
    }

    private static int expectedMigrationCount() throws Exception {
        try (InputStream in = DatabasePatchesUtilsTest.class.getClassLoader().getResourceAsStream("db/migrations/migrations.txt")) {
            if (in == null) {
                throw new IllegalStateException("Missing migrations list");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                List<String> lines = reader.lines().map(String::trim)
                        .filter(s -> !s.isEmpty())
                        .filter(s -> !s.startsWith("#"))
                        .collect(Collectors.toList());
                return lines.size();
            }
        }
    }
}
