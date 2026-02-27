package com.uiptv.player;

import com.uiptv.ui.LogDisplayUI;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.media.TrackType;
import uk.co.caprica.vlcj.media.VideoTrackInfo;
import uk.co.caprica.vlcj.player.base.MediaPlayer;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.base.TrackDescription;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;
import java.util.ArrayList;
import java.util.List;

public class VlcVideoPlayer extends BaseVideoPlayer {

    private EmbeddedMediaPlayer mediaPlayer;
    private ImageView videoImageView;
    private int videoSourceWidth, videoSourceHeight;

    public VlcVideoPlayer() {
        super(); // Must be the first call

        // --- VLCJ SETUP ---
        List<String> vlcArgs = new ArrayList<>();
        String osName = System.getProperty("os.name").toLowerCase();
        if (osName.contains("mac")) {
            vlcArgs.add("--avcodec-hw=videotoolbox");
        } else {
        vlcArgs.add("--avcodec-hw=auto");
        }
        vlcArgs.add("--network-caching=1000");

        MediaPlayerFactory mediaPlayerFactory = new MediaPlayerFactory(vlcArgs.toArray(new String[0]));
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();

        if (videoImageView == null) {
            videoImageView = new ImageView();
            videoImageView.setPreserveRatio(true);
        }
        mediaPlayer.videoSurface().set(new ImageViewVideoSurface(videoImageView));

        mediaPlayer.controls().setRepeat(false);
        mediaPlayer.audio().setMute(isMuted);

        // --- VLC EVENTS ---
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mp) {
                Platform.runLater(() -> {
                    retryCount = 0;
                    loadingSpinner.setVisible(false);
                    btnPlayPause.setGraphic(pauseIcon);
                    updateVideoSize();
                    refreshTrackMenus();
                });
            }

            @Override
            public void paused(MediaPlayer mp) {
                Platform.runLater(() -> btnPlayPause.setGraphic(playIcon));
            }

            @Override
            public void positionChanged(MediaPlayer mp, float newPosition) {
                if (!isUserSeeking) {
                    Platform.runLater(() -> {
                        if (!timeSlider.isDisable()) {
                            timeSlider.setValue(newPosition);
                        }
                    });
                }
            }

            @Override
            public void timeChanged(MediaPlayer mp, long newTime) {
                Platform.runLater(() -> {
                    long totalTime = mp.status().length();
                    boolean seekable = mp.status().isSeekable();
                    updatePlaybackTimeUi(newTime, totalTime, seekable);
                });
            }

            @Override
            public void finished(MediaPlayer mp) {
                Platform.runLater(() -> {
                    btnPlayPause.setGraphic(playIcon);
                    if (isRepeating && isRetrying.get()) {
                        handleRepeat();
                    }
                });
            }

            @Override
            public void stopped(MediaPlayer mp) {
                Platform.runLater(() -> {
                    btnPlayPause.setGraphic(playIcon);
                    timeSlider.setValue(0);
                    timeSlider.setDisable(false);
                    timeLabel.setText("00:00 / 00:00");
                    loadingSpinner.setVisible(false);
                });
            }

            @Override
            public void error(MediaPlayer mp) {
                Platform.runLater(() -> {
                    loadingSpinner.setVisible(false);
                    LogDisplayUI.addLog("VlcVideoPlayer: An error occurred in the media player.");
                    errorLabel.setText("Could not play video.\nUnsupported format or network error.");
                    errorLabel.setVisible(true);
                    if (isRepeating && isRetrying.get()) {
                        handleRepeat();
                    }
                });
            }

            @Override
            public void elementaryStreamAdded(MediaPlayer mp, TrackType type, int id) {
                if (type == TrackType.AUDIO) {
                    Platform.runLater(() -> refreshTrackMenus());
                }
            }

            @Override
            public void elementaryStreamDeleted(MediaPlayer mp, TrackType type, int id) {
                if (type == TrackType.AUDIO) {
                    Platform.runLater(() -> refreshTrackMenus());
                }
            }

