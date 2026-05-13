package com.uiptv.service.cache;

import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
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
}
