package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.model.AccountInfo;
import com.uiptv.model.AccountMediaContext;
import com.uiptv.model.AccountView;
import com.uiptv.model.Category;
import com.uiptv.service.AccountChangeListener;
import com.uiptv.service.AccountInfoService;
import com.uiptv.service.AccountResolver;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ChannelService;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.AccountType;
import com.uiptv.util.I18n;
import com.uiptv.widget.AppHeaderActions;
import com.uiptv.widget.AppPageHeader;
import com.uiptv.widget.InlinePanelService;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.PlayMenuButton;
import com.uiptv.widget.ResponsiveCardGrid;
import com.uiptv.widget.SearchFieldBehavior;
import com.uiptv.widget.SearchableFilterableTableView;
import javafx.application.Platform;
import javafx.application.HostServices;
import javafx.beans.property.SimpleStringProperty;
import javafx.beans.value.ChangeListener;
import javafx.collections.FXCollections;
import javafx.collections.ListChangeListener;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Stage;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.CACHE_SUPPORTED;
import static com.uiptv.model.Account.VOD_AND_SERIES_SUPPORTED;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class AccountListUI extends HBox implements SearchTarget {
    private static final String MULTI_SELECTION_DISABLED_KEY = "autoThisActionIsDisabledForMultipleSelections";
    private static final double GRID_NORMAL_CARD_MIN_WIDTH = 300;
    private static final double GRID_NORMAL_CARD_MAX_WIDTH = 430;
    private static final double GRID_COMPACT_CARD_MIN_WIDTH = 190;
    private static final double GRID_COMPACT_CARD_MAX_WIDTH = 270;
    private static final double GRID_DRAWER_CARD_MIN_WIDTH = 230;
    private static final double GRID_DRAWER_CARD_MAX_WIDTH = 380;
    private static final double GRID_NORMAL_VERTICAL_GAP = 14;
    private static final double GRID_PLAIN_TEXT_VERTICAL_GAP = 6;
    private static final double GRID_NORMAL_CARD_MIN_HEIGHT = 76;
    private static final double GRID_PLAIN_TEXT_CARD_MIN_HEIGHT = 42;
    private static final double FILTER_TOOLBAR_GAP = 8;
    private static final double ACCOUNT_BROWSER_SINGLE_PANE_WIDTH = 720;
    private static final double ACCOUNT_BROWSER_COLUMN_GAP = 4;
    private static final AccountExpiry EMPTY_ACCOUNT_EXPIRY =
            new AccountExpiry("", AccountInfoUiUtil.ExpiryState.UNKNOWN);
    private static final String ICON_SORT = "M3 18H9V16H3V18ZM3 6V8H21V6H3ZM3 13H15V11H3V13Z";
    private static final String ICON_ADD = "M11 5H13V11H19V13H13V19H11V13H5V11H11V5Z";
    private static final Comparator<AccountItem> ACCOUNT_NAME_COMPARATOR =
            Comparator.comparing(AccountItem::getAccountName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AccountItem::getAccountName);
    private final TableColumn<AccountItem, String> accountName = new TableColumn<>(I18n.tr("accountListTitle"));
    private final AccountResolver accountResolver = new AccountResolver();
    private final HostServices hostServices;
    private final Runnable themeToggleHandler;
    private final VBox pageContainer = new VBox(12);
    private final HBox bodyLayout = new HBox();
    private final VBox bodyContainer = new VBox();
    private final VBox listView = new VBox(5);
    private final VBox detailView = new VBox(8);
    private final HBox navHeader = new HBox(6);
    private final Button backButton = createBackButton();
    private final VBox detailContent = new VBox();
    private final ResponsiveCardGrid<AccountItem> accountGrid = new ResponsiveCardGrid<>(this::createAccountCard);
    private final ScrollPane accountScrollPane = new ScrollPane();
    private final HBox browserLayout = new HBox(ACCOUNT_BROWSER_COLUMN_GAP);
    private final PillBar<AccountTypeFilter> accountTypePillBar =
            new PillBar<>(AccountTypeFilter::label, AccountTypeFilter::key, null, AccountTypeFilter::compactLabel);
    private final Deque<Node> viewStack = new ArrayDeque<>();
    private final VBox embeddedContainer = new VBox();
    SearchableFilterableTableView table = new SearchableFilterableTableView();
    AccountService accountService = AccountService.getInstance();
    private Node currentContent;
    private Callback<Object> onEditCallback;
    private Callback<Object> onExplicitEditCallback;
    private Callback<Object> onNewAccountCallback;
    private Callback<Object> onDeleteCallback;
    private final ObservableList<AccountItem> masterAccountItems = FXCollections.observableArrayList();
    private AccountSortMode accountSortMode = AccountSortMode.DEFAULT;
    private final AccountChangeListener accountChangeListener = revision -> Platform.runLater(this::refreshIfAttached);
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = this::onThumbnailModeChanged;
    private AppPageHeader pageHeader;
    private MenuButton accountSortButton;
    private VBox accountToolbar;
    private CategoryListUI activeCategoryListUI;
    private Node leadingBodyContent;
    private final ChangeListener<Boolean> leadingBodyContentVisibilityListener =
            (_, _, _) -> updateBodyLayoutChildren();
    private final AtomicLong refreshGeneration = new AtomicLong();
    private boolean refreshPending = true;
    private boolean mediaDrawerMode;
    private boolean leadingBodyContentExclusive;
    private boolean thumbnailListenerRegistered;
    private boolean accountBrowserCompact;
    private boolean activeCategoryBrowserRetainedForDetailView;
    private HeaderSearchMode headerSearchMode = HeaderSearchMode.ACCOUNTS;
    private boolean updatingHeaderSearchText;
    private String accountHeaderSearchText = "";
    private String browserHeaderSearchText = "";

    public AccountListUI(HostServices hostServices, Runnable themeToggleHandler) {
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        initWidgets();
        // Don't load accounts on startup - load lazily when visible
        registerVisibilityListener();
        registerThumbnailModeListener();
    }

    private void registerVisibilityListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene != null) {
                accountService.addChangeListener(accountChangeListener);
                refreshIfVisible();
            } else {
                accountService.removeChangeListener(accountChangeListener);
            }
        });
        visibleProperty().addListener((_, _, visible) -> {
            if (Boolean.TRUE.equals(visible)) {
                refreshIfVisible();
            }
        });
    }

    private void refreshIfAttached() {
        if (getScene() != null && isVisible()) {
            refresh();
        } else {
            markRefreshPending();
        }
    }

    private void refreshIfVisible() {
        if (getScene() != null && isVisible() && refreshPending) {
            refresh();
        }
    }

    private void markRefreshPending() {
        refreshPending = true;
        refreshGeneration.incrementAndGet();
    }

    public void addUpdateCallbackHandler(Callback<Object> onEditCallback) {
        this.onEditCallback = onEditCallback;
    }

    public void addExplicitEditCallbackHandler(Callback<Object> onExplicitEditCallback) {
        this.onExplicitEditCallback = onExplicitEditCallback;
    }

    public void addNewAccountCallbackHandler(Callback<Object> onNewAccountCallback) {
        this.onNewAccountCallback = onNewAccountCallback;
    }

    public void addDeleteCallbackHandler(Callback<Object> onDeleteCallback) {
        this.onDeleteCallback = onDeleteCallback;
    }

    public void setMediaDrawerMode(boolean enabled) {
        if (mediaDrawerMode == enabled) {
            if (enabled && activeCategoryListUI != null && currentContent == listView) {
                showAccountBrowser(activeCategoryListUI);
            }
            scrollFocusedContentIntoView();
            return;
        }
        boolean wasSingleLine = useSingleLineAccountRows();
        mediaDrawerMode = enabled;
        setMinWidth(0);
        updateMediaDrawerStyle();
        refreshAccountGridIfRowModeChanged(wasSingleLine);
        if (activeCategoryListUI != null) {
            activeCategoryListUI.setMediaDrawerMode(enabled);
            if (currentContent == browserLayout || currentContent == activeCategoryListUI) {
                showAccountBrowser(activeCategoryListUI);
                scrollFocusedContentIntoView();
                return;
            }
            if (enabled && currentContent == listView) {
                showAccountBrowser(activeCategoryListUI);
                scrollFocusedContentIntoView();
                return;
            }
        }
        if (currentContent == listView) {
            setAccountBrowserCompact(false);
        }
        scrollFocusedContentIntoView();
    }

    public void scrollFocusedContentIntoView() {
        accountGrid.scrollFocusedItemIntoView();
        if (activeCategoryListUI != null) {
            activeCategoryListUI.scrollFocusedContentIntoView();
        }
    }

    public boolean handleActiveChannelNavigationKey(KeyEvent event) {
        if (!mediaDrawerMode || activeCategoryListUI == null || event == null || getScene() == null || !isVisible()) {
            return false;
        }
        return activeCategoryListUI.handleActiveChannelNavigationKey(event);
    }

    public void requestContentFocus() {
        Platform.runLater(() -> {
            if (!isNodeDisplayable(accountGrid)) {
                return;
            }
            accountGrid.requestContentFocus();
        });
    }

    private boolean isNodeDisplayable(Node node) {
        if (node == null || node.getScene() == null) {
            return false;
        }
        Node current = node;
        while (current != null) {
            if (!current.isVisible()) {
                return false;
            }
            current = current.getParent();
        }
        return true;
    }

    public void refresh() {
        if (!Platform.isFxApplicationThread()) {
            Platform.runLater(this::refresh);
            return;
        }
        if (getScene() != null && !isVisible()) {
            markRefreshPending();
            return;
        }
        refreshPending = false;
        long generation = refreshGeneration.incrementAndGet();
        List<AccountItem> catList = new ArrayList<>();

        List<AccountResolver.AccountRow> resolved = accountResolver.resolveAccounts();
        for (int index = 0; index < resolved.size(); index++) {
            AccountResolver.AccountRow account = resolved.get(index);
            catList.add(new AccountItem(
                    new SimpleStringProperty(account.getAccountName()),
                    new SimpleStringProperty(account.getDbId()),
                    new SimpleStringProperty(account.getType()),
                    account.isPinToTop(),
                    index,
                    0,
                    0
            ));
        }
        masterAccountItems.setAll(catList);
        applyAccountOrdering();
        loadAccountMetricsAsync(generation, resolved);
    }

    private void loadAccountMetricsAsync(long generation, List<AccountResolver.AccountRow> rows) {
        List<AccountResolver.AccountRow> safeRows = rows == null ? List.of() : List.copyOf(rows);
        Thread metricsThread = new Thread(() -> {
            List<AccountItem> updatedItems = new ArrayList<>();
            for (int index = 0; index < safeRows.size(); index++) {
                if (generation != refreshGeneration.get()) {
                    return;
                }
                AccountResolver.AccountRow row = safeRows.get(index);
                AccountMetrics metrics = cachedAccountMetrics(accountViewForMetrics(row.getDbId()));
                AccountExpiry expiry = cachedAccountExpiry(row.getDbId());
                updatedItems.add(new AccountItem(
                        new SimpleStringProperty(row.getAccountName()),
                        new SimpleStringProperty(row.getDbId()),
                        new SimpleStringProperty(row.getType()),
                        row.isPinToTop(),
                        index,
                        metrics.categoryCount(),
                        metrics.channelCount(),
                        expiry.text(),
                        expiry.state()
                ));
            }
            Platform.runLater(() -> {
                if (generation != refreshGeneration.get()) {
                    return;
                }
                masterAccountItems.setAll(updatedItems);
                applyAccountOrdering();
            });
        }, "account-metrics-loader");
        metricsThread.setDaemon(true);
        metricsThread.start();
    }

    private AccountView accountViewForMetrics(String accountId) {
        Account source;
        try {
            source = accountService.getById(accountId);
        } catch (Exception _) {
            return null;
        }
        if (source == null) {
            return null;
        }
        return AccountView.from(source);
    }

    private AccountMetrics cachedAccountMetrics(AccountView account) {
        if (account == null || account.dbId() == null || account.dbId().isBlank()) {
            return new AccountMetrics(0, 0);
        }
        try {
            int categoryCount = cachedCategoryCount(account);
            int channelCount = ChannelService.getInstance().getChannelCountForAccount(account.dbId());
            return new AccountMetrics(categoryCount, Math.max(0, channelCount));
        } catch (Exception _) {
            return new AccountMetrics(0, 0);
        }
    }

    private AccountExpiry cachedAccountExpiry(String accountId) {
        if (accountId == null || accountId.isBlank()) {
            return EMPTY_ACCOUNT_EXPIRY;
        }
        try {
            return accountExpiry(AccountInfoService.getInstance().getByAccountId(accountId));
        } catch (Exception _) {
            return EMPTY_ACCOUNT_EXPIRY;
        }
    }

    private AccountExpiry accountExpiry(AccountInfo info) {
        String rawExpiry = info == null ? "" : Objects.toString(info.getExpireDate(), "").trim();
        if (rawExpiry.isBlank() || rawExpiry.startsWith("0000-00-00")) {
            return EMPTY_ACCOUNT_EXPIRY;
        }
        String compactDate = AccountInfoUiUtil.formatCompactDate(rawExpiry);
        if (compactDate.isBlank()) {
            return EMPTY_ACCOUNT_EXPIRY;
        }
        return new AccountExpiry(compactDate, AccountInfoUiUtil.resolveExpiryState(rawExpiry));
    }

    private int cachedCategoryCount(AccountView account) {
        int count = cachedCategoryCount(account, Account.AccountAction.itv);
        if (account != null && VOD_AND_SERIES_SUPPORTED.contains(account.type())) {
            count += cachedCategoryCount(account, Account.AccountAction.vod);
            count += cachedCategoryCount(account, Account.AccountAction.series);
        }
        return Math.max(0, count);
    }

    private int cachedCategoryCount(AccountView account, Account.AccountAction action) {
        if (account == null || action == null) {
            return 0;
        }
        Account modeAccount = new AccountMediaContext(account, action).toAccount();
        return CategoryService.getInstance().getCached(modeAccount).size();
    }

    private void initWidgets() {
        setSpacing(0);
        setPadding(Insets.EMPTY);
        setFillHeight(true);
        getStyleClass().add("account-page-root");
        table.setEditable(true);
        table.getColumns().add(accountName);
        accountName.setVisible(true);
        accountName.setSortType(TableColumn.SortType.ASCENDING);
        accountName.setSortable(false);
        accountName.setCellValueFactory(cellData -> cellData.getValue().accountNameProperty());
        accountName.setCellFactory(_ -> createAccountNameCell());
        installAccountHeaderSortHandler();

        HBox.setHgrow(table.getTextField(), Priority.ALWAYS);
        table.getTextField().setMinWidth(120);
        table.getTextField().setMaxWidth(Double.MAX_VALUE);

        configureAccountTypePillBar();
        pageHeader = createPageHeader();
        accountToolbar = createAccountToolbar();
        configurePageContainers();
        configureAccountGrid();
        configureAccountScrollPane();
        configureBrowserLayout();
        listView.getChildren().setAll(accountToolbar, createSearchRow(), accountScrollPane);
        getChildren().setAll(pageContainer);

        initDetailView();
        showAccountListView();
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(accountGrid, Priority.ALWAYS);
        VBox.setVgrow(accountScrollPane, Priority.ALWAYS);
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);
        HBox.setHgrow(pageContainer, Priority.ALWAYS);
        HBox.setHgrow(listView, Priority.ALWAYS);
        listView.setMaxHeight(Double.MAX_VALUE);
        listView.setMinHeight(0);
        detailView.setMaxHeight(Double.MAX_VALUE);
        detailView.setMinHeight(0);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        table.getTextField().textProperty().addListener((_, _, text) -> handleHeaderSearchTextChanged(text));
        widthProperty().addListener((_, _, _) -> updateActiveAccountBrowserLayout());
        registerSceneCleanupListener();
    }

    private void configureAccountTypePillBar() {
        List<AccountTypeFilter> filters = new ArrayList<>();
        filters.add(new AccountTypeFilter("all", I18n.tr("commonAll"), null));
        for (AccountType type : AccountType.values()) {
            filters.add(new AccountTypeFilter(type.name(), type.getDisplay(), type));
        }
        accountTypePillBar.setItems(filters);
        accountTypePillBar.selectedItemProperty().addListener((_, _, _) -> applyAccountOrdering());
        accountTypePillBar.setNarrowReservedRowCount(2);
        accountTypePillBar.setMaxWidth(Double.MAX_VALUE);
    }

    private void configureAccountGrid() {
        accountGrid.getStyleClass().add("account-card-grid");
        accountGrid.setCardWidthRange(GRID_NORMAL_CARD_MIN_WIDTH, GRID_NORMAL_CARD_MAX_WIDTH);
        applyAccountGridDisplayMode(ThumbnailAwareUI.areThumbnailsEnabled());
        accountGrid.setPlaceholderText(I18n.tr("autoNothingFoundFor", I18n.tr("autoAccount")));
        accountGrid.setOnItemActivated(item -> retrieveThreadedAccountCategories(item, itv));
        accountGrid.setContextMenuFactory((item, selectedItems, owner) -> createAccountContextMenu(item, selectedItems, owner));
        accountGrid.getSelectedItems().addListener((ListChangeListener<AccountItem>) _ -> handleAccountGridSelectionChanged());
        accountGrid.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                handleDeleteAccounts();
                event.consume();
            } else if (event.getCode() == KeyCode.ENTER) {
                retrieveThreadedAccountCategories(accountGrid.getFocusedItem(), itv);
                event.consume();
            }
        });
    }

    private void configureAccountScrollPane() {
        accountScrollPane.getStyleClass().addAll("account-list-scroll", "transparent-scroll-pane");
        accountScrollPane.setContent(accountGrid);
        accountScrollPane.setFitToWidth(true);
        accountScrollPane.setPannable(true);
        accountScrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        accountScrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        accountScrollPane.setFocusTraversable(false);
        accountScrollPane.setMinSize(0, 0);
        accountScrollPane.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private void configureBrowserLayout() {
        browserLayout.getStyleClass().add("account-browser-layout");
        browserLayout.setFillHeight(true);
        browserLayout.setMinSize(0, 0);
        browserLayout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    public AppPageHeader detachPageHeader() {
        if (pageHeader == null) {
            return null;
        }
        detachFromParent(pageHeader);
        pageContainer.getStyleClass().add("account-list-page-compact");
        return pageHeader;
    }

    private AppPageHeader createPageHeader() {
        HBox headerActions = new HBox(6, new AppHeaderActions(hostServices, themeToggleHandler, null));
        headerActions.setAlignment(Pos.CENTER_RIGHT);
        return new AppPageHeader(
                I18n.tr("autoAccount"),
                headerActions
        );
    }

    private VBox createAccountToolbar() {
        accountTypePillBar.setMaxWidth(Double.MAX_VALUE);
        HBox actions = createAccountToolbarActions();
        HBox inlineRow = createInlineFilterToolbarRow(accountTypePillBar, actions);
        VBox toolbar = new VBox(FILTER_TOOLBAR_GAP);
        toolbar.getStyleClass().add("account-toolbar");
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setFillWidth(true);
        toolbar.setMinWidth(0);
        toolbar.setMaxWidth(Double.MAX_VALUE);
        toolbar.getChildren().setAll(accountTypePillBar, actions);
        toolbar.widthProperty().addListener((_, _, _) -> applyResponsiveFilterToolbarLayout(toolbar, inlineRow, accountTypePillBar, actions));
        Platform.runLater(() -> applyResponsiveFilterToolbarLayout(toolbar, inlineRow, accountTypePillBar, actions));
        return toolbar;
    }

    private HBox createSearchRow() {
        table.getTextField().setPromptText(I18n.tr("commonSearch"));
        table.getTextField().getStyleClass().add("uiptv-page-search-field");
        table.getTextField().setMinWidth(0);
        table.getTextField().setPrefWidth(420);
        table.getTextField().setMaxWidth(Double.MAX_VALUE);
        SearchFieldBehavior.installMouseClear(table.getTextField());

        HBox searchRow = new HBox(table.getTextField());
        searchRow.setAlignment(Pos.CENTER_LEFT);
        searchRow.setFillHeight(false);
        searchRow.setMinWidth(0);
        searchRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(table.getTextField(), Priority.ALWAYS);
        searchRow.getStyleClass().add("account-search-row");
        return searchRow;
    }

    private HBox createInlineFilterToolbarRow(PillBar<?> pillBar, HBox actions) {
        HBox row = new HBox(FILTER_TOOLBAR_GAP, pillBar, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pillBar, Priority.ALWAYS);
        return row;
    }

    private void applyResponsiveFilterToolbarLayout(VBox row, HBox inlineRow, PillBar<?> pillBar, HBox actions) {
        boolean useInline = shouldUseInlineFilterToolbar(row.getWidth(), actions);
        boolean inlineApplied = row.getChildren().size() == 1 && row.getChildren().getFirst() == inlineRow;
        if (useInline == inlineApplied) {
            return;
        }
        if (useInline) {
            pillBar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(pillBar, Priority.ALWAYS);
            row.getChildren().clear();
            inlineRow.getChildren().setAll(pillBar, actions);
            row.getChildren().setAll(inlineRow);
        } else {
            pillBar.setMaxWidth(PillBar.COMPACT_DROPDOWN_PREF_WIDTH);
            HBox.setHgrow(pillBar, Priority.NEVER);
            inlineRow.getChildren().clear();
            row.getChildren().setAll(pillBar, actions);
        }
    }

    private boolean shouldUseInlineFilterToolbar(double width, HBox actions) {
        if (width <= 0) {
            return false;
        }
        return width >= actions.prefWidth(-1) + FILTER_TOOLBAR_GAP + PillBar.COMPACT_DROPDOWN_MIN_WIDTH;
    }

    private HBox createAccountToolbarActions() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, createAccountSortButton(), createAddAccountToolbarButton());
        actions.getStyleClass().add("list-toolbar-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setFillHeight(false);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.setMaxWidth(Double.MAX_VALUE);
        return actions;
    }

    private void configurePageContainers() {
        pageContainer.getStyleClass().add("account-list-page");
        pageContainer.setFillWidth(true);
        pageContainer.setMinSize(0, 0);
        pageContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        bodyLayout.getStyleClass().add("account-body-layout");
        bodyLayout.setSpacing(ACCOUNT_BROWSER_COLUMN_GAP);
        bodyLayout.setFillHeight(true);
        bodyLayout.setMinSize(0, 0);
        bodyLayout.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        bodyContainer.getStyleClass().add("account-body");
        bodyContainer.setFillWidth(true);
        bodyContainer.setMinSize(0, 0);
        bodyContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        listView.getStyleClass().add("account-list-panel");
        listView.setFillWidth(true);
        listView.setMinSize(0, 0);
        listView.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        embeddedContainer.getStyleClass().add("account-embedded-stack");
        embeddedContainer.setFillWidth(true);
        embeddedContainer.setMinSize(0, 0);
        embeddedContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        HBox.setHgrow(bodyContainer, Priority.ALWAYS);
        VBox.setVgrow(bodyLayout, Priority.ALWAYS);
        updateBodyLayoutChildren();
        pageContainer.getChildren().setAll(pageHeader, bodyLayout);
    }

    public void setLeadingBodyContent(Node content) {
        if (leadingBodyContent == content) {
            return;
        }
        detachLeadingBodyContent();
        leadingBodyContent = content;
        if (leadingBodyContent != null) {
            detachFromParent(leadingBodyContent);
            leadingBodyContent.visibleProperty().addListener(leadingBodyContentVisibilityListener);
            leadingBodyContent.managedProperty().addListener(leadingBodyContentVisibilityListener);
            if (leadingBodyContent instanceof Region region) {
                region.setMinHeight(0);
                region.setMaxHeight(Double.MAX_VALUE);
            }
            HBox.setHgrow(leadingBodyContent, Priority.NEVER);
        }
        updateBodyLayoutChildren();
    }

    public void setLeadingBodyContentExclusive(boolean exclusive) {
        if (leadingBodyContentExclusive == exclusive) {
            return;
        }
        leadingBodyContentExclusive = exclusive;
        updateBodyLayoutChildren();
    }

    private void updateBodyLayoutChildren() {
        boolean showLeadingOnly = leadingBodyContentExclusive && isLeadingBodyContentShowing();
        if (showLeadingOnly) {
            syncActiveCategoryBrowserRetention(true);
        }
        detachFromParent(bodyContainer);
        if (leadingBodyContent == null || (leadingBodyContentExclusive && !showLeadingOnly)) {
            bodyLayout.getChildren().setAll(bodyContainer);
        } else if (showLeadingOnly) {
            detachFromParent(leadingBodyContent);
            bodyLayout.getChildren().setAll(leadingBodyContent);
            HBox.setHgrow(leadingBodyContent, Priority.ALWAYS);
        } else {
            detachFromParent(leadingBodyContent);
            bodyLayout.getChildren().setAll(leadingBodyContent, bodyContainer);
            HBox.setHgrow(leadingBodyContent, Priority.NEVER);
        }
        HBox.setHgrow(bodyContainer, Priority.ALWAYS);
        if (!showLeadingOnly) {
            syncActiveCategoryBrowserRetention();
        }
    }

    private boolean isLeadingBodyContentShowing() {
        return leadingBodyContent != null && leadingBodyContent.isVisible() && leadingBodyContent.isManaged();
    }

    private void detachLeadingBodyContent() {
        if (leadingBodyContent == null) {
            return;
        }
        leadingBodyContent.visibleProperty().removeListener(leadingBodyContentVisibilityListener);
        leadingBodyContent.managedProperty().removeListener(leadingBodyContentVisibilityListener);
        detachFromParent(leadingBodyContent);
    }

    private void showBody(Node content) {
        detachFromParent(content);
        bodyContainer.getChildren().setAll(content);
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    private Button createAddAccountToolbarButton() {
        Button button = new Button(I18n.tr("autoAdd"));
        button.getStyleClass().add("list-toolbar-action-button");
        button.setGraphic(createAddToolbarIcon());
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setAccessibleText(I18n.tr("autoNewAccount"));
        button.setTooltip(new Tooltip(I18n.tr("autoNewAccount")));
        button.setOnAction(_ -> openNewAccountInline());
        return button;
    }

    private static Node createAddToolbarIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent(ICON_ADD);
        icon.getStyleClass().add("list-toolbar-add-icon");
        return icon;
    }

    private MenuButton createAccountSortButton() {
        MenuButton button = new MenuButton();
        button.getStyleClass().add("list-toolbar-sort-menu");
        button.setGraphic(createSortDropdownIcon());
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        ToggleGroup group = new ToggleGroup();
        button.getItems().setAll(
                createSortMenuItem(I18n.tr("autoSortDefault"), AccountSortMode.DEFAULT, accountSortMode, group, this::setAccountSortMode),
                createSortMenuItem(I18n.tr("autoSortNameAscending"), AccountSortMode.ASCENDING, accountSortMode, group, this::setAccountSortMode),
                createSortMenuItem(I18n.tr("autoSortNameDescending"), AccountSortMode.DESCENDING, accountSortMode, group, this::setAccountSortMode)
        );
        accountSortButton = button;
        updateAccountSortButton();
        return button;
    }

    private void openNewAccountInline() {
        if (onNewAccountCallback != null) {
            onNewAccountCallback.call(null);
            return;
        }
        showErrorAlert(I18n.tr("autoManageAccountUIIsNotAvailable"));
    }

    private void initDetailView() {
        backButton.setOnAction(_ -> showPreviousView());
        navHeader.setPadding(new Insets(0, 8, 6, 8));
        navHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        navHeader.getChildren().setAll(backButton);
        detailContent.setSpacing(5);
        detailContent.setPadding(new Insets(5, 0, 0, 0));
        VBox.setVgrow(detailContent, Priority.ALWAYS);
        detailView.getChildren().setAll(detailContent);
    }

    private Button createBackButton() {
        Button button = new Button(I18n.tr("autoBack"));
        button.setFocusTraversable(false);
        return button;
    }

    public void showAccountListView() {
        activeCategoryBrowserRetainedForDetailView = false;
        disposeActiveCategoryList();
        setAccountBrowserCompact(false);
        viewStack.clear();
        switchHeaderSearchMode(HeaderSearchMode.ACCOUNTS, false);
        setCurrentContent(listView);
        updateNavButtons();
        embeddedContainer.getChildren().setAll(currentContent);
        showBody(embeddedContainer);
    }

    private void showAccountBrowser(CategoryListUI categoryListUI) {
        if (categoryListUI == null) {
            return;
        }
        boolean newBrowser = activeCategoryListUI != categoryListUI;
        if (activeCategoryListUI != null && newBrowser) {
            activeCategoryListUI.dispose();
        }
        activeCategoryListUI = categoryListUI;
        activeCategoryBrowserRetainedForDetailView = false;
        categoryListUI.setMediaDrawerMode(mediaDrawerMode);
        switchHeaderSearchMode(HeaderSearchMode.ACTIVE_BROWSER, newBrowser);
        if (mediaDrawerMode || shouldUseSinglePaneAccountBrowser()) {
            setAccountBrowserCompact(false);
            setCurrentContent(categoryListUI);
            updateNavButtons();
            detachFromParent(listView);
            detachFromParent(categoryListUI);
            if (embeddedContainer.getChildren().size() != 1 || embeddedContainer.getChildren().getFirst() != currentContent) {
                embeddedContainer.getChildren().setAll(currentContent);
            }
            showBody(embeddedContainer);
            syncActiveCategoryBrowserRetention();
            return;
        }
        setAccountBrowserCompact(true);
        detachFromParent(listView);
        detachFromParent(categoryListUI);
        browserLayout.getChildren().setAll(listView, categoryListUI);
        HBox.setHgrow(listView, Priority.SOMETIMES);
        HBox.setHgrow(categoryListUI, Priority.ALWAYS);
        setCurrentContent(browserLayout);
        updateNavButtons();
        embeddedContainer.getChildren().setAll(currentContent);
        showBody(embeddedContainer);
        syncActiveCategoryBrowserRetention();
    }

    private void updateActiveAccountBrowserLayout() {
        if (activeCategoryListUI == null) {
            return;
        }
        if (currentContent == browserLayout || currentContent == activeCategoryListUI) {
            showAccountBrowser(activeCategoryListUI);
        }
    }

    private boolean shouldUseSinglePaneAccountBrowser() {
        double width = getWidth();
        if (width <= 0 && pageContainer.getWidth() > 0) {
            width = pageContainer.getWidth();
        }
        if (width <= 0 && getScene() != null) {
            width = getScene().getWidth();
        }
        return width > 0 && width < ACCOUNT_BROWSER_SINGLE_PANE_WIDTH;
    }

    private void disposeActiveCategoryList() {
        activeCategoryBrowserRetainedForDetailView = false;
        if (activeCategoryListUI != null) {
            activeCategoryListUI.dispose();
            activeCategoryListUI = null;
        }
    }

    private void setAccountBrowserCompact(boolean compact) {
        boolean wasSingleLine = useSingleLineAccountRows();
        if (mediaDrawerMode) {
            accountBrowserCompact = false;
            listView.getStyleClass().remove("account-list-panel-compact");
            accountGrid.setCardWidthRange(GRID_DRAWER_CARD_MIN_WIDTH, GRID_DRAWER_CARD_MAX_WIDTH);
            refreshAccountGridIfRowModeChanged(wasSingleLine);
            return;
        }
        if (compact) {
            accountBrowserCompact = true;
            if (!listView.getStyleClass().contains("account-list-panel-compact")) {
                listView.getStyleClass().add("account-list-panel-compact");
            }
            accountGrid.setCardWidthRange(GRID_COMPACT_CARD_MIN_WIDTH, GRID_COMPACT_CARD_MAX_WIDTH);
            refreshAccountGridIfRowModeChanged(wasSingleLine);
            return;
        }
        accountBrowserCompact = false;
        listView.getStyleClass().remove("account-list-panel-compact");
        accountGrid.setCardWidthRange(GRID_NORMAL_CARD_MIN_WIDTH, GRID_NORMAL_CARD_MAX_WIDTH);
        refreshAccountGridIfRowModeChanged(wasSingleLine);
    }

    private void updateMediaDrawerStyle() {
        updateStyleClass(pageContainer, "account-media-drawer", mediaDrawerMode);
        updateStyleClass(listView, "account-list-panel-drawer", mediaDrawerMode);
    }

    private void updateStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null) {
            return;
        }
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
            return;
        }
        node.getStyleClass().remove(styleClass);
    }

    private void showDetailView(Node content) {
        if (!(content instanceof CategoryListUI)) {
            switchHeaderSearchMode(HeaderSearchMode.ACCOUNTS, false);
            retainActiveCategoryBrowserForDetailView();
        }
        detailContent.getChildren().setAll(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        if (currentContent != null) {
            viewStack.push(currentContent);
        }
        setCurrentContent(detailView);
        updateNavButtons();
        embeddedContainer.getChildren().setAll(navHeader, currentContent);
        showBody(embeddedContainer);
    }

    private void setCurrentContent(Node content) {
        currentContent = content;
        if (content != detailView) {
            detailContent.getChildren().clear();
        }
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    private void showPreviousView() {
        if (!detailContent.getChildren().isEmpty()) {
            Node content = detailContent.getChildren().getFirst();
            if (content instanceof CategoryListUI categoryListUI && categoryListUI.navigateBackEmbedded()) {
                return;
            }
        }
        if (viewStack.isEmpty()) {
            return;
        }
        Node prev = viewStack.pop();
        if (prev == listView) {
            showAccountListView();
            return;
        }
        if (prev == browserLayout && activeCategoryListUI != null) {
            showAccountBrowser(activeCategoryListUI);
            return;
        }

        setCurrentContent(prev);
        updateNavButtons();
        embeddedContainer.getChildren().setAll(navHeader, currentContent);
        showBody(embeddedContainer);
        if (prev == activeCategoryListUI && activeCategoryListUI != null) {
            activeCategoryBrowserRetainedForDetailView = false;
            syncActiveCategoryBrowserRetention();
        }
    }

    private void retainActiveCategoryBrowserForDetailView() {
        if (activeCategoryListUI == null) {
            return;
        }
        if (currentContent == browserLayout || currentContent == activeCategoryListUI) {
            activeCategoryBrowserRetainedForDetailView = true;
            syncActiveCategoryBrowserRetention();
        }
    }

    private void syncActiveCategoryBrowserRetention() {
        syncActiveCategoryBrowserRetention(leadingBodyContentExclusive && isLeadingBodyContentShowing());
    }

    private void syncActiveCategoryBrowserRetention(boolean bodyHiddenByExclusiveContent) {
        if (activeCategoryListUI == null) {
            return;
        }
        activeCategoryListUI.setRetainTransientStateOnDetach(
                activeCategoryBrowserRetainedForDetailView || bodyHiddenByExclusiveContent
        );
    }

    private void updateNavButtons() {
        backButton.setDisable(viewStack.isEmpty());
    }

    private Region createAccountCard(AccountItem item) {
        boolean thumbnailsEnabled = ThumbnailAwareUI.areThumbnailsEnabled();
        if (!thumbnailsEnabled) {
            return createPlainTextAccountCard(item);
        }
        if (accountBrowserCompact || mediaDrawerMode) {
            return createCompactAccountCard(item);
        }

        VBox card = new VBox(7);
        card.getStyleClass().add("account-card");
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(item == null ? "" : item.getAccountName());
        title.getStyleClass().add("account-card-title");
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        TextFlow type = createAccountTypeLine(item);

        Label metrics = new Label(accountMetricsText(item));
        metrics.getStyleClass().add("account-card-metrics");
        metrics.setWrapText(true);
        metrics.setMinWidth(0);
        metrics.setMaxWidth(Double.MAX_VALUE);
        metrics.setVisible(!metrics.getText().isBlank());
        metrics.setManaged(metrics.isVisible());

        Button menuButton = createAccountCardMenuButton(item);

        HBox topRow = new HBox(7);
        topRow.setAlignment(Pos.TOP_LEFT);
        topRow.setMinWidth(0);
        topRow.setMaxWidth(Double.MAX_VALUE);
        if (item != null && item.isPinToTop()) {
            topRow.getChildren().add(createAccountPinIcon());
        }
        topRow.getChildren().addAll(title, menuButton);

        card.getChildren().addAll(topRow, type);
        if (metrics.isVisible()) {
            card.getChildren().add(metrics);
        }
        return card;
    }

    private TextFlow createAccountTypeLine(AccountItem item) {
        String typeText = accountTypeDisplay(item);
        String expiryText = item == null ? "" : item.getExpiryText();
        TextFlow typeLine = new TextFlow();
        typeLine.getStyleClass().add("account-card-type");
        typeLine.setMinWidth(0);
        typeLine.setMaxWidth(Double.MAX_VALUE);

        if (!typeText.isBlank()) {
            typeLine.getChildren().add(createAccountTypeText(typeText));
        }
        if (expiryText != null && !expiryText.isBlank()) {
            typeLine.getChildren().add(createAccountTypeText(typeText.isBlank() ? "" : " ("));
            Text expiryDate = createAccountTypeText(expiryText);
            expiryDate.getStyleClass().add("account-card-expiry-date");
            expiryDate.setStyle("-fx-fill: " + AccountInfoUiUtil.colorForExpiry(item.getExpiryState()) + ";");
            typeLine.getChildren().add(expiryDate);
            if (!typeText.isBlank()) {
                typeLine.getChildren().add(createAccountTypeText(")"));
            }
        }
        typeLine.setVisible(!typeLine.getChildren().isEmpty());
        typeLine.setManaged(typeLine.isVisible());
        return typeLine;
    }

    private Text createAccountTypeText(String value) {
        Text text = new Text(value == null ? "" : value);
        text.getStyleClass().add("account-card-type-text");
        return text;
    }

    private Region createCompactAccountCard(AccountItem item) {
        HBox card = new HBox(7);
        card.getStyleClass().addAll("account-card", "plain-text-row-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(item == null ? "" : item.getAccountName());
        title.getStyleClass().add("account-card-title");
        title.setWrapText(false);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        if (item != null && item.isPinToTop()) {
            card.getChildren().add(createAccountPinIcon());
        }
        card.getChildren().addAll(title, createAccountCardMenuButton(item));
        return card;
    }

    private Button createAccountCardMenuButton(AccountItem item) {
        Button menuButton = new PlayMenuButton(I18n.tr("autoManage"));
        menuButton.getStyleClass().add("account-card-menu-button");
        menuButton.setOnAction(event -> {
            event.consume();
            if (item != null) {
                List<AccountItem> selectedItems = selectedAccountsForAccountMenuButton(item);
                ContextMenu menu = createAccountContextMenu(item, selectedItems, menuButton);
                UiI18n.preparePopupControl(menu, menuButton);
                menu.show(menuButton, Side.BOTTOM, 0, 0);
            }
        });
        return menuButton;
    }

    private List<AccountItem> selectedAccountsForAccountMenuButton(AccountItem item) {
        if (item == null) {
            return List.of();
        }
        List<AccountItem> selectedItems = List.copyOf(accountGrid.getSelectedItems());
        boolean itemInSelection = selectedItems.stream().anyMatch(selectedItem -> isSameAccountItem(selectedItem, item));
        if (itemInSelection) {
            return selectedItems;
        }
        accountGrid.selectItems(List.of(item));
        return List.of(item);
    }

    private boolean isSameAccountItem(AccountItem left, AccountItem right) {
        return left != null && right != null && Objects.equals(left.getAccountId(), right.getAccountId());
    }

    private Region createPlainTextAccountCard(AccountItem item) {
        HBox card = new HBox();
        card.getStyleClass().addAll("account-card", "plain-text-row-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(item == null ? "" : item.getAccountName());
        title.getStyleClass().add("account-card-title");
        title.setWrapText(false);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        card.getChildren().add(title);
        return card;
    }

    private String accountTypeDisplay(AccountItem item) {
        if (item == null || item.getAccountType() == null || item.getAccountType().isBlank()) {
            return "";
        }
        try {
            return AccountType.valueOf(item.getAccountType()).getDisplay();
        } catch (Exception _) {
            return item.getAccountType().replace('_', ' ');
        }
    }

    private String accountMetricsText(AccountItem item) {
        if (item == null) {
            return "";
        }
        List<String> metrics = new ArrayList<>();
        if (item.getCategoryCount() > 1) {
            metrics.add(I18n.formatNumber(String.valueOf(item.getCategoryCount())) + " " + I18n.tr("autoCategories"));
        }
        if (item.getChannelCount() > 0) {
            metrics.add(I18n.formatNumber(String.valueOf(item.getChannelCount())) + " " + I18n.tr("autoChannels"));
        }
        return String.join(" / ", metrics);
    }

    private StackPane createAccountPinIcon() {
        SVGPath pinStem = new SVGPath();
        SVGPath pinHead = new SVGPath();
        pinStem.setContent(AccountResolver.PIN_SVG_STEM_PATH);
        pinStem.setFill(Color.web(AccountResolver.PIN_SVG_STEM_FILL));
        pinHead.setContent(AccountResolver.PIN_SVG_HEAD_PATH);
        pinHead.setFill(Color.web(AccountResolver.PIN_SVG_HEAD_FILL));
        Group pinIcon = new Group(pinStem, pinHead);
        pinIcon.setScaleX(AccountResolver.PIN_SVG_SCALE);
        pinIcon.setScaleY(AccountResolver.PIN_SVG_SCALE);
        StackPane pinIconWrapper = new StackPane(pinIcon);
        pinIconWrapper.getStyleClass().add("account-card-pin-icon");
        pinIconWrapper.setMinSize(22, 22);
        pinIconWrapper.setPrefSize(22, 22);
        pinIconWrapper.setMaxSize(22, 22);
        return pinIconWrapper;
    }

    private ContextMenu createAccountContextMenu(AccountItem contextItem, List<AccountItem> selectedItems, Node owner) {
        ContextMenu menu = new ContextMenu();
        UiI18n.preparePopupControl(menu, owner == null ? accountGrid : owner);
        menu.setHideOnEscape(true);
        menu.setAutoHide(true);

        List<AccountItem> actionItems = selectedAccountsForAction(contextItem, selectedItems);

        MenuItem editAccount = new MenuItem(I18n.tr("autoEditManageAccount"));
        editAccount.setOnAction(_ -> runSingleSelectionAction(actionItems, () -> openManageAccount(contextItem, true)));

        MenuItem itvItem = new MenuItem(I18n.tr("autoTvChannels"));
        itvItem.setOnAction(_ -> runSingleSelectionAction(actionItems,
                () -> retrieveThreadedAccountCategories(contextItem, Account.AccountAction.itv)));

        MenuItem vodItem = new MenuItem(I18n.tr("autoVod"));
        vodItem.setOnAction(_ -> runSingleSelectionAction(actionItems,
                () -> retrieveThreadedAccountCategories(contextItem, Account.AccountAction.vod)));

        MenuItem seriesItem = new MenuItem(I18n.tr("autoSeries"));
        seriesItem.setOnAction(_ -> runSingleSelectionAction(actionItems,
                () -> retrieveThreadedAccountCategories(contextItem, Account.AccountAction.series)));

        MenuItem reloadCache = new MenuItem(I18n.tr("autoReloadCache"));
        reloadCache.setOnAction(_ -> handleReloadCache(contextItem, actionItems));

        MenuItem deleteItem = new MenuItem(I18n.tr("autoDeleteAccount"));
        deleteItem.getStyleClass().add("danger-menu-item");
        deleteItem.setOnAction(_ -> handleDeleteAccounts(actionItems));

        Account account = contextItem == null ? null : accountService.getById(contextItem.getAccountId());
        boolean vodSupported = account != null && VOD_AND_SERIES_SUPPORTED.contains(account.getType());
        vodItem.setVisible(vodSupported);
        seriesItem.setVisible(vodSupported);
        boolean cacheSupported = actionItems.stream()
                .map(AccountItem::getAccountId)
                .map(accountService::getById)
                .filter(Objects::nonNull)
                .map(Account::getType)
                .anyMatch(CACHE_SUPPORTED::contains);
        reloadCache.setVisible(cacheSupported);

        if (actionItems.size() > 1) {
            if (cacheSupported) {
                menu.getItems().add(reloadCache);
            }
            menu.getItems().add(deleteItem);
            return menu;
        }

        menu.getItems().addAll(editAccount, new SeparatorMenuItem(), itvItem, vodItem, seriesItem);
        if (cacheSupported) {
            menu.getItems().addAll(new SeparatorMenuItem(), reloadCache);
        }
        menu.getItems().add(deleteItem);
        return menu;
    }

    private void handleAccountGridSelectionChanged() {
        if (onEditCallback == null || accountGrid.getSelectedItems().size() != 1) {
            return;
        }
        AccountItem item = accountGrid.getSelectedItems().getFirst();
        Account account = item == null ? null : accountService.getById(item.getAccountId());
        if (account != null) {
            onEditCallback.call(account);
        }
    }

    private TableCell<AccountItem, String> createAccountNameCell() {
        return new TableCell<>() {
            private final HBox graphic = new HBox(6);
            private final SVGPath pinStem = new SVGPath();
            private final SVGPath pinHead = new SVGPath();
            private final Group pinIcon = new Group(pinStem, pinHead);
            private final StackPane pinIconWrapper = new StackPane(pinIcon);
            private final Label nameLabel = new Label();
            private final Pane spacer = new Pane();

            {
                pinStem.setContent(AccountResolver.PIN_SVG_STEM_PATH);
                pinStem.setFill(Color.web(AccountResolver.PIN_SVG_STEM_FILL));
                pinHead.setContent(AccountResolver.PIN_SVG_HEAD_PATH);
                pinHead.setFill(Color.web(AccountResolver.PIN_SVG_HEAD_FILL));
                pinIcon.setScaleX(AccountResolver.PIN_SVG_SCALE);
                pinIcon.setScaleY(AccountResolver.PIN_SVG_SCALE);
                pinIconWrapper.setPrefSize(24, 24);
                pinIconWrapper.setMinSize(24, 24);
                pinIconWrapper.setMaxSize(24, 24);
                pinIconWrapper.setVisible(false);
                pinIconWrapper.setManaged(false);

                HBox.setHgrow(spacer, Priority.ALWAYS);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(pinIconWrapper, nameLabel, spacer);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                AccountItem accountItem = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                        ? getTableView().getItems().get(getIndex())
                        : null;

                if (accountItem == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(item);
                boolean pinned = accountItem.isPinToTop();
                pinIconWrapper.setVisible(pinned);
                pinIconWrapper.setManaged(pinned);
                setText(null);
                setGraphic(graphic);
            }
        };
    }

    private void registerSceneCleanupListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                if (InlinePanelService.hasOpenPanel()) {
                    return;
                }
                markRefreshPending();
                table.getItems().clear();
                accountGrid.setItems(FXCollections.observableArrayList());
                masterAccountItems.clear();
                detailContent.getChildren().clear();
                viewStack.clear();
            }
        });
    }

    private void registerThumbnailModeListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterThumbnailModeListener();
            } else {
                registerThumbnailModeListenerIfNeeded();
                applyPlainTextMode(ThumbnailAwareUI.areThumbnailsEnabled());
            }
        });
        if (getScene() != null) {
            registerThumbnailModeListenerIfNeeded();
        }
    }

    private void registerThumbnailModeListenerIfNeeded() {
        if (thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = true;
    }

    private void unregisterThumbnailModeListener() {
        if (!thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.removeThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = false;
    }

    private void onThumbnailModeChanged(boolean enabled) {
        Platform.runLater(() -> applyPlainTextMode(enabled));
    }

    private void applyPlainTextMode(boolean thumbnailsEnabled) {
        applyAccountGridDisplayMode(thumbnailsEnabled);
        accountGrid.refresh();
    }

    private void applyAccountGridDisplayMode(boolean thumbnailsEnabled) {
        boolean singleLine = !thumbnailsEnabled || accountBrowserCompact || mediaDrawerMode;
        accountGrid.setSingleColumn(singleLine);
        accountGrid.setCardMinHeight(singleLine
                ? GRID_PLAIN_TEXT_CARD_MIN_HEIGHT
                : GRID_NORMAL_CARD_MIN_HEIGHT);
        accountGrid.setGaps(16, singleLine
                ? GRID_PLAIN_TEXT_VERTICAL_GAP
                : GRID_NORMAL_VERTICAL_GAP);
    }

    private boolean useSingleLineAccountRows() {
        return !ThumbnailAwareUI.areThumbnailsEnabled() || accountBrowserCompact || mediaDrawerMode;
    }

    private void refreshAccountGridIfRowModeChanged(boolean wasSingleLine) {
        applyAccountGridDisplayMode(ThumbnailAwareUI.areThumbnailsEnabled());
        if (wasSingleLine != useSingleLineAccountRows()) {
            accountGrid.refresh();
        }
    }

    private void detachFromParent(Node node) {
        if (node == null || node.getParent() == null) {
            return;
        }
        if (node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    @Override
    public void setSearchQuery(String query) {
        String value = query == null ? "" : query;
        accountHeaderSearchText = value;
        if (headerSearchMode != HeaderSearchMode.ACCOUNTS) {
            return;
        }
        if (!Objects.equals(table.getTextField().getText(), value)) {
            table.getTextField().setText(value);
            return;
        }
        applyAccountOrdering();
    }

    private void handleHeaderSearchTextChanged(String text) {
        if (updatingHeaderSearchText) {
            return;
        }
        String value = text == null ? "" : text;
        if (headerSearchMode == HeaderSearchMode.ACTIVE_BROWSER) {
            browserHeaderSearchText = value;
            applyActiveBrowserSearch();
            return;
        }
        accountHeaderSearchText = value;
        applyAccountOrdering();
    }

    private void switchHeaderSearchMode(HeaderSearchMode mode, boolean resetBrowserSearch) {
        if (mode == HeaderSearchMode.ACTIVE_BROWSER && resetBrowserSearch) {
            browserHeaderSearchText = "";
        }
        headerSearchMode = mode == null ? HeaderSearchMode.ACCOUNTS : mode;
        String targetText = headerSearchMode == HeaderSearchMode.ACTIVE_BROWSER
                ? browserHeaderSearchText
                : accountHeaderSearchText;
        updatingHeaderSearchText = true;
        try {
            if (!Objects.equals(table.getTextField().getText(), targetText)) {
                table.getTextField().setText(targetText);
            }
        } finally {
            updatingHeaderSearchText = false;
        }
        if (headerSearchMode == HeaderSearchMode.ACTIVE_BROWSER) {
            applyActiveBrowserSearch();
        } else {
            applyAccountOrdering();
        }
    }

    private void applyActiveBrowserSearch() {
        SearchTarget.apply(activeCategoryListUI, browserHeaderSearchText);
    }

    private void replaceBrowserHeaderSearchText(String text) {
        browserHeaderSearchText = text == null ? "" : text;
        if (headerSearchMode != HeaderSearchMode.ACTIVE_BROWSER) {
            return;
        }
        updatingHeaderSearchText = true;
        try {
            if (!Objects.equals(table.getTextField().getText(), browserHeaderSearchText)) {
                table.getTextField().setText(browserHeaderSearchText);
            }
        } finally {
            updatingHeaderSearchText = false;
        }
        applyActiveBrowserSearch();
    }

    private void applyAccountOrdering() {
        List<AccountItem> orderedItems = new ArrayList<>(masterAccountItems);
        switch (accountSortMode) {
            case DEFAULT -> orderedItems.sort(Comparator.comparing(AccountItem::isPinToTop).reversed()
                    .thenComparingInt(AccountItem::getOriginalOrder));
            case DESCENDING -> orderedItems.sort(ACCOUNT_NAME_COMPARATOR.reversed());
            default -> orderedItems.sort(ACCOUNT_NAME_COMPARATOR);
        }
        table.setItems(FXCollections.observableArrayList(orderedItems));
        accountGrid.setItems(FXCollections.observableArrayList(orderedItems.stream()
                .filter(this::matchesAccountFilters)
                .toList()));
    }

    private boolean matchesAccountFilters(AccountItem item) {
        if (item == null) {
            return false;
        }
        String searchText = headerSearchMode == HeaderSearchMode.ACCOUNTS ? accountHeaderSearchText : "";
        String normalizedSearch = searchText == null ? "" : searchText.trim().toLowerCase(Locale.ROOT);
        String accountNameValue = item.getAccountName() == null ? "" : item.getAccountName();
        boolean matchesSearch = normalizedSearch.isBlank()
                || accountNameValue.toLowerCase(Locale.ROOT).contains(normalizedSearch);
        AccountTypeFilter selectedType = accountTypePillBar.getSelectedItem();
        boolean matchesType = selectedType == null
                || selectedType.type() == null
                || selectedType.type().name().equals(item.getAccountType());
        return matchesSearch && matchesType;
    }

    private void installAccountHeaderSortHandler() {
        table.skinProperty().addListener((_, _, newSkin) -> {
            if (newSkin != null) {
                Platform.runLater(this::bindAccountHeaderClickHandler);
            }
        });
        Platform.runLater(this::bindAccountHeaderClickHandler);
    }

    private void bindAccountHeaderClickHandler() {
        for (Node header : table.lookupAll(".column-header")) {
            if (Boolean.TRUE.equals(header.getProperties().get("account-sort-bound"))) {
                continue;
            }
            Node labelNode = header.lookup(".label");
            if (labelNode instanceof Labeled labeled && Objects.equals(labeled.getText(), accountName.getText())) {
                header.addEventFilter(MouseEvent.MOUSE_CLICKED, this::handleAccountHeaderClick);
                header.getProperties().put("account-sort-bound", true);
            }
        }
    }

    private void handleAccountHeaderClick(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY || event.getClickCount() != 1) {
            return;
        }
        setAccountSortMode(nextAccountSortMode());
        event.consume();
    }

    private AccountSortMode nextAccountSortMode() {
        return switch (accountSortMode) {
            case DEFAULT -> AccountSortMode.ASCENDING;
            case ASCENDING -> AccountSortMode.DESCENDING;
            case DESCENDING -> AccountSortMode.DEFAULT;
        };
    }

    private void setAccountSortMode(AccountSortMode sortMode) {
        accountSortMode = sortMode == null ? AccountSortMode.DEFAULT : sortMode;
        accountName.setSortType(accountSortMode == AccountSortMode.DESCENDING
                ? TableColumn.SortType.DESCENDING
                : TableColumn.SortType.ASCENDING);
        applyAccountOrdering();
        updateAccountSortButton();
    }

    private void updateAccountSortButton() {
        if (accountSortButton == null) {
            return;
        }
        accountSortButton.setText(sortCompactLabel(accountSortMode));
        accountSortButton.setAccessibleText(accountSortTooltip());
        accountSortButton.setTooltip(new Tooltip(accountSortTooltip()));
        syncAccountSortMenuItems();
        updateStyleClass(accountSortButton, "list-toolbar-sort-menu-active", accountSortMode != AccountSortMode.DEFAULT);
    }

    private static Node createSortDropdownIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent(ICON_SORT);
        icon.getStyleClass().add("list-toolbar-sort-icon");
        return icon;
    }

    private String accountSortTooltip() {
        return I18n.tr("autoSort") + ": " + sortLabel(accountSortMode);
    }

    private String sortLabel(AccountSortMode sortMode) {
        return switch (sortMode == null ? AccountSortMode.DEFAULT : sortMode) {
            case DEFAULT -> I18n.tr("autoSortDefault");
            case ASCENDING -> I18n.tr("autoSortNameAscending");
            case DESCENDING -> I18n.tr("autoSortNameDescending");
        };
    }

    private String sortCompactLabel(AccountSortMode sortMode) {
        return switch (sortMode == null ? AccountSortMode.DEFAULT : sortMode) {
            case DEFAULT -> "Default";
            case ASCENDING -> "A-Z";
            case DESCENDING -> "Z-A";
        };
    }

    private <T> RadioMenuItem createSortMenuItem(String label, T mode, T selectedMode, ToggleGroup group,
                                                 java.util.function.Consumer<T> sortModeConsumer) {
        RadioMenuItem item = new RadioMenuItem(label);
        item.setUserData(mode);
        item.setToggleGroup(group);
        item.setSelected(Objects.equals(mode, selectedMode));
        item.setOnAction(_ -> sortModeConsumer.accept(mode));
        return item;
    }

    private void syncAccountSortMenuItems() {
        for (MenuItem item : accountSortButton.getItems()) {
            if (item instanceof RadioMenuItem radioMenuItem) {
                radioMenuItem.setSelected(Objects.equals(item.getUserData(), accountSortMode));
            }
        }
    }

    private void handleReloadCache(AccountItem contextItem) {
        handleReloadCache(contextItem, null);
    }

    private void handleReloadCache(AccountItem contextItem, List<AccountItem> selectedItems) {
        List<Account> accounts = resolveAccountsForReload(contextItem, selectedItems);
        if (accounts.isEmpty()) {
            showErrorAlert(I18n.tr("autoNoCacheSupportedAccountSelected"));
            return;
        }
        ReloadCachePopup.showPopup(resolveOwnerStage(), accounts, this::refresh);
    }

    private Stage resolveOwnerStage() {
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return RootApplication.getPrimaryStage();
    }

    private void runSingleSelectionAction(Runnable action) {
        runSingleSelectionAction(selectedAccountsForAction(null), action);
    }

    private void runSingleSelectionAction(List<AccountItem> selectedItems, Runnable action) {
        if (selectedItems != null && selectedItems.size() > 1) {
            showErrorAlert(I18n.tr(MULTI_SELECTION_DISABLED_KEY));
            return;
        }
        action.run();
    }

    private List<Account> resolveAccountsForReload(AccountItem contextItem) {
        return resolveAccountsForReload(contextItem, null);
    }

    private List<Account> resolveAccountsForReload(AccountItem contextItem, List<AccountItem> selectedItems) {
        List<AccountItem> sourceItems = selectedAccountsForAction(contextItem, selectedItems);

        LinkedHashSet<String> uniqueAccountIds = new LinkedHashSet<>();
        List<Account> accounts = new ArrayList<>();
        for (AccountItem sourceItem : sourceItems) {
            if (sourceItem == null || !uniqueAccountIds.add(sourceItem.getAccountId())) {
                continue;
            }
            Account account = accountService.getById(sourceItem.getAccountId());
            if (account != null && CACHE_SUPPORTED.contains(account.getType())) {
                accounts.add(account);
            }
        }
        return accounts;
    }

    private List<AccountItem> selectedAccountsForAction(AccountItem contextItem) {
        return selectedAccountsForAction(contextItem, null);
    }

    private List<AccountItem> selectedAccountsForAction(AccountItem contextItem, List<AccountItem> preferredSelection) {
        List<AccountItem> selectedItems = new ArrayList<>();
        if (preferredSelection != null && !preferredSelection.isEmpty()) {
            selectedItems.addAll(preferredSelection);
        } else {
            selectedItems.addAll(accountGrid.getSelectedItems());
        }
        boolean contextInSelection = contextItem != null
                && selectedItems.stream().anyMatch(item -> contextItem.getAccountId().equals(item.getAccountId()));
        if (!selectedItems.isEmpty() && (contextItem == null || contextInSelection)) {
            return selectedItems;
        }
        return contextItem == null ? List.of() : List.of(contextItem);
    }

    private void handleDeleteAccounts() {
        handleDeleteAccounts(selectedAccountsForAction(null));
    }

    private void handleDeleteAccounts(List<AccountItem> selectedAccounts) {
        List<AccountItem> safeSelectedAccounts = selectedAccounts == null ? List.of() : selectedAccounts;
        int selectedCount = safeSelectedAccounts.size();
        if (safeSelectedAccounts.isEmpty()) {
            return;
        }
        String localizedMessage = I18n.tr(
                "accountListDeleteAccountsConfirm",
                selectedCount,
                safeSelectedAccounts.stream()
                        .map(AccountItem::getAccountName)
                        .collect(Collectors.joining(", "))
        );
        if (showConfirmationAlert(localizedMessage)) {
            for (AccountItem selectedItem : safeSelectedAccounts) {
                AccountService.getInstance().delete(selectedItem.getAccountId());
                if (onDeleteCallback != null) {
                    onDeleteCallback.call(accountService.getById(selectedItem.getAccountId()));
                }
            }
            refresh();
        }
    }

    private void retrieveThreadedAccountCategories(AccountItem item, Account.AccountAction accountAction) {
        if (item == null) {
            return;
        }
        Account account = accountService.getById(item.getAccountId());
        if (account == null) {
            showErrorAlert(I18n.tr("autoUnableToFindAccount"));
            return;
        }
        AccountMediaContext mediaContext = AccountMediaContext.from(account, accountAction);

        // Immediately show the CategoryListUI in loading state
        CategoryListUI categoryListUI = new CategoryListUI(mediaContext);
        categoryListUI.setAccountsNavigationHandler(this::showAccountListView);
        categoryListUI.setCloseHandler(this::showAccountListView);
        categoryListUI.setHeaderSearchTextHandler(this::replaceBrowserHeaderSearchText);
        showAccountBrowser(categoryListUI);

        RootApplication.getPrimaryStage().getScene().setCursor(Cursor.WAIT);

        new Thread(() -> {
            try {
                Account modeAccount = mediaContext.toAccount();
                final List<Category> list = CategoryService.getInstance().get(modeAccount, true,
                        message -> com.uiptv.util.AppLog.addInfoLog(AccountListUI.class,
                                "[ParentalLock] account=" + mediaContext.accountName()
                                        + " type=" + mediaContext.type()
                                        + " action=" + accountAction
                                        + " categories: " + message));

                Platform.runLater(() -> categoryListUI.setItems(list));
            } catch (Exception e) {
                Platform.runLater(() -> showErrorAlert(I18n.tr("autoFailedRefreshChannels", e.getMessage())));
            } finally {
                Platform.runLater(() -> RootApplication.getPrimaryStage().getScene().setCursor(null));
            }
        }).start();
    }

    private void openManageAccount(AccountItem item, boolean explicitManageAction) {
        if (item == null) {
            return;
        }
        Account account = accountService.getById(item.getAccountId());
        if (account == null) {
            showErrorAlert(I18n.tr("autoUnableToFindAccount"));
            return;
        }
        if (explicitManageAction && onExplicitEditCallback != null) {
            onExplicitEditCallback.call(account);
            return;
        }
        if (onEditCallback != null) {
            onEditCallback.call(account);
        }
    }

    public static class AccountItem {
        private final SimpleStringProperty accountName;
        private final SimpleStringProperty accountId;
        private final SimpleStringProperty accountType;
        private final boolean pinToTop;
        private final int originalOrder;
        private int categoryCount;
        private int channelCount;
        private String expiryText;
        private AccountInfoUiUtil.ExpiryState expiryState;

        public AccountItem(SimpleStringProperty accountName, SimpleStringProperty accountId, SimpleStringProperty accountType,
                           boolean pinToTop, int originalOrder, int categoryCount, int channelCount) {
            this(accountName, accountId, accountType, pinToTop, originalOrder, categoryCount, channelCount, "",
                    AccountInfoUiUtil.ExpiryState.UNKNOWN);
        }

        public AccountItem(SimpleStringProperty accountName, SimpleStringProperty accountId, SimpleStringProperty accountType,
                           boolean pinToTop, int originalOrder, int categoryCount, int channelCount,
                           String expiryText, AccountInfoUiUtil.ExpiryState expiryState) {
            this.accountName = accountName;
            this.accountId = accountId;
            this.accountType = accountType;
            this.pinToTop = pinToTop;
            this.originalOrder = originalOrder;
            this.categoryCount = Math.max(0, categoryCount);
            this.channelCount = Math.max(0, channelCount);
            this.expiryText = expiryText == null ? "" : expiryText;
            this.expiryState = expiryState == null ? AccountInfoUiUtil.ExpiryState.UNKNOWN : expiryState;
        }

        public String getAccountId() {
            return accountId.get();
        }

        public void setAccountId(String accountId) {
            this.accountId.set(accountId);
        }

        public String getAccountName() {
            return accountName.get();
        }

        public void setAccountName(String accountName) {
            this.accountName.set(accountName);
        }

        public SimpleStringProperty accountNameProperty() {
            return accountName;
        }

        public boolean isPinToTop() {
            return pinToTop;
        }

        public int getOriginalOrder() {
            return originalOrder;
        }

        public String getAccountType() {
            return accountType.get();
        }

        public void setAccountType(String accountType) {
            this.accountType.set(accountType);
        }

        public int getCategoryCount() {
            return categoryCount;
        }

        public void setCategoryCount(int categoryCount) {
            this.categoryCount = Math.max(0, categoryCount);
          }

        public int getChannelCount() {
            return channelCount;
        }

        public void setChannelCount(int channelCount) {
            this.channelCount = Math.max(0, channelCount);
          }

        public String getExpiryText() {
            return expiryText;
        }

        public void setExpiryText(String expiryText) {
            this.expiryText = expiryText == null ? "" : expiryText;
          }

        public AccountInfoUiUtil.ExpiryState getExpiryState() {
            return expiryState;
        }

        public void setExpiryState(AccountInfoUiUtil.ExpiryState expiryState) {
            this.expiryState = expiryState == null ? AccountInfoUiUtil.ExpiryState.UNKNOWN : expiryState;
          }

    }

    private enum AccountSortMode {
        DEFAULT,
        ASCENDING,
        DESCENDING
    }

    private enum HeaderSearchMode {
        ACCOUNTS,
        ACTIVE_BROWSER
    }

    private record AccountMetrics(int categoryCount, int channelCount) {
    }

    private record AccountExpiry(String text, AccountInfoUiUtil.ExpiryState state) {
    }

    private record AccountTypeFilter(String key, String label, AccountType type) {
        private String compactLabel() {
            if (type == null) {
                return label;
            }
            return switch (type) {
                case STALKER_PORTAL -> "Stalker";
                case XTREME_API -> "Xtreme";
                default -> label;
            };
        }
    }
}
