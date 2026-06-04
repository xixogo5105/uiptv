package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.model.Account;
import com.uiptv.service.ConfigurationService;
import com.uiptv.shared.EpisodeList;
import com.uiptv.widget.PillBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.List;

import static com.uiptv.util.StringUtils.isBlank;

public class PlainEpisodesListUI extends BaseEpisodesListUI {
    private final TableView<EpisodeItem> tableView = new TableView<>();
    private final PillBar<String> seasonPillBar = new PillBar<>(I18n::formatTabNumberLabel, season -> season);
    private final MenuButton bingeWatchButton = new MenuButton();
    private final Button reloadEpisodesButton = new Button();
    private HBox seasonControls;
    private VBox bodyContainer;
    private boolean internalReloadControlVisible = true;
    private List<String> seasonOptions = List.of();

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
        seasonPillBar.getStyleClass().add("watching-now-season-pill-bar");
        seasonPillBar.setMaxWidth(Double.MAX_VALUE);
        seasonPillBar.selectedItemProperty().addListener((_, _, _) -> {
            applyTableFilter();
            updateBingeWatchButton();
        });

        bingeWatchButton.setFocusTraversable(true);
        bingeWatchButton.getStyleClass().setAll("button");
        bingeWatchButton.getStyleClass().add("binge-watch-menu-button");
        updateBingeWatchButton();
        configureReloadEpisodesButton();

