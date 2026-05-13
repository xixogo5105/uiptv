package com.uiptv.service;

import com.uiptv.db.SeriesWatchingNowSnapshotDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchingNowSnapshot;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeInfo;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SeriesWatchingNowSnapshotServiceTest extends DbBackedTest {

    private final SeriesWatchingNowSnapshotService service = SeriesWatchingNowSnapshotService.getInstance();

    @Test
    void saveLoadAndClear_roundTripsEpisodesAndCanonicalSeriesIds() {
        Account account = account("snapshots");
        EpisodeList list = new EpisodeList();
        list.getEpisodes().add(episode("ep-1", "Pilot", "1", "1"));
        list.getEpisodes().add(episode("ep-2", "Second", "1", "2"));

        service.save(account, " cat-1 ", "11706:11706", " cat-db ", " Series Title ", " poster ", list);

        SeriesWatchingNowSnapshot snapshot = service.getSnapshot(account.getDbId(), "cat-1", "11706");
        assertNotNull(snapshot);
        assertEquals("11706", snapshot.getSeriesId());
        assertEquals("Series Title", snapshot.getSeriesTitle());

        EpisodeList loadedEpisodes = service.loadEpisodeList(account.getDbId(), "cat-1", "11706:11706");
        assertEquals(2, loadedEpisodes.getEpisodes().size());
        assertEquals("Pilot", loadedEpisodes.getEpisodes().getFirst().getTitle());
        assertEquals("Poster ep-1", loadedEpisodes.getEpisodes().getFirst().getInfo().getMovieImage());

        List<Channel> channels = service.loadChannels(account.getDbId(), "cat-1", "11706");
        assertEquals(2, channels.size());
        assertEquals("Second", channels.get(1).getName());
        assertEquals("Plot ep-2", channels.get(1).getDescription());

        service.clear(account.getDbId(), "cat-1", "11706:11706");
        assertNull(service.getSnapshot(account.getDbId(), "cat-1", "11706"));
    }

    @Test
    void getSnapshot_fallsBackToNewestSnapshotAcrossCategories() {
        Account account = account("snapshot-fallback");
        SeriesWatchingNowSnapshot older = snapshot(account, "cat-a", "42", "Older", 100L);
        SeriesWatchingNowSnapshot newer = snapshot(account, "cat-b", "42:42", "Newer", 200L);
        SeriesWatchingNowSnapshotDb.get().upsert(older);
        SeriesWatchingNowSnapshotDb.get().upsert(newer);

        SeriesWatchingNowSnapshot resolved = service.getSnapshot(account.getDbId(), "", "42");

        assertNotNull(resolved);
        assertEquals("Newer", resolved.getSeriesTitle());
    }

    @Test
    void invalidInputsAndMalformedPayloads_returnEmptyResults() {
        Account account = account("snapshot-invalid");
        assertNull(service.getSnapshot("", "cat", "series"));
        assertTrue(service.loadChannels(account.getDbId(), "cat", "missing").isEmpty());
        assertTrue(service.loadEpisodeList(account.getDbId(), "cat", "missing").getEpisodes().isEmpty());

        SeriesWatchingNowSnapshot malformed = snapshot(account, "cat", "series", "Broken", 1L);
        malformed.setEpisodesJson("not-json");
        SeriesWatchingNowSnapshotDb.get().upsert(malformed);

        assertTrue(service.loadChannels(account.getDbId(), "cat", "series").isEmpty());
        assertTrue(service.loadEpisodeList(account.getDbId(), "cat", "series").getEpisodes().isEmpty());

        service.save(null, "cat", "series", "", "", "", new EpisodeList());
        service.saveChannels(account, "cat", "series", "", "", "", List.of());
    }

    private Account account(String name) {
        Account account = new Account(name, "user", "pass", "http://test/xtreme", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test/xtreme", false);
        account.setDbId(name + "-id");
        return account;
    }

    private Episode episode(String id, String title, String season, String episodeNumber) {
        Episode episode = new Episode();
        episode.setId(id);
        episode.setTitle(title);
        episode.setCmd("http://origin/" + id + ".m3u8");
        episode.setSeason(season);
        episode.setEpisodeNum(episodeNumber);
        EpisodeInfo info = new EpisodeInfo();
        info.setMovieImage("Poster " + id);
        info.setPlot("Plot " + id);
        info.setReleaseDate("2024-01-0" + episodeNumber);
        info.setRating("8." + episodeNumber);
        info.setDuration("4" + episodeNumber);
        episode.setInfo(info);
        return episode;
    }

    private SeriesWatchingNowSnapshot snapshot(Account account, String categoryId, String seriesId, String title, long updatedAt) {
        SeriesWatchingNowSnapshot snapshot = new SeriesWatchingNowSnapshot();
        snapshot.setAccountId(account.getDbId());
        snapshot.setCategoryId(categoryId);
        snapshot.setSeriesId(seriesId);
        snapshot.setCategoryDbId(categoryId + "-db");
        snapshot.setSeriesTitle(title);
        snapshot.setSeriesPoster("poster");
        snapshot.setEpisodesJson("[\"{\\\"channelId\\\":\\\"ep\\\",\\\"name\\\":\\\"Episode\\\",\\\"cmd\\\":\\\"http://origin\\\"}\"]");
        snapshot.setUpdatedAt(updatedAt);
        return snapshot;
    }
}
