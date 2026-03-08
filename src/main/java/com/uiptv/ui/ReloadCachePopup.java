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

import static com.uiptv.model.Account.CACHE_SUPPORTED;

public class ReloadCachePopup extends VBox {
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final String GLOBAL_SERIES_CATEGORY_LIST_FAILED = "Global SERIES category list failed:";
    private static final String GLOBAL_STALKER_GET_ALL_CHANNELS_FAILED = "Global Stalker get_all_channels failed";
    private static final String GLOBAL_VOD_CATEGORY_LIST_FAILED = "Global VOD category list failed:";
    private static final String GLOBAL_XTREME_CHANNEL_LOOKUP_FAILED = "Global Xtreme channel lookup failed";
    private static final String LOG_MARKED_BAD_AND_SKIPPED = "Marked bad and skipped after global call failure.";
    private static final String LOG_NETWORK_ERROR_LOADING_CATEGORIES = "Network error while loading categories";
    private static final String LOG_NO_CHANNELS_FOUND = "No channels found.";
    private static final String LOG_RELOAD_FAILED_PREFIX = "Reload failed:";
    private static final String MODE_SERIES = "SERIES";
    private static final String MODE_VOD = "VOD";
    private static final String STYLE_YELLOW_LABEL = "-fx-font-weight: bold; -fx-text-fill: #d97706;";
    private static final String TR_CATEGORY_TAB_SERIES = "categoryTabTvSeries";
    private static final String TR_CATEGORY_TAB_VOD = "categoryTabVideoOnDemand";
    private static final String TR_RELOAD_MODE_CATEGORY_LIST_CALL_FAILED = "reloadModeCategoryListCallFailed";
    private static final String TR_RELOAD_MODE_CATEGORY_LIST_FAILED = "reloadModeCategoryListFailed";
    private static final String TR_RELOAD_NO_CHANNELS_LOADED = "reloadNoChannelsLoaded";

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
        initializeLayout();
        List<Account> supportedAccounts = loadSupportedAccounts();
        populateAccountCheckboxes(supportedAccounts);
        MenuButton selectMenu = buildSelectMenu();
        configureScrollPanes();
        GridPane mainContent = buildMainContent(selectMenu);
        HBox buttonBox = buildButtonBox();
        getChildren().addAll(progressBar, mainContent, buttonBox);
        registerStageCloseListener();

