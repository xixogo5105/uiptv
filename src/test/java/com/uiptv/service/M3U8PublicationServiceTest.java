package com.uiptv.service;

import com.uiptv.model.Account;
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
        assertTrue(result.contains("#EXTINF:-1,Test Channel"));
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
}
