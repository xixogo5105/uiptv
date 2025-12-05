package com.uiptv.ui;

import com.uiptv.api.EmbeddedVideoPlayer;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
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
import javafx.scene.media.Media;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;

public class JavafxEmbeddedVideoPlayer implements EmbeddedVideoPlayer {

    private MediaPlayer mediaPlayer;
    private final MediaView mediaView = new MediaView();

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
    private Button btnReload;
    private Button btnPip;
    private Button btnStop;
    private ImageView playIcon, pauseIcon, stopIcon, repeatOnIcon, repeatOffIcon, fullscreenIcon, fullscreenExitIcon, muteOnIcon, muteOffIcon, reloadIcon, pipIcon, pipExitIcon;

    private boolean isUserSeeking = false;
    private boolean isRepeating = false;
    private PauseTransition idleTimer;
    private final StackPane playerContainer = new StackPane();

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    // PiP bookkeeping
    private Stage pipStage;
    private Pane originalParent;
    private int originalIndex = -1;
    private FadeTransition fadeIn;
    private FadeTransition fadeOut;
    private String currentMediaUri;

    // For custom title bar drag
    private double xOffset = 0;
    private double yOffset = 0;

    // For custom window resizing
    private boolean isResizing = false;
    private int resizeDirection = 0; // 0=none, 1=N, 2=NE, 3=E, 4=SE, 5=S, 6=SW, 7=W, 8=NW
    private double initialX, initialY, initialWidth, initialHeight;
    private static final double RESIZE_BORDER = 5;
    private static final double MIN_WIDTH = 200;
    private static final double MIN_HEIGHT = 150;

    private final ChangeListener<Duration> progressListener;
    private final ChangeListener<MediaPlayer.Status> statusListener;


    public JavafxEmbeddedVideoPlayer() {
        mediaView.setPreserveRatio(true);

        // --- 1.5 LOAD ICONS ---
        loadIcons();

        // --- 2. BUILD CONTROLS ---
        btnPlayPause = createIconButton(pauseIcon);
        btnStop = createIconButton(stopIcon);
        btnRepeat = createIconButton(repeatOffIcon);
        btnRepeat.setOpacity(0.7);
        btnReload = createIconButton(reloadIcon);
        btnFullscreen = createIconButton(fullscreenIcon);
        btnPip = createIconButton(pipIcon);

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnMute = createIconButton(muteOffIcon);

        volumeSlider = new Slider(0, 1, 0.5); // JavaFX volume is 0.0 to 1.0
        volumeSlider.setPrefWidth(100);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(btnPlayPause, btnStop, btnRepeat, btnReload, btnFullscreen, btnPip, spacer, btnMute, volumeSlider);

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

        mediaView.fitWidthProperty().bind(playerContainer.widthProperty());
        mediaView.fitHeightProperty().bind(playerContainer.heightProperty());

        StackPane overlayWrapper = new StackPane(controlsContainer);
        overlayWrapper.setAlignment(Pos.BOTTOM_CENTER);
        overlayWrapper.setPadding(new Insets(0, 20, 20, 20));

        loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(60, 60);
        loadingSpinner.setVisible(false);

        playerContainer.getChildren().addAll(mediaView, overlayWrapper, loadingSpinner);

        // --- 4. EVENT LOGIC ---
        btnPlayPause.setOnAction(e -> {
            if (mediaPlayer != null) {
                if (mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                    mediaPlayer.pause();
                } else {
                    mediaPlayer.play();
                }
            }
        });

        btnStop.setOnAction(e -> stop());

        btnRepeat.setOnAction(e -> {
            isRepeating = !isRepeating;
            btnRepeat.setGraphic(isRepeating ? repeatOnIcon : repeatOffIcon);
            btnRepeat.setOpacity(isRepeating ? 1.0 : 0.7);
        });

        btnReload.setOnAction(e -> {
            if (currentMediaUri != null && !currentMediaUri.isEmpty()) {
                play(currentMediaUri);
            }
        });

        btnFullscreen.setOnAction(e -> toggleFullscreen());
        btnPip.setOnAction(e -> togglePip());

        btnMute.setOnAction(e -> {
            if (mediaPlayer != null) {
                mediaPlayer.setMute(!mediaPlayer.isMute());
            }
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                mediaPlayer.setVolume(newVal.doubleValue());
            }
        });

        timeSlider.setOnMousePressed(e -> isUserSeeking = true);
        timeSlider.setOnMouseReleased(e -> {
            if (mediaPlayer != null && mediaPlayer.getTotalDuration() != null) {
                mediaPlayer.seek(mediaPlayer.getTotalDuration().multiply(timeSlider.getValue()));
            }
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
            volumeSlider.setValue(currentVolume + (delta > 0 ? 0.05 : -0.05));
        });

