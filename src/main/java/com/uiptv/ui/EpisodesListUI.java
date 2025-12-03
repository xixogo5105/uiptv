package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.model.EpisodeList;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.util.Platform;
import com.uiptv.widget.AutoGrowVBox;
import com.uiptv.widget.SearchableTableView;
import com.uiptv.widget.UIptvAlert;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.scene.control.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class EpisodesListUI extends HBox {
    private final Account account;
    private final String categoryTitle;
    private final BookmarkChannelListUI bookmarkChannelListUI;
    SearchableTableView table = new SearchableTableView();
    TableColumn<EpisodeItem, String> channelName = new TableColumn("Episodes");
    private final EpisodeList channelList;

    public EpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, BookmarkChannelListUI bookmarkChannelListUI) { // Removed MediaPlayer argument
        this.channelList = channelList;
        this.bookmarkChannelListUI = bookmarkChannelListUI;
        this.account = account;
        this.categoryTitle = categoryTitle;
        initWidgets();
        refresh();
    }

    private void refresh() {
        List<EpisodeItem> catList = new ArrayList<>();
        channelList.episodes.forEach(i -> {
            Bookmark b = new Bookmark(account.getAccountName(), categoryTitle, i.getId(), i.getTitle(), i.getCmd(), account.getServerPortalUrl());
            boolean checkBookmark = BookmarkService.getInstance().isChannelBookmarked(b);
            UIptvAlert.showMessage(b + " --- " + String.valueOf(checkBookmark));
            catList.add(new EpisodeItem(new SimpleStringProperty(checkBookmark ? "**" + i.getTitle().replace("*", "") + "**" : i.getTitle()), new SimpleStringProperty(i.getId()), new SimpleStringProperty(i.getCmd())));
        });
        table.setItems(FXCollections.observableArrayList(catList));
        table.addTextFilter();
    }

    private void initWidgets() {
        setSpacing(10);
        table.setEditable(true);
        table.getColumns().addAll(channelName);
        channelName.setText("Episodes of " + categoryTitle);
        channelName.setVisible(true);
        channelName.setCellValueFactory(cellData -> cellData.getValue().episodeNameProperty());
        channelName.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                setText(item);
                if (item != null && !empty) {
                    if (item.startsWith("**")) {
                        setStyle("-fx-font-weight: bold;-fx-font-size: 125%;");
                    } else {
                        setStyle("");
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
                play((EpisodeItem) table.getFocusModel().getFocusedItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath(), false);
            }
        });
        table.setRowFactory(tv -> {
            TableRow<EpisodeItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    play(row.getItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath(), false);
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

        MenuItem playerEmbeddedItem = new MenuItem("Embedded Player");
        playerEmbeddedItem.setOnAction(event -> {
            rowMenu.hide();
            play(row.getItem(), null, false);
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
        rowMenu.getItems().addAll(playerEmbeddedItem, player1Item, player2Item, player3Item, reconnectAndPlayItem);

        // only display context menu for non-empty rows:
        row.contextMenuProperty().bind(
                Bindings.when(row.emptyProperty())
                        .then((ContextMenu) null)
                        .otherwise(rowMenu));
    }

    private void play(EpisodeItem item, String playerPath, boolean runBookmark) {
        try {
            String cmd;
            if (runBookmark) {
                cmd = PlayerService.getInstance().runBookmark(account, item.getCmd());
            } else {
                cmd = PlayerService.getInstance().get(account, item.getCmd());
            }
            if ((isBlank(playerPath) || playerPath.toLowerCase().contains("embedded")) && ConfigurationService.getInstance().read().isEmbeddedPlayer()) {
                MediaPlayerFactory.createMediaPlayer().play(cmd); // Usage updated
            } else {
                Platform.executeCommand(playerPath, cmd);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public static class EpisodeItem {

        private final SimpleStringProperty episodeName;
        private final SimpleStringProperty episodeId;
        private final SimpleStringProperty cmd;

        public EpisodeItem(SimpleStringProperty episodeName, SimpleStringProperty episodeId, SimpleStringProperty cmd) {
            this.episodeName = episodeName;
            this.episodeId = episodeId;
            this.cmd = cmd;
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

        public SimpleStringProperty cmdProperty() {
            return cmd;
        }

        public SimpleStringProperty episodeNameProperty() {
            return episodeName;
        }

        public SimpleStringProperty episodeIdProperty() {
            return episodeId;
        }
    }
}