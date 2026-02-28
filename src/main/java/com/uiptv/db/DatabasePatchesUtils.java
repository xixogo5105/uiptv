package com.uiptv.db;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;

public class DatabasePatchesUtils {
    private static final String MIGRATIONS_LIST_RESOURCE = "db/migrations/migrations.txt";
    private static final String BASELINE_RESOURCE = "db/migrations/0000_baseline.sql";
    private static final String MIGRATIONS_DIR_RESOURCE = "db/migrations/";

    public static void applyPatches(Connection conn) throws SQLException {
        createSchemaMigrationsTable(conn);
        List<String> migrationNames = readMigrationNames();
        for (String migrationName : migrationNames) {
            applyMigration(conn, migrationName);
        }
    }

    public static void applyBaseline(Connection conn) throws SQLException {
        String baselineSql = readResource(BASELINE_RESOURCE);
        executeMigrationContent(conn, baselineSql);
    }

    public static boolean hasMigrationsListResource() {
        return resourceExists(MIGRATIONS_LIST_RESOURCE);
    }

    public static boolean hasBaselineResource() {
        return resourceExists(BASELINE_RESOURCE);
    }

    private static void applyMigration(Connection conn, String migrationName) throws SQLException {
        String resourcePath = MIGRATIONS_DIR_RESOURCE + migrationName;
        String migrationSql = readResource(resourcePath);
        String checksum = checksum(migrationSql);

        MigrationRecord existing = findMigrationRecord(conn, migrationName);
        if (existing != null && "success".equalsIgnoreCase(existing.status) && checksum.equals(existing.checksum)) {
            return;
        }

        boolean originalAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            executeMigrationContent(conn, migrationSql);
            upsertMigrationRecord(conn, migrationName, checksum, "success", null);
            conn.commit();
        } catch (Exception ex) {
            conn.rollback();
            upsertMigrationRecord(conn, migrationName, checksum, "failed", safeError(ex));
            conn.commit();
        } finally {
            conn.setAutoCommit(originalAutoCommit);
        }
    }

    private static void executeMigrationContent(Connection conn, String migrationSql) throws SQLException {
        String directive = findDirectiveLine(migrationSql);
        if (directive != null) {
            executeDirective(conn, directive);
            return;
        }
        executeSqlStatements(conn, migrationSql);
    }

    private static void executeDirective(Connection conn, String directiveLine) throws SQLException {
        String[] parts = directiveLine.trim().split("\\s+", 4);
        if (parts.length < 3) {
            throw new SQLException("Invalid migration directive: " + directiveLine);
        }
        String command = parts[0].toLowerCase();
        if ("--@add_column".equals(command)) {
            if (parts.length < 4) {
                throw new SQLException("Invalid add_column directive: " + directiveLine);
            }
            String table = parts[1];
            String column = parts[2];
            String definition = parts[3];
            if (columnExists(conn, table, column)) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
            }
            return;
        }
        if ("--@drop_column".equals(command)) {
            String table = parts[1];
            String column = parts[2];
            if (!columnExists(conn, table, column)) {
                return;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate("ALTER TABLE " + table + " DROP COLUMN " + column);
            }
            return;
        }
        throw new SQLException("Unsupported migration directive: " + directiveLine);
    }

    private static void executeSqlStatements(Connection conn, String migrationSql) throws SQLException {
        StringBuilder builder = new StringBuilder();
        String[] lines = migrationSql.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--")) {
                continue;
            }
            builder.append(line).append('\n');
        }
        String sqlBlob = builder.toString();
        for (String raw : sqlBlob.split(";")) {
            String statement = raw.trim();
            if (statement.isEmpty()) {
                continue;
            }
            try (Statement stmt = conn.createStatement()) {
                stmt.executeUpdate(statement);
            }
        }
    }

    private static boolean columnExists(Connection conn, String tableName, String columnName) throws SQLException {
        String sql = "PRAGMA table_info(" + tableName + ")";
        try (Statement stmt = conn.createStatement(); ResultSet rs = stmt.executeQuery(sql)) {
            while (rs.next()) {
                if (columnName.equalsIgnoreCase(rs.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }

    private static String findDirectiveLine(String migrationSql) {
        String[] lines = migrationSql.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("--@")) {
                return trimmed;
            }
        }
        return null;
    }

    private static List<String> readMigrationNames() {
        String content = readResource(MIGRATIONS_LIST_RESOURCE);
        List<String> names = new ArrayList<>();
        String[] lines = content.split("\\R");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty() || trimmed.startsWith("#")) {
                continue;
            }
            names.add(trimmed);
        }
        return names;
    }

    private static String readResource(String path) {
        try (InputStream in = openResource(path)) {
            if (in == null) {
                throw new IllegalStateException("Migration resource not found: " + path);
            }
            StringBuilder out = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    out.append(line).append('\n');
                }
            }
            return out.toString();
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to read migration resource: " + path, ex);
        }
    }

    private static boolean resourceExists(String path) {
        try (InputStream in = openResource(path)) {
            return in != null;
        } catch (Exception ex) {
            return false;
        }
    }

    private static InputStream openResource(String path) {
        String normalized = path.startsWith("/") ? path.substring(1) : path;
        String absolute = "/" + normalized;

        InputStream in = DatabasePatchesUtils.class.getResourceAsStream(absolute);
        if (in != null) {
            return in;
        }

        ClassLoader contextClassLoader = Thread.currentThread().getContextClassLoader();
        if (contextClassLoader != null) {
            in = contextClassLoader.getResourceAsStream(normalized);
            if (in != null) {
                return in;
            }
        }

        ClassLoader classLoader = DatabasePatchesUtils.class.getClassLoader();
        if (classLoader != null) {
            in = classLoader.getResourceAsStream(normalized);
            if (in != null) {
                return in;
            }
        }

        Module module = DatabasePatchesUtils.class.getModule();
        try {
            in = module.getResourceAsStream(normalized);
            if (in != null) {
                return in;
            }
        } catch (Exception ignored) {
            // Continue trying other strategies.
        }

        Path localPath = Paths.get("src", "main", "resources", normalized);
        if (Files.exists(localPath)) {
            try {
                return new FileInputStream(localPath.toFile());
            } catch (Exception ignored) {
                return null;
            }
        }
        return null;
    }

    private static String checksum(String text) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(text.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception ex) {
            throw new IllegalStateException("Unable to compute migration checksum", ex);
        }
    }

    private static void createSchemaMigrationsTable(Connection conn) throws SQLException {
        try (Statement stmt = conn.createStatement()) {
            stmt.executeUpdate("CREATE TABLE IF NOT EXISTS schema_migrations ("
                    + "name TEXT PRIMARY KEY,"
                    + "checksum TEXT NOT NULL,"
                    + "status TEXT NOT NULL,"
                    + "applied_at INTEGER NOT NULL,"
                    + "error_message TEXT"
                    + ")");
        }
    }

    private static MigrationRecord findMigrationRecord(Connection conn, String name) throws SQLException {
        String sql = "SELECT checksum, status FROM schema_migrations WHERE name = ?";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return new MigrationRecord(rs.getString("checksum"), rs.getString("status"));
            }
        }
    }

    private static void upsertMigrationRecord(Connection conn, String name, String checksum, String status, String errorMessage) throws SQLException {
        String sql = "INSERT INTO schema_migrations(name, checksum, status, applied_at, error_message) VALUES(?,?,?,?,?) "
                + "ON CONFLICT(name) DO UPDATE SET "
                + "checksum=excluded.checksum,"
                + "status=excluded.status,"
                + "applied_at=excluded.applied_at,"
                + "error_message=excluded.error_message";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, name);
            ps.setString(2, checksum);
            ps.setString(3, status);
            ps.setLong(4, Instant.now().getEpochSecond());
            ps.setString(5, errorMessage);
            ps.executeUpdate();
        }
    }

    private static String safeError(Exception ex) {
        String message = ex == null ? "" : ex.getMessage();
        if (message == null || message.isBlank()) {
            return ex == null ? "" : ex.getClass().getSimpleName();
        }
        if (message.length() > 1000) {
            return message.substring(0, 1000);
        }
        return message;
    }

    private record MigrationRecord(String checksum, String status) {
    }
}
