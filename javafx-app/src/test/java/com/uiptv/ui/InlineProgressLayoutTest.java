package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import com.uiptv.widget.SegmentedProgressBar;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InlineProgressLayoutTest extends DbBackedUiTest {
    @BeforeAll
    static void setUpJavaFx() throws Exception {
        FxTestSupport.initJavaFx();
    }

    @Test
    void reloadCacheProgressUsesInlineSummaryAndGrowingBar() throws Exception {
        ProgressRowSnapshot snapshot = runOnFxThread(() ->
                snapshot(new ReloadCacheInline(), "reload-progress-row"));

        assertInlineProgressRow(snapshot);
    }

    @Test
    void preselectedReloadCacheRunHidesFailureHandlingCard() throws Exception {
        Account account = new Account();
        account.setDbId("not-saved");
        account.setAccountName("Not saved");
        account.setType(AccountType.STALKER_PORTAL);

        ReloadCacheInline inline = runOnFxThread(() -> new ReloadCacheInline(List.of(account)));

        assertNull(findDescendantByStyle(inline, Region.class, "reload-failure-policy-card"));
    }

    @Test
    void verificationProgressUsesInlineSummaryAndGrowingBar() throws Exception {
        ProgressRowSnapshot snapshot = runOnFxThread(() ->
                snapshot(new ProgressInline(), "verification-progress-row"));

        assertInlineProgressRow(snapshot);
    }

    private static void assertInlineProgressRow(ProgressRowSnapshot snapshot) {
        assertEquals(2, snapshot.childCount());
        assertTrue(snapshot.firstChildIsLabel());
        assertTrue(snapshot.secondChildIsSegmentedProgressBar());
        assertEquals(Pos.CENTER_LEFT, snapshot.alignment());
        assertEquals(Priority.ALWAYS, snapshot.progressBarGrow());
        assertEquals(Region.USE_PREF_SIZE, snapshot.labelMinWidth());
        assertEquals(Region.USE_PREF_SIZE, snapshot.labelMaxWidth());
        assertEquals(0, snapshot.progressBarMinWidth());
        assertEquals(Double.MAX_VALUE, snapshot.progressBarMaxWidth());
    }

    private static ProgressRowSnapshot snapshot(Node root, String rowStyleClass) {
        HBox row = findDescendantByStyle(root, HBox.class, rowStyleClass);
        if (row == null) {
            throw new AssertionError("Missing " + rowStyleClass + " descendant in\n" + describeTree(root, 0));
        }
        Node firstChild = row.getChildren().get(0);
        Node secondChild = row.getChildren().get(1);
        Region label = (Region) firstChild;
        Region progressBar = (Region) secondChild;
        return new ProgressRowSnapshot(
                row.getChildren().size(),
                firstChild instanceof Label,
                secondChild instanceof SegmentedProgressBar,
                row.getAlignment(),
                HBox.getHgrow(secondChild),
                label.getMinWidth(),
                label.getMaxWidth(),
                progressBar.getMinWidth(),
                progressBar.getMaxWidth()
        );
    }

    private static <T extends Node> T findDescendantByStyle(Node root, Class<T> type, String styleClass) {
        if (type.isInstance(root) && root.getStyleClass().contains(styleClass)) {
            return type.cast(root);
        }
        if (root instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                T found = findDescendantByStyle(child, type, styleClass);
                if (found != null) {
                    return found;
                }
            }
        } else if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                T found = findDescendantByStyle(child, type, styleClass);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private static String describeTree(Node root, int depth) {
        StringBuilder description = new StringBuilder();
        description.append("  ".repeat(Math.max(0, depth)))
                .append(root.getClass().getSimpleName())
                .append(root.getStyleClass())
                .append('\n');
        if (root instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                description.append(describeTree(child, depth + 1));
            }
        } else if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                description.append(describeTree(child, depth + 1));
            }
        }
        return description.toString();
    }

    private record ProgressRowSnapshot(
            int childCount,
            boolean firstChildIsLabel,
            boolean secondChildIsSegmentedProgressBar,
            Pos alignment,
            Priority progressBarGrow,
            double labelMinWidth,
            double labelMaxWidth,
            double progressBarMinWidth,
            double progressBarMaxWidth
    ) {
    }
}
