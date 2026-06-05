package com.uiptv.widget;

import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.initJavaFx;
import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkCardTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        initJavaFx();
    }

    @Test
    void titleSuffixIsRenderedAsMutedTextAndSubtitleWraps() throws Exception {
        BookmarkCard card = runOnFxThread(() -> new BookmarkCard(
                "BBC One",
                "Living Room",
                "News and entertainment",
                null,
                false,
                null,
                false,
                null
        ));

        TextFlow titleFlow = runOnFxThread(() -> findDescendant(card, TextFlow.class));
        List<String> titleTexts = runOnFxThread(() -> titleFlow.getChildren().stream()
                .filter(Text.class::isInstance)
                .map(Text.class::cast)
                .map(Text::getText)
                .toList());
        assertEquals(List.of("BBC One", " Living Room"), titleTexts);
        assertTrue(runOnFxThread(() -> ((Text) titleFlow.getChildren().get(1))
                .getStyleClass().contains("bookmark-account-suffix-text")));

        Label subtitle = runOnFxThread(() -> findLabels(card).stream()
                .filter(label -> "News and entertainment".equals(label.getText()))
                .findFirst()
                .orElseThrow());
        assertTrue(runOnFxThread(subtitle::isWrapText));
        assertTrue(runOnFxThread(subtitle::isVisible));
        assertTrue(runOnFxThread(subtitle::isManaged));
    }

    @Test
    void blankSubtitleIsHiddenAndTrailingActionGetsCardStyle() throws Exception {
        Button action = runOnFxThread(Button::new);
        BookmarkCard card = runOnFxThread(() -> new BookmarkCard(
                "Channel",
                "",
                "",
                null,
                false,
                null,
                true,
                action
        ));

        Label drmBadge = runOnFxThread(() -> findLabels(card).stream()
                .filter(label -> label.getStyleClass().contains("drm-badge"))
                .findFirst()
                .orElseThrow());
        assertTrue(runOnFxThread(drmBadge::isVisible));
        assertTrue(runOnFxThread(() -> action.getStyleClass().contains("bookmark-card-title-action")));
        assertFalse(runOnFxThread(() -> findLabels(card).stream()
                .filter(label -> label.getStyleClass().contains("bookmark-channel-account"))
                .findFirst()
                .orElseThrow()
                .isManaged()));
    }

    @Test
    void imageIsNotAddedWhenPlainTextModeDisablesImages() throws Exception {
        BookmarkCard card = runOnFxThread(() -> new BookmarkCard(
                "Channel",
                "Account",
                "http://example.invalid/logo.png",
                false,
                "logo",
                false
        ));

        assertEquals(1, runOnFxThread(() -> card.getChildren().size()));
        assertTrue(runOnFxThread(() -> card.getChildren().get(0) instanceof javafx.scene.layout.VBox));
    }

    private static List<Label> findLabels(Node root) {
        if (root instanceof Label label) {
            return List.of(label);
        }
        if (root instanceof javafx.scene.Parent parent) {
            return parent.getChildrenUnmodifiable().stream()
                    .flatMap(child -> findLabels(child).stream())
                    .toList();
        }
        return List.of();
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
