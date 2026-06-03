package com.uiptv.ui;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.shared.Episode;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.widget.BookmarkCard;
import com.uiptv.widget.IconActionButton;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.application.HostServices;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.*;
import javafx.scene.layout.*;
import javafx.stage.Stage;

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

public class BookmarkChannelListUI extends HBox {
    private static final String BOOKMARK_CACHE = "bookmark";
    private static final String REPORT_BUG_URL = "https://github.com/xixogo5105/uiptv/issues";
    private static final String GUIDE_URL = "https://github.com/xixogo5105/uiptv/blob/main/GUIDE.md";
    private static final double COMPACT_HEADER_WIDTH = 980;
    private static final double COMPACT_CATEGORY_WIDTH = 680;
    private static final double WIDE_SEARCH_WIDTH = 560;
    private static final int BOOKMARK_STREAM_BATCH_SIZE = 25;
    private static final String ICON_ABOUT = "M11 17H13V11H11V17ZM11 9H13V7H11V9ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20Z";
    private static final String ICON_BUG = "M20 8H17.19C16.74 7.22 16.12 6.55 15.38 6.04L17 4.41 15.59 3 13.89 4.7C13.29 4.52 12.66 4.43 12 4.43S10.71 4.52 10.11 4.7L8.41 3 7 4.41 8.62 6.04C7.88 6.55 7.26 7.22 6.81 8H4V10H6.09C6.03 10.33 6 10.66 6 11V12H4V14H6V15C6 15.34 6.03 15.67 6.09 16H4V18H6.81C7.84 19.79 9.77 21 12 21S16.16 19.79 17.19 18H20V16H17.91C17.97 15.67 18 15.34 18 15V14H20V12H18V11C18 10.66 17.97 10.33 17.91 10H20V8ZM9 16.5C8.45 16.5 8 16.05 8 15.5S8.45 14.5 9 14.5 10 14.95 10 15.5 9.55 16.5 9 16.5ZM15 16.5C14.45 16.5 14 16.05 14 15.5S14.45 14.5 15 14.5 16 14.95 16 15.5 15.55 16.5 15 16.5ZM16 12H8V11C8 8.79 9.79 7 12 7S16 8.79 16 11V12Z";
    private static final String ICON_HELP = "M11 18H13V16H11V18ZM12 2C6.48 2 2 6.48 2 12S6.48 22 12 22 22 17.52 22 12 17.52 2 12 2ZM12 20C7.59 20 4 16.41 4 12S7.59 4 12 4 20 7.59 20 12 16.41 20 12 20ZM12 6C9.79 6 8 7.79 8 10H10C10 8.9 10.9 8 12 8S14 8.9 14 10C14 12 11 11.75 11 15H13C13 12.75 16 12.5 16 10 16 7.79 14.21 6 12 6Z";
    private static final String ICON_THEME = "M12 3C7.03 3 3 7.03 3 12S7.03 21 12 21C15.31 21 18.2 19.21 19.76 16.54 18.86 16.84 17.91 17 16.92 17 11.95 17 7.92 12.97 7.92 8 7.92 6.39 8.34 4.87 9.08 3.56 9.98 3.2 10.96 3 12 3Z";
    private static final String ICON_PARENTAL_LOCK = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H17V6C17 3.24 14.76 1 12 1S7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM9 6C9 4.34 10.34 3 12 3S15 4.34 15 6V8H9V6ZM18 20H6V10H18V20Z";
    private static final String ICON_PARENTAL_UNLOCKED = "M12 17C13.1 17 14 16.1 14 15S13.1 13 12 13 10 13.9 10 15 10.9 17 12 17ZM18 8H9V6C9 4.34 10.34 3 12 3 13.09 3 14.05 3.58 14.58 4.45L16.32 3.45C15.44 1.99 13.84 1 12 1 9.24 1 7 3.24 7 6V8H6C4.9 8 4 8.9 4 10V20C4 21.1 4.9 22 6 22H18C19.1 22 20 21.1 20 20V10C20 8.9 19.1 8 18 8ZM18 20H6V10H18V20Z";
    private final TextField searchTextField = new TextField();
    private final Button manageCategoriesButton = new Button(I18n.tr("commonAdd"));
    private final ResponsiveCardGrid<BookmarkItem> bookmarkGrid = new ResponsiveCardGrid<>(this::createBookmarkCard);
    private final PillBar<BookmarkCategory> categoryPillBar =
            new PillBar<>(BookmarkCategory::getName, BookmarkCategory::getId);
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
    private final ConfigurationChangeListener configurationChangeListener = _ -> runLater(this::updateParentalPauseButton);
    private IconActionButton parentalPauseButton;
    private boolean isPromptShowing = false;
    private boolean compactHeaderLayout = false;
    private boolean compactCategoryLayout = false;
    private boolean thumbnailsEnabled = ThumbnailAwareUI.areThumbnailsEnabled();
    private volatile long lastKnownBookmarkRevision = 0;
    private volatile boolean reloadInProgress = false;
    private volatile boolean loadedOnce = false;
    private volatile boolean reloadRequestedWhileReloading = false;
    private boolean changeListenerRegistered = false;
    private boolean thumbnailListenerRegistered = false;
    private boolean configurationListenerRegistered = false;
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

