package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ChannelService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.Platform;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import javafx.beans.Observable;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.SortedList;
import javafx.geometry.Pos;
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

import static com.uiptv.model.Account.AccountAction.series;
import static com.uiptv.player.MediaPlayerFactory.getPlayer;
import static com.uiptv.util.AccountType.STALKER_PORTAL;
import static com.uiptv.util.AccountType.XTREME_API;
import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.widget.UIptvAlert.showErrorAlert;

public class ChannelListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    private final String categoryId;
    private final SearchableTableView table = new SearchableTableView();
    private final TableColumn<ChannelItem, String> channelName = new TableColumn<>("Channels");
    private final List<Channel> channelList;
    private ObservableList<ChannelItem> channelItems;

    public ChannelListUI(List<Channel> channelList, Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI, String categoryId) {
        this.categoryId = categoryId;
        this.channelList = channelList;
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        this.account = account;
        this.categoryTitle = categoryTitle;
        initWidgets();
        refresh();
    }

    private void refresh() {
        List<ChannelItem> catList = new ArrayList<>();
        channelList.forEach(i -> {
            Bookmark b = new Bookmark(account.getAccountName(), categoryTitle, i.getChannelId(), i.getName(), i.getCmd(), account.getServerPortalUrl(), categoryId);
            boolean isBookmarked = BookmarkService.getInstance().isChannelBookmarked(b);
            catList.add(new ChannelItem(new SimpleStringProperty(i.getName()), new SimpleStringProperty(i.getChannelId()), new SimpleStringProperty(i.getCmd()), isBookmarked));
        });
        channelItems.setAll(catList);
        table.addTextFilter();
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

        channelName.setCellFactory(column -> new TableCell<>() {
            private final HBox graphic = new HBox(5);
            private final SVGPath bookmarkIcon = new SVGPath();
            private final Label nameLabel = new Label();
            private final Pane spacer = new Pane();

            {
                bookmarkIcon.setContent("M3 0 V14 L8 10 L13 14 V0 H3 Z");
                bookmarkIcon.setFill(Color.BLACK); // Changed to black
                HBox.setHgrow(spacer, Priority.ALWAYS);
                graphic.setAlignment(Pos.CENTER_LEFT);
                graphic.getChildren().addAll(nameLabel, spacer, bookmarkIcon);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (item == null || empty) {
                    setGraphic(null);
                } else {
                    ChannelItem channelItem = (ChannelItem) getTableRow().getItem();
                    if (channelItem != null) {
                        nameLabel.setText(item);
                        nameLabel.setStyle(""); // Ensure no special styling is applied
                        bookmarkIcon.setVisible(channelItem.isBookmarked());
                        setGraphic(graphic);
                    } else {
                        setGraphic(null);
                    }
                }
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
            if (!(account.getAction() == series && account.getType() == XTREME_API)) {
                addRightClickContextMenu(row);
            }

            return row;
        });
    }

    private void PlayOrShowSeries(ChannelItem item) {
        if (item == null) return;
        if (account.getAction() == series && account.getType() == XTREME_API) {
            if (this.getChildren().size() > 1) {
                this.getChildren().remove(1);
            }
            this.getChildren().add(new EpisodesListUI(XtremeParser.parseEpisodes(item.getChannelId(), account), account, item.getChannelName(), bookmarkChannelListUI));
        } else if (account.getAction() == series && account.getType() == STALKER_PORTAL) {
            if (isBlank(item.getCmd())) {
                if (this.getChildren().size() > 1) {
                    this.getChildren().remove(1);
                }
                this.getChildren().clear();
                getChildren().addAll(new VBox(5, table.getSearchTextField(), table), new ChannelListUI(ChannelService.getInstance().getSeries(categoryId, item.getChannelId(), account), account, item.getChannelName(), bookmarkChannelListUI, categoryId));
            } else {
                play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath(), false);
            }
        } else {
            play(item, ConfigurationService.getInstance().read().getDefaultPlayerPath(), false);
        }
    }

    private void addRightClickContextMenu(TableRow<ChannelItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);
        MenuItem editItem = new MenuItem("Toggle Bookmark");
        editItem.setOnAction(actionEvent -> {
            rowMenu.hide();
            ChannelItem item = row.getItem();
            BookmarkService.getInstance().toggleBookmark(new Bookmark(account.getAccountName(), categoryTitle, item.getChannelId(), item.getChannelName(), item.getCmd(), account.getServerPortalUrl(), categoryId));
            item.setBookmarked(!item.isBookmarked());
            bookmarkChannelListUI.refresh();
        });

        MenuItem playerEmbeddedItem = new MenuItem("Embedded Player");
        playerEmbeddedItem.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), "embedded", false);
        });
        MenuItem player1Item = new MenuItem("Player 1");
        player1Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath1(), false);
        });
        MenuItem player2Item = new MenuItem("Player 2");
        player2Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath2(), false);
        });
        MenuItem player3Item = new MenuItem("Player 3");
        player3Item.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath3(), false);
        });

        MenuItem reconnectAndPlayItem = new MenuItem("Reconnect & Play");
        reconnectAndPlayItem.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath(), true);
        });
        rowMenu.getItems().addAll(editItem, playerEmbeddedItem, player1Item, player2Item, player3Item, reconnectAndPlayItem);

        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void play(ChannelItem item, String playerPath, boolean runBookmark) {
        try {
            PlayerResponse response;
            if (runBookmark) {
                Bookmark bookmark = new Bookmark(account.getAccountName(), categoryTitle, item.getChannelId(), item.getChannelName(), item.getCmd(), account.getServerPortalUrl(), categoryId);
                response = PlayerService.getInstance().runBookmark(account, bookmark);
            } else {
                Channel channel = channelList.stream()
                        .filter(c -> c.getChannelId().equals(item.getChannelId()))
                        .findFirst()
                        .orElse(null);

                if (channel == null) {
                    channel = new Channel();
                    channel.setChannelId(item.getChannelId());
                    channel.setName(item.getChannelName());
                    channel.setCmd(item.getCmd());
                }

                response = PlayerService.getInstance().get(account, channel, item.getChannelId());
            }

            String evaluatedStreamUrl = response.getUrl();

            boolean useEmbeddedPlayerConfig = ConfigurationService.getInstance().read().isEmbeddedPlayer();
            boolean playerPathIsEmbedded = (playerPath != null && playerPath.toLowerCase().contains("embedded"));

            if (playerPathIsEmbedded) {
                if (useEmbeddedPlayerConfig) {
                    getPlayer().play(response);
                } else {
                    showErrorAlert("Embedded player is not enabled in settings. Please enable it or choose an external player.");
                }
            } else {
                if (isBlank(playerPath) && useEmbeddedPlayerConfig) {
                    getPlayer().play(response);
                } else if (isBlank(playerPath) && !useEmbeddedPlayerConfig) {
                    showErrorAlert("No default player configured and embedded player is not enabled. Please configure a player in settings.");
                } else {
                    Platform.executeCommand(playerPath, evaluatedStreamUrl);
                }
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class ChannelItem {

        private final SimpleStringProperty channelName;
        private final SimpleStringProperty channelId;
        private final SimpleStringProperty cmd;
        private final SimpleBooleanProperty bookmarked;

        public ChannelItem(SimpleStringProperty channelName, SimpleStringProperty channelId, SimpleStringProperty cmd, boolean isBookmarked) {
            this.channelName = channelName;
            this.channelId = channelId;
            this.cmd = cmd;
            this.bookmarked = new SimpleBooleanProperty(isBookmarked);
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
    }
}
