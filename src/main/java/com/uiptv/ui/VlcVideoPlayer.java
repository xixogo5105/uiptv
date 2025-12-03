package com.uiptv.ui;

import com.uiptv.api.EmbeddedVideoPlayer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.Slider;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
import uk.co.caprica.vlcj.javafx.videosurface.ImageViewVideoSurface;
import uk.co.caprica.vlcj.player.base.MediaPlayerEventAdapter;
import uk.co.caprica.vlcj.player.embedded.EmbeddedMediaPlayer;

public class VlcVideoPlayer implements EmbeddedVideoPlayer {
    private MediaPlayerFactory mediaPlayerFactory;
    private EmbeddedMediaPlayer mediaPlayer;

    // UI Components
    private Slider timeSlider;
    private Slider volumeSlider;
    private Label timeLabel;
    private VBox controlsContainer;
    private ProgressIndicator loadingSpinner;

    // Buttons and Icons
    private Button btnPlayPause;
    private Button btnMute;
    private Button btnRepeat;
    private Button btnFullscreen;
    private Button btnReload; // Added reload button
    private ImageView playIcon, pauseIcon, stopIcon, repeatOnIcon, repeatOffIcon, fullscreenIcon, fullscreenExitIcon, muteOnIcon, muteOffIcon, reloadIcon; // Added reloadIcon

    private boolean isUserSeeking = false;
    private PauseTransition idleTimer;
    private StackPane playerContainer = new StackPane();

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    private Pane originalParent;
    private int originalIndex = -1;
    private final ImageView videoImageView;
    private FadeTransition fadeIn;
    private FadeTransition fadeOut;
    private String currentMediaUri; // Added to store current media URI for reload

    public VlcVideoPlayer() {
        // --- 1. VLCJ SETUP ---
        mediaPlayerFactory = new MediaPlayerFactory();
        mediaPlayer = mediaPlayerFactory.mediaPlayers().newEmbeddedMediaPlayer();
        videoImageView = new ImageView();
        videoImageView.setPreserveRatio(true);
        mediaPlayer.videoSurface().set(new ImageViewVideoSurface(videoImageView));
        mediaPlayer.controls().setRepeat(false);

        // --- 1.5 LOAD ICONS ---
        loadIcons();

        // --- 2. BUILD CONTROLS ---
        btnPlayPause = createIconButton(pauseIcon);
        Button btnStop = createIconButton(stopIcon);
        btnRepeat = createIconButton(repeatOffIcon);
        btnRepeat.setOpacity(0.7);
        btnReload = createIconButton(reloadIcon); // Initialize reload button
        btnFullscreen = createIconButton(fullscreenIcon);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnMute = createIconButton(muteOnIcon);
        // Initialize mute button graphic: if currently muted, show muteOnIcon (to unmute); otherwise, show muteOffIcon (to mute)
        btnMute.setGraphic(mediaPlayer.audio().isMute() ? muteOnIcon : muteOffIcon);

        volumeSlider = new Slider(0, 200, 50);
        volumeSlider.setPrefWidth(100);

        HBox topRow = new HBox(8); // Increased spacing for icons
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(btnPlayPause, btnStop, btnRepeat, btnReload, btnFullscreen, spacer, btnMute, volumeSlider); // Added btnReload

        timeSlider = new Slider(0, 1, 0);
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeLabel = new Label("00:00 / 00:00");
        timeLabel.setTextFill(Color.WHITE);
        timeLabel.setStyle("-fx-font-family: monospace; -fx-font-weight: bold;");

        HBox bottomRow = new HBox(10);
        bottomRow.setAlignment(Pos.CENTER_LEFT);
        bottomRow.getChildren().addAll(timeSlider, timeLabel);

        controlsContainer = new VBox(10);
        controlsContainer.setPadding(new Insets(8));
        controlsContainer.setStyle("-fx-background-color: rgba(0, 0, 0, 0.75); -fx-background-radius: 10;");
        controlsContainer.getChildren().addAll(topRow, bottomRow);
        controlsContainer.setMaxWidth(480);
        controlsContainer.setMaxHeight(80);

        // --- 3. LAYOUT ROOT ---
        playerContainer.setStyle("-fx-background-color: black;");
        playerContainer.setFocusTraversable(true);
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);

