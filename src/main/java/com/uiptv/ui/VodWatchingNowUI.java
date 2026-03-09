package com.uiptv.ui;

import com.uiptv.db.VodChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.VodWatchState;
import com.uiptv.service.AccountService;
import com.uiptv.service.ImdbMetadataService;
import com.uiptv.service.VodWatchStateChangeListener;
import com.uiptv.service.VodWatchStateService;
import com.uiptv.util.I18n;
import com.uiptv.util.ImageCacheManager;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.image.ImageView;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.uiptv.model.Account.AccountAction.vod;
import static com.uiptv.util.StringUtils.isBlank;
public class VodWatchingNowUI extends VBox {
    private static final String KEY_CARD_LABELS = "cardLabels";
    private static final String KEY_CARD_LINKS = "cardLinks";
    private static final String VOD_WATCHING_NOW_CACHE = "vod-watching-now";
    private final VBox contentBox = new VBox(10);
    private final ScrollPane scrollPane = new ScrollPane(contentBox);
    private final AtomicBoolean reloadInProgress = new AtomicBoolean(false);
    private final AtomicBoolean reloadQueued = new AtomicBoolean(false);
    private final Map<String, VodPanelData> panelDataByKey = new LinkedHashMap<>();
    private volatile boolean dirty = true;
    private boolean listenerRegistered = false;
    private HBox selectedCard;
    private final VodWatchStateChangeListener changeListener = this::onDataChanged;

