package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Channel;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.BookmarkChangeListener;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.SeriesWatchStateChangeListener;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
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
import java.util.stream.Collectors;

import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.StringUtils.isBlank;
import static javafx.application.Platform.runLater;

public class EpisodesListUI extends HBox {
    private static final Pattern SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)|\\b(\\d{1,2})x\\d{1,3}\\b");
    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b|\\b\\d{1,2}x(\\d{1,3})\\b");
    private static final Pattern MONTH_DATE_PATTERN = Pattern.compile("(?i)\\b(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec)[a-z]*\\s+\\d{1,2},\\s+\\d{4}\\b");
    private static final Pattern SLASH_DATE_PATTERN = Pattern.compile("\\b\\d{1,2}/\\d{1,2}/\\d{2,4}\\b");
    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("\\b\\d{4}-\\d{2}-\\d{2}\\b");
    private static final DateTimeFormatter UI_DATE_FORMATTER = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.ENGLISH);

    private final Account account;
    private final String categoryTitle;
    private final String seriesId;
    private final String seriesCategoryId;
    private final EpisodeList channelList;

    private final AtomicBoolean itemsLoaded = new AtomicBoolean(false);
    private final TabPane seasonTabPane = new TabPane();
    private final VBox cardsContainer = new VBox(8);
    private final ScrollPane cardsScroll = new ScrollPane(cardsContainer);
    private final Label emptyStateLabel = new Label();
    private final StackPane contentStack = new StackPane();
    private final ObservableList<EpisodeItem> allEpisodeItems = FXCollections.observableArrayList(EpisodeItem.extractor());

    private boolean bookmarkListenerRegistered = false;
    private final BookmarkChangeListener bookmarkChangeListener = (revision, updatedEpochMs) -> refreshBookmarkStatesAsync();
    private boolean watchStateListenerRegistered = false;
    private final SeriesWatchStateChangeListener watchStateChangeListener;

    private JSONObject seasonInfo = new JSONObject();
    private final HBox header = new HBox(12);
    private final ImageView seriesPosterNode = new ImageView();
    private final VBox headerDetails = new VBox(4);
    private final Label titleNode = new Label();
    private final Label ratingNode = new Label();
    private final Label genreNode = new Label();
    private final Label releaseNode = new Label();
    private final Label plotNode = new Label();
    private final HBox imdbLoadingNode = new HBox(6);
    private HBox imdbBadgeNode;
    private volatile boolean imdbLoading = false;
    private volatile boolean imdbLoaded = false;

    public EpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        this(account, categoryTitle, seriesId, seriesCategoryId);
        setItems(channelList);
    }

    public EpisodesListUI(Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        this.channelList = new EpisodeList();
        this.account = account;
        this.categoryTitle = categoryTitle;
        this.seriesId = isBlank(seriesId) ? "" : seriesId.trim();
        this.seriesCategoryId = isBlank(seriesCategoryId) ? "" : seriesCategoryId.trim();
        this.watchStateChangeListener = (accountId, changedSeriesId) -> {
            if (this.account != null
                    && this.account.getDbId() != null
                    && ((this.account.getDbId().equals(accountId) && this.seriesId.equals(changedSeriesId))
                    || (isBlank(accountId) && isBlank(changedSeriesId)))) {
                refreshWatchedStatesAsync();
            }
        };
        ImageCacheManager.clearCache("episode");
        initWidgets();
        registerBookmarkListener();
        registerWatchStateListener();
        showPlaceholder("Loading episodes for '" + categoryTitle + "'...");
    }

    public void setItems(EpisodeList newChannelList) {
        if (newChannelList == null) return;

        if (newChannelList.episodes != null && !newChannelList.episodes.isEmpty()) {
            itemsLoaded.set(true);
            this.channelList.episodes.clear();
            this.channelList.episodes.addAll(newChannelList.episodes);
            this.channelList.seasonInfo = newChannelList.seasonInfo;
            if (newChannelList.seasonInfo == null) {
                this.seasonInfo = new JSONObject();
            } else {
                try {
                    this.seasonInfo = new JSONObject(newChannelList.seasonInfo.toJson());
                } catch (Exception ignored) {
                    this.seasonInfo = new JSONObject();
                }
            }
            if (isBlank(this.seasonInfo.optString("name", ""))) {
                this.seasonInfo.put("name", categoryTitle);
            }

            Set<String> bookmarkKeys = loadBookmarkKeysForAccount();
            SeriesWatchState watchedState = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), seriesCategoryId, seriesId);

            List<EpisodeItem> catList = new ArrayList<>();
            newChannelList.episodes.forEach(i -> {
                boolean isBookmarked = bookmarkKeys.contains(bookmarkKey(categoryTitle, i.getId(), i.getTitle()));
                String logo = i.getInfo() != null ? normalizeImageUrl(i.getInfo().getMovieImage()) : "";
                String tmdbId = i.getInfo() != null ? i.getInfo().getTmdbId() : "";
                String season = inferSeason(i);
                String episodeNo = inferEpisodeNumber(i);
                boolean isWatched = SeriesWatchStateService.getInstance().isMatchingEpisode(
                        watchedState, i.getId(), season, episodeNo, i.getTitle());
                String cleanTitle = cleanEpisodeTitle(i.getTitle());
                String displayTitle = isBlank(episodeNo) ? cleanTitle : "E" + episodeNo + "  " + cleanTitle;
                String plot = i.getInfo() != null ? safe(i.getInfo().getPlot()) : "";
                String releaseDate = i.getInfo() != null ? safe(i.getInfo().getReleaseDate()) : "";
                String rating = i.getInfo() != null ? safe(i.getInfo().getRating()) : "";

                catList.add(new EpisodeItem(
                        new SimpleStringProperty(displayTitle),
                        new SimpleStringProperty(i.getId()),
                        new SimpleStringProperty(i.getCmd()),
                        isBookmarked,
                        isWatched,
                        new SimpleStringProperty(logo),
                        new SimpleStringProperty(tmdbId),
                        new SimpleStringProperty(season),
                        new SimpleStringProperty(episodeNo),
                        new SimpleStringProperty(plot),
                        new SimpleStringProperty(releaseDate),
                        new SimpleStringProperty(rating),
                        i
                ));
            });

            runLater(() -> {
                allEpisodeItems.setAll(catList);
                applySeriesHeader();
                refreshSeasonTabs();
                applySeasonFilter();
                triggerImdbLazyLoad();
            });
        }
    }

    public void setLoadingComplete() {
        runLater(() -> {
            if (!itemsLoaded.get()) {
                setEmptyState("Nothing found for '" + categoryTitle + "'", true);
            }
        });
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(6);
        setMinWidth(0);
        setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        setMaxWidth(Double.MAX_VALUE);

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
        HBox.setHgrow(body, Priority.ALWAYS);
        header.setMaxHeight(Double.MAX_VALUE);
        VBox.setVgrow(header, Priority.SOMETIMES);
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
        VBox.setVgrow(seasonTabPane, Priority.NEVER);

        emptyStateLabel.setWrapText(true);
        emptyStateLabel.setMaxWidth(Double.MAX_VALUE);
        emptyStateLabel.setStyle("-fx-font-size: 1.1em; -fx-text-alignment: center;");
        emptyStateLabel.setManaged(false);
        emptyStateLabel.setVisible(false);
        StackPane.setAlignment(emptyStateLabel, Pos.CENTER);
        contentStack.getChildren().addAll(body, emptyStateLabel);
        HBox.setHgrow(contentStack, Priority.ALWAYS);

        getChildren().add(contentStack);
    }

    private void initHeader() {
        header.setAlignment(Pos.TOP_LEFT);
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

        headerDetails.getChildren().setAll(titleNode);
        HBox.setHgrow(headerDetails, Priority.ALWAYS);
        header.getChildren().setAll(seriesPosterNode, headerDetails);
    }

    private void applySeriesHeader() {
        String title = firstNonBlank(seasonInfo.optString("name", ""), categoryTitle);
        titleNode.setText(title);

        headerDetails.getChildren().removeAll(ratingNode, genreNode, releaseNode, plotNode, imdbLoadingNode);
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

    private void showPlaceholder(String text) {
        cardsContainer.getChildren().setAll(new Label(text));
    }

    private void setEmptyState(String message, boolean empty) {
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

    private void registerBookmarkListener() {
        if (bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = true;
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                unregisterBookmarkListener();
            } else if (!bookmarkListenerRegistered) {
                BookmarkService.getInstance().addChangeListener(bookmarkChangeListener);
                bookmarkListenerRegistered = true;
                refreshBookmarkStatesAsync();
            }
        });
    }

    private void registerWatchStateListener() {
        if (watchStateListenerRegistered) {
            return;
        }
        SeriesWatchStateService.getInstance().addChangeListener(watchStateChangeListener);
        watchStateListenerRegistered = true;
        sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (newScene == null) {
                unregisterWatchStateListener();
            } else if (!watchStateListenerRegistered) {
                SeriesWatchStateService.getInstance().addChangeListener(watchStateChangeListener);
                watchStateListenerRegistered = true;
                refreshWatchedStatesAsync();
            }
        });
    }

    private void unregisterBookmarkListener() {
        if (!bookmarkListenerRegistered) {
            return;
        }
        BookmarkService.getInstance().removeChangeListener(bookmarkChangeListener);
        bookmarkListenerRegistered = false;
    }

    private void unregisterWatchStateListener() {
        if (!watchStateListenerRegistered) {
            return;
        }
        SeriesWatchStateService.getInstance().removeChangeListener(watchStateChangeListener);
        watchStateListenerRegistered = false;
    }

    private void refreshBookmarkStatesAsync() {
        if (allEpisodeItems.isEmpty()) {
            return;
        }
        new Thread(() -> {
            List<Bookmark> bookmarks = BookmarkService.getInstance().read();
            Set<String> bookmarkKeys = bookmarks.stream()
                    .filter(b -> account.getAccountName().equals(b.getAccountName()))
                    .map(b -> bookmarkIdentityKey(b.getChannelId(), b.getChannelName()))
                    .collect(Collectors.toSet());
            runLater(() -> {
                for (EpisodeItem item : allEpisodeItems) {
                    boolean isBookmarked = bookmarkKeys.contains(bookmarkIdentityKey(item.getEpisodeId(), item.getEpisodeName()));
                    item.setBookmarked(isBookmarked);
                }
                applySeasonFilter();
            });
        }, "episodes-bookmark-refresh").start();
    }

    private void refreshWatchedStatesAsync() {
        if (allEpisodeItems.isEmpty() || account == null || isBlank(account.getDbId()) || isBlank(seriesId)) {
            return;
        }
        new Thread(() -> {
            SeriesWatchState state = SeriesWatchStateService.getInstance().getSeriesLastWatched(account.getDbId(), seriesCategoryId, seriesId);
            runLater(() -> {
                for (EpisodeItem item : allEpisodeItems) {
                    item.setWatched(SeriesWatchStateService.getInstance().isMatchingEpisode(
                            state,
                            item.getEpisodeId(),
                            item.getSeason(),
                            item.getEpisodeNumber(),
                            item.getEpisodeName()
                    ));
                }
                applySeasonFilter();
            });
        }, "episodes-watch-refresh").start();
    }

    private String bookmarkIdentityKey(String channelId, String channelName) {
        return (channelId == null ? "" : channelId.trim()) + "|" + (channelName == null ? "" : channelName.trim().toLowerCase());
    }

    private String bookmarkKey(String categoryTitleValue, String channelId, String channelName) {
        String category = categoryTitleValue == null ? "" : categoryTitleValue.trim().toLowerCase();
        String id = channelId == null ? "" : channelId.trim();
        String name = channelName == null ? "" : channelName.trim().toLowerCase();
        return category + "|" + id + "|" + name;
    }

    private Set<String> loadBookmarkKeysForAccount() {
        return BookmarkService.getInstance().read().stream()
                .filter(b -> account.getAccountName().equals(b.getAccountName()))
                .map(b -> bookmarkKey(b.getCategoryTitle(), b.getChannelId(), b.getChannelName()))
                .collect(Collectors.toSet());
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

    private HBox createEpisodeCard(EpisodeItem row) {
        HBox root = new HBox(10);
        root.setAlignment(Pos.CENTER_LEFT);
        root.setPadding(new Insets(6));
        root.setStyle("-fx-border-color: -fx-box-border; -fx-border-radius: 6; -fx-background-radius: 6;");

        ImageView poster = SeriesCardUiSupport.createFitPoster(row.getLogo(), 96, 136, "episode");
        VBox text = new VBox(2);
        text.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(text, Priority.ALWAYS);

        Label title = new Label(row.getEpisodeName());
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinHeight(Region.USE_PREF_SIZE);
        if (row.isWatched()) {
            title.setStyle("-fx-font-weight: bold;");
        }
        text.getChildren().add(title);
        if (!isBlank(row.getReleaseDate())) text.getChildren().add(new Label("Release: " + shortDateOnly(row.getReleaseDate())));
        if (!isBlank(row.getRating())) text.getChildren().add(new Label("Rating: " + row.getRating()));
        if (!isBlank(row.getPlot())) {
            Label plot = new Label(row.getPlot());
            plot.setWrapText(true);
            plot.setMaxWidth(Double.MAX_VALUE);
            plot.setMinHeight(Region.USE_PREF_SIZE);
            text.getChildren().add(plot);
        }

        Region spacer = new Region();
        spacer.setPrefWidth(8);
        spacer.setMinWidth(8);
        VBox badges = new VBox(4);
        if (row.isWatched()) {
            Label watched = new Label("WATCHING");
            watched.getStyleClass().add("drm-badge");
            watched.setMinWidth(Region.USE_PREF_SIZE);
            watched.setMaxWidth(Double.MAX_VALUE);
            badges.getChildren().add(watched);
        }

        root.getChildren().addAll(poster, text, spacer, badges);
        root.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                play(row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            }
        });
        addRightClickContextMenu(row, root);
        return root;
    }

    private void addRightClickContextMenu(EpisodeItem item, Pane target) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        Menu lastWatchedMenu = new Menu("Last Watched");
        rowMenu.getItems().add(lastWatchedMenu);

        Menu bookmarkMenu = new Menu("Bookmark");
        rowMenu.getItems().add(bookmarkMenu);

        rowMenu.setOnShowing(event -> {
            lastWatchedMenu.getItems().clear();
            bookmarkMenu.getItems().clear();
            if (item == null) return;

            MenuItem markWatched = new MenuItem("Mark as Watched");
            markWatched.setOnAction(e -> markEpisodeAsWatched(item));
            lastWatchedMenu.getItems().add(markWatched);

            MenuItem clearWatched = new MenuItem("Clear Watched Marker");
            clearWatched.setDisable(!item.isWatched());
            clearWatched.setOnAction(e -> clearWatchedMarker());
            lastWatchedMenu.getItems().add(clearWatched);

            new Thread(() -> {
                Bookmark existingBookmark = BookmarkService.getInstance().getBookmark(new Bookmark(account.getAccountName(), categoryTitle, item.getEpisodeId(), item.getEpisodeName(), item.getCmd(), account.getServerPortalUrl(), null));
                List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();

                Platform.runLater(() -> {
                    MenuItem allItem = new MenuItem("All");
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
                        MenuItem unbookmarkItem = new MenuItem("Remove Bookmark");
                        unbookmarkItem.setStyle("-fx-text-fill: red;");
                        unbookmarkItem.setOnAction(e -> {
                            new Thread(() -> {
                                BookmarkService.getInstance().remove(existingBookmark.getDbId());
                                Platform.runLater(() -> {
                                    item.setBookmarked(false);
                                    refreshBookmarkStatesAsync();
                                });
                            }, "episodes-unbookmark").start();
                        });
                        bookmarkMenu.getItems().add(unbookmarkItem);
                    }
                });
            }, "episodes-bookmark-menu").start();
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
    }

    private void saveBookmark(EpisodeItem item, String bookmarkCategoryId) {
        new Thread(() -> {
            Bookmark bookmark = new Bookmark(account.getAccountName(), categoryTitle, item.getEpisodeId(), item.getEpisodeName(), item.getCmd(), account.getServerPortalUrl(), null);
            bookmark.setAccountAction(account.getAction());
            bookmark.setCategoryId(bookmarkCategoryId);
            if (item.getEpisode() != null) {
                bookmark.setSeriesJson(item.getEpisode().toJson());
            }
            BookmarkService.getInstance().save(bookmark);
            Platform.runLater(() -> {
                item.setBookmarked(true);
                refreshBookmarkStatesAsync();
            });
        }, "episodes-bookmark-save").start();
    }

    private void markEpisodeAsWatched(EpisodeItem item) {
        if (item == null || isBlank(seriesId) || account == null) {
            return;
        }
        new Thread(() -> {
            account.setAction(Account.AccountAction.series);
            SeriesWatchStateService.getInstance().markSeriesEpisodeManual(
                    account,
                    seriesCategoryId,
                    seriesId,
                    item.getEpisodeId(),
                    item.getEpisodeName(),
                    item.getSeason(),
                    item.getEpisodeNumber()
            );
            refreshWatchedStatesAsync();
        }, "episodes-mark-watched").start();
    }

    private void clearWatchedMarker() {
        if (account == null || isBlank(account.getDbId()) || isBlank(seriesId)) {
            return;
        }
        new Thread(() -> {
            SeriesWatchStateService.getInstance().clearSeriesLastWatched(account.getDbId(), seriesCategoryId, seriesId);
            refreshWatchedStatesAsync();
        }, "episodes-clear-watched").start();
    }

    private void play(EpisodeItem item, String playerPath) {
        if (item == null) {
            return;
        }
        if (!item.isWatched()) {
            markEpisodeAsWatched(item);
        }
        Channel channel = new Channel();
        channel.setChannelId(item.getEpisodeId());
        channel.setName(item.getEpisodeName());
        channel.setCmd(item.getCmd());
        channel.setSeason(item.getSeason());
        channel.setEpisodeNum(item.getEpisodeNumber());
        channel.setLogo(item.getLogo());
        account.setAction(Account.AccountAction.series);
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(account, channel, playerPath)
                .series(item.getEpisodeId(), seriesId)
                .channelId(item.getEpisodeId())
                .categoryId(seriesCategoryId)
                .errorPrefix("Error playing episode: "));
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
                // Keep per-episode failures isolated so other rows can still render metadata.
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
        Matcher matcher = Pattern.compile("\\b(19|20)\\d{2}\\b").matcher(value);
        return matcher.find() ? matcher.group() : "";
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

    private int parseNumberOrDefault(String value) {
        try {
            if (isBlank(value)) return 1;
            return Integer.parseInt(value);
        } catch (Exception ignored) {
            return 1;
        }
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

    public static class EpisodeItem {
        private final SimpleStringProperty episodeName;
        private final SimpleStringProperty episodeId;
        private final SimpleStringProperty cmd;
        private final SimpleBooleanProperty bookmarked;
        private final SimpleBooleanProperty watched;
        private final SimpleStringProperty logo;
        private final SimpleStringProperty tmdbId;
        private final SimpleStringProperty season;
        private final SimpleStringProperty episodeNumber;
        private final SimpleStringProperty plot;
        private final SimpleStringProperty releaseDate;
        private final SimpleStringProperty rating;
        private final Episode episode;

        public EpisodeItem(SimpleStringProperty episodeName,
                          SimpleStringProperty episodeId,
                          SimpleStringProperty cmd,
                          boolean isBookmarked,
                          boolean isWatched,
                          SimpleStringProperty logo,
                          SimpleStringProperty tmdbId,
                          SimpleStringProperty season,
                          SimpleStringProperty episodeNumber,
                          SimpleStringProperty plot,
                          SimpleStringProperty releaseDate,
                          SimpleStringProperty rating,
                          Episode episode) {
            this.episodeName = episodeName;
            this.episodeId = episodeId;
            this.cmd = cmd;
            this.bookmarked = new SimpleBooleanProperty(isBookmarked);
            this.watched = new SimpleBooleanProperty(isWatched);
            this.logo = logo;
            this.tmdbId = tmdbId;
            this.season = season;
            this.episodeNumber = episodeNumber;
            this.plot = plot;
            this.releaseDate = releaseDate;
            this.rating = rating;
            this.episode = episode;
        }

        public static javafx.util.Callback<EpisodeItem, Observable[]> extractor() {
            return item -> new Observable[]{item.bookmarkedProperty(), item.watchedProperty(), item.logoProperty(), item.plotProperty(), item.releaseDateProperty(), item.ratingProperty()};
        }

        public String getEpisodeName() {
            return episodeName.get();
        }

        public String getEpisodeId() {
            return episodeId.get();
        }

        public String getCmd() {
            return cmd.get();
        }

        public boolean isBookmarked() {
            return bookmarked.get();
        }

        public void setBookmarked(boolean bookmarked) {
            this.bookmarked.set(bookmarked);
        }

        public boolean isWatched() {
            return watched.get();
        }

        public void setWatched(boolean watched) {
            this.watched.set(watched);
        }

        public String getLogo() {
            return logo.get();
        }

        public void setLogo(String logo) {
            this.logo.set(logo == null ? "" : logo);
        }

        public String getTmdbId() {
            return tmdbId.get();
        }

        public String getSeason() {
            return season.get();
        }

        public String getEpisodeNumber() {
            return episodeNumber.get();
        }

        public String getPlot() {
            return plot.get();
        }

        public void setPlot(String value) {
            this.plot.set(value == null ? "" : value);
        }

        public String getReleaseDate() {
            return releaseDate.get();
        }

        public void setReleaseDate(String value) {
            this.releaseDate.set(value == null ? "" : value);
        }

        public String getRating() {
            return rating.get();
        }

        public void setRating(String value) {
            this.rating.set(value == null ? "" : value);
        }

        public SimpleBooleanProperty bookmarkedProperty() {
            return bookmarked;
        }

        public SimpleBooleanProperty watchedProperty() {
            return watched;
        }

        public SimpleStringProperty logoProperty() {
            return logo;
        }

        public SimpleStringProperty plotProperty() {
            return plot;
        }

        public SimpleStringProperty releaseDateProperty() {
            return releaseDate;
        }

        public SimpleStringProperty ratingProperty() {
            return rating;
        }

        public Episode getEpisode() {
            return episode;
        }
    }
}
