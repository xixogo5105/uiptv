// java
package com.uiptv.ui;

import javafx.application.Platform;
import javafx.geometry.Rectangle2D;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.File;

public class EmbeddedMediaPlayer {
    private final StackPane playerContainer = new StackPane();
    private final MediaView mediaView = new MediaView();
    private MediaPlayer mediaPlayer;

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    private Pane originalParent;
    private int originalIndex = -1;

    // change increment: 5% per key press
    private static final double VOLUME_STEP = 0.05;

    public EmbeddedMediaPlayer() {
        playerContainer.getChildren().add(mediaView);
        playerContainer.setFocusTraversable(true);
        playerContainer.setStyle("-fx-background-color: black;");

        // Initially hide the player container until something is played.
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);

        mediaView.setPreserveRatio(true);
        mediaView.fitWidthProperty().bind(playerContainer.widthProperty());
        mediaView.fitHeightProperty().bind(playerContainer.heightProperty());

        playerContainer.addEventHandler(MouseEvent.MOUSE_CLICKED, event -> {
            if (event.getClickCount() == 2) {
                toggleFullscreen();
            }
            playerContainer.requestFocus();
        });

        playerContainer.addEventHandler(KeyEvent.KEY_PRESSED, this::handleKeyEvent);
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
        return playerContainer;
    }

    public void setMediaPlayer(MediaPlayer mediaPlayer) {
        this.mediaPlayer = mediaPlayer;
        this.mediaView.setMediaPlayer(mediaPlayer);
    }

    public MediaPlayer getMediaPlayer() {
        return mediaPlayer;
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
            mediaPlayer.setVolume(0.5);

            playerContainer.setManaged(true);
            playerContainer.setVisible(true);

            mediaPlayer.play();
        } catch (Exception e) {
            stop(); // Hide player on error
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
            root.setStyle("-fx-background-color: black;");
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

            mediaView.fitWidthProperty().bind(playerContainer.widthProperty());
            mediaView.fitHeightProperty().bind(playerContainer.heightProperty());

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
            if (mediaPlayer.isMute() && vol > 0) {
                mediaPlayer.setMute(false);
            }
        }
    }

    private void decreaseVolume() {
        if (mediaPlayer != null) {
            double vol = clamp(mediaPlayer.getVolume() - VOLUME_STEP, 0.0, 1.0);
            mediaPlayer.setVolume(vol);
            if (vol == 0.0 && !mediaPlayer.isMute()) {
                mediaPlayer.setMute(true);
            }
        }
    }

    private static double clamp(double v, double min, double max) {
        return Math.max(min, Math.min(max, v));
    }
}