    public VodWatchingNowUI() {
        setPadding(new Insets(5));
        setSpacing(5);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        contentBox.setPadding(new Insets(5));
        getChildren().add(scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        if (ThumbnailAwareUI.areThumbnailsEnabled()) {
            ImageCacheManager.clearCache(VOD_WATCHING_NOW_CACHE);
        }
        registerListeners();
        refreshIfNeeded();
    }

    public void forceReload() {
        dirty = true;
        refreshIfNeeded();
    }

    public void refreshIfNeeded() {
        if (!dirty || !isDisplayable()) {
            return;
        }
        if (reloadInProgress.get()) {
            reloadQueued.set(true);
            return;
        }
        reloadInProgress.set(true);
        reloadQueued.set(false);
        dirty = false;
        contentBox.getChildren().setAll(new Label(I18n.tr("autoLoadingChannelsFor", I18n.tr("autoVod"))));
        new Thread(() -> {
            List<VodPanelData> rows = buildRows();
            Platform.runLater(() -> {
                try {
                    render(rows);
                } finally {
                    reloadInProgress.set(false);
                    if (reloadQueued.getAndSet(false) || dirty) {
                        refreshIfNeeded();
                    }
                }
            });
        }, "vod-watching-now-loader").start();
    }

    private List<VodPanelData> buildRows() {
        List<VodPanelData> rows = new ArrayList<>();
        for (Account account : AccountService.getInstance().getAll().values()) {
            if (account == null || isBlank(account.getDbId())) {
                continue;
            }
            account.setAction(vod);
            for (VodWatchState state : VodWatchStateService.getInstance().getAllByAccount(account.getDbId())) {
                VodPanelData panel = buildPanel(account, state);
                if (panel != null) {
                    rows.add(panel);
                }
            }
        }
        rows.sort(Comparator.comparingLong((VodPanelData data) -> data.state.getUpdatedAt()).reversed()
                .thenComparing(data -> safe(data.displayTitle), String.CASE_INSENSITIVE_ORDER));
        return rows;
    }

    private VodPanelData buildPanel(Account account, VodWatchState state) {
        if (account == null || state == null || isBlank(state.getVodId())) {
            return null;
        }
        Channel provider = resolveProviderChannel(account, state);
        String title = firstNonBlank(provider == null ? "" : provider.getName(), state.getVodName(), state.getVodId());
        String logo = firstNonBlank(normalizeImageUrl(provider == null ? "" : provider.getLogo(), account), normalizeImageUrl(state.getVodLogo(), account));
        String plot = firstNonBlank(provider == null ? "" : provider.getDescription(), "");
        String releaseDate = firstNonBlank(provider == null ? "" : provider.getReleaseDate(), "");
        String rating = firstNonBlank(provider == null ? "" : provider.getRating(), "");
        String duration = firstNonBlank(provider == null ? "" : provider.getDuration(), "");
        Channel playbackChannel = provider != null ? provider : buildFallbackChannel(state);
        return VodPanelData.create(account, state, playbackChannel, title, logo, plot, releaseDate, rating, duration);
    }

    private Channel resolveProviderChannel(Account account, VodWatchState state) {
        Channel direct = VodChannelDb.get().getChannelByChannelId(state.getVodId(), safe(state.getCategoryId()), account.getDbId());
        if (direct != null) {
            return direct;
        }
        List<Channel> matches = VodChannelDb.get().getAll(
                " WHERE accountId=? AND channelId=?",
                new String[]{account.getDbId(), state.getVodId()}
        );
        return matches.isEmpty() ? null : matches.getFirst();
    }

    private Channel buildFallbackChannel(VodWatchState state) {
        Channel channel = new Channel();
        channel.setChannelId(state.getVodId());
        channel.setCategoryId(state.getCategoryId());
        channel.setName(state.getVodName());
        channel.setCmd(state.getVodCmd());
        channel.setLogo(state.getVodLogo());
        return channel;
    }

    private void render(List<VodPanelData> rows) {
        contentBox.getChildren().clear();
        panelDataByKey.clear();
        if (rows == null || rows.isEmpty()) {
            contentBox.getChildren().add(new Label(I18n.tr("autoNoWatchingNowVodFound")));
            return;
        }
        for (VodPanelData row : rows) {
            panelDataByKey.put(panelKey(row), row);
            contentBox.getChildren().add(createCard(row));
            triggerImdbLoad(row);
        }
        VBox.setVgrow(contentBox, Priority.ALWAYS);
    }

    private HBox createCard(VodPanelData data) {
        HBox card = new HBox(10);
        card.setAlignment(Pos.TOP_LEFT);
        card.setPadding(new Insets(8));
        card.getStyleClass().add("uiptv-card");

        ImageView poster = SeriesCardUiSupport.createFitPoster(data.coverUrl, 72, 108, VOD_WATCHING_NOW_CACHE);
        poster.setVisible(ThumbnailAwareUI.areThumbnailsEnabled());
        poster.setManaged(ThumbnailAwareUI.areThumbnailsEnabled());

        VBox details = new VBox(4);
        details.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(details, Priority.ALWAYS);

        ContextMenu cardMenu = new ContextMenu();
        I18n.preparePopupControl(cardMenu, card);
        cardMenu.setHideOnEscape(true);
        cardMenu.setAutoHide(true);

        Button playButton = new Button(I18n.tr("autoPlay") + "...");
        playButton.getStyleClass().setAll("button");
        playButton.getStyleClass().add("small-pill-button");
        playButton.setMinWidth(Region.USE_PREF_SIZE);
        playButton.setMaxWidth(Region.USE_PREF_SIZE);
        playButton.setMinHeight(Region.USE_PREF_SIZE);
        playButton.setFocusTraversable(true);
        playButton.setOnAction(event -> {
            event.consume();
            showContextMenu(cardMenu, card, data);
        });

        Label title = new Label(data.displayTitle);
        title.getStyleClass().add("strong-label");
        title.setWrapText(true);
        title.setMaxWidth(Double.MAX_VALUE);
        title.setMinWidth(0);
        title.setMinHeight(Region.USE_PREF_SIZE);
        HBox.setHgrow(title, Priority.ALWAYS);

        Label accountLabel = new Label("[" + data.account.getAccountName() + "]");
        accountLabel.setWrapText(true);
        accountLabel.setMaxWidth(Double.MAX_VALUE);
        accountLabel.setMinWidth(0);
        accountLabel.setMinHeight(Region.USE_PREF_SIZE);

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        HBox titleRow = new HBox(10, title, titleSpacer, playButton);
        titleRow.setAlignment(Pos.TOP_LEFT);

        details.getChildren().addAll(titleRow, accountLabel);
        addMetadataLines(details, data);
        updateImdbNodes(details, data);

        HBox topRow = new HBox(10, poster, details);
        topRow.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(details, Priority.ALWAYS);

        VBox cardBody = new VBox(4);
        cardBody.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(cardBody, Priority.ALWAYS);
        cardBody.getChildren().add(topRow);
        if (data.plotNode != null) {
            details.getChildren().remove(data.plotNode);
            data.plotNode.setMaxWidth(Double.MAX_VALUE);
            data.plotNode.setMinWidth(0);
            cardBody.getChildren().add(data.plotNode);
        }

        card.getChildren().add(cardBody);
        List<Label> cardLabels = collectCardLabels(data, title);
        cardLabels.add(accountLabel);
        card.getProperties().put(KEY_CARD_LABELS, cardLabels);
        card.getProperties().put(KEY_CARD_LINKS, List.of());
        card.setOnMouseClicked(event -> {
            cardMenu.hide();
            if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 2) {
                setSelectedCard(card);
                play(data, null);
            } else if (event.getButton() == MouseButton.PRIMARY && event.getClickCount() == 1) {
                setSelectedCard(card);
            }
        });
        addContextMenu(card, cardMenu, data);
        return card;
    }

