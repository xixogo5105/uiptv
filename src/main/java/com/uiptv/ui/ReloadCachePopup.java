package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.util.AccountType;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.SegmentedProgressBar;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.CACHE_SUPPORTED;

public class ReloadCachePopup extends VBox {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");

    private enum AccountRunStatus {
        QUEUED, RUNNING, DONE, YELLOW, FAILED, EMPTY
    }

    private enum SummaryLevel {
        GOOD, YELLOW, BAD
    }

    private static final class SummaryStatus {
        private final SummaryLevel level;
        private final int channelsLoaded;
        private final List<String> reasons;

        private SummaryStatus(SummaryLevel level, int channelsLoaded, List<String> reasons) {
            this.level = level;
            this.channelsLoaded = channelsLoaded;
            this.reasons = reasons;
        }
    }

    private final Stage stage;
    private final VBox accountsVBox = new VBox(5);
    private final VBox logVBox = new VBox(8);
    private final ScrollPane accountsScrollPane = new ScrollPane();
    private final ScrollPane logScrollPane = new ScrollPane(logVBox);
    private final SegmentedProgressBar progressBar = new SegmentedProgressBar();
    private final ProminentButton reloadButton = new ProminentButton(I18n.tr("autoReloadSelected"));
    private final CacheService cacheService = new CacheServiceImpl();
    private final AccountService accountService = AccountService.getInstance();
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private final Map<String, AccountLogPanel> accountLogPanels = new LinkedHashMap<>();
    private final List<String> runAccountOrder = new ArrayList<>();
    private final List<String> latestSummaryLines = new ArrayList<>();
    private final Map<String, SummaryStatus> latestAccountSummaries = new LinkedHashMap<>();
    private final ReloadRunOutcomeTracker runOutcomeTracker = new ReloadRunOutcomeTracker();
    private final Runnable onAccountsDeleted;
    private VBox accountColumn;
    private ColumnConstraints accountsColumn;
    private ColumnConstraints logsColumn;
    private String currentRunningAccountId;

    public static void showPopup(Stage owner) {
        showPopup(owner, null);
    }

    public static void showPopup(Stage owner, List<Account> preselectedAccounts) {
        showPopup(owner, preselectedAccounts, null);
    }

    public static void showPopup(Stage owner, List<Account> preselectedAccounts, Runnable onAccountsDeleted) {
        Stage popupStage = new Stage();
        if (owner != null) {
            popupStage.initOwner(owner);
            popupStage.initModality(Modality.WINDOW_MODAL);
        }
        ReloadCachePopup popup = new ReloadCachePopup(popupStage, preselectedAccounts, onAccountsDeleted);
        Scene scene = new Scene(popup, 1368, 720);
        I18n.applySceneOrientation(scene);
        popupStage.setTitle(I18n.tr("autoReloadAccountsCache"));
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    public ReloadCachePopup(Stage stage) {
        this(stage, null);
    }

    public ReloadCachePopup(Stage stage, List<Account> preselectedAccounts) {
        this(stage, preselectedAccounts, null);
    }

    public ReloadCachePopup(Stage stage, List<Account> preselectedAccounts, Runnable onAccountsDeleted) {
        this.stage = stage;
        this.onAccountsDeleted = onAccountsDeleted;
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefSize(1368, 720);
        getStylesheets().add(RootApplication.currentTheme);

        accountsVBox.setPadding(new Insets(10));
        List<Account> supportedAccounts = accountService.getAll().values().stream()
                .filter(account -> CACHE_SUPPORTED.contains(account.getType()))
                .collect(Collectors.toList());

        // Define the sort order for AccountType
        EnumMap<AccountType, Integer> order = new EnumMap<>(AccountType.class);
        order.put(AccountType.STALKER_PORTAL, 1);
        order.put(AccountType.XTREME_API, 2);
        order.put(AccountType.M3U8_LOCAL, 3);
        order.put(AccountType.M3U8_URL, 4);

        // Sort the accounts based on the defined order
        supportedAccounts.sort(Comparator.comparing(account -> order.getOrDefault(account.getType(), Integer.MAX_VALUE)));

        for (int i = 0; i < supportedAccounts.size(); i++) {
            Account account = supportedAccounts.get(i);
            CheckBox accountCheckBox = new CheckBox(account.getAccountName());
            accountCheckBox.setUserData(account);
            accountCheckBox.setMaxWidth(Double.MAX_VALUE);
            accountCheckBox.setPadding(new Insets(5));

            if (i % 2 == 0) {
                accountCheckBox.setStyle("-fx-background-color: derive(-fx-control-inner-background, -2%);");
            } else {
                accountCheckBox.setStyle("-fx-background-color: -fx-control-inner-background;");
            }
            accountsVBox.getChildren().add(accountCheckBox);
            checkBoxes.add(accountCheckBox);
        }

        MenuButton selectMenu = new MenuButton(I18n.tr("autoSelectByTypes"));
        CheckMenuItem allItem = new CheckMenuItem(I18n.tr("commonAll"));
        CheckMenuItem stalkerItem = new CheckMenuItem(I18n.tr("reloadStalkerPortalAccounts"));
        CheckMenuItem xtremeItem = new CheckMenuItem(I18n.tr("reloadXtremeAccount"));
        CheckMenuItem m3uLocalItem = new CheckMenuItem(I18n.tr("reloadM3uLocalPlaylist"));
        CheckMenuItem m3uRemoteItem = new CheckMenuItem(I18n.tr("reloadM3uRemotePlaylist"));

        allItem.setOnAction(e -> {
            boolean selected = allItem.isSelected();
            checkBoxes.forEach(cb -> cb.setSelected(selected));
            stalkerItem.setSelected(selected);
            xtremeItem.setSelected(selected);
            m3uLocalItem.setSelected(selected);
            m3uRemoteItem.setSelected(selected);
        });

        stalkerItem.setOnAction(e -> updateCheckboxes(AccountType.STALKER_PORTAL, stalkerItem.isSelected()));
        xtremeItem.setOnAction(e -> updateCheckboxes(AccountType.XTREME_API, xtremeItem.isSelected()));
        m3uLocalItem.setOnAction(e -> updateCheckboxes(AccountType.M3U8_LOCAL, m3uLocalItem.isSelected()));
        m3uRemoteItem.setOnAction(e -> updateCheckboxes(AccountType.M3U8_URL, m3uRemoteItem.isSelected()));
        selectMenu.setPrefWidth(200);
        selectMenu.getItems().addAll(allItem, new SeparatorMenuItem(), stalkerItem, xtremeItem, m3uLocalItem, m3uRemoteItem);

        accountsScrollPane.setContent(accountsVBox);
        accountsScrollPane.setFitToWidth(true);
        accountsScrollPane.setMinHeight(250);
        accountsScrollPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);

        logVBox.setPadding(new Insets(5));
        logScrollPane.setFitToWidth(true);
        logScrollPane.setMinHeight(250);
        logScrollPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(logScrollPane, Priority.ALWAYS);

        accountColumn = new VBox(10, selectMenu, accountsScrollPane);
        accountColumn.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);

