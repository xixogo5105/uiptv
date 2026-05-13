package com.uiptv.service;

@FunctionalInterface
public interface BookmarkChangeListener {
    void onBookmarksChanged(long revision, long updatedEpochMs);
}

