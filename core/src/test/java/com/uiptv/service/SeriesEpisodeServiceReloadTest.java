package com.uiptv.service;

import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeApiParser;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeriesEpisodeServiceReloadTest extends DbBackedTest {

    @Test
    void reloadEpisodesFromPortal_bypassesCacheAndPersistsFreshXtremeEpisodes() {
        Account account = createSeriesAccount("series-episode-xtreme-reload");
        String categoryId = "cat-10";
        String seriesId = "series-300";
        SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, List.of(channel("cached-ep-1", "Cached Episode", "http://127.0.0.1/cached.m3u8")));

        Episode freshEpisode = new Episode();
        freshEpisode.setId("fresh-ep-9");
        freshEpisode.setTitle("Fresh Episode");
        freshEpisode.setCmd("http://127.0.0.1/fresh.m3u8");
        freshEpisode.setSeason("2");
        freshEpisode.setEpisodeNum("9");
        EpisodeList portalResponse = new EpisodeList();
        portalResponse.setEpisodes(List.of(freshEpisode));

        try (MockedStatic<XtremeApiParser> xtremeParserMock = Mockito.mockStatic(XtremeApiParser.class)) {
            xtremeParserMock.when(() -> XtremeApiParser.parseEpisodes(seriesId, account)).thenReturn(portalResponse);

            EpisodeList reloaded = SeriesEpisodeService.getInstance()
                    .reloadEpisodesFromPortal(account, categoryId, seriesId, () -> false);

            assertEquals(1, reloaded.getEpisodes().size());
            assertEquals("fresh-ep-9", reloaded.getEpisodes().getFirst().getId());
            xtremeParserMock.verify(() -> XtremeApiParser.parseEpisodes(seriesId, account), Mockito.times(1));
        }

        List<Channel> stored = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        assertEquals(1, stored.size());
        assertEquals("fresh-ep-9", stored.getFirst().getChannelId());
        assertEquals("http://127.0.0.1/fresh.m3u8", stored.getFirst().getCmd());
    }

    @Test
    void reloadEpisodesFromPortal_bypassesCacheAndPersistsFreshStalkerEpisodes() {
        Account account = createStalkerAccount("series-episode-stalker-reload");
        String categoryId = "cat-s";
        String seriesId = "series-s";
        SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, List.of(channel("cached-stalker", "Cached Episode", "cmd://cached")));

        ChannelService channelService = Mockito.mock(ChannelService.class);
        List<Channel> remote = List.of(channel("fresh-stalker", "Season 4 Episode 2", "cmd://fresh-stalker"));

        try (MockedStatic<ChannelService> channelServiceStatic = Mockito.mockStatic(ChannelService.class)) {
            channelServiceStatic.when(ChannelService::getInstance).thenReturn(channelService);
            Mockito.when(channelService.getSeries(Mockito.eq(categoryId), Mockito.eq(seriesId), Mockito.eq(account), Mockito.isNull(), Mockito.any()))
                    .thenReturn(remote);

            EpisodeList reloaded = SeriesEpisodeService.getInstance()
                    .reloadEpisodesFromPortal(account, categoryId, seriesId, () -> false);

            assertEquals(1, reloaded.getEpisodes().size());
            assertEquals("fresh-stalker", reloaded.getEpisodes().getFirst().getId());
            Mockito.verify(channelService, Mockito.times(1))
                    .getSeries(Mockito.eq(categoryId), Mockito.eq(seriesId), Mockito.eq(account), Mockito.isNull(), Mockito.any());
        }

        List<Channel> stored = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        assertEquals(1, stored.size());
        assertEquals("fresh-stalker", stored.getFirst().getChannelId());
        assertEquals("cmd://fresh-stalker", stored.getFirst().getCmd());
    }

    @Test
    void reloadEpisodesFromPortal_fallsBackToAnyAgeCacheWhenXtremeProviderFails() {
        Account account = createSeriesAccount("series-episode-reload-fallback");
        String categoryId = "cat-a";
        String seriesId = "series-fallback";
        SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, List.of(channel("cached-ep", "Season 1 Episode 8", "cmd://cached")));

        try (MockedStatic<XtremeApiParser> xtremeParser = Mockito.mockStatic(XtremeApiParser.class)) {
            xtremeParser.when(() -> XtremeApiParser.parseEpisodes(seriesId, account)).thenThrow(new RuntimeException("boom"));

            EpisodeList list = SeriesEpisodeService.getInstance()
                    .reloadEpisodesFromPortal(account, "cat-other", seriesId, () -> false);

            assertEquals(1, list.getEpisodes().size());
            assertEquals("8", list.getEpisodes().getFirst().getEpisodeNum());
            xtremeParser.verify(() -> XtremeApiParser.parseEpisodes(seriesId, account), Mockito.times(1));
        }
    }

    private Channel channel(String id, String name, String cmd) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd(cmd);
        return channel;
    }

    private Account createSeriesAccount(String name) {
        Account account = baseAccount(name, AccountType.XTREME_API);
        account.setAction(Account.AccountAction.series);
        return persist(account);
    }

    private Account createStalkerAccount(String name) {
        Account account = baseAccount(name, AccountType.STALKER_PORTAL);
        account.setAction(Account.AccountAction.series);
        account.setMacAddress("00:11:22:33:44:55");
        return persist(account);
    }

    private Account baseAccount(String name, AccountType type) {
        return new Account(name, "user", "pass", "http://127.0.0.1/mock", null, null, null, null, null, null, type, null, "http://127.0.0.1/mock", false);
    }

    private Account persist(Account account) {
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(account.getAccountName());
        saved.setAction(account.getAction());
        saved.setMacAddress(account.getMacAddress());
        return saved;
    }
}
