package com.uiptv.db;

import com.uiptv.util.ConfigFileReader;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

import static com.uiptv.util.Platform.getUserHomeDirPath;
import static com.uiptv.util.StringUtils.isNotBlank;

public class SQLConnection {
    private static final int BUSY_TIMEOUT_MS = 10_000;
    private static final int CACHE_SIZE_KIB = 32_000;
    private static final int INIT_RETRY_ATTEMPTS = 6;
    private static final long JOURNAL_SIZE_LIMIT_BYTES = 16L * 1024L * 1024L;
    private static final double VACUUM_FREE_PAGE_THRESHOLD = 0.15d;
    private static final int WAL_AUTOCHECKPOINT_PAGES = 1_000;
    private static final long INIT_RETRY_DELAY_MS = 250L;

    private static String databasePathFromConfigFile = ConfigFileReader.getDbPathFromConfigFile();
    private static String dbPath = isNotBlank(databasePathFromConfigFile) ? databasePathFromConfigFile : getUserHomeDirPath() + File.separator + "uiptv.db";

    private SQLConnection() {
    }

    static {
        init();
    }

    @SuppressWarnings("java:S1141")
    public static synchronized void init() {
        try {
            FileUtils.touch(new File(dbPath));
            for (int attempt = 1; attempt <= INIT_RETRY_ATTEMPTS; attempt++) {
                try (Connection conn = openConnection()) {
                    applyPersistentPragmas(conn);
                    applySchema(conn);
                    runStartupMaintenance(conn);
                    return;
                } catch (SQLException ex) {
                    if (isBusy(ex) && attempt < INIT_RETRY_ATTEMPTS) {
                        sleepBeforeRetry();
                        continue;
                    }
                    throw new DatabaseAccessException("Unable to initialize database migrations", ex);
                }
            }
        } catch (IOException e) {
            throw new DatabaseAccessException("Unable to create database file", e);
        }
    }

    private static void applySchema(Connection conn) throws SQLException {
        if (DatabasePatchesUtils.hasMigrationsListResource()) {
            DatabasePatchesUtils.applyPatches(conn);
            return;
        }
        if (DatabasePatchesUtils.hasBaselineResource()) {
            DatabasePatchesUtils.applyBaseline(conn);
            return;
        }
        throw new IllegalStateException("No database schema resources found (missing migrations list and baseline)");
    }

    public static synchronized void setDatabasePath(String path) {
        dbPath = path;
        init();
    }

    public static Connection connect() {
        try {
            return openConnection();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to open database connection", e);
        }
    }

    private static Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
            statement.execute("PRAGMA temp_store = MEMORY");
            statement.execute("PRAGMA cache_size = -" + CACHE_SIZE_KIB);
            statement.execute("PRAGMA journal_size_limit = " + JOURNAL_SIZE_LIMIT_BYTES);
            statement.execute("PRAGMA wal_autocheckpoint = " + WAL_AUTOCHECKPOINT_PAGES);
        }
        return conn;
    }

    private static void applyPersistentPragmas(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA auto_vacuum = INCREMENTAL");
        }
    }

    private static void runStartupMaintenance(Connection conn) throws SQLException {
        try (Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA optimize");
            statement.execute("PRAGMA wal_checkpoint(PASSIVE)");
            if (shouldVacuum(statement)) {
                statement.execute("VACUUM");
                statement.execute("PRAGMA wal_checkpoint(TRUNCATE)");
            }
        }
    }

    private static boolean shouldVacuum(Statement statement) throws SQLException {
        long freePages = queryLongPragma(statement, "freelist_count");
        long totalPages = queryLongPragma(statement, "page_count");
        if (totalPages <= 0) {
            return false;
        }
        return ((double) freePages / totalPages) > VACUUM_FREE_PAGE_THRESHOLD;
    }

    private static long queryLongPragma(Statement statement, String pragmaName) throws SQLException {
        try (var resultSet = statement.executeQuery("PRAGMA " + pragmaName)) {
            return resultSet.next() ? resultSet.getLong(1) : 0L;
        }
    }

    private static boolean isBusy(SQLException exception) {
        SQLException current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (current.getErrorCode() == 5 || (message != null && message.contains("SQLITE_BUSY"))) {
                return true;
            }
            current = current.getNextException();
        }
        return false;
    }

    private static void sleepBeforeRetry() {
        try {
            Thread.sleep(INIT_RETRY_DELAY_MS);
        } catch (InterruptedException interruptedException) {
            Thread.currentThread().interrupt();
            throw new DatabaseAccessException("Interrupted while waiting to retry database initialization", interruptedException);
        }
    }

}
