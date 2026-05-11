package com.uiptv.service.cache;

import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XtremeApiCacheReloaderTest extends DbBackedTest {

    @Test
    void reloadCache_forVodPersistsOnlyVodCategories() {
        Account account = persistAccount("xtreme-vod", Account.AccountAction.vod);
        CategoryService categoryService = Mockito.mock(CategoryService.class);
        List<Category> vodCategories = List.of(new Category("vod-1", "Movies", "movies", false, 0));

        Mockito.when(categoryService.get(account, false, (LoggerCallback) null)).thenReturn(vodCategories);

        new XtremeApiCacheReloader(categoryServiceProvider(categoryService), configurationServiceProvider()).reloadCache(account, null);

        assertEquals(1, VodCategoryDb.get().getCategories(account).size());
        assertTrue(CategoryDb.get().getCategories(account).isEmpty());
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

        try (var xtremeStatic = Mockito.mockStatic(XtremeApiParser.class)) {
            Mockito.when(categoryService.get(account, false, (LoggerCallback) null)).thenReturn(liveCategories);
            Mockito.when(categoryService.get(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.vod), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(vodCategories);
            Mockito.when(categoryService.get(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.series), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(seriesCategories);
            xtremeStatic.when(() -> XtremeApiParser.parseAllChannels(account)).thenReturn(allChannels);

            new XtremeApiCacheReloader(categoryServiceProvider(categoryService), configurationServiceProvider()).reloadCache(account, null);
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

        try (var xtremeStatic = Mockito.mockStatic(XtremeApiParser.class)) {
            Mockito.when(categoryService.get(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.itv), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(liveCategories);
            Mockito.when(categoryService.get(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.vod), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(List.of());
            Mockito.when(categoryService.get(Mockito.argThat(a -> a != null && a.getAction() == Account.AccountAction.series), Mockito.eq(false), Mockito.isNull()))
                    .thenReturn(List.of());
            xtremeStatic.when(() -> XtremeApiParser.parseAllChannels(account)).thenReturn(List.of(channel("uncat", "", "Loose channel")));
            xtremeStatic.when(() -> XtremeApiParser.parseChannels("10", account)).thenReturn(List.of(channel("news-1", "10", "News A")));
            xtremeStatic.when(() -> XtremeApiParser.parseChannels("20", account)).thenReturn(List.of(channel("sports-1", "20", "Sports A")));

            new XtremeApiCacheReloader(categoryServiceProvider(categoryService), configurationServiceProvider()).reloadCache(account, null);
        }

        assertEquals(2, CategoryDb.get().getCategories(account).size());
        assertEquals(2, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
    }

    private Account persistAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        account.setAction(action);
        AccountService.INSTANCE.save(account);
        Account saved = AccountService.INSTANCE.getByName(name);
        saved.setAction(action);
        return saved;
    }

    private static Channel channel(String id, String categoryId, String name) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setCategoryId(categoryId);
        channel.setName(name);
        channel.setCmd("http://example.test/" + id + ".m3u8");
        return channel;
    }

    private static kotlin.jvm.functions.Function0<CategoryService> categoryServiceProvider(CategoryService categoryService) {
        return () -> categoryService;
    }

    private static kotlin.jvm.functions.Function0<ConfigurationService> configurationServiceProvider() {
        return () -> ConfigurationService.INSTANCE;
    }
}
