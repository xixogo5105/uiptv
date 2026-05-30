package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XtremeApiCacheReloaderTest extends DbBackedTest {

    @Test
    void reloadCache_forVodPersistsOnlyVodCategories() {
        Account account = persistAccount("xtreme-vod", Account.AccountAction.vod);
        List<Category> vodCategories = List.of(new Category("vod-1", "Movies", "movies", false, 0));

        try (MockedStatic<XtremeApiParser> xtremeStatic = Mockito.mockStatic(XtremeApiParser.class)) {
            xtremeStatic.when(() -> XtremeApiParser.parseCategories(account)).thenReturn(vodCategories);
            new XtremeApiCacheReloader().reloadCache(account, null);
        }

        assertEquals(1, VodCategoryDb.get().getCategories(account).size());
        assertTrue(CategoryDb.get().getCategories(account).isEmpty());
    }

    @Test
    void reloadCache_forVodClearsAllAccountCacheTablesBeforeSavingFreshCategories() {
        Account account = persistAccount("xtreme-vod-clear", Account.AccountAction.vod);
        seedAllCacheTables(account);
        account.setAction(Account.AccountAction.vod);
        List<Category> freshVodCategories = List.of(new Category("vod-fresh", "Fresh Movies", "fresh", false, 0));

        try (MockedStatic<XtremeApiParser> xtremeStatic = Mockito.mockStatic(XtremeApiParser.class)) {
            xtremeStatic.when(() -> XtremeApiParser.parseCategories(account)).thenReturn(freshVodCategories);
            new XtremeApiCacheReloader().reloadCache(account, null);
        }

        account.setAction(Account.AccountAction.itv);
        assertTrue(CategoryDb.get().getCategories(account).isEmpty());
        assertEquals(0, ChannelDb.get().getChannelCountForAccount(account.getDbId()));

        account.setAction(Account.AccountAction.vod);
        List<Category> vodCategories = VodCategoryDb.get().getCategories(account);
        assertEquals(1, vodCategories.size());
        assertEquals("vod-fresh", vodCategories.getFirst().getCategoryId());
        assertTrue(VodChannelDb.get().getChannels(account, "vod-old").isEmpty());

        account.setAction(Account.AccountAction.series);
        assertTrue(SeriesCategoryDb.get().getCategories(account).isEmpty());
        assertTrue(SeriesChannelDb.get().getChannels(account, "series-old").isEmpty());
        assertTrue(SeriesEpisodeDb.get().getEpisodes(account, "series-old", "series-ch-old").isEmpty());
    }

    @Test
    void reloadCache_liveGlobalLookupSavesMatchedAndOrphanedChannelsAndCachesSecondaryModes() {
        Account account = persistAccount("xtreme-itv-global", Account.AccountAction.itv);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        List<Category> liveCategories = List.of(
                new Category("10", "News", "news", false, 0),
                new Category("20", "Sports", "sports", false, 0)
        );
        List<Category> vodCategories = List.of(new Category("vod-1", "Movies", "movies", false, 0));
        List<Category> seriesCategories = List.of(new Category("series-1", "Shows", "shows", false, 0));
        List<Channel> allChannels = List.of(
                channel("c1", "10", "News One"),
                channel("c2", "20", "Sports One"),
                channel("c3", "999", "Orphan")
        );

        try (MockedStatic<CategoryService> categoriesStatic = Mockito.mockStatic(CategoryService.class);
             MockedStatic<XtremeApiParser> xtremeStatic = Mockito.mockStatic(XtremeApiParser.class)) {
            categoriesStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.get(account, false, (LoggerCallback) null)).thenReturn(liveCategories);
            Mockito.when(categoryService.getFresh(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.vod), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(vodCategories);
            Mockito.when(categoryService.getFresh(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.series), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(seriesCategories);
            xtremeStatic.when(() -> XtremeApiParser.parseAllChannels(account)).thenReturn(allChannels);

            new XtremeApiCacheReloader().reloadCache(account, null);
        }

        List<Category> savedLiveCategories = CategoryDb.get().getCategories(account);
        assertEquals(3, savedLiveCategories.size());
        assertEquals(3, ChannelDb.get().getChannelCountForAccount(account.getDbId()));

        account.setAction(Account.AccountAction.vod);
        assertEquals(1, VodCategoryDb.get().getCategories(account).size());
        account.setAction(Account.AccountAction.series);
        assertEquals(1, SeriesCategoryDb.get().getCategories(account).size());
    }

    @Test
    void reloadCache_fallsBackToCategoryFetchWhenGlobalLookupIsUncategorizedOnly() {
        Account account = persistAccount("xtreme-itv-category", Account.AccountAction.itv);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        List<Category> liveCategories = List.of(
                new Category("10", "News", "news", false, 0),
                new Category("20", "Sports", "sports", false, 0)
        );

        try (MockedStatic<CategoryService> categoriesStatic = Mockito.mockStatic(CategoryService.class);
             MockedStatic<XtremeApiParser> xtremeStatic = Mockito.mockStatic(XtremeApiParser.class)) {
            categoriesStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            Mockito.when(categoryService.get(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.itv), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(liveCategories);
            Mockito.when(categoryService.getFresh(Mockito.any(Account.class), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(List.of());
            xtremeStatic.when(() -> XtremeApiParser.parseAllChannels(account)).thenReturn(List.of(channel("uncat", "", "Loose channel")));
            xtremeStatic.when(() -> XtremeApiParser.parseChannels("10", account)).thenReturn(List.of(channel("news-1", "10", "News A")));
            xtremeStatic.when(() -> XtremeApiParser.parseChannels("20", account)).thenReturn(List.of(channel("sports-1", "20", "Sports A")));

            new XtremeApiCacheReloader().reloadCache(account, null);
        }

        assertEquals(2, CategoryDb.get().getCategories(account).size());
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
    }

    private Account persistAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(action);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(action);
        return saved;
    }

    private void seedAllCacheTables(Account account) {
        account.setAction(Account.AccountAction.itv);
        CategoryDb.get().saveAll(List.of(new Category("live-old", "Old Live", "old-live", false, 0)), account);
        Category liveCategory = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(List.of(channel("live-ch-old", "live-old", "Old Live Channel")), liveCategory.getDbId(), account);

        account.setAction(Account.AccountAction.vod);
        VodCategoryDb.get().saveAll(List.of(new Category("vod-old", "Old Movies", "old-movies", false, 0)), account);
        VodChannelDb.get().saveAll(List.of(channel("vod-ch-old", "vod-old", "Old Movie")), "vod-old", account);

        account.setAction(Account.AccountAction.series);
        SeriesCategoryDb.get().saveAll(List.of(new Category("series-old", "Old Shows", "old-shows", false, 0)), account);
        SeriesChannelDb.get().saveAll(List.of(channel("series-ch-old", "series-old", "Old Show")), "series-old", account);
        SeriesEpisodeDb.get().saveAll(account, "series-old", "series-ch-old", List.of(channel("episode-old", "series-old", "Old Episode")));
    }

    private static Channel channel(String id, String categoryId, String name) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setCategoryId(categoryId);
        channel.setName(name);
        channel.setCmd("http://example.test/" + id + ".m3u8");
        return channel;
    }
}