            @Override
            public void elementaryStreamSelected(MediaPlayer mp, TrackType type, int id) {
                if (type == TrackType.AUDIO) {
                    Platform.runLater(() -> refreshTrackMenus());
                }
            }
        });
    }

    @Override
    protected Node getVideoView() {
        if (videoImageView == null) {
            videoImageView = new ImageView();
            videoImageView.setPreserveRatio(true);
        }
        return videoImageView;
    }

    @Override
    protected void playMedia(String uri) {
        new Thread(() -> mediaPlayer.media().play(uri)).start();
    }

    @Override
    protected void stopMedia() {
        mediaPlayer.controls().stop();
        videoImageView.setImage(null);
    }

    @Override
    protected void disposeMedia() {
        // No-op for VLCJ
    }

    @Override
    protected void setVolume(double volume) {
        mediaPlayer.audio().setVolume((int) volume);
    }

    @Override
    protected void setMute(boolean mute) {
        mediaPlayer.audio().setMute(mute);
    }

    @Override
    protected void seek(float position) {
        mediaPlayer.controls().setPosition(position);
    }

    @Override
    protected boolean isPlaying() {
        return mediaPlayer.status().isPlaying();
    }

    @Override
    protected void pauseMedia() {
        mediaPlayer.controls().pause();
    }

    @Override
    protected void resumeMedia() {
        mediaPlayer.controls().play();
    }

    @Override
    protected void updateVideoSize() {
        if (playerContainer.getWidth() <= 0 || playerContainer.getHeight() <= 0) {
            return;
        }

        videoImageView.fitWidthProperty().unbind();
        videoImageView.fitHeightProperty().unbind();

        double containerWidth = playerContainer.getWidth();
        double containerHeight = playerContainer.getHeight();

        if (aspectRatioMode == 1) { // Stretch to Fill
            videoImageView.setFitWidth(containerWidth);
            videoImageView.setFitHeight(containerHeight);
            videoImageView.setPreserveRatio(false);
        } else { // Default: Fit within (Contain)
            videoImageView.setFitWidth(containerWidth);
            videoImageView.setFitHeight(containerHeight);
            videoImageView.setPreserveRatio(true);
        }

        if (mediaPlayer != null && mediaPlayer.media() != null && mediaPlayer.media().info() != null) {
            List<VideoTrackInfo> tracks = mediaPlayer.media().info().videoTracks();
            if (tracks != null && !tracks.isEmpty()) {
                VideoTrackInfo track = tracks.get(0);
                if (track.width() > 0 && track.height() > 0) {
                    videoSourceWidth = track.width();
                    videoSourceHeight = track.height();
                }
            }
        }
        updateStreamInfo(videoSourceWidth, videoSourceHeight);
    }

    protected void updateStreamInfo(int width, int height) {
        String codec = "";
        if (mediaPlayer != null && mediaPlayer.media() != null && mediaPlayer.media().info() != null) {
            List<VideoTrackInfo> tracks = mediaPlayer.media().info().videoTracks();
            if (tracks != null && !tracks.isEmpty()) {
                VideoTrackInfo bestTrack = tracks.get(0);
                for (VideoTrackInfo track : tracks) {
                    if (track.width() == width && track.height() == height) {
                        bestTrack = track;
                        break;
                    }
                }
                codec = bestTrack.codecName();
            }
        }
        streamInfoText.setText(String.format("\n%dx%d %s (vlc)", width, height, codec));
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
        List<TrackOption> options = new ArrayList<>();
        if (mediaPlayer == null) {
            return options;
        }
        for (TrackDescription track : mediaPlayer.audio().trackDescriptions()) {
            options.add(new TrackOption(track.id(), normalizeTrackLabel(track.description(), "Audio")));
        }
        return options;
    }

    @Override
    protected int getSelectedAudioTrackId() {
        if (mediaPlayer == null) {
            return Integer.MIN_VALUE;
        }
        return mediaPlayer.audio().track();
    }

    @Override
    protected void selectAudioTrack(int trackId) {
        if (mediaPlayer != null) {
            mediaPlayer.audio().setTrack(trackId);
        }
    }

//    @Override
//    protected List<TrackOption> getSubtitleTrackOptions() {
//        List<TrackOption> options = new ArrayList<>();
//        if (mediaPlayer == null) {
//            return options;
//        }
//        for (TrackDescription track : mediaPlayer.subpictures().trackDescriptions()) {
//            options.add(new TrackOption(track.id(), normalizeTrackLabel(track.description(), "Subtitle")));
//        }
//        return options;
//    }
//
//    @Override
//    protected int getSelectedSubtitleTrackId() {
//        if (mediaPlayer == null) {
//            return Integer.MIN_VALUE;
//        }
//        return mediaPlayer.subpictures().track();
//    }
//
//    @Override
//    protected void selectSubtitleTrack(int trackId) {
//        if (mediaPlayer != null) {
//            mediaPlayer.subpictures().setTrack(trackId);
//            if (mediaPlayer.subpictures().track() != trackId) {
//                mediaPlayer.submit(() -> mediaPlayer.subpictures().setTrack(trackId));
//            }
//        }
//    }

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