        videoImageView.fitWidthProperty().bind(playerContainer.widthProperty());
        videoImageView.fitHeightProperty().bind(playerContainer.heightProperty());

        StackPane overlayWrapper = new StackPane(controlsContainer);
        overlayWrapper.setAlignment(Pos.BOTTOM_CENTER);
        overlayWrapper.setPadding(new Insets(0, 20, 20, 20));

        loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(60, 60);
        loadingSpinner.setVisible(false);

        playerContainer.getChildren().addAll(videoImageView, overlayWrapper, loadingSpinner);

        // --- 4. EVENT LOGIC ---
        btnPlayPause.setOnAction(e -> {
            if (mediaPlayer.status().isPlaying()) {
                mediaPlayer.controls().pause();
            } else {
                mediaPlayer.controls().play();
            }
        });

        btnStop.setOnAction(e -> stop());

        btnRepeat.setOnAction(e -> {
            boolean isRepeating = !mediaPlayer.controls().getRepeat();
            mediaPlayer.controls().setRepeat(isRepeating);
            btnRepeat.setGraphic(isRepeating ? repeatOnIcon : repeatOffIcon);
            btnRepeat.setOpacity(isRepeating ? 1.0 : 0.7);
        });

        btnReload.setOnAction(e -> { // Reload action
            if (currentMediaUri != null && !currentMediaUri.isEmpty()) {
                play(currentMediaUri); // Replay the current media
            }
        });

        btnFullscreen.setOnAction(e -> toggleFullscreen());

