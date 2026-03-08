package com.uiptv.service.cache;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class M3uAndRssCacheReloaderTest extends DbBackedTest {

    @Test
    void m3uReloader_skipsPersistenceWhenCategoryFetchIsEmpty() {
        Account account = persistAccount("m3u-empty", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        List<String> logs = new ArrayList<>();

        try (MockedStatic<CategoryService> categoryServiceStatic = Mockito.mockStatic(CategoryService.class)) {
            categoryServiceStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any())).thenReturn(List.of());

            new StubM3uCacheReloader().reloadCache(account, logs::add);
        }

        assertTrue(CategoryDb.get().getCategories(account).isEmpty());
        assertTrue(logs.stream().anyMatch(message -> message.contains("No categories found")));
    }

    @Test
    void m3uReloader_persistsChannelsForSuccessfulCategories_andIgnoresFailures() {
        Account account = persistAccount("m3u-success", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category news = new Category("10", "News", "news", false, 0);
        Category sports = new Category("20", "Sports", "sports", false, 0);
        List<String> logs = new ArrayList<>();

        StubM3uCacheReloader reloader = new StubM3uCacheReloader();
        reloader.failOn = "Sports";
        reloader.channelsByCategory.put("News", List.of(channel("news-1", "News One")));

        try (MockedStatic<CategoryService> categoryServiceStatic = Mockito.mockStatic(CategoryService.class)) {
            categoryServiceStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any())).thenReturn(List.of(news, sports));

            reloader.reloadCache(account, logs::add);
        }

        assertEquals(2, CategoryDb.get().getCategories(account).size());
        assertEquals(1, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().anyMatch(message -> message.contains("saved Successfully")));
    }

    @Test
    void rssReloader_keepsExistingCacheWhenEveryCategoryHasNoChannels() {
        Account account = persistAccount("rss-empty", AccountType.RSS_FEED);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        List<String> logs = new ArrayList<>();

        try (MockedStatic<CategoryService> categoryServiceStatic = Mockito.mockStatic(CategoryService.class)) {
            categoryServiceStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                    .thenReturn(List.of(new Category("1", "Feed", "feed", false, 0)));

            new StubRssCacheReloader().reloadCache(account, logs::add);
        }

        assertTrue(CategoryDb.get().getCategories(account).isEmpty());
        assertTrue(logs.stream().anyMatch(message -> message.contains("No channels found in any category")));
    }

    @Test
    void rssReloader_persistsFetchedChannels() {
        Account account = persistAccount("rss-success", AccountType.RSS_FEED);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category category = new Category("1", "Feed", "feed", false, 0);
        StubRssCacheReloader reloader = new StubRssCacheReloader();
        reloader.channelsByCategory.put("Feed", List.of(channel("feed-1", "Feed One"), channel("feed-2", "Feed Two")));

        try (MockedStatic<CategoryService> categoryServiceStatic = Mockito.mockStatic(CategoryService.class)) {
            categoryServiceStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any())).thenReturn(List.of(category));

            reloader.reloadCache(account, null);
        }

        assertEquals(1, CategoryDb.get().getCategories(account).size());
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
    }

    private Account persistAccount(String name, AccountType accountType) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, accountType, null, "mock-source", false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private static Channel channel(String id, String name) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd("http://example.test/" + id);
        return channel;
    }

    private static final class StubM3uCacheReloader extends M3uCacheReloader {
        private final java.util.Map<String, List<Channel>> channelsByCategory = new java.util.HashMap<>();
        private String failOn;

        @Override
        protected List<Channel> m3u8Channels(String category, Account account) {
            if (category.equals(failOn)) {
                throw new IllegalStateException("boom");
            }
            return channelsByCategory.getOrDefault(category, List.of());
        }
    }

    private static final class StubRssCacheReloader extends RssCacheReloader {
        private final java.util.Map<String, List<Channel>> channelsByCategory = new java.util.HashMap<>();

        @Override
        protected List<Channel> rssChannels(String category, Account account) {
            return channelsByCategory.getOrDefault(category, List.of());
        }
    }
}
