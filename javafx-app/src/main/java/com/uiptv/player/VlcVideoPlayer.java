package com.uiptv.player;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.util.I18n;
import com.uiptv.util.ResolutionDisplayUtil;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;
import javafx.util.Duration;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.media.MediaRef;
import uk.co.caprica.vlcj.media.TrackType;
import uk.co.caprica.vlcj.media.VideoTrackInfo;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import uk.co.caprica.vlcj.player.embedded.videosurface.CallbackVideoSurface;
import uk.co.caprica.vlcj.player.embedded.videosurface.VideoSurfaceAdapters;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormat;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.BufferFormatCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.RenderCallback;
import uk.co.caprica.vlcj.player.embedded.videosurface.callback.format.RV32BufferFormat;

import java.net.URISyntaxException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;
import org.apache.hc.core5.net.URIBuilder;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

public class VlcVideoPlayer extends BaseVideoPlayer {
    static final String VLC_HTTP_USER_AGENT = CHROME_USER_AGENT;

    private final Object playerLock = new Object();
    private final ExecutorService playExecutor = Executors.newVirtualThreadPerTaskExecutor();
    /** Each session serializes its own native start/release; retired sessions never hold up a new one. */
    private static final class NativeSession {
        final ExecutorService commands = Executors.newSingleThreadExecutor(r -> {
            Thread thread = new Thread(r, "vlc-session");
            thread.setDaemon(true);
            return thread;
        });
        MediaPlayerFactory factory;
        final AtomicReference<EmbeddedMediaPlayer> player = new AtomicReference<>();
        MediaPlayerEventAdapter events;
        VlcImageViewVideoSurface surface;
        boolean admitted;
        Throwable initializationFailure;
        final AtomicBoolean playbackRequested = new AtomicBoolean();
    }

    // A broken native plugin cannot be forcibly interrupted safely. Bound abandoned sessions
    // rather than accumulating native players/threads indefinitely during repeated clicks.
    private final java.util.concurrent.Semaphore sessionSlots = new java.util.concurrent.Semaphore(4);
    private final AtomicReference<NativeSession> session = new AtomicReference<>(new NativeSession());
    private final ExecutorService nativeStopExecutor = Executors.newVirtualThreadPerTaskExecutor();
    private final java.util.function.Supplier<MediaPlayerFactory> nativeFactory;
    private final AtomicReference<EmbeddedMediaPlayer> mediaPlayer = new AtomicReference<>();
    private final AtomicReference<Future<?>> playbackTask = new AtomicReference<>();
    private volatile boolean playing;
    private volatile double desiredVolume = 50;
    private List<TrackOption> audioTracks = List.of();
    private int selectedAudioTrack = Integer.MIN_VALUE;
    private String videoCodec = "";
    private final AtomicBoolean timeUpdateQueued = new AtomicBoolean();
    private ImageView videoImageView;
    private int videoSourceWidth;
    private int videoSourceHeight;
    private String lastStreamInfoLabel = "";
    private final AtomicLong audioStateRequestVersion = new AtomicLong();
    private final AtomicLong mediaRequestVersion = new AtomicLong();

    public VlcVideoPlayer() {
        this(null);
    }

    VlcVideoPlayer(java.util.function.Supplier<MediaPlayerFactory> nativeFactory) {
        super();
        this.nativeFactory = nativeFactory;

        if (videoImageView == null) {
            videoImageView = new ImageView();
            videoImageView.setPreserveRatio(true);
            videoImageView.imageProperty().addListener((obs, oldImage, newImage) -> onRenderedImageChanged(newImage));
        }
        // Native discovery and initialization run off JavaFX, when playbackReady is requested.
    }

    /** Runs only on this session's command thread. Never hold playerLock across native code. */
    private void ensurePlayerInitialized(NativeSession target) {
        if (isDisposed.get() || session.get() != target) return;
        if (target.initializationFailure != null) throw new IllegalStateException("VLC initialization failed", target.initializationFailure);
        if (target.player.get() != null) return;
        if (!sessionSlots.tryAcquire()) {
            throw new IllegalStateException("Previous VLC sessions are still closing; retry playback shortly");
        }
        target.admitted = true;
        try {
            createNativePlayer(target);
            publishNativePlayer(target);
        } catch (RuntimeException failure) {
            handleInitializationFailure(target, failure);
            throw failure;
        } catch (LinkageError failure) {
            handleInitializationFailure(target, failure);
            Platform.runLater(() -> {
                if (session.get() == target && !isDisposed.get()) {
                    com.uiptv.player.MediaPlayerFactory.handleVlcInitFailure(failure);
                }
            });
            throw failure;
        }
    }

    private void handleInitializationFailure(NativeSession target, Throwable failure) {
        // Retirement will join any concurrent stop before releasing partially built resources.
        target.initializationFailure = failure;
        com.uiptv.util.AppLog.addErrorLog(VlcVideoPlayer.class, "VLC native initialization failed", failure);
    }

