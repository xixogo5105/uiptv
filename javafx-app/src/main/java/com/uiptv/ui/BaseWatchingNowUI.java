package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.*;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.ui.util.UiServerUrlUtil;
import com.uiptv.util.EpisodeTitleFormatter;
import com.uiptv.util.I18n;
import com.uiptv.util.ImageUrlNormalizer;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.IconActionButton;
import com.uiptv.widget.LoadingStateView;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.PlayMenuButton;
import com.uiptv.widget.ResponsiveCardGrid;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

@SuppressWarnings("java:S5843")
public abstract class BaseWatchingNowUI extends VBox {
    private static final String KEY_CARD_LABELS = "cardLabels";
    private static final String KEY_COVER = "cover";
    private static final String KEY_RELEASE_DATE = "releaseDate";
    private static final String KEY_TITLE = "title";
    private static final String MESSAGE_NO_CURRENTLY_WATCHED_SERIES = "autoNoCurrentlyWatchedSeriesFound";
    private static final String STRONG_LABEL = "strong-label";
    private static final String EPISODE_MENU_ITEM = "episode-menu-item";
    private static final String WATCHING_NOW_CACHE = "watching-now";
    private static final String BINGE_WATCH_FAILED_PREFIX = "Binge watch failed: ";
    private static final String TRASH_ICON_PATH = "M6 19c0 1.1.9 2 2 2h8c1.1 0 2-.9 2-2V7H6v12zM8 9h8v10H8V9zm7.5-5-1-1h-5l-1 1H5v2h14V4h-3.5z";
    private static final Pattern SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b");
    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b|\\b\\d{1,2}x(\\d{1,3})\\b");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},\\s+\\d{4}\\b");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final int MAX_IMDB_CACHE_ENTRIES = Integer.getInteger("uiptv.watchingnow.imdb.maxEntries", 200);
    private static final double SERIES_EPISODE_LOADING_INDICATOR_SIZE = 24;
    private static final double SERIES_EPISODE_LOADING_PANEL_HEIGHT = 220;
    private final VBox contentBox = new VBox(8);
    private final ScrollPane scrollPane = new ScrollPane(contentBox);
    private final ResponsiveCardGrid<SeriesPanelData> seriesGrid = new ResponsiveCardGrid<>(this::createSeriesListCard);
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean reloadQueued = new AtomicBoolean(false);
    private final AtomicBoolean refreshScheduled = new AtomicBoolean(false);
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private final Map<String, SeriesPanelData> panelDataByKey = Collections.synchronizedMap(new LinkedHashMap<>());
    private final WatchingNowSeriesResolver seriesResolver = new WatchingNowSeriesResolver();
    private final Map<String, ImdbCacheEntry> imdbCacheByPanelKey = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ImdbCacheEntry> eldest) {
                    return size() > MAX_IMDB_CACHE_ENTRIES;
                }
            }
    );
    private volatile boolean dirty = true;
    private String selectedSeriesKey = "";
    private String renderedDetailKey = "";
    private String lastListFingerprint = "";
    private String searchQuery = "";
    private String searchQueryDisplay = "";
    private final SeriesWatchStateChangeListener watchStateChangeListener = this::onDataChanged;
    private final AccountChangeListener accountChangeListener = _ -> onAccountsChanged();
    private HBox selectedSeriesCard;
    private boolean watchStateListenerRegistered = false;
    private boolean accountListenerRegistered = false;

    protected BaseWatchingNowUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        contentBox.setPadding(new Insets(5));
        configureSeriesGrid();
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        // Initialize with empty state instead of loading on startup
        contentBox.getChildren().setAll(new Label("")); // Empty container
        registerListeners();
        // Mark as dirty to load data when visible
        dirty = true;
    }

    protected abstract boolean thumbnailsEnabled();

    private void configureSeriesGrid() {
        seriesGrid.getStyleClass().add("watching-now-series-grid");
        seriesGrid.setCardWidthRange(480, 720);
        seriesGrid.setGaps(18, 16);
        seriesGrid.setPlaceholderText(I18n.tr(MESSAGE_NO_CURRENTLY_WATCHED_SERIES));
        seriesGrid.setActivateOnSingleClick(true);
        seriesGrid.setOnItemActivated(this::openSeriesDetail);
        seriesGrid.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER || event.getCode() == KeyCode.SPACE) {
                openSeriesDetail(seriesGrid.getFocusedItem());
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
        searchQueryDisplay = safe(query).trim();
        if (!panelDataByKey.isEmpty()) {
            renderCurrentView();
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
            List<SeriesPanelData> rows = buildPanelsFromCache();
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
        }, "watching-now-loader").start();
    }

    private List<SeriesPanelData> buildPanelsFromCache() {
        List<SeriesPanelData> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            rows.addAll(buildPanelsForAccount(account));
        }
        rows.sort(Comparator.comparing((SeriesPanelData d) -> safe(d.seriesTitle), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private List<SeriesPanelData> buildPanelsForAccount(Account account) {
        List<SeriesPanelData> rows = new ArrayList<>();
        if (account == null || isBlank(account.getDbId())) {
            return rows;
        }
        account.setAction(Account.AccountAction.series);
        for (WatchingNowSeriesResolver.SeriesRow row : seriesResolver.resolveForAccount(account)) {
            SeriesPanelData panel = buildPanel(row);
            if (panel != null) {
                rows.add(panel);
            }
        }
        return rows;
    }

    private SeriesPanelData buildPanel(WatchingNowSeriesResolver.SeriesRow row) {
        Account account = row.getAccount();
        SeriesWatchState scopedState = row.getState();
        SeriesCacheInfo cacheInfo = new SeriesCacheInfo(row.getSeriesTitle(), row.getSeriesPoster(), row.isResolvedFromCache());
        if (!cacheInfo.resolvedFromCache && isBlank(cacheInfo.seriesTitle) && scopedState.getSeriesId().matches("^\\d+$")) {
            return null;
        }
        EpisodeList list = SeriesEpisodeService.getInstance().getEpisodesForWatchingNow(account, scopedState.getCategoryId(), scopedState.getSeriesId(), () -> false);
        if (list == null) {
            list = new EpisodeList();
        }
        if (list.getSeasonInfo() == null) {
            list.setSeasonInfo(new com.uiptv.shared.SeasonInfo());
        }
        if (list.getSeasonInfo() != null && isBlank(list.getSeasonInfo().getName()) && !isBlank(cacheInfo.seriesTitle)) {
            list.getSeasonInfo().setName(cacheInfo.seriesTitle);
        }
        if (list.getEpisodes() != null && !list.getEpisodes().isEmpty()) {
            SeriesWatchingNowSnapshotService.getInstance().save(
                    account,
                    scopedState.getCategoryId(),
                    scopedState.getSeriesId(),
                    row.getCategoryDbId(),
                    cacheInfo.seriesTitle,
                    cacheInfo.seriesPoster,
                    list
            );
        }
        List<WatchingEpisode> episodes = mapEpisodesFromCache(account, scopedState, list);
        if (episodes.isEmpty()) {
            return null;
        }

        JSONObject seasonInfo = new JSONObject();
        seasonInfo.put("name", cacheInfo.seriesTitle);
        String normalizedPoster = normalizeImageUrl(cacheInfo.seriesPoster, account);
        if (isBlank(normalizedPoster)) {
            String firstEpisodePoster = episodes.stream()
                    .map(e -> e.imageUrl)
                    .filter(s -> !isBlank(s))
                    .findFirst()
                    .orElse("");
            cacheInfo = new SeriesCacheInfo(cacheInfo.seriesTitle, firstEpisodePoster, cacheInfo.resolvedFromCache);
        }
        String cover = normalizeImageUrl(cacheInfo.seriesPoster, account);
        if (!isBlank(cover)) {
            seasonInfo.put(KEY_COVER, cover);
        }

        SeriesPanelData panel = new SeriesPanelData(account, scopedState, cacheInfo.seriesTitle, seasonInfo, episodes, list);
        applyImdbMemoryCache(panel);
        return panel;
    }

    private void applyImdbMemoryCache(SeriesPanelData data) {
        if (data == null) {
            return;
        }
        ImdbCacheEntry cached = imdbCacheByPanelKey.get(panelCacheKey(data.account, data.state));
        if (cached == null) {
            return;
        }
        mergeMissingSeasonInfo(data.seasonInfo, cached.seasonInfo);
        data.imdbLoaded = true;
        data.imdbLoading = false;
        data.thumbnailMetadataAttempted = true;
    }

    private List<WatchingEpisode> mapEpisodesFromCache(Account account,
                                                       SeriesWatchState state,
                                                       EpisodeList episodeList) {
        List<WatchingEpisode> episodes = new ArrayList<>();
        if (episodeList == null || episodeList.getEpisodes() == null) {
            return episodes;
        }
        for (Episode episode : episodeList.getEpisodes()) {
            if (episode == null) {
                continue;
            }
            String season = inferSeason(episode);
            String episodeNum = inferEpisodeNumber(episode);
            String plot = episode.getInfo() != null ? safe(episode.getInfo().getPlot()) : "";
            String title = cleanEpisodeTitleWithPlot(episode.getTitle(), plot);
            String image = episode.getInfo() != null ? normalizeImageUrl(episode.getInfo().getMovieImage(), account) : "";
            String releaseDate = episode.getInfo() != null ? safe(episode.getInfo().getReleaseDate()) : "";
            String rating = episode.getInfo() != null ? safe(episode.getInfo().getRating()) : "";

            boolean watched = SeriesWatchStateService.getInstance().isMatchingEpisode(
                    state,
                    episode.getId(),
                    season,
                    episodeNum,
                    episode.getTitle());
            Channel playbackChannel = new Channel();
            playbackChannel.setChannelId(episode.getId());
            playbackChannel.setName(title);
            playbackChannel.setCmd(episode.getCmd());
            playbackChannel.setLogo(image);
            playbackChannel.setSeason(season);
            playbackChannel.setEpisodeNum(episodeNum);

            episodes.add(new WatchingEpisode(account, state, playbackChannel, season, episodeNum, title, image, plot, releaseDate, rating, watched));
        }

        episodes.sort(Comparator
                .comparingInt((WatchingEpisode e) -> parseNumberOrDefault(e.season, 1))
                .thenComparingInt(e -> parseNumberOrDefault(e.episodeNum, Integer.MAX_VALUE))
                .thenComparing(e -> safe(e.title), String.CASE_INSENSITIVE_ORDER));
        return episodes;
    }

    private void render(List<SeriesPanelData> rows) {
        String previousSelection = selectedSeriesKey;
        if (rows == null || rows.isEmpty()) {
            panelDataByKey.clear();
            lastListFingerprint = "";
            showEmptySeriesPlaceholder();
            selectedSeriesKey = "";
            return;
        }
        String fingerprint = seriesListFingerprint(rows);
        List<SeriesPanelData> renderRows = reuseRenderedSeriesRowsWhenStable(rows, fingerprint);
        panelDataByKey.clear();
        for (SeriesPanelData data : renderRows) {
            panelDataByKey.put(seriesPaneKey(data), data);
        }
        selectedSeriesKey = previousSelection;
        renderCurrentView();
    }

    private List<SeriesPanelData> reuseRenderedSeriesRowsWhenStable(List<SeriesPanelData> rows, String fingerprint) {
        if (!fingerprint.equals(lastListFingerprint) || rows.isEmpty()) {
            return rows;
        }
        Map<String, SeriesPanelData> existingByKey = new LinkedHashMap<>();
        for (SeriesPanelData existing : seriesGrid.getItems()) {
            existingByKey.put(seriesPaneKey(existing), existing);
        }
        List<SeriesPanelData> reused = new ArrayList<>(rows.size());
        for (SeriesPanelData row : rows) {
            SeriesPanelData existing = existingByKey.get(seriesPaneKey(row));
            if (existing == null) {
                reused.add(row);
            } else {
                mergePanelInPlace(existing, row);
                reused.add(existing);
            }
        }
        return reused;
    }

    private void renderCurrentView() {
        List<SeriesPanelData> rows = new ArrayList<>(panelDataByKey.values());
        rows.sort(Comparator.comparing((SeriesPanelData d) -> safe(d.seriesTitle), String.CASE_INSENSITIVE_ORDER));
        if (!isBlank(selectedSeriesKey)) {
            SeriesPanelData selected = panelDataByKey.get(selectedSeriesKey);
            if (selected != null) {
                showSeriesDetail(selected);
                return;
            }
                selectedSeriesKey = "";
        }
        rows = filterSeriesRows(rows);
        showSeriesList(rows);
    }

    private void showSeriesList(List<SeriesPanelData> rows) {
        prepareSeriesListContainer();
        seriesGrid.setPlaceholderText(seriesListPlaceholderText());
        if (rows == null || rows.isEmpty()) {
            lastListFingerprint = "";
            contentBox.getChildren().add(new Label(seriesListPlaceholderText()));
            return;
        }

        showResponsiveSeriesList(rows);
    }

    private List<SeriesPanelData> filterSeriesRows(List<SeriesPanelData> rows) {
        if (!isSearchActive() || rows == null || rows.isEmpty()) {
            return rows;
        }
        return rows.stream()
                .filter(row -> matchesSeriesSearch(row, searchQuery))
                .toList();
    }

    private boolean matchesSeriesSearch(SeriesPanelData data, String query) {
        if (data == null || isBlank(query)) {
            return true;
        }
        StringBuilder searchable = new StringBuilder();
        appendSearchText(searchable,
                data.seriesTitle,
                data.account == null ? "" : data.account.getAccountName(),
                data.state == null ? "" : data.state.getSeriesId(),
                data.state == null ? "" : data.state.getCategoryId());
        if (data.seasonInfo != null) {
            appendSearchText(searchable,
                    data.seasonInfo.optString("name", ""),
                    data.seasonInfo.optString("genre", ""),
                    data.seasonInfo.optString("plot", ""),
                    data.seasonInfo.optString(KEY_RELEASE_DATE, ""),
                    data.seasonInfo.optString("rating", ""));
        }
        for (WatchingEpisode episode : data.episodes) {
            if (episode != null) {
                appendSearchText(searchable,
                        episode.title,
                        episode.plot,
                        episode.releaseDate,
                        episode.rating,
                        episode.season,
                        episode.episodeNum);
            }
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

    private String seriesListPlaceholderText() {
        return isSearchActive()
                ? I18n.tr("autoNothingFoundFor", searchQueryDisplay)
                : I18n.tr(MESSAGE_NO_CURRENTLY_WATCHED_SERIES);
    }

    private boolean isSearchActive() {
        return !isBlank(searchQuery);
    }

    private String normalizeSearchQuery(String query) {
        return safe(query).trim().toLowerCase(Locale.ROOT);
    }

    private void prepareSeriesListContainer() {
        if (contentBox.getChildren().size() != 1 || contentBox.getChildren().getFirst() != seriesGrid) {
            contentBox.getChildren().clear();
        }
        renderedDetailKey = "";
        contentBox.setPadding(new Insets(5));
        contentBox.setSpacing(10);
    }

    private void showResponsiveSeriesList(List<SeriesPanelData> rows) {
        String fingerprint = seriesListFingerprint(rows);
        if (!fingerprint.equals(lastListFingerprint)) {
            seriesGrid.setItems(FXCollections.observableArrayList(rows));
            lastListFingerprint = fingerprint;
        }
        if (contentBox.getChildren().size() != 1 || contentBox.getChildren().getFirst() != seriesGrid) {
            contentBox.getChildren().setAll(seriesGrid);
        }
        for (SeriesPanelData data : rows) {
            ensureSeriesThumbnailMetadataLoad(data);
        }
        selectedSeriesCard = null;
        VBox.setVgrow(seriesGrid, Priority.ALWAYS);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
    }

    private void ensureSeriesThumbnailMetadataLoad(SeriesPanelData data) {
        if (!thumbnailsEnabled() || data == null || data.imdbLoading || !isBlank(resolveSeriesPosterUrl(data))) {
            return;
        }
        if (data.imdbLoaded && !data.thumbnailMetadataAttempted) {
            data.imdbLoaded = false;
        }
        if (!data.imdbLoaded) {
            data.imdbLoading = true;
            lazyLoadImdb(data, null);
        }
    }

    private void showLoadingPlaceholderIfEmpty() {
        if (contentBox.getChildren().isEmpty() || isInitialPlaceholder()) {
            contentBox.getChildren().setAll(new LoadingStateView(I18n.tr("autoLoadingCurrentlyWatchedSeries")));
        }
    }

    private void showEmptySeriesPlaceholder() {
        String text = I18n.tr(MESSAGE_NO_CURRENTLY_WATCHED_SERIES);
        if (contentBox.getChildren().size() == 1 && contentBox.getChildren().getFirst() instanceof Label label) {
            label.setText(text);
            return;
        }
        contentBox.getChildren().setAll(new Label(text));
    }

    private boolean isInitialPlaceholder() {
        return contentBox.getChildren().size() == 1
                && contentBox.getChildren().getFirst() instanceof Label label
                && isBlank(label.getText());
    }

    private String seriesListFingerprint(List<SeriesPanelData> rows) {
        if (rows == null || rows.isEmpty()) {
            return "";
        }
        return rows.stream()
                .map(this::seriesPaneKey)
                .sorted()
                .collect(java.util.stream.Collectors.joining("|"));
    }

    private TableView<SeriesListItem> buildSeriesListTable(List<SeriesPanelData> rows) {
        TableView<SeriesListItem> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        table.setFocusTraversable(false);
        table.getColumns().add(createSeriesListColumn());
        table.setItems(buildSeriesListItems(rows));
        wireSeriesListTableSelection(table);
        return table;
    }

    private TableColumn<SeriesListItem, String> createSeriesListColumn() {
        TableColumn<SeriesListItem, String> seriesColumn = new TableColumn<>(I18n.tr("autoSeries"));
        seriesColumn.setCellValueFactory(cellData -> new javafx.beans.property.SimpleStringProperty(
                cellData.getValue().seriesTitleProperty().get() + " (" + cellData.getValue().accountNameProperty().get() + ")"
        ));
        seriesColumn.setReorderable(false);
        return seriesColumn;
    }

    private ObservableList<SeriesListItem> buildSeriesListItems(List<SeriesPanelData> rows) {
        ObservableList<SeriesListItem> items = FXCollections.observableArrayList();
        for (SeriesPanelData data : rows) {
            items.add(new SeriesListItem(
                    firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle),
                    data.account.getAccountName(),
                    seriesPaneKey(data)
            ));
        }
        return items;
    }

    private void wireSeriesListTableSelection(TableView<SeriesListItem> table) {
        table.setOnMousePressed(event -> {
            if (event.getClickCount() == 2 && event.getButton() == MouseButton.PRIMARY) {
                openSelectedSeries(table);
            }
        });
        table.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                openSelectedSeries(table);
            }
        });
    }

    private void openSelectedSeries(TableView<SeriesListItem> table) {
        SeriesListItem selected = table.getSelectionModel().getSelectedItem();
        if (selected == null) {
            return;
        }
        SeriesPanelData panelData = panelDataByKey.get(selected.getPanelKey());
        if (panelData == null) {
            return;
        }
        selectedSeriesKey = selected.getPanelKey();
        renderCurrentView();
    }

    private HBox createSeriesListCard(SeriesPanelData data) {
        HBox card = new HBox(16);
        card.setAlignment(Pos.TOP_LEFT);
        card.setFocusTraversable(true);
        card.setPadding(new Insets(14));
        card.getStyleClass().add("uiptv-card");
        card.getStyleClass().add("watching-now-series-card");

        StackPane posterWrap = null;
        if (thumbnailsEnabled()) {
            ImageView poster = SeriesCardUiSupport.createFitPoster(resolveSeriesPosterUrl(data), 136, 204, WATCHING_NOW_CACHE);
            data.seriesListPosterNode = poster;
            posterWrap = createWatchingNowCardPosterWrap(poster);
            loadSeriesListPosterImage(data);
        } else {
            data.seriesListPosterNode = null;
        }
        VBox text = new VBox(8);
        text.getStyleClass().add("watching-now-card-text");
        text.setMaxWidth(Double.MAX_VALUE);
        text.setMinWidth(0);
        text.setFillWidth(true);
        HBox.setHgrow(text, Priority.ALWAYS);

        String titleText = firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle);
        String accountText = data.account.getAccountName();
        Runnable openDetails = () -> {
            setSelectedSeriesCard(card);
            selectedSeriesKey = seriesPaneKey(data);
            showSeriesDetail(data);
        };

        Hyperlink title = new Hyperlink(titleText);
        data.seriesListTitleNode = title;
        title.getStyleClass().add(STRONG_LABEL);
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

        IconActionButton removeButton = new IconActionButton(I18n.tr("autoRemove"), TRASH_ICON_PATH, () -> {
            String seriesName = firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle, I18n.tr("watchingNowThisSeries"));
            if (!showConfirmationAlert(I18n.tr("autoRemoveFromWatchingNowConfirm", seriesName))) {
                return;
            }
            removeSeriesFromWatchingNow(data);
        });
        removeButton.getStyleClass().add("watching-now-remove-button");
        removeButton.setMinWidth(Region.USE_PREF_SIZE);
        removeButton.setMaxWidth(Region.USE_PREF_SIZE);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);
        HBox titleRow = new HBox(10, title, titleSpacer, removeButton);
        titleRow.setAlignment(Pos.TOP_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);

        Label accountLabel = new Label(accountText);
        accountLabel.getStyleClass().add("watching-now-card-account");
        accountLabel.setWrapText(true);
        accountLabel.setMaxWidth(Double.MAX_VALUE);
        accountLabel.setMinWidth(0);
        accountLabel.setMinHeight(Region.USE_PREF_SIZE);

        FlowPane metaRow = new FlowPane(8, 6);
        metaRow.getStyleClass().add("watching-now-card-meta-row");
        Label typeChip = createWatchingNowCardChip(I18n.tr("autoSeries"));
        Label episodeChip = createWatchingNowCardChip(data.episodes.size() + " " + I18n.tr("autoEpisodes"));
        metaRow.getChildren().addAll(typeChip, episodeChip);
        String season = normalizeNumber(data.state == null ? "" : data.state.getSeason());
        if (!isBlank(season)) {
            metaRow.getChildren().add(createWatchingNowCardChip(I18n.formatSeasonLabel(season)));
        }
        if (data.state != null && data.state.getEpisodeNum() > 0) {
            metaRow.getChildren().add(createWatchingNowCardChip(I18n.formatEpisodeLabel(String.valueOf(data.state.getEpisodeNum()))));
        }

        Button openHint = new Button(I18n.tr("autoViewEpisodes"));
        openHint.getStyleClass().add("watching-now-open-hint");
        openHint.setFocusTraversable(true);
        openHint.setMinHeight(Region.USE_PREF_SIZE);
        openHint.setOnAction(event -> {
            event.consume();
            openDetails.run();
        });

        text.getChildren().addAll(titleRow, accountLabel, metaRow);
        Label plot = createSeriesListPlotLabel(data);
        if (plot != null) {
            text.getChildren().add(plot);
        }
        text.getChildren().add(openHint);

        if (posterWrap == null) {
            card.getChildren().add(text);
        } else {
            card.getChildren().addAll(posterWrap, text);
        }
        List<Label> cardLabels = new ArrayList<>(List.of(accountLabel, typeChip, episodeChip));
        if (plot != null) {
            cardLabels.add(plot);
        }
        card.getProperties().put(KEY_CARD_LABELS, cardLabels);
        card.getProperties().put("cardLinks", List.of(title));
        return card;
    }

    private Label createSeriesListPlotLabel(SeriesPanelData data) {
        if (data == null || data.seasonInfo == null) {
            return null;
        }
        String plotText = data.seasonInfo.optString("plot", "");
        if (isBlank(plotText)) {
            return null;
        }
        Label plot = new Label(plotText);
        plot.getStyleClass().add("watching-now-card-plot");
        plot.setWrapText(true);
        plot.setMaxWidth(Double.MAX_VALUE);
        plot.setMinWidth(0);
        plot.setMinHeight(Region.USE_PREF_SIZE);
        return plot;
    }

    private StackPane createWatchingNowCardPosterWrap(ImageView poster) {
        StackPane posterWrap = new StackPane(poster);
        posterWrap.getStyleClass().add("watching-now-card-poster-wrap");
        posterWrap.setAlignment(Pos.CENTER);
        posterWrap.setMinWidth(Region.USE_PREF_SIZE);
        posterWrap.setMaxWidth(Region.USE_PREF_SIZE);
        return posterWrap;
    }

    private Label createWatchingNowCardChip(String text) {
        Label chip = new Label(text);
        chip.getStyleClass().add("watching-now-card-chip");
        chip.setMinWidth(Region.USE_PREF_SIZE);
        chip.setMaxWidth(Region.USE_PREF_SIZE);
        return chip;
    }

    private void openSeriesDetail(SeriesPanelData data) {
        if (data == null) {
            return;
        }
        selectedSeriesKey = seriesPaneKey(data);
        showSeriesDetail(data);
    }

    private void removeSeriesFromWatchingNow(SeriesPanelData data) {
        if (data == null || data.account == null || data.state == null || isBlank(data.account.getDbId()) || isBlank(data.state.getSeriesId())) {
            return;
        }
        String accountId = data.account.getDbId();
        String seriesId = data.state.getSeriesId();
        new Thread(() -> {
            List<SeriesWatchState> states = SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount(accountId);
            for (SeriesWatchState state : states) {
                if (state == null) {
                    continue;
                }
                if (safe(seriesId).equals(safe(state.getSeriesId()))) {
                    SeriesWatchStateService.getInstance().clearSeriesLastWatched(accountId, state.getCategoryId(), seriesId);
                }
            }
            Platform.runLater(() -> {
                String keyToRemove = seriesPaneKey(data);
                panelDataByKey.remove(keyToRemove);
                if (safe(selectedSeriesKey).equals(keyToRemove)) {
                    selectedSeriesKey = "";
                    renderedDetailKey = "";
                }
                renderCurrentView();
            });
        }, "watching-now-remove-series").start();
    }

    private void showSeriesDetail(SeriesPanelData data) {
        if (data == null) {
            selectedSeriesKey = "";
            renderCurrentView();
            return;
        }
        selectedSeriesKey = seriesPaneKey(data);
        renderedDetailKey = selectedSeriesKey;
        contentBox.getChildren().clear();
        contentBox.setPadding(new Insets(2));
        contentBox.setSpacing(12);

        String initialTitle = firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle);
        Button back = new Button(I18n.tr("autoBack"));
        back.getStyleClass().add("watching-now-back-button");
        back.setOnAction(event -> {
            selectedSeriesKey = "";
            renderCurrentView();
        });

        Button reload = new Button(I18n.tr("autoReloadFromServer"));
        reload.getStyleClass().add("watching-now-detail-reload-button");
        reload.setOnAction(event -> reloadSeriesDetailFromServer(data, reload));

        Label headerTitle = new Label(I18n.tr("autoWatchingNow"));
        headerTitle.getStyleClass().add("watching-now-detail-heading");
        Label headerSubtitle = new Label(initialTitle);
        headerSubtitle.getStyleClass().add("watching-now-detail-subheading");
        headerSubtitle.setWrapText(true);
        headerSubtitle.setMinWidth(0);
        headerSubtitle.setMaxWidth(Double.MAX_VALUE);

        VBox headerText = new VBox(2, headerTitle, headerSubtitle);
        headerText.setMinWidth(0);
        headerText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(headerText, Priority.ALWAYS);

        HBox topBar = new HBox(10, headerText, reload, back);
        topBar.getStyleClass().add("watching-now-detail-topbar");
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setMinWidth(0);
        topBar.setMaxWidth(Double.MAX_VALUE);

        FlowPane detailLayout = new FlowPane(14, 14);
        detailLayout.getStyleClass().add("watching-now-series-detail-layout");
        detailLayout.setAlignment(Pos.TOP_LEFT);
        detailLayout.setMinWidth(0);
        detailLayout.setMaxWidth(Double.MAX_VALUE);

        VBox episodesPanel = createSeriesEpisodesPanel(data);
        if (thumbnailsEnabled()) {
            VBox detailsPanel = createSeriesDetailPanel(data);
            detailLayout.getChildren().addAll(detailsPanel, episodesPanel);
            detailLayout.widthProperty().addListener((_, _, width) ->
                    applySeriesDetailLayoutSizing(detailsPanel, episodesPanel, width.doubleValue()));
            Platform.runLater(() -> applySeriesDetailLayoutSizing(detailsPanel, episodesPanel, detailLayout.getWidth()));
        } else {
            detailLayout.getChildren().add(episodesPanel);
            detailLayout.widthProperty().addListener((_, _, width) ->
                    applyPlainSeriesDetailLayoutSizing(episodesPanel, width.doubleValue()));
            Platform.runLater(() -> applyPlainSeriesDetailLayoutSizing(episodesPanel, detailLayout.getWidth()));
        }

        contentBox.getChildren().addAll(topBar, detailLayout);
        VBox.setVgrow(contentBox, Priority.ALWAYS);

        if (!thumbnailsEnabled()) {
            data.imdbLoading = false;
            data.seriesPosterNode = null;
            return;
        }
        loadSeriesPosterImage(data);
        if (data.imdbLoaded && !data.thumbnailMetadataAttempted && isBlank(resolveSeriesPosterUrl(data))) {
            data.imdbLoaded = false;
            data.imdbLoading = true;
            applySeasonInfoToHeader(data);
            lazyLoadImdb(data, null);
        } else if (!data.imdbLoaded && !data.imdbLoading) {
            data.imdbLoading = true;
            applySeasonInfoToHeader(data);
            lazyLoadImdb(data, null);
        } else {
            applySeasonInfoToHeader(data);
        }
    }

    private VBox createSeriesDetailPanel(SeriesPanelData data) {
        boolean compact = !thumbnailsEnabled();
        VBox panel = new VBox(compact ? 8 : 12);
        panel.getStyleClass().add("watching-now-series-info-panel");
        if (compact) {
            panel.getStyleClass().add("watching-now-series-info-panel-compact");
        }
        panel.setMinWidth(260);
        panel.setPrefWidth(330);
        panel.setMaxWidth(380);

        StackPane posterWrap = null;
        if (thumbnailsEnabled()) {
            ImageView poster = SeriesCardUiSupport.createFitPoster(resolveSeriesPosterUrl(data), 260, 390, WATCHING_NOW_CACHE);
            data.seriesPosterNode = poster;
            posterWrap = new StackPane(poster);
            posterWrap.getStyleClass().add("watching-now-series-poster-wrap");
            posterWrap.setAlignment(Pos.CENTER);
            posterWrap.setMaxWidth(Double.MAX_VALUE);
        } else {
            data.seriesPosterNode = null;
        }

        VBox details = new VBox(6);
        details.getStyleClass().add("watching-now-series-metadata");
        details.setMinWidth(0);
        details.setMaxWidth(Double.MAX_VALUE);

        data.titleNode = new Label(firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle));
        data.titleNode.getStyleClass().add("watching-now-series-detail-title");
        data.titleNode.setWrapText(true);
        data.titleNode.setMinWidth(0);
        data.titleNode.setMaxWidth(Double.MAX_VALUE);

        Label account = new Label(data.account.getAccountName());
        account.getStyleClass().add("watching-now-series-account");
        account.setWrapText(true);
        account.setMinWidth(0);
        account.setMaxWidth(Double.MAX_VALUE);

        details.getChildren().addAll(data.titleNode, account);
        data.ratingNode = new Label();
        data.genreNode = new Label();
        data.releaseNode = new Label();
        data.plotNode = new Label();
        data.plotNode.setWrapText(true);
        data.plotNode.setMinWidth(0);
        data.plotNode.setMaxWidth(Double.MAX_VALUE);
        for (Label label : List.of(data.ratingNode, data.genreNode, data.releaseNode, data.plotNode)) {
            label.getStyleClass().add("watching-now-series-meta-line");
            label.setWrapText(true);
            label.setMinWidth(0);
            label.setMaxWidth(Double.MAX_VALUE);
        }
        addImdbHeaderNodes(details, data);
        addSeasonMetadataText(details, data);

        if (posterWrap != null) {
            panel.getChildren().add(posterWrap);
        }
        panel.getChildren().add(details);
        return panel;
    }

    private VBox createSeriesEpisodesPanel(SeriesPanelData data) {
        VBox panel = new VBox(12);
        panel.getStyleClass().add("watching-now-episodes-panel");
        panel.setMinWidth(0);
        panel.setMaxWidth(Double.MAX_VALUE);

        Label title = new Label(I18n.tr("autoEpisodes"));
        title.getStyleClass().add("watching-now-episodes-title");

        Label seriesTitle = new Label(firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle));
        seriesTitle.getStyleClass().add("watching-now-episodes-series-title");
        seriesTitle.setWrapText(true);
        seriesTitle.setMinWidth(0);

        VBox titleText = new VBox(2, title, seriesTitle);
        titleText.setMinWidth(0);
        titleText.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleText, Priority.ALWAYS);

        MenuButton bingeWatch = createSeriesBingeWatchButton(data);

        HBox titleRow = new HBox(10, titleText, bingeWatch);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMinWidth(0);
        titleRow.setMaxWidth(Double.MAX_VALUE);

        PillBar<String> seasonPillBar = new PillBar<>(I18n::formatTabNumberLabel, season -> season);
        seasonPillBar.getStyleClass().add("watching-now-season-pill-bar");
        data.seasonPillBar = seasonPillBar;

        VBox episodeCards = new VBox(thumbnailsEnabled() ? 10 : 6);
        episodeCards.getStyleClass().add("watching-now-episode-card-list");
        episodeCards.setMinWidth(0);
        episodeCards.setMaxWidth(Double.MAX_VALUE);
        episodeCards.setFillWidth(true);
        data.episodeCardsContainer = episodeCards;
        LoadingStateView loadingNode = createSeriesEpisodeLoadingNode(I18n.tr("autoLoadingIMDbDetails"));
        loadingNode.setVisible(false);
        loadingNode.setManaged(false);
        data.episodeLoadingNode = loadingNode;

        seasonPillBar.selectedItemProperty().addListener((_, _, season) -> {
            renderSeasonEpisodeCards(data, season);
            updateSeriesBingeWatchButton(data);
        });
        populateSeasonPills(seasonPillBar, data);
        updateSeriesBingeWatchButton(data);

        panel.getChildren().addAll(titleRow, seasonPillBar, episodeCards);
        return panel;
    }

    private LoadingStateView createSeriesEpisodeLoadingNode(String message) {
        LoadingStateView loadingNode = new LoadingStateView(message, SERIES_EPISODE_LOADING_INDICATOR_SIZE);
        loadingNode.getStyleClass().add("series-inline-loading-state");
        loadingNode.setAlignment(Pos.CENTER);
        loadingNode.setMinHeight(SERIES_EPISODE_LOADING_PANEL_HEIGHT);
        loadingNode.setPrefHeight(SERIES_EPISODE_LOADING_PANEL_HEIGHT);
        loadingNode.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return loadingNode;
    }

    private void setSeriesEpisodeLoadingOverlayVisible(SeriesPanelData data, boolean visible, String message) {
        if (data == null || data.episodeCardsContainer == null || data.episodeLoadingNode == null) {
            return;
        }
        data.episodeLoadingVisible = visible;
        if (message != null) {
            data.episodeLoadingNode.setMessage(message);
        }
        syncSeriesEpisodeLoadingNode(data);
    }

    private void syncSeriesEpisodeLoadingNode(SeriesPanelData data) {
        if (data == null || data.episodeCardsContainer == null || data.episodeLoadingNode == null) {
            return;
        }
        data.episodeLoadingNode.setVisible(data.episodeLoadingVisible);
        data.episodeLoadingNode.setManaged(data.episodeLoadingVisible);
        if (data.episodeLoadingVisible && !data.episodeCardsContainer.getChildren().contains(data.episodeLoadingNode)) {
            data.episodeCardsContainer.getChildren().add(0, data.episodeLoadingNode);
        } else if (!data.episodeLoadingVisible) {
            data.episodeCardsContainer.getChildren().remove(data.episodeLoadingNode);
        }
    }

    private void applySeriesDetailLayoutSizing(VBox detailsPanel, VBox episodesPanel, double availableWidth) {
        if (detailsPanel == null || episodesPanel == null) {
            return;
        }
        double width = Math.max(320, availableWidth <= 0 ? 960 : availableWidth);
        boolean stacked = width < 840;
        if (stacked) {
            double panelWidth = Math.max(280, width - 4);
            detailsPanel.setPrefWidth(panelWidth);
            detailsPanel.setMaxWidth(Double.MAX_VALUE);
            episodesPanel.setPrefWidth(panelWidth);
            return;
        }
        if (!thumbnailsEnabled()) {
            double detailsWidth = Math.min(290, Math.max(240, width * 0.22));
            detailsPanel.setPrefWidth(detailsWidth);
            detailsPanel.setMaxWidth(310);
            episodesPanel.setPrefWidth(Math.max(480, width - detailsWidth - 18));
            return;
        }
        double detailsWidth = Math.min(340, Math.max(280, width * 0.32));
        detailsPanel.setPrefWidth(detailsWidth);
        detailsPanel.setMaxWidth(380);
        episodesPanel.setPrefWidth(Math.max(420, width - detailsWidth - 18));
    }

    private void applyPlainSeriesDetailLayoutSizing(VBox episodesPanel, double availableWidth) {
        if (episodesPanel == null) {
            return;
        }
        double width = Math.max(320, availableWidth <= 0 ? 960 : availableWidth);
        episodesPanel.setPrefWidth(Math.max(320, width - 4));
        episodesPanel.setMaxWidth(Double.MAX_VALUE);
    }

    private MenuButton createSeriesBingeWatchButton(SeriesPanelData data) {
        MenuButton button = new MenuButton();
        button.getStyleClass().setAll("button");
        button.getStyleClass().add("watching-now-binge-button");
        button.setFocusTraversable(true);
        button.setMinWidth(Region.USE_PREF_SIZE);
        button.setMaxWidth(Region.USE_PREF_SIZE);
        button.setOnShowing(event -> {
            ContextMenu menu = button.getContextMenu();
            if (menu != null && !menu.getStyleClass().contains("binge-watch-context-menu")) {
                menu.getStyleClass().add("binge-watch-context-menu");
            }
        });
        data.bingeWatchButton = button;
        updateSeriesBingeWatchButton(data);
        return button;
    }

    private void updateSeriesBingeWatchButton(SeriesPanelData data) {
        if (data == null || data.bingeWatchButton == null) {
            return;
        }
        String season = selectedSeason(data);
        data.bingeWatchButton.setText(buildSeriesBingeWatchMenuLabel(season));
        data.bingeWatchButton.getItems().clear();
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.getStyleClass().add("binge-watch-menu-item");
            playerItem.setOnAction(event -> bingeWatchSeason(data, season, option.playerPath()));
            data.bingeWatchButton.getItems().add(playerItem);
        }
        data.bingeWatchButton.setDisable(buildSeasonChannels(data, season).isEmpty());
    }

    private void reloadSeriesDetailFromServer(SeriesPanelData data, Button reloadButton) {
        if (data == null || data.account == null || data.state == null || reloadButton == null) {
            return;
        }
        reloadButton.setDisable(true);
        reloadButton.setText(I18n.tr("autoReloading"));
        setSeriesEpisodeLoadingOverlayVisible(data, true,
                I18n.tr("autoLoadingEpisodesFor", firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle)));
        new Thread(() -> {
            EpisodeList refreshed = null;
            RuntimeException failure = null;
            try {
                refreshed = SeriesEpisodeService.getInstance().reloadEpisodesFromPortal(
                        data.account,
                        data.state.getCategoryId(),
                        data.state.getSeriesId(),
                        () -> false
                );
            } catch (RuntimeException ex) {
                failure = ex;
            }
            EpisodeList finalRefreshed = refreshed == null ? new EpisodeList() : refreshed;
            RuntimeException finalFailure = failure;
            Platform.runLater(() -> {
                reloadButton.setDisable(false);
                reloadButton.setText(I18n.tr("autoReloadFromServer"));
                if (finalFailure != null) {
                    setSeriesEpisodeLoadingOverlayVisible(data, false, null);
                    showErrorAlert(I18n.tr("autoFailed") + ": " + finalFailure.getMessage());
                    return;
                }
                applyReloadedEpisodesToPanel(data, finalRefreshed);
                showSeriesDetail(data);
            });
        }, "watching-now-series-detail-reload").start();
    }

    private void applyReloadedEpisodesToPanel(SeriesPanelData data, EpisodeList refreshed) {
        if (data == null) {
            return;
        }
        EpisodeList safeList = refreshed == null ? new EpisodeList() : refreshed;
        data.episodeList = safeList;
        data.episodes.clear();
        data.episodes.addAll(mapEpisodesFromCache(data.account, data.state, safeList));
        imdbCacheByPanelKey.remove(panelCacheKey(data.account, data.state));
        data.imdbLoaded = false;
        data.imdbLoading = false;
        data.thumbnailMetadataAttempted = false;
        loadSeriesListPosterImage(data);
    }

    private String seriesPaneKey(SeriesPanelData data) {
        if (data == null || data.account == null || data.state == null) {
            return "";
        }
        return safe(data.account.getDbId()) + "|" + safe(data.state.getCategoryId()) + "|" + safe(data.state.getSeriesId());
    }

    private String buildSeriesPaneTitle(SeriesPanelData data) {
        String name = firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle, data.state.getSeriesId());
        return name + " [" + data.account.getAccountName() + "]";
    }

    private void applySeasonInfoToHeader(SeriesPanelData data) {
        VBox details = (VBox) data.titleNode.getParent();
        clearSeasonHeaderDetails(details, data);
        data.titleNode.setText(firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle));
        addImdbHeaderNodes(details, data);
        addSeasonMetadataText(details, data);
    }

    private void clearSeasonHeaderDetails(VBox details, SeriesPanelData data) {
        details.getChildren().removeAll(data.ratingNode, data.genreNode, data.releaseNode, data.plotNode);
        if (data.imdbLoadingNode != null) {
            details.getChildren().remove(data.imdbLoadingNode);
        }
        if (data.imdbBadgeNode != null) {
            details.getChildren().remove(data.imdbBadgeNode);
        }
    }

    private void addImdbHeaderNodes(VBox details, SeriesPanelData data) {
        String ratingValue = data.seasonInfo.optString("rating", "");
        if (!isBlank(ratingValue)) {
            data.ratingNode.setText(I18n.tr("autoImdbPrefix", ratingValue));
        }
        String imdbUrl = data.seasonInfo.optString("imdbUrl", "");
        data.imdbBadgeNode = SeriesCardUiSupport.createImdbRatingPill(ratingValue, imdbUrl);
        if (data.imdbBadgeNode != null) {
            details.getChildren().add(data.imdbBadgeNode);
        }
        if (data.imdbLoading && !data.imdbLoaded) {
            if (data.imdbLoadingNode == null) {
                data.imdbLoadingNode = createImdbLoadingNode();
            }
            details.getChildren().add(data.imdbLoadingNode);
        }
    }

    private HBox createImdbLoadingNode() {
        return new LoadingStateView(I18n.tr("autoLoadingIMDbDetails"), 14);
    }

    private void addSeasonMetadataText(VBox details, SeriesPanelData data) {
        String genre = data.seasonInfo.optString("genre", "");
        if (!isBlank(genre)) {
            data.genreNode.setText(I18n.tr("autoGenrePrefix", genre));
            details.getChildren().add(data.genreNode);
        }
        String releaseDate = data.seasonInfo.optString(KEY_RELEASE_DATE, "");
        if (!isBlank(releaseDate)) {
            data.releaseNode.setText(I18n.tr("autoReleasePrefix", shortDateOnly(releaseDate)));
            details.getChildren().add(data.releaseNode);
        }
        String plot = data.seasonInfo.optString("plot", "");
        if (!isBlank(plot)) {
            data.plotNode.setText(plot);
            details.getChildren().add(data.plotNode);
        }
    }

    private void populateSeasonPills(PillBar<String> seasonPillBar, SeriesPanelData data) {
        if (seasonPillBar == null || data == null) {
            return;
        }
        String selectedSeason = firstNonBlank(safe(seasonPillBar.getSelectedItem()), normalizeNumber(data.state.getSeason()));
        data.seasonCardsBySeason.clear();
        data.watchingLabels.clear();
        data.selectedEpisodeCard = null;

        List<String> seasons = seasonKeys(data);
        seasonPillBar.setItems(seasons);
        if (!isBlank(selectedSeason) && seasons.contains(selectedSeason)) {
            seasonPillBar.setSelectedItem(selectedSeason);
        } else if (!seasons.isEmpty()) {
            seasonPillBar.setSelectedItem(seasons.getFirst());
        }
        renderSeasonEpisodeCards(data, seasonPillBar.getSelectedItem());
    }

    private List<String> seasonKeys(SeriesPanelData data) {
        List<String> seasons = new ArrayList<>(episodesBySeason(data).keySet());
        seasons.sort(Comparator.comparingInt(s -> parseNumberOrDefault(s, 1)));
        return seasons;
    }

    private Map<String, List<WatchingEpisode>> episodesBySeason(SeriesPanelData data) {
        Map<String, List<WatchingEpisode>> bySeason = new LinkedHashMap<>();
        if (data != null) {
            for (WatchingEpisode episode : data.episodes) {
                String season = normalizeNumber(episode.season);
                if (isBlank(season)) season = "1";
                bySeason.computeIfAbsent(season, ignored -> new ArrayList<>()).add(episode);
            }
        }
        if (bySeason.isEmpty()) {
            bySeason.put("1", List.of());
        }
        return bySeason;
    }

    private void renderSeasonEpisodeCards(SeriesPanelData data, String season) {
        if (data == null || data.episodeCardsContainer == null) {
            return;
        }
        String selectedSeason = isBlank(season) ? "1" : season;
        List<WatchingEpisode> episodes = episodesBySeason(data).getOrDefault(selectedSeason, List.of());
        data.selectedEpisodeCard = null;
        VBox cards = buildEpisodeCards(data, FXCollections.observableArrayList(episodes));
        cards.getStyleClass().add("watching-now-season-card-group");
        data.seasonCardsBySeason.put(selectedSeason, cards);
        data.episodeCardsContainer.getChildren().setAll(cards);
        syncSeriesEpisodeLoadingNode(data);
    }

    private VBox buildEpisodeCards(SeriesPanelData data, javafx.collections.ObservableList<WatchingEpisode> items) {
        VBox container = new VBox(thumbnailsEnabled() ? 10 : 6);
        container.setPadding(Insets.EMPTY);
        container.setFillWidth(true);
        VBox.setVgrow(container, Priority.ALWAYS);
        if (items == null || items.isEmpty()) {
            container.getChildren().add(new Label(I18n.tr("autoNoEpisodesFound")));
            return container;
        }
        for (WatchingEpisode episode : items) {
            VBox card = createEpisodeCard(data, episode);
            container.getChildren().add(card);
            if (episode.watched) {
                setSelectedEpisodeCard(data, card);
            }
        }
        return container;
    }

    private String selectedSeason(SeriesPanelData data) {
        if (data == null) {
            return "1";
        }
        String selected = data.seasonPillBar == null ? "" : safe(data.seasonPillBar.getSelectedItem());
        if (!isBlank(selected)) {
            return selected;
        }
        String stateSeason = normalizeNumber(data.state == null ? "" : data.state.getSeason());
        if (!isBlank(stateSeason)) {
            return stateSeason;
        }
        List<String> seasons = seasonKeys(data);
        return seasons.isEmpty() ? "1" : seasons.getFirst();
    }

    private void bingeWatchSeason(SeriesPanelData data, String season, String playerPath) {
        if (data == null || data.account == null || data.state == null
                || isBlank(data.account.getDbId()) || isBlank(data.state.getSeriesId())) {
            return;
        }
        String normalizedSeason = normalizeNumber(firstNonBlank(season, "1"));
        List<Channel> seasonEpisodes = buildSeasonChannels(data, normalizedSeason);
        if (seasonEpisodes.isEmpty()) {
            showErrorAlert(BINGE_WATCH_FAILED_PREFIX + "no episodes were found for the selected season.");
            return;
        }
        String startupFailureMessage = "Unable to start the local binge watch server on "
                + ServerUrlUtil.getLocalServerUrl()
                + ". Open Configuration, confirm the port is free, click Start Server, then try again.";
        if (!UiServerUrlUtil.ensureServerForWebPlayback(startupFailureMessage)) {
            return;
        }
        data.account.setAction(Account.AccountAction.series);
        SeriesWatchState watchState = SeriesWatchStateService.getInstance()
                .getSeriesLastWatched(data.account.getDbId(), data.state.getCategoryId(), data.state.getSeriesId());
        String token = BingeWatchService.getInstance().createSession(
                data.account,
                data.state.getSeriesId(),
                data.state.getCategoryId(),
                normalizedSeason,
                seasonEpisodes,
                watchState
        );
        if (isBlank(token)) {
            showErrorAlert(BINGE_WATCH_FAILED_PREFIX + "unable to prepare the season playlist.");
            return;
        }
        Channel bingeWatchChannel = new Channel();
        bingeWatchChannel.setChannelId(data.state.getSeriesId());
        bingeWatchChannel.setName(firstNonBlank(
                data.seasonInfo.optString("name", ""),
                data.seriesTitle,
                buildSeriesBingeWatchMenuLabel(normalizedSeason)
        ));
        bingeWatchChannel.setSeason(normalizedSeason);
        bingeWatchChannel.setLogo(resolveSeriesPosterUrl(data));
        PlaybackUIService.playDirectUrl(
                playerPath,
                BingeWatchService.getInstance().buildPlaylistUrl(token),
                "Binge watch playback failed: ",
                data.account,
                bingeWatchChannel
        );
    }

    private String buildSeriesBingeWatchMenuLabel(String season) {
        int seasonNumber = parseNumberOrDefault(firstNonBlank(season, "1"), 1);
        return String.format(Locale.ROOT, "Binge Watch S%02d", Math.max(seasonNumber, 1));
    }

    private List<Channel> buildSeasonChannels(SeriesPanelData data, String season) {
        if (data == null || data.episodes == null) {
            return List.of();
        }
        String normalizedSeason = normalizeNumber(firstNonBlank(season, "1"));
        List<Channel> channels = new ArrayList<>();
        for (WatchingEpisode episode : data.episodes) {
            if (episode == null || episode.channel == null) {
                continue;
            }
            String episodeSeason = normalizeNumber(firstNonBlank(episode.season, "1"));
            if (!normalizedSeason.equals(episodeSeason) || isBlank(episode.channel.getChannelId())) {
                continue;
            }
            Channel channel = new Channel();
            channel.setChannelId(episode.channel.getChannelId());
            channel.setName(firstNonBlank(episode.title, episode.channel.getName()));
            channel.setCmd(episode.channel.getCmd());
            channel.setSeason(episode.season);
            channel.setEpisodeNum(episode.episodeNum);
            channel.setLogo(firstNonBlank(episode.imageUrl, episode.channel.getLogo()));
            channels.add(channel);
        }
        return channels;
    }

    private VBox createEpisodeCard(SeriesPanelData data, WatchingEpisode row) {
        boolean compact = !thumbnailsEnabled();
        VBox root = new VBox(compact ? 4 : 8);
        root.setPadding(compact ? new Insets(7, 10, 7, 10) : new Insets(10));
        root.getStyleClass().add("uiptv-card");
        root.getStyleClass().add("watching-now-episode-card");
        if (compact) {
            root.getStyleClass().add("watching-now-episode-card-compact");
        }
        root.setFocusTraversable(true);
        root.setMinWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);

        HBox badges = new HBox(4);
        badges.setAlignment(Pos.TOP_RIGHT);

        Label watching = new Label(I18n.tr("autoWatching"));
        watching.getStyleClass().add("drm-badge");
        watching.setMinWidth(Region.USE_PREF_SIZE);
        watching.setMaxWidth(Double.MAX_VALUE);
        watching.setVisible(row.watched);
        watching.setManaged(row.watched);
        badges.getChildren().add(watching);

        // Store the watching label in the episode object or map for later access
        data.watchingLabels.put(row, watching);

        ContextMenu episodeMenu = addEpisodeContextMenu(data, row, root);

        Button play = new PlayMenuButton(I18n.tr("autoPlay2"));
        play.getStyleClass().add("episode-play-button");
        play.setOnAction(event -> {
            event.consume();
            setSelectedEpisodeCard(data, root);
            showEpisodeContextMenu(episodeMenu, root, data, row);
        });
        badges.getChildren().add(play);

        Label title = new Label(buildEpisodeDisplayTitle(row.season, row.episodeNum, row.title));
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinWidth(0);
        title.setMinHeight(Region.USE_PREF_SIZE);
        title.getStyleClass().add(STRONG_LABEL);

        List<Label> cardLabels = new ArrayList<>();
        cardLabels.add(title);

        if (compact) {
            HBox titleRow = new HBox(8, title, badges);
            titleRow.setAlignment(Pos.TOP_LEFT);
            titleRow.setMinWidth(0);
            titleRow.setMaxWidth(Double.MAX_VALUE);
            HBox.setHgrow(title, Priority.ALWAYS);
            root.getChildren().add(titleRow);
        } else {
            HBox top = new HBox(10);
            top.setAlignment(Pos.TOP_LEFT);

            ImageView poster = SeriesCardUiSupport.createFitPoster(row.imageUrl, 96, 136, WATCHING_NOW_CACHE);
            StackPane posterWrap = new StackPane(poster);
            posterWrap.setAlignment(Pos.CENTER);
            posterWrap.setMinWidth(110);
            posterWrap.setPrefWidth(110);

            VBox text = new VBox(4);
            text.setMaxWidth(Double.MAX_VALUE);
            text.setFillWidth(true);
            HBox.setHgrow(text, Priority.ALWAYS);

            HBox actionRow = new HBox();
            actionRow.setAlignment(Pos.TOP_RIGHT);
            Region actionSpacer = new Region();
            HBox.setHgrow(actionSpacer, Priority.ALWAYS);
            actionRow.getChildren().addAll(actionSpacer, badges);

            text.getChildren().addAll(actionRow, title);
            addEpisodeMetadataLabels(text, cardLabels, row);
            top.getChildren().addAll(posterWrap, text);
            root.getChildren().add(top);
        }
        if (compact) {
            FlowPane metadataRow = new FlowPane(8, 3);
            metadataRow.getStyleClass().add("watching-now-episode-compact-meta");
            addEpisodeMetadataLabels(metadataRow, cardLabels, row);
            if (!metadataRow.getChildren().isEmpty()) {
                root.getChildren().add(metadataRow);
            }
        }
        addEpisodePlotLabel(root, cardLabels, row);
        root.getProperties().put(KEY_CARD_LABELS, cardLabels);
        root.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                playEpisode(data, row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                setSelectedEpisodeCard(data, root);
            }
        });
        root.setOnKeyPressed(event -> {
            switch (event.getCode()) {
                case ENTER, SPACE -> {
                    playEpisode(data, row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                    event.consume();
                }
                default -> {
                }
            }
        });
        return root;
    }

    private void addEpisodeMetadataLabels(Pane target, List<Label> cardLabels, WatchingEpisode row) {
        if (target == null || cardLabels == null || row == null) {
            return;
        }
        if (!isBlank(row.rating)) {
            Label rating = new Label(I18n.tr("autoRatingPrefix", row.rating));
            rating.setMinWidth(0);
            rating.getStyleClass().add("watching-now-episode-meta-label");
            target.getChildren().add(rating);
            cardLabels.add(rating);
        }
        if (!isBlank(row.releaseDate)) {
            Label release = new Label(I18n.tr("autoReleasePrefix", shortDateOnly(row.releaseDate)));
            release.setMinWidth(0);
            release.getStyleClass().add("watching-now-episode-meta-label");
            target.getChildren().add(release);
            cardLabels.add(release);
        }
    }

    private void addEpisodePlotLabel(VBox root, List<Label> cardLabels, WatchingEpisode row) {
        if (root == null || cardLabels == null || row == null || isBlank(row.plot)) {
            return;
        }
        Label plot = new Label(row.plot);
        plot.setWrapText(true);
        plot.setMaxWidth(Double.MAX_VALUE);
        plot.setMinWidth(0);
        plot.setMinHeight(Region.USE_PREF_SIZE);
        plot.getStyleClass().add("watching-now-episode-plot");
        root.getChildren().add(plot);
        cardLabels.add(plot);
    }

    private void setSelectedSeriesCard(HBox current) {
        if (current == null) {
            return;
        }
        if (selectedSeriesCard != null && selectedSeriesCard != current) {
            applyCardSelection(selectedSeriesCard, false);
        }
        applyCardSelection(current, true);
        selectedSeriesCard = current;
    }

    private void setSelectedEpisodeCard(SeriesPanelData data, VBox current) {
        if (data == null || current == null) {
            return;
        }
        if (data.selectedEpisodeCard != null && data.selectedEpisodeCard != current) {
            applyCardSelection(data.selectedEpisodeCard, false);
        }
        applyCardSelection(current, true);
        data.selectedEpisodeCard = current;
    }

    @SuppressWarnings("unchecked")
    private void applyCardSelection(Pane card, boolean selected) {
        if (card == null) {
            return;
        }
        if (selected) {
            card.getStyleClass().add("selected-card");
        } else {
            card.getStyleClass().remove("selected-card");
        }
        Object labelsObj = card.getProperties().get(KEY_CARD_LABELS);
        if (labelsObj instanceof List<?> labels) {
            for (Object labelObj : labels) {
                if (labelObj instanceof Label label) {
                    applyLabelSelection(label, selected);
                }
            }
        }
        Object linksObj = card.getProperties().get("cardLinks");
        if (linksObj instanceof List<?> links) {
            for (Object linkObj : links) {
                if (linkObj instanceof Hyperlink link) {
                    applyHyperlinkSelection(link, selected);
                }
            }
        }
    }

    private void applyLabelSelection(Label label, boolean selected) {
        if (label == null) {
            return;
        }
        if (selected) {
            label.getStyleClass().add("selected-card-text");
        } else {
            label.getStyleClass().remove("selected-card-text");
        }
    }

    private void applyHyperlinkSelection(Hyperlink link, boolean selected) {
        if (link == null) {
            return;
        }
        if (selected) {
            link.getStyleClass().add("selected-card-link");
        } else {
            link.getStyleClass().remove("selected-card-link");
        }
    }

    private ContextMenu addEpisodeContextMenu(SeriesPanelData data, WatchingEpisode item, Pane target) {
        ContextMenu rowMenu = new ContextMenu();
        rowMenu.getStyleClass().add("episode-context-menu");
        UiI18n.preparePopupControl(rowMenu, target);
        if (item == null) {
            return rowMenu;
        }
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);
        target.setOnContextMenuRequested(event -> {
            showEpisodeContextMenu(rowMenu, target, data, item, event.getScreenX(), event.getScreenY());
            event.consume();
        });
        return rowMenu;
    }

    private void showEpisodeContextMenu(ContextMenu rowMenu, Pane target, SeriesPanelData data, WatchingEpisode item) {
        double x = target.localToScreen(target.getBoundsInLocal()).getMinX() + target.getWidth() - 8;
        double y = target.localToScreen(target.getBoundsInLocal()).getMinY() + 8;
        showEpisodeContextMenu(rowMenu, target, data, item, x, y);
    }

    private void showEpisodeContextMenu(ContextMenu rowMenu,
                                        Pane target,
                                        SeriesPanelData data,
                                        WatchingEpisode item,
                                        double screenX,
                                        double screenY) {
        populateEpisodeContextMenu(rowMenu, data, item);
        if (!rowMenu.getItems().isEmpty()) {
            rowMenu.show(target, screenX, screenY);
        }
    }

    private void populateEpisodeContextMenu(ContextMenu rowMenu, SeriesPanelData data, WatchingEpisode item) {
        rowMenu.getItems().clear();
        if (!item.watched) {
            MenuItem watchingNow = new MenuItem(I18n.tr("autoWatchingNow"));
            watchingNow.getStyleClass().add(EPISODE_MENU_ITEM);
            watchingNow.setOnAction(e -> {
                markEpisodeAsWatched(item);
                updateWatchingStatusUI(data, item);
            });
            rowMenu.getItems().add(watchingNow);
            rowMenu.getItems().add(new SeparatorMenuItem());
        }
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.getStyleClass().add(EPISODE_MENU_ITEM);
            playerItem.setOnAction(e -> playEpisode(data, item, option.playerPath()));
            rowMenu.getItems().add(playerItem);
        }
        if (item.watched) {
            rowMenu.getItems().add(new SeparatorMenuItem());
            MenuItem removeWatchingNow = new MenuItem(I18n.tr("autoRemoveWatchingNow"));
            removeWatchingNow.getStyleClass().add("danger-menu-item");
            removeWatchingNow.getStyleClass().add(EPISODE_MENU_ITEM);
            removeWatchingNow.setOnAction(e -> {
                clearWatchedMarker(item);
                clearWatchingStatusUI(data);
            });
            rowMenu.getItems().add(removeWatchingNow);
        }
    }

    private void markEpisodeAsWatched(WatchingEpisode item) {
        if (item == null) {
            return;
        }
        new Thread(() -> {
            item.account.setAction(Account.AccountAction.series);
            SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                    item.account,
                    item.state.getCategoryId(),
                    item.state.getSeriesId(),
                    item.channel.getChannelId(),
                    item.title,
                    item.season,
                    item.episodeNum
            );
        }, "watching-now-mark-watched").start();
    }

    private void clearWatchedMarker(WatchingEpisode item) {
        if (item == null || isBlank(item.account.getDbId())) {
            return;
        }
        new Thread(() -> SeriesWatchStateService.getInstance().clearSeriesLastWatched(
                item.account.getDbId(),
                item.state.getCategoryId(),
                item.state.getSeriesId()
        ), "watching-now-clear-watched").start();
    }

    private void playEpisode(SeriesPanelData data, WatchingEpisode item, String playerPath) {
        if (item == null || item.channel == null || item.account == null) {
            return;
        }

        // Optimistically update UI
        updateWatchingStatusUI(data, item);

        item.account.setAction(Account.AccountAction.series);
        SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                item.account,
                item.state.getCategoryId(),
                item.state.getSeriesId(),
                item.channel.getChannelId(),
                item.title,
                item.season,
                item.episodeNum
        );
        // refreshWatchedStateInstant(item); // No longer needed as we update UI directly
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(item.account, item.channel, playerPath)
                .series(item.state.getSeriesId(), item.state.getCategoryId())
                .categoryId(item.state.getCategoryId())
                .channelId(item.channel.getChannelId())
                .errorPrefix(I18n.tr("autoErrorPlayingEpisodePrefix")));
    }

    private void updateWatchingStatusUI(SeriesPanelData data, WatchingEpisode currentEpisode) {
        if (data == null || currentEpisode == null) return;

        // Hide all watching labels
        for (Map.Entry<WatchingEpisode, Label> entry : data.watchingLabels.entrySet()) {
            WatchingEpisode episode = entry.getKey();
            Label label = entry.getValue();

            boolean isCurrent = episode == currentEpisode;
            episode.watched = isCurrent;

            if (label != null) {
                label.setVisible(isCurrent);
                label.setManaged(isCurrent);
            }
        }
    }

    private void clearWatchingStatusUI(SeriesPanelData data) {
        if (data == null) {
            return;
        }
        for (Map.Entry<WatchingEpisode, Label> entry : data.watchingLabels.entrySet()) {
            WatchingEpisode episode = entry.getKey();
            Label label = entry.getValue();
            if (episode != null) {
                episode.watched = false;
            }
            if (label != null) {
                label.setVisible(false);
                label.setManaged(false);
            }
        }
    }

    private void lazyLoadImdb(SeriesPanelData data, TitledPane pane) {
        if (!thumbnailsEnabled()) {
            data.imdbLoading = false;
            setSeriesEpisodeLoadingOverlayVisible(data, false, null);
            return;
        }
        setSeriesEpisodeLoadingOverlayVisible(data, true, I18n.tr("autoLoadingIMDbDetails"));
        long generation = lifecycleGeneration.get();
        boolean submitted = WatchingNowMetadataExecutor.submit(() -> {
            try {
                if (!isPanelCurrent(data, generation)) {
                    return;
                }
                JSONObject imdb = findImdbWithRetry(data, 3);
                if (imdb != null && isPanelCurrent(data, generation)) {
                    mergeImdbIntoPanel(data, imdb);
                }
            } finally {
                Platform.runLater(() -> {
                    if (isPanelCurrent(data, generation)) {
                        data.imdbLoaded = true;
                        data.imdbLoading = false;
                        data.thumbnailMetadataAttempted = true;
                        applyLoadedImdbToUi(data, pane);
                        setSeriesEpisodeLoadingOverlayVisible(data, false, null);
                    }
                });
            }
        });
        if (!submitted) {
            data.imdbLoading = false;
            setSeriesEpisodeLoadingOverlayVisible(data, false, null);
        }
    }

    private void mergeImdbIntoPanel(SeriesPanelData data, JSONObject imdb) {
        String imdbCover = normalizeImageUrl(imdb.optString(KEY_COVER, ""), data.account);
        if (!isBlank(imdbCover)) {
            data.seasonInfo.put(KEY_COVER, imdbCover);
            imdb.put(KEY_COVER, imdbCover);
        }
        mergeMissing(data.seasonInfo, imdb, "name");
        mergeMissing(data.seasonInfo, imdb, "plot");
        mergeMissing(data.seasonInfo, imdb, "cast");
        mergeMissing(data.seasonInfo, imdb, "director");
        mergeMissing(data.seasonInfo, imdb, "genre");
        mergeMissing(data.seasonInfo, imdb, KEY_RELEASE_DATE);
        mergeMissing(data.seasonInfo, imdb, "rating");
        mergeMissing(data.seasonInfo, imdb, "tmdb");
        mergeMissing(data.seasonInfo, imdb, "imdbUrl");
        enrichEpisodesFromMeta(data.episodes, imdb.optJSONArray("episodesMeta"));
        imdbCacheByPanelKey.put(panelCacheKey(data.account, data.state),
                new ImdbCacheEntry(new JSONObject(data.seasonInfo.toString())));
    }

    private void applyLoadedImdbToUi(SeriesPanelData data, TitledPane pane) {
        if (pane != null) {
            pane.setText(buildSeriesPaneTitle(data));
        }
        if (isBlank(selectedSeriesKey)) {
            updateSeriesListCardInPlace(data);
            return;
        }
        applySeasonInfoToHeader(data);
        loadSeriesPosterImage(data);
        refreshSeasonTables(data);
    }

    private void updateSeriesListCardInPlace(SeriesPanelData data) {
        if (data == null) {
            return;
        }
        if (data.seriesListTitleNode != null) {
            data.seriesListTitleNode.setText(firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle));
        }
        loadSeriesListPosterImage(data);
    }

    private void loadSeriesPosterImage(SeriesPanelData data) {
        String cover = resolveSeriesPosterUrl(data);
        if (isBlank(cover)) {
            return;
        }
        ImageCacheManager.loadImageAsync(cover, WATCHING_NOW_CACHE).thenAccept(img -> {
            if (img != null) {
                Platform.runLater(() -> {
                    if (data.seriesPosterNode != null) {
                        data.seriesPosterNode.setImage(img);
                    }
                });
            }
        });
    }

    private void loadSeriesListPosterImage(SeriesPanelData data) {
        if (data == null || data.seriesListPosterNode == null) {
            return;
        }
        String cover = resolveSeriesPosterUrl(data);
        if (isBlank(cover)) {
            return;
        }
        ImageCacheManager.loadImageAsync(cover, WATCHING_NOW_CACHE).thenAccept(img -> {
            if (img != null) {
                Platform.runLater(() -> {
                    if (data.seriesListPosterNode != null) {
                        data.seriesListPosterNode.setImage(img);
                    }
                });
            }
        });
    }

    private JSONObject findImdbWithRetry(SeriesPanelData data, int maxAttempts) {
        if (data == null) {
            return null;
        }
        List<String> hints = buildImdbFuzzyHints(data);
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JSONObject imdb = ImdbMetadataService.getInstance().findBestEffortDetails(
                        firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle),
                        data.seasonInfo.optString("tmdb", ""),
                        hints
                );
                if (imdb != null) {
                    return imdb;
                }
            } catch (Exception _) {
                // Ignore metadata refresh failures; the current cached panel still renders.
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException _) {
                    // Preserve interruption and stop the staggered refresh loop.
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        return null;
    }

    private List<String> buildImdbFuzzyHints(SeriesPanelData data) {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        addHint(ordered, data.seriesTitle);
        addHint(ordered, data.seasonInfo.optString("name", ""));
        addHint(ordered, data.seasonInfo.optString("plot", ""));
        String year = extractYear(firstNonBlank(data.seasonInfo.optString(KEY_RELEASE_DATE, ""), firstEpisodeRelease(data)));
        if (!isBlank(year)) {
            addHint(ordered, data.seriesTitle + " " + year);
            addHint(ordered, data.seasonInfo.optString("name", "") + " " + year);
        }
        for (WatchingEpisode episode : data.episodes.stream().limit(6).toList()) {
            addHint(ordered, cleanEpisodeTitle(episode.title));
            addHint(ordered, episode.channel != null ? episode.channel.getName() : "");
        }
        return new ArrayList<>(ordered.keySet());
    }

    private void addHint(Map<String, Boolean> ordered, String value) {
        if (ordered == null || isBlank(value)) {
            return;
        }
        String cleaned = value
                .replaceAll("(?i)\\b(4k|8k|uhd|fhd|hd|sd|series|movie|complete)\\b", " ")
                .replaceAll("(?i)\\bs\\d{1,2}e\\d{1,3}\\b", " ")
                .replaceAll("[\\[\\]{}()]+", " ")
                .replaceAll("\\s+", " ")
                .trim();
        if (!isBlank(cleaned) && cleaned.length() >= 2) {
            ordered.putIfAbsent(cleaned, Boolean.TRUE);
        }
    }

    private String firstEpisodeRelease(SeriesPanelData data) {
        if (data == null || data.episodes == null || data.episodes.isEmpty()) {
            return "";
        }
        return data.episodes.stream()
                .map(e -> e.releaseDate)
                .filter(v -> !isBlank(v))
                .findFirst()
                .orElse("");
    }

    private String extractYear(String value) {
        if (isBlank(value)) {
            return "";
        }
        Matcher matcher = Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(value);
        return matcher.find() ? matcher.group() : "";
    }

    private void refreshSeasonTables(SeriesPanelData data) {
        if (data == null || data.seasonPillBar == null) {
            return;
        }
        populateSeasonPills(data.seasonPillBar, data);
    }

    private void enrichEpisodesFromMeta(List<WatchingEpisode> episodes, JSONArray metaRows) {
        if (episodes == null || episodes.isEmpty() || metaRows == null || metaRows.isEmpty()) {
            return;
        }
        EpisodeMetaIndex metaIndex = buildEpisodeMetaIndex(metaRows);
        for (WatchingEpisode episode : episodes) {
            if (episode != null) {
                JSONObject meta = findEpisodeMeta(metaIndex, episode);
                if (meta != null) {
                    String metaLogo = normalizeImageUrl(meta.optString("logo", ""), episode.account);
                    episode.imageUrl = firstNonBlank(metaLogo, episode.imageUrl);
                    episode.plot = firstNonBlank(
                            meta.optString("plot", ""),
                            meta.optString("description", ""),
                            meta.optString("overview", ""),
                            episode.plot
                    );
                    episode.releaseDate = firstNonBlank(episode.releaseDate, meta.optString(KEY_RELEASE_DATE, ""));
                    if (!isBlank(episode.imageUrl)) {
                        episode.channel.setLogo(episode.imageUrl);
                    }
                }
            }
        }
    }

    private EpisodeMetaIndex buildEpisodeMetaIndex(JSONArray metaRows) {
        EpisodeMetaIndex index = new EpisodeMetaIndex();
        for (int i = 0; i < metaRows.length(); i++) {
            JSONObject row = metaRows.optJSONObject(i);
            if (row == null) continue;
            String season = normalizeNumber(row.optString("season", ""));
            String episodeNum = resolveMetaEpisodeNumber(row);
            if (!isBlank(season) && !isBlank(episodeNum)) {
                index.bySeasonEpisode.put(season + ":" + episodeNum, row);
            }
            if (!isBlank(episodeNum)) {
                index.byEpisodeOnly.putIfAbsent(episodeNum, row);
            }
            String title = normalizeTitle(cleanEpisodeTitle(row.optString(KEY_TITLE, "")));
            if (!isBlank(title)) {
                index.byTitle.put(title, row);
            }
            String looseTitle = normalizeTitle(extractLooseEpisodeTitle(row.optString(KEY_TITLE, "")));
            if (!isBlank(looseTitle)) {
                index.byLooseTitle.put(looseTitle, row);
            }
        }
        return index;
    }

    private String resolveMetaEpisodeNumber(JSONObject row) {
        String episodeNum = normalizeNumber(row.optString("episodeNum", ""));
        if (isBlank(episodeNum)) {
            episodeNum = normalizeNumber(inferEpisodeNumberFromTitle(row.optString(KEY_TITLE, "")));
        }
        return episodeNum;
    }

    private JSONObject findEpisodeMeta(EpisodeMetaIndex metaIndex, WatchingEpisode episode) {
        if (metaIndex == null || episode == null) {
            return null;
        }
        String normalizedSeason = normalizeNumber(episode.season);
        String normalizedEpisode = normalizeNumber(firstNonBlank(episode.episodeNum, inferEpisodeNumberFromTitle(episode.title)));
        JSONObject meta = metaIndex.bySeasonEpisode.get(normalizedSeason + ":" + normalizedEpisode);
        if (meta == null) {
            meta = metaIndex.byTitle.get(normalizeTitle(cleanEpisodeTitle(episode.title)));
        }
        if (meta == null) {
            meta = metaIndex.byLooseTitle.get(normalizeTitle(extractLooseEpisodeTitle(episode.title)));
        }
        if (meta == null && !isBlank(normalizedEpisode)) {
            meta = metaIndex.byEpisodeOnly.get(normalizedEpisode);
        }
        return meta;
    }

    private String extractLooseEpisodeTitle(String title) {
        String value = safe(title)
                .replaceAll("(?i)^.*?\\bS\\d{1,2}E\\d{1,3}\\b\\s*[-:]*\\s*", "")
                .replaceAll("(?i)^.*?\\bepisode\\s*\\d+\\b\\s*[-:]*\\s*", "")
                .replaceAll("(?i)^.*?\\b\\d{1,2}x\\d{1,3}\\b\\s*[-:]*\\s*", "")
                .trim();
        return isBlank(value) ? cleanEpisodeTitle(title) : value;
    }

    private String inferEpisodeNumberFromTitle(String title) {
        Matcher m = EPISODE_PATTERN.matcher(safe(title));
        if (m.find()) {
            return normalizeNumber(firstNonBlank(m.group(1), m.group(2), m.group(3)));
        }
        Matcher sxey = SXXEYY_PATTERN.matcher(safe(title));
        if (sxey.find()) {
            return normalizeNumber(sxey.group(2));
        }
        return "";
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
        if (!watchStateListenerRegistered) {
            SeriesWatchStateService.getInstance().addChangeListener(watchStateChangeListener);
            watchStateListenerRegistered = true;
        }
        if (!accountListenerRegistered) {
            AccountService.getInstance().addChangeListener(accountChangeListener);
            accountListenerRegistered = true;
        }
    }

    private void unregisterListeners() {
        if (watchStateListenerRegistered) {
            SeriesWatchStateService.getInstance().removeChangeListener(watchStateChangeListener);
            watchStateListenerRegistered = false;
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

    private void onDataChanged(String accountId, String seriesId) {
        if (!isDisplayable()) {
            dirty = true;
            return;
        }
        // If panelDataByKey is empty (UI not yet rendered), do a full refresh instead of delta
        if (panelDataByKey.isEmpty() || isBlank(accountId) || isBlank(seriesId)) {
            dirty = true;
            scheduleRefreshIfNeeded();
            return;
        }
        refreshSeriesEntryAsync(accountId, seriesId);
    }

    private void onAccountsChanged() {
        // When accounts change (e.g., deleted), force full refresh
        dirty = true;
        scheduleRefreshIfNeeded();
    }

    private void refreshSeriesEntryAsync(String accountId, String seriesId) {
        if (reloadInProgress.get()) {
            reloadQueued.set(true);
            return;
        }
        reloadInProgress.set(true);
        long generation = lifecycleGeneration.get();
        new Thread(() -> {
            try {
                List<SeriesPanelData> updated = buildUpdatedSeriesPanels(accountId, seriesId);
                Platform.runLater(() -> {
                    if (lifecycleGeneration.get() == generation && isDisplayable()) {
                        applySeriesDelta(accountId, seriesId, updated);
                    } else {
                        dirty = true;
                    }
                });
            } finally {
                reloadInProgress.set(false);
                if (reloadQueued.getAndSet(false) || dirty) {
                    scheduleRefreshIfNeeded();
                }
            }
        }, "watching-now-delta-loader").start();
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

    private List<SeriesPanelData> buildUpdatedSeriesPanels(String accountId, String seriesId) {
        List<SeriesPanelData> updated = new ArrayList<>();
        Account account = AccountService.getInstance().getById(accountId);
        if (account == null || isBlank(account.getDbId())) {
            return updated;
        }
        account.setAction(Account.AccountAction.series);
        for (WatchingNowSeriesResolver.SeriesRow row : seriesResolver.resolveForAccount(account)) {
            if (!isBlank(seriesId) && !safe(row.getState().getSeriesId()).equals(safe(seriesId))) {
                continue;
            }
            SeriesPanelData panel = buildPanel(row);
            if (panel != null) {
                updated.add(panel);
            }
        }
        return updated;
    }

    private void applySeriesDelta(String accountId, String seriesId, List<SeriesPanelData> updated) {
        SeriesPanelData renderedPanel = panelDataByKey.get(renderedDetailKey);
        String renderedAccountId = renderedPanel != null && renderedPanel.account != null ? safe(renderedPanel.account.getDbId()) : "";
        String renderedSeriesId = renderedPanel != null && renderedPanel.state != null ? safe(renderedPanel.state.getSeriesId()) : "";
        removeSeriesPanels(accountId, seriesId);
        boolean renderedPanelReused = mergeUpdatedSeriesPanels(updated, renderedPanel, renderedAccountId, renderedSeriesId);
        if (!refreshSelectedSeriesDetail(renderedPanel, renderedPanelReused)) {
            renderCurrentView();
        }
    }

    private void removeSeriesPanels(String accountId, String seriesId) {
        String prefix = safe(accountId) + "|";
        String suffix = "|" + safe(seriesId);
        panelDataByKey.keySet().removeIf(key -> key.startsWith(prefix) && key.endsWith(suffix));
    }

    private boolean mergeUpdatedSeriesPanels(List<SeriesPanelData> updated, SeriesPanelData renderedPanel,
                                             String renderedAccountId, String renderedSeriesId) {
        boolean renderedPanelReused = false;
        for (SeriesPanelData panel : updated) {
            String key = seriesPaneKey(panel);
            if (isRenderedSeriesPanel(panel, renderedPanel, renderedAccountId, renderedSeriesId)) {
                mergePanelInPlace(renderedPanel, panel);
                panelDataByKey.put(key, renderedPanel);
                selectedSeriesKey = key;
                renderedDetailKey = key;
                renderedPanelReused = true;
            } else {
                panelDataByKey.put(key, panel);
            }
        }
        return renderedPanelReused;
    }

    private boolean isRenderedSeriesPanel(SeriesPanelData panel, SeriesPanelData renderedPanel,
                                          String renderedAccountId, String renderedSeriesId) {
        return renderedPanel != null
                && safe(panel.account.getDbId()).equals(renderedAccountId)
                && safe(panel.state.getSeriesId()).equals(renderedSeriesId);
    }

    private boolean refreshSelectedSeriesDetail(SeriesPanelData renderedPanel, boolean renderedPanelReused) {
        if (isBlank(selectedSeriesKey)) {
            return false;
        }
        SeriesPanelData selected = panelDataByKey.get(selectedSeriesKey);
        if (selected != null && selectedSeriesKey.equals(renderedDetailKey)) {
            refreshSelectedDetailInPlace(selected);
            return true;
        }
        if (renderedPanelReused && renderedPanel != null && selectedSeriesKey.equals(renderedDetailKey)) {
            refreshSelectedDetailInPlace(renderedPanel);
            return true;
        }
        return false;
    }

    private void mergePanelInPlace(SeriesPanelData target, SeriesPanelData source) {
        if (target == null || source == null) {
            return;
        }
        target.watchingLabels.clear();
        target.episodes.clear();
        target.episodes.addAll(source.episodes);
        target.episodeList = source.episodeList;
        replaceJson(target.seasonInfo, source.seasonInfo);
        target.imdbLoaded = target.imdbLoaded || source.imdbLoaded;
        target.imdbLoading = target.imdbLoading || source.imdbLoading;
        target.thumbnailMetadataAttempted = target.thumbnailMetadataAttempted || source.thumbnailMetadataAttempted;
        if (thumbnailsEnabled()
                && target.imdbLoaded
                && !target.thumbnailMetadataAttempted
                && isBlank(resolveSeriesPosterUrl(target))) {
            target.imdbLoaded = false;
        }
    }

    private void replaceJson(JSONObject target, JSONObject source) {
        if (target == null || source == null) {
            return;
        }
        List<String> keys = new ArrayList<>();
        java.util.Iterator<String> it = target.keys();
        while (it.hasNext()) {
            keys.add(it.next());
        }
        for (String key : keys) {
            target.remove(key);
        }
        java.util.Iterator<String> srcIt = source.keys();
        while (srcIt.hasNext()) {
            String key = srcIt.next();
            target.put(key, source.opt(key));
        }
    }

    private void refreshSelectedDetailInPlace(SeriesPanelData selected) {
        if (selected == null || !selectedSeriesKey.equals(renderedDetailKey)) {
            return;
        }
        if (selected.titleNode != null) {
            applySeasonInfoToHeader(selected);
        }
        refreshSeriesPosterInPlace(selected);
        if (selected.seasonPillBar != null) {
            refreshSeasonTables(selected);
        }
    }

    private void refreshSeriesPosterInPlace(SeriesPanelData selected) {
        if (selected.seriesPosterNode != null) {
            String cover = resolveSeriesPosterUrl(selected);
            if (!isBlank(cover)) {
                ImageCacheManager.loadImageAsync(cover, WATCHING_NOW_CACHE).thenAccept(img -> {
                    if (img != null) {
                        Platform.runLater(() -> {
                            if (selected.seriesPosterNode != null) {
                                selected.seriesPosterNode.setImage(img);
                            }
                        });
                    }
                });
            }
        }
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

    private String resolveSeriesPosterUrl(SeriesPanelData data) {
        if (data == null) {
            return "";
        }
        String cover = sanitizePosterUrl(normalizeImageUrl(data.seasonInfo.optString(KEY_COVER, ""), data.account));
        if (!isBlank(cover)) {
            return cover;
        }
        if (data.episodeList != null && data.episodeList.getSeasonInfo() != null) {
            cover = sanitizePosterUrl(normalizeImageUrl(data.episodeList.getSeasonInfo().getCover(), data.account));
            if (!isBlank(cover)) {
                return cover;
            }
        }
        return data.episodes.stream()
                .map(e -> sanitizePosterUrl(normalizeImageUrl(e == null ? "" : e.imageUrl, data.account)))
                .filter(s -> !isBlank(s))
                .findFirst()
                .orElseGet(() -> resolveEpisodeListPosterUrl(data));
    }

    private String resolveEpisodeListPosterUrl(SeriesPanelData data) {
        if (data == null || data.episodeList == null || data.episodeList.getEpisodes() == null) {
            return "";
        }
        return data.episodeList.getEpisodes().stream()
                .map(episode -> episode != null && episode.getInfo() != null
                        ? sanitizePosterUrl(normalizeImageUrl(episode.getInfo().getMovieImage(), data.account))
                        : "")
                .filter(s -> !isBlank(s))
                .findFirst()
                .orElse("");
    }

    private String sanitizePosterUrl(String url) {
        if (isBlank(url)) {
            return "";
        }
        if (url.startsWith("data:image/")) {
            int commaIndex = url.indexOf(',');
            int payloadLength = commaIndex >= 0 && commaIndex < url.length() - 1 ? url.length() - commaIndex - 1 : 0;
            if (payloadLength < 512) {
                return "";
            }
        }
        return url;
    }

    private String inferSeason(Episode episode) {
        if (episode == null) return "1";
        String explicit = normalizeNumber(episode.getSeason());
        if (!isBlank(explicit)) return explicit;
        String title = safe(episode.getTitle());
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find()) {
            return normalizeNumber(sxey.group(1));
        }
        Matcher m = SEASON_PATTERN.matcher(title);
        if (m.find()) {
            return normalizeNumber(firstNonBlank(m.group(1), m.group(2), m.group(3)));
        }
        return "1";
    }

    private String inferEpisodeNumber(Episode episode) {
        if (episode == null) return "";
        String explicit = normalizeNumber(episode.getEpisodeNum());
        if (!isBlank(explicit)) return explicit;
        String title = safe(episode.getTitle());
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find()) {
            return normalizeNumber(sxey.group(2));
        }
        Matcher m = EPISODE_PATTERN.matcher(title);
        if (m.find()) {
            return normalizeNumber(firstNonBlank(m.group(1), m.group(2), m.group(3)));
        }
        return "";
    }

    private String cleanEpisodeTitle(String title) {
        String value = safe(title);
        String previous;
        do {
            previous = value;
            value = value
                    .replaceAll("(?i)^\\s*season\\s*\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*s\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*episode\\s*\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*ep\\.?\\s*\\d+\\s*[-:]?\\s*", "")
                    .replaceAll("(?i)^\\s*e\\d+\\s*[-:]?\\s*", "")
                    .trim();
        } while (!value.equals(previous));
        return isGenericEpisodeTitle(value) ? "" : value;
    }

    private String cleanEpisodeTitleWithPlot(String title, String plot) {
        String cleaned = cleanEpisodeTitle(title);
        return stripAppendedPlot(cleaned, plot);
    }

    private String stripAppendedPlot(String title, String plot) {
        if (isBlank(title) || isBlank(plot)) {
            return title;
        }
        String trimmedPlot = plot.trim();
        if (trimmedPlot.length() < 15) {
            return title;
        }
        int idx = indexOfIgnoreCase(title, trimmedPlot);
        if (idx <= 0) {
            return title;
        }
        String before = title.substring(0, idx).trim();
        if (before.isEmpty()) {
            return title;
        }
        before = before.replaceAll("[-:|]+\\s*$", "").trim();
        return before.isEmpty() ? title : before;
    }

    private int indexOfIgnoreCase(String text, String needle) {
        if (text == null || needle == null) {
            return -1;
        }
        return text.toLowerCase(Locale.ENGLISH).indexOf(needle.toLowerCase(Locale.ENGLISH));
    }

    private String normalizeNumber(String value) {
        if (isBlank(value)) return "";
        String parsed = value.replaceAll("\\D", "");
        if (isBlank(parsed)) return "";
        try {
            return String.valueOf(Integer.parseInt(parsed));
        } catch (Exception _) {
            // Invalid numeric labels should behave like an unknown season/episode number.
            return "";
        }
    }

    private int parseNumberOrDefault(String value, int fallback) {
        try {
            if (isBlank(value)) return fallback;
            return Integer.parseInt(value);
        } catch (Exception _) {
            // Invalid numeric labels should fall back to the caller-provided default.
            return fallback;
        }
    }

    private String normalizeTitle(String value) {
        return safe(value).toLowerCase().replaceAll("[^a-z0-9 ]", " ").replaceAll("\\s+", " ").trim();
    }

    private String shortDateOnly(String value) {
        String v = safe(value).trim();
        if (isBlank(v)) {
            return "";
        }
        LocalDate parsed = parseDate(v);
        if (parsed != null) {
            return I18n.formatDate(parsed);
        }
        String extracted = extractDateSubstring(v);
        return !isBlank(extracted) ? extracted : collapseCommaSeparatedDate(v);
    }

    private String buildEpisodeDisplayTitle(String season, String episodeNum, String title) {
        return EpisodeTitleFormatter.buildEpisodeDisplayTitle(season, episodeNum, title);
    }

    private boolean isGenericEpisodeTitle(String title) {
        return EpisodeTitleFormatter.isGenericEpisodeTitle(title);
    }

    private LocalDate parseDate(String value) {
        String input = safe(value).trim();
        if (isBlank(input)) {
            return null;
        }
        LocalDate offsetDate = parseOffsetDate(input);
        if (offsetDate != null) {
            return offsetDate;
        }
        LocalDate exactPatternDate = parseDateWithPatterns(input, new String[]{
                "yyyy-MM-dd",
                "yyyy-MM-dd HH:mm:ss",
                "yyyy-MM-dd HH:mm",
                "MMM d, yyyy",
                "MMMM d, yyyy",
                "d MMM yyyy",
                "d MMMM yyyy",
                "M/d/yyyy",
                "MM/dd/yyyy",
                "d/M/yyyy",
                "dd/MM/yyyy"
        });
        if (exactPatternDate != null) {
            return exactPatternDate;
        }
        LocalDate embeddedIsoDate = parseEmbeddedIsoDate(input);
        if (embeddedIsoDate != null) {
            return embeddedIsoDate;
        }
        return parseEmbeddedMonthDate(input);
    }

    private String extractDateSubstring(String value) {
        String leadingIso = extractLeadingIsoDate(value);
        if (!isBlank(leadingIso)) {
            return leadingIso;
        }
        String matchedDate = firstMatchedDate(value, MONTH_DATE_PATTERN, SLASH_DATE_PATTERN, ISO_DATE_PATTERN);
        return !isBlank(matchedDate) ? matchedDate : "";
    }

    private String extractLeadingIsoDate(String value) {
        if (value.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            return value.substring(0, 10);
        }
        int t = value.indexOf('T');
        if (t > 0) {
            String left = value.substring(0, t);
            if (left.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return left;
            }
        }
        return "";
    }

    private String firstMatchedDate(String value, Pattern... patterns) {
        for (Pattern pattern : patterns) {
            Matcher matcher = pattern.matcher(value);
            if (matcher.find()) {
                return matcher.group();
            }
        }
        return "";
    }

    private String collapseCommaSeparatedDate(String value) {
        if (value.contains(",")) {
            String[] parts = value.split(",");
            if (parts.length >= 2) {
                return parts[0].trim() + ", " + parts[1].trim();
            }
        }
        return value;
    }

    private LocalDate parseOffsetDate(String input) {
        try {
            return OffsetDateTime.parse(input).toLocalDate();
        } catch (Exception _) {
            // Offset timestamps are optional; try the plain date patterns next.
            return null;
        }
    }

    private LocalDate parseDateWithPatterns(String input, String[] patterns) {
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(input, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException _) {
                // Try the next parser below.
            }
        }
        return null;
    }

    private LocalDate parseEmbeddedIsoDate(String input) {
        Matcher iso = ISO_DATE_PATTERN.matcher(input);
        if (!iso.find()) {
            return null;
        }
        try {
            return LocalDate.parse(iso.group(), DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));
        } catch (DateTimeParseException _) {
            // Give up on embedded ISO parsing and continue with month-based formats.
            return null;
        }
    }

    private LocalDate parseEmbeddedMonthDate(String input) {
        Matcher month = MONTH_DATE_PATTERN.matcher(input);
        if (!month.find()) {
            return null;
        }
        String candidate = month.group();
        LocalDate shortMonth = parseDateWithPatterns(candidate, new String[]{"MMM d, yyyy"});
        return shortMonth != null ? shortMonth : parseDateWithPatterns(candidate, new String[]{"MMMM d, yyyy"});
    }

    private String normalizeImageUrl(String imageUrl, Account account) {
        return ImageUrlNormalizer.normalizeImageUrl(imageUrl, account);
    }

    private String firstNonBlank(String... values) {
        if (values == null) return "";
        for (String value : values) {
            if (!isBlank(value)) return value.trim();
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private void mergeMissing(JSONObject target, JSONObject source, String key) {
        if (target == null || source == null || isBlank(key)) {
            return;
        }
        if (isBlank(target.optString(key, "")) && !isBlank(source.optString(key, ""))) {
            target.put(key, source.optString(key, ""));
        }
    }

    private void mergeMissingSeasonInfo(JSONObject target, JSONObject source) {
        if (target == null || source == null) {
            return;
        }
        java.util.Iterator<String> it = source.keys();
        while (it.hasNext()) {
            String key = it.next();
            Object value = source.opt(key);
            if (value == null) {
                continue;
            }
            if (value instanceof String str) {
                if (isBlank(target.optString(key, "")) && !isBlank(str)) {
                    target.put(key, str);
                }
            } else if (!target.has(key)) {
                target.put(key, value);
            }
        }
    }

    private void releaseUiState() {
        lifecycleGeneration.incrementAndGet();
        reloadQueued.set(false);
        refreshScheduled.set(false);
        dirty = true;
        lastListFingerprint = "";
        for (SeriesPanelData panel : panelDataByKey.values()) {
            if (panel != null) {
                panel.clearTransientUiState();
            }
        }
        panelDataByKey.clear();
        imdbCacheByPanelKey.clear();
        selectedSeriesKey = "";
        renderedDetailKey = "";
        selectedSeriesCard = null;
        contentBox.getChildren().clear();
    }

    private boolean isPanelCurrent(SeriesPanelData data, long generation) {
        return data != null
                && lifecycleGeneration.get() == generation
                && Objects.equals(panelDataByKey.get(seriesPaneKey(data)), data);
    }

    private String panelCacheKey(Account account, SeriesWatchState state) {
        if (account == null || state == null) {
            return "";
        }
        return safe(account.getDbId()) + "|" + safe(state.getCategoryId()) + "|" + safe(state.getSeriesId());
    }

    private static final class EpisodeMetaIndex {
        private final Map<String, JSONObject> bySeasonEpisode = new HashMap<>();
        private final Map<String, JSONObject> byTitle = new HashMap<>();
        private final Map<String, JSONObject> byLooseTitle = new HashMap<>();
        private final Map<String, JSONObject> byEpisodeOnly = new HashMap<>();
    }

    /**
     * Simple data class for series list table view
     */
    private static final class SeriesListItem {
        private final javafx.beans.property.SimpleStringProperty seriesTitle;
        private final javafx.beans.property.SimpleStringProperty accountName;
        private final String panelKey;

        SeriesListItem(String seriesTitle, String accountName, String panelKey) {
            this.seriesTitle = new javafx.beans.property.SimpleStringProperty(seriesTitle);
            this.accountName = new javafx.beans.property.SimpleStringProperty(accountName);
            this.panelKey = panelKey;
        }

        javafx.beans.property.StringProperty seriesTitleProperty() {
            return seriesTitle;
        }

        javafx.beans.property.StringProperty accountNameProperty() {
            return accountName;
        }

        String getPanelKey() {
            return panelKey;
        }
    }

    private static final class SeriesCacheInfo {
        private final String seriesTitle;
        private final String seriesPoster;
        private final boolean resolvedFromCache;

        private SeriesCacheInfo(String seriesTitle, String seriesPoster, boolean resolvedFromCache) {
            this.seriesTitle = seriesTitle;
            this.seriesPoster = seriesPoster;
            this.resolvedFromCache = resolvedFromCache;
        }
    }

    private static final class ImdbCacheEntry {
        private final JSONObject seasonInfo;

        private ImdbCacheEntry(JSONObject seasonInfo) {
            this.seasonInfo = seasonInfo == null ? new JSONObject() : seasonInfo;
        }
    }

    private static final class SeriesPanelData {
        private final Account account;
        private final SeriesWatchState state;
        private final String seriesTitle;
        private final JSONObject seasonInfo;
        private final List<WatchingEpisode> episodes;
        private final Map<String, VBox> seasonCardsBySeason = new LinkedHashMap<>();
        private final Map<WatchingEpisode, Label> watchingLabels = new HashMap<>();
        private EpisodeList episodeList;
        private boolean imdbLoaded;
        private boolean imdbLoading;
        private boolean thumbnailMetadataAttempted;
        private Label titleNode;
        private Label ratingNode;
        private Label genreNode;
        private Label releaseNode;
        private Label plotNode;
        private HBox imdbBadgeNode;
        private HBox imdbLoadingNode;
        private ImageView seriesPosterNode;
        private ImageView seriesListPosterNode;
        private Hyperlink seriesListTitleNode;
        private PillBar<String> seasonPillBar;
        private VBox episodeCardsContainer;
        private LoadingStateView episodeLoadingNode;
        private boolean episodeLoadingVisible;
        private MenuButton bingeWatchButton;
        private VBox selectedEpisodeCard;

        private SeriesPanelData(Account account, SeriesWatchState state, String seriesTitle, JSONObject seasonInfo, List<WatchingEpisode> episodes, EpisodeList episodeList) {
            this.account = account;
            this.state = state;
            this.seriesTitle = seriesTitle;
            this.seasonInfo = seasonInfo == null ? new JSONObject() : seasonInfo;
            this.episodes = episodes == null ? new ArrayList<>() : episodes;
            this.episodeList = episodeList == null ? new EpisodeList() : episodeList;
            this.imdbLoaded = false;
            this.imdbLoading = false;
            this.thumbnailMetadataAttempted = false;
        }

        private void clearTransientUiState() {
            watchingLabels.clear();
            seasonCardsBySeason.clear();
            if (seriesPosterNode != null) {
                seriesPosterNode.setImage(null);
            }
            if (seriesListPosterNode != null) {
                seriesListPosterNode.setImage(null);
            }
            if (seasonPillBar != null) {
                seasonPillBar.setItems(List.of());
            }
            if (episodeCardsContainer != null) {
                episodeCardsContainer.getChildren().clear();
            }
            if (bingeWatchButton != null) {
                bingeWatchButton.getItems().clear();
            }
            titleNode = null;
            ratingNode = null;
            genreNode = null;
            releaseNode = null;
            plotNode = null;
            imdbBadgeNode = null;
            imdbLoadingNode = null;
            seriesPosterNode = null;
            seriesListPosterNode = null;
            seriesListTitleNode = null;
            seasonPillBar = null;
            episodeCardsContainer = null;
            episodeLoadingNode = null;
            episodeLoadingVisible = false;
            bingeWatchButton = null;
            selectedEpisodeCard = null;
            episodeList = new EpisodeList();
            episodes.clear();
        }
    }

    private static final class WatchingEpisode {
        private final Account account;
        private final SeriesWatchState state;
        private final Channel channel;
        private final String season;
        private final String episodeNum;
        private final String title;
        private String imageUrl;
        private String plot;
        private String releaseDate;
        private String rating;
        private boolean watched;

        @SuppressWarnings("java:S107")
        private WatchingEpisode(Account account,
                                SeriesWatchState state,
                                Channel channel,
                                String season,
                                String episodeNum,
                                String title,
                                String imageUrl,
                                String plot,
                                String releaseDate,
                                String rating,
                                boolean watched) {
            this.account = account;
            this.state = state;
            this.channel = channel;
            this.season = season;
            this.episodeNum = episodeNum;
            this.title = title;
            this.imageUrl = imageUrl;
            this.plot = plot;
            this.releaseDate = releaseDate;
            this.rating = rating;
            this.watched = watched;
        }
    }
}
