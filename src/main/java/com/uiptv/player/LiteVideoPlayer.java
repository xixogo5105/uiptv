package com.uiptv.player;

import com.uiptv.model.Configuration;
import com.uiptv.service.BingeWatchService;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.LitePlayerFfmpegService;
import com.uiptv.util.I18n;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.media.*;
import javafx.util.Duration;

import static com.uiptv.util.StringUtils.isBlank;

public class LiteVideoPlayer extends BaseVideoPlayer {
    private static final String PLAYBACK_MODE_LITE_DIRECT = "Lite direct";

    private MediaPlayer mediaPlayer;
    private MediaView mediaView; // Removed final and initializer
    private final ChangeListener<Duration> progressListener;
    private final ChangeListener<MediaPlayer.Status> statusListener;
    private volatile boolean usingFfmpegFallback;
    private volatile boolean attemptedCompatibilityFallback;
    private volatile String currentPlaybackModeLabel = PLAYBACK_MODE_LITE_DIRECT;
    private volatile long estimatedTotalDurationMs;
    private volatile long playbackStartOffsetMs;
    private volatile long playbackWallClockStartedAtMs;
    private volatile long lastObservedPlaybackTimeMs;
    private final PauseTransition compatibilityFallbackTimer = new PauseTransition(Duration.seconds(6));

    public LiteVideoPlayer() {
        super(); // Calls buildUI -> getVideoView

        setMute(isMuted);
        compatibilityFallbackTimer.setOnFinished(e -> triggerCompatibilityFallbackIfNeeded());
        statusListener = (obs, oldStatus, newStatus) -> Platform.runLater(() -> handleStatusChange(newStatus));
        progressListener = (obs, oldTime, newTime) -> {
            if (!isUserSeeking) {
                Platform.runLater(this::updateTimeLabel);
            }
        };
    }

    @Override
    protected Node getVideoView() {
        if (mediaView == null) {
            mediaView = new MediaView();
            mediaView.setPreserveRatio(true);
        }
        return mediaView;
    }

    @Override
    protected void playMedia(String uri) {
        attemptedCompatibilityFallback = false;
        if (isBingeWatchPlayback()) {
            startBingeWatchPlayback(false, 0L);
            return;
        }
        startPlayback(uri, false, 0L);
    }

    private void startPlayback(String uri, boolean forceCompatibilityFallback, long startOffsetMs) {
        if (isBlank(uri)) {
            stop();
            return;
        }
        compatibilityFallbackTimer.stop();
        this.currentMediaUri = uri;
        releaseExistingPlayback();

        new Thread(() -> {
            try {
                LitePlayerFfmpegService.PreparedPlayback preparedPlayback = resolvePlayback(currentMediaUri, forceCompatibilityFallback, startOffsetMs);
                applyPreparedPlayback(preparedPlayback, forceCompatibilityFallback);
                Platform.runLater(() -> createAndPlayMediaPlayer(preparedPlayback.playbackUrl()));
            } catch (Exception e) {
                handlePlaybackError("Error resolving media URL.", e);
            }
        }).start();
    }

    private void startBingeWatchPlayback(boolean forceCompatibilityFallback, long startOffsetMs) {
        compatibilityFallbackTimer.stop();
        releaseExistingPlayback();

        new Thread(() -> {
            try {
                String episodeId = resolveActiveBingeWatchEpisodeId();
                if (isBlank(episodeId)) {
                    throw new IllegalStateException("No binge watch episode is selected.");
                }
                BingeWatchService.ResolvedEpisode resolvedEpisode = BingeWatchService.getInstance()
                        .resolveEpisode(activeBingeWatchToken, episodeId);
                if (resolvedEpisode == null || isBlank(resolvedEpisode.url())) {
                    throw new IllegalStateException("Unable to resolve binge watch episode URL.");
                }
                activeBingeWatchEpisodeId = episodeId;
                this.currentMediaUri = resolvedEpisode.url();
                LitePlayerFfmpegService.PreparedPlayback preparedPlayback = resolvePlayback(currentMediaUri, forceCompatibilityFallback, startOffsetMs);
                applyPreparedPlayback(preparedPlayback, forceCompatibilityFallback);
                Platform.runLater(() -> createAndPlayMediaPlayer(preparedPlayback.playbackUrl()));
            } catch (Exception e) {
                handlePlaybackError("Error resolving binge watch episode URL.", e);
            }
        }).start();
    }

