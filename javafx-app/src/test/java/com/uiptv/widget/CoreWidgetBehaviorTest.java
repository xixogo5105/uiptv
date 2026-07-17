package com.uiptv.widget;

import javafx.event.Event;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.PickResult;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.shape.Circle;
import javafx.scene.shape.SVGPath;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreWidgetBehaviorTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void switchToggleRespondsToMouseAndKeyboardUnlessDisabled() throws Exception {
        SwitchToggle toggle = runOnFxThread(SwitchToggle::new);

        assertFalse(runOnFxThread(toggle::isSelected));
        assertEquals("Off", runOnFxThread(toggle::getAccessibleText));

        runOnFxThread(() -> {
            Event.fireEvent(toggle, mouseClick(toggle));
            return null;
        });
        assertTrue(runOnFxThread(toggle::isSelected));
        assertEquals("On", runOnFxThread(toggle::getAccessibleText));

        runOnFxThread(() -> {
            toggle.setDisable(true);
            Event.fireEvent(toggle, keyPressed(KeyCode.SPACE));
            return null;
        });
        assertTrue(runOnFxThread(toggle::isSelected));

        runOnFxThread(() -> {
            toggle.setDisable(false);
            Event.fireEvent(toggle, keyPressed(KeyCode.ENTER));
            return null;
        });
        assertFalse(runOnFxThread(toggle::isSelected));
    }

    @Test
    void iconActionButtonUpdatesTooltipIconAndRunsAction() throws Exception {
        AtomicInteger actionCount = new AtomicInteger();
        IconActionButton button = runOnFxThread(() -> new IconActionButton("Open", "M0 0H1V1Z", actionCount::incrementAndGet));

        assertEquals("Open", runOnFxThread(button::getAccessibleText));
        assertEquals("Open", runOnFxThread(() -> button.getTooltip().getText()));
        assertInstanceOf(SVGPath.class, runOnFxThread(button::getGraphic));

        runOnFxThread(() -> {
            button.setTooltipText("Open guide");
            button.setIconPath("M1 1H2V2Z");
            button.fire();
            return null;
        });

        assertEquals(1, actionCount.get());
        assertEquals("Open guide", runOnFxThread(button::getAccessibleText));
        assertEquals("Open guide", runOnFxThread(() -> button.getTooltip().getText()));
        assertEquals("M1 1H2V2Z", runOnFxThread(() -> ((SVGPath) button.getGraphic()).getContent()));
    }

    @Test
    void playMenuButtonBuildsGraphicOnlyAccessibleMenuButton() throws Exception {
        PlayMenuButton button = runOnFxThread(() -> new PlayMenuButton("More playback options"));

        assertEquals("More playback options", runOnFxThread(button::getAccessibleText));
        assertEquals("More playback options", runOnFxThread(() -> button.getTooltip().getText()));
        Pane icon = runOnFxThread(() -> (Pane) button.getGraphic());
        assertEquals(4, runOnFxThread(() -> icon.getChildren().size()));
        assertTrue(runOnFxThread(() -> icon.getChildren().stream().allMatch(Circle.class::isInstance)));
    }

    @Test
    void loadingStateViewUsesSafeMessageAndSizing() throws Exception {
        LoadingStateView loading = runOnFxThread(() -> new LoadingStateView(null, 22));

        assertTrue(runOnFxThread(() -> loading.getStyleClass().contains("uiptv-loading-state")));
        assertEquals("", runOnFxThread(() -> labelText(loading)));

        runOnFxThread(() -> {
            loading.setMessage("Reloading cache");
            return null;
        });
        assertEquals("Reloading cache", runOnFxThread(() -> labelText(loading)));
    }

    @Test
    void renderQualityOptimizesSingleNodesAndTrees() throws Exception {
        VBox tree = runOnFxThread(() -> {
            VBox root = new VBox(new Label("Child"), new Region());
            root.setCache(true);
            root.getChildren().forEach(child -> child.setCache(true));
            return root;
        });

        runOnFxThread(() -> {
            UiRenderQuality.optimizeTextNode(null);
            UiRenderQuality.optimizeLayout(null);
            UiRenderQuality.optimizeTree(null);
            UiRenderQuality.optimizeTree(tree);
            return null;
        });

        assertFalse(runOnFxThread(tree::isCache));
        assertTrue(runOnFxThread(tree::isSnapToPixel));
        assertTrue(runOnFxThread(() -> tree.getChildren().stream().noneMatch(Node::isCache)));
    }

    @Test
    void playerOptionCardSelectsRadioUnlessInteractiveChildWasClicked() throws Exception {
        RadioButton selector = runOnFxThread(RadioButton::new);
        TextField input = runOnFxThread(TextField::new);
        PlayerOptionCard card = runOnFxThread(() -> new PlayerOptionCard("VLC", "Use VLC", selector, input));

        runOnFxThread(() -> {
            Event.fireEvent(card, mouseClick(card));
            return null;
        });
        assertTrue(runOnFxThread(selector::isSelected));

        runOnFxThread(() -> {
            selector.setSelected(false);
            Event.fireEvent(card, mouseClick(input));
            return null;
        });
        assertFalse(runOnFxThread(selector::isSelected));
    }

    @Test
    void externalPlayerPathCardConfiguresPathFieldAndBrowseButton() throws Exception {
        RadioButton selector = runOnFxThread(RadioButton::new);
        TextField pathField = runOnFxThread(TextField::new);
        Button browseButton = runOnFxThread(Button::new);

        ExternalPlayerPathCard card = runOnFxThread(() -> new ExternalPlayerPathCard(
                "External",
                "Custom player",
                selector,
                pathField,
                browseButton
        ));

        assertSame(selector, runOnFxThread(() -> findDescendant(card, RadioButton.class)));
        assertTrue(runOnFxThread(() -> pathField.getStyleClass().contains("settings-player-path-field")));
        assertEquals("...", runOnFxThread(browseButton::getText));
        assertNotNull(runOnFxThread(browseButton::getTooltip));
        assertEquals(34.0, runOnFxThread(browseButton::getPrefWidth));
        assertEquals(30.0, runOnFxThread(browseButton::getPrefHeight));
    }

    private static String labelText(Node root) {
        Label label = findDescendant(root, Label.class);
        return label == null ? null : label.getText();
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

    private static MouseEvent mouseClick(Node target) {
        return new MouseEvent(
                MouseEvent.MOUSE_CLICKED,
                0,
                0,
                0,
                0,
                MouseButton.PRIMARY,
                1,
                false,
                false,
                false,
                false,
                true,
                false,
                false,
                false,
                false,
                false,
                new PickResult(target, 0, 0)
        );
    }
}
