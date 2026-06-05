package com.uiptv.ui;

import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import com.uiptv.widget.InlinePanelService;
import javafx.application.HostServices;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
import javafx.scene.layout.StackPane;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AboutUITest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @AfterEach
    void resetInlinePanelHost() throws Exception {
        runOnFxThread(() -> {
            InlinePanelService.install(new StackPane());
            return null;
        });
    }

    @Test
    void aboutUpdateActionUsesInlinePrimaryStyle() throws Exception {
        ButtonSnapshot updateButton = runOnFxThread(() -> {
            StackPane host = InlinePanelService.createHost(new Label("home"));
            InlinePanelService.install(host);

            AboutUI.show(Mockito.mock(HostServices.class));

            return snapshot(findButtonByText(host, I18n.tr("autoCheckForUpdates")));
        });

        assertNotNull(updateButton);
        assertTrue(updateButton.styleClasses().contains("uiptv-inline-primary-button"));
        assertTrue(updateButton.styleClasses().contains("about-update-button"));
        assertFalse(updateButton.styleClasses().contains("prominent"));
        assertFalse(updateButton.defaultButton());
    }

    @Test
    void aboutCloseActionUsesInlineSecondaryStyle() throws Exception {
        ButtonSnapshot closeButton = runOnFxThread(() -> {
            StackPane host = InlinePanelService.createHost(new Label("home"));
            InlinePanelService.install(host);

            AboutUI.show(Mockito.mock(HostServices.class));

            return snapshot(findButtonByStyle(host, "about-close-button"));
        });

        assertNotNull(closeButton);
        assertTrue(closeButton.styleClasses().contains("uiptv-inline-secondary-button"));
    }

    private static ButtonSnapshot snapshot(Button button) {
        if (button == null) {
            return null;
        }
        return new ButtonSnapshot(List.copyOf(button.getStyleClass()), button.isDefaultButton());
    }

    private static Button findButtonByText(Node root, String text) {
        if (root instanceof Button button && text.equals(button.getText())) {
            return button;
        }
        return findDescendant(root, child -> findButtonByText(child, text));
    }

    private static Button findButtonByStyle(Node root, String styleClass) {
        if (root instanceof Button button && button.getStyleClass().contains(styleClass)) {
            return button;
        }
        return findDescendant(root, child -> findButtonByStyle(child, styleClass));
    }

    private static Button findDescendant(Node root, Finder finder) {
        if (root instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                Button found = finder.find(child);
                if (found != null) {
                    return found;
                }
            }
        } else if (root instanceof ScrollPane scrollPane) {
            Node content = scrollPane.getContent();
            if (content != null) {
                Button found = finder.find(content);
                if (found != null) {
                    return found;
                }
            }
        } else if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Button found = finder.find(child);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    @FunctionalInterface
    private interface Finder {
        Button find(Node child);
    }

    private record ButtonSnapshot(List<String> styleClasses, boolean defaultButton) {
    }
}
