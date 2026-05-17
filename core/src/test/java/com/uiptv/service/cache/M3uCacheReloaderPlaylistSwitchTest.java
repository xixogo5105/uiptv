package com.uiptv.service.cache;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.CategoryType;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3uCacheReloaderPlaylistSwitchTest extends DbBackedTest {

    @Test
    void reloadCache_replacesOldCategoriesWhenPlaylistGroupingChanges() throws Exception {
        java.io.File playlistFile = tempDir.resolve("switch-groups.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 tvg-id="c1" group-title="News",Channel One
                http://example.com/one.m3u8
                #EXTINF:-1 tvg-id="c2" group-title="Sports",Channel Two
                http://example.com/two.m3u8
                """, StandardCharsets.UTF_8);

        Account account = new Account("Switch Groups", "user", "pass", "http://unused", "00:11:22:33:44:67", null, null, null, null, null, AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Switch Groups");

        M3uCacheReloader reloader = new M3uCacheReloader();
        reloader.reloadCache(savedAccount, null);

        Set<String> firstTitles = CategoryDb.get().getCategories(savedAccount).stream()
                .map(Category::getTitle)
                .collect(Collectors.toSet());
        assertTrue(firstTitles.contains("News"));
        assertTrue(firstTitles.contains("Sports"));

        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 tvg-id="l1" group-title="English",Channel One
                http://example.com/one.m3u8
                #EXTINF:-1 tvg-id="l2" group-title="Spanish",Channel Two
                http://example.com/two.m3u8
                #EXTINF:-1 tvg-id="l3" group-title="French",Channel Three
                http://example.com/three.m3u8
                """, StandardCharsets.UTF_8);

        reloader.reloadCache(savedAccount, null);

        List<Category> updatedCategories = CategoryDb.get().getCategories(savedAccount);
        Set<String> updatedTitles = updatedCategories.stream()
                .map(Category::getTitle)
                .collect(Collectors.toSet());

        assertTrue(updatedTitles.contains("English"));
        assertTrue(updatedTitles.contains("Spanish"));
        assertTrue(updatedTitles.contains("French"));
        assertTrue(!updatedTitles.contains("News"));
        assertTrue(!updatedTitles.contains("Sports"));
        assertEquals(Set.of("All", "English", "Spanish", "French"), updatedTitles);
    }

    @Test
    void reloadCache_streamsReadableLocalPlaylistAndKeepsAllBucketComplete() throws Exception {
        java.io.File playlistFile = tempDir.resolve("streaming-groups.m3u8").toFile();
        Files.writeString(playlistFile.toPath(), """
                #EXTM3U
                #EXTINF:-1 tvg-id="shared" group-title="News;Sports",Shared Channel
                http://example.com/shared.m3u8
                #EXTINF:-1 tvg-id="blank",No Group
                http://example.com/blank.m3u8
                #EXTINF:-1 tvg-id="all-only" group-title="All",All Only
                http://example.com/all-only.m3u8
                """, StandardCharsets.UTF_8);

        Account account = new Account("Streaming Groups", "user", "pass", "http://unused",
                "00:11:22:33:44:68", null, null, null, null, null,
                AccountType.M3U8_LOCAL, null, playlistFile.getAbsolutePath(), false);
        AccountService.getInstance().save(account);
        Account savedAccount = AccountService.getInstance().getByName("Streaming Groups");

        M3uCacheReloader reloader = new M3uCacheReloader() {
            @Override
            protected Map<String, List<Channel>> loadM3uChannelsByCategory(List<Category> categories, Account account, com.uiptv.api.LoggerCallback logger) {
                fail("Readable local M3U playlists should use the streaming reload path");
                return Map.of();
            }
        };
        reloader.reloadCache(savedAccount, null);

        List<Category> savedCategories = CategoryDb.get().getCategories(savedAccount);
        Set<String> titles = savedCategories.stream().map(Category::getTitle).collect(Collectors.toSet());
        assertEquals(Set.of(
                CategoryType.ALL.displayName(),
                "News",
                "Sports",
                CategoryType.UNCATEGORIZED.displayName()
        ), titles);

        Map<String, List<String>> channelNamesByCategory = savedCategories.stream()
                .collect(Collectors.toMap(
                        Category::getTitle,
                        category -> ChannelDb.get().getChannels(category.getDbId()).stream()
                                .map(Channel::getName)
                                .collect(Collectors.toCollection(ArrayList::new))
                ));
        assertEquals(Set.of("Shared Channel", "No Group", "All Only"),
                Set.copyOf(channelNamesByCategory.get(CategoryType.ALL.displayName())));
        assertEquals(List.of("Shared Channel"), channelNamesByCategory.get("News"));
        assertEquals(List.of("Shared Channel"), channelNamesByCategory.get("Sports"));
        assertEquals(List.of("No Group"), channelNamesByCategory.get(CategoryType.UNCATEGORIZED.displayName()));
        assertEquals(6, ChannelDb.get().getChannelCountForAccount(savedAccount.getDbId()));
    }
}
