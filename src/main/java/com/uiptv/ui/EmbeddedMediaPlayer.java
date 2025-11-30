// java
package com.uiptv.ui;

import javafx.animation.FadeTransition;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.SVGPath;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.scene.Cursor; // Added import

import java.io.File;
import java.util.concurrent.atomic.AtomicBoolean;

public class EmbeddedMediaPlayer {
    // Constants
    private static final double VOLUME_STEP = 0.05;
    private static final double DEFAULT_VOLUME = 0.5;
    private static final String STYLE_BLACK_BACKGROUND = "-fx-background-color: black;";
    private static final String STYLE_CONTROLS_BACKGROUND = "-fx-background-color: rgba(0, 0, 0, 0.6);";
    private static final String STYLE_BUTTON_TOGGLED = "-fx-background-color: darkgreen;";
    private static final Insets CONTROLS_PADDING = new Insets(5, 10, 5, 10);
    private static final double CONTROLS_SPACING = 10;
    private static final Duration AUTO_HIDE_DELAY = Duration.seconds(3);

    // SVG Icon Paths
    private static final String SVG_PLAY = "M8 5v14l11-7z";
    private static final String SVG_PAUSE = "M6 19h4V5H6v14zm8-14v14h4V5h-4z";
    private static final String SVG_STOP = "M6 6h12v12H6z";
    private static final String SVG_RELOAD = "M17.65 6.35C16.2 4.9 14.21 4 12 4c-4.42 0-7.99 3.58-7.99 8s3.57 8 7.99 8c3.73 0 6.84-2.55 7.73-6h-2.08c-.82 2.33-3.04 4-5.65 4-3.31 0-6-2.69-6-6s2.69-6 6-6c1.66 0 3.14.69 4.22 1.78L13 11h7V4l-2.35 2.35z";
    private static final String SVG_VOLUME_ON = "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z";
    private static final String SVG_VOLUME_OFF = "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z";
    private static final String SVG_FULLSCREEN = "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5V14h-2v3zM14 5v2h3v3h2V5h-5z";

    // UI Components
    private final StackPane playerContainer = new StackPane();
    private final MediaView mediaView = new MediaView();
    private final HBox controls = new HBox();
    private MediaPlayer mediaPlayer;
    private String currentSource;
    private final AtomicBoolean isReloading = new AtomicBoolean(false);

    // Control Buttons
    private Button playPauseButton;
    private Button stopButton;
    private Button reloadButton;
    private Button muteButton;
    private Button fullscreenButton;

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    private Pane originalParent;
    private int originalIndex = -1;

    // Fade transitions and auto-hide timer
    private FadeTransition fadeInControls;
    private FadeTransition fadeOutControls;
    private Timeline autoHideTimer;

    public EmbeddedMediaPlayer() {
        createControls();
        controls.setOpacity(0);

        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(playerContainer.widthProperty());
        mediaView.fitHeightProperty().bind(playerContainer.heightProperty());

        playerContainer.getChildren().addAll(mediaView, controls);
        playerContainer.setStyle(STYLE_BLACK_BACKGROUND);
        StackPane.setAlignment(controls, Pos.BOTTOM_CENTER);
        playerContainer.setFocusTraversable(true);
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);

