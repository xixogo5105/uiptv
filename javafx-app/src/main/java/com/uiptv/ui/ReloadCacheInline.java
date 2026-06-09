package com.uiptv.ui;

import com.uiptv.application.ConfigurationApplicationService;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.model.Account;
import com.uiptv.model.AccountInfo;
import com.uiptv.service.AccountService;
import com.uiptv.service.AccountInfoService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.util.AccountType;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.SegmentedProgressBar;
import com.uiptv.widget.ThemedDialogSupport;
import javafx.application.Platform;
import javafx.geometry.Bounds;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.net.URI;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.model.Account.CACHE_SUPPORTED;

public class ReloadCacheInline extends VBox {
    private static final double ACCOUNT_COLUMN_PERCENT = 42;
    private static final double LOG_COLUMN_PERCENT = 58;
    private static final double ACCOUNT_COLUMN_MIN_WIDTH = 680;
    private static final double LOG_COLUMN_MIN_WIDTH = 360;
    private static final double MAIN_CONTENT_COLUMN_GAP = 14;
    private static final double STACKED_LAYOUT_WIDTH = ACCOUNT_COLUMN_MIN_WIDTH + LOG_COLUMN_MIN_WIDTH + MAIN_CONTENT_COLUMN_GAP;
    private static final Pattern NUMBER_PATTERN = Pattern.compile("(\\d+)");
    private static final Pattern DISPLAY_CENSORED_COUNT_PATTERN =
            Pattern.compile("^(.*\\bcensored\\s+(?:categories|channels):\\s*)([\\p{Nd},.]+)(.*)$",
                    Pattern.CASE_INSENSITIVE);
    private static final String GLOBAL_SERIES_CATEGORY_LIST_FAILED = "Global SERIES category list failed:";
    private static final String GLOBAL_STALKER_GET_ALL_CHANNELS_FAILED = "Global Stalker get_all_channels failed";
    private static final String GLOBAL_VOD_CATEGORY_LIST_FAILED = "Global VOD category list failed:";
    private static final String GLOBAL_XTREME_CHANNEL_LOOKUP_FAILED = "Global Xtreme channel lookup failed";
    private static final String LOG_MARKED_BAD_AND_SKIPPED = "Marked bad and skipped after global call failure.";
    private static final String LOG_NETWORK_ERROR_LOADING_CATEGORIES = "Network error while loading categories";
    private static final String LOG_NO_CHANNELS_FOUND = "No channels found.";
    private static final String LOG_RELOAD_FAILED_PREFIX = "Reload failed:";
    private static final String LOG_SKIPPED_IGNORED_DOMAIN = "Skipped after previous failure for the same domain in this run.";
    private static final String MODE_SERIES = "SERIES";
    private static final String MODE_VOD = "VOD";
    private static final String STYLE_CLASS_CENSORED_COUNT = "censored-count";
    private static final String STYLE_CLASS_LOG_TEXT = "log-text";
    private static final String STYLE_TEXT_BASE_FILL = "-fx-fill: -fx-text-base-color;";
    private static final String TR_CATEGORY_TAB_SERIES = "categoryTabTvSeries";
    private static final String TR_CATEGORY_TAB_VOD = "categoryTabVideoOnDemand";
    private static final String TR_CATEGORY_TAB_LIVE_TV = "categoryTabLiveTv";
    private static final String TR_RELOAD_MODE_CATEGORY_LIST_CALL_FAILED = "reloadModeCategoryListCallFailed";
    private static final String TR_RELOAD_MODE_CATEGORY_LIST_FAILED = "reloadModeCategoryListFailed";
    private static final Set<AccountType> M3U_ACCOUNT_TYPES = Set.of(AccountType.M3U8_LOCAL, AccountType.M3U8_URL);
    private static final String TR_RELOAD_NO_CHANNELS_LOADED = "reloadNoChannelsLoaded";
    private static final int MAX_LOG_LINES_PER_ACCOUNT = Math.max(1, Integer.getInteger("uiptv.reload.logs.maxLinesPerAccount", 500));
    private static final int MAX_PENDING_LOG_LINES = Math.max(1, Integer.getInteger("uiptv.reload.logs.maxPending", 2_000));
    private static final double BULK_FAILURE_DIALOG_CONTENT_WIDTH = 520;
    private static final double BULK_FAILURE_DIALOG_CONTENT_INSET = 14;
    private static final double GLOBAL_FAILURE_DIALOG_CONTENT_WIDTH = 640;
    private static final double GLOBAL_FAILURE_DIALOG_PANE_WIDTH = 720;
    private static final double GLOBAL_FAILURE_DIALOG_CONTENT_INSET = 10;
    private static final boolean POST_RELOAD_MEMORY_CLEANUP_ENABLED = Boolean.parseBoolean(
            System.getProperty("uiptv.reload.memoryCleanup.enabled", "true"));
    private static final int POST_RELOAD_MEMORY_CLEANUP_MIN_ACCOUNTS = Math.max(1,
            Integer.getInteger("uiptv.reload.memoryCleanup.minAccounts", 1));
    private static final long POST_RELOAD_MEMORY_CLEANUP_DELAY_MS = Math.max(0L,
            Long.getLong("uiptv.reload.memoryCleanup.delayMs", 1_500L));
    private static final double PROBLEM_ACCOUNTS_CARD_MAX_WIDTH = 980;
    private static final double PROBLEM_ACCOUNTS_MIN_LIST_HEIGHT = 170;
    private static final double PROBLEM_ACCOUNTS_MAX_LIST_HEIGHT = 420;
    private static final double PROBLEM_ACCOUNTS_ROW_HEIGHT_ESTIMATE = 64;

    private enum AccountRunStatus {
        QUEUED, RUNNING, DONE, YELLOW, FAILED, EMPTY
    }

    private enum SummaryLevel {
        GOOD, YELLOW, BAD
    }

    private enum GlobalFailureDecision {
        CARRY_ON, MARK_BAD, MARK_BAD_AND_IGNORE_DOMAIN, STOP_ALL
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

    private record PendingLogLine(String accountId, String line) {
    }

    private final VBox accountsVBox = new VBox(5);
    private final VBox logVBox = new VBox(8);
    private final ScrollPane accountsScrollPane = new ScrollPane();
    private final ScrollPane logScrollPane = new ScrollPane(logVBox);
    private final SegmentedProgressBar progressBar = new SegmentedProgressBar();
    private final Label progressSummaryLabel = new Label();
    private final ComboBox<AutomaticFailureDecisionOption> failureDecisionComboBox = new ComboBox<>();
    private final ProminentButton reloadButton = new ProminentButton(I18n.tr("autoReloadSelected"));
    private final Button stopButton = new Button(I18n.tr("autoStop"));
    private final CacheService cacheService = new CacheServiceImpl();
    private final AccountService accountService = AccountService.getInstance();
    private final AccountInfoService accountInfoService = AccountInfoService.getInstance();
    private final List<CheckBox> checkBoxes = new ArrayList<>();
    private final Map<String, AccountLogPanel> accountLogPanels = new LinkedHashMap<>();
    private final List<String> runAccountOrder = new ArrayList<>();
    private final List<String> latestSummaryLines = new ArrayList<>();
    private final Map<String, SummaryStatus> latestAccountSummaries = new LinkedHashMap<>();
    private final ReloadRunOutcomeTracker runOutcomeTracker = new ReloadRunOutcomeTracker();
    private final Set<String> ignoredFailureDomains = new HashSet<>();
    private final Set<String> pendingAccountInfoRefreshes = ConcurrentHashMap.newKeySet();
    private final Object pendingLogLock = new Object();
    private final Deque<PendingLogLine> pendingLogLines = new ArrayDeque<>();
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean logDrainScheduled = new AtomicBoolean(false);
    private final AtomicReference<Thread> reloadThread = new AtomicReference<>();
    private final boolean showFailureHandlingCard;
    private final boolean promptFailureHandlingBeforeAutoStart;
    private Runnable externalCloseHandler = () -> { };
    private volatile boolean stopRequested = false;
    private volatile boolean disposed = false;
    private volatile GlobalFailureDecision automaticGlobalFailureDecision;
    private final Runnable onAccountsDeleted;
    private GridPane mainContent;
    private VBox accountColumn;
    private VBox logColumn;
    private ColumnConstraints accountsColumn;
    private ColumnConstraints logsColumn;
    private boolean accountSelectionHidden;
    private boolean stackedMainContent;

    public static void open() {
        open(null, null);
    }

    public static void open(List<Account> preselectedAccounts) {
        open(preselectedAccounts, null);
    }

    public static void open(List<Account> preselectedAccounts, Runnable onAccountsDeleted) {
        ReloadCachePopup.showPopup(RootApplication.getPrimaryStage(), preselectedAccounts, onAccountsDeleted);
    }

    public ReloadCacheInline() {
        this(null);
    }

    public ReloadCacheInline(List<Account> preselectedAccounts) {
        this(preselectedAccounts, null);
    }

    public ReloadCacheInline(List<Account> preselectedAccounts, Runnable onAccountsDeleted) {
        this(
                preselectedAccounts,
                onAccountsDeleted,
                shouldShowFailureHandlingCard(preselectedAccounts),
                shouldPromptFailureHandlingBeforeAutoStart(preselectedAccounts)
        );
    }

