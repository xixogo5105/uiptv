package com.uiptv.ui;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.AsyncImageView;
import com.uiptv.widget.BookmarkCard;
import com.uiptv.widget.LoadingStateView;
import com.uiptv.widget.PlayMenuButton;
import com.uiptv.widget.ResponsiveCardGrid;
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
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.input.ContextMenuEvent;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
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

public class ChannelListUI extends HBox implements SearchTarget {
    private static final String IMAGE_CACHE_KEY_CHANNEL = "channel";
    private static final String DRM_BADGE_STYLE_CLASS = "drm-badge";

    private final AccountMediaContext mediaContext;
    private final Account account;
    private final Account.AccountAction listAction;
    private final String categoryTitle;
    private final String categoryId;
    private final SearchableTableView<ChannelItem> table = new SearchableTableView<>();
    private final ResponsiveCardGrid<ChannelItem> channelGrid = new ResponsiveCardGrid<>(this::createChannelCard);
    private final ScrollPane channelGridScroll = new ScrollPane();
    private final TableColumn<ChannelItem, String> channelName = new TableColumn<>(I18n.tr("autoChannels"));
    private final List<Channel> channelList;
    private ObservableList<ChannelItem> channelItems;
    private final Set<String> seenChannelKeys = new HashSet<>();
    private final Map<String, ChannelItem> channelItemByKey = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicReference<Map<String, String>> categoryTitleByCategoryId = new AtomicReference<>(Map.of());
    private final AtomicReference<Map<String, String>> categoryTitleByNormalizedTitle = new AtomicReference<>(Map.of());
    private final AtomicBoolean itemsLoaded = new AtomicBoolean(false);
    private final AtomicBoolean disposed = new AtomicBoolean(false);
    private final WatchingNowVodResolver vodMetadataResolver = new WatchingNowVodResolver();
    private static final String LOG_ACCOUNT = " account=";
    private static final String LOG_CHANNEL_ID = " channelId=";
    private static final String LOG_NAME = " name=";
    private static final int MAX_SERIES_EPISODE_CACHE_ENTRIES = Integer.getInteger("uiptv.series.cache.maxEntries", 48);
    private static final int CHANNEL_UI_BATCH_SIZE = Integer.getInteger("uiptv.channel.uiBatchSize", 120);
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
    private boolean thumbnailsEnabled = ThumbnailAwareUI.areThumbnailsEnabled();
    private final BookmarkChangeListener bookmarkChangeListener = (revision, updatedEpochMs) -> refreshBookmarkStatesAsync();
    private final VodWatchStateChangeListener vodWatchStateChangeListener = (accountId, vodId) -> refreshBookmarkStatesAsync();
    private final AtomicReference<Thread> currentLoadingThread = new AtomicReference<>();
    private AtomicBoolean currentRequestCancelled;
    private final ThumbnailAwareUI.ThumbnailModeListener thumbnailModeListener = this::onThumbnailModeChanged;
    private boolean embeddedMode = false;
    private boolean inlineEpisodeNavigationEnabled = false;
    private boolean mediaDrawerMode = false;
    private final VBox listPane = new VBox(5);
    private final VBox searchBox = new VBox(5);
    private final VBox detailPane = new VBox(8);
    private final VBox detailHeader = new VBox(4);
    private final HBox detailNavHeader = new HBox(6);
    private final Button detailBackButton = createBackButton();
    private final Label detailTitle = new Label();
    private final VBox detailContent = new VBox();
    private final ProgressBar loadingProgress = new ProgressBar(0);
    private final Label loadingProgressTitle = new Label();
    private final Label loadingProgressValue = new Label();
    private final HBox loadingProgressHeader = new HBox(8, loadingProgressTitle, loadingProgressValue);
    private final VBox loadingProgressBox = new VBox(6, loadingProgressHeader, loadingProgress);
    private PauseTransition loadingProgressHideTimer;

    public ChannelListUI(Account account, String categoryTitle, String categoryId, Account.AccountAction listAction) {
        this(AccountMediaContext.from(account, listAction), categoryTitle, categoryId, listAction);
    }

    public ChannelListUI(AccountMediaContext mediaContext, String categoryTitle, String categoryId, Account.AccountAction listAction) {
        this.categoryId = categoryId;
        this.channelList = new ArrayList<>();
        this.listAction = listAction == null ? Account.AccountAction.itv : listAction;
        this.mediaContext = mediaContext == null
                ? new AccountMediaContext(null, this.listAction)
                : mediaContext.withAction(this.listAction);
        this.account = this.mediaContext.toAccount();
        this.categoryTitle = categoryTitle;
        preloadAllCategoryContextAsync();
        initWidgets();
        registerSceneLifecycleListener();
        table.setPlaceholder(new Label(I18n.tr("autoLoadingChannelsFor", categoryTitle)));
    }

    public void setEmbeddedMode(boolean embeddedMode) {
        this.embeddedMode = embeddedMode;
        applyListContentMode();
        applyChannelGridSizing();
        if (embeddedMode) {
            showListView();
        }
    }

    public void setInlineEpisodeNavigationEnabled(boolean enabled) {
        this.inlineEpisodeNavigationEnabled = enabled;
        applyListContentMode();
        if (enabled) {
            showListView();
        }
    }

    public void setMediaDrawerMode(boolean mediaDrawerMode) {
        if (this.mediaDrawerMode == mediaDrawerMode) {
            return;
        }
        this.mediaDrawerMode = mediaDrawerMode;
        updateStyleClass(this, "account-channel-drawer-mode", mediaDrawerMode);
        updateStyleClass(channelGrid, "account-channel-card-grid-drawer", mediaDrawerMode);
        applyMediaDrawerModeToActiveDetail();
        applyChannelGridSizing();
        channelGrid.refresh();
    }

    @Override
    public void setSearchQuery(String searchText) {
        String value = searchText == null ? "" : searchText;
        TextField searchField = table.getSearchTextField();
        if (!Objects.equals(searchField.getText(), value)) {
            searchField.setText(value);
        }
    }

    public void addItems(List<Channel> newChannels) {
        if (disposed.get() || newChannels == null || newChannels.isEmpty()) {
            return;
        }
        itemsLoaded.set(true);
        if (Platform.isFxApplicationThread() && newChannels.size() > CHANNEL_UI_BATCH_SIZE) {
            List<Channel> channels = new ArrayList<>(newChannels);
            Thread worker = new Thread(() -> addItemsIncrementally(channels), "uiptv-channel-card-batcher");
            worker.setDaemon(true);
            worker.start();
            return;
        }
        addItemsIncrementally(newChannels);
    }

