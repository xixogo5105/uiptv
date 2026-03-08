package com.uiptv.util;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

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

    @Test
    void genericEpisodePatternsAndLocalizedLabelsAreCollapsed() {
        I18n.setLocale("en-US");

        assertTrue(EpisodeTitleFormatter.isGenericEpisodeTitle("Episode 9"));
        assertTrue(EpisodeTitleFormatter.isGenericEpisodeTitle("ep 9"));
        assertTrue(EpisodeTitleFormatter.isGenericEpisodeTitle("e9"));
        assertTrue(EpisodeTitleFormatter.isGenericEpisodeTitle("Season 2 Episode 9"));
        assertTrue(EpisodeTitleFormatter.isGenericEpisodeTitle("Doosra season qist 9"));
        assertFalse(EpisodeTitleFormatter.isGenericEpisodeTitle("The Finale"));

        assertEquals("Season 2 - Episode 11", EpisodeTitleFormatter.buildEpisodeDisplayTitle("2", "11", "Season 2 - Episode 11"));
        assertEquals("Season 1 - Episode 5", EpisodeTitleFormatter.buildEpisodeDisplayTitle("", "5", ""));
    }
}
