package com.uiptv.ui;

import com.uiptv.db.ChannelDb;
import com.uiptv.model.*;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.shared.Episode;
import com.uiptv.util.ImageCacheManager;
import com.uiptv.widget.SearchableTableViewWithButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
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
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class BookmarkChannelListUI extends HBox {
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    private final SearchableTableViewWithButton<BookmarkItem> bookmarkTable = new SearchableTableViewWithButton<>();
    private final TableColumn<BookmarkItem, String> bookmarkColumn = new TableColumn<>("bookmarkColumn");
    private final TabPane categoryTabPane = new TabPane();
    private boolean isPromptShowing = false;
    private final List<BookmarkItem> allBookmarkItems = new ArrayList<>();

    public BookmarkChannelListUI() {
        ImageCacheManager.clearCache("bookmark");
        initWidgets();
        forceReload();
    }

    public void forceReload() {
        bookmarkTable.getTableView().setPlaceholder(new Label("Loading bookmarks..."));
        new Thread(() -> {
            List<Bookmark> bookmarks = BookmarkService.getInstance().read();
            List<BookmarkItem> items = bookmarks.stream()
                    .map(this::createBookmarkItem)
                    .collect(Collectors.toList());

            runLater(() -> {
                allBookmarkItems.clear();
                allBookmarkItems.addAll(items);
                populateCategoryTabPane();
                filterView();
                if (allBookmarkItems.isEmpty()) {
                    bookmarkTable.getTableView().setPlaceholder(new Label("No bookmarks found"));
                } else {
                    bookmarkTable.getTableView().setPlaceholder(null);
                }
            });
        }).start();
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(5);
        setupBookmarkTable();
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
                filterView();
            }
        });
    }

    private void setupSearchTextFieldListener() {
        bookmarkTable.getSearchTextField().textProperty().addListener((observable, oldValue, newValue) -> filterView());
    }

    private void setupManageCategoriesButton() {
        bookmarkTable.getManageCategoriesButton().setOnAction(event -> openCategoryManagementPopup());
    }

    void populateCategoryTabPane() {
        String selectedCategoryId = null;
        Tab selectedTab = categoryTabPane.getSelectionModel().getSelectedItem();
        if (selectedTab != null) {
            BookmarkCategory category = (BookmarkCategory) selectedTab.getUserData();
            if (category != null) {
                selectedCategoryId = category.getId();
            }
        }

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

        // Restore selection
        final String finalSelectedCategoryId = selectedCategoryId;
        categoryTabPane.getTabs().stream()
                .filter(tab -> {
                    BookmarkCategory category = (BookmarkCategory) tab.getUserData();
                    if (category == null || category.getId() == null) {
                        return finalSelectedCategoryId == null;
                    }
                    return category.getId().equals(finalSelectedCategoryId);
                })
                .findFirst()
                .ifPresent(tab -> categoryTabPane.getSelectionModel().select(tab));

        if (categoryTabPane.getSelectionModel().getSelectedItem() == null) {
            categoryTabPane.getSelectionModel().selectFirst();
        }
    }

    private void filterView() {
        Tab selectedTab = categoryTabPane.getSelectionModel().getSelectedItem();
        String categoryId = null;
        if (selectedTab != null) {
            BookmarkCategory category = (BookmarkCategory) selectedTab.getUserData();
            if (category != null) {
                categoryId = category.getId();
            }
        }

        String searchText = bookmarkTable.getSearchTextField().getText().toLowerCase();
        final String finalCategoryId = categoryId;

        List<BookmarkItem> filteredList = allBookmarkItems.stream()
                .filter(item -> {
                    boolean categoryMatch = (finalCategoryId == null) || finalCategoryId.equals(item.getCategoryId());
                    boolean searchMatch = searchText.isEmpty()
                            || item.getChannelName().toLowerCase().contains(searchText)
                            || item.getAccountName().toLowerCase().contains(searchText);
                    return categoryMatch && searchMatch;
                })
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
                accountAction,
                new SimpleStringProperty(bookmark.getCategoryJson()),
                new SimpleStringProperty(bookmark.getChannelJson()),
                new SimpleStringProperty(bookmark.getVodJson()),
                new SimpleStringProperty(bookmark.getSeriesJson())
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
        forceReload();
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
                    play(bookmarkTable.getTableView().getFocusModel().getFocusedItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
                }
            }
        });
        bookmarkTable.getTableView().setRowFactory(tv -> {
            TableRow<BookmarkItem> row = new TableRow<>();

            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY) {
                    if (event.getClickCount() == 2) {
                        play(row.getItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
                    } else if (!event.isControlDown() && !event.isShiftDown()) {
                        TableView.TableViewSelectionModel<BookmarkItem> sm = bookmarkTable.getTableView().getSelectionModel();
                        if (sm.isSelected(row.getIndex())) {
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
                play(row.getItem(), "embedded");
            }
        });

        MenuItem player1Item = new MenuItem("Player 1");
        player1Item.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath1());
            }
        });

        MenuItem player2Item = new MenuItem("Player 2");
        player2Item.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath2());
            }
        });

        MenuItem player3Item = new MenuItem("Player 3");
        player3Item.setOnAction(event -> {
            if (bookmarkTable.getTableView().getSelectionModel().getSelectedItems().size() > 1) {
                showErrorAlert("This action is disabled for multiple selections.");
            } else {
                rowMenu.hide();
                play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath3());
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
                forceReload();
            });
            addToMenu.getItems().add(categoryItem);
        }
        rowMenu.getItems().addAll(editItem, playerEmbeddedItem, player1Item, player2Item, player3Item, addToMenu);
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
                forceReload();
            }
        });
    }

    private void play(BookmarkItem item, String playerPath) {
        runLater(() -> getPlayer().stop());

        getScene().setCursor(Cursor.WAIT);
        new Thread(() -> {
            try {
                Account account = AccountService.getInstance().getAll().get(item.getAccountName());
                account.setServerPortalUrl(item.getServerPortalUrl());
                account.setAction(item.getAccountAction());

                Channel channel = null;
                if (isNotBlank(item.getSeriesJson())) {
                    Episode episode = Episode.fromJson(item.getSeriesJson());
                    if (episode != null) {
                        channel = new Channel();
                        channel.setCmd(episode.getCmd());
                        channel.setName(episode.getTitle());
                        channel.setChannelId(episode.getId());
                        if (episode.getInfo() != null) {
                            channel.setLogo(episode.getInfo().getMovieImage());
                        }
                    }
                } else if (isNotBlank(item.getChannelJson())) {
                    channel = Channel.fromJson(item.getChannelJson());
                } else if (isNotBlank(item.getVodJson())) {
                    channel = Channel.fromJson(item.getVodJson());
                }

                if (channel == null) {
                    channel = new Channel();
                    channel.setCmd(item.getCmd());
                    channel.setChannelId(item.getChannelId());
                    channel.setName(item.getChannelName());
                    channel.setDrmType(item.getDrmType());
                    channel.setDrmLicenseUrl(item.getDrmLicenseUrl());
                    channel.setClearKeysJson(item.getClearKeysJson());
                    channel.setInputstreamaddon(item.getInputstreamaddon());
                    channel.setManifestType(item.getManifestType());
                }

                PlayerResponse response = PlayerService.getInstance().get(account, channel, item.getChannelId());
                response.setFromChannel(channel, account);

                String evaluatedStreamUrl = response.getUrl();

                runLater(() -> {
                    boolean useEmbeddedPlayerConfig = ConfigurationService.getInstance().read().isEmbeddedPlayer();
                    boolean playerPathIsEmbedded = (playerPath != null && playerPath.toLowerCase().contains("embedded"));

                    if (playerPathIsEmbedded) {
                        if (useEmbeddedPlayerConfig) {
                            getPlayer().play(response);
                        } else {
                            showErrorAlert("Embedded player is not enabled in settings. Please enable it or choose an external player.");
                        }
                    } else {
                        if (isBlank(playerPath) && useEmbeddedPlayerConfig) {
                            getPlayer().play(response);
                        } else if (isBlank(playerPath) && !useEmbeddedPlayerConfig) {
                            showErrorAlert("No default player configured and embedded player is not enabled. Please configure a player in settings.");
                        } else {
                            com.uiptv.util.Platform.executeCommand(playerPath, evaluatedStreamUrl);
                        }
                    }
                });
            } catch (Exception e) {
                runLater(() -> showErrorAlert("Error playing bookmark: " + e.getMessage()));
            } finally {
                runLater(() -> getScene().setCursor(Cursor.DEFAULT));
            }
        }).start();
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
        private final SimpleStringProperty categoryJson;
        private final SimpleStringProperty channelJson;
        private final SimpleStringProperty vodJson;
        private final SimpleStringProperty seriesJson;
        private final String drmType;
        private final String drmLicenseUrl;
        private final String clearKeysJson;
        private final String inputstreamaddon;
        private final String manifestType;


        public BookmarkItem(SimpleStringProperty bookmarkId, SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, SimpleStringProperty accountName, SimpleStringProperty categoryTitle, SimpleStringProperty serverPortalUrl, SimpleStringProperty channelAccountName, SimpleStringProperty categoryId, SimpleStringProperty logo, Account.AccountAction accountAction, SimpleStringProperty categoryJson, SimpleStringProperty channelJson, SimpleStringProperty vodJson, SimpleStringProperty seriesJson) {
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
            this.categoryJson = categoryJson;
            this.channelJson = channelJson;
            this.vodJson = vodJson;
            this.seriesJson = seriesJson;
            this.drmType = null;
            this.drmLicenseUrl = null;
            this.clearKeysJson = null;
            this.inputstreamaddon = null;
            this.manifestType = null;
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

        public String getCategoryJson() {
            return categoryJson.get();
        }

        public SimpleStringProperty categoryJsonProperty() {
            return categoryJson;
        }

        public String getChannelJson() {
            return channelJson.get();
        }

        public SimpleStringProperty channelJsonProperty() {
            return channelJson;
        }

        public String getVodJson() {
            return vodJson.get();
        }

        public SimpleStringProperty vodJsonProperty() {
            return vodJson;
        }

        public String getSeriesJson() {
            return seriesJson.get();
        }

        public SimpleStringProperty seriesJsonProperty() {
            return seriesJson;
        }

        public String getDrmType() {
            return drmType;
        }

        public String getDrmLicenseUrl() {
            return drmLicenseUrl;
        }

        public String getClearKeysJson() {
            return clearKeysJson;
        }

        public String getInputstreamaddon() {
            return inputstreamaddon;
        }

        public String getManifestType() {
            return manifestType;
        }
    }
}
