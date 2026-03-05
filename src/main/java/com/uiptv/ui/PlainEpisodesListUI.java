package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.model.Account;
import com.uiptv.service.ConfigurationService;
import com.uiptv.shared.EpisodeList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

public class PlainEpisodesListUI extends BaseEpisodesListUI {
    private final TableView<EpisodeItem> tableView = new TableView<>();

    public PlainEpisodesListUI(EpisodeList channelList, Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        super(account, categoryTitle, seriesId, seriesCategoryId);
        finishInit();
        setItems(channelList);
    }

    public PlainEpisodesListUI(Account account, String categoryTitle, String seriesId, String seriesCategoryId) {
        super(account, categoryTitle, seriesId, seriesCategoryId);
        finishInit();
    }

    @Override
    protected void initBaseLayout() {
        setPadding(Insets.EMPTY);
        setSpacing(0);
        setMinWidth(0);
        setPrefWidth((double) RootApplication.GUIDED_MAX_WIDTH_PIXELS / 3);
        setMaxWidth(Double.MAX_VALUE);
    }

    @Override
    protected void initWidgets() {
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<EpisodeItem, String> nameCol = new TableColumn<>(I18n.tr("autoEpisodes"));
        nameCol.setCellValueFactory(cellData -> cellData.getValue().episodeNameProperty());
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                } else {
                    EpisodeItem row = getTableView().getItems().get(getIndex());
                    HBox box = new HBox(10);
                    box.setAlignment(Pos.CENTER_LEFT);
                    box.getChildren().add(new Label(item));

                    if (row.isWatched()) {
                        Label watched = new Label(I18n.tr("autoWatching"));
                        watched.getStyleClass().add("drm-badge");
                        box.getChildren().add(watched);
                    }
                    setGraphic(box);
                }
            }
        });

        tableView.getColumns().add(nameCol);
        tableView.setRowFactory(tv -> {
            TableRow<EpisodeItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> {
                if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                    play(row.getItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
                }
            });
            addRightClickContextMenu(row);
            return row;
        });
        tableView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                EpisodeItem selected = tableView.getSelectionModel().getSelectedItem();
                if (selected != null) {
                    play(selected, ConfigurationService.getInstance().read().getDefaultPlayerPath());
                }
            }
        });

        VBox body = new VBox(0, tableView);
        body.setMaxWidth(Double.MAX_VALUE);
        body.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(body, Priority.ALWAYS);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        contentStack.getChildren().add(body);
    }

    @Override
    protected void onItemsLoaded() {
        applyTableFilter();
    }

    @Override
    protected void showPlaceholder(String text) {
        tableView.setPlaceholder(new Label(text));
    }

    @Override
    protected void setEmptyState(String message, boolean empty) {
        tableView.setManaged(!empty);
        tableView.setVisible(!empty);
        emptyStateLabel.setText(message == null ? "" : message);
        emptyStateLabel.setManaged(empty);
        emptyStateLabel.setVisible(empty);
    }

    @Override
    protected void clearEpisodesAndRefreshTabs() {
        itemsLoaded.set(false);
        channelList.episodes.clear();
        allEpisodeItems.clear();
        applyTableFilter();
    }

    @Override
    protected void onBookmarksRefreshed() {
        tableView.refresh();
    }

    @Override
    protected void onWatchedStatesRefreshed() {
        tableView.refresh();
    }

    @Override
    protected void navigateToEpisodeTarget(String season, String episodeId, String episodeNumber, String episodeName) {
        EpisodeItem match = findBestEpisodeMatch(season, episodeId, episodeNumber, episodeName);
        if (match == null) {
            return;
        }
        tableView.getSelectionModel().select(match);
        tableView.scrollTo(match);
    }

    private void applyTableFilter() {
        if (allEpisodeItems.isEmpty()) {
            setEmptyState(I18n.tr("autoNoEpisodesFound"), true);
            return;
        }
        setEmptyState("", false);
        tableView.setItems(allEpisodeItems);
    }

    private void addRightClickContextMenu(TableRow<EpisodeItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);
        MenuItem watchingToggleItem = new MenuItem(I18n.tr("autoWatchingNow"));

        MenuItem playerEmbeddedItem = new MenuItem(I18n.tr("autoEmbeddedPlayer"));
        playerEmbeddedItem.setOnAction(e -> {
            rowMenu.hide();
            play(row.getItem(), "embedded");
        });
        MenuItem player1Item = new MenuItem(I18n.tr("autoPlayer1"));
        player1Item.setOnAction(e -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath1());
        });
        MenuItem player2Item = new MenuItem(I18n.tr("autoPlayer2"));
        player2Item.setOnAction(e -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath2());
        });
        MenuItem player3Item = new MenuItem(I18n.tr("autoPlayer3"));
        player3Item.setOnAction(e -> {
            rowMenu.hide();
            play(row.getItem(), ConfigurationService.getInstance().read().getPlayerPath3());
        });
        rowMenu.getItems().addAll(watchingToggleItem, new SeparatorMenuItem(), playerEmbeddedItem, player1Item, player2Item, player3Item);

        rowMenu.setOnShowing(event -> {
            EpisodeItem item = row.getItem();
            if (item == null) {
                watchingToggleItem.setDisable(true);
                watchingToggleItem.setText(I18n.tr("autoWatchingNow"));
                watchingToggleItem.setOnAction(null);
                return;
            }
            watchingToggleItem.setDisable(false);

            if (item.isWatched()) {
                watchingToggleItem.setText(I18n.tr("autoRemoveWatchingNow"));
                watchingToggleItem.setOnAction(e -> clearWatchedMarker());
            } else {
                watchingToggleItem.setText(I18n.tr("autoWatchingNow"));
                watchingToggleItem.setOnAction(e -> markEpisodeAsWatched(item));
            }
        });
        row.setContextMenu(rowMenu);
    }
}
