package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableBooleanValue;
import javafx.scene.control.*;
import javafx.scene.layout.*;

/** Current-channel guide inside the JavaFX video surface, without another window. */
public final class PlayerGuideOverlay extends StackPane {
    private final StackPane player;
    private final Button button;
    private final ObservableBooleanValue fullscreen;
    private final ObservableBooleanValue wideMode;
    private Thread worker;
    private int generation;
    private final ChangeListener<MediaPlayerFactory.GuideContext> contextListener = (_, _, _) -> { close(); updateAvailability(); };
    private final ChangeListener<PlayerEpgApplicationService.Result> summaryListener = (_, _, _) -> updateAvailability();

    public PlayerGuideOverlay(StackPane player, Button button, ObservableBooleanValue fullscreen) {
        this(player, button, fullscreen, null);
    }

    public PlayerGuideOverlay(StackPane player, Button button, ObservableBooleanValue fullscreen, ObservableBooleanValue wideMode) {
        this.player = player; this.button = button; this.fullscreen = fullscreen; this.wideMode = wideMode;
        getStyleClass().add("player-guide-overlay"); setFocusTraversable(true); setMinSize(0, 0);
        maxWidthProperty().bind(player.widthProperty().multiply(.92)); setMaxHeight(Region.USE_PREF_SIZE);
        setVisible(false); setManaged(false);
        button.setTooltip(new Tooltip(I18n.tr("epgOpenGuide"))); button.setAccessibleText(I18n.tr("epgOpenGuide"));
        button.setOnAction(_ -> { if (isVisible()) close(); else open(); });
        player.widthProperty().addListener((_, _, _) -> { close(); updateAvailability(); });
        fullscreen.addListener((_, _, _) -> updateAvailability());
        if (wideMode != null) wideMode.addListener((_, _, _) -> updateAvailability());
        sceneProperty().addListener((_, oldScene, scene) -> {
            if (oldScene != null) {
                MediaPlayerFactory.guideContextProperty().removeListener(contextListener);
                MediaPlayerFactory.guideSummaryProperty().removeListener(summaryListener); close();
            }
            if (scene != null) {
                MediaPlayerFactory.guideContextProperty().addListener(contextListener);
                MediaPlayerFactory.guideSummaryProperty().addListener(summaryListener); updateAvailability();
            }
        });
        EpgModeUI.observeCache(this, this::updateAvailability);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && isVisible()) { close(); event.consume(); }
        });
        updateAvailability();
    }

    private void updateAvailability() {
        // The guide belongs to the active player, including the normal
        // split-layout player. The summary is loaded asynchronously, so using
        // it as the availability gate makes the control disappear during the
        // normal loading window (and whenever the adjacent EPG panel is not
        // mounted). Opening the guide performs its own schedule load.
        boolean wideLayout = wideMode == null || wideMode.get() || fullscreen.get();
        boolean available = wideLayout
                && MediaPlayerFactory.guideContextProperty().get() != null
                && (EpgModeUI.isFeatureEnabled() || MediaPlayerFactory.guideSummaryProperty().get() != null);
        button.setVisible(available); button.setManaged(available);
        if (!available) close();
    }

    private void open() {
        var context = MediaPlayerFactory.guideContextProperty().get();
        if (context == null || (!EpgModeUI.isFeatureEnabled() && MediaPlayerFactory.guideSummaryProperty().get() == null)) return;
        var summary = MediaPlayerFactory.guideSummaryProperty().get();
        // The player panel normally publishes this snapshot first. If the user clicks Guide during that
        // short loading window, resolve it explicitly instead of silently discarding the click.
        load(context, summary);
    }

    private void load(MediaPlayerFactory.GuideContext context, PlayerEpgApplicationService.Result snapshot) {
        close();
        int request = ++generation;
        showLoading();
        worker = Thread.ofVirtual().start(() -> {
            var summary = snapshot != null ? snapshot : MediaPlayerFactory.guideSummaryProperty().get();
            var programmes = EpgModeUI.isPlaybackOnly() && summary != null
                    ? java.util.stream.Stream.of(summary.now(), summary.next()).filter(java.util.Objects::nonNull).toList()
                    : new PlayerEpgApplicationService().schedule(context.account().toAccount(), context.channel());
            var row = new LiveGuideApplicationService.Row(context.account().dbId(), "", "", context.channel(), programmes);
            Platform.runLater(() -> { if (request == generation && getScene() != null && EpgModeUI.isFeatureEnabled()) show(row); });
        });
    }

    private void showLoading() {
        Button cancel = new com.uiptv.widget.CloseIconButton(I18n.tr("autoClose"));
        cancel.getStyleClass().add("epg-guide-action");
        cancel.setOnAction(_ -> close());
        VBox loading = new VBox(10, cancel, new Label(I18n.tr("epgLoading")));
        loading.getStyleClass().add("epg-guide-popup");
        getChildren().setAll(loading); setVisible(true); setManaged(true);
    }

    /** Display a resolved schedule without creating a Stage or Popup. */
    public void show(LiveGuideApplicationService.Row row) {
        var body = ChannelGuidePopup.content(row, Math.clamp(player.getWidth() * .92, 240, 960), this::close);
        ScrollPane viewport = new ScrollPane(body); viewport.setFitToWidth(true);
        viewport.getStyleClass().add("transparent-scroll-pane"); viewport.setMinSize(0, 0);
        viewport.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        viewport.setPrefViewportHeight(Math.clamp(player.getHeight() * .9, 80, 370));
        viewport.maxHeightProperty().bind(player.heightProperty().multiply(.9));
        getChildren().setAll(viewport);
        setVisible(true); setManaged(true); requestFocus();
    }

    public void close() {
        generation++;
        if (worker != null) { worker.interrupt(); worker = null; }
        for (var node : getChildren()) if (node instanceof javafx.scene.Parent parent) ChannelGuidePopup.release(parent);
        getChildren().clear(); setVisible(false); setManaged(false);
    }
}