    public BookmarkChannelListUI() {
        this(null, null);
    }

    public BookmarkChannelListUI(HostServices hostServices, Runnable themeToggleHandler) {
        this.hostServices = hostServices;
        this.themeToggleHandler = themeToggleHandler;
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            ImageCacheManager.clearCache(BOOKMARK_CACHE);
        }
        initWidgets();
        registerBookmarkChangeListener();
        registerThumbnailModeListener();
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                releaseTransientState();
                unregisterConfigurationChangeListener();
            } else if (isVisible()) {
                registerConfigurationChangeListener();
                updateParentalPauseButton();
                ensureLoaded();
            } else {
                registerConfigurationChangeListener();
                updateParentalPauseButton();
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
        showLoadingPlaceholderIfEmpty(generation);
        startReloadThread(generation);
    }

    private void showLoadingPlaceholderIfEmpty(long generation) {
        runLater(() -> {
            if (generation != reloadGeneration.get()) {
                return;
            }
            if (allBookmarkItems.isEmpty()) {
                bookmarkGrid.setPlaceholderText(I18n.tr("autoLoadingBookmarks"));
            }
        });
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
        if (changeListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
        changeListenerRegistered = true;
        sceneProperty().addListener((_, _, newScene) -> {
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

    private void registerConfigurationChangeListener() {
        if (configurationListenerRegistered) {
            return;
        }
        ConfigurationService.getInstance().addChangeListener(configurationChangeListener);
        configurationListenerRegistered = true;
    }

    private void unregisterConfigurationChangeListener() {
        if (!configurationListenerRegistered) {
            return;
        }
        ConfigurationService.getInstance().removeChangeListener(configurationChangeListener);
        configurationListenerRegistered = false;
    }

    private void releaseTransientState() {
        reloadGeneration.incrementAndGet();
        reloadInProgress = false;
        reloadRequestedWhileReloading = false;
        allBookmarkItems.clear();
        filteredItems.clear();
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
        setPadding(Insets.EMPTY);
        setSpacing(0);
        setFillHeight(true);
        setMinSize(0, 0);
        setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setupBookmarkGrid();
        setupCategoryPillListener();
        setupSearchTextFieldListener();
        setupManageCategoriesButton();

        VBox page = new VBox(12);
        page.getStyleClass().add("bookmarks-page");
        page.setMinSize(0, 0);
        page.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        page.getChildren().setAll(createHeaderArea(), bookmarkGrid);

        ScrollPane pageScroll = new ScrollPane(page);
        pageScroll.getStyleClass().addAll("bookmarks-page-scroll", "transparent-scroll-pane");
        pageScroll.setFitToWidth(true);
        pageScroll.setPannable(true);
        pageScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        pageScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        pageScroll.setFocusTraversable(false);
        pageScroll.setMinSize(0, 0);
        pageScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        getChildren().setAll(pageScroll);
        HBox.setHgrow(pageScroll, Priority.ALWAYS);
        addChannelClickHandler();
    }

    private VBox createHeaderArea() {
        Label title = new Label("Favourite");
        title.getStyleClass().add("bookmarks-page-title");
        title.setMaxWidth(Region.USE_PREF_SIZE);
        title.setPickOnBounds(false);

        TextField searchField = searchTextField;
        searchField.getStyleClass().add("bookmarks-search-field");
        searchField.setPromptText(I18n.tr("commonSearch"));
        searchField.setMinWidth(180);
        searchField.setPrefWidth(WIDE_SEARCH_WIDTH);
        searchField.setMaxWidth(WIDE_SEARCH_WIDTH);

        HBox quickActions = createQuickActions();
        StackPane wideHeaderRow = new StackPane();
        wideHeaderRow.setAlignment(Pos.CENTER_LEFT);
        wideHeaderRow.setMaxWidth(Double.MAX_VALUE);
        HBox compactTitleRow = new HBox(10);
        compactTitleRow.setAlignment(Pos.CENTER_LEFT);
        compactTitleRow.setMaxWidth(Double.MAX_VALUE);
        VBox headerStack = new VBox(10);
        headerStack.setFillWidth(true);
        headerStack.setMaxWidth(Double.MAX_VALUE);
        headerStack.getStyleClass().add("bookmarks-header-stack");

        VBox categoryRow = createCategoryRow();
        VBox headerArea = new VBox(12, headerStack, categoryRow);
        headerArea.getStyleClass().add("bookmarks-header-area");
        applyHeaderLayout(headerStack, wideHeaderRow, compactTitleRow, title, searchField, quickActions, false);
        widthProperty().addListener((_, _, newWidth) -> applyHeaderLayout(
                headerStack,
                wideHeaderRow,
                compactTitleRow,
                title,
                searchField,
                quickActions,
                newWidth.doubleValue() < COMPACT_HEADER_WIDTH
        ));
        return headerArea;
    }

    private void applyHeaderLayout(VBox headerStack,
                                   StackPane wideHeaderRow,
                                   HBox compactTitleRow,
                                   Label title,
                                   TextField searchField,
                                   HBox quickActions,
                                   boolean compact) {
        if (compactHeaderLayout == compact && !headerStack.getChildren().isEmpty()) {
            return;
        }

        wideHeaderRow.getChildren().clear();
        compactTitleRow.getChildren().clear();
        headerStack.getChildren().clear();
        compactHeaderLayout = compact;

        if (compact) {
            Region titleSpacer = new Region();
            HBox.setHgrow(titleSpacer, Priority.ALWAYS);
            searchField.setMaxWidth(Double.MAX_VALUE);
            compactTitleRow.getChildren().setAll(title, titleSpacer, quickActions);
            headerStack.getChildren().setAll(compactTitleRow, searchField);
            return;
        }

        searchField.setMaxWidth(WIDE_SEARCH_WIDTH);
        StackPane.setAlignment(title, Pos.CENTER_LEFT);
        StackPane.setAlignment(searchField, Pos.CENTER);
        StackPane.setAlignment(quickActions, Pos.CENTER_RIGHT);
        wideHeaderRow.getChildren().setAll(title, searchField, quickActions);
        headerStack.getChildren().setAll(wideHeaderRow);
    }

    private VBox createCategoryRow() {
        Button manageButton = manageCategoriesButton;
        manageButton.getStyleClass().add("bookmark-manage-categories-button");
        manageButton.setMinWidth(Region.USE_PREF_SIZE);

        HBox wideRow = new HBox(10);
        wideRow.setAlignment(Pos.CENTER_LEFT);
        wideRow.setFillHeight(false);

        HBox manageRow = new HBox(manageButton);
        manageRow.setAlignment(Pos.CENTER_RIGHT);
        manageRow.setFillHeight(false);

        VBox row = new VBox(8);
        row.setFillWidth(true);
        row.setMaxWidth(Double.MAX_VALUE);
        row.getStyleClass().add("bookmark-category-row");

        categoryPillBar.setMaxWidth(Double.MAX_VALUE);
        categoryPillBar.setMaxHeight(Region.USE_PREF_SIZE);
        applyCategoryLayout(row, wideRow, manageRow, manageButton, false);
        widthProperty().addListener((_, _, newWidth) -> applyCategoryLayout(
                row,
                wideRow,
                manageRow,
                manageButton,
                newWidth.doubleValue() < COMPACT_CATEGORY_WIDTH
        ));
        return row;
    }

    private void applyCategoryLayout(VBox row,
                                     HBox wideRow,
                                     HBox manageRow,
                                     Button manageButton,
                                     boolean compact) {
        if (compactCategoryLayout == compact && !row.getChildren().isEmpty()) {
            return;
        }

        wideRow.getChildren().clear();
        manageRow.getChildren().clear();
        row.getChildren().clear();
        compactCategoryLayout = compact;

        if (compact) {
            manageRow.getChildren().setAll(manageButton);
            row.getChildren().setAll(categoryPillBar, manageRow);
            return;
        }

        wideRow.getChildren().setAll(categoryPillBar, manageButton);
        HBox.setHgrow(categoryPillBar, Priority.ALWAYS);
        row.getChildren().setAll(wideRow);
    }

    private HBox createQuickActions() {
        HBox quickActions = new HBox(6);
        quickActions.setAlignment(Pos.CENTER_RIGHT);
        quickActions.setMinWidth(Region.USE_PREF_SIZE);
        quickActions.setMaxWidth(Region.USE_PREF_SIZE);
        quickActions.setPickOnBounds(false);
        quickActions.getStyleClass().add("bookmarks-quick-actions");
        quickActions.getChildren().addAll(
                createQuickActionButton(I18n.tr("autoAbout"), ICON_ABOUT, this::showAbout),
                createQuickActionButton("Report a bug", ICON_BUG, () -> openExternalUrl(REPORT_BUG_URL)),
                createQuickActionButton(I18n.tr("autoHelp"), ICON_HELP, () -> openExternalUrl(GUIDE_URL)),
                createParentalPauseButton(),
                createQuickActionButton("Toggle theme", ICON_THEME, this::toggleTheme)
        );
        return quickActions;
    }

    private IconActionButton createParentalPauseButton() {
        parentalPauseButton = new IconActionButton("Pause parental lock restrictions", ICON_PARENTAL_LOCK, this::toggleParentalPause);
        updateParentalPauseButton();
        return parentalPauseButton;
    }

    private void toggleParentalPause() {
        if (!FilterLockDialogs.ensureUnlocked(this, "filterLockUnlockManageFiltersReason")) {
            updateParentalPauseButton();
            return;
        }
        Configuration configuration = ConfigurationService.getInstance().read();
        if (configuration == null) {
            return;
        }
        configuration.setPauseFiltering(!configuration.isPauseFiltering());
        ConfigurationService.getInstance().save(configuration);
        updateParentalPauseButton();
        showMessageAlert(configuration.isPauseFiltering()
                ? "Parental lock restrictions paused."
                : "Parental lock restrictions resumed.");
    }

    private void updateParentalPauseButton() {
        if (parentalPauseButton == null) {
            return;
        }
        Configuration configuration = ConfigurationService.getInstance().read();
        boolean paused = configuration != null && configuration.isPauseFiltering();
        parentalPauseButton.setTooltipText(paused
                ? "Resume parental lock restrictions"
                : "Pause parental lock restrictions");
        parentalPauseButton.setIconPath(paused ? ICON_PARENTAL_UNLOCKED : ICON_PARENTAL_LOCK);
        parentalPauseButton.getStyleClass().remove("bookmarks-quick-action-button-active");
        if (paused) {
            parentalPauseButton.getStyleClass().add("bookmarks-quick-action-button-active");
        }
    }

    private Button createQuickActionButton(String tooltipText, String iconPath, Runnable action) {
        return new IconActionButton(tooltipText, iconPath, action);
    }

    private void openExternalUrl(String url) {
        if (hostServices != null) {
            hostServices.showDocument(url);
            return;
        }
        com.uiptv.ui.util.UiServerUrlUtil.openInBrowser(url);
    }

    private void showAbout() {
        if (hostServices != null) {
            AboutUI.show(hostServices);
        }
    }

    private void toggleTheme() {
        if (themeToggleHandler != null) {
            themeToggleHandler.run();
        }
    }

    private void setupBookmarkGrid() {
        bookmarkGrid.getStyleClass().add("bookmark-card-grid");
        bookmarkGrid.setItems(filteredItems);
        bookmarkGrid.setCardWidthRange(220, 310);
        bookmarkGrid.setGaps(14, 12);
        bookmarkGrid.setReorderEnabled(true);
        bookmarkGrid.setPlaceholderText(I18n.tr("autoNoBookmarksFound"));
        applyThumbnailMode(ThumbnailAwareUI.areThumbnailsEnabled());
    }

    private BookmarkCard createBookmarkCard(BookmarkItem item) {
        return new BookmarkCard(
                item.getChannelName(),
                item.getAccountName(),
                item.getLogo(),
                thumbnailsEnabled,
                BOOKMARK_CACHE,
                isDrmProtected(item)
        );
    }

    private boolean isDrmProtected(BookmarkItem item) {
        return item != null && (isNotBlank(item.getDrmType())
                || isNotBlank(item.getDrmLicenseUrl())
                || isNotBlank(item.getClearKeysJson())
                || isNotBlank(item.getInputstreamaddon())
                || isNotBlank(item.getManifestType()));
    }

    private void registerThumbnailModeListener() {
        if (thumbnailListenerRegistered) {
            return;
        }
        ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
        thumbnailListenerRegistered = true;
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                ThumbnailAwareUI.removeThumbnailModeListener(thumbnailModeListener);
                thumbnailListenerRegistered = false;
            } else if (!thumbnailListenerRegistered) {
                ThumbnailAwareUI.addThumbnailModeListener(thumbnailModeListener);
                thumbnailListenerRegistered = true;
            }
        });
    }

    private void onThumbnailModeChanged(boolean enabled) {
        runLater(() -> applyThumbnailMode(enabled));
    }

    private void applyThumbnailMode(boolean enabled) {
        if (enabled) {
            ImageCacheManager.clearCache(BOOKMARK_CACHE);
        }
        thumbnailsEnabled = enabled;
        bookmarkGrid.refresh();
    }

    private void setupCategoryPillListener() {
        categoryPillBar.selectedItemProperty().addListener((_, _, _) -> filterView());
    }

    private void setupSearchTextFieldListener() {
        searchTextField.textProperty().addListener((observable, oldValue, newValue) -> filterView());
    }

    private void setupManageCategoriesButton() {
        manageCategoriesButton.setText(I18n.tr("searchableTableManageTabs"));
        manageCategoriesButton.setOnAction(event -> openCategoryManagementPopup());
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
                .toList();

        if (!sameFilteredItems(filteredList)) {
            filteredItems.setAll(filteredList);
        }
        if (filteredList.isEmpty()) {
            bookmarkGrid.setPlaceholderText(searchText.isBlank()
                    ? I18n.tr("autoNoBookmarksFound")
                    : I18n.tr("autoNothingFoundFor", rawSearchText));
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
        Stage popupStage = new Stage();
        CategoryManagementPopup popup = new CategoryManagementPopup(this);
        Scene scene = new Scene(popup, 300, 400);
        UiI18n.applySceneOrientation(scene);
        scene.getStylesheets().add(RootApplication.getCurrentTheme());
        popupStage.setTitle(I18n.tr("autoManageCategories"));
        popupStage.setScene(scene);
        popupStage.showAndWait();
        forceReload();
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
        if (playbackContext == null || playbackContext.account == null || playbackContext.channel == null) {
            showErrorAlert(I18n.tr("autoUnableToLoadAccountChannelForThisBookmark"));
            return;
        }
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(playbackContext.account, playbackContext.channel, playerPath)
                .categoryId(playbackContext.sourceCategoryDbId)
                .channelId(item.getChannelId())
                .errorPrefix(I18n.tr("autoErrorPlayingBookmarkPrefix")));
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
}
