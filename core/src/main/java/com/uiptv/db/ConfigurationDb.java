package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeMode;
import com.uiptv.util.AppLog;

import java.sql.*;
import java.util.List;

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

        Connection conn = null;
        try {
            conn = connect();
            clearCacheTransaction(conn, account.getDbId());
        } catch (Exception e) {
            // Surface the failure instead of silently leaving a half-cleared cache behind.
            AppLog.addErrorLog(ConfigurationDb.class,
                    "Failed to clear cache for account " + account.getAccountName() + " (" + account.getDbId() + "): " + e);
        } finally {
            closeQuietly(conn);
        }
    }

    private void clearCacheTransaction(Connection conn, String accountId) throws SQLException {
        boolean previousAutoCommit = conn.getAutoCommit();
        conn.setAutoCommit(false);
        try {
            deleteAccountLiveChannels(conn, accountId);
            for (DatabaseUtils.DbTable table : DatabaseUtils.Cacheable) {
                if (table != DatabaseUtils.DbTable.CHANNEL_TABLE) deleteAccountCacheForTable(conn, table, accountId);
            }
            String updateAccountSql = "UPDATE " + validatedTableName(DatabaseUtils.DbTable.ACCOUNT_TABLE)
                    + " SET serverPortalUrl='' WHERE id = ?";
            updateAccountServerPortal(conn, updateAccountSql, accountId);
            conn.commit();
        } catch (Exception error) {
            conn.rollback();
            throw error;
        } finally {
            conn.setAutoCommit(previousAutoCommit);
        }
    }

    private void updateAccountServerPortal(Connection conn, String sql, String accountId) throws SQLException {
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountId);
            pstmt.executeUpdate();
        }
    }

    private void closeQuietly(Connection conn) {
        if (conn == null) {
            return;
        }
        try {
            conn.close();
        } catch (SQLException e) {
            AppLog.addWarningLog(ConfigurationDb.class, "Failed to close cache-clear connection: " + e.getMessage());
        }
    }

    private void deleteAccountCacheForTable(Connection conn, DatabaseUtils.DbTable table, String accountId) throws SQLException {
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
        String duration = nullSafeString(resultSet, "filterLockUnlockDurationMinutes");
        Configuration c = new Configuration(
                nullSafeString(resultSet, "playerPath1"),
                nullSafeString(resultSet, "playerPath2"),
                nullSafeString(resultSet, "playerPath3"),
                nullSafeString(resultSet, "defaultPlayerPath"),
                nullSafeString(resultSet, "filterCategoriesList"),
                nullSafeString(resultSet, "filterChannelsList"),
                safeBoolean(resultSet, "pauseFiltering"),
                nullSafeString(resultSet, "serverPort"),
                safeBoolean(resultSet, "embeddedPlayer"),
                nullSafeString(resultSet, "cacheExpiryDays"),
                safeBoolean(resultSet, "enableThumbnails")
        );
        c.applyThemeMode(ThemeMode.fromPersisted(nullSafeString(resultSet, "themeMode")));
        c.setWideView(safeBoolean(resultSet, "wideView"));
        c.setLanguageLocale(nullSafeString(resultSet, "languageLocale"));
        c.setTmdbReadAccessToken(nullSafeString(resultSet, "tmdbReadAccessToken"));
        c.setFilterLockHash(nullSafeString(resultSet, "filterLockHash"));
        c.setUiZoomPercent(nullSafeString(resultSet, "uiZoomPercent"));
        c.setAutoRunServerOnStartup(safeBoolean(resultSet, "autoRunServerOnStartup"));
        c.setHttpsServerEnabled(safeBoolean(resultSet, "httpsServerEnabled"));
        c.setHttpsServerPort(nullSafeString(resultSet, "httpsServerPort"));
        c.setVlcNetworkCachingMs(nullSafeString(resultSet, "vlcNetworkCachingMs"));
        c.setVlcLiveCachingMs(nullSafeString(resultSet, "vlcLiveCachingMs"));
        c.setPublishedM3uCategoryMode(nullSafeString(resultSet, "publishedM3uCategoryMode"));
        c.setEnableVlcHttpUserAgent(missingOrTrue(resultSet, "enableVlcHttpUserAgent"));
        c.setEnableVlcHttpForwardCookies(missingOrTrue(resultSet, "enableVlcHttpForwardCookies"));
        c.setResolveChainAndDeepRedirects(safeBoolean(resultSet, "resolveChainAndDeepRedirects"));
        c.setWebPlaybackProxyEnabled(missingOrTrue(resultSet, "webPlaybackProxyEnabled"));
        c.setFilterLockUnlockDurationMinutes(duration);
        c.setVlcNoVideoTitleShow(missingOrTrue(resultSet, "vlcNoVideoTitleShow"));
        c.setVlcQuiet(missingOrTrue(resultSet, "vlcQuiet"));
        c.setVlcHttpReconnect(missingOrTrue(resultSet, "vlcHttpReconnect"));
        c.setVlcAdaptiveUseAccess(missingOrTrue(resultSet, "vlcAdaptiveUseAccess"));
        c.setVlcVout(nullSafeString(resultSet, "vlcVout"));
        c.setVlcAvcodecHw(nullSafeString(resultSet, "vlcAvcodecHw"));
        c.setLightweightModeEnabled(safeBoolean(resultSet, "lightweightModeEnabled"));
        c.setShowBookmarkAndAccountCounts(safeBoolean(resultSet, "showBookmarkAndAccountCounts"));
        c.setEpgMappingsJson(nullSafeString(resultSet, "epgMappingsJson"));
        c.setEpgEnabled(safeBoolean(resultSet, "epgEnabled"));
        c.setEpgPlaybackOnly(safeBoolean(resultSet, "epgPlaybackOnly"));
        c.setEpgRefreshIntervalHours(nullSafeString(resultSet,"epgRefreshIntervalHours"));
        c.setEpgSchedulerIntervalMinutes(nullSafeString(resultSet,"epgSchedulerIntervalMinutes"));
        c.setGlobalEpgSource(nullSafeString(resultSet, "globalEpgSource"));
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
            update(existing.getFirst(), configuration);
            return;
        }
        insert(configuration);
    }

    private void update(Configuration current, Configuration configuration) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(updateTableSql(CONFIGURATION_TABLE))) {
            setParameters(statement, configuration);
            statement.setString(43, current.getDbId());
            statement.execute();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute update query", e);
        }
    }

    private void insert(Configuration configuration) {
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertTableSql(CONFIGURATION_TABLE))) {
            setParameters(statement, configuration);
            statement.execute();
        } catch (SQLException e) {
            throw new DatabaseAccessException("Unable to execute insert query", e);
        }
    }

    private void setParameters(PreparedStatement statement, Configuration configuration) throws SQLException {
        statement.setString(1, configuration.getPlayerPath1());
        statement.setString(2, configuration.getPlayerPath2());
        statement.setString(3, configuration.getPlayerPath3());
        statement.setString(4, configuration.getDefaultPlayerPath());
        statement.setString(5, configuration.getFilterCategoriesList());
        statement.setString(6, configuration.getFilterChannelsList());
        setBoolean(statement, 7, configuration.isPauseFiltering());
        statement.setString(8, configuration.getServerPort());
        setBoolean(statement, 9, configuration.isEmbeddedPlayer());
        statement.setString(10, configuration.getCacheExpiryDays());
        setBoolean(statement, 11, configuration.isEnableThumbnails());
        setBoolean(statement, 12, configuration.isWideView());
        statement.setString(13, configuration.getLanguageLocale());
        statement.setString(14, configuration.getTmdbReadAccessToken());
        statement.setString(15, configuration.getFilterLockHash());
        statement.setString(16, configuration.getUiZoomPercent());
        setBoolean(statement, 17, configuration.isAutoRunServerOnStartup());
        setBoolean(statement, 18, configuration.isHttpsServerEnabled());
        statement.setString(19, configuration.getHttpsServerPort());
        statement.setString(20, configuration.getVlcNetworkCachingMs());
        statement.setString(21, configuration.getVlcLiveCachingMs());
        statement.setString(22, configuration.getPublishedM3uCategoryMode());
        setBoolean(statement, 23, configuration.isEnableVlcHttpUserAgent());
        setBoolean(statement, 24, configuration.isEnableVlcHttpForwardCookies());
        setBoolean(statement, 25, configuration.isResolveChainAndDeepRedirects());
        setBoolean(statement, 26, configuration.isWebPlaybackProxyEnabled());
        statement.setString(27, configuration.getFilterLockUnlockDurationMinutes());
        setBoolean(statement, 28, configuration.isVlcNoVideoTitleShow());
        setBoolean(statement, 29, configuration.isVlcQuiet());
        setBoolean(statement, 30, configuration.isVlcHttpReconnect());
        setBoolean(statement, 31, configuration.isVlcAdaptiveUseAccess());
        statement.setString(32, configuration.getVlcVout());
        statement.setString(33, configuration.getVlcAvcodecHw());
        setBoolean(statement, 34, configuration.isLightweightModeEnabled());
        setBoolean(statement, 35, configuration.isShowBookmarkAndAccountCounts());
        statement.setString(36, configuration.resolveThemeMode().persistedValue());
        setBoolean(statement, 37, configuration.isEpgEnabled());
        setBoolean(statement, 38, configuration.isEpgPlaybackOnly());
        statement.setString(39, configuration.getGlobalEpgSource());
        statement.setString(40, configuration.getEpgMappingsJson());
        statement.setString(41, String.valueOf(configuration.normalizedEpgRefreshHours()));
        statement.setString(42, String.valueOf(configuration.normalizedEpgSchedulerIntervalMinutes()));
     }

    private void setBoolean(PreparedStatement statement, int index, boolean value) throws SQLException {
        statement.setString(index, value ? "1" : "0");
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
