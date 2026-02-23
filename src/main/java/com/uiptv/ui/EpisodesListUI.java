package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.shared.Episode;
import com.uiptv.shared.EpisodeList;
import com.uiptv.util.ImageCacheManager;
import com.uiptv.widget.AsyncImageView;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.net.URI;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.ui.RootApplication.GUIDED_MAX_WIDTH_PIXELS;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class EpisodesListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    private final EpisodeList channelList;
    TableView<EpisodeItem> table = new TableView<>();
    TableColumn<EpisodeItem, String> channelName = new TableColumn<>("Episodes");
    private final AtomicBoolean itemsLoaded = new AtomicBoolean(false);
    private final TabPane seasonTabPane = new TabPane();
    private final ObservableList<EpisodeItem> allEpisodeItems = FXCollections.observableArrayList(EpisodeItem.extractor());
    private static final Pattern SXXEYY_PATTERN = Pattern.compile("(?i)\\bS(\\d{1,2})E(\\d{1,3})\\b");
    private static final Pattern SEASON_PATTERN = Pattern.compile("(?i)\\bseason\\s*(\\d+)\\b|\\bS(\\d{1,2})(?=\\b|E\\d+)");
    private static final Pattern EPISODE_PATTERN = Pattern.compile("(?i)\\bepisode\\s*(\\d+)\\b|\\bE(\\d{1,3})\\b");

    public EpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI) {
        this(account, categoryTitle, bookmarkChannelListUI);
        setItems(channelList);
    }

    public EpisodesListUI(Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI) {
        this.channelList = new EpisodeList();
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        this.account = account;
        this.categoryTitle = categoryTitle;
        ImageCacheManager.clearCache("episode");
        initWidgets();
        table.setPlaceholder(new Label("Loading episodes for '" + categoryTitle + "'..."));
    }

    public void setItems(EpisodeList newChannelList) {
        if (newChannelList == null) return;
        
        if (newChannelList.episodes != null && !newChannelList.episodes.isEmpty()) {
            itemsLoaded.set(true);
            this.channelList.episodes.clear();
            this.channelList.episodes.addAll(newChannelList.episodes);
            this.channelList.seasonInfo = newChannelList.seasonInfo;
            
            List<EpisodeItem> catList = new ArrayList<>();
            newChannelList.episodes.forEach(i -> {
                Bookmark b = new Bookmark(account.getAccountName(), categoryTitle, i.getId(), i.getTitle(), i.getCmd(), account.getServerPortalUrl(), null);
                b.setAccountAction(account.getAction());
                boolean isBookmarked = BookmarkService.getInstance().isChannelBookmarked(b);
                String logo = i.getInfo() != null ? normalizeImageUrl(i.getInfo().getMovieImage()) : "";
                String tmdbId = i.getInfo() != null ? i.getInfo().getTmdbId() : "";
                String season = inferSeason(i);
                String episodeNo = inferEpisodeNumber(i);
                String displayTitle = isBlank(episodeNo) ? "Episode" : "Episode " + episodeNo;
                catList.add(new EpisodeItem(
                        new SimpleStringProperty(displayTitle),
                        new SimpleStringProperty(i.getId()),
                        new SimpleStringProperty(i.getCmd()),
                        isBookmarked,
                        new SimpleStringProperty(logo),
                        new SimpleStringProperty(tmdbId),
                        new SimpleStringProperty(season),
                        new SimpleStringProperty(episodeNo),
                        i
                ));
            });
            
            runLater(() -> {
                allEpisodeItems.setAll(catList);
                refreshSeasonTabs();
                applySeasonFilter();
                table.setPlaceholder(null);
            });
        }
    }
    
    public void setLoadingComplete() {
        runLater(() -> {
            if (!itemsLoaded.get()) {
                table.setPlaceholder(new Label("Nothing found for '" + categoryTitle + "'"));
            }
        });
    }

    private void initWidgets() {
        setPadding(new javafx.geometry.Insets(5));
        setSpacing(5);
        setMinWidth(0);
        setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        setMaxWidth(Double.MAX_VALUE);
        table.setEditable(true);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        table.setMinWidth(0);
        table.setPrefWidth((double) GUIDED_MAX_WIDTH_PIXELS / 3);
        table.setMaxWidth(Double.MAX_VALUE);
        table.getColumns().add(channelName);
        channelName.setText("Episodes of " + categoryTitle);
        channelName.setVisible(true);
        channelName.setCellValueFactory(cellData -> cellData.getValue().episodeNameProperty());
        channelName.setCellFactory(column -> new TableCell<>() {
            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final AsyncImageView imageView = new AsyncImageView();

            {
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(imageView, nameLabel);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                    setStyle("");
                } else {
                    EpisodeItem episodeItem = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                            ? getTableView().getItems().get(getIndex())
                            : null;
                    if (episodeItem != null) {
                        nameLabel.setText(item);
                        setStyle(episodeItem.isBookmarked() ? "-fx-font-weight: bold; -fx-font-size: 125%;" : "");
                        imageView.loadImage(episodeItem.getLogo(), "episode");
                        setGraphic(graphic);
                    } else {
                        setGraphic(null);
                        setStyle("");
                    }
                }
            }
        });
        channelName.setSortType(TableColumn.SortType.ASCENDING);
        seasonTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
        seasonTabPane.setMaxWidth(Double.MAX_VALUE);
        seasonTabPane.getSelectionModel().selectedItemProperty().addListener((obs, oldTab, newTab) -> applySeasonFilter());
        VBox container = new VBox(5, seasonTabPane, table);
        container.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(container, Priority.ALWAYS);
        VBox.setVgrow(table, Priority.ALWAYS);
        getChildren().add(container);
        addChannelClickHandler();
    }

    private void refreshSeasonTabs() {
        String current = selectedSeason();
        List<String> seasons = allEpisodeItems.stream()
                .map(EpisodeItem::getSeason)
                .filter(s -> !isBlank(s))
                .distinct()
                .sorted(Comparator.comparingInt(Integer::parseInt))
                .toList();
        if (seasons.isEmpty()) {
            seasons = List.of("1");
        }

        seasonTabPane.getTabs().clear();
        for (String season : seasons) {
            Tab tab = new Tab("Season " + season);
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
            table.setItems(FXCollections.observableArrayList());
            return;
        }
        String season = selectedSeason();
        if (isBlank(season)) {
            table.setItems(FXCollections.observableArrayList(allEpisodeItems));
            return;
        }
        List<EpisodeItem> filtered = allEpisodeItems.stream()
                .filter(i -> season.equals(i.getSeason()))
                .toList();
        table.setItems(FXCollections.observableArrayList(filtered));
    }

    private String selectedSeason() {
        Tab selected = seasonTabPane.getSelectionModel().getSelectedItem();
        return selected != null ? String.valueOf(selected.getUserData()) : "";
    }

    private String inferSeason(Episode episode) {
        if (episode == null) return "1";
        String season = onlyDigits(episode.getSeason());
        if (!isBlank(season)) return season;
        String title = String.valueOf(episode.getTitle());
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find() && !isBlank(sxey.group(1))) {
            return sxey.group(1);
        }
        Matcher matcher = SEASON_PATTERN.matcher(title);
        if (matcher.find()) {
            String parsed = !isBlank(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
            if (!isBlank(parsed)) return parsed;
        }
        return "1";
    }

    private String inferEpisodeNumber(Episode episode) {
        if (episode == null) return "";
        String num = onlyDigits(episode.getEpisodeNum());
        if (!isBlank(num)) return num;
        String title = String.valueOf(episode.getTitle());
        Matcher sxey = SXXEYY_PATTERN.matcher(title);
        if (sxey.find() && !isBlank(sxey.group(2))) {
            return sxey.group(2);
        }
        Matcher matcher = EPISODE_PATTERN.matcher(title);
        if (matcher.find()) {
            String parsed = !isBlank(matcher.group(1)) ? matcher.group(1) : matcher.group(2);
            if (!isBlank(parsed)) return parsed;
        }
        return "";
    }

    private String onlyDigits(String value) {
        if (isBlank(value)) return "";
        String parsed = value.replaceAll("[^0-9]", "");
        return isBlank(parsed) ? "" : parsed;
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

    private void addChannelClickHandler() {
        table.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                play((EpisodeItem) table.getFocusModel().getFocusedItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
            }
        });
        table.setRowFactory(tv -> {
            TableRow<EpisodeItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    play(row.getItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
    }

    private void addRightClickContextMenu(TableRow<EpisodeItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        Menu bookmarkMenu = new Menu("Bookmark");
        rowMenu.getItems().add(bookmarkMenu);

        rowMenu.setOnShowing(event -> {
            bookmarkMenu.getItems().clear();
            EpisodeItem item = row.getItem();
            if (item == null) return;

            new Thread(() -> {
                Bookmark existingBookmark = BookmarkService.getInstance().getBookmark(new Bookmark(account.getAccountName(), categoryTitle, item.getEpisodeId(), item.getEpisodeName(), item.getCmd(), account.getServerPortalUrl(), null));
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
                        unbookmarkItem.setStyle("-fx-text-fill: red;");
                        unbookmarkItem.setOnAction(e -> {
                            new Thread(() -> {
                                BookmarkService.getInstance().remove(existingBookmark.getDbId());
                                Platform.runLater(() -> {
                                    item.setBookmarked(false);
                                    bookmarkChannelListUI.forceReload();
                                    table.refresh();
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
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
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
                bookmarkChannelListUI.forceReload();
                table.refresh();
            });
        }).start();
    }

    private void play(EpisodeItem item, String playerPath) {
        // Stop any existing playback immediately
        runLater(() -> getPlayer().stopForReload());

        boolean useEmbeddedPlayerConfig = ConfigurationService.getInstance().read().isEmbeddedPlayer();
        boolean playerPathIsEmbedded = (playerPath != null && playerPath.toLowerCase().contains("embedded"));

        Channel channel = new Channel();
        channel.setChannelId(item.getEpisodeId());
        channel.setName(item.getEpisodeName());
        channel.setCmd(item.getCmd());

        getScene().setCursor(Cursor.WAIT);
        new Thread(() -> {
            try {
                PlayerResponse response;
                if (account.getType() == com.uiptv.util.AccountType.STALKER_PORTAL && account.getAction() == series) {
                    response = PlayerService.getInstance().get(account, channel, item.getEpisodeId());
                } else {
                    response = PlayerService.getInstance().get(account, channel);
                }

                final String evaluatedStreamUrl = response.getUrl();
                final PlayerResponse finalResponse = response;

                runLater(() -> {
                    if (playerPathIsEmbedded) {
                        if (useEmbeddedPlayerConfig) {
                            getPlayer().play(finalResponse);
                        } else {
                            showErrorAlert("Embedded player is not enabled in settings. Please enable it or choose an external player.");
                        }
                    } else {
                        if (isBlank(playerPath) && useEmbeddedPlayerConfig) {
                            getPlayer().play(finalResponse);
                        } else if (isBlank(playerPath) && !useEmbeddedPlayerConfig) {
                            showErrorAlert("No default player configured and embedded player is not enabled. Please configure a player in settings.");
                        } else {
                            com.uiptv.util.Platform.executeCommand(playerPath, evaluatedStreamUrl);
                        }
                    }
                });
            } catch (Exception e) {
                runLater(() -> showErrorAlert("Error playing episode: " + e.getMessage()));
            } finally {
                runLater(() -> getScene().setCursor(Cursor.DEFAULT));
            }
        }).start();
    }

    public static class EpisodeItem {

        private final SimpleStringProperty episodeName;
        private final SimpleStringProperty episodeId;
        private final SimpleStringProperty cmd;
        private final SimpleBooleanProperty bookmarked;
        private final SimpleStringProperty logo;
        private final SimpleStringProperty tmdbId;
        private final SimpleStringProperty season;
        private final SimpleStringProperty episodeNumber;
        private final Episode episode;

        public EpisodeItem(SimpleStringProperty episodeName, SimpleStringProperty episodeId, SimpleStringProperty cmd, boolean isBookmarked, SimpleStringProperty logo, SimpleStringProperty tmdbId, SimpleStringProperty season, SimpleStringProperty episodeNumber, Episode episode) {
            this.episodeName = episodeName;
            this.episodeId = episodeId;
            this.cmd = cmd;
            this.bookmarked = new SimpleBooleanProperty(isBookmarked);
            this.logo = logo;
            this.tmdbId = tmdbId;
            this.season = season;
            this.episodeNumber = episodeNumber;
            this.episode = episode;
        }

        public static Callback<EpisodeItem, Observable[]> extractor() {
            return item -> new Observable[]{item.bookmarkedProperty()};
        }

        public String getEpisodeName() {
            return episodeName.get();
        }

        public void setEpisodeName(String episodeName) {
            this.episodeName.set(episodeName);
        }

        public String getEpisodeId() {
            return episodeId.get();
        }

        public void setEpisodeId(String episodeId) {
            this.episodeId.set(episodeId);
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

        public SimpleStringProperty cmdProperty() {
            return cmd;
        }

        public SimpleStringProperty episodeNameProperty() {
            return episodeName;
        }

        public SimpleStringProperty episodeIdProperty() {
            return episodeId;
        }

        public SimpleBooleanProperty bookmarkedProperty() {
            return bookmarked;
        }

        public String getLogo() {
            return logo.get();
        }

        public SimpleStringProperty logoProperty() {
            return logo;
        }

        public String getTmdbId() {
            return tmdbId.get();
        }

        public SimpleStringProperty tmdbIdProperty() {
            return tmdbId;
        }

        public String getSeason() {
            return season.get();
        }

        public SimpleStringProperty seasonProperty() {
            return season;
        }

        public String getEpisodeNumber() {
            return episodeNumber.get();
        }

        public SimpleStringProperty episodeNumberProperty() {
            return episodeNumber;
        }

        public Episode getEpisode() {
            return episode;
        }
    }
}
