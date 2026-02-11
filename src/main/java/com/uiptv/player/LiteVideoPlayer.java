package com.uiptv.player;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.PlayerService;
import javafx.animation.KeyFrame;
import javafx.animation.PauseTransition;
import javafx.animation.Timeline;
import javafx.application.Platform;
import javafx.beans.value.ChangeListener;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.*;
import javafx.scene.media.Media;
import javafx.scene.media.MediaException;
import javafx.scene.media.MediaPlayer;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URL;

import static com.uiptv.util.StringUtils.isBlank;

public class LiteVideoPlayer implements VideoPlayerInterface {

    private MediaPlayer mediaPlayer;
    private final MediaView mediaView = new MediaView();
    private Timeline inactivityTimer;
    private Account currentAccount;
    private Channel currentChannel;
    private int retryCount = 0;

    // UI Components
    private Slider timeSlider;
    private Slider volumeSlider;
    private Label timeLabel;
    private VBox controlsContainer;
    private ProgressIndicator loadingSpinner;
    private Label errorLabel; // Added for displaying errors

    // Buttons and Icons
    private Button btnPlayPause;
    private Button btnMute;
    private Button btnRepeat;
    private Button btnFullscreen;
    private Button btnReload;
    private Button btnPip;
    private Button btnStop;
    private Button btnAspectRatio; // Changed from MenuButton to Button
    private ImageView playIcon, pauseIcon, stopIcon, repeatOnIcon, repeatOffIcon, fullscreenIcon, fullscreenExitIcon, muteOnIcon, muteOffIcon, reloadIcon, pipIcon, pipExitIcon, aspectRatioIcon, aspectRatioStretchIcon;

    private boolean isUserSeeking = false;
    private boolean isRepeating = false;
    private final StackPane playerContainer = new StackPane();

    // Fullscreen bookkeeping
    private Stage fullscreenStage;
    // PiP bookkeeping
    private Stage pipStage;
    private Pane originalParent;
    private int originalIndex = -1;
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
    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/58.0.3029.110 Safari/537.36";

    private boolean isMuted = true; // Track mute state
    private int aspectRatioMode = 0; // 0=Fit (Default), 1=Stretch


