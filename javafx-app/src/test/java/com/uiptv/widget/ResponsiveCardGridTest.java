package com.uiptv.widget;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.HBox;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ResponsiveCardGridTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void arrowKeysMoveSingleSelectionFromCurrentItem() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        assertEquals(List.of("one"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));

        runOnFxThread(() -> {
            grid.selectItems(List.of("two"));
            Event.fireEvent(grid, keyPressed(KeyCode.DOWN));
            return null;
        });

        assertEquals("three", runOnFxThread(grid::getFocusedItem));
        assertEquals(List.of("three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));

        runOnFxThread(() -> {
            Event.fireEvent(grid, keyPressed(KeyCode.UP));
            return null;
        });

        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void plainClickSelectsOnlyClickedCardAndDoubleClickActivates() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<String> activated = new AtomicReference<>();
        runOnFxThread(() -> {
            grid.setOnItemActivated(activated::set);
            grid.setActivateOnSingleClick(false);
            return null;
        });

        Region thirdCard = runOnFxThread(() -> cardAt(grid, 2));
        runOnFxThread(() -> {
            Event.fireEvent(thirdCard, mouseClick(thirdCard, 1, false, false));
            return null;
        });
        assertEquals(List.of("three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertNull(activated.get());

        runOnFxThread(() -> {
            Event.fireEvent(thirdCard, mouseClick(thirdCard, 2, false, false));
            return null;
        });
        assertEquals("three", activated.get());
    }

    @Test
    void singleClickActivationRequestStillRequiresDoubleClick() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<String> activated = new AtomicReference<>();
        runOnFxThread(() -> {
            grid.setOnItemActivated(activated::set);
            grid.setActivateOnSingleClick(true);
            grid.setSingleClickActivationPredicate("two"::equals);
            return null;
        });

        Region firstCard = runOnFxThread(() -> cardAt(grid, 0));
        runOnFxThread(() -> {
            Event.fireEvent(firstCard, mouseClick(firstCard, 1, false, false));
            return null;
        });
        assertNull(activated.get());

        Region secondCard = runOnFxThread(() -> cardAt(grid, 1));
        runOnFxThread(() -> {
            Event.fireEvent(secondCard, mouseClick(secondCard, 1, false, false));
            return null;
        });
        assertNull(activated.get());

        runOnFxThread(() -> {
            Event.fireEvent(secondCard, mouseClick(secondCard, 2, false, false));
            return null;
        });
        assertEquals("two", activated.get());
    }

    @Test
    void shiftAndControlClicksManageSelectionWithoutLeavingStaleItems() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        Region firstCard = runOnFxThread(() -> cardAt(grid, 0));
        Region secondCard = runOnFxThread(() -> cardAt(grid, 1));
        Region thirdCard = runOnFxThread(() -> cardAt(grid, 2));

        runOnFxThread(() -> {
            Event.fireEvent(firstCard, mouseClick(firstCard, 1, false, false));
            Event.fireEvent(thirdCard, mouseClick(thirdCard, 1, true, false));
            return null;
        });
        assertEquals(List.of("one", "two", "three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));

        runOnFxThread(() -> {
            Event.fireEvent(secondCard, mouseClick(secondCard, 1, false, true));
            return null;
        });
        assertEquals(List.of("one", "three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));

        runOnFxThread(() -> {
            Event.fireEvent(secondCard, mouseClick(secondCard, 1, false, false));
            return null;
        });
        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void metaClickManagesSelectionForMacShortcutSelection() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<String> activated = new AtomicReference<>();
        runOnFxThread(() -> {
            grid.setOnItemActivated(activated::set);
            grid.setActivateOnSingleClick(true);
            grid.setSingleClickActivationPredicate("three"::equals);
            return null;
        });

        Region firstCard = runOnFxThread(() -> cardAt(grid, 0));
        Region thirdCard = runOnFxThread(() -> cardAt(grid, 2));
        runOnFxThread(() -> {
            Event.fireEvent(firstCard, mouseClick(firstCard, 1, false, false));
            Event.fireEvent(thirdCard, mouseClick(thirdCard, 1, false, false, true));
            return null;
        });

        assertEquals(List.of("one", "three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertNull(activated.get());
    }

    @Test
    void modifiedMousePressAndClickToggleSelectionOnlyOnce() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        Region thirdCard = runOnFxThread(() -> cardAt(grid, 2));
        runOnFxThread(() -> {
            Event.fireEvent(thirdCard, mousePressed(thirdCard, MouseButton.PRIMARY, false, false, true));
            Event.fireEvent(thirdCard, mouseClick(thirdCard, 1, false, false, true));
            return null;
        });

        assertEquals(List.of("one", "three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void shortcutASelectsAllVisibleCards() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        runOnFxThread(() -> {
            Event.fireEvent(grid, keyPressed(KeyCode.A, false, true, false));
            return null;
        });

        assertEquals(List.of("one", "two", "three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void shiftArrowExtendsSelectionFromAnchor() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        runOnFxThread(() -> {
            Event.fireEvent(grid, keyPressed(KeyCode.RIGHT, true, false, false));
            return null;
        });

        assertEquals(List.of("one", "two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertEquals("two", runOnFxThread(grid::getFocusedItem));
    }

    @Test
    void contextMenuOnSelectedItemPreservesMultiSelection() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<List<String>> selectionRef = new AtomicReference<>();

        runOnFxThread(() -> {
            grid.selectItems(List.of("one", "three"));
            grid.setContextMenuFactory((_, selectedItems, _) -> {
                selectionRef.set(selectedItems);
                return null;
            });
            return null;
        });

        Region thirdCard = runOnFxThread(() -> cardAt(grid, 2));
        runOnFxThread(() -> {
            Event.fireEvent(thirdCard, contextMenuEvent(thirdCard));
            return null;
        });

        assertEquals(List.of("one", "three"), selectionRef.get());
        assertEquals(List.of("one", "three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void clickingCardAfterPlayerFocusFocusesGridWithoutMovingScroll() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(item -> {
                Label label = new Label(item);
                label.setMinHeight(44);
                label.setPrefHeight(44);
                return label;
            });
            cardGrid.setItems(FXCollections.observableArrayList("one", "two", "three", "four", "five", "six"));
            cardGrid.setSingleColumn(true);
            cardGrid.setGaps(0, 4);

            ScrollPane scrollPane = new ScrollPane(cardGrid);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            scrollPane.setPrefWidth(280);
            Button playerFocusTarget = new Button("player");
            playerFocusTarget.setFocusTraversable(true);
            HBox root = new HBox(scrollPane, playerFocusTarget);
            new Scene(root, 420, 180);
            root.resize(420, 180);
            root.applyCss();
            root.layout();
            scrollPane.setVvalue(0);
            playerFocusTarget.requestFocus();
            return cardGrid;
        });

        Region secondCard = runOnFxThread(() -> cardAt(grid, 1));
        ScrollPane scrollPane = runOnFxThread(() -> ancestorScrollPane(grid));
        double beforeClickScroll = runOnFxThread(scrollPane::getVvalue);

        runOnFxThread(() -> {
            Event.fireEvent(secondCard, mousePressed(secondCard, MouseButton.PRIMARY));
            Event.fireEvent(secondCard, mouseClick(secondCard, 1, false, false));
            return null;
        });

        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertEquals("two", runOnFxThread(grid::getFocusedItem));
        assertFalse(runOnFxThread(secondCard::isFocusTraversable));
        assertFalse(runOnFxThread(secondCard::isFocused));
        assertEquals(beforeClickScroll, runOnFxThread(scrollPane::getVvalue), 0.0001);
    }

    @Test
    void interactiveChildClickDoesNotSelectOrActivateCard() throws Exception {
        AtomicReference<String> activated = new AtomicReference<>();
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(item -> new HBox(new Button(item)));
            cardGrid.setItems(FXCollections.observableArrayList("one", "two"));
            cardGrid.setOnItemActivated(activated::set);
            cardGrid.setActivateOnSingleClick(true);
            return cardGrid;
        });

        Region secondCard = runOnFxThread(() -> cardAt(grid, 1));
        Button childButton = runOnFxThread(() -> findDescendant(secondCard, Button.class));
        runOnFxThread(() -> {
            Event.fireEvent(childButton, mouseClick(childButton, 1, false, false));
            return null;
        });

        assertEquals(List.of("one"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertNull(activated.get());
    }

    @Test
    void contextMenuFactoryReceivesNormalizedSelectionAndOwner() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<String> itemRef = new AtomicReference<>();
        AtomicReference<List<String>> selectionRef = new AtomicReference<>();
        AtomicReference<Node> ownerRef = new AtomicReference<>();

        runOnFxThread(() -> {
            grid.setContextMenuFactory((item, selectedItems, owner) -> {
                itemRef.set(item);
                selectionRef.set(selectedItems);
                ownerRef.set(owner);
                return null;
            });
            return null;
        });

        Region secondCard = runOnFxThread(() -> cardAt(grid, 1));
        runOnFxThread(() -> {
            Event.fireEvent(secondCard, contextMenuEvent(secondCard));
            return null;
        });

        assertEquals("two", itemRef.get());
        assertEquals(List.of("two"), selectionRef.get());
        assertEquals(secondCard, ownerRef.get());
        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void itemListMutationsKeepCardsSelectionAndPlaceholderInSync() throws Exception {
        ObservableList<String> items = FXCollections.observableArrayList("one", "two");
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(Label::new);
            cardGrid.setPlaceholderNode(new Label("No items"));
            cardGrid.setItems(items);
            return cardGrid;
        });

        runOnFxThread(() -> {
            grid.selectItems(List.of("two"));
            items.add("three");
            return null;
        });
        assertEquals(3, runOnFxThread(() -> cardPane(grid).getChildren().size()));
        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));

        runOnFxThread(() -> {
            items.remove("two");
            return null;
        });
        assertEquals(List.of("three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));

        runOnFxThread(() -> {
            items.clear();
            return null;
        });
        assertEquals(0, runOnFxThread(() -> cardPane(grid).getChildren().size()));
        assertEquals(List.of(), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void widthSettingsApplySingleColumnAndBoundedMultiColumnWidths() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        runOnFxThread(() -> {
            grid.resize(500, 300);
            grid.setSingleColumn(true);
            grid.layout();
            return null;
        });
        assertTrue(runOnFxThread(() -> cardAt(grid, 0).getPrefWidth() > 450));

        runOnFxThread(() -> {
            grid.setSingleColumn(false);
            grid.setCardWidthRange(120, 160);
            grid.setGaps(5, 7);
            grid.setCardMinHeight(36);
            grid.layout();
            return null;
        });

        assertTrue(runOnFxThread(() -> cardAt(grid, 0).getPrefWidth() >= 120));
        assertTrue(runOnFxThread(() -> cardAt(grid, 0).getPrefWidth() < 170));
        assertEquals(36.0, runOnFxThread(() -> cardAt(grid, 0).getMinHeight()));
        assertEquals(5.0, runOnFxThread(() -> cardPane(grid).getHgap()));
        assertEquals(7.0, runOnFxThread(() -> cardPane(grid).getVgap()));
    }

    @Test
    void selectItemsIgnoresUnknownItemsAndRefreshRebuildsCards() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        runOnFxThread(() -> {
            grid.selectItems(List.of("missing", "two", "two"));
            grid.refresh();
            return null;
        });

        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertEquals(3, runOnFxThread(() -> cardPane(grid).getChildren().size()));
    }

    @Test
    void moveItemReordersItemsSelectsMovedItemAndReportsNewOrder() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<List<String>> reordered = new AtomicReference<>();
        runOnFxThread(() -> {
            grid.setOnItemsReordered(reordered::set);
            return null;
        });

        boolean moved = runOnFxThread(() -> invokeMoveItem(grid, 0, 2));

        assertTrue(moved);
        assertEquals(List.of("two", "three", "one"), runOnFxThread(() -> List.copyOf(grid.getItems())));
        assertEquals(List.of("one"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertEquals(List.of("two", "three", "one"), reordered.get());

        assertFalse(runOnFxThread(() -> invokeMoveItem(grid, -1, 1)));
        assertFalse(runOnFxThread(() -> invokeMoveItem(grid, 1, 1)));
    }

    @Test
    void nullItemsPlaceholdersSecondaryPressAndHomeEndNavigationAreHandled() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        runOnFxThread(() -> {
            grid.setPlaceholderText(null);
            grid.setPlaceholderText("No channels");
            grid.setPlaceholderNode(null);
            grid.setItems(null);
            return null;
        });
        assertEquals(List.of(), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertEquals(0, runOnFxThread(() -> cardPane(grid).getChildren().size()));

        runOnFxThread(() -> {
            grid.setItems(FXCollections.observableArrayList("one", "two", "three"));
            grid.setSingleClickActivationPredicate(null);
            grid.setReorderEnabled(true);
            return null;
        });

        Region secondCard = runOnFxThread(() -> cardAt(grid, 1));
        runOnFxThread(() -> {
            Event.fireEvent(secondCard, mousePressed(secondCard, MouseButton.SECONDARY));
            Event.fireEvent(grid, keyPressed(KeyCode.END));
            Event.fireEvent(grid, keyPressed(KeyCode.HOME));
            return null;
        });

        assertEquals(List.of("one"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void clearSelectionAndPlaceholderStateAreConsistent() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);

        runOnFxThread(() -> {
            grid.clearSelection();
            return null;
        });
        assertEquals(List.of(), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertNull(runOnFxThread(grid::getFocusedItem));

        runOnFxThread(() -> {
            grid.setPlaceholderText("No results");
            grid.setItems(FXCollections.observableArrayList());
            return null;
        });

        assertEquals(0, runOnFxThread(() -> cardPane(grid).getChildren().size()));
        assertEquals(List.of(), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void scrollIntoViewDoesNotMoveWhenCardIsAlreadyVisible() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(item -> {
                Label label = new Label(item);
                label.setMinHeight(44);
                label.setPrefHeight(44);
                return label;
            });
            ObservableList<String> manyItems = FXCollections.observableArrayList();
            for (int index = 1; index <= 30; index++) {
                manyItems.add("item-" + index);
            }
            cardGrid.setItems(manyItems);
            cardGrid.setSingleColumn(true);
            cardGrid.setGaps(0, 4);

            ScrollPane scrollPane = new ScrollPane(cardGrid);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            StackPane root = new StackPane(scrollPane);
            new Scene(root, 300, 180);
            root.resize(300, 180);
            root.applyCss();
            root.layout();
            scrollPane.setVvalue(0);
            root.layout();
            return cardGrid;
        });

        Region firstCard = runOnFxThread(() -> cardAt(grid, 0));
        ScrollPane scrollPane = runOnFxThread(() -> ancestorScrollPane(grid));
        assertEquals(0.0, runOnFxThread(scrollPane::getVvalue), 0.0001);

        runOnFxThread(() -> {
            invokeScrollIntoPageView(grid, firstCard);
            return null;
        });
        assertEquals(0.0, runOnFxThread(scrollPane::getVvalue), 0.0001);

        Region lastCard = runOnFxThread(() -> cardAt(grid, 29));
        runOnFxThread(() -> {
            invokeScrollIntoPageView(grid, lastCard);
            return null;
        });
        assertTrue(runOnFxThread(scrollPane::getVvalue) > 0.0);
    }

    private static ResponsiveCardGrid<String> newGrid() {
        ResponsiveCardGrid<String> grid = new ResponsiveCardGrid<>(Label::new);
        grid.setItems(FXCollections.observableArrayList("one", "two", "three"));
        return grid;
    }

    private static FlowPane cardPane(ResponsiveCardGrid<?> grid) {
        return (FlowPane) grid.getChildren().get(0);
    }

    private static Region cardAt(ResponsiveCardGrid<?> grid, int index) {
        return (Region) cardPane(grid).getChildren().get(index);
    }

    private static KeyEvent keyPressed(KeyCode keyCode) {
        return keyPressed(keyCode, false, false, false);
    }

    private static KeyEvent keyPressed(KeyCode keyCode, boolean shiftDown, boolean controlDown, boolean metaDown) {
        return new KeyEvent(
                KeyEvent.KEY_PRESSED,
                "",
                "",
                keyCode,
                shiftDown,
                controlDown,
                false,
                metaDown
        );
    }

    private static MouseEvent mouseClick(Region target, int clickCount, boolean shiftDown, boolean controlDown) {
        return mouseClick((Node) target, clickCount, shiftDown, controlDown);
    }

    private static MouseEvent mouseClick(Node target, int clickCount, boolean shiftDown, boolean controlDown) {
        return mouseClick(target, clickCount, shiftDown, controlDown, false);
    }

    private static MouseEvent mouseClick(Node target, int clickCount, boolean shiftDown, boolean controlDown, boolean metaDown) {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                clickCount,
                shiftDown,
                controlDown,
                false,
                metaDown,
                true,
                false,
                false,
                false,
                false,
                false,
                new PickResult(target, 0, 0)
        );
    }

    private static ContextMenuEvent contextMenuEvent(Node target) {
        return new ContextMenuEvent(
                ContextMenuEvent.CONTEXT_MENU_REQUESTED,
                0,
                0,
                0,
                0,
                false,
                new PickResult(target, 0, 0)
        );
    }

    private static MouseEvent mousePressed(Node target, MouseButton button) {
        return mousePressed(target, button, false, false, false);
    }

    private static MouseEvent mousePressed(Node target,
                                           MouseButton button,
                                           boolean shiftDown,
                                           boolean controlDown,
                                           boolean metaDown) {
        return new MouseEvent(
                MouseEvent.MOUSE_PRESSED,
                0,
                0,
                0,
                0,
                button,
                1,
                shiftDown,
                controlDown,
                false,
                metaDown,
                button == MouseButton.PRIMARY,
                false,
                button == MouseButton.SECONDARY,
                false,
                false,
                false,
                new PickResult(target, 0, 0)
        );
    }

    private static <T extends Node> T findDescendant(Node root, Class<T> type) {
        if (type.isInstance(root)) {
            return type.cast(root);
        }
        if (root instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findDescendant(child, type);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static ScrollPane ancestorScrollPane(Node node) {
        Node current = node.getParent();
        while (current != null) {
            if (current instanceof ScrollPane scrollPane) {
                return scrollPane;
            }
            current = current.getParent();
        }
        return null;
    }

    private static void invokeScrollIntoPageView(ResponsiveCardGrid<?> grid, Region card) throws Exception {
        Method method = ResponsiveCardGrid.class.getDeclaredMethod("scrollIntoPageView", Region.class);
        method.setAccessible(true);
        method.invoke(grid, card);
    }

    private static boolean invokeMoveItem(ResponsiveCardGrid<?> grid, int sourceIndex, int targetIndex) throws Exception {
        Method method = ResponsiveCardGrid.class.getDeclaredMethod("moveItem", int.class, int.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(grid, sourceIndex, targetIndex);
    }
}
