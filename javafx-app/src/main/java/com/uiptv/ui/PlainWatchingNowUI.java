package com.uiptv.ui;

public class PlainWatchingNowUI extends BaseWatchingNowUI {
    public PlainWatchingNowUI() {
        super();
    }

    @Override
    protected boolean thumbnailsEnabled() {
        return false;
    }
}
