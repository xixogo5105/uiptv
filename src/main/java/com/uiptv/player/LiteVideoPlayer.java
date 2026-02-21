package com.uiptv.player;

import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.scene.Node;
import javafx.scene.media.*;
import javafx.util.Duration;

import java.io.File;

import static com.uiptv.util.StringUtils.isBlank;

public class LiteVideoPlayer extends BaseVideoPlayer {

    private MediaPlayer mediaPlayer;
    private MediaView mediaView; // Removed final and initializer
    private final ChangeListener<Duration> progressListener;
    private final ChangeListener<MediaPlayer.Status> statusListener;

    public LiteVideoPlayer() {
        super(); // Calls buildUI -> getVideoView

        // Apply initial mute state from BaseVideoPlayer
        setMute(isMuted);

        // --- JAVAFX MEDIA PLAYER LISTENERS ---
        statusListener = (obs, oldStatus, newStatus) -> {
            Platform.runLater(() -> {
                switch (newStatus) {
                    case PLAYING:
                        onPlaybackStarted();
                        btnPlayPause.setGraphic(pauseIcon);
                        updateVideoSize();
                        break;
                    case PAUSED:
                        btnPlayPause.setGraphic(playIcon);
                        break;
                    case STOPPED:
                        btnPlayPause.setGraphic(playIcon);
                        timeSlider.setValue(0);
                        loadingSpinner.setVisible(false);
                        break;
                    case HALTED:
                        loadingSpinner.setVisible(false);
                        if (!errorLabel.isVisible()) {
                            errorLabel.setText("An error occurred during playback.");
                            errorLabel.setVisible(true);
                        }
                        break;
                    case READY:
                        updateTimeLabel();
                        updateVideoSize();
                        Media m = mediaPlayer.getMedia();
                        if (m != null) {
                            updateStreamInfo(m);
                            m.widthProperty().addListener((obs2, old, newVal) -> updateStreamInfo(m));
                            m.heightProperty().addListener((obs2, old, newVal) -> updateStreamInfo(m));
                        }
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
        if (isBlank(uri)) {
            stop();
            return;
        }
        this.currentMediaUri = uri.replace("extension=ts", "extension=m3u8");

        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        new Thread(() -> {
            try {
                String sourceUrl = currentMediaUri.trim();
                if (!sourceUrl.startsWith("http") && !sourceUrl.startsWith("file:")) {
                    File f = new File(sourceUrl);
                    if (f.exists()) sourceUrl = f.toURI().toString();
                }

                final String finalSourceUrl = sourceUrl;

                Platform.runLater(() -> {
                    try {
                        Media media = new Media(finalSourceUrl);
                        mediaPlayer = new MediaPlayer(media);
                        mediaView.setMediaPlayer(mediaPlayer);

                        mediaPlayer.setOnError(() -> {
                            final MediaException me = mediaPlayer.getError();
                            Platform.runLater(() -> {
                                System.err.println("MediaPlayer Error: " + me.getMessage() + " (" + me.getType() + ")");
                                errorLabel.setText("Could not play video.\nUnsupported format or network error.");
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
    }

    @Override
    protected void disposeMedia() {
        if (mediaPlayer != null) {
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
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

        if (aspectRatioMode == 1) { // Stretch to Fill
            mediaView.setFitWidth(containerWidth);
            mediaView.setFitHeight(containerHeight);
            mediaView.setPreserveRatio(false);
        } else { // Default: Fit within (Contain)
            mediaView.setFitWidth(containerWidth);
            mediaView.setFitHeight(containerHeight);
            mediaView.setPreserveRatio(true);
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
                streamInfoText.setText(String.format("\n%dx%d %s (Lite)", m.getWidth(), m.getHeight(), encoding));
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
            timeLabel.setText("00:00 / 00:00");
            timeSlider.setDisable(false);
            if (!isUserSeeking) {
                timeSlider.setValue(0);
            }
        }
    }

    protected void handlePlaybackError(String message, Exception e) {
        Platform.runLater(() -> {
            System.err.println(message + ": " + e.getMessage());
            e.printStackTrace();
            loadingSpinner.setVisible(false);
            errorLabel.setText("Could not load video.\nInvalid path or network issue.");
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
}
