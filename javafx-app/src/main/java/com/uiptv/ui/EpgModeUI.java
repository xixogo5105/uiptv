package com.uiptv.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

/** Observable enable switch for explicit live guide requests. */
public final class EpgModeUI {
    public static final boolean CARDS_ENABLED = false;
    public static final boolean PLAYBACK_ONLY = true;
    private static final BooleanProperty enabled = new SimpleBooleanProperty(false);
    private static final java.util.concurrent.atomic.AtomicLong revision = new java.util.concurrent.atomic.AtomicLong();
    static {
        com.uiptv.service.ConfigurationService.getInstance().addChangeListener(_ -> reloadFeature());
        reloadFeature();
    }
    private EpgModeUI() { }
    private static void reloadFeature() {
        long request = revision.incrementAndGet();
        Thread.ofVirtual().start(() -> {
            boolean value;
            try { value = com.uiptv.service.ConfigurationService.getInstance().read().isEpgEnabled(); }
            catch (RuntimeException _) { value = false; }
            boolean result = value;
            javafx.application.Platform.runLater(() -> { if (request == revision.get()) enabled.set(result); });
        });
    }
    public static boolean isFeatureEnabled() { return enabled.get(); }
    public static void setEnabled(boolean value) {
        Thread.ofVirtual().start(() -> new com.uiptv.application.EpgSettingsApplicationService().setEnabled(value));
    }
    public static void observe(Node owner, Runnable refresh) {
        ChangeListener<Boolean> listener = (_, _, _) -> refresh.run();
        owner.sceneProperty().addListener((_, oldScene, scene) -> {
            if (oldScene != null) enabled.removeListener(listener);
            if (scene != null) { enabled.addListener(listener); refresh.run(); }
        });
        if (owner.getScene() != null) enabled.addListener(listener);
    }
    public static void observeCache(Node owner, Runnable refresh) { observe(owner, refresh); }
}
