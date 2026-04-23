package com.uiptv.service;

@FunctionalInterface
public interface SeriesWatchStateChangeListener {
    void onSeriesWatchStateChanged(String accountId, String seriesId);
}
