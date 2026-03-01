package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.SeriesEpisodeService;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isBlank;

public class ThumbnailEpisodesListUI extends BaseEpisodesListUI {
    private final TabPane seasonTabPane = new TabPane();
    private final VBox cardsContainer = new VBox(8);
    private final ScrollPane cardsScroll = new ScrollPane(cardsContainer);
    private final HBox header = new HBox(12);
    private final ImageView seriesPosterNode = new ImageView();
    private final VBox headerDetails = new VBox(4);
    private final Label titleNode = new Label();
    private final Label ratingNode = new Label();
    private final Label genreNode = new Label();
    private final Label releaseNode = new Label();
    private final Label plotNode = new Label();
    private final Button reloadEpisodesButton = new Button("Reload Episodes from Portal");
    private final HBox imdbLoadingNode = new HBox(6);
    private HBox imdbBadgeNode;
    private volatile boolean imdbLoading = false;
    private volatile boolean imdbLoaded = false;
    private VBox selectedEpisodeCard;
    private boolean watchingNowDetailStylingApplied = false;

    public ThumbnailEpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        super(account, categoryTitle, seriesId, seriesCategoryId);
        finishInit();
        setItems(channelList);
    }

    public ThumbnailEpisodesListUI(Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        super(account, categoryTitle, seriesId, seriesCategoryId);
        finishInit();
    }

    @Override
    protected void initWidgets() {
        ImageCacheManager.clearCache("episode");
        initHeader();
        seasonTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        seasonTabPane.setMaxWidth(Double.MAX_VALUE);
        seasonTabPane.setMaxHeight(Double.MAX_VALUE);
        seasonTabPane.setMinHeight(36);
        seasonTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> applySeasonFilter());

        cardsContainer.setPadding(new Insets(4));
        cardsContainer.setFillWidth(true);

        cardsScroll.setFitToWidth(true);
        cardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        cardsScroll.setMaxWidth(Double.MAX_VALUE);
        cardsScroll.setMaxHeight(Double.MAX_VALUE);

        VBox body = new VBox(6, header, seasonTabPane, cardsScroll);
        body.setMaxWidth(Double.MAX_VALUE);
        body.setMaxHeight(Double.MAX_VALUE);
        body.setPadding(new Insets(0, 4, 0, 4));
        HBox.setHgrow(body, Priority.ALWAYS);
        header.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(header, Priority.SOMETIMES);
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
        VBox.setVgrow(seasonTabPane, Priority.NEVER);
        contentStack.getChildren().add(body);
    }

    @Override
    protected void onItemsLoaded() {
        applySeriesHeader();
        refreshSeasonTabs();
        applySeasonFilter();
        triggerImdbLazyLoad();
    }

    @Override
    protected void showPlaceholder(String text) {
        cardsContainer.getChildren().setAll(new Label(text));
    }

    @Override
    protected void setEmptyState(String message, boolean empty) {
        header.setManaged(!empty);
        header.setVisible(!empty);
        seasonTabPane.setManaged(!empty);
        seasonTabPane.setVisible(!empty);
        cardsScroll.setManaged(!empty);
        cardsScroll.setVisible(!empty);
        emptyStateLabel.setText(message == null ? "" : message);
        emptyStateLabel.setManaged(empty);
        emptyStateLabel.setVisible(empty);
    }

    @Override
    protected void clearEpisodesAndRefreshTabs() {
        itemsLoaded.set(false);
        channelList.episodes.clear();
        allEpisodeItems.clear();
        refreshSeasonTabs();
        applySeasonFilter();
    }

    @Override
    protected void onBookmarksRefreshed() {
        applySeasonFilter();
    }

    @Override
    protected void onWatchedStatesRefreshed() {
        applySeasonFilter();
    }

    private void initHeader() {
        header.setAlignment(Pos.TOP_LEFT);
        header.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6;");

        seriesPosterNode.setFitWidth(170);
        seriesPosterNode.setFitHeight(250);
        seriesPosterNode.setPreserveRatio(true);
        seriesPosterNode.setSmooth(true);

        titleNode.setStyle("-fx-font-weight: bold;");
        titleNode.setWrapText(true);
        titleNode.setMaxWidth(Double.MAX_VALUE);
        plotNode.setWrapText(true);
        plotNode.setMaxWidth(Double.MAX_VALUE);
        plotNode.setMinHeight(Region.USE_PREF_SIZE);
        headerDetails.setMaxWidth(Double.MAX_VALUE);
        headerDetails.setFillWidth(true);
        headerDetails.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(plotNode, Priority.ALWAYS);
        plotNode.prefWidthProperty().bind(headerDetails.widthProperty().subtract(6));
        titleNode.prefWidthProperty().bind(headerDetails.widthProperty().subtract(6));
        ProgressIndicator imdbProgress = new ProgressIndicator();
        imdbProgress.setPrefSize(14, 14);
        imdbProgress.setMinSize(14, 14);
        imdbProgress.setMaxSize(14, 14);
        Label imdbLoadingLabel = new Label("Loading IMDb details...");
        imdbLoadingNode.getChildren().setAll(imdbProgress, imdbLoadingLabel);
        reloadEpisodesButton.setFocusTraversable(false);
        reloadEpisodesButton.setOnAction(event -> reloadEpisodesFromPortal());

        headerDetails.getChildren().setAll(titleNode);
        HBox.setHgrow(headerDetails, Priority.ALWAYS);

        header.getChildren().setAll(seriesPosterNode, headerDetails);
    }

    public void applyWatchingNowDetailStyling() {
        if (watchingNowDetailStylingApplied) {
            return;
        }
        watchingNowDetailStylingApplied = true;
        header.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6;");
        seasonTabPane.setStyle("");
    }

    private void applySeriesHeader() {
        String title = firstNonBlank(seasonInfo.optString("name", ""), categoryTitle);
        titleNode.setText(title);

        headerDetails.getChildren().removeAll(ratingNode, genreNode, releaseNode, plotNode, imdbLoadingNode, reloadEpisodesButton);
        if (imdbBadgeNode != null) {
            headerDetails.getChildren().remove(imdbBadgeNode);
        }

        String rating = seasonInfo.optString("rating", "");
        if (!isBlank(rating)) {
            ratingNode.setText("IMDb: " + rating);
        }

        String imdbUrl = seasonInfo.optString("imdbUrl", "");
        imdbBadgeNode = SeriesCardUiSupport.createImdbRatingPill(rating, imdbUrl);
        if (imdbBadgeNode != null) {
            headerDetails.getChildren().add(imdbBadgeNode);
        }
        if (imdbLoading && !imdbLoaded) {
            headerDetails.getChildren().add(imdbLoadingNode);
        }

        String genre = seasonInfo.optString("genre", "");
        if (!isBlank(genre)) {
            genreNode.setText("Genre: " + genre);
            headerDetails.getChildren().add(genreNode);
        }

        String releaseDate = seasonInfo.optString("releaseDate", "");
        if (!isBlank(releaseDate)) {
            releaseNode.setText("Release: " + releaseDate);
            headerDetails.getChildren().add(releaseNode);
        }

        String plot = seasonInfo.optString("plot", "");
        if (!isBlank(plot)) {
            plotNode.setText(plot);
            headerDetails.getChildren().add(plotNode);
        }
        headerDetails.getChildren().add(reloadEpisodesButton);

        String cover = normalizeImageUrl(seasonInfo.optString("cover", ""));
        if (isBlank(cover)) {
            cover = allEpisodeItems.stream().map(EpisodeItem::getLogo).filter(s -> !isBlank(s)).findFirst().orElse("");
        }
        if (!isBlank(cover)) {
            String finalCover = cover;
            ImageCacheManager.loadImageAsync(cover, "episode")
                    .thenAccept(image -> {
                        if (image != null) {
                            Platform.runLater(() -> seriesPosterNode.setImage(image));
                        } else {
                            System.out.println("EpisodesListUI series poster failed: " + finalCover);
                        }
                    });
        }
    }

    private void reloadEpisodesFromPortal() {
        if (account == null || isBlank(seriesId)) {
            return;
        }
        reloadEpisodesButton.setDisable(true);
        reloadEpisodesButton.setText("Reloading...");
        new Thread(() -> {
            EpisodeList refreshed = SeriesEpisodeService.getInstance()
                    .reloadEpisodesFromPortal(account, seriesCategoryId, seriesId, () -> false);
            Platform.runLater(() -> {
                imdbLoaded = false;
                imdbLoading = false;
                clearEpisodesAndRefreshTabs();
                if (refreshed != null && refreshed.episodes != null && !refreshed.episodes.isEmpty()) {
                    setItems(refreshed);
                } else {
                    setEmptyState("No episodes found.", true);
                }
                reloadEpisodesButton.setText("Reload Episodes from Portal");
                reloadEpisodesButton.setDisable(false);
            });
        }, "episodes-portal-reload").start();
    }

    private void refreshSeasonTabs() {
        String current = selectedSeason();
        List<String> seasons = allEpisodeItems.stream()
                .map(EpisodeItem::getSeason)
                .filter(s -> !isBlank(s))
                .distinct()
                .sorted(Comparator.comparingInt(this::parseNumberOrDefault))
                .toList();
        if (seasons.isEmpty()) {
            seasons = List.of("1");
        }

        seasonTabPane.getTabs().clear();
        for (String season : seasons) {
            Tab tab = new Tab(season);
            tab.setClosable(false);
            tab.setUserData(season);
            seasonTabPane.getTabs().add(tab);
        }

        Tab defaultTab = seasonTabPane.getTabs().stream()
                .filter(t -> "1".equals(String.valueOf(t.getUserData())))
                .findFirst()
                .orElse(seasonTabPane.getTabs().get(0));
        if (!isBlank(current)) {
            defaultTab = seasonTabPane.getTabs().stream()
                    .filter(t -> current.equals(String.valueOf(t.getUserData())))
                    .findFirst()
                    .orElse(defaultTab);
        }
        seasonTabPane.getSelectionModel().select(defaultTab);
    }

    private void applySeasonFilter() {
        if (allEpisodeItems.isEmpty()) {
            setEmptyState("No episodes found.", true);
            return;
        }
        setEmptyState("", false);
        selectedEpisodeCard = null;
        String season = selectedSeason();
        List<EpisodeItem> filtered;
        if (isBlank(season)) {
            filtered = new ArrayList<>(allEpisodeItems);
        } else {
            filtered = allEpisodeItems.stream().filter(i -> season.equals(i.getSeason())).toList();
        }

        cardsContainer.getChildren().clear();
        if (filtered.isEmpty()) {
            cardsContainer.getChildren().add(new Label("No episodes found."));
            return;
        }
        for (EpisodeItem item : filtered) {
            cardsContainer.getChildren().add(createEpisodeCard(item));
        }
    }

    private String selectedSeason() {
        Tab selected = seasonTabPane.getSelectionModel().getSelectedItem();
        return selected != null ? String.valueOf(selected.getUserData()) : "";
    }

    private VBox createEpisodeCard(EpisodeItem row) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(6));
        String baseStyle = "-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6;";
        root.setStyle(baseStyle);
        root.getProperties().put("baseStyle", baseStyle);

        HBox top = new HBox(10);
        top.setAlignment(Pos.TOP_LEFT);

        ImageView poster = SeriesCardUiSupport.createFitPoster(row.getLogo(), 96, 136, "episode");
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
        if (row.isWatched()) {
            Label watched = new Label("WATCHING");
            watched.getStyleClass().add("drm-badge");
            watched.setMinWidth(Region.USE_PREF_SIZE);
            watched.setMaxWidth(Double.MAX_VALUE);
            badges.getChildren().add(watched);
        }
        ContextMenu rowMenu = addRightClickContextMenu(row, root);
        Button playButton = new Button("Play ...");
        playButton.getStyleClass().setAll("button");
        playButton.setMinWidth(Region.USE_PREF_SIZE);
        playButton.setMaxWidth(Double.MAX_VALUE);
        playButton.setMinHeight(Region.USE_PREF_SIZE);
        playButton.setStyle("-fx-font-size: 10px; -fx-font-weight: bold; -fx-padding: 2 6 2 6; -fx-background-radius: 6;");
        playButton.setFocusTraversable(false);
        playButton.setOnAction(event -> {
            event.consume();
            rowMenu.hide();
            rowMenu.show(playButton, Side.BOTTOM, 0, 0);
        });
        badges.getChildren().add(playButton);

        HBox actionRow = new HBox();
        actionRow.setAlignment(Pos.TOP_RIGHT);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        actionRow.getChildren().addAll(actionSpacer, badges);

        Label title = new Label(row.getEpisodeName());
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(Region.USE_PREF_SIZE);
        title.setStyle("-fx-font-weight: bold;");
        registerLabelBaseStyle(title);
        text.getChildren().addAll(actionRow, title);
        List<Label> cardLabels = new ArrayList<>();
        cardLabels.add(title);
        if (!isBlank(row.getReleaseDate())) {
            Label release = new Label("Release: " + shortDateOnly(row.getReleaseDate()));
            registerLabelBaseStyle(release);
            text.getChildren().add(release);
            cardLabels.add(release);
        }
        if (!isBlank(row.getRating())) {
            Label rating = new Label("Rating: " + row.getRating());
            registerLabelBaseStyle(rating);
            text.getChildren().add(rating);
            cardLabels.add(rating);
        }

        top.getChildren().addAll(posterWrap, text);
        root.getChildren().add(top);

        if (!isBlank(row.getPlot())) {
            Label plot = new Label(row.getPlot());
            plot.setWrapText(true);
            plot.setMaxWidth(Double.MAX_VALUE);
            plot.setMinHeight(Region.USE_PREF_SIZE);
            registerLabelBaseStyle(plot);
            root.getChildren().add(plot);
            cardLabels.add(plot);
        }
        root.getProperties().put("cardLabels", cardLabels);
        root.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                play(row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                setSelectedEpisodeCard(root);
            }
        });
        return root;
    }

    private void setSelectedEpisodeCard(VBox current) {
        if (current == null) {
            return;
        }
        if (selectedEpisodeCard != null && selectedEpisodeCard != current) {
            applyCardSelection(selectedEpisodeCard, false);
        }
        applyCardSelection(current, true);
        selectedEpisodeCard = current;
    }

    @SuppressWarnings("unchecked")
    private void applyCardSelection(VBox card, boolean selected) {
        if (card == null) {
            return;
        }
        Object baseStyle = card.getProperties().get("baseStyle");
        String base = baseStyle instanceof String ? (String) baseStyle : "";
        if (selected) {
            card.setStyle(base + " -fx-background-color: -fx-selection-bar;");
        } else {
            card.setStyle(base);
        }
        Object labelsObj = card.getProperties().get("cardLabels");
        if (labelsObj instanceof List<?> labels) {
            for (Object labelObj : labels) {
                if (labelObj instanceof Label label) {
                    applyLabelSelection(label, selected);
                }
            }
        }
    }

    private void registerLabelBaseStyle(Label label) {
        if (label == null) {
            return;
        }
        Object existing = label.getProperties().get("baseTextStyle");
        if (existing == null) {
            label.getProperties().put("baseTextStyle", label.getStyle() == null ? "" : label.getStyle());
        }
    }

    private void applyLabelSelection(Label label, boolean selected) {
        if (label == null) {
            return;
        }
        Object baseStyle = label.getProperties().get("baseTextStyle");
        String base = baseStyle instanceof String ? (String) baseStyle : "";
        if (selected) {
            label.setStyle(base + " -fx-text-fill: white;");
        } else {
            label.setStyle(base);
        }
    }

    private ContextMenu addRightClickContextMenu(EpisodeItem item, Pane target) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        Menu lastWatchedMenu = new Menu("Last Watched");
        rowMenu.getItems().add(lastWatchedMenu);

        rowMenu.setOnShowing(event -> {
            lastWatchedMenu.getItems().clear();
            if (item == null) return;

            MenuItem markWatched = new MenuItem("Mark as Watched");
            markWatched.setOnAction(e -> markEpisodeAsWatched(item));
            lastWatchedMenu.getItems().add(markWatched);

            MenuItem clearWatched = new MenuItem("Clear Watched Marker");
            clearWatched.setDisable(!item.isWatched());
            clearWatched.setOnAction(e -> clearWatchedMarker());
            lastWatchedMenu.getItems().add(clearWatched);
        });

        MenuItem playerEmbeddedItem = new MenuItem("Embedded Player");
        playerEmbeddedItem.setOnAction(event -> {
            rowMenu.hide();
            play(item, "embedded");
        });
        MenuItem player1Item = new MenuItem("Player 1");
        player1Item.setOnAction(event -> {
            rowMenu.hide();
            play(item, ConfigurationService.getInstance().read().getPlayerPath1());
        });
        MenuItem player2Item = new MenuItem("Player 2");
        player2Item.setOnAction(event -> {
            rowMenu.hide();
            play(item, ConfigurationService.getInstance().read().getPlayerPath2());
        });
        MenuItem player3Item = new MenuItem("Player 3");
        player3Item.setOnAction(event -> {
            rowMenu.hide();
            play(item, ConfigurationService.getInstance().read().getPlayerPath3());
        });

        rowMenu.getItems().addAll(new SeparatorMenuItem(), playerEmbeddedItem, player1Item, player2Item, player3Item);
        target.setOnContextMenuRequested(event -> rowMenu.show(target, event.getScreenX(), event.getScreenY()));
        return rowMenu;
    }

    private void triggerImdbLazyLoad() {
        if (imdbLoaded || imdbLoading) {
            return;
        }
        imdbLoading = true;
        Platform.runLater(this::applySeriesHeader);
        new Thread(() -> {
            try {
                JSONObject imdb = findImdbWithRetry(
                        firstNonBlank(seasonInfo.optString("name", ""), categoryTitle),
                        seasonInfo.optString("tmdb", ""),
                        3
                );
                if (imdb != null) {
                    String imdbCover = normalizeImageUrl(imdb.optString("cover", ""));
                    if (!isBlank(imdbCover)) {
                        imdb.put("cover", imdbCover);
                        seasonInfo.put("cover", imdbCover);
                    }
                    mergeMissing(seasonInfo, imdb, "name");
                    mergeMissing(seasonInfo, imdb, "plot");
                    mergeMissing(seasonInfo, imdb, "cast");
                    mergeMissing(seasonInfo, imdb, "director");
                    mergeMissing(seasonInfo, imdb, "genre");
                    mergeMissing(seasonInfo, imdb, "releaseDate");
                    mergeMissing(seasonInfo, imdb, "rating");
                    mergeMissing(seasonInfo, imdb, "tmdb");
                    mergeMissing(seasonInfo, imdb, "imdbUrl");
                    enrichEpisodesFromMeta(allEpisodeItems, imdb.optJSONArray("episodesMeta"));
                }
            } finally {
                imdbLoaded = true;
                imdbLoading = false;
                Platform.runLater(() -> {
                    applySeriesHeader();
                    applySeasonFilter();
                });
            }
        }, "episodes-imdb-loader").start();
    }

    private void enrichEpisodesFromMeta(List<EpisodeItem> episodes, JSONArray metaRows) {
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

        for (EpisodeItem episode : episodes) {
            try {
                String normalizedSeason = normalizeNumber(episode.getSeason());
                String normalizedEpisode = normalizeNumber(firstNonBlank(episode.getEpisodeNumber(), inferEpisodeNumberFromTitle(episode.getEpisodeName())));
                JSONObject meta = bySeasonEpisode.get(normalizedSeason + ":" + normalizedEpisode);
                if (meta == null) {
                    meta = byTitle.get(normalizeTitle(cleanEpisodeTitle(episode.getEpisodeName())));
                }
                if (meta == null) {
                    meta = byLooseTitle.get(normalizeTitle(extractLooseEpisodeTitle(episode.getEpisodeName())));
                }
                if (meta == null && !isBlank(normalizedEpisode)) {
                    meta = byEpisodeOnly.get(normalizedEpisode);
                }
                if (meta == null) {
                    continue;
                }
                String metaLogo = normalizeImageUrl(meta.optString("logo", ""));
                episode.setLogo(firstNonBlank(metaLogo, episode.getLogo()));
                episode.setPlot(firstNonBlank(
                        meta.optString("plot", ""),
                        meta.optString("description", ""),
                        meta.optString("overview", ""),
                        episode.getPlot()
                ));
                episode.setReleaseDate(firstNonBlank(episode.getReleaseDate(), meta.optString("releaseDate", "")));
                episode.setRating(firstNonBlank(episode.getRating(), meta.optString("rating", "")));
            } catch (Exception ignored) {
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
        java.util.regex.Matcher m = EPISODE_PATTERN.matcher(safe(title));
        if (m.find()) {
            return normalizeNumber(firstNonBlank(m.group(1), m.group(2), m.group(3)));
        }
        java.util.regex.Matcher sxey = SXXEYY_PATTERN.matcher(safe(title));
        if (sxey.find()) {
            return normalizeNumber(sxey.group(2));
        }
        return "";
    }

    private JSONObject findImdbWithRetry(String title, String tmdb, int maxAttempts) {
        List<String> hints = buildImdbFuzzyHints(title);
        int attempts = Math.max(1, maxAttempts);
        for (int attempt = 1; attempt <= attempts; attempt++) {
            try {
                JSONObject imdb = ImdbMetadataService.getInstance().findBestEffortDetails(title, tmdb, hints);
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

    private List<String> buildImdbFuzzyHints(String title) {
        LinkedHashMap<String, Boolean> ordered = new LinkedHashMap<>();
        addHint(ordered, title);
        addHint(ordered, categoryTitle);
        addHint(ordered, seasonInfo.optString("name", ""));
        addHint(ordered, seasonInfo.optString("plot", ""));
        String year = extractYear(firstNonBlank(
                seasonInfo.optString("releaseDate", ""),
                allEpisodeItems.stream().map(EpisodeItem::getReleaseDate).filter(v -> !isBlank(v)).findFirst().orElse("")
        ));
        if (!isBlank(year)) {
            addHint(ordered, title + " " + year);
            addHint(ordered, categoryTitle + " " + year);
        }
        for (EpisodeItem episode : allEpisodeItems.stream().limit(6).toList()) {
            addHint(ordered, cleanEpisodeTitle(episode.getEpisodeName()));
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

    private String extractYear(String value) {
        if (isBlank(value)) {
            return "";
        }
        java.util.regex.Matcher matcher = java.util.regex.Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(value);
        return matcher.find() ? matcher.group() : "";
    }
}
