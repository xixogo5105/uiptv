package com.uiptv.shared.schema;

public enum UiptvTable {
    CONFIGURATION("Configuration"),
    ACCOUNT("Account"),
    ACCOUNT_INFO("AccountInfo"),
    BOOKMARK("Bookmark"),
    CATEGORY("Category"),
    CHANNEL("Channel"),
    VOD_CATEGORY("VodCategory"),
    VOD_CHANNEL("VodChannel"),
    VOD_WATCH_STATE("VodWatchState"),
    SERIES_CATEGORY("SeriesCategory"),
    SERIES_CHANNEL("SeriesChannel"),
    SERIES_EPISODE("SeriesEpisode"),
    SERIES_WATCH_STATE("SeriesWatchState"),
    SERIES_WATCHING_NOW_SNAPSHOT("SeriesWatchingNowSnapshot"),
    PUBLISHED_M3U_SELECTION("PublishedM3uSelection"),
    PUBLISHED_M3U_CATEGORY_SELECTION("PublishedM3uCategorySelection"),
    PUBLISHED_M3U_CHANNEL_SELECTION("PublishedM3uChannelSelection"),
    BOOKMARK_CATEGORY("BookmarkCategory"),
    BOOKMARK_ORDER("BookmarkOrder");

    private final String tableName;

    UiptvTable(String tableName) {
        this.tableName = tableName;
    }

    public String tableName() {
        return tableName;
    }
}