    private ReloadCacheInline(List<Account> preselectedAccounts,
                              Runnable onAccountsDeleted,
                              boolean showFailureHandlingCard,
                              boolean promptFailureHandlingBeforeAutoStart) {
        this.onAccountsDeleted = onAccountsDeleted;
        this.showFailureHandlingCard = showFailureHandlingCard;
        this.promptFailureHandlingBeforeAutoStart = promptFailureHandlingBeforeAutoStart;
        initializeLayout();
        List<Account> supportedAccounts = loadSupportedAccounts();
        populateAccountCheckboxes(supportedAccounts);
        FlowPane selectControls = buildSelectControls();
        configureScrollPanes();
        VBox progressCard = buildProgressCard();
        VBox failureHandlingCard = buildFailureHandlingCard();
        GridPane mainContent = buildMainContent(selectControls);
        HBox buttonBox = buildButtonBox();
        getChildren().addAll(buildHeader(), progressCard);
        if (this.showFailureHandlingCard) {
            getChildren().add(failureHandlingCard);
        }
        getChildren().addAll(mainContent, buttonBox);

        if (preselectedAccounts != null && !preselectedAccounts.isEmpty()) {
            preselectAccounts(preselectedAccounts);
            if (checkBoxes.stream().anyMatch(CheckBox::isSelected)) {
                hideAccountSelectionColumn();
                Platform.runLater(this::startReloadInBackground);
            }
        }
    }

    private static boolean shouldShowFailureHandlingCard(List<Account> preselectedAccounts) {
        return preselectedAccounts == null || preselectedAccounts.isEmpty();
    }

    private static boolean shouldPromptFailureHandlingBeforeAutoStart(List<Account> preselectedAccounts) {
        return preselectedAccounts != null && preselectedAccounts.size() > 1;
    }

    private void initializeLayout() {
        getStyleClass().addAll("management-popup-root", "reload-cache-popup", "uiptv-inline-fill-height");
        setSpacing(14);
        setPadding(new Insets(18));
        setFillWidth(true);
        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        accountsVBox.getStyleClass().add("reload-account-list");
        accountsVBox.setPadding(new Insets(2));
        accountsVBox.setFillWidth(true);
        logVBox.getStyleClass().add("reload-log-list");
        logVBox.setPadding(new Insets(2));
        logVBox.setFillWidth(true);
    }

    private VBox buildHeader() {
        Label title = new Label(I18n.tr("autoReloadAccountsCache"));
        title.getStyleClass().add("management-popup-title");

        VBox header = new VBox(2, title);
        header.getStyleClass().add("management-popup-header");
        return header;
    }

    private VBox buildProgressCard() {
        progressSummaryLabel.getStyleClass().add("reload-progress-summary");
        progressSummaryLabel.setMinWidth(Region.USE_PREF_SIZE);
        progressSummaryLabel.setMaxWidth(Region.USE_PREF_SIZE);
        progressSummaryLabel.setText(I18n.tr("autoQueued"));

        progressBar.setMinWidth(0);
        progressBar.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(progressBar, Priority.ALWAYS);

        HBox progressRow = new HBox(12, progressSummaryLabel, progressBar);
        progressRow.getStyleClass().add("reload-progress-row");
        progressRow.setAlignment(Pos.CENTER_LEFT);
        progressRow.setMaxWidth(Double.MAX_VALUE);

        VBox progressCard = new VBox(progressRow);
        progressCard.getStyleClass().addAll("management-popup-card", "reload-progress-card");
        progressCard.setAlignment(Pos.CENTER_LEFT);
        progressCard.setFillWidth(true);
        progressCard.setMinWidth(0);
        progressCard.setMaxWidth(Double.MAX_VALUE);
        return progressCard;
    }

    private VBox buildFailureHandlingCard() {
        Label title = new Label(I18n.tr("reloadBulkFailureHandlingTitle"));
        title.getStyleClass().add("reload-failure-policy-title");

        Label description = new Label(I18n.tr("reloadBulkFailureHandlingMessage"));
        description.getStyleClass().add("reload-failure-policy-description");
        description.setWrapText(true);
        description.setMinWidth(0);
        description.setMaxWidth(Double.MAX_VALUE);

        configureFailureDecisionComboBox();

        VBox text = new VBox(3, title, description);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(12, text, failureDecisionComboBox);
        row.getStyleClass().add("reload-failure-policy-row");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(row);
        card.getStyleClass().addAll("management-popup-card", "reload-failure-policy-card");
        card.setFillWidth(true);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        return card;
    }

    private void configureFailureDecisionComboBox() {
        failureDecisionComboBox.getStyleClass().add("reload-failure-policy-combo");
        failureDecisionComboBox.getItems().setAll(automaticFailureDecisionOptions());
        failureDecisionComboBox.setValue(failureDecisionComboBox.getItems().isEmpty()
                ? null
                : failureDecisionComboBox.getItems().getFirst());
        failureDecisionComboBox.setCellFactory(_ -> createFailureDecisionOptionCell());
        failureDecisionComboBox.setButtonCell(createFailureDecisionOptionCell());
        failureDecisionComboBox.setMinWidth(260);
        failureDecisionComboBox.setPrefWidth(340);
        failureDecisionComboBox.setMaxWidth(420);
    }

    private ListCell<AutomaticFailureDecisionOption> createFailureDecisionOptionCell() {
        return new ListCell<>() {
            @Override
            protected void updateItem(AutomaticFailureDecisionOption item, boolean empty) {
                super.updateItem(item, empty);
                setText(empty || item == null ? null : item.label());
            }
        };
    }

    private List<Account> loadSupportedAccounts() {
        List<Account> supportedAccounts = new ArrayList<>(accountService.getAll().values().stream()
                .filter(account -> CACHE_SUPPORTED.contains(account.getType()))
                .toList());
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
        accountCheckBox.getStyleClass().add("reload-account-row");
        if (index % 2 != 0) {
            accountCheckBox.getStyleClass().add("reload-account-row-alt");
        }
        return accountCheckBox;
    }

    private FlowPane buildSelectControls() {
        ToggleButton allChip = createSelectChip(I18n.tr("commonAll"));
        ToggleButton stalkerChip = createSelectChip(I18n.tr("reloadStalkerPortalAccounts"));
        ToggleButton xtremeChip = createSelectChip(I18n.tr("reloadXtremeAccount"));
        ToggleButton m3uChip = createSelectChip("M3U");

        allChip.setOnAction(e -> {
            boolean selected = allChip.isSelected();
            setAllSelectionStates(selected);
            stalkerChip.setSelected(selected);
            xtremeChip.setSelected(selected);
            m3uChip.setSelected(selected);
        });
        stalkerChip.setOnAction(e -> updateCheckboxes(AccountType.STALKER_PORTAL, stalkerChip.isSelected()));
        xtremeChip.setOnAction(e -> updateCheckboxes(AccountType.XTREME_API, xtremeChip.isSelected()));
        m3uChip.setOnAction(e -> updateCheckboxes(M3U_ACCOUNT_TYPES, m3uChip.isSelected()));

        FlowPane chips = new FlowPane(8, 8, allChip, stalkerChip, xtremeChip, m3uChip);
        chips.getStyleClass().add("reload-select-chip-row");
        chips.setAlignment(Pos.CENTER_LEFT);
        chips.setMinWidth(0);
        chips.setMaxWidth(Double.MAX_VALUE);
        return chips;
    }

    private ToggleButton createSelectChip(String text) {
        ToggleButton chip = new ToggleButton(text);
        chip.getStyleClass().add("reload-select-chip");
        chip.setFocusTraversable(false);
        chip.setMinHeight(32);
        return chip;
    }

    private void setAllSelectionStates(boolean selected) {
        checkBoxes.forEach(cb -> cb.setSelected(selected));
    }

    private void configureScrollPanes() {
        accountsScrollPane.setContent(accountsVBox);
        accountsScrollPane.setFitToWidth(true);
        accountsScrollPane.setFitToHeight(true);
        accountsScrollPane.setMinWidth(0);
        accountsScrollPane.setMinHeight(0);
        accountsScrollPane.setMaxWidth(Double.MAX_VALUE);
        accountsScrollPane.setMaxHeight(Double.MAX_VALUE);
        accountsScrollPane.getStyleClass().addAll("transparent-scroll-pane", "reload-account-scroll");
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);
        logScrollPane.setFitToWidth(true);
        logScrollPane.setFitToHeight(true);
        logScrollPane.setMinWidth(0);
        logScrollPane.setMinHeight(0);
        logScrollPane.setMaxWidth(Double.MAX_VALUE);
        logScrollPane.setMaxHeight(Double.MAX_VALUE);
        logScrollPane.getStyleClass().addAll("transparent-scroll-pane", "reload-log-scroll");
        VBox.setVgrow(logScrollPane, Priority.ALWAYS);
    }

    private GridPane buildMainContent(FlowPane selectControls) {
        Label accountTitle = createColumnTitle(I18n.tr("autoAccount"));
        accountColumn = new VBox(10, accountTitle, selectControls, accountsScrollPane);
        accountColumn.getStyleClass().addAll("management-popup-card", "reload-column-card", "reload-account-column");
        accountColumn.setFillWidth(true);
        accountColumn.setMinSize(0, 0);
        accountColumn.setMaxWidth(Double.MAX_VALUE);
        accountColumn.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(accountsScrollPane, Priority.ALWAYS);

        Label logTitle = createColumnTitle(I18n.tr("autoLogs"));
        logColumn = new VBox(10, logTitle, logScrollPane);
        logColumn.getStyleClass().addAll("management-popup-card", "reload-column-card", "reload-log-column");
        logColumn.setFillWidth(true);
        logColumn.setMinSize(0, 0);
        logColumn.setMaxWidth(Double.MAX_VALUE);
        logColumn.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(logScrollPane, Priority.ALWAYS);
        GridPane.setHgrow(accountColumn, Priority.ALWAYS);
        GridPane.setHgrow(logColumn, Priority.ALWAYS);
        GridPane.setVgrow(accountColumn, Priority.ALWAYS);
        GridPane.setVgrow(logColumn, Priority.ALWAYS);

        mainContent = new GridPane();
        mainContent.getStyleClass().add("reload-main-content");
        mainContent.setHgap(14);
        mainContent.setVgap(0);
        mainContent.setMinSize(0, 0);
        mainContent.setMaxWidth(Double.MAX_VALUE);
        mainContent.setMaxHeight(Double.MAX_VALUE);
        accountsColumn = createAccountContentColumn();
        logsColumn = createLogContentColumn();
        RowConstraints contentRow = new RowConstraints();
        contentRow.setVgrow(Priority.ALWAYS);
        contentRow.setFillHeight(true);
        mainContent.getColumnConstraints().addAll(accountsColumn, logsColumn);
        mainContent.getRowConstraints().add(contentRow);
        mainContent.add(accountColumn, 0, 0);
        mainContent.add(logColumn, 1, 0);
        VBox.setVgrow(mainContent, Priority.ALWAYS);
        configureResponsiveMainContent(mainContent);
        return mainContent;
    }

