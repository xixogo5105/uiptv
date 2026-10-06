package com.uiptv.widget;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.css.PseudoClass;
import javafx.event.Event;
import javafx.geometry.Pos;
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
import javafx.scene.layout.GridPane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.geometry.Insets;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
    void boundaryArrowKeysAreConsumedSoParentScrollDoesNotMove() throws Exception {
        AtomicReference<Boolean> leakedToParent = new AtomicReference<>(false);
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = newGrid();
            StackPane parent = new StackPane(cardGrid);
            parent.addEventHandler(KeyEvent.KEY_PRESSED, _ -> leakedToParent.set(true));
            new Scene(parent, 300, 180);
            parent.resize(300, 180);
            parent.applyCss();
            parent.layout();
            return cardGrid;
        });

        runOnFxThread(() -> {
            grid.selectItems(List.of("three"));
            Event.fireEvent(grid, keyPressed(KeyCode.DOWN));
            return null;
        });

        assertFalse(leakedToParent.get());
        assertEquals(List.of("three"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
    }

    @Test
    void arrowNavigationToVisibleCardDoesNotScrollParent() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(item -> {
                Label label = new Label(item);
                label.setMinHeight(44);
                label.setPrefHeight(44);
                return label;
            });
            ObservableList<String> manyItems = FXCollections.observableArrayList();
            for (int index = 1; index <= 20; index++) {
                manyItems.add("item-" + index);
            }
            cardGrid.setItems(manyItems);
            cardGrid.setSingleColumn(true);
            cardGrid.setCardMinHeight(44);
            cardGrid.setGaps(0, 4);

            ScrollPane scrollPane = new ScrollPane(cardGrid);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            StackPane root = new StackPane(scrollPane);
            new Scene(root, 300, 220);
            root.resize(300, 220);
            root.applyCss();
            root.layout();
            scrollPane.setVvalue(0);
            return cardGrid;
        });

        runOnFxThread(() -> {
            grid.selectItems(List.of("item-1"));
            Event.fireEvent(grid, keyPressed(KeyCode.DOWN));
            return null;
        });
        waitForFxEvents();
        waitForFxEvents();

        assertEquals(List.of("item-2"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertEquals(0.0, runOnFxThread(() -> ancestorScrollPane(grid).getVvalue()), 0.0001);
    }

    @Test
    void repeatedArrowNavigationAcrossVisibleCardsDoesNotDriftParentScroll() throws Exception {
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
            cardGrid.setCardMinHeight(44);
            cardGrid.setGaps(0, 4);

            ScrollPane scrollPane = new ScrollPane(cardGrid);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            StackPane root = new StackPane(scrollPane);
            new Scene(root, 300, 800);
            root.resize(300, 800);
            root.applyCss();
            root.layout();
            scrollPane.setVvalue(0);
            root.layout();
            cardGrid.selectItems(List.of("item-10"));
            return cardGrid;
        });

        ScrollPane scrollPane = runOnFxThread(() -> ancestorScrollPane(grid));
        double beforeNavigationScroll = runOnFxThread(scrollPane::getVvalue);

        runOnFxThread(() -> {
            Event.fireEvent(grid, keyPressed(KeyCode.DOWN));
            Event.fireEvent(grid, keyPressed(KeyCode.DOWN));
            Event.fireEvent(grid, keyPressed(KeyCode.DOWN));
            return null;
        });
        waitForFxEvents();
        waitForFxEvents();
        waitForFxEvents();
        waitForFxEvents();

        assertEquals(List.of("item-13"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertEquals(beforeNavigationScroll, runOnFxThread(scrollPane::getVvalue), 0.0001);
    }

    @Test
    void plainClickSelectsOnlyClickedCardAndDoubleClickActivates() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<String> activated = new AtomicReference<>();
        runOnFxThread(() -> {
            grid.setOnItemActivated(activated::set);
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
    void doubleClickShowsBroadcastIndicatorAndKeepsSelectedCardStyling() throws Exception {
        CardIndicatorSnapshot snapshot = runOnFxThread(() -> {
            ResponsiveCardGrid<String> grid = new ResponsiveCardGrid<>(_ -> {
                HBox row = new HBox();
                row.setAlignment(Pos.CENTER_LEFT);
                return row;
            }, true);
            grid.setItems(FXCollections.observableArrayList("one"));
            grid.setOnItemActivated(_ -> {
            });
            grid.resize(400, 240);
            grid.layout();

            Region card = cardAt(grid, 0);
            HBox cardPane = (HBox) card;
            PlayingCardIndicator indicator = cardPane.getChildren().stream()
                    .filter(PlayingCardIndicator.class::isInstance)
                    .map(PlayingCardIndicator.class::cast)
                    .findFirst()
                    .orElseThrow();
            Event.fireEvent(card, mouseClick(card, 2, false, false));

            javafx.scene.Scene scene = new javafx.scene.Scene(grid, 400, 240);
            scene.getStylesheets().add(ResponsiveCardGridTest.class.getResource("/application.css").toExternalForm());
            scene.getRoot().applyCss();
            scene.getRoot().layout();
            return new CardIndicatorSnapshot(
                    card.getPseudoClassStates().contains(PseudoClass.getPseudoClass("activated")),
                    card.getStyleClass().contains("selected"),
                    indicator.getOpacity(),
                    indicator.isVisible(),
                    indicator.isManaged(),
                    indicator.getLayoutX() + indicator.getTranslateX()
                            + indicator.getLayoutBounds().getWidth() <= card.getWidth() - 16,
                    Math.abs(indicator.getLayoutY() + indicator.getTranslateY()
                            + indicator.getLayoutBounds().getHeight() / 2 - card.getHeight() / 2) < 1);
        });

        assertTrue(snapshot.activated());
        assertTrue(snapshot.selected());
        assertEquals(1, snapshot.indicatorOpacity(), 0.001);
        assertTrue(snapshot.indicatorVisible());
        assertFalse(snapshot.indicatorManaged());
        assertTrue(snapshot.indicatorStaysInsideCard());
        assertTrue(snapshot.indicatorVerticallyCentered());
    }

    @Test
    void broadcastIndicatorHidesAndStopsAnimatingWhenItsPageIsHidden() throws Exception {
        CardIndicatorVisibilitySnapshot snapshot = runOnFxThread(() -> {
            ResponsiveCardGrid<String> grid = new ResponsiveCardGrid<>(_ -> new HBox(), true);
            grid.setItems(FXCollections.observableArrayList("one"));
            grid.setOnItemActivated(_ -> {
            });
            grid.resize(400, 240);
            grid.layout();
            Region card = cardAt(grid, 0);
            PlayingCardIndicator indicator = ((HBox) card).getChildren().stream()
                    .filter(PlayingCardIndicator.class::isInstance)
                    .map(PlayingCardIndicator.class::cast)
                    .findFirst()
                    .orElseThrow();
            VBox page = new VBox(grid);
            javafx.scene.Scene scene = new javafx.scene.Scene(page, 400, 240);
            scene.getRoot().applyCss();
            Event.fireEvent(card, mouseClick(card, 2, false, false));
            boolean visibleOnPage = indicator.isVisible();
            boolean animatingOnPage = indicator.isAnimationRunning();

            page.setVisible(false);
            boolean hiddenWithPage = !indicator.isVisible();
            boolean stoppedWithPage = !indicator.isAnimationRunning();
            page.setVisible(true);

            return new CardIndicatorVisibilitySnapshot(
                    visibleOnPage,
                    animatingOnPage,
                    hiddenWithPage,
                    stoppedWithPage,
                    indicator.isVisible());
        });

        assertTrue(snapshot.visibleOnPage());
        assertTrue(snapshot.animatingOnPage());
        assertTrue(snapshot.hiddenWithPage());
        assertTrue(snapshot.stoppedWithPage());
        assertTrue(snapshot.visibleWhenPageReturns());
    }

    @Test
    void broadcastIndicatorAppearsBesideTextWithoutReorderingBookmarkActions() throws Exception {
        CardRowSnapshot snapshot = runOnFxThread(() -> {
            ResponsiveCardGrid<String> grid = new ResponsiveCardGrid<>(_ -> {
                Label title = new Label("Cricket Event 2");
                title.setAlignment(Pos.CENTER_LEFT);
                Region spacer = new Region();
                HBox row = new HBox(6, title, spacer, new Button("bookmark"));
                row.getStyleClass().add("playing-indicator-inline-row");
                row.setAlignment(Pos.CENTER_LEFT);
                row.setMaxWidth(Double.MAX_VALUE);
                HBox.setHgrow(spacer, javafx.scene.layout.Priority.ALWAYS);
                VBox card = new VBox(row);
                card.setMaxWidth(Double.MAX_VALUE);
                return card;
            }, true);
            grid.setItems(FXCollections.observableArrayList("one"));
            grid.setOnItemActivated(_ -> {
            });
            grid.resize(400, 100);
            grid.layout();

            Region card = cardAt(grid, 0);
            HBox row = (HBox) ((VBox) card).getChildren().getFirst();
            Label title = (Label) row.getChildren().getFirst();
            PlayingCardIndicator indicator = (PlayingCardIndicator) row.getChildren().get(1);
            Button bookmark = (Button) row.getChildren().getLast();
            new Scene(grid, 400, 100);
            grid.applyCss();
            grid.layout();
            double bookmarkXBeforeActivation = bookmark.getLayoutX();
            Event.fireEvent(card, mouseClick(card, 2, false, false));
            grid.layout();
            Insets margin = HBox.getMargin(indicator);
            return new CardRowSnapshot(
                    row.getChildren().indexOf(indicator) == row.getChildren().indexOf(title) + 1,
                    indicator.getLayoutY() + indicator.getHeight() / 2
                            <= title.getLayoutY() + title.getHeight() / 2 + 1
                            && indicator.getLayoutY() + indicator.getHeight() / 2
                            >= title.getLayoutY() + title.getHeight() / 2 - 1,
                    row.getChildren().indexOf(bookmark) == row.getChildren().size() - 1,
                    margin != null && margin.getRight() == 8,
                    indicator.isManaged(),
                    Math.abs(bookmark.getLayoutX() - bookmarkXBeforeActivation) < 1);
        });

        assertTrue(snapshot.indicatorFollowsTitle());
        assertTrue(snapshot.indicatorVerticallyCenteredWithTitle());
        assertTrue(snapshot.bookmarkActionFollowsIndicator());
        assertTrue(snapshot.indicatorHasRightMargin());
        assertTrue(snapshot.indicatorManagedWhilePlaying());
        assertTrue(snapshot.bookmarkActionPositionPreserved());
    }

    @Test
    void thumbnailBookmarkIndicatorStaysBesideTitleAndKeepsTrailingActionAtRight() throws Exception {
        CardRowSnapshot snapshot = runOnFxThread(() -> {
            Button trailingAction = new Button("bookmark");
            BookmarkCard bookmarkCard = new BookmarkCard(
                    "Bollygold", "docker samsung India", null, true, "bookmark", false, trailingAction);
            ResponsiveCardGrid<String> grid = new ResponsiveCardGrid<>(_ -> bookmarkCard, true);
            grid.setItems(FXCollections.observableArrayList("one"));
            grid.setOnItemActivated(_ -> {
            });
            grid.setSingleColumn(true);
            grid.resize(420, 180);
            Scene scene = new Scene(grid, 420, 180);
            scene.getStylesheets().add(ResponsiveCardGridTest.class.getResource("/application.css").toExternalForm());
            scene.getRoot().applyCss();
            scene.getRoot().layout();

            TextFlow title = findDescendant(bookmarkCard, TextFlow.class);
            PlayingCardIndicator indicator = findDescendant(bookmarkCard, PlayingCardIndicator.class);
            double trailingActionXBeforeActivation = trailingAction.localToScene(0, 0).getX();
            Event.fireEvent(bookmarkCard, mouseClick(bookmarkCard, 2, false, false));
            scene.getRoot().layout();
            Insets indicatorMargin = HBox.getMargin(indicator);

            return new CardRowSnapshot(
                    indicator.getParent() instanceof HBox row
                            && row.getStyleClass().contains("playing-indicator-inline-row")
                            && row.getChildren().indexOf(indicator) == row.getChildren().indexOf(title) + 1,
                    Math.abs(indicator.localToScene(0, 0).getY() + indicator.getHeight() / 2
                            - title.localToScene(0, 0).getY() - title.getHeight() / 2) < 1,
                    trailingAction.localToScene(0, 0).getX() > indicator.localToScene(0, 0).getX(),
                    indicatorMargin != null && indicatorMargin.getRight() == 8,
                    indicator.isManaged(),
                    Math.abs(trailingAction.localToScene(0, 0).getX() - trailingActionXBeforeActivation) < 1);
        });

        assertTrue(snapshot.indicatorFollowsTitle());
        assertTrue(snapshot.indicatorVerticallyCenteredWithTitle());
        assertTrue(snapshot.bookmarkActionFollowsIndicator());
        assertTrue(snapshot.indicatorHasRightMargin());
        assertTrue(snapshot.indicatorManagedWhilePlaying());
        assertTrue(snapshot.bookmarkActionPositionPreserved());
    }

    @Test
    void singleClickNeverActivatesItem() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(ResponsiveCardGridTest::newGrid);
        AtomicReference<String> activated = new AtomicReference<>();
        runOnFxThread(() -> {
            grid.setOnItemActivated(activated::set);
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
        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertNull(activated.get());
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
    void cardsAreClampedToOneColumn_beforeTheGridIsMeasured() throws Exception {
        // A card factory that reports a very wide content-driven pref width, like an account card
        // holding a long, non-wrapping title. If the grid leaves cards at their factory bounds
        // before it has been sized, the GridPane sizes the column to that width and the overflow
        // spills into the pane on the right, overlapping neighbouring content.
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(item -> {
                Label label = new Label(item);
                label.setMinWidth(0);
                label.setMaxWidth(Double.MAX_VALUE);
                VBox card = new VBox(label) {
                    @Override
                    protected double computePrefWidth(double height) {
                        return 1400;
                    }
                };
                return card;
            });
            cardGrid.setItems(FXCollections.observableArrayList("a", "b"));
            cardGrid.setCardWidthRange(200, 300);
            cardGrid.setGaps(10, 10);
            cardGrid.setMinWidth(0);
            cardGrid.setMaxWidth(Double.MAX_VALUE);
            return cardGrid;
        });

        // No resize/layout: this is the pre-measurement window.
        runOnFxThread(() -> {
            grid.layout();
            return null;
        });

        runOnFxThread(() -> {
            Region card = cardAt(grid, 0);
            assertEquals(200.0, card.getMinWidth(),
                    "Card must be clamped to the configured minimum, not sized by its content");
            assertEquals(200.0, card.getPrefWidth());
            assertEquals(200.0, card.getMaxWidth(),
                    "Card must be exactly one column wide so it cannot overlap a neighbour");
            return null;
        });
    }

    @Test
    void measuredGridKeepsEveryCardWithinOneColumnWidth() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(item -> {
                Label label = new Label(item);
                label.setMinWidth(0);
                label.setMaxWidth(Double.MAX_VALUE);
                return new VBox(label) {
                    @Override
                    protected double computePrefWidth(double height) {
                        return 1400;
                    }
                };
            });
            cardGrid.setItems(FXCollections.observableArrayList("a", "b", "c"));
            cardGrid.setCardWidthRange(200, 300);
            cardGrid.setGaps(10, 10);
            cardGrid.setMinWidth(0);
            cardGrid.setMaxWidth(Double.MAX_VALUE);
            return cardGrid;
        });

        runOnFxThread(() -> {
            grid.resize(660, 300);
            grid.layout();
            return null;
        });

        double available = 660.0;
        runOnFxThread(() -> {
            for (int i = 0; i < 3; i++) {
                Region card = cardAt(grid, i);
                assertTrue(card.getMaxWidth() <= available,
                        "Card " + i + " must not exceed the grid width: " + card.getMaxWidth());
                assertTrue(card.getMinWidth() >= 200.0,
                        "Card " + i + " must respect the configured minimum width");
                assertEquals(card.getMinWidth(), card.getMaxWidth(),
                        "Card " + i + " must be a fixed one-column width");
            }
            return null;
        });
    }

    @Test
    void switchingFromSingleColumnRepositionsCardsInMultiColumnLayout() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(Label::new);
            cardGrid.setItems(FXCollections.observableArrayList(
                    "one", "two", "three", "four", "five", "six"));
            cardGrid.setCardWidthRange(120, 160);
            cardGrid.setGaps(5, 7);
            cardGrid.setMinHeight(0);
            cardGrid.setMaxHeight(Double.MAX_VALUE);
            return cardGrid;
        });

        runOnFxThread(() -> {
            grid.resize(500, 300);
            grid.setSingleColumn(true);
            grid.layout();
            return null;
        });

        runOnFxThread(() -> {
            grid.setSingleColumn(false);
            grid.layout();
            return null;
        });

        runOnFxThread(() -> {
            GridPane pane = cardPane(grid);
            for (int i = 0; i < 6; i++) {
                Region card = cardAt(grid, i);
                Integer col = GridPane.getColumnIndex(card);
                Integer row = GridPane.getRowIndex(card);
                assertNotNull(col);
                assertNotNull(row);
                assertTrue(col >= 0 && col < 3,
                        "Card " + i + " should be in a multi-column layout but was at column " + col);
            }
            return null;
        });
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
    void scrollFocusedItemIntoViewKeepsSelectionVisibleAfterRefresh() throws Exception {
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

            cardGrid.selectItems(List.of("item-25"));
            cardGrid.refresh();
            scrollPane.setVvalue(0);
            root.layout();
            cardGrid.scrollFocusedItemIntoView();
            return cardGrid;
        });

        waitForFxEvents();
        waitForFxEvents();
        waitForFxEvents();
        waitForFxEvents();

        assertTrue(runOnFxThread(() -> ancestorScrollPane(grid).getVvalue()) > 0.0);
        assertEquals(List.of("item-25"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
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

    @Test
    void largeItemSetRendersOnlyVisibleCardWindowAndNavigatesToFarItems() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> cardGrid = new ResponsiveCardGrid<>(item -> {
                Label label = new Label(item);
                label.setMinHeight(40);
                label.setPrefHeight(40);
                return label;
            });
            ObservableList<String> manyItems = FXCollections.observableArrayList();
            for (int index = 0; index < 10_000; index++) {
                manyItems.add("item-" + index);
            }
            cardGrid.setVirtualizationThreshold(50);
            cardGrid.setItems(manyItems);
            cardGrid.setSingleColumn(true);
            cardGrid.setGaps(0, 4);

            ScrollPane scrollPane = new ScrollPane(cardGrid);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            StackPane root = new StackPane(scrollPane);
            new Scene(root, 320, 240);
            root.resize(320, 240);
            root.applyCss();
            root.layout();
            return cardGrid;
        });

        assertTrue(runOnFxThread(() -> cardPane(grid).getChildren().size()) < 250);
        assertTrue(runOnFxThread(() -> renderedLabelTexts(grid).contains("item-0")));

        runOnFxThread(() -> {
            Event.fireEvent(grid, keyPressed(KeyCode.END));
            return null;
        });

        assertEquals("item-9999", runOnFxThread(grid::getFocusedItem));
        assertEquals(List.of("item-9999"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())));
        assertTrue(runOnFxThread(() -> cardPane(grid).getChildren().size()) < 250);
        assertTrue(runOnFxThread(() -> renderedLabelTexts(grid).contains("item-9999")));
    }

    @Test
    void virtualizedWindowTracksScrollValueImmediately() throws Exception {
        ResponsiveCardGrid<String> cardGrid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> grid = new ResponsiveCardGrid<>(item -> {
                Label label = new Label(item);
                label.setMinHeight(40);
                label.setPrefHeight(40);
                return label;
            });
            ObservableList<String> manyItems = FXCollections.observableArrayList();
            for (int index = 0; index < 10_000; index++) {
                manyItems.add("item-" + index);
            }
            grid.setVirtualizationThreshold(1);
            grid.setVirtualRowBuffer(1);
            grid.setItems(manyItems);
            grid.setSingleColumn(true);
            grid.setGaps(0, 4);

            ScrollPane scrollPane = new ScrollPane(grid);
            scrollPane.setFitToWidth(true);
            scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            StackPane root = new StackPane(scrollPane);
            new Scene(root, 320, 240);
            root.resize(320, 240);
            root.applyCss();
            root.layout();

            invokeInstallVirtualScrollPane(grid);
            scrollPane.setVvalue(1.0);
            return grid;
        });

        waitForFxEvents();

        List<String> rendered = runOnFxThread(() -> renderedLabelTexts(cardGrid));

        assertTrue(rendered.contains("item-9999"));
    }

    @Test
    void virtualizedWindowReusesOverlappingCardsDuringScroll() throws Exception {
        AtomicInteger createdCards = new AtomicInteger();
        ResponsiveCardGrid<Integer> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<Integer> cardGrid = new ResponsiveCardGrid<>(item -> {
                createdCards.incrementAndGet();
                Label label = new Label(String.valueOf(item));
                label.setMinHeight(40);
                label.setPrefHeight(40);
                return label;
            });
            ObservableList<Integer> manyItems = FXCollections.observableArrayList();
            for (int index = 0; index < 100; index++) {
                manyItems.add(index);
            }
            cardGrid.setVirtualizationThreshold(1);
            cardGrid.setItems(manyItems);
            return cardGrid;
        });

        VirtualWindowSnapshot snapshot = runOnFxThread(() -> {
            invokeRenderVirtualWindow(grid, 0, 10, 0);
            createdCards.set(0);
            invokeRenderVirtualWindow(grid, 2, 12, 88);
            return new VirtualWindowSnapshot(createdCards.get(), renderedLabelTexts(grid));
        });

        assertEquals(2, snapshot.createdCards());
        assertEquals(List.of("2", "3", "4", "5", "6", "7", "8", "9", "10", "11"),
                snapshot.renderedLabels());
    }

    private static ResponsiveCardGrid<String> newGrid() {
        ResponsiveCardGrid<String> grid = new ResponsiveCardGrid<>(Label::new);
        grid.setItems(FXCollections.observableArrayList("one", "two", "three"));
        return grid;
    }

    private static GridPane cardPane(ResponsiveCardGrid<?> grid) {
        return (GridPane) grid.getChildren().get(0);
    }

    private static Region cardAt(ResponsiveCardGrid<?> grid, int index) {
        return (Region) cardPane(grid).getChildren().get(index);
    }

    private static List<String> renderedLabelTexts(ResponsiveCardGrid<?> grid) {
        return cardPane(grid).getChildren().stream()
                .filter(Label.class::isInstance)
                .map(Label.class::cast)
                .map(Label::getText)
                .toList();
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

    private static void invokeRenderVirtualWindow(ResponsiveCardGrid<?> grid,
                                                  int firstIndex,
                                                  int lastIndex,
                                                  double translateY) throws Exception {
        Method method = ResponsiveCardGrid.class.getDeclaredMethod("renderVirtualWindow", int.class, int.class, double.class);
        method.setAccessible(true);
        method.invoke(grid, firstIndex, lastIndex, translateY);
    }

    private static void invokeInstallVirtualScrollPane(ResponsiveCardGrid<?> grid) throws Exception {
        Method method = ResponsiveCardGrid.class.getDeclaredMethod("installVirtualScrollPane");
        method.setAccessible(true);
        method.invoke(grid);
    }

    @Test
    void incrementalLoadAppendsKeepEveryStreamedItemAndSelectionIntact() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ObservableList<String> items = FXCollections.observableArrayList("one", "two", "three");
            ResponsiveCardGrid<String> created = new ResponsiveCardGrid<>(Label::new);
            created.setItems(items);
            created.resize(600, 400);
            created.layout();
            return created;
        });

        runOnFxThread(() -> {
            grid.selectItems(List.of("two"));
            grid.beginIncrementalLoad();
            grid.getItems().addAll(List.of("four", "five"));
            grid.endIncrementalLoad();
            return null;
        });

        assertEquals(List.of("one", "two", "three", "four", "five"),
                runOnFxThread(() -> List.copyOf(grid.getItems())),
                "All streamed items must be present, in order");
        assertEquals(List.of("two"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())),
                "Streaming appends must not disturb the current selection");
    }

    @Test
    void selectionRestyleStillAppliesAfterIncrementalLoadFinishes() throws Exception {
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ObservableList<String> items = FXCollections.observableArrayList("one", "two", "three");
            ResponsiveCardGrid<String> created = new ResponsiveCardGrid<>(Label::new);
            created.setItems(items);
            created.resize(600, 400);
            created.layout();
            return created;
        });

        runOnFxThread(() -> {
            grid.beginIncrementalLoad();
            grid.getItems().addAll(List.of("four", "five"));
            grid.endIncrementalLoad();
            grid.selectItems(List.of("five"));
            return null;
        });

        assertEquals(List.of("five"), runOnFxThread(() -> List.copyOf(grid.getSelectedItems())),
                "A selection made after loading must still be recorded so it can be styled");
    }

    private record VirtualWindowSnapshot(int createdCards, List<String> renderedLabels) {
    }

    private record CardIndicatorSnapshot(boolean activated,
                                         boolean selected,
                                         double indicatorOpacity,
                                         boolean indicatorVisible,
                                         boolean indicatorManaged,
                                         boolean indicatorStaysInsideCard,
                                         boolean indicatorVerticallyCentered) {
    }

    private record CardIndicatorVisibilitySnapshot(boolean visibleOnPage,
                                                   boolean animatingOnPage,
                                                   boolean hiddenWithPage,
                                                   boolean stoppedWithPage,
                                                   boolean visibleWhenPageReturns) {
    }

    private record CardRowSnapshot(boolean indicatorFollowsTitle,
                                   boolean indicatorVerticallyCenteredWithTitle,
                                   boolean bookmarkActionFollowsIndicator,
                                   boolean indicatorHasRightMargin,
                                   boolean indicatorManagedWhilePlaying,
                                   boolean bookmarkActionPositionPreserved) {
    }
}
