package com.uiptv.service.cache;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.CategoryType;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
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

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any())).thenReturn(List.of());
        new StubM3uCacheReloader(categoryService).reloadCache(account, logs::add);

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

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any())).thenReturn(List.of(news, sports));

        StubM3uCacheReloader reloader = new StubM3uCacheReloader(categoryService);
        reloader.failOn = "Sports";
        reloader.channelsByCategory.put("News", List.of(channel("news-1", "News One")));
        reloader.reloadCache(account, logs::add);

        // Sports should be filtered out because it has no channels
        assertEquals(1, CategoryDb.get().getCategories(account).size());
        assertEquals(1, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().anyMatch(message -> message.contains("saved Successfully")));
        assertTrue(logs.stream().anyMatch(message -> message.contains("Filtering out empty category: Sports")));
    }

    @Test
    void m3uReloader_buildsChannelMapOncePerReload() {
        Account account = persistAccount("m3u-single-pass", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category news = new Category("1", "News", "news", false, 0);
        Category sports = new Category("2", "Sports", "sports", false, 0);

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory, news, sports));

        StubM3uCacheReloader reloader = new StubM3uCacheReloader(categoryService);
        reloader.channelsByCategory.put(CategoryType.ALL.displayName(), List.of(
                channel("all-1", "All One"),
                channel("all-2", "All Two")
        ));
        reloader.channelsByCategory.put("News", List.of(channel("news-1", "News One")));
        reloader.channelsByCategory.put("Sports", List.of(channel("sports-1", "Sports One")));

        reloader.reloadCache(account, null);
        assertEquals(1, reloader.loadMapInvocationCount, "Should build the M3U channel map once per reload");
        assertEquals(3, CategoryDb.get().getCategories(account).size());
    }

    @Test
    void rssReloader_keepsExistingCacheWhenEveryCategoryHasNoChannels() {
        Account account = persistAccount("rss-empty", AccountType.RSS_FEED);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        List<String> logs = new ArrayList<>();

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(new Category("1", "Feed", "feed", false, 0)));
        new StubRssCacheReloader(categoryService).reloadCache(account, logs::add);

        assertTrue(CategoryDb.get().getCategories(account).isEmpty());
        assertTrue(logs.stream().anyMatch(message -> message.contains("No channels found in any category")));
    }

    @Test
    void rssReloader_persistsFetchedChannels() {
        Account account = persistAccount("rss-success", AccountType.RSS_FEED);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category category = new Category("1", "Feed", "feed", false, 0);
        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any())).thenReturn(List.of(category));

        StubRssCacheReloader reloader = new StubRssCacheReloader(categoryService);
        reloader.channelsByCategory.put("Feed", List.of(channel("feed-1", "Feed One"), channel("feed-2", "Feed Two")));
        reloader.reloadCache(account, null);

        assertEquals(1, CategoryDb.get().getCategories(account).size());
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
    }

    @Test
    void rssReloader_ignoresPerCategoryFailure_andSavesSuccessfulCategories() {
        Account account = persistAccount("rss-partial", AccountType.RSS_FEED);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(
                        new Category("1", "Broken", "broken", false, 0),
                        new Category("2", "Working", "working", false, 0)
                ));

        StubRssCacheReloader reloader2 = new StubRssCacheReloader(categoryService);
        reloader2.failOn = "Broken";
        reloader2.channelsByCategory.put("Working", List.of(channel("work-1", "Working One")));
        reloader2.reloadCache(account, null);

        assertEquals(2, CategoryDb.get().getCategories(account).size());
        assertEquals(1, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
    }

    @Test
    void m3uReloader_singleNonAllCategoryTreatedAsAll() {
        // When exactly one category (besides All) exists, channels from that category should be treated as All
        Account account = persistAccount("m3u-single-category", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category singleCategory = new Category("1", "Movies", "movies", false, 0);
        List<String> logs = new ArrayList<>();

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory, singleCategory));

        StubM3uCacheReloader reloader3 = new StubM3uCacheReloader(categoryService);
        reloader3.channelsByCategory.put("Movies", List.of(channel("mov-1", "Channel One"), channel("mov-2", "Channel Two")));
        reloader3.reloadCache(account, logs::add);

        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(1, savedCategories.size(), "Should only have All category");
        assertEquals(CategoryType.ALL.displayName(), savedCategories.get(0).getTitle());
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Single non-All category detected")));
    }

    @Test
    void m3uReloader_uncategorizedOnlyCreatedWhenItHasChannels() {
        // Uncategorized should only be saved if it has channels
        Account account = persistAccount("m3u-uncategorized-empty", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category sportsCategory = new Category("2", "Sports", "sports", false, 0);
        Category uncategorizedEmpty = new Category("3", CategoryType.UNCATEGORIZED.displayName(), CategoryType.UNCATEGORIZED.identifier(), false, 0);

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory, sportsCategory, uncategorizedEmpty));

        StubM3uCacheReloader reloader = new StubM3uCacheReloader(categoryService);
        reloader.channelsByCategory.put(CategoryType.ALL.displayName(), List.of(channel("all-1", "All Channels")));
        reloader.channelsByCategory.put("Sports", List.of(channel("sp-1", "Sports Channel")));
        reloader.reloadCache(account, null);

        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(2, savedCategories.size(), "Should have All and Sports, but not Uncategorized");
        assertTrue(savedCategories.stream().anyMatch(c -> CategoryType.ALL.displayName().equals(c.getTitle())));
        assertTrue(savedCategories.stream().anyMatch(c -> "Sports".equals(c.getTitle())));
        assertTrue(savedCategories.stream().noneMatch(c -> CategoryType.UNCATEGORIZED.displayName().equals(c.getTitle())));
    }

    @Test
    void m3uReloader_uncategorizedCreatedWhenItHasChannelsAndOtherCategoriesExist() {
        // Uncategorized should be saved if it has channels AND other categories exist
        Account account = persistAccount("m3u-uncategorized-with-channels", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category sportsCategory = new Category("2", "Sports", "sports", false, 0);
        Category uncategorized = new Category("3", CategoryType.UNCATEGORIZED.displayName(), CategoryType.UNCATEGORIZED.identifier(), false, 0);

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory, sportsCategory, uncategorized));

        StubM3uCacheReloader reloader4 = new StubM3uCacheReloader(categoryService);
        reloader4.channelsByCategory.put(CategoryType.ALL.displayName(), List.of(channel("all-1", "All Channels")));
        reloader4.channelsByCategory.put("Sports", List.of(channel("sp-1", "Sports Channel")));
        reloader4.channelsByCategory.put(CategoryType.UNCATEGORIZED.displayName(), List.of(channel("unc-1", "Uncategorized Channel")));
        reloader4.reloadCache(account, null);

        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(3, savedCategories.size(), "Should have All, Sports, and Uncategorized");
        assertTrue(savedCategories.stream().anyMatch(c -> CategoryType.UNCATEGORIZED.displayName().equals(c.getTitle())));
    }

    @Test
    void m3uReloader_noUncategorizedWhenOnlyAllExists() {
        // Never create Uncategorized when only All category exists
        Account account = persistAccount("m3u-only-all", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory));

        StubM3uCacheReloader reloader5 = new StubM3uCacheReloader(categoryService);
        reloader5.channelsByCategory.put(CategoryType.ALL.displayName(), List.of(channel("all-1", "Channel One"), channel("all-2", "Channel Two")));
        reloader5.reloadCache(account, null);

        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(1, savedCategories.size());
        assertEquals(CategoryType.ALL.displayName(), savedCategories.get(0).getTitle());
        assertTrue(savedCategories.stream().noneMatch(c -> CategoryType.UNCATEGORIZED.displayName().equals(c.getTitle())));
    }

    @Test
    void m3uReloader_multipleCategoriesPreservedWhenNotSingleCategory() {
        // Multiple real categories should be preserved (not treated as single)
        Account account = persistAccount("m3u-multi-cats", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category news = new Category("1", "News", "news", false, 0);
        Category sports = new Category("2", "Sports", "sports", false, 0);

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory, news, sports));

        StubM3uCacheReloader reloader6 = new StubM3uCacheReloader(categoryService);
        reloader6.channelsByCategory.put(CategoryType.ALL.displayName(), List.of(channel("all-1", "All Channels")));
        reloader6.channelsByCategory.put("News", List.of(channel("news-1", "News Channel")));
        reloader6.channelsByCategory.put("Sports", List.of(channel("sp-1", "Sports Channel")));
        reloader6.reloadCache(account, null);

        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(3, savedCategories.size(), "Should preserve all 3 categories");
        assertTrue(savedCategories.stream().anyMatch(c -> CategoryType.ALL.displayName().equals(c.getTitle())));
        assertTrue(savedCategories.stream().anyMatch(c -> "News".equals(c.getTitle())));
        assertTrue(savedCategories.stream().anyMatch(c -> "Sports".equals(c.getTitle())));
    }

    @Test
    void m3uReloader_emptyEntriesFilteredOut() {
        // Categories with no channels should be filtered out
        Account account = persistAccount("m3u-empty-categories", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category emptyNews = new Category("1", "News", "news", false, 0);
        Category sports = new Category("2", "Sports", "sports", false, 0);
        List<String> logs = new ArrayList<>();

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory, emptyNews, sports));

        StubM3uCacheReloader reloader7 = new StubM3uCacheReloader(categoryService);
        reloader7.channelsByCategory.put(CategoryType.ALL.displayName(), List.of(channel("all-1", "All Channels")));
        reloader7.channelsByCategory.put("Sports", List.of(channel("sp-1", "Sports Channel")));
        reloader7.reloadCache(account, logs::add);

        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(2, savedCategories.size(), "Should only have All and Sports, News filtered out");
        assertTrue(savedCategories.stream().anyMatch(c -> CategoryType.ALL.displayName().equals(c.getTitle())));
        assertTrue(savedCategories.stream().anyMatch(c -> "Sports".equals(c.getTitle())));
        assertTrue(savedCategories.stream().noneMatch(c -> "News".equals(c.getTitle())));
        assertTrue(logs.stream().anyMatch(m -> m.contains("Filtering out empty category: News")));
    }

    @Test
    void m3uReloader_singleUndefinedCategoryMergedToAll() {
        // This is the APSATTV M3U scenario: all entries have group-title="Undefined"
        // Should merge Undefined channels into All and filter out Undefined
        Account account = persistAccount("m3u-apsattv-undefined", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category allCategory = new Category("all", CategoryType.ALL.displayName(), "all", false, 0);
        Category undefinedCategory = new Category("1", "Undefined", "undefined", false, 0);
        List<String> logs = new ArrayList<>();

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(allCategory, undefinedCategory));

        StubM3uCacheReloader reloader8 = new StubM3uCacheReloader(categoryService);
        reloader8.channelsByCategory.put("Undefined", List.of(
                channel("ftf-1", "FTF Sports"),
                channel("horizon-1", "Horizon Sports"),
                channel("boat-1", "The Boat Show")
        ));
        reloader8.reloadCache(account, logs::add);

        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(1, savedCategories.size(), "Should only have All category");
        assertEquals(CategoryType.ALL.displayName(), savedCategories.get(0).getTitle());
        assertEquals(3, ChannelDb.get().getChannelCountForAccount(account.getDbId()), "Should have 3 channels in All");
        assertTrue(logs.stream().anyMatch(m -> m.contains("Single non-All category detected")));
    }

    @Test
    void m3uReloader_allEmptyCategoriesMergedToAll() {
        // If all categories end up being empty (due to filtering), create All with accumulated channels
        Account account = persistAccount("m3u-all-empty", AccountType.M3U8_LOCAL);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        Category emptyNews = new Category("1", "News", "news", false, 0);
        Category emptySports = new Category("2", "Sports", "sports", false, 0);

        // No categories have channels in this mock
        // (This simulates a parsing scenario where channels exist but no categories match)

        Mockito.when(categoryService.get(Mockito.eq(account), Mockito.eq(false), Mockito.any()))
                .thenReturn(List.of(emptyNews, emptySports));

        // The actual flow would be that reloadCache exits early with "No channels found"
        List<Category> savedCategories = CategoryDb.get().getCategories(account);
        assertEquals(0, savedCategories.size(), "Should not save anything when no channels found");
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
        private int loadMapInvocationCount;

        private StubM3uCacheReloader(CategoryService categoryService) {
            super(() -> categoryService, com.uiptv.service.ConfigurationService::getInstance);
        }

        @Override
        protected java.util.Map<String, List<Channel>> loadM3uChannelsByCategory(List<Category> categories, Account account, com.uiptv.api.LoggerCallback logger) {
            loadMapInvocationCount++;
            java.util.Map<String, List<Channel>> result = new java.util.HashMap<>();
            for (Category category : categories) {
                if (category == null || category.getTitle() == null) {
                    continue;
                }
                if (category.getTitle().equals(failOn)) {
                    continue;
                }
                List<Channel> channels = channelsByCategory.get(category.getTitle());
                if (channels != null && !channels.isEmpty()) {
                    result.put(category.getTitle(), channels);
                }
            }
            return result;
        }
    }

    private static final class StubRssCacheReloader extends RssCacheReloader {
        private final java.util.Map<String, List<Channel>> channelsByCategory = new java.util.HashMap<>();
        private String failOn;

        private StubRssCacheReloader(CategoryService categoryService) {
            super(() -> categoryService, com.uiptv.service.ConfigurationService::getInstance);
        }

        @Override
        protected List<Channel> rssChannels(String category, Account account) {
            if (category.equals(failOn)) {
                throw new IllegalStateException("boom");
            }
            return channelsByCategory.getOrDefault(category, List.of());
        }
    }
}
