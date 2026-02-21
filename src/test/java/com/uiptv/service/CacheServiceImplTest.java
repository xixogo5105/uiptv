package com.uiptv.service;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.test.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CacheServiceImplTest extends DbBackedTest {

    @Test
    void reloadCache_m3u8Local_ignoresFiltering_whenPauseFilteringFalse() throws IOException {
        saveConfiguration("live", "premium", false);
        Account account = createM3uAccount("acc-cache-1", writePlaylist("cache-playlist-1.m3u"));

        CacheService cacheService = new CacheServiceImpl();
        cacheService.reloadCache(account, m -> {
        });

        List<Channel> cachedChannels = getAllCachedChannels(account);
        List<Category> cachedCategories = CategoryDb.get().getCategories(account);

        assertTrue(cachedChannels.stream().anyMatch(c -> "Premium Plus".equals(c.getName())));
        assertTrue(cachedChannels.stream().anyMatch(c -> "Sports Live".equals(c.getName())));
        assertTrue(cachedCategories.stream().anyMatch(c -> "Live".equalsIgnoreCase(c.getTitle())));
    }

    @Test
    void reloadCache_m3u8Local_ignoresFiltering_whenPauseFilteringTrue() throws IOException {
        saveConfiguration("live", "premium", true);
        Account account = createM3uAccount("acc-cache-2", writePlaylist("cache-playlist-2.m3u"));

        CacheService cacheService = new CacheServiceImpl();
        cacheService.reloadCache(account, m -> {
        });

        List<Channel> cachedChannels = getAllCachedChannels(account);
        List<Category> cachedCategories = CategoryDb.get().getCategories(account);

        assertTrue(cachedChannels.stream().anyMatch(c -> "Premium Plus".equals(c.getName())));
        assertTrue(cachedChannels.stream().anyMatch(c -> "Sports Live".equals(c.getName())));
        assertTrue(cachedCategories.stream().anyMatch(c -> "Live".equalsIgnoreCase(c.getTitle())));
    }

    private List<Channel> getAllCachedChannels(Account account) {
        assertTrue(ChannelDb.get().getChannelCountForAccount(account.getDbId()) > 0);
        List<Category> categories = CategoryDb.get().getCategories(account);
        assertTrue(categories.size() >= 2);

        List<Channel> all = new ArrayList<>();
        for (Category category : categories) {
            all.addAll(ChannelDb.get().getChannels(category.getDbId()));
        }
        return all;
    }

    private Account createM3uAccount(String accountId, String playlistPath) {
        Account account = new Account(
                "test-account-" + accountId,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.M3U8_LOCAL,
                null,
                playlistPath,
                false
        );
        account.setDbId(accountId);
        account.setAction(Account.AccountAction.itv);
        return account;
    }

    private void saveConfiguration(String categoryFilter, String channelFilter, boolean pauseFiltering) {
        Configuration configuration = new Configuration(
                null,
                null,
                null,
                null,
                categoryFilter,
                channelFilter,
                pauseFiltering,
                null,
                null,
                null,
                false,
                "8888",
                false,
                false
        );
        ConfigurationService.getInstance().save(configuration);
    }

    private String writePlaylist(String filename) throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 tvg-id="sports-1" tvg-logo="sports.png" group-title="Live",Sports Live
                http://example.com/live/sports
                #EXTINF:-1 tvg-id="premium-1" tvg-logo="premium.png" group-title="Live",Premium Plus
                http://example.com/live/premium
                """;
        Path file = tempDir.resolve(filename);
        Files.writeString(file, content);
        return file.toString();
    }
}
