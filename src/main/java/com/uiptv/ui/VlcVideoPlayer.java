package com.uiptv.ui;

import com.uiptv.api.VideoPlayerInterface;
import javafx.animation.FadeTransition;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
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
import javafx.scene.image.*;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import uk.co.caprica.vlcj.factory.MediaPlayerFactory;
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


public class VlcVideoPlayer implements VideoPlayerInterface {
    private EmbeddedMediaPlayer mediaPlayer;

    // UI Components
    private Slider timeSlider;
    private Slider volumeSlider;
    private Label timeLabel;
    private VBox controlsContainer;
    private ProgressIndicator loadingSpinner;
    private Label errorLabel;

    // Buttons and Icons
    private Button btnPlayPause;
    private Button btnMute;
    private Button btnRepeat;
    private Button btnFullscreen;
    private Button btnReload;
    private Button btnPip; // New PiP button
    private Button btnStop; // Declared as a class member
    private ImageView playIcon, pauseIcon, stopIcon, repeatOnIcon, repeatOffIcon, fullscreenIcon, fullscreenExitIcon, muteOnIcon, muteOffIcon, reloadIcon, pipIcon, pipExitIcon; // New PiP icons

    private boolean isUserSeeking = false;
    private PauseTransition idleTimer;
    private StackPane playerContainer = new StackPane();

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    // PiP bookkeeping
    private Stage pipStage;
    private Pane originalParent;
    private int originalIndex = -1;
    private final ImageView videoImageView;
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

    private WritableImage videoImage;
    private WritablePixelFormat<ByteBuffer> pixelFormat;


