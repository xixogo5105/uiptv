// java
package com.uiptv.ui;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Slider;
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

import java.io.File;

public class EmbeddedMediaPlayer {
    // Constants
    private static final double VOLUME_STEP = 0.05;
    private static final double DEFAULT_VOLUME = 0.5;
    private static final String STYLE_BLACK_BACKGROUND = "-fx-background-color: black;";
    private static final Insets CONTROLS_PADDING = new Insets(5, 10, 5, 10);
    private static final double CONTROLS_SPACING = 10;
    private static final double SLIDER_PREF_WIDTH = 100;

    // SVG Icon Paths
    private static final String SVG_PLAY = "M8 5v14l11-7z";
    private static final String SVG_PAUSE = "M6 19h4V5H6v14zm8-14v14h4V5h-4z";
    private static final String SVG_STOP = "M6 6h12v12H6z";
    private static final String SVG_VOLUME_ON = "M3 9v6h4l5 5V4L7 9H3zm13.5 3c0-1.77-1.02-3.29-2.5-4.03v8.05c1.48-.73 2.5-2.25 2.5-4.02zM14 3.23v2.06c2.89.86 5 3.54 5 6.71s-2.11 5.85-5 6.71v2.06c4.01-.91 7-4.49 7-8.77s-2.99-7.86-7-8.77z";
    private static final String SVG_VOLUME_OFF = "M16.5 12c0-1.77-1.02-3.29-2.5-4.03v2.21l2.45 2.45c.03-.2.05-.41.05-.63zm2.5 0c0 .94-.2 1.82-.54 2.64l1.51 1.51C20.63 14.91 21 13.5 21 12c0-4.28-2.99-7.86-7-8.77v2.06c2.89.86 5 3.54 5 6.71zM4.27 3L3 4.27 7.73 9H3v6h4l5 5v-6.73l4.25 4.25c-.67.52-1.42.93-2.25 1.18v2.06c1.38-.31 2.63-.95 3.69-1.81L19.73 21 21 19.73l-9-9L4.27 3zM12 4L9.91 6.09 12 8.18V4z";
    private static final String SVG_FULLSCREEN = "M7 14H5v5h5v-2H7v-3zm-2-4h2V7h3V5H5v5zm12 7h-3v2h5V14h-2v3zM14 5v2h3v3h2V5h-5z";

    // UI Components
    private final VBox rootLayout = new VBox();
    private final StackPane mediaPane = new StackPane();
    private final MediaView mediaView = new MediaView();
    private final HBox controls = new HBox();
    private MediaPlayer mediaPlayer;

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    private Pane originalParent;
    private int originalIndex = -1;

