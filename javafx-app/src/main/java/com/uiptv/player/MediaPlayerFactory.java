package com.uiptv.player;

import com.uiptv.player.api.VideoPlayerInterface;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.ui.DummyVideoPlayer;
import com.uiptv.util.AppLog;
import javafx.scene.Node;

import com.uiptv.util.I18n;
import com.uiptv.widget.AppNotificationCenter;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Objects;
import javafx.scene.Parent;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;

public class MediaPlayerFactory {
    public record GuideContext(com.uiptv.model.AccountMediaContext account, com.uiptv.model.Channel channel, long playbackIntentId) { }
    private static final javafx.beans.property.ReadOnlyObjectWrapper<com.uiptv.application.PlayerEpgApplicationService.Result> guideSummary = new javafx.beans.property.ReadOnlyObjectWrapper<>();
    public static javafx.beans.property.ReadOnlyObjectProperty<com.uiptv.application.PlayerEpgApplicationService.Result> guideSummaryProperty() { return guideSummary.getReadOnlyProperty(); }
    public static void publishGuideSummary(com.uiptv.application.PlayerEpgApplicationService.Result value) { guideSummary.set(value); }
    private static final javafx.beans.property.ReadOnlyObjectWrapper<GuideContext> guideContext = new javafx.beans.property.ReadOnlyObjectWrapper<>();
    private static final java.util.concurrent.atomic.AtomicLong GUIDE_CONTEXT_SEQUENCE = new java.util.concurrent.atomic.AtomicLong();
    private static final java.util.concurrent.atomic.AtomicLong NATIVE_EPG_REQUESTED_INTENT = new java.util.concurrent.atomic.AtomicLong();
    public static javafx.beans.property.ReadOnlyObjectProperty<GuideContext> guideContextProperty() { return guideContext.getReadOnlyProperty(); }
    /** Claims the single native EPG request permitted for this resolved play intent. */
    public static boolean claimNativeEpgRequest(GuideContext context) {
        if (context == null || context.playbackIntentId() <= 0) return false;
        while (true) {
            long last = NATIVE_EPG_REQUESTED_INTENT.get();
            if (last >= context.playbackIntentId()) return false;
            if (NATIVE_EPG_REQUESTED_INTENT.compareAndSet(last, context.playbackIntentId())) return true;
        }
    }
    public static void updateGuideContext(com.uiptv.model.Account account, com.uiptv.model.Channel channel) {
        com.uiptv.model.Channel lookup = new com.uiptv.model.Channel();
        if (channel != null) { lookup.setChannelId(channel.getChannelId()); lookup.setName(channel.getName()); lookup.setExtraJson(channel.getExtraJson()); }
        GuideContext context = account == null || channel == null ? null : new GuideContext(
                com.uiptv.model.AccountMediaContext.from(account), lookup, GUIDE_CONTEXT_SEQUENCE.incrementAndGet());
        Runnable publish = () -> { guideSummary.set(null); guideContext.set(context); };
        if (javafx.application.Platform.isFxApplicationThread()) publish.run(); else javafx.application.Platform.runLater(publish);
    }
    private static VideoPlayerInterface instance;
    private static VideoPlayerInterface.PlayerType playerType;
    private static final StackPane playerHostContainer = new StackPane();
    private static boolean hostConfigured = false;

    static {
        playerHostContainer.visibleProperty().addListener((_, _, _) -> requestLayoutHierarchy());
        playerHostContainer.managedProperty().addListener((_, _, _) -> requestLayoutHierarchy());
        playerHostContainer.parentProperty().addListener((_, _, _) -> requestLayoutHierarchy());
    }

    private MediaPlayerFactory() {
    }

    public static synchronized VideoPlayerInterface getPlayer() {
        if (instance == null) {
            // Lazy initialization - player only created when first needed
            initializePlayer();
        }
        return instance;
    }

    private static void initializePlayer() {
        Configuration config = ConfigurationService.getInstance().read();
        if (config != null && config.isEmbeddedPlayer()) {
            try {
                instance = new VlcVideoPlayer();
                playerType = VideoPlayerInterface.PlayerType.VLC;
                AppLog.addInfoLog(MediaPlayerFactory.class, "Using embedded VLC player; native initialization is deferred until playback");
            } catch (Exception | LinkageError failure) {
                // Native VLC loading can fail with a linkage error on hosts without libVLC.
                handleVlcInitFailure(failure);
                instance = new DummyVideoPlayer();
                playerType = VideoPlayerInterface.PlayerType.DUMMY;
            }
            if (instance.getPlayerContainer() instanceof Region playerContainer) {
                if (playerType == VideoPlayerInterface.PlayerType.VLC) {
                    definePlayerRegion(playerContainer);
                } else {
                    defineDummyRegion(playerContainer);
                }
            }
        } else {
            instance = new DummyVideoPlayer();
            playerType = VideoPlayerInterface.PlayerType.DUMMY;
            if (instance.getPlayerContainer() instanceof Region playerContainer) {
                defineDummyRegion(playerContainer);
            }
        }
        syncHostToPlayerNode(instance.getPlayerContainer());
        playerHostContainer.getChildren().setAll(instance.getPlayerContainer());
        requestLayoutHierarchy();
        hostConfigured = true;
    }

