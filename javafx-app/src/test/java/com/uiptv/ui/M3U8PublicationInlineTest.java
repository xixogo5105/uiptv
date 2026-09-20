package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Configuration;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static com.uiptv.service.M3U8PublicationService.BOOKMARKS_PLAYLIST_ACCOUNT_ID;
import static com.uiptv.service.M3U8PublicationService.BOOKMARKS_PLAYLIST_NAME;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3U8PublicationInlineTest extends DbBackedUiTest {
    @BeforeAll
    static void initJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    private void waitForAccountLoaded(M3U8PublicationInline inline, String accountId) throws Exception {
        long deadline = System.currentTimeMillis() + 10000;
        while (System.currentTimeMillis() < deadline) {
            if (runOnFxThread(() -> inline.isAccountLoadedForTest(accountId))) {
                return;
            }
            Thread.sleep(50);
        }
        throw new IllegalStateException("Account loading timed out: " + accountId);
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

        M3U8PublicationInline inline = runOnFxThread(M3U8PublicationInline::new);

        List<String> names = runOnFxThread(inline::accountNamesForTest);
        assertEquals(BOOKMARKS_PLAYLIST_NAME, names.getFirst());
        assertFalse(runOnFxThread(() -> inline.hasDetailsToggleForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID)));
        assertFalse(runOnFxThread(() -> inline.isAccountSelectedForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID)));
        assertEquals(M3U8PublicationService.PublishedCategoryMode.SOURCE_DASH_CATEGORY,
                runOnFxThread(inline::selectedCategoryModeForTest));

        runOnFxThread(() -> {
            inline.setAccountSelectedForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID, true);
            return null;
        });

        assertTrue(runOnFxThread(() -> inline.isAccountSelectedForTest(BOOKMARKS_PLAYLIST_ACCOUNT_ID)));
    }

    @Test
    void popup_readsPersistedPublishedCategoryMode() throws Exception {
        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        M3U8PublicationInline inline = runOnFxThread(M3U8PublicationInline::new);

        assertEquals(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP,
                runOnFxThread(inline::selectedCategoryModeForTest));
    }

    @Test
    void partialCategorySelection_setsParentCheckboxIndeterminate() throws Exception {
        java.io.File playlistFile = tempDir.resolve("indeterminate-test.m3u8").toFile();
        try (FileWriter writer = new FileWriter(playlistFile)) {
            writer.write("""
                    #EXTM3U
                    #EXTINF:-1 group-title="News",News One
                    http://example.com/news-1.ts
                    #EXTINF:-1 group-title="Sports",Sports One
                    http://example.com/sports-1.ts
                    """);
        }
        Account account = new Account("Indeterminate Account", "user", "pass", "http://unused", "00:11:22:33:44:70", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Indeterminate Account");

        M3U8PublicationInline inline = runOnFxThread(M3U8PublicationInline::new);

        runOnFxThread(() -> {
            inline.expandAccountForTest(savedAccount.getDbId());
            return null;
        });
        waitForAccountLoaded(inline, savedAccount.getDbId());

        runOnFxThread(() -> {
            inline.selectCategoryForTest(savedAccount.getDbId(), "News");
            return null;
        });

        assertTrue(runOnFxThread(() -> inline.isAccountIndeterminateForTest(savedAccount.getDbId())));
        assertTrue(runOnFxThread(() -> inline.isAccountSelectedForTest(savedAccount.getDbId())));

        runOnFxThread(() -> {
            inline.selectCategoryForTest(savedAccount.getDbId(), "Sports");
            return null;
        });

        assertFalse(runOnFxThread(() -> inline.isAccountIndeterminateForTest(savedAccount.getDbId())));
        assertTrue(runOnFxThread(() -> inline.isAccountSelectedForTest(savedAccount.getDbId())));
    }

    @Test
    void partialCategorySelection_showsIndeterminateOnInitialLoad() throws Exception {
        java.io.File playlistFile = tempDir.resolve("indeterminate-initial.m3u8").toFile();
        try (FileWriter writer = new FileWriter(playlistFile)) {
            writer.write("""
                    #EXTM3U
                    #EXTINF:-1 group-title="News",News One
                    http://example.com/news-1.ts
                    #EXTINF:-1 group-title="Sports",Sports One
                    http://example.com/sports-1.ts
                    """);
        }
        Account account = new Account("Initial Indeterminate", "user", "pass", "http://unused", "00:11:22:33:44:71", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Initial Indeterminate");

        M3U8PublicationService.getInstance().saveSelections(new M3U8PublicationService.PublicationSelections(
                Set.of(),
                Map.of(new M3U8PublicationService.CategorySelectionKey(savedAccount.getDbId(), "News"), true),
                Map.of()
        ));

        M3U8PublicationInline inline = runOnFxThread(M3U8PublicationInline::new);

        assertTrue(runOnFxThread(() -> inline.isAccountIndeterminateForTest(savedAccount.getDbId())));
        assertTrue(runOnFxThread(() -> inline.isAccountSelectedForTest(savedAccount.getDbId())));
    }
}
