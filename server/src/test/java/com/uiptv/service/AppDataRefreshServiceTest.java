package com.uiptv.service;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AppDataRefreshServiceTest extends DbBackedTest {

    @Test
    void refreshAfterDatabaseChange_notifiesAccountBookmarkAndConfigurationListeners() {
        AtomicInteger accountNotifications = new AtomicInteger();
        AtomicInteger configurationNotifications = new AtomicInteger();
        long bookmarkRevisionBefore = BookmarkService.INSTANCE.getChangeRevision();

        AccountChangeListener accountListener = revision -> accountNotifications.incrementAndGet();
        ConfigurationChangeListener configurationListener = revision -> configurationNotifications.incrementAndGet();

        AccountService.INSTANCE.addChangeListener(accountListener);
        ConfigurationService.INSTANCE.addChangeListener(configurationListener);
        try {
            AppDataRefreshService.INSTANCE.refreshAfterDatabaseChange();
        } finally {
            AccountService.INSTANCE.removeChangeListener(accountListener);
            ConfigurationService.INSTANCE.removeChangeListener(configurationListener);
        }

        assertEquals(1, accountNotifications.get());
        assertEquals(1, configurationNotifications.get());
        assertTrue(BookmarkService.INSTANCE.getChangeRevision() > bookmarkRevisionBefore);
    }
}