        GridPane mainContent = new GridPane();
        mainContent.setHgap(10);
        mainContent.setMaxWidth(Double.MAX_VALUE);

        accountsColumn = new ColumnConstraints();
        accountsColumn.setPercentWidth(35);
        accountsColumn.setFillWidth(true);
        accountsColumn.setHgrow(Priority.ALWAYS);

        logsColumn = new ColumnConstraints();
        logsColumn.setPercentWidth(65);
        logsColumn.setFillWidth(true);
        logsColumn.setHgrow(Priority.ALWAYS);

        RowConstraints contentRow = new RowConstraints();
        contentRow.setVgrow(Priority.ALWAYS);
        contentRow.setFillHeight(true);

        mainContent.getColumnConstraints().addAll(accountsColumn, logsColumn);
        mainContent.getRowConstraints().add(contentRow);
        mainContent.add(accountColumn, 0, 0);
        mainContent.add(logScrollPane, 1, 0);
        VBox.setVgrow(mainContent, Priority.ALWAYS);


        reloadButton.setOnAction(event -> {
            startReloadInBackground();
        });

        // Ensure proper layout when toggling visibility
        reloadButton.managedProperty().bind(reloadButton.visibleProperty());

        Button copyLogButton = new Button(I18n.tr("autoCopyLog"));
        copyLogButton.setOnAction(event -> {
            final Clipboard clipboard = Clipboard.getSystemClipboard();
            final ClipboardContent content = new ClipboardContent();
            StringBuilder sb = new StringBuilder();
            for (String accountId : runAccountOrder) {
                AccountLogPanel panel = accountLogPanels.get(accountId);
                if (panel == null) {
                    continue;
                }
                sb.append(panel.getAccountLabel()).append("\n");
                panel.getLogs().forEach(line -> sb.append("  ").append(line).append("\n"));
                SummaryStatus status = latestAccountSummaries.get(accountId);
                if (status != null) {
                    sb.append("  ").append(I18n.tr("reloadSummaryLabel")).append(": ")
                            .append(summaryLevelLabel(status.level))
                            .append(" (")
                            .append(I18n.tr("autoChannels").toLowerCase())
                            .append("=")
                            .append(I18n.formatNumber(String.valueOf(status.channelsLoaded)))
                            .append(")\n");
                    if (!status.reasons.isEmpty()) {
                        sb.append("  ").append(I18n.tr("reloadReasonsLabel")).append(": ").append(String.join(" | ", status.reasons)).append("\n");
                    }
                }
                sb.append("\n");
            }
            if (!latestSummaryLines.isEmpty()) {
                sb.append(I18n.tr("autoRunSummary")).append("\n");
                latestSummaryLines.forEach(line -> sb.append("  ").append(line).append("\n"));
                sb.append("\n");
            }
            content.putString(sb.toString());
            clipboard.setContent(content);
        });

        Button closeButton = new Button(I18n.tr("autoClose"));
        closeButton.setOnAction(event -> stage.close());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonBox = new HBox(10, reloadButton, spacer, copyLogButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        getChildren().addAll(progressBar, mainContent, buttonBox);

        // Register cleanup listener to release memory when stage closes
        registerStageCloseListener();

        if (preselectedAccounts != null && !preselectedAccounts.isEmpty()) {
            preselectAccounts(preselectedAccounts);
            if (checkBoxes.stream().anyMatch(CheckBox::isSelected)) {
                hideAccountSelectionColumn();
                Platform.runLater(this::startReloadInBackground);
            }
        }
    }

    private void registerStageCloseListener() {
        stage.setOnCloseRequest(event -> releaseTransientState());
    }

    private void releaseTransientState() {
        // Clear all cached data to allow garbage collection
        checkBoxes.clear();
        accountLogPanels.clear();
        runAccountOrder.clear();
        latestSummaryLines.clear();
        latestAccountSummaries.clear();

        // Clear all log panel UI nodes
        logVBox.getChildren().clear();

        // Clear account column UI
        if (accountColumn != null) {
            accountColumn.getChildren().clear();
        }
    }

    private void hideAccountSelectionColumn() {
        accountColumn.setVisible(false);
        accountColumn.setManaged(false);
        accountsColumn.setPercentWidth(0);
        accountsColumn.setMinWidth(0);
        accountsColumn.setMaxWidth(0);
        logsColumn.setPercentWidth(100);
    }

