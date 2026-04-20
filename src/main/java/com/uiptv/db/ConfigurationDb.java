package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Configuration;

import java.sql.*;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static com.uiptv.db.DatabaseUtils.DbTable.CONFIGURATION_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.DatabaseUtils.updateTableSql;
import static com.uiptv.db.DatabaseUtils.validatedTableName;
import static com.uiptv.db.SQLConnection.connect;

public class ConfigurationDb extends BaseDb {
    private static final String DELETE_FROM = "DELETE FROM ";
    private static ConfigurationDb instance;


    public static synchronized ConfigurationDb get() {
        if (instance == null) {
            instance = new ConfigurationDb();
        }
        return instance;
    }

    public ConfigurationDb() {
        super(CONFIGURATION_TABLE);
    }

    public void clearAllCache() {
        try (Connection conn = connect(); Statement statement = conn.createStatement()) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.Cacheable) {
                statement.addBatch(DELETE_FROM + validatedTableName(table));
            }
            statement.addBatch("UPDATE " + validatedTableName(DatabaseUtils.DbTable.ACCOUNT_TABLE) + " SET serverPortalUrl=''");
            statement.executeBatch();
        } catch (Exception _) {
            // Cache clearing is best-effort; keep app startup resilient if a stale table or DB handle fails here.
        }
    }

    public void clearCache(Account account) {
        if (account == null || account.getDbId() == null) {
            return;
        }

        try (Connection conn = connect()) {
            Set<String> clearedTables = new HashSet<>();
            for (DatabaseUtils.DbTable table : DatabaseUtils.Cacheable) {
                deleteAccountCacheForTable(conn, table, account.getDbId());
                clearedTables.add(table.getTableName());
            }

            // Live channels are tied to live categories, so clear them once via explicit join if not already covered.
            if (!clearedTables.contains(DatabaseUtils.DbTable.CHANNEL_TABLE.getTableName())) {
                deleteAccountLiveChannels(conn, account.getDbId());
            }

            String updateAccountSql = "UPDATE " + validatedTableName(DatabaseUtils.DbTable.ACCOUNT_TABLE)
                    + " SET serverPortalUrl='' WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateAccountSql)) {
                pstmt.setString(1, account.getDbId());
                pstmt.executeUpdate();
            }
        } catch (Exception _) {
            // Cache clearing is best-effort; preserve current app flow even if one table cannot be cleared.
        }
    }

    private void deleteAccountCacheForTable(Connection conn, DatabaseUtils.DbTable table, String accountId) throws SQLException {
        if (table == DatabaseUtils.DbTable.CHANNEL_TABLE) {
            deleteAccountLiveChannels(conn, accountId);
            return;
        }

        String sql = DELETE_FROM + validatedTableName(table) + " WHERE accountId=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountId);
            pstmt.executeUpdate();
        }
    }

    private void deleteAccountLiveChannels(Connection conn, String accountId) throws SQLException {
        String sql = DELETE_FROM + validatedTableName(DatabaseUtils.DbTable.CHANNEL_TABLE)
                + " WHERE categoryId IN (SELECT id FROM " + validatedTableName(DatabaseUtils.DbTable.CATEGORY_TABLE)
                + " WHERE accountId=?)";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountId);
            pstmt.executeUpdate();
        }
    }

    @Override
    Configuration populate(ResultSet resultSet) {
        Configuration c = new Configuration(
                nullSafeString(resultSet, "playerPath1"),
                nullSafeString(resultSet, "playerPath2"),
                nullSafeString(resultSet, "playerPath3"),
                nullSafeString(resultSet, "defaultPlayerPath"),
                nullSafeString(resultSet, "filterCategoriesList"),
                nullSafeString(resultSet, "filterChannelsList"),
                safeBoolean(resultSet, "pauseFiltering"),
                safeBoolean(resultSet, "darkTheme"),
                nullSafeString(resultSet, "serverPort"),
                safeBoolean(resultSet, "embeddedPlayer"),
                safeBoolean(resultSet, "enableFfmpegTranscoding"),
                nullSafeString(resultSet, "cacheExpiryDays"),
                safeBoolean(resultSet, "enableThumbnails")
        );
        c.setWideView(safeBoolean(resultSet, "wideView"));
        c.setLanguageLocale(nullSafeString(resultSet, "languageLocale"));
        c.setTmdbReadAccessToken(nullSafeString(resultSet, "tmdbReadAccessToken"));
        c.setUiZoomPercent(nullSafeString(resultSet, "uiZoomPercent"));
        c.setEnableLitePlayerFfmpeg(safeBoolean(resultSet, "enableLitePlayerFfmpeg"));
        c.setVlcNetworkCachingMs(nullSafeString(resultSet, "vlcNetworkCachingMs"));
        c.setVlcLiveCachingMs(nullSafeString(resultSet, "vlcLiveCachingMs"));
        c.setEnableVlcHttpUserAgent(missingOrTrue(resultSet, "enableVlcHttpUserAgent"));
        c.setEnableVlcHttpForwardCookies(missingOrTrue(resultSet, "enableVlcHttpForwardCookies"));
        c.setDbId(nullSafeString(resultSet, "id"));
        return c;
    }

    public Configuration getConfiguration() {
        List<Configuration> configurations = super.getAll();
        return configurations != null && !configurations.isEmpty() ? configurations.get(0) : new Configuration();
    }

    public void save(final Configuration configuration) {
        List<Configuration> existing = super.getAll();

        if (existing != null && !existing.isEmpty()) {
            // Update existing
            Configuration current = existing.get(0);
            String updateQuery = updateTableSql(CONFIGURATION_TABLE);
            try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(updateQuery)) {
                setParameters(statement, configuration);
                statement.setString(23, current.getDbId());
                statement.execute();
            } catch (SQLException e) {
                throw new DatabaseAccessException("Unable to execute update query", e);
            }
        } else {
            // Insert new
            String insertQuery = insertTableSql(CONFIGURATION_TABLE);
            try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertQuery)) {
                setParameters(statement, configuration);
                statement.execute();
            } catch (SQLException e) {
                throw new DatabaseAccessException("Unable to execute insert query", e);
            }
        }
    }

    private void setParameters(PreparedStatement statement, Configuration configuration) throws SQLException {
        statement.setString(1, configuration.getPlayerPath1());
        statement.setString(2, configuration.getPlayerPath2());
        statement.setString(3, configuration.getPlayerPath3());
        statement.setString(4, configuration.getDefaultPlayerPath());
        statement.setString(5, configuration.getFilterCategoriesList());
        statement.setString(6, configuration.getFilterChannelsList());
        statement.setString(7, configuration.isPauseFiltering() ? "1" : "0");
        statement.setString(8, configuration.isDarkTheme() ? "1" : "0");
        statement.setString(9, configuration.getServerPort());
        statement.setString(10, configuration.isEmbeddedPlayer() ? "1" : "0");
        statement.setString(11, configuration.isEnableFfmpegTranscoding() ? "1" : "0");
        statement.setString(12, configuration.getCacheExpiryDays());
        statement.setString(13, configuration.isEnableThumbnails() ? "1" : "0");
        statement.setString(14, configuration.isWideView() ? "1" : "0");
        statement.setString(15, configuration.getLanguageLocale());
        statement.setString(16, configuration.getTmdbReadAccessToken());
        statement.setString(17, configuration.getUiZoomPercent());
        statement.setString(18, configuration.isEnableLitePlayerFfmpeg() ? "1" : "0");
        statement.setString(19, configuration.getVlcNetworkCachingMs());
        statement.setString(20, configuration.getVlcLiveCachingMs());
        statement.setString(21, configuration.isEnableVlcHttpUserAgent() ? "1" : "0");
        statement.setString(22, configuration.isEnableVlcHttpForwardCookies() ? "1" : "0");
    }

    private boolean missingOrTrue(ResultSet resultSet, String columnName) {
        try {
            String raw = resultSet.getString(columnName);
            return raw == null || raw.isBlank() || !"0".equals(raw.trim());
        } catch (SQLException _) {
            return true;
        }
    }
}
