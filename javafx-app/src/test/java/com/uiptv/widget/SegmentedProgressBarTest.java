package com.uiptv.widget;

import javafx.scene.Node;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SegmentedProgressBarTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void setTotalCreatesDefaultSegmentsAndResetClearsThem() throws Exception {
        SegmentedProgressBar progressBar = runOnFxThread(SegmentedProgressBar::new);

        runOnFxThread(() -> {
            progressBar.setTotal(3);
            return null;
        });
        waitForFxEvents();

        assertEquals(3, runOnFxThread(() -> progressBar.getChildren().size()));
        assertFalse(runOnFxThread(() -> progressBar.getStyleClass().contains("empty")));
        assertTrue(runOnFxThread(() -> progressBar.getChildren().stream()
                .allMatch(node -> node.getStyleClass().contains("progress-bar-segment-default"))));

        runOnFxThread(() -> {
            progressBar.reset();
            return null;
        });
        waitForFxEvents();

        assertEquals(0, runOnFxThread(() -> progressBar.getChildren().size()));
        assertTrue(runOnFxThread(() -> progressBar.getStyleClass().contains("empty")));
    }

    @Test
    void updateSegmentAppliesStatusStyleAndIgnoresOutOfRangeIndexes() throws Exception {
        SegmentedProgressBar progressBar = runOnFxThread(SegmentedProgressBar::new);
        runOnFxThread(() -> {
            progressBar.setTotal(3);
            return null;
        });
        waitForFxEvents();

        runOnFxThread(() -> {
            progressBar.updateSegment(0, SegmentedProgressBar.SegmentStatus.SUCCESS);
            progressBar.updateSegment(1, SegmentedProgressBar.SegmentStatus.WARNING);
            progressBar.updateSegment(2, null);
            progressBar.updateSegment(4, SegmentedProgressBar.SegmentStatus.SUCCESS);
            return null;
        });
        waitForFxEvents();

        List<List<String>> segmentStyles = runOnFxThread(() -> progressBar.getChildren().stream()
                .map(Node::getStyleClass)
                .map(List::copyOf)
                .toList());
        assertTrue(segmentStyles.get(0).contains("success"));
        assertTrue(segmentStyles.get(1).contains("warning"));
        assertTrue(segmentStyles.get(2).contains("failure"));
        assertEquals(3, segmentStyles.size());
    }

    @Test
    void addResultAdvancesAcrossSegments() throws Exception {
        SegmentedProgressBar progressBar = runOnFxThread(SegmentedProgressBar::new);
        runOnFxThread(() -> {
            progressBar.setTotal(2);
            return null;
        });
        waitForFxEvents();

        runOnFxThread(() -> {
            progressBar.addResult(true);
            progressBar.addResult(false);
            return null;
        });
        waitForFxEvents();

        List<List<String>> segmentStyles = runOnFxThread(() -> progressBar.getChildren().stream()
                .map(Node::getStyleClass)
                .map(List::copyOf)
                .toList());
        assertTrue(segmentStyles.get(0).contains("success"));
        assertTrue(segmentStyles.get(1).contains("failure"));
    }
}
