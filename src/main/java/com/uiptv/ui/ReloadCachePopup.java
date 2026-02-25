package com.uiptv.ui;

import com.uiptv.db.AccountDb;
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
        QUEUED, RUNNING, DONE, FAILED, EMPTY
    }

    private final Stage stage;
    private final VBox accountsVBox = new VBox(5);
    private final VBox logVBox = new VBox(8);
    private final ScrollPane accountsScrollPane = new ScrollPane();
    private final ScrollPane logScrollPane = new ScrollPane(logVBox);
    private final SegmentedProgressBar progressBar = new SegmentedProgressBar();
    private final ProminentButton reloadButton = new ProminentButton("Reload Selected");
    private final ProgressIndicator loadingIndicator = createLoadingIndicator();
    private final CacheService cacheService = new CacheServiceImpl();
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private final Map<String, AccountLogPanel> accountLogPanels = new LinkedHashMap<>();
    private final List<String> runAccountOrder = new ArrayList<>();
    private final List<String> latestSummaryLines = new ArrayList<>();
    private String currentRunningAccountId;

    public static void showPopup(Stage owner) {
        showPopup(owner, null);
    }

    public static void showPopup(Stage owner, List<Account> preselectedAccounts) {
        Stage popupStage = new Stage();
        if (owner != null) {
            popupStage.initOwner(owner);
            popupStage.initModality(Modality.WINDOW_MODAL);
        }
        ReloadCachePopup popup = new ReloadCachePopup(popupStage, preselectedAccounts);
        Scene scene = new Scene(popup, 1368, 720);
        popupStage.setTitle("Reload Accounts Cache");
        popupStage.setScene(scene);
        popupStage.showAndWait();
    }

    public ReloadCachePopup(Stage stage) {
        this(stage, null);
    }

    public ReloadCachePopup(Stage stage, List<Account> preselectedAccounts) {
        this.stage = stage;
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefSize(1368, 720);
        getStylesheets().add(RootApplication.currentTheme);

        accountsVBox.setPadding(new Insets(10));
        List<Account> supportedAccounts = AccountDb.get().getAccounts().stream()
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

        VBox accountColumn = new VBox(10, selectMenu, accountsScrollPane);
        accountColumn.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);

        GridPane mainContent = new GridPane();
        mainContent.setHgap(10);
        mainContent.setMaxWidth(Double.MAX_VALUE);

        ColumnConstraints accountsColumn = new ColumnConstraints();
        accountsColumn.setPercentWidth(35);
        accountsColumn.setFillWidth(true);
        accountsColumn.setHgrow(Priority.ALWAYS);

        ColumnConstraints logsColumn = new ColumnConstraints();
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
        loadingIndicator.managedProperty().bind(loadingIndicator.visibleProperty());

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
        HBox buttonBox = new HBox(10, reloadButton, loadingIndicator, spacer, copyLogButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));

        getChildren().addAll(progressBar, mainContent, buttonBox);

        if (preselectedAccounts != null && !preselectedAccounts.isEmpty()) {
            preselectAccounts(preselectedAccounts);
            if (checkBoxes.stream().anyMatch(CheckBox::isSelected)) {
                Platform.runLater(this::startReloadInBackground);
            }
        }
    }

    private void updateCheckboxes(AccountType type, boolean selected) {
        for (CheckBox cb : checkBoxes) {
            Account acc = (Account) cb.getUserData();
            if (acc.getType() == type) {
                cb.setSelected(selected);
            }
        }
    }

    private ProgressIndicator createLoadingIndicator() {
        ProgressIndicator indicator = new ProgressIndicator();
        indicator.setMaxSize(24, 24);
        indicator.setVisible(false);
        return indicator;
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
            loadingIndicator.setVisible(true);
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
                loadingIndicator.setVisible(false);
            });
            return;
        }

        List<Account> processedAccounts = new ArrayList<>();
        Map<String, AccountRunStatus> finalStatuses = new LinkedHashMap<>();
        int totalSuccessChannels = 0;

        for (int i = 0; i < total; i++) {
            Account account = selectedAccounts.get(i);
            processedAccounts.add(account);
            setRunningAccount(account, i + 1, total);

            boolean success = false;
            boolean failed = false;
            int channelCount = 0;
            try {
                cacheService.reloadCache(account, message -> logMessage(account, message));
                channelCount = cacheService.getChannelCountForAccount(account.getDbId());
                if (channelCount > 0) {
                    success = true;
                    totalSuccessChannels += channelCount;
                } else {
                    logMessage(account, "No channels found.");
                }
            } catch (Exception e) {
                failed = true;
                logMessage(account, "Reload failed: " + shortFailure(e.getMessage()));
            }

            progressBar.updateSegment(i, success);
            AccountRunStatus finalStatus = success
                    ? AccountRunStatus.DONE
                    : (failed ? AccountRunStatus.FAILED : AccountRunStatus.EMPTY);
            finalStatuses.put(account.getDbId(), finalStatus);
            updateAccountStatus(account, finalStatus, channelCount);
        }

        int runTotalSuccessChannels = totalSuccessChannels;
        Platform.runLater(() -> {
            System.out.print("\u0007"); // Beep
            reloadButton.setVisible(true);
            loadingIndicator.setVisible(false);
            appendRunSummary(processedAccounts, finalStatuses, runTotalSuccessChannels);

            List<Account> emptyAccounts = processedAccounts.stream()
                    .filter(a -> cacheService.getChannelCountForAccount(a.getDbId()) == 0)
                    .collect(Collectors.toList());

            if (!emptyAccounts.isEmpty()) {
                showDeleteEmptyAccountsPopup(emptyAccounts);
            }
        });
    }

    private void showDeleteEmptyAccountsPopup(List<Account> emptyAccounts) {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle("Delete Empty Accounts");

        VBox root = new VBox(10);
        root.setPadding(new Insets(20));

        Label warningLabel = new Label("The following accounts have 0 channels. Select the ones you want to delete.");
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

        for (Account account : emptyAccounts) {
            CheckBox cb = new CheckBox(account.getAccountName() + " (" + account.getType().getDisplay() + ")");
            cb.setUserData(account);
            accountsBox.getChildren().add(cb);
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

    private void prepareAccountLogPanels(List<Account> selectedAccounts) {
        runOnFxThreadAndWait(() -> {
            accountLogPanels.clear();
            runAccountOrder.clear();
            latestSummaryLines.clear();
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
        int successCount = 0;
        int failedCount = 0;
        int emptyCount = 0;

        List<String> failedNames = new ArrayList<>();
        List<String> emptyNames = new ArrayList<>();

        for (Account account : processedAccounts) {
            AccountRunStatus status = finalStatuses.get(account.getDbId());
            if (status == AccountRunStatus.DONE) {
                successCount++;
            } else if (status == AccountRunStatus.FAILED) {
                failedCount++;
                failedNames.add(account.getAccountName());
            } else if (status == AccountRunStatus.EMPTY) {
                emptyCount++;
                emptyNames.add(account.getAccountName());
            }
        }

        latestSummaryLines.clear();
        latestSummaryLines.add("Completed: " + processedAccounts.size() + "/" + processedAccounts.size());
        latestSummaryLines.add("Successful: " + successCount);
        latestSummaryLines.add("Failed: " + failedCount);
        latestSummaryLines.add("Empty: " + emptyCount);
        latestSummaryLines.add("Channels loaded: " + totalSuccessChannels);
        if (!failedNames.isEmpty()) {
            latestSummaryLines.add("Failed accounts: " + String.join(", ", failedNames));
        }
        if (!emptyNames.isEmpty()) {
            latestSummaryLines.add("Empty accounts: " + String.join(", ", emptyNames));
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

            if (currentRunningAccountId != null && !currentRunningAccountId.equals(nextAccountId)) {
                AccountLogPanel previousPanel = accountLogPanels.get(currentRunningAccountId);
                if (previousPanel != null) {
                    previousPanel.setExpanded(false);
                }
            }

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
            return mode == null ? "Categories cache miss -> fetching."
                    : mode + " Categories: cache miss, fetching.";
        }
        if (trimmed.startsWith("No cached categories found")) {
            String mode = modeLabel(account);
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
        if (trimmed.startsWith("Last-resort fetch succeeded. Collected")) {
            Integer count = extractFirstNumber(trimmed);
            return count == null ? "Fallback fetch succeeded." : "Fallback fetch succeeded: " + count + " channels.";
        }
        return trimmed;
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
            this.header.getChildren().addAll(accountLabel, spacer, statusLabel, arrowLabel);

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
                    statusLabel.setText("Queued");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: -fx-text-base-color;");
                    break;
                case RUNNING:
                    statusLabel.setText("Running");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0b79d0;");
                    break;
                case DONE:
                    statusLabel.setText(channelCount == null ? "Done" : "Done (" + channelCount + " channels)");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #2e7d32;");
                    break;
                case EMPTY:
                    statusLabel.setText("Empty (0 channels)");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #d97706;");
                    break;
                case FAILED:
                    statusLabel.setText("Failed");
                    statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #b91c1c;");
                    break;
                default:
                    statusLabel.setText("");
                    break;
            }
        }

        private void setStatus(AccountRunStatus status, Integer current, Integer total) {
            if (status == AccountRunStatus.RUNNING && current != null && total != null) {
                statusLabel.setText("Running (" + current + "/" + total + ")");
                statusLabel.setStyle("-fx-font-weight: bold; -fx-text-fill: #0b79d0;");
            } else {
                setStatus(status, (Integer) null);
            }
        }
    }
}