    private void updateCheckboxes(AccountType type, boolean selected) {
        for (CheckBox cb : checkBoxes) {
            Account acc = (Account) cb.getUserData();
            if (acc.getType() == type) {
                cb.setSelected(selected);
            }
        }
    }

    private void startReloadInBackground() {
        new Thread(this::reloadSelectedAccounts).start();
    }

    private void preselectAccounts(List<Account> accountsToPreselect) {
        Set<String> accountIds = new HashSet<>();
        Set<String> accountNames = new HashSet<>();

        for (Account account : accountsToPreselect) {
            if (account == null) {
                continue;
            }
            if (account.getDbId() != null && !account.getDbId().isBlank()) {
                accountIds.add(account.getDbId());
            }
            if (account.getAccountName() != null && !account.getAccountName().isBlank()) {
                accountNames.add(account.getAccountName());
            }
        }

        for (CheckBox checkBox : checkBoxes) {
            Account listedAccount = (Account) checkBox.getUserData();
            if (listedAccount == null) {
                continue;
            }
            String listedAccountId = listedAccount.getDbId();
            String listedAccountName = listedAccount.getAccountName();
            boolean matchesId = listedAccountId != null && accountIds.contains(listedAccountId);
            boolean matchesName = listedAccountName != null && accountNames.contains(listedAccountName);
            checkBox.setSelected(matchesId || matchesName);
        }
    }

    private void reloadSelectedAccounts() {
        Platform.runLater(() -> {
            reloadButton.setVisible(false);
        });

        List<Account> selectedAccounts = checkBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(checkBox -> (Account) checkBox.getUserData())
                .collect(Collectors.toList());

        int total = selectedAccounts.size();
        progressBar.setTotal(total);
        prepareAccountLogPanels(selectedAccounts);

        if (total == 0) {
            Platform.runLater(() -> {
                reloadButton.setVisible(true);
            });
            return;
        }

        List<Account> processedAccounts = new ArrayList<>();
        Map<String, AccountRunStatus> finalStatuses = new LinkedHashMap<>();
        Map<String, SummaryStatus> summaryStatusByAccountId = new LinkedHashMap<>();
        int totalFetchedChannels = 0;

        for (int i = 0; i < total; i++) {
            Account account = selectedAccounts.get(i);
            processedAccounts.add(account);
            setRunningAccount(account, i + 1, total);

            boolean success = false;
            boolean failed = false;
            int fetchedChannelCount = 0;
            List<String> accountIssues = new ArrayList<>();
            final boolean[] globalFailurePrompted = {false};
            try {
                cacheService.reloadCache(account, message -> {
                    logMessage(account, message);
                    String issue = extractIssueReason(account, message);
                    if (issue != null) {
                        addIssue(accountIssues, issue);
                    }
                    if (globalFailurePrompted[0]) {
                        return;
                    }
                    String failureReason = extractGlobalFailureReason(account, message);
                    if (failureReason == null) {
                        return;
                    }
                    globalFailurePrompted[0] = true;
                    boolean carryOn = promptCarryOnAfterGlobalFailure(account, failureReason);
                    if (!carryOn) {
                        throw new SkipAccountReloadException();
                    }
                });
                fetchedChannelCount = runOutcomeTracker.getFetchedChannels(account.getDbId());
                if (fetchedChannelCount <= 0) {
                    logMessage(account, "No channels found.");
                    addIssue(accountIssues, I18n.tr("reloadNoChannelsLoaded"));
                }
            } catch (SkipAccountReloadException e) {
                failed = true;
                logMessage(account, "Marked bad and skipped after global call failure.");
                addIssue(accountIssues, I18n.tr("reloadMarkedBadByUser"));
            } catch (Exception e) {
                failed = true;
                logMessage(account, "Reload failed: " + shortFailure(e.getMessage()));
                addIssue(accountIssues, I18n.tr("reloadFailedReason", shortFailure(e.getMessage())));
            }

            if (runOutcomeTracker.hasCriticalFailure(account.getDbId())) {
                failed = true;
                success = false;
            } else if (fetchedChannelCount > 0) {
                success = true;
                totalFetchedChannels += fetchedChannelCount;
            }

            int existingChannelCount = cacheService.getChannelCountForAccount(account.getDbId());
            int availableChannelCount = Math.max(fetchedChannelCount, existingChannelCount);

            SummaryStatus summaryStatus = buildSummaryStatus(availableChannelCount, failed, accountIssues);
            summaryStatusByAccountId.put(account.getDbId(), summaryStatus);

            SegmentedProgressBar.SegmentStatus segmentStatus = switch (summaryStatus.level) {
                case GOOD -> SegmentedProgressBar.SegmentStatus.SUCCESS;
                case YELLOW -> SegmentedProgressBar.SegmentStatus.WARNING;
                case BAD -> SegmentedProgressBar.SegmentStatus.FAILURE;
            };
            progressBar.updateSegment(i, segmentStatus);

            AccountRunStatus finalStatus = switch (summaryStatus.level) {
                case GOOD -> AccountRunStatus.DONE;
                case YELLOW -> AccountRunStatus.YELLOW;
                case BAD -> (availableChannelCount > 0 ? AccountRunStatus.YELLOW : (failed ? AccountRunStatus.FAILED : AccountRunStatus.EMPTY));
            };
            finalStatuses.put(account.getDbId(), finalStatus);
            updateAccountStatus(account, finalStatus, availableChannelCount);
        }

        int runTotalFetchedChannels = totalFetchedChannels;
        Platform.runLater(() -> {
            com.uiptv.util.AppLog.addLog("Reload run completed.");
            reloadButton.setVisible(true);
            latestAccountSummaries.clear();
            latestAccountSummaries.putAll(summaryStatusByAccountId);
            appendRunSummary(processedAccounts, finalStatuses, runTotalFetchedChannels);

            Map<String, SummaryStatus> problematicAccounts = new LinkedHashMap<>();
            for (Account account : processedAccounts) {
                SummaryStatus status = summaryStatusByAccountId.get(account.getDbId());
                if (status != null && status.level != SummaryLevel.GOOD) {
                    problematicAccounts.put(account.getDbId(), status);
                }
            }
            if (!problematicAccounts.isEmpty()) {
                showDeleteProblemAccountsPopup(processedAccounts, problematicAccounts);
            }
        });
    }

