package com.uiptv.db;

import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Guards the two-step theme schema change: 0206 copies the legacy flag into {@code themeMode}, 0207 drops
 * the flag. The order is what makes the backfill possible, and it is only expressed by the migration list.
 */
class ThemeModeMigrationTest extends DbBackedTest {

    private static final String ADD_THEME_MODE = "0206_add_configuration_theme_mode.sql";
    private static final String DROP_DARK_THEME = "0207_drop_configuration_dark_theme.sql";

    @Test
    void theDarkThemeDropRunsAfterTheThemeModeBackfill() throws Exception {
        List<String> migrations = migrationNames();

        int addIndex = migrations.indexOf(ADD_THEME_MODE);
        int dropIndex = migrations.indexOf(DROP_DARK_THEME);

        assertTrue(addIndex >= 0, "Missing migration " + ADD_THEME_MODE);
        assertTrue(dropIndex >= 0, "Missing migration " + DROP_DARK_THEME);
        assertTrue(addIndex < dropIndex,
                ADD_THEME_MODE + " must run before " + DROP_DARK_THEME + ": the backfill reads the flag it drops");
    }

    @Test
    void theMigratedSchemaHasNoDarkThemeColumn() throws SQLException {
        // Applies to a database created from the current baseline, where the column never existed.
        assertFalse(columnExists("darkTheme"), "The legacy flag must be gone after the migration chain");
        assertTrue(columnExists("themeMode"));
    }

    @Test
    void rerunningTheDropMigrationRemovesALegacyColumnThatIsStillPresent() throws SQLException {
        // A database upgraded from a build that predates the migration, so the column exists and the drop has
        // not run yet. Clearing the record forces the runner to execute it again.
        execute("ALTER TABLE Configuration ADD COLUMN darkTheme TEXT");
        execute("UPDATE Configuration SET darkTheme = '1'");
        execute("DELETE FROM schema_migrations WHERE name = '" + DROP_DARK_THEME + "'");

        try (Connection connection = SQLConnection.connect()) {
            DatabasePatchesUtils.applyPatches(connection);
        }

        assertFalse(columnExists("darkTheme"), "The drop migration must remove the legacy column");
    }

    private List<String> migrationNames() throws Exception {
        List<String> names = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream("/db/migrations/migrations.txt")) {
            if (in == null) {
                throw new IllegalStateException("migrations.txt is not on the classpath");
            }
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    String trimmed = line.trim();
                    if (!trimmed.isEmpty() && !trimmed.startsWith("#")) {
                        names.add(trimmed);
                    }
                }
            }
        }
        return names;
    }

    private void execute(String sql) throws SQLException {
        try (Connection connection = SQLConnection.connect();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate(sql);
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
