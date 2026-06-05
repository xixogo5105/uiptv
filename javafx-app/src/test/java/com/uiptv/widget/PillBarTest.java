package com.uiptv.widget;

import javafx.event.Event;
import javafx.scene.control.ToggleButton;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.layout.FlowPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