    private void showDeleteProblemAccountsPopup(List<Account> processedAccounts, Map<String, SummaryStatus> problematicAccounts) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle(I18n.tr("autoDeleteProblematicAccounts"));

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label warningLabel = new Label(I18n.tr("autoTheFollowingAccountsAreFlaggedAsBADOrYELLOWSelectTheOnesYouWantToDelete"));
        warningLabel.setWrapText(true);
        warningLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        VBox accountsBox = new VBox(5);
        ScrollPane scrollPane = new ScrollPane(accountsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        CheckBox selectAll = new CheckBox(I18n.tr("autoSelectAll"));
        selectAll.setOnAction(e -> accountsBox.getChildren().forEach(node -> {
            if (node instanceof CheckBox) ((CheckBox) node).setSelected(selectAll.isSelected());
        }));

        List<Account> badAccounts = processedAccounts.stream()
                .filter(a -> problematicAccounts.containsKey(a.getDbId()))
                .filter(a -> problematicAccounts.get(a.getDbId()).level == SummaryLevel.BAD)
                .collect(Collectors.toList());
        List<Account> yellowAccounts = processedAccounts.stream()
                .filter(a -> problematicAccounts.containsKey(a.getDbId()))
                .filter(a -> problematicAccounts.get(a.getDbId()).level == SummaryLevel.YELLOW)
                .collect(Collectors.toList());

        if (!badAccounts.isEmpty()) {
            Label badLabel = new Label(I18n.tr("autoBadRed"));
            badLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b91c1c;");
            accountsBox.getChildren().add(badLabel);
            addProblemAccountsToDeleteBox(accountsBox, badAccounts, problematicAccounts);
        }
        if (!yellowAccounts.isEmpty()) {
            Label yellowLabel = new Label(I18n.tr("autoYellowPartiallySuccessful"));
            yellowLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
            accountsBox.getChildren().add(yellowLabel);
            addProblemAccountsToDeleteBox(accountsBox, yellowAccounts, problematicAccounts);
        }

        Button deleteButton = new Button(I18n.tr("autoDeleteSelected"));
        deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteButton.setOnAction(e -> {
            List<Account> toDelete = accountsBox.getChildren().stream()
                    .filter(n -> n instanceof CheckBox && ((CheckBox) n).isSelected())
                    .map(n -> (Account) n.getUserData())
                    .collect(Collectors.toList());

            if (toDelete.isEmpty()) return;

            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    I18n.tr("reloadConfirmDeleteAccounts", toDelete.size()),
                    ButtonType.YES,
                    ButtonType.NO
            );
            if (RootApplication.currentTheme != null) {
                alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
            }
            alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl()
                    ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT
                    : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);
            alert.showAndWait().ifPresent(response -> {
                if (response == ButtonType.YES) {
                    toDelete.forEach(a -> AccountService.getInstance().delete(a.getDbId()));
                    if (onAccountsDeleted != null) {
                        onAccountsDeleted.run();
                    }
                    popupStage.close();
                }
            });
        });

        Button cancelButton = new Button(I18n.tr("autoCancel"));
        cancelButton.setOnAction(e -> popupStage.close());

        HBox buttons = new HBox(10, deleteButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(warningLabel, selectAll, scrollPane, buttons);

        Scene scene = new Scene(root, 500, 500);
        I18n.applySceneOrientation(scene);
        if (RootApplication.currentTheme != null) {
            scene.getStylesheets().add(RootApplication.currentTheme);
        }
        popupStage.setScene(scene);
        popupStage.show();
    }

    private void addProblemAccountsToDeleteBox(VBox accountsBox, List<Account> accounts, Map<String, SummaryStatus> problematicAccounts) {
        for (Account account : accounts) {
            SummaryStatus status = problematicAccounts.get(account.getDbId());
            String reasons = status == null || status.reasons.isEmpty()
                    ? I18n.tr("reloadNoReasonCaptured")
                    : String.join(" | ", status.reasons);
            CheckBox cb = new CheckBox(account.getAccountName() + " (" + account.getType().getDisplay() + ") - " + reasons);
            cb.setWrapText(true);
            cb.setUserData(account);
            accountsBox.getChildren().add(cb);
        }
    }

    private void prepareAccountLogPanels(List<Account> selectedAccounts) {
        runOnFxThreadAndWait(() -> {
            accountLogPanels.clear();
            runAccountOrder.clear();
            latestSummaryLines.clear();
            latestAccountSummaries.clear();
            runOutcomeTracker.clear();
            currentRunningAccountId = null;
            logVBox.getChildren().clear();

            for (Account account : selectedAccounts) {
                AccountLogPanel panel = new AccountLogPanel(account);
                panel.setStatus(AccountRunStatus.QUEUED, null);
                panel.setExpanded(false);
                accountLogPanels.put(account.getDbId(), panel);
                runAccountOrder.add(account.getDbId());
                logVBox.getChildren().add(panel.getRoot());
            }
        });
    }

    private void appendRunSummary(List<Account> processedAccounts, Map<String, AccountRunStatus> finalStatuses, int totalSuccessChannels) {
        if (processedAccounts.size() == 1) {
            latestSummaryLines.clear();
            return;
        }

        int successCount = 0;
        int yellowCount = 0;
        int badCount = 0;

        List<String> yellowNames = new ArrayList<>();
        List<String> badNames = new ArrayList<>();

        for (Account account : processedAccounts) {
            SummaryStatus summary = latestAccountSummaries.get(account.getDbId());
            if (summary == null) {
                AccountRunStatus status = finalStatuses.get(account.getDbId());
                if (status == AccountRunStatus.DONE) {
                    successCount++;
                } else {
                    badCount++;
                    badNames.add(account.getAccountName());
                }
                continue;
            }
            if (summary.level == SummaryLevel.GOOD) {
                successCount++;
            } else if (summary.level == SummaryLevel.YELLOW) {
                yellowCount++;
                yellowNames.add(account.getAccountName());
            } else {
                badCount++;
                badNames.add(account.getAccountName());
            }
        }

        latestSummaryLines.clear();
        latestSummaryLines.add(I18n.tr("reloadSummaryCompleted", processedAccounts.size(), processedAccounts.size()));
        latestSummaryLines.add(I18n.tr("reloadSummaryGood", successCount));
        latestSummaryLines.add(I18n.tr("reloadSummaryYellow", yellowCount));
        latestSummaryLines.add(I18n.tr("reloadSummaryBad", badCount));
        latestSummaryLines.add(I18n.tr("reloadSummaryChannelsLoaded", totalSuccessChannels));
        if (!yellowNames.isEmpty()) {
            latestSummaryLines.add(I18n.tr("reloadSummaryYellowAccounts", String.join(", ", yellowNames)));
        }
        if (!badNames.isEmpty()) {
            latestSummaryLines.add(I18n.tr("reloadSummaryBadAccounts", String.join(", ", badNames)));
        }

        VBox summaryBox = new VBox(4);
        summaryBox.setPadding(new Insets(10));
        summaryBox.setStyle("-fx-background-color: derive(-fx-control-inner-background, -2%);"
                + "-fx-border-color: -fx-box-border;"
                + "-fx-border-radius: 6;"
                + "-fx-background-radius: 6;");

        Label title = new Label(I18n.tr("autoRunSummary"));
        title.setStyle("-fx-font-weight: bold;");
        summaryBox.getChildren().add(title);

        for (String line : latestSummaryLines) {
            Label label = new Label(line);
            label.setWrapText(true);
            summaryBox.getChildren().add(label);
        }

        logVBox.getChildren().add(new Separator());
        logVBox.getChildren().add(summaryBox);
        logScrollPane.setVvalue(1.0);
    }

    private void setRunningAccount(Account currentAccount, int current, int total) {
        runOnFxThreadAndWait(() -> {
            String nextAccountId = currentAccount.getDbId();

            AccountLogPanel currentPanel = accountLogPanels.get(nextAccountId);
            if (currentPanel != null) {
                currentPanel.setExpanded(true);
                currentPanel.setStatus(AccountRunStatus.RUNNING, current, total);
                scrollPanelIntoView(currentPanel);
            }
            currentRunningAccountId = nextAccountId;
        });
    }

    private void scrollPanelIntoView(AccountLogPanel panel) {
        if (panel == null) {
            return;
        }
        logVBox.applyCss();
        logVBox.layout();

        Bounds panelBounds = panel.getRoot().getBoundsInParent();
        double contentHeight = logVBox.getBoundsInLocal().getHeight();
        double viewportHeight = logScrollPane.getViewportBounds().getHeight();
        if (contentHeight <= 0 || viewportHeight <= 0) {
            return;
        }

        double targetY = panelBounds.getMinY() - Math.max(0, (viewportHeight - panelBounds.getHeight()) / 2.0);
        double maxScroll = contentHeight - viewportHeight;
        if (maxScroll <= 0) {
            logScrollPane.setVvalue(0);
            return;
        }
        double vValue = Math.max(0, Math.min(1, targetY / maxScroll));
        logScrollPane.setVvalue(vValue);
    }

    private void updateAccountStatus(Account account, AccountRunStatus status, Integer channelCount) {
        Platform.runLater(() -> {
            AccountLogPanel panel = accountLogPanels.get(account.getDbId());
            if (panel != null) {
                panel.setStatus(status, channelCount);
            }
        });
    }

    private void logMessage(Account account, String message) {
        String compact = compactLog(account, message);
        if (compact == null || compact.isBlank()) {
            return;
        }
        runOutcomeTracker.recordMessage(account.getDbId(), message, compact);
        Platform.runLater(() -> {
            AccountLogPanel panel = accountLogPanels.get(account.getDbId());
            if (panel != null) {
                panel.appendLog(compact);
            }
        });
    }

    private String compactLog(Account account, String message) {
        if (message == null) {
            return "";
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        if (trimmed.startsWith("Loaded channels from local cache")) {
            return I18n.tr("reloadLoadedChannelsFromLocalCache");
        }
        if (trimmed.startsWith("No fresh cache found for category")) {
            String category = trimmed.replace("No fresh cache found for category", "")
                    .replace(". Fetching from portal...", "")
                    .trim();
            return I18n.tr("reloadCacheMissFetchingCategory", category);
        }
        if (trimmed.startsWith("No fresh cached categories found")) {
            String modeCode = modeCode(account);
            if ("VOD".equals(modeCode) || "SERIES".equals(modeCode)) {
                return "";
            }
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadCategoriesCacheMissFetching")
                    : I18n.tr("reloadModeCategoriesCacheMissFetching", mode);
        }
        if (trimmed.startsWith("No cached categories found")) {
            String modeCode = modeCode(account);
            if ("VOD".equals(modeCode) || "SERIES".equals(modeCode)) {
                return "";
            }
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadNoCachedCategoriesFetching")
                    : I18n.tr("reloadModeNoCachedCategoriesFetching", mode);
        }
        if (trimmed.startsWith("Fetching categories from Xtreme API")) {
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadFetchingCategoriesFromProvider", "Xtreme API")
                    : I18n.tr("reloadFetchingModeCategoriesFromProvider", mode, "Xtreme API");
        }
        if (trimmed.startsWith("Fetching categories from Stalker Portal")) {
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadFetchingCategoriesFromProvider", "Stalker Portal")
                    : I18n.tr("reloadFetchingModeCategoriesFromProvider", mode, "Stalker Portal");
        }
        if (trimmed.startsWith("Found Categories")) {
            Integer count = extractFirstNumber(trimmed);
            if (count != null) {
                String mode = modeLabel(account);
                return mode == null
                        ? I18n.tr("reloadCategoriesCount", I18n.formatNumber(String.valueOf(count)))
                        : I18n.tr("reloadModeCategoriesCount", mode, I18n.formatNumber(String.valueOf(count)));
            }
            return I18n.tr("autoCategories") + ":";
        }
        if (trimmed.startsWith("Found Channels")) {
            Integer count = extractFirstNumber(trimmed);
            if (count != null) {
                String mode = modeLabel(account);
                return mode == null
                        ? I18n.tr("reloadChannelsCount", I18n.formatNumber(String.valueOf(count)))
                        : I18n.tr("reloadModeChannelsCount", mode, I18n.formatNumber(String.valueOf(count)));
            }
            return I18n.tr("autoChannels") + ":";
        }
        if (trimmed.endsWith("saved Successfully ✓")) {
            return I18n.tr("reloadSaved");
        }
        if (trimmed.startsWith("Fetching page")) {
            Matcher matcher = Pattern.compile("Fetching page\\s+(\\d+)\\s+for category\\s+(.+)\\.\\.\\.")
                    .matcher(trimmed);
            if (matcher.matches()) {
                return I18n.tr("reloadPageCategory",
                        I18n.formatNumber(matcher.group(1)),
                        matcher.group(2).trim());
            }
        }
        if (trimmed.startsWith("Fetched")) {
            Matcher matcher = Pattern.compile("Fetched\\s+(\\d+)\\s+channels from page\\s+(\\d+)\\.?")
                    .matcher(trimmed);
            if (matcher.matches()) {
                return I18n.tr("reloadChannelsPage",
                        I18n.formatNumber(matcher.group(1)),
                        I18n.formatNumber(matcher.group(2)));
            }
        }
        if (trimmed.startsWith("Page ") && trimmed.endsWith(" returned no channels.")) {
            Integer page = extractFirstNumber(trimmed);
            return page == null
                    ? I18n.tr("reloadNoChannelsLoaded")
                    : I18n.tr("reloadPageNoChannels", I18n.formatNumber(String.valueOf(page)));
        }
        if (trimmed.startsWith("Saved ")) {
            if (trimmed.contains(" to local VOD/Series cache.")) {
                Integer count = extractFirstNumber(trimmed);
                if (count != null) {
                    String mode = modeLabel(account);
                    if (mode != null) {
                        return I18n.tr("reloadModeCategoriesCount", mode, I18n.formatNumber(String.valueOf(count)));
                    }
                    return I18n.tr("reloadCategoriesSaved", I18n.formatNumber(String.valueOf(count)));
                }
            }
            return I18n.tr("reloadSavedToCache");
        }
        if (trimmed.equals("No categories found. Keeping existing cache.")) {
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadNoCategoriesFoundCacheKept")
                    : I18n.tr("reloadModeCategoriesNoneFound", mode);
        }
        if (trimmed.equals("No channels found in any category. Keeping existing cache.")) {
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadModeChannelsNoneFound", I18n.tr("categoryTabLiveTv"))
                    : I18n.tr("reloadModeChannelsNoneFound", mode);
        }
        if (trimmed.startsWith("Reload failed:")) {
            return I18n.tr("reloadFailedReason",
                    shortFailure(trimmed.substring("Reload failed:".length()).trim()));
        }
        if (trimmed.equals("Handshake failed.") || trimmed.startsWith("Handshake failed for")) {
            return I18n.tr("reloadFailedHandshake");
        }
        if (trimmed.startsWith("Network error while loading categories")) {
            return I18n.tr("reloadFailedNetworkError");
        }
        if (trimmed.startsWith("Failed to parse channels")) {
            return I18n.tr("reloadFailedChannelParseError");
        }
        if (trimmed.startsWith("Last-resort fetch failed for category")) {
            return I18n.tr("reloadFailedFallbackCategoryFetch");
        }
        if (trimmed.startsWith("Global Xtreme channel lookup failed")) {
            return I18n.tr("reloadGlobalLookupFailedUsingCategoryFetch");
        }
        if (trimmed.startsWith("Global Xtreme channel lookup returned no channels")) {
            return I18n.tr("reloadGlobalLookupEmptyUsingCategoryFetch");
        }
        if (trimmed.startsWith("Global Xtreme channel lookup returned uncategorized rows only")) {
            return I18n.tr("reloadGlobalLookupUncategorizedUsingCategoryFetch");
        }
        if (trimmed.startsWith("No channels returned by get_all_channels")) {
            return I18n.tr("reloadGlobalChannelListEmptyTryingFallback");
        }
        if (trimmed.startsWith("Global Stalker get_all_channels failed")) {
            return I18n.tr("reloadGlobalChannelListFailedTryingFallback");
        }
        if (trimmed.startsWith("Last-resort fetch succeeded. Collected")) {
            Integer count = extractFirstNumber(trimmed);
            return count == null
                    ? I18n.tr("reloadFallbackFetchSucceeded")
                    : I18n.tr("reloadFallbackFetchSucceededWithChannels", I18n.formatNumber(String.valueOf(count)));
        }
        if (trimmed.startsWith("Global VOD category list failed:")) {
            return I18n.tr("reloadModeCategoryListFailed", I18n.tr("categoryTabVideoOnDemand"));
        }
        if (trimmed.startsWith("Global SERIES category list failed:")) {
            return I18n.tr("reloadModeCategoryListFailed", I18n.tr("categoryTabTvSeries"));
        }
        if (trimmed.equals("Marked bad and skipped after global call failure.")) {
            return I18n.tr("reloadMarkedBadMovedNext");
        }
        return trimmed;
    }

    private String extractGlobalFailureReason(Account account, String message) {
        if (account == null || message == null) {
            return null;
        }
        if (account.getType() != AccountType.XTREME_API && account.getType() != AccountType.STALKER_PORTAL) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.startsWith("Global Xtreme channel lookup failed")) {
            return I18n.tr("reloadLiveTvGlobalCallFailedForXtreme");
        }
        if (trimmed.startsWith("Global Stalker get_all_channels failed")) {
            return I18n.tr("reloadLiveTvGetAllChannelsFailedForStalker");
        }
        if (trimmed.startsWith("Global VOD category list failed:")) {
            return I18n.tr("reloadModeCategoryListCallFailed", I18n.tr("categoryTabVideoOnDemand"));
        }
        if (trimmed.startsWith("Global SERIES category list failed:")) {
            return I18n.tr("reloadModeCategoryListCallFailed", I18n.tr("categoryTabTvSeries"));
        }
        if (trimmed.startsWith("Network error while loading categories")) {
            String modeCode = modeCode(account);
            if ("VOD".equals(modeCode)) {
                return I18n.tr("reloadModeCategoryListCallFailed", I18n.tr("categoryTabVideoOnDemand"));
            }
            if ("SERIES".equals(modeCode)) {
                return I18n.tr("reloadModeCategoryListCallFailed", I18n.tr("categoryTabTvSeries"));
            }
        }
        return null;
    }

    private SummaryStatus buildSummaryStatus(int fetchedChannelCount, boolean failed, List<String> issues) {
        List<String> normalizedReasons = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
        if (fetchedChannelCount > 0) {
            if (normalizedReasons.isEmpty() && !failed) {
                return new SummaryStatus(SummaryLevel.GOOD, fetchedChannelCount, normalizedReasons);
            }
            return new SummaryStatus(SummaryLevel.YELLOW, fetchedChannelCount, normalizedReasons);
        }
        if (normalizedReasons.isEmpty()) {
            normalizedReasons.add(I18n.tr("reloadNoChannelsLoaded"));
        }
        return new SummaryStatus(SummaryLevel.BAD, fetchedChannelCount, normalizedReasons);
    }

    private void addIssue(List<String> issues, String issue) {
        if (issues == null || issue == null || issue.isBlank()) {
            return;
        }
        if (!issues.contains(issue)) {
            issues.add(issue);
        }
    }

    private String extractIssueReason(Account account, String message) {
        if (message == null) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.startsWith("Reload failed:")) {
            return I18n.tr("reloadReloadFailed");
        }
        if (trimmed.equals("Handshake failed.") || trimmed.startsWith("Handshake failed for")) {
            return I18n.tr("reloadHandshakeFailed");
        }
        if (trimmed.startsWith("Network error while loading categories")) {
            return I18n.tr("reloadNetworkErrorLoadingCategories");
        }
        if (trimmed.startsWith("Failed to parse channels")) {
            return I18n.tr("reloadFailedToParseChannels");
        }
        if (trimmed.startsWith("Last-resort fetch failed for category")) {
            return I18n.tr("reloadFallbackCategoryFetchFailed");
        }
        if (trimmed.startsWith("No channels returned by get_all_channels")
                || trimmed.startsWith("Global Stalker get_all_channels failed")) {
            return I18n.tr("reloadGlobalLiveTvChannelCallFailed");
        }
        if (trimmed.startsWith("Global Xtreme channel lookup failed")
                || trimmed.startsWith("Global Xtreme channel lookup returned no channels")
                || trimmed.startsWith("Global Xtreme channel lookup returned uncategorized rows only")) {
            return I18n.tr("reloadGlobalXtremeLookupFailed");
        }
        if (trimmed.startsWith("Global VOD category list failed:")) {
            return I18n.tr("reloadModeCategoryListFailed", I18n.tr("categoryTabVideoOnDemand"));
        }
        if (trimmed.startsWith("Global SERIES category list failed:")) {
            return I18n.tr("reloadModeCategoryListFailed", I18n.tr("categoryTabTvSeries"));
        }
        if (trimmed.equals("No channels found in any category. Keeping existing cache.")
                || trimmed.equals("No channels found.")) {
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadNoChannelsFound")
                    : I18n.tr("reloadModeNoChannelsFound", mode);
        }
        if (trimmed.equals("Marked bad and skipped after global call failure.")) {
            return I18n.tr("reloadMarkedBadByUser");
        }
        return null;
    }

    private boolean promptCarryOnAfterGlobalFailure(Account account, String reason) {
        final boolean[] carryOn = {true};
        runOnFxThreadAndWait(() -> {
            ButtonType carryOnButton = new ButtonType(I18n.tr("reloadCarryOn"), ButtonBar.ButtonData.YES);
            ButtonType markBadButton = new ButtonType(I18n.tr("reloadMarkBadAndNext"), ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    I18n.tr("reloadGlobalFailurePrompt", account.getAccountName(), reason),
                    carryOnButton,
                    markBadButton
            );
            alert.setHeaderText(I18n.tr("reloadGlobalCallFailure"));
            if (RootApplication.currentTheme != null) {
                alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
            }
            alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl()
                    ? javafx.geometry.NodeOrientation.RIGHT_TO_LEFT
                    : javafx.geometry.NodeOrientation.LEFT_TO_RIGHT);
            carryOn[0] = alert.showAndWait().orElse(markBadButton) == carryOnButton;
        });
        return carryOn[0];
    }

    private Integer extractFirstNumber(String input) {
        if (input == null) {
            return null;
        }
        Matcher matcher = NUMBER_PATTERN.matcher(input);
        if (!matcher.find()) {
            return null;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String modeCode(Account account) {
        if (account == null || account.getAction() == null) {
            return null;
        }
        switch (account.getAction()) {
            case itv:
                return "ITV";
            case vod:
                return "VOD";
            case series:
                return "SERIES";
            default:
                return null;
        }
    }

    private String modeLabel(Account account) {
        String code = modeCode(account);
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ITV" -> I18n.tr("categoryTabLiveTv");
            case "VOD" -> I18n.tr("categoryTabVideoOnDemand");
            case "SERIES" -> I18n.tr("categoryTabTvSeries");
            default -> null;
        };
    }

    private String summaryLevelLabel(SummaryLevel level) {
        if (level == null) {
            return "";
        }
        return switch (level) {
            case GOOD -> I18n.tr("reloadSummaryLevelGood");
            case YELLOW -> I18n.tr("reloadSummaryLevelYellow");
            case BAD -> I18n.tr("reloadSummaryLevelBad");
        };
    }

    private String shortFailure(String message) {
        if (message == null) {
            return I18n.tr("reloadUnknownError");
        }
        String compact = message.trim();
        if (compact.isEmpty()) {
            return I18n.tr("reloadUnknownError");
        }
        int lineBreak = compact.indexOf('\n');
        if (lineBreak >= 0) {
            compact = compact.substring(0, lineBreak).trim();
        }
        if (compact.length() > 120) {
            compact = compact.substring(0, 117).trim() + "...";
        }
        return compact;
    }

    private void runOnFxThreadAndWait(Runnable runnable) {
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return;
        }
        CountDownLatch latch = new CountDownLatch(1);
        Platform.runLater(() -> {
            try {
                runnable.run();
            } finally {
                latch.countDown();
            }
        });
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class AccountLogPanel {
        private final Account account;
        private final VBox root = new VBox(6);
        private final HBox header = new HBox(8);
        private final Label accountLabel = new Label();
        private final Label statusLabel = new Label();
        private final Label arrowLabel = new Label("▸");
        private final ProgressIndicator runningIndicator = new ProgressIndicator();
        private final VBox logBody = new VBox(4);
        private final List<String> logs = new ArrayList<>();

        private AccountLogPanel(Account account) {
            this.account = account;
            this.accountLabel.setText(getAccountLabel());
            this.accountLabel.setStyle("-fx-font-weight: bold;");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            this.header.setAlignment(Pos.CENTER_LEFT);
            this.header.setPadding(new Insets(8));
            this.header.setStyle("-fx-background-color: derive(-fx-control-inner-background, -3%);"
                    + "-fx-border-color: -fx-box-border;"
                    + "-fx-border-radius: 6;"
                    + "-fx-background-radius: 6;");
            this.runningIndicator.setMaxSize(14, 14);
            this.runningIndicator.setVisible(false);
            this.runningIndicator.managedProperty().bind(this.runningIndicator.visibleProperty());

            this.header.getChildren().addAll(accountLabel, spacer, runningIndicator, statusLabel, arrowLabel);

            this.logBody.setPadding(new Insets(0, 10, 8, 10));
            this.root.getChildren().addAll(header, logBody);
            this.root.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6;");

            this.header.setOnMouseClicked(event -> setExpanded(!logBody.isVisible()));
        }

        private String getAccountLabel() {
            return account.getAccountName() + " (" + account.getType().getDisplay() + ")";
        }

        private VBox getRoot() {
            return root;
        }

        private List<String> getLogs() {
            return logs;
        }

        private void setExpanded(boolean expanded) {
            logBody.setVisible(expanded);
            logBody.setManaged(expanded);
            arrowLabel.setText(expanded ? "▾" : "▸");
        }

        private void appendLog(String line) {
            logs.add(line);
            Label lineLabel = new Label(line);
            lineLabel.setWrapText(true);
            lineLabel.getStyleClass().add("log-text");
            logBody.getChildren().add(lineLabel);
        }

        private void setStatus(AccountRunStatus status, Integer channelCount) {
            switch (status) {
                case QUEUED:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(I18n.tr("autoQueued"));
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-text-base-color;");
                    break;
                case RUNNING:
                    runningIndicator.setVisible(true);
                    statusLabel.setText(I18n.tr("autoRunning"));
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0b79d0;");
                    break;
                case DONE:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(channelCount == null
                            ? I18n.tr("reloadDone")
                            : I18n.tr("reloadDoneWithChannels", channelCount));
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;");
                    break;
                case YELLOW:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(channelCount == null
                            ? I18n.tr("reloadPartial")
                            : I18n.tr("reloadPartialWithChannels", channelCount));
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
                    break;
                case EMPTY:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(I18n.tr("autoEmpty0Channels"));
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
                    break;
                case FAILED:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(I18n.tr("autoFailed2"));
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b91c1c;");
                    break;
                default:
                    runningIndicator.setVisible(false);
                    statusLabel.setText("");
                    break;
            }
        }

        private void setStatus(AccountRunStatus status, Integer current, Integer total) {
            if (status == AccountRunStatus.RUNNING && current != null && total != null) {
                runningIndicator.setVisible(true);
                statusLabel.setText(I18n.tr("autoRunningProgress", current, total));
                statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0b79d0;");
            } else {
                setStatus(status, (Integer) null);
            }
        }
    }

    private static final class SkipAccountReloadException extends RuntimeException {
    }
}