    private void addItemsIncrementally(List<Channel> newChannels) {
        List<Bookmark> accountBookmarks = loadBookmarksForAccount();
        Set<String> savedVodKeys = loadVodWatchStateKeys();
        Map<String, SeriesWatchState> seriesWatchStates = loadSeriesWatchStates();
        List<ChannelItem> pendingItems = new ArrayList<>(CHANNEL_UI_BATCH_SIZE);
        List<LogoUpdate> pendingLogoUpdates = new ArrayList<>();
        for (Channel channel : newChannels) {
            if (disposed.get()) {
                return;
            }
            processIncomingChannel(channel, accountBookmarks, savedVodKeys, seriesWatchStates, pendingItems, pendingLogoUpdates);
            if (pendingItems.size() >= CHANNEL_UI_BATCH_SIZE || pendingLogoUpdates.size() >= CHANNEL_UI_BATCH_SIZE) {
                publishChannelChanges(pendingItems, pendingLogoUpdates);
            }
        }
        if (!disposed.get()) {
            publishChannelChanges(pendingItems, pendingLogoUpdates);
        }
    }

    private void publishChannelChanges(List<ChannelItem> pendingItems, List<LogoUpdate> pendingLogoUpdates) {
        if ((pendingItems == null || pendingItems.isEmpty())
                && (pendingLogoUpdates == null || pendingLogoUpdates.isEmpty())) {
            return;
        }
        List<ChannelItem> itemBatch = pendingItems == null || pendingItems.isEmpty()
                ? List.of()
                : new ArrayList<>(pendingItems);
        List<LogoUpdate> logoBatch = pendingLogoUpdates == null || pendingLogoUpdates.isEmpty()
                ? List.of()
                : new ArrayList<>(pendingLogoUpdates);
        if (pendingItems != null) {
            pendingItems.clear();
        }
        if (pendingLogoUpdates != null) {
            pendingLogoUpdates.clear();
        }
        runLater(() -> {
            if (disposed.get()) {
                return;
            }
            if (!itemBatch.isEmpty()) {
                channelItems.addAll(itemBatch);
                table.setPlaceholder(null);
                channelGrid.setPlaceholderText("");
            }
            if (!logoBatch.isEmpty()) {
                for (LogoUpdate update : logoBatch) {
                    update.item().setLogo(update.normalizedLogo());
                    update.item().getChannel().setLogo(update.sourceChannel().getLogo());
                }
                refreshChannelViews();
            }
        });
    }

