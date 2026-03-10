package com.uiptv.ui;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.*;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.EpisodeTitleFormatter;
import com.uiptv.util.I18n;
import com.uiptv.util.ImageCacheManager;
import com.uiptv.util.ServerUrlUtil;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showConfirmationAlert;

@SuppressWarnings("java:S5843")
public abstract class BaseWatchingNowUI extends VBox {
    private static final String KEY_CARD_LABELS = "cardLabels";
    private static final String KEY_COVER = "cover";
    private static final String KEY_RELEASE_DATE = "releaseDate";
    private static final String KEY_TITLE = "title";
    private static final String MESSAGE_NO_CURRENTLY_WATCHED_SERIES = "autoNoCurrentlyWatchedSeriesFound";
    private static final String STRONG_LABEL = "strong-label";
    private static final String WATCHING_NOW_CACHE = "watching-now";
    private static final Pattern SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b");
    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b|\\b\\d{1,2}x(\\d{1,3})\\b");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},\\s+\\d{4}\\b");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    public static final String AUTO_RELOAD_FROM_SERVER = "autoReloadFromServer";
    private final VBox contentBox = new VBox(8);
    private final ScrollPane scrollPane = new ScrollPane(contentBox);
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean reloadQueued = new AtomicBoolean(false);
    private volatile boolean dirty = true;
    private String selectedSeriesKey = "";
    private String renderedDetailKey = "";
    private HBox selectedSeriesCard;
    private final Map<String, SeriesPanelData> panelDataByKey = new LinkedHashMap<>();
    private static final int MAX_IMDB_CACHE_ENTRIES = Integer.getInteger("uiptv.watchingnow.imdb.maxEntries", 200);
    private final Map<String, ImdbCacheEntry> imdbCacheByPanelKey = Collections.synchronizedMap(
            new LinkedHashMap<>(64, 0.75f, true) {
                @Override
                protected boolean removeEldestEntry(Map.Entry<String, ImdbCacheEntry> eldest) {
                    return size() > MAX_IMDB_CACHE_ENTRIES;
                }
            }
    );
    private boolean watchStateListenerRegistered = false;
    private final SeriesWatchStateChangeListener watchStateChangeListener = this::onDataChanged;

    protected BaseWatchingNowUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        contentBox.setPadding(new Insets(5));
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        if (thumbnailsEnabled()) {
            ImageCacheManager.clearCache(WATCHING_NOW_CACHE);
        }
        registerListeners();
        refreshIfNeeded();
    }

    protected abstract boolean thumbnailsEnabled();

    public void forceReload() {
        dirty = true;
        refreshIfNeeded();
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
        contentBox.getChildren().setAll(new Label(I18n.tr("autoLoadingCurrentlyWatchedSeries")));
        new Thread(() -> {
            List<SeriesPanelData> rows = buildPanelsFromCache();
            Platform.runLater(() -> {
                try {
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
        for (SeriesWatchState state : dedupeSeriesStates(account.getDbId(), null).values()) {
            SeriesPanelData panel = buildPanel(account, state);
            if (panel != null) {
                rows.add(panel);
            }
        }
        return rows;
    }

    private Map<String, SeriesWatchState> dedupeSeriesStates(String accountId, String seriesIdFilter) {
        Map<String, SeriesWatchState> deduped = new LinkedHashMap<>();
        for (SeriesWatchState state : SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount(accountId)) {
            if (!isEligibleSeriesState(state, seriesIdFilter)) {
                continue;
            }
            String key = normalizeSeriesIdentity(state.getSeriesId());
            SeriesWatchState existing = deduped.get(key);
            if (existing == null || state.getUpdatedAt() > existing.getUpdatedAt()) {
                deduped.put(key, state);
            }
        }
        return deduped;
    }

    private String normalizeSeriesIdentity(String seriesId) {
        String normalized = safe(seriesId);
        if (isBlank(normalized)) {
            return "";
        }
        if (!normalized.contains(":")) {
            return normalized;
        }
        String[] parts = normalized.split(":");
        for (String part : parts) {
            String p = safe(part);
            if (!isBlank(p)) {
                return p;
            }
        }
        return normalized;
    }

    private boolean isEligibleSeriesState(SeriesWatchState state, String seriesIdFilter) {
        return state != null
                && !isBlank(state.getSeriesId())
                && (isBlank(seriesIdFilter) || safe(state.getSeriesId()).equals(safe(seriesIdFilter)));
    }

    private SeriesPanelData buildPanel(Account account, SeriesWatchState state) {
        SnapshotScope scope = resolveSnapshotScope(state);
        SeriesWatchState scopedState = copyStateWithScope(state, scope.categoryId, scope.parentChannelId);

        SeriesCacheInfo cacheInfo = resolveSeriesInfoFromCache(account, scopedState);
        if (!isBlank(scope.seriesTitle)) {
            cacheInfo = new SeriesCacheInfo(scope.seriesTitle, firstNonBlank(scope.seriesPoster, cacheInfo.seriesPoster), true);
        } else if (!isBlank(scope.seriesPoster)) {
            cacheInfo = new SeriesCacheInfo(cacheInfo.seriesTitle, scope.seriesPoster, cacheInfo.resolvedFromCache);
        }
        if (!cacheInfo.resolvedFromCache && scopedState.getSeriesId().matches("^\\d+$")) {
            return null;
        }
        EpisodeList list = SeriesEpisodeService.getInstance().getEpisodes(account, scopedState.getCategoryId(), scopedState.getSeriesId(), () -> false);
        if (list == null) {
            list = new EpisodeList();
        }
        if (list.getSeasonInfo() != null && isBlank(list.getSeasonInfo().getName()) && !isBlank(cacheInfo.seriesTitle)) {
            list.getSeasonInfo().setName(cacheInfo.seriesTitle);
        }
        List<WatchingEpisode> episodes = mapEpisodesFromCache(account, scopedState, list);
        if (episodes.isEmpty()) {
            return null;
        }

        JSONObject seasonInfo = new JSONObject();
        seasonInfo.put("name", cacheInfo.seriesTitle);
        if (isBlank(cacheInfo.seriesPoster)) {
            String firstEpisodePoster = episodes.stream()
                    .map(e -> e.imageUrl)
                    .filter(s -> !isBlank(s))
                    .findFirst()
                    .orElse("");
            cacheInfo = new SeriesCacheInfo(cacheInfo.seriesTitle, firstEpisodePoster, cacheInfo.resolvedFromCache);
        }
        if (!isBlank(cacheInfo.seriesPoster)) {
            seasonInfo.put(KEY_COVER, cacheInfo.seriesPoster);
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
    }

    private SeriesCacheInfo resolveSeriesInfoFromCache(Account account, SeriesWatchState state) {
        SeriesCacheInfo directMatch = resolveSeriesInfoFromCandidateCategories(account, state);
        if (!needsSeriesCacheFallback(directMatch, state)) {
            return directMatch;
        }
        SeriesCacheInfo fallbackMatch = resolveSeriesInfoFromAllCategories(account, state, directMatch);
        return fallbackMatch != null ? fallbackMatch : directMatch;
    }

    private SeriesCacheInfo resolveSeriesInfoFromCandidateCategories(Account account, SeriesWatchState state) {
        String defaultTitle = state.getSeriesId();
        for (String categoryCandidate : buildSeriesCategoryCandidates(account, state)) {
            Channel match = findSeriesChannel(account, categoryCandidate, state.getSeriesId());
            if (match != null) {
                return buildSeriesCacheInfo(match, account, defaultTitle, true);
            }
        }
        return new SeriesCacheInfo(firstNonBlank(defaultTitle, state.getSeriesId()), "", false);
    }

    private List<String> buildSeriesCategoryCandidates(Account account, SeriesWatchState state) {
        String categoryDbId = resolveSeriesCategoryDbId(account, state.getCategoryId());
        List<String> categoryCandidates = new ArrayList<>();
        categoryCandidates.add(safe(state.getCategoryId()));
        if (!isBlank(categoryDbId)) {
            categoryCandidates.add(categoryDbId);
        }
        return categoryCandidates;
    }

    private boolean needsSeriesCacheFallback(SeriesCacheInfo cacheInfo, SeriesWatchState state) {
        return cacheInfo == null
                || isBlank(cacheInfo.seriesTitle)
                || cacheInfo.seriesTitle.equals(state.getSeriesId());
    }

    private SeriesCacheInfo resolveSeriesInfoFromAllCategories(Account account, SeriesWatchState state, SeriesCacheInfo current) {
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            Channel match = findSeriesChannel(account, category.getDbId(), state.getSeriesId());
            if (match != null) {
                String defaultTitle = current == null ? state.getSeriesId() : current.seriesTitle;
                return buildSeriesCacheInfo(match, account, defaultTitle, true);
            }
        }
        return null;
    }

    private Channel findSeriesChannel(Account account, String categoryId, String seriesId) {
        if (isBlank(categoryId)) {
            return null;
        }
        List<Channel> channels = SeriesChannelDb.get().getChannels(account, categoryId);
        return channels.stream()
                .filter(c -> safe(c.getChannelId()).equals(safe(seriesId)))
                .findFirst()
                .orElse(null);
    }

    private SeriesCacheInfo buildSeriesCacheInfo(Channel match, Account account, String defaultTitle, boolean resolved) {
        String title = firstNonBlank(match.getName(), defaultTitle);
        String poster = firstNonBlank(normalizeImageUrl(match.getLogo(), account), "");
        return new SeriesCacheInfo(firstNonBlank(title, defaultTitle), poster, resolved);
    }

    private SnapshotScope resolveSnapshotScope(SeriesWatchState state) {
        String categoryId = safe(state == null ? "" : state.getCategoryId());
        String parentChannelId = safe(state == null ? "" : state.getSeriesId());
        String title = "";
        String poster = "";
        if (state == null) {
            return new SnapshotScope(categoryId, parentChannelId, title, poster);
        }
        try {
            Category category = Category.fromJson(state.getSeriesCategorySnapshot());
            if (category != null) {
                categoryId = firstNonBlank(category.getCategoryId(), categoryId);
            }
        } catch (Exception _) {
            // Snapshot payloads are optional; keep the original category id when parsing fails.
        }
        try {
            Channel channel = Channel.fromJson(state.getSeriesChannelSnapshot());
            if (channel != null) {
                parentChannelId = firstNonBlank(channel.getChannelId(), parentChannelId);
                categoryId = firstNonBlank(channel.getCategoryId(), categoryId);
                title = firstNonBlank(channel.getName(), title);
                poster = firstNonBlank(channel.getLogo(), poster);
            }
        } catch (Exception _) {
            // Snapshot payloads are optional; keep the original series/channel scope when parsing fails.
        }
        return new SnapshotScope(categoryId, parentChannelId, title, poster);
    }

    private SeriesWatchState copyStateWithScope(SeriesWatchState source, String categoryId, String parentChannelId) {
        SeriesWatchState scoped = new SeriesWatchState();
        if (source == null) {
            return scoped;
        }
        scoped.setDbId(source.getDbId());
        scoped.setAccountId(source.getAccountId());
        scoped.setMode(source.getMode());
        scoped.setCategoryId(firstNonBlank(categoryId, source.getCategoryId()));
        scoped.setSeriesId(firstNonBlank(parentChannelId, source.getSeriesId()));
        scoped.setEpisodeId(source.getEpisodeId());
        scoped.setEpisodeName(source.getEpisodeName());
        scoped.setSeason(source.getSeason());
        scoped.setEpisodeNum(source.getEpisodeNum());
        scoped.setUpdatedAt(source.getUpdatedAt());
        scoped.setSource(source.getSource());
        scoped.setSeriesCategorySnapshot(source.getSeriesCategorySnapshot());
        scoped.setSeriesChannelSnapshot(source.getSeriesChannelSnapshot());
        scoped.setSeriesEpisodeSnapshot(source.getSeriesEpisodeSnapshot());
        return scoped;
    }

    private String resolveSeriesCategoryDbId(Account account, String apiCategoryId) {
        if (account == null || isBlank(account.getDbId()) || isBlank(apiCategoryId)) {
            return "";
        }
        List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
        for (Category category : categories) {
            if (safe(category.getCategoryId()).equals(safe(apiCategoryId))) {
                return safe(category.getDbId());
            }
        }
        return "";
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
        contentBox.getChildren().clear();
        panelDataByKey.clear();
        if (rows == null || rows.isEmpty()) {
            contentBox.getChildren().add(new Label(I18n.tr(MESSAGE_NO_CURRENTLY_WATCHED_SERIES)));
            selectedSeriesKey = "";
            return;
        }
        for (SeriesPanelData data : rows) {
            panelDataByKey.put(seriesPaneKey(data), data);
        }
        renderCurrentView();
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
        showSeriesList(rows);
    }

    private void showSeriesList(List<SeriesPanelData> rows) {
        prepareSeriesListContainer();
        if (rows == null || rows.isEmpty()) {
            contentBox.getChildren().add(new Label(I18n.tr(MESSAGE_NO_CURRENTLY_WATCHED_SERIES)));
            return;
        }

        if (thumbnailsEnabled()) {
            showThumbnailSeriesList(rows);
            return;
        }

        TableView<SeriesListItem> table = buildSeriesListTable(rows);

        contentBox.getChildren().add(table);
        VBox.setVgrow(table, Priority.ALWAYS);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
    }

    private void prepareSeriesListContainer() {
        contentBox.getChildren().clear();
        renderedDetailKey = "";
        contentBox.setPadding(new Insets(5));
        contentBox.setSpacing(10);
    }

    private void showThumbnailSeriesList(List<SeriesPanelData> rows) {
        for (SeriesPanelData data : rows) {
            if (thumbnailsEnabled() && isBlank(resolveSeriesPosterUrl(data)) && !data.imdbLoaded && !data.imdbLoading) {
                data.imdbLoading = true;
                lazyLoadImdb(data, null);
            }
            contentBox.getChildren().add(createSeriesListCard(data));
        }
        selectedSeriesCard = null;
        VBox.setVgrow(contentBox, Priority.ALWAYS);
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
        HBox card = new HBox(8);
        card.setAlignment(Pos.CENTER_LEFT);
        card.setFocusTraversable(false);
        card.setPadding(new Insets(6));
        card.getStyleClass().add("uiptv-card");

        ImageView poster = SeriesCardUiSupport.createFitPoster(resolveSeriesPosterUrl(data), 52, 74, WATCHING_NOW_CACHE);
        data.seriesListPosterNode = poster;
        loadSeriesListPosterImage(data);
        VBox text = new VBox(2);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        String titleText = firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle);
        String accountText = data.account.getAccountName();
        Label title = new Label(titleText);
        title.getStyleClass().add(STRONG_LABEL);
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinWidth(0);
        title.setMinHeight(Region.USE_PREF_SIZE);

        Label accountLabel = new Label("[" + accountText + "]");
        accountLabel.setWrapText(true);
        accountLabel.setMaxWidth(Double.MAX_VALUE);
        accountLabel.setMinWidth(0);
        accountLabel.setMinHeight(Region.USE_PREF_SIZE);

        Hyperlink removeLink = new Hyperlink(I18n.tr("autoRemove"));
        removeLink.getStyleClass().add("danger-link");
        removeLink.setMinWidth(Region.USE_PREF_SIZE);
        removeLink.setMaxWidth(Region.USE_PREF_SIZE);
        removeLink.setFocusTraversable(true);
        removeLink.setOnAction(event -> {
            event.consume();
            String seriesName = firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle, I18n.tr("watchingNowThisSeries"));
            if (!showConfirmationAlert(I18n.tr("autoRemoveFromWatchingNowConfirm", seriesName))) {
                return;
            }
            removeSeriesFromWatchingNow(data);
        });

        Hyperlink viewEpisodesLink = new Hyperlink(I18n.tr("autoViewEpisodes"));
        viewEpisodesLink.getStyleClass().add("watching-now-view-link");
        viewEpisodesLink.setMinWidth(Region.USE_PREF_SIZE);
        viewEpisodesLink.setMaxWidth(Region.USE_PREF_SIZE);
        viewEpisodesLink.setFocusTraversable(true);
        viewEpisodesLink.setOnAction(event -> {
            event.consume();
            setSelectedSeriesCard(card);
            selectedSeriesKey = seriesPaneKey(data);
            showSeriesDetail(data);
        });

        HBox linkRow = new HBox();
        Region linkSpacer = new Region();
        HBox.setHgrow(linkSpacer, Priority.ALWAYS);
        linkRow.getChildren().addAll(linkSpacer, viewEpisodesLink);

        text.getChildren().addAll(title, accountLabel, removeLink, linkRow);

        card.getChildren().addAll(poster, text);
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                setSelectedSeriesCard(card);
                selectedSeriesKey = seriesPaneKey(data);
                showSeriesDetail(data);
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                setSelectedSeriesCard(card);
            }
        });
        card.getProperties().put(KEY_CARD_LABELS, List.of(title, accountLabel));
        card.getProperties().put("cardLinks", List.of(viewEpisodesLink));
        return card;
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
        contentBox.setPadding(Insets.EMPTY);

        boolean thumbnailsEnabled = thumbnailsEnabled();
        HBox topBar = new HBox(8);
        topBar.setAlignment(Pos.CENTER_LEFT);
        topBar.setPadding(Insets.EMPTY);
        topBar.setMaxWidth(Double.MAX_VALUE);
        Button back = new Button(I18n.tr("autoBack"));
        back.setOnAction(event -> {
            selectedSeriesKey = "";
            renderCurrentView();
        });
        topBar.getChildren().add(back);

        VBox body = new VBox(10);
        body.setPadding(Insets.EMPTY);
        body.setMaxWidth(Double.MAX_VALUE);
        body.setMaxHeight(Double.MAX_VALUE);
        EpisodesListUI episodesListUI = new EpisodesListUI(
                data.account,
                firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle),
                data.state.getSeriesId(),
                data.state.getCategoryId()
        );
        if (thumbnailsEnabled) {
            episodesListUI.applyWatchingNowDetailStyling();
        }
        if (data.episodeList != null) {
            episodesListUI.setItems(data.episodeList);
        }
        episodesListUI.setSeasonInfoListener(seasonInfo -> {
            if (seasonInfo == null) {
                return;
            }
            Platform.runLater(() -> {
                mergeMissingSeasonInfo(data.seasonInfo, seasonInfo);
                String cover = seasonInfo.optString(KEY_COVER, "");
                if (!isBlank(cover)) {
                    data.seasonInfo.put(KEY_COVER, cover);
                }
                loadSeriesListPosterImage(data);
            });
        });
        episodesListUI.navigateToLastWatched(data.state);
        episodesListUI.setLoadingComplete();
        if (episodesListUI.canReloadFromServer()) {
            Button reload = new Button(I18n.tr(AUTO_RELOAD_FROM_SERVER));
            reload.setOnAction(event -> episodesListUI.reloadFromServer());
            topBar.getChildren().add(reload);
        }
        body.getChildren().add(episodesListUI);
        VBox.setVgrow(body, Priority.ALWAYS);
        VBox.setVgrow(episodesListUI, Priority.ALWAYS);
        HBox.setHgrow(episodesListUI, Priority.ALWAYS);

        contentBox.getChildren().addAll(topBar, body);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
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
        details.getChildren().add(resolveReloadEpisodesButton(data));
    }

    private void clearSeasonHeaderDetails(VBox details, SeriesPanelData data) {
        details.getChildren().removeAll(data.ratingNode, data.genreNode, data.releaseNode, data.plotNode, data.reloadEpisodesButton);
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
        ProgressIndicator imdbProgress = new ProgressIndicator();
        imdbProgress.setPrefSize(14, 14);
        imdbProgress.setMinSize(14, 14);
        imdbProgress.setMaxSize(14, 14);
        Label imdbLoadingLabel = new Label(I18n.tr("autoLoadingIMDbDetails"));
        return new HBox(6, imdbProgress, imdbLoadingLabel);
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

    private Button resolveReloadEpisodesButton(SeriesPanelData data) {
        if (data.reloadEpisodesButton == null) {
            data.reloadEpisodesButton = new Button(I18n.tr(AUTO_RELOAD_FROM_SERVER));
            data.reloadEpisodesButton.setFocusTraversable(true);
            data.reloadEpisodesButton.setOnAction(event -> reloadEpisodesFromPortal(data));
        }
        return data.reloadEpisodesButton;
    }

    private void populateSeasonTabs(TabPane tabPane, SeriesPanelData data) {
        if (tabPane == null || data == null) {
            return;
        }
        Tab selectedTab = tabPane.getSelectionModel().getSelectedItem();
        String selectedSeason = selectedTab == null ? "" : safe(selectedTab.getText());
        tabPane.getTabs().clear();
        data.seasonCardsBySeason.clear();
        data.watchingLabels.clear();
        data.selectedEpisodeCard = null;

        Map<String, List<WatchingEpisode>> bySeason = new LinkedHashMap<>();
        for (WatchingEpisode episode : data.episodes) {
            String season = normalizeNumber(episode.season);
            if (isBlank(season)) season = "1";
            bySeason.computeIfAbsent(season, k -> new ArrayList<>()).add(episode);
        }
        if (bySeason.isEmpty()) {
            bySeason.put("1", List.of());
        }

        List<String> seasons = new ArrayList<>(bySeason.keySet());
        seasons.sort(Comparator.comparingInt(s -> parseNumberOrDefault(s, 1)));

        for (String season : seasons) {
            Tab tab = new Tab(I18n.formatTabNumberLabel(season));
            tab.setUserData(season);
            VBox cards = buildEpisodeCards(data, FXCollections.observableArrayList(bySeason.getOrDefault(season, List.of())));
            ScrollPane cardsScroll = new ScrollPane(cards);
            cardsScroll.setFitToWidth(true);
            cardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            tab.setContent(cardsScroll);
            tabPane.getTabs().add(tab);
            data.seasonCardsBySeason.put(season, cards);
        }
        if (!tabPane.getTabs().isEmpty()) {
            Tab toSelect = tabPane.getTabs().stream()
                    .filter(t -> safe(String.valueOf(t.getUserData())).equals(selectedSeason))
                    .findFirst()
                    .orElse(tabPane.getTabs().getFirst());
            tabPane.getSelectionModel().select(toSelect);
        }
    }

    private VBox buildEpisodeCards(SeriesPanelData data, javafx.collections.ObservableList<WatchingEpisode> items) {
        VBox container = new VBox(10);
        container.setPadding(new Insets(5));
        container.setFillWidth(true);
        VBox.setVgrow(container, Priority.ALWAYS);
        if (items == null || items.isEmpty()) {
            container.getChildren().add(new Label(I18n.tr("autoNoEpisodesFound")));
            return container;
        }
        for (WatchingEpisode episode : items) {
            container.getChildren().add(createEpisodeCard(data, episode));
        }
        return container;
    }

    private VBox createEpisodeCard(SeriesPanelData data, WatchingEpisode row) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("uiptv-card");

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

        Button play = new Button(I18n.tr("autoPlay"));
        play.getStyleClass().setAll("button");
        play.getStyleClass().add("small-pill-button");
        play.setMinWidth(Region.USE_PREF_SIZE);
        play.setMaxWidth(Region.USE_PREF_SIZE);
        play.setMinHeight(Region.USE_PREF_SIZE);
        play.setFocusTraversable(true);
        play.setOnAction(event -> {
            event.consume();
            playEpisode(data, row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
        });
        badges.getChildren().add(play);

        HBox actionRow = new HBox();
        actionRow.setAlignment(Pos.TOP_RIGHT);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        actionRow.getChildren().addAll(actionSpacer, badges);

        Label title = new Label(buildEpisodeDisplayTitle(row.season, row.episodeNum, row.title));
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinWidth(0);
        title.setMinHeight(Region.USE_PREF_SIZE);
        title.getStyleClass().add(STRONG_LABEL);

        text.getChildren().addAll(actionRow, title);
        List<Label> cardLabels = new ArrayList<>();
        cardLabels.add(title);
        if (!isBlank(row.rating)) {
            Label rating = new Label(I18n.tr("autoRatingPrefix", row.rating));
            rating.setMinWidth(0);
            text.getChildren().add(rating);
            cardLabels.add(rating);
        }
        if (!isBlank(row.releaseDate)) {
            Label release = new Label(I18n.tr("autoReleasePrefix", shortDateOnly(row.releaseDate)));
            release.setMinWidth(0);
            text.getChildren().add(release);
            cardLabels.add(release);
        }

        top.getChildren().addAll(posterWrap, text);
        root.getChildren().add(top);
        if (!isBlank(row.plot)) {
            Label plot = new Label(row.plot);
            plot.setWrapText(true);
            plot.setMaxWidth(Double.MAX_VALUE);
            plot.setMinWidth(0);
            plot.setMinHeight(Region.USE_PREF_SIZE);
            root.getChildren().add(plot);
            cardLabels.add(plot);
        }
        root.getProperties().put(KEY_CARD_LABELS, cardLabels);
        root.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                playEpisode(data, row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                setSelectedEpisodeCard(data, root);
            }
        });
        addEpisodeContextMenu(data, row, root);
        return root;
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

    private void addEpisodeContextMenu(SeriesPanelData data, WatchingEpisode item, Pane target) {
        ContextMenu rowMenu = new ContextMenu();
        I18n.preparePopupControl(rowMenu, target);
        if (item == null) {
            return;
        }
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);
        target.setOnContextMenuRequested(event -> {
            populateEpisodeContextMenu(rowMenu, data, item);
            if (!rowMenu.getItems().isEmpty()) {
                rowMenu.show(target, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
    }

    private void populateEpisodeContextMenu(ContextMenu rowMenu, SeriesPanelData data, WatchingEpisode item) {
        rowMenu.getItems().clear();
        if (!item.watched) {
            MenuItem watchingNow = new MenuItem(I18n.tr("autoWatchingNow"));
            watchingNow.setOnAction(e -> {
                markEpisodeAsWatched(item);
                updateWatchingStatusUI(data, item);
            });
            rowMenu.getItems().add(watchingNow);
            rowMenu.getItems().add(new SeparatorMenuItem());
        }
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.setOnAction(e -> playEpisode(data, item, option.playerPath()));
            rowMenu.getItems().add(playerItem);
        }
        if (item.watched) {
            rowMenu.getItems().add(new SeparatorMenuItem());
            MenuItem removeWatchingNow = new MenuItem(I18n.tr("autoRemoveWatchingNow"));
            removeWatchingNow.getStyleClass().add("danger-menu-item");
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
            data.imdbLoaded = true;
            data.imdbLoading = false;
            return;
        }
        new Thread(() -> {
            try {
                JSONObject imdb = findImdbWithRetry(data, 3);
                if (imdb != null) {
                    mergeImdbIntoPanel(data, imdb);
                }
            } finally {
                data.imdbLoaded = true;
                data.imdbLoading = false;
                Platform.runLater(() -> applyLoadedImdbToUi(data, pane));
            }
        }, "watching-now-imdb-loader").start();
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
        loadSeriesListPosterImage(data);
        if (isBlank(selectedSeriesKey)) {
            renderCurrentView();
            return;
        }
        applySeasonInfoToHeader(data);
        loadSeriesPosterImage(data);
        refreshSeasonTables(data);
    }

    private void loadSeriesPosterImage(SeriesPanelData data) {
        String cover = resolveSeriesPosterUrl(data);
        if (isBlank(cover)) {
            return;
        }
        ImageCacheManager.loadImageAsync(cover, WATCHING_NOW_CACHE).thenAccept(img -> {
            if (img != null) {
                Platform.runLater(() -> data.seriesPosterNode.setImage(img));
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
                Platform.runLater(() -> data.seriesListPosterNode.setImage(img));
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
        if (data == null || data.seasonTabs == null) {
            return;
        }
        populateSeasonTabs(data.seasonTabs, data);
    }

    private void reloadEpisodesFromPortal(SeriesPanelData data) {
        if (data == null || data.account == null || data.state == null || isBlank(data.state.getSeriesId())) {
            return;
        }
        if (data.reloadEpisodesButton != null) {
            data.reloadEpisodesButton.setDisable(true);
            data.reloadEpisodesButton.setText(I18n.tr("autoReloading"));
        }
        new Thread(() -> {
            EpisodeList refreshed = SeriesEpisodeService.getInstance()
                    .reloadEpisodesFromPortal(data.account, data.state.getCategoryId(), data.state.getSeriesId(), () -> false);
            List<WatchingEpisode> refreshedEpisodes = mapEpisodesFromCache(data.account, data.state, refreshed);
            Platform.runLater(() -> {
                // Always discard old episodes on reload and rebuild tabs from the latest response.
                data.episodes.clear();
                data.episodes.addAll(refreshedEpisodes);
                data.episodeList = refreshed;
                refreshSeasonTables(data);
                // Reload complete: invalidate prior IMDb merge and fetch fresh metadata for updated episodes/seasons.
                imdbCacheByPanelKey.remove(panelCacheKey(data.account, data.state));
                data.imdbLoaded = false;
                data.imdbLoading = true;
                applySeasonInfoToHeader(data);
                lazyLoadImdb(data, null);
                if (data.reloadEpisodesButton != null) {
                    data.reloadEpisodesButton.setText(I18n.tr(AUTO_RELOAD_FROM_SERVER));
                    data.reloadEpisodesButton.setDisable(false);
                }
            });
        }, "watching-now-portal-reload").start();
    }

    private void enrichEpisodesFromMeta(List<WatchingEpisode> episodes, JSONArray metaRows) {
        if (episodes == null || episodes.isEmpty() || metaRows == null || metaRows.isEmpty()) {
            return;
        }
        EpisodeMetaIndex metaIndex = buildEpisodeMetaIndex(metaRows);
        for (WatchingEpisode episode : episodes) {
            JSONObject meta = findEpisodeMeta(metaIndex, episode);
            if (meta == null) {
                continue;
            }
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
        if (!watchStateListenerRegistered) {
            SeriesWatchStateService.getInstance().addChangeListener(watchStateChangeListener);
            watchStateListenerRegistered = true;
        }
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                if (watchStateListenerRegistered) {
                    SeriesWatchStateService.getInstance().removeChangeListener(watchStateChangeListener);
                    watchStateListenerRegistered = false;
                }
                releaseUiState();
            } else {
                if (!watchStateListenerRegistered) {
                    SeriesWatchStateService.getInstance().addChangeListener(watchStateChangeListener);
                    watchStateListenerRegistered = true;
                }
                refreshIfNeeded();
            }
        });
        visibleProperty().addListener((obs, oldVal, newVal) -> {
            if (Boolean.TRUE.equals(newVal)) {
                refreshIfNeeded();
            }
        });
    }

    private void onDataChanged(String accountId, String seriesId) {
        if (isBlank(accountId) || isBlank(seriesId)) {
            dirty = true;
            Platform.runLater(this::refreshIfNeeded);
            return;
        }
        refreshSeriesEntryAsync(accountId, seriesId);
    }

    private void refreshSeriesEntryAsync(String accountId, String seriesId) {
        if (reloadInProgress.get()) {
            reloadQueued.set(true);
            return;
        }
        reloadInProgress.set(true);
        new Thread(() -> {
            try {
                List<SeriesPanelData> updated = buildUpdatedSeriesPanels(accountId, seriesId);
                Platform.runLater(() -> applySeriesDelta(accountId, seriesId, updated));
            } finally {
                reloadInProgress.set(false);
                if (reloadQueued.getAndSet(false) || dirty) {
                    Platform.runLater(this::refreshIfNeeded);
                }
            }
        }, "watching-now-delta-loader").start();
    }

    private List<SeriesPanelData> buildUpdatedSeriesPanels(String accountId, String seriesId) {
        List<SeriesPanelData> updated = new ArrayList<>();
        Account account = AccountService.getInstance().getById(accountId);
        if (account == null || isBlank(account.getDbId())) {
            return updated;
        }
        account.setAction(Account.AccountAction.series);
        for (SeriesWatchState state : dedupeSeriesStates(account.getDbId(), seriesId).values()) {
            SeriesPanelData panel = buildPanel(account, state);
            if (panel != null) {
                updated.add(panel);
            }
        }
        return updated;
    }

    private static final class EpisodeMetaIndex {
        private final Map<String, JSONObject> bySeasonEpisode = new HashMap<>();
        private final Map<String, JSONObject> byTitle = new HashMap<>();
        private final Map<String, JSONObject> byLooseTitle = new HashMap<>();
        private final Map<String, JSONObject> byEpisodeOnly = new HashMap<>();
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
        target.imdbLoaded = source.imdbLoaded;
        target.imdbLoading = source.imdbLoading;
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
        if (selected.seriesPosterNode != null) {
            String cover = resolveSeriesPosterUrl(selected);
            if (!isBlank(cover)) {
                ImageCacheManager.loadImageAsync(cover, WATCHING_NOW_CACHE).thenAccept(img -> {
                    if (img != null) {
                        Platform.runLater(() -> selected.seriesPosterNode.setImage(img));
                    }
                });
            }
        }
        if (selected.seasonTabs != null) {
            refreshSeasonTables(selected);
        }
    }

    private boolean isDisplayable() {
        return getScene() != null && isVisible();
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
        if (isBlank(imageUrl)) {
            return "";
        }
        String value = imageUrl.trim().replace("\\/", "/");
        value = trimWrappedImageQuotes(value);
        if (isBlank(value)) {
            return "";
        }
        if (isAbsoluteImageUrl(value) || isInlineImageUrl(value)) {
            return value;
        }
        URI base = resolveBaseUri(account);
        String scheme = resolveBaseScheme(base);
        String host = resolveBaseHost(base);
        int port = base == null ? -1 : base.getPort();
        if (value.startsWith("//")) {
            return scheme + ":" + value;
        }
        if (value.startsWith("/")) {
            return buildRootRelativeImageUrl(value, scheme, host, port);
        }
        if (value.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) {
            return scheme + "://" + value;
        }
        return buildRelativeImageUrl(value, scheme, host, port);
    }

    private URI resolveBaseUri(Account account) {
        if (account == null) {
            return null;
        }
        List<String> candidates = List.of(account.getServerPortalUrl(), account.getUrl());
        for (String candidate : candidates) {
            URI resolved = parseCandidateBaseUri(candidate);
            if (resolved != null) {
                return resolved;
            }
        }
        return null;
    }

    private String trimWrappedImageQuotes(String value) {
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

    private String resolveBaseScheme(URI base) {
        return base != null && !isBlank(base.getScheme()) ? base.getScheme() : "https";
    }

    private String resolveBaseHost(URI base) {
        return base != null && !isBlank(base.getHost()) ? base.getHost() : "";
    }

    private String buildRootRelativeImageUrl(String value, String scheme, String host, int port) {
        if (!isBlank(host)) {
            return scheme + "://" + host + formatPort(port) + value;
        }
        return value;
    }

    private String buildRelativeImageUrl(String value, String scheme, String host, int port) {
        String normalized = value.startsWith("./") ? value.substring(2) : value;
        if (!isBlank(host)) {
            return scheme + "://" + host + formatPort(port) + "/" + normalized;
        }
        return ServerUrlUtil.getLocalServerUrl() + "/" + normalized;
    }

    private String formatPort(int port) {
        return port > 0 ? ":" + port : "";
    }

    private URI parseCandidateBaseUri(String candidate) {
        if (isBlank(candidate)) {
            return null;
        }
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
            // Invalid image/base URIs should not break rendering; the caller will keep the raw URL.
        }
        return null;
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
        for (SeriesPanelData panel : panelDataByKey.values()) {
            if (panel != null) {
                panel.watchingLabels.clear();
                panel.seasonCardsBySeason.clear();
            }
        }
        panelDataByKey.clear();
        imdbCacheByPanelKey.clear();
        selectedSeriesKey = "";
        renderedDetailKey = "";
        contentBox.getChildren().clear();
    }

    private String panelCacheKey(Account account, SeriesWatchState state) {
        if (account == null || state == null) {
            return "";
        }
        return safe(account.getDbId()) + "|" + safe(state.getCategoryId()) + "|" + safe(state.getSeriesId());
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
        private EpisodeList episodeList;
        private boolean imdbLoaded;
        private boolean imdbLoading;

        private Label titleNode;
        private Label ratingNode;
        private Label genreNode;
        private Label releaseNode;
        private Label plotNode;
        private HBox imdbBadgeNode;
        private HBox imdbLoadingNode;
        private Button reloadEpisodesButton;
        private ImageView seriesPosterNode;
        private ImageView seriesListPosterNode;
        private TabPane seasonTabs;
        private final Map<String, VBox> seasonCardsBySeason = new LinkedHashMap<>();
        private final Map<WatchingEpisode, Label> watchingLabels = new HashMap<>();
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
        }
    }

    private static final class SnapshotScope {
        private final String categoryId;
        private final String parentChannelId;
        private final String seriesTitle;
        private final String seriesPoster;

        private SnapshotScope(String categoryId, String parentChannelId, String seriesTitle, String seriesPoster) {
            this.categoryId = categoryId == null ? "" : categoryId.trim();
            this.parentChannelId = parentChannelId == null ? "" : parentChannelId.trim();
            this.seriesTitle = seriesTitle == null ? "" : seriesTitle;
            this.seriesPoster = seriesPoster == null ? "" : seriesPoster;
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