        btnMute.setOnAction(e -> {
            boolean isMuted = !mediaPlayer.audio().isMute();
            mediaPlayer.audio().setMute(isMuted);
            // After toggling, if it's now muted, show muteOnIcon (to unmute); otherwise, show muteOffIcon (to mute)
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> mediaPlayer.audio().setVolume(newVal.intValue()));

        timeSlider.setOnMousePressed(e -> isUserSeeking = true);
        timeSlider.setOnMouseReleased(e -> {
            mediaPlayer.controls().setPosition((float) timeSlider.getValue());
            isUserSeeking = false;
        });

        playerContainer.setOnMouseClicked(e -> {
            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) playerContainer.requestFocus();
                else if (e.getClickCount() == 2) toggleFullscreen();
            }
        });

        playerContainer.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F) toggleFullscreen();
            else if (e.getCode() == KeyCode.M) btnMute.fire();
            else if (e.getCode() == KeyCode.ESCAPE && fullscreenStage != null) toggleFullscreen();
        });

        playerContainer.setOnScroll(e -> {
            double delta = e.getDeltaY();
            double currentVolume = volumeSlider.getValue();
            volumeSlider.setValue(currentVolume + (delta > 0 ? 5 : -5));
        });

        // --- VLC EVENTS ---
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    loadingSpinner.setVisible(false);
                    btnPlayPause.setGraphic(pauseIcon);
                    fadeIn.play();
                    idleTimer.playFromStart();
                });
            }

            @Override
            public void paused(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> btnPlayPause.setGraphic(playIcon));
            }

            @Override
            public void positionChanged(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, float newPosition) {
                if (!isUserSeeking) Platform.runLater(() -> timeSlider.setValue(newPosition));
            }

            @Override
            public void timeChanged(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer, long newTime) {
                Platform.runLater(() -> timeLabel.setText(formatTime(newTime) + " / " + formatTime(mediaPlayer.status().length())));
            }

            @Override
            public void finished(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> btnPlayPause.setGraphic(playIcon));
            }

            @Override
            public void stopped(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    btnPlayPause.setGraphic(playIcon);
                    timeSlider.setValue(0);
                    loadingSpinner.setVisible(false);
                });
            }

            @Override
            public void error(uk.co.caprica.vlcj.player.base.MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    loadingSpinner.setVisible(false);
                    System.err.println("An error occurred in the media player.");
                });
            }
        });

        // --- 5. FADE / HIDE LOGIC ---
        setupFadeAndIdleLogic();
    }

    private void loadIcons() {
        playIcon = createIconView("play.png");
        pauseIcon = createIconView("pause.png");
        stopIcon = createIconView("stop.png");
        repeatOnIcon = createIconView("repeat-on.png");
        repeatOffIcon = createIconView("repeat-off.png");
        reloadIcon = createIconView("reload.png"); // Load reload icon
        fullscreenIcon = createIconView("fullscreen.png");
        fullscreenExitIcon = createIconView("fullscreen-exit.png");
        muteOnIcon = createIconView("mute-on.png");
        muteOffIcon = createIconView("mute-off.png");
    }

    private ImageView createIconView(String iconName) {
        try {
            String iconPath = "/icons/videoPlayer/" + iconName;
            Image image = new Image(getClass().getResource(iconPath).toExternalForm());
            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(20);
            imageView.setFitWidth(20);
            imageView.setPreserveRatio(true);

            // Apply ColorAdjust to lighten the icons
            ColorAdjust colorAdjust = new ColorAdjust();
            colorAdjust.setBrightness(0.8); // Adjust brightness as needed (0.0 to 1.0)
            imageView.setEffect(colorAdjust);

            return imageView;
        } catch (Exception e) {
            System.err.println("Failed to load icon: " + iconName);
            e.printStackTrace();
            return new ImageView(); // Return empty view on error
        }
    }

    private Button createIconButton(ImageView icon) {
        Button btn = new Button();
        btn.setGraphic(icon);
        btn.setPadding(new Insets(6));
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-cursor: hand; -fx-background-radius: 4;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
        return btn;
    }

    private void setupFadeAndIdleLogic() {
        controlsContainer.setOpacity(0);
        fadeOut = new FadeTransition(Duration.millis(500), controlsContainer);
        fadeOut.setFromValue(1.0);
        fadeOut.setToValue(0.0);
        fadeIn = new FadeTransition(Duration.millis(200), controlsContainer);
        fadeIn.setFromValue(0.0);
        fadeIn.setToValue(1.0);
        idleTimer = new PauseTransition(Duration.seconds(3));
        idleTimer.setOnFinished(e -> fadeOut.play());
        playerContainer.setOnMouseMoved(e -> {
            if (controlsContainer.getOpacity() < 1.0) fadeIn.play();
            idleTimer.playFromStart();
        });
        playerContainer.setOnMouseExited(e -> fadeOut.play());
    }

    @Override
    public void play(String uri) {
        if (uri != null && !uri.isEmpty()) {
            this.currentMediaUri = uri; // Store the current media URI
            playerContainer.setVisible(true);
            playerContainer.setManaged(true);
            loadingSpinner.setVisible(true);
            mediaPlayer.media().play(uri);
        }
    }

    @Override
    public Node getPlayerContainer() {
        return playerContainer;
    }

    private String formatTime(long millis) {
        long seconds = millis / 1000, s = seconds % 60, m = (seconds / 60) % 60, h = (seconds / 3600) % 24;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.controls().stop();
            playerContainer.setVisible(false);
            playerContainer.setManaged(false);

            // mediaPlayer.release();
        }
        //if (mediaPlayerFactory != null) mediaPlayerFactory.release();
    }

    @Override
    public void toggleFullscreen() {
        if (fullscreenStage == null) enterFullscreen();
        else exitFullscreen();
    }

    public void enterFullscreen() {
        if (fullscreenStage != null) return;
        Platform.runLater(() -> {
            originalParent = (Pane) playerContainer.getParent();
            if (originalParent != null) {
                originalIndex = originalParent.getChildren().indexOf(playerContainer);
                originalParent.getChildren().remove(playerContainer);
            }
            fullscreenStage = new Stage(StageStyle.UNDECORATED);
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(playerContainer, bounds.getWidth(), bounds.getHeight());
            scene.setFill(Color.BLACK);
            fullscreenStage.setScene(scene);
            fullscreenStage.setFullScreen(true);
            fullscreenStage.setFullScreenExitHint("");
            fullscreenStage.setOnCloseRequest(e -> exitFullscreen());
            fullscreenStage.show();
            playerContainer.requestFocus();
            btnFullscreen.setGraphic(fullscreenExitIcon);
        });
    }

    public void exitFullscreen() {
        if (fullscreenStage == null) return;
        Platform.runLater(() -> {
            fullscreenStage.close();
            fullscreenStage = null;
            if (originalParent != null) originalParent.getChildren().add(originalIndex, playerContainer);
            playerContainer.applyCss();
            playerContainer.layout();
            playerContainer.requestLayout();
            playerContainer.requestFocus();
            btnFullscreen.setGraphic(fullscreenIcon);
        });
    }
}