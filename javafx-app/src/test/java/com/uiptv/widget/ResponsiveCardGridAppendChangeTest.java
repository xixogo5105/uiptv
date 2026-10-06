package com.uiptv.widget;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code ListChangeListener.Change} is a single-pass cursor: {@code next()} has to be called
 * before any {@code wasXxx()} inspection, otherwise {@code getPermutation()}'s {@code checkState()}
 * throws {@code IllegalStateException: next() must be called before inspecting the Change}.
 * <p>
 * The grid used to run its append-during-load fast path before advancing the cursor, so every
 * batch appended while an incremental load was in progress threw on the JavaFX Application Thread
 * and the batch was dropped. These tests drive the same public entry points a streamed load uses,
 * so a regression shows up as a failure here rather than as a console trace in the app.
 */
class ResponsiveCardGridAppendChangeTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void appendingDuringIncrementalLoadKeepsEarlierCardsAndAddsTheBatch() throws Exception {
        ObservableList<String> items = seededGrid();
        assertTrue(renderedCardCount() > 0, "pre-existing cards must be rendered");

        appendDuringLoad(items, List.of("c", "d", "e"));

        assertEquals(List.of("a", "b", "c", "d", "e"), items,
                "the appended batch must be accepted; a rejected change would mean the listener threw");
        assertTrue(renderedCardCount() > 0, "the grid must still hold rendered cards");
        assertTrue(renderedLabelExists("c"), "newly appended cards must render while virtualization is inactive");
    }

    @Test
    void repeatedBatchesDuringOneIncrementalLoadAllLand() throws Exception {
        ObservableList<String> items = seededGrid();

        runOnFxThread(() -> {
            grid.beginIncrementalLoad();
            for (int batch = 0; batch < 4; batch++) {
                items.addAll(List.of("i" + batch + "a", "i" + batch + "b"));
            }
            grid.endIncrementalLoad();
            return null;
        });

        assertEquals(10, items.size(), "the 2 seeded items plus 8 appended ones must all be accepted");
        assertTrue(renderedCardCount() > 0, "the grid must still hold rendered cards");
    }

    @Test
    void appendAfterIncrementalLoadEndsStillWorks() throws Exception {
        ObservableList<String> items = seededGrid();

        runOnFxThread(() -> {
            items.addAll(List.of("c", "d"));
            return null;
        });

        assertEquals(List.of("a", "b", "c", "d"), items, "a plain append outside a load must still work");
        assertTrue(renderedCardCount() > 0, "the grid must still hold rendered cards");
    }

    @Test
    void permutationDuringIncrementalLoadDoesNotThrowOrDropCards() throws Exception {
        ObservableList<String> items = seededGrid();

        runOnFxThread(() -> {
            grid.beginIncrementalLoad();
            items.sort((left, right) -> right.compareTo(left));
            grid.endIncrementalLoad();
            return null;
        });

        assertEquals(List.of("b", "a"), items, "a sort during a load must be accepted and applied");
        assertTrue(renderedCardCount() > 0, "the grid must still hold rendered cards");
    }

    // The grid under test, and the list it listens on.
    private static ResponsiveCardGrid<String> grid;
    private static ObservableList<String> gridItems;

    /**
     * Builds a grid holding two cards and returns the observable list backing it. Mutations must
     * be made from the test thread rather than from inside a runOnFxThread block, because the
     * time-sliced card build defers work to later pulses and those pulses can only run once the
     * FX thread is free.
     */
    private static ObservableList<String> seededGrid() throws Exception {
        runOnFxThread(() -> {
            grid = new ResponsiveCardGrid<>(item -> {
                VBox card = new VBox(new Label(item));
                card.setPrefSize(180, 90);
                return card;
            });
            gridItems = FXCollections.observableArrayList();
            grid.setItems(gridItems);
            new Scene(new StackPane(grid), 600, 400);
            grid.applyCss();
            grid.layout();
            return null;
        });
        runOnFxThread(() -> {
            gridItems.setAll(List.of("a", "b"));
            return null;
        });
        pumpUntilQuiet();
        return gridItems;
    }

    private static void appendDuringLoad(ObservableList<String> items, List<String> batch) throws Exception {
        runOnFxThread(() -> {
            grid.beginIncrementalLoad();
            items.addAll(batch);
            grid.endIncrementalLoad();
            return null;
        });
        pumpUntilQuiet();
    }

    /**
     * Drains the FX queue until the deferred card build has stopped adding cards. The build is
     * deliberately spread over several pulses, so a single wait is not enough.
     */
    private static void pumpUntilQuiet() throws Exception {
        // The card build is spread over several pulses; drain enough of them to let it finish.
        for (int attempt = 0; attempt < 8; attempt++) {
            waitForFxEvents();
        }
    }

    /**
     * Counts labels in the rendered tree. Only the cards inside the viewport are rendered, so this
     * deliberately asserts "something is on screen" rather than an exact count: the grid virtualises,
     * and cardsByItem holds the window, not the whole catalogue.
     */
    private static int renderedCardCount() throws Exception {
        return runOnFxThread(() -> {
            int count = 0;
            for (javafx.scene.Node child : grid.getChildrenUnmodifiable()) {
                count += countLabels(child);
            }
            return count;
        });
    }

    private static boolean renderedLabelExists(String expected) throws Exception {
        return runOnFxThread(() -> grid.getChildrenUnmodifiable().stream()
                .anyMatch(child -> containsLabel(child, expected)));
    }

    private static boolean containsLabel(javafx.scene.Node node, String expected) {
        if (node instanceof Label label && expected.equals(label.getText())) {
            return true;
        }
        if (node instanceof javafx.scene.Parent parent) {
            return parent.getChildrenUnmodifiable().stream().anyMatch(child -> containsLabel(child, expected));
        }
        return false;
    }

    private static int countLabels(javafx.scene.Node node) {
        int count = node instanceof Label ? 1 : 0;
        if (node instanceof javafx.scene.Parent parent) {
            for (javafx.scene.Node child : parent.getChildrenUnmodifiable()) {
                count += countLabels(child);
            }
        }
        return count;
    }
}
