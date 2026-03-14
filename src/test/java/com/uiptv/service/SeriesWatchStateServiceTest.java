package com.uiptv.service;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.util.AccountType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import static com.uiptv.model.Account.AccountAction.series;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

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

    @Test
    void manualUpdate_preservesExistingSnapshots_whenCacheLookupsFail() {
        Account account = createSeriesAccount("watch-series-snapshot-preserve");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        Channel snapshotChannel = new Channel();
        snapshotChannel.setChannelId("12345");
        snapshotChannel.setName("Law & Order");
        snapshotChannel.setLogo("http://img/law.png");
        String channelSnapshotJson = new JSONObject(snapshotChannel.toJson()).toString();

        Category snapshotCategory = new Category("cat-1", "Drama", "drama", false, 0);
        String categorySnapshotJson = new JSONObject(snapshotCategory.toJson()).toString();

        SeriesWatchState existing = new SeriesWatchState();
        existing.setAccountId(account.getDbId());
        existing.setMode("series");
        existing.setCategoryId("cat-1");
        existing.setSeriesId("12345");
        existing.setEpisodeId("ep-1");
        existing.setEpisodeName("Episode 1");
        existing.setSeason("1");
        existing.setEpisodeNum(1);
        existing.setUpdatedAt(100L);
        existing.setSource("MANUAL");
        existing.setSeriesCategorySnapshot(categorySnapshotJson);
        existing.setSeriesChannelSnapshot(channelSnapshotJson);
        existing.setSeriesEpisodeSnapshot("{\"id\":\"ep-1\"}");
        com.uiptv.db.SeriesWatchStateDb.get().upsert(existing);

        service.markSeriesEpisodeManualIfNewer(account, "cat-1", "12345", "ep-2", "Episode 2", "1", "2");

        SeriesWatchState updated = service.getSeriesLastWatched(account.getDbId(), "cat-1", "12345");
        assertNotNull(updated);
        assertEquals("ep-2", updated.getEpisodeId());
        assertEquals(channelSnapshotJson, updated.getSeriesChannelSnapshot());
        assertEquals(categorySnapshotJson, updated.getSeriesCategorySnapshot());
        assertNotEquals("", updated.getSeriesChannelSnapshot());
        assertEquals("", updated.getSeriesEpisodeSnapshot());
    }

    @Test
    void manualUpdate_resolvesColonSeriesIdFromCache() {
        Account account = createSeriesAccount("watch-series-colon-cache");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        SeriesCategoryDb.get().saveAll(
                java.util.List.of(new Category("1714", "Drama", "drama", false, 0)),
                account
        );
        Category category = SeriesCategoryDb.get().getCategories(account).getFirst();

        Channel seriesChannel = new Channel();
        seriesChannel.setChannelId("37177:37177");
        seriesChannel.setName("EN - Friends 4K (1994–2004)");
        seriesChannel.setLogo("http://img/friends.png");
        SeriesChannelDb.get().saveAll(java.util.List.of(seriesChannel), category.getDbId(), account);

        Channel episode = new Channel();
        episode.setChannelId("8");
        episode.setName("Season 1 - Episode 8");
        episode.setSeason("1");
        episode.setEpisodeNum("8");
        SeriesEpisodeDb.get().saveAll(account, category.getDbId(), "37177:37177", java.util.List.of(episode));

        service.markSeriesEpisodeManual(account, "1714", "37177", "8", "Season 1 - Episode 8", "1", "8");

        SeriesWatchState updated = service.getSeriesLastWatched(account.getDbId(), "1714", "37177");
        assertNotNull(updated);
        assertNotEquals("", updated.getSeriesChannelSnapshot());
        assertNotEquals("", updated.getSeriesEpisodeSnapshot());
    }

    @Test
    void manualUpdate_normalizesColonDelimitedSeriesIds() {
        Account account = createSeriesAccount("watch-series-normalize");
        SeriesWatchStateService service = SeriesWatchStateService.getInstance();

        service.markSeriesEpisodeManual(account, "cat-1", "37177:37177", "ep-8", "Episode 8", "1", "8");

        SeriesWatchState updated = service.getSeriesLastWatched(account.getDbId(), "cat-1", "37177");
        assertNotNull(updated);
        assertEquals("37177", updated.getSeriesId());
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
