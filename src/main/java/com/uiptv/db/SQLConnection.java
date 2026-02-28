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
    private static final int INIT_RETRY_ATTEMPTS = 6;
    private static final long INIT_RETRY_DELAY_MS = 250L;

    private static String databasePathFromConfigFile = ConfigFileReader.getDbPathFromConfigFile();
    private static String dbPath = isNotBlank(databasePathFromConfigFile) ? databasePathFromConfigFile : getUserHomeDirPath() + File.separator + "uiptv.db";

    static {
        init();
    }

    public static synchronized void init() {
        try {
            FileUtils.touch(new File(dbPath));
            for (int attempt = 1; attempt <= INIT_RETRY_ATTEMPTS; attempt++) {
                try (Connection conn = openConnection()) {
                    applySchema(conn);
                    return;
                } catch (SQLException ex) {
                    if (isBusy(ex) && attempt < INIT_RETRY_ATTEMPTS) {
                        sleepBeforeRetry();
                        continue;
                    }
                    throw new RuntimeException("Unable to initialize database migrations", ex);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
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
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static Connection openConnection() throws SQLException {
        Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        try (Statement statement = conn.createStatement()) {
            statement.execute("PRAGMA busy_timeout = " + BUSY_TIMEOUT_MS);
            statement.execute("PRAGMA journal_mode = WAL");
            statement.execute("PRAGMA synchronous = NORMAL");
        }
        return conn;
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
            throw new RuntimeException("Interrupted while waiting to retry database initialization", interruptedException);
        }
    }

}
