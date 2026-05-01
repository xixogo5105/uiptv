package com.uiptv.service;

import com.uiptv.db.DatabaseUtils;
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

class PublishedM3uSelectionSyncTest extends DbBackedTest {

    @Test
    void syncConfiguration_remapsPublishedM3uSelectionToTargetAccountIdsWhenIdsDiffer() throws Exception {
        Path sourcePath = tempDir.resolve("published-selection-source.db");
        Path targetPath = tempDir.resolve("published-selection-target.db");
        createSchema(sourcePath);
        createSchema(targetPath);

        seedAccount(sourcePath, "100", "Sky UK");
        seedPublishedSelection(sourcePath, "100");
        seedPublishedCategorySelection(sourcePath, "100", "Sports", false);
        seedPublishedChannelSelection(sourcePath, "100", "Sports", "channel-1", true);

        seedAccount(targetPath, "9001", "Sky UK");

        com.uiptv.ui.RootApplication.syncDatabases(
                sourcePath.toString(),
                targetPath.toString(),
                true,
                false
        );

        String targetSkyUkId = findAccountIdByName(targetPath, "Sky UK");
        assertTrue(hasPublishedSelection(targetPath, targetSkyUkId));
        assertTrue(hasPublishedCategorySelection(targetPath, targetSkyUkId, "Sports", false));
        assertTrue(hasPublishedChannelSelection(targetPath, targetSkyUkId, "Sports", "channel-1", true));
        assertEquals(1, countPublishedSelections(targetPath));
    }

    private void createSchema(Path dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = conn.createStatement()) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.DbTable.values()) {
                statement.execute(DatabaseUtils.createTableSql(table));
            }
        }
    }

    private void seedAccount(Path dbPath, String accountId, String accountName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT OR REPLACE INTO Account (id, accountName, username, password, url, macAddress, macAddressList, serialNumber, deviceId1, deviceId2, signature, epg, m3u8Path, type, serverPortalUrl, pinToTop, resolveChainAndDeepRedirects, httpMethod, timezone) " +
                             "VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)")) {
            ps.setString(1, accountId);
            ps.setString(2, accountName);
            ps.setString(3, "u");
            ps.setString(4, "p");
            ps.setString(5, "http://example.test");
            ps.setString(6, null);
            ps.setString(7, null);
            ps.setString(8, null);
            ps.setString(9, null);
            ps.setString(10, null);
            ps.setString(11, null);
            ps.setString(12, null);
            ps.setString(13, null);
            ps.setString(14, "M3U8_URL");
            ps.setString(15, "");
            ps.setString(16, "0");
            ps.setString(17, "0");
            ps.setString(18, "GET");
            ps.setString(19, "UTC");
            ps.executeUpdate();
        }
    }

    private void seedPublishedSelection(Path dbPath, String accountId) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO PublishedM3uSelection (accountId) VALUES (?)")) {
            ps.setString(1, accountId);
            ps.executeUpdate();
        }
    }

    private void seedPublishedCategorySelection(Path dbPath, String accountId, String categoryName, boolean selected) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO PublishedM3uCategorySelection (accountId, categoryName, selected) VALUES (?,?,?)")) {
            ps.setString(1, accountId);
            ps.setString(2, categoryName);
            ps.setString(3, selected ? "1" : "0");
            ps.executeUpdate();
        }
    }

    private void seedPublishedChannelSelection(Path dbPath, String accountId, String categoryName, String channelId, boolean selected) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "INSERT INTO PublishedM3uChannelSelection (accountId, categoryName, channelId, selected) VALUES (?,?,?,?)")) {
            ps.setString(1, accountId);
            ps.setString(2, categoryName);
            ps.setString(3, channelId);
            ps.setString(4, selected ? "1" : "0");
            ps.executeUpdate();
        }
    }

    private boolean hasPublishedSelection(Path dbPath, String accountId) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM PublishedM3uSelection WHERE accountId = ?")) {
            ps.setString(1, accountId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasPublishedCategorySelection(Path dbPath, String accountId, String categoryName, boolean selected) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM PublishedM3uCategorySelection WHERE accountId = ? AND categoryName = ? AND selected = ?")) {
            ps.setString(1, accountId);
            ps.setString(2, categoryName);
            ps.setString(3, selected ? "1" : "0");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private boolean hasPublishedChannelSelection(Path dbPath, String accountId, String categoryName, String channelId, boolean selected) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM PublishedM3uChannelSelection WHERE accountId = ? AND categoryName = ? AND channelId = ? AND selected = ?")) {
            ps.setString(1, accountId);
            ps.setString(2, categoryName);
            ps.setString(3, channelId);
            ps.setString(4, selected ? "1" : "0");
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    private String findAccountIdByName(Path dbPath, String accountName) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT id FROM Account WHERE accountName = ?")) {
            ps.setString(1, accountName);
            try (ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next(), "Expected target account row for " + accountName);
                return rs.getString("id");
            }
        }
    }

    private int countPublishedSelections(Path dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement("SELECT COUNT(*) FROM PublishedM3uSelection");
             ResultSet rs = ps.executeQuery()) {
            return rs.next() ? rs.getInt(1) : 0;
        }
    }
}
