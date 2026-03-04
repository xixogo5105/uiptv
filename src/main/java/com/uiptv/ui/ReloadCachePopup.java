package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.util.AccountType;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.SegmentedProgressBar;
import javafx.application.Platform;
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
    private final ProminentButton reloadButton = new ProminentButton("Reload Selected");
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
        popupStage.setTitle("Reload Accounts Cache");
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

        MenuButton selectMenu = new MenuButton("Select by types");
        CheckMenuItem allItem = new CheckMenuItem("ALL");
        CheckMenuItem stalkerItem = new CheckMenuItem("Stalker Portal Accounts");
        CheckMenuItem xtremeItem = new CheckMenuItem("Xtreme Account");
        CheckMenuItem m3uLocalItem = new CheckMenuItem("M3U Local Playlist");
        CheckMenuItem m3uRemoteItem = new CheckMenuItem("M3U Remote Playlist");

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

        Button copyLogButton = new Button("Copy Log");
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
                    sb.append("  Summary: ").append(status.level.name())
                            .append(" (channels=").append(status.channelsLoaded).append(")\n");
                    if (!status.reasons.isEmpty()) {
                        sb.append("  Reasons: ").append(String.join(" | ", status.reasons)).append("\n");
                    }
                }
                sb.append("\n");
            }
            if (!latestSummaryLines.isEmpty()) {
                sb.append("Run Summary\n");
                latestSummaryLines.forEach(line -> sb.append("  ").append(line).append("\n"));
                sb.append("\n");
            }
            content.putString(sb.toString());
            clipboard.setContent(content);
        });

        Button closeButton = new Button("Close");
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
                    addIssue(accountIssues, "No channels loaded.");
                }
            } catch (SkipAccountReloadException e) {
                failed = true;
                logMessage(account, "Marked bad and skipped after global call failure.");
                addIssue(accountIssues, "Marked bad by user after global call failure.");
            } catch (Exception e) {
                failed = true;
                logMessage(account, "Reload failed: " + shortFailure(e.getMessage()));
                addIssue(accountIssues, "Exception: " + shortFailure(e.getMessage()));
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
            LogDisplayUI.addLog("Reload run completed.");
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
        popupStage.setTitle("Delete Problematic Accounts");

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label warningLabel = new Label("The following accounts are flagged as BAD or YELLOW. Select the ones you want to delete.");
        warningLabel.setWrapText(true);
        warningLabel.setFont(Font.font("System", FontWeight.BOLD, 14));

        VBox accountsBox = new VBox(5);
        ScrollPane scrollPane = new ScrollPane(accountsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);

        CheckBox selectAll = new CheckBox("Select All");
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
            Label badLabel = new Label("BAD (Red)");
            badLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b91c1c;");
            accountsBox.getChildren().add(badLabel);
            addProblemAccountsToDeleteBox(accountsBox, badAccounts, problematicAccounts);
        }
        if (!yellowAccounts.isEmpty()) {
            Label yellowLabel = new Label("YELLOW (Partially successful)");
            yellowLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
            accountsBox.getChildren().add(yellowLabel);
            addProblemAccountsToDeleteBox(accountsBox, yellowAccounts, problematicAccounts);
        }

        Button deleteButton = new Button("Delete Selected");
        deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteButton.setOnAction(e -> {
            List<Account> toDelete = accountsBox.getChildren().stream()
                    .filter(n -> n instanceof CheckBox && ((CheckBox) n).isSelected())
                    .map(n -> (Account) n.getUserData())
                    .collect(Collectors.toList());

            if (toDelete.isEmpty()) return;

            Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Are you sure you want to delete " + toDelete.size() + " accounts?", ButtonType.YES, ButtonType.NO);
            if (RootApplication.currentTheme != null) {
                alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
            }
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

        Button cancelButton = new Button("Cancel");
        cancelButton.setOnAction(e -> popupStage.close());

        HBox buttons = new HBox(10, deleteButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        root.getChildren().addAll(warningLabel, selectAll, scrollPane, buttons);

        Scene scene = new Scene(root, 500, 500);
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
                    ? "No reason captured."
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
        latestSummaryLines.add("Completed: " + processedAccounts.size() + "/" + processedAccounts.size());
        latestSummaryLines.add("Good: " + successCount);
        latestSummaryLines.add("Yellow (Partial): " + yellowCount);
        latestSummaryLines.add("Bad: " + badCount);
        latestSummaryLines.add("Channels loaded: " + totalSuccessChannels);
        if (!yellowNames.isEmpty()) {
            latestSummaryLines.add("Yellow accounts: " + String.join(", ", yellowNames));
        }
        if (!badNames.isEmpty()) {
            latestSummaryLines.add("Bad accounts: " + String.join(", ", badNames));
        }

        VBox summaryBox = new VBox(4);
        summaryBox.setPadding(new Insets(10));
        summaryBox.setStyle("-fx-background-color: derive(-fx-control-inner-background, -2%);"
                + "-fx-border-color: -fx-box-border;"
                + "-fx-border-radius: 6;"
                + "-fx-background-radius: 6;");

        Label title = new Label("Run Summary");
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
            }
            currentRunningAccountId = nextAccountId;

            if (total > 1) {
                logScrollPane.setVvalue((double) (current - 1) / (double) (total - 1));
            } else {
                logScrollPane.setVvalue(0);
            }
        });
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
            return "Loaded channels from local cache.";
        }
        if (trimmed.startsWith("No fresh cache found for category")) {
            return trimmed.replace("No fresh cache found for category", "Cache miss:")
                    .replace(". Fetching from portal...", " -> fetching.");
        }
        if (trimmed.startsWith("No fresh cached categories found")) {
            String mode = modeLabel(account);
            if ("VOD".equals(mode) || "SERIES".equals(mode)) {
                return "";
            }
            return mode == null ? "Categories cache miss -> fetching."
                    : mode + " Categories: cache miss, fetching.";
        }
        if (trimmed.startsWith("No cached categories found")) {
            String mode = modeLabel(account);
            if ("VOD".equals(mode) || "SERIES".equals(mode)) {
                return "";
            }
            return mode == null ? "No cached categories -> fetching."
                    : mode + " Categories: no cache, fetching.";
        }
        if (trimmed.startsWith("Fetching categories from Xtreme API")) {
            String mode = modeLabel(account);
            return mode == null ? "Fetching categories (Xtreme API)..."
                    : "Fetching " + mode + " categories (Xtreme API)...";
        }
        if (trimmed.startsWith("Fetching categories from Stalker Portal")) {
            String mode = modeLabel(account);
            return mode == null ? "Fetching categories (Stalker Portal)..."
                    : "Fetching " + mode + " categories (Stalker Portal)...";
        }
        if (trimmed.startsWith("Found Categories")) {
            Integer count = extractFirstNumber(trimmed);
            if (count != null) {
                String mode = modeLabel(account);
                if (mode == null) {
                    mode = "ITV";
                }
                return mode + " Categories: " + count;
            }
            return trimmed.replace("Found Categories", "Categories:");
        }
        if (trimmed.startsWith("Found Channels")) {
            Integer count = extractFirstNumber(trimmed);
            if (count != null) {
                String mode = modeLabel(account);
                if (mode == null) {
                    mode = "ITV";
                }
                return mode + " Channels: " + count;
            }
            return trimmed.replace("Found Channels", "Channels:");
        }
        if (trimmed.endsWith("saved Successfully ✓")) {
            return "Saved.";
        }
        if (trimmed.startsWith("Fetching page")) {
            return trimmed.replace("Fetching page", "Page")
                    .replace(" for category ", " (category ")
                    .replace("...", ")");
        }
        if (trimmed.startsWith("Fetched")) {
            return trimmed.replace(" channels from page ", " channels (page ")
                    .replace(".", ")");
        }
        if (trimmed.startsWith("Page ") && trimmed.endsWith(" returned no channels.")) {
            return trimmed.replace(" returned no channels.", " -> no channels.");
        }
        if (trimmed.startsWith("Saved ")) {
            if (trimmed.contains(" to local VOD/Series cache.")) {
                Integer count = extractFirstNumber(trimmed);
                if (count != null) {
                    String mode = modeLabel(account);
                    if (mode != null) {
                        return mode + " Categories: " + count;
                    }
                    return "Categories saved: " + count;
                }
            }
            return trimmed.replace(" to local cache.", " to cache.");
        }
        if (trimmed.equals("No categories found. Keeping existing cache.")) {
            String mode = modeLabel(account);
            return mode == null ? "No categories found. Cache kept."
                    : mode + " Categories: none found.";
        }
        if (trimmed.equals("No channels found in any category. Keeping existing cache.")) {
            String mode = modeLabel(account);
            if (mode == null) {
                mode = "ITV";
            }
            return mode + " Channels: none found.";
        }
        if (trimmed.startsWith("Reload failed:")) {
            return "Failed: " + shortFailure(trimmed.substring("Reload failed:".length()).trim());
        }
        if (trimmed.equals("Handshake failed.") || trimmed.startsWith("Handshake failed for")) {
            return "Failed: handshake.";
        }
        if (trimmed.startsWith("Network error while loading categories")) {
            return "Failed: network error.";
        }
        if (trimmed.startsWith("Failed to parse channels")) {
            return "Failed: channel parse error.";
        }
        if (trimmed.startsWith("Last-resort fetch failed for category")) {
            return "Failed: fallback category fetch.";
        }
        if (trimmed.startsWith("Global Xtreme channel lookup failed")) {
            return "Global lookup failed, using category fetch.";
        }
        if (trimmed.startsWith("Global Xtreme channel lookup returned no channels")) {
            return "Global lookup empty, using category fetch.";
        }
        if (trimmed.startsWith("Global Xtreme channel lookup returned uncategorized rows only")) {
            return "Global lookup uncategorized, using category fetch.";
        }
        if (trimmed.startsWith("No channels returned by get_all_channels")) {
            return "Global channel list empty, trying fallback.";
        }
        if (trimmed.startsWith("Global Stalker get_all_channels failed")) {
            return "Global channel list failed, trying fallback.";
        }
        if (trimmed.startsWith("Last-resort fetch succeeded. Collected")) {
            Integer count = extractFirstNumber(trimmed);
            return count == null ? "Fallback fetch succeeded." : "Fallback fetch succeeded: " + count + " channels.";
        }
        if (trimmed.startsWith("Global VOD category list failed:")) {
            return "VOD category list failed.";
        }
        if (trimmed.startsWith("Global SERIES category list failed:")) {
            return "SERIES category list failed.";
        }
        if (trimmed.equals("Marked bad and skipped after global call failure.")) {
            return "Marked bad and moved to next account.";
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
            return "ITV global call failed for Xtreme.";
        }
        if (trimmed.startsWith("Global Stalker get_all_channels failed")) {
            return "ITV get_all_channels failed for Stalker Portal.";
        }
        if (trimmed.startsWith("Global VOD category list failed:")) {
            return "VOD category list call failed.";
        }
        if (trimmed.startsWith("Global SERIES category list failed:")) {
            return "SERIES category list call failed.";
        }
        if (trimmed.startsWith("Network error while loading categories")) {
            String mode = modeLabel(account);
            if ("VOD".equals(mode) || "SERIES".equals(mode)) {
                return mode + " category list call failed.";
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
            normalizedReasons.add("No channels loaded.");
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
            return "Reload failed.";
        }
        if (trimmed.equals("Handshake failed.") || trimmed.startsWith("Handshake failed for")) {
            return "Handshake failed.";
        }
        if (trimmed.startsWith("Network error while loading categories")) {
            return "Network error while loading categories.";
        }
        if (trimmed.startsWith("Failed to parse channels")) {
            return "Failed to parse channels.";
        }
        if (trimmed.startsWith("Last-resort fetch failed for category")) {
            return "Fallback category fetch failed.";
        }
        if (trimmed.startsWith("No channels returned by get_all_channels")
                || trimmed.startsWith("Global Stalker get_all_channels failed")) {
            return "Global ITV channel call failed.";
        }
        if (trimmed.startsWith("Global Xtreme channel lookup failed")
                || trimmed.startsWith("Global Xtreme channel lookup returned no channels")
                || trimmed.startsWith("Global Xtreme channel lookup returned uncategorized rows only")) {
            return "Global Xtreme lookup failed.";
        }
        if (trimmed.startsWith("Global VOD category list failed:")) {
            return "VOD category list failed.";
        }
        if (trimmed.startsWith("Global SERIES category list failed:")) {
            return "SERIES category list failed.";
        }
        if (trimmed.equals("No channels found in any category. Keeping existing cache.")
                || trimmed.equals("No channels found.")) {
            String mode = modeLabel(account);
            return (mode == null ? "No channels found." : mode + " no channels found.");
        }
        if (trimmed.equals("Marked bad and skipped after global call failure.")) {
            return "Marked bad by user.";
        }
        return null;
    }

    private boolean promptCarryOnAfterGlobalFailure(Account account, String reason) {
        final boolean[] carryOn = {true};
        runOnFxThreadAndWait(() -> {
            ButtonType carryOnButton = new ButtonType("Carry On", ButtonBar.ButtonData.YES);
            ButtonType markBadButton = new ButtonType("Mark Bad & Next", ButtonBar.ButtonData.CANCEL_CLOSE);
            Alert alert = new Alert(
                    Alert.AlertType.CONFIRMATION,
                    "Account: \"" + account.getAccountName() + "\"\n"
                            + reason + "\n\nDo you want to continue this account run?",
                    carryOnButton,
                    markBadButton
            );
            alert.setHeaderText("Global Call Failure");
            if (RootApplication.currentTheme != null) {
                alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
            }
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

    private String modeLabel(Account account) {
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

    private String shortFailure(String message) {
        if (message == null) {
            return "unknown error.";
        }
        String compact = message.trim();
        if (compact.isEmpty()) {
            return "unknown error.";
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
                    statusLabel.setText("Queued");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-text-base-color;");
                    break;
                case RUNNING:
                    runningIndicator.setVisible(true);
                    statusLabel.setText("Running");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0b79d0;");
                    break;
                case DONE:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(channelCount == null ? "Done" : "Done (" + channelCount + " channels)");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;");
                    break;
                case YELLOW:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(channelCount == null ? "Partial" : "Partial (" + channelCount + " channels)");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
                    break;
                case EMPTY:
                    runningIndicator.setVisible(false);
                    statusLabel.setText("Empty (0 channels)");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
                    break;
                case FAILED:
                    runningIndicator.setVisible(false);
                    statusLabel.setText("Failed");
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
                statusLabel.setText("Running (" + current + "/" + total + ")");
                statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0b79d0;");
            } else {
                setStatus(status, (Integer) null);
            }
        }
    }

    private static final class SkipAccountReloadException extends RuntimeException {
    }
}
