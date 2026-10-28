package com.uiptv.util;

import com.uiptv.db.DatabaseUtils;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SQLiteTableSyncCoverageTest extends DbBackedTest {

    @Test
    void replacementConstraintFailureRollsBackDeletion() throws Exception {
        Path source = tempDir.resolve("constraint-source.db");
        Path target = tempDir.resolve("constraint-target.db");
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE Account(id INTEGER PRIMARY KEY, value TEXT)");
            statement.execute("INSERT INTO Account VALUES (1, NULL)");
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE Account(id INTEGER PRIMARY KEY, value TEXT NOT NULL)");
            statement.execute("INSERT INTO Account VALUES (2, 'preserved')");
        }
        org.junit.jupiter.api.Assertions.assertThrows(java.sql.SQLException.class, () ->
                SQLiteTableSync.replaceTable(source.toString(), target.toString(), DatabaseUtils.DbTable.ACCOUNT_TABLE));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement statement = connection.createStatement(); ResultSet result = statement.executeQuery("SELECT * FROM Account")) {
            assertTrue(result.next()); assertEquals(2, result.getInt("id"));
            assertEquals("preserved", result.getString("value"));
        }
    }

    @Test
    void schemaMetadataNamesAreQuotedWhenCopyingAndReplacing() throws Exception {
        Path source = tempDir.resolve("quoted-source.db");
        Path target = tempDir.resolve("quoted-target.db");
        for (Path database : java.util.List.of(source, target)) {
            try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + database);
                 Statement statement = connection.createStatement()) {
                statement.execute("CREATE TABLE Account (id INTEGER PRIMARY KEY, \"select\" TEXT, \"space name\" TEXT, \"quote\"\"name\" TEXT)");
            }
        }
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + source);
             Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO Account VALUES (1, 'reserved', 'space', 'quote')");
        }
        assertEquals(1, SQLiteTableSync.syncTables(source.toString(), target.toString(), DatabaseUtils.DbTable.ACCOUNT_TABLE));
        assertEquals(1, SQLiteTableSync.replaceTable(source.toString(), target.toString(), DatabaseUtils.DbTable.ACCOUNT_TABLE));
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + target);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT * FROM Account")) {
            assertTrue(result.next());
            assertEquals("reserved", result.getString("select"));
            assertEquals("space", result.getString("space name"));
            assertEquals("quote", result.getString("quote\"name"));
        }
    }

    @Test
    void syncAndReplaceTablesCopyRowsAndHandleEmptyTables() throws Exception {
        Path source = tempDir.resolve("source.db");
        Path target = tempDir.resolve("target.db");
        createSchema(source);
        createSchema(target);
        insertAccount(source, 1, "source-one");
        insertAccount(source, 2, "source-two");
        insertAccount(target, 1, "old-one");
        insertAccount(target, 3, "old-three");

        assertEquals(2, SQLiteTableSync.syncTables(source.toString(), target.toString(), DatabaseUtils.DbTable.ACCOUNT_TABLE));
        assertEquals(3, count(target, "Account"));
        assertEquals("source-one", accountName(target, 1));

        assertEquals(2, SQLiteTableSync.replaceTable(source.toString(), target.toString(), DatabaseUtils.DbTable.ACCOUNT_TABLE));
        assertEquals(2, count(target, "Account"));
        assertEquals(0, countByName(target, "Account", "old-three"));

        Path empty = tempDir.resolve("empty.db");
        createSchema(empty);
        assertEquals(0, SQLiteTableSync.replaceTable(empty.toString(), target.toString(), DatabaseUtils.DbTable.ACCOUNT_TABLE));
        assertEquals(0, count(target, "Account"));
    }

    @Test
    void publishedSelectionsRemapAccountsAndSkipUnknownRows() throws Exception {
        Path source = tempDir.resolve("selection-source.db");
        Path target = tempDir.resolve("selection-target.db");
        createSchema(source);
        createSchema(target);
        insertAccount(source, 1, "provider");
        insertAccount(source, 2, "missing");
        insertAccount(target, 7, "provider");
        insertAccount(target, 8, "other");

        insertSelection(source, "PublishedM3uSelection", "1");
        insertSelection(source, "PublishedM3uSelection", "2");
        insertSelection(source, "PublishedM3uSelection", "__bookmarks__");
        insertSelection(target, "PublishedM3uSelection", "8");
        assertEquals(2, SQLiteTableSync.syncPublishedM3uSelections(source.toString(), target.toString()));
        assertEquals(2, count(target, "PublishedM3uSelection"));
        assertTrue(hasAccount(target, "PublishedM3uSelection", "7"));
        assertTrue(hasAccount(target, "PublishedM3uSelection", "__bookmarks__"));

        insertCategorySelection(source, "1", "News", "1");
        insertCategorySelection(source, "2", "Ignored", "0");
        assertEquals(1, SQLiteTableSync.syncPublishedM3uCategorySelections(source.toString(), target.toString()));
        assertEquals(1, count(target, "PublishedM3uCategorySelection"));
        assertEquals("7", firstValue(target, "PublishedM3uCategorySelection", "accountId"));

        insertChannelSelection(source, "__bookmarks__", "Sports", "channel-1", "1");
        insertChannelSelection(source, "1", "News", "channel-2", "0");
        assertEquals(2, SQLiteTableSync.syncPublishedM3uChannelSelections(source.toString(), target.toString()));
        assertEquals(2, count(target, "PublishedM3uChannelSelection"));
    }

    @Test
    void configurationSyncReturnsFalseWhenTheSourceHasNoConfigurationRow() throws Exception {
        Path source = tempDir.resolve("empty-config-source.db");
        Path target = tempDir.resolve("config-target.db");
        createSchema(source);
        createSchema(target);

        assertFalse(SQLiteTableSync.syncConfiguration(source.toString(), target.toString(), false));
    }

    private void createSchema(Path path) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement statement = connection.createStatement()) {
            for (DatabaseUtils.DbTable table : DatabaseUtils.DbTable.values()) {
                statement.execute(DatabaseUtils.createTableSql(table));
            }
        }
    }

    private void insertAccount(Path path, int id, String name) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement statement = connection.prepareStatement("INSERT INTO Account (id, accountName) VALUES (?, ?)")) {
            statement.setInt(1, id);
            statement.setString(2, name);
            statement.executeUpdate();
        }
    }

    private void insertSelection(Path path, String table, String accountId) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement statement = connection.prepareStatement("INSERT INTO " + table + " (accountId) VALUES (?)")) {
            statement.setString(1, accountId);
            statement.executeUpdate();
        }
    }

    private void insertCategorySelection(Path path, String accountId, String category, String selected) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO PublishedM3uCategorySelection (accountId, categoryName, selected) VALUES (?, ?, ?)")) {
            statement.setString(1, accountId);
            statement.setString(2, category);
            statement.setString(3, selected);
            statement.executeUpdate();
        }
    }

    private void insertChannelSelection(Path path, String accountId, String category, String channelId, String selected) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement statement = connection.prepareStatement(
                     "INSERT INTO PublishedM3uChannelSelection (accountId, categoryName, channelId, selected) VALUES (?, ?, ?, ?)")) {
            statement.setString(1, accountId);
            statement.setString(2, category);
            statement.setString(3, channelId);
            statement.setString(4, selected);
            statement.executeUpdate();
        }
    }

    private int count(Path path, String table) throws Exception {
        return countByQuery(path, "SELECT COUNT(*) FROM " + table);
    }

    private int countByName(Path path, String table, String name) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement statement = connection.prepareStatement("SELECT COUNT(*) FROM " + table + " WHERE accountName = ?")) {
            statement.setString(1, name);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getInt(1) : 0;
            }
        }
    }

    private int countByQuery(Path path, String sql) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery(sql)) {
            return result.next() ? result.getInt(1) : 0;
        }
    }

    private String accountName(Path path, int id) throws Exception {
        return firstValue(path, "Account WHERE id = " + id, "accountName");
    }

    private String firstValue(Path path, String tableExpression, String column) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             Statement statement = connection.createStatement();
             ResultSet result = statement.executeQuery("SELECT " + column + " FROM " + tableExpression + " LIMIT 1")) {
            return result.next() ? result.getString(1) : null;
        }
    }

    private boolean hasAccount(Path path, String table, String accountId) throws Exception {
        try (Connection connection = DriverManager.getConnection("jdbc:sqlite:" + path);
             PreparedStatement statement = connection.prepareStatement("SELECT 1 FROM " + table + " WHERE accountId = ?")) {
            statement.setString(1, accountId);
            try (ResultSet result = statement.executeQuery()) {
                return result.next();
            }
        }
    }
}
