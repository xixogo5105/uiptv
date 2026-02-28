package com.uiptv.service;

import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.XtremeParser;
import com.uiptv.util.AccountType;
import com.uiptv.util.ServerUrlUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SeriesEpisodeServiceReloadTest extends DbBackedTest {

    @Test
    void reloadEpisodesFromPortal_bypassesCacheAndPersistsFreshEpisodes() {
        Account account = createSeriesAccount("series-episode-reload-test");
        String categoryId = "cat-10";
        String seriesId = "series-300";

        Channel cached = new Channel();
        cached.setChannelId("cached-ep-1");
        cached.setName("Cached Episode");
        cached.setCmd("http://127.0.0.1/cached.m3u8");
        cached.setSeason("1");
        cached.setEpisodeNum("1");
        SeriesEpisodeDb.get().saveAll(account, categoryId, seriesId, List.of(cached));

        Episode freshEpisode = new Episode();
        freshEpisode.setId("fresh-ep-9");
        freshEpisode.setTitle("Fresh Episode");
        freshEpisode.setCmd("http://127.0.0.1/fresh.m3u8");
        freshEpisode.setSeason("2");
        freshEpisode.setEpisodeNum("9");
        EpisodeList portalResponse = new EpisodeList();
        portalResponse.setEpisodes(List.of(freshEpisode));

        try (MockedStatic<XtremeParser> xtremeParserMock = Mockito.mockStatic(XtremeParser.class)) {
            xtremeParserMock.when(() -> XtremeParser.parseEpisodes(seriesId, account)).thenReturn(portalResponse);

            EpisodeList reloaded = SeriesEpisodeService.getInstance()
                    .reloadEpisodesFromPortal(account, categoryId, seriesId, () -> false);

            assertEquals(1, reloaded.getEpisodes().size());
            assertEquals("fresh-ep-9", reloaded.getEpisodes().get(0).getId());
            xtremeParserMock.verify(() -> XtremeParser.parseEpisodes(seriesId, account), Mockito.times(1));
        }

        List<Channel> stored = SeriesEpisodeDb.get().getEpisodes(account, categoryId, seriesId);
        assertEquals(1, stored.size());
        assertEquals("fresh-ep-9", stored.get(0).getChannelId());
        assertEquals("http://127.0.0.1/fresh.m3u8", stored.get(0).getCmd());
    }

    private Account createSeriesAccount(String name) {
        Account account = new Account(
                name,
                "user",
                "pass",
                "http://127.0.0.1/mock",
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.XTREME_API,
                null,
                "http://127.0.0.1/mock",
                false
        );
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(Account.AccountAction.series);
        return saved;
    }
}
