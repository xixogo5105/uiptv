package com.uiptv.db;

import com.uiptv.application.EpgProgramme;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SharedEpgCacheDbCoverageTest extends DbBackedTest {
    @Test
    void readResolvesAliasesIncludesIconsAndExpandsDescriptionsWhenFull() throws Exception {
        long now = System.currentTimeMillis();
        EpgCacheDb cache = new EpgCacheDb();
        cache.replaceSource("shared-source", Map.of("guide.in", List.of(
                new EpgProgramme("Morning News", "A detailed description", now - 1_000, now + 60_000)
        )), Map.of("Example News", "guide.in"), Map.of("example news", "guide.in"),
                Map.of("guide.in", "https://img.example/icon.png"), now, now + 60_000);
        addSharedSource("shared-source", 10, "IN", "EN");
        SharedEpgCacheDb.invalidate();

        var query = new SharedEpgCacheDb.Query("", "Example News", false);
        var shortResult = new SharedEpgCacheDb().read(List.of(query), now, false).get(query);
        assertEquals("Morning News", shortResult.programmes().getFirst().title());
        assertEquals("", shortResult.programmes().getFirst().description());
        assertEquals("https://img.example/icon.png", shortResult.icon());

        var fullResult = new SharedEpgCacheDb().read(List.of(query), now, true).get(query);
        assertEquals("A detailed description", fullResult.programmes().getFirst().description());
        assertEquals(SharedEpgCacheDb.ResolutionStatus.MATCHED,
                new SharedEpgCacheDb().diagnostics(List.of(query), now).get(query).status());
    }

    @Test
    void diagnosticsDistinguishNoSourceFromARejectedTerritoryMatch() throws Exception {
        long now = System.currentTimeMillis();
        SharedEpgCacheDb db = new SharedEpgCacheDb();
        var absent = new SharedEpgCacheDb.Query("", "Missing Channel", false);
        var absentDiagnostic = db.diagnostics(List.of(absent), now).get(absent);
        assertEquals(SharedEpgCacheDb.ResolutionStatus.NO_SOURCE_COVERAGE, absentDiagnostic.status());

        EpgCacheDb cache = new EpgCacheDb();
        cache.replaceSource("regional-source", Map.of("guide.in", List.of(
                new EpgProgramme("Regional", "", now - 1_000, now + 60_000)
        )), Map.of("Example Sports", "guide.in"), Map.of(), now, now + 60_000);
        addSharedSource("regional-source", 10, "IN", "EN");
        SharedEpgCacheDb.invalidate();

        var rejected = new SharedEpgCacheDb.Query("", "Example Sports (UK)", false);
        assertEquals(SharedEpgCacheDb.ResolutionStatus.MATCHING_FAILED,
                db.diagnostics(List.of(rejected), now).get(rejected).status());
        assertTrue(db.read(List.of(rejected), now, false).isEmpty());
    }

    private void addSharedSource(String sourceId, int priority, String countries, String languages) throws Exception {
        try (Connection connection = SQLConnection.connect();
             var insert = connection.prepareStatement("INSERT INTO epg_shared_source VALUES(?,?,?,?)")) {
            insert.setString(1, sourceId);
            insert.setInt(2, priority);
            insert.setString(3, countries);
            insert.setString(4, languages);
            insert.executeUpdate();
        }
    }
}
