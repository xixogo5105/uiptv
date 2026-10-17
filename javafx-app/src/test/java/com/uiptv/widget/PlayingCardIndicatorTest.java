package com.uiptv.widget;

import javafx.scene.Scene;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PlayingCardIndicatorTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void recordingDotNeverStartsAnAnimation() throws Exception {
        runOnFxThread(() -> {
            PlayingCardIndicator indicator = new PlayingCardIndicator();

            assertFalse(indicator.isAnimationRunning());
            indicator.setVisible(true);
            assertFalse(indicator.isAnimationRunning());
            assertNotNull(indicator.lookup(".playing-card-recording-dot"));
            indicator.setVisible(false);
            assertFalse(indicator.isAnimationRunning());
            return null;
        });
    }

    @Test
    void lightThemeIndicatorUsesVisibleNeutralCircle() throws Exception {
        runOnFxThread(() -> {
            PlayingCardIndicator indicator = new PlayingCardIndicator();
            StackPane root = new StackPane(indicator);
            Scene scene = new Scene(root, 40, 40);
            scene.getStylesheets().add(Objects.requireNonNull(
                    PlayingCardIndicatorTest.class.getResource("/application.css")
            ).toExternalForm());
            root.applyCss();

            assertEquals(Color.web("#e5e5e5"), indicator.getBackground().getFills().getFirst().getFill());
            return null;
        });
    }
}
