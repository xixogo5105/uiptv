package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.CategoryService;
import com.uiptv.util.AccountType;
import com.uiptv.service.ConfigurationService;
import com.uiptv.widget.AutoGrowPaneVBox;
import com.uiptv.widget.SearchableFilterableTableView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;
import javafx.scene.shape.SVGPath;

import java.util.ArrayList;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.CACHE_SUPPORTED;
import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.model.Account.VOD_AND_SERIES_SUPPORTED;
import static com.uiptv.ui.RootApplication.primaryStage;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;

public class AccountListUI extends HBox {
    private static final String MULTI_SELECTION_DISABLED_KEY = "autoThisActionIsDisabledForMultipleSelections";
    private final TableColumn<AccountItem, String> accountName = new TableColumn<>(I18n.tr("accountListTitle"));
    private final CacheService cacheService = new CacheServiceImpl();
    SearchableFilterableTableView table = new SearchableFilterableTableView();
    AccountService accountService = AccountService.getInstance();
    private final boolean embeddedMode;
    private final VBox listView = new VBox(5);
    private final VBox detailView = new VBox(8);
    private final HBox navHeader = new HBox(6);
    private final Button backButton = createBackButton();
    private final Button homeButton = createHomeButton();
    private final VBox detailContent = new VBox();
    private ManageAccountUI manageAccountUI;
    private final Button newAccountButton = new Button(I18n.tr("autoAdd"));
    private final Deque<Node> viewStack = new ArrayDeque<>();
    private Node currentContent;
    private final VBox embeddedContainer = new VBox();
    private Callback<Object> onEditCallback;
    private Callback<Object> onDeleteCallback;
    private boolean isPromptShowing = false;

    public AccountListUI() { // Removed MediaPlayer argument
        this(ConfigurationService.getInstance().read().isEmbeddedPlayer());
    }

    public AccountListUI(boolean embeddedMode) {
        this.embeddedMode = embeddedMode;
        initWidgets();
        // Don't load accounts on startup - load lazily when visible
        registerVisibilityListener();
    }

