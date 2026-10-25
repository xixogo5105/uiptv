package com.uiptv.util;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;

import java.nio.file.*;
import java.sql.*;
import java.security.MessageDigest;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Explicit offline measurement against a DISPOSABLE fixture, never the installed application DB.
 * Creates two new files using the fixture's exact schema and rows. The only difference is that
 * provider channel metadata goes through the production ingestion helper before INSERT.
 * No migrations, UPDATEs, cache clears, VACUUM, provider requests, or application DB connections.
 */
@EnabledIfSystemProperty(named = "uiptv.metadata.fixture", matches = ".+")
class ChannelMetadataSizeProbeTest {
    private record Schema(String type, String name, String sql) { }
    private record Stats(long rows, long characters, long cmds) { }
    private final Map<String, String> accountTypes = new HashMap<>();
    private final Map<String, String> categoryAccounts = new HashMap<>();

    @Test void rebuildSameCatalogueWithCompactImportMetadata() throws Exception {
        Path fixture = Path.of(System.getProperty("uiptv.metadata.fixture")).toRealPath();
        Path output = Path.of(System.getProperty("uiptv.metadata.output")).toAbsolutePath();
        assertTrue(fixture.startsWith(Path.of("/private/tmp")), "Use a disposable fixture in /private/tmp");
        Files.createDirectories(output);
        Path raw = output.resolve("raw-rebuild.db"), compact = output.resolve("compact-rebuild.db");
        assertFalse(Files.exists(raw)); assertFalse(Files.exists(compact));
        byte[] originalHash = fileHash(fixture);
        try (Connection source = DriverManager.getConnection("jdbc:sqlite:file:" + fixture + "?mode=ro")) {
            try (var query = source.createStatement(); var rows = query.executeQuery("SELECT id,type FROM Account")) {
                while (rows.next()) accountTypes.put(rows.getString(1), rows.getString(2));
            }
            try (var query = source.createStatement(); var rows = query.executeQuery("SELECT id,accountId FROM Category")) {
                while (rows.next()) categoryAccounts.put(rows.getString(1), rows.getString(2));
            }
            List<Schema> schema = new ArrayList<>();
            try (var query = source.createStatement(); var rows = query.executeQuery(
                    "SELECT type,name,sql FROM sqlite_master WHERE sql IS NOT NULL AND name NOT LIKE 'sqlite_%' ORDER BY rowid")) {
                while (rows.next()) schema.add(new Schema(rows.getString(1), rows.getString(2), rows.getString(3)));
            }
            rebuild(source, raw, schema, false);
            rebuild(source, compact, schema, true);
            try (Connection before = DriverManager.getConnection("jdbc:sqlite:" + raw);
                 Connection after = DriverManager.getConnection("jdbc:sqlite:" + compact)) {
                for (Schema item : schema) {
                    if (!item.type.equals("table")) continue;
                    assertSameRowsExceptProviderMetadata(source, after, item.name);
                    Stats old = stats(before, item.name), updated = stats(after, item.name);
                    assertEquals(old.rows, updated.rows, item.name);
                    System.out.printf(Locale.ROOT, "SIZE_TABLE %s rows=%d extraCharsBefore=%d extraCharsAfter=%d cmdsBefore=%d cmdsAfter=%d%n",
                            item.name, old.rows, old.characters, updated.characters, old.cmds, updated.cmds);
                }
                for (Connection db : List.of(before, after)) {
                    try (var query = db.createStatement(); var rows = query.executeQuery("PRAGMA integrity_check")) {
                        assertTrue(rows.next()); assertEquals("ok", rows.getString(1));
                    }
                }
            }
        }
        assertArrayEquals(originalHash, fileHash(fixture), "The input fixture must stay unchanged");
        for (Path db : List.of(fixture, raw, compact)) {
            long bytes = Files.size(db);
            System.out.printf(Locale.ROOT, "SIZE_FILE %s bytes=%d MB=%.3f MiB=%.3f%n", db.getFileName(), bytes, bytes / 1_000_000.0, bytes / 1048576.0);
        }
    }

