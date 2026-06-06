package com.uiptv.widget;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Objects;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class PillBarTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void setItemsSelectsFirstItemAndPreservesSelectionByKey() throws Exception {
        PillBar<Item> pillBar = runOnFxThread(() -> new PillBar<>(Item::label, Item::key));

        runOnFxThread(() -> {
            pillBar.setItems(List.of(
                    new Item("one", "One"),
                    new Item("two", "Two"),
                    new Item("three", "Three")
            ));
            return null;
        });
        assertEquals("one", runOnFxThread(() -> pillBar.getSelectedItem().key()));

        runOnFxThread(() -> {
            pillBar.setSelectedItem(new Item("two", "Updated label does not matter"));
            pillBar.setItems(List.of(
                    new Item("zero", "Zero"),
                    new Item("two", "Two refreshed"),
                    new Item("four", "Four")
            ));
            return null;
        });
        assertEquals("two", runOnFxThread(() -> pillBar.getSelectedItem().key()));

        runOnFxThread(() -> {
            pillBar.setItems(List.of(new Item("alpha", "Alpha"), new Item("beta", "Beta")));
            return null;
        });
        assertEquals("alpha", runOnFxThread(() -> pillBar.getSelectedItem().key()));
    }

    @Test
    void arrowKeysMoveSingleSelectionBetweenPills() throws Exception {
        PillBar<String> pillBar = runOnFxThread(() -> new PillBar<>(value -> value, value -> value));
        runOnFxThread(() -> {
            pillBar.setItems(List.of("first", "second", "third"));
            return null;
        });

        ToggleButton firstPill = runOnFxThread(() -> pillAt(pillBar, 0));
        assertNotNull(firstPill);

        runOnFxThread(() -> {
            Event.fireEvent(firstPill, keyPressed(KeyCode.RIGHT));
            return null;
        });
        assertEquals("second", runOnFxThread(pillBar::getSelectedItem));

        ToggleButton secondPill = runOnFxThread(() -> pillAt(pillBar, 1));
        runOnFxThread(() -> {
            Event.fireEvent(secondPill, keyPressed(KeyCode.LEFT));
            return null;
        });
        assertEquals("first", runOnFxThread(pillBar::getSelectedItem));
    }

    @Test
    void usesFixedSingleAndDoubleRowHeights() throws Exception {
        FixedHeightSnapshot snapshot = runOnFxThread(() -> {
            PillBar<String> pillBar = new PillBar<>(value -> value, value -> value);
            pillBar.setItems(List.of("One", "Two", "Three", "Four"));
            new Scene(pillBar, 720, 90);
            pillBar.applyCss();

            pillBar.resize(720, 140);
            pillBar.layout();
            pillBar.layout();
            double computedSingleRowHeight = pillBar.getPrefHeight();

            pillBar.resize(180, 140);
            pillBar.layout();
            pillBar.layout();
            double computedDoubleRowHeight = pillBar.getPrefHeight();

            return new FixedHeightSnapshot(
                    computedSingleRowHeight,
                    computedDoubleRowHeight,
                    pillBar.getPrefHeight()
            );
        });

        assertEquals(40, snapshot.computedSingleRowHeight(), 0.01);
        assertEquals(88, snapshot.computedDoubleRowHeight(), 0.01);
        assertEquals(88, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void reservedRowCountOverridesAutomaticWrapping() throws Exception {
        FixedHeightSnapshot snapshot = runOnFxThread(() -> {
            PillBar<String> pillBar = new PillBar<>(value -> value, value -> value);
            pillBar.setItems(List.of("One", "Two"));
            new Scene(pillBar, 360, 90);
            pillBar.applyCss();

            pillBar.setReservedRowCount(2);
            double reservedDoubleRowHeight = pillBar.getPrefHeight();

            pillBar.setReservedRowCount(1);
            double reservedSingleRowHeight = pillBar.getPrefHeight();

            return new FixedHeightSnapshot(
                    reservedSingleRowHeight,
                    reservedDoubleRowHeight,
                    pillBar.getPrefHeight()
            );
        });

        assertEquals(40, snapshot.computedSingleRowHeight(), 0.01);
        assertEquals(88, snapshot.computedDoubleRowHeight(), 0.01);
        assertEquals(40, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void narrowItemsPerRowPreallocatesRowsFromItemCount() throws Exception {
        NarrowHeightSnapshot snapshot = runOnFxThread(() -> {
            PillBar<String> pillBar = new PillBar<>(value -> value, value -> value);
            pillBar.setNarrowItemsPerRow(5);
            pillBar.setItems(List.of(
                    "One",
                    "Two",
                    "Three",
                    "Four",
                    "Five",
                    "Six",
                    "Seven",
                    "Eight",
                    "Nine",
                    "Ten",
                    "Eleven"
            ));
            double unresolvedWidthHeight = pillBar.prefHeight(-1);
            new Scene(pillBar, 720, 140);
            pillBar.applyCss();
            pillBar.resize(720, 160);
            pillBar.layout();
            pillBar.layout();
            double wideHeight = pillBar.getPrefHeight();
            pillBar.resize(480, 160);
            pillBar.layout();
            pillBar.layout();
            double narrowHeight = pillBar.getPrefHeight();

            return new NarrowHeightSnapshot(
                    wideHeight,
                    narrowHeight,
                    unresolvedWidthHeight,
                    pillBar.getPrefHeight()
            );
        });

        assertEquals(40, snapshot.wideHeight(), 0.01);
        assertEquals(136, snapshot.narrowHeight(), 0.01);
        assertEquals(136, snapshot.widthlessHeight(), 0.01);
        assertEquals(136, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void narrowReservedRowsApplyOnlyInNarrowMode() throws Exception {
        NarrowHeightSnapshot snapshot = runOnFxThread(() -> {
            PillBar<String> pillBar = new PillBar<>(value -> value, value -> value);
            pillBar.setNarrowReservedRowCount(3);
            pillBar.setItems(List.of("All", "Video Players", "Parental Lock", "Theme"));
            double unresolvedWidthHeight = pillBar.prefHeight(-1);
            new Scene(pillBar, 720, 140);
            pillBar.applyCss();

            pillBar.resize(720, 160);
            pillBar.layout();
            pillBar.layout();
            double wideHeight = pillBar.getPrefHeight();

            pillBar.resize(480, 160);
            pillBar.layout();
            pillBar.layout();
            double narrowHeight = pillBar.getPrefHeight();

            return new NarrowHeightSnapshot(
                    wideHeight,
                    narrowHeight,
                    unresolvedWidthHeight,
                    pillBar.getPrefHeight()
            );
        });

        assertEquals(40, snapshot.wideHeight(), 0.01);
        assertEquals(136, snapshot.narrowHeight(), 0.01);
        assertEquals(136, snapshot.widthlessHeight(), 0.01);
        assertEquals(136, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void selectionCssDoesNotShrinkNarrowReservedBackground() throws Exception {
        FixedHeightSnapshot snapshot = runOnFxThread(() -> {
            PillBar<String> pillBar = new PillBar<>(value -> value, value -> value);
            pillBar.setNarrowItemsPerRow(5);
            pillBar.setItems(List.of("All", "Cricket", "Movies", "Drama", "4K", "News", "A Samad"));
            VBox root = new VBox(pillBar);
            Scene scene = new Scene(root, 520, 180);
            scene.getStylesheets().add(Objects.requireNonNull(
                    PillBarTest.class.getResource("/application.css")
            ).toExternalForm());
            root.applyCss();
            root.layout();
            double initialHeight = pillBar.getHeight();

            pillAt(pillBar, 5).fire();
            root.applyCss();
            root.layout();
            root.layout();

            return new FixedHeightSnapshot(
                    initialHeight,
                    pillBar.getHeight(),
                    pillBar.getPrefHeight()
            );
        });

        assertEquals(88, snapshot.computedSingleRowHeight(), 0.01);
        assertEquals(88, snapshot.computedDoubleRowHeight(), 0.01);
        assertEquals(88, snapshot.prefHeightProperty(), 0.01);
    }

    private static ToggleButton pillAt(PillBar<?> pillBar, int index) {
        FlowPane content = (FlowPane) pillBar.getChildren().get(0);
        return (ToggleButton) content.getChildren().get(index);
    }

    private static KeyEvent keyPressed(KeyCode keyCode) {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                keyCode,
                false,
                false,
                false,
                false
        );
    }

    private record Item(String key, String label) {
    }

    private record FixedHeightSnapshot(double computedSingleRowHeight,
                                       double computedDoubleRowHeight,
                                       double prefHeightProperty) {
    }

    private record NarrowHeightSnapshot(double wideHeight,
                                        double narrowHeight,
                                        double widthlessHeight,
                                        double prefHeightProperty) {
    }
}
