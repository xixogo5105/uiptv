package com.uiptv.application;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesEpisodeDb;
import com.uiptv.db.SeriesWatchStateDb;
import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.service.SeriesWatchingNowSnapshotService;
import com.uiptv.service.VodWatchStateService;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchingNowApplicationServiceTest extends DbBackedTest {

    private final WatchingNowApplicationService service = WatchingNowApplicationService.getInstance();

    @Test
    void listSeriesRows_sortsAndMapsResolverRows() {
        Account account = createAccount("watch-series-list", Account.AccountAction.series);
        Category category = new Category("cat-api", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(category), account);
        Category savedCategory = SeriesCategoryDb.get().getCategories(account).getFirst();

        Channel series = new Channel();
        series.setChannelId("series-1");
        series.setName("Series One");
        series.setLogo("https://img/series.png");
        SeriesChannelDb.get().saveAll(List.of(series), savedCategory.getDbId(), account);

        SeriesWatchState state = new SeriesWatchState();
        state.setAccountId(account.getDbId());
        state.setMode("series");
        state.setCategoryId(savedCategory.getCategoryId());
        state.setSeriesId("series-1");
        state.setEpisodeId("ep-2");
        state.setEpisodeName("Episode 2");
        state.setSeason("1");
        state.setEpisodeNum(2);
        state.setUpdatedAt(200L);
        SeriesWatchStateDb.get().upsert(state);

        List<WatchingNowSeriesRow> rows = service.listSeriesRows();

        assertEquals(1, rows.size());
        WatchingNowSeriesRow row = rows.getFirst();
        assertEquals(account.getDbId(), row.accountId());
        assertEquals("Series One", row.seriesTitle());
        assertEquals("https://img/series.png", row.seriesPoster());
        assertEquals("Episode 2", row.episodeName());
        assertEquals(savedCategory.getDbId(), row.categoryDbId());
    }

    @Test
    void listSeriesEpisodes_usesCachedEpisodesAppliesWatchedFlagAndSavesSnapshot() {
        Account account = createAccount("watch-series-episodes", Account.AccountAction.series);
        Category category = new Category("cat-api", "Series", "series", false, 0);
        SeriesCategoryDb.get().saveAll(List.of(category), account);
        Category savedCategory = SeriesCategoryDb.get().getCategories(account).getFirst();

        Channel episode1 = episode("ep-1", "Episode 1", "1", "1");
        Channel episode2 = episode("ep-2", "Episode 2", "1", "2");
        SeriesEpisodeDb.get().saveAll(account, savedCategory.getCategoryId(), "series-2", List.of(episode1, episode2));
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(account, savedCategory.getCategoryId(), "series-2", "ep-2", "Episode 2", "1", "2");

        List<Channel> episodes = service.listSeriesEpisodes(account.getDbId(), savedCategory.getCategoryId(), "series-2");

        assertEquals(2, episodes.size());
        assertFalse(episodes.getFirst().isWatched());
        assertTrue(episodes.get(1).isWatched());
        assertNotNull(SeriesWatchingNowSnapshotService.getInstance().getSnapshot(account.getDbId(), savedCategory.getCategoryId(), "series-2"));
        assertTrue(service.listSeriesEpisodes("missing", savedCategory.getCategoryId(), "series-2").isEmpty());
        assertTrue(service.listSeriesEpisodes(account.getDbId(), savedCategory.getCategoryId(), "").isEmpty());
    }

    @Test
    void saveAndRemoveSeries_updatesWatchStateAndSnapshot() {
        Account account = createAccount("watch-series-save", Account.AccountAction.series);
        Channel episode = episode("ep-9", "Episode 9", "2", "9");

        service.saveSeriesEpisode(new WatchingNowSeriesActionRequest(
                account.getDbId(), "cat-9", "series-9", "ep-9", "Episode 9",
                "2", "9", "cat-db-9", "Series Nine", "poster", List.of(episode)
        ), account);

        SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), "cat-9", "series-9");
        assertNotNull(state);
        assertEquals("ep-9", state.getEpisodeId());
        assertNotNull(SeriesWatchingNowSnapshotService.getInstance().getSnapshot(account.getDbId(), "cat-9", "series-9"));

        service.removeSeries(account.getDbId(), "cat-9", "series-9");
        assertNull(SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), "cat-9", "series-9"));
    }

    @Test
    void saveListAndRemoveVod_roundTripsRows() {
        Account account = createAccount("watch-vod", Account.AccountAction.vod);
        Channel provider = new Channel();
        provider.setChannelId("vod-1");
        provider.setName("Provider Movie");
        provider.setLogo("https://img/provider.png");
        provider.setDescription("Provider Plot");
        provider.setReleaseDate("2024");
        provider.setRating("7.1");
        provider.setDuration("100");
        provider.setExtraJson(new org.json.JSONObject()
                .put("description", "Provider Plot")
                .put("release_date", "2024")
                .put("rating", "7.1")
                .put("duration", "100")
                .toString());
        VodChannelDb.get().saveAll(List.of(provider), "vod-cat", account);

        service.saveVod(new WatchingNowVodActionRequest(account.getDbId(), "vod-cat", "vod-1", "Saved Movie", "http://vod/1", "https://img/state.png"), account);

        List<WatchingNowVodRow> rows = service.listVodRows();

        assertEquals(1, rows.size());
        WatchingNowVodRow row = rows.getFirst();
        assertEquals(account.getDbId(), row.accountId());
        assertEquals("Saved Movie", row.vodName());
        assertEquals("Provider Plot", row.plot());
        assertEquals("7.1", row.rating());
        assertNotNull(row.playItem());

        service.removeVod(account.getDbId(), "vod-cat", "vod-1");
        assertTrue(VodWatchStateService.getInstance().getAllByAccount(account.getDbId()).isEmpty());
        assertEquals(account.getDbId(), service.getAccount(account.getDbId()).getDbId());
    }

    private Account createAccount(String name, Account.AccountAction action) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(action);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(action);
        return persisted;
    }

    private Channel episode(String id, String name, String season, String episodeNumber) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName(name);
        channel.setCmd("http://origin/" + id + ".m3u8");
        channel.setSeason(season);
        channel.setEpisodeNum(episodeNumber);
        return channel;
    }
}
