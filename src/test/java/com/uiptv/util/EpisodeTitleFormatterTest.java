package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class EpisodeTitleFormatterTest {
    private String originalLanguageTag;

    @BeforeEach
    void captureLocale() {
        originalLanguageTag = I18n.getCurrentLanguageTag();
    }

    @AfterEach
    void restoreLocale() {
        I18n.setLocale(originalLanguageTag);
    }

    @Test
    void localizedUrduGenericTitleIsNotDuplicated() {
        I18n.setLocale("ur-PK");

        String displayTitle = EpisodeTitleFormatter.buildEpisodeDisplayTitle("2", "11", "دوسرا سیزن - قسط 11");

        assertEquals("دوسرا سیزن - گیارہویں قسط", displayTitle);
    }

    @Test
    void localizedUrduEpisodeOnlyGenericTitleIsNotDuplicated() {
        I18n.setLocale("ur-PK");

        String displayTitle = EpisodeTitleFormatter.buildEpisodeDisplayTitle("2", "11", "قسط 11");

        assertEquals("دوسرا سیزن - گیارہویں قسط", displayTitle);
    }

    @Test
    void nonGenericTitleIsRetained() {
        I18n.setLocale("en-US");

        String displayTitle = EpisodeTitleFormatter.buildEpisodeDisplayTitle("2", "11", "The One with the Lesbian Wedding");

        assertEquals("Season 2 - Episode 11: The One with the Lesbian Wedding", displayTitle);
    }
}