    @Override
    protected void stopMedia() {
        if (mediaPlayer != null) {
            safeStopPlayer(mediaPlayer);
            mediaView.setMediaPlayer(null);
            mediaPlayer = null;
        }
        if (usingFfmpegFallback) {
            LitePlayerFfmpegService.getInstance().stopPlayback();
            usingFfmpegFallback = false;
        }
        currentPlaybackModeLabel = PLAYBACK_MODE_LITE_DIRECT;
        estimatedTotalDurationMs = 0L;
        playbackStartOffsetMs = 0L;
        playbackWallClockStartedAtMs = 0L;
        lastObservedPlaybackTimeMs = 0L;
        compatibilityFallbackTimer.stop();
    }

    @Override
    protected void disposeMedia() {
        if (mediaPlayer != null) {
            safeDisposePlayer(mediaPlayer);
            mediaPlayer = null;
        }
        if (usingFfmpegFallback) {
            LitePlayerFfmpegService.getInstance().stopPlayback();
            usingFfmpegFallback = false;
        }
        currentPlaybackModeLabel = PLAYBACK_MODE_LITE_DIRECT;
        estimatedTotalDurationMs = 0L;
        playbackStartOffsetMs = 0L;
        playbackWallClockStartedAtMs = 0L;
        lastObservedPlaybackTimeMs = 0L;
        compatibilityFallbackTimer.stop();
    }

    @Override
    protected void setVolume(double volume) {
        if (mediaPlayer != null) {
            mediaPlayer.setVolume(volume / 100.0);
        }
    }

    @Override
    protected void setMute(boolean mute) {
        if (mediaPlayer != null) {
            mediaPlayer.setMute(mute);
        }
    }

    @Override
    protected void seek(float position) {
        if (usingFfmpegFallback) {
            return;
        }
        if (tryManagedSeekByPosition(position)) {
            return;
        }
        if (mediaPlayer != null) {
            Duration total = safeTotalDuration(mediaPlayer);
            if (total != null && total.greaterThan(Duration.ZERO) && !total.isIndefinite()) {
                safeSeekPlayer(mediaPlayer, total.multiply(position));
            }
        }
    }

    @Override
    protected void seekBySeconds(int deltaSeconds) {
        if (usingFfmpegFallback) {
            return;
        }
        if (tryManagedSeekByDelta(deltaSeconds)) {
            return;
        }
        if (mediaPlayer == null) {
            return;
        }
        Duration current = safeCurrentTime(mediaPlayer);
        if (current == null) {
            return;
        }
        Duration target = current.add(Duration.seconds(deltaSeconds));
        if (target.lessThan(Duration.ZERO)) {
            target = Duration.ZERO;
        }
        Duration total = safeTotalDuration(mediaPlayer);
        if (total != null && total.greaterThan(Duration.ZERO) && !total.isIndefinite() && target.greaterThan(total)) {
            target = total;
        }
        safeSeekPlayer(mediaPlayer, target);
    }

    @Override
    protected boolean isPlaying() {
        return mediaPlayer != null && safeStatus(mediaPlayer) == MediaPlayer.Status.PLAYING;
    }

