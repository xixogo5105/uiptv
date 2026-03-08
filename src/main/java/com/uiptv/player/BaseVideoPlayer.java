package com.uiptv.player;

import com.uiptv.util.I18n;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.service.PlayerService;
import com.uiptv.util.StyleClassDecorator;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.geometry.Side;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.effect.ColorAdjust;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.InputEvent;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import javafx.scene.layout.*;
import javafx.scene.media.MediaView;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;
import javafx.scene.text.TextFlow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.event.EventHandler;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import static com.uiptv.util.StringUtils.isNotBlank;

public abstract class BaseVideoPlayer implements VideoPlayerInterface {

    // State
    protected boolean isMuted = true;
    protected Account currentAccount;
    protected Channel currentChannel;
    protected boolean isRepeating = false;
    protected int retryCount = 0;
    protected final AtomicBoolean isRetrying = new AtomicBoolean(false);
    protected String currentMediaUri;
    protected static final int ASPECT_RATIO_FIT = 0;
    protected static final int ASPECT_RATIO_FILL = 1;
    protected static final int ASPECT_RATIO_STRETCH = 2;
    protected int aspectRatioMode = ASPECT_RATIO_FIT; // 0=Fit, 1=Fill (Zoom), 2=Stretch
    protected boolean isUserSeeking = false;

    // UI Components
    protected Slider timeSlider;
    protected Slider volumeSlider;
    protected Label timeLabel;
    protected VBox controlsContainer;
    protected ProgressIndicator loadingSpinner;
    protected Label errorLabel;
    protected TextFlow nowShowingFlow;
    protected Text streamInfoText;
    protected StackPane playerContainer = new StackPane();

    // Buttons and Icons
    protected Button btnPlayPause;
    protected Button btnMute;
    protected Button btnRepeat;
    protected Button btnFullscreen;
    protected Button btnReload;
    protected Button btnPip;
    protected Button btnStop;
    protected Button btnAspectRatio;
    protected Button btnHideBar;
    protected Button btnTracks;
    protected ContextMenu tracksContextMenu;
    protected ImageView playIcon, pauseIcon, stopIcon, repeatOnIcon, repeatOffIcon, fullscreenIcon, fullscreenExitIcon, muteOnIcon, muteOffIcon, reloadIcon, pipIcon, pipExitIcon, aspectRatioIcon, aspectRatioFillIcon, aspectRatioStretchIcon, hideBarIcon;

    // Fullscreen & PiP
    protected Stage fullscreenStage;
    protected StackPane fullscreenRoot;
    protected boolean isFullscreen = false;
    protected Stage pipStage;
    protected Pane originalParent;
    protected int originalIndex = -1;
    protected PauseTransition idleTimer;
    protected boolean isControlBarHiddenByUser = false;
    protected boolean isPointerInsidePlayer = false;
    protected StackPane hiddenBarMessage; // Changed from HBox to StackPane
    protected PauseTransition hiddenBarMessageHideTimer;
    protected static boolean hasShownHiddenBarMessage = false;
    protected boolean isTracksMenuOpen = false;
    protected Scene activeInputRecoveryScene;
    protected Scene pipInputRecoveryScene;
    private final EventHandler<InputEvent> sceneInputRecoveryHandler = event -> handleSceneInputRecovery(event);

    // Resizing Logic
    protected boolean isResizing = false;
    protected int resizeDirection = 0;
    protected double initialX, initialY, initialWidth, initialHeight;
    protected double xOffset = 0;
    protected double yOffset = 0;
    protected static final double RESIZE_BORDER = 5;
    protected static final double MIN_WIDTH = 200;
    protected static final double MIN_HEIGHT = 150;

    protected static class TrackOption {
        final int id;
        final String label;

