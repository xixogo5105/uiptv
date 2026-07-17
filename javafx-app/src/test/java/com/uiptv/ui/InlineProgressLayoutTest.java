package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.testsupport.DbBackedUiTest;
import com.uiptv.testsupport.FxTestSupport;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import com.uiptv.widget.InlinePanelService;
import com.uiptv.widget.SegmentedProgressBar;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.ToggleButton;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
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
    void manualReloadHidesQueueUntilStartThenRestoresFailureActionOnCompletion() throws Exception {
        ReloadCacheInline inline = runOnFxThread(() -> new ReloadCacheInline(List.of()));

        ReloadStartVisibilitySnapshot initial = runOnFxThread(() -> reloadStartVisibilitySnapshot(inline));

        assertTrue(initial.hasProgressCard());
        assertFalse(initial.progressVisible());
        assertFalse(initial.progressManaged());
        assertTrue(initial.hasFailureCard());
        assertTrue(initial.failureVisible());
        assertTrue(initial.failureManaged());

        ReloadStartVisibilitySnapshot started = runOnFxThread(() -> {
            var method = ReloadCacheInline.class.getDeclaredMethod("showReloadStartedState");
            method.setAccessible(true);
            method.invoke(inline);
            return reloadStartVisibilitySnapshot(inline);
        });

        assertTrue(started.progressVisible());
        assertTrue(started.progressManaged());
        assertFalse(started.failureVisible());
        assertFalse(started.failureManaged());

        ReloadStartVisibilitySnapshot completed = runOnFxThread(() -> {
            var method = ReloadCacheInline.class.getDeclaredMethod("showReloadCompletedState");
            method.setAccessible(true);
            method.invoke(inline);
            return reloadStartVisibilitySnapshot(inline);
        });

        assertTrue(completed.progressVisible());
        assertTrue(completed.progressManaged());
        assertTrue(completed.failureVisible());
        assertTrue(completed.failureManaged());
    }

    @Test
    void manualReloadOmitsAccountColumnTitleAndUsesClearStartActionLabel() throws Exception {
        ReloadActionLayoutSnapshot snapshot = runOnFxThread(() -> {
            ReloadCacheInline inline = new ReloadCacheInline(List.of());
            VBox accountColumn = findDescendantByStyle(inline, VBox.class, "reload-account-column");
            boolean hasAccountTitle = accountColumn != null && accountColumn.getChildren().stream()
                    .filter(Label.class::isInstance)
                    .map(Label.class::cast)
                    .anyMatch(label -> label.getStyleClass().contains("management-popup-section-title")
                            && I18n.tr("autoAccount").equals(label.getText()));
            boolean hasStartReloadButton = findDescendantsByType(inline, Button.class).stream()
                    .anyMatch(button -> I18n.tr("autoStartCacheReload").equals(button.getText()));
            return new ReloadActionLayoutSnapshot(hasAccountTitle, hasStartReloadButton);
        });

        assertFalse(snapshot.hasAccountTitle());
        assertTrue(snapshot.hasStartReloadButton());
    }

    @Test
    void globalFailurePromptUsesVerticalRadioOptionsAndSingleActionButton() throws Exception {
        GlobalFailurePromptSnapshot snapshot = runOnFxThread(() -> {
            ReloadCacheInline inline = new ReloadCacheInline(List.of());
            var method = ReloadCacheInline.class.getDeclaredMethod(
                    "buildGlobalFailurePrompt",
                    Account.class,
                    String.class,
                    boolean.class
            );
            method.setAccessible(true);
            Object prompt = method.invoke(
                    inline,
                    account("global-failure", "yoogold.fyi"),
                    "Live TV get_all_channels failed for Stalker Portal.",
                    true
            );
            var dialogAccessor = prompt.getClass().getDeclaredMethod("dialog");
            dialogAccessor.setAccessible(true);
            Dialog<?> dialog = (Dialog<?>) dialogAccessor.invoke(prompt);
            List<RadioButton> options = findDescendantsByType(dialog.getDialogPane().getContent(), RadioButton.class);
            return new GlobalFailurePromptSnapshot(
                    dialog.getDialogPane().getPrefWidth(),
                    dialog.getDialogPane().getButtonTypes().size(),
                    dialog.getDialogPane().getButtonTypes().getFirst().getText(),
                    options.stream().map(RadioButton::getText).toList(),
                    options.stream().allMatch(option -> option.getStyleClass().contains("reload-global-failure-option")),
                    !options.isEmpty() && options.getFirst().isSelected()
            );
        });

        assertTrue(snapshot.dialogWidth() >= 700);
        assertEquals(1, snapshot.actionButtonCount());
        assertEquals("Apply Action", snapshot.actionButtonText());
        assertEquals(List.of(
                I18n.tr("reloadCarryOn"),
                I18n.tr("reloadMarkBadIgnoreDomain"),
                I18n.tr("reloadMarkBadAndNext"),
                I18n.tr("reloadStopAll")
        ), snapshot.options());
        assertTrue(snapshot.allOptionsUseCompactStyle());
        assertTrue(snapshot.firstOptionSelected());
    }

    @Test
    void reloadCacheTwoColumnLayoutKeepsAccountTypeChipsWideEnoughInPopup() throws Exception {
        ReloadColumnsSnapshot snapshot = runOnFxThread(() -> {
            ReloadCacheInline inline = new ReloadCacheInline(List.of());
            GridPane mainContent = findDescendantByStyle(inline, GridPane.class, "reload-main-content");
            if (mainContent == null || mainContent.getColumnConstraints().size() < 2) {
                throw new AssertionError("Missing reload two-column layout");
            }
            ColumnConstraints accountColumn = mainContent.getColumnConstraints().getFirst();
            ColumnConstraints logColumn = mainContent.getColumnConstraints().get(1);
            return new ReloadColumnsSnapshot(
                    accountColumn.getPercentWidth(),
                    accountColumn.getMinWidth(),
                    logColumn.getPercentWidth(),
                    logColumn.getMinWidth()
            );
        });

        assertEquals(42.0, snapshot.accountPercent(), 0.001);
        assertTrue(snapshot.accountMinWidth() >= 680);
        assertEquals(58.0, snapshot.logPercent(), 0.001);
        assertTrue(snapshot.logMinWidth() >= 360);
    }

    @Test
    void reloadCacheUsesSingleM3uChipForLocalAndRemoteAccounts() throws Exception {
        AccountService service = AccountService.getInstance();
        service.save(cacheAccount("Reload Local", AccountType.M3U8_LOCAL));
        service.save(cacheAccount("Reload Remote", AccountType.M3U8_URL));
        service.save(cacheAccount("Reload Xtreme", AccountType.XTREME_API));

        ReloadM3uChipSnapshot snapshot = runOnFxThread(() -> {
            ReloadCacheInline inline = new ReloadCacheInline(List.of());
            FlowPane chipRow = findDescendantByStyle(inline, FlowPane.class, "reload-select-chip-row");
            List<ToggleButton> chips = findDescendantsByType(chipRow, ToggleButton.class);
            ToggleButton m3uChip = chips.stream()
                    .filter(chip -> "M3U".equals(chip.getText()))
                    .findFirst()
                    .orElseThrow(() -> new AssertionError("Missing M3U chip"));

            m3uChip.fire();

            List<CheckBox> accountRows = findDescendantsByType(inline, CheckBox.class).stream()
                    .filter(row -> row.getStyleClass().contains("reload-account-row"))
                    .toList();
            return new ReloadM3uChipSnapshot(
                    chips.stream().map(ToggleButton::getText).toList(),
                    isTypeSelected(accountRows, AccountType.M3U8_LOCAL),
                    isTypeSelected(accountRows, AccountType.M3U8_URL),
                    isTypeSelected(accountRows, AccountType.XTREME_API)
            );
        });

        assertEquals(4, snapshot.chipLabels().size());
        assertTrue(snapshot.chipLabels().contains("M3U"));
        assertFalse(snapshot.chipLabels().contains(I18n.tr("reloadM3uLocalPlaylist")));
        assertFalse(snapshot.chipLabels().contains(I18n.tr("reloadM3uRemotePlaylist")));
        assertTrue(snapshot.localM3uSelected());
        assertTrue(snapshot.remoteM3uSelected());
        assertFalse(snapshot.xtremeSelected());
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
        return account(id, name, AccountType.STALKER_PORTAL);
    }

    private static Account account(String id, String name, AccountType type) {
        Account account = new Account();
        account.setDbId(id);
        account.setAccountName(name);
        account.setType(type);
        return account;
    }

    private static Account cacheAccount(String name, AccountType type) {
        Account account = new Account();
        account.setAccountName(name);
        account.setType(type);
        account.setUrl("http://example.test/");
        account.setM3u8Path("http://example.test/list.m3u");
        return account;
    }

    private static boolean isTypeSelected(List<CheckBox> rows, AccountType type) {
        List<CheckBox> matchingRows = rows.stream()
                .filter(row -> row.getUserData() instanceof Account account && account.getType() == type)
                .toList();
        return !matchingRows.isEmpty() && matchingRows.stream().allMatch(CheckBox::isSelected);
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

    private static ReloadStartVisibilitySnapshot reloadStartVisibilitySnapshot(ReloadCacheInline inline) {
        VBox progressCard = findDescendantByStyle(inline, VBox.class, "reload-progress-card");
        VBox failureCard = findDescendantByStyle(inline, VBox.class, "reload-failure-policy-card");
        return new ReloadStartVisibilitySnapshot(
                progressCard != null,
                progressCard != null && progressCard.isVisible(),
                progressCard != null && progressCard.isManaged(),
                failureCard != null,
                failureCard != null && failureCard.isVisible(),
                failureCard != null && failureCard.isManaged()
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

    private static <T extends Node> List<T> findDescendantsByType(Node root, Class<T> type) {
        List<T> results = new ArrayList<>();
        collectDescendantsByType(root, type, results);
        return results;
    }

    private static <T extends Node> void collectDescendantsByType(Node root, Class<T> type, List<T> results) {
        if (root == null) {
            return;
        }
        if (type.isInstance(root)) {
            results.add(type.cast(root));
        }
        if (root instanceof Pane pane) {
            for (Node child : pane.getChildren()) {
                collectDescendantsByType(child, type, results);
            }
        } else if (root instanceof ScrollPane scrollPane) {
            collectDescendantsByType(scrollPane.getContent(), type, results);
        } else if (root instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                collectDescendantsByType(child, type, results);
            }
        }
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

    private record ReloadStartVisibilitySnapshot(
            boolean hasProgressCard,
            boolean progressVisible,
            boolean progressManaged,
            boolean hasFailureCard,
            boolean failureVisible,
            boolean failureManaged
    ) {
    }

    private record ReloadActionLayoutSnapshot(
            boolean hasAccountTitle,
            boolean hasStartReloadButton
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

    private record ReloadColumnsSnapshot(
            double accountPercent,
            double accountMinWidth,
            double logPercent,
            double logMinWidth
    ) {
    }

    private record ReloadM3uChipSnapshot(
            List<String> chipLabels,
            boolean localM3uSelected,
            boolean remoteM3uSelected,
            boolean xtremeSelected
    ) {
    }

    private record ProblemAccountRowSnapshot(
            Pos rowAlignment,
            Pos contentAlignment,
            double contentLeftPadding,
            boolean hasAccountTitle
    ) {
    }

    private record GlobalFailurePromptSnapshot(
            double dialogWidth,
            int actionButtonCount,
            String actionButtonText,
            List<String> options,
            boolean allOptionsUseCompactStyle,
            boolean firstOptionSelected
    ) {
    }
}