    @Override
    protected void pauseMedia() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.pause();
            } catch (RuntimeException _) {
                // Ignore late pause requests while the JavaFX backend is swapping players.
            }
        }
    }

    @Override
    protected void resumeMedia() {
        if (mediaPlayer != null) {
            try {
                mediaPlayer.play();
            } catch (RuntimeException _) {
                // Ignore late play requests while the JavaFX backend is swapping players.
            }
        }
    }

    @Override
    protected void updateVideoSize() {
        if (playerContainer.getWidth() <= 0 || playerContainer.getHeight() <= 0) {
            return;
        }

        mediaView.fitWidthProperty().unbind();
        mediaView.fitHeightProperty().unbind();

        double containerWidth = playerContainer.getWidth();
        double containerHeight = playerContainer.getHeight();
        mediaView.setScaleX(1.0);
        mediaView.setScaleY(1.0);

        if (aspectRatioMode == ASPECT_RATIO_STRETCH) { // Stretch to Fill
            mediaView.setFitWidth(containerWidth);
            mediaView.setFitHeight(containerHeight);
            mediaView.setPreserveRatio(false);
        } else { // Fit or Fill (preserve aspect ratio)
            mediaView.setFitWidth(containerWidth);
            mediaView.setFitHeight(containerHeight);
            mediaView.setPreserveRatio(true);
            if (aspectRatioMode == ASPECT_RATIO_FILL) {
                applyFillZoom(containerWidth, containerHeight);
            }
        }
    }

    private void applyFillZoom(double containerWidth, double containerHeight) {
        if (mediaPlayer == null || mediaPlayer.getMedia() == null) {
            return;
        }

        Media media = mediaPlayer.getMedia();
        double sourceWidth = media.getWidth();
        double sourceHeight = media.getHeight();
        if (sourceWidth <= 0 || sourceHeight <= 0 || containerWidth <= 0 || containerHeight <= 0) {
            return;
        }

        double sourceRatio = sourceWidth / sourceHeight;
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
            mediaView.setScaleX(zoom);
            mediaView.setScaleY(zoom);
        }
    }

    protected void updateStreamInfo(Media m) {
        Platform.runLater(() -> {
            if (m == null) {
                return;
            }
            String encoding = resolveVideoEncoding(m);
            if (m.getWidth() > 0 && m.getHeight() > 0) {
                compatibilityFallbackTimer.stop();
            }
            streamInfoText.setText(String.format("%n%dx%d %s (%s)", m.getWidth(), m.getHeight(), encoding, currentPlaybackModeLabel));
        });
    }

    private void handleStatusChange(MediaPlayer.Status status) {
        switch (status) {
            case PLAYING -> onPlayingStatus();
            case PAUSED -> btnPlayPause.setGraphic(playIcon);
            case STOPPED -> onStoppedStatus();
            case HALTED -> onHaltedStatus();
            case READY -> onReadyStatus();
            case DISPOSED, STALLED -> loadingSpinner.setVisible(true);
            default -> {
                // no UI change needed for transitional statuses
            }
        }
    }

    private void onPlayingStatus() {
        onPlaybackStarted();
        btnPlayPause.setGraphic(pauseIcon);
        updateVideoSize();
        scheduleCompatibilityFallbackCheck();
    }

    private void onStoppedStatus() {
        btnPlayPause.setGraphic(playIcon);
        timeSlider.setValue(0);
        loadingSpinner.setVisible(false);
        playbackWallClockStartedAtMs = 0L;
        lastObservedPlaybackTimeMs = 0L;
        compatibilityFallbackTimer.stop();
    }

    private void onHaltedStatus() {
        loadingSpinner.setVisible(false);
        compatibilityFallbackTimer.stop();
        if (!attemptedCompatibilityFallback && canUseCompatibilityFallback(currentMediaUri)) {
            startPlayback(currentMediaUri, true, playbackStartOffsetMs);
            return;
        }
        if (!errorLabel.isVisible()) {
            errorLabel.setText(I18n.tr("autoAnErrorOccurredDuringPlayback"));
            errorLabel.setVisible(true);
        }
    }

    private void onReadyStatus() {
        updateTimeLabel();
        updateVideoSize();
        Media media = mediaPlayer == null ? null : mediaPlayer.getMedia();
        if (media != null) {
            updateStreamInfo(media);
            wireMediaDimensionListeners(media);
        }
        scheduleCompatibilityFallbackCheck();
    }

    private void wireMediaDimensionListeners(Media media) {
        media.widthProperty().addListener((obs2, old, newVal) -> {
            updateStreamInfo(media);
            updateVideoSize();
        });
        media.heightProperty().addListener((obs2, old, newVal) -> {
            updateStreamInfo(media);
            updateVideoSize();
        });
    }

    private void releaseExistingPlayback() {
        if (mediaPlayer != null) {
            safeStopPlayer(mediaPlayer);
            safeDisposePlayer(mediaPlayer);
            mediaPlayer = null;
        }
        if (usingFfmpegFallback) {
            LitePlayerFfmpegService.getInstance().stopPlayback();
            usingFfmpegFallback = false;
        }
    }

    private void applyPreparedPlayback(LitePlayerFfmpegService.PreparedPlayback preparedPlayback, boolean forceCompatibilityFallback) {
        usingFfmpegFallback = preparedPlayback.usesFfmpeg();
        attemptedCompatibilityFallback = forceCompatibilityFallback || preparedPlayback.usesFfmpeg();
        currentPlaybackModeLabel = preparedPlayback.displayModeLabel();
        estimatedTotalDurationMs = preparedPlayback.estimatedDurationMs();
        playbackStartOffsetMs = preparedPlayback.startOffsetMs();
        playbackWallClockStartedAtMs = 0L;
        lastObservedPlaybackTimeMs = 0L;
    }

    private void createAndPlayMediaPlayer(String sourceUrl) {
        try {
            Media media = new Media(sourceUrl);
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            wireMediaPlayer(mediaPlayer);
            mediaPlayer.play();
        } catch (Exception e) {
            handlePlaybackError("Error creating media player.", e);
        }
    }

    private void wireMediaPlayer(MediaPlayer player) {
        player.setOnError(() -> handleMediaPlayerError(player.getError()));
        setVolume(volumeSlider.getValue());
        setMute(isMuted);
        player.muteProperty().addListener((obs, oldMute, newMute) -> {
            isMuted = Boolean.TRUE.equals(newMute);
            btnMute.setGraphic(Boolean.TRUE.equals(newMute) ? muteOnIcon : muteOffIcon);
        });
        player.statusProperty().addListener(statusListener);
        player.currentTimeProperty().addListener(progressListener);
        player.setOnEndOfMedia(() -> handleEndOfMedia(player));
    }

    private void handleMediaPlayerError(MediaException mediaException) {
        Platform.runLater(() -> {
            if (!attemptedCompatibilityFallback && canUseCompatibilityFallback(currentMediaUri)) {
                com.uiptv.util.AppLog.addLog("LiteVideoPlayer: direct playback failed, retrying with FFmpeg compatibility path.");
                loadingSpinner.setVisible(true);
                errorLabel.setVisible(false);
                startPlayback(currentMediaUri, true, playbackStartOffsetMs);
                return;
            }
            com.uiptv.util.AppLog.addLog("MediaPlayer Error: " + mediaException.getMessage() + " (" + mediaException.getType() + ")");
            errorLabel.setText(I18n.tr("autoCouldNotPlayVideoUnsupportedFormatOrNetworkError"));
            errorLabel.setVisible(true);
            loadingSpinner.setVisible(false);
            if (isRepeating && isRetrying.get()) {
                handleRepeat();
            }
        });
    }

    private void handleEndOfMedia(MediaPlayer player) {
        if (isBingeWatchPlayback() && advanceBingeWatchEpisode()) {
            return;
        }
        if (isRepeating && isRetrying.get()) {
            handleRepeat();
            return;
        }
        btnPlayPause.setGraphic(playIcon);
        player.seek(Duration.ZERO);
        player.pause();
    }

    private String resolveVideoEncoding(Media media) {
        if (media.getTracks() == null) {
            return "";
        }
        for (Track track : media.getTracks()) {
            if (track instanceof VideoTrack) {
                Object encoding = track.getMetadata().get("encoding");
                return encoding == null ? "" : String.valueOf(encoding);
            }
        }
        return "";
    }

    protected void updateTimeLabel() {
        if (mediaPlayer == null) {
            resetPlaybackUi();
            return;
        }

        Duration currentTime = safeCurrentTime(mediaPlayer);
        Duration totalDuration = safeTotalDuration(mediaPlayer);
        if (currentTime == null || totalDuration == null) {
            resetPlaybackUi();
            return;
        }

        PlaybackProgress progress = computePlaybackProgress(currentTime, totalDuration);
        updatePlaybackTimeUi(progress.currentMs(), progress.totalMs(), progress.seekable());
        setSeekControlsEnabled(!usingFfmpegFallback);
    }

    protected void handlePlaybackError(String message, Exception e) {
        Platform.runLater(() -> {
            com.uiptv.util.AppLog.addLog(message + ": " + e.getMessage());
            loadingSpinner.setVisible(false);
            errorLabel.setText(I18n.tr("autoCouldNotLoadVideoInvalidPathOrNetworkIssue"));
            errorLabel.setVisible(true);
        });
    }

    @Override
    public PlayerType getType() {
        return PlayerType.LITE;
    }

    protected void onPlaybackStarted() {
        retryCount = 0;
        loadingSpinner.setVisible(false);
        if (playbackWallClockStartedAtMs <= 0L) {
            playbackWallClockStartedAtMs = System.currentTimeMillis() - lastObservedPlaybackTimeMs;
        }
    }

    @Override
    protected boolean supportsTrackSelection() {
        return false;
    }

    private LitePlayerFfmpegService.PreparedPlayback resolvePlayback(String rawUri, boolean forceCompatibilityFallback, long startOffsetMs) {
        if (!forceCompatibilityFallback && !isLitePlayerFfmpegEnabled()) {
            return LitePlayerFfmpegService.getInstance().prepareDirectPlayback(rawUri);
        }
        return LitePlayerFfmpegService.getInstance().preparePlayback(rawUri, currentAccount, forceCompatibilityFallback, startOffsetMs);
    }

    private boolean canUseCompatibilityFallback(String sourceUrl) {
        if (isBlank(sourceUrl) || LitePlayerFfmpegService.getInstance().isManagedPlaybackUrl(sourceUrl)) {
            return false;
        }
        return isLitePlayerFfmpegEnabled();
    }

    private boolean isLitePlayerFfmpegEnabled() {
        Configuration configuration = ConfigurationService.getInstance().read();
        return configuration != null && configuration.isEnableLitePlayerFfmpeg();
    }

    private void scheduleCompatibilityFallbackCheck() {
        if (usingFfmpegFallback || attemptedCompatibilityFallback || !canUseCompatibilityFallback(currentMediaUri)) {
            compatibilityFallbackTimer.stop();
            return;
        }
        compatibilityFallbackTimer.playFromStart();
    }

    private void triggerCompatibilityFallbackIfNeeded() {
        if (usingFfmpegFallback || attemptedCompatibilityFallback || !canUseCompatibilityFallback(currentMediaUri)) {
            return;
        }
        if (!hasUsableVideoSignal()) {
            com.uiptv.util.AppLog.addLog("LiteVideoPlayer: no usable video signal detected, retrying with FFmpeg compatibility path.");
            loadingSpinner.setVisible(true);
            errorLabel.setVisible(false);
            startPlayback(currentMediaUri, true, playbackStartOffsetMs);
        }
    }

    private boolean isBingeWatchPlayback() {
        return activeBingeWatchToken != null && !activeBingeWatchToken.isBlank();
    }

    private String resolveActiveBingeWatchEpisodeId() {
        if (!isBlank(activeBingeWatchEpisodeId)) {
            return activeBingeWatchEpisodeId;
        }
        java.util.List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(activeBingeWatchToken);
        if (items.isEmpty()) {
            return "";
        }
        return items.get(0).episodeId();
    }

    private boolean advanceBingeWatchEpisode() {
        if (!isBingeWatchPlayback()) {
            return false;
        }
        java.util.List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(activeBingeWatchToken);
        if (items.isEmpty()) {
            return false;
        }
        int currentIndex = 0;
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).episodeId().equals(activeBingeWatchEpisodeId)) {
                currentIndex = i;
                break;
            }
        }
        int nextIndex = currentIndex + 1;
        if (nextIndex >= items.size()) {
            return false;
        }
        activeBingeWatchEpisodeId = items.get(nextIndex).episodeId();
        Platform.runLater(this::refreshNowShowingHeader);
        startBingeWatchPlayback(false, 0L);
        return true;
    }

    private boolean tryManagedSeekByPosition(float position) {
        if (!canUseEstimatedSeeking()) {
            return false;
        }
        long clampedTarget = Math.clamp(Math.round(estimatedTotalDurationMs * position), 0L, estimatedTotalDurationMs);
        restartPlaybackAtOffset(clampedTarget);
        return true;
    }

    private boolean tryManagedSeekByDelta(int deltaSeconds) {
        if (!canUseEstimatedSeeking()) {
            return false;
        }
        long currentMs = playbackStartOffsetMs;
        if (mediaPlayer != null && mediaPlayer.getCurrentTime() != null) {
            currentMs += Math.max(0L, (long) mediaPlayer.getCurrentTime().toMillis());
        }
        long targetMs = currentMs + (deltaSeconds * 1000L);
        if (targetMs < 0L) {
            targetMs = 0L;
        }
        if (estimatedTotalDurationMs > 0L) {
            targetMs = Math.min(targetMs, estimatedTotalDurationMs);
        }
        restartPlaybackAtOffset(targetMs);
        return true;
    }

    private boolean canUseEstimatedSeeking() {
        return usingFfmpegFallback && estimatedTotalDurationMs > 0L && !isBlank(currentMediaUri);
    }

    private void restartPlaybackAtOffset(long targetOffsetMs) {
        loadingSpinner.setVisible(true);
        errorLabel.setVisible(false);
        startPlayback(currentMediaUri, true, targetOffsetMs);
    }

    private void resetPlaybackUi() {
        timeLabel.setText(I18n.tr("auto00000000"));
        setSeekControlsEnabled(true);
        if (!isUserSeeking) {
            timeSlider.setValue(0);
        }
    }

    private PlaybackProgress computePlaybackProgress(Duration currentTime, Duration totalDuration) {
        boolean hasKnownTotal = totalDuration.greaterThan(Duration.ZERO) && !totalDuration.isIndefinite();
        long observedCurrentMs = resolveObservedCurrentMs(currentTime);
        long currentMs = playbackStartOffsetMs + observedCurrentMs;
        long totalMs = hasKnownTotal
                ? Math.max(estimatedTotalDurationMs, playbackStartOffsetMs + (long) totalDuration.toMillis())
                : estimatedTotalDurationMs;
        boolean seekable = hasKnownTotal && !usingFfmpegFallback;
        return new PlaybackProgress(currentMs, totalMs, seekable);
    }

    private long resolveObservedCurrentMs(Duration currentTime) {
        long observedCurrentMs = Math.max(0L, (long) currentTime.toMillis());
        if (observedCurrentMs > 0L) {
            lastObservedPlaybackTimeMs = observedCurrentMs;
            playbackWallClockStartedAtMs = System.currentTimeMillis() - observedCurrentMs;
            return observedCurrentMs;
        }
        if (!canUseEstimatedSeeking() || !isPlaying()) {
            return observedCurrentMs;
        }
        if (playbackWallClockStartedAtMs <= 0L) {
            playbackWallClockStartedAtMs = System.currentTimeMillis();
        }
        long wallClockElapsed = System.currentTimeMillis() - playbackWallClockStartedAtMs;
        return Math.max(lastObservedPlaybackTimeMs, wallClockElapsed);
    }

    private void setSeekControlsEnabled(boolean enabled) {
        timeSlider.setDisable(!enabled);
        btnRewind.setDisable(!enabled);
        btnFastForward.setDisable(!enabled);
    }

    private record PlaybackProgress(long currentMs, long totalMs, boolean seekable) {
    }

    private boolean hasUsableVideoSignal() {
        if (mediaPlayer == null) {
            return false;
        }
        Media media = mediaPlayer.getMedia();
        if (media == null) {
            return false;
        }
        if (media.getWidth() > 0 && media.getHeight() > 0) {
            return true;
        }
        if (media.getTracks() == null) {
            return false;
        }
        for (Track track : media.getTracks()) {
            if (track instanceof VideoTrack) {
                return media.getWidth() > 0 && media.getHeight() > 0;
            }
        }
        return false;
    }

    private Duration safeCurrentTime(MediaPlayer player) {
        try {
            return player.getCurrentTime();
        } catch (RuntimeException _) {
            return null;
        }
    }

    private Duration safeTotalDuration(MediaPlayer player) {
        try {
            return player.getTotalDuration();
        } catch (RuntimeException _) {
            return null;
        }
    }

    private MediaPlayer.Status safeStatus(MediaPlayer player) {
        try {
            return player.getStatus();
        } catch (RuntimeException _) {
            return MediaPlayer.Status.UNKNOWN;
        }
    }

    private void safeSeekPlayer(MediaPlayer player, Duration target) {
        try {
            player.seek(target);
        } catch (RuntimeException _) {
            // Ignore late seek requests while the JavaFX backend is swapping players.
        }
    }

    private void safeStopPlayer(MediaPlayer player) {
        try {
            player.stop();
        } catch (RuntimeException _) {
            // Ignore late stop requests while the JavaFX backend is swapping players.
        }
    }

    private void safeDisposePlayer(MediaPlayer player) {
        try {
            player.dispose();
        } catch (RuntimeException _) {
            // Ignore late dispose requests while the JavaFX backend is swapping players.
        }
    }
}