        TrackOption(int id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    public BaseVideoPlayer() {
        loadIcons();
        buildUI();
        setupEventHandlers();
        setupFadeAndIdleLogic();
        playerContainer.sceneProperty().addListener((obs, oldScene, newScene) -> {
            if (oldScene != newScene) {
                uninstallSceneInputRecovery(oldScene);
                installSceneInputRecovery(newScene);
            }
        });
    }

    // --- Abstract Methods ---
    protected abstract Node getVideoView();
    protected abstract void playMedia(String uri);
    protected abstract void stopMedia();
    protected abstract void disposeMedia();
    protected abstract void setVolume(double volume);
    protected abstract void setMute(boolean mute);
    protected abstract void seek(float position); // 0.0 to 1.0
    protected abstract void updateVideoSize();
    protected abstract void pauseMedia();
    protected abstract void resumeMedia();
    protected abstract boolean isPlaying();

    // --- UI Construction ---
    private void buildUI() {
        nowShowingFlow = new TextFlow();
        nowShowingFlow.setPadding(new Insets(0, 0, 5, 0));
        nowShowingFlow.setTextAlignment(TextAlignment.LEFT);
        streamInfoText = new Text();
        streamInfoText.getStyleClass().add("player-stream-info-text");
        applyFixedControlBarOrientation(nowShowingFlow, streamInfoText);

        btnPlayPause = createIconButton(pauseIcon);
        btnStop = createIconButton(stopIcon);
        btnRepeat = createIconButton(repeatOffIcon);
        btnRepeat.setOpacity(0.7);
        btnReload = createIconButton(reloadIcon);
        btnFullscreen = createIconButton(fullscreenIcon);
        btnPip = createIconButton(pipIcon);
        btnAspectRatio = createIconButton(aspectRatioIcon);
        btnAspectRatio.setTooltip(new Tooltip(I18n.tr("autoFit")));

        btnHideBar = createIconButton(hideBarIcon);
        btnHideBar.setTooltip(new Tooltip(I18n.tr("autoHideThisBar")));

        if (supportsTrackSelection()) {
            btnTracks = createTrackRootButton();
            tracksContextMenu = new ContextMenu();
            I18n.preparePopupControl(tracksContextMenu, btnTracks);
            tracksContextMenu.getStyleClass().add("player-tracks-menu");
            tracksContextMenu.setOnHidden(e -> isTracksMenuOpen = false);
            btnTracks.setOnAction(e -> {
                if (tracksContextMenu == null) return;
                if (tracksContextMenu.isShowing()) {
                    tracksContextMenu.hide();
                    return;
                }
                isTracksMenuOpen = true;
                controlsContainer.setVisible(true);
                refreshTrackMenus();
                tracksContextMenu.show(btnTracks, Side.TOP, 0, 0);
            });
        }

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnMute = createIconButton(muteOnIcon);

        volumeSlider = new Slider(0, 100, 50);
        volumeSlider.setPrefWidth(100);
        volumeSlider.getStyleClass().add("video-player-slider");

        HBox buttonRow = new HBox(3);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(btnPlayPause, btnStop, btnRepeat, btnReload, btnFullscreen, btnPip, spacer, btnMute, volumeSlider, btnAspectRatio);
        if (supportsTrackSelection()) {
            buttonRow.getChildren().add(btnTracks);
        }
        buttonRow.getChildren().add(btnHideBar);

        timeSlider = new Slider(0, 1, 0);
        timeSlider.getStyleClass().add("video-player-slider");
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeLabel = new Label(I18n.tr("auto00000000"));
        timeLabel.getStyleClass().add("player-time-label");

        HBox timeRow = new HBox(5);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        timeRow.getChildren().addAll(timeSlider, timeLabel);
        applyFixedControlBarOrientation(buttonRow, timeRow, volumeSlider, timeSlider);

        controlsContainer = new VBox(4);
        controlsContainer.setPadding(new Insets(5));
        controlsContainer.getStyleClass().add("player-controls-container");
        controlsContainer.getChildren().addAll(nowShowingFlow, buttonRow, timeRow);
        controlsContainer.setMaxWidth(576);
        controlsContainer.setPrefWidth(36);
        controlsContainer.setMaxHeight(Region.USE_PREF_SIZE);
        applyFixedControlBarOrientation(controlsContainer, nowShowingFlow, buttonRow, timeRow, timeLabel, volumeSlider, timeSlider);

        playerContainer.getStyleClass().add("player-container");
        playerContainer.setFocusTraversable(true);
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);
        Rectangle playerClip = new Rectangle();
        playerClip.widthProperty().bind(playerContainer.widthProperty());
        playerClip.heightProperty().bind(playerContainer.heightProperty());
        playerContainer.setClip(playerClip);

        playerContainer.widthProperty().addListener((obs, oldVal, newVal) -> updateVideoSize());
        playerContainer.heightProperty().addListener((obs, oldVal, newVal) -> updateVideoSize());

        StackPane overlayWrapper = new StackPane(controlsContainer);
        overlayWrapper.setAlignment(Pos.BOTTOM_CENTER);
        overlayWrapper.setPadding(new Insets(0, 10, 10, 10));

        loadingSpinner = new ProgressIndicator();
        loadingSpinner.setMaxSize(60, 60);
        loadingSpinner.setVisible(false);

        errorLabel = new Label();
        errorLabel.setWrapText(true);
        errorLabel.getStyleClass().add("player-error-label");
        errorLabel.setVisible(false);
        StackPane.setAlignment(errorLabel, Pos.CENTER);

        // --- Hidden Bar Message Construction ---
        hiddenBarMessage = new StackPane();
        hiddenBarMessage.setMaxHeight(Region.USE_PREF_SIZE);
        hiddenBarMessage.setMaxWidth(Region.USE_PREF_SIZE);
        hiddenBarMessage.setVisible(false);
        hiddenBarMessage.setManaged(false);
        
        // Bind width to 80% of player container
        hiddenBarMessage.maxWidthProperty().bind(playerContainer.widthProperty().multiply(0.92));

        // Inner box for text with background
        HBox messageBox = new HBox(10);
        messageBox.setAlignment(Pos.CENTER);
        messageBox.getStyleClass().add("player-hiddenbar-message-box");
        messageBox.setMinHeight(90); // Minimum height

        Label msgLabel = new Label(I18n.tr("autoControlBarHiddenMessage"));
        msgLabel.setWrapText(true);
        msgLabel.getStyleClass().add("player-hiddenbar-message-label");
        
        messageBox.getChildren().add(msgLabel);

        // Close Button
        Button msgCloseBtn = new Button();
        SVGPath closeIcon = new SVGPath();
        closeIcon.setContent("M 4 4 L 12 12 M 4 12 L 12 4"); // Simple X shape
        closeIcon.getStyleClass().add("player-hiddenbar-close-icon");
        msgCloseBtn.setGraphic(closeIcon);
        msgCloseBtn.getStyleClass().add("player-round-control-button");
        msgCloseBtn.setPadding(new Insets(8)); // Bigger hit area
        
        // Action
        msgCloseBtn.setOnAction(e -> {
            hiddenBarMessage.setVisible(false);
            hiddenBarMessage.setManaged(false);
        });

        // Add components to StackPane
        hiddenBarMessage.getChildren().addAll(messageBox, msgCloseBtn);
        
        // Positioning
        StackPane.setAlignment(messageBox, Pos.CENTER);
        StackPane.setAlignment(msgCloseBtn, Pos.TOP_RIGHT);
        
        // "Half-in, Half-out" effect
        msgCloseBtn.setTranslateX(10); 
        msgCloseBtn.setTranslateY(-10);

        StackPane.setAlignment(hiddenBarMessage, Pos.CENTER);

        // Subclasses must provide the video view
        Node videoView = getVideoView();
        if (videoView != null) {
            playerContainer.getChildren().add(videoView);
        }
        playerContainer.getChildren().addAll(overlayWrapper, loadingSpinner, errorLabel, hiddenBarMessage);
    }

