package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.Platform;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;

public class BookmarkChannelListUI extends HBox {
    private SearchableTableView bookmarkTable = new SearchableTableView();
    private TableColumn<BookmarkItem, String> bookmarkColumn = new TableColumn<>("bookmarkColumn");
    private TabPane categoryTabPane = new TabPane();
    private Button manageCategoriesButton = new Button("Manage Categories");

    public BookmarkChannelListUI() {
        initWidgets();
        refresh();
    }

    public void refresh() {
        List<BookmarkItem> catList = new ArrayList<>();
        List<Bookmark> list = BookmarkService.getInstance().read().stream().toList();
        list.forEach(i -> catList.add(new BookmarkItem(new SimpleStringProperty(i.getDbId()), new SimpleStringProperty(i.getChannelName()),
                new SimpleStringProperty(i.getChannelId()), new SimpleStringProperty(i.getCmd()), new SimpleStringProperty(i.getAccountName()), new SimpleStringProperty(i.getCategoryTitle()), new SimpleStringProperty(i.getServerPortalUrl()), new SimpleStringProperty(i.getChannelName() + " (" + i.getAccountName() + ")"))));
        bookmarkTable.setItems(FXCollections.observableArrayList(catList));
        bookmarkTable.addTextFilter();
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(5);
        bookmarkTable.setEditable(true);
        bookmarkTable.getColumns().addAll(bookmarkColumn);
        bookmarkColumn.setVisible(true);
        bookmarkColumn.setCellValueFactory(cellData -> cellData.getValue().channelAccountNameProperty());
        bookmarkColumn.setSortType(TableColumn.SortType.ASCENDING);
        bookmarkColumn.setText("Bookmarked Channels");
        populateCategoryTabPane();
        categoryTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> applyCategoryFilter());
        manageCategoriesButton.setOnAction(event -> openCategoryManagementPopup());

        HBox hBox = new HBox(5, categoryTabPane);
        HBox.setHgrow(categoryTabPane, Priority.ALWAYS);
        hBox.getChildren().add(manageCategoriesButton);
        VBox vBox = new VBox(5, hBox, new AutoGrowVBox(5, bookmarkTable.getSearchTextField(), bookmarkTable));

