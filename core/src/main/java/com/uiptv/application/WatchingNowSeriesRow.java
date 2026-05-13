package com.uiptv.application;

public record WatchingNowSeriesRow(
        String accountId,
        String accountName,
        String accountType,
        String categoryId,
        String categoryDbId,
        String seriesId,
        String episodeId,
        String episodeName,
        String season,
        int episodeNum,
        String seriesTitle,
        String seriesPoster,
        long updatedAt
) {
}
