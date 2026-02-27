package com.uiptv.ui;

import com.uiptv.db.SeriesCategoryDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Category;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.SeriesEpisodeService;
import com.uiptv.service.SeriesWatchStateChangeListener;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Accordion;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.control.TitledPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.Pane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;

public class WatchingNowUI extends VBox {
    private static final Pattern SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b");
    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b|\\b\\d{1,2}x(\\d{1,3})\\b");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},\\s+\\d{4}\\b");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final DateTimeFormatter UI_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final VBox contentBox = new VBox(8);
    private final ScrollPane scrollPane = new ScrollPane(contentBox);
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean reloadQueued = new AtomicBoolean(false);
    private volatile boolean dirty = true;
    private boolean firstRender = true;
    private String lastExpandedSeriesKey = "";
    private Accordion seriesAccordion;
    private final Map<String, SeriesPanelData> panelDataByKey = new LinkedHashMap<>();
    private boolean watchStateListenerRegistered = false;
    private final SeriesWatchStateChangeListener watchStateChangeListener = this::onDataChanged;

    public WatchingNowUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        contentBox.setPadding(new Insets(4));
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        registerListeners();
        refreshIfNeeded();
    }

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
        contentBox.getChildren().setAll(new Label("Loading currently watched series..."));
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
            if (account == null || isBlank(account.getDbId())) {
                continue;
            }
            account.setAction(Account.AccountAction.series);
            Map<String, SeriesWatchState> deduped = new LinkedHashMap<>();
            for (SeriesWatchState state : SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount(account.getDbId())) {
                if (state == null || isBlank(state.getSeriesId())) {
                    continue;
                }
                String key = safe(state.getCategoryId()) + "|" + safe(state.getSeriesId());
                SeriesWatchState existing = deduped.get(key);
                if (existing == null || state.getUpdatedAt() > existing.getUpdatedAt()) {
                    deduped.put(key, state);
                }
            }
            for (SeriesWatchState state : deduped.values()) {
                SeriesPanelData panel = buildPanel(account, state);
                if (panel != null) {
                    rows.add(panel);
                }
            }
        }
        rows.sort(Comparator.comparing((SeriesPanelData d) -> safe(d.seriesTitle), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private SeriesPanelData buildPanel(Account account, SeriesWatchState state) {
        SeriesCacheInfo cacheInfo = resolveSeriesInfoFromCache(account, state);
        if (!cacheInfo.resolvedFromCache && state.getSeriesId().matches("^\\d+$")) {
            return null;
        }
        EpisodeList list = SeriesEpisodeService.getInstance().getEpisodes(account, state.getCategoryId(), state.getSeriesId(), () -> false);
        List<WatchingEpisode> episodes = mapEpisodesFromCache(account, state, cacheInfo.seriesTitle, list);
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
            seasonInfo.put("cover", cacheInfo.seriesPoster);
        }

        return new SeriesPanelData(account, state, cacheInfo.seriesTitle, seasonInfo, episodes);
    }

    private SeriesCacheInfo resolveSeriesInfoFromCache(Account account, SeriesWatchState state) {
        String title = state.getSeriesId();
        String poster = "";
        boolean resolved = false;

        String categoryDbId = resolveSeriesCategoryDbId(account, state.getCategoryId());
        if (!isBlank(categoryDbId)) {
            List<Channel> channels = SeriesChannelDb.get().getChannels(account, categoryDbId);
            Channel match = channels.stream().filter(c -> safe(c.getChannelId()).equals(safe(state.getSeriesId()))).findFirst().orElse(null);
            if (match != null) {
                if (!isBlank(match.getName())) {
                    title = match.getName();
                }
                poster = firstNonBlank(normalizeImageUrl(match.getLogo(), account), poster);
                resolved = true;
            }
        }

        if (isBlank(title) || title.equals(state.getSeriesId())) {
            // Fallback: try any cached category if state category mapping is missing.
            List<Category> categories = SeriesCategoryDb.get().getAll(" WHERE accountId=?", new String[]{account.getDbId()});
            for (Category category : categories) {
                List<Channel> channels = SeriesChannelDb.get().getChannels(account, category.getDbId());
                Channel match = channels.stream().filter(c -> safe(c.getChannelId()).equals(safe(state.getSeriesId()))).findFirst().orElse(null);
                if (match != null) {
                    title = firstNonBlank(match.getName(), title);
                    poster = firstNonBlank(normalizeImageUrl(match.getLogo(), account), poster);
                    resolved = true;
                    break;
                }
            }
        }

        return new SeriesCacheInfo(firstNonBlank(title, state.getSeriesId()), poster, resolved);
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
                                                       String seriesTitle,
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
            String title = cleanEpisodeTitle(episode.getTitle());
            String image = episode.getInfo() != null ? normalizeImageUrl(episode.getInfo().getMovieImage(), account) : "";
            String plot = episode.getInfo() != null ? safe(episode.getInfo().getPlot()) : "";
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

            episodes.add(new WatchingEpisode(account, state, seriesTitle, playbackChannel, season, episodeNum, title, image, plot, releaseDate, rating, watched));
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
        if (rows.isEmpty()) {
            contentBox.getChildren().add(new Label("No currently watched series found."));
            lastExpandedSeriesKey = "";
            seriesAccordion = null;
            return;
        }

        Accordion accordion = new Accordion();
        seriesAccordion = accordion;
        accordion.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(accordion, Priority.ALWAYS);

        for (SeriesPanelData data : rows) {
            panelDataByKey.put(seriesPaneKey(data), data);
            accordion.getPanes().add(createSeriesPane(data));
        }
        if (firstRender) {
            accordion.setExpandedPane(null);
            firstRender = false;
        } else if (!isBlank(lastExpandedSeriesKey)) {
            for (TitledPane pane : accordion.getPanes()) {
                if (lastExpandedSeriesKey.equals(safe((String) pane.getUserData()))) {
                    accordion.setExpandedPane(pane);
                    break;
                }
            }
        }

        contentBox.getChildren().add(accordion);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
    }

    private TitledPane createSeriesPane(SeriesPanelData data) {
        VBox body = new VBox(8);
        body.setPadding(new Insets(8));

        HBox header = createSeriesHeader(data);
        TabPane seasonTabs = createSeasonTabs(data);
        VBox.setVgrow(seasonTabs, Priority.ALWAYS);

        body.getChildren().addAll(header, seasonTabs);
        TitledPane pane = new TitledPane(buildSeriesPaneTitle(data), body);
        pane.setAnimated(false);
        pane.setUserData(seriesPaneKey(data));
        pane.setExpanded(false);
        pane.expandedProperty().addListener((obs, oldVal, expanded) -> {
            if (expanded) {
                lastExpandedSeriesKey = safe((String) pane.getUserData());
            }
            if (expanded && !data.imdbLoaded && !data.imdbLoading) {
                data.imdbLoading = true;
                applySeasonInfoToHeader(data);
                lazyLoadImdb(data, pane, header, seasonTabs);
            }
        });
        return pane;
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

    private HBox createSeriesHeader(SeriesPanelData data) {
        HBox root = new HBox(12);
        root.setAlignment(Pos.TOP_LEFT);

        ImageView cover = SeriesCardUiSupport.createFitPoster(data.seasonInfo.optString("cover", ""), 140, 200, "watching-now");
        data.seriesPosterNode = cover;

        VBox details = new VBox(4);
        details.setMaxWidth(Double.MAX_VALUE);
        data.titleNode = new Label(firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle));
        data.titleNode.setStyle("-fx-font-weight: bold;");
        data.titleNode.setWrapText(true);
        data.titleNode.setMaxWidth(Double.MAX_VALUE);
        details.getChildren().add(data.titleNode);

        data.ratingNode = new Label();
        data.genreNode = new Label();
        data.releaseNode = new Label();
        data.plotNode = new Label();
        data.plotNode.setWrapText(true);
        data.plotNode.setMaxWidth(Double.MAX_VALUE);
        data.plotNode.setMinHeight(Region.USE_PREF_SIZE);
        details.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(data.plotNode, Priority.ALWAYS);
        data.plotNode.prefWidthProperty().bind(details.widthProperty().subtract(6));
        data.titleNode.prefWidthProperty().bind(details.widthProperty().subtract(6));

        applySeasonInfoToHeader(data);
        root.setMaxHeight(Double.MAX_VALUE);
        root.getChildren().addAll(cover, details);
        HBox.setHgrow(details, Priority.ALWAYS);
        return root;
    }

    private void applySeasonInfoToHeader(SeriesPanelData data) {
        VBox details = (VBox) data.titleNode.getParent();
        details.getChildren().removeAll(data.ratingNode, data.genreNode, data.releaseNode, data.plotNode);
        if (data.imdbLoadingNode != null) {
            details.getChildren().remove(data.imdbLoadingNode);
        }
        if (data.imdbBadgeNode != null) {
            details.getChildren().remove(data.imdbBadgeNode);
        }

        data.titleNode.setText(firstNonBlank(data.seasonInfo.optString("name", ""), data.seriesTitle));

        String ratingValue = data.seasonInfo.optString("rating", "");
        if (!isBlank(ratingValue)) {
            data.ratingNode.setText("IMDb: " + ratingValue);
        }

        String imdbUrl = data.seasonInfo.optString("imdbUrl", "");
        data.imdbBadgeNode = SeriesCardUiSupport.createImdbRatingPill(ratingValue, imdbUrl);
        if (data.imdbBadgeNode != null) {
            details.getChildren().add(data.imdbBadgeNode);
        }
        if (data.imdbLoading && !data.imdbLoaded) {
            if (data.imdbLoadingNode == null) {
                ProgressIndicator imdbProgress = new ProgressIndicator();
                imdbProgress.setPrefSize(14, 14);
                imdbProgress.setMinSize(14, 14);
                imdbProgress.setMaxSize(14, 14);
                Label imdbLoadingLabel = new Label("Loading IMDb details...");
                data.imdbLoadingNode = new HBox(6, imdbProgress, imdbLoadingLabel);
            }
            details.getChildren().add(data.imdbLoadingNode);
        }

        String genre = data.seasonInfo.optString("genre", "");
        if (!isBlank(genre)) {
            data.genreNode.setText("Genre: " + genre);
            details.getChildren().add(data.genreNode);
        }

        String releaseDate = data.seasonInfo.optString("releaseDate", "");
        if (!isBlank(releaseDate)) {
            data.releaseNode.setText("Release: " + releaseDate);
            details.getChildren().add(data.releaseNode);
        }

        String plot = data.seasonInfo.optString("plot", "");
        if (!isBlank(plot)) {
            data.plotNode.setText(plot);
            details.getChildren().add(data.plotNode);
        }
    }

    private TabPane createSeasonTabs(SeriesPanelData data) {
        TabPane tabPane = new TabPane();
        tabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        tabPane.setMaxWidth(Double.MAX_VALUE);
        tabPane.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(tabPane, Priority.ALWAYS);

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
            Tab tab = new Tab(season);
            VBox cards = buildEpisodeCards(data, FXCollections.observableArrayList(bySeason.getOrDefault(season, List.of())));
            ScrollPane cardsScroll = new ScrollPane(cards);
            cardsScroll.setFitToWidth(true);
            cardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
            cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
            tab.setContent(cardsScroll);
            tabPane.getTabs().add(tab);
            data.seasonCardsBySeason.put(season, cards);
        }

        data.seasonTabs = tabPane;
        return tabPane;
    }

    private VBox buildEpisodeCards(SeriesPanelData data, javafx.collections.ObservableList<WatchingEpisode> items) {
        VBox container = new VBox(8);
        container.setPadding(new Insets(4));
        container.setFillWidth(true);
        VBox.setVgrow(container, Priority.ALWAYS);
        if (items == null || items.isEmpty()) {
            container.getChildren().add(new Label("No episodes found."));
            return container;
        }
        for (WatchingEpisode episode : items) {
            container.getChildren().add(createEpisodeCard(data, episode));
        }
        return container;
    }

    private HBox createEpisodeCard(SeriesPanelData data, WatchingEpisode row) {
        HBox root = new HBox(10);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(6));
        root.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6;");

        ImageView poster = SeriesCardUiSupport.createFitPoster(row.imageUrl, 96, 136, "watching-now");
        VBox text = new VBox(2);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);
        Label title = new Label("E" + (isBlank(row.episodeNum) ? "-" : row.episodeNum) + "  " + safe(row.title));
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(Region.USE_PREF_SIZE);
        if (row.watched) {
            title.setStyle("-fx-font-weight: bold;");
        }
        text.getChildren().add(title);
        if (!isBlank(row.releaseDate)) text.getChildren().add(new Label("Release: " + shortDateOnly(row.releaseDate)));
        if (!isBlank(row.rating)) text.getChildren().add(new Label("Rating: " + row.rating));
        if (!isBlank(row.plot)) {
            Label plot = new Label(row.plot);
            plot.setWrapText(true);
            plot.setMaxWidth(Double.MAX_VALUE);
            plot.setMinHeight(Region.USE_PREF_SIZE);
            text.getChildren().add(plot);
        }

        Region spacer = new Region();
        spacer.setPrefWidth(8);
        spacer.setMinWidth(8);
        VBox badges = new VBox(4);
                if (row.watched) {
                    Label watching = new Label("WATCHING");
                    watching.getStyleClass().add("drm-badge");
                    watching.setMinWidth(Region.USE_PREF_SIZE);
                    watching.setMaxWidth(Double.MAX_VALUE);
                    badges.getChildren().add(watching);
                }

        root.getChildren().addAll(poster, text, spacer, badges);
        root.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                playEpisode(row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            }
        });
        addEpisodeContextMenu(data, row, root);
        return root;
    }

    private void addEpisodeContextMenu(SeriesPanelData data, WatchingEpisode item, Pane target) {
        ContextMenu rowMenu = new ContextMenu();
        Menu lastWatchedMenu = new Menu("Last Watched");

        rowMenu.setOnShowing(event -> {
            lastWatchedMenu.getItems().clear();
            if (item == null) {
                return;
            }

            MenuItem markWatched = new MenuItem("Mark as Watched");
            markWatched.setOnAction(e -> markEpisodeAsWatched(item));
            lastWatchedMenu.getItems().add(markWatched);

            MenuItem clearWatched = new MenuItem("Clear Watched Marker");
            clearWatched.setDisable(!item.watched);
            clearWatched.setOnAction(e -> clearWatchedMarker(item));
            lastWatchedMenu.getItems().add(clearWatched);
        });

        MenuItem embedded = new MenuItem("Embedded Player");
        embedded.setOnAction(e -> playEpisode(item, "embedded"));
        MenuItem p1 = new MenuItem("Player 1");
        p1.setOnAction(e -> playEpisode(item, ConfigurationService.getInstance().read().getPlayerPath1()));
        MenuItem p2 = new MenuItem("Player 2");
        p2.setOnAction(e -> playEpisode(item, ConfigurationService.getInstance().read().getPlayerPath2()));
        MenuItem p3 = new MenuItem("Player 3");
        p3.setOnAction(e -> playEpisode(item, ConfigurationService.getInstance().read().getPlayerPath3()));

        rowMenu.getItems().addAll(lastWatchedMenu, new SeparatorMenuItem(), embedded, p1, p2, p3);
        target.setOnContextMenuRequested(event -> rowMenu.show(target, event.getScreenX(), event.getScreenY()));
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
        new Thread(() -> {
            SeriesWatchStateService.getInstance().clearSeriesLastWatched(item.account.getDbId(), item.state.getCategoryId(), item.state.getSeriesId());
        }, "watching-now-clear-watched").start();
    }

    private void playEpisode(WatchingEpisode item, String playerPath) {
        if (item == null || item.channel == null || item.account == null) {
            return;
        }
        if (!item.watched) {
            markEpisodeAsWatched(item);
        }
        item.account.setAction(Account.AccountAction.series);
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(item.account, item.channel, playerPath)
                .series(item.channel.getChannelId(), item.state.getSeriesId())
                .categoryId(item.state.getCategoryId())
                .channelId(item.channel.getChannelId())
                .errorPrefix("Error playing episode: "));
    }

    private void lazyLoadImdb(SeriesPanelData data, TitledPane pane, HBox header, TabPane seasonTabs) {
        new Thread(() -> {
            try {
                JSONObject imdb = findImdbWithRetry(data, 3);
                if (imdb != null) {
                    String imdbCover = normalizeImageUrl(imdb.optString("cover", ""), data.account);
                    if (!isBlank(imdbCover)) {
                        data.seasonInfo.put("cover", imdbCover);
                        imdb.put("cover", imdbCover);
                    }
                    mergeMissing(data.seasonInfo, imdb, "name");
                    mergeMissing(data.seasonInfo, imdb, "plot");
                    mergeMissing(data.seasonInfo, imdb, "cast");
                    mergeMissing(data.seasonInfo, imdb, "director");
                    mergeMissing(data.seasonInfo, imdb, "genre");
                    mergeMissing(data.seasonInfo, imdb, "releaseDate");
                    mergeMissing(data.seasonInfo, imdb, "rating");
                    mergeMissing(data.seasonInfo, imdb, "tmdb");
                    mergeMissing(data.seasonInfo, imdb, "imdbUrl");

                    enrichEpisodesFromMeta(data.episodes, imdb.optJSONArray("episodesMeta"));
                }
            } finally {
                data.imdbLoaded = true;
                data.imdbLoading = false;
                Platform.runLater(() -> {
                    pane.setText(buildSeriesPaneTitle(data));
                    applySeasonInfoToHeader(data);
                    String cover = data.seasonInfo.optString("cover", "");
                    if (!isBlank(cover)) {
                        ImageCacheManager.loadImageAsync(cover, "watching-now").thenAccept(img -> {
                            if (img != null) {
                                Platform.runLater(() -> data.seriesPosterNode.setImage(img));
                            }
                        });
                    }
                    refreshSeasonTables(data);
                });
            }
        }, "watching-now-imdb-loader").start();
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
            } catch (Exception ignored) {
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException ignored) {
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
        String year = extractYear(firstNonBlank(data.seasonInfo.optString("releaseDate", ""), firstEpisodeRelease(data)));
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
        Map<String, List<WatchingEpisode>> bySeason = new HashMap<>();
        for (WatchingEpisode e : data.episodes) {
            String season = isBlank(e.season) ? "1" : normalizeNumber(e.season);
            if (isBlank(season)) season = "1";
            bySeason.computeIfAbsent(season, k -> new ArrayList<>()).add(e);
        }
        for (Map.Entry<String, VBox> entry : data.seasonCardsBySeason.entrySet()) {
            List<WatchingEpisode> seasonRows = bySeason.getOrDefault(entry.getKey(), List.of());
            VBox cards = entry.getValue();
            cards.getChildren().clear();
            if (seasonRows.isEmpty()) {
                cards.getChildren().add(new Label("No episodes found."));
            } else {
                for (WatchingEpisode episode : seasonRows) {
                    cards.getChildren().add(createEpisodeCard(data, episode));
                }
            }
        }
    }

    private void enrichEpisodesFromMeta(List<WatchingEpisode> episodes, JSONArray metaRows) {
        if (episodes == null || episodes.isEmpty() || metaRows == null || metaRows.isEmpty()) {
            return;
        }
        Map<String, JSONObject> bySeasonEpisode = new HashMap<>();
        Map<String, JSONObject> byTitle = new HashMap<>();
        Map<String, JSONObject> byLooseTitle = new HashMap<>();
        Map<String, JSONObject> byEpisodeOnly = new HashMap<>();
        for (int i = 0; i < metaRows.length(); i++) {
            JSONObject row = metaRows.optJSONObject(i);
            if (row == null) continue;
            String season = normalizeNumber(row.optString("season", ""));
            String episodeNum = normalizeNumber(row.optString("episodeNum", ""));
            if (isBlank(episodeNum)) {
                episodeNum = normalizeNumber(inferEpisodeNumberFromTitle(row.optString("title", "")));
            }
            if (!isBlank(season) && !isBlank(episodeNum)) {
                bySeasonEpisode.put(season + ":" + episodeNum, row);
            }
            if (!isBlank(episodeNum)) {
                byEpisodeOnly.putIfAbsent(episodeNum, row);
            }
            String title = normalizeTitle(cleanEpisodeTitle(row.optString("title", "")));
            if (!isBlank(title)) {
                byTitle.put(title, row);
            }
            String looseTitle = normalizeTitle(extractLooseEpisodeTitle(row.optString("title", "")));
            if (!isBlank(looseTitle)) {
                byLooseTitle.put(looseTitle, row);
            }
        }

        for (WatchingEpisode episode : episodes) {
            String normalizedSeason = normalizeNumber(episode.season);
            String normalizedEpisode = normalizeNumber(firstNonBlank(episode.episodeNum, inferEpisodeNumberFromTitle(episode.title)));
            JSONObject meta = bySeasonEpisode.get(normalizedSeason + ":" + normalizedEpisode);
            if (meta == null) {
                meta = byTitle.get(normalizeTitle(cleanEpisodeTitle(episode.title)));
            }
            if (meta == null) {
                meta = byLooseTitle.get(normalizeTitle(extractLooseEpisodeTitle(episode.title)));
            }
            if (meta == null && !isBlank(normalizedEpisode)) {
                meta = byEpisodeOnly.get(normalizedEpisode);
            }
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
            episode.releaseDate = firstNonBlank(episode.releaseDate, meta.optString("releaseDate", ""));
            if (!isBlank(episode.imageUrl)) {
                episode.channel.setLogo(episode.imageUrl);
            }
        }
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
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                if (watchStateListenerRegistered) {
                    SeriesWatchStateService.getInstance().removeChangeListener(watchStateChangeListener);
                    watchStateListenerRegistered = false;
                }
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
        if (isBlank(accountId) || isBlank(seriesId) || seriesAccordion == null) {
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
                Account account = AccountService.getInstance().getById(accountId);
                List<SeriesPanelData> updated = new ArrayList<>();
                if (account != null && !isBlank(account.getDbId())) {
                    account.setAction(Account.AccountAction.series);
                    Map<String, SeriesWatchState> deduped = new LinkedHashMap<>();
                    for (SeriesWatchState state : SeriesWatchStateService.getInstance().getAllSeriesLastWatchedByAccount(account.getDbId())) {
                        if (state == null || isBlank(state.getSeriesId()) || !safe(state.getSeriesId()).equals(safe(seriesId))) {
                            continue;
                        }
                        String key = safe(state.getCategoryId()) + "|" + safe(state.getSeriesId());
                        SeriesWatchState existing = deduped.get(key);
                        if (existing == null || state.getUpdatedAt() > existing.getUpdatedAt()) {
                            deduped.put(key, state);
                        }
                    }
                    for (SeriesWatchState state : deduped.values()) {
                        SeriesPanelData panel = buildPanel(account, state);
                        if (panel != null) {
                            updated.add(panel);
                        }
                    }
                }
                Platform.runLater(() -> applySeriesDelta(accountId, seriesId, updated));
            } finally {
                reloadInProgress.set(false);
                if (reloadQueued.getAndSet(false) || dirty) {
                    Platform.runLater(this::refreshIfNeeded);
                }
            }
        }, "watching-now-delta-loader").start();
    }

    private void applySeriesDelta(String accountId, String seriesId, List<SeriesPanelData> updated) {
        String prefix = safe(accountId) + "|";
        String suffix = "|" + safe(seriesId);
        panelDataByKey.keySet().removeIf(key -> key.startsWith(prefix) && key.endsWith(suffix));
        for (SeriesPanelData panel : updated) {
            panelDataByKey.put(seriesPaneKey(panel), panel);
        }
        List<SeriesPanelData> rows = new ArrayList<>(panelDataByKey.values());
        rows.sort(Comparator.comparing((SeriesPanelData d) -> safe(d.seriesTitle), String.CASE_INSENSITIVE_ORDER));

        String expandedKey = lastExpandedSeriesKey;
        if (seriesAccordion != null && seriesAccordion.getExpandedPane() != null) {
            Object key = seriesAccordion.getExpandedPane().getUserData();
            if (key instanceof String s) {
                expandedKey = s;
            }
        }
        rebuildAccordion(rows, expandedKey);
    }

    private void rebuildAccordion(List<SeriesPanelData> rows, String expandedKey) {
        contentBox.getChildren().clear();
        if (rows.isEmpty()) {
            contentBox.getChildren().add(new Label("No currently watched series found."));
            seriesAccordion = null;
            lastExpandedSeriesKey = "";
            return;
        }
        Accordion accordion = new Accordion();
        seriesAccordion = accordion;
        accordion.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(accordion, Priority.ALWAYS);
        panelDataByKey.clear();
        for (SeriesPanelData data : rows) {
            String key = seriesPaneKey(data);
            panelDataByKey.put(key, data);
            accordion.getPanes().add(createSeriesPane(data));
        }
        if (!isBlank(expandedKey)) {
            for (TitledPane pane : accordion.getPanes()) {
                if (expandedKey.equals(safe((String) pane.getUserData()))) {
                    accordion.setExpandedPane(pane);
                    break;
                }
            }
        }
        contentBox.getChildren().add(accordion);
        VBox.setVgrow(contentBox, Priority.ALWAYS);
    }

    private boolean isDisplayable() {
        return getScene() != null && isVisible();
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
        return value
                .replaceAll("(?i)^\\s*season\\s*\\d+\\s*[-:]\\s*", "")
                .replaceAll("(?i)^\\s*s\\d+\\s*[-:]\\s*", "")
                .trim();
    }

    private String normalizeNumber(String value) {
        if (isBlank(value)) return "";
        String parsed = value.replaceAll("[^0-9]", "");
        if (isBlank(parsed)) return "";
        try {
            return String.valueOf(Integer.parseInt(parsed));
        } catch (Exception ignored) {
            return "";
        }
    }

    private int parseNumberOrDefault(String value, int fallback) {
        try {
            if (isBlank(value)) return fallback;
            return Integer.parseInt(value);
        } catch (Exception ignored) {
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
            return UI_DATE_FORMATTER.format(parsed);
        }
        if (v.matches("^\\d{4}-\\d{2}-\\d{2}.*")) {
            return v.substring(0, 10);
        }
        int t = v.indexOf('T');
        if (t > 0) {
            String left = v.substring(0, t);
            if (left.matches("\\d{4}-\\d{2}-\\d{2}")) {
                return left;
            }
        }
        Matcher monthMatcher = MONTH_DATE_PATTERN.matcher(v);
        if (monthMatcher.find()) {
            return monthMatcher.group();
        }
        Matcher slashMatcher = SLASH_DATE_PATTERN.matcher(v);
        if (slashMatcher.find()) {
            return slashMatcher.group();
        }
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(v);
        if (isoMatcher.find()) {
            return isoMatcher.group();
        }
        if (v.contains(",")) {
            String[] parts = v.split(",");
            if (parts.length >= 2) {
                return parts[0].trim() + ", " + parts[1].trim();
            }
        }
        return v;
    }

    private LocalDate parseDate(String value) {
        String input = safe(value).trim();
        if (isBlank(input)) {
            return null;
        }
        try {
            return OffsetDateTime.parse(input).toLocalDate();
        } catch (Exception ignored) {
        }
        String[] patterns = new String[]{
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
        };
        for (String pattern : patterns) {
            try {
                return LocalDate.parse(input, DateTimeFormatter.ofPattern(pattern, Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
            }
        }
        Matcher iso = ISO_DATE_PATTERN.matcher(input);
        if (iso.find()) {
            try {
                return LocalDate.parse(iso.group(), DateTimeFormatter.ofPattern("yyyy-MM-dd", Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
            }
        }
        Matcher month = MONTH_DATE_PATTERN.matcher(input);
        if (month.find()) {
            String candidate = month.group();
            try {
                return LocalDate.parse(candidate, DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
            }
            try {
                return LocalDate.parse(candidate, DateTimeFormatter.ofPattern("MMMM d, yyyy", Locale.ENGLISH));
            } catch (DateTimeParseException ignored) {
            }
        }
        return null;
    }

    private String normalizeImageUrl(String imageUrl, Account account) {
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
        URI base = resolveBaseUri(account);
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

    private URI resolveBaseUri(Account account) {
        if (account == null) {
            return null;
        }
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
        String port = "8888";
        try {
            String configured = ConfigurationService.getInstance().read().getServerPort();
            if (!isBlank(configured)) {
                port = configured.trim();
            }
        } catch (Exception ignored) {
        }
        return "http://127.0.0.1:" + port;
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

    private static final class SeriesPanelData {
        private final Account account;
        private final SeriesWatchState state;
        private final String seriesTitle;
        private final JSONObject seasonInfo;
        private final List<WatchingEpisode> episodes;
        private boolean imdbLoaded;
        private boolean imdbLoading;

        private Label titleNode;
        private Label ratingNode;
        private Label genreNode;
        private Label releaseNode;
        private Label plotNode;
        private HBox imdbBadgeNode;
        private HBox imdbLoadingNode;
        private ImageView seriesPosterNode;
        private TabPane seasonTabs;
        private final Map<String, VBox> seasonCardsBySeason = new LinkedHashMap<>();

        private SeriesPanelData(Account account, SeriesWatchState state, String seriesTitle, JSONObject seasonInfo, List<WatchingEpisode> episodes) {
            this.account = account;
            this.state = state;
            this.seriesTitle = seriesTitle;
            this.seasonInfo = seasonInfo == null ? new JSONObject() : seasonInfo;
            this.episodes = episodes == null ? new ArrayList<>() : episodes;
            this.imdbLoaded = false;
            this.imdbLoading = false;
        }
    }

    private static final class WatchingEpisode {
        private final Account account;
        private final SeriesWatchState state;
        private final String seriesTitle;
        private final Channel channel;
        private final String season;
        private final String episodeNum;
        private final String title;
        private String imageUrl;
        private String plot;
        private String releaseDate;
        private String rating;
        private boolean watched;

        private WatchingEpisode(Account account,
                                SeriesWatchState state,
                                String seriesTitle,
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
            this.seriesTitle = seriesTitle;
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
