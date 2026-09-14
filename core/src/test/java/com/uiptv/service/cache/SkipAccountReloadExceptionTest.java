package com.uiptv.service.cache;

import static org.mockito.Mockito.argThat;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import com.uiptv.api.LoggerCallback;
import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
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

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SkipAccountReloadExceptionTest extends DbBackedTest {

    @Test
    void skipAccountReloadException_fromCategoryFetchIsNotSilentlyCaught() {
        Account account = createAndPersistXtremeAccount("xtreme-exc-1", Account.AccountAction.itv);
        CategoryService categoryService = mock(CategoryService.class);
        List<Category> vodCategories = List.of(new Category("vod-1", "Movies", "movies", false, 0));
        List<Category> seriesCategories = List.of(new Category("series-1", "Shows", "shows", false, 0));

        try (MockedStatic<CategoryService> categoriesStatic = mockStatic(CategoryService.class);
             MockedStatic<XtremeApiParser> xtremeStatic = mockStatic(XtremeApiParser.class)) {
            categoriesStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.itv), eq(false), isNull()))
                    .thenReturn(List.of());
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.vod), eq(false), isNull()))
                    .thenReturn(vodCategories);
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.series), eq(false), isNull()))
                    .thenReturn(seriesCategories);
            xtremeStatic.when(() -> XtremeApiParser.parseAllChannels(account))
                    .thenReturn(List.of(channel("c1", "10", "News One")));
            xtremeStatic.when(() -> XtremeApiParser.parseChannels("10", account))
                    .thenReturn(List.of(channel("news-1", "10", "News A")));

            assertThrows(SkipAccountReloadException.class, () ->
                    new XtremeApiCacheReloader().reloadCache(account, msg -> {
                        throw new SkipAccountReloadException();
                    })
            );
        }

        // Action must be restored after exception propagates from cacheVodAndSeriesCategoriesOnly
        assertEquals(Account.AccountAction.itv, account.getAction());
    }

    @Test
    void skipAccountReloadException_fromVodModeRestoresAction() {
        Account account = createAndPersistXtremeAccount("xtreme-exc-2", Account.AccountAction.itv);
        CategoryService categoryService = mock(CategoryService.class);

        try (MockedStatic<CategoryService> categoriesStatic = mockStatic(CategoryService.class)) {
            categoriesStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.itv), eq(false), isNull()))
                    .thenReturn(List.of());
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.vod), eq(false), isNull()))
                    .thenThrow(new SkipAccountReloadException());
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.series), eq(false), isNull()))
                    .thenReturn(List.of());
        }

        assertThrows(SkipAccountReloadException.class, () ->
                new XtremeApiCacheReloader().reloadCache(account, msg -> {
                    throw new SkipAccountReloadException();
                })
        );

        // Action must be restored even when VOD mode throws
        assertEquals(Account.AccountAction.itv, account.getAction());
    }

    @Test
    void skipAccountReloadException_fromSeriesModeRestoresAction() {
        Account account = createAndPersistXtremeAccount("xtreme-exc-3", Account.AccountAction.itv);
        CategoryService categoryService = mock(CategoryService.class);

        try (MockedStatic<CategoryService> categoriesStatic = mockStatic(CategoryService.class)) {
            categoriesStatic.when(CategoryService::getInstance).thenReturn(categoryService);
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.itv), eq(false), isNull()))
                    .thenReturn(List.of());
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.vod), eq(false), isNull()))
                    .thenReturn(List.of());
            when(categoryService.get(argThat(a -> a != null && a.getAction() == Account.AccountAction.series), eq(false), isNull()))
                    .thenThrow(new SkipAccountReloadException());
        }

        assertThrows(SkipAccountReloadException.class, () ->
                new XtremeApiCacheReloader().reloadCache(account, msg -> {
                    throw new SkipAccountReloadException();
                })
        );

        // Action must be restored even when SERIES mode throws
        assertEquals(Account.AccountAction.itv, account.getAction());
    }

    @Test
    void skipAccountReloadException_fromXtremeGlobalLookupNotSilentlyCaught() {
        Account account = createAndPersistXtremeAccount("xtreme-exc-4", Account.AccountAction.itv);

        try (MockedStatic<XtremeApiParser> xtremeStatic = mockStatic(XtremeApiParser.class)) {
            xtremeStatic.when(() -> XtremeApiParser.parseAllChannels(account))
                    .thenThrow(new SkipAccountReloadException());
        }

        assertThrows(SkipAccountReloadException.class, () ->
                new XtremeApiCacheReloader().reloadCache(account, msg -> {
                    throw new SkipAccountReloadException();
                })
        );
    }

    private Account createAndPersistXtremeAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, AccountType.XTREME_API, null, null, false);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
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
}
