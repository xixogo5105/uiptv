package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.shared.EpisodeList;
import com.uiptv.ui.util.ImageCacheManager;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.widget.LoadingStateView;
import com.uiptv.widget.PillBar;
import com.uiptv.widget.PlayMenuButton;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Side;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.Node;
import javafx.scene.layout.FlowPane;
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
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static com.uiptv.util.StringUtils.isBlank;

public class ThumbnailEpisodesListUI extends BaseEpisodesListUI {
    private static final String KEY_CARD_LABELS = "cardLabels";
    private static final String EPISODE_CACHE = "episode";
    private static final String KEY_COVER = "cover";
    private static final String KEY_RATING = "rating";
    private static final String KEY_RELEASE_DATE = "releaseDate";
    private static final String KEY_TITLE = "title";
    private static final String STYLE_CLASS_BUTTON = "button";
    private static final double SERIES_EPISODE_LOADING_INDICATOR_SIZE = 24;
    private static final double SERIES_EPISODE_LOADING_PANEL_HEIGHT = 220;
    private final PillBar<String> seasonPillBar = new PillBar<>(I18n::formatTabNumberLabel, season -> season);
    private final VBox cardsContainer = new VBox(8);
    private final ScrollPane cardsScroll = new ScrollPane(cardsContainer);
    private final StackPane cardsFrame = new StackPane();
    private final VBox header = new VBox(12);
    private final ImageView seriesPosterNode = new ImageView();
    private final StackPane seriesPosterWrap = new StackPane(seriesPosterNode);
    private final VBox headerDetails = new VBox(4);
    private final VBox titleRow = new VBox(4);
    private final Label titleNode = new Label();
    private final Label ratingNode = new Label();
    private final Label genreNode = new Label();
    private final Label releaseNode = new Label();
    private final Label plotNode = new Label();
    private final MenuButton bingeWatchButton = new MenuButton();
    private final Button reloadEpisodesButton = new Button();
    private final LoadingStateView imdbLoadingNode = new LoadingStateView(I18n.tr("autoLoadingIMDbDetails"), 14);
    private final LoadingStateView episodeLoadingNode = createSeriesEpisodeLoadingNode(I18n.tr("autoLoadingIMDbDetails"));
    private final FlowPane watchingNowDetailLayout = new FlowPane(14, 14);
    private final VBox watchingNowEpisodesPanel = new VBox(12);
    private final Label episodesTitleNode = new Label(I18n.tr("autoEpisodes"));
    private final Label episodesSeriesTitleNode = new Label();
    private HBox imdbBadgeNode;
    private volatile boolean imdbLoading = false;
    private volatile boolean imdbLoaded = false;
    private final AtomicLong lifecycleGeneration = new AtomicLong();
    private VBox selectedEpisodeCard;
    private boolean watchingNowDetailStylingApplied = false;
    private VBox bodyContainer;
    private final Map<EpisodeItem, VBox> renderedCardsByItem = new HashMap<>();
    private Consumer<JSONObject> seasonInfoListener;
    private boolean internalBingeWatchControlVisible = true;
    private boolean internalSeriesTitleVisible = true;
    private boolean internalReloadControlVisible = true;
    private List<String> seasonOptions = List.of();
    private String pendingTargetSeason;
    private String pendingTargetEpisodeId;
    private String pendingTargetEpisodeNumber;
    private String pendingTargetEpisodeName;
    private boolean episodeLoadingVisible = false;

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
        initHeader();
        seasonPillBar.getStyleClass().add("watching-now-season-pill-bar");
        seasonPillBar.setMaxWidth(Double.MAX_VALUE);
        seasonPillBar.selectedItemProperty().addListener((_, _, _) -> {
            applySeasonFilter();
            updateBingeWatchButton();
        });

        cardsContainer.setPadding(new Insets(5));
        cardsContainer.setFillWidth(true);
        cardsContainer.setMaxWidth(Double.MAX_VALUE);