    private Label createColumnTitle(String text) {
        Label label = new Label(text);
        label.getStyleClass().add("management-popup-section-title");
        return label;
    }

    private ColumnConstraints createContentColumn(double widthPercent) {
        ColumnConstraints column = new ColumnConstraints();
        column.setPercentWidth(widthPercent);
        column.setFillWidth(true);
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private ColumnConstraints createAccountContentColumn() {
        ColumnConstraints column = createContentColumn(ACCOUNT_COLUMN_PERCENT);
        column.setMinWidth(ACCOUNT_COLUMN_MIN_WIDTH);
        return column;
    }

    private ColumnConstraints createLogContentColumn() {
        ColumnConstraints column = createContentColumn(LOG_COLUMN_PERCENT);
        column.setMinWidth(LOG_COLUMN_MIN_WIDTH);
        return column;
    }

    private RowConstraints createContentRow() {
        RowConstraints row = new RowConstraints();
        row.setFillHeight(true);
        row.setVgrow(Priority.ALWAYS);
        return row;
    }

    private RowConstraints createContentRow(double heightPercent) {
        RowConstraints row = createContentRow();
        row.setPercentHeight(heightPercent);
        return row;
    }

    private void configureResponsiveMainContent(GridPane content) {
        content.widthProperty().addListener((_, _, width) -> updateMainContentLayout(width.doubleValue()));
        Platform.runLater(() -> updateMainContentLayout(content.getWidth()));
    }

    private void updateMainContentLayout(double width) {
        if (mainContent == null || logColumn == null || accountColumn == null) {
            return;
        }
        if (accountSelectionHidden) {
            applyLogOnlyLayout();
            return;
        }
        if (width <= 0) {
            return;
        }
        boolean shouldStack = width < STACKED_LAYOUT_WIDTH;
        if (shouldStack == stackedMainContent
                && mainContent.getColumnConstraints().size() == (shouldStack ? 1 : 2)
                && mainContent.getRowConstraints().size() == (shouldStack ? 2 : 1)) {
            return;
        }
        stackedMainContent = shouldStack;
        if (shouldStack) {
            applyStackedLayout();
        } else {
            applyTwoColumnLayout();
        }
    }

    private void applyTwoColumnLayout() {
        accountColumn.setVisible(true);
        accountColumn.setManaged(true);
        accountsColumn = createAccountContentColumn();
        logsColumn = createLogContentColumn();
        mainContent.getColumnConstraints().setAll(accountsColumn, logsColumn);
        mainContent.getRowConstraints().setAll(createContentRow());
        mainContent.setHgap(14);
        mainContent.setVgap(0);
        GridPane.setColumnIndex(accountColumn, 0);
        GridPane.setRowIndex(accountColumn, 0);
        GridPane.setColumnSpan(accountColumn, 1);
        GridPane.setRowSpan(accountColumn, 1);
        GridPane.setColumnIndex(logColumn, 1);
        GridPane.setRowIndex(logColumn, 0);
        GridPane.setColumnSpan(logColumn, 1);
        GridPane.setRowSpan(logColumn, 1);
    }

    private void applyStackedLayout() {
        accountColumn.setVisible(true);
        accountColumn.setManaged(true);
        accountsColumn = createContentColumn(100);
        logsColumn = null;
        mainContent.getColumnConstraints().setAll(accountsColumn);
        mainContent.getRowConstraints().setAll(createContentRow(44), createContentRow(56));
        mainContent.setHgap(0);
        mainContent.setVgap(14);
        GridPane.setColumnIndex(accountColumn, 0);
        GridPane.setRowIndex(accountColumn, 0);
        GridPane.setColumnSpan(accountColumn, 1);
        GridPane.setRowSpan(accountColumn, 1);
        GridPane.setColumnIndex(logColumn, 0);
        GridPane.setRowIndex(logColumn, 1);
        GridPane.setColumnSpan(logColumn, 1);
        GridPane.setRowSpan(logColumn, 1);
    }

    private void applyLogOnlyLayout() {
        accountColumn.setVisible(false);
        accountColumn.setManaged(false);
        accountsColumn = createContentColumn(100);
        logsColumn = null;
        mainContent.getColumnConstraints().setAll(accountsColumn);
        mainContent.getRowConstraints().setAll(createContentRow());
        mainContent.setHgap(0);
        mainContent.setVgap(0);
        GridPane.setColumnIndex(logColumn, 0);
        GridPane.setRowIndex(logColumn, 0);
        GridPane.setColumnSpan(logColumn, 1);
        GridPane.setRowSpan(logColumn, 1);
    }

    private HBox buildButtonBox() {
        reloadButton.setOnAction(event -> startReloadInBackground());
        reloadButton.managedProperty().bind(reloadButton.visibleProperty());
        stopButton.setVisible(false);
        stopButton.managedProperty().bind(stopButton.visibleProperty());
        stopButton.getStyleClass().add("dangerous");
        stopButton.setOnAction(event -> requestStop());
        Button copyLogButton = new Button(I18n.tr("autoCopyLog"));
        copyLogButton.getStyleClass().add("reload-secondary-button");
        copyLogButton.setOnAction(event -> copyLogsToClipboard());
        Button closeButton = new Button(I18n.tr("autoClose"));
        closeButton.getStyleClass().add("reload-secondary-button");
        closeButton.setOnAction(event -> requestClose());
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox buttonBox = new HBox(10, reloadButton, stopButton, spacer, copyLogButton, closeButton);
        buttonBox.getStyleClass().add("management-popup-footer");
        buttonBox.setAlignment(Pos.CENTER_LEFT);
        buttonBox.setMinWidth(0);
        buttonBox.setMaxWidth(Double.MAX_VALUE);
        return buttonBox;
    }

    private void copyLogsToClipboard() {
        drainPendingLogLines();
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

    void setExternalCloseHandler(Runnable externalCloseHandler) {
        this.externalCloseHandler = externalCloseHandler == null ? () -> { } : externalCloseHandler;
    }

    void disposeExternal() {
        disposeInline();
    }

    private void requestClose() {
        disposeInline();
        externalCloseHandler.run();
    }

    private void disposeInline() {
        if (disposed) {
            return;
        }
        disposed = true;
        requestStop();
        clearPendingLogLines();
        releaseTransientState();
    }

    private void requestStop() {
        stopRequested = true;
        Thread activeReloadThread = reloadThread.get();
        if (activeReloadThread != null) {
            activeReloadThread.interrupt();
        }
    }

    private void releaseTransientState() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::releaseTransientState);
            return;
        }

        for (AccountLogPanel panel : accountLogPanels.values()) {
            panel.dispose();
        }

        // Clear all cached data to allow garbage collection
        checkBoxes.clear();
        accountLogPanels.clear();
        runAccountOrder.clear();
        latestSummaryLines.clear();
        latestAccountSummaries.clear();
        ignoredFailureDomains.clear();
        pendingAccountInfoRefreshes.clear();
        runOutcomeTracker.clear();

        // Clear all log panel UI nodes
        accountsVBox.getChildren().clear();
        logVBox.getChildren().clear();
        accountsScrollPane.setContent(null);
        logScrollPane.setContent(null);
        progressBar.reset();