    private void createNativePlayer(NativeSession target) {
        target.factory = nativeFactory == null
                ? new MediaPlayerFactory(createVlcArgs().toArray(new String[0])) : nativeFactory.get();
        target.player.set(target.factory.mediaPlayers().newEmbeddedMediaPlayer());
        target.surface = new VlcImageViewVideoSurface(videoImageView);
        target.player.get().videoSurface().set(target.surface);
        target.player.get().controls().setRepeat(false);
        target.events = createMediaPlayerEvents();
        target.player.get().events().addMediaPlayerEventListener(target.events);
    }

    private void publishNativePlayer(NativeSession target) {
        synchronized (playerLock) {
            if (session.get() == target && !isDisposed.get()) {
                mediaPlayer.set(target.player.get());
            } else {
                target.surface.disable();
            }
        }
    }

    @Override
    public void showPlaybackError() {
        disposeMedia();
        super.showPlaybackError();
    }

    private List<String> createVlcArgs() {
        return buildVlcArgs(ConfigurationService.getInstance().read());
    }

    static List<String> buildVlcArgs(Configuration configuration) {
        List<String> vlcArgs = new ArrayList<>();
        ConfigurationService configurationService = ConfigurationService.getInstance();
        String networkCachingMs = normalizeCachingMs(configurationService, configuration, Configuration::getVlcNetworkCachingMs);
        String liveCachingMs = normalizeCachingMs(configurationService, configuration, Configuration::getVlcLiveCachingMs);
        boolean enableUserAgent = configuration == null || configuration.isEnableVlcHttpUserAgent();
        boolean enableForwardCookies = configuration == null || configuration.isEnableVlcHttpForwardCookies();

        addCachingArgs(vlcArgs, networkCachingMs, liveCachingMs);
        addVideoOutputArg(vlcArgs, configuration);
        addHardwareAccelerationArg(vlcArgs, configuration);
        addGeneralVlcArgs(vlcArgs, configuration);
        addHttpArgs(vlcArgs, enableUserAgent, enableForwardCookies);
        return vlcArgs;
    }

    private static String normalizeCachingMs(ConfigurationService configurationService, Configuration configuration, java.util.function.Function<Configuration, String> accessor) {
        return configurationService.normalizeVlcCachingMs(configuration == null ? null : accessor.apply(configuration));
    }

    private static void addCachingArgs(List<String> vlcArgs, String networkCachingMs, String liveCachingMs) {
        if (!networkCachingMs.isBlank()) {
            vlcArgs.add("--network-caching=" + networkCachingMs);
        }
        if (!liveCachingMs.isBlank()) {
            vlcArgs.add("--live-caching=" + liveCachingMs);
        }
    }

    private static void addVideoOutputArg(List<String> vlcArgs, Configuration configuration) {
        boolean enableVlcVout = configuration != null && configuration.getVlcVout() != null && !configuration.getVlcVout().isBlank();
        if (!enableVlcVout) {
            return;
        }
        // The embedded player is backed by vlcj's callback surface. A platform
        // window vout creates a second native rendering path and can retain
        // large GPU surfaces after stop. vmem is the VLC 3.x callback output.
        vlcArgs.add("--vout=vmem");
    }

    private static void addHardwareAccelerationArg(List<String> vlcArgs, Configuration configuration) {
        boolean enableVlcAvcodecHw = configuration != null && configuration.getVlcAvcodecHw() != null && !configuration.getVlcAvcodecHw().isBlank();
        if (!enableVlcAvcodecHw) {
            return;
        }
        // VLC 3.x accepts any/none here. The old d3d11va/videotoolbox values
        // belong to different option vocabularies and are rejected by VLC 3.x.
        vlcArgs.add("--avcodec-hw=any");
    }

    private static void addGeneralVlcArgs(List<String> vlcArgs, Configuration configuration) {
        if (configuration == null || configuration.isVlcNoVideoTitleShow()) {
            vlcArgs.add("--no-video-title-show");
        }
        if (configuration == null || configuration.isVlcQuiet()) {
            vlcArgs.add("--quiet");
        }
        if (configuration == null || configuration.isVlcHttpReconnect()) {
            vlcArgs.add("--http-reconnect");
        }
        if (configuration == null || configuration.isVlcAdaptiveUseAccess()) {
            vlcArgs.add("--adaptive-use-access");
        }
    }

    private static void addHttpArgs(List<String> vlcArgs, boolean enableUserAgent, boolean enableForwardCookies) {
        if (enableUserAgent) {
            vlcArgs.add("--http-user-agent=" + VLC_HTTP_USER_AGENT);
        }
        if (enableForwardCookies) {
            vlcArgs.add("--http-forward-cookies");
        }
    }

    private void withCurrentPlayer(java.util.function.Consumer<EmbeddedMediaPlayer> action) {
        NativeSession target = session.get();
        try {
            target.commands.submit(() -> {
                if (session.get() != target || isDisposed.get() || target.player.get() == null) return;
                try {
                    action.accept(target.player.get());
                } catch (Exception failure) {
                    com.uiptv.util.AppLog.addErrorLog(VlcVideoPlayer.class, "VLC session command failed", failure);
                }
            });
        } catch (RejectedExecutionException _) {
            // A retired session owns its final cleanup.
        }
    }

