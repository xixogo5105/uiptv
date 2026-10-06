package com.uiptv.widget;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayingCardIndicatorTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void broadcastsOnlyWhileTheIndicatorIsVisible() throws Exception {
        runOnFxThread(() -> {
            PlayingCardIndicator indicator = new PlayingCardIndicator();

            assertFalse(indicator.isAnimationRunning());
            indicator.setVisible(true);
            assertTrue(indicator.isAnimationRunning());
            indicator.setVisible(false);
            assertFalse(indicator.isAnimationRunning());
            return null;
        });
    }
}