    private void setupEventHandlers() {
        btnPlayPause.setOnAction(e -> {
            if (isPlaying()) {
                pauseMedia();
            } else {
                resumeMedia();
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

        btnHideBar.setOnAction(e -> {
            isControlBarHiddenByUser = true;
            controlsContainer.setVisible(false);
            if (!hasShownHiddenBarMessage) {
                hiddenBarMessage.setVisible(true);
                hiddenBarMessage.setManaged(true);
                hasShownHiddenBarMessage = true;
                if (hiddenBarMessageHideTimer != null) hiddenBarMessageHideTimer.stop();
                hiddenBarMessageHideTimer = new PauseTransition(Duration.seconds(10));
                hiddenBarMessageHideTimer.setOnFinished(ev -> {
                    hiddenBarMessage.setVisible(false);
                    hiddenBarMessage.setManaged(false);
                });
                hiddenBarMessageHideTimer.play();
            }
        });

        btnMute.setOnAction(e -> {
            isMuted = !isMuted;
            setMute(isMuted);
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        });

        volumeSlider.valueProperty().addListener((e, t, newVal) -> setVolume(newVal.doubleValue()));

        timeSlider.setOnMousePressed(e -> {
            restoreVisibleCursor();
            isUserSeeking = true;
            if (isFullscreen) idleTimer.stop();
        });
        timeSlider.setOnMouseReleased(e -> {
            restoreVisibleCursor();
            seek((float) timeSlider.getValue());
            isUserSeeking = false;
            restartIdleTimerForActivePlayer();
        });

        volumeSlider.setOnMousePressed(e -> {
            restoreVisibleCursor();
            if (isFullscreen) idleTimer.stop();
        });
        volumeSlider.setOnMouseReleased(e -> {
            restoreVisibleCursor();
            restartIdleTimerForActivePlayer();
        });

        playerContainer.setOnMouseClicked(e -> {
            restoreVisibleCursor();
            if (!isControlBarHiddenByUser) {
                controlsContainer.setVisible(true);
            }
            if (playerContainer.isVisible() && playerContainer.isManaged()) {
                idleTimer.playFromStart();
            }
            if (e.getButton() == MouseButton.PRIMARY) {
                if (e.getClickCount() == 1) playerContainer.requestFocus();
                else if (e.getClickCount() == 2) toggleFullscreen();
            } else if (e.getButton() == MouseButton.SECONDARY) {
                showControlBar();
            }
        });

        playerContainer.addEventFilter(KeyEvent.ANY, e -> {
            restoreVisibleCursor();
            restartIdleTimerForActivePlayer();
        });

        playerContainer.setOnKeyPressed(e -> {
            if (e.getCode() == KeyCode.F) toggleFullscreen();
            else if (e.getCode() == KeyCode.M) btnMute.fire();
            else if (e.getCode() == KeyCode.ESCAPE && fullscreenStage != null) toggleFullscreen();
            else if (e.getCode() == KeyCode.B) showControlBar();
        });

        playerContainer.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> restoreVisibleCursor());
        playerContainer.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> restoreVisibleCursor());
        playerContainer.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> {
            isPointerInsidePlayer = true;
            restoreVisibleCursor();
            if (!isControlBarHiddenByUser) {
                controlsContainer.setVisible(true);
            }
            restartIdleTimerForActivePlayer();
        });

        playerContainer.setOnScroll(e -> {
            restoreVisibleCursor();
            double delta = e.getDeltaY();
            if (delta == 0) return;
            double change = Math.signum(delta) * 5;
            volumeSlider.setValue(volumeSlider.getValue() + change);
        });
    }

    private void setupFadeAndIdleLogic() {
        controlsContainer.setVisible(false);
        idleTimer = new PauseTransition(Duration.seconds(5));
        idleTimer.setOnFinished(e -> handleIdleTimeout());

        playerContainer.addEventFilter(MouseEvent.MOUSE_MOVED, e -> {
            isPointerInsidePlayer = true;
            handlePlayerInteraction();
        });
        playerContainer.addEventFilter(MouseEvent.MOUSE_ENTERED, e -> {
            isPointerInsidePlayer = true;
            handlePlayerInteraction();
        });

        playerContainer.setOnMouseExited(e -> {
            isPointerInsidePlayer = false;
            idleTimer.stop();
            restoreVisibleCursor();
            if (!isTracksMenuOpen) {
                controlsContainer.setVisible(false);
            }
        });

        controlsContainer.setOnMouseEntered(e -> {
            isPointerInsidePlayer = true;
            handlePlayerInteraction();
        });
        controlsContainer.setOnMouseExited(e -> {
            if (isPointerInsidePlayer) {
                idleTimer.playFromStart();
            }
        });
        controlsContainer.setOnMouseMoved(e -> {
            isPointerInsidePlayer = true;
            handlePlayerInteraction();
            e.consume();
        });
    }