    public LiteVideoPlayer() {
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
        btnAspectRatio = createIconButton(aspectRatioIcon); // Initialize aspect ratio button
        btnAspectRatio.setTooltip(new Tooltip("Fit"));

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnMute = createIconButton(muteOnIcon); // Set to muteOnIcon initially

        volumeSlider = new Slider(0, 100, 50); // Use 0-100 scale
        volumeSlider.setPrefWidth(100);

        HBox topRow = new HBox(8);
        topRow.setAlignment(Pos.CENTER_LEFT);
        topRow.getChildren().addAll(btnPlayPause, btnStop, btnRepeat, btnReload, btnFullscreen, btnPip, spacer, btnMute, volumeSlider, btnAspectRatio);

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

        playerContainer.getChildren().addAll(mediaView, overlayWrapper, loadingSpinner, errorLabel);

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
            retryCount = 0;
            refreshAndPlay();
        });

        btnFullscreen.setOnAction(e -> toggleFullscreen());
        btnPip.setOnAction(e -> togglePip());

        btnAspectRatio.setOnAction(e -> toggleAspectRatio());

        btnMute.setOnAction(e -> {
            isMuted = !isMuted; // Toggle internal state
            if (mediaPlayer != null) {
                mediaPlayer.setMute(isMuted); // Apply to player
            }
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon); // Update button graphic
        });

        volumeSlider.valueProperty().addListener((obs, oldVal, newVal) -> {
            if (mediaPlayer != null) {
                // Map 0-100 slider range to 0.0-1.0 volume range
                mediaPlayer.setVolume(newVal.doubleValue() / 100.0);
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
            if (delta == 0) return;
            // This ensures scroll up always increases volume and scroll down always decreases.
            double change = Math.signum(delta) * 5;
            volumeSlider.setValue(volumeSlider.getValue() + change);
        });

        // --- JAVAFX MEDIA PLAYER LISTENERS ---
        statusListener = (obs, oldStatus, newStatus) -> {
            Platform.runLater(() -> {
                switch (newStatus) {
                    case PLAYING:
                        retryCount = 0;
                        loadingSpinner.setVisible(false);
                        btnPlayPause.setGraphic(pauseIcon);
                        break;
                    case PAUSED:
                        btnPlayPause.setGraphic(playIcon);
                        break;
                    case STOPPED:
                        btnPlayPause.setGraphic(playIcon);
                        timeSlider.setValue(0);
                        loadingSpinner.setVisible(false);
                        break;
                    case HALTED:
                        loadingSpinner.setVisible(false);
                        // The specific error is handled by the onError handler,
                        // but we ensure the UI is cleaned up.
                        if (!errorLabel.isVisible()) {
                            errorLabel.setText("An error occurred during playback.");
                            errorLabel.setVisible(true);
                        }
                        break;
                    case READY:
                        updateTimeLabel();
                        updateVideoSize();
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

    private void handleRepeat() {
        if (retryCount < 5) {
            retryCount++;
            if (retryCount == 1) {
                refreshAndPlay();
            } else {
                PauseTransition delay = new PauseTransition(Duration.seconds(10));
                delay.setOnFinished(e -> refreshAndPlay());
                delay.play();
            }
        } else {
            stop();
            Platform.runLater(() -> {
                errorLabel.setText("Failed to reconnect after 5 attempts.");
                errorLabel.setVisible(true);
            });
        }
    }

    private void refreshAndPlay() {
        if (currentAccount != null && currentChannel != null) {
            loadingSpinner.setVisible(true);
            errorLabel.setVisible(false);
            new Thread(() -> {
                try {
                    final PlayerResponse newResponse = PlayerService.getInstance().get(currentAccount, currentChannel);
                    Platform.runLater(() -> play(newResponse, true));
                } catch (IOException e) {
                    e.printStackTrace();
                    Platform.runLater(() -> {
                        loadingSpinner.setVisible(false);
                        errorLabel.setText("Could not refresh stream.");
                        errorLabel.setVisible(true);
                        if (isRepeating) {
                            handleRepeat();
                        }
                    });
                }
            }).start();
        } else {
            // Fallback to old behavior if account/channel not available
            if (currentMediaUri != null && !currentMediaUri.isEmpty()) {
                play(new PlayerResponse(currentMediaUri), true);
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
        mediaView.fitWidthProperty().unbind();
        mediaView.fitHeightProperty().unbind();

        double containerWidth = playerContainer.getWidth();
        double containerHeight = playerContainer.getHeight();

        if (aspectRatioMode == 1) {
            // Stretch to Fill
            mediaView.setFitWidth(containerWidth);
            mediaView.setFitHeight(containerHeight);
            mediaView.setPreserveRatio(false);
        } else {
            // Default: Fit within (Contain)
            mediaView.setFitWidth(containerWidth);
            mediaView.setFitHeight(containerHeight);
            mediaView.setPreserveRatio(true);
        }
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
        aspectRatioIcon = createIconView("aspect-ratio.png", true);
        aspectRatioStretchIcon = createIconView("aspect-ratio-stretch.png", true);
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
        // Timer to hide controls after 5 seconds of inactivity.
        inactivityTimer = new Timeline(new KeyFrame(Duration.seconds(5), event -> {
            // Only hide controls if the video is playing.
            if (mediaPlayer != null && mediaPlayer.getStatus() == MediaPlayer.Status.PLAYING) {
                controlsContainer.setVisible(false);
                // In fullscreen, also hide the mouse cursor.
                if (fullscreenStage != null) {
                    playerContainer.setCursor(Cursor.NONE);
                }
            }
        }));
        inactivityTimer.setCycleCount(1); // Run once per trigger

        // Event handler for mouse movement.
        playerContainer.setOnMouseMoved(event -> {
            // Show controls and restore cursor.
            controlsContainer.setVisible(true);
            if (playerContainer.getCursor() != Cursor.DEFAULT) {
                playerContainer.setCursor(Cursor.DEFAULT);
            }
            // Restart the inactivity timer.
            inactivityTimer.playFromStart();
        });

        // When the mouse enters, we also want to show controls and start the timer.
        playerContainer.setOnMouseEntered(event -> {
            controlsContainer.setVisible(true);
            if (playerContainer.getCursor() != Cursor.DEFAULT) {
                playerContainer.setCursor(Cursor.DEFAULT);
            }
            inactivityTimer.playFromStart();
        });

        // When the mouse exits, we have different behavior for fullscreen.
        playerContainer.setOnMouseExited(event -> {
            // If not in fullscreen, hide controls immediately.
            if (fullscreenStage == null) {
                controlsContainer.setVisible(false);
                inactivityTimer.stop();
            }
        });

        // Initially, the controls are visible, and we start the timer.
        controlsContainer.setVisible(true);
        inactivityTimer.play();
    }

    private String getFinalUrl(String url) throws Exception {
        HttpURLConnection con = (HttpURLConnection) new URL(url).openConnection();
        con.setInstanceFollowRedirects(true);
        con.setRequestProperty("User-Agent", USER_AGENT);
        con.connect();
        return con.getURL().toString();
    }

    @Override
    public void play(PlayerResponse response) {
        play(response, false);
    }

    private void play(PlayerResponse response, boolean isInternalRetry) {
        if (!isInternalRetry) {
            retryCount = 0;
        }

        String uri = null;
        if (response != null) {
            uri = response.getUrl();
            this.currentAccount = response.getAccount();
            this.currentChannel = response.getChannel();
        }

        if (isBlank(uri)) {
            stop();
            return;
        }
        this.currentMediaUri = uri.replace("extension=ts", "extension=m3u8");
        playerContainer.setVisible(true);
        playerContainer.setManaged(true);
        playerContainer.setMinHeight(275);
        loadingSpinner.setVisible(true);
        errorLabel.setVisible(false); // Hide error from previous attempt

        // Dispose of old player first
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
        }

        new Thread(() -> {
            try {
                String sourceUrl = currentMediaUri.trim();
                if (sourceUrl.startsWith("http")) {
                    sourceUrl = getFinalUrl(sourceUrl);
                } else if (!sourceUrl.startsWith("file:")) {
                    File f = new File(sourceUrl);
                    if (f.exists()) sourceUrl = f.toURI().toString();
                }

                final String finalSourceUrl = sourceUrl;
                Platform.runLater(() -> {
                    try {
                        Media media = new Media(finalSourceUrl);
                        mediaPlayer = new MediaPlayer(media);
                        mediaView.setMediaPlayer(mediaPlayer);

                        mediaPlayer.setOnError(() -> {
                            final MediaException me = mediaPlayer.getError();
                            Platform.runLater(() -> {
                                System.err.println("MediaPlayer Error: " + me.getMessage() + " (" + me.getType() + ")");
                                errorLabel.setText("Could not play video.\nUnsupported format or network error.");
                                errorLabel.setVisible(true);
                                loadingSpinner.setVisible(false);
                                if (isRepeating) {
                                    handleRepeat();
                                }
                            });
                        });

                        // Set initial volume and mute state
                        mediaPlayer.setVolume(volumeSlider.getValue() / 100.0);
                        mediaPlayer.setMute(isMuted); // Apply the persistent mute state
                        btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon); // Update button graphic
                        mediaPlayer.muteProperty().addListener((obs, oldMute, newMute) -> {
                            isMuted = newMute; // Keep internal state in sync
                            btnMute.setGraphic(newMute ? muteOnIcon : muteOffIcon);
                        });

                        mediaPlayer.statusProperty().addListener(statusListener);
                        mediaPlayer.currentTimeProperty().addListener(progressListener);
                        mediaPlayer.setOnEndOfMedia(() -> {
                            if (isRepeating) {
                                handleRepeat(); // Reload the stream
                            } else {
                                btnPlayPause.setGraphic(playIcon);
                                mediaPlayer.seek(Duration.ZERO);
                                mediaPlayer.pause();
                            }
                        });

                        mediaPlayer.play();
                    } catch (Exception e) {
                        handlePlaybackError("Error creating media player.", e);
                    }
                });
            } catch (Exception e) {
                handlePlaybackError("Error resolving media URL.", e);
            }
        }).start();
    }

    private void handlePlaybackError(String message, Exception e) {
        Platform.runLater(() -> {
            System.err.println(message + ": " + e.getMessage());
            e.printStackTrace();
            loadingSpinner.setVisible(false);
            errorLabel.setText("Could not load video.\nInvalid path or network issue.");
            errorLabel.setVisible(true);
        });
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
        retryCount = 0;
        if (mediaPlayer != null) {
            mediaPlayer.stop();
            mediaPlayer.dispose();
            mediaPlayer = null;
        }
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);
        playerContainer.setMinHeight(0);
        btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
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
            // Store original parent and remove playerContainer from it
            originalParent = (Pane) playerContainer.getParent();
            if (originalParent != null) {
                originalIndex = originalParent.getChildren().indexOf(playerContainer);
                originalParent.getChildren().remove(playerContainer);
            }

            // Remove mediaView from playerContainer before moving it to PiP stage
            playerContainer.getChildren().remove(mediaView);

            // Create new stage for PiP
            pipStage = new Stage(StageStyle.UNDECORATED);
            pipStage.setAlwaysOnTop(true);

            // Create a StackPane for the PiP window to hold mediaView and the restore button
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
            pipRoot.getChildren().addAll(mediaView, restoreButton, pipControls);
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

            // Bind mediaView size to pipRoot size
            mediaView.fitWidthProperty().bind(pipRoot.widthProperty());
            mediaView.fitHeightProperty().bind(pipRoot.heightProperty());

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

    @Override
    public PlayerType getType() {
        return PlayerType.LITE;
    }
}
