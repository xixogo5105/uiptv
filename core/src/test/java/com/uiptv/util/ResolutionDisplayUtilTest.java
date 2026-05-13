package com.uiptv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResolutionDisplayUtilTest {

    @Test
    void normalize_mapsKnownResolutionsWithinTolerance() {
        assertEquals("720p", ResolutionDisplayUtil.normalize(1288, 718).shortText());
        assertEquals("1080p", ResolutionDisplayUtil.normalize(1920, 1080).shortText());
        assertEquals("2K", ResolutionDisplayUtil.normalize(2550, 1440).shortText());
        assertEquals("4K UHD", ResolutionDisplayUtil.normalize(3840, 2160).shortText());
        assertEquals("8K UHD", ResolutionDisplayUtil.normalize(7680, 4320).shortText());
    }

    @Test
    void normalize_keepsDimensionsWhenUnknownOrTooSmall() {
        ResolutionDisplayUtil.ResolutionDisplay invalid = ResolutionDisplayUtil.normalize(0, -1);
        assertEquals("0x-1", invalid.dimensionsText());
        assertEquals("0x-1", invalid.shortText());

        assertEquals("", ResolutionDisplayUtil.normalize(640, 480).label());
        assertEquals("1111x777", ResolutionDisplayUtil.normalize(1111, 777).shortText());
    }
}
