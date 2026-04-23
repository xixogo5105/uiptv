package com.uiptv.service;

import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BingeWatchServiceTest {

    @Test
    void orderSeasonEpisodes_startsAtWatchedEpisodeWithinSelectedSeason() {
        BingeWatchService service = BingeWatchService.getInstance();
        SeriesWatchState watchState = new SeriesWatchState();
        watchState.setSeason("2");
        watchState.setEpisodeId("s2e2");
        watchState.setEpisodeNum(2);

        List<BingeWatchService.SessionEpisode> ordered = service.orderSeasonEpisodes("2", List.of(
                episode("s1e1", "1", "1"),
                episode("s2e1", "2", "1"),
                episode("s2e2", "2", "2"),
                episode("s2e3", "2", "3")
        ), watchState);

        assertEquals(List.of("s2e2", "s2e3"), ordered.stream().map(BingeWatchService.SessionEpisode::episodeId).toList());
    }

    @Test
    void orderSeasonEpisodes_ignoresWatchStateFromDifferentSeason() {
        BingeWatchService service = BingeWatchService.getInstance();
        SeriesWatchState watchState = new SeriesWatchState();
        watchState.setSeason("1");
        watchState.setEpisodeId("s1e2");
        watchState.setEpisodeNum(2);

        List<BingeWatchService.SessionEpisode> ordered = service.orderSeasonEpisodes("2", List.of(
                episode("s2e1", "2", "1"),
                episode("s2e2", "2", "2")
        ), watchState);

        assertEquals(List.of("s2e1", "s2e2"), ordered.stream().map(BingeWatchService.SessionEpisode::episodeId).toList());
    }

    @Test
    void orderSeasonEpisodes_startsFromEpisodeOneWhenWatchFlagIsFromDifferentSeason() {
        BingeWatchService service = BingeWatchService.getInstance();
        SeriesWatchState watchState = new SeriesWatchState();
        watchState.setSeason("1");
        watchState.setEpisodeId("s1e3");
        watchState.setEpisodeNum(3);

        List<BingeWatchService.SessionEpisode> ordered = service.orderSeasonEpisodes("2", List.of(
                episode("s2e1", "2", "1"),
                episode("s2e2", "2", "2"),
                episode("s2e3", "2", "3")
        ), watchState);

        assertEquals(List.of("s2e1", "s2e2", "s2e3"), ordered.stream().map(BingeWatchService.SessionEpisode::episodeId).toList());
    }

    private Channel episode(String id, String season, String episodeNumber) {
        Channel channel = new Channel();
        channel.setChannelId(id);
        channel.setName("Episode " + episodeNumber);
        channel.setSeason(season);
        channel.setEpisodeNum(episodeNumber);
        channel.setCmd("http://example.com/" + id + ".m3u8");
        return channel;
    }
}
