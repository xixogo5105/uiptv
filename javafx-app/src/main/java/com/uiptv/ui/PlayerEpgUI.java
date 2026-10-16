package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.Objects;

/** Adjacent to the video, never over its heavyweight native surface. */
public class PlayerEpgUI extends VBox {
    private final Label heading = new Label(), nowTitle = new Label(), nowTime = new Label(), nowDescription = new Label(),
            nextTitle = new Label(), nextTime = new Label(), nextDescription = new Label();
    private final FlowPane cards = new FlowPane(10, 10);
    private final VBox nowCard = new VBox(6), nextCard = new VBox(6);
    private final ProgressBar progress = new ProgressBar();
    private final Hyperlink nowDetails = new Hyperlink(I18n.tr("epgProgrammeDetails")), nextDetails = new Hyperlink(I18n.tr("epgProgrammeDetails"));
    private final VBox expandedDetails = new VBox(8);
    private final Label expandedTitle = new Label(), expandedDescription = new Label();
    private boolean compact;
    private PlayerEpgApplicationService.Result displayed;
    private Thread guideWorker;
    private static final long CACHE_READ_INTERVAL_MS = 30_000;
    private static final long CHANGE_COALESCE_MS = 100;
    private long lastReadNanos;
    private MediaPlayerFactory.GuideContext activeContext;
    private final javafx.animation.PauseTransition cacheRefresh = new javafx.animation.PauseTransition();
    private final javafx.animation.PauseTransition boundaryWake = new javafx.animation.PauseTransition();
    private volatile boolean pendingChange;
    private EpgChangeNotifications.Subscription epgSubscription;
    private final AtomicInteger generation = new AtomicInteger();
    private final ChangeListener<MediaPlayerFactory.GuideContext> listener = (_, _, context) -> refresh(context, true);
    private final javafx.animation.Timeline clock = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.minutes(1),
            _ -> updateProgress()));

    public PlayerEpgUI() {
        getStyleClass().add("player-epg-panel");
        setSpacing(10); setMinWidth(0); setMinHeight(Region.USE_PREF_SIZE); setMaxWidth(Double.MAX_VALUE);
        for (Label label : new Label[]{heading, nowTitle, nowTime, nowDescription, nextTitle, nextTime, nextDescription}) {
            label.setWrapText(true); label.setMinWidth(0); label.setMaxWidth(Double.MAX_VALUE);
            label.setMinHeight(Region.USE_PREF_SIZE);
        }
        heading.getStyleClass().add("epg-player-heading");
        nowTitle.getStyleClass().add("epg-programme-title"); nextTitle.getStyleClass().add("epg-programme-title");
        nowDescription.getStyleClass().add("epg-programme-description"); nextDescription.getStyleClass().add("epg-programme-description");
        nowTime.getStyleClass().add("epg-programme-time"); nextTime.getStyleClass().add("epg-programme-time");
        Label nowBadge = new Label(I18n.tr("epgNow")), nextBadge = new Label(I18n.tr("epgNext"));
        nowBadge.getStyleClass().addAll("epg-programme-badge", "epg-now-badge"); nextBadge.getStyleClass().add("epg-programme-badge");
        progress.getStyleClass().add("epg-programme-progress"); progress.setMaxWidth(Double.MAX_VALUE);
        nowCard.getStyleClass().addAll("epg-programme-card", "epg-current-card"); nextCard.getStyleClass().add("epg-programme-card");
        nowCard.getChildren().addAll(nowBadge, nowTitle, nowTime, progress, nowDescription, nowDetails);
        nextCard.getChildren().addAll(nextBadge, nextTitle, nextTime, nextDescription, nextDetails);
        nowDetails.setOnAction(_ -> showDetails(displayed == null ? null : displayed.now()));
        nextDetails.setOnAction(_ -> showDetails(displayed == null ? null : displayed.next()));
        cards.getChildren().addAll(nowCard, nextCard); cards.setMinWidth(0);
        nowCard.setPrefWidth(300); nextCard.setPrefWidth(300);
        cards.prefWrapLengthProperty().bind(javafx.beans.binding.Bindings.max(300, widthProperty()));
        widthProperty().addListener((_, _, _) -> sizeCards());
        expandedTitle.setMinWidth(0); expandedTitle.setWrapText(true); expandedTitle.setMaxWidth(Double.MAX_VALUE); expandedTitle.getStyleClass().add("epg-programme-title");
        expandedDescription.setMinWidth(0); expandedDescription.setWrapText(true); expandedDescription.setMaxWidth(Double.MAX_VALUE); expandedDescription.getStyleClass().add("epg-programme-description");
        var close = new com.uiptv.widget.CloseIconButton(I18n.tr("autoClose")); close.setOnAction(_ -> closeDetails());
        HBox detailsHeader = new HBox(8, expandedTitle, close); HBox.setHgrow(expandedTitle, Priority.ALWAYS);
        expandedDetails.getStyleClass().addAll("epg-programme-card", "epg-inline-details");
        expandedDetails.getChildren().add(detailsHeader); closeDetails();
        getChildren().addAll(heading, cards, expandedDetails); setVisible(false); setManaged(false);
        cacheRefresh.setOnFinished(_ -> refresh(MediaPlayerFactory.guideContextProperty().get(), false));
        boundaryWake.setOnFinished(_ -> refresh(MediaPlayerFactory.guideContextProperty().get(), false));
        // Keep the existing UI cache-observer contract as a bridge for local cache updates. The typed
        // notification subscription below remains the source for cross-process changes; both paths are
        // coalesced by cacheChanged() and therefore never restore polling or duplicate reads.
        EpgModeUI.observeCache(this, this::cacheChanged);
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) { MediaPlayerFactory.guideContextProperty().removeListener(listener); if (epgSubscription != null) { epgSubscription.close(); epgSubscription = null; } clock.stop(); cacheRefresh.stop(); boundaryWake.stop(); activeContext = null; pendingChange = false; generation.incrementAndGet(); if (guideWorker != null) { guideWorker.interrupt(); guideWorker = null; } display(null); }
            if (newScene != null) { MediaPlayerFactory.guideContextProperty().addListener(listener); epgSubscription = EpgChangeNotifications.subscribe(EpgChangeNotifications.snapshot().revision(), event -> { var context = MediaPlayerFactory.guideContextProperty().get(); if (context != null && context.account() != null && context.channel() != null && event.affects(context.account().dbId(), context.channel().getChannelId())) Platform.runLater(this::cacheChanged); }); refresh(MediaPlayerFactory.guideContextProperty().get(), true); if (EpgModeUI.isFeatureEnabled() && !EpgModeUI.isPlaybackOnly()) clock.play(); }
        });
    }

    /** Coalesce unrelated importer events without temporarily removing the player panel. */
    private void cacheChanged() {
        if (EpgModeUI.isPlaybackOnly()) {
            clock.stop(); cacheRefresh.stop(); boundaryWake.stop(); pendingChange = false;
            return;
        }
        if (!EpgModeUI.isFeatureEnabled()) {
            refresh(MediaPlayerFactory.guideContextProperty().get(), false);
            return;
        }
        if (getScene() == null) return;
        if (guideWorker != null) pendingChange = true;
        long elapsed = lastReadNanos == 0 ? CACHE_READ_INTERVAL_MS
                : java.util.concurrent.TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - lastReadNanos);
        cacheRefresh.setDuration(javafx.util.Duration.millis(Math.max(CHANGE_COALESCE_MS,
                CACHE_READ_INTERVAL_MS - elapsed)));
        cacheRefresh.playFromStart();
    }

    private boolean sameChannel(MediaPlayerFactory.GuideContext context) {
        return context != null && activeContext != null && context.account() != null && activeContext.account() != null
                && context.channel() != null && activeContext.channel() != null
                && Objects.equals(context.account().dbId(), activeContext.account().dbId())
                && context.account().action() == activeContext.account().action()
                && Objects.equals(context.channel().getChannelId(), activeContext.channel().getChannelId());
    }

    private void refresh(MediaPlayerFactory.GuideContext context, boolean contextEvent) {
        // Playback-only mode has no background/cache polling. The context event is
        // emitted by the player immediately before playback; that is the sole fetch.
        if (EpgModeUI.isPlaybackOnly() && !contextEvent) return;
        boolean changed = contextEvent && !sameChannel(context);
        if (changed || !EpgModeUI.isFeatureEnabled() || context == null || context.account() == null
                || context.channel() == null || context.account().action() != com.uiptv.model.Account.AccountAction.itv) {
            generation.incrementAndGet(); cacheRefresh.stop(); boundaryWake.stop(); lastReadNanos = 0;
            if (guideWorker != null) { guideWorker.interrupt(); guideWorker = null; }
            activeContext = context;
            MediaPlayerFactory.publishGuideSummary(null); display(null);
            if (!EpgModeUI.isFeatureEnabled() || context == null || context.account() == null || context.channel() == null
                    || context.account().action() != com.uiptv.model.Account.AccountAction.itv) return;
        }
        if (guideWorker != null) { pendingChange = true; return; }
        cacheRefresh.stop(); activeContext = context; lastReadNanos = System.nanoTime();
        int request = generation.incrementAndGet();
        guideWorker = Thread.ofVirtual().start(() -> {
            try {
                var result = new PlayerEpgApplicationService().current(context.account().toAccount(), context.channel());
                Platform.runLater(() -> completeRefresh(request, result, true));
            } catch (Exception failure) {
                Platform.runLater(() -> completeRefresh(request, null, false));
            }
        });
    }

    private void completeRefresh(int request, PlayerEpgApplicationService.Result result, boolean succeeded) {
        if (request != generation.get()) return;
        guideWorker = null;
        if (!succeeded) return; // Keep the current panel stable during a transient read failure.
        if (!Objects.equals(MediaPlayerFactory.guideSummaryProperty().get(), result)) MediaPlayerFactory.publishGuideSummary(result);
        display(result);
        if (pendingChange) {
            pendingChange = false;
            cacheChanged();
        }
    }

    private void updateProgress() {
        var programme = displayed == null ? null : displayed.now();
        progress.setProgress(programme == null || programme.stop() <= programme.start() ? 0
                : Math.clamp((System.currentTimeMillis() - programme.start()) / (double) (programme.stop() - programme.start()), 0, 1));
    }

    void display(PlayerEpgApplicationService.Result result) {
        if (Objects.equals(displayed, result)) { updateProgress(); return; }
        boolean available = result != null && (result.now() != null || result.next() != null);
        setVisible(available); setManaged(available);
        if (!available) {
            boundaryWake.stop();
            displayed = null; heading.setText("");
            for (Label label : new Label[]{nowTitle, nowTime, nowDescription, nextTitle, nextTime, nextDescription}) label.setText("");
            progress.setProgress(0); closeDetails(); return;
        }
        if (displayed != null && !displayed.equals(result)) closeDetails();
        displayed = result;
        heading.setText(result.channelName());
        nowCard.setVisible(result.now() != null); nowCard.setManaged(result.now() != null);
        nextCard.setVisible(result.next() != null); nextCard.setManaged(result.next() != null);
        programme(result.now(), I18n.tr("epgNow"), nowTitle, nowTime, nowDescription);
        programme(result.next(), I18n.tr("epgNext"), nextTitle, nextTime, nextDescription);
        updateProgress();
        updateDescriptions(); sizeCards();
        scheduleBoundaryWake(result);
    }

    private void scheduleBoundaryWake(PlayerEpgApplicationService.Result result) {
        if (EpgModeUI.isPlaybackOnly()) return;
        long now = System.currentTimeMillis();
        long boundary = result.now() != null ? result.now().stop() : result.next() == null ? 0 : result.next().start();
        if (boundary <= now) boundary = now + 250;
        boundaryWake.setDuration(javafx.util.Duration.millis(Math.min(boundary - now, Integer.MAX_VALUE)));
        boundaryWake.playFromStart();
    }

    private void programme(EpgProgramme programme, String prefix, Label title, Label time, Label description) {
        title.setText(programme == null ? "" : programme.title());
        DateTimeFormatter format = DateTimeFormatter.ofPattern("EEE HH:mm").withZone(ZoneId.systemDefault());
        time.setText(programme == null ? "" : format.format(Instant.ofEpochMilli(programme.start())) + " – " + format.format(Instant.ofEpochMilli(programme.stop())));
        description.setText(programme == null ? "" : programme.description());
        for (Label label : new Label[]{title, time, description}) { label.setVisible(!label.getText().isBlank()); label.setManaged(label.isVisible()); }
    }
    public void setCompact(boolean compact) {
        if (this.compact == compact) return;
        this.compact = compact;
        getStyleClass().remove("compact"); if (compact) getStyleClass().add("compact");
        updateDescriptions(); sizeCards();
    }

    private void sizeCards() {
        double available = Math.max(0, getWidth());
        if (available == 0) return;
        boolean paired = available >= (compact ? 300 : 680) && nowCard.isManaged() && nextCard.isManaged();
        double width = paired ? (available - 10) / 2 : available;
        for (VBox card : new VBox[]{nowCard, nextCard}) { card.setMinWidth(0); card.setPrefWidth(width); card.setMaxWidth(width); }
    }

    private void updateDescriptions() {
        for (Label description : new Label[]{nowDescription, nextDescription}) {
            boolean visible = !description.getText().isBlank();
            description.setVisible(visible); description.setManaged(visible);
        }
        updateDetailsLink(nowDetails, nowDescription);
        updateDetailsLink(nextDetails, nextDescription);
        for (Label title : new Label[]{nowTitle, nextTitle}) {
            Tooltip tooltip = new Tooltip(title.getText()); tooltip.setWrapText(true); tooltip.setMaxWidth(460); title.setTooltip(tooltip);
        }
    }

    private void updateDetailsLink(Hyperlink link, Label description) {
        boolean hasMore = !description.getText().isBlank() && !description.isManaged()
                && !(expandedDetails.isManaged() && description.getText().equals(expandedDescription.getText()));
        link.setVisible(hasMore); link.setManaged(hasMore);
    }

    private void closeDetails() {
        expandedDetails.setVisible(false); expandedDetails.setManaged(false);
        expandedTitle.setText(""); expandedDescription.setText("");
        if (expandedDetails.getChildren().size() > 1) expandedDetails.getChildren().remove(1, expandedDetails.getChildren().size());
        updateDescriptions();
    }

    private void showDetails(EpgProgramme programme) {
        if (programme == null || programme.description().isBlank()) return;
        expandedTitle.setText(programme.title()); expandedDescription.setText(programme.description());
        if (expandedDetails.getChildren().size() == 1) {
            ScrollPane body = new ScrollPane(expandedDescription); body.setFitToWidth(true); body.setPrefViewportHeight(180); body.setMaxHeight(220);
            body.getStyleClass().add("epg-inline-details-scroll"); expandedDetails.getChildren().add(body);
        }
        expandedDetails.setVisible(true); expandedDetails.setManaged(true);
        updateDescriptions();
    }
}
