package com.uiptv.service;

import com.uiptv.model.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterLockServiceTest extends DbBackedTest {

    @AfterEach
    void clearLockSession() {
        FilterLockService.INSTANCE.clearUnlockSession();
    }

    @Test
    void initialPassword_isStoredHashed_andUnlocksWithCorrectPassword() {
        Configuration configuration = ConfigurationService.INSTANCE.read();

        FilterLockService.INSTANCE.applyInitialPassword(configuration, "correct horse battery");
        ConfigurationService.INSTANCE.save(configuration);

        Configuration saved = ConfigurationService.INSTANCE.read();
        assertNotNull(saved.getFilterLockHash());
        assertFalse(saved.getFilterLockHash().isBlank());
        assertFalse(saved.getFilterLockHash().contains("correct horse battery"));
        assertTrue(saved.getFilterLockHash().startsWith("pbkdf2-sha256$"));
        assertTrue(FilterLockService.INSTANCE.unlockWithPassword("correct horse battery"));
        assertTrue(FilterLockService.INSTANCE.isUnlocked());
        assertFalse(FilterLockService.INSTANCE.unlockWithPassword("wrong password"));
    }

    @Test
    void passwordChange_requiresCurrentPassword_andInvalidatesPreviousPassword() {
        Configuration configuration = ConfigurationService.INSTANCE.read();
        FilterLockService.INSTANCE.applyInitialPassword(configuration, "correct horse battery");
        ConfigurationService.INSTANCE.save(configuration);

        Configuration persisted = ConfigurationService.INSTANCE.read();
        Runnable changeWithWrongPassword = () -> FilterLockService.INSTANCE.applyPasswordChange(
                persisted,
                "wrong password",
                "new parent lock secret"
        );
        assertThrows(IllegalArgumentException.class, changeWithWrongPassword::run);

        FilterLockService.INSTANCE.applyPasswordChange(
                persisted,
                "correct horse battery",
                "new parent lock secret"
        );
        ConfigurationService.INSTANCE.save(persisted);

        FilterLockService.INSTANCE.clearUnlockSession();
        assertFalse(FilterLockService.INSTANCE.unlockWithPassword("correct horse battery"));
        assertTrue(FilterLockService.INSTANCE.unlockWithPassword("new parent lock secret"));
    }

    @Test
    void shortPassword_isRejected() {
        Configuration configuration = ConfigurationService.INSTANCE.read();

        Runnable applyShortPassword = () -> FilterLockService.INSTANCE.applyInitialPassword(configuration, "short");
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, applyShortPassword::run);

        assertEquals("filterLockPasswordTooShort", ex.getMessage());
    }

    @Test
    void sixCharacterPassword_isAccepted() {
        Configuration configuration = ConfigurationService.INSTANCE.read();

        FilterLockService.INSTANCE.applyInitialPassword(configuration, "secret");
        ConfigurationService.INSTANCE.save(configuration);

        assertTrue(FilterLockService.INSTANCE.unlockWithPassword("secret"));
    }

    @Test
    void clearPassword_requiresCurrentPassword_andRemovesStoredHash() {
        Configuration configuration = ConfigurationService.INSTANCE.read();
        FilterLockService.INSTANCE.applyInitialPassword(configuration, "secret");
        ConfigurationService.INSTANCE.save(configuration);

        Configuration persisted = ConfigurationService.INSTANCE.read();
        Runnable clearWithWrongPassword = () -> FilterLockService.INSTANCE.clearPassword(persisted, "wrong");
        assertThrows(IllegalArgumentException.class, clearWithWrongPassword::run);

        FilterLockService.INSTANCE.clearPassword(persisted, "secret");
        ConfigurationService.INSTANCE.save(persisted);

        Configuration saved = ConfigurationService.INSTANCE.read();
        assertTrue(saved.getFilterLockHash() == null || saved.getFilterLockHash().isBlank());
        assertTrue(FilterLockService.INSTANCE.isUnlocked());
        assertFalse(FilterLockService.INSTANCE.hasPasswordConfigured());
      }

      @Test
    void filterLockUnlockDurationDefaultsTo15Minutes() {
        Configuration configuration = ConfigurationService.INSTANCE.read();
        assertEquals("15", configuration.getFilterLockUnlockDurationMinutes());
      }

      @Test
    void filterLockUnlockDurationCanBeSetToCustomValue() {
        Configuration configuration = ConfigurationService.INSTANCE.read();
        configuration.setFilterLockUnlockDurationMinutes("30");
        ConfigurationService.INSTANCE.save(configuration);

        Configuration saved = ConfigurationService.INSTANCE.read();
        assertEquals("30", saved.getFilterLockUnlockDurationMinutes());
      }

      @Test
    void filterLockUnlockDurationPersistsWithCorrectValues() {
        Configuration configuration = ConfigurationService.INSTANCE.read();
        configuration.setFilterLockUnlockDurationMinutes("60");
        ConfigurationService.INSTANCE.save(configuration);

        Configuration saved = ConfigurationService.INSTANCE.read();
        assertEquals("60", saved.getFilterLockUnlockDurationMinutes());
      }
    }
