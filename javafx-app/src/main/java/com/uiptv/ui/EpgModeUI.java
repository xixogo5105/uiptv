package com.uiptv.ui;

import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;

/** Observable desktop presentation of the persisted EPG flag. */
public final class EpgModeUI {
    private static final java.util.concurrent.atomic.AtomicLong featureRevision = new java.util.concurrent.atomic.AtomicLong();
    private static final javafx.beans.property.LongProperty cacheRevision = new javafx.beans.property.SimpleLongProperty();
    private static final BooleanProperty featureEnabled = new SimpleBooleanProperty(false);
    static {
        com.uiptv.service.ConfigurationService.getInstance().addChangeListener(_ -> reloadFeature());
        reloadFeature();
    }
    private static void reloadFeature() {
        long revision = featureRevision.incrementAndGet();
        Thread.ofVirtual().start(() -> {
            boolean value = new com.uiptv.application.EpgApplicationService().isEnabled();
            javafx.application.Platform.runLater(() -> { if (revision != featureRevision.get()) return; featureEnabled.set(value); });
        });
    }
    public static boolean isFeatureEnabled() { return featureEnabled.get(); }
    private EpgModeUI() { }
    public static boolean isEnabled() { return featureEnabled.get(); }
    public static void setEnabled(boolean value) {
        Thread.ofVirtual().start(() -> new com.uiptv.application.EpgSettingsApplicationService().setEnabled(value));
    }
    public static void refreshGuides(Runnable finished) {
        Thread.ofVirtual().start(() -> {
            try { new com.uiptv.application.EpgApplicationService().clearCache(); }
            finally { javafx.application.Platform.runLater(() -> {
                cacheRevision.set(cacheRevision.get() + 1);
                if (finished != null) finished.run();
            }); }
        });
    }
    public static void observe(Node owner, Runnable refresh) {
        ChangeListener<Boolean> listener = (_, _, _) -> refresh.run();
        ChangeListener<Number> cacheListener = (_, _, _) -> refresh.run();
        owner.sceneProperty().addListener((_, oldScene, newScene) -> {
            if (oldScene != null) { featureEnabled.removeListener(listener); cacheRevision.removeListener(cacheListener); }
            if (newScene != null) { featureEnabled.addListener(listener); cacheRevision.addListener(cacheListener); refresh.run(); }
        });
        if (owner.getScene() != null) { featureEnabled.addListener(listener); cacheRevision.addListener(cacheListener); }
    }
}
