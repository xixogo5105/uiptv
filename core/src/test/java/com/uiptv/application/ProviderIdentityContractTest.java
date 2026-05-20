package com.uiptv.application;

import com.uiptv.db.CategoryDb;
import com.uiptv.db.ChannelDb;
import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.VodCategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.AccountService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.HandshakeService;
import com.uiptv.service.PlayerRequestResolver;
import com.uiptv.service.PlayerService;
import com.uiptv.util.AccountType;
import com.uiptv.util.FetchAPI;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ProviderIdentityContractTest extends DbBackedTest {

    @Test
    void listChannelsTranslatesUiCategoryDbIdToProviderCategoryIdBeforeLoadingChannels() throws Exception {
        Account account = saveAccount("identity-series-catalog", AccountType.XTREME_API, Account.AccountAction.series);
        SeriesCategoryDb.get().saveAll(List.of(new Category("portal-series-cat-900", "Drama", "drama", false, 0)), account);
        Category category = SeriesCategoryDb.get().getCategories(account).getFirst();
        assertNotEquals(category.getDbId(), category.getCategoryId());

        Channel providerRow = new Channel();
        providerRow.setChannelId("provider-series-77");
        providerRow.setName("Provider Series");
        providerRow.setCategoryId(category.getCategoryId());

        ChannelService channelService = Mockito.mock(ChannelService.class);
        try (MockedStatic<ChannelService> channelServiceStatic = Mockito.mockStatic(ChannelService.class)) {
            channelServiceStatic.when(ChannelService::getInstance).thenReturn(channelService);
            Mockito.when(channelService.get(
                    Mockito.eq(category.getCategoryId()),
                    Mockito.any(Account.class),
                    Mockito.eq(category.getDbId())
            )).thenReturn(List.of(providerRow));

            List<Channel> channels = CatalogApplicationService.getInstance().listChannels(
                    new CatalogChannelsQuery(account.getDbId(), CatalogMode.SERIES, category.getDbId(), "")
            );

            assertEquals(List.of("provider-series-77"), channels.stream().map(Channel::getChannelId).toList());
            Mockito.verify(channelService).get(
                    Mockito.eq(category.getCategoryId()),
                    Mockito.any(Account.class),
                    Mockito.eq(category.getDbId())
            );
            Mockito.verify(channelService, Mockito.never()).get(
                    Mockito.eq(category.getDbId()),
                    Mockito.any(Account.class),
                    Mockito.eq(category.getDbId())
            );
        }
    }

    @Test
    void stalkerWebChannelsUseProviderCategoryIdInServerRequestParams() throws Exception {
        Account account = saveAccount("identity-stalker-vod", AccountType.STALKER_PORTAL, Account.AccountAction.vod);
        VodCategoryDb.get().saveAll(List.of(new Category("portal-vod-cat-901", "Movies", "movies", false, 0)), account);
        Category category = VodCategoryDb.get().getCategories(account).getFirst();
        assertNotEquals(category.getDbId(), category.getCategoryId());

        List<Map<String, String>> requests = new ArrayList<>();
        try (MockedStatic<HandshakeService> handshakeStatic = Mockito.mockStatic(HandshakeService.class);
             MockedStatic<FetchAPI> fetchStatic = Mockito.mockStatic(FetchAPI.class)) {
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(Mockito.mock(HandshakeService.class));
            fetchStatic.when(() -> FetchAPI.fetch(Mockito.anyMap(), Mockito.any(Account.class))).thenAnswer(invocation -> {
                Map<String, String> params = invocation.getArgument(0);
                requests.add(new LinkedHashMap<>(params));
                return """
                        {
                          "pagination": {"total_items": 1, "max_page_items": 25},
                          "js": {"data": [
                            {"id": "provider-vod-11", "name": "Movie One", "cmd": "movie-cmd", "tv_genre_id": "portal-vod-cat-901",
                             "screenshot_uri": "", "censored": 0, "status": 1, "hd": 0}
                          ]}
                        }
                        """;
            });

            CatalogPagedChannelsResult result = CatalogApplicationService.getInstance().listWebChannels(
                    new CatalogWebChannelsQuery(account.getDbId(), CatalogMode.VOD, category.getDbId(), "", 0, 25, 1, 0)
            );

            assertFalse(result.items().isEmpty());
            assertEquals("provider-vod-11", result.items().getFirst().getChannelId());
            assertEquals("portal-vod-cat-901", requests.getFirst().get("genre"));
            assertNotEquals(category.getDbId(), requests.getFirst().get("genre"));
        }
    }

    @Test
    void directLivePlaybackUsesChannelDbIdForLocalLookupButProviderChannelIdForPlayback() throws Exception {
        Account account = saveAccount("identity-live-playback", AccountType.XTREME_API, Account.AccountAction.itv);
        CategoryDb.get().saveAll(List.of(new Category("portal-live-cat-902", "News", "news", false, 0)), account);
        Category category = CategoryDb.get().getCategories(account).getFirst();
        assertNotEquals(category.getDbId(), category.getCategoryId());

        Channel liveChannel = new Channel("portal-live-channel-33", "News One", "1", "http://stream/live",
                null, null, null, "logo", 0, 1, 1, null, null, null, null, null);
        ChannelDb.get().saveAll(List.of(liveChannel), category.getDbId(), account);
        Channel storedChannel = ChannelDb.get().getChannels(category.getDbId()).getFirst();
        assertNotEquals(storedChannel.getDbId(), storedChannel.getChannelId());

        PlayerService playerService = Mockito.mock(PlayerService.class);
        try (MockedStatic<PlayerService> playerStatic = Mockito.mockStatic(PlayerService.class)) {
            playerStatic.when(PlayerService::getInstance).thenReturn(playerService);
            Mockito.when(playerService.get(
                    Mockito.any(Account.class),
                    Mockito.any(Channel.class),
                    Mockito.anyString(),
                    Mockito.anyString(),
                    Mockito.anyString()
            )).thenReturn(new PlayerResponse("http://resolved/live"));

            new PlayerRequestResolver().resolveDirectPlayback(
                    account,
                    category.getDbId(),
                    storedChannel.getDbId(),
                    "itv",
                    "",
                    "",
                    null
            );

            ArgumentCaptor<Channel> channelCaptor = ArgumentCaptor.forClass(Channel.class);
            Mockito.verify(playerService).get(
                    Mockito.eq(account),
                    channelCaptor.capture(),
                    Mockito.eq(""),
                    Mockito.eq(""),
                    Mockito.eq("")
            );
            assertEquals(storedChannel.getDbId(), channelCaptor.getValue().getDbId());
            assertEquals("portal-live-channel-33", channelCaptor.getValue().getChannelId());
        }
    }

    @Test
    void directSeriesPlaybackPassesProviderCategoryIdToPlayerService() throws Exception {
        Account account = saveAccount("identity-series-playback", AccountType.XTREME_API, Account.AccountAction.series);
        SeriesCategoryDb.get().saveAll(List.of(new Category("portal-series-cat-903", "Shows", "shows", false, 0)), account);
        Category category = SeriesCategoryDb.get().getCategories(account).getFirst();
        assertNotEquals(category.getDbId(), category.getCategoryId());

        Channel requestEpisode = new Channel();
        requestEpisode.setChannelId("provider-episode-44");
        requestEpisode.setName("Episode 44");
        requestEpisode.setCmd("http://stream/episode-44");

        PlayerService playerService = Mockito.mock(PlayerService.class);
        try (MockedStatic<PlayerService> playerStatic = Mockito.mockStatic(PlayerService.class)) {
            playerStatic.when(PlayerService::getInstance).thenReturn(playerService);
            Mockito.when(playerService.get(
                    Mockito.any(Account.class),
                    Mockito.any(Channel.class),
                    Mockito.anyString(),
                    Mockito.anyString(),
                    Mockito.anyString()
            )).thenReturn(new PlayerResponse("http://resolved/series"));

            new PlayerRequestResolver().resolveDirectPlayback(
                    account,
                    category.getDbId(),
                    requestEpisode.getChannelId(),
                    "series",
                    "provider-series-55",
                    requestEpisode.getChannelId(),
                    requestEpisode
            );

            Mockito.verify(playerService).get(
                    account,
                    requestEpisode,
                    "provider-episode-44",
                    "provider-series-55",
                    "portal-series-cat-903"
            );
        }
    }

    private Account saveAccount(String name, AccountType type, Account.AccountAction action) {
        Account account = new Account(
                name,
                "user",
                "pass",
                "http://portal.test/stalker_portal/server/load.php",
                "00:11:22:33:44:55",
                null,
                null,
                null,
                null,
                null,
                type,
                null,
                "http://portal.test/player_api.php",
                false
        );
        account.setAction(action);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(action);
        return saved;
    }
}
