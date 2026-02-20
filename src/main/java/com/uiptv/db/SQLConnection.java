package com.uiptv.db;

import com.uiptv.ui.LogDisplayUI;
import com.uiptv.util.ConfigFileReader;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static com.uiptv.util.Platform.getUserHomeDirPath;
import static com.uiptv.util.StringUtils.isNotBlank;

public class SQLConnection {
    private static String databasePathFromConfigFile = ConfigFileReader.getDbPathFromConfigFile();
    private static String dbPath = isNotBlank(databasePathFromConfigFile) ? databasePathFromConfigFile : getUserHomeDirPath() + File.separator + "uiptv.db";

    static {
        init();
    }

    public static void init() {
        try {
            FileUtils.touch(new File(dbPath));
            for (DatabaseUtils.DbTable t : DatabaseUtils.DbTable.values()) {
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath); Statement statement = conn.createStatement()) {
                    String sql = DatabaseUtils.createTableSql(t);
                    statement.execute(sql);
                } catch (Exception ex) {
                    LogDisplayUI.addLog(ex.getMessage());
                }
            }

            try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath)) {
                DatabasePatchesUtils.applyPatches(conn);
            } catch (Exception ignored) {
            }

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void setDatabasePath(String path) {
        dbPath = path;
        init();
    }

    public static Connection connect() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + dbPath);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
