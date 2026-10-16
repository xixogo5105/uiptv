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
    private static final BooleanProperty playbackOnly = new SimpleBooleanProperty(false);
    static {
        var pending=new java.util.concurrent.atomic.AtomicBoolean();
        com.uiptv.application.EpgChangeNotifications.subscribe(com.uiptv.application.EpgChangeNotifications.snapshot().revision(), _ -> {
            if(pending.compareAndSet(false,true)) javafx.application.Platform.runLater(() -> {
                pending.set(false); cacheRevision.set(cacheRevision.get()+1);
            });
        });
        com.uiptv.service.ConfigurationService.getInstance().addChangeListener(_ -> reloadFeature());
        reloadFeature();
    }
    private static void reloadFeature() {
        long revision = featureRevision.incrementAndGet();
        Thread.ofVirtual().start(() -> {
            boolean value = false;
            boolean playerOnly = false;
            try {
                var configuration = com.uiptv.service.ConfigurationService.getInstance().read();
                value = configuration != null && configuration.isEpgEnabled();
                playerOnly = value && configuration.isEpgPlaybackOnly();
            } catch (RuntimeException ignored) {
                // A transient configuration read failure must not leave the UI in an active mode.
            }
            boolean finalValue = value;
            boolean finalPlayerOnly = playerOnly;
            javafx.application.Platform.runLater(() -> {
                if (revision != featureRevision.get()) return;
                featureEnabled.set(finalValue);
                playbackOnly.set(finalPlayerOnly);
            });
        });
    }
    public static boolean isFeatureEnabled() { return featureEnabled.get(); }
    private EpgModeUI() { }
    public static boolean isEnabled() { return featureEnabled.get(); }
    public static boolean isPlaybackOnly() { return featureEnabled.get() && playbackOnly.get(); }
    public static boolean isCardsEnabled() { return featureEnabled.get() && !playbackOnly.get(); }
    public static void setEnabled(boolean value) {
        Thread.ofVirtual().start(() -> new com.uiptv.application.EpgSettingsApplicationService().setEnabled(value));
    }
    public static void setPlaybackOnly(boolean value) {
        Thread.ofVirtual().start(() -> new com.uiptv.application.EpgSettingsApplicationService().setPlaybackOnly(value));
    }
    public static void refreshGuides(Runnable finished) {
        Thread.ofVirtual().start(() -> {
            try { new com.uiptv.application.EpgCacheRefreshService().clearAndRefresh(); }
            finally { javafx.application.Platform.runLater(() -> {
                cacheRevision.set(cacheRevision.get() + 1);
                if (finished != null) finished.run();
            }); }
        });
    }
    /** Feature observers may rebuild layout only when the saved enable switch changes. */
    public static void observe(Node owner,Runnable refresh) { observe(owner,refresh,false); }
    /** Data observers update existing nodes when cached records change; never rebuild a catalog. */
    public static void observeCache(Node owner,Runnable refresh) { observe(owner,refresh,true); }
    private static void observe(Node owner,Runnable refresh,boolean cacheUpdates) {
        ChangeListener<Boolean> listener=(_,_,_) -> refresh.run();
        ChangeListener<Number> cacheListener=(_,_,_) -> refresh.run();
        owner.sceneProperty().addListener((_,oldScene,newScene) -> {
            if(oldScene!=null) {
                featureEnabled.removeListener(listener);
                playbackOnly.removeListener(listener);
                if(cacheUpdates) cacheRevision.removeListener(cacheListener);
            }
            if(newScene!=null) {
                featureEnabled.addListener(listener);
                playbackOnly.addListener(listener);
                if(cacheUpdates) cacheRevision.addListener(cacheListener);
                refresh.run();
            }
        });
        if(owner.getScene()!=null) {
            featureEnabled.addListener(listener);
            playbackOnly.addListener(listener);
            if(cacheUpdates) cacheRevision.addListener(cacheListener);
        }
    }
}