        tableView.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        tableView.getColumns().add(createNameColumn());
        configureRowInteractions();
        contentStack.getChildren().add(buildTableBody());
    }

    private TableColumn<EpisodeItem, String> createNameColumn() {
        TableColumn<EpisodeItem, String> nameCol = new TableColumn<>(I18n.tr("autoEpisodes"));
        nameCol.setCellValueFactory(cellData -> cellData.getValue().episodeNameProperty());
        nameCol.setCellFactory(col -> new TableCell<>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                updateEpisodeCell(item, empty);
            }

            private void updateEpisodeCell(String item, boolean empty) {
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }
                EpisodeItem row = getTableView().getItems().get(getIndex());
                setGraphic(buildEpisodeCellGraphic(row, item));
            }
        });
        return nameCol;
    }

    private HBox buildEpisodeCellGraphic(EpisodeItem row, String item) {
        HBox box = new HBox(10);
        box.setAlignment(Pos.CENTER_LEFT);
        box.getChildren().add(new Label(buildEpisodeDisplayTitle(
                row.getSeason(),
                row.getEpisodeNumber(),
                item
        )));
        if (row.isWatched()) {
            Label watched = new Label(I18n.tr("autoWatching"));
            watched.getStyleClass().add("drm-badge");
            box.getChildren().add(watched);
        }
        return box;
    }

    private void configureRowInteractions() {
        tableView.setRowFactory(_ -> {
            TableRow<EpisodeItem> row = new TableRow<>();
            row.setOnMouseClicked(event -> handleEpisodeRowClick(row, event));
            addRightClickContextMenu(row);
            return row;
        });
        tableView.setOnKeyPressed(event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ENTER) {
                playSelectedEpisode();
            }
        });
    }

    private void handleEpisodeRowClick(TableRow<EpisodeItem> row, javafx.scene.input.MouseEvent event) {
        if (!row.isEmpty() && event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
            play(row.getItem(), ConfigurationService.getInstance().read().getDefaultPlayerPath());
        }
    }

    private void playSelectedEpisode() {
        EpisodeItem selected = tableView.getSelectionModel().getSelectedItem();
        if (selected != null) {
            play(selected, ConfigurationService.getInstance().read().getDefaultPlayerPath());
        }
    }

    private VBox buildTableBody() {
        seasonControls = new HBox(8, seasonPillBar, bingeWatchButton, reloadEpisodesButton);
        seasonControls.setAlignment(Pos.CENTER_LEFT);
        seasonControls.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(seasonPillBar, Priority.ALWAYS);
        bingeWatchButton.setMinWidth(Region.USE_PREF_SIZE);
        bingeWatchButton.setMaxWidth(Region.USE_PREF_SIZE);
        reloadEpisodesButton.setMinWidth(Region.USE_PREF_SIZE);
        reloadEpisodesButton.setMaxWidth(Region.USE_PREF_SIZE);

        bodyContainer = new VBox(6, seasonControls, tableView);
        bodyContainer.setMaxWidth(Double.MAX_VALUE);
        bodyContainer.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(bodyContainer, Priority.ALWAYS);
        VBox.setVgrow(tableView, Priority.ALWAYS);
        return bodyContainer;
    }

    public void applyWatchingNowDetailStyling() {
        seasonPillBar.getStyleClass().add("watching-now-season-pill-bar");
        if (bodyContainer != null) {
            bodyContainer.setPadding(new Insets(0, 1, 0, 1));
            bodyContainer.setSpacing(1);
        }
    }

    @Override
    protected void onItemsLoaded() {
        refreshSeasonTabs();
        applyTableFilter();
        updateBingeWatchButton();
    }

    @Override
    protected void showPlaceholder(String text) {
        tableView.setPlaceholder(new Label(text));
    }

    @Override
    protected void setEmptyState(String message, boolean empty) {
        if (seasonControls != null) {
            seasonControls.setManaged(!empty);
            seasonControls.setVisible(!empty);
        }
        updateSeasonPillBarVisibility(!empty);
        tableView.setManaged(!empty);
        tableView.setVisible(!empty);
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
        applyTableFilter();
        updateBingeWatchButton();
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
        String requestedSeason = normalizeNumber(season);
        if (!isBlank(requestedSeason)) {
            selectSeasonTab(requestedSeason);
        }

        EpisodeItem match = findBestEpisodeMatch(season, episodeId, episodeNumber, episodeName);
        if (match == null) {
            return;
        }
        String targetSeason = normalizeNumber(match.getSeason());
        if (!isBlank(targetSeason)) {
            selectSeasonTab(targetSeason);
        }
        applyTableFilter();
        tableView.getSelectionModel().select(match);
        tableView.scrollTo(match);
    }

    private void applyTableFilter() {
        if (allEpisodeItems.isEmpty()) {
            setEmptyState(I18n.tr("autoNoEpisodesFound"), true);
            return;
        }
        setEmptyState("", false);
        String season = selectedSeason();
        if (isBlank(season)) {
            tableView.setItems(allEpisodeItems);
            return;
        }
        tableView.setItems(allEpisodeItems.filtered(item -> season.equals(item.getSeason())));
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
        updateSeasonPillBarVisibility(!allEpisodeItems.isEmpty());
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

    private void updateSeasonPillBarVisibility(boolean hasEpisodes) {
        boolean visible = hasEpisodes && seasonOptions.size() > 1;
        seasonPillBar.setManaged(visible);
        seasonPillBar.setVisible(visible);
    }

    private String selectedSeason() {
        return seasonPillBar.getSelectedItem() == null ? "" : seasonPillBar.getSelectedItem();
    }

    @Override
    protected String selectedBingeWatchSeason() {
        return firstNonBlank(selectedSeason(), "1");
    }

    @Override
    protected void setInternalBingeWatchControlVisible(boolean visible) {
        bingeWatchButton.setManaged(visible);
        bingeWatchButton.setVisible(visible);
    }

    @Override
    protected void setInternalReloadControlVisible(boolean visible) {
        internalReloadControlVisible = visible;
        updateReloadEpisodesButton();
    }

    @Override
    protected void onReloadControlChanged() {
        updateReloadEpisodesButton();
    }

    private void selectSeasonTab(String season) {
        String match = seasonOptions.stream()
                .filter(item -> season.equals(normalizeNumber(item)))
                .findFirst()
                .orElse(null);
        if (match != null) {
            seasonPillBar.setSelectedItem(match);
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
        reloadEpisodesButton.getStyleClass().setAll("button");
        reloadEpisodesButton.setOnAction(event -> reloadFromServer());
        updateReloadEpisodesButton();
    }

    private void updateReloadEpisodesButton() {
        reloadEpisodesButton.setText(reloadFromServerButtonText());
        reloadEpisodesButton.setDisable(reloadFromServerButtonDisabled());
        reloadEpisodesButton.setManaged(internalReloadControlVisible);
        reloadEpisodesButton.setVisible(internalReloadControlVisible);
    }

    private void addRightClickContextMenu(TableRow<EpisodeItem> row) {
        final ContextMenu rowMenu = new ContextMenu();
        UiI18n.preparePopupControl(rowMenu, row);
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);
        row.setOnContextMenuRequested(event -> {
            populateEpisodeContextMenu(rowMenu, row.getItem());
            if (!rowMenu.getItems().isEmpty()) {
                rowMenu.show(row, event.getScreenX(), event.getScreenY());
            }
            event.consume();
        });
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
}
