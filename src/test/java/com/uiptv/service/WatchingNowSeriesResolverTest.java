package com.uiptv.service;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.db.SeriesWatchStateDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.util.AccountType;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WatchingNowSeriesResolverTest extends DbBackedTest {

    @Test
    void resolve_prefersSnapshotTitle_andDedupesIdentity() {
        Account account = createSeriesAccount("resolver-series-snapshot");

        SeriesCategoryDb.get().saveAll(List.of(
                new Category("cat-api-1", "Drama", "drama", false, 0)
        ), account);
        Category drama = SeriesCategoryDb.get().getCategories(account).getFirst();

        SeriesChannelDb.get().saveAll(List.of(
                channel("11706:11706", "Cache Title", "https://img/cache.png")
        ), drama.getDbId(), account);

        SeriesWatchState older = state(account, drama.getCategoryId(), "11706:11706", "ep-1", "Episode 1", "1", 1, 100L);
        SeriesWatchState newer = state(account, drama.getCategoryId(), "11706", "ep-2", "Episode 2", "1", 2, 200L);
        Channel snapshotChannel = new Channel();
        snapshotChannel.setChannelId("11706:11706");
        snapshotChannel.setCategoryId(drama.getCategoryId());
        snapshotChannel.setName("Snapshot Title");
        snapshotChannel.setLogo("https://img/snapshot.png");
        newer.setSeriesChannelSnapshot(new JSONObject(snapshotChannel.toJson()).toString());

        SeriesWatchStateDb.get().upsert(older);
        SeriesWatchStateDb.get().upsert(newer);

        WatchingNowSeriesResolver resolver = new WatchingNowSeriesResolver();
        List<WatchingNowSeriesResolver.SeriesRow> rows = resolver.resolveForAccount(account);

        assertEquals(1, rows.size());
        WatchingNowSeriesResolver.SeriesRow row = rows.getFirst();
        assertEquals("Snapshot Title", row.getSeriesTitle());
        assertEquals("https://img/snapshot.png", row.getSeriesPoster());
        assertEquals("11706:11706", row.getState().getSeriesId());
    }

    @Test
    void resolve_skipsNumericSeriesWithoutCache() {
        Account account = createSeriesAccount("resolver-series-numeric");

        SeriesWatchStateDb.get().upsert(state(account, "unknown", "12345", "ep-1", "Episode 1", "1", 1, 100L));

        WatchingNowSeriesResolver resolver = new WatchingNowSeriesResolver();
        List<WatchingNowSeriesResolver.SeriesRow> rows = resolver.resolveForAccount(account);

        assertTrue(rows.isEmpty());
    }

    private Account createSeriesAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com/xtreme/", null, null, null, null, null, null, AccountType.XTREME_API, null, "http://test.com/xtreme/", false);
        account.setAction(Account.AccountAction.series);
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName(name);
        persisted.setAction(Account.AccountAction.series);
        return persisted;
    }

    private Channel channel(String channelId, String name, String logo) {
        Channel channel = new Channel();
        channel.setChannelId(channelId);
        channel.setName(name);
        channel.setLogo(logo);
        channel.setCmd("http://example.com/" + channelId);
        return channel;
    }

    private SeriesWatchState state(Account account, String categoryId, String seriesId, String episodeId,
                                   String episodeName, String season, int episodeNum, long updatedAt) {
        SeriesWatchState state = new SeriesWatchState();
        state.setAccountId(account.getDbId());
        state.setMode("series");
        state.setCategoryId(categoryId);
        state.setSeriesId(seriesId);
        state.setEpisodeId(episodeId);
        state.setEpisodeName(episodeName);
        state.setSeason(season);
        state.setEpisodeNum(episodeNum);
        state.setUpdatedAt(updatedAt);
        state.setSource("MANUAL");
        state.setSeriesCategorySnapshot("");
        state.setSeriesChannelSnapshot("");
        state.setSeriesEpisodeSnapshot("");
        return state;
    }
}