    private List<Label> collectCardLabels(VodPanelData data, Label title) {
        List<Label> labels = new ArrayList<>();
        labels.add(title);
        if (data.ratingNode != null) {
            labels.add(data.ratingNode);
        }
        if (data.releaseNode != null) {
            labels.add(data.releaseNode);
        }
        if (data.durationNode != null) {
            labels.add(data.durationNode);
        }
        if (data.plotNode != null) {
            labels.add(data.plotNode);
        }
        return labels;
    }

    private void addMetadataLines(VBox details, VodPanelData data) {
        if (!isBlank(data.rating)) {
            data.ratingNode = new Label(I18n.tr("autoImdbPrefix", data.rating));
            details.getChildren().add(data.ratingNode);
        }
        if (!isBlank(data.releaseDate)) {
            data.releaseNode = new Label(I18n.tr("autoReleasePrefix", data.releaseDate));
            details.getChildren().add(data.releaseNode);
        }
        if (!isBlank(data.duration)) {
            data.durationNode = new Label(I18n.tr("autoDurationPrefix", data.duration));
            details.getChildren().add(data.durationNode);
        }
        if (!isBlank(data.plot)) {
            data.plotNode = new Label(data.plot);
            data.plotNode.setWrapText(true);
            data.plotNode.setMaxWidth(Double.MAX_VALUE);
            data.plotNode.setMinWidth(0);
            details.getChildren().add(data.plotNode);
        }
    }

    private void updateImdbNodes(VBox details, VodPanelData data) {
        if (data.imdbBadgeNode != null) {
            details.getChildren().remove(data.imdbBadgeNode);
        }
        if (data.imdbLoadingNode != null) {
            details.getChildren().remove(data.imdbLoadingNode);
        }
        data.imdbBadgeNode = SeriesCardUiSupport.createImdbRatingPill(data.rating, data.imdbUrl);
        if (data.imdbBadgeNode != null) {
            details.getChildren().add(2, data.imdbBadgeNode);
        }
        if (data.imdbLoading && !data.imdbLoaded) {
            if (data.imdbLoadingNode == null) {
                ProgressIndicator imdbProgress = new ProgressIndicator();
                imdbProgress.setPrefSize(14, 14);
                imdbProgress.setMinSize(14, 14);
                imdbProgress.setMaxSize(14, 14);
                data.imdbLoadingNode = new HBox(6, imdbProgress, new Label(I18n.tr("autoLoadingIMDbDetails")));
            }
            details.getChildren().add(data.imdbLoadingNode);
        }
    }

    private void triggerImdbLoad(VodPanelData data) {
        if (data == null || data.imdbLoaded || data.imdbLoading) {
            return;
        }
        data.imdbLoading = true;
        new Thread(() -> {
            try {
                JSONObject imdb = ImdbMetadataService.getInstance().findBestEffortMovieDetails(data.displayTitle, "");
                if (imdb != null) {
                    mergeImdb(data, imdb);
                }
            } finally {
                data.imdbLoaded = true;
                data.imdbLoading = false;
                Platform.runLater(this::refreshRenderedCards);
            }
        }, "vod-watching-now-imdb").start();
    }

    private void mergeImdb(VodPanelData data, JSONObject imdb) {
        if (data == null || imdb == null) {
            return;
        }
        if (isBlank(data.coverUrl)) {
            data.coverUrl = normalizeImageUrl(imdb.optString("cover", ""), data.account);
        }
        data.plot = firstNonBlank(data.plot, imdb.optString("plot", ""));
        data.releaseDate = firstNonBlank(data.releaseDate, imdb.optString("releaseDate", ""));
        data.rating = firstNonBlank(data.rating, imdb.optString("rating", ""));
        data.imdbUrl = firstNonBlank(data.imdbUrl, imdb.optString("imdbUrl", ""));
    }

    private void refreshRenderedCards() {
        if (!isDisplayable()) {
            dirty = true;
            return;
        }
        render(new ArrayList<>(panelDataByKey.values()));
    }

