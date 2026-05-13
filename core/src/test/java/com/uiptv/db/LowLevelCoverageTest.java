package com.uiptv.db;

import com.uiptv.model.ThemeCssOverride;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LowLevelCoverageTest extends DbBackedTest {

    @Test
    void databaseAccessException_preservesMessageAndCause() {
        DatabaseAccessException withoutCause = new DatabaseAccessException("plain");
        IllegalStateException cause = new IllegalStateException("boom");
        DatabaseAccessException withCause = new DatabaseAccessException("wrapped", cause);

        assertEquals("plain", withoutCause.getMessage());
        assertEquals("wrapped", withCause.getMessage());
        assertEquals(cause, withCause.getCause());
    }

    @Test
    void themeCssOverrideDb_readsDefaults_andSupportsInsertAndUpdate() {
        ThemeCssOverrideDb db = ThemeCssOverrideDb.get();

        ThemeCssOverride empty = db.read();
        assertNotNull(empty);

        ThemeCssOverride first = new ThemeCssOverride();
        first.setLightThemeCssName("light.css");
        first.setLightThemeCssContent(".root { -fx-base: white; }");
        db.save(first);

        ThemeCssOverride stored = db.read();
        assertEquals("light.css", stored.getLightThemeCssName());
        assertTrue(stored.getUpdatedAt() != null && !stored.getUpdatedAt().isBlank());

        ThemeCssOverride update = new ThemeCssOverride();
        update.setLightThemeCssName("light-updated.css");
        update.setDarkThemeCssName("dark.css");
        update.setDarkThemeCssContent(".root { -fx-base: black; }");
        update.setUpdatedAt("123");
        db.save(update);

        ThemeCssOverride updated = db.read();
        assertEquals("light-updated.css", updated.getLightThemeCssName());
        assertEquals("dark.css", updated.getDarkThemeCssName());
        assertEquals("123", updated.getUpdatedAt());
    }
}