    private void applyDesiredAudioState() {
        withCurrentPlayer(this::applyDesiredAudioState);
    }

    private void applyDesiredAudioState(EmbeddedMediaPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.audio().setMute(isMuted);
            int vlcVolume = (int) Math.round(desiredVolume * 2.0);
            if (vlcVolume < 0) vlcVolume = 0;
            if (vlcVolume > 200) vlcVolume = 200;
            player.audio().setVolume(vlcVolume);
        } catch (Exception _) {
            // Best-effort: audio state can fail transiently while VLC is still starting.
        }
    }

    private long markAudioStateSyncRequested() {
        return audioStateRequestVersion.incrementAndGet();
    }

    private void scheduleAudioStateRetry(long requestVersion, int delayMs) {
        Platform.runLater(() -> {
            PauseTransition retry = new PauseTransition(Duration.millis(delayMs));
            retry.setOnFinished(_ -> {
                if (isDisposed.get() || requestVersion != audioStateRequestVersion.get()) {
                    return;
                }
                applyDesiredAudioState();
            });
            retry.play();
        });
    }

    private void scheduleAudioStateStartupSync(long requestVersion) {
        scheduleAudioStateRetry(requestVersion, 150);
        scheduleAudioStateRetry(requestVersion, 450);
        scheduleAudioStateRetry(requestVersion, 1000);
    }

    private boolean isCurrentPlayer(MediaPlayer player) {
        return !isDisposed.get() && player != null && mediaPlayer.get() == player;
    }

    private MediaPlayerEventAdapter createMediaPlayerEvents() {
        return new MediaPlayerEventAdapter() {
            @Override
            public void mediaChanged(MediaPlayer mp, MediaRef mediaRef) {
                handleMediaChanged(mp);
            }

            @Override
            public void playing(MediaPlayer mp) {
                handlePlaying(mp);
            }

            @Override
            public void paused(MediaPlayer mp) {
                handlePaused(mp);
            }

            @Override
            public void positionChanged(MediaPlayer mp, float newPosition) {
                handlePositionChanged(mp, newPosition);
            }

            @Override
            public void timeChanged(MediaPlayer mp, long newTime) {
                handleTimeChanged(mp, newTime);
            }

            @Override
            public void finished(MediaPlayer mp) {
                handleFinished(mp);
            }

            @Override
            public void stopped(MediaPlayer mp) {
                handleStopped(mp);
            }

            @Override
            public void error(MediaPlayer mp) {
                handleError(mp);
            }

            @Override
            public void elementaryStreamAdded(MediaPlayer mp, TrackType type, int id) {
                handleElementaryStreamEvent(mp, type);
            }

            @Override
            public void elementaryStreamDeleted(MediaPlayer mp, TrackType type, int id) {
                handleElementaryStreamEvent(mp, type);
            }

            @Override
            public void elementaryStreamSelected(MediaPlayer mp, TrackType type, int id) {
                handleElementaryStreamEvent(mp, type);
            }
        };
    }

    private void handleMediaChanged(MediaPlayer mp) {
        if (!isCurrentPlayer(mp)) return;
        withCurrentPlayer(player -> {
            String uri = player.media().info() == null ? "" : player.media().info().mrl();
            if (uri != null && !uri.isBlank()) Platform.runLater(() -> {
                if (isCurrentPlayer(mp)) updateBingeWatchEpisodeFromActiveMedia(uri);
            });
        });
    }

    private void refreshNativeMetadata(MediaPlayer mp) {
        withCurrentPlayer(player -> {
            List<TrackOption> tracks = player.audio().trackDescriptions().stream()
                    .map(track -> new TrackOption(track.id(), normalizeTrackLabel(track.description(), "Audio")))
                    .toList();
            int selected = player.audio().track();
            List<VideoTrackInfo> video = player.media().info() == null ? List.of() : player.media().info().videoTracks();
            String codec = video == null || video.isEmpty() ? "" : video.getFirst().codecName();
            Platform.runLater(() -> {
                if (!isCurrentPlayer(mp)) return;
                audioTracks = List.copyOf(tracks);
                selectedAudioTrack = selected;
                videoCodec = codec;
                refreshTrackMenus();
                refreshRenderedImageStreamInfo();
            });
        });
    }

    private void handlePlaying(MediaPlayer mp) {
        if (!isCurrentPlayer(mp)) {
            return;
        }
        long requestVersion = audioStateRequestVersion.get();
        EmbeddedMediaPlayer currentPlayer = mediaPlayer.get();
        if (currentPlayer == null || currentPlayer != mp) {
            return;
        }
        Platform.runLater(() -> {
            if (!isCurrentPlayer(mp)) {
                return;
            }
            playing = true;
            playbackStarted();
            refreshNativeMetadata(mp);
            errorLabel.setVisible(false);
            retryCount = 0;
            applyDesiredAudioState();
            scheduleAudioStateStartupSync(requestVersion);
            loadingSpinner.setVisible(false);
            btnPlayPause.setGraphic(pauseIcon);
            updateVideoSize();
            refreshTrackMenus();
        });
    }

    private void handlePaused(MediaPlayer mp) {
        if (!isCurrentPlayer(mp)) {
            return;
        }
        Platform.runLater(() -> {
            if (isCurrentPlayer(mp)) {
                playing = false;
                btnPlayPause.setGraphic(playIcon);
            }
        });
    }

    private void handlePositionChanged(MediaPlayer mp, float newPosition) {
        if (!isCurrentPlayer(mp) || isUserSeeking) {
            return;
        }
        Platform.runLater(() -> {
            if (isCurrentPlayer(mp) && !timeSlider.isDisable()) {
                timeSlider.setValue(newPosition);
            }
        });
    }

    private void handleTimeChanged(MediaPlayer mp, long newTime) {
        if (!isCurrentPlayer(mp) || !timeUpdateQueued.compareAndSet(false, true)) return;
        withCurrentPlayer(player -> {
            try {
                long totalTime = player.status().length();
                boolean seekable = player.status().isSeekable();
                Platform.runLater(() -> {
                    if (isCurrentPlayer(mp)) {
                        updatePlaybackTimeUi(newTime, totalTime, seekable);
                        refreshRenderedImageStreamInfo();
                    }
                });
            } finally {
                timeUpdateQueued.set(false);
            }
        });
    }

    private void handleFinished(MediaPlayer mp) {
        if (!isCurrentPlayer(mp)) {
            return;
        }
        Platform.runLater(() -> {
            if (!isCurrentPlayer(mp)) {
                return;
            }
            btnPlayPause.setGraphic(playIcon);
            if (isRepeating && isRetrying.get()) {
                handleRepeat();
            }
        });
    }

    private void handleStopped(MediaPlayer mp) {
        if (!isCurrentPlayer(mp)) {
            return;
        }
        Platform.runLater(() -> {
            if (!isCurrentPlayer(mp)) {
                return;
            }
            playing = false;
            btnPlayPause.setGraphic(playIcon);
            timeSlider.setValue(0);
            timeSlider.setDisable(false);
            timeLabel.setText(I18n.tr("auto00000000"));
            loadingSpinner.setVisible(false);
            videoSourceWidth = 0;
            videoSourceHeight = 0;
            lastStreamInfoLabel = "";
        });
    }

    private void handleError(MediaPlayer mp) {
        if (!isCurrentPlayer(mp)) {
            return;
        }
        Platform.runLater(() -> {
            if (!isCurrentPlayer(mp)) {
                return;
            }
            playbackStarted();
            com.uiptv.util.AppLog.addErrorLog(VlcVideoPlayer.class, "VLC media error request=" + mediaRequestVersion.get());
            showPlaybackError();
            if (isRepeating && isRetrying.get()) {
                handleRepeat();
            }
        });
    }

    private void handleElementaryStreamEvent(MediaPlayer mp, TrackType type) {
        if (type != TrackType.AUDIO || !isCurrentPlayer(mp)) {
            return;
        }
        long requestVersion = audioStateRequestVersion.get();
        EmbeddedMediaPlayer currentPlayer = mediaPlayer.get();
        if (currentPlayer == null || currentPlayer != mp) {
            return;
        }
        Platform.runLater(() -> {
            if (!isCurrentPlayer(mp)) {
                return;
            }
            refreshNativeMetadata(mp);
            applyDesiredAudioState();
            scheduleAudioStateRetry(requestVersion, 250);
        });
    }

    @Override
    protected Node getVideoView() {
        if (videoImageView == null) {
            videoImageView = new ImageView();
            videoImageView.setPreserveRatio(true);
            videoImageView.imageProperty().addListener((obs, oldImage, newImage) -> onRenderedImageChanged(newImage));
        }
        return videoImageView;
    }

    @Override
    protected void playMedia(String uri) {
        NativeSession currentSession = session.get();
        if (currentSession.playbackRequested.get()) disposeMedia();
        currentSession = session.get();
        currentSession.playbackRequested.set(true);
        loadingSpinner.setVisible(true);
        long playbackRequestVersion = mediaRequestVersion.incrementAndGet();
        NativeSession target = currentSession;
        cancelPreviousPlaybackTask();
        playbackTask.set(playExecutor.submit(() -> prepareAndQueuePlayback(uri, target, playbackRequestVersion)));
    }

    private void cancelPreviousPlaybackTask() {
        Future<?> previousTask = playbackTask.getAndSet(null);
        if (previousTask != null) previousTask.cancel(true);
    }

    private void prepareAndQueuePlayback(String uri, NativeSession target, long requestVersion) {
        try {
            if (playbackCancelled(requestVersion)) return;
            java.util.concurrent.CompletableFuture.runAsync(() -> ensurePlayerInitialized(target), target.commands).get();
            if (playbackCancelled(requestVersion)) return;
            resetPlaybackMetadata();
            String playUri = preparePlaybackUri(uri, requestVersion);
            if (playbackCancelled(requestVersion)) return;
            target.commands.submit(() -> startMedia(target, playUri, requestVersion));
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception | LinkageError failure) {
            reportPlaybackFailure(requestVersion, failure);
        }
    }

    private boolean playbackCancelled(long requestVersion) {
        return isDisposed.get() || requestVersion != mediaRequestVersion.get() || Thread.currentThread().isInterrupted();
    }

    private void resetPlaybackMetadata() {
        videoSourceWidth = 0;
        videoSourceHeight = 0;
        lastStreamInfoLabel = "";
    }

    private String preparePlaybackUri(String uri, long requestVersion) {
        long started = System.nanoTime();
        String playUri = sanitizeUriForLibVlc(resolveHlsPlaylistChain(uri));
        com.uiptv.util.AppLog.addInfoLog(VlcVideoPlayer.class,
                "VLC playlist preparation request=" + requestVersion
                        + " elapsed=" + (System.nanoTime() - started) / 1_000_000 + "ms");
        return playUri;
    }

    private void startMedia(NativeSession target, String resolvedUri, long requestVersion) {
        if (isDisposed.get() || requestVersion != mediaRequestVersion.get()) return;
        long started = System.nanoTime();
        try {
            ensurePlayerInitialized(target);
            EmbeddedMediaPlayer player = target.player.get();
            if (player == null || isDisposed.get() || requestVersion != mediaRequestVersion.get()) return;
            long audioRequestVersion = markAudioStateSyncRequested();
            applyDesiredAudioState(player);
            boolean accepted = playResolvedMedia(player, resolvedUri);
            com.uiptv.util.AppLog.addInfoLog(VlcVideoPlayer.class,
                    "VLC start request=" + requestVersion + " accepted=" + accepted
                            + " elapsed=" + (System.nanoTime() - started) / 1_000_000 + "ms");
            if (!accepted) throw new IllegalStateException("libVLC rejected media startup");
            if (requestVersion == mediaRequestVersion.get() && !isDisposed.get() && mediaPlayer.get() == player) {
                scheduleAudioStateStartupSync(audioRequestVersion);
            }
        } catch (Exception | LinkageError failure) {
            reportPlaybackFailure(requestVersion, failure);
        }
    }

    private boolean playResolvedMedia(EmbeddedMediaPlayer player, String uri) {
        if (ConfigurationService.getInstance().isVlcHttpUserAgentEnabled()) {
            return player.media().play(uri, ":http-user-agent=" + VLC_HTTP_USER_AGENT);
        }
        return player.media().play(uri);
    }

    /**
     * Surfaces a failure that happened before libVLC ever saw the media, mirroring
     * {@link #handleError()} so the user gets the same message and a logged cause.
     */
    private void reportPlaybackFailure(long playbackRequestVersion, Throwable t) {
        if (playbackRequestVersion != mediaRequestVersion.get() || isDisposed.get()) {
            return;
        }
        com.uiptv.util.AppLog.addErrorLog(VlcVideoPlayer.class,
                "VlcVideoPlayer: playback could not be started request=" + playbackRequestVersion, t);
        Platform.runLater(() -> {
            if (isDisposed.get() || playbackRequestVersion != mediaRequestVersion.get()) {
                return;
            }
            showPlaybackError();
        });
    }

    // Sanitize URIs before handing to libvlc. Use Apache URIBuilder to build a properly encoded URI.
    private String sanitizeUriForLibVlc(String uri) {
        if (uri == null) return null;
        try {
            URIBuilder b = new URIBuilder(uri);
            return b.build().toString();
        } catch (URISyntaxException | IllegalArgumentException _) {
            // Fallback to conservative replacements if parsing/encoding fails
            try {
                return uri.replace("{", "%7B").replace("}", "%7D").replace(" ", "%20").replace("`", "%60");
            } catch (Exception _) {
                return uri;
            }
        }
    }

    @Override
    protected void stopMedia() {
        disposeMedia();
    }

    @Override
    protected void disposeMedia() {
        mediaRequestVersion.incrementAndGet();
        audioStateRequestVersion.incrementAndGet();
        Future<?> task = playbackTask.getAndSet(null);
        if (task != null) {
            task.cancel(true);
        }
        playing = false;
        timeUpdateQueued.set(false);
        audioTracks = List.of();
        selectedAudioTrack = Integer.MIN_VALUE;
        videoCodec = "";
        NativeSession retired;
        synchronized (playerLock) {
            retired = session.getAndSet(new NativeSession());
            if (retired.surface != null) retired.surface.disable();
            mediaPlayer.set(null);
        }
        // Signal stop independently of a blocked start, but join it OFF JavaFX before release.
        // The queued final stop covers stop arriving before media.play attaches its media.
        java.util.concurrent.CompletableFuture<Void> stop = java.util.concurrent.CompletableFuture.runAsync(
                () -> stopNativePlayback(retired.player.get()), nativeStopExecutor);
        retired.commands.submit(() -> {
            try {
                stop.join();
                detachNativeRendering(retired.player.get(), retired.events);
                releaseSession(retired);
            } finally {
                retired.commands.shutdown();
            }
        });
        clearVideoImage();
        videoSourceWidth = 0;
        videoSourceHeight = 0;
        lastStreamInfoLabel = "";
    }

    private void detachNativeRendering(EmbeddedMediaPlayer player, MediaPlayerEventAdapter events) {
        if (player == null) {
            return;
        }
        if (events != null) {
            try {
                player.events().removeMediaPlayerEventListener(events);
            } catch (Exception _) {
                // Best-effort: teardown must continue even if VLC is already closing.
            }
        }
        try {
            player.videoSurface().set(null);
        } catch (Exception _) {
            // Best-effort: the surface can already be detached during native teardown.
        }
    }

    /**
     * JavaFX surface with an explicit stop gate, one reusable image and one
     * pending pulse at most.
     *
     * Do not expose vlcj's native callback buffer through a PixelBuffer. A
     * PixelBuffer retains that direct buffer and its JavaFX texture after libVLC
     * has replaced or freed the native buffer. Instead, copy only the latest
     * frame into one bounded byte array and upload it into one reusable image.
     */
    private static final class VlcImageViewVideoSurface extends CallbackVideoSurface {
        private final RenderGate gate;

        private VlcImageViewVideoSurface(ImageView imageView) {
            this(imageView, new RenderGate());
        }

        private VlcImageViewVideoSurface(ImageView imageView, RenderGate gate) {
            super(new BufferFormatCallback() {
                @Override public BufferFormat getBufferFormat(int width, int height) {
                    gate.sourceWidth = width;
                    gate.sourceHeight = height;
                    return new RV32BufferFormat(width, height);
                }
                @Override public void newFormatSize(int bufferWidth, int bufferHeight, int displayWidth, int displayHeight) {
                    // Frame dimensions are read from getBufferFormat; no separate resize action is needed.
                }
                @Override public void allocatedBuffers(ByteBuffer[] buffers) {
                    if (!gate.enabled.get() || buffers == null || buffers.length == 0) return;
                    WritableImage next;
                    try {
                        next = gate.allocateFrame(gate.sourceWidth, gate.sourceHeight);
                    } catch (IllegalArgumentException | ArithmeticException _) {
                        gate.disable();
                        return;
                    }
                    Platform.runLater(() -> {
                        if (gate.enabled.get() && gate.image.get() == next) {
                            imageView.setImage(next);
                        }
                    });
                }
            }, new RenderCallback() {
                @Override public void lock(MediaPlayer mediaPlayer) {
                    // Frame copying is guarded by RenderGate rather than an external surface lock.
                }
                @Override public void display(MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers,
                                              BufferFormat bufferFormat, int displayWidth, int displayHeight) {
                    if (!gate.enabled.get()) {
                        return;
                    }
                    if (!gate.copyFrame(nativeBuffers, bufferFormat)
                            || !gate.updateQueued.compareAndSet(false, true)) {
                        return;
                    }
                    Platform.runLater(() -> {
                        try {
                            gate.publishLatestFrame();
                        } finally {
                            gate.updateQueued.set(false);
                        }
                    });
                }
                @Override public void unlock(MediaPlayer mediaPlayer) {
                    // Frame copying is guarded by RenderGate rather than an external surface lock.
                }
            }, true, VideoSurfaceAdapters.getVideoSurfaceAdapter());
            this.gate = gate;
        }

        private void disable() {
            gate.disable();
        }

        private static final class RenderGate {
            private final AtomicBoolean enabled = new AtomicBoolean(true);
            private final AtomicBoolean updateQueued = new AtomicBoolean();
            private final Object frameLock = new Object();
            private final AtomicReference<byte[]> latestFrame = new AtomicReference<>();
            private final AtomicReference<WritableImage> image = new AtomicReference<>();
            private volatile int sourceWidth;
            private volatile int sourceHeight;

            private WritableImage allocateFrame(int width, int height) {
                if (width <= 0 || height <= 0) {
                    throw new IllegalArgumentException("Invalid VLC frame dimensions");
                }
                int frameBytes = Math.multiplyExact(Math.multiplyExact(width, height), 4);
                WritableImage next = new WritableImage(width, height);
                synchronized (frameLock) {
                    latestFrame.set(new byte[frameBytes]);
                    image.set(next);
                    sourceWidth = width;
                    sourceHeight = height;
                }
                return next;
            }

            private boolean copyFrame(ByteBuffer[] nativeBuffers, BufferFormat bufferFormat) {
                if (!enabled.get() || nativeBuffers == null || nativeBuffers.length == 0
                        || nativeBuffers[0] == null || bufferFormat == null) {
                    return false;
                }
                int width = sourceWidth;
                int height = sourceHeight;
                int rowBytes = width * 4;
                byte[] target = latestFrame.get();
                int[] pitches = bufferFormat.getPitches();
                if (target == null || width <= 0 || height <= 0 || pitches == null || pitches.length == 0
                        || target.length < rowBytes * height || pitches[0] < rowBytes) {
                    return false;
                }
                ByteBuffer source = nativeBuffers[0].duplicate();
                source.clear();
                int pitch = pitches[0];
                synchronized (frameLock) {
                    if (!enabled.get() || target != latestFrame.get()) {
                        return false;
                    }
                    for (int row = 0; row < height; row++) {
                        int targetOffset = row * rowBytes;
                        if (source.remaining() < pitch) {
                            return false;
                        }
                        source.get(target, targetOffset, rowBytes);
                        if (pitch > rowBytes) {
                            source.position(source.position() + pitch - rowBytes);
                        }
                    }
                }
                return true;
            }

            private void publishLatestFrame() {
                if (!enabled.get()) {
                    return;
                }
                WritableImage currentImage = image.get();
                byte[] frame = latestFrame.get();
                if (currentImage == null || frame == null) {
                    return;
                }
                synchronized (frameLock) {
                    if (enabled.get() && currentImage == image.get() && frame == latestFrame.get()) {
                        currentImage.getPixelWriter().setPixels(0, 0, sourceWidth, sourceHeight,
                                PixelFormat.getByteBgraPreInstance(), frame, 0, sourceWidth * 4);
                    }
                }
            }

            private void disable() {
                enabled.set(false);
                synchronized (frameLock) {
                    latestFrame.set(null);
                    image.set(null);
                }
                updateQueued.set(false);
            }
        }
    }

    private void stopNativePlayback(EmbeddedMediaPlayer player) {
        if (player == null) {
            return;
        }
        try {
            player.controls().stop();
        } catch (Exception failure) {
            com.uiptv.util.AppLog.addErrorLog(VlcVideoPlayer.class, "VLC stop request failed", failure);
        }
    }

    @Override
    public java.util.concurrent.CompletionStage<Void> playbackReady() {
        NativeSession target = session.get();
        return java.util.concurrent.CompletableFuture.runAsync(() -> ensurePlayerInitialized(target), target.commands);
    }

    private void releaseSession(NativeSession target) {
        if (target.surface != null) target.surface.disable();
        releaseNativePlayer(target.player.get(), target.factory);
        target.player.set(null);
        target.factory = null;
        if (target.admitted) {
            target.admitted = false;
            sessionSlots.release();
        }
    }

    private void releaseNativePlayer(EmbeddedMediaPlayer playerToCleanup, MediaPlayerFactory factoryToCleanup) {
        if (playerToCleanup != null) {
            stopNativePlayback(playerToCleanup);
            try {
                playerToCleanup.videoSurface().set(null);
            } catch (Exception _) {
                // Best-effort: surface can already be released during teardown.
            }
            try {
                playerToCleanup.release();
            } catch (Exception _) {
                // Best-effort: native VLC release can fail after partial teardown.
            }
        }
        if (factoryToCleanup != null) {
            try {
                factoryToCleanup.release();
            } catch (Exception _) {
                // Best-effort: factory cleanup should not block UI disposal.
            }
        }
    }

    private void clearVideoImage() {
        if (Platform.isFxApplicationThread()) {
            videoImageView.setImage(null);
        } else {
            Platform.runLater(() -> videoImageView.setImage(null));
        }
    }

    @Override
    public void disposePlayer() {
        try {
            super.disposePlayer();
        } finally {
            playExecutor.shutdownNow();
            nativeStopExecutor.shutdown();
            session.get().commands.shutdown(); // Retired sessions drain their own cleanup queues.
        }
    }

    @Override
    protected void setVolume(double volume) {
        desiredVolume = volume;
        markAudioStateSyncRequested();
        applyDesiredAudioState();
    }

    @Override
    protected void setMute(boolean mute) {
        markAudioStateSyncRequested();
        withCurrentPlayer(player -> player.audio().setMute(mute));
    }

    @Override
    protected void seek(float position) {
        withCurrentPlayer(player -> player.controls().setPosition(position));
    }

    @Override
    protected void seekBySeconds(int deltaSeconds) {
        withCurrentPlayer(player -> {
            long currentTime = Math.max(0L, player.status().time());
            long totalTime = player.status().length();
            long targetTime = currentTime + deltaSeconds * 1000L;
            player.controls().setTime(totalTime > 0 ? Math.clamp(targetTime, 0L, totalTime) : Math.max(0L, targetTime));
        });
    }

    @Override
    protected boolean isPlaying() {
        return playing;
    }

    @Override
    protected void pauseMedia() {
        withCurrentPlayer(player -> player.controls().pause());
    }

    @Override
    protected void resumeMedia() {
        withCurrentPlayer(player -> player.controls().play());
    }

    @Override
    protected void updateVideoSize() {
        if (isPip()) {
            return;
        }
        if (playerContainer.getWidth() <= 0 || playerContainer.getHeight() <= 0) {
            return;
        }

        double containerWidth = playerContainer.getWidth();
        double containerHeight = playerContainer.getHeight();
        resetVideoImageView(containerWidth, containerHeight);
        captureRenderedImageDimensions();
        if (aspectRatioMode == ASPECT_RATIO_FILL) {
            applyFillZoom(containerWidth, containerHeight);
        }
        if (!refreshRenderedImageStreamInfo() && videoSourceWidth > 0 && videoSourceHeight > 0) {
            updateStreamInfo(videoSourceWidth, videoSourceHeight);
        }
    }

    private void resetVideoImageView(double containerWidth, double containerHeight) {
        videoImageView.fitWidthProperty().unbind();
        videoImageView.fitHeightProperty().unbind();
        videoImageView.setScaleX(1.0);
        videoImageView.setScaleY(1.0);
        videoImageView.setFitWidth(containerWidth);
        videoImageView.setFitHeight(containerHeight);
        if (aspectRatioMode == ASPECT_RATIO_STRETCH) {
            videoImageView.setPreserveRatio(false);
            return;
        }
        videoImageView.setPreserveRatio(true);
        if (aspectRatioMode == ASPECT_RATIO_FILL) {
            applyFillZoom(containerWidth, containerHeight);
        }
    }

    private void applyFillZoom(double containerWidth, double containerHeight) {
        if (containerWidth <= 0 || containerHeight <= 0) {
            return;
        }
        if (videoSourceWidth <= 0 || videoSourceHeight <= 0) {
            captureRenderedImageDimensions();
        }
        if (videoSourceWidth <= 0 || videoSourceHeight <= 0) {
            return;
        }

        double sourceRatio = (double) videoSourceWidth / videoSourceHeight;
        double containerRatio = containerWidth / containerHeight;
        double fittedWidth = containerWidth;
        double fittedHeight = containerHeight;
        if (sourceRatio > containerRatio) {
            fittedHeight = containerWidth / sourceRatio;
        } else {
            fittedWidth = containerHeight * sourceRatio;
        }
        if (fittedWidth <= 0 || fittedHeight <= 0) {
            return;
        }

        double zoom = Math.max(containerWidth / fittedWidth, containerHeight / fittedHeight);
        if (Double.isFinite(zoom) && zoom > 1.0) {
            videoImageView.setScaleX(zoom);
            videoImageView.setScaleY(zoom);
        }
    }

    protected void updateStreamInfo(int width, int height) {
        ResolutionDisplayUtil.ResolutionDisplay resolution = ResolutionDisplayUtil.normalize(width, height);
        String newLabel = String.format("%n%s%s (vlc)", resolution.shortText(), formatCodecSuffix(videoCodec));
        if (!newLabel.equals(lastStreamInfoLabel)) {
            lastStreamInfoLabel = newLabel;
            streamInfoText.setText(newLabel);
        }
    }

    private String formatCodecSuffix(String codec) {
        return codec == null || codec.isBlank() ? "" : " " + codec;
    }

    private boolean refreshRenderedImageStreamInfo() {
        if (!captureRenderedImageDimensions()) {
            return false;
        }
        updateStreamInfo(videoSourceWidth, videoSourceHeight);
        return true;
    }

    private void onRenderedImageChanged(Image image) {
        if (captureRenderedImageDimensions(image)) {
            updateStreamInfo(videoSourceWidth, videoSourceHeight);
            if (aspectRatioMode == ASPECT_RATIO_FILL) {
                updateVideoSize();
            }
        }
    }

    private boolean captureRenderedImageDimensions() {
        return captureRenderedImageDimensions(videoImageView != null ? videoImageView.getImage() : null);
    }

    private boolean captureRenderedImageDimensions(Image image) {
        if (image == null) {
            return false;
        }
        int renderedWidth = (int) Math.round(image.getWidth());
        int renderedHeight = (int) Math.round(image.getHeight());
        if (renderedWidth <= 0 || renderedHeight <= 0) {
            return false;
        }
        videoSourceWidth = renderedWidth;
        videoSourceHeight = renderedHeight;
        return true;
    }

    @Override
    protected boolean supportsTrackSelection() {
        return true;
    }

    @Override
    protected boolean supportsSubtitleTrackSelection() {
        return false;
    }

    @Override
    protected List<TrackOption> getAudioTrackOptions() {
        return audioTracks;
    }

    @Override
    protected int getSelectedAudioTrackId() {
        return selectedAudioTrack;
    }

    @Override
    protected void selectAudioTrack(int trackId) {
        withCurrentPlayer(player -> {
            player.audio().setTrack(trackId);
            refreshNativeMetadata(player);
        });
    }

    private String normalizeTrackLabel(String label, String fallbackPrefix) {
        if (label == null || label.trim().isEmpty()) {
            return fallbackPrefix;
        }
        return label.trim();
    }

    @Override
    public PlayerType getType() {
        return PlayerType.VLC;
    }

    protected void onPlaybackStarted() {
        retryCount = 0;
        loadingSpinner.setVisible(false);
    }
}
