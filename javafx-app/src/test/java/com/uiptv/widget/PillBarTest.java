package com.uiptv.widget;

import javafx.event.Event;
import javafx.scene.Scene;
import javafx.scene.control.MenuButton;
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
import static org.junit.jupiter.api.Assertions.assertTrue;

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
    void switchesWrappedRowsToCompactDropdownHeight() throws Exception {
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
        assertEquals(42, snapshot.computedDoubleRowHeight(), 0.01);
        assertEquals(42, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void reservedRowCountUsesCompactDropdownInsteadOfReservedRows() throws Exception {
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
        assertEquals(42, snapshot.computedDoubleRowHeight(), 0.01);
        assertEquals(40, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void narrowItemsPerRowUsesCompactDropdownFromItemCount() throws Exception {
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
        assertEquals(42, snapshot.narrowHeight(), 0.01);
        assertEquals(42, snapshot.widthlessHeight(), 0.01);
        assertEquals(42, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void narrowReservedRowsUseCompactDropdownOnlyInNarrowMode() throws Exception {
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
        assertEquals(42, snapshot.narrowHeight(), 0.01);
        assertEquals(42, snapshot.widthlessHeight(), 0.01);
        assertEquals(42, snapshot.prefHeightProperty(), 0.01);
    }

    @Test
    void selectionUpdatesCompactDropdownWithoutChangingHeight() throws Exception {
        CompactSelectionSnapshot snapshot = runOnFxThread(() -> {
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
            MenuButton dropdown = compactDropdown(pillBar);

            return new CompactSelectionSnapshot(
                    initialHeight,
                    pillBar.getHeight(),
                    pillBar.getPrefHeight(),
                    dropdown.isManaged(),
                    dropdown.getText()
            );
        });

        assertEquals(42, snapshot.initialHeight(), 0.01);
        assertEquals(42, snapshot.afterSelectionHeight(), 0.01);
        assertEquals(42, snapshot.prefHeightProperty(), 0.01);
        assertEquals(true, snapshot.dropdownManaged());
        assertEquals("News", snapshot.dropdownText());
    }

    @Test
    void compactDropdownCanUseShorterSelectedLabelWithoutShorteningMenuItems() throws Exception {
        CompactLabelSnapshot snapshot = runOnFxThread(() -> {
            PillBar<CompactItem> pillBar = new PillBar<>(
                    CompactItem::label,
                    CompactItem::key,
                    null,
                    CompactItem::compactLabel
            );
            pillBar.setItems(List.of(
                    new CompactItem("stalker", "Stalker Portal", "Stalker"),
                    new CompactItem("xtreme", "Xtreme API", "Xtreme")
            ));
            new Scene(pillBar, 120, 90);
            pillBar.applyCss();
            pillBar.resize(120, 90);
            pillBar.layout();
            MenuButton dropdown = compactDropdown(pillBar);
            return new CompactLabelSnapshot(dropdown.getText(), dropdown.getItems().getFirst().getText());
        });

        assertEquals("Stalker", snapshot.dropdownText());
        assertEquals("Stalker Portal", snapshot.firstMenuItemText());
    }

    @Test
    void compactDropdownFillsAllocatedRowWidth() throws Exception {
        CompactWidthSnapshot snapshot = runOnFxThread(() -> {
            PillBar<String> pillBar = new PillBar<>(value -> value, value -> value);
            pillBar.setItems(List.of("All", "Video Players", "Parental Lock", "Appearance", "Server"));
            VBox root = new VBox(pillBar);
            root.setFillWidth(true);
            Scene scene = new Scene(root, 360, 90);
            scene.getStylesheets().add(Objects.requireNonNull(
                    PillBarTest.class.getResource("/application.css")
            ).toExternalForm());
            root.applyCss();
            root.layout();
            root.layout();
            MenuButton dropdown = compactDropdown(pillBar);
            return new CompactWidthSnapshot(
                    pillBar.getWidth(),
                    dropdown.getWidth(),
                    dropdown.isManaged()
            );
        });

        assertEquals(true, snapshot.dropdownManaged());
        assertEquals(360, snapshot.pillBarWidth(), 0.01);
        assertTrue(snapshot.dropdownWidth() > PillBar.COMPACT_DROPDOWN_PREF_WIDTH);
        assertTrue(snapshot.dropdownWidth() <= snapshot.pillBarWidth());
    }

    private static ToggleButton pillAt(PillBar<?> pillBar, int index) {
        FlowPane content = (FlowPane) pillBar.getChildren().get(0);
        return (ToggleButton) content.getChildren().get(index);
    }

    private static MenuButton compactDropdown(PillBar<?> pillBar) {
        return (MenuButton) pillBar.getChildren().get(1);
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

    private record CompactItem(String key, String label, String compactLabel) {
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

    private record CompactSelectionSnapshot(double initialHeight,
                                            double afterSelectionHeight,
                                            double prefHeightProperty,
                                            boolean dropdownManaged,
                                            String dropdownText) {
    }

    private record CompactLabelSnapshot(String dropdownText,
                                        String firstMenuItemText) {
    }

    private record CompactWidthSnapshot(double pillBarWidth,
                                        double dropdownWidth,
                                        boolean dropdownManaged) {
    }
}
