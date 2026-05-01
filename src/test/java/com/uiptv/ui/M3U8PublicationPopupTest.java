package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Configuration;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.util.AccountType;
import javafx.application.Platform;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.service.M3U8PublicationService.BOOKMARKS_PLAYLIST_ACCOUNT_ID;
import static com.uiptv.service.M3U8PublicationService.BOOKMARKS_PLAYLIST_NAME;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3U8PublicationPopupTest extends DbBackedTest {
    private static final AtomicBoolean FX_STARTED = new AtomicBoolean(false);

    @BeforeAll
    static void initJavaFx() throws Exception {
        if (FX_STARTED.compareAndSet(false, true)) {
            CountDownLatch latch = new CountDownLatch(1);
            try {
                Platform.startup(latch::countDown);
                if (!latch.await(5, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("JavaFX platform failed to start");
                }
            } catch (IllegalStateException e) {
                if (!e.getMessage().contains("Toolkit already initialized")) {
                    throw e;
                }
            }
        }
    }

    @Test
    void bookmarksPlaylist_isListedFirstWithoutDetailsToggleAndRemainsSelectable() throws Exception {
        Bookmark bookmark = new Bookmark("acc", "", "ch-1", "Bookmark One", "cmd1", "http://portal", null);
        com.uiptv.service.BookmarkService.getInstance().save(bookmark);

        java.io.File playlistFile = tempDir.resolve("popup-test.m3u8").toFile();
        try (FileWriter writer = new FileWriter(playlistFile)) {
            writer.write("#EXTM3U\n#EXTINF:-1,Channel One\nhttp://example.com/one.ts\n");
        }
        Account account = new Account("Playlist Account", "user", "pass", "http://unused", "00:11:22:33:44:62", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);

        M3U8PublicationPopup popup = runOnFxThread(() -> new M3U8PublicationPopup(null));

        List<String> names = runOnFxThread(popup::accountNamesForTest);
        assertEquals(BOOKMARKS_PLAYLIST_NAME, names.getFirst());
        assertFalse(runOnFxThread(() -> popup.hasDetailsToggleForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID)));
        assertFalse(runOnFxThread(() -> popup.isAccountSelectedForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID)));
        assertEquals(M3U8PublicationService.PublishedCategoryMode.SOURCE_DASH_CATEGORY,
                runOnFxThread(popup::selectedCategoryModeForTest));

        runOnFxThread(() -> {
            popup.setAccountSelectedForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID, true);
            return null;
        });

        assertTrue(runOnFxThread(() -> popup.isAccountSelectedForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID)));
    }

    @Test
    void popup_readsPersistedPublishedCategoryMode() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        M3U8PublicationPopup popup = runOnFxThread(() -> new M3U8PublicationPopup(null));

        assertEquals(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP,
                runOnFxThread(popup::selectedCategoryModeForTest));
    }

    private static <T> T runOnFxThread(FxCallable<T> task) throws Exception {
        if (Platform.isFxApplicationThread()) {
            return task.call();
        }
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<T> result = new AtomicReference<>();
        AtomicReference<Throwable> failure = new AtomicReference<>();
        Platform.runLater(() -> {
            try {
                result.set(task.call());
            } catch (Throwable t) {
                failure.set(t);
            } finally {
                latch.countDown();
            }
        });
        if (!latch.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Timed out waiting for FX task");
        }
        if (failure.get() != null) {
            throw new RuntimeException(failure.get());
        }
        return result.get();
    }

    @FunctionalInterface
    private interface FxCallable<T> {
        T call() throws Exception;
    }
}