    public VlcVideoPlayer() {
        // --- 1. VLCJ SETUP ---
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
        videoImageView = new ImageView();
        videoImageView.setPreserveRatio(true);
        mediaPlayer.videoSurface().set(new FXCallbackVideoSurface()); // Changed to use CallbackVideoSurface
        mediaPlayer.controls().setRepeat(false);
        mediaPlayer.audio().setMute(true); // Explicitly mute in constructor

        // --- 1.5 LOAD ICONS ---
        loadIcons();

        // --- 2. BUILD CONTROLS ---
        btnPlayPause = createIconButton(pauseIcon);
        btnStop = createIconButton(stopIcon); // Now assigns to the class member
        btnRepeat = createIconButton(repeatOffIcon);
        btnRepeat.setOpacity(0.7);
        btnReload = createIconButton(reloadIcon);
        btnFullscreen = createIconButton(fullscreenIcon);
        btnPip = createIconButton(pipIcon); // Initialize PiP button

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnMute = createIconButton(muteOnIcon); // Set to muteOnIcon initially

        volumeSlider = new Slider(0, 200, 100);
        volumeSlider.setPrefWidth(100);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(btnPlayPause, btnStop, btnRepeat, btnReload, btnFullscreen, btnPip, spacer, btnMute, volumeSlider); // Added btnPip

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

        errorLabel = new Label();
        errorLabel.setTextFill(Color.WHITE);
        errorLabel.setWrapText(true);
        errorLabel.setStyle("-fx-font-size: 14px; -fx-background-color: rgba(0, 0, 0, 0.6); -fx-padding: 10; -fx-background-radius: 5;");
        errorLabel.setVisible(false);
        StackPane.setAlignment(errorLabel, Pos.CENTER);

        playerContainer.getChildren().addAll(videoImageView, overlayWrapper, loadingSpinner, errorLabel);

        // --- 4. EVENT LOGIC ---
        btnPlayPause.setOnAction(_ -> {
            if (mediaPlayer.status().isPlaying()) {
                mediaPlayer.controls().pause();
            } else {
                mediaPlayer.controls().play();
            }
        });

        btnStop.setOnAction(_ -> stop());

        btnRepeat.setOnAction(_ -> {
            boolean isRepeating = !mediaPlayer.controls().getRepeat();
            mediaPlayer.controls().setRepeat(isRepeating);
            btnRepeat.setGraphic(isRepeating ? repeatOnIcon : repeatOffIcon);
            btnRepeat.setOpacity(isRepeating ? 1.0 : 0.7);
        });

        btnReload.setOnAction(_ -> {
            if (currentMediaUri != null && !currentMediaUri.isEmpty()) {
                play(currentMediaUri);
            }
        });

        btnFullscreen.setOnAction(_ -> toggleFullscreen());

        btnPip.setOnAction(_ -> togglePip()); // PiP button action

        btnMute.setOnAction(_ -> {
            boolean isMuted = !mediaPlayer.audio().isMute();
            mediaPlayer.audio().setMute(isMuted);
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        });

        volumeSlider.valueProperty().addListener((_, _, newVal) -> mediaPlayer.audio().setVolume(newVal.intValue()));

        timeSlider.setOnMousePressed(_ -> {
            isUserSeeking = true;
            idleTimer.stop(); // Keep controls visible while seeking
        });
        timeSlider.setOnMouseReleased(_ -> {
            mediaPlayer.controls().setPosition((float) timeSlider.getValue());
            isUserSeeking = false;
            idleTimer.playFromStart(); // Restart idle timer
        });

        // Add mouse pressed/released handlers for volumeSlider to control idleTimer
        volumeSlider.setOnMousePressed(_ -> idleTimer.stop());
        volumeSlider.setOnMouseReleased(_ -> idleTimer.playFromStart());

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
            if (delta == 0) return;
            // Invert the delta for "natural" scrolling, then multiply by a step value.
            // This ensures scroll up always increases volume and scroll down always decreases.
            double change = Math.signum(delta) * 5;
            volumeSlider.setValue(volumeSlider.getValue() + change);
        });

        // --- VLC EVENTS ---
        mediaPlayer.events().addMediaPlayerEventListener(new MediaPlayerEventAdapter() {
            @Override
            public void playing(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    loadingSpinner.setVisible(false);
                    btnPlayPause.setGraphic(pauseIcon);
                    fadeIn.play();
                    idleTimer.playFromStart();
                });
            }

            @Override
            public void paused(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> btnPlayPause.setGraphic(playIcon));
            }

            @Override
            public void positionChanged(MediaPlayer mediaPlayer, float newPosition) {
                if (!isUserSeeking) Platform.runLater(() -> timeSlider.setValue(newPosition));
            }

            @Override
            public void timeChanged(MediaPlayer mediaPlayer, long newTime) {
                Platform.runLater(() -> timeLabel.setText(formatTime(newTime) + " / " + formatTime(mediaPlayer.status().length())));
            }

            @Override
            public void finished(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> btnPlayPause.setGraphic(playIcon));
            }

            @Override
            public void stopped(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    btnPlayPause.setGraphic(playIcon);
                    timeSlider.setValue(0);
                    loadingSpinner.setVisible(false);
                });
            }

            @Override
            public void error(MediaPlayer mediaPlayer) {
                Platform.runLater(() -> {
                    loadingSpinner.setVisible(false);
                    System.err.println("An error occurred in the media player.");
                    errorLabel.setText("Could not play video.\nUnsupported format or network error.");
                    errorLabel.setVisible(true);
                });
            }
        });

        // --- 5. FADE / HIDE LOGIC ---
        setupFadeAndIdleLogic();
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
        pipExitIcon = createIconView("picture-in-picture-exit.png", false); // Load without ColorAdjust
    }

    private ImageView createIconView(String iconName, boolean applyColorAdjust) {
        try {
            String iconPath = "/icons/videoPlayer/" + iconName;
            java.net.URL resourceUrl = getClass().getResource(iconPath);
            if (resourceUrl == null) {
                return new ImageView(); // Return empty view if resource not found
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
                // Apply a ColorAdjust to make the pipExitIcon white
                ColorAdjust whiteColorAdjust = new ColorAdjust();
                whiteColorAdjust.setBrightness(1.0); // Max brightness
                whiteColorAdjust.setSaturation(-1.0); // Fully desaturate to remove original color tint
                imageView.setEffect(whiteColorAdjust);
            }

            return imageView;
        } catch (Exception e) {
            return new ImageView(); // Return empty view on error
        }
    }

    private Button createIconButton(ImageView icon) {
        Button btn = new Button();
        btn.setGraphic(icon);
        btn.setPadding(new Insets(6));
        btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;");
        btn.setOnMouseEntered(_ -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-cursor: hand; -fx-background-radius: 4;"));
        btn.setOnMouseExited(_ -> btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
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
        idleTimer.setOnFinished(_ -> fadeOut.play());

        // Show controls when mouse moves over the player
        playerContainer.setOnMouseMoved(_ -> {
            if (controlsContainer.getOpacity() < 1.0) {
                fadeIn.play();
            }
            idleTimer.playFromStart();
        });

        // Hide controls when mouse exits the player
        playerContainer.setOnMouseExited(_ -> {
            if (!controlsContainer.isHover()) { // Only fade out if mouse is not over controls
                idleTimer.playFromStart();
            }
        });

        // Keep controls visible when mouse is over them
        controlsContainer.setOnMouseEntered(_ -> idleTimer.stop());
        controlsContainer.setOnMouseExited(_ -> idleTimer.playFromStart());
    }

    @Override
    public void play(String uri) {
        if (uri != null && !uri.isEmpty()) {
            this.currentMediaUri = uri;
            playerContainer.setVisible(true);
            playerContainer.setManaged(true);
            playerContainer.setMinHeight(275);
            loadingSpinner.setVisible(true);
            errorLabel.setVisible(false);

            boolean isMuted = mediaPlayer.audio().isMute(); // Get current mute state
            mediaPlayer.audio().setMute(isMuted); // Apply current mute state (redundant but harmless if already set)
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon); // Update button graphic

            mediaPlayer.audio().setVolume((int) volumeSlider.getValue()); // Set initial volume
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
            playerContainer.setMinHeight(0);
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
            fullscreenStage.setOnCloseRequest(_ -> exitFullscreen()); // Removed unused parameter 'e'
            fullscreenStage.show();
            playerContainer.requestFocus();
            btnFullscreen.setGraphic(fullscreenExitIcon);
            // Hide PiP button when entering fullscreen
            btnPip.setVisible(false);
            btnPip.setManaged(false);
            // Hide stop button when entering fullscreen
            btnStop.setVisible(false);
            btnStop.setManaged(false);
        });
    }

    public void exitFullscreen() {
        if (fullscreenStage == null) return;
        Platform.runLater(() -> {
            if (fullscreenStage != null) fullscreenStage.close();
            fullscreenStage = null;
            if (originalParent != null) originalParent.getChildren().add(originalIndex, playerContainer);
            playerContainer.applyCss();
            playerContainer.layout();
            playerContainer.requestLayout();
            playerContainer.requestFocus();
            btnFullscreen.setGraphic(fullscreenIcon);
            // Show PiP button when exiting fullscreen
            btnPip.setVisible(true);
            btnPip.setManaged(true);
            // Show stop button when exiting fullscreen
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
            // Store original parent and remove playerContainer from it
            originalParent = (Pane) playerContainer.getParent();
            if (originalParent != null) {
                originalIndex = originalParent.getChildren().indexOf(playerContainer);
                originalParent.getChildren().remove(playerContainer);
            }

            // Remove videoImageView from playerContainer before moving it to PiP stage
            playerContainer.getChildren().remove(videoImageView);

            // Create new stage for PiP
            pipStage = new Stage(StageStyle.UNDECORATED);
            pipStage.setAlwaysOnTop(true);

            // Create a StackPane for the PiP window to hold videoImageView and the restore button
            StackPane pipRoot = new StackPane();
            pipRoot.setStyle("-fx-background-color: black;");

            // Create the restore button that appears on hover
            Button restoreButton = new Button();
            if (pipExitIcon != null && pipExitIcon.getImage() != null) {
                // Make the icon larger for better visibility in the center
                ImageView restoreIconView = new ImageView(pipExitIcon.getImage());
                restoreIconView.setFitHeight(64);
                restoreIconView.setFitWidth(64);
                // Apply the same white color adjust effect
                ColorAdjust whiteColorAdjust = new ColorAdjust();
                whiteColorAdjust.setBrightness(1.0);
                whiteColorAdjust.setSaturation(-1.0);
                restoreIconView.setEffect(whiteColorAdjust);
                restoreButton.setGraphic(restoreIconView);
            } else {
                restoreButton.setText("Restore"); // Fallback
                restoreButton.setTextFill(Color.WHITE);
            }
            restoreButton.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6); -fx-background-radius: 50em; -fx-cursor: hand;");
            restoreButton.setPadding(new Insets(15));
            restoreButton.setVisible(false); // Initially hidden
            restoreButton.setOnAction(_ -> exitPip());

            // Add video and button to the root
            pipRoot.getChildren().addAll(videoImageView, restoreButton);
            StackPane.setAlignment(restoreButton, Pos.CENTER);

            // Show/hide restore button on hover
            pipRoot.setOnMouseEntered(_ -> restoreButton.setVisible(true));
            pipRoot.setOnMouseExited(_ -> restoreButton.setVisible(false));

            // Bind videoImageView size to pipRoot size
            videoImageView.fitWidthProperty().bind(pipRoot.widthProperty());
            videoImageView.fitHeightProperty().bind(pipRoot.heightProperty());

            Scene scene = new Scene(pipRoot, 480, 270); // Default PiP size
            scene.setFill(Color.TRANSPARENT);
            pipStage.setScene(scene);

            // Position PiP window
            Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
            pipStage.setX(primaryScreenBounds.getMaxX() - 480 - 20);
            pipStage.setY(primaryScreenBounds.getMaxY() - 270 - 20);

            // --- Combined Dragging and Resizing Logic ---
            pipRoot.setOnMouseMoved(event -> {
                if (isResizing) return;

                double x = event.getX();
                double y = event.getY();
                double width = pipStage.getWidth();
                double height = pipStage.getHeight();

                Cursor cursor = Cursor.DEFAULT;
                resizeDirection = 0;

                if (y < RESIZE_BORDER) { // North
                    cursor = Cursor.N_RESIZE;
                    resizeDirection = 1;
                } else if (y > height - RESIZE_BORDER) { // South
                    cursor = Cursor.S_RESIZE;
                    resizeDirection = 5;
                }

                if (x < RESIZE_BORDER) { // West
                    if (resizeDirection == 1) {
                        cursor = Cursor.NW_RESIZE;
                        resizeDirection = 8;
                    } else if (resizeDirection == 5) {
                        cursor = Cursor.SW_RESIZE;
                        resizeDirection = 6;
                    } else {
                        cursor = Cursor.W_RESIZE;
                        resizeDirection = 7;
                    }
                } else if (x > width - RESIZE_BORDER) { // East
                    if (resizeDirection == 1) {
                        cursor = Cursor.NE_RESIZE;
                        resizeDirection = 2;
                    } else if (resizeDirection == 5) {
                        cursor = Cursor.SE_RESIZE;
                        resizeDirection = 4;
                    } else {
                        cursor = Cursor.E_RESIZE;
                        resizeDirection = 3;
                    }
                }
                pipStage.getScene().setCursor(cursor);
            });

            pipRoot.setOnMousePressed(event -> {
                if (event.getButton() == MouseButton.PRIMARY) {
                    if (resizeDirection != 0) {
                        isResizing = true;
                        initialX = event.getScreenX();
                        initialY = event.getScreenY();
                        initialWidth = pipStage.getWidth();
                        initialHeight = pipStage.getHeight();
                    } else {
                        xOffset = pipStage.getX() - event.getScreenX();
                        yOffset = pipStage.getY() - event.getScreenY();
                    }
                }
            });

            pipRoot.setOnMouseDragged(event -> {
                if (isResizing) {
                    double newWidth = initialWidth;
                    double newHeight = initialHeight;
                    double newX = pipStage.getX();
                    double newY = pipStage.getY();

                    double deltaX = event.getScreenX() - initialX;
                    double deltaY = event.getScreenY() - initialY;

                    switch (resizeDirection) {
                        case 1: // N
                            newHeight = initialHeight - deltaY;
                            newY = initialY + deltaY;
                            break;
                        case 2: // NE
                            newWidth = initialWidth + deltaX;
                            newHeight = initialHeight - deltaY;
                            newY = initialY + deltaY;
                            break;
                        case 3: // E
                            newWidth = initialWidth + deltaX;
                            break;
                        case 4: // SE
                            newWidth = initialWidth + deltaX;
                            newHeight = initialHeight + deltaY;
                            break;
                        case 5: // S
                            newHeight = initialHeight + deltaY;
                            break;
                        case 6: // SW
                            newWidth = initialWidth - deltaX;
                            newX = initialX + deltaX;
                            newHeight = initialHeight + deltaY;
                            break;
                        case 7: // W
                            newWidth = initialWidth - deltaX;
                            newX = initialX + deltaX;
                            break;
                        case 8: // NW
                            newWidth = initialWidth - deltaX;
                            newX = initialX + deltaX;
                            newHeight = initialHeight - deltaY;
                            newY = initialY + deltaY;
                            break;
                    }

                    if (newWidth < MIN_WIDTH) {
                        if (resizeDirection == 7 || resizeDirection == 6 || resizeDirection == 8) { // West side resize
                            newX = pipStage.getX() + (pipStage.getWidth() - MIN_WIDTH);
                        }
                        newWidth = MIN_WIDTH;
                    }
                    if (newHeight < MIN_HEIGHT) {
                        if (resizeDirection == 1 || resizeDirection == 2 || resizeDirection == 8) { // North side resize
                            newY = pipStage.getY() + (pipStage.getHeight() - MIN_HEIGHT);
                        }
                        newHeight = MIN_HEIGHT;
                    }

                    pipStage.setWidth(newWidth);
                    pipStage.setHeight(newHeight);
                    pipStage.setX(newX);
                    pipStage.setY(newY);
                } else { // Dragging
                    pipStage.setX(event.getScreenX() + xOffset);
                    pipStage.setY(event.getScreenY() + yOffset);
                }
            });

            pipRoot.setOnMouseReleased(_ -> {
                isResizing = false;
                resizeDirection = 0;
                pipStage.getScene().setCursor(Cursor.DEFAULT);
            });
            // --- End Resizing Logic ---

            pipStage.show();

            // Hide controls in PiP mode
            controlsContainer.setVisible(false);
            controlsContainer.setManaged(false);

            // Hide play/pause and stop buttons
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

            // Remove videoImageView from its current parent (pipRoot)
            // This is important because pipRoot is about to be garbage collected
            ((Pane) videoImageView.getParent()).getChildren().remove(videoImageView);

            // Re-add playerContainer to its original parent
            if (originalParent != null) {
                originalParent.getChildren().add(originalIndex, playerContainer);
            } else {
                System.err.println("Original parent was null when exiting PiP. Cannot re-add playerContainer.");
            }

            // Re-add videoImageView back to playerContainer
            // Ensure it's added before other overlays like controlsContainer
            playerContainer.getChildren().addFirst(videoImageView); // Changed to addFirst()

            // Ensure videoImageView is correctly re-parented and sized
            playerContainer.setVisible(true);
            playerContainer.setManaged(true);
            videoImageView.fitWidthProperty().bind(playerContainer.widthProperty());
            videoImageView.fitHeightProperty().bind(playerContainer.heightProperty());

            // Show controls again
            controlsContainer.setVisible(true);
            controlsContainer.setManaged(true);

            // Show play/pause and stop buttons
            btnPlayPause.setVisible(true);
            btnPlayPause.setManaged(true);
            btnStop.setVisible(true);
            btnStop.setManaged(true);

            playerContainer.applyCss();
            playerContainer.layout();
            playerContainer.requestLayout();
            playerContainer.requestFocus();
            btnPip.setGraphic(pipIcon);
        });
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

    private static class FXBufferFormatCallback implements BufferFormatCallback {
        @Override
        public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
            return new RV32BufferFormat(sourceWidth, sourceHeight);
        }

        @Override
        public void newFormatSize(int newWidth, int newHeight, int sarNumerator, int sarDenominator) {
            // No-op, as the BufferFormat is returned by getBufferFormat
        }

        @Override
        public void allocatedBuffers(ByteBuffer[] buffers) {
            // No-op
        }
    }

    private class FXRenderCallback implements RenderCallback {
        @Override
        public void display(MediaPlayer mediaPlayer, ByteBuffer[] nativeBuffers, BufferFormat bufferFormat, int sarNum, int sarDen) {
            if (videoImage == null || videoImage.getWidth() != bufferFormat.getWidth() || videoImage.getHeight() != bufferFormat.getHeight()) {
                videoImage = new WritableImage(bufferFormat.getWidth(), bufferFormat.getHeight());
                videoImageView.setImage(videoImage);
                pixelFormat = PixelFormat.getByteBgraPreInstance();
            }
            Platform.runLater(() -> videoImage.getPixelWriter().setPixels(0, 0, bufferFormat.getWidth(), bufferFormat.getHeight(), pixelFormat, nativeBuffers[0], bufferFormat.getPitches()[0]));
        }

        @Override
        public void lock(MediaPlayer mediaPlayer) {
            // No-op, typically used to acquire a lock before rendering
        }

        @Override
        public void unlock(MediaPlayer mediaPlayer) {
            // No-op, typically used to release locks acquired during rendering
        }

    }
}
