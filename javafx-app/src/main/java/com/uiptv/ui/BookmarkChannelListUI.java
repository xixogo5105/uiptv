package com.uiptv.ui;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.shared.Episode;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.widget.AppHeaderActions;
import com.uiptv.widget.AppPageHeader;
import com.uiptv.widget.BookmarkCard;
import com.uiptv.widget.LoadingStateView;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.PlayMenuButton;
import com.uiptv.widget.ResponsiveCardGrid;
import com.uiptv.widget.UiRenderQuality;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.scene.shape.SVGPath;

import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;
import static javafx.application.Platform.runLater;

public class BookmarkChannelListUI extends HBox implements SearchTarget {
    private static final String BOOKMARK_CACHE = "bookmark";
    private static final double GRID_NORMAL_VERTICAL_GAP = 14;
    private static final double GRID_PLAIN_TEXT_VERTICAL_GAP = 6;
    private static final double GRID_NORMAL_CARD_MIN_HEIGHT = 76;
    private static final double GRID_PLAIN_TEXT_CARD_MIN_HEIGHT = 46;
    private static final int BOOKMARK_STREAM_BATCH_SIZE = 25;
    private static final double FILTER_TOOLBAR_GAP = 8;
    private static final String ICON_SORT = "M3 18H9V16H3V18ZM3 6V8H21V6H3ZM3 13H15V11H3V13Z";
    private static final Comparator<BookmarkItem> BOOKMARK_NAME_COMPARATOR =
            Comparator.comparing(BookmarkItem::getChannelName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(BookmarkItem::getChannelName, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(BookmarkItem::getAccountName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                    .thenComparing(BookmarkItem::getAccountName, Comparator.nullsLast(Comparator.naturalOrder()));
    private final TextField searchTextField = new TextField();
    private final ResponsiveCardGrid<BookmarkItem> bookmarkGrid = new ResponsiveCardGrid<>(this::createBookmarkCard);
    private final StackPane bookmarkGridFrame = new StackPane();
    private final LoadingStateView bookmarkLoadingOverlay = new LoadingStateView(I18n.tr("autoLoadingBookmarks"));
    private final PillBar<BookmarkCategory> categoryPillBar =
            new PillBar<>(BookmarkCategory::getName, BookmarkCategory::getId);
    private final VBox listPanel = new VBox(8);
    private final ObservableList<BookmarkItem> filteredItems = FXCollections.observableArrayList();
    private final List<BookmarkItem> allBookmarkItems = new ArrayList<>();
    private final AtomicLong reloadGeneration = new AtomicLong(0);
    private final AtomicLong bookmarkOrderSaveGeneration = new AtomicLong(0);
    private final ExecutorService bookmarkOrderSaveExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "bookmark-order-save");
        thread.setDaemon(true);
        return thread;
    });
    private final BookmarkResolver bookmarkResolver = new BookmarkResolver();
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = this::onThumbnailModeChanged;
    private final HostServices hostServices;
    private final Runnable themeToggleHandler;
    private boolean isPromptShowing = false;
    private boolean thumbnailsEnabled = ThumbnailAwareUI.areThumbnailsEnabled();
    private volatile long lastKnownBookmarkRevision = 0;
    private volatile boolean reloadInProgress = false;
    private volatile boolean loadedOnce = false;
    private volatile boolean reloadRequestedWhileReloading = false;
    private boolean changeListenerRegistered = false;
    private boolean accountChangeListenerRegistered = false;
    private boolean thumbnailListenerRegistered = false;
    private BookmarkSortMode bookmarkSortMode = BookmarkSortMode.DEFAULT;
    private MenuButton bookmarkSortButton;
    private volatile boolean suppressAutoReloadOnBookmarkChange = false;
    private final BookmarkChangeListener bookmarkChangeListener = (revision, updatedEpochMs) -> runLater(() -> {
        if (!changeListenerRegistered || suppressAutoReloadOnBookmarkChange) {
            return;
        }
        if (reloadInProgress) {
            if (revision != lastKnownBookmarkRevision) {
                reloadRequestedWhileReloading = true;
            }
            return;
        }
        if (revision != lastKnownBookmarkRevision) {
            forceReload();
        }
    });
    private final AccountChangeListener accountChangeListener = revision -> runLater(() -> {
        if (!accountChangeListenerRegistered) {
            return;
        }
        requestExternalReload();
    });

