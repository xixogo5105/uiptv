package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Configuration;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3U8PublicationServiceTest extends DbBackedTest {

    private java.io.File m3u8File;

    @Override
    protected void afterDatabaseSetup() {
        AccountService.getInstance().deleteAll();
        M3U8PublicationService.getInstance().setSelectedAccountIds(Set.of());
    }

    @BeforeEach
    void setUp() throws Exception {
        // Create a dummy M3U8 file
        m3u8File = tempDir.resolve("test.m3u8").toFile();
        try (FileWriter writer = new FileWriter(m3u8File)) {
            writer.write("#EXTM3U\n");
            writer.write("#EXTINF:-1,Test Channel\n");
            writer.write("http://test.com/stream.ts\n");
        }
    }

    @Test
    void testGetPublishedM3u8() {
        AccountService accountService = AccountService.getInstance();

        // Create an account pointing to the local M3U8 file
        Account account = new Account("M3U8Account", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_LOCAL, null, m3u8File.getAbsolutePath(), false);
        accountService.save(account);
        Account savedAccount = accountService.getByName("M3U8Account");

        // Select the account for publication
        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        Set<String> selectedIds = new LinkedHashSet<>();
        selectedIds.add(savedAccount.getDbId());
        publicationService.setSelectedAccountIds(selectedIds);

        assertEquals(Set.of(savedAccount.getDbId()), publicationService.getSelectedAccountIds());

        // Generate the combined M3U8
        String result = publicationService.getPublishedM3u8();

        // Verify content
        assertTrue(result.contains("#EXTM3U"));
        assertTrue(result.contains("#EXTINF:-1 group-title=\"M3U8Account\",Test Channel"));
        assertTrue(result.contains("http://test.com/stream.ts"));
    }

    @Test
    void deletingAccountRemovesPublishedSelection() {
        AccountService accountService = AccountService.getInstance();
        Account account = new Account("DeleteMe", "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_LOCAL, null, m3u8File.getAbsolutePath(), false);
        accountService.save(account);
        Account savedAccount = accountService.getByName("DeleteMe");

        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        publicationService.setSelectedAccountIds(new LinkedHashSet<>(Set.of(savedAccount.getDbId())));
        accountService.delete(savedAccount.getDbId());

        assertTrue(publicationService.getSelectedAccountIds().isEmpty());
        assertEquals("", publicationService.getPublishedM3u8());
    }

    @Test
    void deleteAllRemovesAllPublishedSelections() {
        AccountService accountService = AccountService.getInstance();
        Account first = new Account("DeleteAllOne", "user", "pass", "http://test.com", "00:11:22:33:44:51", null, null, null, null, null, AccountType.M3U8_LOCAL, null, m3u8File.getAbsolutePath(), false);
        Account second = new Account("DeleteAllTwo", "user", "pass", "http://test.com", "00:11:22:33:44:52", null, null, null, null, null, AccountType.M3U8_LOCAL, null, m3u8File.getAbsolutePath(), false);
        accountService.save(first);
        accountService.save(second);

        Account savedFirst = accountService.getByName("DeleteAllOne");
        Account savedSecond = accountService.getByName("DeleteAllTwo");

        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        publicationService.setSelectedAccountIds(new LinkedHashSet<>(Set.of(savedFirst.getDbId(), savedSecond.getDbId())));
        assertEquals(Set.of(savedFirst.getDbId(), savedSecond.getDbId()), publicationService.getSelectedAccountIds());

        accountService.deleteAll();

        assertTrue(publicationService.getSelectedAccountIds().isEmpty());
        assertEquals("", publicationService.getPublishedM3u8());
    }

    @Test
    void getPublishedM3u8IgnoresStaleSelectedAccountIds() {
        AccountService accountService = AccountService.getInstance();
        Account valid = new Account("ValidSelection", "user", "pass", "http://test.com", "00:11:22:33:44:53", null, null, null, null, null, AccountType.M3U8_LOCAL, null, m3u8File.getAbsolutePath(), false);
        accountService.save(valid);
        Account savedValid = accountService.getByName("ValidSelection");

        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        LinkedHashSet<String> selectedIds = new LinkedHashSet<>();
        selectedIds.add("999999");
        selectedIds.add(savedValid.getDbId());
        publicationService.setSelectedAccountIds(selectedIds);

        String result = publicationService.getPublishedM3u8();

        assertEquals(selectedIds, publicationService.getSelectedAccountIds());
        assertTrue(result.contains("#EXTM3U"));
        assertTrue(result.contains("Test Channel"));
        assertTrue(result.contains("http://test.com/stream.ts"));
    }

    @Test
    void getPublishedM3u8_filtersCategoriesAndChannelsUsingSavedOverrides() throws Exception {
        java.io.File playlistFile = tempDir.resolve("filtered.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 group-title="News",News One
                http://example.com/news-1.ts
                #EXTINF:-1 group-title="News",News Two
                http://example.com/news-2.ts
                #EXTINF:-1 group-title="Sports",Sports One
                http://example.com/sports-1.ts
                """, StandardCharsets.UTF_8);

        Account account = new Account("FilterMe", "user", "pass", "http://unused", "00:11:22:33:44:59", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("FilterMe");

        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        M3U8PublicationService.PlaylistAccount playlist = publicationService.getPlaylist(savedAccount.getDbId());
        String newsTwoId = playlist.categories().stream()
                .filter(category -> "News".equals(category.categoryName()))
                .flatMap(category -> category.channels().stream())
                .filter(channel -> "News Two".equals(channel.title()))
                .findFirst()
                .orElseThrow()
                .channelId();

        publicationService.saveSelections(new M3U8PublicationService.PublicationSelections(
                Set.of(savedAccount.getDbId()),
                Map.of(new M3U8PublicationService.CategorySelectionKey(savedAccount.getDbId(), "Sports"), false),
                Map.of(new M3U8PublicationService.ChannelSelectionKey(savedAccount.getDbId(), "News", newsTwoId), false)
        ));

        String result = publicationService.getPublishedM3u8();

        assertTrue(result.contains("News One"));
        assertFalse(result.contains("News Two"));
        assertFalse(result.contains("Sports One"));
    }

    @Test
    void getPlaylist_mergesCategoryNamesCaseInsensitively() throws Exception {
        java.io.File playlistFile = tempDir.resolve("case-categories.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 group-title="Movies",Movie One
                http://example.com/movie-1.ts
                #EXTINF:-1 group-title="movies",Movie Two
                http://example.com/movie-2.ts
                """, StandardCharsets.UTF_8);

        Account account = new Account("CaseMerge", "user", "pass", "http://unused", "00:11:22:33:44:60", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("CaseMerge");

        M3U8PublicationService.PlaylistAccount playlist = M3U8PublicationService.getInstance().getPlaylist(savedAccount.getDbId());

        assertEquals(1, playlist.categories().size());
        assertEquals("Movies", playlist.categories().getFirst().categoryName());
        assertEquals(2, playlist.categories().getFirst().channels().size());
    }

    @Test
    void getPlaylist_splitsSemicolonSeparatedCategories() throws Exception {
        java.io.File playlistFile = tempDir.resolve("multi-group-categories.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 tvg-id="shared" group-title="News;Sports",Shared Channel
                http://example.com/shared.ts
                """, StandardCharsets.UTF_8);

        Account account = new Account("SplitCats", "user", "pass", "http://unused", "00:11:22:33:44:62", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("SplitCats");

        M3U8PublicationService.PlaylistAccount playlist = M3U8PublicationService.getInstance().getPlaylist(savedAccount.getDbId());

        assertEquals(Set.of("News", "Sports"),
                playlist.categories().stream().map(M3U8PublicationService.PlaylistCategory::categoryName).collect(java.util.stream.Collectors.toSet()));
    }

    @Test
    void getAvailableAccounts_listsBookmarksPlaylistFirst() {
        Account account = new Account("LaterAccount", "user", "pass", "http://test.com", "00:11:22:33:44:61", null, null, null, null, null, AccountType.M3U8_LOCAL, null, m3u8File.getAbsolutePath(), false);
        AccountService.getInstance().save(account);

        var accounts = M3U8PublicationService.getInstance().getAvailableAccounts();

        assertFalse(accounts.isEmpty());
        assertEquals(M3U8PublicationService.BOOKMARKS_PLAYLIST_ACCOUNT_ID, accounts.getFirst().accountId());
        assertEquals(M3U8PublicationService.BOOKMARKS_PLAYLIST_NAME, accounts.getFirst().accountName());
    }

    @Test
    void getPublishedM3u8_includesBookmarksPlaylistWhenSelected() {
        Bookmark bookmark = new Bookmark("acc", "", "ch-1", "Favorite One", "cmd1", "http://portal", null);
        BookmarkService.getInstance().save(bookmark);
        Bookmark savedBookmark = BookmarkService.getInstance().getBookmark(bookmark);

        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        publicationService.setSelectedAccountIds(Set.of(M3U8PublicationService.BOOKMARKS_PLAYLIST_ACCOUNT_ID));

        String result = publicationService.getPublishedM3u8();

        assertTrue(result.contains("#EXTM3U"));
        assertTrue(result.contains("group-title=\"Bookmarks\""));
        assertTrue(result.contains("Favorite One"));
        assertTrue(result.contains("/bookmarkEntry.ts?bookmarkId=" + savedBookmark.getDbId()));
    }

    @Test
    void getPublishedM3u8_usesRequestHostForBookmarksPlaylistWhenProvided() {
        Bookmark bookmark = new Bookmark("acc", "", "ch-1", "Favorite One", "cmd1", "http://portal", null);
        BookmarkService.getInstance().save(bookmark);
        Bookmark savedBookmark = BookmarkService.getInstance().getBookmark(bookmark);

        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        publicationService.setSelectedAccountIds(Set.of(M3U8PublicationService.BOOKMARKS_PLAYLIST_ACCOUNT_ID));

        String result = publicationService.getPublishedM3u8("192.168.0.210:8080");

        assertTrue(result.contains("http://192.168.0.210:8080/bookmarkEntry.ts?bookmarkId=" + savedBookmark.getDbId()));
        assertFalse(result.contains("http://127.0.0.1:8080/bookmarkEntry.ts?bookmarkId=" + savedBookmark.getDbId()));
    }

    @Test
    void getPublishedM3u8_defaultsToSourceDashCategoryMode() throws Exception {
        java.io.File playlistFile = tempDir.resolve("group-format-default.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 group-title="News",News One
                http://example.com/news-1.ts
                """, StandardCharsets.UTF_8);

        Account account = new Account("Provider One", "user", "pass", "http://unused", "00:11:22:33:44:63", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Provider One");
        M3U8PublicationService.getInstance().setSelectedAccountIds(Set.of(savedAccount.getDbId()));

        String result = M3U8PublicationService.getInstance().getPublishedM3u8("192.168.0.210:8080");

        assertTrue(result.contains("group-title=\"Provider One\""));
    }

    @Test
    void getPublishedM3u8_appliesConfiguredCategoryModeFormats() throws Exception {
        java.io.File playlistFile = tempDir.resolve("group-format-configured.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 group-title="Sports",Sports One
                http://example.com/sports-1.ts
                #EXTINF:-1,Untagged One
                http://example.com/untagged-1.ts
                """, StandardCharsets.UTF_8);

        Account account = new Account("Provider Two", "user", "pass", "http://unused", "00:11:22:33:44:64", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Provider Two");
        M3U8PublicationService.getInstance().setSelectedAccountIds(Set.of(savedAccount.getDbId()));

        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.CATEGORY_WITH_SOURCE.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        String categoryWithSource = M3U8PublicationService.getInstance().getPublishedM3u8("192.168.0.210:8080");
        assertTrue(categoryWithSource.contains("group-title=\"Sports [Provider Two]\""));
        assertTrue(categoryWithSource.contains("group-title=\"Uncategorized [Provider Two]\""));

        configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        String multiGroup = M3U8PublicationService.getInstance().getPublishedM3u8("192.168.0.210:8080");
        assertTrue(multiGroup.contains("group-title=\"Provider Two;Sports\""));
        assertTrue(multiGroup.contains("group-title=\"Provider Two;Uncategorized\""));

        configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.ORIGINAL_CATEGORY.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        String original = M3U8PublicationService.getInstance().getPublishedM3u8("192.168.0.210:8080");
        assertTrue(original.contains("group-title=\"Sports\""));
        assertFalse(original.contains("group-title=\"Uncategorized\""));
    }

    @Test
    void getPublishedM3u8_originalCategoryModeRewritesSelectedSemicolonCategory() throws Exception {
        java.io.File playlistFile = tempDir.resolve("group-format-semicolon-original.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 tvg-id="shared" group-title="News;Sports",Shared Channel
                http://example.com/shared.ts
                """, StandardCharsets.UTF_8);

        Account account = new Account("Provider Split", "user", "pass", "http://unused", "00:11:22:33:44:66", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Provider Split");

        M3U8PublicationService publicationService = M3U8PublicationService.getInstance();
        M3U8PublicationService.PlaylistAccount playlist = publicationService.getPlaylist(savedAccount.getDbId());
        String newsChannelId = playlist.categories().stream()
                .filter(category -> "News".equals(category.categoryName()))
                .flatMap(category -> category.channels().stream())
                .findFirst()
                .orElseThrow()
                .channelId();

        publicationService.saveSelections(new M3U8PublicationService.PublicationSelections(
                Set.of(savedAccount.getDbId()),
                Map.of(new M3U8PublicationService.CategorySelectionKey(savedAccount.getDbId(), "Sports"), false),
                Map.of(new M3U8PublicationService.ChannelSelectionKey(savedAccount.getDbId(), "News", newsChannelId), true)
        ));

        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.ORIGINAL_CATEGORY.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        String result = publicationService.getPublishedM3u8("192.168.0.210:8080");

        assertTrue(result.contains("group-title=\"News\""));
        assertFalse(result.contains("group-title=\"News;Sports\""));
    }

    @Test
    void getPublishedM3u8_collapsesSingleCategorySourcesToPlaylistName() throws Exception {
        java.io.File playlistFile = tempDir.resolve("group-format-single-category.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 group-title="Sports",Sports One
                http://example.com/sports-1.ts
                #EXTINF:-1 group-title="Sports",Sports Two
                http://example.com/sports-2.ts
                """, StandardCharsets.UTF_8);

        Account account = new Account("Provider Three", "user", "pass", "http://unused", "00:11:22:33:44:65", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Provider Three");
        M3U8PublicationService.getInstance().setSelectedAccountIds(Set.of(savedAccount.getDbId()));

        Configuration configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.CATEGORY_WITH_SOURCE.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        String categoryWithSource = M3U8PublicationService.getInstance().getPublishedM3u8("192.168.0.210:8080");
        assertTrue(categoryWithSource.contains("group-title=\"Provider Three\""));
        assertFalse(categoryWithSource.contains("group-title=\"Sports [Provider Three]\""));

        configuration = ConfigurationService.getInstance().read();
        configuration.setPublishedM3uCategoryMode(M3U8PublicationService.PublishedCategoryMode.MULTI_GROUP.persistedValue());
        ConfigurationService.getInstance().save(configuration);

        String multiGroup = M3U8PublicationService.getInstance().getPublishedM3u8("192.168.0.210:8080");
        assertTrue(multiGroup.contains("group-title=\"Provider Three\""));
        assertFalse(multiGroup.contains("group-title=\"Provider Three;Sports\""));
    }
}
