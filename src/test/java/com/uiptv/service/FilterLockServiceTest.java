package com.uiptv.service;

import com.uiptv.model.Configuration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FilterLockServiceTest extends DbBackedTest {

    @AfterEach
    void clearLockSession() {
        FilterLockService.getInstance().clearUnlockSession();
    }

    @Test
    void initialPassword_isStoredHashed_andUnlocksWithCorrectPassword() {
        Configuration configuration = ConfigurationService.getInstance().read();

        FilterLockService.getInstance().applyInitialPassword(configuration, "correct horse battery");
        ConfigurationService.getInstance().save(configuration);

        Configuration saved = ConfigurationService.getInstance().read();
        assertNotNull(saved.getFilterLockHash());
        assertFalse(saved.getFilterLockHash().isBlank());
        assertFalse(saved.getFilterLockHash().contains("correct horse battery"));
        assertTrue(saved.getFilterLockHash().startsWith("pbkdf2-sha256$"));
        assertTrue(FilterLockService.getInstance().unlockWithPassword("correct horse battery"));
        assertTrue(FilterLockService.getInstance().isUnlocked());
        assertFalse(FilterLockService.getInstance().unlockWithPassword("wrong password"));
    }

    @Test
    void passwordChange_requiresCurrentPassword_andInvalidatesPreviousPassword() {
        Configuration configuration = ConfigurationService.getInstance().read();
        FilterLockService.getInstance().applyInitialPassword(configuration, "correct horse battery");
        ConfigurationService.getInstance().save(configuration);

        Configuration persisted = ConfigurationService.getInstance().read();
        assertThrows(IllegalArgumentException.class,
                () -> FilterLockService.getInstance().applyPasswordChange(
                        persisted,
                        "wrong password",
                        "new parent lock secret"
                ));

        FilterLockService.getInstance().applyPasswordChange(
                persisted,
                "correct horse battery",
                "new parent lock secret"
        );
        ConfigurationService.getInstance().save(persisted);

        FilterLockService.getInstance().clearUnlockSession();
        assertFalse(FilterLockService.getInstance().unlockWithPassword("correct horse battery"));
        assertTrue(FilterLockService.getInstance().unlockWithPassword("new parent lock secret"));
    }

    @Test
    void shortPassword_isRejected() {
        Configuration configuration = ConfigurationService.getInstance().read();

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> FilterLockService.getInstance().applyInitialPassword(configuration, "short"));

        assertEquals("filterLockPasswordTooShort", ex.getMessage());
    }

    @Test
    void sixCharacterPassword_isAccepted() {
        Configuration configuration = ConfigurationService.getInstance().read();

        FilterLockService.getInstance().applyInitialPassword(configuration, "secret");
        ConfigurationService.getInstance().save(configuration);

        assertTrue(FilterLockService.getInstance().unlockWithPassword("secret"));
    }

    @Test
    void clearPassword_requiresCurrentPassword_andRemovesStoredHash() {
        Configuration configuration = ConfigurationService.getInstance().read();
        FilterLockService.getInstance().applyInitialPassword(configuration, "secret");
        ConfigurationService.getInstance().save(configuration);

        Configuration persisted = ConfigurationService.getInstance().read();
        assertThrows(IllegalArgumentException.class,
                () -> FilterLockService.getInstance().clearPassword(persisted, "wrong"));

        FilterLockService.getInstance().clearPassword(persisted, "secret");
        ConfigurationService.getInstance().save(persisted);

        Configuration saved = ConfigurationService.getInstance().read();
        assertTrue(saved.getFilterLockHash() == null || saved.getFilterLockHash().isBlank());
        assertTrue(FilterLockService.getInstance().isUnlocked());
        assertFalse(FilterLockService.getInstance().hasPasswordConfigured());
    }
}
