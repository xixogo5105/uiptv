package com.uiptv.db;

import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EpgMigrationTest extends DbBackedTest {
    @Test void upgradeDropsEveryEpgTableAndKeepsAccountsAndEnableFlag() throws Exception {
        com.uiptv.service.ConfigurationService.getInstance().save(com.uiptv.service.ConfigurationService.getInstance().read());
        try (var connection = SQLConnection.connect(); var statement = connection.createStatement()) {
            statement.execute("INSERT INTO Account(accountName,type,epg) VALUES('Keep me','M3U8_URL','account-only.xml')");
            statement.execute("UPDATE Configuration SET epgEnabled='1', globalEpgSource='obsolete', epgMappingsJson='obsolete'");
            for (String table : java.util.List.of("epg_source","epg_programme","epg_alias","epg_channel_mapping","epg_channel_icon","epg_name_exclusion","epg_shared_source","epg_change_state","epg_change_history","source_blacklist")) {
                statement.execute("CREATE TABLE IF NOT EXISTS "+table+"(retired TEXT)");
                statement.execute("INSERT INTO "+table+" VALUES('old guide data')");
            }
            statement.execute("DELETE FROM schema_migrations WHERE name='0222_remove_epg_caches.sql'");
            statement.execute("PRAGMA secure_delete=1");
            DatabasePatchesUtils.applyPatches(connection);
            DatabasePatchesUtils.applyPatches(connection);
            try (var value = statement.executeQuery("PRAGMA secure_delete")) { assertTrue(value.next()); assertEquals(1,value.getInt(1)); }
            try (var rows = statement.executeQuery("SELECT name FROM sqlite_master WHERE type='table' AND (name GLOB 'epg_*' OR name='source_blacklist')")) { assertFalse(rows.next()); }
            try (var rows = statement.executeQuery("SELECT accountName,epg FROM Account WHERE accountName='Keep me'")) { assertTrue(rows.next()); assertEquals("account-only.xml",rows.getString(2)); }
            try (var rows = statement.executeQuery("SELECT epgEnabled,globalEpgSource,epgMappingsJson FROM Configuration")) {
                assertTrue(rows.next()); assertEquals("1",rows.getString(1)); assertEquals("",rows.getString(2)); assertEquals("",rows.getString(3));
            }
        }
        assertTrue(java.util.Arrays.stream(DatabaseUtils.DbTable.values()).noneMatch(table -> table.getTableName().startsWith("epg_") || table.getTableName().equals("source_blacklist")));
    }
}