    private void registerVisibilityListener() {
        // Load data only when tab becomes visible
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene != null) {
                refresh();
            }
        });
    }

    public void addUpdateCallbackHandler(Callback<Object> onEditCallback) {
        this.onEditCallback = onEditCallback;
    }

    public void addDeleteCallbackHandler(Callback<Object> onDeleteCallback) {
        this.onDeleteCallback = onDeleteCallback;
    }

    public void refresh() {
        List<AccountItem> catList = new ArrayList<>();

        LinkedHashMap<String, Account> spClients = accountService.getAll();
        if (spClients != null) {
            spClients.keySet().forEach(k -> catList.add(new AccountItem(new SimpleStringProperty(spClients.get(k).getAccountName()), new SimpleStringProperty(spClients.get(k).getDbId()), new SimpleStringProperty(spClients.get(k).getType().name()))));
        }
        table.setItems(FXCollections.observableArrayList(catList));
        table.filterByAccountType();
    }

    private void initWidgets() {
        setSpacing(5);
        setPadding(new Insets(5));
        setFillHeight(true);
        table.setEditable(true);
        table.getColumns().addAll(accountName);
        accountName.setVisible(true);
        accountName.setSortType(TableColumn.SortType.ASCENDING);
        accountName.setSortable(true);
        accountName.setCellValueFactory(cellData -> cellData.getValue().accountNameProperty());
        HBox sceneBox = new HBox(5, table.getTextField(), table.getMenuButton(), newAccountButton);
        sceneBox.setMaxHeight(25);
        newAccountButton.setManaged(embeddedMode);
        newAccountButton.setVisible(embeddedMode);
        AutoGrowPaneVBox contentBox = new AutoGrowPaneVBox(5, sceneBox, table);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        listView.getChildren().setAll(contentBox);
        if (embeddedMode) {
            initDetailView();
            embeddedContainer.getChildren().setAll(navHeader, listView);
            showAccountListView();
        } else {
            getChildren().setAll(listView);
        }
        table.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(table, Priority.ALWAYS);
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);
        HBox.setHgrow(listView, Priority.ALWAYS);
        listView.setMaxHeight(Double.MAX_VALUE);
        listView.setMinHeight(0);
        detailView.setMaxHeight(Double.MAX_VALUE);
        detailView.setMinHeight(0);
        configureNewAccountButton();
        addAccountClickHandler();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        registerSceneCleanupListener();
    }

    private void configureNewAccountButton() {
        newAccountButton.setMinWidth(56);
        newAccountButton.setPrefWidth(64);
        newAccountButton.setMinHeight(26);
        newAccountButton.setPrefHeight(26);
        newAccountButton.setTooltip(new Tooltip(I18n.tr("autoNewAccount")));
        newAccountButton.setOnAction(event -> {
            if (!embeddedMode) {
                return;
            }
            if (manageAccountUI == null) {
                showErrorAlert(I18n.tr("autoManageAccountUIIsNotAvailable"));
                return;
            }
            manageAccountUI.clearAll();
            showDetailView(manageAccountUI);
        });
    }

    private void initDetailView() {
        backButton.setOnAction(event -> showPreviousView());
        homeButton.setOnAction(event -> showAccountListView());
        navHeader.setPadding(new Insets(0, 8, 6, 8));
        navHeader.setAlignment(javafx.geometry.Pos.CENTER_LEFT);
        navHeader.getChildren().setAll(backButton, homeButton);
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

    private Button createHomeButton() {
        Button button = new Button();
        button.getStyleClass().add("nav-back-button");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(I18n.tr("autoHome")));
        SVGPath icon = new SVGPath();
        icon.setContent("M4 10 L12 4 L20 10 V20 H14 V13 H10 V20 H4 Z");
        icon.setScaleX(1.25);
        icon.setScaleY(1.25);
        icon.getStyleClass().add("nav-back-icon");
        button.setGraphic(icon);
        return button;
    }

    public void setManageAccountUI(ManageAccountUI manageAccountUI) {
        this.manageAccountUI = manageAccountUI;
    }

    public void showAccountListView() {
        if (!embeddedMode) {
            return;
        }
        viewStack.clear();
        setCurrentContent(listView);
        updateNavButtons();
        if (embeddedContainer.getChildren().isEmpty()) {
            embeddedContainer.getChildren().setAll(navHeader, listView);
        } else {
            embeddedContainer.getChildren().setAll(navHeader, currentContent);
        }
        getChildren().setAll(embeddedContainer);
    }

    private void showDetailView(Node content) {
        if (!embeddedMode) {
            return;
        }
        detailContent.getChildren().setAll(content);
        VBox.setVgrow(content, Priority.ALWAYS);
        if (currentContent != null) {
            viewStack.push(currentContent);
        }
        setCurrentContent(detailView);
        updateNavButtons();
        embeddedContainer.getChildren().setAll(navHeader, currentContent);
        getChildren().setAll(embeddedContainer);
    }

    private void showDetailViewNonEmbedded(Node content) {
        HBox sceneBox = new HBox(5, table.getTextField(), table.getMenuButton(), newAccountButton);
        sceneBox.setMaxHeight(25);
        AutoGrowPaneVBox listContainer = new AutoGrowPaneVBox(5, sceneBox, table);
        VBox.setVgrow(listContainer, Priority.ALWAYS);
        HBox.setHgrow(listContainer, Priority.ALWAYS);
        HBox.setHgrow(content, Priority.ALWAYS);
        getChildren().setAll(listContainer, content);
    }

    private void setCurrentContent(Node content) {
        currentContent = content;
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    private void showPreviousView() {
        if (!embeddedMode) {
            return;
        }
        if (!detailContent.getChildren().isEmpty()) {
            Node content = detailContent.getChildren().get(0);
            if (content instanceof CategoryListUI categoryListUI && categoryListUI.navigateBackEmbedded()) {
                return;
            }
        }
        if (viewStack.isEmpty()) {
            return;
        }
        Node prev = viewStack.pop();
        setCurrentContent(prev);
        updateNavButtons();
        embeddedContainer.getChildren().setAll(navHeader, currentContent);
        getChildren().setAll(embeddedContainer);
    }

    private void updateNavButtons() {
        backButton.setDisable(viewStack.isEmpty());
        homeButton.setDisable(false);
    }

    private void registerSceneCleanupListener() {
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                // Clear all children to allow garbage collection of CategoryListUI
                getChildren().clear();
                table.getItems().clear();
            }
        });
    }

    private void addAccountClickHandler() {
        table.setOnKeyReleased(this::handleAccountKeyReleased);
        table.setRowFactory(tv -> {
            TableRow<AccountItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> handleAccountRowClick(row, event));
            addRightClickContextMenu(row);
            return row;
        });
    }

    private void handleAccountKeyReleased(javafx.scene.input.KeyEvent event) {
        AccountItem focusedItem = table.getFocusModel().getFocusedItem();
        openFocusedAccountForEditing(focusedItem);
        if (event.getCode() == KeyCode.DELETE) {
            handleDeleteAccounts();
            return;
        }
        if (event.getCode() == KeyCode.ENTER) {
            handleEnterKey(event, focusedItem);
        }
    }

    private void openFocusedAccountForEditing(AccountItem focusedItem) {
        if (!embeddedMode && focusedItem != null && onEditCallback != null) {
            onEditCallback.call(accountService.getById(focusedItem.accountId.get()));
        }
    }

    private void handleEnterKey(javafx.scene.input.KeyEvent event, AccountItem focusedItem) {
        if (isPromptShowing) {
            event.consume();
            isPromptShowing = false;
            return;
        }
        retrieveThreadedAccountCategories(focusedItem, itv);
    }

    private void handleAccountRowClick(TableRow<AccountItem> row, javafx.scene.input.MouseEvent event) {
        if (row.isEmpty() || event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (!embeddedMode && event.getClickCount() == 1) {
            openManageAccount(row.getItem());
            return;
        }
        if (event.getClickCount() == 2) {
            retrieveThreadedAccountCategories(row.getItem(), itv);
        }
    }

    private void addRightClickContextMenu(TableRow<AccountItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        I18n.preparePopupControl(rowMenu, row);
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        MenuItem editAccount = new MenuItem(I18n.tr("autoEditManageAccount"));
        editAccount.setOnAction(actionEvent -> runSingleSelectionAction(() -> openManageAccount(row.getItem())));

        MenuItem itv = new MenuItem(I18n.tr("autoTvChannels"));
        itv.setOnAction(actionEvent -> runSingleSelectionAction(() -> retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.itv)));

        MenuItem vod = new MenuItem(I18n.tr("autoVod"));
        vod.setOnAction(actionEvent -> runSingleSelectionAction(() -> retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.vod)));

        MenuItem series = new MenuItem(I18n.tr("autoSeries"));
        series.setOnAction(actionEvent -> runSingleSelectionAction(() -> retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.series)));

        MenuItem reloadCache = new MenuItem(I18n.tr("autoReloadCache"));
        reloadCache.setOnAction(actionEvent -> handleReloadCache(row.getItem()));

        MenuItem deleteItem = new MenuItem(I18n.tr("autoDeleteAccount"));
        deleteItem.getStyleClass().add("danger-menu-item");
        deleteItem.setOnAction(actionEvent -> handleDeleteAccounts());

        rowMenu.getItems().addAll(editAccount, new SeparatorMenuItem(), itv, vod, series, new SeparatorMenuItem(), reloadCache, deleteItem);

        rowMenu.setOnShowing(e -> {
            if (row.getItem() != null) {
                Account account = accountService.getById(row.getItem().getAccountId());
                if (account == null) {
                    vod.setVisible(false);
                    series.setVisible(false);
                    reloadCache.setVisible(false);
                    return;
                }
                boolean vodSupported = VOD_AND_SERIES_SUPPORTED.contains(account.getType());
                vod.setVisible(vodSupported);
                series.setVisible(vodSupported);
                
                boolean cacheSupported = CACHE_SUPPORTED.contains(account.getType());
                reloadCache.setVisible(cacheSupported);
            }
        });

        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void handleReloadCache(AccountItem contextItem) {
        List<Account> accounts = resolveAccountsForReload(contextItem);
        if (accounts.isEmpty()) {
            showErrorAlert(I18n.tr("autoNoCacheSupportedAccountSelected"));
            return;
        }
        ReloadCachePopup.showPopup(resolveOwnerStage(), accounts, this::refresh);
    }

    private void runSingleSelectionAction(Runnable action) {
        if (table.getSelectionModel().getSelectedItems().size() > 1) {
            showErrorAlert(I18n.tr(MULTI_SELECTION_DISABLED_KEY));
            return;
        }
        action.run();
    }

    private List<Account> resolveAccountsForReload(AccountItem contextItem) {
        List<AccountItem> selectedItems = (List<AccountItem>) (List<?>) table.getSelectionModel().getSelectedItems();
        boolean contextInSelection = contextItem != null
                && selectedItems.stream().anyMatch(item -> contextItem.getAccountId().equals(item.getAccountId()));

        List<AccountItem> sourceItems;
        if (!selectedItems.isEmpty() && contextInSelection) {
            sourceItems = selectedItems;
        } else if (contextItem != null) {
            sourceItems = List.of(contextItem);
        } else {
            sourceItems = List.of();
        }

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

    private void handleDeleteAccounts() {
        int selectedCount = table.getSelectionModel().getSelectedItems().size();
        String localizedMessage = I18n.tr(
                "accountListDeleteAccountsConfirm",
                selectedCount,
                table.getSelectionModel().getSelectedItems().stream()
                        .map(AccountItem::getAccountName)
                        .collect(Collectors.joining(", "))
        );
        isPromptShowing = true;
        if (showConfirmationAlert(localizedMessage)) {
            for (AccountItem selectedItem : table.getSelectionModel().getSelectedItems()) {
                AccountService.getInstance().delete(selectedItem.getAccountId());
                if (onDeleteCallback != null) {
                    onDeleteCallback.call(accountService.getById(selectedItem.getAccountId()));
                }
            }
            refresh();
        }
    }

    private void retrieveThreadedAccountCategories(AccountItem item, Account.AccountAction accountAction) {
        Account account = accountService.getById(item.getAccountId());
        if (account == null) {
            showErrorAlert(I18n.tr("autoUnableToFindAccount"));
            return;
        }
        account.setAction(accountAction);

        // Immediately show the CategoryListUI in loading state
        CategoryListUI categoryListUI = new CategoryListUI(account, embeddedMode);
        if (embeddedMode) {
            showDetailView(categoryListUI);
        } else {
            showDetailViewNonEmbedded(categoryListUI);
        }

        boolean noCachingNeeded = NOT_LIVE_TV_CHANNELS.contains(account.getAction()) || account.getType() == AccountType.RSS_FEED;
        boolean channelsAlreadyLoaded = noCachingNeeded || cacheService.getChannelCountForAccount(account.getDbId()) > 0;

        if (!channelsAlreadyLoaded) {
            ReloadCachePopup.showPopup(resolveOwnerStage(), List.of(account), this::refresh);
        }

        primaryStage.getScene().setCursor(Cursor.WAIT);

        new Thread(() -> {
            try {
                final List<Category> list = CategoryService.getInstance().get(account);

                Platform.runLater(() -> categoryListUI.setItems(list));
            } catch (Exception e) {
                Platform.runLater(() -> showErrorAlert(I18n.tr("autoFailedRefreshChannels", e.getMessage())));
            } finally {
                Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
            }
        }).start();
    }

    private Stage resolveOwnerStage() {
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return primaryStage;
    }

    private void openManageAccount(AccountItem item) {
        if (item == null) {
            return;
        }
        Account account = accountService.getById(item.getAccountId());
        if (account == null) {
            showErrorAlert(I18n.tr("autoUnableToFindAccount"));
            return;
        }
        if (embeddedMode && manageAccountUI != null) {
            manageAccountUI.editAccount(account);
            showDetailView(manageAccountUI);
            return;
        }
        if (!embeddedMode && manageAccountUI != null) {
            if (onEditCallback != null) {
                onEditCallback.call(account);
            } else {
                manageAccountUI.editAccount(account);
            }
            return;
        }
        if (onEditCallback != null) {
            onEditCallback.call(account);
        }
    }

    public class AccountItem {
        private final SimpleStringProperty accountName;
        private final SimpleStringProperty accountId;
        private final SimpleStringProperty accountType;

        public AccountItem(SimpleStringProperty accountName, SimpleStringProperty accountId, SimpleStringProperty accountType) {
            this.accountName = accountName;
            this.accountId = accountId;
            this.accountType = accountType;
        }

        public String getAccountId() {
            return accountId.get();
        }

        public void setAccountId(String accountId) {
            this.accountId.set(accountId);
        }

        public SimpleStringProperty accountIdProperty() {
            return accountId;
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

        public String getAccountType() {
            return accountType.get();
        }

        public void setAccountType(String accountType) {
            this.accountType.set(accountType);
        }

        public SimpleStringProperty accountTypeProperty() {
            return accountType;
        }
    }
}
