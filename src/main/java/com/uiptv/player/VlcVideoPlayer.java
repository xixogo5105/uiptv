package com.uiptv.player;

import com.uiptv.util.I18n;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.Image;
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

    private final Object playerLock = new Object();
    private MediaPlayerFactory mediaPlayerFactory;
    private EmbeddedMediaPlayer mediaPlayer;
    private MediaPlayerEventAdapter mediaPlayerEvents;
    private ImageView videoImageView;
    private int videoSourceWidth, videoSourceHeight;
    private String lastStreamInfoLabel = "";

    public VlcVideoPlayer() {
        super(); // Must be the first call

        if (videoImageView == null) {
            videoImageView = new ImageView();
            videoImageView.setPreserveRatio(true);
            videoImageView.imageProperty().addListener((obs, oldImage, newImage) -> onRenderedImageChanged(newImage));
        }
        ensurePlayerInitialized();
    }

    private void ensurePlayerInitialized() {
        synchronized (playerLock) {
            if (mediaPlayer != null) {
                return;
            }
            mediaPlayerFactory = new MediaPlayerFactory(createVlcArgs().toArray(new String[0]));
            mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
            configureEmbeddedPlayer();
            mediaPlayerEvents = createMediaPlayerEvents();
            mediaPlayer.events().addMediaPlayerEventListener(mediaPlayerEvents);
        }
    }

    private List<String> createVlcArgs() {
        List<String> vlcArgs = new ArrayList<>();
        String osName = System.getProperty("os.name").toLowerCase();
        String defaultHw = osName.contains("mac") ? "none" : "auto";
        String hwDecode = System.getProperty("uiptv.vlc.hwdec", defaultHw).trim().toLowerCase();
        if (hwDecode.isBlank()) {
            hwDecode = defaultHw;
        }
        vlcArgs.add("--avcodec-hw=" + hwDecode);
        vlcArgs.add("--network-caching=1000");
        return vlcArgs;
    }

    private void configureEmbeddedPlayer() {
        mediaPlayer.videoSurface().set(new ImageViewVideoSurface(videoImageView));
        mediaPlayer.controls().setRepeat(false);
        mediaPlayer.audio().setMute(isMuted);
    }

    private MediaPlayerEventAdapter createMediaPlayerEvents() {
        return new MediaPlayerEventAdapter() {
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
                    refreshRenderedImageStreamInfo();
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
                    timeLabel.setText(I18n.tr("auto00000000"));
                    loadingSpinner.setVisible(false);
                    videoSourceWidth = 0;
                    videoSourceHeight = 0;
                    lastStreamInfoLabel = "";
                });
            }

            @Override
            public void error(MediaPlayer mp) {
                Platform.runLater(() -> {
                    loadingSpinner.setVisible(false);
                    com.uiptv.util.AppLog.addLog("VlcVideoPlayer: An error occurred in the media player.");
                    errorLabel.setText(I18n.tr("autoCouldNotPlayVideoUnsupportedFormatOrNetworkError"));
                    errorLabel.setVisible(true);
                    if (isRepeating && isRetrying.get()) {
                        handleRepeat();
                    }
                });
            }

            @Override
            public void elementaryStreamAdded(MediaPlayer mp, TrackType type, int id) {
                refreshTrackMenusIfAudio(type);
            }

            @Override
            public void elementaryStreamDeleted(MediaPlayer mp, TrackType type, int id) {
                refreshTrackMenusIfAudio(type);
            }

            @Override
            public void elementaryStreamSelected(MediaPlayer mp, TrackType type, int id) {
                refreshTrackMenusIfAudio(type);
            }
        };
    }

    private void refreshTrackMenusIfAudio(TrackType type) {
        if (type == TrackType.AUDIO) {
            Platform.runLater(this::refreshTrackMenus);
        }
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
        new Thread(() -> {
            ensurePlayerInitialized();
            videoSourceWidth = 0;
            videoSourceHeight = 0;
            lastStreamInfoLabel = "";
            EmbeddedMediaPlayer player;
            synchronized (playerLock) {
                player = mediaPlayer;
            }
            if (player != null) {
                player.media().play(uri);
            }
        }).start();
    }

    @Override
    protected void stopMedia() {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null) {
            player.controls().stop();
        }
        videoImageView.setImage(null);
        videoSourceWidth = 0;
        videoSourceHeight = 0;
        lastStreamInfoLabel = "";
    }

    @Override
    protected void disposeMedia() {
        synchronized (playerLock) {
            if (mediaPlayer != null) {
                try {
                    mediaPlayer.controls().stop();
                } catch (Exception _) {
                }
                try {
                    if (mediaPlayerEvents != null) {
                        mediaPlayer.events().removeMediaPlayerEventListener(mediaPlayerEvents);
                    }
                } catch (Exception _) {
                }
                try {
                    mediaPlayer.videoSurface().set(null);
                } catch (Exception _) {
                }
                try {
                    mediaPlayer.release();
                } catch (Exception _) {
                }
                mediaPlayer = null;
                mediaPlayerEvents = null;
            }
            if (mediaPlayerFactory != null) {
                try {
                    mediaPlayerFactory.release();
                } catch (Exception _) {
                }
                mediaPlayerFactory = null;
            }
        }
        videoImageView.setImage(null);
        videoSourceWidth = 0;
        videoSourceHeight = 0;
        lastStreamInfoLabel = "";
    }

    @Override
    protected void setVolume(double volume) {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null) {
            player.audio().setVolume((int) volume);
        }
    }

    @Override
    protected void setMute(boolean mute) {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null) {
            player.audio().setMute(mute);
        }
    }

    @Override
    protected void seek(float position) {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null) {
            player.controls().setPosition(position);
        }
    }

    @Override
    protected boolean isPlaying() {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        return player != null && player.status().isPlaying();
    }

    @Override
    protected void pauseMedia() {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null) {
            player.controls().pause();
        }
    }

    @Override
    protected void resumeMedia() {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null) {
            player.controls().play();
        }
    }

    @Override
    protected void updateVideoSize() {
        if (playerContainer.getWidth() <= 0 || playerContainer.getHeight() <= 0) {
            return;
        }

        double containerWidth = playerContainer.getWidth();
        double containerHeight = playerContainer.getHeight();
        resetVideoImageView(containerWidth, containerHeight);
        refreshVideoSourceDimensions();
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

    private void refreshVideoSourceDimensions() {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player == null || player.media() == null || player.media().info() == null) {
            return;
        }
        List<VideoTrackInfo> tracks = player.media().info().videoTracks();
        if (tracks == null || tracks.isEmpty()) {
            return;
        }
        VideoTrackInfo track = tracks.get(0);
        if (track.width() > 0 && track.height() > 0) {
            videoSourceWidth = track.width();
            videoSourceHeight = track.height();
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
        String codec = "";
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null && player.media() != null && player.media().info() != null) {
            List<VideoTrackInfo> tracks = player.media().info().videoTracks();
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
        String newLabel = String.format("%n%dx%d %s (vlc)", width, height, codec);
        if (!newLabel.equals(lastStreamInfoLabel)) {
            lastStreamInfoLabel = newLabel;
            streamInfoText.setText(newLabel);
        }
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
        List<TrackOption> options = new ArrayList<>();
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player == null) {
            return options;
        }
        for (TrackDescription track : player.audio().trackDescriptions()) {
            options.add(new TrackOption(track.id(), normalizeTrackLabel(track.description(), "Audio")));
        }
        return options;
    }

    @Override
    protected int getSelectedAudioTrackId() {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player == null) {
            return Integer.MIN_VALUE;
        }
        return player.audio().track();
    }

    @Override
    protected void selectAudioTrack(int trackId) {
        EmbeddedMediaPlayer player;
        synchronized (playerLock) {
            player = mediaPlayer;
        }
        if (player != null) {
            player.audio().setTrack(trackId);
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
