package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.db.AccountDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.CategoryService;
import com.uiptv.util.AccountType;
import com.uiptv.widget.AutoGrowPaneVBox;
import com.uiptv.widget.LogPopupUI;
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

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.model.Account.NOT_LIVE_TV_CHANNELS;
import static com.uiptv.ui.RootApplication.primaryStage;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class AccountListUI extends HBox {
    private final TableColumn<AccountItem, String> accountName = new TableColumn<>("Account List");
    private final BookmarkChannelListUI bookmarkChannelListUI;
    private final CacheService cacheService = new CacheServiceImpl();
    SearchableFilterableTableView table = new SearchableFilterableTableView();
    AccountService accountService = AccountService.getInstance();
    private Callback onEditCallback;
    private Callback onDeleteCallback;
    private boolean isPromptShowing = false;

    public AccountListUI(BookmarkChannelListUI bookmarkChannelListUI) { // Removed MediaPlayer argument
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        initWidgets();
        refresh();
    }

    public CategoryListUI refreshCategoryList(Account account) {
        List<Category> list = CategoryService.getInstance().get(account);
        if (account.isNotConnected()) return null;
        return new CategoryListUI(list, account, bookmarkChannelListUI);
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
        HBox sceneBox = new HBox(5, table.getTextField(), table.getComboBox());
        sceneBox.setMaxHeight(25);
        getChildren().addAll(new AutoGrowPaneVBox(5, sceneBox, table));
        addAccountClickHandler();
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
    }

    private void addAccountClickHandler() {
        table.setOnKeyReleased(event -> {
            onEditCallback.call(AccountDb.get().getAccountById(((AccountItem) table.getFocusModel().getFocusedItem()).accountId.get()));
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
                        onEditCallback.call(AccountDb.get().getAccountById(clickedRow.getAccountId()));
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

        MenuItem deleteItem = new MenuItem("Delete Account");
        deleteItem.setOnAction(actionEvent -> handleDeleteAccounts());

        MenuItem itv = new MenuItem("TV/Channels");
        itv.setOnAction(actionEvent -> {
            if (table.getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.itv);
            }
        });

        MenuItem vod = new MenuItem("Video On Demand (VOD)");
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

        rowMenu.getItems().addAll(deleteItem, itv, vod, series);

        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
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
        isPromptShowing = true;
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                for (AccountItem selectedItem : (List<AccountItem>) (List<?>) table.getSelectionModel().getSelectedItems()) {
                    AccountService.getInstance().delete(selectedItem.getAccountId());
                    onDeleteCallback.call(AccountDb.get().getAccountById(selectedItem.getAccountId()));
                }
                refresh();
            }
        });
    }

    private void retrieveThreadedAccountCategories(AccountItem item, Account.AccountAction accountAction) {
        Account account = AccountDb.get().getAccountById(item.getAccountId());
        account.setAction(accountAction);
        boolean noCachingNeeded = NOT_LIVE_TV_CHANNELS.contains(account.getAction()) || account.getType() == AccountType.RSS_FEED;
        boolean channelsAlreadyLoaded = noCachingNeeded || cacheService.getChannelCountForAccount(account.getDbId()) > 0;

        if (!channelsAlreadyLoaded) {
            LogPopupUI logPopup = new LogPopupUI("Caching channels. This will take a while...");
            logPopup.getScene().getStylesheets().add(RootApplication.currentTheme);
            logPopup.show();
            primaryStage.getScene().setCursor(Cursor.WAIT);

            new Thread(() -> {
                try {
                    cacheService.reloadCache(account, logPopup.getLogger());
                    Platform.runLater(() -> {
                        try {
                            retrieveAccountCategories(item, accountAction);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } catch (IOException e) {
                    Platform.runLater(() -> showErrorAlert("Failed to refresh channels: " + e.getMessage()));
                } finally {
                    primaryStage.getScene().setCursor(Cursor.DEFAULT);
                    logPopup.closeGracefully();
                }
            }).start();
        } else {
            primaryStage.getScene().setCursor(Cursor.WAIT);
            new Thread(() -> {
                try {
                    Platform.runLater(() -> {
                        try {
                            retrieveAccountCategories(item, accountAction);
                        } catch (IOException e) {
                            throw new RuntimeException(e);
                        }
                    });
                } finally {
                    Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
                }
            }).start();
        }
    }

    private synchronized void retrieveAccountCategories(AccountItem clickedRow, Account.AccountAction accountAction) throws IOException {
        Account account = AccountDb.get().getAccountById(clickedRow.getAccountId());
        account.setAction(accountAction);
        CategoryListUI categoryListUI = refreshCategoryList(account);
        if (categoryListUI == null) return;
        AccountListUI.this.getChildren().clear();
        HBox sceneBox = new HBox(5, table.getTextField(), table.getComboBox());
        sceneBox.setMaxHeight(25);
        AccountListUI.this.getChildren().addAll(new VBox(5, sceneBox, table), categoryListUI);
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