    private void processIncomingChannel(Channel channel,
                                        List<Bookmark> accountBookmarks,
                                        Set<String> savedVodKeys,
                                        Map<String, SeriesWatchState> seriesWatchStates,
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
        updateSeriesProgressIfNeeded(channel, seriesWatchStates);
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

    private void updateSeriesProgressIfNeeded(Channel channel, Map<String, SeriesWatchState> seriesWatchStates) {
        if (listAction != series || channel == null || seriesWatchStates == null || seriesWatchStates.isEmpty()) {
            return;
        }
        channel.setWatched(seriesWatchStates.containsKey(normalizeSeriesWatchKey(channel.getChannelId())));
    }

    private ChannelItem buildChannelItem(Channel channel,
                                         String normalizedLogo,
                                         BookmarkContext context,
                                         List<Bookmark> accountBookmarks,
                                         Set<String> savedVodKeys) {
        boolean isBookmarked = listAction == vod
                ? isVodSaved(channel, context, savedVodKeys)
                : isChannelBookmarked(channel, context, accountBookmarks);
        ChannelItem item = new ChannelItem(
                new SimpleStringProperty(channel.getName()),
                new SimpleStringProperty(channel.getChannelId()),
                new SimpleStringProperty(channel.getCmd()),
                isBookmarked,
                new SimpleStringProperty(normalizedLogo),
                channel
        );
        if (account.getType() == STALKER_PORTAL && channel.getCensored() == 1) {
            com.uiptv.util.AppLog.addInfoLog(ChannelListUI.class,
                    "[ParentalLock] censoredChannelLoaded"
                            + LOG_ACCOUNT + account.getAccountName()
                            + " action=" + listAction
                            + " categoryTitle=" + categoryTitle
                            + LOG_CHANNEL_ID + channel.getChannelId()
                            + LOG_NAME + channel.getName()
                            + " censored=" + channel.getCensored());
        }
        return item;
    }

    private String buildChannelKey(Channel channel) {
        String id = channel.getChannelId() == null ? "" : channel.getChannelId().trim();
        String cmd = channel.getCmd() == null ? "" : channel.getCmd().trim();
        String name = channel.getName() == null ? "" : channel.getName().trim().toLowerCase();
        return id + "|" + cmd + "|" + name;
    }

    public void setLoadingComplete() {
        if (disposed.get()) {
            return;
        }
        runLater(() -> {
            if (disposed.get()) {
                return;
            }
            if (!itemsLoaded.get()) {
                String emptyText = I18n.tr("autoNothingFoundFor", categoryTitle);
                table.setPlaceholder(new Label(emptyText));
                channelGrid.setPlaceholderText(emptyText);
            }
            finalizeLoadingProgress();
        });
    }

    public void startLoadingProgressIfNeeded() {
        if (disposed.get() || (listAction != vod && listAction != series)) {
            return;
        }
        runLater(() -> {
            if (disposed.get()) {
                return;
            }
            loadingProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
            updateLoadingProgressValue(ProgressIndicator.INDETERMINATE_PROGRESS);
            showLoadingProgress();
        });
    }

    public void updateLoadingProgress(int fetchedItems, int totalItems, int pageNumber, int pageCount) {
        if (disposed.get() || (listAction != vod && listAction != series)) {
            return;
        }
        runLater(() -> {
            if (disposed.get()) {
                return;
            }
            showLoadingProgress();
            if (totalItems > 0) {
                double progress = Math.min(1.0, (double) fetchedItems / (double) totalItems);
                loadingProgress.setProgress(progress);
                updateLoadingProgressValue(progress);
            } else if (pageCount > 0) {
                double progress = Math.min(1.0, (double) pageNumber / (double) pageCount);
                loadingProgress.setProgress(progress);
                updateLoadingProgressValue(progress);
            } else {
                loadingProgress.setProgress(ProgressIndicator.INDETERMINATE_PROGRESS);
                updateLoadingProgressValue(ProgressIndicator.INDETERMINATE_PROGRESS);
            }
        });
    }

    private void configureLoadingProgressBar() {
        loadingProgressBox.getStyleClass().add("account-loading-progress-card");
        loadingProgressHeader.getStyleClass().add("account-loading-progress-header");
        loadingProgressTitle.getStyleClass().add("account-loading-progress-title");
        loadingProgressValue.getStyleClass().add("account-loading-progress-value");
        loadingProgress.getStyleClass().add("account-loading-progress-bar");
        loadingProgressTitle.setText(I18n.tr("autoLoadingChannelsFor", categoryTitle));
        loadingProgressTitle.setWrapText(true);
        loadingProgressTitle.setMinWidth(0);
        loadingProgressTitle.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(loadingProgressTitle, Priority.ALWAYS);
        loadingProgressHeader.setAlignment(Pos.CENTER_LEFT);
        loadingProgressHeader.setMaxWidth(Double.MAX_VALUE);
        loadingProgress.setMaxWidth(Double.MAX_VALUE);
        loadingProgressBox.setFillWidth(true);
        loadingProgressBox.setMaxWidth(Double.MAX_VALUE);
        loadingProgressBox.setVisible(false);
        loadingProgressBox.setManaged(false);
    }

    private void showLoadingProgress() {
        cancelLoadingProgressHide();
        loadingProgressBox.setVisible(true);
        loadingProgressBox.setManaged(true);
        loadingProgressBox.getStyleClass().remove("complete");
    }

    private void finalizeLoadingProgress() {
        if (listAction != vod && listAction != series) {
            return;
        }
        if (!loadingProgressBox.isVisible()) {
            return;
        }
        loadingProgress.setProgress(1.0);
        updateLoadingProgressValue(1.0);
        if (!loadingProgressBox.getStyleClass().contains("complete")) {
            loadingProgressBox.getStyleClass().add("complete");
        }
        scheduleLoadingProgressHide();
    }

    private void updateLoadingProgressValue(double progress) {
        if (progress < 0) {
            loadingProgressValue.setText("");
            loadingProgressValue.setVisible(false);
            loadingProgressValue.setManaged(false);
            return;
        }
        int percent = (int) Math.round(Math.min(1.0, progress) * 100);
        loadingProgressValue.setText(percent + "%");
        loadingProgressValue.setVisible(true);
        loadingProgressValue.setManaged(true);
    }

    private void scheduleLoadingProgressHide() {
        cancelLoadingProgressHide();
        loadingProgressHideTimer = new PauseTransition(Duration.seconds(5));
        loadingProgressHideTimer.setOnFinished(event -> {
            loadingProgressBox.setVisible(false);
            loadingProgressBox.setManaged(false);
            loadingProgressBox.getStyleClass().remove("complete");
            updateLoadingProgressValue(ProgressIndicator.INDETERMINATE_PROGRESS);
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
        setMinWidth(0);
        setMaxWidth(Double.MAX_VALUE);
        setMaxHeight(Double.MAX_VALUE);
        setMinHeight(0);
        table.setEditable(true);
        table.getSelectionModel().setSelectionMode(SelectionMode.MULTIPLE);
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
        setupChannelGrid();
        setupChannelGridScroll();

        applyThumbnailMode(ThumbnailAwareUI.areThumbnailsEnabled());

        channelName.setSortType(TableColumn.SortType.ASCENDING);

        configureLoadingProgressBar();
        searchBox.getChildren().setAll(loadingProgressBox, table.getSearchTextField());
        VBox.setVgrow(searchBox, Priority.NEVER);
        VBox.setVgrow(table, Priority.ALWAYS);
        listPane.setMaxHeight(Double.MAX_VALUE);
        listPane.setMinHeight(0);
        listPane.setMinWidth(0);
        listPane.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(listPane, Priority.ALWAYS);
        HBox.setHgrow(listPane, Priority.ALWAYS);
        initDetailPane();
        applyListContentMode();
        showListView();
        getChildren().setAll(listPane);
        addChannelClickHandler();
    }

    private void setupChannelGrid() {
        channelGrid.getStyleClass().add("account-channel-card-grid");
        if (isMediaCatalogMode()) {
            channelGrid.getStyleClass().add(listAction == vod ? "watching-now-vod-grid" : "watching-now-series-grid");
        } else {
            channelGrid.getStyleClass().add("bookmark-card-grid");
        }
        applyChannelGridSizing();
        channelGrid.setItems(table.getItems());
        channelGrid.setPlaceholderNode(new LoadingStateView(I18n.tr("autoLoadingChannelsFor", categoryTitle)));
        channelGrid.setOnItemActivated(this::playOrShowSeries);
        channelGrid.setContextMenuFactory((item, selectedItems, owner) -> createChannelContextMenu(item, selectedItems, owner));
        channelGrid.setOnKeyPressed(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                playOrShowSeries(channelGrid.getFocusedItem());
                event.consume();
            }
        });
    }

    private void applyChannelGridSizing() {
        if (channelGrid == null) {
            return;
        }
        channelGrid.setSingleColumn(mediaDrawerMode || !thumbnailsEnabled);
        if (!thumbnailsEnabled) {
            channelGrid.setCardMinHeight(42);
            channelGrid.setCardWidthRange(240, 760);
            channelGrid.setGaps(16, 6);
            channelGrid.setActivateOnSingleClick(false);
            return;
        }
        channelGrid.setCardMinHeight(76);
        if (mediaDrawerMode) {
            channelGrid.setCardWidthRange(260, 520);
            channelGrid.setGaps(7, 7);
            channelGrid.setActivateOnSingleClick(false);
            return;
        }
        if (isMediaCatalogMode()) {
            channelGrid.setCardWidthRange(embeddedMode ? 360 : 520, 760);
            channelGrid.setGaps(18, 16);
            channelGrid.setActivateOnSingleClick(false);
            return;
        }
        channelGrid.setCardWidthRange(255, 345);
        channelGrid.setGaps(16, 14);
        channelGrid.setActivateOnSingleClick(false);
    }

    private boolean isDirectPlaybackSeriesItem(ChannelItem item) {
        return account != null
                && account.getType() == STALKER_PORTAL
                && item != null
                && !isBlank(item.getCmd());
    }

    private void setupChannelGridScroll() {
        channelGridScroll.getStyleClass().addAll("account-channel-card-scroll", "transparent-scroll-pane");
        channelGridScroll.setContent(channelGrid);
        channelGridScroll.setFitToWidth(true);
        channelGridScroll.setPannable(true);
        channelGridScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        channelGridScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        channelGridScroll.setFocusTraversable(false);
        channelGridScroll.setMinSize(0, 0);
        channelGridScroll.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        VBox.setVgrow(channelGridScroll, Priority.ALWAYS);
    }

    private void applyListContentMode() {
        if (searchBox.getChildren().isEmpty()) {
            return;
        }
        boolean showLocalSearch = !embeddedMode && !inlineEpisodeNavigationEnabled;
        table.getSearchTextField().setVisible(showLocalSearch);
        table.getSearchTextField().setManaged(showLocalSearch);
        Node content = embeddedMode ? channelGridScroll : table;
        if (content instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        listPane.getChildren().setAll(searchBox, content);
        VBox.setVgrow(content, Priority.ALWAYS);
    }

    private void initDetailPane() {
        detailBackButton.setOnAction(event -> showListView());
        detailNavHeader.setAlignment(Pos.CENTER_LEFT);
        detailNavHeader.setMaxWidth(Double.MAX_VALUE);
        detailHeader.setMaxWidth(Double.MAX_VALUE);
        detailContent.setSpacing(5);
        detailContent.setPadding(new javafx.geometry.Insets(5, 0, 0, 0));
        detailContent.setFillWidth(true);
        detailContent.setMinWidth(0);
        detailContent.setMaxWidth(Double.MAX_VALUE);
        detailContent.setMinHeight(0);
        detailContent.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(detailContent, Priority.ALWAYS);
        detailPane.setMinWidth(0);
        detailPane.setMaxWidth(Double.MAX_VALUE);
        detailPane.setMinHeight(0);
        detailPane.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(detailPane, Priority.ALWAYS);
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
        if (ui instanceof EpisodesListUI episodesListUI) {
            episodesListUI.setMediaDrawerMode(mediaDrawerMode);
        }
        detailTitle.setText(title == null ? "" : title);
        boolean plainEpisodeHeader = configureDetailHeader(ui);
        detailContent.getChildren().setAll(ui);
        if (ui instanceof Region region) {
            region.setMinWidth(0);
            region.setMaxWidth(Double.MAX_VALUE);
            region.setMinHeight(0);
            region.setMaxHeight(Double.MAX_VALUE);
        }
        VBox.setVgrow(ui, Priority.ALWAYS);
        detailPane.setMaxHeight(Double.MAX_VALUE);
        detailPane.setMinHeight(0);
        if (inlineEpisodeNavigationEnabled) {
            detailPane.getChildren().setAll(plainEpisodeHeader ? detailHeader : detailNavHeader, detailContent);
        } else {
            detailPane.getChildren().setAll(detailContent);
        }
        getChildren().setAll(detailPane);
    }

    private void applyMediaDrawerModeToActiveDetail() {
        if (detailContent.getChildren().isEmpty()) {
            return;
        }
        Node content = detailContent.getChildren().getFirst();
        if (content instanceof EpisodesListUI episodesListUI) {
            episodesListUI.setMediaDrawerMode(mediaDrawerMode);
        }
    }

    private boolean configureDetailHeader(Node ui) {
        EpisodeDetailHeaderUI.clearPlainHeader(detailHeader);
        detailNavHeader.getChildren().clear();

        if (shouldUsePlainEpisodeHeader(ui) && ui instanceof EpisodesListUI episodesListUI) {
            episodesListUI.useExternalSeriesTitle();
            EpisodeDetailHeaderUI.configurePlainHeader(
                    detailHeader,
                    detailBackButton,
                    detailTitle,
                    episodesListUI.getBingeWatchButton(),
                    episodesListUI.getReloadFromServerButton()
            );
            return true;
        }
        if (ui instanceof EpisodesListUI episodesListUI) {
            EpisodeDetailHeaderUI.configureBackTitleHeader(
                    detailNavHeader,
                    detailBackButton,
                    episodesListUI.getReloadFromServerButton(),
                    detailTitle
            );
            return false;
        }

        EpisodeDetailHeaderUI.configureBackTitleHeader(detailNavHeader, detailBackButton, detailTitle);
        return false;
    }

    private boolean shouldUsePlainEpisodeHeader(Node ui) {
        return ui instanceof EpisodesListUI episodesListUI && episodesListUI.isPlainMode();
    }

    private Region createChannelCard(ChannelItem item) {
        if (!thumbnailsEnabled) {
            return createPlainTextChannelCard(item);
        }
        if (mediaDrawerMode) {
            return createDrawerChannelRow(item);
        }
        if (isMediaCatalogMode()) {
            return createWatchingNowMediaCard(item);
        }

        Button playButton = new PlayMenuButton(I18n.tr("autoPlay2"));
        playButton.getStyleClass().add("bookmark-play-menu-button");
        boolean actionAvailable = item != null && (listAction != series || !isBlank(item.getCmd()));
        playButton.setVisible(actionAvailable);
        playButton.setManaged(actionAvailable);
        playButton.setOnAction(event -> {
            event.consume();
            if (item == null) {
                return;
            }
            channelGrid.selectItems(List.of(item));
            ContextMenu menu = createChannelContextMenu(item, List.of(item), playButton);
            if (menu != null && !menu.getItems().isEmpty()) {
                UiI18n.preparePopupControl(menu, playButton);
                menu.show(playButton, Side.BOTTOM, 0, 0);
            }
        });
        return new BookmarkCard(
                item == null ? "" : item.getChannelName(),
                account == null ? "" : account.getAccountName(),
                item == null ? "" : item.getLogo(),
                thumbnailsEnabled,
                IMAGE_CACHE_KEY_CHANNEL,
                item != null && item.getChannel() != null && PlayerService.getInstance().isDrmProtected(item.getChannel()),
                playButton
        );
    }

    private Region createPlainTextChannelCard(ChannelItem item) {
        HBox card = new HBox();
        card.getStyleClass().addAll("bookmark-card", "plain-text-row-card");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(item == null ? "" : item.getChannelName());
        title.getStyleClass().add("bookmark-channel-title");
        title.setWrapText(false);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(title, Priority.ALWAYS);

        card.getChildren().add(title);
        return card;
    }

    private Region createDrawerChannelRow(ChannelItem item) {
        Label title = new Label(item == null ? "" : item.getChannelName());
        title.getStyleClass().addAll("strong-label", "account-drawer-channel-title");
        title.setWrapText(true);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);

        Label meta = new Label(drawerChannelMeta(item));
        meta.getStyleClass().add("account-drawer-channel-meta");
        meta.setWrapText(true);
        meta.setMinWidth(0);
        meta.setMaxWidth(Double.MAX_VALUE);
        meta.setVisible(!isBlank(meta.getText()));
        meta.setManaged(!isBlank(meta.getText()));

        VBox text = new VBox(2, title, meta);
        text.setMinWidth(0);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        HBox badges = new HBox(4);
        badges.setAlignment(Pos.CENTER_RIGHT);
        populateDrawerChannelBadges(item, badges);

        HBox row = new HBox(8, text, badges);
        row.getStyleClass().addAll("account-drawer-channel-row", "watching-now-episode-card", "watching-now-episode-card-compact");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 9, 8, 9));
        row.setMinWidth(0);
        row.setMaxWidth(Double.MAX_VALUE);
        row.setMinHeight(44);
        row.setPrefHeight(Region.USE_COMPUTED_SIZE);
        row.setMaxHeight(Region.USE_PREF_SIZE);
        return row;
    }

    private String drawerChannelMeta(ChannelItem item) {
        List<String> parts = new ArrayList<>();
        if (listAction == vod) {
            parts.add(I18n.tr("autoVod"));
        } else if (listAction == series) {
            parts.add(I18n.tr("autoSeries"));
        } else {
            parts.add(I18n.tr("autoChannels"));
        }
        if (!isBlank(categoryTitle)) {
            parts.add(categoryTitle);
        }
        Channel channel = item == null ? null : item.getChannel();
        if (channel != null && !isBlank(channel.getRating())) {
            parts.add(I18n.tr("autoRatingPrefix", channel.getRating()));
        }
        return String.join(" · ", parts);
    }

    private void populateDrawerChannelBadges(ChannelItem item, HBox badges) {
        if (badges == null || item == null || item.getChannel() == null) {
            return;
        }
        Channel channel = item.getChannel();
        if (PlayerService.getInstance().isDrmProtected(channel)) {
            badges.getChildren().add(createDrawerBadge(I18n.tr("autoDrm")));
        }
        if (listAction == series && channel.isWatched()) {
            badges.getChildren().add(createDrawerBadge(I18n.tr("autoInPROGRESS")));
        }
    }

    private Label createDrawerBadge(String text) {
        Label badge = new Label(text == null ? "" : text);
        badge.getStyleClass().add("drm-badge");
        badge.setMinWidth(Region.USE_PREF_SIZE);
        badge.setMaxWidth(Region.USE_PREF_SIZE);
        return badge;
    }

    private HBox createWatchingNowMediaCard(ChannelItem item) {
        WatchingNowVodResolver.VodMetadata vodMetadata = resolveVodMetadata(item);
        String posterUrl = firstNonBlank(item == null ? "" : item.getLogo(), vodMetadata == null ? "" : normalizeImageUrl(vodMetadata.getLogo()));
        ImageView poster = thumbnailsEnabled
                ? SeriesCardUiSupport.createFitPoster(posterUrl, 136, 204, IMAGE_CACHE_KEY_CHANNEL)
                : null;

        Button actionButton = new PlayMenuButton(I18n.tr("autoPlay2"));
        actionButton.setOnAction(event -> {
            event.consume();
            if (item == null) {
                return;
            }
            channelGrid.selectItems(List.of(item));
            ContextMenu menu = createChannelContextMenu(item, List.of(item), actionButton);
            if (menu != null && !menu.getItems().isEmpty()) {
                UiI18n.preparePopupControl(menu, actionButton);
                menu.show(actionButton, Side.BOTTOM, 0, 0);
                return;
            }
            playOrShowSeries(item);
        });

        List<Label> metadataNodes = createMediaMetadataNodes(item, vodMetadata);
        Label plot = createMediaPlotLabel(item, vodMetadata);

        Button openHint = null;
        if (listAction == series) {
            openHint = new Button(I18n.tr("autoViewEpisodes"));
            openHint.getStyleClass().add("watching-now-open-hint");
            openHint.setFocusTraversable(true);
            openHint.setMinHeight(Region.USE_PREF_SIZE);
            openHint.setOnAction(event -> {
                event.consume();
                if (item == null) {
                    return;
                }
                channelGrid.selectItems(List.of(item));
                playOrShowSeries(item);
            });
        }

        return WatchingNowMediaCardFactory.builder(listAction == vod
                        ? WatchingNowMediaCardFactory.CardType.VOD
                        : WatchingNowMediaCardFactory.CardType.SERIES)
                .title(item == null ? "" : item.getChannelName())
                .account(account == null ? "" : account.getAccountName())
                .poster(poster, thumbnailsEnabled)
                .actionButton(actionButton)
                .openAction(() -> playOrShowSeries(item))
                .metadataNodes(metadataNodes)
                .plot(plot, listAction == vod
                        ? WatchingNowMediaCardFactory.PlotPlacement.FULL_WIDTH
                        : WatchingNowMediaCardFactory.PlotPlacement.DETAILS)
                .footer(openHint)
                .build()
                .card();
    }

    private List<Label> createMediaMetadataNodes(ChannelItem item, WatchingNowVodResolver.VodMetadata vodMetadata) {
        List<Label> metadataNodes = new ArrayList<>();
        if (listAction == series) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoSeries")));
        }
        Channel channel = item == null ? null : item.getChannel();
        if (channel == null) {
            return metadataNodes;
        }
        if (PlayerService.getInstance().isDrmProtected(channel)) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoDrm")));
        }
        if (listAction == vod) {
            populateVodMetaChips(metadataNodes, vodMetadata);
            return metadataNodes;
        }
        if (listAction == series && channel.isWatched()) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoInPROGRESS")));
        }
        if (!isBlank(channel.getRating())) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoRatingPrefix", channel.getRating())));
        }
        if (!isBlank(channel.getDuration())) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoDurationPrefix", channel.getDuration())));
        }
        if (!isBlank(channel.getReleaseDate())) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(channel.getReleaseDate()));
        }
        return metadataNodes;
    }

    private void populateVodMetaChips(List<Label> metadataNodes, WatchingNowVodResolver.VodMetadata vodMetadata) {
        if (vodMetadata == null) {
            return;
        }
        if (!isBlank(vodMetadata.getRating())) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoImdbPrefix", vodMetadata.getRating())));
        }
        if (!isBlank(vodMetadata.getReleaseDate())) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoReleasePrefix", vodMetadata.getReleaseDate())));
        }
        if (!isBlank(vodMetadata.getDuration())) {
            metadataNodes.add(WatchingNowMediaCardFactory.createChip(I18n.tr("autoDurationPrefix", vodMetadata.getDuration())));
        }
    }

    private Label createMediaPlotLabel(ChannelItem item, WatchingNowVodResolver.VodMetadata vodMetadata) {
        Channel channel = item == null ? null : item.getChannel();
        String plotText = listAction == vod
                ? vodMetadata == null ? "" : vodMetadata.getPlot()
                : channel == null ? "" : channel.getDescription();
        return WatchingNowMediaCardFactory.createPlotLabel(plotText);
    }

    private WatchingNowVodResolver.VodMetadata resolveVodMetadata(ChannelItem item) {
        if (listAction != vod || item == null || item.getChannel() == null) {
            return null;
        }
        return vodMetadataResolver.resolveMetadata(item.getChannel());
    }

    private String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private void updateStyleClass(Node node, String styleClass, boolean enabled) {
        if (node == null) {
            return;
        }
        if (enabled) {
            if (!node.getStyleClass().contains(styleClass)) {
                node.getStyleClass().add(styleClass);
            }
            return;
        }
        node.getStyleClass().remove(styleClass);
    }

    private boolean isMediaCatalogMode() {
        return account != null && (listAction == vod || listAction == series);
    }

    private ContextMenu createChannelContextMenu(ChannelItem item, List<ChannelItem> selectedItems, Node owner) {
        if (item == null || (listAction == series && isBlank(item.getCmd()))) {
            return null;
        }
        ContextMenu menu = new ContextMenu();
        UiI18n.preparePopupControl(menu, owner == null ? this : owner);
        menu.setHideOnEscape(true);
        menu.setAutoHide(true);

        if (listAction == vod) {
            populateVodContextMenu(menu, item);
            return menu;
        }

        List<ChannelItem> effectiveSelection = selectedItems == null || selectedItems.isEmpty()
                ? List.of(item)
                : selectedItems;
        addPlayerItems(menu, item);
        addSeparatorIfNeeded(menu);
        Menu bookmarkMenu = new Menu(I18n.tr("autoBookmark"));
        menu.getItems().add(bookmarkMenu);
        menu.setOnShowing(_ -> loadBookmarkMenuItemsAsync(effectiveSelection, bookmarkMenu));
        return menu;
    }

    private void addPlayerItems(ContextMenu menu, ChannelItem item) {
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.setOnAction(event -> {
                menu.hide();
                play(item, option.playerPath());
            });
            menu.getItems().add(playerItem);
        }
    }

    public boolean navigateBackEmbedded() {
        if (!embeddedMode && !inlineEpisodeNavigationEnabled) {
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
                    imageView.clearImage();
                    setGraphic(null);
                    return;
                }

                ChannelItem channelItem = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                        ? getTableView().getItems().get(getIndex())
                        : null;

                if (channelItem == null) {
                    imageView.clearImage();
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(item);
                boolean drmProtected = channelItem.getChannel() != null && PlayerService.getInstance().isDrmProtected(channelItem.getChannel());
                drmBadge.setVisible(drmProtected);
                drmBadge.setManaged(drmProtected);
                boolean inProgress = listAction == series
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
                boolean inProgress = listAction == series
                        && channelItem.getChannel() != null
                        && channelItem.getChannel().isWatched();
                progressBadge.setVisible(inProgress);
                progressBadge.setManaged(inProgress);
                bookmarkIcon.setVisible(channelItem.isBookmarked());
                setGraphic(graphic);
            }
        };
    }

    private void registerSceneLifecycleListener() {
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterBookmarkListener();
                unregisterThumbnailModeListener();
                if (!embeddedMode && !inlineEpisodeNavigationEnabled) {
                    releaseTransientState();
                }
                return;
            }
            if (!disposed.get()) {
                registerBookmarkListener();
                registerThumbnailModeListener();
                applyThumbnailMode(ThumbnailAwareUI.areThumbnailsEnabled());
                refreshBookmarkStatesAsync();
            }
        });
        if (getScene() != null && !disposed.get()) {
            registerBookmarkListener();
            registerThumbnailModeListener();
            applyThumbnailMode(ThumbnailAwareUI.areThumbnailsEnabled());
            refreshBookmarkStatesAsync();
        }
    }

    private void registerBookmarkListener() {
        if (bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = true;
        if (listAction == vod && !vodWatchStateListenerRegistered) {
            VodWatchStateService.getInstance().addChangeListener(vodWatchStateChangeListener);
            vodWatchStateListenerRegistered = true;
        }
    }

    private void registerThumbnailModeListener() {
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
        if (enabled) {
            channelName.setCellFactory(column -> createThumbnailCell());
        } else {
            channelName.setCellFactory(column -> createPlainTextCell());
        }
        applyChannelGridSizing();
        refreshChannelViews();
    }

    private void refreshChannelViews() {
        table.refresh();
        channelGrid.refresh();
    }

    public void dispose() {
        if (!disposed.compareAndSet(false, true)) {
            return;
        }
        unregisterBookmarkListener();
        unregisterThumbnailModeListener();
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
        cancelLoadingProgressHide();
        seriesEpisodesCache.clear();
        // Clear channel items and metadata to allow garbage collection
        if (channelItems != null) {
            channelItems.clear();
        }
        channelList.clear();
        seenChannelKeys.clear();
        channelItemByKey.clear();
        categoryTitleByCategoryId.set(Map.of());
        categoryTitleByNormalizedTitle.set(Map.of());
        detailContent.getChildren().clear();
        detailPane.getChildren().clear();
        table.setItems(FXCollections.observableArrayList());
        channelGrid.setItems(FXCollections.observableArrayList());
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
        if (disposed.get() || channelItems == null || channelItems.isEmpty()) {
            return;
        }
        new Thread(() -> {
            if (disposed.get()) {
                return;
            }
            List<Bookmark> accountBookmarks = loadBookmarksForAccount();
            Set<String> savedVodKeys = loadVodWatchStateKeys();
            runLater(() -> {
                if (disposed.get()) {
                    return;
                }
                for (ChannelItem item : channelItems) {
                    BookmarkContext context = resolveBookmarkContext(item.getChannel());
                    boolean isBookmarked = listAction == vod
                            ? isVodSaved(item.getChannel(), context, savedVodKeys)
                            : isChannelBookmarked(item.getChannel(), context, accountBookmarks);
                    item.setBookmarked(isBookmarked);
                }
                refreshChannelViews();
            });
        }).start();
    }

    private List<Bookmark> loadBookmarksForAccount() {
        if (listAction == vod) {
            return List.of();
        }
        return BookmarkService.getInstance().read().stream()
                .filter(b -> account.getAccountName().equals(b.getAccountName()))
                .toList();
    }

    private Set<String> loadVodWatchStateKeys() {
        if (listAction != vod || isBlank(account.getDbId())) {
            return Set.of();
        }
        return VodWatchStateService.getInstance().getAllByAccount(account.getDbId()).stream()
                .filter(Objects::nonNull)
                .map(state -> normalizeExact(state.getCategoryId()) + "|" + normalizeExact(state.getVodId()))
                .collect(Collectors.toSet());
    }

    private Map<String, SeriesWatchState> loadSeriesWatchStates() {
        if (listAction != series || account == null || isBlank(account.getDbId())) {
            return Map.of();
        }
        Map<String, SeriesWatchState> states = SeriesWatchStateService.getInstance()
                .getSeriesLastWatchedByAccount(account.getDbId());
        if (states == null || states.isEmpty()) {
            return Map.of();
        }
        Map<String, SeriesWatchState> normalized = new HashMap<>();
        for (Map.Entry<String, SeriesWatchState> entry : states.entrySet()) {
            String key = normalizeSeriesWatchKey(entry.getKey());
            if (!isBlank(key)) {
                normalized.put(key, entry.getValue());
            }
        }
        return normalized;
    }

    private String normalizeSeriesWatchKey(String seriesId) {
        String raw = seriesId == null ? "" : seriesId.trim();
        if (isBlank(raw) || !raw.contains(":")) {
            return raw;
        }
        String[] parts = raw.split(":");
        for (int index = parts.length - 1; index >= 0; index--) {
            String part = parts[index] == null ? "" : parts[index].trim();
            if (!isBlank(part)) {
                return part;
            }
        }
        return raw;
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
            if (disposed.get()) {
                return;
            }
            try {
                List<Category> categories = CategoryService.getInstance().getCached(account);
                if (disposed.get()) {
                    return;
                }

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

            } catch (Exception _) {
                // Ignore malformed category context and keep default empty mappings.
                categoryTitleByCategoryId.set(Map.of());
                categoryTitleByNormalizedTitle.set(Map.of());
            } finally {
                if (!disposed.get()) {
                    runLater(this::refreshBookmarkStatesAsync);
                }
            }
        }, "bookmark-all-context-loader").start();
    }

    private String normalizeCategoryKey(String value) {
        return value == null ? "" : value.trim().toLowerCase();
    }

    private boolean isM3uAccount() {
        return account.getType() == M3U8_LOCAL || account.getType() == M3U8_URL;
    }

    private BookmarkContext resolveBookmarkContext(Channel channel) {
        String effectiveCategoryId = categoryId;
        String effectiveCategoryTitle = categoryTitle;
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
            if (event.getCode() == KeyCode.A && event.isShortcutDown()) {
                table.getSelectionModel().selectAll();
                event.consume();
                return;
            }
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
        if (!ensureCensoredAccess(item)) {
            return;
        }
        if (showCachedEpisodesIfPresent(item)) {
            return;
        }
        AtomicBoolean isCancelled = preparePlaybackLoad();

        if (listAction != series) {
            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            return;
        }
        if (isDirectPlaybackSeriesItem(item)) {
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
        EpisodesListUI ui = new EpisodesListUI(mediaContext, item.getChannelName(), item.getChannelId(), categoryId);
        ui.applyWatchingNowDetailStyling();
        return ui;
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
        if (listAction != series) {
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
            if (disposed.get()) {
                return;
            }
            if (this.getChildren().size() > 1) {
                this.getChildren().remove(1);
            }
            EpisodesListUI ui = new EpisodesListUI(mediaContext, item.getChannelName(), item.getChannelId(), categoryId);
            ui.applyWatchingNowDetailStyling();
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

        if (listAction == vod) {
            configureVodContextMenu(row, rowMenu);
            return;
        }

        addPlayerItems(row, rowMenu);
        addSeparatorIfNeeded(rowMenu);
        Menu bookmarkMenu = new Menu(I18n.tr("autoBookmark"));
        rowMenu.getItems().add(bookmarkMenu);
        configureBookmarkMenu(row, rowMenu, bookmarkMenu);
        bindRowContextMenu(row, rowMenu);
    }

    private void addSeparatorIfNeeded(ContextMenu menu) {
        if (menu != null && !menu.getItems().isEmpty()) {
            menu.getItems().add(new SeparatorMenuItem());
        }
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
                () -> listAction == series && (row.getItem() == null || isBlank(row.getItem().getCmd())),
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
        row.addEventHandler(ContextMenuEvent.CONTEXT_MENU_REQUESTED, event -> normalizeContextMenuSelection(row));
        rowMenu.setOnShowing(event -> {
            bookmarkMenu.getItems().clear();
            List<ChannelItem> selectedItems = resolveBookmarkSelection(row);
            if (selectedItems.isEmpty()) {
                return;
            }
            loadBookmarkMenuItemsAsync(selectedItems, bookmarkMenu);
        });
    }

    private void normalizeContextMenuSelection(TableRow<ChannelItem> row) {
        if (row == null || row.isEmpty()) {
            return;
        }
        TableView.TableViewSelectionModel<ChannelItem> selectionModel = table.getSelectionModel();
        if (selectionModel.isSelected(row.getIndex())) {
            return;
        }
        selectionModel.clearAndSelect(row.getIndex());
    }

    private List<ChannelItem> resolveBookmarkSelection(TableRow<ChannelItem> row) {
        if (row == null || row.isEmpty() || row.getItem() == null) {
            return List.of();
        }
        List<ChannelItem> selectedItems = new ArrayList<>(table.getSelectionModel().getSelectedItems());
        if (!selectedItems.contains(row.getItem())) {
            return List.of(row.getItem());
        }
        return selectedItems.isEmpty() ? List.of(row.getItem()) : selectedItems;
    }

    private void loadBookmarkMenuItemsAsync(List<ChannelItem> items, Menu bookmarkMenu) {
        Thread bookmarkMenuThread = new Thread(() -> {
            try {
                List<Bookmark> accountBookmarks = loadBookmarksForAccount();
                List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();
                Map<ChannelItem, Bookmark> existingBookmarks = new LinkedHashMap<>();
                for (ChannelItem item : items) {
                    BookmarkContext ctx = resolveBookmarkContext(item.getChannel());
                    Bookmark existingBookmark = findMatchingBookmark(item.getChannel(), ctx, accountBookmarks);
                    if (existingBookmark != null) {
                        existingBookmarks.put(item, existingBookmark);
                    }
                }
                Platform.runLater(() -> populateBookmarkMenuItems(items, bookmarkMenu, categories, existingBookmarks));
            } catch (RuntimeException _) {
                // Context menus remain usable without bookmark sub-items if the cache is unavailable.
            }
        }, "channel-bookmark-menu-loader");
        bookmarkMenuThread.setDaemon(true);
        bookmarkMenuThread.start();
    }

    private void populateBookmarkMenuItems(List<ChannelItem> items,
                                           Menu bookmarkMenu,
                                           List<BookmarkCategory> categories,
                                           Map<ChannelItem, Bookmark> existingBookmarks) {
        MenuItem allItem = new MenuItem(I18n.tr("autoAll"));
        allItem.setOnAction(e -> saveBookmarks(items, null));
        bookmarkMenu.getItems().add(allItem);
        bookmarkMenu.getItems().add(new SeparatorMenuItem());

        for (BookmarkCategory category : categories) {
            MenuItem categoryItem = new MenuItem(category.getName());
            categoryItem.setOnAction(e -> saveBookmarks(items, category.getId()));
            bookmarkMenu.getItems().add(categoryItem);
        }

        if (!existingBookmarks.isEmpty()) {
            bookmarkMenu.getItems().add(new SeparatorMenuItem());
            bookmarkMenu.getItems().add(buildRemoveBookmarkItem(existingBookmarks));
        }
    }

    private MenuItem buildRemoveBookmarkItem(Map<ChannelItem, Bookmark> existingBookmarks) {
        MenuItem unbookmarkItem = new MenuItem(I18n.tr("autoRemoveBookmark"));
        unbookmarkItem.getStyleClass().add("danger-menu-item");
        unbookmarkItem.setOnAction(e -> removeBookmarksAsync(existingBookmarks));
        return unbookmarkItem;
    }

    private void removeBookmarksAsync(Map<ChannelItem, Bookmark> existingBookmarks) {
        new Thread(() -> {
            for (Map.Entry<ChannelItem, Bookmark> entry : existingBookmarks.entrySet()) {
                Bookmark bookmark = entry.getValue();
                if (bookmark != null && !isBlank(bookmark.getDbId())) {
                    BookmarkService.getInstance().remove(bookmark.getDbId());
                }
            }
            Platform.runLater(() -> {
                for (ChannelItem item : existingBookmarks.keySet()) {
                    item.setBookmarked(false);
                }
                refreshChannelViews();
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

    private void saveBookmarks(List<ChannelItem> items, String bookmarkCategoryId) {
        new Thread(() -> {
            for (ChannelItem item : items) {
                BookmarkContext ctx = resolveBookmarkContext(item.getChannel());
                Bookmark bookmark = new Bookmark(account.getAccountName(), ctx.categoryTitle, item.getChannelId(), item.getChannelName(), item.getCmd(), account.getServerPortalUrl(), ctx.categoryId);
                bookmark.setAccountAction(listAction);
                bookmark.setCategoryId(bookmarkCategoryId);

                Category cat = new Category();
                cat.setCategoryId(ctx.categoryId);
                cat.setTitle(ctx.categoryTitle);
                bookmark.setCategoryJson(cat.toJson());

                if (item.getChannel() != null) {
                    if (listAction == vod) {
                        bookmark.setVodJson(item.getChannel().toJson());
                    } else {
                        bookmark.setChannelJson(item.getChannel().toJson());
                    }
                }
                BookmarkService.getInstance().save(bookmark);
            }
            Platform.runLater(() -> {
                for (ChannelItem item : items) {
                    item.setBookmarked(true);
                }
                refreshChannelViews();
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
                refreshChannelViews();
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
                refreshChannelViews();
                refreshBookmarkStatesAsync();
            });
        }).start();
    }

    private void play(ChannelItem item, String playerPath) {
        if (item == null) {
            return;
        }
        if (!ensureCensoredAccess(item)) {
            return;
        }
        Channel channelForPlayback = resolveChannelForPlayback(item);
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(mediaContext, channelForPlayback, playerPath)
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

    private boolean ensureCensoredAccess(ChannelItem item) {
        if (item == null || item.getChannel() == null) {
            com.uiptv.util.AppLog.addInfoLog(ChannelListUI.class, "[ParentalLock] channelAccessCheck skipped: missing item/channel");
            return true;
        }
        int censored = item.getChannel().getCensored();
        boolean isStalker = account.getType() == STALKER_PORTAL;
        boolean passwordConfigured = com.uiptv.service.FilterLockService.getInstance().hasPasswordConfigured();
        boolean sessionUnlocked = com.uiptv.service.FilterLockService.getInstance().isUnlocked();
        com.uiptv.util.AppLog.addInfoLog(ChannelListUI.class,
                "[ParentalLock] channelAccessCheck"
                        + LOG_ACCOUNT + account.getAccountName()
                        + " type=" + account.getType()
                        + " action=" + listAction
                        + " categoryTitle=" + categoryTitle
                        + LOG_CHANNEL_ID + item.getChannelId()
                        + LOG_NAME + item.getChannelName()
                        + " censored=" + censored
                        + " passwordConfigured=" + passwordConfigured
                        + " sessionUnlocked=" + sessionUnlocked);
        if (!isStalker || censored != 1) {
            return true;
        }
        boolean unlocked = FilterLockDialogs.ensureUnlocked(this, "filterLockUnlockCensoredChannelReason");
        com.uiptv.util.AppLog.addInfoLog(ChannelListUI.class,
                "[ParentalLock] channelAccessResult"
                        + LOG_ACCOUNT + account.getAccountName()
                        + LOG_CHANNEL_ID + item.getChannelId()
                        + LOG_NAME + item.getChannelName()
                        + " unlocked=" + unlocked);
        return unlocked;
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