    private void addContextMenu(HBox card, ContextMenu menu, VodPanelData data) {
        card.setOnContextMenuRequested(event -> {
            setSelectedCard(card);
            showContextMenu(menu, card, data, event.getScreenX(), event.getScreenY());
            event.consume();
        });
    }

    private void showContextMenu(ContextMenu menu, HBox card, VodPanelData data) {
        double x = card.localToScreen(card.getBoundsInLocal()).getMinX() + card.getWidth() - 8;
        double y = card.localToScreen(card.getBoundsInLocal()).getMinY() + 8;
        showContextMenu(menu, card, data, x, y);
    }

    private void showContextMenu(ContextMenu menu, HBox card, VodPanelData data, double screenX, double screenY) {
        populateContextMenu(menu, data);
        if (!menu.getItems().isEmpty()) {
            menu.show(card, screenX, screenY);
        }
    }

    @SuppressWarnings("unchecked")
    private void setSelectedCard(HBox current) {
        if (current == null) {
            return;
        }
        if (selectedCard != null && selectedCard != current) {
            applyCardSelection(selectedCard, false);
        }
        applyCardSelection(current, true);
        selectedCard = current;
    }

    @SuppressWarnings("unchecked")
    private void applyCardSelection(HBox card, boolean selected) {
        if (card == null) {
            return;
        }
        toggleStyleClass(card.getStyleClass(), "selected-card", selected);
        applyLabelSelection(card, selected);
        applyLinkSelection(card, selected);
    }

    private void applyLabelSelection(HBox card, boolean selected) {
        Object labelsObj = card.getProperties().get(KEY_CARD_LABELS);
        if (!(labelsObj instanceof List<?> labels)) {
            return;
        }
        for (Object labelObj : labels) {
            if (labelObj instanceof Label label) {
                toggleStyleClass(label.getStyleClass(), "selected-card-text", selected);
            }
        }
    }

    private void applyLinkSelection(HBox card, boolean selected) {
        Object linksObj = card.getProperties().get(KEY_CARD_LINKS);
        if (!(linksObj instanceof List<?> links)) {
            return;
        }
        for (Object linkObj : links) {
            if (linkObj instanceof Hyperlink link) {
                toggleStyleClass(link.getStyleClass(), "selected-card-link", selected);
            }
        }
    }

    private void toggleStyleClass(List<String> styleClasses, String styleClass, boolean enabled) {
        if (enabled) {
            if (!styleClasses.contains(styleClass)) {
                styleClasses.add(styleClass);
            }
            return;
        }
        styleClasses.remove(styleClass);
    }

    private void populateContextMenu(ContextMenu menu, VodPanelData data) {
        menu.getItems().clear();
        if (data == null) {
            return;
        }
        for (WatchingNowActionMenu.ActionDescriptor action : WatchingNowActionMenu.buildEpisodeStyleActions(
                true,
                PlaybackUIService.getConfiguredPlayerOptions()
        )) {
            switch (action.kind()) {
                case SEPARATOR -> menu.getItems().add(new SeparatorMenuItem());
                case PLAYER -> {
                    MenuItem playerItem = new MenuItem(action.label());
                    playerItem.setOnAction(e -> {
                        menu.hide();
                        play(data, action.playerPath());
                    });
                    menu.getItems().add(playerItem);
                }
                case REMOVE_WATCHING_NOW -> {
                    MenuItem removeItem = new MenuItem(I18n.tr("autoRemoveWatchingNow"));
                    removeItem.getStyleClass().add("danger-menu-item");
                    removeItem.setOnAction(e -> removeVod(data));
                    menu.getItems().add(removeItem);
                }
                case WATCHING_NOW -> {
                    // VOD cards are already in watching-now; no add action here.
                }
            }
        }
    }

    private void play(VodPanelData data, String playerPath) {
        if (data == null || data.playbackChannel == null) {
            return;
        }
        PlaybackUIService.play(this, new PlaybackUIService.PlaybackRequest(data.account, data.playbackChannel, playerPath)
                .categoryId(data.state.getCategoryId())
                .channelId(data.state.getVodId())
                .errorPrefix(I18n.tr("autoErrorPlayingChannelPrefix")));
    }

    private void removeVod(VodPanelData data) {
        if (data == null || data.account == null || data.state == null) {
            return;
        }
        new Thread(() -> {
            VodWatchStateService.getInstance().remove(data.account.getDbId(), data.state.getCategoryId(), data.state.getVodId());
            Platform.runLater(() -> {
                panelDataByKey.remove(panelKey(data));
                render(new ArrayList<>(panelDataByKey.values()));
            });
        }, "vod-watching-now-remove").start();
    }

