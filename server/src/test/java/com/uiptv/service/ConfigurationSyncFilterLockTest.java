package com.uiptv.service;

import com.uiptv.db.DatabaseUtils;
import com.uiptv.util.SQLiteTableSync;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigurationSyncFilterLockTest extends DbBackedTest {

    @Test
    void syncConfiguration_copiesFilterListsButKeepsTargetFilterLockHash() throws Exception {
        Path sourcePath = tempDir.resolve("config-source.db");
        Path targetPath = tempDir.resolve("config-target.db");
        createSchema(sourcePath);
        createSchema(targetPath);

        seedConfiguration(sourcePath,
                "adult, xxx",
                "playboy",
                "1",
                "source-lock-hash");
        seedConfiguration(targetPath,
                "kids",
                "cartoon",
                "0",
                "target-lock-hash");

        assertTrue(SQLiteTableSync.syncConfiguration(sourcePath.toString(), targetPath.toString(), false));

        assertEquals("adult, xxx", readConfigurationColumn(targetPath, "filterCategoriesList"));
        assertEquals("playboy", readConfigurationColumn(targetPath, "filterChannelsList"));
        assertEquals("1", readConfigurationColumn(targetPath, "pauseFiltering"));
        assertEquals("target-lock-hash", readConfigurationColumn(targetPath, "filterLockHash"));
    }

    private void createSchema(Path dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = conn.createStatement()) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.DbTable.values()) {
                statement.execute(DatabaseUtils.createTableSql(table));
            }
        }
    }

    private void seedConfiguration(Path dbPath,
                                   String categoryFilter,
                                   String channelFilter,
                                   String pauseFiltering,
                                   String filterLockHash) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO Configuration (" +
                             "playerPath1, playerPath2, playerPath3, defaultPlayerPath, " +
                             "filterCategoriesList, filterChannelsList, pauseFiltering, darkTheme, serverPort, embeddedPlayer, " +
                             "enableFfmpegTranscoding, cacheExpiryDays, enableThumbnails, wideView, languageLocale, " +
                             "tmdbReadAccessToken, filterLockHash, uiZoomPercent, enableLitePlayerFfmpeg, autoRunServerOnStartup, " +
                             "vlcNetworkCachingMs, vlcLiveCachingMs, publishedM3uCategoryMode, enableVlcHttpUserAgent, " +
                             "enableVlcHttpForwardCookies, resolveChainAndDeepRedirects" +
                             ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, "player-1");
            ps.setString(2, "player-2");
            ps.setString(3, "player-3");
            ps.setString(4, "player-1");
            ps.setString(5, categoryFilter);
            ps.setString(6, channelFilter);
            ps.setString(7, pauseFiltering);
            ps.setString(8, "0");
            ps.setString(9, "8888");
            ps.setString(10, "0");
            ps.setString(11, "0");
            ps.setString(12, "30");
            ps.setString(13, "1");
            ps.setString(14, "0");
            ps.setString(15, "en-US");
            ps.setString(16, "");
            ps.setString(17, filterLockHash);
            ps.setString(18, "100");
            ps.setString(19, "0");
            ps.setString(20, "0");
            ps.setString(21, "1000");
            ps.setString(22, "1000");
            ps.setString(23, "");
            ps.setString(24, "1");
            ps.setString(25, "1");
            ps.setString(26, "0");
            ps.executeUpdate();
        }
    }

    private String readConfigurationColumn(Path dbPath, String column) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT " + column + " FROM Configuration LIMIT 1")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}