        getChildren().add(vBox);
        addChannelClickHandler();
    }

    void populateCategoryTabPane() {
        categoryTabPane.getTabs().clear();
        List<BookmarkCategory> categories = new ArrayList<>();
        categories.add(new BookmarkCategory(null, "All"));
        categories.addAll(BookmarkService.getInstance().getAllCategories());
        for (BookmarkCategory category : categories) {
            Tab tab = new Tab(category.getName());
            tab.setClosable(false);
            tab.setUserData(category);
            categoryTabPane.getTabs().add(tab);
        }
        categoryTabPane.getSelectionModel().selectFirst();
    }

    private void applyCategoryFilter() {
        Tab selectedTab = categoryTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            BookmarkCategory selectedCategory = (BookmarkCategory) selectedTab.getUserData();
            if (selectedCategory != null && !"All".equals(selectedCategory.getName())) {
                bookmarkTable.setItems(FXCollections.observableArrayList(
                        BookmarkService.getInstance().read().stream()
                                .filter(bookmark -> selectedCategory.getId().equals(bookmark.getCategoryId()))
                                .map(bookmark -> new BookmarkItem(
                                        new SimpleStringProperty(bookmark.getDbId()),
                                        new SimpleStringProperty(bookmark.getChannelName()),
                                        new SimpleStringProperty(bookmark.getChannelId()),
                                        new SimpleStringProperty(bookmark.getCmd()),
                                        new SimpleStringProperty(bookmark.getAccountName()),
                                        new SimpleStringProperty(bookmark.getCategoryTitle()),
                                        new SimpleStringProperty(bookmark.getServerPortalUrl()),
                                        new SimpleStringProperty(bookmark.getChannelName() + " (" + bookmark.getAccountName() + ")")
                                ))
                                .toList()
                ));
            } else {
                refresh();
            }
        }
    }

    private void openCategoryManagementPopup() {
        Stage popupStage = new Stage();
        CategoryManagementPopup popup = new CategoryManagementPopup(this);
        Scene scene = new Scene(popup, 300, 400);
        popupStage.setTitle("Manage Categories");
        popupStage.setScene(scene);
        popupStage.showAndWait();
        refresh();
    }

    private void addChannelClickHandler() {
        bookmarkTable.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                play((BookmarkItem) bookmarkTable.getFocusModel().getFocusedItem(), false, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            }
        });
        bookmarkTable.setRowFactory(tv -> {
            TableRow<BookmarkItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    play(row.getItem(), false, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
    }

    private void addRightClickContextMenu(TableRow<BookmarkItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        MenuItem editItem = new MenuItem("Remove from favorite");
        editItem.setOnAction(actionEvent -> {
            rowMenu.hide();
            BookmarkService.getInstance().remove(row.getItem().getBookmarkId());
            refresh();
        });

        MenuItem playerItem = new MenuItem("Reconnect & Play");
        playerItem.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), true, ConfigurationService.getInstance().read().getDefaultPlayerPath());
        });
        MenuItem player1Item = new MenuItem("Player 1");
        player1Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), false, ConfigurationService.getInstance().read().getPlayerPath1());
        });
        MenuItem player2Item = new MenuItem("Player 2");
        player2Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), false, ConfigurationService.getInstance().read().getPlayerPath2());
        });
        MenuItem player3Item = new MenuItem("Player 3");
        player3Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), false, ConfigurationService.getInstance().read().getPlayerPath3());
        });

        Menu addToMenu = new Menu("Add to");
        List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();
        for (BookmarkCategory category : categories) {
            MenuItem categoryItem = new MenuItem(category.getName());
            categoryItem.setOnAction(event -> {
                row.getItem().setCategoryTitle(category.getName());
                Bookmark b = BookmarkService.getInstance().getBookmark(row.getItem().getBookmarkId());
                b.setCategoryId(category.getId());
                BookmarkService.getInstance().save(b);
                refresh();
            });
            addToMenu.getItems().add(categoryItem);
        }
        rowMenu.getItems().addAll(editItem, player1Item, player2Item, player3Item, playerItem, addToMenu);
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void play(BookmarkItem item, boolean hardReset, String playerPath) {
        try {
            Account account = AccountService.getInstance().getAll().get(item.getAccountName());
            account.setServerPortalUrl(item.getServerPortalUrl());
            if (hardReset) {
                Platform.executeCommand(playerPath, PlayerService.getInstance().runBookmark(account, item.getCmd()));
            } else {
                Platform.executeCommand(playerPath, PlayerService.getInstance().get(account, item.getCmd()));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class BookmarkItem {
        private final SimpleStringProperty bookmarkId;
        private final SimpleStringProperty channelName;
        private final SimpleStringProperty channelId;
        private final SimpleStringProperty cmd;
        private final SimpleStringProperty accountName;
        private final SimpleStringProperty categoryTitle;
        private final SimpleStringProperty serverPortalUrl;
        private final SimpleStringProperty channelAccountName;

        public BookmarkItem(SimpleStringProperty bookmarkId, SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, SimpleStringProperty accountName, SimpleStringProperty categoryTitle, SimpleStringProperty serverPortalUrl, SimpleStringProperty channelAccountName) {
            this.bookmarkId = bookmarkId;
            this.channelName = channelName;
            this.channelId = channelId;
            this.cmd = cmd;
            this.accountName = accountName;
            this.categoryTitle = categoryTitle;
            this.serverPortalUrl = serverPortalUrl;
            this.channelAccountName = channelAccountName;
        }

        public String getBookmarkId() {
            return bookmarkId.get();
        }

        public SimpleStringProperty bookmarkIdProperty() {
            return bookmarkId;
        }

        public void setBookmarkId(String bookmarkId) {
            this.bookmarkId.set(bookmarkId);
        }

        public String getChannelName() {
            return channelName.get();
        }

        public void setChannelName(String channelName) {
            this.channelName.set(channelName);
        }

        public String getChannelId() {
            return channelId.get();
        }

        public void setChannelId(String channelId) {
            this.channelId.set(channelId);
        }

        public String getCmd() {
            return cmd.get();
        }

        public void setCmd(String cmd) {
            this.cmd.set(cmd);
        }

        public String getAccountName() {
            return accountName.get();
        }

        public String getCategoryTitle() {
            return categoryTitle.get();
        }

        public SimpleStringProperty categoryTitleProperty() {
            return categoryTitle;
        }

        public void setCategoryTitle(String categoryTitle) {
            this.categoryTitle.set(categoryTitle);
        }

        public String getChannelAccountName() {
            return channelAccountName.get();
        }

        public SimpleStringProperty channelAccountNameProperty() {
            return channelAccountName;
        }

        public void setChannelAccountName(String channelAccountName) {
            this.channelAccountName.set(channelAccountName);
        }

        public SimpleStringProperty accountNameProperty() {
            return accountName;
        }

        public void setAccountName(String accountName) {
            this.accountName.set(accountName);
        }

        public SimpleStringProperty cmdProperty() {
            return cmd;
        }

        public SimpleStringProperty channelNameProperty() {
            return channelName;
        }

        public SimpleStringProperty channelIdProperty() {
            return channelId;
        }

        public String getServerPortalUrl() {
            return serverPortalUrl.get();
        }

        public SimpleStringProperty serverPortalUrlProperty() {
            return serverPortalUrl;
        }

        public void setServerPortalUrl(String serverPortalUrl) {
            this.serverPortalUrl.set(serverPortalUrl);
        }
    }
}