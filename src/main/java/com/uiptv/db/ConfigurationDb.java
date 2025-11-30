package com.uiptv.db;

import com.uiptv.model.Configuration;

import java.sql.*;
import java.util.List;

import static com.uiptv.db.DatabaseUtils.DbTable.CONFIGURATION_TABLE;
import static com.uiptv.db.DatabaseUtils.insertTableSql;
import static com.uiptv.db.SQLConnection.connect;

public class ConfigurationDb extends BaseDb {
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

    public void clearCache() {
        for (DatabaseUtils.DbTable t : DatabaseUtils.DbTable.values()) {
            try (Connection conn = connect(); Statement statement = conn.createStatement()) {
                if (DatabaseUtils.Cacheable.contains(t)) {
                    statement.execute(DatabaseUtils.dropTableSql(t));
                }
            } catch (Exception ignored) {
            }
            try (Connection conn = connect(); Statement statement = conn.createStatement()) {
                if (DatabaseUtils.Cacheable.contains(t)) {
                    statement.execute(DatabaseUtils.createTableSql(t));
                }
            } catch (Exception ignored) {
            }
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
                safeBoolean(resultSet, "pauseCaching"),
                safeBoolean(resultSet, "embeddedPlayer")
        );
        c.setDbId(nullSafeString(resultSet, "id"));
        return c;
    }

    public Configuration getConfiguration() {
        List<Configuration> configurations = super.getAll();
        return configurations != null && !configurations.isEmpty() ? configurations.get(0) : new Configuration();
    }

    public void save(final Configuration configuration) {
        super.<Configuration>getAll().forEach(c -> {
            delete(c.getDbId());
        });
        String saveQuery;
        saveQuery = insertTableSql(CONFIGURATION_TABLE);
        try (Connection conn = connect(); PreparedStatement statement = conn.prepareStatement(saveQuery)) {
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
            statement.setString(13, configuration.isPauseCaching() ? "1" : "0");
            statement.setString(14, configuration.isEmbeddedPlayer() ? "1" : "0");
            statement.execute();
        } catch (SQLException e) {
            throw new RuntimeException("Unable to execute query");
        }
    }
}
