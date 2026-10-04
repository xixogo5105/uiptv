package com.uiptv.service;

import com.uiptv.db.DataColumn;
import com.uiptv.db.DatabasePatchesUtils;
import com.uiptv.db.DatabaseUtils;
import com.uiptv.util.SQLiteTableSync;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Proves that Configuration values survive an Android-to-desktop restore intact.
 *
 * <p>The desktop can restore a database produced on Android, and the snapshot is applied as a whole-file
 * clone, so the contract is an exact clone rather than a filtered subset. This walks the real production path
 * ({@link SQLiteTableSync#syncConfiguration}) with a distinct value in every declared column and asserts each
 * one arrives at the target.
 *
 * <p>Exactly two groups are expected not to arrive:
 * <ul>
 *   <li>{@code filterLockHash} is a secret-derived value; the receiving device keeps its own.</li>
 *   <li>The four player-path columns name executables installed on one machine. They are only copied when the
 *       caller asks for external player paths, which a restore does not.</li>
 * </ul>
 *
 * <p>The column list is read from {@link DatabaseUtils#dbStructure} rather than restated, so a column added
 * later is covered here automatically and a genuine regression in column coverage fails the build.
 */
class ConfigurationSyncColumnParityTest extends DbBackedTest {

    private static final List<String> NEVER_TRANSFERRED = List.of(
            "playerPath1",
            "playerPath2",
            "playerPath3",
            "defaultPlayerPath",
            "filterLockHash"
    );

    /** The row key. Not a synced value, so it is excluded from the data columns under test. */
    private static final String ROW_KEY = "id";

    @Test
    void everyConfigurationColumnSurvivesARestore() throws Exception {
        Path source = tempDir.resolve("android-source.db");
        Path target = tempDir.resolve("desktop-target.db");
        createSchema(source);
        createSchema(target);

        List<String> columns = configurationColumns();
        seedDistinctValues(source, columns);
        seedDistinctValues(target, columns, "target");

        assertTrue(SQLiteTableSync.syncConfiguration(source.toString(), target.toString(), false),
                "Configuration sync reported no work done");

        List<String> dataColumns = columns.stream().filter(c -> !ROW_KEY.equals(c)).toList();
        List<String> transferred = dataColumns.stream().filter(c -> !NEVER_TRANSFERRED.contains(c)).toList();
        List<String> uncovered = dataColumns.stream()
                .filter(c -> !transferred.contains(c) && !NEVER_TRANSFERRED.contains(c))
                .toList();
        assertTrue(uncovered.isEmpty(),
                "Columns are neither transferred nor documented as excluded: " + uncovered);

        for (String column : transferred) {
            assertEquals(sourceValue(column), readConfigurationColumn(target, column),
                    "Configuration column '" + column + "' did not survive the restore intact");
        }

        for (String column : NEVER_TRANSFERRED) {
            assertEquals(targetValue(column), readConfigurationColumn(target, column),
                    "Configuration column '" + column + "' should have been kept from the target, not overwritten");
        }
    }

    @Test
    void externalPlayerPathsAreCopiedOnlyWhenTheCallerOptsIn() throws Exception {
        Path source = tempDir.resolve("android-source-paths.db");
        Path target = tempDir.resolve("desktop-target-paths.db");
        createSchema(source);
        createSchema(target);

        List<String> columns = configurationColumns();
        seedDistinctValues(source, columns);
        seedDistinctValues(target, columns, "target");

        assertTrue(SQLiteTableSync.syncConfiguration(source.toString(), target.toString(), true),
                "Configuration sync reported no work done");

        for (String column : List.of("playerPath1", "playerPath2", "playerPath3", "defaultPlayerPath")) {
            assertEquals(sourceValue(column), readConfigurationColumn(target, column),
                    "Player path column '" + column + "' should be copied when external paths are requested");
        }
        assertEquals(targetValue("filterLockHash"), readConfigurationColumn(target, "filterLockHash"),
                "filterLockHash must stay local even when external player paths are copied");
    }

    private String sourceValue(String column) {
        return "android-" + column;
    }

    private String targetValue(String column) {
        return "desktop-" + column;
    }

    @SuppressWarnings("unchecked")
    private List<String> configurationColumns() throws Exception {
        Field structureField = DatabaseUtils.class.getDeclaredField("dbStructure");
        structureField.setAccessible(true);
        Map<String, List<DataColumn>> dbStructure =
                (Map<String, List<DataColumn>>) structureField.get(null);
        List<DataColumn> configuration =
                dbStructure.get(DatabaseUtils.DbTable.CONFIGURATION_TABLE.getTableName());
        assertTrue(configuration != null && !configuration.isEmpty(),
                "DatabaseUtils.dbStructure declares no Configuration columns");
        List<String> columns = new ArrayList<>();
        for (DataColumn column : configuration) {
            columns.add(column.getColumnName());
        }
        return columns;
    }

    private void createSchema(Path dbPath) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = conn.createStatement()) {
            DatabasePatchesUtils.applyBaseline(conn);
        }
    }

    private void seedDistinctValues(Path dbPath, List<String> columns) throws SQLException {
        seedDistinctValues(dbPath, columns, "");
    }

    /** Writes a recognisable value into every column so a silent drop cannot go unnoticed. */
    private void seedDistinctValues(Path dbPath, List<String> columns, String suffix) throws SQLException {
        Map<String, String> values = new LinkedHashMap<>();
        for (String column : columns) {
            if ("id".equals(column)) {
                continue;
            }
            values.put(column, suffix.isEmpty() ? sourceValue(column) : targetValue(column));
        }

        String quotedColumns = String.join(", ", values.keySet());
        String placeholders = values.keySet().stream().map(c -> "?").reduce((a, b) -> a + "," + b).orElse("");
        String sql = "INSERT INTO Configuration (" + quotedColumns + ") VALUES (" + placeholders + ")";

        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             PreparedStatement ps = conn.prepareStatement(sql)) {
            int index = 1;
            for (String value : values.values()) {
                ps.setString(index++, value);
            }
            ps.executeUpdate();
        }
    }

    private String readConfigurationColumn(Path dbPath, String column) throws SQLException {
        try (Connection conn = DriverManager.getConnection("jdbc:sqlite:" + dbPath);
             Statement statement = conn.createStatement();
             ResultSet rs = statement.executeQuery("SELECT \"" + column + "\" FROM Configuration LIMIT 1")) {
            return rs.next() ? rs.getString(1) : null;
        }
    }
}