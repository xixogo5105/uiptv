package com.uiptv.player;

import com.uiptv.model.Configuration;
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

    private MediaPlayer mediaPlayer;
    private MediaView mediaView; // Removed final and initializer
    private final ChangeListener<Duration> progressListener;
    private final ChangeListener<MediaPlayer.Status> statusListener;
    private volatile boolean usingFfmpegFallback;
    private volatile boolean attemptedCompatibilityFallback;
    private volatile String currentPlaybackModeLabel = "Lite direct";
    private final PauseTransition compatibilityFallbackTimer = new PauseTransition(Duration.seconds(6));

    public LiteVideoPlayer() {
        super(); // Calls buildUI -> getVideoView

        // Apply initial mute state from BaseVideoPlayer
        setMute(isMuted);
        compatibilityFallbackTimer.setOnFinished(e -> triggerCompatibilityFallbackIfNeeded());

        // --- JAVAFX MEDIA PLAYER LISTENERS ---
        statusListener = (obs, oldStatus, newStatus) -> {
            Platform.runLater(() -> {
                switch (newStatus) {
                    case PLAYING:
                        onPlaybackStarted();
                        btnPlayPause.setGraphic(pauseIcon);
                        updateVideoSize();
                        scheduleCompatibilityFallbackCheck();
                        break;
                    case PAUSED:
                        btnPlayPause.setGraphic(playIcon);
                        break;
                    case STOPPED:
                        btnPlayPause.setGraphic(playIcon);
                        timeSlider.setValue(0);
                        loadingSpinner.setVisible(false);
                        compatibilityFallbackTimer.stop();
                        break;
                    case HALTED:
                        loadingSpinner.setVisible(false);
                        compatibilityFallbackTimer.stop();
                        if (!attemptedCompatibilityFallback && canUseCompatibilityFallback(currentMediaUri)) {
                            startPlayback(currentMediaUri, true);
                            return;
                        }
                        if (!errorLabel.isVisible()) {
                            errorLabel.setText(I18n.tr("autoAnErrorOccurredDuringPlayback"));
                            errorLabel.setVisible(true);
                        }
                        break;
                    case READY:
                        updateTimeLabel();
                        updateVideoSize();
                        Media m = mediaPlayer.getMedia();
                        if (m != null) {
                            updateStreamInfo(m);
                            m.widthProperty().addListener((obs2, old, newVal) -> {
                                updateStreamInfo(m);
                                updateVideoSize();
                            });
                            m.heightProperty().addListener((obs2, old, newVal) -> {
                                updateStreamInfo(m);
                                updateVideoSize();
                            });
                        }
                        scheduleCompatibilityFallbackCheck();
                        break;
                    case DISPOSED:
                    case STALLED:
                        loadingSpinner.setVisible(true);
                        break;
                }
            });
        };

        progressListener = (obs, oldTime, newTime) -> {
            if (!isUserSeeking) {
                Platform.runLater(() -> {
                    updateTimeLabel();
                });
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
        startPlayback(uri, false);
    }

    private void startPlayback(String uri, boolean forceCompatibilityFallback) {
        if (isBlank(uri)) {
            stop();
            return;
        }
        compatibilityFallbackTimer.stop();
        this.currentMediaUri = uri;

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }
        if (usingFfmpegFallback) {
            LitePlayerFfmpegService.getInstance().stopPlayback();
            usingFfmpegFallback = false;
        }

        new Thread(() -> {
            try {
                LitePlayerFfmpegService.PreparedPlayback preparedPlayback = resolvePlayback(currentMediaUri, forceCompatibilityFallback);
                usingFfmpegFallback = preparedPlayback.usesFfmpeg();
                attemptedCompatibilityFallback = forceCompatibilityFallback || preparedPlayback.usesFfmpeg();
                currentPlaybackModeLabel = preparedPlayback.displayModeLabel();

                final String finalSourceUrl = preparedPlayback.playbackUrl();

                Platform.runLater(() -> {
                    try {
                        Media media = new Media(finalSourceUrl);
                        mediaPlayer = new MediaPlayer(media);
                        mediaView.setMediaPlayer(mediaPlayer);

                        mediaPlayer.setOnError(() -> {
                            final MediaException me = mediaPlayer.getError();
                            Platform.runLater(() -> {
                                if (!attemptedCompatibilityFallback && canUseCompatibilityFallback(currentMediaUri)) {
                                    com.uiptv.util.AppLog.addLog("LiteVideoPlayer: direct playback failed, retrying with FFmpeg compatibility path.");
                                    loadingSpinner.setVisible(true);
                                    errorLabel.setVisible(false);
                                    startPlayback(currentMediaUri, true);
                                    return;
                                }
                                com.uiptv.util.AppLog.addLog("MediaPlayer Error: " + me.getMessage() + " (" + me.getType() + ")");
                                errorLabel.setText(I18n.tr("autoCouldNotPlayVideoUnsupportedFormatOrNetworkError"));
                                errorLabel.setVisible(true);
                                loadingSpinner.setVisible(false);
                                if (isRepeating && isRetrying.get()) {
                                    handleRepeat();
                                }
                            });
                        });

                        setVolume(volumeSlider.getValue());
                        setMute(isMuted);
                        mediaPlayer.muteProperty().addListener((obs, oldMute, newMute) -> {
                            isMuted = newMute;
                            btnMute.setGraphic(newMute ? muteOnIcon : muteOffIcon);
                        });

                        mediaPlayer.statusProperty().addListener(statusListener);
                        mediaPlayer.currentTimeProperty().addListener(progressListener);
                        mediaPlayer.setOnEndOfMedia(() -> {
                            if (isRepeating && isRetrying.get()) {
                                handleRepeat();
                            } else {
                                btnPlayPause.setGraphic(playIcon);
                                mediaPlayer.seek(Duration.ZERO);
                                mediaPlayer.pause();
                            }
                        });

                        mediaPlayer.play();
                    } catch (Exception e) {
                        handlePlaybackError("Error creating media player.", e);
                    }
                });
            } catch (Exception e) {
                handlePlaybackError("Error resolving media URL.", e);
            }
        }).start();
    }

    @Override
    protected void stopMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaView.setMediaPlayer(null);
        }
        if (usingFfmpegFallback) {
            LitePlayerFfmpegService.getInstance().stopPlayback();
            usingFfmpegFallback = false;
        }
        currentPlaybackModeLabel = "Lite direct";
        compatibilityFallbackTimer.stop();
    }

    @Override
    protected void disposeMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        if (usingFfmpegFallback) {
            LitePlayerFfmpegService.getInstance().stopPlayback();
            usingFfmpegFallback = false;
        }
        currentPlaybackModeLabel = "Lite direct";
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
        if (mediaPlayer != null && mediaPlayer.getTotalDuration() != null) {
            Duration total = mediaPlayer.getTotalDuration();
            if (total != null && total.greaterThan(Duration.ZERO) && !total.isIndefinite()) {
                mediaPlayer.seek(total.multiply(position));
            }
        }
    }

    @Override
    protected boolean isPlaying() {
        return mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
    }

    @Override
    protected void pauseMedia() {
        if (mediaPlayer != null) mediaPlayer.pause();
    }

    @Override
    protected void resumeMedia() {
        if (mediaPlayer != null) mediaPlayer.play();
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
            if (m != null) {
                String encoding = "";
                if (m.getTracks() != null) {
                    for (Track track : m.getTracks()) {
                        if (track instanceof VideoTrack) {
                            Object enc = track.getMetadata().get("encoding");
                            if (enc != null) {
                                encoding = String.valueOf(enc);
                            }
                            break;
                        }
                    }
                }
                if (m.getWidth() > 0 && m.getHeight() > 0) {
                    compatibilityFallbackTimer.stop();
                }
                streamInfoText.setText(String.format("\n%dx%d %s (%s)", m.getWidth(), m.getHeight(), encoding, currentPlaybackModeLabel));
            }
        });
    }

    protected void updateTimeLabel() {
        if (mediaPlayer != null && mediaPlayer.getCurrentTime() != null && mediaPlayer.getTotalDuration() != null) {
            Duration currentTime = mediaPlayer.getCurrentTime();
            Duration totalDuration = mediaPlayer.getTotalDuration();
            boolean hasKnownTotal = totalDuration != null && totalDuration.greaterThan(Duration.ZERO) && !totalDuration.isIndefinite();
            long totalMs = hasKnownTotal ? (long) totalDuration.toMillis() : -1L;
            // JavaFX media API does not expose seekable() directly; indefinite total is treated as live-like.
            boolean seekable = hasKnownTotal;
            updatePlaybackTimeUi((long) currentTime.toMillis(), totalMs, seekable);
        } else {
            timeLabel.setText(I18n.tr("auto00000000"));
            timeSlider.setDisable(false);
            if (!isUserSeeking) {
                timeSlider.setValue(0);
            }
        }
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
    }

    @Override
    protected boolean supportsTrackSelection() {
        return false;
    }

    private LitePlayerFfmpegService.PreparedPlayback resolvePlayback(String rawUri, boolean forceCompatibilityFallback) {
        if (!forceCompatibilityFallback && !isLitePlayerFfmpegEnabled()) {
            return LitePlayerFfmpegService.getInstance().prepareDirectPlayback(rawUri);
        }
        return LitePlayerFfmpegService.getInstance().preparePlayback(rawUri, currentAccount, forceCompatibilityFallback);
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
            startPlayback(currentMediaUri, true);
        }
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
}