        // --- JAVAFX MEDIA PLAYER LISTENERS ---
        statusListener = (obs, oldStatus, newStatus) -> {
            Platform.runLater(() -> {
                switch (newStatus) {
                    case PLAYING:
                        loadingSpinner.setVisible(false);
                        btnPlayPause.setGraphic(pauseIcon);
                        fadeIn.play();
                        idleTimer.playFromStart();
                        break;
                    case PAUSED:
                        btnPlayPause.setGraphic(playIcon);
                        break;
                    case STOPPED:
                    case HALTED:
                        btnPlayPause.setGraphic(playIcon);
                        timeSlider.setValue(0);
                        loadingSpinner.setVisible(false);
                        if (newStatus == MediaPlayer.Status.HALTED) {
                            System.err.println("An error occurred in the media player.");
                        }
                        break;
                    case READY:
                        updateTimeLabel();
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
                    if (mediaPlayer != null && mediaPlayer.getTotalDuration() != null) {
                        Duration total = mediaPlayer.getTotalDuration();
                        if (total != null && total.greaterThan(Duration.ZERO) && !total.isIndefinite()) {
                            timeSlider.setValue(newTime.toMillis() / total.toMillis());
                        }
                    }
                    updateTimeLabel();
                });
            }
        };


        // --- 5. FADE / HIDE LOGIC ---
        setupFadeAndIdleLogic();
    }

    private void updateTimeLabel() {
        if (mediaPlayer != null && mediaPlayer.getCurrentTime() != null && mediaPlayer.getTotalDuration() != null) {
            Duration currentTime = mediaPlayer.getCurrentTime();
            Duration totalDuration = mediaPlayer.getTotalDuration();

            if (totalDuration.isIndefinite()) {
                timeLabel.setText(formatTime((long) currentTime.toMillis()) + " / Live");
            } else {
                timeLabel.setText(formatTime((long) currentTime.toMillis()) + " / " + formatTime((long) totalDuration.toMillis()));
            }
        } else {
            timeLabel.setText("00:00 / 00:00");
        }
    }

    private void loadIcons() {
        playIcon = createIconView("play.png", true);
        pauseIcon = createIconView("pause.png", true);
        stopIcon = createIconView("stop.png", true);
        repeatOnIcon = createIconView("repeat-on.png", true);
        repeatOffIcon = createIconView("repeat-off.png", true);
        reloadIcon = createIconView("reload.png", true);
        fullscreenIcon = createIconView("fullscreen.png", true);
        fullscreenExitIcon = createIconView("fullscreen-exit.png", true);
        muteOnIcon = createIconView("mute-on.png", true);
        muteOffIcon = createIconView("mute-off.png", true);
        pipIcon = createIconView("picture-in-picture.png", true);
        pipExitIcon = createIconView("picture-in-picture-exit.png", false);
    }

    private ImageView createIconView(String iconName, boolean applyColorAdjust) {
        try {
            String iconPath = "/icons/videoPlayer/" + iconName;
            java.net.URL resourceUrl = getClass().getResource(iconPath);
            if (resourceUrl == null) {
                return new ImageView();
            }
            Image image = new Image(resourceUrl.toExternalForm());
            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(20);
            imageView.setFitWidth(20);
            imageView.setPreserveRatio(true);

            if (applyColorAdjust) {
                ColorAdjust colorAdjust = new ColorAdjust();
                colorAdjust.setBrightness(0.8);
                imageView.setEffect(colorAdjust);
            } else if (iconName.equals("picture-in-picture-exit.png")) {
                ColorAdjust whiteColorAdjust = new ColorAdjust();
                whiteColorAdjust.setBrightness(1.0);
                whiteColorAdjust.setSaturation(-1.0);
                imageView.setEffect(whiteColorAdjust);
            }
            return imageView;
        } catch (Exception e) {
            return new ImageView();
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
        if (uri == null || uri.isEmpty()) {
            stop();
            return;
        }

        this.currentMediaUri = uri;
        playerContainer.setVisible(true);
        playerContainer.setManaged(true);
        loadingSpinner.setVisible(true);

        // Dispose of old player first
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        try {
            String sourceUrl = uri.trim();
            if (!sourceUrl.startsWith("http") && !sourceUrl.startsWith("file:")) {
                File f = new File(sourceUrl);
                if (f.exists()) sourceUrl = f.toURI().toString();
            }

            Media media = new Media(sourceUrl);
            mediaPlayer = new MediaPlayer(media);
            mediaView.setMediaPlayer(mediaPlayer);

            mediaPlayer.setVolume(volumeSlider.getValue());
            mediaPlayer.muteProperty().addListener((obs, oldMute, newMute) -> {
                btnMute.setGraphic(newMute ? muteOnIcon : muteOffIcon);
            });

            mediaPlayer.statusProperty().addListener(statusListener);
            mediaPlayer.currentTimeProperty().addListener(progressListener);
            mediaPlayer.setOnEndOfMedia(() -> {
                if (isRepeating) {
                    play(currentMediaUri); // Reload the stream
                } else {
                    btnPlayPause.setGraphic(playIcon);
                    mediaPlayer.seek(Duration.ZERO);
                    mediaPlayer.pause();
                }
            });

            mediaPlayer.play();
        } catch (Exception e) {
            System.err.println("Error playing media: " + uri);
            e.printStackTrace();
            loadingSpinner.setVisible(false);
        }
    }

    @Override
    public Node getPlayerContainer() {
        return playerContainer;
    }

    private String formatTime(long millis) {
        if (millis < 0) return "00:00";
        long seconds = millis / 1000, s = seconds % 60, m = (seconds / 60) % 60, h = (seconds / 3600) % 24;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    @Override
    public void stop() {
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);
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
            btnPip.setVisible(false);
            btnPip.setManaged(false);
            btnStop.setVisible(false);
            btnStop.setManaged(false);
        });
    }

    public void exitFullscreen() {
        if (fullscreenStage == null) return;
        Platform.runLater(() -> {
            fullscreenStage.close();
            fullscreenStage = null;
            if (originalParent != null) originalParent.getChildren().add(originalIndex, playerContainer);
            playerContainer.requestFocus();
            btnFullscreen.setGraphic(fullscreenIcon);
            btnPip.setVisible(true);
            btnPip.setManaged(true);
            btnStop.setVisible(true);
            btnStop.setManaged(true);
        });
    }

    public void togglePip() {
        if (pipStage == null) enterPip();
        else exitPip();
    }

    public void enterPip() {
        if (pipStage != null) return;
        Platform.runLater(() -> {
            originalParent = (Pane) playerContainer.getParent();
            if (originalParent != null) {
                originalIndex = originalParent.getChildren().indexOf(playerContainer);
                originalParent.getChildren().remove(playerContainer);
            }

            playerContainer.getChildren().remove(mediaView);

            pipStage = new Stage(StageStyle.UNDECORATED);
            pipStage.setAlwaysOnTop(true);

            HBox titleBar = new HBox();
            titleBar.setAlignment(Pos.CENTER_LEFT);
            titleBar.setPadding(new Insets(5, 10, 5, 10));
            titleBar.setStyle("-fx-background-color: #222;");

            Label titleLabel = new Label("Picture-in-Picture");
            titleLabel.setTextFill(Color.WHITE);
            titleLabel.setStyle("-fx-font-weight: bold;");

            Region titleSpacer = new Region();
            HBox.setHgrow(titleSpacer, Priority.ALWAYS);

            Button closeButton = new Button();
            if (pipExitIcon != null && pipExitIcon.getImage() != null) {
                closeButton.setGraphic(pipExitIcon);
            } else {
                closeButton.setText("X");
                closeButton.setTextFill(Color.WHITE);
            }
            closeButton.setPadding(new Insets(2, 5, 2, 5));
            closeButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
            closeButton.setOnMouseEntered(e -> closeButton.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-cursor: hand; -fx-background-radius: 4;"));
            closeButton.setOnMouseExited(e -> closeButton.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
            closeButton.setOnAction(e -> exitPip());

            titleBar.getChildren().addAll(titleLabel, titleSpacer, closeButton);

            titleBar.setOnMousePressed(event -> {
                xOffset = pipStage.getX() - event.getScreenX();
                yOffset = pipStage.getY() - event.getScreenY();
            });
            titleBar.setOnMouseDragged(event -> {
                pipStage.setX(event.getScreenX() + xOffset);
                pipStage.setY(event.getScreenY() + yOffset);
            });
            titleBar.setOnMouseClicked(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    pipStage.toFront();
                }
            });

            BorderPane pipRoot = new BorderPane();
            pipRoot.setStyle("-fx-background-color: black;");
            pipRoot.setTop(titleBar);
            pipRoot.setCenter(mediaView);

            mediaView.fitWidthProperty().bind(pipRoot.widthProperty());
            mediaView.fitHeightProperty().bind(pipRoot.heightProperty());

            Scene scene = new Scene(pipRoot, 480, 270);
            scene.setFill(Color.TRANSPARENT);
            pipStage.setScene(scene);

            Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
            pipStage.setX(primaryScreenBounds.getMaxX() - 480 - 20);
            pipStage.setY(primaryScreenBounds.getMaxY() - 270 - 20);

            setupPipResizing(pipRoot);

            pipStage.show();
            controlsContainer.setVisible(false);
            controlsContainer.setManaged(false);
            btnPlayPause.setVisible(false);
            btnPlayPause.setManaged(false);
            btnStop.setVisible(false);
            btnStop.setManaged(false);
            btnPip.setGraphic(pipIcon);
        });
    }

    public void exitPip() {
        if (pipStage == null) return;
        Platform.runLater(() -> {
            pipStage.close();
            pipStage = null;

            ((Pane) mediaView.getParent()).getChildren().remove(mediaView);

            if (originalParent != null) {
                originalParent.getChildren().add(originalIndex, playerContainer);
            }

            playerContainer.getChildren().add(0, mediaView);

            playerContainer.setVisible(true);
            playerContainer.setManaged(true);
            mediaView.fitWidthProperty().bind(playerContainer.widthProperty());
            mediaView.fitHeightProperty().bind(playerContainer.heightProperty());

            controlsContainer.setVisible(true);
            controlsContainer.setManaged(true);
            btnPlayPause.setVisible(true);
            btnPlayPause.setManaged(true);
            btnStop.setVisible(true);
            btnStop.setManaged(true);

            playerContainer.requestFocus();
            btnPip.setGraphic(pipIcon);
        });
    }

    private void setupPipResizing(Pane pipRoot) {
        pipRoot.setOnMouseMoved(event -> {
            if (isResizing) return;
            double x = event.getX(), y = event.getY();
            double width = pipStage.getWidth(), height = pipStage.getHeight();
            Cursor cursor = Cursor.DEFAULT;
            resizeDirection = 0;
            if (y < RESIZE_BORDER) { cursor = Cursor.N_RESIZE; resizeDirection = 1; }
            else if (y > height - RESIZE_BORDER) { cursor = Cursor.S_RESIZE; resizeDirection = 5; }
            if (x < RESIZE_BORDER) {
                if (resizeDirection == 1) { cursor = Cursor.NW_RESIZE; resizeDirection = 8; }
                else if (resizeDirection == 5) { cursor = Cursor.SW_RESIZE; resizeDirection = 6; }
                else { cursor = Cursor.W_RESIZE; resizeDirection = 7; }
            } else if (x > width - RESIZE_BORDER) {
                if (resizeDirection == 1) { cursor = Cursor.NE_RESIZE; resizeDirection = 2; }
                else if (resizeDirection == 5) { cursor = Cursor.SE_RESIZE; resizeDirection = 4; }
                else { cursor = Cursor.E_RESIZE; resizeDirection = 3; }
            }
            pipStage.getScene().setCursor(cursor);
        });

        pipRoot.setOnMousePressed(event -> {
            if (event.getButton() == MouseButton.PRIMARY && resizeDirection != 0) {
                isResizing = true;
                initialX = event.getScreenX();
                initialY = event.getScreenY();
                initialWidth = pipStage.getWidth();
                initialHeight = pipStage.getHeight();
            }
        });

        pipRoot.setOnMouseDragged(event -> {
            if (isResizing) {
                double newWidth = initialWidth, newHeight = initialHeight;
                double newX = pipStage.getX(), newY = pipStage.getY();
                double deltaX = event.getScreenX() - initialX;
                double deltaY = event.getScreenY() - initialY;

                if ((resizeDirection & 1) != 0) { newHeight = initialHeight - deltaY; newY = initialY + deltaY; } // N
                if ((resizeDirection & 4) != 0) { newHeight = initialHeight + deltaY; } // S
                if ((resizeDirection & 2) != 0) { newWidth = initialWidth + deltaX; } // E
                if ((resizeDirection & 8) != 0) { newWidth = initialWidth - deltaX; newX = initialX + deltaX; } // W

                if (newWidth < MIN_WIDTH) {
                    if ((resizeDirection & 8) != 0) newX = pipStage.getX() + newWidth - MIN_WIDTH;
                    newWidth = MIN_WIDTH;
                }
                if (newHeight < MIN_HEIGHT) {
                    if ((resizeDirection & 1) != 0) newY = pipStage.getY() + newHeight - MIN_HEIGHT;
                    newHeight = MIN_HEIGHT;
                }
                pipStage.setWidth(newWidth);
                pipStage.setHeight(newHeight);
                pipStage.setX(newX);
                pipStage.setY(newY);
            }
        });

        pipRoot.setOnMouseReleased(event -> {
            isResizing = false;
            resizeDirection = 0;
            pipStage.getScene().setCursor(Cursor.DEFAULT);
        });
    }
}
