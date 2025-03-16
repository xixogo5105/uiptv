package com.uiptv.db;

import com.uiptv.ui.LogDisplayUI;
import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;

import static com.uiptv.db.DatabasePatchesUtils.getDbPatches;
import static com.uiptv.util.Platform.getUserHomeDirPath;

public class SQLConnection {
    private static final String DB_PATH = getUserHomeDirPath() + File.separator + "uiptv.db";

    static {
        try {
            FileUtils.touch(new File(DB_PATH));
            for (DatabaseUtils.DbTable t : DatabaseUtils.DbTable.values()) {
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH); Statement statement = conn.createStatement()) {
                    String sql = DatabaseUtils.createTableSql(t);
                    statement.execute(sql);
                } catch (Exception ex) {
                    LogDisplayUI.addLog(ex.getMessage());
                }
            }
            for (String sql : getDbPatches()) {
                try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + DB_PATH); Statement statement = conn.createStatement()) {
                    statement.execute(sql);
                } catch (Exception ignored) {
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static Connection connect() {
        try {
            return DriverManager.getConnection("jdbc:sqlite:" + DB_PATH);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
