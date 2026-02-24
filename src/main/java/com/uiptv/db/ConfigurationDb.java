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
import static com.uiptv.db.SQLConnection.connect;

public class ConfigurationDb extends BaseDb {
    private static ConfigurationDb instance;


    public static synchronized ConfigurationDb get() {
        if (instance == null) {
            instance = new ConfigurationDb();
            // Ensure patches are applied, especially if hot-swapped
            try (Connection conn = connect()) {
                DatabasePatchesUtils.applyPatches(conn);
            } catch (SQLException e) {
                e.printStackTrace();
            }
        }
        return instance;
    }

    public ConfigurationDb() {
        super(CONFIGURATION_TABLE);
    }

    public void clearAllCache() {
        try (Connection conn = connect(); Statement statement = conn.createStatement()) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.Cacheable) {
                statement.execute("DELETE FROM " + table.getTableName());
            }
            statement.execute("UPDATE " + DatabaseUtils.DbTable.ACCOUNT_TABLE.getTableName() + " SET serverPortalUrl=''");
        } catch (Exception ignored) {
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

            String updateAccountSql = "UPDATE " + DatabaseUtils.DbTable.ACCOUNT_TABLE.getTableName()
                    + " SET serverPortalUrl='' WHERE id = ?";
            try (PreparedStatement pstmt = conn.prepareStatement(updateAccountSql)) {
                pstmt.setString(1, account.getDbId());
                pstmt.executeUpdate();
            }
        } catch (Exception ignored) {
        }
    }

    private void deleteAccountCacheForTable(Connection conn, DatabaseUtils.DbTable table, String accountId) throws SQLException {
        if (table == DatabaseUtils.DbTable.CHANNEL_TABLE) {
            deleteAccountLiveChannels(conn, accountId);
            return;
        }

        String sql = "DELETE FROM " + table.getTableName() + " WHERE accountId=?";
        try (PreparedStatement pstmt = conn.prepareStatement(sql)) {
            pstmt.setString(1, accountId);
            pstmt.executeUpdate();
        }
    }

    private void deleteAccountLiveChannels(Connection conn, String accountId) throws SQLException {
        String sql = "DELETE FROM " + DatabaseUtils.DbTable.CHANNEL_TABLE.getTableName()
                + " WHERE categoryId IN (SELECT id FROM " + DatabaseUtils.DbTable.CATEGORY_TABLE.getTableName()
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
                nullSafeString(resultSet, "fontFamily"),
                nullSafeString(resultSet, "fontSize"),
                nullSafeString(resultSet, "fontWeight"),
                safeBoolean(resultSet, "darkTheme"),
                nullSafeString(resultSet, "serverPort"),
                safeBoolean(resultSet, "embeddedPlayer"),
                safeBoolean(resultSet, "enableFfmpegTranscoding")
        );
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
                statement.setString(15, current.getDbId());
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException("Unable to execute update query", e);
            }
        } else {
            // Insert new
            String insertQuery = insertTableSql(CONFIGURATION_TABLE);
            try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(insertQuery)) {
                setParameters(statement, configuration);
                statement.execute();
            } catch (SQLException e) {
                throw new RuntimeException("Unable to execute insert query", e);
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
        statement.setString(8, configuration.getFontFamily());
        statement.setString(9, configuration.getFontSize());
        statement.setString(10, configuration.getFontWeight());
        statement.setString(11, configuration.isDarkTheme() ? "1" : "0");
        statement.setString(12, configuration.getServerPort());
        statement.setString(13, configuration.isEmbeddedPlayer() ? "1" : "0");
        statement.setString(14, configuration.isEnableFfmpegTranscoding() ? "1" : "0");
    }
}