    public EmbeddedMediaPlayer() {
        // Media Pane (video part)
        mediaPane.getChildren().add(mediaView);
        mediaPane.setStyle(STYLE_BLACK_BACKGROUND);
        VBox.setVgrow(mediaPane, Priority.ALWAYS);

        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(mediaPane.widthProperty());
        mediaView.fitHeightProperty().bind(mediaPane.heightProperty());

        // Controls
        createControls();

        // Root VBox layout
        rootLayout.getChildren().addAll(mediaPane, controls);
        rootLayout.setFocusTraversable(true);
        rootLayout.setVisible(false);
        rootLayout.setManaged(false);

        // Event handlers
        rootLayout.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                toggleFullscreen();
            }
            rootLayout.requestFocus();
        });
        rootLayout.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyEvent);
    }

    private void createControls() {
        controls.setAlignment(Pos.CENTER);
        controls.setPadding(CONTROLS_PADDING);
        controls.setSpacing(CONTROLS_SPACING);
        controls.setStyle(STYLE_BLACK_BACKGROUND);

        // Play/Pause Button
        Button playPauseButton = new Button();
        SVGPath playIcon = createSVGIcon(SVG_PLAY);
        SVGPath pauseIcon = createSVGIcon(SVG_PAUSE);
        playPauseButton.setGraphic(playIcon);
        playPauseButton.setOnAction(e -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                } else {
                    mediaPlayer.play();
                }
            }
        });

        // Stop Button
        Button stopButton = new Button();
        stopButton.setGraphic(createSVGIcon(SVG_STOP));
        stopButton.setOnAction(e -> stop());

        // Mute Button
        Button muteButton = new Button();
        SVGPath volumeOnIcon = createSVGIcon(SVG_VOLUME_ON);
        SVGPath volumeOffIcon = createSVGIcon(SVG_VOLUME_OFF);
        muteButton.setGraphic(volumeOnIcon);
        muteButton.setOnAction(e -> toggleMute());

        // Volume Slider
        Slider volumeSlider = new Slider();
        volumeSlider.setPrefWidth(SLIDER_PREF_WIDTH);
        volumeSlider.setMaxWidth(Region.USE_PREF_SIZE);
        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue() / 100.0);
            }
        });

        // Fullscreen Button
        Button fullscreenButton = new Button();
        fullscreenButton.setGraphic(createSVGIcon(SVG_FULLSCREEN));
        fullscreenButton.setOnAction(e -> toggleFullscreen());

        controls.getChildren().clear();
        controls.getChildren().addAll(playPauseButton, stopButton, muteButton, volumeSlider, fullscreenButton);

        // Listeners to update control states
        if (mediaPlayer != null) {
            mediaPlayer.statusProperty().addListener((obs, oldStatus, newStatus) -> {
                playPauseButton.setGraphic(newStatus == MediaPlayer.Status.PLAYING ? pauseIcon : playIcon);
            });
            mediaPlayer.volumeProperty().addListener((obs, oldVal, newVal) -> {
                if (!volumeSlider.isValueChanging()) {
                    volumeSlider.setValue(newVal.doubleValue() * 100);
                }
            });
            mediaPlayer.muteProperty().addListener((obs, wasMuted, isMuted) -> {
                muteButton.setGraphic(isMuted ? volumeOffIcon : volumeOnIcon);
            });
            mediaPlayer.setOnReady(() -> {
                volumeSlider.setValue(mediaPlayer.getVolume() * 100);
                muteButton.setGraphic(mediaPlayer.isMute() ? volumeOffIcon : volumeOnIcon);
            });
        }
    }

    private SVGPath createSVGIcon(String path) {
        SVGPath svg = new SVGPath();
        svg.setContent(path);
        svg.setFill(Color.WHITE);
        return svg;
    }

    private void handleKeyEvent(KeyEvent event) {
        KeyCode code = event.getCode();

        if (code == KeyCode.F) {
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
        } else if (code == KeyCode.ESCAPE && fullscreenStage != null) {
            exitFullscreen();
            event.consume();
        }
    }

    public Node getPlayerContainer() {
        return rootLayout;
    }

    public void play(String source) {
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
                if (f.exists()) {
                    uri = f.toURI().toString();
                }
            }

            Media media = new Media(uri);
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);
            mediaPlayer.setVolume(DEFAULT_VOLUME);

            createControls();

            rootLayout.setManaged(true);
            rootLayout.setVisible(true);

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
            rootLayout.setVisible(false);
            rootLayout.setManaged(false);
        });
    }

    public void toggleFullscreen() {
        if (fullscreenStage == null) {
            enterFullscreen();
        } else {
            exitFullscreen();
        }
    }

    public void enterFullscreen() {
        if (fullscreenStage != null) return;

        Platform.runLater(() -> {
            originalParent = (Pane) mediaView.getParent();
            if (originalParent != null) {
                originalIndex = originalParent.getChildren().indexOf(mediaView);
                originalParent.getChildren().remove(mediaView);
            }

            fullscreenStage = new Stage(StageStyle.UNDECORATED);
            StackPane root = new StackPane(mediaView);
            root.setStyle(STYLE_BLACK_BACKGROUND);
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            Scene scene = new Scene(root, bounds.getWidth(), bounds.getHeight());

            mediaView.fitWidthProperty().unbind();
            mediaView.fitHeightProperty().unbind();
            mediaView.fitWidthProperty().bind(scene.widthProperty());
            mediaView.fitHeightProperty().bind(scene.heightProperty());

            scene.setOnKeyPressed(this::handleKeyEvent);
            scene.setOnMouseClicked(event -> {
                if (event.getClickCount() == 2) {
                    toggleFullscreen();
                }
            });

            fullscreenStage.setScene(scene);
            fullscreenStage.setFullScreen(true);
            fullscreenStage.setFullScreenExitHint("");
            fullscreenStage.setOnCloseRequest(e -> exitFullscreen());
            fullscreenStage.show();
        });
    }

    public void exitFullscreen() {
        if (fullscreenStage == null) return;

        Platform.runLater(() -> {
            mediaView.fitWidthProperty().unbind();
            mediaView.fitHeightProperty().unbind();

            fullscreenStage.close();
            fullscreenStage = null;

            if (originalParent != null) {
                int insertIndex = (originalIndex >= 0 && originalIndex <= originalParent.getChildren().size())
                        ? originalIndex
                        : originalParent.getChildren().size();
                originalParent.getChildren().add(insertIndex, mediaView);
            }

            mediaView.fitWidthProperty().bind(mediaPane.widthProperty());
            mediaView.fitHeightProperty().bind(mediaPane.heightProperty());

            originalParent = null;
            originalIndex = -1;
        });
    }

    private void toggleMute() {
        if (mediaPlayer != null) {
            mediaPlayer.setMute(!mediaPlayer.isMute());
        }
    }

    private void increaseVolume() {
        if (mediaPlayer != null) {
            double vol = clamp(mediaPlayer.getVolume() + VOLUME_STEP, 0.0, 1.0);
            mediaPlayer.setVolume(vol);
        }
    }

    private void decreaseVolume() {
        if (mediaPlayer != null) {
            double vol = clamp(mediaPlayer.getVolume() - VOLUME_STEP, 0.0, 1.0);
            mediaPlayer.setVolume(vol);
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}