    private void applyFixedControlBarOrientation(Node... nodes) {
        if (nodes == null) {
            return;
        }
        for (Node node : nodes) {
            if (node != null) {
                node.setNodeOrientation(NodeOrientation.LEFT_TO_RIGHT);
            }
        }
    }

    private void handlePlayerInteraction() {
        restoreVisibleCursor();
        if (!isControlBarHiddenByUser) {
            controlsContainer.setVisible(true);
        }
        restartIdleTimerForActivePlayer();
    }

    private void handleIdleTimeout() {
        if (!playerContainer.isVisible() || !playerContainer.isManaged() || !isPointerInsidePlayer || isTracksMenuOpen) {
            restoreVisibleCursor();
            return;
        }
        if (!isControlBarHiddenByUser) {
            controlsContainer.setVisible(false);
        }
        if (isFullscreen) {
            playerContainer.setCursor(Cursor.NONE);
        } else {
            restoreVisibleCursor();
        }
    }

    private void restoreVisibleCursor() {
        if (playerContainer != null) {
            playerContainer.setCursor(Cursor.DEFAULT);
            if (playerContainer.getScene() != null) {
                playerContainer.getScene().setCursor(Cursor.DEFAULT);
            }
        }
        if (fullscreenStage != null && fullscreenStage.getScene() != null) {
            fullscreenStage.getScene().setCursor(Cursor.DEFAULT);
        }
        if (originalParent != null && originalParent.getScene() != null) {
            originalParent.getScene().setCursor(Cursor.DEFAULT);
        }
        if (pipStage != null && pipStage.getScene() != null) {
            pipStage.getScene().setCursor(Cursor.DEFAULT);
        }
    }

    private void handleSceneInputRecovery(InputEvent event) {
        restoreVisibleCursor();
        Scene eventScene = event == null || !(event.getSource() instanceof Scene) ? null : (Scene) event.getSource();
        if (eventScene == null || eventScene != playerContainer.getScene() || !playerContainer.isVisible() || !playerContainer.isManaged()) {
            return;
        }
        restartIdleTimerForActivePlayer();
    }

    private void restartIdleTimerForActivePlayer() {
        if (idleTimer == null || !playerContainer.isVisible() || !playerContainer.isManaged()) {
            return;
        }
        if (!isFullscreen && !isPointerInsidePlayer) {
            return;
        }
        idleTimer.playFromStart();
    }

    private void installSceneInputRecovery(Scene scene) {
        if (scene == null || scene == pipInputRecoveryScene || scene == activeInputRecoveryScene) {
            return;
        }
        scene.addEventFilter(InputEvent.ANY, sceneInputRecoveryHandler);
        activeInputRecoveryScene = scene;
    }

    private void uninstallSceneInputRecovery(Scene scene) {
        if (scene == null) {
            return;
        }
        scene.removeEventFilter(InputEvent.ANY, sceneInputRecoveryHandler);
        if (scene == activeInputRecoveryScene) {
            activeInputRecoveryScene = null;
        }
        if (scene == pipInputRecoveryScene) {
            pipInputRecoveryScene = null;
        }
    }

    private void installPipSceneInputRecovery(Scene scene) {
        if (scene == null || scene == pipInputRecoveryScene) {
            return;
        }
        uninstallSceneInputRecovery(scene);
        scene.addEventFilter(InputEvent.ANY, sceneInputRecoveryHandler);
        pipInputRecoveryScene = scene;
    }

    protected boolean supportsTrackSelection() {
        return false;
    }

    protected List<TrackOption> getAudioTrackOptions() {
        return Collections.emptyList();
    }

    protected int getSelectedAudioTrackId() {
        return Integer.MIN_VALUE;
    }

    protected void selectAudioTrack(int trackId) {
        // No-op by default.
    }

    protected List<TrackOption> getSubtitleTrackOptions() {
        return Collections.emptyList();
    }

    protected boolean supportsSubtitleTrackSelection() {
        return false;
    }

    protected int getSelectedSubtitleTrackId() {
        return Integer.MIN_VALUE;
    }

    protected void selectSubtitleTrack(int trackId) {
        // No-op by default.
    }

    protected void refreshTrackMenus() {
        if (!supportsTrackSelection() || tracksContextMenu == null) {
            return;
        }
        List<MenuItem> items = tracksContextMenu.getItems();
        items.clear();
        updateTrackMenu(items, getAudioTrackOptions(), getSelectedAudioTrackId(), this::selectAudioTrack, "No audio tracks");
        if (supportsSubtitleTrackSelection()) {
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            updateTrackMenu(items, getSubtitleTrackOptions(), getSelectedSubtitleTrackId(), this::selectSubtitleTrack, "No subtitle tracks");
        }
    }