    public static VideoPlayerInterface.PlayerType getPlayerType() {
        if (playerType == null) {
            getPlayer(); // Ensure player is initialized
        }
        return playerType;
    }

    private static void definePlayerRegion(Region playerContainer) {
        playerContainer.setMinSize(0, 0);
        playerContainer.setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        playerContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
    }

    private static void defineDummyRegion(Region playerContainer) {
        playerContainer.setMinWidth(0);
        playerContainer.setPrefWidth(0);
        playerContainer.setMaxWidth(0);
        playerContainer.setMinHeight(0);
        playerContainer.setPrefHeight(0);
        playerContainer.setMaxHeight(0);
    }

    public static synchronized Node getPlayerContainer() {
        if (!hostConfigured) {
            Configuration config = ConfigurationService.getInstance().read();
            if (config != null && config.isEmbeddedPlayer()) {
                definePlayerRegion(playerHostContainer);
            } else {
                defineDummyRegion(playerHostContainer);
            }
            playerHostContainer.setVisible(false);
            playerHostContainer.setManaged(false);
            requestLayoutHierarchy();
            hostConfigured = true;
        }
        return playerHostContainer;
    }

    private static void syncHostToPlayerNode(Node playerNode) {
        if (playerNode == null) {
            playerHostContainer.visibleProperty().unbind();
            playerHostContainer.managedProperty().unbind();
            playerHostContainer.setVisible(false);
            playerHostContainer.setManaged(false);
            return;
        }
        playerHostContainer.visibleProperty().unbind();
        playerHostContainer.managedProperty().unbind();
        playerHostContainer.visibleProperty().bind(playerNode.visibleProperty());
        playerHostContainer.managedProperty().bind(playerNode.managedProperty());
        requestLayoutHierarchy();
    }

    /**
     * Handle failures during VLC native initialization.
     * Logs stacktrace for diagnostics and shows a user-facing notification.
     * Package-private for testability.
     */
    static void handleVlcInitFailure(Throwable t) {
        Throwable failure = Objects.requireNonNullElseGet(t, () -> new IllegalStateException("unknown native VLC failure"));
        String cause = failure.toString();
        AppLog.addWarningLog(MediaPlayerFactory.class, "VLC not available. Embedded playback disabled. Cause: " + cause);

        // Log full stacktrace for diagnostics / analytics
        try {
            StringWriter sw = new StringWriter();
            PrintWriter pw = new PrintWriter(sw);
            failure.printStackTrace(pw);
            pw.flush();
            AppLog.addErrorLog(MediaPlayerFactory.class, sw.toString());
        } catch (Exception ex) {
            AppLog.addErrorLog(MediaPlayerFactory.class, "Failed to capture VLC init stacktrace: " + ex);
        }

        // Show a user-visible notification so users understand why embedded playback is disabled.
        String message = I18n.tr("autoVlcNativeMissing");
        if (message.isBlank() || message.startsWith("autoVlcNativeMissing")) {
            message = "VLC native libraries not found. Embedded playback is disabled. Install VLC or select a different player in Settings.";
        }
        // Best-effort show notification (may be false if notification host not installed yet)
        try {
            AppNotificationCenter.showError(message, null);
        } catch (Exception _) {
            // Do not let notification failures break startup.
        }
    }

    private static void requestLayoutHierarchy() {
        playerHostContainer.requestLayout();
        Parent parent = playerHostContainer.getParent();
        while (parent != null) {
            parent.requestLayout();
            parent = parent.getParent();
        }
        if (playerHostContainer.getScene() != null && playerHostContainer.getScene().getRoot() != null) {
            playerHostContainer.getScene().getRoot().requestLayout();
        }
    }

    /**
     * Releases and disposes the player singleton.
     * Must be called on application shutdown to prevent memory leaks.
     * Cleans up all resources held by the player.
     */
    public static synchronized void release() {
        if (instance != null) {
            try {
                // Call player-specific disposal (especially important for VLC)
                instance.disposePlayer();
            } catch (Exception e) {
                // Best-effort shutdown: continue releasing shared state even if player teardown fails.
                AppLog.addErrorLog(MediaPlayerFactory.class, "Failed to dispose player: " + e.getMessage());
            }

            try {
                // Clear player container
                playerHostContainer.getChildren().clear();
            } catch (Exception e) {
                // Best-effort shutdown: stale JavaFX nodes should not block process exit.
                AppLog.addErrorLog(MediaPlayerFactory.class, "Failed to clear player container: " + e.getMessage());
            }

            instance = null;
        }
        syncHostToPlayerNode(null);
        playerType = null;
        hostConfigured = false;
    }
}
