package com.uiptv.application;

import com.uiptv.model.Channel;

import java.util.List;

public record WatchingNowSeriesActionRequest(
        String accountId,
        String categoryId,
        String seriesId,
        String episodeId,
        String episodeName,
        String season,
        String episodeNum,
        String categoryDbId,
        String seriesTitle,
        String seriesPoster,
        List<Channel> episodes
) {
}
