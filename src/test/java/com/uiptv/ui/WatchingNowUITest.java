package com.uiptv.ui;

import com.uiptv.util.I18n;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class WatchingNowUITest {
    private String originalLanguageTag;

    @BeforeEach
    void captureLocale() {
        originalLanguageTag = I18n.getCurrentLanguageTag();
        I18n.setLocale("en-US");
    }

    @AfterEach
    void restoreLocale() {
        I18n.setLocale(originalLanguageTag);
    }

    @Test
    void tabLabels_exposeSeriesFirstAndVodSecond() {
        assertEquals(List.of("Series", "VOD"), WatchingNowUI.tabLabels());
    }
}
