package com.uiptv.ui;

import com.uiptv.model.*;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
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
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.util.Callback;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static javafx.application.Platform.runLater;

public class ChannelListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    private final String categoryId;
    private final SearchableTableView<ChannelItem> table = new SearchableTableView<>();
    private final TableColumn<ChannelItem, String> channelName = new TableColumn<>("Channels");
    private final List<Channel> channelList;
    private ObservableList<ChannelItem> channelItems;
    private final AtomicBoolean itemsLoaded = new AtomicBoolean(false);
    private volatile Thread currentLoadingThread;
    private AtomicBoolean currentRequestCancelled;

    public ChannelListUI(List<Channel> channelList, Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI, String categoryId) {
        this(account, categoryTitle, bookmarkChannelListUI, categoryId);
        addItems(channelList);
    }

    public ChannelListUI(Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI, String categoryId) {
        this.categoryId = categoryId;
        this.channelList = new ArrayList<>();
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        this.account = account;
        this.categoryTitle = categoryTitle;
        ImageCacheManager.clearCache("channel");
        initWidgets();
        table.setPlaceholder(new Label("Loading channels for '" + categoryTitle + "'..."));
    }

    public void addItems(List<Channel> newChannels) {
        if (newChannels != null && !newChannels.isEmpty()) {
            itemsLoaded.set(true);
            channelList.addAll(newChannels);
            List<ChannelItem> newItems = new ArrayList<>();
            newChannels.forEach(i -> {
                Bookmark b = new Bookmark(account.getAccountName(), categoryTitle, i.getChannelId(), i.getName(), i.getCmd(), account.getServerPortalUrl(), categoryId);
                b.setAccountAction(account.getAction());
                boolean isBookmarked = BookmarkService.getInstance().isChannelBookmarked(b);
                newItems.add(new ChannelItem(new SimpleStringProperty(i.getName()), new SimpleStringProperty(i.getChannelId()), new SimpleStringProperty(i.getCmd()), isBookmarked, new SimpleStringProperty(i.getLogo()), i));
            });
            
            runLater(() -> {
                channelItems.addAll(newItems);
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
        channelName.setText(categoryTitle);
        channelName.setVisible(true);
        channelName.setCellValueFactory(cellData -> cellData.getValue().channelNameProperty());

        channelItems = FXCollections.observableArrayList(ChannelItem.extractor());
        SortedList<ChannelItem> sortedList = new SortedList<>(channelItems);

        // Bind the sorted list's comparator to a custom one that wraps the table's default comparator
        sortedList.comparatorProperty().bind(Bindings.createObjectBinding(() -> {
            Comparator<ChannelItem> tableComparator = table.getComparator();
            Comparator<ChannelItem> bookmarkComparator = Comparator.comparing(ChannelItem::isBookmarked).reversed();
            return tableComparator == null ? bookmarkComparator : bookmarkComparator.thenComparing(tableComparator);
        }, table.comparatorProperty()));

        table.setItems(sortedList);
        table.addTextFilter();

        channelName.setCellFactory(column -> new TableCell<>() {

            private final HBox graphic = new HBox(10);
            private final Label nameLabel = new Label();
            private final Pane spacer = new Pane();
            private final SVGPath bookmarkIcon = new SVGPath();
            private final AsyncImageView imageView = new AsyncImageView();

            {
                bookmarkIcon.setContent("M3 0 V14 L8 10 L13 14 V0 H3 Z");
                bookmarkIcon.setFill(Color.BLACK);

                HBox.setHgrow(spacer, Priority.ALWAYS);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(imageView, nameLabel, spacer, bookmarkIcon);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }

                ChannelItem channelItem = getIndex() >= 0 && getIndex() < getTableView().getItems().size()
                        ? getTableView().getItems().get(getIndex())
                        : null;

                if (channelItem == null) {
                    setGraphic(null);
                    return;
                }

                nameLabel.setText(item);
                bookmarkIcon.setVisible(channelItem.isBookmarked());
                imageView.loadImage(channelItem.getLogo(), "channel");
                setGraphic(graphic);
            }
        });

        channelName.setSortType(TableColumn.SortType.ASCENDING);

        getChildren().addAll(new AutoGrowVBox(5, table.getSearchTextField(), table));
        addChannelClickHandler();
    }

    private void addChannelClickHandler() {
        table.setOnKeyReleased(event -> {
            if (event.getCode() == KeyCode.ENTER) {
                PlayOrShowSeries((ChannelItem) table.getFocusModel().getFocusedItem());
            }
        });
        table.setRowFactory(tv -> {
            TableRow<ChannelItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    PlayOrShowSeries(row.getItem());
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
    }

    private void PlayOrShowSeries(ChannelItem item) {
        if (item == null) return;

        if (currentRequestCancelled != null) {
            currentRequestCancelled.set(true);
        }

        if (currentLoadingThread != null && currentLoadingThread.isAlive()) {
            getScene().setCursor(Cursor.WAIT);
            currentLoadingThread.interrupt();
            try {
                currentLoadingThread.join(2000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        currentRequestCancelled = new AtomicBoolean(false);
        AtomicBoolean isCancelled = currentRequestCancelled;

        if (account.getAction() == series) {
            getScene().setCursor(Cursor.WAIT);
            currentLoadingThread = new Thread(() -> {
                try {
                    if (account.getType() == XTREME_API) {
                        final EpisodesListUI[] episodesListUIHolder = new EpisodesListUI[1];
                        CountDownLatch latch = new CountDownLatch(1);
                        
                        runLater(() -> {
                            if (this.getChildren().size() > 1) {
                                this.getChildren().remove(1);
                            }
                            EpisodesListUI ui = new EpisodesListUI(account, item.getChannelName(), bookmarkChannelListUI);
                            episodesListUIHolder[0] = ui;
                            this.getChildren().add(ui);
                            latch.countDown();
                        });
                        
                        latch.await();
                        if (Thread.currentThread().isInterrupted() || isCancelled.get()) return;
                        try {
                            EpisodeList episodes = XtremeParser.parseEpisodes(item.getChannelId(), account);
                            episodesListUIHolder[0].setItems(episodes);
                        } finally {
                            episodesListUIHolder[0].setLoadingComplete();
                        }
                    } else if (account.getType() == STALKER_PORTAL) {
                        if (isBlank(item.getCmd())) {
                            // Immediately show the ChannelListUI in loading state
                            ChannelListUI channelListUI = new ChannelListUI(account, item.getChannelName(), bookmarkChannelListUI, categoryId);
                            runLater(() -> {
                                this.getChildren().clear();
                                getChildren().addAll(new VBox(5, table.getSearchTextField(), table), channelListUI);
                            });
                            
                            // Fetch series channels
                            try {
                                ChannelService.getInstance().getSeries(categoryId, item.getChannelId(), account, channelListUI::addItems, isCancelled::get);
                            } finally {
                                channelListUI.setLoadingComplete();
                            }
                        } else {
                            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                        }
                    }
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                } catch (Exception e) {
                    runLater(() -> showErrorAlert("Error loading series: " + e.getMessage()));
                } finally {
                    runLater(() -> getScene().setCursor(Cursor.DEFAULT));
                }
            });
            currentLoadingThread.start();
        } else {
            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath());
        }
    }

    private void addRightClickContextMenu(TableRow<ChannelItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        Menu bookmarkMenu = new Menu("Bookmark");
        rowMenu.getItems().add(bookmarkMenu);

        rowMenu.setOnShowing(event -> {
            bookmarkMenu.getItems().clear();
            ChannelItem item = row.getItem();
            if (item == null) return;

            new Thread(() -> {
                Bookmark existingBookmark = BookmarkService.getInstance().getBookmark(new Bookmark(account.getAccountName(), categoryTitle, item.getChannelId(), item.getChannelName(), item.getCmd(), account.getServerPortalUrl(), categoryId));
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
            Bindings.when(
                row.emptyProperty().or(
                    Bindings.createBooleanBinding(() ->
                        account.getAction() == series && (row.getItem() == null || isBlank(row.getItem().getCmd())),
                        row.itemProperty()
                    )
                )
            )
            .then((ContextMenu) null)
            .otherwise(rowMenu)
        );
    }

    private void saveBookmark(ChannelItem item, String bookmarkCategoryId) {
        new Thread(() -> {
            Bookmark bookmark = new Bookmark(account.getAccountName(), categoryTitle, item.getChannelId(), item.getChannelName(), item.getCmd(), account.getServerPortalUrl(), categoryId);
            bookmark.setAccountAction(account.getAction());
            bookmark.setCategoryId(bookmarkCategoryId);
            
            Category cat = new Category();
            cat.setCategoryId(categoryId);
            cat.setTitle(categoryTitle);
            bookmark.setCategoryJson(cat.toJson());

            if (item.getChannel() != null) {
                if (account.getAction() == vod) {
                    bookmark.setVodJson(item.getChannel().toJson());
                } else {
                    bookmark.setChannelJson(item.getChannel().toJson());
                }
            }
            BookmarkService.getInstance().save(bookmark);
            Platform.runLater(() -> {
                item.setBookmarked(true);
                bookmarkChannelListUI.forceReload();
                table.refresh();
            });
        }).start();
    }

    private void play(ChannelItem item, String playerPath) {
        table.setPlayingItem(item);
        getScene().setCursor(Cursor.WAIT);
        new Thread(() -> {
            try {
                PlayerResponse response;
                Channel channel = channelList.stream()
                        .filter(c -> c.getChannelId().equals(item.getChannelId()))
                        .findFirst()
                        .orElse(null);

                if (channel == null) {
                    channel = new Channel();
                    channel.setChannelId(item.getChannelId());
                    channel.setName(item.getChannelName());
                    channel.setCmd(item.getCmd());
                    channel.setLogo(item.getLogo());
                }

                response = PlayerService.getInstance().get(account, channel, item.getChannelId());
                response.setFromChannel(channel, account); // Ensure response has channel and account

                final String evaluatedStreamUrl = response.getUrl();
                final PlayerResponse finalResponse = response;

                runLater(() -> {
                    boolean useEmbeddedPlayerConfig = ConfigurationService.getInstance().read().isEmbeddedPlayer();
                    boolean playerPathIsEmbedded = (playerPath != null && playerPath.toLowerCase().contains("embedded"));

                    if (playerPathIsEmbedded) {
                        if (useEmbeddedPlayerConfig) {
                            getPlayer().stopForReload();
                            getPlayer().play(finalResponse);
                        } else {
                            showErrorAlert("Embedded player is not enabled in settings. Please enable it or choose an external player.");
                        }
                    } else {
                        if (isBlank(playerPath) && useEmbeddedPlayerConfig) {
                            getPlayer().stopForReload();
                            getPlayer().play(finalResponse);
                        } else if (isBlank(playerPath) && !useEmbeddedPlayerConfig) {
                            showErrorAlert("No default player configured and embedded player is not enabled. Please configure a player in settings.");
                        } else {
                            com.uiptv.util.Platform.executeCommand(playerPath, evaluatedStreamUrl);
                        }
                    }
                });
            } catch (Exception e) {
                runLater(() -> showErrorAlert("Error playing channel: " + e.getMessage()));
            } finally {
                runLater(() -> getScene().setCursor(Cursor.DEFAULT));
            }
        }).start();
    }

    public static class ChannelItem {

        private final SimpleStringProperty channelName;
        private final SimpleStringProperty channelId;
        private final SimpleStringProperty cmd;
        private final SimpleBooleanProperty bookmarked;
        private final SimpleStringProperty logo;
        private final Channel channel;

        public ChannelItem(SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, boolean isBookmarked, SimpleStringProperty logo, Channel channel) {
            this.channelName = channelName;
            this.channelId = channelId;
            this.cmd = cmd;
            this.bookmarked = new SimpleBooleanProperty(isBookmarked);
            this.logo = logo;
            this.channel = channel;
        }

        public static Callback<ChannelItem, Observable[]> extractor() {
            return item -> new Observable[]{item.bookmarkedProperty()};
        }

        public String getChannelName() {
            return channelName.get();
        }

        public void setChannelName(String channelName) {
            this.channelName.set(channelName);
        }

        public String getChannelId() {
            return channelId.get();
        }

        public void setChannelId(String channelId) {
            this.channelId.set(channelId);
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

        public String getLogo() {
            return logo.get();
        }

        public void setLogo(String logo) {
            this.logo.set(logo);
        }

        public SimpleStringProperty logoProperty() {
            return logo;
        }

        public SimpleBooleanProperty bookmarkedProperty() {
            return bookmarked;
        }

        public SimpleStringProperty cmdProperty() {
            return cmd;
        }

        public SimpleStringProperty channelNameProperty() {
            return channelName;
        }

        public SimpleStringProperty channelIdProperty() {
            return channelId;
        }

        public Channel getChannel() {
            return channel;
        }
    }
}
