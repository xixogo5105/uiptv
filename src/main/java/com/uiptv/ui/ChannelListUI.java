package com.uiptv.ui;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.ImageCacheManager;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.AsyncImageView;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.Observable;
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

import java.net.URI;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.AccountType.*;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class ChannelListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final String categoryId;
    private final SearchableTableView table = new SearchableTableView();
    private final TableColumn<ChannelItem, String> channelName = new TableColumn<>("Channels");
    private final List<Channel> channelList;
    private ObservableList<ChannelItem> channelItems;
    private final Set<String> seenChannelKeys = new HashSet<>();
    private volatile Map<String, String> categoryTitleByCategoryId = Map.of();
    private volatile Map<String, String> categoryTitleByNormalizedTitle = Map.of();
    private volatile Map<String, BookmarkContext> m3uAllSourceContextByChannelKey = Map.of();
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
    private boolean thumbnailListenerRegistered = false;
    private final BookmarkChangeListener bookmarkChangeListener = (revision, updatedEpochMs) -> refreshBookmarkStatesAsync();
    private volatile Thread currentLoadingThread;
    private AtomicBoolean currentRequestCancelled;
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = this::onThumbnailModeChanged;
    private boolean embeddedMode = false;
    private boolean inlineEpisodeNavigationEnabled = false;
    private final VBox listPane = new VBox(5);
    private final VBox detailPane = new VBox(8);
    private Runnable onHome;
    private final HBox detailNavHeader = new HBox(6);
    private final Button detailBackButton = createBackButton();
    private final Label detailTitle = new Label();
    private final VBox detailContent = new VBox();

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
            ImageCacheManager.clearCache("channel");
        }
        initWidgets();
        registerBookmarkListener();
        registerThumbnailModeListener();
        table.setPlaceholder(new Label("Loading channels for '" + categoryTitle + "'..."));
    }

    public void setEmbeddedMode(boolean embeddedMode, Runnable onHome) {
        this.embeddedMode = embeddedMode;
        this.onHome = onHome;
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
            List<ChannelItem> newItems = new ArrayList<>();
            newChannels.forEach(i -> {
                if (i == null) {
                    return;
                }
                String key = buildChannelKey(i);
                if (!seenChannelKeys.add(key)) {
                    return;
                }
                channelList.add(i);
                BookmarkContext ctx = resolveBookmarkContext(i);
                boolean isBookmarked = isChannelBookmarked(i, ctx, accountBookmarks);
                if (account.getAction() == series) {
                    String seriesCategoryId = resolveSeriesCategoryId(i, ctx);
                    boolean inProgress = SeriesWatchStateService.getInstance()
                            .getSeriesLastWatched(account.getDbId(), seriesCategoryId, i.getChannelId()) != null;
                    i.setWatched(inProgress);
                }
                String normalizedLogo = normalizeImageUrl(i.getLogo());
                newItems.add(new ChannelItem(new SimpleStringProperty(i.getName()), new SimpleStringProperty(i.getChannelId()), new SimpleStringProperty(i.getCmd()), isBookmarked, new SimpleStringProperty(normalizedLogo), i));
            });

            runLater(() -> {
                channelItems.addAll(newItems);
                table.setPlaceholder(null);
            });
        }
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
                table.setPlaceholder(new Label("Nothing found for '" + categoryTitle + "'"));
            }
        });
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

        AutoGrowVBox auto = new AutoGrowVBox(5, table.getSearchTextField(), table);
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

    private Button createHomeButton() {
        Button button = new Button();
        button.getStyleClass().add("nav-back-button");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip("Home"));
        SVGPath icon = new SVGPath();
        icon.setContent("M4 10 L12 4 L20 10 V20 H14 V13 H10 V20 H4 Z");
        icon.getStyleClass().add("nav-back-icon");
        button.setGraphic(icon);
        return button;
    }

    private Button createBackButton() {
        Button button = new Button("Back");
        button.setFocusTraversable(false);
        button.setTooltip(new Tooltip("Back"));
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
        if (!embeddedMode && inlineEpisodeNavigationEnabled) {
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
            private final Label drmBadge = new Label("DRM");
            private final Label progressBadge = new Label("IN PROGRESS");
            private final Pane spacer = new Pane();
            private final SVGPath bookmarkIcon = new SVGPath();
            private final AsyncImageView imageView = new AsyncImageView();

            {
                bookmarkIcon.setContent("M3 0 V14 L8 10 L13 14 V0 H3 Z");
                bookmarkIcon.setFill(Color.BLACK);
                drmBadge.getStyleClass().add("drm-badge");
                drmBadge.setVisible(false);
                drmBadge.setManaged(false);
                progressBadge.getStyleClass().add("drm-badge");
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
                imageView.loadImage(channelItem.getLogo(), "channel");
                setGraphic(graphic);
            }
        };
    }

    private TableCell<ChannelItem, String> createPlainTextCell() {
        return new TableCell<>() {

            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final Label drmBadge = new Label("DRM");
            private final Label progressBadge = new Label("IN PROGRESS");
            private final Pane spacer = new Pane();
            private final SVGPath bookmarkIcon = new SVGPath();

            {
                bookmarkIcon.setContent("M3 0 V14 L8 10 L13 14 V0 H3 Z");
                bookmarkIcon.setFill(Color.BLACK);
                drmBadge.getStyleClass().add("drm-badge");
                drmBadge.setVisible(false);
                drmBadge.setManaged(false);
                progressBadge.getStyleClass().add("drm-badge");
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
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                unregisterBookmarkListener();
                if (!embeddedMode && !inlineEpisodeNavigationEnabled) {
                    releaseTransientState();
                }
            } else if (!bookmarkListenerRegistered) {
                BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
                bookmarkListenerRegistered = true;
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
        sceneProperty().addListener((obs, oldScene, newScene) -> {
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
            ImageCacheManager.clearCache("channel");
            channelName.setCellFactory(column -> createThumbnailCell());
        } else {
            channelName.setCellFactory(column -> createPlainTextCell());
        }
        table.refresh();
    }

    private void releaseTransientState() {
        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }
        if (currentLoadingThread != null && currentLoadingThread.isAlive()) {
            currentLoadingThread.interrupt();
        }
        seriesEpisodesCache.clear();
        // Clear channel items and metadata to allow garbage collection
        if (channelItems != null) {
            channelItems.clear();
        }
        seenChannelKeys.clear();
        categoryTitleByCategoryId = Map.of();
        categoryTitleByNormalizedTitle = Map.of();
        m3uAllSourceContextByChannelKey = Map.of();
    }

    private void unregisterBookmarkListener() {
        if (!bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().removeChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = false;
    }

    private void refreshBookmarkStatesAsync() {
        if (channelItems == null || channelItems.isEmpty()) {
            return;
        }
        new Thread(() -> {
            List<Bookmark> accountBookmarks = loadBookmarksForAccount();
            runLater(() -> {
                for (ChannelItem item : channelItems) {
                    boolean isBookmarked = isChannelBookmarked(item.getChannel(), resolveBookmarkContext(item.getChannel()), accountBookmarks);
                    item.setBookmarked(isBookmarked);
                }
                table.refresh();
            });
        }).start();
    }

    private List<Bookmark> loadBookmarksForAccount() {
        return BookmarkService.getInstance().read().stream()
                .filter(b -> account.getAccountName().equals(b.getAccountName()))
                .collect(Collectors.toList());
    }

    private boolean isAllCategoryView() {
        return "all".equalsIgnoreCase(categoryTitle == null ? "" : categoryTitle.trim());
    }

    private boolean isChannelBookmarked(Channel channel, BookmarkContext context, List<Bookmark> bookmarks) {
        return findMatchingBookmark(channel, context, bookmarks) != null;
    }

    private Bookmark findMatchingBookmark(Channel channel, BookmarkContext context, List<Bookmark> bookmarks) {
        if (channel == null || bookmarks == null || bookmarks.isEmpty()) {
            return null;
        }
        String channelId = normalizeExact(channel.getChannelId());
        String channelCmd = normalizeExact(channel.getCmd());
        String channelName = normalizeLower(channel.getName());
        String channelCategoryId = normalizeExact(context == null ? null : context.categoryId);
        String channelCategoryTitle = normalizeLower(context == null ? null : context.categoryTitle);
        boolean allView = isAllCategoryView();

        for (Bookmark bookmark : bookmarks) {
            if (bookmark == null) {
                continue;
            }

            if (!allView) {
                String bookmarkCategoryId = resolveBookmarkSourceCategoryId(bookmark);
                String bookmarkCategoryTitle = resolveBookmarkSourceCategoryTitle(bookmark);
                boolean sameCategoryById = !isBlank(channelCategoryId) && !isBlank(bookmarkCategoryId) && channelCategoryId.equals(bookmarkCategoryId);
                boolean sameCategoryByTitle = !isBlank(channelCategoryTitle) && !isBlank(bookmarkCategoryTitle) && channelCategoryTitle.equals(bookmarkCategoryTitle);
                if (!sameCategoryById && !sameCategoryByTitle) {
                    continue;
                }
            }

            String bookmarkId = normalizeExact(bookmark.getChannelId());
            String bookmarkCmd = normalizeExact(bookmark.getCmd());
            String bookmarkName = normalizeLower(bookmark.getChannelName());

            boolean idMatch = !isBlank(channelId) && !isBlank(bookmarkId) && channelId.equals(bookmarkId);
            boolean cmdMatch = !isBlank(channelCmd) && !isBlank(bookmarkCmd) && channelCmd.equals(bookmarkCmd);
            if (idMatch || cmdMatch) {
                return bookmark;
            }

            boolean bothWithoutStrongIdentity =
                    isBlank(channelId) && isBlank(channelCmd) && isBlank(bookmarkId) && isBlank(bookmarkCmd);
            if (bothWithoutStrongIdentity && !isBlank(channelName) && channelName.equals(bookmarkName)) {
                return bookmark;
            }
        }
        return null;
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

                categoryTitleByCategoryId = categories.stream()
                        .filter(c -> c != null && c.getCategoryId() != null)
                        .collect(Collectors.toMap(
                                c -> c.getCategoryId(),
                                Category::getTitle,
                                (left, right) -> left));

                categoryTitleByNormalizedTitle = categories.stream()
                        .filter(c -> c != null && !isBlank(c.getTitle()))
                        .collect(Collectors.toMap(
                                c -> normalizeCategoryKey(c.getTitle()),
                                Category::getTitle,
                                (left, right) -> left));

                if (isM3uAccount()) {
                    m3uAllSourceContextByChannelKey = loadM3uAllSourceContextMap(categories);
                }
            } catch (Exception ignored) {
                categoryTitleByCategoryId = Map.of();
                categoryTitleByNormalizedTitle = Map.of();
                m3uAllSourceContextByChannelKey = Map.of();
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
        } catch (Exception ignored) {
            return Map.of();
        }
    }

    private BookmarkContext resolveBookmarkContext(Channel channel) {
        String effectiveCategoryId = categoryId;
        String effectiveCategoryTitle = categoryTitle;
        if (isAllCategoryView() && channel != null) {
            if (isM3uAccount()) {
                BookmarkContext sourceContext = m3uAllSourceContextByChannelKey.get(channelIdentityKey(channel));
                if (sourceContext != null) {
                    return sourceContext;
                }
            }
        }
        if (isAllCategoryView() && channel != null && !isBlank(channel.getCategoryId())) {
            effectiveCategoryId = channel.getCategoryId();
            String mappedTitle = categoryTitleByCategoryId.get(channel.getCategoryId());
            if (isBlank(mappedTitle)) {
                mappedTitle = categoryTitleByNormalizedTitle.get(normalizeCategoryKey(channel.getCategoryId()));
            }
            if (isBlank(mappedTitle) && isM3uAccount()) {
                mappedTitle = channel.getCategoryId();
            }
            if (!isBlank(mappedTitle)) {
                effectiveCategoryTitle = mappedTitle;
            }
        }
        return new BookmarkContext(effectiveCategoryId, effectiveCategoryTitle);
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
                    PlayOrShowSeries(selected);
                    event.consume();
                }
            }
        });
        table.getSearchTextField().setOnAction(event -> {
            ChannelItem selected = resolveEnterTargetItem();
            if (selected != null) {
                PlayOrShowSeries(selected);
            }
        });
        table.setRowFactory(tv -> {
            TableRow<ChannelItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    PlayOrShowSeries(row.getItem());
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
    }

    private ChannelItem resolveEnterTargetItem() {
        ChannelItem selected = (ChannelItem) table.getSelectionModel().getSelectedItem();
        if (selected != null) {
            return selected;
        }
        ChannelItem focused = (ChannelItem) table.getFocusModel().getFocusedItem();
        if (focused != null) {
            return focused;
        }
        if (table.getItems() != null && !table.getItems().isEmpty()) {
            return (ChannelItem) table.getItems().get(0);
        }
        return null;
    }

    private void PlayOrShowSeries(ChannelItem item) {
        if (item == null) return;
        if (account.getAction() == series) {
            EpisodeList cachedEpisodes = seriesEpisodesCache.get(seriesEpisodeCacheKey(item));
            if (cachedEpisodes != null) {
                showEpisodesListUI(item, cachedEpisodes);
                return;
            }
        }

        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }

        if (currentLoadingThread != null && currentLoadingThread.isAlive()) {
            getScene().setCursor(Cursor.WAIT);
            currentLoadingThread.interrupt();
            try {
                currentLoadingThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        currentRequestCancelled = new AtomicBoolean(false);
        AtomicBoolean isCancelled = currentRequestCancelled;

        if (account.getAction() == series) {
            getScene().setCursor(Cursor.WAIT);
            currentLoadingThread = new Thread(() -> {
                try {
                    if (account.getType() == XTREME_API) {
                        final EpisodesListUI[] episodesListUIHolder = new EpisodesListUI[1];
                        CountDownLatch latch = new CountDownLatch(1);

                        runLater(() -> {
                            if (this.getChildren().size() > 1) {
                                this.getChildren().remove(1);
                            }
                            EpisodesListUI ui = new EpisodesListUI(account, item.getChannelName(), item.getChannelId(), categoryId);
                            episodesListUIHolder[0] = ui;
                            HBox.setHgrow(ui, Priority.ALWAYS);
                            if (embeddedMode || inlineEpisodeNavigationEnabled) {
                                showDetailView(ui, item.getChannelName());
                            } else {
                                this.getChildren().add(ui);
                            }
                            latch.countDown();
                        });

                        latch.await();
                        if (Thread.currentThread().isInterrupted() || isCancelled.get()) return;
                        try {
                            EpisodeList episodes = SeriesEpisodeService.getInstance()
                                    .getEpisodes(account, categoryId, item.getChannelId(), isCancelled::get);
                            seriesEpisodesCache.put(seriesEpisodeCacheKey(item), episodes);
                            episodesListUIHolder[0].setItems(episodes);
                        } finally {
                            episodesListUIHolder[0].setLoadingComplete();
                        }
                    } else if (account.getType() == STALKER_PORTAL) {
                        if (isBlank(item.getCmd())) {
                            final EpisodesListUI[] episodesListUIHolder = new EpisodesListUI[1];
                            CountDownLatch latch = new CountDownLatch(1);
                            runLater(() -> {
                                if (this.getChildren().size() > 1) {
                                    this.getChildren().remove(1);
                                }
                                EpisodesListUI ui = new EpisodesListUI(account, item.getChannelName(), item.getChannelId(), categoryId);
                                episodesListUIHolder[0] = ui;
                                HBox.setHgrow(ui, Priority.ALWAYS);
                                if (embeddedMode || inlineEpisodeNavigationEnabled) {
                                    showDetailView(ui, item.getChannelName());
                                } else {
                                    this.getChildren().add(ui);
                                }
                                latch.countDown();
                            });

                            latch.await();
                            if (Thread.currentThread().isInterrupted() || isCancelled.get()) return;

                            try {
                                EpisodeList episodeList = SeriesEpisodeService.getInstance()
                                        .getEpisodes(account, categoryId, item.getChannelId(), isCancelled::get);
                                seriesEpisodesCache.put(seriesEpisodeCacheKey(item), episodeList);
                                episodesListUIHolder[0].setItems(episodeList);
                            } finally {
                                episodesListUIHolder[0].setLoadingComplete();
                            }
                        } else {
                            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    runLater(() -> showErrorAlert("Error loading series: " + e.getMessage()));
                } finally {
                    runLater(() -> getScene().setCursor(Cursor.DEFAULT));
                }
            });
            currentLoadingThread.start();
        } else {
            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath());
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
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        Menu bookmarkMenu = new Menu("Bookmark");
        rowMenu.getItems().add(bookmarkMenu);

        rowMenu.setOnShowing(event -> {
            bookmarkMenu.getItems().clear();
            ChannelItem item = row.getItem();
            if (item == null) return;

            new Thread(() -> {
                BookmarkContext ctx = resolveBookmarkContext(item.getChannel());
                Bookmark existingBookmark = findMatchingBookmark(item.getChannel(), ctx, loadBookmarksForAccount());
                List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();

                Platform.runLater(() -> {
                    MenuItem allItem = new MenuItem("All");
                    allItem.setOnAction(e -> {
                        saveBookmark(item, null);
                    });
                    bookmarkMenu.getItems().add(allItem);
                    bookmarkMenu.getItems().add(new SeparatorMenuItem());

                    for (BookmarkCategory category : categories) {
                        MenuItem categoryItem = new MenuItem(category.getName());
                        categoryItem.setOnAction(e -> {
                            saveBookmark(item, category.getId());
                        });
                        bookmarkMenu.getItems().add(categoryItem);
                    }

                    if (existingBookmark != null) {
                        bookmarkMenu.getItems().add(new SeparatorMenuItem());
                        MenuItem unbookmarkItem = new MenuItem("Remove Bookmark");
                        unbookmarkItem.getStyleClass().add("danger-menu-item");
                        unbookmarkItem.setOnAction(e -> {
                            new Thread(() -> {
                                BookmarkService.getInstance().remove(existingBookmark.getDbId());
                                Platform.runLater(() -> {
                                    item.setBookmarked(false);
                                    table.refresh();
                                    refreshBookmarkStatesAsync();
                                });
                            }).start();
                        });
                        bookmarkMenu.getItems().add(unbookmarkItem);
                    }
                });
            }).start();
        });

        MenuItem playerEmbeddedItem = new MenuItem("Embedded Player");
        playerEmbeddedItem.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), "embedded");
        });
        MenuItem player1Item = new MenuItem("Player 1");
        player1Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath1());
        });
        MenuItem player2Item = new MenuItem("Player 2");
        player2Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath2());
        });
        MenuItem player3Item = new MenuItem("Player 3");
        player3Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath3());
        });

        rowMenu.getItems().addAll(playerEmbeddedItem, player1Item, player2Item, player3Item);

        row.contextMenuProperty().bind(
                Bindings.when(
                                row.emptyProperty().or(
                                        Bindings.createBooleanBinding(() ->
                                                        account.getAction() == series && (row.getItem() == null || isBlank(row.getItem().getCmd())),
                                                row.itemProperty()
                                        )
                                )
                        )
                        .then((ContextMenu) null)
                        .otherwise(rowMenu)
        );
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

    private void play(ChannelItem item, String playerPath) {
        if (item == null) {
            return;
        }
        Channel channelForPlayback = resolveChannelForPlayback(item);
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(account, channelForPlayback, playerPath)
                .categoryId(categoryId)
                .channelId(item.getChannelId())
                .errorPrefix("Error playing channel: "));
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
        String value = imageUrl.trim().replace("\\/", "/");
        if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
            value = value.substring(1, value.length() - 1).trim();
        }
        if (isBlank(value)) {
            return "";
        }
        if (value.matches("^[a-zA-Z][a-zA-Z0-9+.-]*://.*")) {
            return value;
        }
        if (value.startsWith("data:") || value.startsWith("blob:") || value.startsWith("file:")) {
            return value;
        }
        URI base = resolveBaseUri();
        String scheme = "https";
        String host = "";
        int port = -1;
        if (base != null) {
            if (!isBlank(base.getScheme())) scheme = base.getScheme();
            if (!isBlank(base.getHost())) host = base.getHost();
            port = base.getPort();
        }
        if (value.startsWith("//")) {
            return scheme + ":" + value;
        }
        if (value.startsWith("/")) {
            if (!isBlank(host)) {
                return scheme + "://" + host + (port > 0 ? ":" + port : "") + value;
            }
            return value;
        }
        if (value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) {
            return scheme + "://" + value;
        }
        if (!isBlank(host)) {
            String normalized = value.startsWith("./") ? value.substring(2) : value;
            return scheme + "://" + host + (port > 0 ? ":" + port : "") + "/" + normalized;
        }
        return localServerOrigin() + "/" + value.replaceFirst("^\\./", "");
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
            } catch (Exception ignored) {
            }
        }
        return null;
    }

    private String localServerOrigin() {
        return ServerUrlUtil.getLocalServerUrl();
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
