package com.uiptv.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeModeTest {

    @Test
    void persistedValuesAreReadableWords() {
        assertEquals("light", ThemeMode.LIGHT.persistedValue());
        assertEquals("dark", ThemeMode.DARK.persistedValue());
        assertEquals(ThemeMode.SYSTEM_VALUE, ThemeMode.SYSTEM.persistedValue());
    }

    @Test
    void fromPersistedReadsTheStoredWords() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersisted("light"));
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersisted("dark"));
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPersisted("system"));
    }

    @Test
    void fromPersistedTreatsAnAbsentOrUnknownValueAsLight() {
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersisted(null));
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersisted(""));
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersisted("   "));
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersisted("1"));
        assertEquals(ThemeMode.LIGHT, ThemeMode.fromPersisted("unexpected"));
    }

    @Test
    void fromPersistedIsCaseAndPaddingTolerant() {
        assertEquals(ThemeMode.SYSTEM, ThemeMode.fromPersisted(" SYSTEM "));
        assertEquals(ThemeMode.DARK, ThemeMode.fromPersisted(" Dark "));
    }

    @Test
    void nextCyclesLightDarkSystem() {
        assertEquals(ThemeMode.DARK, ThemeMode.LIGHT.next());
        assertEquals(ThemeMode.SYSTEM, ThemeMode.DARK.next());
        assertEquals(ThemeMode.LIGHT, ThemeMode.SYSTEM.next());
    }

    @Test
    void onlySystemNeedsTheOperatingSystemScheme() {
        assertTrue(ThemeMode.SYSTEM.isSystem());
        assertFalse(ThemeMode.LIGHT.isSystem());
        assertFalse(ThemeMode.DARK.isSystem());
    }

    @Test
    void configurationStoresAndResolvesTheSelection() {
        Configuration configuration = new Configuration();

        configuration.applyThemeMode(ThemeMode.SYSTEM);
        assertEquals(ThemeMode.SYSTEM, configuration.resolveThemeMode());

        configuration.applyThemeMode(ThemeMode.DARK);
        assertEquals(ThemeMode.DARK, configuration.resolveThemeMode());

        configuration.applyThemeMode(ThemeMode.LIGHT);
        assertEquals(ThemeMode.LIGHT, configuration.resolveThemeMode());
    }

    @Test
    void aNullSelectionIsStoredAsLight() {
        Configuration configuration = new Configuration();

        configuration.applyThemeMode(null);

        assertEquals("light", configuration.getThemeMode());
        assertEquals(ThemeMode.LIGHT, configuration.resolveThemeMode());
    }

    @Test
    void aConfigurationWithoutAStoredValueResolvesToLight() {
        assertEquals(ThemeMode.LIGHT, new Configuration().resolveThemeMode());
    }
}
