package com.uiptv.ui.main;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MainApplicationUILayoutTest {
    @Test
    void wideModeLeavesSlightlyMoreSpaceForPlayerOnLargeScreens() throws Exception {
        assertEquals(518.4, preferredWideAppAreaWidth(1920), 0.001);
    }

    @Test
    void wideModeAppAreaStillHasStableBounds() throws Exception {
        assertEquals(540.0, preferredWideAppAreaWidth(2560), 0.001);
        assertEquals(360.0, preferredWideAppAreaWidth(1200), 0.001);
    }

    private static double preferredWideAppAreaWidth(int guidedWidth) throws Exception {
        MainApplicationUI ui = new MainApplicationUI(null, null, null, null, guidedWidth, 720, true);
        Method method = MainApplicationUI.class.getDeclaredMethod("preferredWideAppAreaWidth");
        method.setAccessible(true);
        return (double) method.invoke(ui);
    }
}
