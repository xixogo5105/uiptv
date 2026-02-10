package com.uiptv.ui;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.*;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.ImageCacheManager;
import com.uiptv.util.Platform;
import com.uiptv.widget.SearchableTableViewWithButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class BookmarkChannelListUI extends HBox {
    private final SearchableTableViewWithButton bookmarkTable = new SearchableTableViewWithButton();
    private final TableColumn<BookmarkItem, String> bookmarkColumn = new TableColumn<>("bookmarkColumn");
    private final TabPane categoryTabPane = new TabPane();
    private boolean isPromptShowing = false;

    public BookmarkChannelListUI() { // Removed MediaPlayer argument
        ImageCacheManager.clearCache("bookmark");
        initWidgets();
        refresh();
    }

    public void refresh() {
        List<BookmarkItem> catList = new ArrayList<>();
        List<Bookmark> list = BookmarkService.getInstance().read().stream().toList();
        list.forEach(i -> catList.add(createBookmarkItem(i)));
        bookmarkTable.getTableView().setItems(FXCollections.observableArrayList(catList));
        bookmarkTable.addTextFilter();
        applyCategoryFilter();
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(5);
        setupBookmarkTable();
        populateCategoryTabPane();
        setupCategoryTabPaneListener();
        setupSearchTextFieldListener();
        setupManageCategoriesButton();

        HBox hBox = new HBox(5, categoryTabPane);
        HBox.setHgrow(categoryTabPane, Priority.ALWAYS);
        VBox vBox = new VBox(5, hBox, bookmarkTable);

        getChildren().add(vBox);
        addChannelClickHandler();
    }

    private void setupBookmarkTable() {
        bookmarkTable.getTableView().setEditable(true);
        bookmarkTable.getTableView().getColumns().addAll(bookmarkColumn);
        bookmarkColumn.setVisible(true);
        bookmarkColumn.setCellValueFactory(cellData -> cellData.getValue().channelAccountNameProperty());
        bookmarkColumn.setSortType(TableColumn.SortType.ASCENDING);
        bookmarkColumn.setText("Bookmarked Channels");

        bookmarkColumn.setCellFactory(column -> new TableCell<>() {
            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final Pane spacer = new Pane();
            private final ImageView imageView = new ImageView();

            {
                imageView.setFitWidth(32);
                imageView.setFitHeight(32);
                imageView.setPreserveRatio(true);
                HBox.setHgrow(spacer, Priority.ALWAYS);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(imageView, nameLabel, spacer);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                BookmarkItem bookmarkItem = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                        ? getTableView().getItems().get(getIndex())
                        : null;

                if (bookmarkItem == null) {
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(item);
                imageView.setImage(ImageCacheManager.DEFAULT_IMAGE); // Set default image immediately

                ImageCacheManager.loadImageAsync(bookmarkItem.getLogo(), "bookmark")
                        .thenAccept(image -> {
                            if (image != null && getItem() != null && getIndex() < getTableView().getItems().size()) {
                                runLater(() -> imageView.setImage(image));
                            }
                        });
                setGraphic(graphic);
            }
        });
    }

    private void setupCategoryTabPaneListener() {
        categoryTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> applyCategoryFilter());
    }

    private void setupSearchTextFieldListener() {
        bookmarkTable.getSearchTextField().textProperty().addListener((observable, oldValue, newValue) -> applyCategoryFilter());
    }

    private void setupManageCategoriesButton() {
        bookmarkTable.getManageCategoriesButton().setOnAction(event -> openCategoryManagementPopup());
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
            String searchText = bookmarkTable.getSearchTextField().getText().toLowerCase();
            List<BookmarkItem> filteredList = BookmarkService.getInstance().read().stream()
                    .filter(bookmark -> filterBookmark(bookmark, selectedCategory, searchText))
                    .map(this::createBookmarkItem)
                    .toList();
            bookmarkTable.getTableView().setItems(FXCollections.observableArrayList(filteredList));
        }
    }

    private boolean filterBookmark(Bookmark bookmark, BookmarkCategory selectedCategory, String searchText) {
        return (selectedCategory == null || "All".equals(selectedCategory.getName()) || selectedCategory.getId().equals(bookmark.getCategoryId())) &&
                (searchText.isEmpty() || bookmark.getChannelName().toLowerCase().contains(searchText) || bookmark.getAccountName().toLowerCase().contains(searchText));
    }

    private BookmarkItem createBookmarkItem(Bookmark bookmark) {
        Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());
        Channel channel = ChannelDb.get().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId());
        String logo = channel != null ? channel.getLogo() : "";
        return new BookmarkItem(
                new SimpleStringProperty(bookmark.getDbId()),
                new SimpleStringProperty(bookmark.getChannelName()),
                new SimpleStringProperty(bookmark.getChannelId()),
                new SimpleStringProperty(bookmark.getCmd()),
                new SimpleStringProperty(bookmark.getAccountName()),
                new SimpleStringProperty(bookmark.getCategoryTitle()),
                new SimpleStringProperty(bookmark.getServerPortalUrl()),
                new SimpleStringProperty(bookmark.getChannelName() + " (" + bookmark.getAccountName() + ")"),
                new SimpleStringProperty(bookmark.getCategoryId()),
                new SimpleStringProperty(logo)
        );
    }

    private void openCategoryManagementPopup() {
        Stage popupStage = new Stage();
        CategoryManagementPopup popup = new CategoryManagementPopup(this);
        Scene scene = new Scene(popup, 300, 400);
        scene.getStylesheets().add(RootApplication.currentTheme);
        popupStage.setTitle("Manage Categories");
        popupStage.setScene(scene);
        popupStage.showAndWait();
        refresh();
    }

    private void addChannelClickHandler() {
        bookmarkTable.getTableView().setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.DELETE) {
                handleDeleteMultipleBookmarks();
            } else if (event.getCode() == KeyCode.ENTER) {
                if (isPromptShowing) {
                    event.consume();
                    isPromptShowing = false;
                } else {
                    play((BookmarkItem) bookmarkTable.getTableView().getFocusModel().getFocusedItem(), false, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                }
            }
        });
        bookmarkTable.getTableView().setRowFactory(tv -> {
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
            handleDeleteMultipleBookmarks();
        });

        MenuItem playerEmbeddedItem = new MenuItem("Embedded Player");
        playerEmbeddedItem.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), false, "embedded"); // Use "embedded" as a special playerPath
            }
        });

        MenuItem playerItem = new MenuItem("Reconnect & Play");
        playerItem.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), true, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            }
        });

        MenuItem player1Item = new MenuItem("Player 1");
        player1Item.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), false, ConfigurationService.getInstance().read().getPlayerPath1());
            }
        });

        MenuItem player2Item = new MenuItem("Player 2");
        player2Item.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), false, ConfigurationService.getInstance().read().getPlayerPath2());
            }
        });

        MenuItem player3Item = new MenuItem("Player 3");
        player3Item.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), false, ConfigurationService.getInstance().read().getPlayerPath3());
            }
        });

        Menu addToMenu = new Menu("Add to");
        List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();
        for (BookmarkCategory category : categories) {
            MenuItem categoryItem = new MenuItem(category.getName());
            categoryItem.setOnAction(event -> {
                for (BookmarkItem selectedItem : (List<BookmarkItem>) (List<?>) bookmarkTable.getTableView().getSelectionModel().getSelectedItems()) {
                    selectedItem.setCategoryTitle(category.getName());
                    Bookmark b = BookmarkService.getInstance().getBookmark(selectedItem.getBookmarkId());
                    b.setCategoryId(category.getId());
                    BookmarkService.getInstance().save(b);
                }
                refresh();
            });
            addToMenu.getItems().add(categoryItem);
        }
        rowMenu.getItems().addAll(editItem, playerEmbeddedItem, player1Item, player2Item, player3Item, playerItem, addToMenu);
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void handleDeleteMultipleBookmarks() {
        int selectedCount = bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size();

        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Confirmation");
        alert.setHeaderText(null);
        alert.setContentText("Are you sure you want to remove " + selectedCount + " bookmark(s) from favorite? Bookmark(s) to be deleted:\n" +
                bookmarkTable.getTableView().getSelectionModel().getSelectedItems().stream()
                        .map(bookmarkItem -> ((BookmarkItem) bookmarkItem).getChannelName())
                        .collect(Collectors.joining(", ")));

        isPromptShowing = true;
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                for (BookmarkItem selectedItem : (List<BookmarkItem>) (List<?>) bookmarkTable.getTableView().getSelectionModel().getSelectedItems()) {
                    BookmarkService.getInstance().remove(selectedItem.getBookmarkId());
                }
                refresh();
            }
        });
    }

    private void play(BookmarkItem item, boolean hardReset, String playerPath) {
        try {
            Account account = AccountService.getInstance().getAll().get(item.getAccountName());
            account.setServerPortalUrl(item.getServerPortalUrl());

            Bookmark bookmark = new Bookmark(item.getAccountName(), item.getCategoryTitle(), item.getChannelId(), item.getChannelName(), item.getCmd(), item.getServerPortalUrl(), item.getCategoryId());
            bookmark.setDbId(item.getBookmarkId());

            PlayerResponse response;
            Channel channel = new Channel();
            channel.setCmd(bookmark.getCmd());
            channel.setChannelId(bookmark.getChannelId());
            channel.setName(bookmark.getChannelName());
            channel.setDrmType(bookmark.getDrmType());
            channel.setDrmLicenseUrl(bookmark.getDrmLicenseUrl());
            channel.setClearKeysJson(bookmark.getClearKeysJson());
            channel.setInputstreamaddon(bookmark.getInputstreamaddon());
            channel.setManifestType(bookmark.getManifestType());

            if (hardReset) {
                response = PlayerService.getInstance().runBookmark(account, bookmark);
            } else {
                response = PlayerService.getInstance().get(account, channel);
            }

            response.setFromChannel(channel, account); // Ensure response has channel and account

            String evaluatedStreamUrl = response.getUrl();

            boolean useEmbeddedPlayerConfig = ConfigurationService.getInstance().read().isEmbeddedPlayer();
            boolean playerPathIsEmbedded = (playerPath != null && playerPath.toLowerCase().contains("embedded"));

            if (playerPathIsEmbedded) {
                if (useEmbeddedPlayerConfig) {
                    getPlayer().play(response);
                } else {
                    showErrorAlert("Embedded player is not enabled in settings. Please enable it or choose an external player.");
                }
            } else { // playerPath is not "embedded" or is blank
                if (isBlank(playerPath) && useEmbeddedPlayerConfig) { // Default player is embedded
                    getPlayer().play(response);
                } else if (isBlank(playerPath) && !useEmbeddedPlayerConfig) { // Default player is not embedded, and playerPath is blank
                    showErrorAlert("No default player configured and embedded player is not enabled. Please configure a player in settings.");
                } else { // Use external player
                    Platform.executeCommand(playerPath, evaluatedStreamUrl);
                }
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
        private final SimpleStringProperty categoryId;
        private final SimpleStringProperty logo;

        public BookmarkItem(SimpleStringProperty bookmarkId, SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, SimpleStringProperty accountName, SimpleStringProperty categoryTitle, SimpleStringProperty serverPortalUrl, SimpleStringProperty channelAccountName, SimpleStringProperty categoryId, SimpleStringProperty logo) {
            this.bookmarkId = bookmarkId;
            this.channelName = channelName;
            this.channelId = channelId;
            this.cmd = cmd;
            this.accountName = accountName;
            this.categoryTitle = categoryTitle;
            this.serverPortalUrl = serverPortalUrl;
            this.channelAccountName = channelAccountName;
            this.categoryId = categoryId;
            this.logo = logo;
        }

        public String getBookmarkId() {
            return bookmarkId.get();
        }

        public void setBookmarkId(String bookmarkId) {
            this.bookmarkId.set(bookmarkId);
        }

        public SimpleStringProperty bookmarkIdProperty() {
            return bookmarkId;
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

        public void setAccountName(String accountName) {
            this.accountName.set(accountName);
        }

        public String getCategoryTitle() {
            return categoryTitle.get();
        }

        public void setCategoryTitle(String categoryTitle) {
            this.categoryTitle.set(categoryTitle);
        }

        public SimpleStringProperty categoryTitleProperty() {
            return categoryTitle;
        }

        public String getChannelAccountName() {
            return channelAccountName.get();
        }

        public void setChannelAccountName(String channelAccountName) {
            this.channelAccountName.set(channelAccountName);
        }

        public SimpleStringProperty channelAccountNameProperty() {
            return channelAccountName;
        }

        public SimpleStringProperty accountNameProperty() {
            return accountName;
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

        public void setServerPortalUrl(String serverPortalUrl) {
            this.serverPortalUrl.set(serverPortalUrl);
        }

        public SimpleStringProperty serverPortalUrlProperty() {
            return serverPortalUrl;
        }

        public String getCategoryId() {
            return categoryId.get();
        }

        public SimpleStringProperty categoryIdProperty() {
            return categoryId;
        }

        public String getLogo() {
            return logo.get();
        }

        public SimpleStringProperty logoProperty() {
            return logo;
        }
    }
}
