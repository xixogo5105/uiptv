package com.uiptv.service;

import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesEpisodeServiceCoverageTest extends DbBackedTest {

    @Test
    void getEpisodes_returnsEmptyForMissingInputs() {
        assertTrue(SeriesEpisodeService.getInstance().getEpisodes(null, "cat", "series", () -> false).getEpisodes().isEmpty());
        assertTrue(SeriesEpisodeService.getInstance().getEpisodes(createSeriesAccount("blank-series"), "cat", " ", () -> false).getEpisodes().isEmpty());
    }

    @Test
    void getEpisodes_prefersFreshSpecificCache() {
        Account account = createSeriesAccount("fresh-specific-cache");
        SeriesEpisodeDb.get().saveAll(account, "cat-a", "series-1", List.of(channel("ep-1", "Season 2 Episode 7", "cmd://1")));

        EpisodeList list = SeriesEpisodeService.getInstance().getEpisodes(account, "cat-a", "series-1", () -> false);

        assertEquals(1, list.getEpisodes().size());
        assertEquals("2", list.getEpisodes().get(0).getSeason());
        assertEquals("7", list.getEpisodes().get(0).getEpisodeNum());
    }

    @Test
    void getEpisodes_usesFreshAnyCategoryCacheForXtreme() {
        Account account = createSeriesAccount("fresh-any-category-cache");
        SeriesEpisodeDb.get().saveAll(account, "cat-a", "series-2", List.of(channel("ep-2", "Season 03 Episode 04", "cmd://2")));

        EpisodeList list = SeriesEpisodeService.getInstance().getEpisodes(account, "cat-b", "series-2", () -> false);

        assertEquals(1, list.getEpisodes().size());
        assertEquals("03", list.getEpisodes().get(0).getSeason());
        assertEquals("04", list.getEpisodes().get(0).getEpisodeNum());
    }

    @Test
    void reloadEpisodesFromPortal_fallsBackToAnyAgeCacheWhenProviderFails() {
        Account account = createSeriesAccount("reload-fallback-cache");
        SeriesEpisodeDb.get().saveAll(account, "cat-a", "series-3", List.of(channel("ep-3", "1x9 Finale", "cmd://3")));

        try (MockedStatic<XtremeParser> xtremeParser = Mockito.mockStatic(XtremeParser.class)) {
            xtremeParser.when(() -> XtremeParser.parseEpisodes("series-3", account)).thenThrow(new RuntimeException("boom"));

            EpisodeList list = SeriesEpisodeService.getInstance().reloadEpisodesFromPortal(account, "cat-b", "series-3", () -> false);

            assertEquals(1, list.getEpisodes().size());
            assertEquals("9", list.getEpisodes().get(0).getEpisodeNum());
        }
    }

    @Test
    void getEpisodes_fetchesAndPersistsStalkerEpisodes() {
        Account account = createStalkerAccount("stalker-series-fetch");
        ChannelService channelService = Mockito.mock(ChannelService.class);
        List<Channel> remote = List.of(channel("stalker-1", "Season 4 Episode 2", "cmd://stalker"));

        try (MockedStatic<ChannelService> channelServiceStatic = Mockito.mockStatic(ChannelService.class)) {
            channelServiceStatic.when(ChannelService::getInstance).thenReturn(channelService);
            Mockito.when(channelService.getSeries(Mockito.eq("cat-s"), Mockito.eq("series-s"), Mockito.eq(account), Mockito.isNull(), Mockito.any()))
                    .thenReturn(remote);

            EpisodeList list = SeriesEpisodeService.getInstance().getEpisodes(account, "cat-s", "series-s", () -> false);

            assertEquals(1, list.getEpisodes().size());
            assertEquals("4", list.getEpisodes().get(0).getSeason());
            assertEquals(1, SeriesEpisodeDb.get().getEpisodes(account, "cat-s", "series-s").size());
        }
    }

    @Test
    void privateHelpers_restoreAndMergeEpisodeMetadata() throws Exception {
        SeriesEpisodeService service = SeriesEpisodeService.getInstance();
        Channel channel = channel("ep-json", "Season 5 Episode 11", "cmd://json");
        channel.setLogo("logo.png");
        channel.setDescription("plot");
        channel.setReleaseDate("2024-01-01");
        channel.setRating("8.0");
        channel.setDuration("42m");

        Episode partial = new Episode();
        partial.setId("ep-json");
        partial.setTitle("");
        partial.setCmd("cmd://json");
        channel.setExtraJson(partial.toJson());

        Method toEpisode = SeriesEpisodeService.class.getDeclaredMethod("toEpisode", Channel.class);
        toEpisode.setAccessible(true);
        Episode restored = (Episode) toEpisode.invoke(service, channel);

        assertEquals("Season 5 Episode 11", restored.getTitle());
        assertEquals("5", restored.getSeason());
        assertEquals("11", restored.getEpisodeNum());
        assertNotNull(restored.getInfo());
        assertEquals("logo.png", restored.getInfo().getMovieImage());
        assertEquals("plot", restored.getInfo().getPlot());

        Method extractSeason = SeriesEpisodeService.class.getDeclaredMethod("extractSeason", String.class);
        extractSeason.setAccessible(true);
        Method extractEpisode = SeriesEpisodeService.class.getDeclaredMethod("extractEpisode", String.class);
        extractEpisode.setAccessible(true);
        assertEquals("08", extractSeason.invoke(service, "S08E03"));
        assertEquals("3", extractEpisode.invoke(service, "Season 8 Episode 3"));
        assertEquals("123", extractEpisode.invoke(service, "E123"));
        assertEquals("1", extractSeason.invoke(service, "Unknown Title"));
        assertEquals("", extractEpisode.invoke(service, "Unknown Title"));
    }

    private static Channel channel(String id, String name, String cmd) {
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
