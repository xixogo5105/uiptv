package com.uiptv.widget;

import javafx.collections.FXCollections;
import javafx.event.Event;
import javafx.event.EventType;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The "..." affordance is hollow until a card is hovered, so a catalogue does not build a Tooltip
 * and four shapes for every card up front. These tests pin both halves of that contract: the
 * button stays hollow while untouched, and the grid reveals exactly the card under the pointer.
 */
class PlayMenuButtonHoverRevealTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void buttonIsHollowUntilRevealed() throws Exception {
        runOnFx(() -> {
            PlayMenuButton button = new PlayMenuButton("Play");

            assertFalse(button.isMaterialised(), "graphic must not be built up front");
            assertNull(button.getGraphic(), "no graphic node should exist yet");
            assertNull(button.getTooltip(), "no Tooltip should exist yet - that is the expensive part");
            assertFalse(button.isRevealed());
            assertEquals(0.0, button.getOpacity(), 0.0001, "hidden until revealed");
        });
    }

    @Test
    void revealBuildsGraphicAndTooltipAndRevealIsIdempotent() throws Exception {
        runOnFx(() -> {
            PlayMenuButton button = new PlayMenuButton("Play");

            button.reveal();
            assertTrue(button.isMaterialised());
            assertNotNull(button.getGraphic(), "graphic should be built on first reveal");
            assertNotNull(button.getTooltip(), "tooltip should be built on first reveal");
            assertTrue(button.isRevealed());
            assertEquals(1.0, button.getOpacity(), 0.0001);

            Node graphic = button.getGraphic();
            button.reveal();
            assertEquals(graphic, button.getGraphic(), "a second reveal must not rebuild the graphic");
        });
    }

    @Test
    void concealHidesButKeepsTheBuiltContent() throws Exception {
        runOnFx(() -> {
            PlayMenuButton button = new PlayMenuButton("Play");
            button.reveal();
            Node graphic = button.getGraphic();

            button.conceal();
            assertFalse(button.isRevealed());
            assertEquals(0.0, button.getOpacity(), 0.0001);
            assertEquals(graphic, button.getGraphic(), "conceal must not throw content away");

            button.conceal();
            assertFalse(button.isRevealed(), "concealing an already hidden button is a no-op");
        });
    }

    @Test
    void gridRevealsOnlyTheHoveredCard() throws Exception {
        AtomicInteger cardsCreated = new AtomicInteger();
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> created = newGrid(cardsCreated);
            created.setItems(FXCollections.observableArrayList(List.of("a", "b", "c")));
            new Scene(new StackPane(created), 600, 400);
            // Keep the test deterministic: virtualization only engages for large catalogues.
            setBooleanField(created, "virtualizationEnabled", false);
            created.applyCss();
            created.layout();
            return created;
        });

        runOnFx(() -> {
            assertEquals(3, collectPlayMenuButtons(grid).size(), "every card still owns a menu button");
            for (PlayMenuButton button : collectPlayMenuButtons(grid)) {
                assertFalse(button.isMaterialised(), "no button may be built before any hover");
            }

            Region cardB = cardFor(grid, "b");
            dispatchMouseEvent(cardB, MouseEvent.MOUSE_ENTERED);
            waitForFxEvents();

            assertTrue(buttonFor(grid, "b").isMaterialised(), "the hovered card reveals its button");
            assertFalse(buttonFor(grid, "a").isMaterialised(), "other cards stay hollow");
            assertFalse(buttonFor(grid, "c").isMaterialised(), "other cards stay hollow");
            assertEquals(3, cardsCreated.get(), "cards are still built once each; only the button content is deferred");

            dispatchMouseEvent(cardB, MouseEvent.MOUSE_EXITED);
            waitForFxEvents();
            assertFalse(buttonFor(grid, "b").isRevealed(), "leaving the card hides the button again");
        });
    }

    @Test
    void parkingACardInTheDetachedCacheHidesItsAffordance() throws Exception {
        // A card scrolled out from under the pointer never receives MOUSE_EXITED, so a card parked
        // by the detached-card cache must be concealed explicitly, otherwise it scrolls back with
        // the "..." already showing.
        ResponsiveCardGrid<String> grid = runOnFxThread(() -> {
            ResponsiveCardGrid<String> created = newGrid(new AtomicInteger());
            created.setDetachedCardCachingEnabled(true);
            created.setItems(FXCollections.observableArrayList(List.of("a", "b", "c")));
            new Scene(new StackPane(created), 600, 400);
            setBooleanField(created, "virtualizationEnabled", false);
            created.applyCss();
            created.layout();
            return created;
        });

        runOnFx(() -> {
            Region cardA = cardFor(grid, "a");
            dispatchMouseEvent(cardA, MouseEvent.MOUSE_ENTERED);
            waitForFxEvents();
            assertTrue(buttonFor(grid, "a").isRevealed());

            concealHoverActionsIn(grid, cardA);

            assertFalse(buttonFor(grid, "a").isRevealed(),
                    "a card parked in the detached cache must not keep its affordance revealed");
        });
    }

    /** runOnFxThread requires a value; this is the void-block form. */
    private static void runOnFx(FxBody body) throws Exception {
        runOnFxThread(() -> {
            body.run();
            return null;
        });
    }

    @FunctionalInterface
    private interface FxBody {
        void run() throws Exception;
    }

    private static ResponsiveCardGrid<String> newGrid(AtomicInteger cardsCreated) {
        return new ResponsiveCardGrid<>(item -> {
            cardsCreated.incrementAndGet();
            VBox card = new VBox(new javafx.scene.control.Label(item), new PlayMenuButton("Play " + item));
            card.setPrefSize(180, 90);
            return card;
        });
    }

    private static void dispatchMouseEvent(Node target, EventType<MouseEvent> type) {
        Event.fireEvent(target, new MouseEvent(
                type,
                0,
                0,
                0,
                0,
                MouseButton.NONE,
                0,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                false,
                new PickResult(target, 0, 0)
        ));
    }

    private static PlayMenuButton buttonFor(ResponsiveCardGrid<String> grid, String item) {
        for (PlayMenuButton button : collectPlayMenuButtons(grid)) {
            String accessibleText = button.getAccessibleText();
            if (accessibleText != null && accessibleText.endsWith(item)) {
                return button;
            }
        }
        throw new AssertionError("no menu button found for item " + item);
    }

    private static List<PlayMenuButton> collectPlayMenuButtons(ResponsiveCardGrid<String> grid) {
        List<PlayMenuButton> buttons = new ArrayList<>();
        collectPlayMenuButtons(grid, buttons);
        return buttons;
    }

    private static void collectPlayMenuButtons(Node node, List<PlayMenuButton> out) {
        if (node instanceof PlayMenuButton button) {
            out.add(button);
        }
        if (node instanceof javafx.scene.Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectPlayMenuButtons(child, out);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Region cardFor(ResponsiveCardGrid<String> grid, String item) {
        Map<String, Region> cards = (Map<String, Region>) readField(grid, "cardsByItem");
        Region card = cards.get(item);
        assertNotNull(card, "expected a rendered card for item " + item);
        return card;
    }

    private static void concealHoverActionsIn(ResponsiveCardGrid<String> grid, Node card) {
        try {
            Method method = ResponsiveCardGrid.class.getDeclaredMethod("concealHoverActionsIn", Node.class);
            method.setAccessible(true);
            method.invoke(grid, card);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not invoke concealHoverActionsIn", e);
        }
    }

    private static void setBooleanField(Object target, String name, boolean value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.setBoolean(target, value);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not set field " + name, e);
        }
    }

    private static Object readField(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError("could not read field " + name, e);
        }
    }
}