        cardsScroll.setFitToWidth(true);
        cardsScroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        cardsScroll.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        cardsScroll.setMinSize(0, 0);
        cardsScroll.setMaxWidth(Double.MAX_VALUE);
        cardsScroll.setMaxHeight(Double.MAX_VALUE);
        cardsScroll.getStyleClass().add("transparent-scroll-pane");

        cardsFrame.getStyleClass().add("episode-loading-frame");
        cardsFrame.setMinSize(0, 0);
        cardsFrame.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        setEpisodeLoadingOverlayVisible(false, null);
        cardsFrame.getChildren().setAll(cardsScroll);

        bodyContainer = new VBox(6, header, seasonPillBar, cardsFrame);
        bodyContainer.setMinSize(0, 0);
        bodyContainer.setMaxWidth(Double.MAX_VALUE);
        bodyContainer.setMaxHeight(Double.MAX_VALUE);
        bodyContainer.setPadding(new Insets(0, 4, 0, 4));
        HBox.setHgrow(bodyContainer, Priority.ALWAYS);
        header.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(header, Priority.NEVER);
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
        VBox.setVgrow(cardsFrame, Priority.ALWAYS);
        VBox.setVgrow(seasonPillBar, Priority.NEVER);
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
        setEpisodeLoadingOverlayVisible(false, null);
        cardsContainer.getChildren().setAll(new LoadingStateView(text));
    }

    @Override
    protected void setEmptyState(String message, boolean empty) {
        header.setManaged(!empty);
        header.setVisible(!empty);
        seasonPillBar.setManaged(!empty);
        seasonPillBar.setVisible(!empty);
        cardsFrame.setManaged(!empty);
        cardsFrame.setVisible(!empty);
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
            selectSeasonPill(requestedSeason);
        }

        EpisodeItem match = findBestEpisodeMatch(season, episodeId, episodeNumber, episodeName);
        if (match == null) {
            return;
        }

        String targetSeason = normalizeNumber(match.getSeason());
        if (!isBlank(targetSeason)) {
            selectSeasonPill(targetSeason);
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

    private void setEpisodeLoadingOverlayVisible(boolean visible, String message) {
        episodeLoadingVisible = visible;
        if (message != null) {
            episodeLoadingNode.setMessage(message);
        }
        syncEpisodeLoadingNode();
    }

    private void syncEpisodeLoadingNode() {
        episodeLoadingNode.setVisible(episodeLoadingVisible);
        episodeLoadingNode.setManaged(episodeLoadingVisible);
        if (episodeLoadingVisible && !cardsContainer.getChildren().contains(episodeLoadingNode)) {
            cardsContainer.getChildren().add(0, episodeLoadingNode);
        } else if (!episodeLoadingVisible) {
            cardsContainer.getChildren().remove(episodeLoadingNode);
        }
    }

    private static LoadingStateView createSeriesEpisodeLoadingNode(String message) {
        LoadingStateView loadingNode = new LoadingStateView(message, SERIES_EPISODE_LOADING_INDICATOR_SIZE);
        loadingNode.getStyleClass().add("series-inline-loading-state");
        loadingNode.setAlignment(Pos.CENTER);
        loadingNode.setMinHeight(SERIES_EPISODE_LOADING_PANEL_HEIGHT);
        loadingNode.setPrefHeight(SERIES_EPISODE_LOADING_PANEL_HEIGHT);
        loadingNode.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
        return loadingNode;
    }

    private void initHeader() {
        header.setAlignment(Pos.TOP_LEFT);
        header.setFillWidth(true);
        header.getStyleClass().add("uiptv-outline-pane");
        header.setPadding(new Insets(5));

        seriesPosterNode.setFitWidth(170);
        seriesPosterNode.setFitHeight(250);
        seriesPosterNode.setPreserveRatio(true);
        seriesPosterNode.setSmooth(true);
        seriesPosterWrap.setAlignment(Pos.CENTER);
        seriesPosterWrap.setMaxWidth(Double.MAX_VALUE);

        titleNode.getStyleClass().add("strong-label");
        titleNode.setWrapText(true);
        titleNode.setTextOverrun(OverrunStyle.CLIP);
        titleNode.setMaxWidth(Double.MAX_VALUE);
        titleNode.setMinWidth(0);
        titleNode.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(titleNode, Priority.ALWAYS);
        plotNode.setWrapText(true);
        plotNode.setMaxWidth(Double.MAX_VALUE);
        plotNode.setMinWidth(0);
        plotNode.setMinHeight(Region.USE_PREF_SIZE);
        titleRow.setAlignment(Pos.TOP_LEFT);
        titleRow.setMaxWidth(Double.MAX_VALUE);
        headerDetails.setMaxWidth(Double.MAX_VALUE);
        headerDetails.setFillWidth(true);
        headerDetails.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(plotNode, Priority.ALWAYS);
        plotNode.prefWidthProperty().bind(headerDetails.widthProperty().subtract(6));
        titleNode.prefWidthProperty().bind(headerDetails.widthProperty().subtract(6));
        bingeWatchButton.setFocusTraversable(true);
        bingeWatchButton.getStyleClass().setAll(STYLE_CLASS_BUTTON);
        bingeWatchButton.getStyleClass().add("binge-watch-menu-button");
        bingeWatchButton.setOnShowing(event -> {
            ContextMenu menu = bingeWatchButton.getContextMenu();
            if (menu != null && !menu.getStyleClass().contains("binge-watch-context-menu")) {
                menu.getStyleClass().add("binge-watch-context-menu");
            }
        });
        configureReloadEpisodesButton();
        titleRow.getChildren().setAll(titleNode, bingeWatchButton, reloadEpisodesButton);
        refreshTitleRowVisibility();
        headerDetails.getChildren().setAll(titleRow);
        HBox.setHgrow(headerDetails, Priority.ALWAYS);

        header.getChildren().setAll(seriesPosterWrap, headerDetails);
    }

    @Override
    protected void setInternalBingeWatchControlVisible(boolean visible) {
        internalBingeWatchControlVisible = visible;
        refreshTitleRowVisibility();
    }

    @Override
    protected void setInternalSeriesTitleVisible(boolean visible) {
        internalSeriesTitleVisible = visible;
        refreshTitleRowVisibility();
    }

    @Override
    protected void setInternalReloadControlVisible(boolean visible) {
        internalReloadControlVisible = visible;
        updateReloadEpisodesButton();
        refreshTitleRowVisibility();
    }

    @Override
    protected void onReloadControlChanged() {
        updateReloadEpisodesButton();
        setEpisodeLoadingOverlayVisible(
                isReloadFromServerInProgress() && !allEpisodeItems.isEmpty(),
                I18n.tr("autoLoadingEpisodesFor", categoryTitle)
        );
    }

    @Override
    protected void beforeApplyingPortalReload(EpisodeList refreshed) {
        lifecycleGeneration.incrementAndGet();
        imdbLoaded = false;
        imdbLoading = false;
        imdbBadgeNode = null;
    }

    private void refreshTitleRowVisibility() {
        titleNode.setManaged(internalSeriesTitleVisible);
        titleNode.setVisible(internalSeriesTitleVisible);
        bingeWatchButton.setManaged(internalBingeWatchControlVisible);
        bingeWatchButton.setVisible(internalBingeWatchControlVisible);
        reloadEpisodesButton.setManaged(internalReloadControlVisible);
        reloadEpisodesButton.setVisible(internalReloadControlVisible);
        boolean titleRowVisible = internalSeriesTitleVisible || internalBingeWatchControlVisible || internalReloadControlVisible;
        titleRow.setManaged(titleRowVisible);
        titleRow.setVisible(titleRowVisible);
    }

    public void applyWatchingNowDetailStyling() {
        if (watchingNowDetailStylingApplied) {
            return;
        }
        watchingNowDetailStylingApplied = true;
        header.getStyleClass().remove("uiptv-outline-pane");
        header.getStyleClass().remove("uiptv-card");
        header.getStyleClass().add("watching-now-series-info-panel");
        seriesPosterWrap.getStyleClass().add("watching-now-series-poster-wrap");
        titleNode.getStyleClass().add("watching-now-series-detail-title");
        for (Label label : List.of(ratingNode, genreNode, releaseNode, plotNode)) {
            label.getStyleClass().add("watching-now-series-meta-line");
        }
        configureWatchingNowEpisodesPanel();
        if (bodyContainer != null) {
            bodyContainer.setSpacing(0);
            bodyContainer.setPadding(Insets.EMPTY);
            bodyContainer.getChildren().setAll(watchingNowDetailLayout);
            VBox.setVgrow(watchingNowDetailLayout, Priority.ALWAYS);
        }
        header.setMinWidth(0);
        header.setPrefWidth(330);
        header.setMaxWidth(380);
        seriesPosterNode.setFitWidth(260);
        seriesPosterNode.setFitHeight(390);
        cardsContainer.setPadding(Insets.EMPTY);
        seasonPillBar.getStyleClass().add("watching-now-season-pill-bar");
        applyWatchingNowLayoutSizing(watchingNowDetailLayout.getWidth());
        watchingNowDetailLayout.widthProperty().addListener((_, _, width) ->
                applyWatchingNowLayoutSizing(width.doubleValue()));
    }

    private void configureWatchingNowEpisodesPanel() {
        watchingNowDetailLayout.getStyleClass().add("watching-now-series-detail-layout");
        watchingNowDetailLayout.setAlignment(Pos.TOP_LEFT);
        watchingNowDetailLayout.setMinWidth(0);
        watchingNowDetailLayout.setMaxWidth(Double.MAX_VALUE);
        watchingNowDetailLayout.setMinHeight(0);
        watchingNowDetailLayout.setMaxHeight(Double.MAX_VALUE);

        watchingNowEpisodesPanel.getStyleClass().add("watching-now-episodes-panel");
        watchingNowEpisodesPanel.setMinWidth(0);
        watchingNowEpisodesPanel.setMaxWidth(Double.MAX_VALUE);
        watchingNowEpisodesPanel.setMinHeight(0);
        watchingNowEpisodesPanel.setMaxHeight(Double.MAX_VALUE);

        episodesTitleNode.getStyleClass().add("watching-now-episodes-title");
        episodesSeriesTitleNode.getStyleClass().add("watching-now-episodes-series-title");
        episodesSeriesTitleNode.setWrapText(true);
        episodesSeriesTitleNode.setMinWidth(0);
        episodesSeriesTitleNode.setMaxWidth(Double.MAX_VALUE);

        VBox titleText = new VBox(2, episodesTitleNode, episodesSeriesTitleNode);
        titleText.setMinWidth(0);
        titleText.setMaxWidth(Double.MAX_VALUE);

        cardsContainer.getStyleClass().add("watching-now-episode-card-list");
        detachFromParent(header);
        detachFromParent(seasonPillBar);
        detachFromParent(cardsFrame);
        watchingNowEpisodesPanel.getChildren().setAll(titleText, seasonPillBar, cardsFrame);
        VBox.setVgrow(cardsFrame, Priority.ALWAYS);
        watchingNowDetailLayout.getChildren().setAll(header, watchingNowEpisodesPanel);
    }

    private void detachFromParent(Node node) {
        if (node != null && node.getParent() instanceof Pane pane) {
            pane.getChildren().remove(node);
        }
    }

    private void applyWatchingNowLayoutSizing(double availableWidth) {
        double width = Math.max(240, availableWidth <= 0 ? 720 : availableWidth);
        boolean stacked = width < 840;
        if (stacked) {
            double panelWidth = Math.max(220, width - 4);
            header.setPrefWidth(panelWidth);
            header.setMaxWidth(Double.MAX_VALUE);
            watchingNowEpisodesPanel.setPrefWidth(panelWidth);
            return;
        }
        double detailsWidth = Math.min(340, Math.max(280, width * 0.32));
        header.setPrefWidth(detailsWidth);
        header.setMaxWidth(380);
        watchingNowEpisodesPanel.setPrefWidth(Math.max(420, width - detailsWidth - 18));
    }

    private void applySeriesHeader() {
        String title = firstNonBlank(seasonInfo.optString("name", ""), categoryTitle);
        titleNode.setText(title);
        episodesSeriesTitleNode.setText(title);

        headerDetails.getChildren().removeAll(ratingNode, genreNode, releaseNode, plotNode, imdbLoadingNode);
        if (imdbBadgeNode != null) {
            headerDetails.getChildren().remove(imdbBadgeNode);
        }
        titleRow.getChildren().setAll(titleNode, bingeWatchButton, reloadEpisodesButton);
        refreshTitleRowVisibility();
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
                            com.uiptv.util.AppLog.addWarningLog(ThumbnailEpisodesListUI.class, "EpisodesListUI series poster failed: " + finalCover);
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

        seasonOptions = seasons;
        seasonPillBar.setItems(seasonOptions);
        String defaultSeason = seasons.stream()
                .filter("1"::equals)
                .findFirst()
                .orElse(seasons.getFirst());
        if (!isBlank(current)) {
            defaultSeason = seasons.stream()
                    .filter(current::equals)
                    .findFirst()
                    .orElse(defaultSeason);
        }
        seasonPillBar.setSelectedItem(defaultSeason);
    }

    private void applySeasonFilter() {
        if (allEpisodeItems.isEmpty()) {
            setEmptyState(I18n.tr("autoNoEpisodesFound"), true);
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
            syncEpisodeLoadingNode();
            updateBingeWatchButton();
            return;
        }
        for (EpisodeItem item : filtered) {
            VBox card = createEpisodeCard(item);
            renderedCardsByItem.put(item, card);
            cardsContainer.getChildren().add(card);
        }
        syncEpisodeLoadingNode();
        updateBingeWatchButton();
    }

    private String selectedSeason() {
        return seasonPillBar.getSelectedItem() == null ? "" : seasonPillBar.getSelectedItem();
    }

    private void selectSeasonPill(String season) {
        if (isBlank(season)) {
            return;
        }
        String normalized = normalizeNumber(season);
        String match = seasonOptions.stream()
                .filter(item -> normalized.equals(normalizeNumber(item)))
                .findFirst()
                .orElse(null);
        if (match != null) {
            seasonPillBar.setSelectedItem(match);
        }
    }

    @Override
    protected String selectedBingeWatchSeason() {
        return firstNonBlank(selectedSeason(), "1");
    }

    private VBox createEpisodeCard(EpisodeItem row) {
        VBox root = new VBox(8);
        root.setPadding(new Insets(10));
        root.getStyleClass().add("uiptv-card");
        if (watchingNowDetailStylingApplied) {
            root.getStyleClass().add("watching-now-episode-card");
        }
        root.setFocusTraversable(true);
        root.setMinWidth(0);
        root.setMaxWidth(Double.MAX_VALUE);

        HBox top = new HBox(10);
        top.setAlignment(Pos.TOP_LEFT);

        ImageView poster = createEpisodePoster(row);
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
        Button playButton = new PlayMenuButton(I18n.tr("autoPlay2"));
        playButton.getStyleClass().add("episode-play-button");
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
            if (watchingNowDetailStylingApplied) {
                release.getStyleClass().add("watching-now-episode-meta-label");
            }
            text.getChildren().add(release);
            cardLabels.add(release);
        }
        if (!isBlank(row.getRating())) {
            Label rating = new Label(I18n.tr("autoRatingPrefix", row.getRating()));
            if (watchingNowDetailStylingApplied) {
                rating.getStyleClass().add("watching-now-episode-meta-label");
            }
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
            if (watchingNowDetailStylingApplied) {
                plot.getStyleClass().add("watching-now-episode-plot");
            }
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

    private ImageView createEpisodePoster(EpisodeItem row) {
        ImageView poster = new ImageView();
        poster.setFitWidth(96);
        poster.setFitHeight(136);
        poster.setPreserveRatio(true);
        poster.setSmooth(true);
        refreshEpisodePoster(poster, row);
        row.logoProperty().addListener((obs, oldValue, newValue) -> refreshEpisodePoster(poster, row));
        return poster;
    }

    private void refreshEpisodePoster(ImageView poster, EpisodeItem row) {
        if (poster == null) {
            return;
        }
        String posterUrl = resolveEpisodePosterUrl(row, "");
        if (isBlank(posterUrl)) {
            poster.setImage(null);
            return;
        }
        loadEpisodePoster(poster, row, posterUrl);
    }

    private void loadEpisodePoster(ImageView poster, EpisodeItem row, String posterUrl) {
        ImageCacheManager.loadImageAsync(posterUrl, EPISODE_CACHE).thenAccept(image -> Platform.runLater(() -> {
                String currentPrimaryUrl = resolveEpisodePosterUrl(row, "");
                if (!posterUrl.equals(currentPrimaryUrl) && !posterUrl.equals(resolveEpisodePosterUrl(row, currentPrimaryUrl))) {
                    return;
                }
                if (image != null) {
                    poster.setImage(image);
                    poster.setViewport(null);
                    poster.setPreserveRatio(true);
                    poster.setFitWidth(96);
                    poster.setFitHeight(136);
                    return;
                }

                String fallbackUrl = resolveEpisodePosterUrl(row, posterUrl);
                if (!isBlank(fallbackUrl) && !fallbackUrl.equals(posterUrl)) {
                    loadEpisodePoster(poster, row, fallbackUrl);
                    return;
                }
                poster.setImage(null);
            }));
    }

    private String resolveEpisodePosterUrl(EpisodeItem row, String excludedUrl) {
        String excluded = sanitizePosterUrl(excludedUrl);
        String primary = row == null ? "" : sanitizePosterUrl(row.getLogo());
        if (!isBlank(primary) && !primary.equals(excluded)) {
            return primary;
        }
        String cover = sanitizePosterUrl(normalizeImageUrl(seasonInfo.optString(KEY_COVER, "")));
        if (!isBlank(cover) && !cover.equals(excluded)) {
            return cover;
        }
        return allEpisodeItems.stream()
                .map(EpisodeItem::getLogo)
                .map(this::sanitizePosterUrl)
                .filter(url -> !isBlank(url) && !url.equals(excluded))
                .findFirst()
                .orElse("");
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
        UiI18n.preparePopupControl(rowMenu, target);
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
        for (PlaybackUIService.PlayerOption option : PlaybackUIService.getConfiguredPlayerOptions()) {
            MenuItem playerItem = new MenuItem(option.label());
            playerItem.getStyleClass().add("binge-watch-menu-item");
            playerItem.setOnAction(event -> bingeWatchSeason(season, option.playerPath()));
            bingeWatchButton.getItems().add(playerItem);
        }
        bingeWatchButton.setDisable(allEpisodeItems.isEmpty());
        notifyBingeWatchControlChanged();
    }

    private void configureReloadEpisodesButton() {
        reloadEpisodesButton.setFocusTraversable(true);
        reloadEpisodesButton.getStyleClass().setAll(STYLE_CLASS_BUTTON);
        reloadEpisodesButton.setMinWidth(Region.USE_PREF_SIZE);
        reloadEpisodesButton.setMaxWidth(Region.USE_PREF_SIZE);
        reloadEpisodesButton.setOnAction(event -> reloadFromServer());
        updateReloadEpisodesButton();
    }

    private void updateReloadEpisodesButton() {
        reloadEpisodesButton.setText(reloadFromServerButtonText());
        reloadEpisodesButton.setDisable(reloadFromServerButtonDisabled());
        reloadEpisodesButton.setManaged(internalReloadControlVisible);
        reloadEpisodesButton.setVisible(internalReloadControlVisible);
    }

    private void triggerImdbLazyLoad() {
        if (imdbLoaded || imdbLoading) {
            return;
        }
        imdbLoading = true;
        Platform.runLater(() -> {
            applySeriesHeader();
            setEpisodeLoadingOverlayVisible(!allEpisodeItems.isEmpty(), I18n.tr("autoLoadingIMDbDetails"));
        });
        long generation = lifecycleGeneration.get();
        boolean submitted = WatchingNowMetadataExecutor.submit(() -> {
            try {
                if (!isImdbTaskCurrent(generation)) {
                    return;
                }
                JSONObject imdb = findImdbWithRetry(
                        firstNonBlank(seasonInfo.optString("name", ""), categoryTitle),
                        seasonInfo.optString("tmdb", ""),
                        3
                );
                if (imdb != null && isImdbTaskCurrent(generation)) {
                    applyImdbMetadata(imdb);
                }
            } finally {
                Platform.runLater(() -> completeImdbLazyLoad(generation));
            }
        });
        if (!submitted) {
            imdbLoading = false;
            Platform.runLater(() -> setEpisodeLoadingOverlayVisible(false, null));
        }
    }

    private void applyImdbMetadata(JSONObject imdb) {
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

    private void completeImdbLazyLoad(long generation) {
        if (!isImdbTaskCurrent(generation)) {
            return;
        }
        imdbLoaded = true;
        imdbLoading = false;
        applySeriesHeader();
        applySeasonFilter();
        setEpisodeLoadingOverlayVisible(false, null);
        navigateToPendingEpisodeTarget();
    }

    private void navigateToPendingEpisodeTarget() {
        if (pendingTargetSeason == null) {
            return;
        }
        navigateToEpisodeTarget(pendingTargetSeason, pendingTargetEpisodeId, pendingTargetEpisodeNumber, pendingTargetEpisodeName);
        pendingTargetSeason = null;
        pendingTargetEpisodeId = null;
        pendingTargetEpisodeNumber = null;
        pendingTargetEpisodeName = null;
    }

    private boolean isImdbTaskCurrent(long generation) {
        return lifecycleGeneration.get() == generation;
    }

    @Override
    protected void releaseTransientState() {
        lifecycleGeneration.incrementAndGet();
        super.releaseTransientState();
        renderedCardsByItem.clear();
        cardsContainer.getChildren().clear();
        seasonOptions = List.of();
        seasonPillBar.setItems(seasonOptions);
        selectedEpisodeCard = null;
        imdbLoaded = false;
        imdbLoading = false;
        imdbBadgeNode = null;
        setEpisodeLoadingOverlayVisible(false, null);
        seriesPosterNode.setImage(null);
        pendingTargetSeason = null;
        pendingTargetEpisodeId = null;
        pendingTargetEpisodeNumber = null;
        pendingTargetEpisodeName = null;
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
        String sourceTitle = episodeMetadataTitle(episode);
        String normalizedSeason = normalizeNumber(episode.getSeason());
        String normalizedEpisode = normalizeNumber(firstNonBlank(episode.getEpisodeNumber(), inferEpisodeNumberFromTitle(sourceTitle)));
        JSONObject meta = index.bySeasonEpisode().get(normalizedSeason + ":" + normalizedEpisode);
        if (meta == null) {
            meta = index.byTitle().get(normalizeTitle(cleanEpisodeTitle(sourceTitle)));
        }
        if (meta == null) {
            meta = index.byLooseTitle().get(normalizeTitle(extractLooseEpisodeTitle(sourceTitle)));
        }
        if (meta == null && !isBlank(normalizedEpisode)) {
            meta = index.byEpisodeOnly().get(normalizedEpisode);
        }
        return meta;
    }

    private String episodeMetadataTitle(EpisodeItem episode) {
        if (episode == null) {
            return "";
        }
        String rawTitle = episode.getEpisode() != null ? safe(episode.getEpisode().getTitle()) : "";
        return firstNonBlank(rawTitle, episode.getEpisodeName());
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