        if (preselectedAccounts != null && !preselectedAccounts.isEmpty()) {
            preselectAccounts(preselectedAccounts);
            if (checkBoxes.stream().anyMatch(CheckBox::isSelected)) {
                hideAccountSelectionColumn();
                Platform.runLater(this::startReloadInBackground);
            }
        }
    }

    private void initializeLayout() {
        setSpacing(10);
        setPadding(new Insets(10));
        setPrefSize(1368, 720);
        getStylesheets().add(RootApplication.currentTheme);
        accountsVBox.setPadding(new Insets(10));
        logVBox.setPadding(new Insets(5));
    }

    private List<Account> loadSupportedAccounts() {
        List<Account> supportedAccounts = accountService.getAll().values().stream()
                .filter(account -> CACHE_SUPPORTED.contains(account.getType()))
                .toList();
        supportedAccounts.sort(Comparator.comparing(account -> accountTypeOrder().getOrDefault(account.getType(), Integer.MAX_VALUE)));
        return supportedAccounts;
    }

    private EnumMap<AccountType, Integer> accountTypeOrder() {
        EnumMap<AccountType, Integer> order = new EnumMap<>(AccountType.class);
        order.put(AccountType.STALKER_PORTAL, 1);
        order.put(AccountType.XTREME_API, 2);
        order.put(AccountType.M3U8_LOCAL, 3);
        order.put(AccountType.M3U8_URL, 4);
        return order;
    }

    private void populateAccountCheckboxes(List<Account> supportedAccounts) {
        for (int i = 0; i < supportedAccounts.size(); i++) {
            Account account = supportedAccounts.get(i);
            CheckBox accountCheckBox = createAccountCheckBox(account, i);
            accountsVBox.getChildren().add(accountCheckBox);
            checkBoxes.add(accountCheckBox);
        }
    }

    private CheckBox createAccountCheckBox(Account account, int index) {
        CheckBox accountCheckBox = new CheckBox(account.getAccountName());
        accountCheckBox.setUserData(account);
        accountCheckBox.setMaxWidth(Double.MAX_VALUE);
        accountCheckBox.setPadding(new Insets(5));
        accountCheckBox.setStyle(index % 2 == 0
                ? "-fx-background-color: derive(-fx-control-inner-background, -2%);"
                : "-fx-background-color: -fx-control-inner-background;");
        return accountCheckBox;
    }

    private MenuButton buildSelectMenu() {
        MenuButton selectMenu = new MenuButton(I18n.tr("autoSelectByTypes"));
        CheckMenuItem allItem = new CheckMenuItem(I18n.tr("commonAll"));
        CheckMenuItem stalkerItem = new CheckMenuItem(I18n.tr("reloadStalkerPortalAccounts"));
        CheckMenuItem xtremeItem = new CheckMenuItem(I18n.tr("reloadXtremeAccount"));
        CheckMenuItem m3uLocalItem = new CheckMenuItem(I18n.tr("reloadM3uLocalPlaylist"));
        CheckMenuItem m3uRemoteItem = new CheckMenuItem(I18n.tr("reloadM3uRemotePlaylist"));
        configureSelectMenuHandlers(allItem, stalkerItem, xtremeItem, m3uLocalItem, m3uRemoteItem);
        selectMenu.setPrefWidth(200);
        selectMenu.getItems().addAll(allItem, new SeparatorMenuItem(), stalkerItem, xtremeItem, m3uLocalItem, m3uRemoteItem);
        return selectMenu;
    }

    private void configureSelectMenuHandlers(CheckMenuItem allItem, CheckMenuItem stalkerItem, CheckMenuItem xtremeItem,
                                             CheckMenuItem m3uLocalItem, CheckMenuItem m3uRemoteItem) {
        allItem.setOnAction(e -> setAllSelectionStates(allItem.isSelected(), stalkerItem, xtremeItem, m3uLocalItem, m3uRemoteItem));
        stalkerItem.setOnAction(e -> updateCheckboxes(AccountType.STALKER_PORTAL, stalkerItem.isSelected()));
        xtremeItem.setOnAction(e -> updateCheckboxes(AccountType.XTREME_API, xtremeItem.isSelected()));
        m3uLocalItem.setOnAction(e -> updateCheckboxes(AccountType.M3U8_LOCAL, m3uLocalItem.isSelected()));
        m3uRemoteItem.setOnAction(e -> updateCheckboxes(AccountType.M3U8_URL, m3uRemoteItem.isSelected()));
    }

    private void setAllSelectionStates(boolean selected, CheckMenuItem stalkerItem, CheckMenuItem xtremeItem,
                                       CheckMenuItem m3uLocalItem, CheckMenuItem m3uRemoteItem) {
        checkBoxes.forEach(cb -> cb.setSelected(selected));
        stalkerItem.setSelected(selected);
        xtremeItem.setSelected(selected);
        m3uLocalItem.setSelected(selected);
        m3uRemoteItem.setSelected(selected);
    }

    private void configureScrollPanes() {
        accountsScrollPane.setContent(accountsVBox);
        accountsScrollPane.setFitToWidth(true);
        accountsScrollPane.setMinHeight(250);
        accountsScrollPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);
        logScrollPane.setFitToWidth(true);
        logScrollPane.setMinHeight(250);
        logScrollPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(logScrollPane, Priority.ALWAYS);
    }

    private GridPane buildMainContent(MenuButton selectMenu) {
        accountColumn = new VBox(10, selectMenu, accountsScrollPane);
        accountColumn.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);
        GridPane mainContent = new GridPane();
        mainContent.setHgap(10);
        mainContent.setMaxWidth(Double.MAX_VALUE);
        accountsColumn = createContentColumn(35);
        logsColumn = createContentColumn(65);
        RowConstraints contentRow = new RowConstraints();
        contentRow.setVgrow(Priority.ALWAYS);
        contentRow.setFillHeight(true);
        mainContent.getColumnConstraints().addAll(accountsColumn, logsColumn);
        mainContent.getRowConstraints().add(contentRow);
        mainContent.add(accountColumn, 0, 0);
        mainContent.add(logScrollPane, 1, 0);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        return mainContent;
    }

    private ColumnConstraints createContentColumn(double widthPercent) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(widthPercent);
        column.setFillWidth(true);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private HBox buildButtonBox() {
        reloadButton.setOnAction(event -> startReloadInBackground());
        reloadButton.managedProperty().bind(reloadButton.visibleProperty());
        Button copyLogButton = new Button(I18n.tr("autoCopyLog"));
        copyLogButton.setOnAction(event -> copyLogsToClipboard());
        Button closeButton = new Button(I18n.tr("autoClose"));
        closeButton.setOnAction(event -> stage.close());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonBox = new HBox(10, reloadButton, spacer, copyLogButton, closeButton);
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setPadding(new Insets(10, 0, 0, 0));
        return buttonBox;
    }

    private void copyLogsToClipboard() {
        final Clipboard clipboard = Clipboard.getSystemClipboard();
        final ClipboardContent content = new ClipboardContent();
        content.putString(buildLogsClipboardText());
        clipboard.setContent(content);
    }

    private String buildLogsClipboardText() {
        StringBuilder sb = new StringBuilder();
        for (String accountId : runAccountOrder) {
            appendAccountLogSummary(sb, accountId);
        }
        if (!latestSummaryLines.isEmpty()) {
            sb.append(I18n.tr("autoRunSummary")).append("\n");
            latestSummaryLines.forEach(line -> sb.append("  ").append(line).append("\n"));
            sb.append("\n");
        }
        return sb.toString();
    }

    private void appendAccountLogSummary(StringBuilder sb, String accountId) {
        AccountLogPanel panel = accountLogPanels.get(accountId);
        if (panel == null) {
            return;
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
                sb.append("  ").append(I18n.tr("reloadReasonsLabel")).append(": ")
                        .append(String.join(" | ", status.reasons)).append("\n");
            }
        }
        sb.append("\n");
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
        List<Account> selectedAccounts = checkBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(checkBox -> (Account) checkBox.getUserData())
                .toList();
        prepareReloadRun(selectedAccounts);
        if (selectedAccounts.isEmpty()) {
            showReloadButton();
            return;
        }
        List<Account> processedAccounts = new ArrayList<>();
        Map<String, AccountRunStatus> finalStatuses = new LinkedHashMap<>();
        Map<String, SummaryStatus> summaryStatusByAccountId = new LinkedHashMap<>();
        int totalFetchedChannels = 0;
        for (int i = 0; i < selectedAccounts.size(); i++) {
            Account account = selectedAccounts.get(i);
            processedAccounts.add(account);
            setRunningAccount(account, i + 1, selectedAccounts.size());
            AccountReloadResult result = reloadSingleAccount(account);
            totalFetchedChannels += result.countedChannels;
            SummaryStatus summaryStatus = buildSummaryStatus(result.availableChannelCount, result.failed, result.accountIssues);
            summaryStatusByAccountId.put(account.getDbId(), summaryStatus);
            progressBar.updateSegment(i, segmentStatus(summaryStatus));
            AccountRunStatus finalStatus = finalAccountRunStatus(summaryStatus, result.availableChannelCount, result.failed);
            finalStatuses.put(account.getDbId(), finalStatus);
            updateAccountStatus(account, finalStatus, result.availableChannelCount);
        }
        finishReloadRun(processedAccounts, finalStatuses, summaryStatusByAccountId, totalFetchedChannels);
    }

    private void prepareReloadRun(List<Account> selectedAccounts) {
        Platform.runLater(() -> reloadButton.setVisible(false));
        progressBar.setTotal(selectedAccounts.size());
        prepareAccountLogPanels(selectedAccounts);
    }

    private void showReloadButton() {
        Platform.runLater(() -> reloadButton.setVisible(true));
    }

    private AccountReloadResult reloadSingleAccount(Account account) {
        boolean failed = false;
        int fetchedChannelCount = 0;
        List<String> accountIssues = new ArrayList<>();
        final boolean[] globalFailurePrompted = {false};
        try {
            cacheService.reloadCache(account, message -> handleReloadLogMessage(account, message, accountIssues, globalFailurePrompted));
            fetchedChannelCount = runOutcomeTracker.getFetchedChannels(account.getDbId());
            if (fetchedChannelCount <= 0) {
                logMessage(account, LOG_NO_CHANNELS_FOUND);
                addIssue(accountIssues, I18n.tr(TR_RELOAD_NO_CHANNELS_LOADED));
            }
        } catch (SkipAccountReloadException e) {
            failed = true;
            logMessage(account, LOG_MARKED_BAD_AND_SKIPPED);
            addIssue(accountIssues, I18n.tr("reloadMarkedBadByUser"));
        } catch (Exception e) {
            failed = true;
            logMessage(account, LOG_RELOAD_FAILED_PREFIX + " " + shortFailure(e.getMessage()));
            addIssue(accountIssues, I18n.tr("reloadFailedReason", shortFailure(e.getMessage())));
        }
        boolean criticalFailure = runOutcomeTracker.hasCriticalFailure(account.getDbId());
        int countedChannels = !criticalFailure && fetchedChannelCount > 0 ? fetchedChannelCount : 0;
        int existingChannelCount = cacheService.getChannelCountForAccount(account.getDbId());
        int availableChannelCount = Math.max(fetchedChannelCount, existingChannelCount);
        return new AccountReloadResult(failed || criticalFailure, countedChannels, availableChannelCount, accountIssues);
    }

    private void handleReloadLogMessage(Account account, String message, List<String> accountIssues, boolean[] globalFailurePrompted) {
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
        if (!promptCarryOnAfterGlobalFailure(account, failureReason)) {
            throw new SkipAccountReloadException();
        }
    }

    private SegmentedProgressBar.SegmentStatus segmentStatus(SummaryStatus summaryStatus) {
        return switch (summaryStatus.level) {
            case GOOD -> SegmentedProgressBar.SegmentStatus.SUCCESS;
            case YELLOW -> SegmentedProgressBar.SegmentStatus.WARNING;
            case BAD -> SegmentedProgressBar.SegmentStatus.FAILURE;
        };
    }

    private AccountRunStatus finalAccountRunStatus(SummaryStatus summaryStatus, int availableChannelCount, boolean failed) {
        AccountRunStatus badStatus;
        if (availableChannelCount > 0) {
            badStatus = AccountRunStatus.YELLOW;
        } else if (failed) {
            badStatus = AccountRunStatus.FAILED;
        } else {
            badStatus = AccountRunStatus.EMPTY;
        }
        return switch (summaryStatus.level) {
            case GOOD -> AccountRunStatus.DONE;
            case YELLOW -> AccountRunStatus.YELLOW;
            case BAD -> badStatus;
        };
    }

    private void finishReloadRun(List<Account> processedAccounts, Map<String, AccountRunStatus> finalStatuses,
                                 Map<String, SummaryStatus> summaryStatusByAccountId, int totalFetchedChannels) {
        Platform.runLater(() -> {
            com.uiptv.util.AppLog.addLog("Reload run completed.");
            reloadButton.setVisible(true);
            latestAccountSummaries.clear();
            latestAccountSummaries.putAll(summaryStatusByAccountId);
            appendRunSummary(processedAccounts, finalStatuses, totalFetchedChannels);
            Map<String, SummaryStatus> problematicAccounts = collectProblematicAccounts(processedAccounts, summaryStatusByAccountId);
            if (!problematicAccounts.isEmpty()) {
                showDeleteProblemAccountsPopup(processedAccounts, problematicAccounts);
            }
        });
    }

    private Map<String, SummaryStatus> collectProblematicAccounts(List<Account> processedAccounts,
                                                                  Map<String, SummaryStatus> summaryStatusByAccountId) {
        Map<String, SummaryStatus> problematicAccounts = new LinkedHashMap<>();
        for (Account account : processedAccounts) {
            SummaryStatus status = summaryStatusByAccountId.get(account.getDbId());
            if (status != null && status.level != SummaryLevel.GOOD) {
                problematicAccounts.put(account.getDbId(), status);
            }
        }
        return problematicAccounts;
    }

    private static final class AccountReloadResult {
        private final boolean failed;
        private final int countedChannels;
        private final int availableChannelCount;
        private final List<String> accountIssues;

        private AccountReloadResult(boolean failed, int countedChannels, int availableChannelCount, List<String> accountIssues) {
            this.failed = failed;
            this.countedChannels = countedChannels;
            this.availableChannelCount = availableChannelCount;
            this.accountIssues = accountIssues;
        }
    }

    private void showDeleteProblemAccountsPopup(List<Account> processedAccounts, Map<String, SummaryStatus> problematicAccounts) {
        Stage popupStage = createProblemAccountsStage();
        VBox accountsBox = new VBox(5);
        VBox root = buildProblemAccountsPopupRoot(processedAccounts, problematicAccounts, popupStage, accountsBox);
        popupStage.setScene(buildProblemAccountsScene(root));
        popupStage.show();
    }

    private Stage createProblemAccountsStage() {
        Stage popupStage = new Stage();
        popupStage.initModality(Modality.APPLICATION_MODAL);
        popupStage.setTitle(I18n.tr("autoDeleteProblematicAccounts"));
        return popupStage;
    }

    private VBox buildProblemAccountsPopupRoot(List<Account> processedAccounts, Map<String, SummaryStatus> problematicAccounts,
                                               Stage popupStage, VBox accountsBox) {
        VBox root = new VBox(10);
        root.setPadding(new Insets(20));
        root.getChildren().addAll(
                createProblemAccountsWarningLabel(),
                createSelectAllCheckBox(accountsBox),
                buildProblemAccountsScrollPane(processedAccounts, problematicAccounts, accountsBox),
                buildProblemAccountsButtons(popupStage, accountsBox)
        );
        return root;
    }

    private Label createProblemAccountsWarningLabel() {
        Label warningLabel = new Label(I18n.tr("autoTheFollowingAccountsAreFlaggedAsBADOrYELLOWSelectTheOnesYouWantToDelete"));
        warningLabel.setWrapText(true);
        warningLabel.setFont(Font.font("System", FontWeight.BOLD, 14));
        return warningLabel;
    }

    private CheckBox createSelectAllCheckBox(VBox accountsBox) {
        CheckBox selectAll = new CheckBox(I18n.tr("autoSelectAll"));
        selectAll.setOnAction(e -> accountsBox.getChildren().forEach(node -> {
            if (node instanceof CheckBox checkBox) {
                checkBox.setSelected(selectAll.isSelected());
            }
        }));
        return selectAll;
    }

    private ScrollPane buildProblemAccountsScrollPane(List<Account> processedAccounts, Map<String, SummaryStatus> problematicAccounts,
                                                      VBox accountsBox) {
        populateProblemAccountsBox(accountsBox, processedAccounts, problematicAccounts);
        ScrollPane scrollPane = new ScrollPane(accountsBox);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(300);
        return scrollPane;
    }

    private void populateProblemAccountsBox(VBox accountsBox, List<Account> processedAccounts,
                                            Map<String, SummaryStatus> problematicAccounts) {
        addProblemAccountsSection(accountsBox, processedAccounts, problematicAccounts, SummaryLevel.BAD,
                I18n.tr("autoBadRed"), "-fx-font-weight: bold; -fx-text-fill: #b91c1c;");
        addProblemAccountsSection(accountsBox, processedAccounts, problematicAccounts, SummaryLevel.YELLOW,
                I18n.tr("autoYellowPartiallySuccessful"), STYLE_YELLOW_LABEL);
    }

    private void addProblemAccountsSection(VBox accountsBox, List<Account> processedAccounts,
                                           Map<String, SummaryStatus> problematicAccounts, SummaryLevel level,
                                           String title, String style) {
        List<Account> accounts = findProblemAccounts(processedAccounts, problematicAccounts, level);
        if (accounts.isEmpty()) {
            return;
        }
        Label label = new Label(title);
        label.setStyle(style);
        accountsBox.getChildren().add(label);
        addProblemAccountsToDeleteBox(accountsBox, accounts, problematicAccounts);
    }

    private List<Account> findProblemAccounts(List<Account> processedAccounts, Map<String, SummaryStatus> problematicAccounts,
                                              SummaryLevel level) {
        return processedAccounts.stream()
                .filter(a -> problematicAccounts.containsKey(a.getDbId()))
                .filter(a -> problematicAccounts.get(a.getDbId()).level == level)
                .toList();
    }

    private HBox buildProblemAccountsButtons(Stage popupStage, VBox accountsBox) {
        Button deleteButton = new Button(I18n.tr("autoDeleteSelected"));
        deleteButton.setStyle("-fx-background-color: #ff4444; -fx-text-fill: white; -fx-font-weight: bold;");
        deleteButton.setOnAction(e -> deleteSelectedProblemAccounts(popupStage, accountsBox));

        Button cancelButton = new Button(I18n.tr("autoCancel"));
        cancelButton.setOnAction(e -> popupStage.close());

        HBox buttons = new HBox(10, deleteButton, cancelButton);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        return buttons;
    }

    private void deleteSelectedProblemAccounts(Stage popupStage, VBox accountsBox) {
        List<Account> toDelete = selectedProblemAccounts(accountsBox);
        if (toDelete.isEmpty()) {
            return;
        }
        confirmDeleteProblemAccounts(toDelete).ifPresent(response -> {
            if (response == ButtonType.YES) {
                deleteAccountsAndRefresh(toDelete);
                popupStage.close();
            }
        });
    }

    private List<Account> selectedProblemAccounts(VBox accountsBox) {
        return accountsBox.getChildren().stream()
                .filter(n -> n instanceof CheckBox checkbox && checkbox.isSelected())
                .map(n -> (Account) n.getUserData())
                .toList();
    }

    private java.util.Optional<ButtonType> confirmDeleteProblemAccounts(List<Account> toDelete) {
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
        return alert.showAndWait();
    }

    private void deleteAccountsAndRefresh(List<Account> toDelete) {
        toDelete.forEach(a -> AccountService.getInstance().delete(a.getDbId()));
        if (onAccountsDeleted != null) {
            onAccountsDeleted.run();
        }
    }

    private Scene buildProblemAccountsScene(VBox root) {
        Scene scene = new Scene(root, 500, 500);
        I18n.applySceneOrientation(scene);
        if (RootApplication.currentTheme != null) {
            scene.getStylesheets().add(RootApplication.currentTheme);
        }
        return scene;
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
        RunSummary summary = buildRunSummary(processedAccounts, finalStatuses);
        latestSummaryLines.clear();
        latestSummaryLines.add(I18n.tr("reloadSummaryCompleted", processedAccounts.size(), processedAccounts.size()));
        latestSummaryLines.add(I18n.tr("reloadSummaryGood", summary.successCount));
        latestSummaryLines.add(I18n.tr("reloadSummaryYellow", summary.yellowCount));
        latestSummaryLines.add(I18n.tr("reloadSummaryBad", summary.badCount));
        latestSummaryLines.add(I18n.tr("reloadSummaryChannelsLoaded", totalSuccessChannels));
        if (!summary.yellowNames.isEmpty()) {
            latestSummaryLines.add(I18n.tr("reloadSummaryYellowAccounts", String.join(", ", summary.yellowNames)));
        }
        if (!summary.badNames.isEmpty()) {
            latestSummaryLines.add(I18n.tr("reloadSummaryBadAccounts", String.join(", ", summary.badNames)));
        }
        VBox summaryBox = buildRunSummaryBox();
        logVBox.getChildren().add(new Separator());
        logVBox.getChildren().add(summaryBox);
        logScrollPane.setVvalue(1.0);
    }

    private RunSummary buildRunSummary(List<Account> processedAccounts, Map<String, AccountRunStatus> finalStatuses) {
        RunSummary summary = new RunSummary();
        for (Account account : processedAccounts) {
            applyAccountSummary(summary, account, finalStatuses.get(account.getDbId()));
        }
        return summary;
    }

    private void applyAccountSummary(RunSummary summary, Account account, AccountRunStatus fallbackStatus) {
        SummaryStatus accountSummary = latestAccountSummaries.get(account.getDbId());
        if (accountSummary == null) {
            applyFallbackRunStatus(summary, account, fallbackStatus);
            return;
        }
        if (accountSummary.level == SummaryLevel.GOOD) {
            summary.successCount++;
            return;
        }
        if (accountSummary.level == SummaryLevel.YELLOW) {
            summary.yellowCount++;
            summary.yellowNames.add(account.getAccountName());
            return;
        }
        summary.badCount++;
        summary.badNames.add(account.getAccountName());
    }

    private void applyFallbackRunStatus(RunSummary summary, Account account, AccountRunStatus fallbackStatus) {
        if (fallbackStatus == AccountRunStatus.DONE) {
            summary.successCount++;
            return;
        }
        summary.badCount++;
        summary.badNames.add(account.getAccountName());
    }

    private VBox buildRunSummaryBox() {
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
        return summaryBox;
    }

    private static final class RunSummary {
        private int successCount;
        private int yellowCount;
        private int badCount;
        private final List<String> yellowNames = new ArrayList<>();
        private final List<String> badNames = new ArrayList<>();
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
        double vValue = Math.clamp(targetY / maxScroll, 0.0, 1.0);
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
        if (compact.isBlank()) {
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
        String translated = compactCategoryLog(account, trimmed);
        if (translated != null) {
            return translated;
        }
        translated = compactCountLog(account, trimmed);
        if (translated != null) {
            return translated;
        }
        translated = compactFailureLog(trimmed);
        if (translated != null) {
            return translated;
        }
        translated = compactGlobalFallbackLog(account, trimmed);
        if (translated != null) {
            return translated;
        }
        return trimmed;
    }

    private String compactCategoryLog(Account account, String trimmed) {
        if (isLoadedChannelsFromCacheLog(trimmed)) {
            return I18n.tr("reloadLoadedChannelsFromLocalCache");
        }
        if (isFreshCacheMissLog(trimmed)) {
            String category = trimmed.replace("No fresh cache found for category", "")
                    .replace(". Fetching from portal...", "")
                    .trim();
            return I18n.tr("reloadCacheMissFetchingCategory", category);
        }
        if (isFreshCategoriesCacheMissLog(trimmed)) {
            String modeCode = modeCode(account);
            if ("VOD".equals(modeCode) || MODE_SERIES.equals(modeCode)) {
                return "";
            }
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadCategoriesCacheMissFetching")
                    : I18n.tr("reloadModeCategoriesCacheMissFetching", mode);
        }
        if (isNoCachedCategoriesLog(trimmed)) {
            String modeCode = modeCode(account);
            if ("VOD".equals(modeCode) || MODE_SERIES.equals(modeCode)) {
                return "";
            }
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadNoCachedCategoriesFetching")
                    : I18n.tr("reloadModeNoCachedCategoriesFetching", mode);
        }
        String provider = resolveCategoryFetchProvider(trimmed);
        if (provider != null) {
            return translateProviderCategoryFetch(account, provider);
        }
        return null;
    }

    private String compactCountLog(Account account, String trimmed) {
        String countTranslation = translateFoundCountLog(account, trimmed);
        if (countTranslation != null) {
            return countTranslation;
        }
        if (isSavedSuccessfullyLog(trimmed)) {
            return I18n.tr("reloadSaved");
        }
        String pageTranslation = translatePageFetchLog(trimmed);
        if (pageTranslation != null) {
            return pageTranslation;
        }
        if (isPageNoChannelsLog(trimmed)) {
            Integer page = extractFirstNumber(trimmed);
            return page == null
                    ? I18n.tr(TR_RELOAD_NO_CHANNELS_LOADED)
                    : I18n.tr("reloadPageNoChannels", I18n.formatNumber(String.valueOf(page)));
        }
        String savedTranslation = translateSavedLog(account, trimmed);
        if (savedTranslation != null) {
            return savedTranslation;
        }
        return null;
    }

    private String compactFailureLog(String trimmed) {
        if (trimmed.startsWith(LOG_RELOAD_FAILED_PREFIX)) {
            return I18n.tr("reloadFailedReason",
                    shortFailure(trimmed.substring(LOG_RELOAD_FAILED_PREFIX.length()).trim()));
        }
        if (trimmed.equals("Handshake failed.") || trimmed.startsWith("Handshake failed for")) {
            return I18n.tr("reloadFailedHandshake");
        }
        if (trimmed.startsWith(LOG_NETWORK_ERROR_LOADING_CATEGORIES)) {
            return I18n.tr("reloadFailedNetworkError");
        }
        if (trimmed.startsWith("Failed to parse channels")) {
            return I18n.tr("reloadFailedChannelParseError");
        }
        if (trimmed.startsWith("Last-resort fetch failed for category")) {
            return I18n.tr("reloadFailedFallbackCategoryFetch");
        }
        if (trimmed.equals(LOG_MARKED_BAD_AND_SKIPPED)) {
            return I18n.tr("reloadMarkedBadMovedNext");
        }
        return null;
    }

    private String compactGlobalFallbackLog(Account account, String trimmed) {
        String modeBased = translateModeFallbackLog(account, trimmed);
        if (modeBased != null) {
            return modeBased;
        }
        String globalLookup = translateGlobalLookupFallbackLog(trimmed);
        if (globalLookup != null) {
            return globalLookup;
        }
        return translateCategoryListFallbackLog(trimmed);
    }

    private boolean isLoadedChannelsFromCacheLog(String trimmed) {
        return trimmed.startsWith("Loaded channels from local cache");
    }

    private boolean isFreshCacheMissLog(String trimmed) {
        return trimmed.startsWith("No fresh cache found for category");
    }

    private boolean isFreshCategoriesCacheMissLog(String trimmed) {
        return trimmed.startsWith("No fresh cached categories found");
    }

    private boolean isNoCachedCategoriesLog(String trimmed) {
        return trimmed.startsWith("No cached categories found");
    }

    private String resolveCategoryFetchProvider(String trimmed) {
        if (trimmed.startsWith("Fetching categories from Xtreme API")) {
            return "Xtreme API";
        }
        if (trimmed.startsWith("Fetching categories from Stalker Portal")) {
            return "Stalker Portal";
        }
        return null;
    }

    private String translateProviderCategoryFetch(Account account, String provider) {
        String mode = modeLabel(account);
        return mode == null
                ? I18n.tr("reloadFetchingCategoriesFromProvider", provider)
                : I18n.tr("reloadFetchingModeCategoriesFromProvider", mode, provider);
    }

    private String translateFoundCountLog(Account account, String trimmed) {
        if (trimmed.startsWith("Found Categories")) {
            return translateModeAwareCount(account, trimmed, "reloadCategoriesCount", "reloadModeCategoriesCount", I18n.tr("autoCategories") + ":");
        }
        if (trimmed.startsWith("Found Channels")) {
            return translateModeAwareCount(account, trimmed, "reloadChannelsCount", "reloadModeChannelsCount", I18n.tr("autoChannels") + ":");
        }
        return null;
    }

    private String translateModeAwareCount(Account account, String trimmed, String noModeKey, String modeKey, String fallback) {
        Integer count = extractFirstNumber(trimmed);
        if (count == null) {
            return fallback;
        }
        String formattedCount = I18n.formatNumber(String.valueOf(count));
        String mode = modeLabel(account);
        return mode == null ? I18n.tr(noModeKey, formattedCount) : I18n.tr(modeKey, mode, formattedCount);
    }

    private boolean isSavedSuccessfullyLog(String trimmed) {
        return trimmed.endsWith("saved Successfully ✓");
    }

    private String translatePageFetchLog(String trimmed) {
        Matcher fetching = Pattern.compile("Fetching page\\s+(\\d+)\\s+for category\\s+(.+)\\.\\.\\.").matcher(trimmed);
        if (fetching.matches()) {
            return I18n.tr("reloadPageCategory", I18n.formatNumber(fetching.group(1)), fetching.group(2).trim());
        }
        Matcher fetched = Pattern.compile("Fetched\\s+(\\d+)\\s+channels from page\\s+(\\d+)\\.?").matcher(trimmed);
        if (fetched.matches()) {
            return I18n.tr("reloadChannelsPage", I18n.formatNumber(fetched.group(1)), I18n.formatNumber(fetched.group(2)));
        }
        return null;
    }

    private boolean isPageNoChannelsLog(String trimmed) {
        return trimmed.startsWith("Page ") && trimmed.endsWith(" returned no channels.");
    }

    private String translateSavedLog(Account account, String trimmed) {
        if (!trimmed.startsWith("Saved ")) {
            return null;
        }
        if (trimmed.contains(" to local VOD/Series cache.")) {
            Integer count = extractFirstNumber(trimmed);
            if (count != null) {
                String mode = modeLabel(account);
                return mode != null
                        ? I18n.tr("reloadModeCategoriesCount", mode, I18n.formatNumber(String.valueOf(count)))
                        : I18n.tr("reloadCategoriesSaved", I18n.formatNumber(String.valueOf(count)));
            }
        }
        return I18n.tr("reloadSavedToCache");
    }

    private boolean isNoCategoriesFoundLog(String trimmed) {
        return "No categories found. Keeping existing cache.".equals(trimmed);
    }

    private boolean isLastResortFetchSucceededLog(String trimmed) {
        return trimmed.startsWith("Last-resort fetch succeeded. Collected");
    }

    private String extractGlobalFailureReason(Account account, String message) {
        if (account == null || message == null) {
            return null;
        }
        if (account.getType() != AccountType.XTREME_API && account.getType() != AccountType.STALKER_PORTAL) {
            return null;
        }
        String trimmed = message.trim();
        if (trimmed.startsWith(GLOBAL_XTREME_CHANNEL_LOOKUP_FAILED)) {
            return I18n.tr("reloadLiveTvGlobalCallFailedForXtreme");
        }
        if (trimmed.startsWith(GLOBAL_STALKER_GET_ALL_CHANNELS_FAILED)) {
            return I18n.tr("reloadLiveTvGetAllChannelsFailedForStalker");
        }
        if (trimmed.startsWith(GLOBAL_VOD_CATEGORY_LIST_FAILED)) {
            return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_CALL_FAILED, I18n.tr(TR_CATEGORY_TAB_VOD));
        }
        if (trimmed.startsWith(GLOBAL_SERIES_CATEGORY_LIST_FAILED)) {
            return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_CALL_FAILED, I18n.tr(TR_CATEGORY_TAB_SERIES));
        }
        if (trimmed.startsWith(LOG_NETWORK_ERROR_LOADING_CATEGORIES)) {
            String modeCode = modeCode(account);
            if (MODE_VOD.equals(modeCode)) {
                return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_CALL_FAILED, I18n.tr(TR_CATEGORY_TAB_VOD));
            }
            if (MODE_SERIES.equals(modeCode)) {
                return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_CALL_FAILED, I18n.tr(TR_CATEGORY_TAB_SERIES));
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
            normalizedReasons.add(I18n.tr(TR_RELOAD_NO_CHANNELS_LOADED));
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
        String issue = extractCommonIssueReason(trimmed);
        if (issue != null) {
            return issue;
        }
        issue = extractGlobalIssueReason(trimmed);
        if (issue != null) {
            return issue;
        }
        if (trimmed.equals("No channels found in any category. Keeping existing cache.")
                || trimmed.equals(LOG_NO_CHANNELS_FOUND)) {
            String mode = modeLabel(account);
            return mode == null
                    ? I18n.tr("reloadNoChannelsFound")
                    : I18n.tr("reloadModeNoChannelsFound", mode);
        }
        if (trimmed.equals(LOG_MARKED_BAD_AND_SKIPPED)) {
            return I18n.tr("reloadMarkedBadByUser");
        }
        return null;
    }

    private String extractCommonIssueReason(String trimmed) {
        if (trimmed.startsWith(LOG_RELOAD_FAILED_PREFIX)) {
            return I18n.tr("reloadReloadFailed");
        }
        if (trimmed.equals("Handshake failed.") || trimmed.startsWith("Handshake failed for")) {
            return I18n.tr("reloadHandshakeFailed");
        }
        if (trimmed.startsWith(LOG_NETWORK_ERROR_LOADING_CATEGORIES)) {
            return I18n.tr("reloadNetworkErrorLoadingCategories");
        }
        if (trimmed.startsWith("Failed to parse channels")) {
            return I18n.tr("reloadFailedToParseChannels");
        }
        if (trimmed.startsWith("Last-resort fetch failed for category")) {
            return I18n.tr("reloadFallbackCategoryFetchFailed");
        }
        return null;
    }

    private String extractGlobalIssueReason(String trimmed) {
        if (trimmed.startsWith("No channels returned by get_all_channels")
                || trimmed.startsWith(GLOBAL_STALKER_GET_ALL_CHANNELS_FAILED)) {
            return I18n.tr("reloadGlobalLiveTvChannelCallFailed");
        }
        if (trimmed.startsWith(GLOBAL_XTREME_CHANNEL_LOOKUP_FAILED)
                || trimmed.startsWith("Global Xtreme channel lookup returned no channels")
                || trimmed.startsWith("Global Xtreme channel lookup returned uncategorized rows only")) {
            return I18n.tr("reloadGlobalXtremeLookupFailed");
        }
        if (trimmed.startsWith(GLOBAL_VOD_CATEGORY_LIST_FAILED)) {
            return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_FAILED, I18n.tr(TR_CATEGORY_TAB_VOD));
        }
        if (trimmed.startsWith(GLOBAL_SERIES_CATEGORY_LIST_FAILED)) {
            return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_FAILED, I18n.tr(TR_CATEGORY_TAB_SERIES));
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
        } catch (NumberFormatException _) {
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
                return MODE_SERIES;
            default:
                return null;
        }
    }

    private String translateModeFallbackLog(Account account, String trimmed) {
        String mode = modeLabel(account);
        if (isNoCategoriesFoundLog(trimmed)) {
            return mode == null
                    ? I18n.tr("reloadNoCategoriesFoundCacheKept")
                    : I18n.tr("reloadModeCategoriesNoneFound", mode);
        }
        if (LOG_NO_CHANNELS_FOUND.equals(trimmed)) {
            return mode == null
                    ? I18n.tr("reloadModeChannelsNoneFound", I18n.tr("categoryTabLiveTv"))
                    : I18n.tr("reloadModeChannelsNoneFound", mode);
        }
        if (isLastResortFetchSucceededLog(trimmed)) {
            Integer count = extractFirstNumber(trimmed);
            return count == null
                    ? I18n.tr("reloadFallbackFetchSucceeded")
                    : I18n.tr("reloadFallbackFetchSucceededWithChannels", I18n.formatNumber(String.valueOf(count)));
        }
        return null;
    }

    private String translateGlobalLookupFallbackLog(String trimmed) {
        if (trimmed.startsWith(GLOBAL_XTREME_CHANNEL_LOOKUP_FAILED)) {
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
        if (trimmed.startsWith(GLOBAL_STALKER_GET_ALL_CHANNELS_FAILED)) {
            return I18n.tr("reloadGlobalChannelListFailedTryingFallback");
        }
        return null;
    }

    private String translateCategoryListFallbackLog(String trimmed) {
        if (trimmed.startsWith(GLOBAL_VOD_CATEGORY_LIST_FAILED)) {
            return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_FAILED, I18n.tr(TR_CATEGORY_TAB_VOD));
        }
        if (trimmed.startsWith(GLOBAL_SERIES_CATEGORY_LIST_FAILED)) {
            return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_FAILED, I18n.tr(TR_CATEGORY_TAB_SERIES));
        }
        return null;
    }

    private String modeLabel(Account account) {
        String code = modeCode(account);
        if (code == null) {
            return null;
        }
        return switch (code) {
            case "ITV" -> I18n.tr("categoryTabLiveTv");
            case MODE_VOD -> I18n.tr(TR_CATEGORY_TAB_VOD);
            case MODE_SERIES -> I18n.tr(TR_CATEGORY_TAB_SERIES);
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
                    statusLabel.setStyle(STYLE_YELLOW_LABEL);
                    break;
                case EMPTY:
                    runningIndicator.setVisible(false);
                    statusLabel.setText(I18n.tr("autoEmpty0Channels"));
                    statusLabel.setStyle(STYLE_YELLOW_LABEL);
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
