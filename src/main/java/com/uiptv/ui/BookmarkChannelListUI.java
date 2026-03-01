package com.uiptv.ui;

import com.uiptv.model.*;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkChangeListener;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.CategoryService;
import com.uiptv.shared.Episode;
import com.uiptv.util.ImageCacheManager;
import com.uiptv.widget.AsyncImageView;
import com.uiptv.widget.SearchableTableViewWithButton;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.concurrent.atomic.AtomicLong;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class BookmarkChannelListUI extends HBox {
    private static final DataFormat SERIALIZED_MIME_TYPE = new DataFormat("application/x-java-serialized-object");
    private static final int BOOKMARK_BATCH_SIZE = 20;
    private final SearchableTableViewWithButton<BookmarkItem> bookmarkTable = new SearchableTableViewWithButton<>();
    private final TableColumn<BookmarkItem, String> bookmarkColumn = new TableColumn<>("bookmarkColumn");
    private final TabPane categoryTabPane = new TabPane();
    private final ObservableList<BookmarkItem> filteredItems = FXCollections.observableArrayList();
    private boolean isPromptShowing = false;
    private final List<BookmarkItem> allBookmarkItems = new ArrayList<>();
    private volatile long lastKnownBookmarkRevision = 0;
    private volatile boolean reloadInProgress = false;
    private volatile boolean loadedOnce = false;
    private final AtomicLong reloadGeneration = new AtomicLong(0);
    private boolean changeListenerRegistered = false;
    private final BookmarkChangeListener bookmarkChangeListener = (revision, updatedEpochMs) -> runLater(() -> {
        if (!changeListenerRegistered || reloadInProgress) {
            return;
        }
        if (revision != lastKnownBookmarkRevision) {
            forceReload();
        }
    });

    public BookmarkChannelListUI() {
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            ImageCacheManager.clearCache("bookmark");
        }
        initWidgets();
        registerBookmarkChangeListener();
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                releaseTransientState();
            } else if (isVisible()) {
                ensureLoaded();
            }
        });
        visibleProperty().addListener((obs, oldVisible, newVisible) -> {
            if (Boolean.TRUE.equals(newVisible) && getScene() != null) {
                ensureLoaded();
            }
        });
    }

    private void ensureLoaded() {
        if (!loadedOnce && !reloadInProgress) {
            forceReload();
        }
    }

    public void forceReload() {
        loadedOnce = true;
        long generation = reloadGeneration.incrementAndGet();
        reloadInProgress = true;
        runLater(() -> {
            if (generation != reloadGeneration.get()) {
                return;
            }
            allBookmarkItems.clear();
            populateCategoryTabPane();
            filterView();
            bookmarkTable.getTableView().setPlaceholder(new Label("Loading bookmarks..."));
        });
        new Thread(() -> {
            try {
                List<Bookmark> bookmarks = BookmarkService.getInstance().read();
                Map<String, Account> accountByName = AccountService.getInstance().getAll();
                Map<String, String> logoByAccountAndChannel = new HashMap<>();
                List<BookmarkItem> batch = new ArrayList<>(BOOKMARK_BATCH_SIZE);
                int totalCount = bookmarks.size();
                int loadedCount = 0;

                for (Bookmark bookmark : bookmarks) {
                    if (generation != reloadGeneration.get()) {
                        return;
                    }
                    batch.add(createBookmarkItem(bookmark, accountByName, logoByAccountAndChannel));
                    loadedCount++;
                    if (batch.size() >= BOOKMARK_BATCH_SIZE) {
                        List<BookmarkItem> chunk = new ArrayList<>(batch);
                        int finalLoadedCount = loadedCount;
                        runLater(() -> appendLoadedBatch(generation, chunk, finalLoadedCount, totalCount));
                        batch.clear();
                    }
                }

                if (!batch.isEmpty()) {
                    List<BookmarkItem> chunk = new ArrayList<>(batch);
                    int finalLoadedCount = loadedCount;
                    runLater(() -> appendLoadedBatch(generation, chunk, finalLoadedCount, totalCount));
                }

                runLater(() -> finishReload(generation));
            } catch (Exception ignored) {
                runLater(() -> {
                    if (generation != reloadGeneration.get()) {
                        return;
                    }
                    reloadInProgress = false;
                    bookmarkTable.getTableView().setPlaceholder(new Label("Unable to load bookmarks"));
                });
            }
        }).start();
    }

    private void appendLoadedBatch(long generation, List<BookmarkItem> chunk, int loadedCount, int totalCount) {
        if (generation != reloadGeneration.get()) {
            return;
        }
        allBookmarkItems.addAll(chunk);
        filterView();
        if (loadedCount < totalCount) {
            bookmarkTable.getTableView().setPlaceholder(new Label("Loading bookmarks... (" + loadedCount + "/" + totalCount + ")"));
        }
    }

    private void finishReload(long generation) {
        if (generation != reloadGeneration.get()) {
            return;
        }
        if (allBookmarkItems.isEmpty()) {
            bookmarkTable.getTableView().setPlaceholder(new Label("No bookmarks found"));
        } else {
            bookmarkTable.getTableView().setPlaceholder(null);
        }
        lastKnownBookmarkRevision = BookmarkService.getInstance().getChangeRevision();
        reloadInProgress = false;
    }

    private void registerBookmarkChangeListener() {
        if (changeListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
        changeListenerRegistered = true;
        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                unregisterBookmarkChangeListener();
            } else {
                if (!changeListenerRegistered) {
                    BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
                    changeListenerRegistered = true;
                }
            }
        });
    }

    private void unregisterBookmarkChangeListener() {
        if (!changeListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().removeChangeListener(bookmarkChangeListener);
        changeListenerRegistered = false;
    }

    private void releaseTransientState() {
        reloadGeneration.incrementAndGet();
        reloadInProgress = false;
        allBookmarkItems.clear();
        filteredItems.clear();
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
        bookmarkTable.getTableView().setItems(filteredItems);
        bookmarkColumn.setVisible(true);
        bookmarkColumn.setCellValueFactory(cellData -> cellData.getValue().channelAccountNameProperty());
        bookmarkColumn.setSortType(TableColumn.SortType.ASCENDING);
        bookmarkColumn.setText("Bookmarked Channels");

        boolean enableThumbnails = ThumbnailAwareUI.areThumbnailsEnabled();

        if (enableThumbnails) {
            bookmarkColumn.setCellFactory(column -> createThumbnailCell());
        } else {
            bookmarkColumn.setCellFactory(column -> createPlainTextCell());
        }
    }

    private TableCell<BookmarkItem, String> createThumbnailCell() {
        return new TableCell<>() {
            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final Label drmBadge = new Label("DRM");
            private final Pane spacer = new Pane();
            private final AsyncImageView imageView = new AsyncImageView();

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                drmBadge.getStyleClass().add("drm-badge");
                drmBadge.setVisible(false);
                drmBadge.setManaged(false);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(imageView, nameLabel, drmBadge, spacer);
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
                boolean drmProtected = isNotBlank(bookmarkItem.getDrmType())
                        || isNotBlank(bookmarkItem.getDrmLicenseUrl())
                        || isNotBlank(bookmarkItem.getClearKeysJson())
                        || isNotBlank(bookmarkItem.getInputstreamaddon())
                        || isNotBlank(bookmarkItem.getManifestType());
                drmBadge.setVisible(drmProtected);
                drmBadge.setManaged(drmProtected);
                imageView.loadImage(bookmarkItem.getLogo(), "bookmark");
                setGraphic(graphic);
            }
        };
    }

    private TableCell<BookmarkItem, String> createPlainTextCell() {
        return new TableCell<>() {
            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final Label drmBadge = new Label("DRM");
            private final Pane spacer = new Pane();

            {
                HBox.setHgrow(spacer, Priority.ALWAYS);
                drmBadge.getStyleClass().add("drm-badge");
                drmBadge.setVisible(false);
                drmBadge.setManaged(false);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(nameLabel, drmBadge, spacer);
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
                boolean drmProtected = isNotBlank(bookmarkItem.getDrmType())
                        || isNotBlank(bookmarkItem.getDrmLicenseUrl())
                        || isNotBlank(bookmarkItem.getClearKeysJson())
                        || isNotBlank(bookmarkItem.getInputstreamaddon())
                        || isNotBlank(bookmarkItem.getManifestType());
                drmBadge.setVisible(drmProtected);
                drmBadge.setManaged(drmProtected);
                setGraphic(graphic);
            }
        };
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
        bookmarkTable.getManageCategoriesButton().setText("Manage Tabs");
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

        filteredItems.setAll(filteredList);
    }

    private BookmarkItem createBookmarkItem(Bookmark bookmark, Map<String, Account> accountByName, Map<String, String> logoByAccountAndChannel) {
        Account account = accountByName.get(bookmark.getAccountName());
        String logo = "";
        Channel channel = null;

        if (account != null) {
            String key = account.getDbId() + "|" + bookmark.getChannelId();
            if (logoByAccountAndChannel.containsKey(key)) {
                logo = logoByAccountAndChannel.get(key);
            } else {
                channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId());
                logo = channel != null ? channel.getLogo() : "";
                logoByAccountAndChannel.put(key, logo);
            }
            if (channel == null) {
                channel = ChannelService.getInstance().getChannelByChannelIdAndAccount(bookmark.getChannelId(), account.getDbId());
            }
        }

        String drmType = bookmark.getDrmType();
        String drmLicenseUrl = bookmark.getDrmLicenseUrl();
        String clearKeysJson = bookmark.getClearKeysJson();
        String inputstreamaddon = bookmark.getInputstreamaddon();
        String manifestType = bookmark.getManifestType();

        if (channel != null) {
            if (isBlank(drmType)) drmType = channel.getDrmType();
            if (isBlank(drmLicenseUrl)) drmLicenseUrl = channel.getDrmLicenseUrl();
            if (isBlank(clearKeysJson)) clearKeysJson = channel.getClearKeysJson();
            if (isBlank(inputstreamaddon)) inputstreamaddon = channel.getInputstreamaddon();
            if (isBlank(manifestType)) manifestType = channel.getManifestType();
        }

        Channel jsonChannel = null;
        if (isBlank(drmType) && isNotBlank(bookmark.getChannelJson())) {
            jsonChannel = Channel.fromJson(bookmark.getChannelJson());
        } else if (isBlank(drmType) && isNotBlank(bookmark.getVodJson())) {
            jsonChannel = Channel.fromJson(bookmark.getVodJson());
        }
        if (jsonChannel != null) {
            if (isBlank(drmType)) drmType = jsonChannel.getDrmType();
            if (isBlank(drmLicenseUrl)) drmLicenseUrl = jsonChannel.getDrmLicenseUrl();
            if (isBlank(clearKeysJson)) clearKeysJson = jsonChannel.getClearKeysJson();
            if (isBlank(inputstreamaddon)) inputstreamaddon = jsonChannel.getInputstreamaddon();
            if (isBlank(manifestType)) manifestType = jsonChannel.getManifestType();
        }

        Account.AccountAction accountAction = bookmark.getAccountAction() != null
                ? bookmark.getAccountAction()
                : (account != null ? account.getAction() : Account.AccountAction.itv);
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
                drmType,
                drmLicenseUrl,
                clearKeysJson,
                inputstreamaddon,
                manifestType
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
        if (item == null) {
            return;
        }
        PlaybackContext playbackContext;
        try {
            playbackContext = resolvePlaybackContext(item);
        } catch (Exception e) {
            showErrorAlert("Error preparing bookmark: " + e.getMessage());
            return;
        }
        if (playbackContext == null || playbackContext.account == null || playbackContext.channel == null) {
            showErrorAlert("Unable to load account/channel for this bookmark.");
            return;
        }
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(playbackContext.account, playbackContext.channel, playerPath)
                .categoryId(playbackContext.sourceCategoryDbId)
                .channelId(item.getChannelId())
                .errorPrefix("Error playing bookmark: "));
    }

    private PlaybackContext resolvePlaybackContext(BookmarkItem item) {
        Account account = AccountService.getInstance().getByName(item.getAccountName());
        if (account == null) {
            return null;
        }
        if (isNotBlank(item.getServerPortalUrl())) {
            account.setServerPortalUrl(item.getServerPortalUrl());
        }
        account.setAction(item.getAccountAction());
        Bookmark bookmark = BookmarkService.getInstance().getBookmark(item.getBookmarkId());

        Channel channel = null;
        if (bookmark != null && isNotBlank(bookmark.getSeriesJson())) {
            Episode episode = Episode.fromJson(bookmark.getSeriesJson());
            if (episode != null) {
                channel = new Channel();
                channel.setCmd(episode.getCmd());
                channel.setName(episode.getTitle());
                channel.setChannelId(episode.getId());
                if (episode.getInfo() != null) {
                    channel.setLogo(episode.getInfo().getMovieImage());
                }
            }
        } else if (bookmark != null && isNotBlank(bookmark.getChannelJson())) {
            channel = Channel.fromJson(bookmark.getChannelJson());
        } else if (bookmark != null && isNotBlank(bookmark.getVodJson())) {
            channel = Channel.fromJson(bookmark.getVodJson());
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

        Channel latestCachedChannel = findLatestCachedChannel(account, item);
        if (latestCachedChannel != null) {
            mergeLatestChannel(channel, latestCachedChannel);
        }

        String sourceCategoryDbId = resolveSourceCategoryDbId(account, item, bookmark);
        return new PlaybackContext(account, channel, sourceCategoryDbId);
    }

    private Channel findLatestCachedChannel(Account account, BookmarkItem item) {
        if (account == null || item == null) {
            return null;
        }
        Bookmark bookmark = BookmarkService.getInstance().getBookmark(item.getBookmarkId());
        if (bookmark != null && isNotBlank(bookmark.getSeriesJson())) {
            return null;
        }
        String sourceCategoryDbId = resolveSourceCategoryDbId(account, item, bookmark);
        return switch (account.getAction()) {
            case itv -> ChannelService.getInstance().findCachedLiveChannel(account, item.getChannelId(), item.getChannelName());
            case vod -> ChannelService.getInstance().findCachedVodChannel(account, sourceCategoryDbId, item.getChannelId(), item.getChannelName());
            case series -> ChannelService.getInstance().findCachedSeriesChannel(account, sourceCategoryDbId, item.getChannelId(), item.getChannelName());
        };
    }

    private String resolveSourceCategoryDbId(Account account, BookmarkItem item, Bookmark bookmark) {
        if (item == null || account == null) {
            return "";
        }
        if (bookmark != null && isNotBlank(bookmark.getCategoryJson())) {
            Category category = Category.fromJson(bookmark.getCategoryJson());
            if (category != null && isNotBlank(category.getCategoryId())) {
                return category.getCategoryId();
            }
            if (category != null && isNotBlank(category.getTitle())) {
                String resolvedByJsonTitle = resolveCategoryDbIdByTitle(account, category.getTitle());
                if (isNotBlank(resolvedByJsonTitle)) {
                    return resolvedByJsonTitle;
                }
            }
        }
        if (isNotBlank(item.getCategoryId())) {
            return item.getCategoryId();
        }
        return resolveCategoryDbIdByTitle(account, item.getCategoryTitle());
    }

    private String resolveCategoryDbIdByTitle(Account account, String categoryTitle) {
        if (account == null || isBlank(categoryTitle)) {
            return "";
        }
        List<Category> categories = CategoryService.getInstance().getCached(account);
        if (categories == null || categories.isEmpty()) {
            return "";
        }
        Category matched = categories.stream()
                .filter(c -> c != null && isNotBlank(c.getTitle()) && categoryTitle.trim().equalsIgnoreCase(c.getTitle().trim()))
                .findFirst()
                .orElse(null);
        return matched == null || isBlank(matched.getDbId()) ? "" : matched.getDbId();
    }

    private void mergeLatestChannel(Channel target, Channel latest) {
        if (target == null || latest == null) {
            return;
        }
        if (isNotBlank(latest.getCmd())) target.setCmd(latest.getCmd());
        if (isNotBlank(latest.getName())) target.setName(latest.getName());
        if (isNotBlank(latest.getChannelId())) target.setChannelId(latest.getChannelId());
        if (isNotBlank(latest.getLogo())) target.setLogo(latest.getLogo());
        if (isNotBlank(latest.getDrmType())) target.setDrmType(latest.getDrmType());
        if (isNotBlank(latest.getDrmLicenseUrl())) target.setDrmLicenseUrl(latest.getDrmLicenseUrl());
        if (isNotBlank(latest.getClearKeysJson())) target.setClearKeysJson(latest.getClearKeysJson());
        if (isNotBlank(latest.getInputstreamaddon())) target.setInputstreamaddon(latest.getInputstreamaddon());
        if (isNotBlank(latest.getManifestType())) target.setManifestType(latest.getManifestType());
    }

    private static final class PlaybackContext {
        private final Account account;
        private final Channel channel;
        private final String sourceCategoryDbId;

        private PlaybackContext(Account account, Channel channel, String sourceCategoryDbId) {
            this.account = account;
            this.channel = channel;
            this.sourceCategoryDbId = sourceCategoryDbId;
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
        private final String drmType;
        private final String drmLicenseUrl;
        private final String clearKeysJson;
        private final String inputstreamaddon;
        private final String manifestType;


        public BookmarkItem(SimpleStringProperty bookmarkId, SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, SimpleStringProperty accountName, SimpleStringProperty categoryTitle, SimpleStringProperty serverPortalUrl, SimpleStringProperty channelAccountName, SimpleStringProperty categoryId, SimpleStringProperty logo, Account.AccountAction accountAction, String drmType, String drmLicenseUrl, String clearKeysJson, String inputstreamaddon, String manifestType) {
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
            this.drmType = drmType;
            this.drmLicenseUrl = drmLicenseUrl;
            this.clearKeysJson = clearKeysJson;
            this.inputstreamaddon = inputstreamaddon;
            this.manifestType = manifestType;
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
