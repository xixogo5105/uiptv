package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.db.AccountDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.CategoryService;
import com.uiptv.widget.AutoGrowPaneVBox;
import com.uiptv.widget.SearchableFilterableTableView;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.Cursor;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;

import static com.uiptv.model.Account.AccountAction.itv;
import static com.uiptv.ui.RootApplication.primaryStage;

public class AccountListUI extends HBox {
    private final TableColumn<AccountItem, String> accountName = new TableColumn<>("Account List");
    private final BookmarkChannelListUI bookmarkChannelListUI;
    SearchableFilterableTableView table = new SearchableFilterableTableView();
    AccountService accountService = AccountService.getInstance();
    private Callback onEditCallback;

    public AccountListUI(BookmarkChannelListUI bookmarkChannelListUI) {
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        initWidgets();
        refresh();
    }

    public CategoryListUI refreshCategoryList(Account account) {
        List<Category> list = CategoryService.getInstance().get(account);
        if (account.isNotConnected()) return null;
        CategoryListUI spCat = new CategoryListUI(list, account, bookmarkChannelListUI);
        return spCat;
    }

    public void addCallbackHandler(Callback onEditCallback) {
        this.onEditCallback = onEditCallback;
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
        setSpacing(10);
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
    }

    private void addAccountClickHandler() {
        table.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                retrieveThreadedAccountCategories((AccountItem) table.getFocusModel().getFocusedItem(), itv);
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
        MenuItem itv = new MenuItem("TV/Channels");
        itv.setOnAction(actionEvent -> {
            retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.itv);
        });
        MenuItem vod = new MenuItem("Video On Demand (VOD)");
        vod.setOnAction(actionEvent -> {
            retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.vod);
        });
        MenuItem series = new MenuItem("Series");
        series.setOnAction(actionEvent -> {
            retrieveThreadedAccountCategories(row.getItem(), Account.AccountAction.series);
        });

        rowMenu.getItems().addAll(itv, vod, series);

        // only display context menu for non-empty rows:
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void retrieveThreadedAccountCategories(AccountItem item, Account.AccountAction accountAction) {
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
            } catch (Throwable ignored) {
            } finally {
                Platform.runLater(() -> primaryStage.getScene().setCursor(Cursor.DEFAULT));
            }

        }).start();
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

        public SimpleStringProperty accountIdProperty() {
            return accountId;
        }

        public void setAccountId(String accountId) {
            this.accountId.set(accountId);
        }

        public String getAccountName() {
            return accountName.get();
        }

        public SimpleStringProperty accountNameProperty() {
            return accountName;
        }

        public void setAccountName(String accountName) {
            this.accountName.set(accountName);
        }

        public String getAccountType() {
            return accountType.get();
        }

        public SimpleStringProperty accountTypeProperty() {
            return accountType;
        }

        public void setAccountType(String accountType) {
            this.accountType.set(accountType);
        }
    }
}