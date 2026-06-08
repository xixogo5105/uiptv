package com.uiptv.ui;

import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.I18n;
import javafx.application.HostServices;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.Pane;
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

    @Test
    void aboutUpdateActionUsesInlinePrimaryStyle() throws Exception {
        ButtonSnapshot updateButton = runOnFxThread(() -> {
            Pane dialogCard = AboutUI.createDialogCard(Mockito.mock(HostServices.class), () -> { });
            return snapshot(findButtonByText(dialogCard, I18n.tr("autoCheckForUpdates")));
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
            Pane dialogCard = AboutUI.createDialogCard(Mockito.mock(HostServices.class), () -> { });
            return snapshot(findButtonByStyle(dialogCard, "about-close-button"));
        });

        assertNotNull(closeButton);
        assertTrue(closeButton.styleClasses().contains("uiptv-inline-secondary-button"));
    }

    @Test
    void aboutIncludesHelpGuideLink() throws Exception {
        HostServices hostServices = Mockito.mock(HostServices.class);

        Hyperlink helpLink = runOnFxThread(() -> {
            Pane dialogCard = AboutUI.createDialogCard(hostServices, () -> { });
            return findHyperlinkByText(dialogCard, I18n.tr("autoHelp"));
        });

        assertNotNull(helpLink);

        runOnFxThread(() -> {
            helpLink.fire();
            return null;
        });

        Mockito.verify(hostServices).showDocument("https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md");
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

    private static Hyperlink findHyperlinkByText(Node root, String text) {
        if (root instanceof Hyperlink hyperlink && text.equals(hyperlink.getText())) {
            return hyperlink;
        }
        return findHyperlinkDescendant(root, child -> findHyperlinkByText(child, text));
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

    private static Hyperlink findHyperlinkDescendant(Node root, HyperlinkFinder finder) {
        if (root instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                Hyperlink found = finder.find(child);
                if (found != null) {
                    return found;
                }
            }
        } else if (root instanceof ScrollPane scrollPane) {
            Node content = scrollPane.getContent();
            if (content != null) {
                Hyperlink found = finder.find(content);
                if (found != null) {
                    return found;
                }
            }
        } else if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                Hyperlink found = finder.find(child);
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

    @FunctionalInterface
    private interface HyperlinkFinder {
        Hyperlink find(Node child);
    }

    private record ButtonSnapshot(List<String> styleClasses, boolean defaultButton) {
    }
}
