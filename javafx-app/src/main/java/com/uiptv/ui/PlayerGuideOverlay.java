package com.uiptv.ui;

import com.uiptv.application.*;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.geometry.Pos;
import java.util.function.BooleanSupplier;

/** Current-channel guide inside the JavaFX video surface, without another window. */
public final class PlayerGuideOverlay extends StackPane {
    private final StackPane player;
    private final Button button;
    private final BooleanSupplier liveContent;
    private boolean layoutAvailable = true;
    private Thread worker;
    private int generation;
    private final javafx.beans.value.ChangeListener<MediaPlayerFactory.GuideContext> contextListener = (_, _, _) -> close();

    public PlayerGuideOverlay(StackPane player, Button button, BooleanSupplier liveContent) {
        this.player = player; this.button = button; this.liveContent = liveContent;
        getStyleClass().add("player-guide-overlay"); setFocusTraversable(true); setMinSize(0, 0);
        setAlignment(Pos.CENTER);
        maxWidthProperty().bind(player.widthProperty().multiply(.92)); setMaxHeight(Region.USE_PREF_SIZE);
        setVisible(false); setManaged(false);
        button.setTooltip(new Tooltip(I18n.tr("epgOpenGuide"))); button.setAccessibleText(I18n.tr("epgOpenGuide"));
        button.setOnAction(_ -> { if (isVisible()) close(); else open(); });
        player.widthProperty().addListener((_, _, _) -> close());
        sceneProperty().addListener((_, oldScene, scene) -> {
            if (oldScene != null) {
                MediaPlayerFactory.guideContextProperty().removeListener(contextListener);
                close();
            }
            if (scene != null) {
                MediaPlayerFactory.guideContextProperty().addListener(contextListener);
                updateAvailability();
            }
        });
        EpgModeUI.observeCache(this, this::updateAvailability);
        addEventFilter(javafx.scene.input.KeyEvent.KEY_PRESSED, event -> {
            if (event.getCode() == javafx.scene.input.KeyCode.ESCAPE && isVisible()) { close(); event.consume(); }
        });
        updateAvailability();
    }

    public void updateAvailability() {
        boolean available = layoutAvailable && EpgModeUI.isFeatureEnabled() && liveContent.getAsBoolean();
        button.setVisible(available); button.setManaged(available);
        if (!available) close();
    }

    /**
     * Lets the responsive embedded-player host suppress the overlay when the
     * same guide is already rendered in the adjacent/narrow layout.
     */
    public void setLayoutAvailable(boolean available) {
        layoutAvailable = available;
        updateAvailability();
    }

    private void open() {
        var context = MediaPlayerFactory.guideContextProperty().get();
        if (!EpgModeUI.isFeatureEnabled() || !liveContent.getAsBoolean()) return;
        if (context == null) {
            showUnavailable();
            return;
        }
        load(context);
    }

    private void load(MediaPlayerFactory.GuideContext context) {
        close();
        int request = ++generation;
        showLoading();
        worker = Thread.ofVirtual().start(() -> {
            var programmes = new PlayerEpgApplicationService().schedule(context.account().toAccount(), context.channel());
            var row = new LiveGuideApplicationService.Row(context.account().dbId(), "", "", context.channel(), programmes);
            Platform.runLater(() -> { if (request == generation && getScene() != null && EpgModeUI.isFeatureEnabled()) show(row); });
        });
    }

    private void showUnavailable() {
        Button cancel = new com.uiptv.widget.CloseIconButton(I18n.tr("autoClose"));
        cancel.getStyleClass().add("epg-guide-action");
        cancel.setOnAction(_ -> close());
        VBox unavailable = new VBox(10, cancel, new Label(I18n.tr("epgNoGuide")));
        unavailable.getStyleClass().add("epg-guide-popup");
        getChildren().setAll(unavailable); setVisible(true); setManaged(true);
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
        if (!layoutAvailable) {
            close();
            return;
        }
        double popupWidth = Math.clamp(player.getWidth() * .92, 240, 960);
        var body = ChannelGuidePopup.content(row, popupWidth, this::close);
        ScrollPane viewport = new ScrollPane(body); viewport.setFitToWidth(true);
        viewport.getStyleClass().add("transparent-scroll-pane"); viewport.setMinSize(0, 0);
        viewport.setPrefWidth(popupWidth); viewport.setMaxWidth(popupWidth);
        StackPane.setAlignment(viewport, Pos.CENTER);
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
