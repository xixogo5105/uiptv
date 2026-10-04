package com.uiptv.db;

import com.uiptv.model.Configuration;
import com.uiptv.model.ThemeMode;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the three-state theme selection round trip through the {@code themeMode} column. The legacy
 * {@code darkTheme} flag is gone as of migration 0207.
 */
class ConfigurationDbThemeModeTest extends DbBackedTest {

    @Test
    void systemSelectionRoundTripsThroughThemeMode() {
        ConfigurationDb configurationDb = ConfigurationDb.get();
        Configuration configuration = configurationDb.getConfiguration();
        configuration.applyThemeMode(ThemeMode.SYSTEM);

        configurationDb.save(configuration);

        assertEquals(ThemeMode.SYSTEM, configurationDb.getConfiguration().resolveThemeMode());
    }

    @Test
    void explicitModesRoundTripThroughThemeMode() {
        ConfigurationDb configurationDb = ConfigurationDb.get();

        Configuration configuration = configurationDb.getConfiguration();
        configuration.applyThemeMode(ThemeMode.DARK);
        configurationDb.save(configuration);
        assertEquals(ThemeMode.DARK, configurationDb.getConfiguration().resolveThemeMode());

        Configuration reloadedDark = configurationDb.getConfiguration();
        reloadedDark.applyThemeMode(ThemeMode.LIGHT);
        configurationDb.save(reloadedDark);
        assertEquals(ThemeMode.LIGHT, configurationDb.getConfiguration().resolveThemeMode());
    }

    @Test
    void aSystemSelectionSurvivesAnUnrelatedSave() {
        ConfigurationDb configurationDb = ConfigurationDb.get();
        Configuration configuration = configurationDb.getConfiguration();
        configuration.applyThemeMode(ThemeMode.SYSTEM);
        configurationDb.save(configuration);

        Configuration reloaded = configurationDb.getConfiguration();
        reloaded.setServerPort("9999");
        configurationDb.save(reloaded);

        assertEquals(ThemeMode.SYSTEM, configurationDb.getConfiguration().resolveThemeMode());
    }

    @Test
    void anUnsetColumnValueResolvesToTheLightTheme() throws SQLException {
        ensureConfigurationRowExists();
        writeThemeMode(null);

        assertEquals(ThemeMode.LIGHT, ConfigurationDb.get().getConfiguration().resolveThemeMode());
    }

    @Test
    void theColumnStoresTheModeWord() throws SQLException {
        ConfigurationDb configurationDb = ConfigurationDb.get();
        ensureConfigurationRowExists();

        for (ThemeMode mode : ThemeMode.values()) {
            Configuration configuration = configurationDb.getConfiguration();
            configuration.applyThemeMode(mode);
            configurationDb.save(configuration);
            assertEquals(mode.persistedValue(), readThemeMode());
        }
    }

    @Test
    void theLegacyFlagColumnIsGone() throws SQLException {
        ensureConfigurationRowExists();

        assertFalse(columnExists("darkTheme"), "Migration 0207 must drop the legacy flag");
        assertTrue(columnExists("themeMode"));
    }

    private void ensureConfigurationRowExists() {
        ConfigurationDb configurationDb = ConfigurationDb.get();
        if (configurationDb.getConfiguration().getDbId() == null) {
            configurationDb.save(new Configuration());
        }
    }

    private String readThemeMode() throws SQLException {
        try (Connection connection = SQLConnection.connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("SELECT themeMode FROM Configuration LIMIT 1")) {
            return resultSet.next() ? resultSet.getString(1) : null;
        }
    }

    private void writeThemeMode(String value) throws SQLException {
        try (Connection connection = SQLConnection.connect();
             PreparedStatement statement = connection.prepareStatement("UPDATE Configuration SET themeMode = ?")) {
            if (value == null) {
                statement.setNull(1, java.sql.Types.VARCHAR);
            } else {
                statement.setString(1, value);
            }
            statement.executeUpdate();
        }
    }

    private boolean columnExists(String column) throws SQLException {
        try (Connection connection = SQLConnection.connect();
             Statement statement = connection.createStatement();
             ResultSet resultSet = statement.executeQuery("PRAGMA table_info(Configuration)")) {
            while (resultSet.next()) {
                if (column.equalsIgnoreCase(resultSet.getString("name"))) {
                    return true;
                }
            }
            return false;
        }
    }
}
