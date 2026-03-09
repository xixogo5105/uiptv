package com.uiptv.ui;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchingNowUITest {

    @Test
    void tabLabels_exposeSeriesFirstAndVodSecond() {
        assertEquals(List.of("Series", "VOD"), WatchingNowUI.tabLabels());
    }
}
