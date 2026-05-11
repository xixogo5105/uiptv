package com.uiptv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolutionDisplayUtilTest {
    @Test
    void normalize_returnsBlankForInvalidOrTooSmallDimensions() {
        assertEquals("", ResolutionDisplayUtil.normalize(0, 1080).getLabel());
        assertEquals("", ResolutionDisplayUtil.normalize(1280, 700).getLabel());
    }

    @Test
    void normalize_matchesNearestKnownProfileWithinTolerance() {
        ResolutionDisplayUtil.ResolutionDisplay display = ResolutionDisplayUtil.normalize(1910, 1078);

        assertEquals(1920, display.getWidth());
        assertEquals(1080, display.getHeight());
        assertEquals("1080p", display.getLabel());
        assertEquals("1080p", display.shortText());
    }

    @Test
    void normalize_returnsDimensionsWhenNoProfileMatches() {
        ResolutionDisplayUtil.ResolutionDisplay display = ResolutionDisplayUtil.normalize(1600, 900);

        assertEquals("", display.getLabel());
        assertEquals("1600x900", display.dimensionsText());
        assertEquals("1600x900", display.shortText());
    }
}
