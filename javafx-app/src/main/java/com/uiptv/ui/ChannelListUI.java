package com.uiptv.ui;
import com.uiptv.ui.util.*;
import com.uiptv.ui.util.*;

import com.uiptv.util.I18n;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.AsyncImageView;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.BooleanBinding;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Callback;
import javafx.animation.PauseTransition;
import javafx.util.Duration;

import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class ChannelListUI extends HBox {
    private static final String IMAGE_CACHE_KEY_CHANNEL = "channel";
    private static final String DRM_BADGE_STYLE_CLASS = "drm-badge";

    private final Account account;
    private final String categoryTitle;
    private final String categoryId;
    private final SearchableTableView<ChannelItem> table = new SearchableTableView<>();
    private final TableColumn<ChannelItem, String> channelName = new TableColumn<>(I18n.tr("autoChannels"));
    private final List<Channel> channelList;
    private ObservableList<ChannelItem> channelItems;
    private final Set<String> seenChannelKeys = new HashSet<>();
    private final Map<String, ChannelItem> channelItemByKey = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicReference<Map<String, String>> categoryTitleByCategoryId = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, String>> categoryTitleByNormalizedTitle = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, BookmarkContext>> m3uAllSourceContextByChannelKey = new AtomicReference<>(Map.of());
    private final AtomicBoolean itemsLoaded = new AtomicBoolean(false);
    private static final int MAX_SERIES_EPISODE_CACHE_ENTRIES = Integer.getInteger("uiptv.series.cache.maxEntries", 48);
    private final Map<String, EpisodeList> seriesEpisodesCache = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, EpisodeList> eldest) {
                    return size() > MAX_SERIES_EPISODE_CACHE_ENTRIES;
                }
            }
    );
    private boolean bookmarkListenerRegistered = false;
    private boolean vodWatchStateListenerRegistered = false;
    private boolean thumbnailListenerRegistered = false;
    private final BookmarkChangeListener bookmarkChangeListener = (revision, updatedEpochMs) -> refreshBookmarkStatesAsync();
    private final VodWatchStateChangeListener vodWatchStateChangeListener = (accountId, vodId) -> refreshBookmarkStatesAsync();
    private final AtomicReference<Thread> currentLoadingThread = new AtomicReference<>();
    private AtomicBoolean currentRequestCancelled;
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = this::onThumbnailModeChanged;
    private boolean embeddedMode = false;
    private boolean inlineEpisodeNavigationEnabled = false;
    private final VBox listPane = new VBox(5);
    private final VBox detailPane = new VBox(8);
    private final HBox detailNavHeader = new HBox(6);
    private final Button detailBackButton = createBackButton();
    private final Label detailTitle = new Label();
    private final VBox detailContent = new VBox();
    private final ProgressBar loadingProgress = new ProgressBar(0);
    private final HBox loadingProgressBox = new HBox(loadingProgress);
    private PauseTransition loadingProgressHideTimer;

    public ChannelListUI(List<Channel> channelList, Account account, String categoryTitle, String categoryId) {
        this(account, categoryTitle, categoryId);
        addItems(channelList);
    }

    public ChannelListUI(Account account, String categoryTitle, String categoryId) {
        this.categoryId = categoryId;
        this.channelList = new ArrayList<>();
        this.account = account;
        this.categoryTitle = categoryTitle;
        preloadAllCategoryContextAsync();
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            ImageCacheManager.clearCache(IMAGE_CACHE_KEY_CHANNEL);
        }
        initWidgets();
        registerBookmarkListener();
        registerThumbnailModeListener();
        table.setPlaceholder(new Label(I18n.tr("autoLoadingChannelsFor", categoryTitle)));
    }

    public void setEmbeddedMode(boolean embeddedMode) {
        this.embeddedMode = embeddedMode;
        if (embeddedMode) {
            showListView();
        }
    }

    public void setInlineEpisodeNavigationEnabled(boolean enabled) {
        this.inlineEpisodeNavigationEnabled = enabled;
        if (enabled) {
            showListView();
        }
    }

    public void addItems(List<Channel> newChannels) {
        if (newChannels != null && !newChannels.isEmpty()) {
            itemsLoaded.set(true);
            List<Bookmark> accountBookmarks = loadBookmarksForAccount();
            Set<String> savedVodKeys = loadVodWatchStateKeys();
            List<ChannelItem> newItems = new ArrayList<>();
            List<LogoUpdate> logoUpdates = new ArrayList<>();
            newChannels.forEach(channel -> processIncomingChannel(channel, accountBookmarks, savedVodKeys, newItems, logoUpdates));

            runLater(() -> {
                if (!newItems.isEmpty()) {
                    channelItems.addAll(newItems);
                    table.setPlaceholder(null);
                }
                if (!logoUpdates.isEmpty()) {
                    for (LogoUpdate update : logoUpdates) {
                        update.item().setLogo(update.normalizedLogo());
                        update.item().getChannel().setLogo(update.sourceChannel().getLogo());
                    }
                    table.refresh();
                }
            });
        }
    }

    private void processIncomingChannel(Channel channel,
                                        List<Bookmark> accountBookmarks,
                                        Set<String> savedVodKeys,
                                        List<ChannelItem> newItems,
                                        List<LogoUpdate> logoUpdates) {
        if (channel == null) {
            return;
        }
        String key = buildChannelKey(channel);
        String normalizedLogo = normalizeImageUrl(channel.getLogo());
        if (queueLogoUpdateIfPresent(channel, key, normalizedLogo, logoUpdates)) {
            return;
        }
        if (!seenChannelKeys.add(key)) {
            return;
        }
        BookmarkContext context = resolveBookmarkContext(channel);
        updateSeriesProgressIfNeeded(channel, context);
        ChannelItem channelItem = buildChannelItem(channel, normalizedLogo, context, accountBookmarks, savedVodKeys);
        channelList.add(channel);
        channelItemByKey.put(key, channelItem);
        newItems.add(channelItem);
    }

    private boolean queueLogoUpdateIfPresent(Channel channel, String key, String normalizedLogo, List<LogoUpdate> logoUpdates) {
        ChannelItem existingItem = channelItemByKey.get(key);
        if (existingItem == null) {
            return false;
        }
        if (!isBlank(normalizedLogo) && !Objects.equals(existingItem.getLogo(), normalizedLogo)) {
            logoUpdates.add(new LogoUpdate(existingItem, normalizedLogo, channel));
        }
        return true;
    }

    private void updateSeriesProgressIfNeeded(Channel channel, BookmarkContext context) {
        if (account.getAction() != series) {
            return;
        }
        String seriesCategoryId = resolveSeriesCategoryId(channel, context);
        boolean inProgress = SeriesWatchStateService.getInstance()
                .getSeriesLastWatched(account.getDbId(), seriesCategoryId, channel.getChannelId()) != null;
        channel.setWatched(inProgress);
    }

    private ChannelItem buildChannelItem(Channel channel,
                                         String normalizedLogo,
                                         BookmarkContext context,
                                         List<Bookmark> accountBookmarks,
                                         Set<String> savedVodKeys) {
        boolean isBookmarked = account.getAction() == vod
                ? isVodSaved(channel, context, savedVodKeys)
                : isChannelBookmarked(channel, context, accountBookmarks);
        return new ChannelItem(
                new SimpleStringProperty(channel.getName()),
                new SimpleStringProperty(channel.getChannelId()),
                new SimpleStringProperty(channel.getCmd()),
                isBookmarked,
                new SimpleStringProperty(normalizedLogo),
                channel
        );
    }

    private String buildChannelKey(Channel channel) {
        String id = channel.getChannelId() == null ? "" : channel.getChannelId().trim();
        String cmd = channel.getCmd() == null ? "" : channel.getCmd().trim();
        String name = channel.getName() == null ? "" : channel.getName().trim().toLowerCase();
        return id + "|" + cmd + "|" + name;
    }

    public void setLoadingComplete() {
        runLater(() -> {
            if (!itemsLoaded.get()) {
                table.setPlaceholder(new Label(I18n.tr("autoNothingFoundFor", categoryTitle)));
            }
            finalizeLoadingProgress();
        });
    }

    public void startLoadingProgressIfNeeded() {
        if (account.getAction() != vod && account.getAction() != series) {
            return;
        }
        runLater(() -> {
            loadingProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            showLoadingProgress();
        });
    }

    public void updateLoadingProgress(int fetchedItems, int totalItems, int pageNumber, int pageCount) {
        if (account.getAction() != vod && account.getAction() != series) {
            return;
        }
        runLater(() -> {
            showLoadingProgress();
            if (totalItems > 0) {
                double progress = Math.min(1.0, (double) fetchedItems / (double) totalItems);
                loadingProgress.setProgress(progress);
            } else if (pageCount > 0) {
                double progress = Math.min(1.0, (double) pageNumber / (double) pageCount);
                loadingProgress.setProgress(progress);
            } else {
                loadingProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            }
        });
    }

    private void configureLoadingProgressBar() {
        loadingProgress.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(loadingProgress, Priority.ALWAYS);
        loadingProgressBox.setAlignment(Pos.CENTER_LEFT);
        loadingProgressBox.setVisible(false);
        loadingProgressBox.setManaged(false);
    }

    private void showLoadingProgress() {
        cancelLoadingProgressHide();
        loadingProgressBox.setVisible(true);
        loadingProgressBox.setManaged(true);
        loadingProgress.setStyle("");
    }

    private void finalizeLoadingProgress() {
        if (account.getAction() != vod && account.getAction() != series) {
            return;
        }
        if (!loadingProgressBox.isVisible()) {
            return;
        }
        loadingProgress.setProgress(1.0);
        loadingProgress.setStyle("-fx-accent: #28a745;");
        scheduleLoadingProgressHide();
    }

    private void scheduleLoadingProgressHide() {
        cancelLoadingProgressHide();
        loadingProgressHideTimer = new PauseTransition(Duration.seconds(5));
        loadingProgressHideTimer.setOnFinished(event -> {
            loadingProgressBox.setVisible(false);
            loadingProgressBox.setManaged(false);
            loadingProgress.setStyle("");
        });
        loadingProgressHideTimer.play();
    }

    private void cancelLoadingProgressHide() {
        if (loadingProgressHideTimer != null) {
            loadingProgressHideTimer.stop();
        }
    }

    private void initWidgets() {
        setSpacing(5);
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);
        table.setEditable(true);
        table.getColumns().add(channelName);
        channelName.setText(categoryTitle);
        channelName.setVisible(true);
        channelName.setCellValueFactory(cellData -> cellData.getValue().channelNameProperty());

        channelItems = FXCollections.observableArrayList(ChannelItem.extractor());
        SortedList<ChannelItem> sortedList = new SortedList<>(channelItems);

        // Bind the sorted list's comparator to a custom one that wraps the table's default comparator
        sortedList.comparatorProperty().bind(Bindings.createObjectBinding(() -> {
            Comparator<ChannelItem> tableComparator = table.getComparator();
            Comparator<ChannelItem> bookmarkComparator = Comparator.comparing(ChannelItem::isBookmarked).reversed();
            return tableComparator == null ? bookmarkComparator : bookmarkComparator.thenComparing(tableComparator);
        }, table.comparatorProperty()));

        table.setItems(sortedList);
        table.addTextFilter();

        applyThumbnailMode(ThumbnailAwareUI.areThumbnailsEnabled());

        channelName.setSortType(TableColumn.SortType.ASCENDING);

        configureLoadingProgressBar();
        VBox searchBox = new VBox(5, loadingProgressBox, table.getSearchTextField());
        AutoGrowVBox auto = new AutoGrowVBox(5, searchBox, table);
        VBox.setVgrow(searchBox, Priority.NEVER);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(auto, Priority.ALWAYS);
        listPane.getChildren().setAll(auto);
        listPane.setMaxHeight(Double.MAX_VALUE);
        listPane.setMinHeight(0);
        VBox.setVgrow(listPane, Priority.ALWAYS);
        initDetailPane();
        showListView();
        getChildren().setAll(listPane);
        addChannelClickHandler();
    }

    private void initDetailPane() {
        detailBackButton.setOnAction(event -> showListView());
        detailNavHeader.setAlignment(Pos.CENTER_LEFT);
        detailNavHeader.getChildren().setAll(detailBackButton, detailTitle);
        detailContent.setSpacing(5);
        detailContent.setPadding(new javafx.geometry.Insets(5, 0, 0, 0));
        VBox.setVgrow(detailContent, Priority.ALWAYS);
        detailPane.getChildren().setAll(detailContent);
    }

    private Button createBackButton() {
        Button button = new Button(I18n.tr("autoBack"));
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip(I18n.tr("autoBack")));
        return button;
    }

    public void showChannelListView() {
        showListView();
    }

    private void showListView() {
        if (!embeddedMode && !inlineEpisodeNavigationEnabled) {
            getChildren().setAll(listPane);
            return;
        }
        getChildren().setAll(listPane);
    }

    private void showDetailView(Node ui, String title) {
        if ((!embeddedMode && !inlineEpisodeNavigationEnabled) || ui == null) {
            return;
        }
        detailTitle.setText(title == null ? "" : title);
        detailContent.getChildren().setAll(ui);
        VBox.setVgrow(ui, Priority.ALWAYS);
        detailPane.setMaxHeight(Double.MAX_VALUE);
        detailPane.setMinHeight(0);
        if (inlineEpisodeNavigationEnabled) {
            detailPane.getChildren().setAll(detailNavHeader, detailContent);
        } else {
            detailPane.getChildren().setAll(detailContent);
        }
        getChildren().setAll(detailPane);
    }

    public boolean navigateBackEmbedded() {
        if (!embeddedMode) {
            return false;
        }
        if (getChildren().contains(detailPane)) {
            showListView();
            return true;
        }
        return false;
    }

    private TableCell<ChannelItem, String> createThumbnailCell() {
        return new TableCell<>() {

            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final Label drmBadge = new Label(I18n.tr("autoDrm"));
            private final Label progressBadge = new Label(I18n.tr("autoInPROGRESS"));
            private final Pane spacer = new Pane();
            private final SVGPath bookmarkIcon = new SVGPath();
            private final AsyncImageView imageView = new AsyncImageView();

            {
                bookmarkIcon.setContent("M3 0 V14 L8 10 L13 14 V0 H3 Z");
                bookmarkIcon.setFill(Color.BLACK);
                drmBadge.getStyleClass().add(DRM_BADGE_STYLE_CLASS);
                drmBadge.setVisible(false);
                drmBadge.setManaged(false);
                progressBadge.getStyleClass().add(DRM_BADGE_STYLE_CLASS);
                progressBadge.setVisible(false);
                progressBadge.setManaged(false);

                HBox.setHgrow(spacer, Priority.ALWAYS);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(imageView, nameLabel, drmBadge, progressBadge, spacer, bookmarkIcon);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                ChannelItem channelItem = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                        ? getTableView().getItems().get(getIndex())
                        : null;

                if (channelItem == null) {
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(item);
                boolean drmProtected = channelItem.getChannel() != null && PlayerService.getInstance().isDrmProtected(channelItem.getChannel());
                drmBadge.setVisible(drmProtected);
                drmBadge.setManaged(drmProtected);
                boolean inProgress = account.getAction() == series
                        && channelItem.getChannel() != null
                        && channelItem.getChannel().isWatched();
                progressBadge.setVisible(inProgress);
                progressBadge.setManaged(inProgress);
                bookmarkIcon.setVisible(channelItem.isBookmarked());
                imageView.loadImage(channelItem.getLogo(), IMAGE_CACHE_KEY_CHANNEL);
                setGraphic(graphic);
            }
        };
    }

    private TableCell<ChannelItem, String> createPlainTextCell() {
        return new TableCell<>() {

            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final Label drmBadge = new Label(I18n.tr("autoDrm"));
            private final Label progressBadge = new Label(I18n.tr("autoInPROGRESS"));
            private final Pane spacer = new Pane();
            private final SVGPath bookmarkIcon = new SVGPath();

            {
                bookmarkIcon.setContent("M3 0 V14 L8 10 L13 14 V0 H3 Z");
                bookmarkIcon.setFill(Color.BLACK);
                drmBadge.getStyleClass().add(DRM_BADGE_STYLE_CLASS);
                drmBadge.setVisible(false);
                drmBadge.setManaged(false);
                progressBadge.getStyleClass().add(DRM_BADGE_STYLE_CLASS);
                progressBadge.setVisible(false);
                progressBadge.setManaged(false);

                HBox.setHgrow(spacer, Priority.ALWAYS);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(nameLabel, drmBadge, progressBadge, spacer, bookmarkIcon);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                ChannelItem channelItem = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                        ? getTableView().getItems().get(getIndex())
                        : null;

                if (channelItem == null) {
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(item);
                boolean drmProtected = channelItem.getChannel() != null && PlayerService.getInstance().isDrmProtected(channelItem.getChannel());
                drmBadge.setVisible(drmProtected);
                drmBadge.setManaged(drmProtected);
                boolean inProgress = account.getAction() == series
                        && channelItem.getChannel() != null
                        && channelItem.getChannel().isWatched();
                progressBadge.setVisible(inProgress);
                progressBadge.setManaged(inProgress);
                bookmarkIcon.setVisible(channelItem.isBookmarked());
                setGraphic(graphic);
            }
        };
    }

    private void registerBookmarkListener() {
        if (bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = true;
        if (account.getAction() == vod && !vodWatchStateListenerRegistered) {
            VodWatchStateService.getInstance().addChangeListener(vodWatchStateChangeListener);
            vodWatchStateListenerRegistered = true;
        }
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterBookmarkListener();
                if (!embeddedMode && !inlineEpisodeNavigationEnabled) {
                    releaseTransientState();
                }
            } else if (!bookmarkListenerRegistered) {
                BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
                bookmarkListenerRegistered = true;
                if (account.getAction() == vod && !vodWatchStateListenerRegistered) {
                    VodWatchStateService.getInstance().addChangeListener(vodWatchStateChangeListener);
                    vodWatchStateListenerRegistered = true;
                }
                refreshBookmarkStatesAsync();
            }
        });
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
            ImageCacheManager.clearCache(IMAGE_CACHE_KEY_CHANNEL);
            channelName.setCellFactory(column -> createThumbnailCell());
        } else {
            channelName.setCellFactory(column -> createPlainTextCell());
        }
        table.refresh();
    }

    public void dispose() {
        releaseTransientState();
    }

    private void releaseTransientState() {
        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }
        Thread loadingThread = currentLoadingThread.getAndSet(null);
        if (loadingThread != null && loadingThread.isAlive()) {
            loadingThread.interrupt();
        }
        seriesEpisodesCache.clear();
        // Clear channel items and metadata to allow garbage collection
        if (channelItems != null) {
            channelItems.clear();
        }
        seenChannelKeys.clear();
        channelItemByKey.clear();
        categoryTitleByCategoryId.set(Map.of());
        categoryTitleByNormalizedTitle.set(Map.of());
        m3uAllSourceContextByChannelKey.set(Map.of());
    }

    private void unregisterBookmarkListener() {
        if (!bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().removeChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = false;
        if (vodWatchStateListenerRegistered) {
            VodWatchStateService.getInstance().removeChangeListener(vodWatchStateChangeListener);
            vodWatchStateListenerRegistered = false;
        }
    }

    private void refreshBookmarkStatesAsync() {
        if (channelItems == null || channelItems.isEmpty()) {
            return;
        }
        new Thread(() -> {
            List<Bookmark> accountBookmarks = loadBookmarksForAccount();
            Set<String> savedVodKeys = loadVodWatchStateKeys();
            runLater(() -> {
                for (ChannelItem item : channelItems) {
                    BookmarkContext context = resolveBookmarkContext(item.getChannel());
                    boolean isBookmarked = account.getAction() == vod
                            ? isVodSaved(item.getChannel(), context, savedVodKeys)
                            : isChannelBookmarked(item.getChannel(), context, accountBookmarks);
                    item.setBookmarked(isBookmarked);
                }
                table.refresh();
            });
        }).start();
    }

    private List<Bookmark> loadBookmarksForAccount() {
        if (account.getAction() == vod) {
            return List.of();
        }
        return BookmarkService.getInstance().read().stream()
                .filter(b -> account.getAccountName().equals(b.getAccountName()))
                .toList();
    }

    private Set<String> loadVodWatchStateKeys() {
        if (account.getAction() != vod || isBlank(account.getDbId())) {
            return Set.of();
        }
        return VodWatchStateService.getInstance().getAllByAccount(account.getDbId()).stream()
                .filter(Objects::nonNull)
                .map(state -> normalizeExact(state.getCategoryId()) + "|" + normalizeExact(state.getVodId()))
                .collect(Collectors.toSet());
    }

    private boolean isAllCategoryView() {
        return "all".equalsIgnoreCase(categoryTitle == null ? "" : categoryTitle.trim());
    }

    private boolean isChannelBookmarked(Channel channel, BookmarkContext context, List<Bookmark> bookmarks) {
        return findMatchingBookmark(channel, context, bookmarks) != null;
    }

    private boolean isVodSaved(Channel channel, BookmarkContext context, Set<String> savedVodKeys) {
        if (channel == null || savedVodKeys == null || savedVodKeys.isEmpty()) {
            return false;
        }
        return savedVodKeys.contains(normalizeExact(context == null ? null : context.categoryId) + "|" + normalizeExact(channel.getChannelId()));
    }

    private Bookmark findMatchingBookmark(Channel channel, BookmarkContext context, List<Bookmark> bookmarks) {
        if (channel == null || bookmarks == null || bookmarks.isEmpty()) {
            return null;
        }
        boolean allView = isAllCategoryView();
        ChannelBookmarkIdentity channelIdentity = new ChannelBookmarkIdentity(
                normalizeExact(channel.getChannelId()),
                normalizeExact(channel.getCmd()),
                normalizeLower(channel.getName()),
                normalizeExact(context == null ? null : context.categoryId),
                normalizeLower(context == null ? null : context.categoryTitle)
        );

        for (Bookmark bookmark : bookmarks) {
            if (bookmarkMatches(channelIdentity, bookmark, allView)) {
                return bookmark;
            }
        }
        return null;
    }

    private boolean bookmarkMatches(ChannelBookmarkIdentity channelIdentity, Bookmark bookmark, boolean allView) {
        if (bookmark == null) {
            return false;
        }
        String bookmarkCategoryId = resolveBookmarkSourceCategoryId(bookmark);
        String bookmarkCategoryTitle = resolveBookmarkSourceCategoryTitle(bookmark);
        if (!allView && !sameBookmarkCategory(channelIdentity, bookmarkCategoryId, bookmarkCategoryTitle)) {
            return false;
        }
        String bookmarkId = normalizeExact(bookmark.getChannelId());
        String bookmarkCmd = normalizeExact(bookmark.getCmd());
        if (matchesStrongIdentity(channelIdentity, bookmarkId, bookmarkCmd)) {
            return true;
        }
        return matchesNameOnly(channelIdentity, bookmarkId, bookmarkCmd, normalizeLower(bookmark.getChannelName()));
    }

    private boolean sameBookmarkCategory(ChannelBookmarkIdentity channelIdentity, String bookmarkCategoryId, String bookmarkCategoryTitle) {
        boolean sameCategoryById = !isBlank(channelIdentity.categoryId)
                && !isBlank(bookmarkCategoryId)
                && channelIdentity.categoryId.equals(bookmarkCategoryId);
        boolean sameCategoryByTitle = !isBlank(channelIdentity.categoryTitle)
                && !isBlank(bookmarkCategoryTitle)
                && channelIdentity.categoryTitle.equals(bookmarkCategoryTitle);
        return sameCategoryById || sameCategoryByTitle;
    }

    private boolean matchesStrongIdentity(ChannelBookmarkIdentity channelIdentity, String bookmarkId, String bookmarkCmd) {
        boolean idMatch = !isBlank(channelIdentity.channelId)
                && !isBlank(bookmarkId)
                && channelIdentity.channelId.equals(bookmarkId);
        boolean cmdMatch = !isBlank(channelIdentity.channelCmd)
                && !isBlank(bookmarkCmd)
                && channelIdentity.channelCmd.equals(bookmarkCmd);
        return idMatch || cmdMatch;
    }

    private boolean matchesNameOnly(ChannelBookmarkIdentity channelIdentity, String bookmarkId, String bookmarkCmd, String bookmarkName) {
        boolean bothWithoutStrongIdentity = isBlank(channelIdentity.channelId)
                && isBlank(channelIdentity.channelCmd)
                && isBlank(bookmarkId)
                && isBlank(bookmarkCmd);
        return bothWithoutStrongIdentity
                && !isBlank(channelIdentity.channelName)
                && channelIdentity.channelName.equals(bookmarkName);
    }

    private String normalizeExact(String value) {
        return value == null ? "" : value.trim();
    }

    private String normalizeLower(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private String resolveBookmarkSourceCategoryId(Bookmark bookmark) {
        if (bookmark == null) {
            return "";
        }
        Category sourceCategory = Category.fromJson(bookmark.getCategoryJson());
        if (sourceCategory != null && !isBlank(sourceCategory.getCategoryId())) {
            return normalizeExact(sourceCategory.getCategoryId());
        }
        // Legacy fallback for older bookmarks without categoryJson.
        return normalizeExact(bookmark.getCategoryId());
    }

    private String resolveBookmarkSourceCategoryTitle(Bookmark bookmark) {
        if (bookmark == null) {
            return "";
        }
        Category sourceCategory = Category.fromJson(bookmark.getCategoryJson());
        if (sourceCategory != null && !isBlank(sourceCategory.getTitle())) {
            return normalizeLower(sourceCategory.getTitle());
        }
        return normalizeLower(bookmark.getCategoryTitle());
    }

    private void preloadAllCategoryContextAsync() {
        if (!isAllCategoryView()) {
            return;
        }
        new Thread(() -> {
            try {
                List<Category> categories = CategoryService.getInstance().getCached(account);

                categoryTitleByCategoryId.set(categories.stream()
                        .filter(c -> c != null && c.getCategoryId() != null && c.getTitle() != null)
                        .collect(Collectors.toMap(
                                Category::getCategoryId,
                                Category::getTitle,
                                (left, right) -> left)));

                categoryTitleByNormalizedTitle.set(categories.stream()
                        .filter(c -> c != null && !isBlank(c.getTitle()))
                        .collect(Collectors.toMap(
                                c -> normalizeCategoryKey(c.getTitle()),
                                Category::getTitle,
                                (left, right) -> left)));

                if (isM3uAccount()) {
                    m3uAllSourceContextByChannelKey.set(loadM3uAllSourceContextMap(categories));
                }
            } catch (Exception _) {
                // Ignore malformed category context and keep default empty mappings.
                categoryTitleByCategoryId.set(Map.of());
                categoryTitleByNormalizedTitle.set(Map.of());
                m3uAllSourceContextByChannelKey.set(Map.of());
            } finally {
                runLater(this::refreshBookmarkStatesAsync);
            }
        }, "bookmark-all-context-loader").start();
    }

    private String normalizeCategoryKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isM3uAccount() {
        return account.getType() == M3U8_LOCAL || account.getType() == M3U8_URL;
    }

    private String channelIdentityKey(Channel channel) {
        if (channel == null) {
            return "";
        }
        String id = channel.getChannelId() == null ? "" : channel.getChannelId().trim();
        String cmd = channel.getCmd() == null ? "" : channel.getCmd().trim();
        String name = channel.getName() == null ? "" : channel.getName().trim().toLowerCase();
        return id + "|" + cmd + "|" + name;
    }

    @SuppressWarnings("java:S135")
    private Map<String, BookmarkContext> loadM3uAllSourceContextMap(List<Category> categories) {
        if (categories == null || categories.isEmpty()) {
            return Map.of();
        }
        try {
            Map<String, BookmarkContext> contextByKey = new java.util.HashMap<>();
            for (Category category : categories) {
                if (category == null || isBlank(category.getDbId()) || isBlank(category.getTitle())) {
                    continue;
                }
                if ("all".equalsIgnoreCase(category.getTitle().trim())) {
                    continue;
                }
                List<Channel> channels = ChannelService.getInstance().getCachedLiveChannelsByDbCategoryId(category.getDbId());
                for (Channel channel : channels) {
                    String key = channelIdentityKey(channel);
                    if (isBlank(key)) {
                        continue;
                    }
                    contextByKey.putIfAbsent(key, new BookmarkContext(category.getDbId(), category.getTitle()));
                }
            }
            return contextByKey;
        } catch (Exception _) {
            return Map.of();
        }
    }

    private BookmarkContext resolveBookmarkContext(Channel channel) {
        String effectiveCategoryId = categoryId;
        String effectiveCategoryTitle = categoryTitle;
        if (isAllCategoryView() && channel != null && isM3uAccount()) {
            BookmarkContext sourceContext = m3uAllSourceContextByChannelKey.get().get(channelIdentityKey(channel));
            if (sourceContext != null) {
                return sourceContext;
            }
        }
        if (isAllCategoryView() && channel != null && !isBlank(channel.getCategoryId())) {
            effectiveCategoryId = channel.getCategoryId();
            String mappedTitle = resolveMappedCategoryTitle(channel.getCategoryId());
            if (!isBlank(mappedTitle)) {
                effectiveCategoryTitle = mappedTitle;
            }
        }
        return new BookmarkContext(effectiveCategoryId, effectiveCategoryTitle);
    }

    private String resolveMappedCategoryTitle(String rawCategoryId) {
        String mappedTitle = categoryTitleByCategoryId.get().get(rawCategoryId);
        if (isBlank(mappedTitle)) {
            mappedTitle = categoryTitleByNormalizedTitle.get().get(normalizeCategoryKey(rawCategoryId));
        }
        if (isBlank(mappedTitle) && isM3uAccount()) {
            mappedTitle = rawCategoryId;
        }
        return mappedTitle;
    }

    private String resolveSeriesCategoryId(Channel channel, BookmarkContext context) {
        String candidate = "";
        if (channel != null && !isBlank(channel.getCategoryId())) {
            candidate = channel.getCategoryId();
        } else if (context != null && !isBlank(context.categoryId)) {
            candidate = context.categoryId;
        } else if (!isBlank(categoryId)) {
            candidate = categoryId;
        }
        return isBlank(candidate) ? "" : candidate;
    }

    private static final class BookmarkContext {
        private final String categoryId;
        private final String categoryTitle;

        private BookmarkContext(String categoryId, String categoryTitle) {
            this.categoryId = categoryId;
            this.categoryTitle = categoryTitle;
        }
    }

    private void addChannelClickHandler() {
        table.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                ChannelItem selected = resolveEnterTargetItem();
                if (selected != null) {
                    playOrShowSeries(selected);
                    event.consume();
                }
            }
        });
        table.getSearchTextField().setOnAction(event -> {
            ChannelItem selected = resolveEnterTargetItem();
            if (selected != null) {
                playOrShowSeries(selected);
            }
        });
        table.setRowFactory(_ -> {
            TableRow<ChannelItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    playOrShowSeries(row.getItem());
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
    }

    private ChannelItem resolveEnterTargetItem() {
        ChannelItem selected = table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }
        ChannelItem focused = table.getFocusModel().getFocusedItem();
        if (focused != null) {
            return focused;
        }
        if (table.getItems() != null && !table.getItems().isEmpty()) {
            return table.getItems().getFirst();
        }
        return null;
    }

    private void playOrShowSeries(ChannelItem item) {
        if (item == null) return;
        if (showCachedEpisodesIfPresent(item)) {
            return;
        }
        AtomicBoolean isCancelled = preparePlaybackLoad();

        if (account.getAction() != series) {
            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            return;
        }
        if (account.getType() == STALKER_PORTAL && !isBlank(item.getCmd())) {
            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            return;
        }
        loadSeriesEpisodesAsync(item, isCancelled);
    }

    private void loadSeriesEpisodesAsync(ChannelItem item, AtomicBoolean isCancelled) {
        getScene().setCursor(Cursor.WAIT);
        Thread loadingThread = new Thread(() -> {
            try {
                EpisodesListUI ui = buildEpisodesListUi(item);
                if (!awaitEpisodesUiReady(item, ui, isCancelled)) {
                    return;
                }
                populateEpisodes(item, isCancelled, ui);
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            } catch (Exception e) {
                runLater(() -> showErrorAlert(I18n.tr("autoErrorLoadingSeries", e.getMessage())));
            } finally {
                runLater(() -> getScene().setCursor(Cursor.DEFAULT));
                currentLoadingThread.compareAndSet(Thread.currentThread(), null);
            }
        });
        currentLoadingThread.set(loadingThread);
        loadingThread.start();
    }

    private EpisodesListUI buildEpisodesListUi(ChannelItem item) {
        return new EpisodesListUI(account, item.getChannelName(), item.getChannelId(), categoryId);
    }

    private boolean awaitEpisodesUiReady(ChannelItem item, EpisodesListUI ui, AtomicBoolean isCancelled) throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(1);
        runLater(() -> {
            if (this.getChildren().size() > 1) {
                this.getChildren().remove(1);
            }
            HBox.setHgrow(ui, Priority.ALWAYS);
            if (embeddedMode || inlineEpisodeNavigationEnabled) {
                showDetailView(ui, item.getChannelName());
            } else {
                this.getChildren().add(ui);
            }
            latch.countDown();
        });
        latch.await();
        return !Thread.currentThread().isInterrupted() && !isCancelled.get();
    }

    private void populateEpisodes(ChannelItem item, AtomicBoolean isCancelled, EpisodesListUI ui) {
        try {
            EpisodeList episodes = SeriesEpisodeService.getInstance()
                    .getEpisodes(account, categoryId, item.getChannelId(), isCancelled::get);
            seriesEpisodesCache.put(seriesEpisodeCacheKey(item), episodes);
            ui.setItems(episodes);
        } finally {
            ui.setLoadingComplete();
        }
    }

    private boolean showCachedEpisodesIfPresent(ChannelItem item) {
        if (account.getAction() != series) {
            return false;
        }
        EpisodeList cachedEpisodes = seriesEpisodesCache.get(seriesEpisodeCacheKey(item));
        if (cachedEpisodes == null) {
            return false;
        }
        showEpisodesListUI(item, cachedEpisodes);
        return true;
    }

    private AtomicBoolean preparePlaybackLoad() {
        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }
        interruptRunningLoad();
        currentRequestCancelled = new AtomicBoolean(false);
        return currentRequestCancelled;
    }

    private void interruptRunningLoad() {
        Thread runningThread = currentLoadingThread.get();
        if (runningThread == null || !runningThread.isAlive()) {
            return;
        }
        getScene().setCursor(Cursor.WAIT);
        runningThread.interrupt();
        try {
            runningThread.join(2000);
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class ChannelBookmarkIdentity {
        private final String channelId;
        private final String channelCmd;
        private final String channelName;
        private final String categoryId;
        private final String categoryTitle;

        private ChannelBookmarkIdentity(String channelId, String channelCmd, String channelName, String categoryId, String categoryTitle) {
            this.channelId = channelId;
            this.channelCmd = channelCmd;
            this.channelName = channelName;
            this.categoryId = categoryId;
            this.categoryTitle = categoryTitle;
        }
    }

    private String seriesEpisodeCacheKey(ChannelItem item) {
        if (item == null) {
            return "";
        }
        String category = categoryId == null ? "" : categoryId.trim();
        String id = item.getChannelId() == null ? "" : item.getChannelId().trim();
        return category + "|" + id;
    }

    private void showEpisodesListUI(ChannelItem item, EpisodeList episodes) {
        runLater(() -> {
            if (this.getChildren().size() > 1) {
                this.getChildren().remove(1);
            }
            EpisodesListUI ui = new EpisodesListUI(account, item.getChannelName(), item.getChannelId(), categoryId);
            HBox.setHgrow(ui, Priority.ALWAYS);
            if (embeddedMode || inlineEpisodeNavigationEnabled) {
                showDetailView(ui, item.getChannelName());
            } else {
                this.getChildren().add(ui);
            }
            ui.setItems(episodes);
            ui.setLoadingComplete();
        });
    }

    private void addRightClickContextMenu(TableRow<ChannelItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        UiI18n.preparePopupControl(rowMenu, row);
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);

        if (account.getAction() == vod) {
            configureVodContextMenu(row, rowMenu);
            return;
        }

        Menu bookmarkMenu = new Menu(I18n.tr("autoBookmark"));
        rowMenu.getItems().add(bookmarkMenu);
        configureBookmarkMenu(row, rowMenu, bookmarkMenu);
        addPlayerItems(row, rowMenu);
        bindRowContextMenu(row, rowMenu);
    }

    private void addPlayerItems(TableRow<ChannelItem> row, ContextMenu rowMenu) {
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.setOnAction(event -> {
                rowMenu.hide();
                play(row.getItem(), option.playerPath());
            });
            rowMenu.getItems().add(playerItem);
        }
    }

    private void bindRowContextMenu(TableRow<ChannelItem> row, ContextMenu rowMenu) {
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty().or(buildSeriesCommandMissingBinding(row)))
                        .then((ContextMenu) null)
                        .otherwise(rowMenu)
        );
    }

    private BooleanBinding buildSeriesCommandMissingBinding(TableRow<ChannelItem> row) {
        return Bindings.createBooleanBinding(
                () -> account.getAction() == series && (row.getItem() == null || isBlank(row.getItem().getCmd())),
                row.itemProperty()
        );
    }

    private void configureVodContextMenu(TableRow<ChannelItem> row, ContextMenu rowMenu) {
        row.setOnContextMenuRequested(event -> {
            populateVodContextMenu(rowMenu, row.getItem());
            if (!rowMenu.getItems().isEmpty()) {
                rowMenu.show(row, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu)
        );
    }

    private void configureBookmarkMenu(TableRow<ChannelItem> row, ContextMenu rowMenu, Menu bookmarkMenu) {
        rowMenu.setOnShowing(event -> {
            bookmarkMenu.getItems().clear();
            ChannelItem item = row.getItem();
            if (item == null) {
                return;
            }
            loadBookmarkMenuItemsAsync(item, bookmarkMenu);
        });
    }

    private void loadBookmarkMenuItemsAsync(ChannelItem item, Menu bookmarkMenu) {
        new Thread(() -> {
            BookmarkContext ctx = resolveBookmarkContext(item.getChannel());
            Bookmark existingBookmark = findMatchingBookmark(item.getChannel(), ctx, loadBookmarksForAccount());
            List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();
            Platform.runLater(() -> populateBookmarkMenuItems(item, bookmarkMenu, categories, existingBookmark));
        }).start();
    }

    private void populateBookmarkMenuItems(ChannelItem item,
                                           Menu bookmarkMenu,
                                           List<BookmarkCategory> categories,
                                           Bookmark existingBookmark) {
        MenuItem allItem = new MenuItem(I18n.tr("autoAll"));
        allItem.setOnAction(e -> saveBookmark(item, null));
        bookmarkMenu.getItems().add(allItem);
        bookmarkMenu.getItems().add(new SeparatorMenuItem());

        for (BookmarkCategory category : categories) {
            MenuItem categoryItem = new MenuItem(category.getName());
            categoryItem.setOnAction(e -> saveBookmark(item, category.getId()));
            bookmarkMenu.getItems().add(categoryItem);
        }

        if (existingBookmark != null) {
            bookmarkMenu.getItems().add(new SeparatorMenuItem());
            bookmarkMenu.getItems().add(buildRemoveBookmarkItem(item, existingBookmark));
        }
    }

    private MenuItem buildRemoveBookmarkItem(ChannelItem item, Bookmark existingBookmark) {
        MenuItem unbookmarkItem = new MenuItem(I18n.tr("autoRemoveBookmark"));
        unbookmarkItem.getStyleClass().add("danger-menu-item");
        unbookmarkItem.setOnAction(e -> removeBookmarkAsync(item, existingBookmark));
        return unbookmarkItem;
    }

    private void removeBookmarkAsync(ChannelItem item, Bookmark existingBookmark) {
        new Thread(() -> {
            BookmarkService.getInstance().remove(existingBookmark.getDbId());
            Platform.runLater(() -> {
                item.setBookmarked(false);
                table.refresh();
                refreshBookmarkStatesAsync();
            });
        }).start();
    }

    private void populateVodContextMenu(ContextMenu rowMenu, ChannelItem item) {
        rowMenu.getItems().clear();
        if (item == null) {
            return;
        }
        for (WatchingNowActionMenu.ActionDescriptor action : WatchingNowActionMenu.buildEpisodeStyleActions(
                item.isBookmarked(),
                PlaybackUIService.getConfiguredPlayerOptions()
        )) {
            switch (action.kind()) {
                case WATCHING_NOW -> {
                    MenuItem watchingNowItem = new MenuItem(I18n.tr("autoWatchingNow"));
                    watchingNowItem.setOnAction(e -> saveVodWatchingNow(item));
                    rowMenu.getItems().add(watchingNowItem);
                }
                case SEPARATOR -> rowMenu.getItems().add(new SeparatorMenuItem());
                case PLAYER -> {
                    MenuItem playerItem = new MenuItem(action.label());
                    playerItem.setOnAction(event -> {
                        rowMenu.hide();
                        play(item, action.playerPath());
                    });
                    rowMenu.getItems().add(playerItem);
                }
                case REMOVE_WATCHING_NOW -> {
                    MenuItem removeWatchingNowItem = new MenuItem(I18n.tr("autoRemoveWatchingNow"));
                    removeWatchingNowItem.getStyleClass().add("danger-menu-item");
                    removeWatchingNowItem.setOnAction(e -> removeVodWatchingNow(item));
                    rowMenu.getItems().add(removeWatchingNowItem);
                }
            }
        }
    }

    private void saveBookmark(ChannelItem item, String bookmarkCategoryId) {
        new Thread(() -> {
            BookmarkContext ctx = resolveBookmarkContext(item.getChannel());
            Bookmark bookmark = new Bookmark(account.getAccountName(), ctx.categoryTitle, item.getChannelId(), item.getChannelName(), item.getCmd(), account.getServerPortalUrl(), ctx.categoryId);
            bookmark.setAccountAction(account.getAction());
            bookmark.setCategoryId(bookmarkCategoryId);

            Category cat = new Category();
            cat.setCategoryId(ctx.categoryId);
            cat.setTitle(ctx.categoryTitle);
            bookmark.setCategoryJson(cat.toJson());

            if (item.getChannel() != null) {
                if (account.getAction() == vod) {
                    bookmark.setVodJson(item.getChannel().toJson());
                } else {
                    bookmark.setChannelJson(item.getChannel().toJson());
                }
            }
            BookmarkService.getInstance().save(bookmark);
            Platform.runLater(() -> {
                item.setBookmarked(true);
                table.refresh();
                refreshBookmarkStatesAsync();
            });
        }).start();
    }

    private void saveVodWatchingNow(ChannelItem item) {
        if (item == null || item.getChannel() == null) {
            return;
        }
        new Thread(() -> {
            BookmarkContext ctx = resolveBookmarkContext(item.getChannel());
            VodWatchStateService.getInstance().save(account, ctx == null ? categoryId : ctx.categoryId, item.getChannel());
            Platform.runLater(() -> {
                item.setBookmarked(true);
                table.refresh();
                refreshBookmarkStatesAsync();
            });
        }).start();
    }

    private void removeVodWatchingNow(ChannelItem item) {
        if (item == null || item.getChannel() == null || isBlank(account.getDbId())) {
            return;
        }
        new Thread(() -> {
            BookmarkContext ctx = resolveBookmarkContext(item.getChannel());
            VodWatchStateService.getInstance().remove(account.getDbId(), ctx == null ? categoryId : ctx.categoryId, item.getChannelId());
            Platform.runLater(() -> {
                item.setBookmarked(false);
                table.refresh();
                refreshBookmarkStatesAsync();
            });
        }).start();
    }

    private void play(ChannelItem item, String playerPath) {
        if (item == null) {
            return;
        }
        Channel channelForPlayback = resolveChannelForPlayback(item);
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(account, channelForPlayback, playerPath)
                .categoryId(categoryId)
                .channelId(item.getChannelId())
                .errorPrefix(I18n.tr("autoErrorPlayingChannelPrefix")));
    }

    private Channel resolveChannelForPlayback(ChannelItem item) {
        if (item == null) {
            return null;
        }
        Channel resolvedChannel = channelList.stream()
                .filter(c -> Objects.equals(c.getChannelId(), item.getChannelId()))
                .findFirst()
                .orElse(null);
        if (resolvedChannel != null) {
            return resolvedChannel;
        }

        Channel fallback = new Channel();
        fallback.setChannelId(item.getChannelId());
        fallback.setName(item.getChannelName());
        fallback.setCmd(item.getCmd());
        fallback.setLogo(item.getLogo());
        if (item.getChannel() != null) {
            fallback.setDrmType(item.getChannel().getDrmType());
            fallback.setDrmLicenseUrl(item.getChannel().getDrmLicenseUrl());
            fallback.setClearKeysJson(item.getChannel().getClearKeysJson());
            fallback.setInputstreamaddon(item.getChannel().getInputstreamaddon());
            fallback.setManifestType(item.getChannel().getManifestType());
        }
        return fallback;
    }


    private String normalizeImageUrl(String imageUrl) {
        if (isBlank(imageUrl)) {
            return "";
        }
        String value = trimWrappedImageUrl(imageUrl.trim().replace("\\/", "/"));
        if (isBlank(value)) {
            return "";
        }
        if (isAbsoluteImageUrl(value) || isInlineImageUrl(value)) {
            return value;
        }
        BaseUriParts base = baseUriParts();
        if (value.startsWith("//")) {
            return base.scheme() + ":" + value;
        }
        if (value.startsWith("/")) {
            if (!isBlank(base.host())) {
                return buildBaseUrl(base) + value;
            }
            return value;
        }
        if (value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) {
            return base.scheme() + "://" + value;
        }
        if (!isBlank(base.host())) {
            return buildBaseUrl(base) + "/" + stripLeadingRelativePrefix(value);
        }
        return localServerOrigin() + "/" + stripLeadingRelativePrefix(value);
    }

    private String trimWrappedImageUrl(String value) {
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            return value.substring(1, value.length() - 1).trim();
        }
        return value;
    }

    private boolean isAbsoluteImageUrl(String value) {
        return value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*");
    }

    private boolean isInlineImageUrl(String value) {
        return value.startsWith("data:") || value.startsWith("blob:") || value.startsWith("file:");
    }

    private BaseUriParts baseUriParts() {
        URI base = resolveBaseUri();
        if (base == null) {
            return new BaseUriParts("https", "", -1);
        }
        return new BaseUriParts(
                isBlank(base.getScheme()) ? "https" : base.getScheme(),
                isBlank(base.getHost()) ? "" : base.getHost(),
                base.getPort()
        );
    }

    private String buildBaseUrl(BaseUriParts base) {
        return base.scheme() + "://" + base.host() + (base.port() > 0 ? ":" + base.port() : "");
    }

    private String stripLeadingRelativePrefix(String value) {
        return value.replaceFirst("^\\./", "");
    }

    private URI resolveBaseUri() {
        List<String> candidates = List.of(account.getServerPortalUrl(), account.getUrl());
        for (String candidate : candidates) {
            if (isBlank(candidate)) continue;
            try {
                URI uri = URI.create(candidate.trim());
                if (!isBlank(uri.getHost())) {
                    return uri;
                }
                if (isBlank(uri.getScheme())) {
                    URI withScheme = URI.create("http://" + candidate.trim());
                    if (!isBlank(withScheme.getHost())) {
                        return withScheme;
                    }
                }
            } catch (Exception _) {
                // Ignore malformed base URIs and continue with the next candidate.
            }
        }
        return null;
    }

    private String localServerOrigin() {
        return ServerUrlUtil.getLocalServerUrl();
    }

    private record BaseUriParts(String scheme, String host, int port) {
    }

    private record LogoUpdate(ChannelItem item, String normalizedLogo, Channel sourceChannel) {
    }

    public static class ChannelItem {

        private final SimpleStringProperty channelName;
        private final SimpleStringProperty channelId;
        private final SimpleStringProperty cmd;
        private final SimpleBooleanProperty bookmarked;
        private final SimpleStringProperty logo;
        private final Channel channel;

        public ChannelItem(SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, boolean isBookmarked, SimpleStringProperty logo, Channel channel) {
            this.channelName = channelName;
            this.channelId = channelId;
            this.cmd = cmd;
            this.bookmarked = new SimpleBooleanProperty(isBookmarked);
            this.logo = logo;
            this.channel = channel;
        }

        public static Callback<ChannelItem, Observable[]> extractor() {
            return item -> new Observable[]{item.bookmarkedProperty()};
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

        public boolean isBookmarked() {
            return bookmarked.get();
        }

        public void setBookmarked(boolean bookmarked) {
            this.bookmarked.set(bookmarked);
        }

        public String getLogo() {
            return logo.get();
        }

        public void setLogo(String logo) {
            this.logo.set(logo);
        }

        public SimpleStringProperty logoProperty() {
            return logo;
        }

        public SimpleBooleanProperty bookmarkedProperty() {
            return bookmarked;
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

        public Channel getChannel() {
            return channel;
        }
    }
}