    private void updateTrackMenu(List<MenuItem> targetItems, List<TrackOption> options, int selectedTrackId, IntConsumer onTrackSelected, String emptyLabel) {
        if (options == null || options.isEmpty()) {
            MenuItem noneItem = new MenuItem(emptyLabel);
            noneItem.getStyleClass().add("player-tracks-menu-item");
            noneItem.setDisable(true);
            targetItems.add(noneItem);
            return;
        }

        ToggleGroup group = new ToggleGroup();
        for (TrackOption option : options) {
            RadioMenuItem item = new RadioMenuItem(option.label);
            item.setToggleGroup(group);
            item.setSelected(option.id == selectedTrackId);
            item.getStyleClass().add("player-tracks-menu-item");
            item.setOnAction(e -> {
                onTrackSelected.accept(option.id);
                refreshTrackMenus();
            });
            targetItems.add(item);
        }
    }

    // --- Common Logic ---

    @Override
    public void play(PlayerResponse response) {
        play(response, false);
    }

    protected void play(PlayerResponse response, boolean isInternalRetry) {
        if (!isInternalRetry) {
            retryCount = 0;
            isRetrying.set(true);
        }

        String uri;
        if (response != null) {
            uri = response.getUrl();
            this.currentAccount = response.getAccount();
            this.currentChannel = response.getChannel();
        } else {
            uri = null;
        }

        if (isNotBlank(uri)) {
            this.currentMediaUri = uri;
            preparePlayerUiForPlayback();

            setMute(isMuted);
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
            setVolume(volumeSlider.getValue());
            updateVideoSize();

            playMedia(uri);
        }
    }

    private void preparePlayerUiForPlayback() {
        playerContainer.setVisible(true);
        playerContainer.setManaged(true);
        playerContainer.setMinHeight(275);
        isPointerInsidePlayer = false;
        loadingSpinner.setVisible(true);
        errorLabel.setVisible(false);
        controlsContainer.setVisible(false);
        timeSlider.setDisable(false);
        timeSlider.setValue(0);
        timeLabel.setText(I18n.tr("auto00000000"));
        restoreVisibleCursor();

        nowShowingFlow.getChildren().clear();
        if (currentChannel != null && isNotBlank(currentChannel.getName())) {
            Text channelNameText = new Text(currentChannel.getName());
            channelNameText.getStyleClass().add("player-channel-title");
            applyFixedControlBarOrientation(channelNameText);
            streamInfoText.setText("");
            nowShowingFlow.getChildren().addAll(channelNameText, streamInfoText);
            nowShowingFlow.setVisible(true);
            nowShowingFlow.setManaged(true);
        } else {
            nowShowingFlow.setVisible(false);
            nowShowingFlow.setManaged(false);
        }
    }

    @Override
    public void stop() {
        retryCount = 0;
        isRetrying.set(false);
        idleTimer.stop();
        isPointerInsidePlayer = false;
        stopMedia();
        playerContainer.setMinHeight(0);
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);
        btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        restoreVisibleCursor();
    }

