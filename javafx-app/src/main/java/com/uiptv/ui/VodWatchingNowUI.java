package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;
import com.uiptv.service.AccountChangeListener;
import com.uiptv.service.AccountService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.VodWatchStateChangeListener;
import com.uiptv.service.VodWatchStateService;
import com.uiptv.service.WatchingNowVodResolver;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.ImageUrlNormalizer;
import com.uiptv.widget.LoadingStateView;
import com.uiptv.widget.PlayMenuButton;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.json.JSONObject;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;

public class VodWatchingNowUI extends VBox {
    private static final String KEY_CARD_LABELS = "cardLabels";
    private static final String KEY_CARD_LINKS = "cardLinks";
    private static final String VOD_WATCHING_NOW_CACHE = "vod-watching-now";
    private final VBox contentBox = new VBox(10);
    private final ScrollPane scrollPane = new ScrollPane(contentBox);
    private final ResponsiveCardGrid<VodPanelData> vodGrid = new ResponsiveCardGrid<>(this::createCard);
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean reloadQueued = new AtomicBoolean(false);
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private final Map<String, VodPanelData> panelDataByKey = Collections.synchronizedMap(new LinkedHashMap<>());
    private final WatchingNowVodResolver vodResolver = new WatchingNowVodResolver();
    private static final int MAX_IMDB_CACHE_ENTRIES = Integer.getInteger("uiptv.watchingnow.vod.imdb.maxEntries", 200);
    private final Map<String, VodImdbCacheEntry> imdbCacheByPanelKey = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, VodImdbCacheEntry> eldest) {
                    return size() > MAX_IMDB_CACHE_ENTRIES;
                }
            }
    );
    private volatile boolean dirty = true;
    private final VodWatchStateChangeListener changeListener = this::onDataChanged;
    private final AccountChangeListener accountChangeListener = _ -> onAccountsChanged();
    private String lastListFingerprint = "";
    private String selectedVodKey = "";
    private String renderedDetailKey = "";
    private String searchQuery = "";
    private String searchQueryDisplay = "";
    private boolean listenerRegistered = false;
    private boolean accountListenerRegistered = false;
    private HBox selectedCard;

    public VodWatchingNowUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        contentBox.setPadding(new Insets(5));
        configureVodGrid();
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        // Initialize with empty state instead of loading on startup
        contentBox.getChildren().setAll(new Label("")); // Empty container
        registerListeners();
        // Mark as dirty to load data when visible
        dirty = true;
    }

    private void configureVodGrid() {
        vodGrid.getStyleClass().add("watching-now-vod-grid");
        vodGrid.setCardWidthRange(520, 760);
        vodGrid.setGaps(18, 16);
        vodGrid.setPlaceholderText(I18n.tr("autoNoWatchingNowVodFound"));
        vodGrid.setActivateOnSingleClick(true);
        vodGrid.setOnItemActivated(this::showVodDetail);
        vodGrid.setContextMenuFactory((item, _, owner) -> createContextMenu(item, owner));
        vodGrid.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                play(vodGrid.getFocusedItem(), null);
                event.consume();
            }
        });
    }

    public void forceReload() {
        dirty = true;
        refreshIfNeeded();
    }

    void markDirty() {
        dirty = true;
    }

    void setSearchQuery(String query) {
        String normalized = normalizeSearchQuery(query);
        if (Objects.equals(searchQuery, normalized)) {
            return;
        }
        searchQuery = normalized;
        searchQueryDisplay = safe(query);
        if (!panelDataByKey.isEmpty()) {
            renderCurrentListFromCachedRows();
        }
    }

    public void refreshIfNeeded() {
        if (!dirty || !isDisplayable()) {
            return;
        }
        if (reloadInProgress.get()) {
            reloadQueued.set(true);
            return;
        }
        reloadInProgress.set(true);
        reloadQueued.set(false);
        dirty = false;
        showLoadingPlaceholderIfEmpty();
        long generation = lifecycleGeneration.get();
        new Thread(() -> {
            List<VodPanelData> rows = buildRows();
            Platform.runLater(() -> {
                try {
                    if (lifecycleGeneration.get() != generation || !isDisplayable()) {
                        dirty = true;
                        return;
                    }
                    render(rows);
                } finally {
                    reloadInProgress.set(false);
                    if (reloadQueued.getAndSet(false) || dirty) {
                        refreshIfNeeded();
                    }
                }
            });
        }, "vod-watching-now-loader").start();
    }

    private List<VodPanelData> buildRows() {
        List<VodPanelData> rows = new ArrayList<>();
        for (WatchingNowVodResolver.VodRow row : vodResolver.resolveAll()) {
            Account account = row.getAccount();
            if (account != null) {
                account.setAction(vod);
            }
            VodPanelData panel = buildPanel(row);
            if (panel != null) {
                rows.add(panel);
            }
        }
        rows.sort(Comparator.comparingLong((VodPanelData data) -> data.state.getUpdatedAt()).reversed()
                .thenComparing(data -> safe(data.displayTitle), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private VodPanelData buildPanel(WatchingNowVodResolver.VodRow row) {
        if (row == null || row.getAccount() == null || row.getState() == null || isBlank(row.getState().getVodId())) {
            return null;
        }
        Account account = row.getAccount();
        VodWatchState state = row.getState();
        WatchingNowVodResolver.VodMetadata meta = row.getMetadata();
        String logo = normalizeImageUrl(meta.getLogo(), account);
        VodPanelData.DisplayMetadata metadata = VodPanelData.DisplayMetadata.of(
                logo,
                meta.getPlot(),
                meta.getReleaseDate(),
                meta.getRating()
        );
        VodPanelData panel = VodPanelData.create(account, state, row.getPlaybackChannel(), row.getDisplayTitle(), metadata, meta.getDuration());
        applyImdbCache(panel);
        return panel;
    }

    private void render(List<VodPanelData> rows) {
        List<VodPanelData> previousPanels = new ArrayList<>(panelDataByKey.values());
        selectedCard = null;
        List<VodPanelData> safeRows = rows == null ? List.of() : rows;
        String fingerprint = vodListFingerprint(safeRows);
        List<VodPanelData> renderRows = reuseRenderedRowsWhenStable(safeRows, fingerprint);
        panelDataByKey.clear();
        for (VodPanelData row : renderRows) {
            panelDataByKey.put(panelKey(row), row);
        }
        if (!fingerprint.equals(lastListFingerprint)) {
            clearPanelUiReferences(previousPanels);
            List<VodPanelData> visibleRows = filterVodRows(renderRows);
            String visibleFingerprint = vodListFingerprint(visibleRows);
            vodGrid.setItems(FXCollections.observableArrayList(visibleRows));
            lastListFingerprint = visibleFingerprint;
        }
        List<VodPanelData> visibleRows = filterVodRows(renderRows);
        renderCurrentView(visibleRows, vodListFingerprint(visibleRows));
    }

    private void renderCurrentView(List<VodPanelData> rows, String fingerprint) {
        if (!isBlank(selectedVodKey)) {
            VodPanelData selected = panelDataByKey.get(selectedVodKey);
            if (selected != null) {
                if (selectedVodKey.equals(renderedDetailKey)) {
                    refreshVodDetailInPlace(selected);
                    return;
                }
                showVodDetail(selected);
                return;
            }
            selectedVodKey = "";
            renderedDetailKey = "";
        }
        showVodList(rows, fingerprint);
    }

    private void showVodList(List<VodPanelData> rows, String fingerprint) {
        renderedDetailKey = "";
        lastListFingerprint = fingerprint;
        vodGrid.setPlaceholderText(vodListPlaceholderText());
        if (contentBox.getChildren().size() != 1 || contentBox.getChildren().getFirst() != vodGrid) {
            contentBox.getChildren().setAll(vodGrid);
        }
        for (VodPanelData row : rows) {
            triggerImdbLoad(row);
        }
        VBox.setVgrow(vodGrid, Priority.ALWAYS);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
    }

    private void renderCurrentListFromCachedRows() {
        if (!isBlank(selectedVodKey)) {
            renderCurrentView(filterVodRows(new ArrayList<>(panelDataByKey.values())),
                    vodListFingerprint(filterVodRows(new ArrayList<>(panelDataByKey.values()))));
            return;
        }
        List<VodPanelData> visibleRows = filterVodRows(new ArrayList<>(panelDataByKey.values()));
        String fingerprint = vodListFingerprint(visibleRows);
        if (!fingerprint.equals(lastListFingerprint)) {
            vodGrid.setItems(FXCollections.observableArrayList(visibleRows));
            lastListFingerprint = fingerprint;
        }
        renderCurrentView(visibleRows, fingerprint);
    }

    private List<VodPanelData> filterVodRows(List<VodPanelData> rows) {
        if (!isSearchActive() || rows == null || rows.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> matchesVodSearch(row, searchQuery))
                .toList();
    }

    private boolean matchesVodSearch(VodPanelData data, String query) {
        if (data == null || isBlank(query)) {
            return true;
        }
        StringBuilder searchable = new StringBuilder();
        appendSearchText(searchable,
                data.displayTitle,
                data.duration,
                data.imdbUrl,
                data.account == null ? "" : data.account.getAccountName(),
                data.state == null ? "" : data.state.getVodId(),
                data.state == null ? "" : data.state.getCategoryId(),
                data.playbackChannel == null ? "" : data.playbackChannel.getName());
        if (data.metadata != null) {
            appendSearchText(searchable,
                    data.metadata.plot,
                    data.metadata.releaseDate,
                    data.metadata.rating);
        }
        return searchable.toString().toLowerCase(Locale.ROOT).contains(query);
    }

    private void appendSearchText(StringBuilder builder, String... values) {
        if (builder == null || values == null) {
            return;
        }
        for (String value : values) {
            if (!isBlank(value)) {
                builder.append(' ').append(value);
            }
        }
    }

    private String vodListPlaceholderText() {
        return isSearchActive()
                ? I18n.tr("autoNothingFoundFor", searchQueryDisplay)
                : I18n.tr("autoNoWatchingNowVodFound");
    }

    private boolean isSearchActive() {
        return !isBlank(searchQuery);
    }

    private String normalizeSearchQuery(String query) {
        return safe(query).toLowerCase(Locale.ROOT);
    }

    private List<VodPanelData> reuseRenderedRowsWhenStable(List<VodPanelData> rows, String fingerprint) {
        if (!fingerprint.equals(lastListFingerprint) || rows.isEmpty()) {
            return rows;
        }
        Map<String, VodPanelData> existingByKey = new LinkedHashMap<>();
        for (VodPanelData existing : vodGrid.getItems()) {
            existingByKey.put(panelKey(existing), existing);
        }
        List<VodPanelData> reused = new ArrayList<>(rows.size());
        for (VodPanelData row : rows) {
            VodPanelData existing = existingByKey.get(panelKey(row));
            if (existing == null) {
                reused.add(row);
            } else {
                mergeVodPanelInPlace(existing, row);
                reused.add(existing);
            }
        }
        return reused;
    }

    private void mergeVodPanelInPlace(VodPanelData target, VodPanelData source) {
        if (target == null || source == null || target.metadata == null || source.metadata == null) {
            return;
        }
        target.metadata.coverUrl = firstNonBlank(source.metadata.coverUrl, target.metadata.coverUrl);
        target.metadata.plot = firstNonBlank(source.metadata.plot, target.metadata.plot);
        target.metadata.releaseDate = firstNonBlank(source.metadata.releaseDate, target.metadata.releaseDate);
        target.metadata.rating = firstNonBlank(source.metadata.rating, target.metadata.rating);
        target.imdbUrl = firstNonBlank(source.imdbUrl, target.imdbUrl);
        target.imdbLoaded = target.imdbLoaded || source.imdbLoaded;
        target.imdbLoading = target.imdbLoading || source.imdbLoading;
    }

    private void showLoadingPlaceholderIfEmpty() {
        if (contentBox.getChildren().isEmpty() || isInitialPlaceholder()) {
            contentBox.getChildren().setAll(new LoadingStateView(I18n.tr("autoLoadingChannelsFor", I18n.tr("autoVod"))));
        }
    }

    private boolean isInitialPlaceholder() {
        return contentBox.getChildren().size() == 1
                && contentBox.getChildren().getFirst() instanceof Label label
                && isBlank(label.getText());
    }

    private String vodListFingerprint(List<VodPanelData> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        return rows.stream()
                .map(this::panelKey)
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private void applyImdbCache(VodPanelData data) {
        if (data == null) {
            return;
        }
        VodImdbCacheEntry cached = imdbCacheByPanelKey.get(panelKey(data));
        if (cached == null) {
            return;
        }
        cached.applyTo(data);
        data.imdbLoaded = true;
        data.imdbLoading = false;
    }

    private HBox createCard(VodPanelData data) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(14));
        card.getStyleClass().add("uiptv-card");
        card.getStyleClass().add("watching-now-vod-card");

        ImageView poster = SeriesCardUiSupport.createFitPoster(data.metadata.coverUrl, 136, 204, VOD_WATCHING_NOW_CACHE);
        StackPane posterWrap = createWatchingNowCardPosterWrap(poster);
        posterWrap.setVisible(ThumbnailAwareUI.areThumbnailsEnabled());
        posterWrap.setManaged(ThumbnailAwareUI.areThumbnailsEnabled());

        VBox details = new VBox(8);
        details.getStyleClass().add("watching-now-card-text");
        details.setMaxWidth(Double.MAX_VALUE);
        details.setMinWidth(0);
        details.setFillWidth(true);
        HBox.setHgrow(details, Priority.ALWAYS);

        ContextMenu cardMenu = new ContextMenu();
        UiI18n.preparePopupControl(cardMenu, card);
        cardMenu.setHideOnEscape(true);
        cardMenu.setAutoHide(true);

        Button playButton = new PlayMenuButton(I18n.tr("autoPlay2"));
        playButton.setOnAction(event -> {
            event.consume();
            showContextMenu(cardMenu, card, data);
        });

        Runnable openDetails = () -> {
            selectedVodKey = panelKey(data);
            showVodDetail(data);
        };

        Hyperlink title = new Hyperlink(data.displayTitle);
        title.getStyleClass().add("strong-label");
        title.getStyleClass().add("watching-now-card-title");
        title.getStyleClass().add("watching-now-title-link");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinWidth(0);
        title.setMinHeight(Region.USE_PREF_SIZE);
        title.setFocusTraversable(true);
        title.setOnAction(event -> {
            event.consume();
            openDetails.run();
        });
        HBox.setHgrow(title, Priority.ALWAYS);

        Label accountLabel = new Label(data.account.getAccountName());
        accountLabel.getStyleClass().add("watching-now-card-account");
        accountLabel.setWrapText(true);
        accountLabel.setMaxWidth(Double.MAX_VALUE);
        accountLabel.setMinWidth(0);
        accountLabel.setMinHeight(Region.USE_PREF_SIZE);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        HBox titleRow = new HBox(10, title, titleSpacer, playButton);
        titleRow.setAlignment(Pos.TOP_LEFT);

        details.getChildren().addAll(titleRow, accountLabel);
        FlowPane metaRow = new FlowPane(8, 6);
        metaRow.getStyleClass().add("watching-now-card-meta-row");
        addMetadataLines(metaRow, data);
        if (!metaRow.getChildren().isEmpty()) {
            details.getChildren().add(metaRow);
        }
        updateImdbNodes(details, data);
        Label openHint = new Label(I18n.tr("autoViewDetails"));
        openHint.getStyleClass().add("watching-now-open-hint");
        openHint.setMinHeight(Region.USE_PREF_SIZE);
        details.getChildren().add(openHint);

        HBox topRow = new HBox(16, posterWrap, details);
        topRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(details, Priority.ALWAYS);

        VBox cardBody = new VBox(4);
        cardBody.setMaxWidth(Double.MAX_VALUE);
        cardBody.setFillWidth(true);
        HBox.setHgrow(cardBody, Priority.ALWAYS);
        cardBody.getChildren().add(topRow);
        if (data.plotNode != null) {
            details.getChildren().remove(data.plotNode);
            data.plotNode.setMaxWidth(Double.MAX_VALUE);
            data.plotNode.setMinWidth(0);
            data.plotNode.setMinHeight(Region.USE_PREF_SIZE);
            data.plotNode.setMaxHeight(Double.MAX_VALUE);
            data.plotNode.prefWidthProperty().bind(cardBody.widthProperty().subtract(6));
            cardBody.getChildren().add(data.plotNode);
        }

        card.getChildren().add(cardBody);
        List<Label> cardLabels = collectCardLabels(data);
        cardLabels.add(accountLabel);
        cardLabels.add(openHint);
        card.getProperties().put(KEY_CARD_LABELS, cardLabels);
        card.getProperties().put(KEY_CARD_LINKS, List.of(title));
        return card;
    }

    private StackPane createWatchingNowCardPosterWrap(ImageView poster) {
        StackPane posterWrap = new StackPane(poster);
        posterWrap.getStyleClass().add("watching-now-card-poster-wrap");
        posterWrap.setAlignment(Pos.CENTER);
        posterWrap.setMinWidth(Region.USE_PREF_SIZE);
        posterWrap.setMaxWidth(Region.USE_PREF_SIZE);
        return posterWrap;
    }

    private void showVodDetail(VodPanelData data) {
        if (data == null) {
            selectedVodKey = "";
            render(new ArrayList<>(panelDataByKey.values()));
            return;
        }
        selectedVodKey = panelKey(data);
        renderedDetailKey = selectedVodKey;
        contentBox.getChildren().clear();
        contentBox.setPadding(new Insets(2));
        contentBox.setSpacing(12);

        Button back = new Button(I18n.tr("autoBack"));
        back.getStyleClass().add("watching-now-back-button");
        back.setOnAction(event -> {
            selectedVodKey = "";
            showVodList(new ArrayList<>(panelDataByKey.values()), lastListFingerprint);
        });

        Button play = new Button(I18n.tr("autoPlay"));
        play.getStyleClass().add("watching-now-detail-reload-button");
        play.setOnAction(event -> play(data, null));

        Label headerTitle = new Label(I18n.tr("autoWatchingNow"));
        headerTitle.getStyleClass().add("watching-now-detail-heading");
        Label headerSubtitle = new Label(data.displayTitle);
        headerSubtitle.getStyleClass().add("watching-now-detail-subheading");
        headerSubtitle.setWrapText(true);
        headerSubtitle.setMinWidth(0);
        headerSubtitle.setMaxWidth(Double.MAX_VALUE);

        VBox headerText = new VBox(2, headerTitle, headerSubtitle);
        headerText.setMinWidth(0);
        headerText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(headerText, Priority.ALWAYS);

        HBox topBar = new HBox(10, headerText, play, back);
        topBar.getStyleClass().add("watching-now-detail-topbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setMinWidth(0);
        topBar.setMaxWidth(Double.MAX_VALUE);

        FlowPane detailLayout = new FlowPane(14, 14);
        detailLayout.getStyleClass().add("watching-now-vod-detail-layout");
        detailLayout.setAlignment(Pos.TOP_LEFT);
        detailLayout.setMinWidth(0);
        detailLayout.setMaxWidth(Double.MAX_VALUE);

        VBox posterPanel = createVodPosterPanel(data);
        VBox detailsPanel = createVodDetailsPanel(data);
        detailLayout.getChildren().addAll(posterPanel, detailsPanel);
        detailLayout.widthProperty().addListener((_, _, width) ->
                applyVodDetailLayoutSizing(posterPanel, detailsPanel, width.doubleValue()));
        Platform.runLater(() -> applyVodDetailLayoutSizing(posterPanel, detailsPanel, detailLayout.getWidth()));

        contentBox.getChildren().addAll(topBar, detailLayout);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
        triggerImdbLoad(data);
        refreshVodDetailMetadata(data);
    }

    private VBox createVodPosterPanel(VodPanelData data) {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("watching-now-vod-poster-panel");
        panel.setMinWidth(260);
        panel.setPrefWidth(330);
        panel.setMaxWidth(380);

        ImageView poster = SeriesCardUiSupport.createFitPoster(data.metadata.coverUrl, 260, 390, VOD_WATCHING_NOW_CACHE);
        data.detailPosterNode = poster;
        StackPane posterWrap = new StackPane(poster);
        posterWrap.getStyleClass().add("watching-now-series-poster-wrap");
        posterWrap.setAlignment(Pos.CENTER);
        posterWrap.setMaxWidth(Double.MAX_VALUE);
        panel.getChildren().add(posterWrap);
        return panel;
    }

    private VBox createVodDetailsPanel(VodPanelData data) {
        VBox panel = new VBox(10);
        panel.getStyleClass().add("watching-now-vod-details-panel");
        panel.setMinWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);

        data.detailTitleNode = new Label(data.displayTitle);
        data.detailTitleNode.getStyleClass().add("watching-now-series-detail-title");
        data.detailTitleNode.setWrapText(true);
        data.detailTitleNode.setMinWidth(0);
        data.detailTitleNode.setMaxWidth(Double.MAX_VALUE);

        Label account = new Label(data.account.getAccountName());
        account.getStyleClass().add("watching-now-series-account");
        account.setWrapText(true);
        account.setMinWidth(0);
        account.setMaxWidth(Double.MAX_VALUE);

        data.detailMetadataBox = new VBox(7);
        data.detailMetadataBox.setMinWidth(0);
        data.detailMetadataBox.setMaxWidth(Double.MAX_VALUE);
        refreshVodDetailMetadata(data);

        panel.getChildren().addAll(data.detailTitleNode, account, data.detailMetadataBox);
        return panel;
    }

    private void applyVodDetailLayoutSizing(VBox posterPanel, VBox detailsPanel, double width) {
        double available = Math.max(0, width);
        if (available < 720) {
            posterPanel.setPrefWidth(Math.max(260, available - 4));
            posterPanel.setMaxWidth(Double.MAX_VALUE);
            detailsPanel.setPrefWidth(Math.max(260, available - 4));
        } else {
            posterPanel.setPrefWidth(330);
            posterPanel.setMaxWidth(380);
            detailsPanel.setPrefWidth(Math.max(360, available - 360));
        }
    }

    private void refreshVodDetailInPlace(VodPanelData data) {
        if (data == null || !panelKey(data).equals(renderedDetailKey)) {
            return;
        }
        if (data.detailPosterNode != null && !isBlank(data.metadata.coverUrl)) {
            ImageCacheManager.loadImageAsync(data.metadata.coverUrl, VOD_WATCHING_NOW_CACHE).thenAccept(image -> {
                if (image != null) {
                    Platform.runLater(() -> {
                        if (data.detailPosterNode != null) {
                            data.detailPosterNode.setImage(image);
                        }
                    });
                }
            });
        }
        refreshVodDetailMetadata(data);
    }

    private void refreshVodDetailMetadata(VodPanelData data) {
        if (data == null || data.detailMetadataBox == null) {
            return;
        }
        data.detailMetadataBox.getChildren().clear();
        HBox imdbPill = SeriesCardUiSupport.createImdbRatingPill(data.metadata.rating, data.imdbUrl);
        if (imdbPill != null) {
            data.detailMetadataBox.getChildren().add(imdbPill);
        }
        addDetailLine(data.detailMetadataBox, I18n.tr("autoDurationPrefix", data.duration), data.duration);
        addDetailLine(data.detailMetadataBox, I18n.tr("autoReleasePrefix", data.metadata.releaseDate), data.metadata.releaseDate);
        addDetailLine(data.detailMetadataBox, I18n.tr("autoImdbPrefix", data.metadata.rating), data.metadata.rating);
        if (!isBlank(data.metadata.plot)) {
            Label plot = new Label(data.metadata.plot);
            plot.getStyleClass().add("watching-now-series-meta-line");
            plot.setWrapText(true);
            plot.setMinWidth(0);
            plot.setMaxWidth(Double.MAX_VALUE);
            data.detailMetadataBox.getChildren().add(plot);
        }
        if (data.imdbLoading && !data.imdbLoaded) {
            data.detailMetadataBox.getChildren().add(new LoadingStateView(I18n.tr("autoLoadingIMDbDetails"), 14));
        }
    }

    private void addDetailLine(VBox box, String text, String value) {
        if (isBlank(value)) {
            return;
        }
        Label label = new Label(text);
        label.getStyleClass().add("watching-now-series-meta-line");
        label.setWrapText(true);
        label.setMinWidth(0);
        label.setMaxWidth(Double.MAX_VALUE);
        box.getChildren().add(label);
    }

    private ContextMenu createContextMenu(VodPanelData data, javafx.scene.Node owner) {
        ContextMenu menu = new ContextMenu();
        UiI18n.preparePopupControl(menu, owner);
        menu.setHideOnEscape(true);
        menu.setAutoHide(true);
        populateContextMenu(menu, data);
        return menu;
    }

    private List<Label> collectCardLabels(VodPanelData data) {
        List<Label> labels = new ArrayList<>();
        if (data.ratingNode != null) {
            labels.add(data.ratingNode);
        }
        if (data.releaseNode != null) {
            labels.add(data.releaseNode);
        }
        if (data.durationNode != null) {
            labels.add(data.durationNode);
        }
        if (data.plotNode != null) {
            labels.add(data.plotNode);
        }
        return labels;
    }

    private void addMetadataLines(FlowPane metadataRow, VodPanelData data) {
        if (!isBlank(data.metadata.rating)) {
            data.ratingNode = new Label(I18n.tr("autoImdbPrefix", data.metadata.rating));
            styleMetadataChip(data.ratingNode);
            metadataRow.getChildren().add(data.ratingNode);
        }
        if (!isBlank(data.metadata.releaseDate)) {
            data.releaseNode = new Label(I18n.tr("autoReleasePrefix", data.metadata.releaseDate));
            styleMetadataChip(data.releaseNode);
            metadataRow.getChildren().add(data.releaseNode);
        }
        if (!isBlank(data.duration)) {
            data.durationNode = new Label(I18n.tr("autoDurationPrefix", data.duration));
            styleMetadataChip(data.durationNode);
            metadataRow.getChildren().add(data.durationNode);
        }
        if (!isBlank(data.metadata.plot)) {
            data.plotNode = new Label(data.metadata.plot);
            data.plotNode.getStyleClass().add("watching-now-card-plot");
            data.plotNode.setWrapText(true);
            data.plotNode.setTextOverrun(OverrunStyle.CLIP);
            data.plotNode.setMaxWidth(Double.MAX_VALUE);
            data.plotNode.setMinWidth(0);
            data.plotNode.setMinHeight(Region.USE_PREF_SIZE);
        }
    }

    private void styleMetadataChip(Label label) {
        label.getStyleClass().add("watching-now-card-chip");
        label.setMinWidth(Region.USE_PREF_SIZE);
        label.setMaxWidth(Region.USE_PREF_SIZE);
    }

    private void updateImdbNodes(VBox details, VodPanelData data) {
        if (data.imdbBadgeNode != null) {
            details.getChildren().remove(data.imdbBadgeNode);
        }
        if (data.imdbLoadingNode != null) {
            details.getChildren().remove(data.imdbLoadingNode);
        }
        data.imdbBadgeNode = SeriesCardUiSupport.createImdbRatingPill(data.metadata.rating, data.imdbUrl);
        if (data.imdbBadgeNode != null) {
            details.getChildren().add(Math.min(2, details.getChildren().size()), data.imdbBadgeNode);
        }
        if (data.imdbLoading && !data.imdbLoaded) {
            if (data.imdbLoadingNode == null) {
                data.imdbLoadingNode = new LoadingStateView(I18n.tr("autoLoadingIMDbDetails"), 14);
            }
            details.getChildren().add(data.imdbLoadingNode);
        }
    }

    private void triggerImdbLoad(VodPanelData data) {
        if (data == null || data.imdbLoaded || data.imdbLoading) {
            return;
        }
        data.imdbLoading = true;
        long generation = lifecycleGeneration.get();
        boolean submitted = WatchingNowMetadataExecutor.submit(() -> {
            try {
                if (!isPanelCurrent(data, generation)) {
                    return;
                }
                JSONObject imdb = ImdbMetadataService.getInstance().findBestEffortMovieDetails(data.displayTitle, "");
                if (imdb != null && isPanelCurrent(data, generation)) {
                    mergeImdb(data, imdb);
                    imdbCacheByPanelKey.put(panelKey(data), VodImdbCacheEntry.from(data));
                }
            } finally {
                Platform.runLater(() -> {
                    if (isPanelCurrent(data, generation)) {
                        data.imdbLoaded = true;
                        data.imdbLoading = false;
                        refreshRenderedCards();
                    }
                });
            }
        });
        if (!submitted) {
            data.imdbLoading = false;
        } else {
            refreshRenderedCards();
        }
    }

    private void mergeImdb(VodPanelData data, JSONObject imdb) {
        if (data == null || imdb == null) {
            return;
        }
        if (isBlank(data.metadata.coverUrl)) {
            data.metadata.coverUrl = normalizeImageUrl(imdb.optString("cover", ""), data.account);
        }
        data.metadata.plot = firstNonBlank(data.metadata.plot, imdb.optString("plot", ""));
        data.metadata.releaseDate = firstNonBlank(data.metadata.releaseDate, imdb.optString("releaseDate", ""));
        data.metadata.rating = firstNonBlank(data.metadata.rating, imdb.optString("rating", ""));
        data.imdbUrl = firstNonBlank(data.imdbUrl, imdb.optString("imdbUrl", ""));
    }

    private void refreshRenderedCards() {
        if (!isDisplayable()) {
            dirty = true;
            return;
        }
        if (!isBlank(renderedDetailKey)) {
            refreshVodDetailInPlace(panelDataByKey.get(renderedDetailKey));
            return;
        }
        vodGrid.refresh();
    }

    private void addContextMenu(HBox card, ContextMenu menu, VodPanelData data) {
        card.setOnContextMenuRequested(event -> {
            setSelectedCard(card);
            showContextMenu(menu, card, data, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void showContextMenu(ContextMenu menu, HBox card, VodPanelData data) {
        double x = card.localToScreen(card.getBoundsInLocal()).getMinX() + card.getWidth() - 8;
        double y = card.localToScreen(card.getBoundsInLocal()).getMinY() + 8;
        showContextMenu(menu, card, data, x, y);
    }

    private void showContextMenu(ContextMenu menu, HBox card, VodPanelData data, double screenX, double screenY) {
        populateContextMenu(menu, data);
        if (!menu.getItems().isEmpty()) {
            menu.show(card, screenX, screenY);
        }
    }

    @SuppressWarnings("unchecked")
    private void setSelectedCard(HBox current) {
        if (current == null) {
            return;
        }
        if (selectedCard != null && selectedCard != current) {
            applyCardSelection(selectedCard, false);
        }
        applyCardSelection(current, true);
        selectedCard = current;
    }

    @SuppressWarnings("unchecked")
    private void applyCardSelection(HBox card, boolean selected) {
        if (card == null) {
            return;
        }
        toggleStyleClass(card.getStyleClass(), "selected-card", selected);
        applyLabelSelection(card, selected);
        applyLinkSelection(card, selected);
    }

    private void applyLabelSelection(HBox card, boolean selected) {
        Object labelsObj = card.getProperties().get(KEY_CARD_LABELS);
        if (!(labelsObj instanceof List<?> labels)) {
            return;
        }
        for (Object labelObj : labels) {
            if (labelObj instanceof Label label) {
                toggleStyleClass(label.getStyleClass(), "selected-card-text", selected);
            }
        }
    }

    private void applyLinkSelection(HBox card, boolean selected) {
        Object linksObj = card.getProperties().get(KEY_CARD_LINKS);
        if (!(linksObj instanceof List<?> links)) {
            return;
        }
        for (Object linkObj : links) {
            if (linkObj instanceof Hyperlink link) {
                toggleStyleClass(link.getStyleClass(), "selected-card-link", selected);
            }
        }
    }

    private void toggleStyleClass(List<String> styleClasses, String styleClass, boolean enabled) {
        if (enabled) {
            if (!styleClasses.contains(styleClass)) {
                styleClasses.add(styleClass);
            }
            return;
        }
        styleClasses.remove(styleClass);
    }

    private void populateContextMenu(ContextMenu menu, VodPanelData data) {
        menu.getItems().clear();
        if (data == null) {
            return;
        }
        for (WatchingNowActionMenu.ActionDescriptor action : WatchingNowActionMenu.buildEpisodeStyleActions(
                true,
                PlaybackUIService.getConfiguredPlayerOptions()
        )) {
            switch (action.kind()) {
                case SEPARATOR -> menu.getItems().add(new SeparatorMenuItem());
                case PLAYER -> {
                    MenuItem playerItem = new MenuItem(action.label());
                    playerItem.setOnAction(e -> {
                        menu.hide();
                        play(data, action.playerPath());
                    });
                    menu.getItems().add(playerItem);
                }
                case REMOVE_WATCHING_NOW -> {
                    MenuItem removeItem = new MenuItem(I18n.tr("autoRemoveWatchingNow"));
                    removeItem.getStyleClass().add("danger-menu-item");
                    removeItem.setOnAction(e -> confirmAndRemoveVod(data));
                    menu.getItems().add(removeItem);
                }
                case WATCHING_NOW -> {
                    // VOD cards are already in watching-now; no add action here.
                }
            }
        }
    }

    private void play(VodPanelData data, String playerPath) {
        if (data == null || data.playbackChannel == null) {
            return;
        }
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(data.account, data.playbackChannel, playerPath)
                .categoryId(data.state.getCategoryId())
                .channelId(data.state.getVodId())
                .errorPrefix(I18n.tr("autoErrorPlayingChannelPrefix")));
    }

    private void removeVod(VodPanelData data) {
        if (data == null || data.account == null || data.state == null) {
            return;
        }
        new Thread(() -> {
            VodWatchStateService.getInstance().remove(data.account.getDbId(), data.state.getCategoryId(), data.state.getVodId());
            Platform.runLater(() -> {
                panelDataByKey.remove(panelKey(data));
                if (panelKey(data).equals(selectedVodKey)) {
                    selectedVodKey = "";
                    renderedDetailKey = "";
                }
                render(new ArrayList<>(panelDataByKey.values()));
            });
        }, "vod-watching-now-remove").start();
    }

    private void confirmAndRemoveVod(VodPanelData data) {
        if (data == null) {
            return;
        }
        if (!showConfirmationAlert(I18n.tr("autoRemoveFromWatchingNowConfirm", firstNonBlank(data.displayTitle, I18n.tr("autoVod"))))) {
            return;
        }
        removeVod(data);
    }

    private void registerListeners() {
        ensureListenersRegistered();

        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                unregisterListeners();
                releaseUiState();
            } else {
                ensureListenersRegistered();
                refreshIfNeeded();
            }
        });
        visibleProperty().addListener((obs, oldVal, newVal) -> {
            if (Boolean.TRUE.equals(newVal)) {
                refreshIfNeeded();
            }
        });
    }

    private void ensureListenersRegistered() {
        if (!listenerRegistered) {
            VodWatchStateService.getInstance().addChangeListener(changeListener);
            listenerRegistered = true;
        }
        if (!accountListenerRegistered) {
            AccountService.getInstance().addChangeListener(accountChangeListener);
            accountListenerRegistered = true;
        }
    }

    private void unregisterListeners() {
        if (listenerRegistered) {
            VodWatchStateService.getInstance().removeChangeListener(changeListener);
            listenerRegistered = false;
        }
        if (accountListenerRegistered) {
            AccountService.getInstance().removeChangeListener(accountChangeListener);
            accountListenerRegistered = false;
        }
    }

    void dispose() {
        unregisterListeners();
        releaseUiState();
    }

    private void releaseUiState() {
        lifecycleGeneration.incrementAndGet();
        reloadQueued.set(false);
        refreshScheduled.set(false);
        dirty = true;
        lastListFingerprint = "";
        selectedVodKey = "";
        renderedDetailKey = "";
        clearPanelUiReferences(panelDataByKey.values());
        panelDataByKey.clear();
        imdbCacheByPanelKey.clear();
        selectedCard = null;
        contentBox.getChildren().clear();
    }

    private void clearPanelUiReferences(Collection<VodPanelData> panels) {
        if (panels == null || panels.isEmpty()) {
            return;
        }
        for (VodPanelData panel : new ArrayList<>(panels)) {
            if (panel != null) {
                panel.clearTransientUiState();
            }
        }
    }

    private void onDataChanged(String accountId, String vodId) {
        if (!isDisplayable()) {
            dirty = true;
            return;
        }
        // If panelDataByKey is empty (UI not yet rendered), do a full refresh instead of delta
        if (panelDataByKey.isEmpty()) {
            dirty = true;
            scheduleRefreshIfNeeded();
            return;
        }
        dirty = true;
        scheduleRefreshIfNeeded();
    }

    private void onAccountsChanged() {
        // When accounts change (e.g., deleted), force full refresh
        dirty = true;
        scheduleRefreshIfNeeded();
    }

    private void scheduleRefreshIfNeeded() {
        if (!refreshScheduled.compareAndSet(false, true)) {
            return;
        }
        Platform.runLater(() -> {
            refreshScheduled.set(false);
            refreshIfNeeded();
        });
    }

    private boolean isDisplayable() {
        if (getScene() == null) {
            return false;
        }
        javafx.scene.Node node = this;
        while (node != null) {
            if (!node.isVisible()) {
                return false;
            }
            node = node.getParent();
        }
        return true;
    }

    private boolean isPanelCurrent(VodPanelData data, long generation) {
        return data != null
                && lifecycleGeneration.get() == generation
                && Objects.equals(panelDataByKey.get(panelKey(data)), data);
    }

    private String panelKey(VodPanelData data) {
        return safe(data.account.getDbId()) + "|" + safe(data.state.getCategoryId()) + "|" + safe(data.state.getVodId());
    }

    private String normalizeImageUrl(String value, Account account) {
        if (!ThumbnailAwareUI.areThumbnailsEnabled() || isBlank(value)) {
            return "";
        }
        return ImageUrlNormalizer.normalizeImageUrl(value, account);
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class VodPanelData {
        private final Account account;
        private final VodWatchState state;
        private final Channel playbackChannel;
        private final String displayTitle;
        private final DisplayMetadata metadata;
        private final String duration;
        private String imdbUrl = "";
        private boolean imdbLoaded;
        private boolean imdbLoading;
        private Label ratingNode;
        private Label releaseNode;
        private Label durationNode;
        private Label plotNode;
        private HBox imdbBadgeNode;
        private HBox imdbLoadingNode;
        private ImageView detailPosterNode;
        private Label detailTitleNode;
        private VBox detailMetadataBox;
        private VodPanelData(Account account, VodWatchState state, Channel playbackChannel, String displayTitle, DisplayMetadata metadata, String duration) {
            this.account = account;
            this.state = state;
            this.playbackChannel = playbackChannel;
            this.displayTitle = displayTitle;
            this.metadata = metadata;
            this.duration = duration;
        }

        private static VodPanelData create(Account account, VodWatchState state, Channel playbackChannel, String displayTitle,
                                           DisplayMetadata metadata, String duration) {
            return new VodPanelData(account, state, playbackChannel, displayTitle, metadata, duration);
        }

        private void clearTransientUiState() {
            ratingNode = null;
            releaseNode = null;
            durationNode = null;
            plotNode = null;
            imdbBadgeNode = null;
            imdbLoadingNode = null;
            detailPosterNode = null;
            detailTitleNode = null;
            detailMetadataBox = null;
        }

        private static final class DisplayMetadata {
            private String coverUrl;
            private String plot;
            private String releaseDate;
            private String rating;

            private DisplayMetadata(String coverUrl, String plot, String releaseDate, String rating) {
                this.coverUrl = coverUrl;
                this.plot = plot;
                this.releaseDate = releaseDate;
                this.rating = rating;
            }

            private static DisplayMetadata of(String coverUrl, String plot, String releaseDate, String rating) {
                return new DisplayMetadata(coverUrl, plot, releaseDate, rating);
            }
        }
    }

    private static final class VodImdbCacheEntry {
        private final String coverUrl;
        private final String plot;
        private final String releaseDate;
        private final String rating;
        private final String imdbUrl;

        private VodImdbCacheEntry(String coverUrl, String plot, String releaseDate, String rating, String imdbUrl) {
            this.coverUrl = coverUrl == null ? "" : coverUrl;
            this.plot = plot == null ? "" : plot;
            this.releaseDate = releaseDate == null ? "" : releaseDate;
            this.rating = rating == null ? "" : rating;
            this.imdbUrl = imdbUrl == null ? "" : imdbUrl;
        }

        private static VodImdbCacheEntry from(VodPanelData data) {
            if (data == null || data.metadata == null) {
                return new VodImdbCacheEntry("", "", "", "", "");
            }
            return new VodImdbCacheEntry(
                    data.metadata.coverUrl,
                    data.metadata.plot,
                    data.metadata.releaseDate,
                    data.metadata.rating,
                    data.imdbUrl
            );
        }

        private void applyTo(VodPanelData data) {
            if (data == null || data.metadata == null) {
                return;
            }
            data.metadata.coverUrl = firstNonBlank(data.metadata.coverUrl, coverUrl);
            data.metadata.plot = firstNonBlank(data.metadata.plot, plot);
            data.metadata.releaseDate = firstNonBlank(data.metadata.releaseDate, releaseDate);
            data.metadata.rating = firstNonBlank(data.metadata.rating, rating);
            data.imdbUrl = firstNonBlank(data.imdbUrl, imdbUrl);
        }

        private static String firstNonBlank(String primary, String fallback) {
            if (!isBlank(primary)) {
                return primary.trim();
            }
            return fallback == null ? "" : fallback.trim();
        }
    }
}