    public BookmarkChannelListUI(HostServices hostServices, Runnable themeToggleHandler) {
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        initWidgets();
        registerBookmarkChangeListener();
        registerThumbnailModeListener();
        sceneProperty().addListener((_, _, newScene) -> {
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
        reloadRequestedWhileReloading = false;
        long generation = reloadGeneration.incrementAndGet();
        reloadInProgress = true;
        showLoadingState(generation);
        startReloadThread(generation);
    }

    private void showLoadingState(long generation) {
        Runnable update = () -> {
            if (generation != reloadGeneration.get()) {
                return;
            }
            bookmarkGrid.setPlaceholderNode(new LoadingStateView(I18n.tr("autoLoadingBookmarks")));
            setBookmarkLoadingOverlayVisible(!filteredItems.isEmpty());
        };
        if (Platform.isFxApplicationThread()) {
            update.run();
        } else {
            runLater(update);
        }
    }

    private void startReloadThread(long generation) {
        new Thread(() -> reloadBookmarks(generation)).start();
    }

    private void reloadBookmarks(long generation) {
        try {
            long revisionBeforeRead = BookmarkService.getInstance().getChangeRevision();
            List<Bookmark> bookmarks = BookmarkService.getInstance().read();
            BookmarkResolver.ResolutionContext context = bookmarkResolver.prepare(bookmarks);
            List<BookmarkItem> loadedItems = buildLoadedBookmarkItems(generation, bookmarks, context);
            if (generation != reloadGeneration.get()) {
                return;
            }

            List<BookmarkCategory> categories = new ArrayList<>();
            categories.add(new BookmarkCategory(null, I18n.tr("commonAll")));
            categories.addAll(BookmarkService.getInstance().getAllCategories());
            long revisionAfterRead = BookmarkService.getInstance().getChangeRevision();
            if (revisionAfterRead != revisionBeforeRead) {
                reloadRequestedWhileReloading = true;
            }

            runLater(() -> applyReloadResult(generation, loadedItems, categories, revisionAfterRead));
        } catch (Exception _) {
            // Keep the bookmark pane usable and show the existing placeholder on reload failure.
            runLater(() -> handleReloadFailure(generation));
        }
    }

    private List<BookmarkItem> buildLoadedBookmarkItems(long generation,
                                                        List<Bookmark> bookmarks,
                                                        BookmarkResolver.ResolutionContext context) {
        List<BookmarkItem> loadedItems = new ArrayList<>(bookmarks.size());
        for (Bookmark bookmark : bookmarks) {
            if (generation != reloadGeneration.get()) {
                return List.of();
            }
            loadedItems.add(createBookmarkItem(bookmarkResolver.resolveBookmark(bookmark, context)));
            maybeStreamPartialReload(generation, loadedItems);
        }
        return loadedItems;
    }

    private void maybeStreamPartialReload(long generation, List<BookmarkItem> loadedItems) {
        if (loadedItems.size() % BOOKMARK_STREAM_BATCH_SIZE != 0) {
            return;
        }
        List<BookmarkItem> snapshot = new ArrayList<>(loadedItems);
        runLater(() -> applyPartialReload(generation, snapshot));
    }

    private void handleReloadFailure(long generation) {
        if (generation != reloadGeneration.get()) {
            return;
        }
        reloadInProgress = false;
        setBookmarkLoadingOverlayVisible(false);
        bookmarkGrid.setPlaceholderText(I18n.tr("autoUnableToLoadBookmarks"));
        triggerDeferredReloadIfNeeded();
    }

    private void applyReloadResult(long generation, List<BookmarkItem> loadedItems, List<BookmarkCategory> categories, long revision) {
        if (generation != reloadGeneration.get()) {
            return;
        }
        List<String> selectedBookmarkIds = bookmarkGrid.getSelectedItems().stream()
                .map(BookmarkItem::getBookmarkId)
                .toList();
        populateCategoryPills(categories);
        if (!sameBookmarkItems(loadedItems)) {
            allBookmarkItems.clear();
            allBookmarkItems.addAll(loadedItems);
        }
        filterView();
        restoreGridSelection(selectedBookmarkIds);
        if (allBookmarkItems.isEmpty()) {
            bookmarkGrid.setPlaceholderText(I18n.tr("autoNoBookmarksFound"));
        }
        lastKnownBookmarkRevision = revision;
        reloadInProgress = false;
        setBookmarkLoadingOverlayVisible(false);
        triggerDeferredReloadIfNeeded();
    }

    private void applyPartialReload(long generation, List<BookmarkItem> partialItems) {
        if (generation != reloadGeneration.get() || partialItems == null) {
            return;
        }
        if (partialItems.isEmpty()) {
            return;
        }
        if (allBookmarkItems.size() >= partialItems.size()) {
            return;
        }
        allBookmarkItems.clear();
        allBookmarkItems.addAll(partialItems);
        filterView();
        setBookmarkLoadingOverlayVisible(reloadInProgress && !filteredItems.isEmpty());
    }

    private void restoreGridSelection(List<String> selectedBookmarkIds) {
        if (selectedBookmarkIds == null || selectedBookmarkIds.isEmpty()) {
            return;
        }
        List<BookmarkItem> restored = filteredItems.stream()
                .filter(item -> selectedBookmarkIds.contains(item.getBookmarkId()))
                .toList();
        bookmarkGrid.selectItems(restored);
    }

    private void registerBookmarkChangeListener() {
        if (!changeListenerRegistered) {
            BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
            changeListenerRegistered = true;
        }
        registerAccountChangeListenerIfNeeded();
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterBookmarkChangeListener();
            } else {
                if (!changeListenerRegistered) {
                    BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
                    changeListenerRegistered = true;
                }
                registerAccountChangeListenerIfNeeded();
            }
        });
    }

    private void registerAccountChangeListenerIfNeeded() {
        if (accountChangeListenerRegistered) {
            return;
        }
        AccountService.getInstance().addChangeListener(accountChangeListener);
        accountChangeListenerRegistered = true;
    }

    private void unregisterBookmarkChangeListener() {
        if (changeListenerRegistered) {
            BookmarkService.getInstance().removeChangeListener(bookmarkChangeListener);
            changeListenerRegistered = false;
        }
        if (accountChangeListenerRegistered) {
            AccountService.getInstance().removeChangeListener(accountChangeListener);
            accountChangeListenerRegistered = false;
        }
    }

    private void requestExternalReload() {
        if (reloadInProgress) {
            reloadRequestedWhileReloading = true;
            return;
        }
        forceReload();
    }

    private void releaseTransientState() {
        reloadGeneration.incrementAndGet();
        reloadInProgress = false;
        loadedOnce = false;
        reloadRequestedWhileReloading = false;
        allBookmarkItems.clear();
        filteredItems.clear();
        setBookmarkLoadingOverlayVisible(false);
    }

    private void triggerDeferredReloadIfNeeded() {
        if (!reloadRequestedWhileReloading || suppressAutoReloadOnBookmarkChange) {
            return;
        }
        reloadRequestedWhileReloading = false;
        forceReload();
    }

    private void initWidgets() {
        getStyleClass().add("bookmarks-page-root");
        UiRenderQuality.optimizeLayout(this);
        UiRenderQuality.optimizeTextNode(searchTextField);
        setPadding(Insets.EMPTY);
        setSpacing(0);
        setFillHeight(true);
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setupBookmarkGrid();
        setupCategoryPillListener();
        setupSearchTextFieldListener();

        VBox page = new VBox(12);
        UiRenderQuality.optimizeLayout(page);
        page.getStyleClass().add("bookmarks-page");
        page.setFillWidth(true);
        page.setMinSize(0, 0);
        page.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        ScrollPane pageScroll = new ScrollPane(bookmarkGridFrame);
        UiRenderQuality.optimizeLayout(pageScroll);
        pageScroll.getStyleClass().addAll("bookmarks-page-scroll", "transparent-scroll-pane");
        pageScroll.setFitToWidth(true);
        pageScroll.setPannable(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pageScroll.setFocusTraversable(false);
        pageScroll.setMinSize(0, 0);
        pageScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(pageScroll, Priority.ALWAYS);

        configureListPanel(pageScroll);
        page.getChildren().setAll(createHeaderArea(), listPanel);

        getChildren().setAll(page);
        HBox.setHgrow(page, Priority.ALWAYS);
        addChannelClickHandler();
    }

    private VBox createHeaderArea() {
        TextField searchField = searchTextField;
        searchField.setPromptText(I18n.tr("commonSearch"));

        HBox headerActions = new HBox(6, new AppHeaderActions(hostServices, themeToggleHandler, null));
        headerActions.setAlignment(Pos.CENTER_RIGHT);

        AppPageHeader header = new AppPageHeader(I18n.tr("autoFavorite"), searchField, headerActions);
        header.getStyleClass().add("bookmarks-header-stack");

        VBox headerArea = new VBox(12, header);
        headerArea.getStyleClass().add("bookmarks-header-area");
        return headerArea;
    }

    private void configureListPanel(ScrollPane pageScroll) {
        listPanel.getStyleClass().add("bookmark-list-panel");
        listPanel.setFillWidth(true);
        listPanel.setMinSize(0, 0);
        listPanel.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(listPanel, Priority.ALWAYS);
        listPanel.getChildren().setAll(createCategoryRow(), pageScroll);
    }

    private VBox createCategoryRow() {
        VBox row = new VBox(FILTER_TOOLBAR_GAP);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillWidth(true);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("bookmark-category-row");

        categoryPillBar.setNarrowItemsPerRow(5);
        categoryPillBar.setMaxWidth(Double.MAX_VALUE);
        HBox actions = createBookmarkToolbarActions();
        HBox inlineRow = createInlineFilterToolbarRow(categoryPillBar, actions);
        row.getChildren().setAll(categoryPillBar, actions);
        row.widthProperty().addListener((_, _, _) -> applyResponsiveFilterToolbarLayout(row, inlineRow, categoryPillBar, actions));
        Platform.runLater(() -> applyResponsiveFilterToolbarLayout(row, inlineRow, categoryPillBar, actions));
        return row;
    }

    private HBox createInlineFilterToolbarRow(PillBar<?> pillBar, HBox actions) {
        HBox row = new HBox(FILTER_TOOLBAR_GAP, pillBar, actions);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setFillHeight(false);
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(pillBar, Priority.ALWAYS);
        return row;
    }

    private void applyResponsiveFilterToolbarLayout(VBox row, HBox inlineRow, PillBar<?> pillBar, HBox actions) {
        boolean useInline = shouldUseInlineFilterToolbar(row.getWidth(), actions);
        boolean inlineApplied = row.getChildren().size() == 1 && row.getChildren().getFirst() == inlineRow;
        if (useInline == inlineApplied) {
            return;
        }
        if (useInline) {
            pillBar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(pillBar, Priority.ALWAYS);
            row.getChildren().clear();
            inlineRow.getChildren().setAll(pillBar, actions);
            row.getChildren().setAll(inlineRow);
        } else {
            pillBar.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(pillBar, Priority.ALWAYS);
            inlineRow.getChildren().clear();
            row.getChildren().setAll(pillBar, actions);
        }
    }

    private boolean shouldUseInlineFilterToolbar(double width, HBox actions) {
        if (width <= 0) {
            return false;
        }
        return width >= actions.prefWidth(-1) + FILTER_TOOLBAR_GAP + PillBar.COMPACT_DROPDOWN_PREF_WIDTH;
    }

    private HBox createBookmarkToolbarActions() {
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox actions = new HBox(8, spacer, createBookmarkSortButton(), createManageTabsToolbarButton());
        actions.getStyleClass().add("list-toolbar-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setFillHeight(false);
        actions.setMinWidth(Region.USE_PREF_SIZE);
        actions.setMaxWidth(Double.MAX_VALUE);
        return actions;
    }

    private void setupBookmarkGrid() {
        bookmarkGrid.getStyleClass().add("bookmark-card-grid");
        bookmarkGrid.setItems(filteredItems);
        bookmarkGrid.setCardWidthRange(255, 345);
        bookmarkGrid.setGaps(16, 14);
        bookmarkGrid.setReorderEnabled(true);
        bookmarkGrid.setPlaceholderNode(new LoadingStateView(I18n.tr("autoLoadingBookmarks")));
        applyThumbnailMode(ThumbnailAwareUI.areThumbnailsEnabled());

        bookmarkGridFrame.getStyleClass().add("bookmark-grid-frame");
        UiRenderQuality.optimizeLayout(bookmarkGridFrame);
        bookmarkGridFrame.setMinSize(0, 0);
        bookmarkGridFrame.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        bookmarkLoadingOverlay.getStyleClass().add("bookmark-loading-overlay");
        bookmarkLoadingOverlay.setMouseTransparent(true);
        setBookmarkLoadingOverlayVisible(false);
        StackPane.setAlignment(bookmarkLoadingOverlay, Pos.TOP_CENTER);
        StackPane.setMargin(bookmarkLoadingOverlay, new Insets(10, 0, 0, 0));
        bookmarkGridFrame.getChildren().setAll(bookmarkGrid, bookmarkLoadingOverlay);
    }

    private MenuButton createBookmarkSortButton() {
        MenuButton button = new MenuButton();
        button.getStyleClass().add("list-toolbar-sort-menu");
        button.setGraphic(createSortDropdownIcon());
        button.setContentDisplay(ContentDisplay.LEFT);
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        ToggleGroup group = new ToggleGroup();
        button.getItems().setAll(
                createBookmarkSortMenuItem(I18n.tr("autoSortDefault"), BookmarkSortMode.DEFAULT, group),
                createBookmarkSortMenuItem(I18n.tr("autoSortNameAscending"), BookmarkSortMode.ASCENDING, group),
                createBookmarkSortMenuItem(I18n.tr("autoSortNameDescending"), BookmarkSortMode.DESCENDING, group)
        );
        bookmarkSortButton = button;
        updateBookmarkSortButton();
        return button;
    }

    private Button createManageTabsToolbarButton() {
        Button button = new Button(I18n.tr("searchableTableManageTabs"));
        button.getStyleClass().add("list-toolbar-action-button");
        button.setFocusTraversable(false);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setAccessibleText(I18n.tr("searchableTableManageTabs"));
        button.setTooltip(new Tooltip(I18n.tr("searchableTableManageTabs")));
        button.setOnAction(_ -> openCategoryManagementPopup());
        return button;
    }

    private RadioMenuItem createBookmarkSortMenuItem(String label, BookmarkSortMode sortMode, ToggleGroup group) {
        RadioMenuItem item = new RadioMenuItem(label);
        item.setUserData(sortMode);
        item.setToggleGroup(group);
        item.setSelected(bookmarkSortMode == sortMode);
        item.setOnAction(_ -> setBookmarkSortMode(sortMode));
        return item;
    }

    private void setBookmarkSortMode(BookmarkSortMode sortMode) {
        bookmarkSortMode = sortMode == null ? BookmarkSortMode.DEFAULT : sortMode;
        bookmarkGrid.setReorderEnabled(bookmarkSortMode == BookmarkSortMode.DEFAULT);
        filterView();
        updateBookmarkSortButton();
    }

    private void updateBookmarkSortButton() {
        if (bookmarkSortButton == null) {
            return;
        }
        bookmarkSortButton.setText(bookmarkSortCompactLabel(bookmarkSortMode));
        bookmarkSortButton.setAccessibleText(bookmarkSortTooltip());
        bookmarkSortButton.setTooltip(new Tooltip(bookmarkSortTooltip()));
        syncBookmarkSortMenuItems();
        updateStyleClass(bookmarkSortButton, "list-toolbar-sort-menu-active", bookmarkSortMode != BookmarkSortMode.DEFAULT);
    }

    private static Node createSortDropdownIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent(ICON_SORT);
        icon.getStyleClass().add("list-toolbar-sort-icon");
        return icon;
    }

    private void syncBookmarkSortMenuItems() {
        for (MenuItem item : bookmarkSortButton.getItems()) {
            if (item instanceof RadioMenuItem radioMenuItem) {
                radioMenuItem.setSelected(Objects.equals(item.getUserData(), bookmarkSortMode));
            }
        }
    }

    private static void updateStyleClass(Node node, String styleClass, boolean enabled) {
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
        } else {
            node.getStyleClass().remove(styleClass);
        }
    }

    private String bookmarkSortTooltip() {
        return I18n.tr("autoSort") + ": " + bookmarkSortLabel(bookmarkSortMode);
    }

    private String bookmarkSortLabel(BookmarkSortMode sortMode) {
        return switch (sortMode == null ? BookmarkSortMode.DEFAULT : sortMode) {
            case DEFAULT -> I18n.tr("autoSortDefault");
            case ASCENDING -> I18n.tr("autoSortNameAscending");
            case DESCENDING -> I18n.tr("autoSortNameDescending");
        };
    }

    private String bookmarkSortCompactLabel(BookmarkSortMode sortMode) {
        return switch (sortMode == null ? BookmarkSortMode.DEFAULT : sortMode) {
            case DEFAULT -> "Default";
            case ASCENDING -> "A-Z";
            case DESCENDING -> "Z-A";
        };
    }

    private void setBookmarkLoadingOverlayVisible(boolean visible) {
        bookmarkLoadingOverlay.setVisible(visible);
        bookmarkLoadingOverlay.setManaged(visible);
        if (visible) {
            bookmarkLoadingOverlay.toFront();
        }
    }

    private Region createBookmarkCard(BookmarkItem item) {
        if (!thumbnailsEnabled) {
            return createPlainTextBookmarkCard(item);
        }

        Button playButton = new PlayMenuButton(I18n.tr("autoPlay2"));
        playButton.getStyleClass().add("bookmark-play-menu-button");
        playButton.setOnAction(event -> {
            event.consume();
            bookmarkGrid.selectItems(List.of(item));
            ContextMenu menu = createBookmarkContextMenu(item, List.of(item), playButton);
            UiI18n.preparePopupControl(menu, playButton);
            menu.show(playButton, Side.BOTTOM, 0, 0);
        });
        return new BookmarkCard(
                item == null ? "" : item.getChannelName(),
                bookmarkAccountName(item),
                item == null ? "" : item.getLogo(),
                thumbnailsEnabled,
                BOOKMARK_CACHE,
                isDrmProtected(item),
                playButton
        );
    }

    private Region createPlainTextBookmarkCard(BookmarkItem item) {
        VBox card = new VBox(1);
        card.getStyleClass().addAll("bookmark-card", "plain-text-row-card", "bookmark-plain-text-row-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(item == null || item.getChannelName() == null ? "" : item.getChannelName());
        title.getStyleClass().add("bookmark-channel-title");
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);

        HBox titleRow = new HBox(6);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);
        titleRow.getChildren().add(title);
        if (isDrmProtected(item)) {
            titleRow.getChildren().add(createPlainTextDrmBadge());
        }

        card.getChildren().add(titleRow);
        String accountName = bookmarkAccountName(item);
        if (!accountName.isBlank()) {
            Label account = new Label(accountName);
            account.getStyleClass().add("bookmark-channel-account");
            account.setWrapText(true);
            account.setMinWidth(0);
            account.setMaxWidth(Double.MAX_VALUE);
            card.getChildren().add(account);
        }
        return card;
    }

    private Label createPlainTextDrmBadge() {
        Label badge = new Label(I18n.tr("autoDrm"));
        badge.getStyleClass().add("drm-badge");
        badge.setMinWidth(Region.USE_PREF_SIZE);
        badge.setMaxWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    private String bookmarkAccountName(BookmarkItem item) {
        if (item == null) {
            return "";
        }
        String accountName = item.getAccountName();
        return isBlank(accountName) ? "" : accountName;
    }

    private boolean isDrmProtected(BookmarkItem item) {
        return item != null && (isNotBlank(item.getDrmType())
                || isNotBlank(item.getDrmLicenseUrl())
                || isNotBlank(item.getClearKeysJson())
                || isNotBlank(item.getInputstreamaddon())
                || isNotBlank(item.getManifestType()));
    }

    private void registerThumbnailModeListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterThumbnailModeListener();
            } else {
                registerThumbnailModeListenerIfNeeded();
                applyThumbnailMode(ThumbnailAwareUI.areThumbnailsEnabled());
            }
        });
        if (getScene() != null) {
            registerThumbnailModeListenerIfNeeded();
        }
    }

    private void registerThumbnailModeListenerIfNeeded() {
        if (thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = true;
    }

    private void unregisterThumbnailModeListener() {
        if (!thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.removeThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = false;
    }

    private void onThumbnailModeChanged(boolean enabled) {
        runLater(() -> applyThumbnailMode(enabled));
    }

    private void applyThumbnailMode(boolean enabled) {
        thumbnailsEnabled = enabled;
        applyBookmarkGridDisplayMode(enabled);
        bookmarkGrid.refresh();
    }

    private void applyBookmarkGridDisplayMode(boolean thumbnailsEnabled) {
        bookmarkGrid.setSingleColumn(!thumbnailsEnabled);
        bookmarkGrid.setCardMinHeight(thumbnailsEnabled
                ? GRID_NORMAL_CARD_MIN_HEIGHT
                : GRID_PLAIN_TEXT_CARD_MIN_HEIGHT);
        bookmarkGrid.setGaps(16, thumbnailsEnabled
                ? GRID_NORMAL_VERTICAL_GAP
                : GRID_PLAIN_TEXT_VERTICAL_GAP);
    }

    private void setupCategoryPillListener() {
        categoryPillBar.selectedItemProperty().addListener((_, _, _) -> filterView());
    }

    private void setupSearchTextFieldListener() {
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> filterView());
    }

    @Override
    public void setSearchQuery(String query) {
        String value = query == null ? "" : query;
        if (!Objects.equals(searchTextField.getText(), value)) {
            searchTextField.setText(value);
        }
    }

    void populateCategoryTabPane() {
        List<BookmarkCategory> categories = new ArrayList<>();
        categories.add(new BookmarkCategory(null, I18n.tr("commonAll")));
        categories.addAll(BookmarkService.getInstance().getAllCategories());
        populateCategoryPills(categories);
    }

    private void populateCategoryPills(List<BookmarkCategory> categories) {
        categoryPillBar.setItems(categories);
    }

    private void filterView() {
        String categoryId = selectedCategoryId();

        String rawSearchText = searchTextField.getText();
        String searchText = rawSearchText == null ? "" : rawSearchText.toLowerCase();
        final String finalCategoryId = categoryId;

        List<BookmarkItem> filteredList = allBookmarkItems.stream()
                .filter(item -> {
                    boolean categoryMatch = (finalCategoryId == null) || finalCategoryId.equals(item.getCategoryId());
                    boolean searchMatch = searchText.isEmpty()
                            || item.getChannelName().toLowerCase().contains(searchText)
                            || item.getAccountName().toLowerCase().contains(searchText);
                    return categoryMatch && searchMatch;
                })
                .collect(Collectors.toCollection(ArrayList::new));
        applyBookmarkSort(filteredList);

        if (!sameFilteredItems(filteredList)) {
            filteredItems.setAll(filteredList);
        }
        if (filteredList.isEmpty()) {
            if (reloadInProgress && allBookmarkItems.isEmpty()) {
                bookmarkGrid.setPlaceholderNode(new LoadingStateView(I18n.tr("autoLoadingBookmarks")));
            } else {
                bookmarkGrid.setPlaceholderText(searchText.isBlank()
                        ? I18n.tr("autoNoBookmarksFound")
                        : I18n.tr("autoNothingFoundFor", rawSearchText));
            }
        } else {
            bookmarkGrid.setPlaceholderText("");
        }
    }

    private boolean sameFilteredItems(List<BookmarkItem> filteredList) {
        if (filteredItems.size() != filteredList.size()) {
            return false;
        }
        for (int i = 0; i < filteredList.size(); i++) {
            String a = filteredItems.get(i).getBookmarkId();
            String b = filteredList.get(i).getBookmarkId();
            if (!Objects.equals(a, b)) {
                return false;
            }
        }
        return true;
    }

    private void applyBookmarkSort(List<BookmarkItem> items) {
        if (items == null || items.size() < 2) {
            return;
        }
        switch (bookmarkSortMode) {
            case ASCENDING -> items.sort(BOOKMARK_NAME_COMPARATOR);
            case DESCENDING -> items.sort(BOOKMARK_NAME_COMPARATOR.reversed());
            case DEFAULT -> {
                // Default order is the persisted order already represented by allBookmarkItems.
            }
        }
    }

    private boolean sameBookmarkItems(List<BookmarkItem> loadedItems) {
        if (loadedItems == null) {
            return allBookmarkItems.isEmpty();
        }
        if (allBookmarkItems.size() != loadedItems.size()) {
            return false;
        }
        for (int i = 0; i < loadedItems.size(); i++) {
            BookmarkItem current = allBookmarkItems.get(i);
            BookmarkItem incoming = loadedItems.get(i);
            if (!sameBookmarkItem(current, incoming)) {
                return false;
            }
        }
        return true;
    }

    private boolean sameBookmarkItem(BookmarkItem left, BookmarkItem right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return Objects.equals(left.getBookmarkId(), right.getBookmarkId())
                && Objects.equals(left.getChannelName(), right.getChannelName())
                && Objects.equals(left.getCategoryId(), right.getCategoryId())
                && Objects.equals(left.getAccountName(), right.getAccountName())
                && Objects.equals(left.getLogo(), right.getLogo());
    }

    private BookmarkItem createBookmarkItem(BookmarkResolver.ResolvedBookmark resolved) {
        Bookmark bookmark = resolved.getBookmark();
        Account.AccountAction accountAction = resolved.getAccountAction();
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
                new SimpleStringProperty(resolved.getLogo()),
                accountAction,
                resolved.getDrmType(),
                resolved.getDrmLicenseUrl(),
                resolved.getClearKeysJson(),
                resolved.getInputstreamaddon(),
                resolved.getManifestType()
        );
    }

    private void openCategoryManagementPopup() {
        CategoryManagementPopup.showPopup(RootApplication.getPrimaryStage(), this, this::forceReload);
    }

    private void addChannelClickHandler() {
        bookmarkGrid.setOnKeyReleased(this::handleBookmarkGridKeyReleased);
        bookmarkGrid.setOnItemActivated(item -> play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath()));
        bookmarkGrid.setContextMenuFactory(this::createBookmarkContextMenu);
        bookmarkGrid.setOnItemsReordered(_ -> applyDraggedBookmarkOrder());
    }

    private void handleBookmarkGridKeyReleased(KeyEvent event) {
        if (event.getCode() == KeyCode.DELETE) {
            handleDeleteMultipleBookmarks();
            return;
        }
        if (event.getCode() == KeyCode.ENTER) {
            if (isPromptShowing) {
                event.consume();
                isPromptShowing = false;
                return;
            }
            play(bookmarkGrid.getFocusedItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
        }
    }

    private void applyDraggedBookmarkOrder() {
        if (bookmarkSortMode != BookmarkSortMode.DEFAULT) {
            return;
        }
        List<String> orderedDbIds = bookmarkGrid.getItems().stream()
                .map(BookmarkItem::getBookmarkId)
                .toList();
        applyLocalBookmarkOrder(selectedCategoryId(), orderedDbIds);
        persistBookmarkOrderAsync(buildPersistedBookmarkOrders());
    }

    private String selectedCategoryId() {
        BookmarkCategory category = categoryPillBar.getSelectedItem();
        return category != null ? category.getId() : null;
    }


    private void persistBookmarkOrderAsync(Map<String, Integer> bookmarkOrders) {
        final long saveGeneration = bookmarkOrderSaveGeneration.incrementAndGet();
        final Map<String, Integer> finalBookmarkOrders = Map.copyOf(bookmarkOrders);
        suppressAutoReloadOnBookmarkChange = true;
        bookmarkOrderSaveExecutor.execute(() -> {
            if (saveGeneration != bookmarkOrderSaveGeneration.get()) {
                return;
            }
            try {
                BookmarkService.getInstance().saveBookmarkOrders(finalBookmarkOrders);
                long revision = BookmarkService.getInstance().getChangeRevision();
                runLater(() -> {
                    lastKnownBookmarkRevision = revision;
                    if (saveGeneration == bookmarkOrderSaveGeneration.get()) {
                        suppressAutoReloadOnBookmarkChange = false;
                    }
                });
            } catch (Exception _) {
                runLater(() -> {
                    if (saveGeneration == bookmarkOrderSaveGeneration.get()) {
                        suppressAutoReloadOnBookmarkChange = false;
                    }
                    forceReload();
                    showErrorAlert("Unable to save bookmark order.");
                });
            }
        });
    }

    private void applyLocalBookmarkOrder(String categoryId, List<String> orderedDbIds) {
        Map<String, Integer> orderByBookmarkId = new HashMap<>();
        for (int i = 0; i < orderedDbIds.size(); i++) {
            orderByBookmarkId.put(orderedDbIds.get(i), i);
        }

        if (categoryId == null) {
            allBookmarkItems.sort(Comparator.comparingInt(item -> orderByBookmarkId.getOrDefault(item.getBookmarkId(), Integer.MAX_VALUE)));
            return;
        }

        List<BookmarkItem> reorderedCategoryItems = allBookmarkItems.stream()
                .filter(item -> Objects.equals(categoryId, item.getCategoryId()))
                .sorted(Comparator.comparingInt(item -> orderByBookmarkId.getOrDefault(item.getBookmarkId(), Integer.MAX_VALUE)))
                .toList();

        if (reorderedCategoryItems.isEmpty()) {
            return;
        }

        int categoryItemIndex = 0;
        for (int i = 0; i < allBookmarkItems.size(); i++) {
            BookmarkItem current = allBookmarkItems.get(i);
            if (Objects.equals(categoryId, current.getCategoryId())) {
                allBookmarkItems.set(i, reorderedCategoryItems.get(categoryItemIndex++));
            }
        }
    }

    private Map<String, Integer> buildPersistedBookmarkOrders() {
        Map<String, Integer> bookmarkOrders = new HashMap<>();
        for (int i = 0; i < allBookmarkItems.size(); i++) {
            bookmarkOrders.put(allBookmarkItems.get(i).getBookmarkId(), i + 1);
        }
        return bookmarkOrders;
    }

    private ContextMenu createBookmarkContextMenu(BookmarkItem item, List<BookmarkItem> selectedItems, Node owner) {
        ContextMenu rowMenu = new ContextMenu();
        rowMenu.getStyleClass().add("bookmark-context-menu");
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);

        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.setOnAction(_ -> {
                if (selectedItems.size() > 1) {
                    showErrorAlert(I18n.tr("autoThisActionIsDisabledForMultipleSelections"));
                } else {
                    rowMenu.hide();
                    play(item, option.playerPath());
                }
            });
            rowMenu.getItems().add(playerItem);
        }

        Menu addToMenu = new Menu(I18n.tr("autoAddTo"));
        List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();
        for (BookmarkCategory category : categories) {
            MenuItem categoryItem = new MenuItem(category.getName());
            categoryItem.setOnAction(_ -> {
                for (BookmarkItem selectedItem : selectedItems) {
                    selectedItem.setCategoryTitle(category.getName());
                    Bookmark b = BookmarkService.getInstance().getBookmark(selectedItem.getBookmarkId());
                    if (b != null) {
                        b.setCategoryId(category.getId());
                        BookmarkService.getInstance().save(b);
                    }
                }
                forceReload();
            });
            addToMenu.getItems().add(categoryItem);
        }

        MenuItem editItem = new MenuItem(I18n.tr("autoRemoveFromFavorite"));
        editItem.getStyleClass().add("danger-menu-item");
        editItem.setOnAction(_ -> handleDeleteMultipleBookmarks());

        rowMenu.getItems().add(addToMenu);
        rowMenu.getItems().add(new SeparatorMenuItem());
        rowMenu.getItems().add(editItem);
        return rowMenu;
    }

    private void handleDeleteMultipleBookmarks() {
        List<BookmarkItem> selectedItems = new ArrayList<>(bookmarkGrid.getSelectedItems());
        int selectedCount = selectedItems.size();
        if (selectedCount == 0) {
            return;
        }

        String message = I18n.tr(
                "autoRemoveBookmarksFromFavoriteConfirm",
                selectedCount,
                selectedItems.stream()
                        .map(BookmarkItem::getChannelName)
                        .collect(Collectors.joining(", "))
        );

        isPromptShowing = true;
        if (!showConfirmationAlert(message)) {
            return;
        }
        suppressAutoReloadOnBookmarkChange = true;
        new Thread(() -> {
            List<String> removedBookmarkIds = new ArrayList<>();
            for (BookmarkItem selectedItem : selectedItems) {
                try {
                    BookmarkService.getInstance().remove(selectedItem.getBookmarkId());
                    removedBookmarkIds.add(selectedItem.getBookmarkId());
                } catch (Exception _) {
                    // Best-effort batch delete: continue removing the remaining bookmarks.
                }
            }
            runLater(() -> {
                try {
                    if (!removedBookmarkIds.isEmpty()) {
                        allBookmarkItems.removeIf(item -> removedBookmarkIds.contains(item.getBookmarkId()));
                        filterView();
                        bookmarkGrid.clearSelection();
                        if (allBookmarkItems.isEmpty()) {
                            bookmarkGrid.setPlaceholderText(I18n.tr("autoNoBookmarksFound"));
                        }
                    }
                    lastKnownBookmarkRevision = BookmarkService.getInstance().getChangeRevision();
                } finally {
                    suppressAutoReloadOnBookmarkChange = false;
                }
            });
        }, "bookmark-delete").start();
    }

    private void play(BookmarkItem item, String playerPath) {
        if (item == null) {
            return;
        }
        PlaybackContext playbackContext;
        try {
            playbackContext = resolvePlaybackContext(item);
        } catch (Exception e) {
            showErrorAlert(I18n.tr("autoErrorPreparingBookmark", e.getMessage()));
            return;
        }
        if (playbackContext == null || playbackContext.mediaContext == null || playbackContext.channel == null) {
            showErrorAlert(I18n.tr("autoUnableToLoadAccountChannelForThisBookmark"));
            return;
        }
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(playbackContext.mediaContext, playbackContext.channel, playerPath)
                .categoryId(playbackContext.sourceCategoryDbId)
                .channelId(item.getChannelId())
                .errorPrefix(I18n.tr("autoErrorPlayingBookmarkPrefix")));
    }

    private PlaybackContext resolvePlaybackContext(BookmarkItem item) {
        Account account = AccountService.getInstance().getByName(item.getAccountName());
        if (account == null) {
            return null;
        }
        AccountMediaContext mediaContext = AccountMediaContext.from(account, item.getAccountAction());
        if (mediaContext == null) {
            return null;
        }
        if (isNotBlank(item.getServerPortalUrl())) {
            mediaContext = mediaContext.withServerPortalUrl(item.getServerPortalUrl());
        }
        Account lookupAccount = mediaContext.toAccount();
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

        Channel latestCachedChannel = findLatestCachedChannel(lookupAccount, item);
        if (latestCachedChannel != null) {
            mergeLatestChannel(channel, latestCachedChannel);
        }

        String sourceCategoryDbId = resolveSourceCategoryDbId(lookupAccount, item, bookmark);
        return new PlaybackContext(mediaContext, channel, sourceCategoryDbId);
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
            case itv ->
                    ChannelService.getInstance().findCachedLiveChannel(account, item.getChannelId(), item.getChannelName());
            case vod ->
                    ChannelService.getInstance().findCachedVodChannel(account, sourceCategoryDbId, item.getChannelId(), item.getChannelName());
            case series ->
                    ChannelService.getInstance().findCachedSeriesChannel(account, sourceCategoryDbId, item.getChannelId(), item.getChannelName());
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
        private final AccountMediaContext mediaContext;
        private final Channel channel;
        private final String sourceCategoryDbId;

        private PlaybackContext(AccountMediaContext mediaContext, Channel channel, String sourceCategoryDbId) {
            this.mediaContext = mediaContext;
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


        @SuppressWarnings("java:S107")
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

    private enum BookmarkSortMode {
        DEFAULT,
        ASCENDING,
        DESCENDING
    }
}
