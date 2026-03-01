package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.CategoryService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.AutoGrowPaneVBox;
import com.uiptv.widget.SearchableFilterableTableView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
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

public class AccountListUI extends HBox {
    private final TableColumn<AccountItem, String> accountName = new TableColumn<>("Account List");
    private final CacheService cacheService = new CacheServiceImpl();
    SearchableFilterableTableView table = new SearchableFilterableTableView();
    AccountService accountService = AccountService.getInstance();
    private Callback onEditCallback;
    private Callback onDeleteCallback;
    private boolean isPromptShowing = false;

    public AccountListUI() { // Removed MediaPlayer argument
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

    public void addUpdateCallbackHandler(Callback onEditCallback) {
        this.onEditCallback = onEditCallback;
    }

    public void addDeleteCallbackHandler(Callback onDeleteCallback) {
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
        setPadding(new Insets(5, 0, 0, 0));
        table.setEditable(true);
        table.getColumns().addAll(accountName);
        accountName.setVisible(true);
        accountName.setSortType(TableColumn.SortType.ASCENDING);
        accountName.setSortable(true);
        accountName.setCellValueFactory(cellData -> cellData.getValue().accountNameProperty());
        HBox sceneBox = new HBox(5, table.getTextField(), table.getMenuButton());
        sceneBox.setMaxHeight(25);
        getChildren().addAll(new AutoGrowPaneVBox(5, sceneBox, table));
        addAccountClickHandler();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
        registerSceneCleanupListener();
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
        table.setOnKeyReleased(event -> {
            AccountItem focusedItem = (AccountItem) table.getFocusModel().getFocusedItem();
            if (focusedItem != null && onEditCallback != null) {
                onEditCallback.call(accountService.getById(focusedItem.accountId.get()));
            }
            if (event.getCode() == KeyCode.DELETE) {
                handleDeleteAccounts();
            } else if (event.getCode() == KeyCode.ENTER) {
                if (isPromptShowing) {
                    event.consume();
                    isPromptShowing = false;
                } else {
                    retrieveThreadedAccountCategories((AccountItem) table.getFocusModel().getFocusedItem(), itv);
                }
            }
        });
        table.setRowFactory(tv -> {
            TableRow<AccountItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                    AccountItem clickedRow = row.getItem();
                    try {
                        if (onEditCallback != null) {
                            onEditCallback.call(accountService.getById(clickedRow.getAccountId()));
                        }
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                } else if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    retrieveThreadedAccountCategories(row.getItem(), itv);
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
    }

    private void addRightClickContextMenu(TableRow<AccountItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        MenuItem itv = new MenuItem("TV Channels");
        itv.setOnAction(actionEvent -> {
            if (table.getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.itv);
            }
        });

        MenuItem vod = new MenuItem("VOD");
        vod.setOnAction(actionEvent -> {
            if (table.getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.vod);
            }
        });

        MenuItem series = new MenuItem("Series");
        series.setOnAction(actionEvent -> {
            if (table.getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.series);
            }
        });

        MenuItem reloadCache = new MenuItem("Reload Cache");
        reloadCache.setOnAction(actionEvent -> handleReloadCache(row.getItem()));

        MenuItem deleteItem = new MenuItem("Delete Account");
        deleteItem.setOnAction(actionEvent -> handleDeleteAccounts());

        rowMenu.getItems().addAll(itv, vod, series, new SeparatorMenuItem(), reloadCache, deleteItem);

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
            showErrorAlert("No cache-supported account selected.");
            return;
        }
        ReloadCachePopup.showPopup(resolveOwnerStage(), accounts, this::refresh);
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
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to remove " + selectedCount + " account(s)? Account(s) to be deleted:\n" +
                table.getSelectionModel().getSelectedItems().stream()
                        .map(accountItem -> ((AccountItem) accountItem).getAccountName())
                        .collect(Collectors.joining(", ")));
        if (RootApplication.currentTheme != null) {
            alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
        }
        isPromptShowing = true;
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                for (AccountItem selectedItem : (List<AccountItem>) (List<?>) table.getSelectionModel().getSelectedItems()) {
                    AccountService.getInstance().delete(selectedItem.getAccountId());
                    if (onDeleteCallback != null) {
                        onDeleteCallback.call(accountService.getById(selectedItem.getAccountId()));
                    }
                }
                refresh();
            }
        });
    }

    private void retrieveThreadedAccountCategories(AccountItem item, Account.AccountAction accountAction) {
        Account account = accountService.getById(item.getAccountId());
        if (account == null) {
            showErrorAlert("Unable to find account.");
            return;
        }
        account.setAction(accountAction);

        // Immediately show the CategoryListUI in loading state
        CategoryListUI categoryListUI = new CategoryListUI(account);
        AccountListUI.this.getChildren().clear();
        HBox sceneBox = new HBox(5, table.getTextField(), table.getMenuButton());
        sceneBox.setMaxHeight(25);
        AccountListUI.this.getChildren().addAll(new VBox(5, sceneBox, table), categoryListUI);

        boolean noCachingNeeded = NOT_LIVE_TV_CHANNELS.contains(account.getAction()) || account.getType() == AccountType.RSS_FEED;
        boolean channelsAlreadyLoaded = noCachingNeeded || cacheService.getChannelCountForAccount(account.getDbId()) > 0;

        if (!channelsAlreadyLoaded) {
            ReloadCachePopup.showPopup(resolveOwnerStage(), List.of(account), this::refresh);
        }

        primaryStage.getScene().setCursor(Cursor.WAIT);

        new Thread(() -> {
            try {
                final List<Category> list = CategoryService.getInstance().get(account);

                Platform.runLater(() -> {
                    categoryListUI.setItems(list);
                });
            } catch (Exception e) {
                Platform.runLater(() -> showErrorAlert("Failed to refresh channels: " + e.getMessage()));
            } finally {
                Platform.runLater(() -> {
                    primaryStage.getScene().setCursor(Cursor.DEFAULT);
                });
            }
        }).start();
    }

    private Stage resolveOwnerStage() {
        if (getScene() != null && getScene().getWindow() instanceof Stage) {
            return (Stage) getScene().getWindow();
        }
        return primaryStage;
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
