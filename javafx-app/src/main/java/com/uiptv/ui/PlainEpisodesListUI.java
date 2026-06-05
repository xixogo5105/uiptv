package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.model.Account;
import com.uiptv.service.ConfigurationService;
import com.uiptv.shared.EpisodeList;
import com.uiptv.widget.LoadingStateView;
import com.uiptv.widget.PillBar;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuButton;
import javafx.scene.control.MenuItem;
import javafx.scene.control.OverrunStyle;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.Node;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.StringUtils.isBlank;

public class PlainEpisodesListUI extends BaseEpisodesListUI {
    private static final String KEY_CARD_LABELS = "cardLabels";
    private final VBox cardsContainer = new VBox(6);
    private final ScrollPane cardsScroll = new ScrollPane(cardsContainer);
    private final PillBar<String> seasonPillBar = new PillBar<>(I18n::formatTabNumberLabel, season -> season);
    private final MenuButton bingeWatchButton = new MenuButton();
    private final Button reloadEpisodesButton = new Button();
    private final Map<EpisodeItem, Pane> renderedCardsByItem = new HashMap<>();
    private HBox seasonControls;
    private VBox bodyContainer;
    private Pane selectedEpisodeCard;
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
            applyEpisodeRows();
            updateBingeWatchButton();
        });

        bingeWatchButton.setFocusTraversable(true);
        bingeWatchButton.getStyleClass().setAll("button");
        bingeWatchButton.getStyleClass().add("binge-watch-menu-button");
        updateBingeWatchButton();
        configureReloadEpisodesButton();

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
        contentStack.getChildren().add(buildCardBody());
    }

    private VBox buildCardBody() {
        seasonControls = new HBox(8, seasonPillBar, bingeWatchButton, reloadEpisodesButton);
        seasonControls.setAlignment(Pos.CENTER_LEFT);
        seasonControls.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(seasonPillBar, Priority.ALWAYS);
        bingeWatchButton.setMinWidth(Region.USE_PREF_SIZE);
        bingeWatchButton.setMaxWidth(Region.USE_PREF_SIZE);
        reloadEpisodesButton.setMinWidth(Region.USE_PREF_SIZE);
        reloadEpisodesButton.setMaxWidth(Region.USE_PREF_SIZE);

        bodyContainer = new VBox(6, seasonControls, cardsScroll);
        bodyContainer.setMaxWidth(Double.MAX_VALUE);
        bodyContainer.setMaxHeight(Double.MAX_VALUE);
        HBox.setHgrow(bodyContainer, Priority.ALWAYS);
        VBox.setVgrow(cardsScroll, Priority.ALWAYS);
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
        applyEpisodeRows();
        updateBingeWatchButton();
    }

    @Override
    protected void showPlaceholder(String text) {
        cardsContainer.getChildren().setAll(new LoadingStateView(text));
    }

    @Override
    protected void setEmptyState(String message, boolean empty) {
        if (seasonControls != null) {
            seasonControls.setManaged(!empty);
            seasonControls.setVisible(!empty);
        }
        updateSeasonPillBarVisibility(!empty);
        cardsScroll.setManaged(!empty);
        cardsScroll.setVisible(!empty);
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
        applyEpisodeRows();
        updateBingeWatchButton();
    }

    @Override
    protected void onBookmarksRefreshed() {
        applyEpisodeRows();
    }

    @Override
    protected void onWatchedStatesRefreshed() {
        applyEpisodeRows();
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
        applyEpisodeRows();
        Pane card = renderedCardsByItem.get(match);
        if (card != null) {
            setSelectedEpisodeCard(card);
            card.requestFocus();
        }
    }

    private void applyEpisodeRows() {
        if (allEpisodeItems.isEmpty()) {
            setEmptyState(I18n.tr("autoNoEpisodesFound"), true);
            return;
        }
        setEmptyState("", false);
        String season = selectedSeason();
        List<EpisodeItem> rows = isBlank(season)
                ? List.copyOf(allEpisodeItems)
                : allEpisodeItems.stream()
                .filter(item -> season.equals(item.getSeason()))
                .toList();
        renderedCardsByItem.clear();
        selectedEpisodeCard = null;
        cardsContainer.getChildren().setAll(rows.stream()
                .map(this::createEpisodeRow)
                .toList());
    }

    private Pane createEpisodeRow(EpisodeItem row) {
        HBox card = new HBox(8);
        card.getStyleClass().addAll("uiptv-card", "plain-text-row-card", "watching-now-episode-card", "watching-now-episode-card-compact");
        card.setAlignment(Pos.CENTER_LEFT);
        card.setFocusTraversable(true);
        card.setMinWidth(0);
        card.setMaxWidth(Double.MAX_VALUE);
        card.setMinHeight(42);

        Label title = new Label(buildEpisodeDisplayTitle(
                row.getSeason(),
                row.getEpisodeNumber(),
                row.getEpisodeName()
        ));
        title.getStyleClass().add("strong-label");
        title.setWrapText(false);
        title.setTextOverrun(OverrunStyle.ELLIPSIS);
        title.setMinWidth(0);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMouseTransparent(true);
        HBox.setHgrow(title, Priority.ALWAYS);

        card.getChildren().add(title);
        card.getProperties().put(KEY_CARD_LABELS, List.of(title));
        addRightClickContextMenu(row, card);
        card.setOnMouseClicked(event -> {
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                play(row, ConfigurationService.getInstance().read().getDefaultPlayerPath());
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                setSelectedEpisodeCard(card);
            }
        });
        renderedCardsByItem.put(row, card);
        return card;
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

    @SuppressWarnings("unchecked")
    private void setSelectedEpisodeCard(Pane current) {
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
    private void applyCardSelection(Pane card, boolean selected) {
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
                    if (selected) {
                        label.getStyleClass().add("selected-card-text");
                    } else {
                        label.getStyleClass().remove("selected-card-text");
                    }
                }
            }
        }
    }

    private ContextMenu addRightClickContextMenu(EpisodeItem item, Node owner) {
        final ContextMenu rowMenu = new ContextMenu();
        UiI18n.preparePopupControl(rowMenu, owner);
        rowMenu.setHideOnEscape(true);
        rowMenu.setAutoHide(true);
        owner.setOnContextMenuRequested(event -> {
            populateEpisodeContextMenu(rowMenu, item);
            if (!rowMenu.getItems().isEmpty()) {
                rowMenu.show(owner, event.getScreenX(), event.getScreenY());
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
}
