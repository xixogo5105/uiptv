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
}
