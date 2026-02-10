package com.uiptv.player;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.PlayerService;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
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

import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.StringUtils.isNotBlank;


public class VlcVideoPlayer implements VideoPlayerInterface {
    private EmbeddedMediaPlayer mediaPlayer;
    private boolean isMuted = true; // Single source of truth for mute state
    private Account currentAccount;
    private Channel currentChannel;
    private boolean isRepeating = false;

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
    private Button btnAspectRatio; // Changed from MenuButton to Button
    private ImageView playIcon, pauseIcon, stopIcon, repeatOnIcon, repeatOffIcon, fullscreenIcon, fullscreenExitIcon, muteOnIcon, muteOffIcon, reloadIcon, pipIcon, pipExitIcon, aspectRatioIcon, aspectRatioStretchIcon; // New PiP icons

    private boolean isUserSeeking = false;
    private PauseTransition idleTimer; // Re-introducing idleTimer for fullscreen mouse/overlay hide
    private StackPane playerContainer = new StackPane();

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    private boolean isFullscreen = false; // Track fullscreen state
    // PiP bookkeeping
    private Stage pipStage;
    private Pane originalParent;
    private int originalIndex = -1;
    private final ImageView videoImageView;
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
    private int aspectRatioMode = 0; // 0=Fit (Default), 1=Stretch
    private int videoSourceWidth, videoSourceHeight, videoSarNum, videoSarDen;


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
        videoImageView.setPreserveRatio(true); // Default to preserve ratio
        mediaPlayer.videoSurface().set(new FXCallbackVideoSurface()); // Changed to use CallbackVideoSurface
        mediaPlayer.controls().setRepeat(false);
        mediaPlayer.audio().setMute(isMuted); // Explicitly mute in constructor

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
        btnAspectRatio = createIconButton(aspectRatioIcon); // Initialize aspect ratio button
        btnAspectRatio.setTooltip(new Tooltip("Fit"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnMute = createIconButton(muteOnIcon); // Set to muteOnIcon initially

        volumeSlider = new Slider(0, 150, 75);
        volumeSlider.setPrefWidth(100);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(btnPlayPause, btnStop, btnRepeat, btnReload, btnFullscreen, btnPip, spacer, btnMute, volumeSlider, btnAspectRatio); // Added btnPip

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

        playerContainer.widthProperty().addListener((obs, oldVal, newVal) -> updateVideoSize());
        playerContainer.heightProperty().addListener((obs, oldVal, newVal) -> updateVideoSize());

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
        btnPlayPause.setOnAction(e -> {
            if (mediaPlayer.status().isPlaying()) {
                mediaPlayer.controls().pause();
            } else {
                mediaPlayer.controls().play();
            }
        });

        btnStop.setOnAction(e -> stop());

        btnRepeat.setOnAction(e -> {
            isRepeating = !isRepeating;
            btnRepeat.setGraphic(isRepeating ? repeatOnIcon : repeatOffIcon);
            btnRepeat.setOpacity(isRepeating ? 1.0 : 0.7);
            mediaPlayer.controls().setRepeat(false); // Always disable vlcj's own repeat
        });

        btnReload.setOnAction(e -> refreshAndPlay());

        btnFullscreen.setOnAction(e -> toggleFullscreen());

        btnPip.setOnAction(e -> togglePip()); // PiP button action

        btnAspectRatio.setOnAction(e -> toggleAspectRatio());

        btnMute.setOnAction(e -> {
            isMuted = !isMuted;
            mediaPlayer.audio().setMute(isMuted);
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        });

        volumeSlider.valueProperty().addListener((e, t, newVal) -> mediaPlayer.audio().setVolume(newVal.intValue()));

        timeSlider.setOnMousePressed(e -> {
            isUserSeeking = true;
            if (isFullscreen) idleTimer.stop(); // Keep controls visible while seeking in fullscreen
        });
        timeSlider.setOnMouseReleased(e -> {
            mediaPlayer.controls().setPosition((float) timeSlider.getValue());
            isUserSeeking = false;
            if (isFullscreen) idleTimer.playFromStart(); // Restart idle timer after seeking
        });

        // Add mouse pressed/released handlers for volumeSlider to control idleTimer
        volumeSlider.setOnMousePressed(e -> {
            if (isFullscreen) idleTimer.stop();
        });
        volumeSlider.setOnMouseReleased(e -> {
            if (isFullscreen) idleTimer.playFromStart();
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
                    updateVideoSize(); // Ensure video size is correct on playback start
                    if (isFullscreen) idleTimer.playFromStart(); // Start idle timer when playback begins in fullscreen
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
                Platform.runLater(() -> {
                    btnPlayPause.setGraphic(playIcon);
                    if (isRepeating) {
                        refreshAndPlay();
                    }
                });
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

    private void refreshAndPlay() {
        if (currentAccount != null && currentChannel != null) {
            loadingSpinner.setVisible(true);
            errorLabel.setVisible(false);
            new Thread(() -> {
                try {
                    final PlayerResponse newResponse = PlayerService.getInstance().get(currentAccount, currentChannel);
                    Platform.runLater(() -> play(newResponse));
                } catch (IOException e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        loadingSpinner.setVisible(false);
                        errorLabel.setText("Could not refresh stream.");
                        errorLabel.setVisible(true);
                    });
                }
            }).start();
        } else {
            // Fallback to old behavior if account/channel not available
            if (currentMediaUri != null && !currentMediaUri.isEmpty()) {
                play(new PlayerResponse(currentMediaUri));
            }
        }
    }

    private void toggleAspectRatio() {
        aspectRatioMode = (aspectRatioMode + 1) % 2; // Now only 2 modes
        String tooltipText;
        ImageView icon;
        if (aspectRatioMode == 1) {
            tooltipText = "Stretch";
            icon = aspectRatioStretchIcon;
        } else {
            tooltipText = "Fit";
            icon = aspectRatioIcon;
        }
        btnAspectRatio.setGraphic(icon);
        if (btnAspectRatio.getTooltip() != null) {
            btnAspectRatio.getTooltip().setText(tooltipText);
        }
        updateVideoSize();
    }

    private void updateVideoSize() {
        if (playerContainer.getWidth() <= 0 || playerContainer.getHeight() <= 0) {
            return;
        }

        // Unbind properties to prevent RuntimeException when setting them manually
        videoImageView.fitWidthProperty().unbind();
        videoImageView.fitHeightProperty().unbind();

        double containerWidth = playerContainer.getWidth();
        double containerHeight = playerContainer.getHeight();

        if (aspectRatioMode == 1) {
            // Stretch to Fill
            videoImageView.setFitWidth(containerWidth);
            videoImageView.setFitHeight(containerHeight);
            videoImageView.setPreserveRatio(false);
        } else {
            // Default: Fit within (Contain)
            videoImageView.setFitWidth(containerWidth);
            videoImageView.setFitHeight(containerHeight);
            videoImageView.setPreserveRatio(true);
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
        pipExitIcon = createIconView("picture-in-picture-exit.png", false); // Load without ColorAdjust
        aspectRatioIcon = createIconView("aspect-ratio.png", true);
        aspectRatioStretchIcon = createIconView("aspect-ratio-stretch.png", true);
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
        btn.setOnMouseEntered(e -> btn.setStyle("-fx-background-color: rgba(255,255,255,0.2); -fx-cursor: hand; -fx-background-radius: 4;"));
        btn.setOnMouseExited(e -> btn.setStyle("-fx-background-color: transparent; -fx-cursor: hand;"));
        return btn;
    }

    private void setupFadeAndIdleLogic() {
        controlsContainer.setVisible(false);

        idleTimer = new PauseTransition(Duration.seconds(5));
        idleTimer.setOnFinished(e -> {
            if (isFullscreen) {
                controlsContainer.setVisible(false);
                if (playerContainer.getScene() != null) {
                    playerContainer.getScene().setCursor(Cursor.NONE);
                }
            }
        });

        playerContainer.setOnMouseMoved(e -> {
            if (isFullscreen) {
                controlsContainer.setVisible(true);
                if (playerContainer.getScene() != null) {
                    playerContainer.getScene().setCursor(Cursor.DEFAULT);
                }
                idleTimer.playFromStart();
            } else {
                controlsContainer.setVisible(true);
            }
        });

        playerContainer.setOnMouseExited(e -> {
            if (isFullscreen) {
                idleTimer.playFromStart(); // Start timer to hide controls/cursor
            } else {
                controlsContainer.setVisible(false);
            }
        });

        controlsContainer.setOnMouseEntered(e -> {
            if (isFullscreen) {
                idleTimer.stop(); // Keep controls visible if mouse is over them
            }
        });
        controlsContainer.setOnMouseExited(e -> {
            if (isFullscreen) {
                idleTimer.playFromStart(); // Start timer to hide controls/cursor
            }
        });
    }

    @Override
    public void play(PlayerResponse response) {
        String uri = null;
        if (response != null) {
            uri = response.getUrl();
            this.currentAccount = response.getAccount();
            this.currentChannel = response.getChannel();
        }

        if (isNotBlank(uri)) {
            this.currentMediaUri = uri;
            playerContainer.setVisible(true);
            playerContainer.setManaged(true);
            playerContainer.setMinHeight(275);
            loadingSpinner.setVisible(true);
            errorLabel.setVisible(false);

            mediaPlayer.audio().setMute(isMuted); // Apply persistent mute state
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon); // Update button graphic

            mediaPlayer.audio().setVolume((int) volumeSlider.getValue()); // Set initial volume
            updateVideoSize(); // Apply the last selected aspect ratio
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
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);

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
            // Hide PiP button when entering fullscreen
            btnPip.setVisible(false);
            btnPip.setManaged(false);
            // Hide stop button when entering fullscreen
            btnStop.setVisible(false);
            btnStop.setManaged(false);

            isFullscreen = true;
            controlsContainer.setVisible(true); // Show controls initially
            if (playerContainer.getScene() != null) {
                playerContainer.getScene().setCursor(Cursor.DEFAULT); // Show cursor initially
            }
            idleTimer.playFromStart(); // Start idle timer
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

            isFullscreen = false;
            idleTimer.stop(); // Stop idle timer
            controlsContainer.setVisible(false); // Hide controls on exit
            if (playerContainer.getScene() != null) {
                playerContainer.getScene().setCursor(Cursor.DEFAULT); // Restore default cursor
            }
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
            restoreButton.setOnAction(e -> exitPip());

            // Create mute button for PiP
            Button pipMuteButton = new Button();
            ImageView pipMuteIcon = new ImageView(isMuted ? muteOnIcon.getImage() : muteOffIcon.getImage());
            pipMuteIcon.setFitHeight(20);
            pipMuteIcon.setFitWidth(20);
            ColorAdjust whiteColorAdjust = new ColorAdjust();
            whiteColorAdjust.setBrightness(1.0);
            pipMuteIcon.setEffect(whiteColorAdjust);
            pipMuteButton.setGraphic(pipMuteIcon);
            pipMuteButton.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6); -fx-background-radius: 50em; -fx-cursor: hand;");
            pipMuteButton.setPadding(new Insets(8));
            pipMuteButton.setVisible(false);
            pipMuteButton.setOnAction(e -> {
                btnMute.fire(); // Toggle mute using main button logic
                pipMuteIcon.setImage(isMuted ? muteOnIcon.getImage() : muteOffIcon.getImage());
            });

            // Create reload button for PiP
            Button pipReloadButton = new Button();
            ImageView pipReloadIcon = new ImageView(reloadIcon.getImage());
            pipReloadIcon.setFitHeight(20);
            pipReloadIcon.setFitWidth(20);
            pipReloadIcon.setEffect(whiteColorAdjust);
            pipReloadButton.setGraphic(pipReloadIcon);
            pipReloadButton.setStyle("-fx-background-color: rgba(0, 0, 0, 0.6); -fx-background-radius: 50em; -fx-cursor: hand;");
            pipReloadButton.setPadding(new Insets(8));
            pipReloadButton.setVisible(false);
            pipReloadButton.setOnAction(e -> refreshAndPlay());

            HBox pipControls = new HBox(10);
            pipControls.setAlignment(Pos.TOP_RIGHT);
            pipControls.setPadding(new Insets(10));
            pipControls.getChildren().addAll(pipReloadButton, pipMuteButton);
            pipControls.setPickOnBounds(false); // Allow clicks to pass through transparent areas

            StackPane.setAlignment(pipControls, Pos.TOP_RIGHT);

            // Add video and buttons to the root
            pipRoot.getChildren().addAll(videoImageView, restoreButton, pipControls);
            StackPane.setAlignment(restoreButton, Pos.CENTER);

            // Show/hide buttons on hover
            pipRoot.setOnMouseEntered(e -> {
                restoreButton.setVisible(true);
                pipMuteButton.setVisible(true);
                pipReloadButton.setVisible(true);
            });
            pipRoot.setOnMouseExited(e -> {
                restoreButton.setVisible(false);
                pipMuteButton.setVisible(false);
                pipReloadButton.setVisible(false);
            });

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

            pipRoot.setOnMouseReleased(event -> {
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

    private class FXBufferFormatCallback implements BufferFormatCallback {
        @Override
        public BufferFormat getBufferFormat(int sourceWidth, int sourceHeight) {
            return new RV32BufferFormat(sourceWidth, sourceHeight);
        }

        @Override
        public void newFormatSize(int newWidth, int newHeight, int sarNumerator, int sarDenominator) {
            VlcVideoPlayer.this.videoSourceWidth = newWidth;
            VlcVideoPlayer.this.videoSourceHeight = newHeight;
            VlcVideoPlayer.this.videoSarNum = sarNumerator;
            VlcVideoPlayer.this.videoSarDen = sarDenominator;
            Platform.runLater(VlcVideoPlayer.this::updateVideoSize);
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
