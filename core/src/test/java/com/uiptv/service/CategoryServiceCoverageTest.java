package com.uiptv.service;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CategoryServiceCoverageTest extends DbBackedTest {

    @TempDir
    Path tempPath;

    @Test
    void getCached_readsAppropriateBackingStore() {
        assertTrue(CategoryService.getInstance().getCached(null).isEmpty());

        Account itv = persistAccount("cached-itv", Account.AccountAction.itv, AccountType.XTREME_API, null);
        CategoryDb.get().saveAll(List.of(new Category("1", "News", "news", false, 0)), itv);
        assertEquals(1, CategoryService.getInstance().getCached(itv).size());

        Account vod = persistAccount("cached-vod", Account.AccountAction.vod, AccountType.XTREME_API, null);
        VodCategoryDb.get().saveAll(List.of(new Category("2", "Movies", "movies", false, 0)), vod);
        assertEquals(1, CategoryService.getInstance().getCached(vod).size());

        Account series = persistAccount("cached-series", Account.AccountAction.series, AccountType.XTREME_API, null);
        SeriesCategoryDb.get().saveAll(List.of(new Category("3", "Shows", "shows", false, 0)), series);
        assertEquals(1, CategoryService.getInstance().getCached(series).size());
    }

    @Test
    void get_usesFreshVodSeriesCacheWithoutProviderCall() {
        Account account = persistAccount("fresh-vod-cache", Account.AccountAction.vod, AccountType.XTREME_API, null);
        VodCategoryDb.get().saveAll(List.of(new Category("vod-1", "Movies", "movies", false, 0)), account);

        List<Category> categories = CategoryService.getInstance().get(account, false, null);

        assertEquals(1, categories.size());
        assertEquals("Movies", categories.get(0).getTitle());
    }

    @Test
    void get_fetchesAndStoresVodSeriesCategoriesWhenCacheMissing() {
        Account account = persistAccount("provider-vod-cache", Account.AccountAction.vod, AccountType.XTREME_API, null);
        List<Category> remote = List.of(new Category("vod-2", "Cinema", "cinema", false, 0));

        try (MockedStatic<XtremeApiParser> xtremeParser = Mockito.mockStatic(XtremeApiParser.class)) {
            xtremeParser.when(() -> XtremeApiParser.parseCategories(account)).thenReturn(remote);

            List<Category> categories = CategoryService.getInstance().get(account, false, null);

            assertEquals(1, categories.size());
            assertEquals("Cinema", categories.get(0).getTitle());
        }

        assertEquals(1, VodCategoryDb.get().getCategories(account).size());
    }

    @Test
    void get_nonLiveXtremeUsesCategoryDbCacheAndReadToJsonSerializesResults() {
        Account account = persistAccount("series-category-cache", Account.AccountAction.series, AccountType.XTREME_API, null);
        SeriesCategoryDb.get().saveAll(List.of(new Category("series-1", "Shows", "shows", false, 0)), account);

        List<Category> categories = CategoryService.getInstance().get(account, false, null);
        String json = CategoryService.getInstance().readToJson(account);

        assertEquals(1, categories.size());
        assertTrue(json.contains("Shows"));
    }

    @Test
    void get_m3u8LocalReloadsCategoriesFromPlaylist() throws Exception {
        Path playlist = tempPath.resolve("sample.m3u");
        Files.writeString(playlist, """
                #EXTM3U
                #EXTINF:-1 tvg-id="one" group-title="News",News One
                http://example.test/news.m3u8
                #EXTINF:-1 tvg-id="two" group-title="Sports",Sports One
                http://example.test/sports.m3u8
                """);

        Account account = persistAccount("m3u-local", Account.AccountAction.itv, AccountType.M3U8_LOCAL, playlist.toString());

        List<Category> categories = CategoryService.getInstance().get(account, false, null);

        assertTrue(categories.size() >= 2);
        assertFalse(CategoryDb.get().getCategories(account).isEmpty());
    }

    private Account persistAccount(String name, Account.AccountAction action, AccountType type, String m3uPath) {
        Account account = new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, type, null, m3uPath, false);
        account.setAction(action);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(action);
        saved.setM3u8Path(m3uPath);
        return saved;
    }
}
