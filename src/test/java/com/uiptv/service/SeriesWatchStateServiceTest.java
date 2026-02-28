package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.util.AccountType;
import com.uiptv.util.ServerUrlUtil;
import org.junit.jupiter.api.Test;

import static com.uiptv.model.Account.AccountAction.series;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesWatchStateServiceTest extends DbBackedTest {

    @Test
    void playbackResolved_progressesForwardOnlyForSeries() {
        Account account = createSeriesAccount("watch-series-auto");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        service.onPlaybackResolved(account, episode("ep-2", "Episode 2", "2"), "ep-2", "series-1");
        SeriesWatchState afterEp2 = service.getSeriesLastWatched(account.getDbId(), "series-1");
        assertNotNull(afterEp2);
        assertEquals("ep-2", afterEp2.getEpisodeId());
        assertEquals(2, afterEp2.getEpisodeNum());
        assertEquals("AUTO", afterEp2.getSource());

        service.onPlaybackResolved(account, episode("ep-1", "Episode 1", "1"), "ep-1", "series-1");
        SeriesWatchState afterEp1 = service.getSeriesLastWatched(account.getDbId(), "series-1");
        assertNotNull(afterEp1);
        assertEquals("ep-2", afterEp1.getEpisodeId(), "Pointer should not regress on lower episode");

        service.onPlaybackResolved(account, episode("ep-3", "Episode 3", "3"), "ep-3", "series-1");
        SeriesWatchState afterEp3 = service.getSeriesLastWatched(account.getDbId(), "series-1");
        assertNotNull(afterEp3);
        assertEquals("ep-3", afterEp3.getEpisodeId());
        assertEquals(3, afterEp3.getEpisodeNum());
    }

    @Test
    void manualMark_canMovePointerAndClearState() {
        Account account = createSeriesAccount("watch-series-manual");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        service.onPlaybackResolved(account, episode("ep-3", "Episode 3", "3"), "ep-3", "series-2");
        service.markSeriesEpisodeManual(account, "series-2", "ep-1", "Episode 1", "1", "1");

        SeriesWatchState manual = service.getSeriesLastWatched(account.getDbId(), "series-2");
        assertNotNull(manual);
        assertEquals("ep-1", manual.getEpisodeId());
        assertEquals(1, manual.getEpisodeNum());
        assertEquals("MANUAL", manual.getSource());

        service.clearSeriesLastWatched(account.getDbId(), "series-2");
        assertNull(service.getSeriesLastWatched(account.getDbId(), "series-2"));
    }

    @Test
    void sameSeriesId_isScopedByCategoryId() {
        Account account = createSeriesAccount("watch-series-category-scope");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        service.onPlaybackResolved(account, episode("ep-a2", "Episode 2", "2"), "ep-a2", "series-dup", "cat-a");
        service.onPlaybackResolved(account, episode("ep-b3", "Episode 3", "3"), "ep-b3", "series-dup", "cat-b");

        SeriesWatchState catA = service.getSeriesLastWatched(account.getDbId(), "cat-a", "series-dup");
        SeriesWatchState catB = service.getSeriesLastWatched(account.getDbId(), "cat-b", "series-dup");
        assertNotNull(catA);
        assertNotNull(catB);
        assertEquals("ep-a2", catA.getEpisodeId());
        assertEquals("ep-b3", catB.getEpisodeId());
    }

    @Test
    void getSeriesLastWatched_fallsBackWhenCategoryDiffers() {
        Account account = createSeriesAccount("watch-series-category-fallback");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        service.onPlaybackResolved(account, episode("ep-4", "Episode 4", "4"), "ep-4", "series-fallback", "portal-cat-201");
        SeriesWatchState state = service.getSeriesLastWatched(account.getDbId(), "db-cat-999", "series-fallback");

        assertNotNull(state);
        assertEquals("ep-4", state.getEpisodeId());
    }

    @Test
    void matching_isSeasonAware_whenEpisodeIdsRepeatAcrossSeasons() {
        Account account = createSeriesAccount("watch-series-season-aware");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        service.markSeriesEpisodeManual(account, "cat-1", "series-1", "episode-10", "Season 2 Episode 10", "2", "10");
        SeriesWatchState watched = service.getSeriesLastWatched(account.getDbId(), "cat-1", "series-1");

        assertNotNull(watched);
        assertTrue(service.isMatchingEpisode(watched, "episode-10", "2", "10", "Season 2 Episode 10"));
        assertFalse(service.isMatchingEpisode(watched, "episode-10", "1", "10", "Season 1 Episode 10"));
    }

    @Test
    void playbackResolved_infersSeasonFromTitle_whenSeasonMissing() {
        Account account = createSeriesAccount("watch-series-infer-season");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        Channel channel = new Channel();
        channel.setChannelId("episode-15");
        channel.setName("S03E15 - Finale");
        channel.setEpisodeNum("15");
        channel.setSeason("");
        channel.setCmd("http://127.0.0.1/media/episode-15.m3u8");

        service.onPlaybackResolved(account, channel, "episode-15", "series-3", "cat-1");

        SeriesWatchState state = service.getSeriesLastWatched(account.getDbId(), "cat-1", "series-3");
        assertNotNull(state);
        assertEquals("3", state.getSeason());
        assertEquals(15, state.getEpisodeNum());
    }

    @Test
    void playbackResolved_advancesAcrossSeasons_evenWhenEpisodeNumberResets() {
        Account account = createSeriesAccount("watch-series-season-progress");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        service.onPlaybackResolved(account, episode("ep-s1e10", "Episode 10", "10"), "ep-s1e10", "series-4", "cat-1");
        SeriesWatchState afterS1 = service.getSeriesLastWatched(account.getDbId(), "cat-1", "series-4");
        assertNotNull(afterS1);
        assertEquals("1", afterS1.getSeason());
        assertEquals(10, afterS1.getEpisodeNum());

        Channel s2e1 = episode("ep-s2e1", "Episode 1", "1");
        s2e1.setSeason("2");
        service.onPlaybackResolved(account, s2e1, "ep-s2e1", "series-4", "cat-1");

        SeriesWatchState afterS2 = service.getSeriesLastWatched(account.getDbId(), "cat-1", "series-4");
        assertNotNull(afterS2);
        assertEquals("ep-s2e1", afterS2.getEpisodeId());
        assertEquals("2", afterS2.getSeason());
        assertEquals(1, afterS2.getEpisodeNum());
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
        account.setAction(series);
        AccountService.getInstance().save(account);
        Account saved = AccountService.getInstance().getByName(name);
        saved.setAction(series);
        return saved;
    }

    private Channel episode(String episodeId, String name, String episodeNum) {
        Channel channel = new Channel();
        channel.setChannelId(episodeId);
        channel.setName(name);
        channel.setEpisodeNum(episodeNum);
        channel.setSeason("1");
        channel.setCmd("http://127.0.0.1/media/" + episodeId + ".m3u8");
        return channel;
    }
}
