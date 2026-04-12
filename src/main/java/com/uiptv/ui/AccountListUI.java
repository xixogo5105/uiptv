package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountResolver;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.I18n;
import com.uiptv.widget.AutoGrowPaneVBox;
import com.uiptv.widget.SearchableFilterableTableView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Stage;
import lombok.Setter;

import java.util.*;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.CACHE_SUPPORTED;
import static com.uiptv.model.Account.VOD_AND_SERIES_SUPPORTED;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class AccountListUI extends HBox {
    private static final String MULTI_SELECTION_DISABLED_KEY = "autoThisActionIsDisabledForMultipleSelections";
    private static final Comparator<AccountItem> ACCOUNT_NAME_COMPARATOR =
            Comparator.comparing(AccountItem::getAccountName, String.CASE_INSENSITIVE_ORDER)
                    .thenComparing(AccountItem::getAccountName);
    private final TableColumn<AccountItem, String> accountName = new TableColumn<>(I18n.tr("accountListTitle"));
    private final AccountResolver accountResolver = new AccountResolver();
    private final boolean embeddedMode;
    private final VBox listView = new VBox(5);
    private final VBox detailView = new VBox(8);
    private final HBox navHeader = new HBox(6);
    private final Button backButton = createBackButton();
    private final Button homeButton = createHomeButton();
    private final VBox detailContent = new VBox();
    private final Button newAccountButton = new Button(I18n.tr("autoAdd"));
    private final Deque<Node> viewStack = new ArrayDeque<>();
    private final VBox embeddedContainer = new VBox();
    SearchableFilterableTableView table = new SearchableFilterableTableView();
    AccountService accountService = AccountService.getInstance();
    @Setter
    private ManageAccountUI manageAccountUI;
    private Node currentContent;
    private Callback<Object> onEditCallback;
    private Callback<Object> onDeleteCallback;
    private boolean isPromptShowing = false;
    private final ObservableList<AccountItem> masterAccountItems = FXCollections.observableArrayList();
    private AccountSortMode accountSortMode = AccountSortMode.DEFAULT;

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
        sceneProperty().addListener((_, _, newScene) -> {
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

        List<AccountResolver.AccountRow> resolved = accountResolver.resolveAccounts();
        for (int index = 0; index < resolved.size(); index++) {
            AccountResolver.AccountRow account = resolved.get(index);
            catList.add(new AccountItem(
                    new SimpleStringProperty(account.getAccountName()),
                    new SimpleStringProperty(account.getDbId()),
                    new SimpleStringProperty(account.getType()),
                    account.isPinToTop(),
                    index
            ));
        }
        masterAccountItems.setAll(catList);
        applyAccountOrdering();
    }

    private void initWidgets() {
        setSpacing(5);
        setPadding(new Insets(5));
        setFillHeight(true);
        table.setEditable(true);
        table.getColumns().add(accountName);
        accountName.setVisible(true);
        accountName.setSortType(TableColumn.SortType.ASCENDING);
        accountName.setSortable(false);
        accountName.setCellValueFactory(cellData -> cellData.getValue().accountNameProperty());
        accountName.setCellFactory(_ -> createAccountNameCell());
        installAccountHeaderSortHandler();
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
        newAccountButton.setOnAction(_ -> {
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
        backButton.setOnAction(_ -> showPreviousView());
        homeButton.setOnAction(_ -> showAccountListView());
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
            Node content = detailContent.getChildren().getFirst();
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
                // Clear all children to allow garbage collection of CategoryListUI
                getChildren().clear();
                table.getItems().clear();
                masterAccountItems.clear();
            }
        });
    }

    private void applyAccountOrdering() {
        List<AccountItem> orderedItems = new ArrayList<>(masterAccountItems);
        if (accountSortMode == AccountSortMode.DEFAULT) {
            orderedItems.sort(Comparator.comparing(AccountItem::isPinToTop).reversed()
                    .thenComparingInt(AccountItem::getOriginalOrder));
        } else if (accountSortMode == AccountSortMode.DESCENDING) {
            orderedItems.sort(ACCOUNT_NAME_COMPARATOR.reversed());
        } else {
            orderedItems.sort(ACCOUNT_NAME_COMPARATOR);
        }
        table.setItems(FXCollections.observableArrayList(orderedItems));
        table.filterByAccountType();
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
        accountSortMode = switch (accountSortMode) {
            case DEFAULT -> AccountSortMode.ASCENDING;
            case ASCENDING -> AccountSortMode.DESCENDING;
            case DESCENDING -> AccountSortMode.DEFAULT;
        };
        accountName.setSortType(accountSortMode == AccountSortMode.DESCENDING
                ? TableColumn.SortType.DESCENDING
                : TableColumn.SortType.ASCENDING);
        applyAccountOrdering();
        event.consume();
    }

    private void addAccountClickHandler() {
        table.setOnKeyReleased(this::handleAccountKeyReleased);
        table.setRowFactory(_ -> {
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
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);

        MenuItem editAccount = new MenuItem(I18n.tr("autoEditManageAccount"));
        editAccount.setOnAction(_ -> runSingleSelectionAction(() -> openManageAccount(row.getItem())));

        MenuItem itv = new MenuItem(I18n.tr("autoTvChannels"));
        itv.setOnAction(_ -> runSingleSelectionAction(() -> retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.itv)));

        MenuItem vod = new MenuItem(I18n.tr("autoVod"));
        vod.setOnAction(_ -> runSingleSelectionAction(() -> retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.vod)));

        MenuItem series = new MenuItem(I18n.tr("autoSeries"));
        series.setOnAction(_ -> runSingleSelectionAction(() -> retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.series)));

        MenuItem reloadCache = new MenuItem(I18n.tr("autoReloadCache"));
        reloadCache.setOnAction(_ -> handleReloadCache(row.getItem()));

        MenuItem deleteItem = new MenuItem(I18n.tr("autoDeleteAccount"));
        deleteItem.getStyleClass().add("danger-menu-item");
        deleteItem.setOnAction(_ -> handleDeleteAccounts());

        rowMenu.getItems().addAll(editAccount, new SeparatorMenuItem(), itv, vod, series, new SeparatorMenuItem(), reloadCache, deleteItem);

        rowMenu.setOnShowing(_ -> {
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
        List<AccountItem> selectedItems = table.getSelectionModel().getSelectedItems();
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

        RootApplication.getPrimaryStage().getScene().setCursor(Cursor.WAIT);

        new Thread(() -> {
            try {
                final List<Category> list = CategoryService.getInstance().get(account);

                Platform.runLater(() -> categoryListUI.setItems(list));
            } catch (Exception e) {
                Platform.runLater(() -> showErrorAlert(I18n.tr("autoFailedRefreshChannels", e.getMessage())));
            } finally {
                Platform.runLater(() -> RootApplication.getPrimaryStage().getScene().setCursor(Cursor.DEFAULT));
            }
        }).start();
    }

    private Stage resolveOwnerStage() {
        if (getScene() != null && getScene().getWindow() instanceof Stage stage) {
            return stage;
        }
        return RootApplication.getPrimaryStage();
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

    public static class AccountItem {
        private final SimpleStringProperty accountName;
        private final SimpleStringProperty accountId;
        private final SimpleStringProperty accountType;
        private final boolean pinToTop;
        private final int originalOrder;

        public AccountItem(SimpleStringProperty accountName, SimpleStringProperty accountId, SimpleStringProperty accountType, boolean pinToTop, int originalOrder) {
            this.accountName = accountName;
            this.accountId = accountId;
            this.accountType = accountType;
            this.pinToTop = pinToTop;
            this.originalOrder = originalOrder;
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

    }

    private enum AccountSortMode {
        DEFAULT,
        ASCENDING,
        DESCENDING
    }
}
