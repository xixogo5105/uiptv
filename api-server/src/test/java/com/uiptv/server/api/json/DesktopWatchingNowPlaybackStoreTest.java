package com.uiptv.server.api.json;

import com.uiptv.application.WatchingNowSeriesRow;
import com.uiptv.model.Channel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DesktopWatchingNowPlaybackStoreTest {
    @Test
    void episodePlaybackReferenceKeepsEpisodeDataServerSide() {
        DesktopWatchingNowPlaybackStore store = DesktopWatchingNowPlaybackStore.getInstance();
        WatchingNowSeriesRow row = new WatchingNowSeriesRow(
                "account-row", "Main", "XTREME_API", "provider-category", "category-row",
                "provider-series", "last-episode", "Episode 1", "1", 1,
                "Series", "", 1L
        );
        String seriesId = store.register(row);
        DesktopWatchingNowPlaybackStore.Selection series = store.resolve(seriesId);

        Channel episode = new Channel();
        episode.setChannelId("provider-episode");
        episode.setName("Episode 2");
        episode.setSeason("2");
        episode.setEpisodeNum("3");
        episode.setCmd("https://provider.test/private-command");
        String playbackId = store.registerEpisode(series, episode);

        DesktopWatchingNowPlaybackStore.Selection resolved = store.resolve(playbackId);
        assertNotNull(resolved);
        assertEquals("account-row", resolved.accountId());
        assertEquals("provider-category", resolved.categoryId());
        assertEquals("category-row", resolved.categoryDbId());
        assertEquals("provider-series", resolved.seriesId());
        assertEquals("provider-episode", resolved.episodeId());
        assertEquals("series", resolved.mode());
        assertTrue(resolved.channel().getCmd().contains("private-command"));
    }

    @Test
    void episodeReferenceCannotBeCreatedFromNonSeriesOrMissingEpisode() {
        DesktopWatchingNowPlaybackStore store = DesktopWatchingNowPlaybackStore.getInstance();
        WatchingNowSeriesRow row = new WatchingNowSeriesRow(
                "account-row", "Main", "XTREME_API", "provider-category", "category-row",
                "provider-series", "last-episode", "Episode 1", "1", 1,
                "Series", "", 1L
        );
        DesktopWatchingNowPlaybackStore.Selection series = store.resolve(store.register(row));
        Channel missingId = new Channel();
        assertEquals("", store.registerEpisode(series, missingId));
        assertEquals("", store.registerEpisode(store.resolve("missing"), new Channel()));
    }
}
