package com.uiptv.ui;

import com.uiptv.application.LiveGuideApplicationService;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.control.*;
import java.util.function.Supplier;

/** Cards remain plain; an explicit Guide action loads one channel from its provider. */
final class CardEpgSupport {
    private final Node owner;
    private Thread worker;
    private long generation;
    private javafx.stage.Popup popup;
    CardEpgSupport(Node owner) {
        this.owner = owner;
        owner.sceneProperty().addListener((_, _, scene) -> { if (scene == null) stop(); });
    }
    void addGuideMenu(ContextMenu menu, Node anchor, Supplier<LiveGuideApplicationService.Row> source) {
        if (!EpgModeUI.isFeatureEnabled()) return;
        MenuItem guide = new MenuItem(I18n.tr("epgOpenGuide"));
        guide.setOnAction(_ -> {
            menu.hide(); stop(); long request = ++generation;
            worker = Thread.ofVirtual().start(() -> {
                LiveGuideApplicationService.Row row = source.get();
                if (row != null) row = new LiveGuideApplicationService().hydrate(row);
                var result = row;
                Platform.runLater(() -> {
                    if (request != generation || owner.getScene() == null || !EpgModeUI.isFeatureEnabled() || result == null) return;
                    popup = ChannelGuidePopup.show(anchor != null && anchor.getScene() != null ? anchor : owner, result);
                });
            });
        });
        menu.getItems().add(guide);
    }
    void stop() {
        generation++;
        if (worker != null) { worker.interrupt(); worker = null; }
        if (popup != null) { popup.hide(); popup = null; }
    }
}