        // Clear account column UI
        if (accountColumn != null) {
            accountColumn.getChildren().clear();
        }
        getChildren().clear();
    }

    private void hideAccountSelectionColumn() {
        accountSelectionHidden = true;
        applyLogOnlyLayout();
    }

    private void updateCheckboxes(AccountType type, boolean selected) {
        updateCheckboxes(Set.of(type), selected);
    }

    private void updateCheckboxes(Set<AccountType> types, boolean selected) {
        for (CheckBox cb : checkBoxes) {
            Account acc = (Account) cb.getUserData();
            if (types.contains(acc.getType())) {
                cb.setSelected(selected);
            }
        }
    }

    private void startReloadInBackground() {
        if (disposed) {
            return;
        }

        List<Account> selectedAccounts = selectedAccountsSnapshot();
        GlobalFailureDecision selectedAutomaticDecision = resolveFailureDecisionBeforeStart(selectedAccounts);
        if (!reloadInProgress.compareAndSet(false, true)) {
            return;
        }
        automaticGlobalFailureDecision = selectedAutomaticDecision;
        Thread thread = new Thread(() -> reloadSelectedAccounts(selectedAccounts), "uiptv-cache-reload");
        thread.setDaemon(true);
        reloadThread.set(thread);
        thread.start();
    }

    private GlobalFailureDecision resolveFailureDecisionBeforeStart(List<Account> selectedAccounts) {
        if (shouldPromptAutomaticGlobalFailureDecision(selectedAccounts)) {
            return promptAutomaticGlobalFailureDecision(selectedAccounts);
        }
        return selectedFailureDecision();
    }

    boolean shouldPromptAutomaticGlobalFailureDecision(List<Account> selectedAccounts) {
        return promptFailureHandlingBeforeAutoStart
                && selectedAccounts != null
                && selectedAccounts.size() > 1
                && !disposed;
    }

    private GlobalFailureDecision promptAutomaticGlobalFailureDecision(List<Account> selectedAccounts) {
        List<AutomaticFailureDecisionOption> options = automaticFailureDecisionOptions();
        ToggleGroup optionGroup = new ToggleGroup();
        VBox optionBox = new VBox(8);
        optionBox.setFillWidth(true);
        for (AutomaticFailureDecisionOption option : options) {
            RadioButton optionButton = new RadioButton(option.label());
            optionButton.setToggleGroup(optionGroup);
            optionButton.setUserData(option);
            optionButton.setWrapText(true);
            optionButton.setMaxWidth(Double.MAX_VALUE);
            optionBox.getChildren().add(optionButton);
        }
        if (!optionGroup.getToggles().isEmpty()) {
            optionGroup.selectToggle(optionGroup.getToggles().getFirst());
        }

        Label message = new Label(I18n.tr("reloadBulkFailureHandlingMessage"));
        message.setWrapText(true);
        message.setPrefWidth(BULK_FAILURE_DIALOG_CONTENT_WIDTH);
        message.setMinHeight(Region.USE_PREF_SIZE);
        VBox content = new VBox(12, message, optionBox);
        content.setPadding(new Insets(8, BULK_FAILURE_DIALOG_CONTENT_INSET, 0, BULK_FAILURE_DIALOG_CONTENT_INSET));
        content.setPrefWidth(BULK_FAILURE_DIALOG_CONTENT_WIDTH);
        content.setMaxWidth(BULK_FAILURE_DIALOG_CONTENT_WIDTH);

        ButtonType okButton = new ButtonType(I18n.tr("commonOk"), ButtonBar.ButtonData.OK_DONE);
        ButtonType closeButton = new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Dialog<AutomaticFailureDecisionOption> dialog = new Dialog<>();
        dialog.setTitle(I18n.tr("reloadBulkFailureHandlingTitle"));
        dialog.setHeaderText(I18n.tr("reloadBulkFailureHandlingHeader",
                I18n.formatNumber(String.valueOf(selectedAccounts.size()))));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().setPrefWidth(BULK_FAILURE_DIALOG_CONTENT_WIDTH
                + (BULK_FAILURE_DIALOG_CONTENT_INSET * 2));
        dialog.getDialogPane().getButtonTypes().setAll(okButton, closeButton);
        dialog.setResultConverter(button -> button == okButton && optionGroup.getSelectedToggle() != null
                ? (AutomaticFailureDecisionOption) optionGroup.getSelectedToggle().getUserData()
                : null);
        applyDialogThemeAndOrientation(dialog);

        return ThemedDialogSupport.showAndWait(dialog, ownerWindow())
                .map(AutomaticFailureDecisionOption::decision)
                .orElse(null);
    }

    private GlobalFailureDecision selectedFailureDecision() {
        AtomicReference<GlobalFailureDecision> decision = new AtomicReference<>();
        runOnFxThreadAndWait(() -> {
            AutomaticFailureDecisionOption selected = failureDecisionComboBox.getValue();
            decision.set(selected == null ? null : selected.decision());
        });
        return decision.get();
    }

    private List<AutomaticFailureDecisionOption> automaticFailureDecisionOptions() {
        return List.of(
                new AutomaticFailureDecisionOption(null, I18n.tr("reloadBulkFailureAskEachAccount")),
                new AutomaticFailureDecisionOption(GlobalFailureDecision.CARRY_ON, I18n.tr("reloadCarryOn")),
                new AutomaticFailureDecisionOption(GlobalFailureDecision.MARK_BAD_AND_IGNORE_DOMAIN, I18n.tr("reloadMarkBadIgnoreDomain")),
                new AutomaticFailureDecisionOption(GlobalFailureDecision.MARK_BAD, I18n.tr("reloadMarkBadAndNext")),
                new AutomaticFailureDecisionOption(GlobalFailureDecision.STOP_ALL, I18n.tr("reloadStopAll"))
        );
    }

    private List<Account> selectedAccountsSnapshot() {
        AtomicReference<List<Account>> selectedAccounts = new AtomicReference<>(List.of());
        runOnFxThreadAndWait(() -> selectedAccounts.set(checkBoxes.stream()
                .filter(CheckBox::isSelected)
                .map(checkBox -> (Account) checkBox.getUserData())
                .toList()));
        return selectedAccounts.get();
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

    private void reloadSelectedAccounts(List<Account> selectedAccounts) {
        try {
            if (disposed) {
                return;
            }
            prepareReloadRun(selectedAccounts);
            if (selectedAccounts.isEmpty()) {
                showReloadButton();
                return;
            }
            List<Account> processedAccounts = new ArrayList<>();
            Map<String, AccountRunStatus> finalStatuses = new LinkedHashMap<>();
            Map<String, SummaryStatus> summaryStatusByAccountId = new LinkedHashMap<>();
            int totalFetchedChannels = 0;
            for (int i = 0; i < selectedAccounts.size() && !isReloadStopped() && !disposed; i++) {
                Account account = selectedAccounts.get(i);
                processedAccounts.add(account);
                setRunningAccount(account, i + 1, selectedAccounts.size());
                if (!isReloadStopped()) {
                    AccountReloadResult result = reloadSingleAccount(account);
                    if (!disposed) {
                        totalFetchedChannels += result.countedChannels;
                        SummaryStatus summaryStatus = buildSummaryStatus(result.availableChannelCount, result.failed,
                                result.accountIssues, result.acceptableZeroResult);
                        summaryStatusByAccountId.put(account.getDbId(), summaryStatus);
                        progressBar.updateSegment(i, segmentStatus(summaryStatus));
                        updateProgressSummary(i + 1, selectedAccounts.size());
                        AccountRunStatus finalStatus = finalAccountRunStatus(summaryStatus, result.availableChannelCount, result.failed);
                        finalStatuses.put(account.getDbId(), finalStatus);
                        updateAccountStatus(account, finalStatus, result.availableChannelCount);
                    }
                }
            }
            finishReloadRun(processedAccounts, finalStatuses, summaryStatusByAccountId, totalFetchedChannels);
        } finally {
            automaticGlobalFailureDecision = null;
            reloadInProgress.set(false);
            reloadThread.compareAndSet(Thread.currentThread(), null);
            requestPostReloadMemoryCleanup(selectedAccounts.size());
        }
    }

    private void requestPostReloadMemoryCleanup(int accountCount) {
        if (!POST_RELOAD_MEMORY_CLEANUP_ENABLED || accountCount < POST_RELOAD_MEMORY_CLEANUP_MIN_ACCOUNTS) {
            return;
        }
        Thread cleanupThread = new Thread(() -> {
            try {
                Thread.sleep(POST_RELOAD_MEMORY_CLEANUP_DELAY_MS);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
                return;
            }
            ConfigurationApplicationService.getInstance().releaseDatabaseMemory();
        }, "uiptv-reload-memory-cleanup");
        cleanupThread.setDaemon(true);
        cleanupThread.start();
    }

    private boolean isReloadStopped() {
        return stopRequested || disposed || Thread.currentThread().isInterrupted();
    }

    private void prepareReloadRun(List<Account> selectedAccounts) {
        stopRequested = false;
        clearPendingLogLines();
        pendingAccountInfoRefreshes.clear();
        Platform.runLater(() -> {
            if (disposed) {
                return;
            }
            reloadButton.setVisible(false);
            stopButton.setVisible(true);
            failureDecisionComboBox.setDisable(true);
        });
        progressBar.setTotal(selectedAccounts.size());
        updateProgressSummary(0, selectedAccounts.size());
        prepareAccountLogPanels(selectedAccounts);
    }

    private void updateProgressSummary(int completed, int total) {
        Platform.runLater(() -> {
            if (disposed) {
                return;
            }
            int safeTotal = Math.max(0, total);
            int safeCompleted = Math.max(0, Math.min(completed, safeTotal));
            progressSummaryLabel.setText(safeTotal <= 0
                    ? I18n.tr("autoQueued")
                    : I18n.tr("autoRunningProgress", safeCompleted, safeTotal));
        });
    }

    private void showReloadButton() {
        Platform.runLater(() -> {
            if (disposed) {
                return;
            }
            reloadButton.setVisible(true);
            stopButton.setVisible(false);
            failureDecisionComboBox.setDisable(false);
        });
    }

    private AccountReloadResult reloadSingleAccount(Account account) {
        if (isReloadStopped()) {
            return new AccountReloadResult(true, 0, 0, List.of(I18n.tr("autoStop")));
        }
        String domainKey = resolveRepeatFailureDomain(account);
        if (domainKey != null && ignoredFailureDomains.contains(domainKey)) {
            logMessage(account, LOG_SKIPPED_IGNORED_DOMAIN);
            List<String> accountIssues = new ArrayList<>();
            addIssue(accountIssues, I18n.tr("reloadSkippedIgnoredDomain", domainKey));
            return new AccountReloadResult(true, 0, 0, accountIssues);
        }

        boolean failed = false;
        int fetchedChannelCount = 0;
        List<String> accountIssues = new ArrayList<>();
        final boolean[] globalFailurePrompted = {false};
        try {
            cacheService.reloadCache(account, message -> handleReloadLogMessage(account, message, accountIssues, globalFailurePrompted));
            fetchedChannelCount = runOutcomeTracker.getFetchedChannels(account.getDbId());
            boolean fullCensoringZeroResult = runOutcomeTracker.hasFullCensoringZeroResult(account.getDbId());
            if (fetchedChannelCount <= 0 && !fullCensoringZeroResult) {
                logMessage(account, LOG_NO_CHANNELS_FOUND);
                addIssue(accountIssues, I18n.tr(TR_RELOAD_NO_CHANNELS_LOADED));
            } else if (fetchedChannelCount > 0) {
                logMessage(account, I18n.tr("reloadSavedToCache"));
            }
        } catch (SkipAccountReloadException _) {
            failed = true;
            logMessage(account, LOG_MARKED_BAD_AND_SKIPPED);
            addIssue(accountIssues, I18n.tr("reloadMarkedBadByUser"));
        } catch (Exception e) {
            failed = true;
            logMessage(account, LOG_RELOAD_FAILED_PREFIX + " " + shortFailure(e.getMessage()));
            addIssue(accountIssues, I18n.tr("reloadFailedReason", shortFailure(e.getMessage())));
        } finally {
            refreshAccountInfoTitle(account);
        }
        boolean criticalFailure = runOutcomeTracker.hasCriticalFailure(account.getDbId());
        boolean fullCensoringZeroResult = runOutcomeTracker.hasFullCensoringZeroResult(account.getDbId());
        int countedChannels = !criticalFailure && fetchedChannelCount > 0 ? fetchedChannelCount : 0;
        int existingChannelCount = cacheService.getChannelCountForAccount(account.getDbId());
        int availableChannelCount = Math.max(fetchedChannelCount, existingChannelCount);
        return new AccountReloadResult(failed || criticalFailure, countedChannels, availableChannelCount,
                accountIssues, fullCensoringZeroResult);
    }

    private void handleReloadLogMessage(Account account, String message, List<String> accountIssues, boolean[] globalFailurePrompted) {
        logMessage(account, message);
        refreshAccountInfoTitle(account);
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
        String domainKey = resolveRepeatFailureDomain(account);
        GlobalFailureDecision decision = promptCarryOnAfterGlobalFailure(account, failureReason, domainKey != null);
        if (decision == GlobalFailureDecision.STOP_ALL) {
            stopRequested = true;
            throw new SkipAccountReloadException();
        }
        if (decision == GlobalFailureDecision.MARK_BAD_AND_IGNORE_DOMAIN && domainKey != null) {
            ignoredFailureDomains.add(domainKey);
        }
        if (decision != GlobalFailureDecision.CARRY_ON) {
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
        if (disposed) {
            return;
        }
        Platform.runLater(() -> {
            if (disposed) {
                return;
            }
            drainPendingLogLines();
            com.uiptv.util.AppLog.addInfoLog(ReloadCacheInline.class, "Reload run completed.");
            reloadButton.setVisible(true);
            stopButton.setVisible(false);
            failureDecisionComboBox.setDisable(false);
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
        private final boolean acceptableZeroResult;

        private AccountReloadResult(boolean failed, int countedChannels, int availableChannelCount, List<String> accountIssues) {
            this(failed, countedChannels, availableChannelCount, accountIssues, false);
        }

        private AccountReloadResult(boolean failed, int countedChannels, int availableChannelCount, List<String> accountIssues,
                                    boolean acceptableZeroResult) {
            this.failed = failed;
            this.countedChannels = countedChannels;
            this.availableChannelCount = availableChannelCount;
            this.accountIssues = accountIssues;
            this.acceptableZeroResult = acceptableZeroResult;
        }
    }

    private void showDeleteProblemAccountsPopup(List<Account> processedAccounts, Map<String, SummaryStatus> problematicAccounts) {
        if (disposed) {
            return;
        }
        Stage popupStage = new Stage();
        if (getScene() != null && getScene().getWindow() != null) {
            popupStage.initOwner(getScene().getWindow());
            popupStage.initModality(Modality.WINDOW_MODAL);
        } else {
            popupStage.initModality(Modality.APPLICATION_MODAL);
        }
        popupStage.setTitle(I18n.tr("autoDeleteProblematicAccounts"));
        VBox accountsBox = new VBox(5);
        Runnable closeAction = popupStage::close;
        VBox root = buildProblemAccountsInlineRoot(processedAccounts, problematicAccounts, accountsBox, closeAction);
        Scene scene = new Scene(root, 760, 560);
        UiI18n.applySceneOrientation(scene);
        if (getScene() != null) {
            scene.getStylesheets().addAll(getScene().getStylesheets());
        } else if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        popupStage.setScene(scene);
        popupStage.show();
    }

    private VBox buildProblemAccountsInlineRoot(List<Account> processedAccounts,
                                                Map<String, SummaryStatus> problematicAccounts,
                                                VBox accountsBox,
                                                Runnable closeAction) {
        VBox card = new VBox(14);
        card.getStyleClass().addAll("management-popup-card", "reload-problem-accounts-card");
        card.setFillWidth(true);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.getChildren().addAll(
                createProblemAccountsWarningLabel(),
                createSelectAllCheckBox(accountsBox),
                buildProblemAccountsScrollPane(processedAccounts, problematicAccounts, accountsBox),
                buildProblemAccountsButtons(accountsBox, closeAction)
        );

        VBox root = new VBox(card);
        root.getStyleClass().addAll("reload-problem-accounts-popup");
        root.setAlignment(Pos.TOP_CENTER);
        root.setFillWidth(true);
        root.setMinWidth(0);
        root.setMaxWidth(PROBLEM_ACCOUNTS_CARD_MAX_WIDTH);
        return root;
    }

    private Label createProblemAccountsWarningLabel() {
        Label warningLabel = new Label(I18n.tr("autoTheFollowingAccountsAreFlaggedAsBADOrYELLOWSelectTheOnesYouWantToDelete"));
        warningLabel.getStyleClass().add("reload-problem-warning");
        warningLabel.setWrapText(true);
        warningLabel.setMinWidth(0);
        warningLabel.setMaxWidth(Double.MAX_VALUE);
        return warningLabel;
    }

    private CheckBox createSelectAllCheckBox(VBox accountsBox) {
        CheckBox selectAll = new CheckBox(I18n.tr("autoSelectAll"));
        selectAll.getStyleClass().add("reload-problem-select-all");
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
        accountsBox.getStyleClass().add("reload-problem-account-list");
        accountsBox.setFillWidth(true);
        accountsBox.setMinWidth(0);
        ScrollPane scrollPane = new ScrollPane(accountsBox);
        scrollPane.getStyleClass().addAll("transparent-scroll-pane", "reload-problem-account-scroll");
        scrollPane.setFitToWidth(true);
        scrollPane.setMinHeight(PROBLEM_ACCOUNTS_MIN_LIST_HEIGHT);
        scrollPane.setPrefViewportHeight(problemAccountsListHeight(accountsBox));
        scrollPane.setMaxHeight(PROBLEM_ACCOUNTS_MAX_LIST_HEIGHT);
        return scrollPane;
    }

    private double problemAccountsListHeight(VBox accountsBox) {
        long selectableRows = accountsBox.getChildren().stream()
                .filter(CheckBox.class::isInstance)
                .count();
        long sectionRows = Math.max(1, accountsBox.getChildren().size() - selectableRows);
        double estimatedHeight = selectableRows * PROBLEM_ACCOUNTS_ROW_HEIGHT_ESTIMATE + sectionRows * 34 + 16;
        return Math.min(PROBLEM_ACCOUNTS_MAX_LIST_HEIGHT, Math.max(PROBLEM_ACCOUNTS_MIN_LIST_HEIGHT, estimatedHeight));
    }

    private void populateProblemAccountsBox(VBox accountsBox, List<Account> processedAccounts,
                                            Map<String, SummaryStatus> problematicAccounts) {
        addProblemAccountsSection(accountsBox, processedAccounts, problematicAccounts, SummaryLevel.BAD,
                I18n.tr("autoBadRed"), "reload-problem-level-bad");
        addProblemAccountsSection(accountsBox, processedAccounts, problematicAccounts, SummaryLevel.YELLOW,
                I18n.tr("autoYellowPartiallySuccessful"), "reload-problem-level-yellow");
    }

    private void addProblemAccountsSection(VBox accountsBox, List<Account> processedAccounts,
                                           Map<String, SummaryStatus> problematicAccounts, SummaryLevel level,
                                           String title, String styleClass) {
        List<Account> accounts = findProblemAccounts(processedAccounts, problematicAccounts, level);
        if (accounts.isEmpty()) {
            return;
        }
        Label label = new Label(title);
        label.getStyleClass().addAll("reload-problem-section-title", styleClass);
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

    private HBox buildProblemAccountsButtons(VBox accountsBox, Runnable closeAction) {
        Button deleteButton = new Button(I18n.tr("autoDeleteSelected"));
        deleteButton.getStyleClass().add("dangerous");
        deleteButton.setOnAction(e -> deleteSelectedProblemAccounts(accountsBox, closeAction));

        Button cancelButton = new Button(I18n.tr("autoCancel"));
        cancelButton.getStyleClass().add("reload-secondary-button");
        cancelButton.setOnAction(e -> closeAction.run());

        HBox buttons = new HBox(10, deleteButton, cancelButton);
        buttons.getStyleClass().add("reload-problem-actions");
        buttons.setAlignment(Pos.CENTER_RIGHT);
        return buttons;
    }

    private void deleteSelectedProblemAccounts(VBox accountsBox, Runnable closeAction) {
        List<Account> toDelete = selectedProblemAccounts(accountsBox);
        if (toDelete.isEmpty()) {
            return;
        }
        confirmDeleteProblemAccounts(toDelete).ifPresent(response -> {
            if (response == ButtonType.YES) {
                deleteAccountsAndRefresh(toDelete);
                closeAction.run();
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
        alert.setTitle(I18n.tr("commonConfirm"));
        alert.setHeaderText(I18n.tr("commonConfirm"));
        applyDialogThemeAndOrientation(alert);
        return ThemedDialogSupport.showAndWait(alert, ownerWindow());
    }

    private void deleteAccountsAndRefresh(List<Account> toDelete) {
        toDelete.forEach(a -> AccountService.getInstance().delete(a.getDbId()));
        if (onAccountsDeleted != null) {
            onAccountsDeleted.run();
        }
    }

    private void addProblemAccountsToDeleteBox(VBox accountsBox, List<Account> accounts, Map<String, SummaryStatus> problematicAccounts) {
        for (Account account : accounts) {
            SummaryStatus status = problematicAccounts.get(account.getDbId());
            String reasons = status == null || status.reasons.isEmpty()
                    ? I18n.tr("reloadNoReasonCaptured")
                    : String.join(" | ", status.reasons);
            CheckBox cb = new CheckBox();
            cb.getStyleClass().addAll("reload-problem-account-row", problemAccountStyleClass(status));
            cb.setAlignment(Pos.TOP_LEFT);
            cb.setGraphic(buildProblemAccountGraphic(account, reasons));
            cb.setMaxWidth(Double.MAX_VALUE);
            cb.setUserData(account);
            accountsBox.getChildren().add(cb);
        }
    }

    private String problemAccountStyleClass(SummaryStatus status) {
        SummaryLevel level = status == null ? SummaryLevel.BAD : status.level;
        return level == SummaryLevel.YELLOW ? "reload-problem-account-yellow" : "reload-problem-account-bad";
    }

    private Node buildProblemAccountGraphic(Account account, String reasons) {
        Label nameLabel = new Label(account.getAccountName());
        nameLabel.getStyleClass().add("reload-problem-account-name");
        nameLabel.setWrapText(true);
        nameLabel.setMinWidth(0);
        HBox.setHgrow(nameLabel, Priority.ALWAYS);

        Label typeLabel = new Label(account.getType().getDisplay());
        typeLabel.getStyleClass().add("reload-problem-account-type");
        typeLabel.setMinWidth(Region.USE_PREF_SIZE);

        HBox titleRow = new HBox(8, nameLabel, typeLabel);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);

        Label reasonLabel = new Label(reasons);
        reasonLabel.getStyleClass().add("reload-problem-account-reason");
        reasonLabel.setWrapText(true);
        reasonLabel.setMinWidth(0);
        reasonLabel.setMaxWidth(Double.MAX_VALUE);

        VBox text = new VBox(5, titleRow, reasonLabel);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox row = new HBox(text);
        row.getStyleClass().add("reload-problem-account-content");
        row.setAlignment(Pos.TOP_LEFT);
        row.setPadding(new Insets(0, 0, 0, 12));
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        return row;
    }

    private void prepareAccountLogPanels(List<Account> selectedAccounts) {
        runOnFxThreadAndWait(() -> {
            if (disposed) {
                return;
            }
            for (AccountLogPanel panel : accountLogPanels.values()) {
                panel.dispose();
            }
            accountLogPanels.clear();
            runAccountOrder.clear();
            latestSummaryLines.clear();
            latestAccountSummaries.clear();
            runOutcomeTracker.clear();
            ignoredFailureDomains.clear();
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
        int totalCensoredCategories = runOutcomeTracker.getTotalCensoredCategories();
        int totalCensoredChannels = runOutcomeTracker.getTotalCensoredChannels();
        if (processedAccounts.size() == 1 && totalCensoredCategories == 0 && totalCensoredChannels == 0) {
            latestSummaryLines.clear();
            return;
        }
        RunSummary summary = buildRunSummary(processedAccounts, finalStatuses);
        latestSummaryLines.clear();
        if (processedAccounts.size() > 1) {
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
        }
        if (totalCensoredCategories > 0) {
            latestSummaryLines.add("Overall censored categories: " + I18n.formatNumber(String.valueOf(totalCensoredCategories)));
        }
        if (totalCensoredChannels > 0) {
            latestSummaryLines.add("Overall censored channels: " + I18n.formatNumber(String.valueOf(totalCensoredChannels)));
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
        summaryBox.getStyleClass().add("reload-summary-box");

        Label title = new Label(I18n.tr("autoRunSummary"));
        title.getStyleClass().add("management-popup-section-title");
        summaryBox.getChildren().add(title);
        for (String line : latestSummaryLines) {
            summaryBox.getChildren().add(createDisplayLineNode(line, false));
        }
        return summaryBox;
    }

    private static Node createDisplayLineNode(String line, boolean logLine) {
        TextFlow censoredCountLine = createCensoredCountLine(line, logLine);
        if (censoredCountLine != null) {
            return censoredCountLine;
        }
        Label label = new Label(line);
        label.setWrapText(true);
        if (logLine) {
            label.getStyleClass().add(STYLE_CLASS_LOG_TEXT);
        }
        return label;
    }

    private static TextFlow createCensoredCountLine(String line, boolean logLine) {
        if (line == null) {
            return null;
        }
        Matcher matcher = DISPLAY_CENSORED_COUNT_PATTERN.matcher(line);
        if (!matcher.matches()) {
            return null;
        }
        Text prefix = createNormalDisplayText(matcher.group(1), logLine);
        Text count = new Text(matcher.group(2));
        count.getStyleClass().add(STYLE_CLASS_CENSORED_COUNT);

        TextFlow flow = new TextFlow(prefix, count);
        String suffix = matcher.group(3);
        if (!suffix.isEmpty()) {
            flow.getChildren().add(createNormalDisplayText(suffix, logLine));
        }
        flow.setMaxWidth(Double.MAX_VALUE);
        return flow;
    }

    private static Text createNormalDisplayText(String text, boolean logLine) {
        Text node = new Text(text);
        if (logLine) {
            node.getStyleClass().add(STYLE_CLASS_LOG_TEXT);
        } else {
            node.setStyle(STYLE_TEXT_BASE_FILL);
        }
        return node;
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
            if (disposed) {
                return;
            }
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
        if (disposed || account == null) {
            return;
        }
        Platform.runLater(() -> {
            if (disposed) {
                return;
            }
            AccountLogPanel panel = accountLogPanels.get(account.getDbId());
            if (panel != null) {
                panel.setStatus(status, channelCount);
            }
        });
    }

    private void logMessage(Account account, String message) {
        if (disposed || account == null || account.getDbId() == null || account.getDbId().isBlank()) {
            return;
        }
        String compact = compactLog(account, message);
        if (compact.isBlank()) {
            return;
        }
        runOutcomeTracker.recordMessage(account.getDbId(), message, compact);
        enqueueLogLine(account.getDbId(), compact);
    }

    private void enqueueLogLine(String accountId, String line) {
        if (disposed || accountId == null || accountId.isBlank() || line == null || line.isBlank()) {
            return;
        }
        synchronized (pendingLogLock) {
            while (pendingLogLines.size() >= MAX_PENDING_LOG_LINES) {
                pendingLogLines.removeFirst();
            }
            pendingLogLines.addLast(new PendingLogLine(accountId, line));
        }
        scheduleLogDrain();
    }

    private void scheduleLogDrain() {
        if (disposed) {
            return;
        }
        if (logDrainScheduled.compareAndSet(false, true)) {
            try {
                Platform.runLater(this::drainPendingLogLines);
            } catch (IllegalStateException _) {
                logDrainScheduled.set(false);
            }
        }
    }

    private void drainPendingLogLines() {
        logDrainScheduled.set(false);
        if (disposed) {
            clearPendingLogLines();
            return;
        }

        List<PendingLogLine> lines = new ArrayList<>();
        synchronized (pendingLogLock) {
            while (!pendingLogLines.isEmpty()) {
                lines.add(pendingLogLines.removeFirst());
            }
        }

        for (PendingLogLine pending : lines) {
            AccountLogPanel panel = accountLogPanels.get(pending.accountId());
            if (panel != null) {
                panel.appendLog(pending.line());
            }
        }

        synchronized (pendingLogLock) {
            if (!pendingLogLines.isEmpty()) {
                scheduleLogDrain();
            }
        }
    }

    private void clearPendingLogLines() {
        synchronized (pendingLogLock) {
            pendingLogLines.clear();
        }
        logDrainScheduled.set(false);
    }

    private void refreshAccountInfoTitle(Account account) {
        if (!canRefreshAccountInfoTitle(account)) {
            return;
        }
        String accountId = account.getDbId();
        if (!pendingAccountInfoRefreshes.add(accountId)) {
            return;
        }
        AccountInfo info = accountInfoService.getByAccountId(accountId);
        if (info == null) {
            pendingAccountInfoRefreshes.remove(accountId);
            return;
        }
        String statusValue = info.getAccountStatus() != null ? info.getAccountStatus().toDisplay() : "";
        String expiryValue = AccountInfoUiUtil.formatDate(info.getExpireDate());
        if (expiryValue.isBlank()) {
            expiryValue = "Unknown";
        }
        if (statusValue.isBlank()) {
            statusValue = "unknown";
        }
        AccountInfoUiUtil.StatusState statusStateValue = AccountInfoUiUtil.resolveStatusState(statusValue);
        AccountInfoUiUtil.ExpiryState expiryStateValue = info.getExpireDate() == null || info.getExpireDate().isBlank()
                ? AccountInfoUiUtil.ExpiryState.UNKNOWN
                : AccountInfoUiUtil.resolveExpiryState(info.getExpireDate());
        final String expiry = expiryValue;
        final String status = statusValue;
        final AccountInfoUiUtil.ExpiryState expiryState = expiryStateValue;
        final AccountInfoUiUtil.StatusState statusState = statusStateValue;
        try {
            Platform.runLater(() -> {
                try {
                    if (disposed) {
                        return;
                    }
                    AccountLogPanel panel = accountLogPanels.get(accountId);
                    if (panel != null) {
                        panel.updateAccountInfo(expiry, status, expiryState, statusState);
                    }
                } finally {
                    pendingAccountInfoRefreshes.remove(accountId);
                }
            });
        } catch (IllegalStateException _) {
            pendingAccountInfoRefreshes.remove(accountId);
        }
    }

    private boolean canRefreshAccountInfoTitle(Account account) {
        return !disposed
                && account != null
                && account.getType() == AccountType.STALKER_PORTAL
                && account.getDbId() != null
                && !account.getDbId().isBlank();
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
        String censoringTranslation = translateCensoringCountLog(trimmed);
        if (censoringTranslation != null) {
            return censoringTranslation;
        }
        String countTranslation = translateFoundCountLog(account, trimmed);
        if (countTranslation != null) {
            return countTranslation;
        }
        if (isSavedSuccessfullyLog(trimmed)) {
            return "";
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

    private String translateCensoringCountLog(String trimmed) {
        if (trimmed.startsWith("Censored Categories")) {
            Integer count = extractFirstNumber(trimmed);
            return count == null ? "Censored categories" : "Censored categories: " + I18n.formatNumber(String.valueOf(count));
        }
        if (trimmed.startsWith("Censored Channels")) {
            Integer count = extractFirstNumber(trimmed);
            return count == null ? "Censored channels" : "Censored channels: " + I18n.formatNumber(String.valueOf(count));
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
            return I18n.tr(TR_RELOAD_MODE_CATEGORY_LIST_CALL_FAILED, I18n.tr(TR_CATEGORY_TAB_LIVE_TV));
        }
        return null;
    }

    private SummaryStatus buildSummaryStatus(int fetchedChannelCount, boolean failed, List<String> issues,
                                             boolean acceptableZeroResult) {
        List<String> normalizedReasons = issues == null ? new ArrayList<>() : new ArrayList<>(issues);
        if (acceptableZeroResult && !failed) {
            normalizedReasons.clear();
            return new SummaryStatus(SummaryLevel.GOOD, fetchedChannelCount, normalizedReasons);
        }
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

    private GlobalFailureDecision promptCarryOnAfterGlobalFailure(Account account, String reason, boolean canIgnoreDomain) {
        if (isReloadStopped()) {
            return GlobalFailureDecision.STOP_ALL;
        }
        GlobalFailureDecision automaticDecision = resolveAutomaticGlobalFailureDecision(canIgnoreDomain);
        if (automaticDecision != null) {
            return automaticDecision;
        }
        final GlobalFailureDecision[] decision = {GlobalFailureDecision.CARRY_ON};
        boolean completed = runOnFxThreadAndWait(() -> {
            if (disposed) {
                decision[0] = GlobalFailureDecision.STOP_ALL;
                return;
            }
            GlobalFailurePrompt prompt = buildGlobalFailurePrompt(account, reason, canIgnoreDomain);
            decision[0] = ThemedDialogSupport.showAndWait(prompt.dialog(), ownerWindow())
                    .orElse(GlobalFailureDecision.MARK_BAD);
        });
        return completed ? decision[0] : GlobalFailureDecision.STOP_ALL;
    }

    private GlobalFailureDecision resolveAutomaticGlobalFailureDecision(boolean canIgnoreDomain) {
        GlobalFailureDecision decision = automaticGlobalFailureDecision;
        if (decision == GlobalFailureDecision.MARK_BAD_AND_IGNORE_DOMAIN && !canIgnoreDomain) {
            return GlobalFailureDecision.MARK_BAD;
        }
        return decision;
    }

    private GlobalFailurePrompt buildGlobalFailurePrompt(Account account, String reason, boolean canIgnoreDomain) {
        ToggleGroup optionGroup = new ToggleGroup();
        VBox optionBox = new VBox(6);
        optionBox.getStyleClass().add("reload-global-failure-options");
        optionBox.setFillWidth(true);
        for (AutomaticFailureDecisionOption option : globalFailureDecisionOptions(canIgnoreDomain)) {
            RadioButton optionButton = new RadioButton(option.label());
            optionButton.getStyleClass().add("reload-global-failure-option");
            optionButton.setToggleGroup(optionGroup);
            optionButton.setUserData(option.decision());
            optionButton.setWrapText(true);
            optionButton.setMaxWidth(Double.MAX_VALUE);
            optionBox.getChildren().add(optionButton);
        }
        if (!optionGroup.getToggles().isEmpty()) {
            optionGroup.selectToggle(optionGroup.getToggles().getFirst());
        }

        Label message = new Label(globalFailurePromptMessage(account, reason, canIgnoreDomain));
        message.getStyleClass().add("reload-global-failure-message");
        message.setWrapText(true);
        message.setMinWidth(0);
        message.setPrefWidth(GLOBAL_FAILURE_DIALOG_CONTENT_WIDTH);
        message.setMaxWidth(GLOBAL_FAILURE_DIALOG_CONTENT_WIDTH);
        message.setMinHeight(Region.USE_PREF_SIZE);

        VBox content = new VBox(10, message, optionBox);
        content.getStyleClass().add("reload-global-failure-content");
        content.setPadding(new Insets(
                GLOBAL_FAILURE_DIALOG_CONTENT_INSET,
                GLOBAL_FAILURE_DIALOG_CONTENT_INSET,
                0,
                GLOBAL_FAILURE_DIALOG_CONTENT_INSET
        ));
        content.setMinWidth(0);
        content.setPrefWidth(GLOBAL_FAILURE_DIALOG_CONTENT_WIDTH);
        content.setMaxWidth(GLOBAL_FAILURE_DIALOG_CONTENT_WIDTH);

        ButtonType applyButton = new ButtonType(reloadApplySelectedActionText(), ButtonBar.ButtonData.OK_DONE);
        Dialog<GlobalFailureDecision> dialog = new Dialog<>();
        dialog.setTitle(I18n.tr("autoConfirmation"));
        dialog.setHeaderText(I18n.tr("reloadGlobalCallFailure"));
        dialog.getDialogPane().setContent(content);
        dialog.getDialogPane().getButtonTypes().setAll(applyButton);
        dialog.setResultConverter(button -> button == applyButton
                ? selectedGlobalFailureDecision(optionGroup)
                : null);
        applyDialogThemeAndOrientation(dialog);
        dialog.getDialogPane().getStyleClass().add("reload-global-failure-dialog");
        dialog.getDialogPane().setPrefWidth(GLOBAL_FAILURE_DIALOG_PANE_WIDTH);
        Button apply = (Button) dialog.getDialogPane().lookupButton(applyButton);
        if (apply != null) {
            apply.setMinWidth(132);
        }
        return new GlobalFailurePrompt(dialog);
    }

    private String reloadApplySelectedActionText() {
        String label = I18n.tr("reloadApplySelectedAction");
        return "reloadApplySelectedAction".equals(label) ? "Apply Action" : label;
    }

    private List<AutomaticFailureDecisionOption> globalFailureDecisionOptions(boolean canIgnoreDomain) {
        List<AutomaticFailureDecisionOption> options = new ArrayList<>();
        options.add(new AutomaticFailureDecisionOption(GlobalFailureDecision.CARRY_ON, I18n.tr("reloadCarryOn")));
        if (canIgnoreDomain) {
            options.add(new AutomaticFailureDecisionOption(
                    GlobalFailureDecision.MARK_BAD_AND_IGNORE_DOMAIN,
                    I18n.tr("reloadMarkBadIgnoreDomain")
            ));
        }
        options.add(new AutomaticFailureDecisionOption(GlobalFailureDecision.MARK_BAD, I18n.tr("reloadMarkBadAndNext")));
        options.add(new AutomaticFailureDecisionOption(GlobalFailureDecision.STOP_ALL, I18n.tr("reloadStopAll")));
        return options;
    }

    private GlobalFailureDecision selectedGlobalFailureDecision(ToggleGroup optionGroup) {
        Toggle selected = optionGroup == null ? null : optionGroup.getSelectedToggle();
        return selected != null && selected.getUserData() instanceof GlobalFailureDecision selectedDecision
                ? selectedDecision
                : GlobalFailureDecision.MARK_BAD;
    }

    private String globalFailurePromptMessage(Account account, String reason, boolean canIgnoreDomain) {
        return canIgnoreDomain
                ? I18n.tr("reloadGlobalFailurePromptWithIgnoreDomain", account.getAccountName(), reason)
                : I18n.tr("reloadGlobalFailurePrompt", account.getAccountName(), reason);
    }

    private void applyDialogThemeAndOrientation(Dialog<?> dialog) {
        ThemedDialogSupport.prepare(dialog, ownerWindow(), "uiptv-alert-dialog");
    }

    private javafx.stage.Window ownerWindow() {
        return ThemedDialogSupport.activeOwnerWindow();
    }

    private record GlobalFailurePrompt(Dialog<GlobalFailureDecision> dialog) {
    }

    private record AutomaticFailureDecisionOption(GlobalFailureDecision decision, String label) {
        @Override
        public String toString() {
            return label;
        }
    }

    private String resolveRepeatFailureDomain(Account account) {
        if (!canIgnoreDomainFailures(account)) {
            return null;
        }
        String raw = switch (account.getType()) {
            case STALKER_PORTAL, XTREME_API -> account.getUrl();
            case M3U8_URL -> account.getM3u8Path();
            default -> null;
        };
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String trimmed = raw.trim();
        String host = null;
        try {
            host = URI.create(trimmed).getHost();
        } catch (Exception _) {
            // URI is strict about underscores and other characters
        }

        if (host == null || host.isBlank()) {
            host = extractHostManually(trimmed);
        }

        return host != null && !host.isBlank()
                ? host.toLowerCase(java.util.Locale.ROOT)
                : trimmed.toLowerCase(java.util.Locale.ROOT);
    }

    private String extractHostManually(String url) {
        int protoEnd = url.indexOf("://");
        if (protoEnd == -1) {
            return null;
        }
        String afterProto = url.substring(protoEnd + 3);
        int slashIndex = afterProto.indexOf('/');
        int colonIndex = afterProto.indexOf(':');
        int end;
        if (slashIndex != -1 && colonIndex != -1) {
            end = Math.min(slashIndex, colonIndex);
        } else if (slashIndex != -1) {
            end = slashIndex;
        } else {
            end = colonIndex;
        }
        return end == -1 ? afterProto : afterProto.substring(0, end);
    }

    private boolean canIgnoreDomainFailures(Account account) {
        if (account == null || account.getType() == null) {
            return false;
        }
        return switch (account.getType()) {
            case XTREME_API -> !isGroupedXtremeAccount(account);
            case STALKER_PORTAL -> !isGroupedStalkerAccount(account);
            case M3U8_URL -> true;
            default -> false;
        };
    }

    private boolean isGroupedXtremeAccount(Account account) {
        return com.uiptv.util.XtremeCredentialsJson.parse(account.getXtremeCredentialsJson()).size() > 1;
    }

    private boolean isGroupedStalkerAccount(Account account) {
        String macs = account.getMacAddressList();
        if (macs == null || macs.isBlank()) {
            macs = account.getMacAddress();
        }
        if (macs == null || macs.isBlank()) {
            return false;
        }
        return java.util.Arrays.stream(macs.split(","))
                .map(String::trim)
                .filter(s -> !s.isBlank())
                .limit(2)
                .count() > 1;
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
                    ? I18n.tr("reloadModeChannelsNoneFound", I18n.tr(TR_CATEGORY_TAB_LIVE_TV))
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
            case "ITV" -> I18n.tr(TR_CATEGORY_TAB_LIVE_TV);
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


    private boolean runOnFxThreadAndWait(Runnable runnable) {
        if (runnable == null || disposed) {
            return false;
        }
        if (Platform.isFxApplicationThread()) {
            runnable.run();
            return true;
        }
        CountDownLatch latch = new CountDownLatch(1);
        try {
            Platform.runLater(() -> {
                try {
                    if (!disposed) {
                        runnable.run();
                    }
                } finally {
                    latch.countDown();
                }
            });
        } catch (IllegalStateException _) {
            return false;
        }
        try {
            latch.await();
            return !Thread.currentThread().isInterrupted();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    private static final class AccountLogPanel {
        private final Account account;
        private final VBox root = new VBox(6);
        private final HBox header = new HBox(8);
        private final Label accountLabel = new Label();
        private final Label expiryLabel = new Label();
        private final Label statusLabel = new Label();
        private final Region expiryIndicator = new Region();
        private final Region statusIndicator = new Region();
        private final HBox expiryBox = new HBox(6);
        private final HBox statusBox = new HBox(6);
        private final HBox accountInfoBox = new HBox(8);
        private final Label runStatusLabel = new Label();
        private final Label arrowLabel = new Label("▸");
        private final ProgressIndicator runningIndicator = new ProgressIndicator();
        private final VBox logBody = new VBox(4);
        private final List<String> logs = new ArrayList<>();

        private AccountLogPanel(Account account) {
            this.account = account;
            this.accountLabel.setText(buildBaseLabel());
            this.accountLabel.getStyleClass().add("reload-account-title");

            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);

            this.header.setAlignment(Pos.CENTER_LEFT);
            this.header.getStyleClass().add("reload-log-header");
            this.runningIndicator.setMaxSize(14, 14);
            this.runningIndicator.getStyleClass().add("reload-running-indicator");
            this.runningIndicator.setVisible(false);
            this.runningIndicator.managedProperty().bind(this.runningIndicator.visibleProperty());

            setupIndicator(expiryIndicator);
            setupIndicator(statusIndicator);
            expiryBox.setAlignment(Pos.CENTER_LEFT);
            statusBox.setAlignment(Pos.CENTER_LEFT);
            expiryBox.getChildren().setAll(expiryIndicator, expiryLabel);
            statusBox.getChildren().setAll(statusIndicator, statusLabel);
            expiryBox.getStyleClass().add("reload-account-info-chip");
            statusBox.getStyleClass().add("reload-account-info-chip");
            accountInfoBox.setAlignment(Pos.CENTER_LEFT);
            accountInfoBox.getChildren().setAll(expiryBox, statusBox);

            this.header.getChildren().addAll(accountLabel, accountInfoBox, spacer, runningIndicator, runStatusLabel, arrowLabel);

            this.logBody.getStyleClass().add("reload-log-body");
            this.root.getChildren().addAll(header, logBody);
            this.root.getStyleClass().add("reload-log-root");

            this.header.setOnMouseClicked(event -> setExpanded(!logBody.isVisible()));
        }

        private String getAccountLabel() {
            return accountLabel.getText();
        }

        private String buildBaseLabel() {
            return account.getAccountName() + " (" + account.getType().getDisplay() + ")";
        }

        private void updateAccountInfo(String expiry, String status,
                                       AccountInfoUiUtil.ExpiryState expiryState,
                                       AccountInfoUiUtil.StatusState statusState) {
            String expiryText = I18n.tr("manageAccountInfoExpireDate") + ": " + expiry;
            String statusText = I18n.tr("manageAccountInfoStatus") + ": " + status;
            expiryLabel.setText(expiryText);
            statusLabel.setText(statusText);
            AccountInfoUiUtil.applyIndicator(expiryIndicator, AccountInfoUiUtil.colorForExpiry(expiryState),
                    expiryState != AccountInfoUiUtil.ExpiryState.UNKNOWN);
            AccountInfoUiUtil.applyIndicator(statusIndicator, AccountInfoUiUtil.colorForStatus(statusState),
                    statusState != AccountInfoUiUtil.StatusState.UNKNOWN);
            expiryBox.setVisible(true);
            expiryBox.setManaged(true);
            statusBox.setVisible(true);
            statusBox.setManaged(true);
        }

        private void setupIndicator(Region indicator) {
            indicator.setMinSize(8, 8);
            indicator.setPrefSize(8, 8);
            indicator.setMaxSize(8, 8);
            indicator.setStyle("-fx-background-radius: 6px;");
            indicator.setVisible(false);
            indicator.setManaged(false);
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
            while (logs.size() >= MAX_LOG_LINES_PER_ACCOUNT) {
                logs.remove(0);
                if (!logBody.getChildren().isEmpty()) {
                    logBody.getChildren().remove(0);
                }
            }
            logs.add(line);
            logBody.getChildren().add(createDisplayLineNode(line, true));
        }

        private void dispose() {
            logs.clear();
            header.setOnMouseClicked(null);
            runningIndicator.managedProperty().unbind();
            logBody.getChildren().clear();
            header.getChildren().clear();
            expiryBox.getChildren().clear();
            statusBox.getChildren().clear();
            accountInfoBox.getChildren().clear();
            root.getChildren().clear();
        }

        private void setStatus(AccountRunStatus status, Integer channelCount) {
            switch (status) {
                case QUEUED:
                    runningIndicator.setVisible(false);
                    runStatusLabel.setText(I18n.tr("autoQueued"));
                    setRunStatusStyle("reload-status-queued");
                    break;
                case RUNNING:
                    runningIndicator.setVisible(true);
                    runStatusLabel.setText(I18n.tr("autoRunning"));
                    setRunStatusStyle("reload-status-running");
                    break;
                case DONE:
                    runningIndicator.setVisible(false);
                    runStatusLabel.setText(channelCount == null
                            ? I18n.tr("reloadDone")
                            : I18n.tr("reloadDoneWithChannels", channelCount));
                    setRunStatusStyle("reload-status-done");
                    break;
                case YELLOW:
                    runningIndicator.setVisible(false);
                    runStatusLabel.setText(channelCount == null
                            ? I18n.tr("reloadPartial")
                            : I18n.tr("reloadPartialWithChannels", channelCount));
                    setRunStatusStyle("reload-status-yellow");
                    break;
                case EMPTY:
                    runningIndicator.setVisible(false);
                    runStatusLabel.setText(I18n.tr("autoEmpty0Channels"));
                    setRunStatusStyle("reload-status-yellow");
                    break;
                case FAILED:
                    runningIndicator.setVisible(false);
                    runStatusLabel.setText(I18n.tr("autoFailed2"));
                    setRunStatusStyle("reload-status-bad");
                    break;
                default:
                    runningIndicator.setVisible(false);
                    runStatusLabel.setText("");
                    setRunStatusStyle(null);
                    break;
            }
        }

        private void setStatus(AccountRunStatus status, Integer current, Integer total) {
            if (status == AccountRunStatus.RUNNING && current != null && total != null) {
                runningIndicator.setVisible(true);
                runStatusLabel.setText(I18n.tr("autoRunningProgress", current, total));
                setRunStatusStyle("reload-status-running");
            } else {
                setStatus(status, (Integer) null);
            }
        }

        private void setRunStatusStyle(String statusStyleClass) {
            runStatusLabel.getStyleClass().setAll("reload-status-label");
            if (statusStyleClass != null) {
                runStatusLabel.getStyleClass().add(statusStyleClass);
            }
        }
    }

    private static final class SkipAccountReloadException extends RuntimeException {
    }
}
