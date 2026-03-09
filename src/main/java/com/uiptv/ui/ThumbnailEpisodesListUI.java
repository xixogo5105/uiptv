package com.uiptv.ui;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.util.I18n;

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
import javafx.scene.control.MenuButton;
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
import java.util.function.Consumer;

import static com.uiptv.util.StringUtils.isBlank;

public class ThumbnailEpisodesListUI extends BaseEpisodesListUI {
    private static final String KEY_CARD_LABELS = "cardLabels";
    private static final String EPISODE_CACHE = "episode";
    private static final String KEY_COVER = "cover";
    private static final String KEY_RATING = "rating";
    private static final String KEY_RELEASE_DATE = "releaseDate";
    private static final String KEY_TITLE = "title";
    private final TabPane seasonTabPane = new TabPane();
    private final VBox cardsContainer = new VBox(8);
    private final ScrollPane cardsScroll = new ScrollPane(cardsContainer);
    private final HBox header = new HBox(12);
    private final ImageView seriesPosterNode = new ImageView();
    private final VBox headerDetails = new VBox(4);
    private final HBox titleRow = new HBox(8);
    private final Label titleNode = new Label();
    private final Label ratingNode = new Label();
    private final Label genreNode = new Label();
    private final Label releaseNode = new Label();
    private final Label plotNode = new Label();
    private final MenuButton bingeWatchButton = new MenuButton();
    private final Button reloadEpisodesButton = new Button(I18n.tr("autoReloadFromServer"));
    private final HBox imdbLoadingNode = new HBox(6);
    private HBox imdbBadgeNode;
    private volatile boolean imdbLoading = false;
    private volatile boolean imdbLoaded = false;
    private VBox selectedEpisodeCard;
    private boolean watchingNowDetailStylingApplied = false;
    private VBox bodyContainer;
    private final Map<EpisodeItem, VBox> renderedCardsByItem = new HashMap<>();
    private Consumer<JSONObject> seasonInfoListener;
    private String pendingTargetSeason;
    private String pendingTargetEpisodeId;
    private String pendingTargetEpisodeNumber;
    private String pendingTargetEpisodeName;

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
        ImageCacheManager.clearCache(EPISODE_CACHE);
        initHeader();
        seasonTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        seasonTabPane.setMaxWidth(Double.MAX_VALUE);
        seasonTabPane.setMaxHeight(Double.MAX_VALUE);
        seasonTabPane.setMinHeight(36);
        seasonTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> {
            applySeasonFilter();
            updateBingeWatchButton();
        });

        cardsContainer.setPadding(new Insets(5));
        cardsContainer.setFillWidth(true);
        cardsContainer.setMaxWidth(Double.MAX_VALUE);

        cardsScroll.setFitToWidth(true);
        cardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        cardsScroll.setMaxWidth(Double.MAX_VALUE);
        cardsScroll.setMaxHeight(Double.MAX_VALUE);
        cardsScroll.getStyleClass().add("transparent-scroll-pane");

        bodyContainer = new VBox(6, header, seasonTabPane, cardsScroll);
        bodyContainer.setMaxWidth(Double.MAX_VALUE);
        bodyContainer.setMaxHeight(Region.USE_COMPUTED_SIZE);
        bodyContainer.setPadding(new Insets(0, 4, 0, 4));
        HBox.setHgrow(bodyContainer, Priority.ALWAYS);
        header.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(header, Priority.NEVER);
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
        VBox.setVgrow(seasonTabPane, Priority.NEVER);
        contentStack.getChildren().add(bodyContainer);
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
        channelList.getEpisodes().clear();
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

    @Override
    protected void navigateToEpisodeTarget(String season, String episodeId, String episodeNumber, String episodeName) {
        this.pendingTargetSeason = season;
        this.pendingTargetEpisodeId = episodeId;
        this.pendingTargetEpisodeNumber = episodeNumber;
        this.pendingTargetEpisodeName = episodeName;
        String requestedSeason = normalizeNumber(season);
        if (!isBlank(requestedSeason)) {
            Tab requestedSeasonTab = seasonTabPane.getTabs().stream()
                    .filter(t -> requestedSeason.equals(normalizeNumber(String.valueOf(t.getUserData()))))
                    .findFirst()
                    .orElse(null);
            if (requestedSeasonTab != null) {
                seasonTabPane.getSelectionModel().select(requestedSeasonTab);
            }
        }

        EpisodeItem match = findBestEpisodeMatch(season, episodeId, episodeNumber, episodeName);
        if (match == null) {
            return;
        }

        String targetSeason = normalizeNumber(match.getSeason());
        if (!isBlank(targetSeason)) {
            Tab seasonTab = seasonTabPane.getTabs().stream()
                    .filter(t -> targetSeason.equals(normalizeNumber(String.valueOf(t.getUserData()))))
                    .findFirst()
                    .orElse(null);
            if (seasonTab != null) {
                seasonTabPane.getSelectionModel().select(seasonTab);
            }
        }

        applySeasonFilter();
        VBox card = renderedCardsByItem.get(match);
        if (card == null) {
            return;
        }
        setSelectedEpisodeCard(card);
        int index = cardsContainer.getChildren().indexOf(card);
        int size = cardsContainer.getChildren().size();
        if (index >= 0 && size > 1) {
            cardsScroll.setVvalue((double) index / (double) (size));
        } else {
            cardsScroll.setVvalue(0.0);
        }
    }

    private void initHeader() {
        header.setAlignment(Pos.TOP_LEFT);
        header.getStyleClass().add("uiptv-outline-pane");
        header.setPadding(new Insets(5));

        seriesPosterNode.setFitWidth(170);
        seriesPosterNode.setFitHeight(250);
        seriesPosterNode.setPreserveRatio(true);
        seriesPosterNode.setSmooth(true);

        titleNode.getStyleClass().add("strong-label");
        titleNode.setWrapText(true);
        titleNode.setMaxWidth(Double.MAX_VALUE);
        titleNode.setMinWidth(0);
        HBox.setHgrow(titleNode, Priority.ALWAYS);
        plotNode.setWrapText(true);
        plotNode.setMaxWidth(Double.MAX_VALUE);
        plotNode.setMinWidth(0);
        plotNode.setMinHeight(Region.USE_PREF_SIZE);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        headerDetails.setMaxWidth(Double.MAX_VALUE);
        headerDetails.setFillWidth(true);
        headerDetails.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(plotNode, Priority.ALWAYS);
        plotNode.prefWidthProperty().bind(headerDetails.widthProperty().subtract(6));
        ProgressIndicator imdbProgress = new ProgressIndicator();
        imdbProgress.setPrefSize(14, 14);
        imdbProgress.setMinSize(14, 14);
        imdbProgress.setMaxSize(14, 14);
        Label imdbLoadingLabel = new Label(I18n.tr("autoLoadingIMDbDetails"));
        imdbLoadingNode.getChildren().setAll(imdbProgress, imdbLoadingLabel);
        bingeWatchButton.setFocusTraversable(true);
        bingeWatchButton.getStyleClass().setAll("button");
        bingeWatchButton.getStyleClass().add("small-pill-button");
        reloadEpisodesButton.setFocusTraversable(true);
        reloadEpisodesButton.setOnAction(event -> reloadEpisodesFromPortal());

        titleRow.getChildren().setAll(titleNode, bingeWatchButton);
        headerDetails.getChildren().setAll(titleRow);
        HBox.setHgrow(headerDetails, Priority.ALWAYS);

        header.getChildren().setAll(seriesPosterNode, headerDetails);
    }

    public void applyWatchingNowDetailStyling() {
        if (watchingNowDetailStylingApplied) {
            return;
        }
        watchingNowDetailStylingApplied = true;
        header.getStyleClass().remove("uiptv-outline-pane");
        header.getStyleClass().add("uiptv-card");
        if (bodyContainer != null) {
            bodyContainer.setSpacing(1);
            bodyContainer.setPadding(new Insets(0, 1, 0, 1));
        }
        cardsContainer.setPadding(new Insets(5));
        seasonTabPane.getStyleClass().add("watching-now-detail-tabs");
    }

    private void applySeriesHeader() {
        String title = firstNonBlank(seasonInfo.optString("name", ""), categoryTitle);
        titleNode.setText(title);

        headerDetails.getChildren().removeAll(ratingNode, genreNode, releaseNode, plotNode, imdbLoadingNode, reloadEpisodesButton);
        if (imdbBadgeNode != null) {
            headerDetails.getChildren().remove(imdbBadgeNode);
        }
        titleRow.getChildren().setAll(titleNode, bingeWatchButton);
        if (!headerDetails.getChildren().contains(titleRow)) {
            headerDetails.getChildren().add(0, titleRow);
        }

        String rating = seasonInfo.optString(KEY_RATING, "");
        if (!isBlank(rating)) {
            ratingNode.setText(I18n.tr("autoImdbPrefix", rating));
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
            genreNode.setText(I18n.tr("autoGenrePrefix", genre));
            headerDetails.getChildren().add(genreNode);
        }

        String releaseDate = seasonInfo.optString(KEY_RELEASE_DATE, "");
        if (!isBlank(releaseDate)) {
            releaseNode.setText(I18n.tr("autoReleasePrefix", shortDateOnly(releaseDate)));
            headerDetails.getChildren().add(releaseNode);
        }

        String plot = seasonInfo.optString("plot", "");
        if (!isBlank(plot)) {
            plotNode.setText(plot);
            headerDetails.getChildren().add(plotNode);
        }
        updateBingeWatchButton();

        String cover = sanitizePosterUrl(normalizeImageUrl(seasonInfo.optString(KEY_COVER, "")));
        if (isBlank(cover)) {
            cover = allEpisodeItems.stream().map(EpisodeItem::getLogo).map(this::sanitizePosterUrl).filter(s -> !isBlank(s)).findFirst().orElse("");
        }
        if (!isBlank(cover)) {
            seasonInfo.put(KEY_COVER, cover);
            String finalCover = cover;
            ImageCacheManager.loadImageAsync(cover, EPISODE_CACHE)
                    .thenAccept(image -> {
                        if (image != null) {
                            Platform.runLater(() -> seriesPosterNode.setImage(image));
                        } else {
                            com.uiptv.util.AppLog.addLog("EpisodesListUI series poster failed: " + finalCover);
                        }
                    });
        }
        publishSeasonInfo();
    }

    private void publishSeasonInfo() {
        if (seasonInfoListener != null) {
            seasonInfoListener.accept(new JSONObject(seasonInfo.toString()));
        }
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

    private void reloadEpisodesFromPortal() {
        if (account == null || isBlank(seriesId)) {
            return;
        }
        reloadEpisodesButton.setDisable(true);
        reloadEpisodesButton.setText(I18n.tr("autoReloading"));
        new Thread(() -> {
            EpisodeList refreshed = SeriesEpisodeService.getInstance()
                    .reloadEpisodesFromPortal(account, seriesCategoryId, seriesId, () -> false);
            Platform.runLater(() -> {
                imdbLoaded = false;
                imdbLoading = false;
                clearEpisodesAndRefreshTabs();
                if (refreshed != null && refreshed.getEpisodes() != null && !refreshed.getEpisodes().isEmpty()) {
                    setItems(refreshed);
                } else {
                    setEmptyState("No episodes found.", true);
                }
                reloadEpisodesButton.setText(I18n.tr("autoReloadFromServer"));
                reloadEpisodesButton.setDisable(false);
            });
        }, "episodes-portal-reload").start();
    }

    public void reloadFromServer() {
        reloadEpisodesFromPortal();
    }

    public void setSeasonInfoListener(Consumer<JSONObject> seasonInfoListener) {
        this.seasonInfoListener = seasonInfoListener;
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
            Tab tab = new Tab(I18n.formatTabNumberLabel(season));
            tab.setClosable(false);
            tab.setUserData(season);
            seasonTabPane.getTabs().add(tab);
        }

        Tab defaultTab = seasonTabPane.getTabs().stream()
                .filter(t -> "1".equals(String.valueOf(t.getUserData())))
                .findFirst()
                .orElse(seasonTabPane.getTabs().getFirst());
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
            updateBingeWatchButton();
            return;
        }
        setEmptyState("", false);
        selectedEpisodeCard = null;
        renderedCardsByItem.clear();
        String season = selectedSeason();
        List<EpisodeItem> filtered;
        if (isBlank(season)) {
            filtered = new ArrayList<>(allEpisodeItems);
        } else {
            filtered = allEpisodeItems.stream().filter(i -> season.equals(i.getSeason())).toList();
        }

        cardsContainer.getChildren().clear();
        if (filtered.isEmpty()) {
            cardsContainer.getChildren().add(new Label(I18n.tr("autoNoEpisodesFound")));
            updateBingeWatchButton();
            return;
        }
        for (EpisodeItem item : filtered) {
            VBox card = createEpisodeCard(item);
            renderedCardsByItem.put(item, card);
            cardsContainer.getChildren().add(card);
        }
        updateBingeWatchButton();
    }

    private String selectedSeason() {
        Tab selected = seasonTabPane.getSelectionModel().getSelectedItem();
        return selected != null ? String.valueOf(selected.getUserData()) : "";
    }

    private VBox createEpisodeCard(EpisodeItem row) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("uiptv-card");

        HBox top = new HBox(10);
        top.setAlignment(Pos.TOP_LEFT);

        ImageView poster = SeriesCardUiSupport.createFitPoster(row.getLogo(), 96, 136, EPISODE_CACHE);
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
            Label watched = new Label(I18n.tr("autoWatching"));
            watched.getStyleClass().add("drm-badge");
            watched.setMinWidth(Region.USE_PREF_SIZE);
            watched.setMaxWidth(Double.MAX_VALUE);
            badges.getChildren().add(watched);
        }
        ContextMenu rowMenu = addRightClickContextMenu(row, root);
        Button playButton = new Button(I18n.tr("autoPlay2"));
        playButton.getStyleClass().setAll("button");
        playButton.getStyleClass().add("small-pill-button");
        playButton.setMinWidth(Region.USE_PREF_SIZE);
        playButton.setMaxWidth(Double.MAX_VALUE);
        playButton.setMinHeight(Region.USE_PREF_SIZE);
        playButton.setFocusTraversable(true);
        playButton.setOnAction(event -> {
            event.consume();
            rowMenu.hide();
            populateEpisodeContextMenu(rowMenu, row);
            rowMenu.show(playButton, Side.BOTTOM, 0, 0);
        });
        badges.getChildren().add(playButton);

        HBox actionRow = new HBox();
        actionRow.setAlignment(Pos.TOP_RIGHT);
        Region actionSpacer = new Region();
        HBox.setHgrow(actionSpacer, Priority.ALWAYS);
        actionRow.getChildren().addAll(actionSpacer, badges);

        Label title = new Label(buildEpisodeDisplayTitle(row.getSeason(), row.getEpisodeNumber(), row.getEpisodeName()));
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(Region.USE_PREF_SIZE);
        title.getStyleClass().add("strong-label");
        text.getChildren().addAll(actionRow, title);
        List<Label> cardLabels = new ArrayList<>();
        cardLabels.add(title);
        if (!isBlank(row.getReleaseDate())) {
            Label release = new Label(I18n.tr("autoReleasePrefix", shortDateOnly(row.getReleaseDate())));
            text.getChildren().add(release);
            cardLabels.add(release);
        }
        if (!isBlank(row.getRating())) {
            Label rating = new Label(I18n.tr("autoRatingPrefix", row.getRating()));
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
            root.getChildren().add(plot);
            cardLabels.add(plot);
        }
        root.getProperties().put(KEY_CARD_LABELS, cardLabels);
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

    private ContextMenu addRightClickContextMenu(EpisodeItem item, Pane target) {
        final ContextMenu rowMenu = new ContextMenu();
        I18n.preparePopupControl(rowMenu, target);
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);
        target.setOnContextMenuRequested(event -> {
            populateEpisodeContextMenu(rowMenu, item);
            if (!rowMenu.getItems().isEmpty()) {
                rowMenu.show(target, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
        return rowMenu;
    }

    private void populateEpisodeContextMenu(ContextMenu rowMenu, EpisodeItem item) {
        rowMenu.getItems().clear();
        if (item == null) {
            return;
        }
        for (WatchingNowActionMenu.ActionDescriptor action : WatchingNowActionMenu.buildEpisodeStyleActions(
                item.isWatched(),
                PlaybackUIService.getConfiguredPlayerOptions()
        )) {
            switch (action.kind()) {
                case WATCHING_NOW -> {
                    MenuItem watchingNowItem = new MenuItem(I18n.tr("autoWatchingNow"));
                    watchingNowItem.setOnAction(e -> markEpisodeAsWatched(item));
                    rowMenu.getItems().add(watchingNowItem);
                }
                case SEPARATOR -> rowMenu.getItems().add(new SeparatorMenuItem());
                case PLAYER -> {
                    MenuItem playerItem = new MenuItem(action.label());
                    playerItem.setOnAction(e -> {
                        rowMenu.hide();
                        play(item, action.playerPath());
                    });
                    rowMenu.getItems().add(playerItem);
                }
                case REMOVE_WATCHING_NOW -> {
                    MenuItem removeWatchingNowItem = new MenuItem(I18n.tr("autoRemoveWatchingNow"));
                    removeWatchingNowItem.getStyleClass().add("danger-menu-item");
                    removeWatchingNowItem.setOnAction(e -> clearWatchedMarker());
                    rowMenu.getItems().add(removeWatchingNowItem);
                }
            }
        }
    }

    private void updateBingeWatchButton() {
        String season = firstNonBlank(selectedSeason(), "1");
        bingeWatchButton.setText(buildBingeWatchMenuLabel(season));
        bingeWatchButton.getItems().clear();
        boolean usingLitePlayer = MediaPlayerFactory.getPlayerType() == VideoPlayerInterface.PlayerType.LITE;
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            if (usingLitePlayer && PlaybackUIService.EMBEDDED_PLAYER_PATH.equals(option.playerPath())) {
                continue;
            }
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.setOnAction(event -> bingeWatchSeason(season, option.playerPath()));
            bingeWatchButton.getItems().add(playerItem);
        }
        bingeWatchButton.setDisable(allEpisodeItems.isEmpty());
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
                    String imdbCover = normalizeImageUrl(imdb.optString(KEY_COVER, ""));
                    if (!isBlank(imdbCover)) {
                        imdb.put(KEY_COVER, imdbCover);
                        seasonInfo.put(KEY_COVER, imdbCover);
                    }
                    mergeMissing(seasonInfo, imdb, "name");
                    mergeMissing(seasonInfo, imdb, "plot");
                    mergeMissing(seasonInfo, imdb, "cast");
                    mergeMissing(seasonInfo, imdb, "director");
                    mergeMissing(seasonInfo, imdb, "genre");
                    mergeMissing(seasonInfo, imdb, KEY_RELEASE_DATE);
                    mergeMissing(seasonInfo, imdb, KEY_RATING);
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
                    if (pendingTargetSeason != null) {
                        navigateToEpisodeTarget(pendingTargetSeason, pendingTargetEpisodeId, pendingTargetEpisodeNumber, pendingTargetEpisodeName);
                        pendingTargetSeason = null;
                        pendingTargetEpisodeId = null;
                        pendingTargetEpisodeNumber = null;
                        pendingTargetEpisodeName = null;
                    }
                });
            }
        }, "episodes-imdb-loader").start();
    }

    private void enrichEpisodesFromMeta(List<EpisodeItem> episodes, JSONArray metaRows) {
        if (episodes == null || episodes.isEmpty() || metaRows == null || metaRows.isEmpty()) {
            return;
        }
        EpisodeMetaIndex index = buildEpisodeMetaIndex(metaRows);

        for (EpisodeItem episode : episodes) {
            try {
                JSONObject meta = findEpisodeMeta(index, episode);
                if (meta == null) {
                    continue;
                }
                applyEpisodeMeta(episode, meta);
            } catch (Exception _) {
                // Skip malformed per-episode metadata rows and keep rendering the base episode item.
            }
        }
    }

    private EpisodeMetaIndex buildEpisodeMetaIndex(JSONArray metaRows) {
        Map<String, JSONObject> bySeasonEpisode = new HashMap<>();
        Map<String, JSONObject> byTitle = new HashMap<>();
        Map<String, JSONObject> byLooseTitle = new HashMap<>();
        Map<String, JSONObject> byEpisodeOnly = new HashMap<>();
        for (int i = 0; i < metaRows.length(); i++) {
            JSONObject row = metaRows.optJSONObject(i);
            if (row == null) continue;
            String season = normalizeNumber(row.optString("season", ""));
            String episodeNum = normalizedEpisodeNumber(row);
            if (!isBlank(season) && !isBlank(episodeNum)) {
                bySeasonEpisode.put(season + ":" + episodeNum, row);
            }
            if (!isBlank(episodeNum)) {
                byEpisodeOnly.putIfAbsent(episodeNum, row);
            }
            indexEpisodeTitle(byTitle, normalizeTitle(cleanEpisodeTitle(row.optString(KEY_TITLE, ""))), row);
            indexEpisodeTitle(byLooseTitle, normalizeTitle(extractLooseEpisodeTitle(row.optString(KEY_TITLE, ""))), row);
        }
        return new EpisodeMetaIndex(bySeasonEpisode, byTitle, byLooseTitle, byEpisodeOnly);
    }

    private void indexEpisodeTitle(Map<String, JSONObject> index, String key, JSONObject row) {
        if (!isBlank(key)) {
            index.put(key, row);
        }
    }

    private String normalizedEpisodeNumber(JSONObject row) {
        String episodeNum = normalizeNumber(row.optString("episodeNum", ""));
        if (isBlank(episodeNum)) {
            episodeNum = normalizeNumber(inferEpisodeNumberFromTitle(row.optString(KEY_TITLE, "")));
        }
        return episodeNum;
    }

    private JSONObject findEpisodeMeta(EpisodeMetaIndex index, EpisodeItem episode) {
        String normalizedSeason = normalizeNumber(episode.getSeason());
        String normalizedEpisode = normalizeNumber(firstNonBlank(episode.getEpisodeNumber(), inferEpisodeNumberFromTitle(episode.getEpisodeName())));
        JSONObject meta = index.bySeasonEpisode().get(normalizedSeason + ":" + normalizedEpisode);
        if (meta == null) {
            meta = index.byTitle().get(normalizeTitle(cleanEpisodeTitle(episode.getEpisodeName())));
        }
        if (meta == null) {
            meta = index.byLooseTitle().get(normalizeTitle(extractLooseEpisodeTitle(episode.getEpisodeName())));
        }
        if (meta == null && !isBlank(normalizedEpisode)) {
            meta = index.byEpisodeOnly().get(normalizedEpisode);
        }
        return meta;
    }

    private void applyEpisodeMeta(EpisodeItem episode, JSONObject meta) {
        String metaLogo = normalizeImageUrl(meta.optString("logo", ""));
        episode.setLogo(firstNonBlank(metaLogo, episode.getLogo()));
        episode.setPlot(firstNonBlank(
                meta.optString("plot", ""),
                meta.optString("description", ""),
                meta.optString("overview", ""),
                episode.getPlot()
        ));
        episode.setReleaseDate(firstNonBlank(episode.getReleaseDate(), meta.optString(KEY_RELEASE_DATE, "")));
        episode.setRating(firstNonBlank(episode.getRating(), meta.optString(KEY_RATING, "")));
    }

    private record EpisodeMetaIndex(
            Map<String, JSONObject> bySeasonEpisode,
            Map<String, JSONObject> byTitle,
            Map<String, JSONObject> byLooseTitle,
            Map<String, JSONObject> byEpisodeOnly
    ) {
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
            } catch (Exception _) {
                // Ignore transient IMDb lookup failures and retry on the next attempt.
            }
            if (attempt < attempts) {
                try {
                    Thread.sleep(300L * attempt);
                } catch (InterruptedException _) {
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
                seasonInfo.optString(KEY_RELEASE_DATE, ""),
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
