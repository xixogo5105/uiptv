package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.service.ConfigurationService;
import com.uiptv.shared.EpisodeList;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.Menu;
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

import static com.uiptv.util.StringUtils.isBlank;

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
        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);
        TableColumn<EpisodeItem, String> nameCol = new TableColumn<>("Episodes");
        nameCol.setCellValueFactory(cellData -> {
            EpisodeItem item = cellData.getValue();
            String season = item.getSeason();
            String episode = item.getEpisodeNumber();

            StringBuilder sb = new StringBuilder();
            sb.append("Season ").append(isBlank(season) ? "1" : season);
            sb.append(" - ");
            sb.append("Episode ").append(isBlank(episode) ? "-" : episode);
            return new SimpleStringProperty(sb.toString());
        });
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
                        Label watched = new Label("WATCHING");
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

    private void applyTableFilter() {
        if (allEpisodeItems.isEmpty()) {
            setEmptyState("No episodes found.", true);
            return;
        }
        setEmptyState("", false);
        tableView.setItems(allEpisodeItems);
    }

    private void addRightClickContextMenu(TableRow<EpisodeItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        rowMenu.hideOnEscapeProperty();
        rowMenu.setAutoHide(true);

        Menu lastWatchedMenu = new Menu("Last Watched");
        rowMenu.getItems().add(lastWatchedMenu);

        rowMenu.setOnShowing(event -> {
            lastWatchedMenu.getItems().clear();
            if (row.getItem() == null) return;
            EpisodeItem item = row.getItem();

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

        rowMenu.getItems().addAll(new SeparatorMenuItem(), playerEmbeddedItem, player1Item, player2Item, player3Item);
        row.setContextMenu(rowMenu);
    }
}