    private void rebuild(Connection source, Path target, List<Schema> schema, boolean compact) throws Exception {
        try (Connection db = DriverManager.getConnection("jdbc:sqlite:" + target); var ddl = db.createStatement()) {
            ddl.execute("PRAGMA page_size=4096");
            db.setAutoCommit(false);
            for (Schema item : schema) if (item.type.equals("table")) ddl.execute(item.sql);
            for (Schema item : schema) {
                if (!item.type.equals("table")) continue;
                try (var query = source.createStatement(); var rows = query.executeQuery("SELECT * FROM " + quoted(item.name) + " ORDER BY rowid")) {
                    var metadata = rows.getMetaData();
                    int count = metadata.getColumnCount();
                    String placeholders = String.join(",", Collections.nCopies(count, "?"));
                    try (var insert = db.prepareStatement("INSERT INTO " + quoted(item.name) + " VALUES (" + placeholders + ")")) {
                        while (rows.next()) {
                            for (int i = 1; i <= count; i++) {
                                Object value = rows.getObject(i);
                                if (compact && metadata.getColumnName(i).equals("extraJson") && providerChannel(item.name, rows)) {
                                    value = ChannelExtraJsonCompactor.compact(rows.getString(i));
                                }
                                insert.setObject(i, value);
                            }
                            insert.executeUpdate();
                        }
                    }
                }
            }
            for (Schema item : schema) if (!item.type.equals("table")) ddl.execute(item.sql);
            db.commit();
        }
    }

    private boolean providerChannel(String table, ResultSet rows) throws SQLException {
        if (!List.of("Channel", "VodChannel", "SeriesChannel").contains(table)) return false;
        String account = table.equals("Channel") ? categoryAccounts.get(rows.getString("categoryId")) : rows.getString("accountId");
        return List.of("STALKER_PORTAL", "XTREME_API").contains(accountTypes.getOrDefault(account, ""));
    }

    private void assertSameRowsExceptProviderMetadata(Connection source, Connection target, String table) throws Exception {
        try (var a = source.createStatement(); var b = target.createStatement();
             var left = a.executeQuery("SELECT * FROM " + quoted(table) + " ORDER BY rowid");
             var right = b.executeQuery("SELECT * FROM " + quoted(table) + " ORDER BY rowid")) {
            var columns = left.getMetaData();
            while (left.next()) {
                assertTrue(right.next(), table);
                for (int i = 1; i <= columns.getColumnCount(); i++) {
                    if (columns.getColumnName(i).equals("extraJson") && providerChannel(table, left)) continue;
                    assertEquals(left.getObject(i), right.getObject(i), table + "." + columns.getColumnName(i));
                }
            }
            assertFalse(right.next(), table);
        }
    }

    private Stats stats(Connection db, String table) throws SQLException {
        boolean extra = false;
        try (var query = db.createStatement(); var rows = query.executeQuery("PRAGMA table_info(" + quoted(table) + ")")) {
            while (rows.next()) if (rows.getString("name").equals("extraJson")) extra = true;
        }
        String suffix = extra ? ",COALESCE(SUM(length(extraJson)),0),SUM(CASE WHEN json_valid(extraJson) THEN CASE WHEN json_type(extraJson,'$.cmds') IS NOT NULL THEN 1 ELSE 0 END ELSE 0 END)" : ",0,0";
        try (var query = db.createStatement(); var rows = query.executeQuery("SELECT count(*)" + suffix + " FROM " + quoted(table))) {
            assertTrue(rows.next()); return new Stats(rows.getLong(1), rows.getLong(2), rows.getLong(3));
        }
    }

    private static String quoted(String identifier) { return "\"" + identifier.replace("\"", "\"\"") + "\""; }
    private byte[] fileHash(Path path) throws Exception {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        try (var input = Files.newInputStream(path)) {
            byte[] buffer = new byte[128 * 1024];
            for (int n; (n = input.read(buffer)) > 0;) digest.update(buffer, 0, n);
        }
        return digest.digest();
    }
}
