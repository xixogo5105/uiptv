package com.uiptv.ui;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.*;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.ImageCacheManager;
import com.uiptv.widget.SearchableTableViewWithButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.*;
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
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    private final SearchableTableViewWithButton<BookmarkItem> bookmarkTable = new SearchableTableViewWithButton<>();
    private final TableColumn<BookmarkItem, String> bookmarkColumn = new TableColumn<>("bookmarkColumn");
    private final TabPane categoryTabPane = new TabPane();
    private boolean isPromptShowing = false;

    public BookmarkChannelListUI() { // Removed MediaPlayer argument
        ImageCacheManager.clearCache("bookmark");
        initWidgets();
        refresh();
    }

    public void refresh() {
        Tab selectedTab = categoryTabPane.getSelectionModel().getSelectedItem();
        String selectedCategoryId = null;
        if (selectedTab != null) {
            BookmarkCategory category = (BookmarkCategory) selectedTab.getUserData();
            if (category != null) {
                selectedCategoryId = category.getId();
            }
        }
        applyCategoryFilter(selectedCategoryId);
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
        categoryTabPane.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
            if (newValue != null) {
                BookmarkCategory selectedCategory = (BookmarkCategory) newValue.getUserData();
                applyCategoryFilter(selectedCategory != null ? selectedCategory.getId() : null);
            }
        });
    }

    private void setupSearchTextFieldListener() {
        bookmarkTable.getSearchTextField().textProperty().addListener((observable, oldValue, newValue) -> refresh());
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

    private void applyCategoryFilter(String categoryId) {
        List<Bookmark> bookmarks = (categoryId == null)
                ? BookmarkService.getInstance().read()
                : BookmarkService.getInstance().getBookmarksByCategory(categoryId);

        String searchText = bookmarkTable.getSearchTextField().getText().toLowerCase();
        List<BookmarkItem> filteredList = bookmarks.stream()
                .filter(bookmark -> searchText.isEmpty() || bookmark.getChannelName().toLowerCase().contains(searchText) || bookmark.getAccountName().toLowerCase().contains(searchText))
                .map(this::createBookmarkItem)
                .collect(Collectors.toList());

        bookmarkTable.getTableView().setItems(FXCollections.observableArrayList(filteredList));
    }

    private BookmarkItem createBookmarkItem(Bookmark bookmark) {
        Account account = AccountService.getInstance().getAll().get(bookmark.getAccountName());
        Channel channel = ChannelDb.get().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId());
        String logo = channel != null ? channel.getLogo() : "";
        Account.AccountAction accountAction = bookmark.getAccountAction() != null ? bookmark.getAccountAction() : account.getAction();
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
                new SimpleStringProperty(logo),
                accountAction
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
                    play(bookmarkTable.getTableView().getFocusModel().getFocusedItem(), false, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                }
            }
        });
        bookmarkTable.getTableView().setRowFactory(tv -> {
            TableRow<BookmarkItem> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                    if (event.getClickCount() == 2) {
                        play(row.getItem(), false, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                    } else if (!event.isControlDown() && !event.isShiftDown()) {
                        TableView.TableViewSelectionModel<BookmarkItem> sm = bookmarkTable.getTableView().getSelectionModel();
                        if (sm.isSelected(row.getIndex())) {
                            // Optional: if you want a click on an already selected row to do nothing,
                            // or maybe to deselect all but this one if others are selected.
                            // For now, we ensure only this one is selected.
                            if (sm.getSelectedItems().size() > 1) {
                                sm.clearAndSelect(row.getIndex());
                            }
                        } else {
                            sm.clearAndSelect(row.getIndex());
                        }
                    }
                }
            });

            row.setOnDragDetected(event -> {
                if (!row.isEmpty()) {
                    Integer index = row.getIndex();
                    Dragboard db = row.startDragAndDrop(TransferMode.MOVE);
                    db.setDragView(row.snapshot(null, null));
                    ClipboardContent cc = new ClipboardContent();
                    cc.put(SERIALIZED_MIME_TYPE, index);
                    db.setContent(cc);
                    event.consume();
                }
            });

            row.setOnDragOver(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    if (row.getIndex() != (Integer) db.getContent(SERIALIZED_MIME_TYPE)) {
                        event.acceptTransferModes(TransferMode.MOVE);
                        event.consume();
                    }
                }
            });

            row.setOnDragDropped(event -> {
                Dragboard db = event.getDragboard();
                if (db.hasContent(SERIALIZED_MIME_TYPE)) {
                    int draggedIndex = (Integer) db.getContent(SERIALIZED_MIME_TYPE);
                    BookmarkItem draggedItem = bookmarkTable.getTableView().getItems().remove(draggedIndex);

                    int dropIndex = row.isEmpty() ? bookmarkTable.getTableView().getItems().size() : row.getIndex();
                    bookmarkTable.getTableView().getItems().add(dropIndex, draggedItem);

                    event.setDropCompleted(true);
                    bookmarkTable.getTableView().getSelectionModel().clearSelection();

                    // Save the new order
                    List<String> orderedDbIds = bookmarkTable.getTableView().getItems().stream()
                            .map(BookmarkItem::getBookmarkId)
                            .collect(Collectors.toList());
                    Tab selectedTab = categoryTabPane.getSelectionModel().getSelectedItem();
                    String categoryId = null;
                    if (selectedTab != null) {
                        BookmarkCategory category = (BookmarkCategory) selectedTab.getUserData();
                        if (category != null) {
                            categoryId = category.getId();
                        }
                    }
                    final String finalCategoryId = categoryId;
                    new Thread(() -> BookmarkService.getInstance().saveBookmarkOrder(finalCategoryId, orderedDbIds)).start();

                    event.consume();
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
                ObservableList<BookmarkItem> selectedItems = bookmarkTable.getTableView().getSelectionModel().getSelectedItems();
                for (BookmarkItem selectedItem : selectedItems) {
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
                        .map(BookmarkItem::getChannelName)
                        .collect(Collectors.joining(", ")));

        isPromptShowing = true;
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                ObservableList<BookmarkItem> selectedItems = bookmarkTable.getTableView().getSelectionModel().getSelectedItems();
                for (BookmarkItem selectedItem : selectedItems) {
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
            account.setAction(item.getAccountAction()); // Set the correct action

            Bookmark bookmark = new Bookmark(item.getAccountName(), item.getCategoryTitle(), item.getChannelId(), item.getChannelName(), item.getCmd(), item.getServerPortalUrl(), item.getCategoryId());
            bookmark.setDbId(item.getBookmarkId());
            bookmark.setAccountAction(item.getAccountAction());

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
                    com.uiptv.util.Platform.executeCommand(playerPath, evaluatedStreamUrl);
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
        private final Account.AccountAction accountAction;

        public BookmarkItem(SimpleStringProperty bookmarkId, SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, SimpleStringProperty accountName, SimpleStringProperty categoryTitle, SimpleStringProperty serverPortalUrl, SimpleStringProperty channelAccountName, SimpleStringProperty categoryId, SimpleStringProperty logo, Account.AccountAction accountAction) {
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
            this.accountAction = accountAction;
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

        public Account.AccountAction getAccountAction() {
            return accountAction;
        }
    }
}
