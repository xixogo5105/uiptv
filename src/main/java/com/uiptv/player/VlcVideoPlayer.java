package com.uiptv.player;

import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.image.WritablePixelFormat;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
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

import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

public class VlcVideoPlayer extends BaseVideoPlayer {

    private EmbeddedMediaPlayer mediaPlayer;
    private ImageView videoImageView;
    private WritableImage videoImage;
    private WritablePixelFormat<ByteBuffer> pixelFormat;

    private int videoSourceWidth, videoSourceHeight, videoSarNum, videoSarDen;

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

        mediaPlayer.videoSurface().set(new FXCallbackVideoSurface());
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
                });
            }

            @Override
            public void paused(MediaPlayer mp) {
                Platform.runLater(() -> btnPlayPause.setGraphic(playIcon));
            }

            @Override
            public void positionChanged(MediaPlayer mp, float newPosition) {
                if (!isUserSeeking) Platform.runLater(() -> timeSlider.setValue(newPosition));
            }

            @Override
            public void timeChanged(MediaPlayer mp, long newTime) {
                Platform.runLater(() -> {
                    long totalTime = mp.status().length();
                    timeLabel.setText(formatTime(newTime) + " / " + formatTime(totalTime));
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
                    loadingSpinner.setVisible(false);
                });
            }

            @Override
            public void error(MediaPlayer mp) {
                Platform.runLater(() -> {
                    loadingSpinner.setVisible(false);
                    System.err.println("VlcVideoPlayer: An error occurred in the media player.");
                    errorLabel.setText("Could not play video.\nUnsupported format or network error.");
                    errorLabel.setVisible(true);
                    if (isRepeating && isRetrying.get()) {
                        handleRepeat();
                    }
                });
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
        videoImage = null;
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
        updateStreamInfo(videoSourceWidth, videoSourceHeight);
    }

    protected void updateStreamInfo(int width, int height) {
        String codec = "";
        if (mediaPlayer != null) {
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
    public PlayerType getType() {
        return PlayerType.VLC;
    }

    private class FXCallbackVideoSurface extends CallbackVideoSurface {
        FXCallbackVideoSurface() {
            super(new FXBufferFormatCallback(), new FXRenderCallback(), false, VideoSurfaceAdapters.getVideoSurfaceAdapter());
        }
    }

    private class FXBufferFormatCallback implements BufferFormatCallback {
        @Override
        public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
            return new RV32BufferFormat(sourceWidth, sourceHeight);
        }

        @Override
        public void newFormatSize(int newWidth, int newHeight, int sarNumerator, int sarDenom) {
            VlcVideoPlayer.this.videoSourceWidth = newWidth;
            VlcVideoPlayer.this.videoSourceHeight = newHeight;
            VlcVideoPlayer.this.videoSarNum = sarNumerator;
            VlcVideoPlayer.this.videoSarDen = sarDenom;
            Platform.runLater(() -> VlcVideoPlayer.this.updateVideoSize());
        }

        @Override
        public void allocatedBuffers(ByteBuffer[] buffers) {
            // No-op
        }
    }

    private class FXRenderCallback implements RenderCallback {
        @Override
        public void display(MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers, BufferFormat bufferFormat, int sarNum, int sarDen) {
            WritableImage img = videoImage;
            if (img == null || img.getWidth() != bufferFormat.getWidth() || img.getHeight() != bufferFormat.getHeight()) {
                img = new WritableImage(bufferFormat.getWidth(), bufferFormat.getHeight());
                videoImage = img;
                final WritableImage newImage = img;
                Platform.runLater(() -> videoImageView.setImage(newImage));
                pixelFormat = WritablePixelFormat.getByteBgraPreInstance();
            }

            final WritableImage imageToRender = img;
            Platform.runLater(() -> {
                if (imageToRender != null) {
                    imageToRender.getPixelWriter().setPixels(0, 0, bufferFormat.getWidth(), bufferFormat.getHeight(), pixelFormat, nativeBuffers[0], bufferFormat.getPitches()[0]);
                }
            });
        }

        @Override
        public void lock(MediaPlayer mediaPlayer) {
            // No-op
        }

        @Override
        public void unlock(MediaPlayer mediaPlayer) {
            // No-op
        }
    }

    protected void onPlaybackStarted() {
        retryCount = 0;
        loadingSpinner.setVisible(false);
    }
}
