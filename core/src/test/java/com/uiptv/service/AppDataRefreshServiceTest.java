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
        long bookmarkRevisionBefore = BookmarkService.getInstance().getChangeRevision();

        AccountChangeListener accountListener = revision -> accountNotifications.incrementAndGet();
        ConfigurationChangeListener configurationListener = revision -> configurationNotifications.incrementAndGet();

        AccountService.getInstance().addChangeListener(accountListener);
        ConfigurationService.getInstance().addChangeListener(configurationListener);
        try {
            AppDataRefreshService.getInstance().refreshAfterDatabaseChange();
        } finally {
            AccountService.getInstance().removeChangeListener(accountListener);
            ConfigurationService.getInstance().removeChangeListener(configurationListener);
        }

        assertEquals(1, accountNotifications.get());
        assertEquals(1, configurationNotifications.get());
        assertTrue(BookmarkService.getInstance().getChangeRevision() > bookmarkRevisionBefore);
    }
}
