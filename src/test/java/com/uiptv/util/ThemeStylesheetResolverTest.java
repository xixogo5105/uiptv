package com.uiptv.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertTrue;

class ThemeStylesheetResolverTest {

    @Test
    void buildSceneRootStyle_preservesFractionalZoomFontSize() {
        String ninetyFive = ThemeStylesheetResolver.buildSceneRootStyle(95);
        String hundredFive = ThemeStylesheetResolver.buildSceneRootStyle(105);
        String hundredThirtyThree = ThemeStylesheetResolver.buildSceneRootStyle(133);

        assertTrue(ninetyFive.contains("12.350"));
        assertTrue(hundredFive.contains("13.650"));
        assertTrue(hundredThirtyThree.contains("17.290"));
    }
}
