package com.uiptv.service.cache;

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
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.HandshakeService;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StalkerPortalCacheReloaderTest extends DbBackedTest {

    @Test
    void reloadCache_forVodClearsAllAccountCacheTablesBeforeSavingFreshCategories() {
        Account account = persistAccount("stalker-vod-clear", Account.AccountAction.vod);
        seedAllCacheTables(account);
        account.setAction(Account.AccountAction.vod);

        try (MockedStatic<HandshakeService> handshakeStatic = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchStatic = Mockito.mockStatic(FetchAPI.class)) {
            HandshakeService handshakeService = Mockito.mock(HandshakeService.class);
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(handshakeService);
            Mockito.doAnswer(invocation -> {
                Account requestedAccount = invocation.getArgument(0);
                requestedAccount.setToken("token");
                requestedAccount.setServerPortalUrl("http://stalker.example/server/load.php");
                return null;
            }).when(handshakeService).connect(Mockito.any(Account.class));
            fetchStatic.when(() -> FetchAPI.fetch(Mockito.anyMap(), Mockito.eq(account))).thenReturn("""
                    {"js":[{"id":"vod-fresh","title":"Fresh Movies","alias":"fresh","active_sub":true,"censored":0}]}
                    """);

            new StalkerPortalCacheReloader().reloadCache(account, null);
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

    private Account persistAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://stalker.example/portal.php",
                "00:11:22:33:44:55", null, null, null, null, null,
                AccountType.STALKER_PORTAL, null, null, false);
        account.setServerPortalUrl("http://stalker.example/server/load.php");
        account.setAction(action);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(action);
        saved.setServerPortalUrl("http://stalker.example/server/load.php");
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