    private void registerListeners() {
        if (!listenerRegistered) {
            VodWatchStateService.getInstance().addChangeListener(changeListener);
            listenerRegistered = true;
        }
        sceneProperty().addListener((_, _, newScene) -> {
            if (newScene == null) {
                if (listenerRegistered) {
                    VodWatchStateService.getInstance().removeChangeListener(changeListener);
                    listenerRegistered = false;
                }
            } else {
                if (!listenerRegistered) {
                    VodWatchStateService.getInstance().addChangeListener(changeListener);
                    listenerRegistered = true;
                }
                refreshIfNeeded();
            }
        });
        visibleProperty().addListener((obs, oldVal, newVal) -> {
            if (Boolean.TRUE.equals(newVal)) {
                refreshIfNeeded();
            }
        });
    }

    private void onDataChanged(String accountId, String vodId) {
        dirty = true;
        Platform.runLater(this::refreshIfNeeded);
    }

    private boolean isDisplayable() {
        return getScene() != null && isVisible();
    }

    private String panelKey(VodPanelData data) {
        return safe(data.account.getDbId()) + "|" + safe(data.state.getCategoryId()) + "|" + safe(data.state.getVodId());
    }

    private String normalizeImageUrl(String value, Account account) {
        if (!ThumbnailAwareUI.areThumbnailsEnabled() || isBlank(value)) {
            return "";
        }
        String normalized = value.trim().replace("\\/", "/");
        if (isBlank(normalized)) {
            return "";
        }
        if (isAbsoluteImageUrl(normalized)) {
            return normalized;
        }
        URI base = parseAccountBaseUri(account);
        String scheme = base != null && !isBlank(base.getScheme()) ? base.getScheme() : "http";
        String host = base != null ? base.getHost() : null;
        int port = base == null ? -1 : base.getPort();
        return resolveRelativeImageUrl(normalized, scheme, host, port);
    }

    private boolean isAbsoluteImageUrl(String normalized) {
        return normalized.startsWith("http://")
                || normalized.startsWith("https://")
                || normalized.startsWith("data:")
                || normalized.startsWith("file:");
    }

    private URI parseAccountBaseUri(Account account) {
        try {
            return URI.create(firstNonBlank(account == null ? "" : account.getServerPortalUrl(), account == null ? "" : account.getUrl()));
        } catch (Exception _) {
            // Keep relative value when the configured account URL cannot be parsed.
            return null;
        }
    }

    private String resolveRelativeImageUrl(String normalized, String scheme, String host, int port) {
        if (normalized.startsWith("//")) {
            return scheme + ":" + normalized;
        }
        if (normalized.startsWith("/") && !isBlank(host)) {
            return scheme + "://" + host + (port > 0 ? ":" + port : "") + normalized;
        }
        if (normalized.matches("^[a-zA-Z0-9.-]+(?::\\d+)?/.*")) {
            return scheme + "://" + normalized;
        }
        return normalized;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) {
            if (!isBlank(value)) {
                return value.trim();
            }
        }
        return "";
    }

    private String safe(String value) {
        return value == null ? "" : value.trim();
    }

    private static final class VodPanelData {
        private final Account account;
        private final VodWatchState state;
        private final Channel playbackChannel;
        private final String displayTitle;
        private String coverUrl;
        private String plot;
        private String releaseDate;
        private String rating;
        private final String duration;
        private String imdbUrl = "";
        private boolean imdbLoaded;
        private boolean imdbLoading;
        private Label ratingNode;
        private Label releaseNode;
        private Label durationNode;
        private Label plotNode;
        private HBox imdbBadgeNode;
        private HBox imdbLoadingNode;

        private VodPanelData(Account account, VodWatchState state, Channel playbackChannel, String displayTitle, String duration) {
            this.account = account;
            this.state = state;
            this.playbackChannel = playbackChannel;
            this.displayTitle = displayTitle;
            this.duration = duration;
        }

        private static VodPanelData create(Account account, VodWatchState state, Channel playbackChannel, String displayTitle,
                                           String coverUrl, String plot, String releaseDate, String rating, String duration) {
            VodPanelData data = new VodPanelData(account, state, playbackChannel, displayTitle, duration);
            data.coverUrl = coverUrl;
            data.plot = plot;
            data.releaseDate = releaseDate;
            data.rating = rating;
            return data;
        }
    }
}
