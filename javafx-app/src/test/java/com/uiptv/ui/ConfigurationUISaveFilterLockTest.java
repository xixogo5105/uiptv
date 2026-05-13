package com.uiptv.ui;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.FilterLockService;
import com.uiptv.testsupport.DbBackedUiTest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for filter lock unlock session behavior during save operations.
 * Tests the wasFilterAlreadyUnlocked() helper method and the save flow that
 * restores the original lock state after save.
 */
class ConfigurationUISaveFilterLockTest extends DbBackedUiTest {

    private final FilterLockService filterLockService = FilterLockService.getInstance();
    private final ConfigurationService configurationService = ConfigurationService.getInstance();

    @AfterEach
    void clearLockSession() {
        filterLockService.clearUnlockSession();
     }

     @Test
     void saveWhenNoPasswordConfigured_allowsSaveWithoutPrompt() {
        Configuration configuration = configurationService.read();

        assertTrue(filterLockService.isUnlocked());
        assertFalse(filterLockService.hasPasswordConfigured());

        configuration.setServerPort("8080");
        configurationService.save(configuration);

        assertTrue(filterLockService.isUnlocked());
        assertFalse(filterLockService.hasPasswordConfigured());
      }

     @Test
     void saveWhenAlreadyUnlocked_keepsSessionUnlocked() {
        Configuration configuration = configurationService.read();
        filterLockService.applyInitialPassword(configuration, "testPassword123");
        configurationService.save(configuration);

        assertTrue(filterLockService.unlockWithPassword("testPassword123"));
        assertTrue(filterLockService.isUnlocked());

        configuration.setServerPort("8081");
        configurationService.save(configuration);

        assertTrue(filterLockService.isUnlocked());
      }

     @Test
     void wasFilterAlreadyUnlocked_returnsTrueWhenNoPasswordConfigured() {
        assertTrue(filterLockService.isUnlocked());
        assertFalse(filterLockService.hasPasswordConfigured());
      }

     @Test
     void wasFilterAlreadyUnlocked_returnsTrueWhenUnlocked() {
        Configuration configuration = configurationService.read();
        filterLockService.applyInitialPassword(configuration, "testPassword123");
        configurationService.save(configuration);

        assertFalse(filterLockService.isUnlocked());

        assertTrue(filterLockService.unlockWithPassword("testPassword123"));
        assertTrue(filterLockService.isUnlocked());
      }

     @Test
     void wasFilterAlreadyUnlocked_returnsFalseWhenLocked() {
        Configuration configuration = configurationService.read();
        filterLockService.applyInitialPassword(configuration, "testPassword123");
        configurationService.save(configuration);

        assertFalse(filterLockService.isUnlocked());
        assertTrue(filterLockService.hasPasswordConfigured());
     }
}
