package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import com.uiptv.widget.InlinePanelService;
import com.uiptv.widget.SegmentedProgressBar;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.uiptv.testsupport.FxTestSupport.runOnFxThread;
import static com.uiptv.testsupport.FxTestSupport.waitForFxEvents;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
        assertFalse(inline.shouldPromptAutomaticGlobalFailureDecision(List.of(account)));
    }

    @Test
    void preselectedMultiReloadPromptsBeforeRunInsteadOfShowingInlineFailureCard() throws Exception {
        Account first = account("first", "First");
        Account second = account("second", "Second");

        ReloadCacheInline inline = runOnFxThread(() -> new ReloadCacheInline(List.of(first, second)));

        assertNull(findDescendantByStyle(inline, Region.class, "reload-failure-policy-card"));
        assertTrue(inline.shouldPromptAutomaticGlobalFailureDecision(List.of(first, second)));
        assertFalse(inline.shouldPromptAutomaticGlobalFailureDecision(List.of(first)));
    }

    @Test
    void manualReloadUsesInlineFailureCardAndDoesNotUsePreRunPrompt() throws Exception {
        Account first = account("first", "First");
        Account second = account("second", "Second");

        ReloadCacheInline inline = runOnFxThread(() -> new ReloadCacheInline(List.of()));

        assertTrue(findDescendantByStyle(inline, Region.class, "reload-failure-policy-card") != null);
        assertFalse(inline.shouldPromptAutomaticGlobalFailureDecision(List.of(first, second)));
    }

    @Test
    void problemAccountRowsAlignCheckboxWithAccountTitleAndKeepSpacing() throws Exception {
        ProblemAccountRowSnapshot snapshot = runOnFxThread(() -> {
            ReloadCacheInline inline = new ReloadCacheInline(List.of());
            Account account = new Account();
            account.setDbId("problem-account");
            account.setAccountName("Problem Account");
            account.setType(AccountType.STALKER_PORTAL);

            VBox accountsBox = new VBox();
            var method = ReloadCacheInline.class.getDeclaredMethod(
                    "buildProblemAccountsInlineRoot",
                    List.class,
                    java.util.Map.class,
                    VBox.class,
                    Runnable.class
            );
            method.setAccessible(true);
            VBox root = (VBox) method.invoke(
                    inline,
                    List.of(account),
                    java.util.Map.of(account.getDbId(), summaryStatus("BAD", List.of("Handshake failed"))),
                    accountsBox,
                    (Runnable) () -> { }
            );

            CheckBox row = findDescendantByStyle(root, CheckBox.class, "reload-problem-account-row");
            HBox content = row != null && row.getGraphic() instanceof HBox hBox
                    && hBox.getStyleClass().contains("reload-problem-account-content")
                    ? hBox
                    : null;
            Label accountName = content == null
                    ? null
                    : findDescendantByStyle(content, Label.class, "reload-problem-account-name");
            return new ProblemAccountRowSnapshot(
                    row == null ? null : row.getAlignment(),
                    content == null ? null : content.getAlignment(),
                    content == null ? -1 : content.getPadding().getLeft(),
                    accountName != null
            );
        });

        assertEquals(Pos.TOP_LEFT, snapshot.rowAlignment());
        assertEquals(Pos.TOP_LEFT, snapshot.contentAlignment());
        assertTrue(snapshot.contentLeftPadding() >= 10);
        assertTrue(snapshot.hasAccountTitle());
    }

    @Test
    void verificationProgressUsesInlineSummaryAndGrowingBar() throws Exception {
        ProgressRowSnapshot snapshot = runOnFxThread(() ->
                snapshot(new ProgressInline(), "verification-progress-row"));

        assertInlineProgressRow(snapshot);
    }

    @Test
    void verificationProgressUsesCenteredCardWithInternalLogScroll() throws Exception {
        VerificationLayoutSnapshot snapshot = runOnFxThread(() -> {
            ProgressInline inline = new ProgressInline();
            BorderPane card = findDescendantByStyle(inline, BorderPane.class, "verification-progress-card-shell");
            ScrollPane logScroll = findDescendantByStyle(inline, ScrollPane.class, "verification-log-scroll");
            return new VerificationLayoutSnapshot(
                    inline.getStyleClass().contains(InlinePanelService.FILL_HEIGHT_STYLE_CLASS),
                    findDescendantByStyle(inline, javafx.scene.layout.StackPane.class, "verification-progress-shell") != null,
                    card != null,
                    card != null && card.getBottom() instanceof HBox footer
                            && footer.getStyleClass().contains("management-popup-footer"),
                    logScroll != null,
                    logScroll == null ? null : logScroll.getHbarPolicy(),
                    logScroll == null ? null : logScroll.getVbarPolicy()
            );
        });

        assertTrue(snapshot.fillHeight());
        assertTrue(snapshot.hasCenteredShell());
        assertTrue(snapshot.hasCard());
        assertTrue(snapshot.footerPinnedToCardBottom());
        assertTrue(snapshot.hasInternalLogScroll());
        assertEquals(ScrollPane.ScrollBarPolicy.NEVER, snapshot.horizontalPolicy());
        assertEquals(ScrollPane.ScrollBarPolicy.AS_NEEDED, snapshot.verticalPolicy());
    }

    @Test
    void verificationProgressMarksDefaultMacHeader() throws Exception {
        ProgressInline inline = runOnFxThread(() -> {
            ProgressInline progressInline = new ProgressInline();
            progressInline.setDefaultMacAddress("00:1A:79:AA:BB:CC");
            progressInline.addVerificationHeader("00:1A:79:AA:BB:CC", 0, 2);
            return progressInline;
        });
        waitForFxEvents();

        DefaultMacSnapshot snapshot = runOnFxThread(() -> {
            Label badge = findDescendantByStyle(inline, Label.class, "verification-default-pill");
            return new DefaultMacSnapshot(
                    findDescendantByStyle(inline, VBox.class, "verification-mac-card") != null,
                    badge != null,
                    badge == null ? "" : badge.getText()
            );
        });

        assertTrue(snapshot.hasMacCard());
        assertTrue(snapshot.hasDefaultBadge());
        assertEquals("Default", snapshot.badgeText());
    }

    @Test
    void verificationPauseClockAppearsInFixedFooter() throws Exception {
        ProgressInline inline = runOnFxThread(() -> {
            ProgressInline progressInline = new ProgressInline();
            progressInline.setPauseStatus(9, 10);
            return progressInline;
        });
        waitForFxEvents();

        PauseWidgetSnapshot snapshot = runOnFxThread(() -> {
            BorderPane card = findDescendantByStyle(inline, BorderPane.class, "verification-progress-card-shell");
            HBox pauseWidget = findDescendantByStyle(inline, HBox.class, "verification-pause-widget");
            return new PauseWidgetSnapshot(
                    pauseWidget != null,
                    pauseWidget != null && pauseWidget.isVisible(),
                    pauseWidget != null && pauseWidget.isManaged(),
                    card != null
                            && card.getBottom() instanceof HBox footer
                            && pauseWidget != null
                            && footer.getChildren().contains(pauseWidget)
            );
        });

        assertTrue(snapshot.hasPauseWidget());
        assertTrue(snapshot.visible());
        assertTrue(snapshot.managed());
        assertTrue(snapshot.inFixedFooter());
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

    private static Account account(String id, String name) {
        Account account = new Account();
        account.setDbId(id);
        account.setAccountName(name);
        account.setType(AccountType.STALKER_PORTAL);
        return account;
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
        } else if (root instanceof ScrollPane scrollPane) {
            Node content = scrollPane.getContent();
            if (content != null) {
                T found = findDescendantByStyle(content, type, styleClass);
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

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Object summaryStatus(String level, List<String> reasons) throws Exception {
        Class levelClass = Class.forName("com.uiptv.ui.ReloadCacheInline$SummaryLevel");
        Object summaryLevel = Enum.valueOf(levelClass, level);
        Class<?> statusClass = Class.forName("com.uiptv.ui.ReloadCacheInline$SummaryStatus");
        var constructor = statusClass.getDeclaredConstructor(levelClass, int.class, List.class);
        constructor.setAccessible(true);
        return constructor.newInstance(summaryLevel, 0, reasons);
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

    private record VerificationLayoutSnapshot(
            boolean fillHeight,
            boolean hasCenteredShell,
            boolean hasCard,
            boolean footerPinnedToCardBottom,
            boolean hasInternalLogScroll,
            ScrollPane.ScrollBarPolicy horizontalPolicy,
            ScrollPane.ScrollBarPolicy verticalPolicy
    ) {
    }

    private record DefaultMacSnapshot(boolean hasMacCard, boolean hasDefaultBadge, String badgeText) {
    }

    private record PauseWidgetSnapshot(boolean hasPauseWidget, boolean visible, boolean managed, boolean inFixedFooter) {
    }

    private record ProblemAccountRowSnapshot(
            Pos rowAlignment,
            Pos contentAlignment,
            double contentLeftPadding,
            boolean hasAccountTitle
    ) {
    }
}