        setupHoverFade();
        playerContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                toggleFullscreen();
            }
            playerContainer.requestFocus();
        });
        playerContainer.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyEvent);
    }

    private void setupHoverFade() {
        fadeInControls = new FadeTransition(Duration.millis(200), controls);
        fadeInControls.setToValue(1.0);
        fadeOutControls = new FadeTransition(Duration.millis(200), controls);
        fadeOutControls.setToValue(0);
        fadeOutControls.setOnFinished(e -> {
            if (fullscreenStage != null) {
                hideMouseCursor();
            }
        });

        autoHideTimer = new Timeline(new KeyFrame(AUTO_HIDE_DELAY, e -> fadeOutControls.play()));
        autoHideTimer.setCycleCount(1);

        playerContainer.setOnMouseMoved(e -> {
            fadeOutControls.stop();
            fadeInControls.play();
            autoHideTimer.stop();
            autoHideTimer.playFromStart();
            if (fullscreenStage != null) {
                showMouseCursor();
            }
        });
        playerContainer.setOnMouseExited(e -> {
            autoHideTimer.stop();
            fadeOutControls.play();
        });
    }

    private void createControls() {
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(CONTROLS_PADDING);
        controls.setSpacing(CONTROLS_SPACING);
        controls.setStyle(STYLE_CONTROLS_BACKGROUND);

        playPauseButton = new Button();
        stopButton = new Button();
        reloadButton = new Button();
        muteButton = new Button();
        fullscreenButton = new Button();

        playPauseButton.setGraphic(createSVGIcon(SVG_PLAY));
        stopButton.setGraphic(createSVGIcon(SVG_STOP));
        reloadButton.setGraphic(createSVGIcon(SVG_RELOAD));
        muteButton.setGraphic(createSVGIcon(SVG_VOLUME_ON));
        fullscreenButton.setGraphic(createSVGIcon(SVG_FULLSCREEN));

        controls.getChildren().clear();
        // Reload button is now the first from the left
        controls.getChildren().addAll(reloadButton, playPauseButton, stopButton, muteButton, fullscreenButton);
    }

    private void attachListeners() {
        playPauseButton.setOnAction(e -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) mediaPlayer.pause();
                else mediaPlayer.play();
            }
        });
        stopButton.setOnAction(e -> stop());
        reloadButton.setOnAction(e -> reloadStream());
        muteButton.setOnAction(e -> toggleMute());
        fullscreenButton.setOnAction(e -> toggleFullscreen());

        mediaPlayer.statusProperty().addListener((obs, o, n) -> updateButtonStyles());
        mediaPlayer.muteProperty().addListener((obs, o, n) -> updateButtonStyles());
        mediaPlayer.setOnReady(this::updateButtonStyles);
    }

    private void reloadStream() {
        if (currentSource == null || currentSource.trim().isEmpty() || !isReloading.compareAndSet(false, true)) {
            return;
        }

        reloadButton.setStyle(STYLE_BUTTON_TOGGLED);
        reloadButton.setDisable(true);

        play(currentSource);

        new Thread(() -> {
            try {
                Thread.sleep(2000); // 2-second delay
            } catch (InterruptedException ignored) {
            }
            Platform.runLater(() -> {
                reloadButton.setStyle("");
                reloadButton.setDisable(false);
                isReloading.set(false);
            });
        }).start();
    }

    private void updateButtonStyles() {
        if (mediaPlayer == null) return;

        boolean isPlaying = mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING;
        playPauseButton.setGraphic(isPlaying ? createSVGIcon(SVG_PAUSE) : createSVGIcon(SVG_PLAY));
        playPauseButton.setStyle(isPlaying ? "" : STYLE_BUTTON_TOGGLED);

        boolean isMuted = mediaPlayer.isMute();
        muteButton.setGraphic(isMuted ? createSVGIcon(SVG_VOLUME_OFF) : createSVGIcon(SVG_VOLUME_ON));
        muteButton.setStyle(isMuted ? STYLE_BUTTON_TOGGLED : "");

        fullscreenButton.setStyle(fullscreenStage != null ? STYLE_BUTTON_TOGGLED : "");
    }

    private SVGPath createSVGIcon(String path) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFill(Color.WHITE);
        return svg;
    }

    private void handleKeyEvent(KeyEvent event) {
        KeyCode code = event.getCode();
        if (code == KeyCode.F || (code == KeyCode.ESCAPE && fullscreenStage != null)) {
            toggleFullscreen();
            event.consume();
        } else if (code == KeyCode.M) {
            toggleMute();
            event.consume();
        } else if (code == KeyCode.UP) {
            increaseVolume();
            event.consume();
        } else if (code == KeyCode.DOWN) {
            decreaseVolume();
            event.consume();
        }
    }

    public Node getPlayerContainer() {
        return playerContainer;
    }

    public void play(String source) {
        this.currentSource = source;
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
            }
            if (source == null || source.trim().isEmpty()) {
                stop();
                return;
            }

            String uri = source.trim();
            if (!uri.startsWith("http://") && !uri.startsWith("https://") && !uri.startsWith("file:")) {
                File f = new File(uri);
                if (f.exists()) uri = f.toURI().toString();
            }

            Media media = new Media(uri);
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            mediaPlayer.setVolume(DEFAULT_VOLUME);

            attachListeners();

            playerContainer.setManaged(true);
            playerContainer.setVisible(true);

            mediaPlayer.play();
        } catch (Exception e) {
            stop();
        }
    }

    public void stop() {
        try {
            if (mediaPlayer != null) {
                mediaPlayer.stop();
                mediaPlayer.dispose();
                mediaPlayer = null;
                mediaView.setMediaPlayer(null);
            }
        } catch (Exception ignored) {
        }
        Platform.runLater(() -> {
            playerContainer.setVisible(false);
            playerContainer.setManaged(false);
        });
    }

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

            // Attach mouse moved listener to the fullscreen scene
            scene.setOnMouseMoved(e -> {
                fadeOutControls.stop();
                fadeInControls.play();
                autoHideTimer.stop();
                autoHideTimer.playFromStart();
                showMouseCursor(); // Show cursor when mouse moves in fullscreen
            });
            scene.setOnMouseExited(e -> {
                autoHideTimer.stop();
                fadeOutControls.play();
            });

            fullscreenStage.show();
            updateButtonStyles();
            // Ensure controls are visible when entering fullscreen
            fadeInControls.play();
            autoHideTimer.playFromStart();
            hideMouseCursor(); // Hide cursor initially when entering fullscreen
        });
    }

    public void exitFullscreen() {
        if (fullscreenStage == null) return;

        Platform.runLater(() -> {
            fullscreenStage.close();
            fullscreenStage = null;

            if (originalParent != null) {
                originalParent.getChildren().add(originalIndex, playerContainer);
            }
            updateButtonStyles();
            // Stop auto-hide timer when exiting fullscreen
            autoHideTimer.stop();
            // Re-apply normal mouse listeners (already set on playerContainer)
            // Ensure controls are visible briefly after exiting fullscreen
            fadeInControls.play();
            autoHideTimer.playFromStart();
            showMouseCursor(); // Show cursor when exiting fullscreen
        });
    }

    private void toggleMute() {
        if (mediaPlayer != null) mediaPlayer.setMute(!mediaPlayer.isMute());
    }

    private void increaseVolume() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isMute()) {
                mediaPlayer.setMute(false);
            }
            double currentVolume = mediaPlayer.getVolume();
            mediaPlayer.setVolume(clamp(currentVolume + VOLUME_STEP, 0.0, 1.0));
        }
    }

    private void decreaseVolume() {
        if (mediaPlayer != null) {
            if (mediaPlayer.isMute()) {
                mediaPlayer.setMute(false);
            }
            double currentVolume = mediaPlayer.getVolume();
            mediaPlayer.setVolume(clamp(currentVolume - VOLUME_STEP, 0.0, 1.0));
        }
    }

    private void hideMouseCursor() {
        if (fullscreenStage != null && fullscreenStage.getScene() != null) {
            fullscreenStage.getScene().setCursor(Cursor.NONE);
        }
    }

    private void showMouseCursor() {
        if (fullscreenStage != null && fullscreenStage.getScene() != null) {
            fullscreenStage.getScene().setCursor(Cursor.DEFAULT);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}