    @Override
    public void stopForReload() {
        if (Platform.isFxApplicationThread()) {
            stopMedia();
            disposeMedia();
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    stopMedia();
                    disposeMedia();
                } finally {
                    latch.countDown();
                }
            });
            try {
                latch.await();
            } catch (InterruptedException _) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public Node getPlayerContainer() {
        return playerContainer;
    }

    /**
     * Disposes the entire player and cleans up all resources.
     * Must be called when the application exits or player is no longer needed.
     * This is separate from stopMedia/disposeMedia to handle complete cleanup.
     */
    public void disposePlayer() {
        try {
            // First stop any current playback and dispose current media
            stopForReload();
        } catch (Exception _) {
        }

        try {
            // Clear UI components
            if (playerContainer != null) {
                playerContainer.getChildren().clear();
            }

            // Clear controls container
            if (controlsContainer != null) {
                controlsContainer.getChildren().clear();
            }

            // Clear all buttons
            if (tracksContextMenu != null) {
                tracksContextMenu.getItems().clear();
            }

            // Clear text flows
            if (nowShowingFlow != null) {
                nowShowingFlow.getChildren().clear();
            }

            // Remove any fullscreen stage
            if (fullscreenStage != null) {
                try {
                    uninstallSceneInputRecovery(fullscreenStage.getScene());
                    fullscreenStage.close();
                } catch (Exception _) {
                }
                fullscreenStage = null;
            }

            // Remove any PiP stage
            if (pipStage != null) {
                try {
                    uninstallSceneInputRecovery(pipStage.getScene());
                    pipStage.close();
                } catch (Exception _) {
                }
                pipStage = null;
            }

            // Cancel idle timers
            if (idleTimer != null) {
                idleTimer.stop();
            }

            if (hiddenBarMessageHideTimer != null) {
                hiddenBarMessageHideTimer.stop();
            }
            restoreVisibleCursor();
        } catch (Exception _) {
        }
    }

    protected void handleRepeat() {
        if (!isRetrying.get()) return;
        if (retryCount < 5) {
            retryCount++;
            if (retryCount == 1) {
                refreshAndPlay();
            } else {
                PauseTransition delay = new PauseTransition(Duration.seconds(10));
                delay.setOnFinished(e -> {
                    if (isRetrying.get()) refreshAndPlay();
                });
                delay.play();
            }
        } else {
            stop();
            Platform.runLater(() -> {
                errorLabel.setText(I18n.tr("autoFailedToReconnectAfter5Attempts"));
                errorLabel.setVisible(true);
            });
        }
    }

    protected void refreshAndPlay() {
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
                        errorLabel.setText(I18n.tr("autoCouldNotRefreshStream"));
                        errorLabel.setVisible(true);
                        if (isRepeating && isRetrying.get()) handleRepeat();
                    });
                }
            }).start();
        } else {
            if (currentMediaUri != null && !currentMediaUri.isEmpty()) {
                play(new PlayerResponse(currentMediaUri), true);
            }
        }
    }

    protected void showControlBar() {
        if (isControlBarHiddenByUser) {
            isControlBarHiddenByUser = false;
        }
        controlsContainer.setVisible(true);
        hiddenBarMessage.setVisible(false);
        hiddenBarMessage.setManaged(false);
        if (hiddenBarMessageHideTimer != null) hiddenBarMessageHideTimer.stop();
        restoreVisibleCursor();
        if (isPointerInsidePlayer) {
            idleTimer.playFromStart();
        }
    }

    protected void toggleAspectRatio() {
        aspectRatioMode = (aspectRatioMode + 1) % 3;
        updateAspectRatioButtonState();
        updateVideoSize();
    }

    private void updateAspectRatioButtonState() {
        String tooltipText;
        ImageView icon;
        if (aspectRatioMode == ASPECT_RATIO_FILL) {
            tooltipText = "Fill";
            icon = aspectRatioFillIcon;
        } else if (aspectRatioMode == ASPECT_RATIO_STRETCH) {
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
    }

    protected String formatTime(long millis) {
        if (millis < 0) return "00:00";
        long seconds = millis / 1000, s = seconds % 60, m = (seconds / 60) % 60, h = (seconds / 3600) % 24;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    protected void updatePlaybackTimeUi(long currentTimeMs, long totalTimeMs, boolean seekable) {
        long safeCurrent = Math.max(0, currentTimeMs);
        boolean hasKnownTotal = totalTimeMs > 0;
        boolean isLiveLike = !hasKnownTotal || !seekable;

        if (!isLiveLike) {
            long safeTotal = Math.max(0, totalTimeMs);
            timeLabel.setText(formatTime(safeCurrent) + " / " + formatTime(safeTotal));
            timeSlider.setDisable(false);
            if (!isUserSeeking && safeTotal > 0) {
                timeSlider.setValue(clamp01((double) safeCurrent / safeTotal));
            }
            return;
        }

        if (seekable && hasKnownTotal) {
            long safeTotal = Math.max(0, totalTimeMs);
            long behindLive = Math.max(0, safeTotal - safeCurrent);
            timeLabel.setText(formatTime(safeCurrent) + " / LIVE -" + formatTime(behindLive));
            timeSlider.setDisable(false);
            if (!isUserSeeking && safeTotal > 0) {
                timeSlider.setValue(clamp01((double) safeCurrent / safeTotal));
            }
            return;
        }

        timeLabel.setText(formatTime(safeCurrent) + " / LIVE");
        timeSlider.setDisable(true);
        if (!isUserSeeking) {
            timeSlider.setValue(1.0);
        }
    }

    private double clamp01(double value) {
        if (value < 0) return 0;
        if (value > 1) return 1;
        return value;
    }

    // --- Fullscreen Logic ---
    @Override
    public void toggleFullscreen() {
        if (fullscreenStage == null) enterFullscreen();
        else exitFullscreen();
    }

    public void enterFullscreen() {
        if (fullscreenStage != null) return;
        Platform.runLater(() -> {
            Scene originalScene = playerContainer.getScene();
            originalParent = (Pane) playerContainer.getParent();
            if (originalParent != null) {
                originalIndex = originalParent.getChildren().indexOf(playerContainer);
                originalParent.getChildren().remove(playerContainer);
            }
            fullscreenStage = new Stage(StageStyle.UNDECORATED);
            Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
            fullscreenRoot = new StackPane(playerContainer);
            if (!fullscreenRoot.getStyleClass().contains("root")) {
                fullscreenRoot.getStyleClass().add("root");
            }
            fullscreenRoot.getStyleClass().add("player-fullscreen-root");
            StyleClassDecorator.decorate(fullscreenRoot);
            // Ensure video container fills fullscreen root instead of staying at computed pref size.
            playerContainer.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);
            playerContainer.prefWidthProperty().bind(fullscreenRoot.widthProperty());
            playerContainer.prefHeightProperty().bind(fullscreenRoot.heightProperty());
            Scene scene = new Scene(fullscreenRoot, bounds.getWidth(), bounds.getHeight());
            I18n.applySceneOrientation(scene);
            scene.setFill(Color.BLACK);
            if (originalScene != null && !originalScene.getStylesheets().isEmpty()) {
                scene.getStylesheets().setAll(originalScene.getStylesheets());
            }
            StyleClassDecorator.decorate(playerContainer);
            fullscreenStage.setScene(scene);
            installSceneInputRecovery(scene);
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

            isFullscreen = true;
            if (!isControlBarHiddenByUser) controlsContainer.setVisible(true);
            restoreVisibleCursor();
            if (isPointerInsidePlayer) {
                idleTimer.playFromStart();
            }
        });
    }

    public void exitFullscreen() {
        if (fullscreenStage == null) return;
        Platform.runLater(() -> {
            playerContainer.prefWidthProperty().unbind();
            playerContainer.prefHeightProperty().unbind();
            if (fullscreenStage != null) fullscreenStage.close();
            fullscreenStage = null;
            if (fullscreenRoot != null) {
                fullscreenRoot.getChildren().remove(playerContainer);
                fullscreenRoot = null;
            }
            if (originalParent != null) {
                originalParent.getChildren().add(originalIndex, playerContainer);
                if (originalParent.getScene() != null) {
                    originalParent.getScene().setCursor(Cursor.DEFAULT);
                }
            }
            playerContainer.applyCss();
            playerContainer.layout();
            playerContainer.requestLayout();
            playerContainer.requestFocus();
            btnFullscreen.setGraphic(fullscreenIcon);
            btnPip.setVisible(true);
            btnPip.setManaged(true);
            btnStop.setVisible(true);
            btnStop.setManaged(true);

            isFullscreen = false;
            idleTimer.stop();
            controlsContainer.setVisible(false);
            isPointerInsidePlayer = false;
            restoreVisibleCursor();
        });
    }

    // --- PiP Logic ---
    public void togglePip() {
        if (pipStage == null) enterPip();
        else exitPip();
    }

    public void enterPip() {
        if (pipStage != null) return;
        Platform.runLater(() -> {
            Scene originalScene = playerContainer.getScene();
            originalParent = (Pane) playerContainer.getParent();
            if (originalParent != null) {
                originalIndex = originalParent.getChildren().indexOf(playerContainer);
                originalParent.getChildren().remove(playerContainer);
            }

            Node videoView = getVideoView();
            playerContainer.getChildren().remove(videoView);

            pipStage = new Stage(StageStyle.UNDECORATED);
            pipStage.setAlwaysOnTop(true);

            StackPane pipRoot = new StackPane();
            pipRoot.getStyleClass().add("player-container");
            StyleClassDecorator.decorate(pipRoot);

            Button restoreButton = new Button();
            if (pipExitIcon != null && pipExitIcon.getImage() != null) {
                ImageView restoreIconView = new ImageView(pipExitIcon.getImage());
                restoreIconView.setFitHeight(64);
                restoreIconView.setFitWidth(64);
                ColorAdjust whiteColorAdjust = new ColorAdjust();
                whiteColorAdjust.setBrightness(1.0);
                whiteColorAdjust.setSaturation(-1.0);
                restoreIconView.setEffect(whiteColorAdjust);
                restoreButton.setGraphic(restoreIconView);
            } else {
                restoreButton.setText(I18n.tr("autoRestore"));
                restoreButton.getStyleClass().add("player-pip-restore-button");
            }
            restoreButton.getStyleClass().add("player-round-control-button");
            restoreButton.getStyleClass().add("player-pip-overlay-button");
            restoreButton.setPadding(new Insets(15));
            restoreButton.setVisible(false);
            restoreButton.setOnAction(e -> exitPip());

            Button pipMuteButton = new Button();
            ImageView pipMuteIcon = new ImageView(isMuted ? muteOnIcon.getImage() : muteOffIcon.getImage());
            pipMuteIcon.setFitHeight(20);
            pipMuteIcon.setFitWidth(20);
            ColorAdjust whiteColorAdjust = new ColorAdjust();
            whiteColorAdjust.setBrightness(1.0);
            pipMuteIcon.setEffect(whiteColorAdjust);
            pipMuteButton.setGraphic(pipMuteIcon);
            pipMuteButton.getStyleClass().add("player-round-control-button");
            pipMuteButton.getStyleClass().add("player-pip-overlay-button");
            pipMuteButton.setPadding(new Insets(8));
            pipMuteButton.setVisible(false);
            pipMuteButton.setOnAction(e -> {
                btnMute.fire();
                pipMuteIcon.setImage(isMuted ? muteOnIcon.getImage() : muteOffIcon.getImage());
            });

            Button pipReloadButton = new Button();
            ImageView pipReloadIcon = new ImageView(reloadIcon.getImage());
            pipReloadIcon.setFitHeight(20);
            pipReloadIcon.setFitWidth(20);
            pipReloadIcon.setEffect(whiteColorAdjust);
            pipReloadButton.setGraphic(pipReloadIcon);
            pipReloadButton.getStyleClass().add("player-round-control-button");
            pipReloadButton.getStyleClass().add("player-pip-overlay-button");
            pipReloadButton.setPadding(new Insets(8));
            pipReloadButton.setVisible(false);
            pipReloadButton.setOnAction(e -> refreshAndPlay());

            HBox pipControls = new HBox(10);
            pipControls.setAlignment(Pos.TOP_RIGHT);
            pipControls.setPadding(new Insets(10));
            pipControls.getChildren().addAll(pipReloadButton, pipMuteButton);
            pipControls.setPickOnBounds(false);
            applyFixedControlBarOrientation(pipControls);

            StackPane.setAlignment(pipControls, Pos.TOP_RIGHT);

            pipRoot.getChildren().addAll(videoView, restoreButton, pipControls);
            StackPane.setAlignment(restoreButton, Pos.CENTER);

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

            if (videoView instanceof ImageView) {
                ((ImageView) videoView).fitWidthProperty().bind(pipRoot.widthProperty());
                ((ImageView) videoView).fitHeightProperty().bind(pipRoot.heightProperty());
            } else if (videoView instanceof MediaView) {
                ((MediaView) videoView).fitWidthProperty().bind(pipRoot.widthProperty());
                ((MediaView) videoView).fitHeightProperty().bind(pipRoot.heightProperty());
            }

            Scene scene = new Scene(pipRoot, 480, 270);
            I18n.applySceneOrientation(scene);
            scene.setFill(Color.TRANSPARENT);
            if (originalScene != null && !originalScene.getStylesheets().isEmpty()) {
                scene.getStylesheets().setAll(originalScene.getStylesheets());
            }
            pipStage.setScene(scene);
            installPipSceneInputRecovery(scene);

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
            uninstallSceneInputRecovery(pipStage.getScene());
            pipStage.close();
            pipStage = null;

            Node videoView = getVideoView();
            ((Pane) videoView.getParent()).getChildren().remove(videoView);

            if (originalParent != null) {
                originalParent.getChildren().add(originalIndex, playerContainer);
            }

            playerContainer.getChildren().addFirst(videoView);

            playerContainer.setVisible(true);
            playerContainer.setManaged(true);

            if (videoView instanceof ImageView) {
                ((ImageView) videoView).fitWidthProperty().bind(playerContainer.widthProperty());
                ((ImageView) videoView).fitHeightProperty().bind(playerContainer.heightProperty());
            } else if (videoView instanceof MediaView) {
                ((MediaView) videoView).fitWidthProperty().bind(playerContainer.widthProperty());
                ((MediaView) videoView).fitHeightProperty().bind(playerContainer.heightProperty());
            }

            controlsContainer.setVisible(false);
            controlsContainer.setManaged(true);
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

    private void setupPipResizing(StackPane pipRoot) {
        pipRoot.setOnMouseMoved(event -> {
            if (isResizing) return;
            double x = event.getX();
            double y = event.getY();
            double width = pipStage.getWidth();
            double height = pipStage.getHeight();
            Cursor cursor = Cursor.DEFAULT;
            resizeDirection = 0;
            if (y < RESIZE_BORDER) {
                cursor = Cursor.N_RESIZE;
                resizeDirection = 1;
            } else if (y > height - RESIZE_BORDER) {
                cursor = Cursor.S_RESIZE;
                resizeDirection = 5;
            }
            if (x < RESIZE_BORDER) {
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
            } else if (x > width - RESIZE_BORDER) {
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
                    case 1:
                        newHeight = initialHeight - deltaY;
                        newY = initialY + deltaY;
                        break;
                    case 2:
                        newWidth = initialWidth + deltaX;
                        newHeight = initialHeight - deltaY;
                        newY = initialY + deltaY;
                        break;
                    case 3:
                        newWidth = initialWidth + deltaX;
                        break;
                    case 4:
                        newWidth = initialWidth + deltaX;
                        newHeight = initialHeight + deltaY;
                        break;
                    case 5:
                        newHeight = initialHeight + deltaY;
                        break;
                    case 6:
                        newWidth = initialWidth - deltaX;
                        newX = initialX + deltaX;
                        newHeight = initialHeight + deltaY;
                        break;
                    case 7:
                        newWidth = initialWidth - deltaX;
                        newX = initialX + deltaX;
                        break;
                    case 8:
                        newWidth = initialWidth - deltaX;
                        newX = initialX + deltaX;
                        newHeight = initialHeight - deltaY;
                        newY = initialY + deltaY;
                        break;
                    default:
                        break;
                }

                if (newWidth < MIN_WIDTH) {
                    if (resizeDirection == 7 || resizeDirection == 6 || resizeDirection == 8)
                        newX = pipStage.getX() + (pipStage.getWidth() - MIN_WIDTH);
                    newWidth = MIN_WIDTH;
                }
                if (newHeight < MIN_HEIGHT) {
                    if (resizeDirection == 1 || resizeDirection == 2 || resizeDirection == 8)
                        newY = pipStage.getY() + (pipStage.getHeight() - MIN_HEIGHT);
                    newHeight = MIN_HEIGHT;
                }
                pipStage.setWidth(newWidth);
                pipStage.setHeight(newHeight);
                pipStage.setX(newX);
                pipStage.setY(newY);
            } else {
                pipStage.setX(event.getScreenX() + xOffset);
                pipStage.setY(event.getScreenY() + yOffset);
            }
        });

        pipRoot.setOnMouseReleased(event -> {
            isResizing = false;
            resizeDirection = 0;
            pipStage.getScene().setCursor(Cursor.DEFAULT);
        });
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
        aspectRatioFillIcon = createIconView("aspect-ratio-fill.png", true);
        aspectRatioStretchIcon = createIconView("aspect-ratio-stretch.png", true);
        hideBarIcon = createIconView("arrow-down.png", true);
    }

    private ImageView createIconView(String iconName, boolean applyColorAdjust) {
        try {
            String iconPath = "/icons/videoPlayer/" + iconName;
            java.net.URL resourceUrl = getClass().getResource(iconPath);
            if (resourceUrl == null) return new ImageView();

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
        btn.setPadding(new Insets(4));
        btn.getStyleClass().add("player-icon-button");
        return btn;
    }

    private Button createTrackRootButton() {
        Button btn = new Button();
        ImageView upIcon = createIconView("arrow-up.png", false);
        ColorAdjust whiteColorAdjust = new ColorAdjust();
        whiteColorAdjust.setBrightness(1.0);
        whiteColorAdjust.setSaturation(-1.0);
        upIcon.setEffect(whiteColorAdjust);
        btn.setGraphic(upIcon);
        btn.setText("");
        btn.setTooltip(new Tooltip("Audio/Subtitles"));
        btn.getStyleClass().add("player-icon-button");
        btn.setPadding(new Insets(4));
        return btn;
    }
}
