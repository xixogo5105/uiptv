package com.uiptv.db;

import com.uiptv.model.Configuration;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EpgMigrationTest extends DbBackedTest {
    @Test void olderConfigurationMigratesOffAndPartialMigrationIsRepairable() throws Exception {
        Configuration existing = new Configuration(); existing.setServerPort("9999"); ConfigurationDb.get().save(existing);
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Configuration DROP COLUMN epgEnabled");
            statement.execute("ALTER TABLE Configuration DROP COLUMN globalEpgSource");
            statement.execute("ALTER TABLE Configuration DROP COLUMN epgMappingsJson");
            statement.execute("DELETE FROM schema_migrations WHERE name='0208_add_configuration_epg.sql'");
            DatabasePatchesUtils.applyPatches(connection);
        }
        Configuration loaded = ConfigurationDb.get().getConfiguration();
        assertFalse(loaded.isEpgEnabled()); assertEquals("", loaded.getGlobalEpgSource()); assertEquals("{}", loaded.getEpgMappingsJson()); assertEquals("9999", loaded.getServerPort());
        // Existing first column must not prevent the remaining columns being repaired.
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement()) {
            statement.execute("ALTER TABLE Configuration DROP COLUMN globalEpgSource");
            statement.execute("DELETE FROM schema_migrations WHERE name='0208_add_configuration_epg.sql'");
            DatabasePatchesUtils.applyPatches(connection);
        }
        assertEquals("", ConfigurationDb.get().getConfiguration().getGlobalEpgSource());
    }
}
