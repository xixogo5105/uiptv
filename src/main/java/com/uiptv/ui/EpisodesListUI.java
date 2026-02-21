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
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import javafx.application.Platform;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class EpisodesListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    private final EpisodeList channelList;
    SearchableTableView table = new SearchableTableView();
    TableColumn<EpisodeItem, String> channelName = new TableColumn<>("Episodes");
    private final AtomicBoolean itemsLoaded = new AtomicBoolean(false);

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
            this.channelList.episodes.addAll(newChannelList.episodes);
            this.channelList.seasonInfo = newChannelList.seasonInfo;
            
            List<EpisodeItem> catList = new ArrayList<>();
            newChannelList.episodes.forEach(i -> {
                Bookmark b = new Bookmark(account.getAccountName(), categoryTitle, i.getId(), i.getTitle(), i.getCmd(), account.getServerPortalUrl(), null);
                b.setAccountAction(account.getAction());
                boolean isBookmarked = BookmarkService.getInstance().isChannelBookmarked(b);
                String logo = i.getInfo() != null ? i.getInfo().getMovieImage() : "";
                String tmdbId = i.getInfo() != null ? i.getInfo().getTmdbId() : "";
                catList.add(new EpisodeItem(
                        new SimpleStringProperty(i.getTitle()),
                        new SimpleStringProperty(i.getId()),
                        new SimpleStringProperty(i.getCmd()),
                        isBookmarked,
                        new SimpleStringProperty(logo),
                        new SimpleStringProperty(tmdbId),
                        i
                ));
            });
            
            runLater(() -> {
                table.setItems(FXCollections.observableArrayList(catList));
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
        setSpacing(5);
        table.setEditable(true);
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
                    EpisodeItem episodeItem = getTableRow().getItem();
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
        getChildren().addAll(new AutoGrowVBox(5, table.getSearchTextField(), table));
        addChannelClickHandler();
        table.addTextFilter();
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
        boolean useEmbeddedPlayerConfig = ConfigurationService.getInstance().read().isEmbeddedPlayer();
        boolean playerPathIsEmbedded = (playerPath != null && playerPath.toLowerCase().contains("embedded"));
        boolean shouldResolveForEmbedded = useEmbeddedPlayerConfig && (playerPathIsEmbedded || isBlank(playerPath));

        Channel channel = new Channel();
        channel.setChannelId(item.getEpisodeId());
        channel.setName(item.getEpisodeName());
        channel.setCmd(item.getCmd());

        if (shouldResolveForEmbedded) {
            PlayerResponse loadingPreview = new PlayerResponse(null);
            loadingPreview.setFromChannel(channel, account);
            runLater(() -> {
                getPlayer().stopForReload();
                getPlayer().showLoading(loadingPreview);
            });
        }

        getScene().setCursor(Cursor.WAIT);
        new Thread(() -> {
            try {
                PlayerResponse response;
                response = PlayerService.getInstance().get(account, channel, shouldResolveForEmbedded);

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
        private final Episode episode;

        public EpisodeItem(SimpleStringProperty episodeName, SimpleStringProperty episodeId, SimpleStringProperty cmd, boolean isBookmarked, SimpleStringProperty logo, SimpleStringProperty tmdbId, Episode episode) {
            this.episodeName = episodeName;
            this.episodeId = episodeId;
            this.cmd = cmd;
            this.bookmarked = new SimpleBooleanProperty(isBookmarked);
            this.logo = logo;
            this.tmdbId = tmdbId;
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

        public Episode getEpisode() {
            return episode;
        }
    }
}
