package com.uiptv.widget;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlinePanelServiceTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void openReplacesHostAndCloseRestoresPreviousViewOnce() throws Exception {
        Label initial = new Label("Initial");
        StackPane host = runOnFxThread(() -> InlinePanelService.createHost(initial));
        runOnFxThread(() -> {
            InlinePanelService.install(host);
            return null;
        });
        AtomicInteger closeCount = new AtomicInteger();

        Optional<InlinePanelService.InlinePanelHandle> handle = runOnFxThread(() ->
                InlinePanelService.open("About", new Label("Inline content"), "Close", closeCount::incrementAndGet));

        assertTrue(handle.isPresent());
        assertEquals(1, runOnFxThread(() -> host.getChildren().size()));
        assertTrue(runOnFxThread(() -> host.getChildren().get(0).getStyleClass().contains("uiptv-inline-panel")));

        runOnFxThread(() -> {
            handle.get().close();
            handle.get().close();
            return null;
        });

        assertEquals(1, closeCount.get());
        assertSame(initial, runOnFxThread(() -> host.getChildren().get(0)));
    }

    @Test
    void openDetachesContentFromExistingParent() throws Exception {
        StackPane host = runOnFxThread(() -> InlinePanelService.createHost(new Label("Initial")));
        Pane previousParent = runOnFxThread(Pane::new);
        Label content = runOnFxThread(() -> {
            InlinePanelService.install(host);
            Label label = new Label("Moved");
            previousParent.getChildren().add(label);
            return label;
        });

        Optional<InlinePanelService.InlinePanelHandle> handle = runOnFxThread(() ->
                InlinePanelService.open("Moved content", content));

        assertTrue(handle.isPresent());
        assertFalse(runOnFxThread(() -> previousParent.getChildren().contains(content)));
    }

    @Test
    void fillHeightContentIsUsedDirectlyInsideScroller() throws Exception {
        StackPane host = runOnFxThread(() -> InlinePanelService.createHost(new Label("Initial")));
        VBox content = runOnFxThread(() -> {
            InlinePanelService.install(host);
            VBox box = new VBox(new Label("Fill"));
            box.getStyleClass().add(InlinePanelService.FILL_HEIGHT_STYLE_CLASS);
            return box;
        });

        runOnFxThread(() -> {
            InlinePanelService.open("Fill", content);
            return null;
        });

        Node frame = runOnFxThread(() -> host.getChildren().get(0));
        ScrollPane scroller = runOnFxThread(() -> findDescendant(frame, ScrollPane.class));
        assertSame(content, runOnFxThread(scroller::getContent));
    }

    @Test
    void showChoiceReturnsButtonFiredByConfigurer() throws Exception {
        StackPane host = runOnFxThread(() -> InlinePanelService.createHost(new Label("Initial")));
        runOnFxThread(() -> {
            InlinePanelService.install(host);
            return null;
        });

        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType apply = new ButtonType("Apply", ButtonBar.ButtonData.APPLY);

        Optional<ButtonType> result = InlinePanelService.showChoice(
                "Apply change",
                new Label("Apply now?"),
                List.of(cancel, apply),
                cancel,
                buttons -> {
                    Button applyButton = buttons.get(apply);
                    Platform.runLater(applyButton::fire);
                }
        );

        assertEquals(Optional.of(apply), result);
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
}
