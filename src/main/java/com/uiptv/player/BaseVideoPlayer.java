package com.uiptv.player;

import com.uiptv.util.I18n;

import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.PlayerResponse;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.BingeWatchService;
import com.uiptv.service.PlayerService;
import com.uiptv.service.SeriesWatchStateChangeListener;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.util.StyleClassDecorator;
import com.uiptv.util.PlayerUrlUtils;
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
import javafx.scene.input.ScrollEvent;
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
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import static com.uiptv.util.StringUtils.isNotBlank;

import static com.uiptv.widget.UIptvAlert.showError;

public abstract class BaseVideoPlayer implements VideoPlayerInterface {
    private static final String STYLE_CLASS_PLAYER_ROUND_CONTROL_BUTTON = "player-round-control-button";
    private static final String STYLE_CLASS_PLAYER_PIP_OVERLAY_BUTTON = "player-pip-overlay-button";
    public static final String PLAYER_ICON_BUTTON = "player-icon-button";
    public static final String PLAYER_TRACKS_MENU_ITEM = "player-tracks-menu-item";

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
    protected Button btnRewind;
    protected Button btnFastForward;
    protected Button btnAspectRatio;
    protected Button btnHideBar;
    protected Button btnTracks;
    protected ContextMenu tracksContextMenu;
    protected ImageView playIcon;
    protected ImageView pauseIcon;
    protected ImageView stopIcon;
    protected ImageView repeatOnIcon;
    protected ImageView repeatOffIcon;
    protected ImageView fullscreenIcon;
    protected ImageView fullscreenExitIcon;
    protected ImageView rewindIcon;
    protected ImageView fastForwardIcon;
    protected ImageView muteOnIcon;
    protected ImageView muteOffIcon;
    protected ImageView reloadIcon;
    protected ImageView pipIcon;
    protected ImageView pipExitIcon;
    protected ImageView aspectRatioIcon;
    protected ImageView aspectRatioFillIcon;
    protected ImageView aspectRatioStretchIcon;
    protected ImageView hideBarIcon;

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
    protected boolean isLiveLikeContent = true;
    protected String activeBingeWatchToken = "";
    protected String activeBingeWatchEpisodeId = "";
    private SeriesWatchStateChangeListener bingeWatchStateChangeListener;
    private final EventHandler<InputEvent> sceneInputRecoveryHandler = event -> handleSceneInputRecovery(event);

    // Resizing Logic
    protected boolean isResizing = false;
    protected int resizeDirection = 0;
    protected double initialX;
    protected double initialY;
    protected double initialWidth;
    protected double initialHeight;
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

    public static final String CHROME_USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/115.0.0.0 Safari/537.36";

    protected BaseVideoPlayer() {
        loadIcons();
        buildUI();
        setupEventHandlers();
        setupFadeAndIdleLogic();
        playerContainer.sceneProperty().addListener((_, oldScene, newScene) -> {
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
    protected abstract void seekBySeconds(int deltaSeconds);
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
        btnRewind = createTransportButton(rewindIcon, "Rewind 15s");
        btnFastForward = createTransportButton(fastForwardIcon, "Fast forward 15s");
        btnRepeat = createIconButton(repeatOffIcon);
        btnRepeat.setOpacity(0.7);
        btnReload = createIconButton(reloadIcon);
        btnFullscreen = createIconButton(fullscreenIcon);
        btnPip = createIconButton(pipIcon);
        btnAspectRatio = createIconButton(aspectRatioIcon);
        btnAspectRatio.setTooltip(new Tooltip(I18n.tr("autoFit")));

        btnHideBar = createIconButton(hideBarIcon);
        btnHideBar.setTooltip(new Tooltip(I18n.tr("autoHideThisBar")));

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

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        btnMute = createIconButton(muteOnIcon);

        volumeSlider = new Slider(0, 100, 50);
        volumeSlider.setPrefWidth(88);
        volumeSlider.getStyleClass().add("video-player-slider");

        HBox buttonRow = new HBox(1.5);
        buttonRow.setAlignment(Pos.CENTER_LEFT);
        buttonRow.getChildren().addAll(btnPlayPause, btnStop, btnRewind, btnFastForward, btnRepeat, btnReload, btnFullscreen, btnPip, spacer, btnMute, volumeSlider, btnAspectRatio);
        buttonRow.getChildren().add(btnTracks);
        buttonRow.getChildren().add(btnHideBar);

        timeSlider = new Slider(0, 1, 0);
        timeSlider.getStyleClass().add("video-player-slider");
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeLabel = new Label(I18n.tr("auto00000000"));
        timeLabel.getStyleClass().add("player-time-label");

        HBox timeRow = new HBox(2.5);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        timeRow.getChildren().addAll(timeSlider, timeLabel);
        applyFixedControlBarOrientation(buttonRow, timeRow, volumeSlider, timeSlider);

        controlsContainer = new VBox(2);
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
        msgCloseBtn.getStyleClass().add(STYLE_CLASS_PLAYER_ROUND_CONTROL_BUTTON);
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
        wirePlaybackButtons();
        wireSliderInteractions();
        wireContainerInteractions();
    }

    private void wirePlaybackButtons() {
        btnPlayPause.setOnAction(e -> {
            if (isPlaying()) {
                pauseMedia();
            } else {
                resumeMedia();
            }
        });
        btnStop.setOnAction(e -> {
            exitFullscreen();
            stop();
        });
        btnRewind.setOnAction(e -> seekBySeconds(-15));
        btnFastForward.setOnAction(e -> seekBySeconds(15));
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
        btnPip.setOnAction(e -> {
            exitFullscreen();
            togglePip();
        });
        btnAspectRatio.setOnAction(e -> toggleAspectRatio());
        btnHideBar.setOnAction(e -> hideControlBarByUser());
        btnMute.setOnAction(e -> {
            isMuted = !isMuted;
            setMute(isMuted);
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        });
        volumeSlider.valueProperty().addListener((e, t, newVal) -> setVolume(newVal.doubleValue()));
    }

    private void wireSliderInteractions() {
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
    }

    private void wireContainerInteractions() {
        playerContainer.setOnMouseClicked(this::handlePlayerMouseClick);
        playerContainer.addEventFilter(KeyEvent.ANY, e -> onPlayerInteraction());
        playerContainer.setOnKeyPressed(this::handlePlayerKeyPress);
        playerContainer.addEventFilter(MouseEvent.MOUSE_PRESSED, e -> restoreVisibleCursor());
        playerContainer.addEventFilter(MouseEvent.MOUSE_RELEASED, e -> restoreVisibleCursor());
        playerContainer.addEventFilter(MouseEvent.MOUSE_DRAGGED, e -> handlePlayerMouseDragged());
        playerContainer.setOnScroll(this::handlePlayerScroll);
    }

    private void handlePlayerMouseClick(MouseEvent event) {
        onPlayerInteraction();
        if (event.getButton() == MouseButton.PRIMARY) {
            handlePrimaryPlayerClick(event);
            return;
        }
        if (event.getButton() == MouseButton.SECONDARY) {
            showControlBar();
        }
    }

    private void handlePrimaryPlayerClick(MouseEvent event) {
        if (event.getClickCount() == 1) {
            playerContainer.requestFocus();
        } else if (event.getClickCount() == 2) {
            toggleFullscreen();
        }
    }

    private void handlePlayerKeyPress(KeyEvent event) {
        if (event.getCode() == KeyCode.F) {
            toggleFullscreen();
        } else if (event.getCode() == KeyCode.M) {
            btnMute.fire();
        } else if (event.getCode() == KeyCode.ESCAPE && fullscreenStage != null) {
            toggleFullscreen();
        } else if (event.getCode() == KeyCode.B) {
            showControlBar();
        }
    }

    private void handlePlayerMouseDragged() {
        isPointerInsidePlayer = true;
        onPlayerInteraction();
    }

    private void handlePlayerScroll(ScrollEvent event) {
        restoreVisibleCursor();
        double delta = event.getDeltaY();
        if (delta == 0) return;
        double change = Math.signum(delta) * 5;
        volumeSlider.setValue(volumeSlider.getValue() + change);
    }

    private void onPlayerInteraction() {
        restoreVisibleCursor();
        if (!isControlBarHiddenByUser) {
            controlsContainer.setVisible(true);
        }
        if (playerContainer.isVisible() && playerContainer.isManaged()) {
            idleTimer.playFromStart();
        }
        restartIdleTimerForActivePlayer();
    }

    private void hideControlBarByUser() {
        isControlBarHiddenByUser = true;
        controlsContainer.setVisible(false);
        if (!hasShownHiddenBarMessage) {
            showHiddenBarMessage();
        }
    }

    private void showHiddenBarMessage() {
        hiddenBarMessage.setVisible(true);
        hiddenBarMessage.setManaged(true);
        markHiddenBarMessageShown();
        if (hiddenBarMessageHideTimer != null) hiddenBarMessageHideTimer.stop();
        hiddenBarMessageHideTimer = new PauseTransition(Duration.seconds(10));
        hiddenBarMessageHideTimer.setOnFinished(ev -> {
            hiddenBarMessage.setVisible(false);
            hiddenBarMessage.setManaged(false);
        });
        hiddenBarMessageHideTimer.play();
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
        if (tracksContextMenu == null) {
            return;
        }
        List<MenuItem> items = tracksContextMenu.getItems();
        items.clear();
        appendTrackSection(items, "Audio", getAudioTrackOptions(), getSelectedAudioTrackId(), this::selectAudioTrack, "No audio tracks");

        List<TrackOption> playlistOptions = getPlaylistTrackOptions();
        if (!playlistOptions.isEmpty()) {
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            appendTrackSection(items, "Playlist", playlistOptions, getSelectedPlaylistTrackId(), this::selectPlaylistTrack, "No playlist items");
        }
        if (items.isEmpty()) {
            MenuItem noneItem = new MenuItem("No options");
            noneItem.getStyleClass().add(PLAYER_TRACKS_MENU_ITEM);
            noneItem.setDisable(true);
            items.add(noneItem);
        }
    }

    private void appendTrackSection(List<MenuItem> targetItems,
                                    String sectionTitle,
                                    List<TrackOption> options,
                                    int selectedTrackId,
                                    IntConsumer onTrackSelected,
                                    String emptyLabel) {
        MenuItem headerItem = new MenuItem(sectionTitle);
        headerItem.getStyleClass().add(PLAYER_TRACKS_MENU_ITEM);
        headerItem.setDisable(true);
        targetItems.add(headerItem);
        if (options == null || options.isEmpty()) {
            MenuItem noneItem = new MenuItem(emptyLabel);
            noneItem.getStyleClass().add(PLAYER_TRACKS_MENU_ITEM);
            noneItem.setDisable(true);
            targetItems.add(noneItem);
            return;
        }

        ToggleGroup group = new ToggleGroup();
        for (TrackOption option : options) {
            RadioMenuItem item = new RadioMenuItem(option.label);
            item.setToggleGroup(group);
            item.setSelected(option.id == selectedTrackId);
            item.getStyleClass().add(PLAYER_TRACKS_MENU_ITEM);
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
            if (response.getAccount() != null) {
                this.currentAccount = response.getAccount();
            }
            if (response.getChannel() != null) {
                this.currentChannel = response.getChannel();
            }
        } else {
            uri = null;
        }

        if (isNotBlank(uri)) {
            this.currentMediaUri = uri;
            updateBingeWatchContext(uri);
            syncBingeWatchEpisodeFromWatchState();
            updateBingeWatchWatchStateListener();
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
        isLiveLikeContent = true;
        loadingSpinner.setVisible(true);
        errorLabel.setVisible(false);
        controlsContainer.setVisible(false);
        timeSlider.setDisable(false);
        timeSlider.setValue(0);
        timeLabel.setText(I18n.tr("auto00000000"));
        restoreVisibleCursor();
        updateTrackButtonVisibility();
        updateTransportButtonsVisibility();
        updateRepeatButtonVisibility();

        streamInfoText.setText("");
        refreshNowShowingHeader();
    }

    @Override
    public void stop() {
        retryCount = 0;
        isRetrying.set(false);
        idleTimer.stop();
        isPointerInsidePlayer = false;
        activeBingeWatchToken = "";
        activeBingeWatchEpisodeId = "";
        removeBingeWatchWatchStateListener();
        isLiveLikeContent = true;
        updateTrackButtonVisibility();
        updateRepeatButtonVisibility();
        stopMedia();
        playerContainer.setMinHeight(0);
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);
        btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        restoreVisibleCursor();
    }

    @Override
    public void stopForReload() {
        removeBingeWatchWatchStateListener();
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
                // Preserve interruption while waiting for JavaFX to finish PiP cleanup.
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
    @SuppressWarnings("java:S1141")
    public void disposePlayer() {
        try {
            // First stop any current playback and dispose current media
            stopForReload();
        } catch (Exception _) {
            // Best-effort shutdown; continue disposing the remaining player resources.
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
                    // Ignore stage teardown issues during fullscreen cleanup.
                }
                fullscreenStage = null;
            }

            // Remove any PiP stage
            if (pipStage != null) {
                try {
                    uninstallSceneInputRecovery(pipStage.getScene());
                    pipStage.close();
                } catch (Exception _) {
                    // Ignore stage teardown issues during PiP cleanup.
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
            // Best-effort shutdown; media disposal failures should not leave stale UI around.
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
                    showError("Failed to refresh stream for account '" + currentAccount.getAccountName() + "' and channel '" + currentChannel.getName() + "'", e);
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
        String tooltipText = switch (aspectRatioMode) {
            case ASPECT_RATIO_FILL -> "Fill";
            case ASPECT_RATIO_STRETCH -> "Stretch";
            default -> "Fit";
        };
        ImageView icon = switch (aspectRatioMode) {
            case ASPECT_RATIO_FILL -> aspectRatioFillIcon;
            case ASPECT_RATIO_STRETCH -> aspectRatioStretchIcon;
            default -> aspectRatioIcon;
        };
        btnAspectRatio.setGraphic(icon);
        if (btnAspectRatio.getTooltip() != null) {
            btnAspectRatio.getTooltip().setText(tooltipText);
        }
    }

    protected String formatTime(long millis) {
        if (millis < 0) return "00:00";
        long seconds = millis / 1000;
        long s = seconds % 60;
        long m = (seconds / 60) % 60;
        long h = (seconds / 3600) % 24;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    protected void updatePlaybackTimeUi(long currentTimeMs, long totalTimeMs, boolean seekable) {
        long safeCurrent = Math.max(0, currentTimeMs);
        boolean hasKnownTotal = totalTimeMs > 0;
        boolean isLikelyOnDemand = isOnDemandContent();
        boolean isLiveLike = !isLikelyOnDemand && (!hasKnownTotal || !seekable);
        isLiveLikeContent = isLiveLike;
        updateTransportButtonsVisibility();

        if (!isLiveLike) {
            long safeTotal = Math.max(0, totalTimeMs);
            timeLabel.setText(hasKnownTotal
                    ? formatTime(safeCurrent) + " / " + formatTime(safeTotal)
                    : formatTime(safeCurrent) + " / --:--");
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
            detachPlayerContainer();
            Node videoView = getVideoView();
            playerContainer.getChildren().remove(videoView);

            pipStage = createPipStage();
            StackPane pipRoot = createPipRoot();
            Button restoreButton = createPipRestoreButton();
            PipControlButtons buttons = createPipControlButtons();
            HBox pipControls = buildPipControls(buttons);
            attachPipContent(pipRoot, videoView, restoreButton, pipControls);
            wirePipHoverState(pipRoot, restoreButton, buttons);
            bindPipVideoView(videoView, pipRoot);

            Scene scene = createPipScene(pipRoot, originalScene);
            pipStage.setScene(scene);
            installPipSceneInputRecovery(scene);
            positionPipStage();
            setupPipResizing(pipRoot);
            pipStage.show();
            applyPipUiState();
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

            if (videoView instanceof ImageView imageView) {
                imageView.fitWidthProperty().bind(playerContainer.widthProperty());
                imageView.fitHeightProperty().bind(playerContainer.heightProperty());
            } else if (videoView instanceof MediaView mediaView) {
                mediaView.fitWidthProperty().bind(playerContainer.widthProperty());
                mediaView.fitHeightProperty().bind(playerContainer.heightProperty());
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
        pipRoot.setOnMouseMoved(this::updatePipResizeCursor);
        pipRoot.setOnMousePressed(this::handlePipMousePressed);
        pipRoot.setOnMouseDragged(this::handlePipMouseDragged);
        pipRoot.setOnMouseReleased(event -> finishPipInteraction());
    }

    private void detachPlayerContainer() {
        originalParent = (Pane) playerContainer.getParent();
        if (originalParent != null) {
            originalIndex = originalParent.getChildren().indexOf(playerContainer);
            originalParent.getChildren().remove(playerContainer);
        }
    }

    private Stage createPipStage() {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        stage.setAlwaysOnTop(true);
        return stage;
    }

    private StackPane createPipRoot() {
        StackPane pipRoot = new StackPane();
        pipRoot.getStyleClass().add("player-container");
        StyleClassDecorator.decorate(pipRoot);
        return pipRoot;
    }

    private Button createPipRestoreButton() {
        Button restoreButton = new Button();
        if (pipExitIcon != null && pipExitIcon.getImage() != null) {
            ImageView restoreIconView = createWhiteIconView(pipExitIcon.getImage(), 64);
            restoreButton.setGraphic(restoreIconView);
        } else {
            restoreButton.setText(I18n.tr("autoRestore"));
            restoreButton.getStyleClass().add("player-pip-restore-button");
        }
        restoreButton.getStyleClass().add(STYLE_CLASS_PLAYER_ROUND_CONTROL_BUTTON);
        restoreButton.getStyleClass().add(STYLE_CLASS_PLAYER_PIP_OVERLAY_BUTTON);
        restoreButton.setPadding(new Insets(15));
        restoreButton.setVisible(false);
        restoreButton.setOnAction(e -> exitPip());
        return restoreButton;
    }

    private PipControlButtons createPipControlButtons() {
        Button pipMuteButton = createPipIconButton(isMuted ? muteOnIcon.getImage() : muteOffIcon.getImage(), 20, null);
        pipMuteButton.setOnAction(e -> {
            btnMute.fire();
            ((ImageView) pipMuteButton.getGraphic()).setImage(isMuted ? muteOnIcon.getImage() : muteOffIcon.getImage());
        });
        Button pipReloadButton = createPipIconButton(reloadIcon.getImage(), 20, e -> refreshAndPlay());
        return new PipControlButtons(pipMuteButton, pipReloadButton);
    }

    private Button createPipIconButton(Image image, int size, EventHandler<javafx.event.ActionEvent> handler) {
        Button button = new Button();
        button.setGraphic(createWhiteIconView(image, size));
        button.getStyleClass().add(STYLE_CLASS_PLAYER_ROUND_CONTROL_BUTTON);
        button.getStyleClass().add(STYLE_CLASS_PLAYER_PIP_OVERLAY_BUTTON);
        button.setPadding(new Insets(8));
        button.setVisible(false);
        button.setOnAction(handler);
        return button;
    }

    private ImageView createWhiteIconView(Image image, int size) {
        ImageView iconView = new ImageView(image);
        iconView.setFitHeight(size);
        iconView.setFitWidth(size);
        ColorAdjust whiteColorAdjust = new ColorAdjust();
        whiteColorAdjust.setBrightness(1.0);
        if (size == 64) {
            whiteColorAdjust.setSaturation(-1.0);
        }
        iconView.setEffect(whiteColorAdjust);
        return iconView;
    }

    private HBox buildPipControls(PipControlButtons buttons) {
        HBox pipControls = new HBox(10);
        pipControls.setAlignment(Pos.TOP_RIGHT);
        pipControls.setPadding(new Insets(10));
        pipControls.getChildren().addAll(buttons.reloadButton(), buttons.muteButton());
        pipControls.setPickOnBounds(false);
        applyFixedControlBarOrientation(pipControls);
        StackPane.setAlignment(pipControls, Pos.TOP_RIGHT);
        return pipControls;
    }

    private void attachPipContent(StackPane pipRoot, Node videoView, Button restoreButton, HBox pipControls) {
        pipRoot.getChildren().addAll(videoView, restoreButton, pipControls);
        StackPane.setAlignment(restoreButton, Pos.CENTER);
    }

    private void wirePipHoverState(StackPane pipRoot, Button restoreButton, PipControlButtons buttons) {
        pipRoot.setOnMouseEntered(e -> setPipButtonsVisible(true, restoreButton, buttons));
        pipRoot.setOnMouseExited(e -> setPipButtonsVisible(false, restoreButton, buttons));
    }

    private void setPipButtonsVisible(boolean visible, Button restoreButton, PipControlButtons buttons) {
        restoreButton.setVisible(visible);
        buttons.muteButton().setVisible(visible);
        buttons.reloadButton().setVisible(visible);
    }

    private void bindPipVideoView(Node videoView, StackPane pipRoot) {
        if (videoView instanceof ImageView imageView) {
            imageView.fitWidthProperty().bind(pipRoot.widthProperty());
            imageView.fitHeightProperty().bind(pipRoot.heightProperty());
        } else if (videoView instanceof MediaView mediaView) {
            mediaView.fitWidthProperty().bind(pipRoot.widthProperty());
            mediaView.fitHeightProperty().bind(pipRoot.heightProperty());
        }
    }

    private Scene createPipScene(StackPane pipRoot, Scene originalScene) {
        Scene scene = new Scene(pipRoot, 480, 270);
        I18n.applySceneOrientation(scene);
        scene.setFill(Color.TRANSPARENT);
        if (originalScene != null && !originalScene.getStylesheets().isEmpty()) {
            scene.getStylesheets().setAll(originalScene.getStylesheets());
        }
        return scene;
    }

    private void positionPipStage() {
        Rectangle2D primaryScreenBounds = Screen.getPrimary().getVisualBounds();
        pipStage.setX(primaryScreenBounds.getMaxX() - 480 - 20);
        pipStage.setY(primaryScreenBounds.getMaxY() - 270 - 20);
    }

    private void applyPipUiState() {
        controlsContainer.setVisible(false);
        controlsContainer.setManaged(false);
        btnPlayPause.setVisible(false);
        btnPlayPause.setManaged(false);
        btnStop.setVisible(false);
        btnStop.setManaged(false);
        btnPip.setGraphic(pipIcon);
    }

    private void updatePipResizeCursor(MouseEvent event) {
        if (isResizing) return;
        ResizeState state = resolveResizeState(event.getX(), event.getY(), pipStage.getWidth(), pipStage.getHeight());
        resizeDirection = state.direction();
        pipStage.getScene().setCursor(state.cursor());
    }

    private ResizeState resolveResizeState(double x, double y, double width, double height) {
        Cursor cursor = Cursor.DEFAULT;
        int direction = 0;
        if (y < RESIZE_BORDER) {
            cursor = Cursor.N_RESIZE;
            direction = 1;
        } else if (y > height - RESIZE_BORDER) {
            cursor = Cursor.S_RESIZE;
            direction = 5;
        }
        if (x < RESIZE_BORDER) {
            return resolveLeftResize(direction);
        }
        if (x > width - RESIZE_BORDER) {
            return resolveRightResize(direction);
        }
        return new ResizeState(cursor, direction);
    }

    private ResizeState resolveLeftResize(int direction) {
        if (direction == 1) return new ResizeState(Cursor.NW_RESIZE, 8);
        if (direction == 5) return new ResizeState(Cursor.SW_RESIZE, 6);
        return new ResizeState(Cursor.W_RESIZE, 7);
    }

    private ResizeState resolveRightResize(int direction) {
        if (direction == 1) return new ResizeState(Cursor.NE_RESIZE, 2);
        if (direction == 5) return new ResizeState(Cursor.SE_RESIZE, 4);
        return new ResizeState(Cursor.E_RESIZE, 3);
    }

    private void handlePipMousePressed(MouseEvent event) {
        if (event.getButton() != MouseButton.PRIMARY) {
            return;
        }
        if (resizeDirection != 0) {
            isResizing = true;
            initialX = event.getScreenX();
            initialY = event.getScreenY();
            initialWidth = pipStage.getWidth();
            initialHeight = pipStage.getHeight();
            return;
        }
        xOffset = pipStage.getX() - event.getScreenX();
        yOffset = pipStage.getY() - event.getScreenY();
    }

    private void handlePipMouseDragged(MouseEvent event) {
        if (!isResizing) {
            pipStage.setX(event.getScreenX() + xOffset);
            pipStage.setY(event.getScreenY() + yOffset);
            return;
        }
        applyResizeState(calculatePipBounds(event));
    }

    private ResizedBounds calculatePipBounds(MouseEvent event) {
        double newWidth = initialWidth;
        double newHeight = initialHeight;
        double newX = pipStage.getX();
        double newY = pipStage.getY();
        double deltaX = event.getScreenX() - initialX;
        double deltaY = event.getScreenY() - initialY;
        switch (resizeDirection) {
            case 1 -> {
                newHeight = initialHeight - deltaY;
                newY = initialY + deltaY;
            }
            case 2 -> {
                newWidth = initialWidth + deltaX;
                newHeight = initialHeight - deltaY;
                newY = initialY + deltaY;
            }
            case 3 -> newWidth = initialWidth + deltaX;
            case 4 -> {
                newWidth = initialWidth + deltaX;
                newHeight = initialHeight + deltaY;
            }
            case 5 -> newHeight = initialHeight + deltaY;
            case 6 -> {
                newWidth = initialWidth - deltaX;
                newX = initialX + deltaX;
                newHeight = initialHeight + deltaY;
            }
            case 7 -> {
                newWidth = initialWidth - deltaX;
                newX = initialX + deltaX;
            }
            case 8 -> {
                newWidth = initialWidth - deltaX;
                newX = initialX + deltaX;
                newHeight = initialHeight - deltaY;
                newY = initialY + deltaY;
            }
            default -> {
                // keep the existing PiP bounds when no resize edge is active
            }
        }
        return enforceMinPipBounds(new ResizedBounds(newWidth, newHeight, newX, newY));
    }

    private ResizedBounds enforceMinPipBounds(ResizedBounds bounds) {
        double newWidth = bounds.width();
        double newHeight = bounds.height();
        double newX = bounds.x();
        double newY = bounds.y();
        if (newWidth < MIN_WIDTH) {
            if (resizeDirection == 7 || resizeDirection == 6 || resizeDirection == 8) {
                newX = pipStage.getX() + (pipStage.getWidth() - MIN_WIDTH);
            }
            newWidth = MIN_WIDTH;
        }
        if (newHeight < MIN_HEIGHT) {
            if (resizeDirection == 1 || resizeDirection == 2 || resizeDirection == 8) {
                newY = pipStage.getY() + (pipStage.getHeight() - MIN_HEIGHT);
            }
            newHeight = MIN_HEIGHT;
        }
        return new ResizedBounds(newWidth, newHeight, newX, newY);
    }

    private void applyResizeState(ResizedBounds bounds) {
        pipStage.setWidth(bounds.width());
        pipStage.setHeight(bounds.height());
        pipStage.setX(bounds.x());
        pipStage.setY(bounds.y());
    }

    private void finishPipInteraction() {
        isResizing = false;
        resizeDirection = 0;
        pipStage.getScene().setCursor(Cursor.DEFAULT);
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
        rewindIcon = createIconView("reverse-rewind.png", true);
        fastForwardIcon = createIconView("fast-forward.png", true);
        muteOnIcon = createIconView("mute-on.png", true);
        muteOffIcon = createIconView("mute-off.png", true);
        pipIcon = createIconView("picture-in-picture.png", true);
        pipExitIcon = createIconView("picture-in-picture-exit.png", false);
        aspectRatioIcon = createIconView("aspect-ratio.png", true);
        aspectRatioFillIcon = createIconView("aspect-ratio-fill.png", true);
        aspectRatioStretchIcon = createIconView("aspect-ratio-stretch.png", true);
        hideBarIcon = createIconView("arrow-down.png", true);
    }

    private record PipControlButtons(Button muteButton, Button reloadButton) {
    }

    private record ResizeState(Cursor cursor, int direction) {
    }

    private record ResizedBounds(double width, double height, double x, double y) {
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
        } catch (Exception _) {
            return new ImageView();
        }
    }

    private Button createIconButton(ImageView icon) {
        Button btn = new Button();
        btn.setGraphic(icon);
        btn.setPadding(new Insets(3));
        btn.getStyleClass().add(PLAYER_ICON_BUTTON);
        return btn;
    }

    private Button createTransportButton(ImageView icon, String tooltip) {
        Button btn = new Button();
        btn.setGraphic(icon);
        btn.getStyleClass().add(PLAYER_ICON_BUTTON);
        btn.setPadding(new Insets(3));
        btn.setTooltip(new Tooltip(tooltip));
        btn.setVisible(false);
        btn.setManaged(false);
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
        btn.setTooltip(new Tooltip("Audio / Playlist"));
        btn.getStyleClass().add(PLAYER_ICON_BUTTON);
        btn.setPadding(new Insets(3));
        return btn;
    }

    private boolean isOnDemandContent() {
        if (currentAccount != null && currentAccount.getAction() != null && Account.NOT_LIVE_TV_CHANNELS.contains(currentAccount.getAction())) {
            return true;
        }
        if (currentAccount != null && currentAccount.getAction() != null) {
            return false;
        }
        if (PlayerUrlUtils.isLikelyOnDemandPlaybackUrl(currentMediaUri)) {
            return true;
        }
        return isNotBlank(activeBingeWatchToken);
    }

    private void updateTransportButtonsVisibility() {
        boolean showSeekButtons = isOnDemandContent();
        btnRewind.setVisible(showSeekButtons);
        btnRewind.setManaged(showSeekButtons);
        btnFastForward.setVisible(showSeekButtons);
        btnFastForward.setManaged(showSeekButtons);
    }

    private void updateRepeatButtonVisibility() {
        boolean showRepeatButton = !isNotBlank(activeBingeWatchToken);
        btnRepeat.setVisible(showRepeatButton);
        btnRepeat.setManaged(showRepeatButton);
    }

    private void updateTrackButtonVisibility() {
        boolean showTracksButton = supportsTrackSelection() || isNotBlank(activeBingeWatchToken);
        btnTracks.setVisible(showTracksButton);
        btnTracks.setManaged(showTracksButton);
    }

    private List<TrackOption> getPlaylistTrackOptions() {
        if (!isNotBlank(activeBingeWatchToken)) {
            return Collections.emptyList();
        }
        List<BingeWatchService.PlaylistItem> playlistItems = BingeWatchService.getInstance().getPlaylistItems(activeBingeWatchToken);
        if (playlistItems.isEmpty()) {
            return Collections.emptyList();
        }
        List<TrackOption> options = new ArrayList<>(playlistItems.size());
        for (int i = 0; i < playlistItems.size(); i++) {
            BingeWatchService.PlaylistItem item = playlistItems.get(i);
            options.add(new TrackOption(i, formatPlaylistLabel(item)));
        }
        if (!isNotBlank(activeBingeWatchEpisodeId)) {
            activeBingeWatchEpisodeId = playlistItems.get(0).episodeId();
        }
        return options;
    }

    private String formatPlaylistLabel(BingeWatchService.PlaylistItem item) {
        if (item == null) {
            return "";
        }
        String episodeNumber = item.episodeNumber();
        if (isNotBlank(episodeNumber)) {
            return I18n.formatEpisodeLabel(episodeNumber) + ": " + item.episodeName();
        }
        return item.episodeName();
    }

    private String buildDisplayTitle() {
        String baseTitle = currentChannel == null ? "" : currentChannel.getName();
        if (!isNotBlank(activeBingeWatchToken)) {
            return baseTitle;
        }
        BingeWatchService.PlaylistItem currentItem = getCurrentPlaylistItem();
        if (currentItem == null) {
            return baseTitle;
        }
        String episodeLabel = formatDisplayEpisodeCode(currentItem);
        if (!isNotBlank(baseTitle)) {
            return episodeLabel;
        }
        if (!isNotBlank(episodeLabel) || episodeLabel.equals(baseTitle)) {
            return baseTitle;
        }
        return baseTitle + ": " + episodeLabel;
    }

    private String formatDisplayEpisodeCode(BingeWatchService.PlaylistItem item) {
        if (item == null) {
            return "";
        }
        String seasonCode = normalizeEpisodeIndex(item.season(), "S");
        String episodeCode = normalizeEpisodeIndex(item.episodeNumber(), "E");
        if (isNotBlank(seasonCode) && isNotBlank(episodeCode)) {
            return seasonCode + ":" + episodeCode;
        }
        return formatPlaylistLabel(item);
    }

    private String normalizeEpisodeIndex(String value, String prefix) {
        if (!isNotBlank(value)) {
            return "";
        }
        try {
            int number = Integer.parseInt(value.trim());
            return String.format("%s%02d", prefix, Math.max(number, 0));
        } catch (NumberFormatException _) {
            return prefix + value.trim();
        }
    }

    protected void refreshNowShowingHeader() {
        if (nowShowingFlow == null || streamInfoText == null) {
            return;
        }
        nowShowingFlow.getChildren().clear();
        String displayTitle = buildDisplayTitle();
        if (isNotBlank(displayTitle)) {
            Text channelNameText = new Text(displayTitle);
            channelNameText.getStyleClass().add("player-channel-title");
            applyFixedControlBarOrientation(channelNameText);
            nowShowingFlow.getChildren().addAll(channelNameText, streamInfoText);
        } else {
            nowShowingFlow.getChildren().add(streamInfoText);
        }
        nowShowingFlow.setVisible(true);
        nowShowingFlow.setManaged(true);
    }

    private BingeWatchService.PlaylistItem getCurrentPlaylistItem() {
        if (!isNotBlank(activeBingeWatchToken)) {
            return null;
        }
        List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(activeBingeWatchToken);
        if (items.isEmpty()) {
            return null;
        }
        for (BingeWatchService.PlaylistItem item : items) {
            if (item.episodeId().equals(activeBingeWatchEpisodeId)) {
                return item;
            }
        }
        return items.get(0);
    }

    private int getSelectedPlaylistTrackId() {
        List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(activeBingeWatchToken);
        for (int i = 0; i < items.size(); i++) {
            if (items.get(i).episodeId().equals(activeBingeWatchEpisodeId)) {
                return i;
            }
        }
        return Integer.MIN_VALUE;
    }

    private void selectPlaylistTrack(int index) {
        List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(activeBingeWatchToken);
        if (index < 0 || index >= items.size()) {
            return;
        }
        BingeWatchService.PlaylistItem selected = items.get(index);
        String playlistUrl = BingeWatchService.getInstance().buildPlaylistUrl(activeBingeWatchToken, selected.episodeId());
        if (!isNotBlank(playlistUrl)) {
            return;
        }
        activeBingeWatchEpisodeId = selected.episodeId();
        PlayerResponse response = new PlayerResponse(playlistUrl);
        response.setAccount(currentAccount);
        response.setChannel(currentChannel);
        play(response);
    }

    private void updateBingeWatchContext(String uri) {
        activeBingeWatchToken = extractBingeWatchQueryValue(uri, "token");
        activeBingeWatchEpisodeId = extractBingeWatchQueryValue(uri, "episodeId");
        if (!isNotBlank(activeBingeWatchToken)) {
            activeBingeWatchEpisodeId = "";
            return;
        }
        if (!isNotBlank(activeBingeWatchEpisodeId)) {
            List<BingeWatchService.PlaylistItem> items = BingeWatchService.getInstance().getPlaylistItems(activeBingeWatchToken);
            if (!items.isEmpty()) {
                activeBingeWatchEpisodeId = items.get(0).episodeId();
            }
        }
    }

    protected void updateBingeWatchEpisodeFromActiveMedia(String uri) {
        if (!isNotBlank(uri) || (!uri.contains("bingwatch") && !uri.contains("bingewatch"))) {
            return;
        }
        String nextToken = extractBingeWatchQueryValue(uri, "token");
        String nextEpisodeId = extractBingeWatchQueryValue(uri, "episodeId");
        boolean changed = false;
        if (isNotBlank(nextToken) && !nextToken.equals(activeBingeWatchToken)) {
            activeBingeWatchToken = nextToken;
            changed = true;
        }
        if (isNotBlank(nextEpisodeId) && !nextEpisodeId.equals(activeBingeWatchEpisodeId)) {
            activeBingeWatchEpisodeId = nextEpisodeId;
            changed = true;
        }
        if (changed) {
            refreshNowShowingHeader();
            refreshTrackMenus();
        }
    }

    private void updateBingeWatchWatchStateListener() {
        removeBingeWatchWatchStateListener();
        if (!hasBingeWatchSeriesContext()) {
            return;
        }
        bingeWatchStateChangeListener = (accountId, seriesId) -> {
            if (!matchesActiveBingeWatchSeries(accountId, seriesId)) {
                return;
            }
            SeriesWatchState state = SeriesWatchStateService.getInstance()
                    .getSeriesLastWatched(currentAccount.getDbId(), currentChannel.getChannelId());
            if (state == null || !isNotBlank(state.getEpisodeId()) || state.getEpisodeId().equals(activeBingeWatchEpisodeId)) {
                return;
            }
            Platform.runLater(() -> {
                activeBingeWatchEpisodeId = state.getEpisodeId();
                refreshNowShowingHeader();
                refreshTrackMenus();
            });
        };
        SeriesWatchStateService.getInstance().addChangeListener(bingeWatchStateChangeListener);
    }

    private void removeBingeWatchWatchStateListener() {
        if (bingeWatchStateChangeListener != null) {
            SeriesWatchStateService.getInstance().removeChangeListener(bingeWatchStateChangeListener);
            bingeWatchStateChangeListener = null;
        }
    }

    private void syncBingeWatchEpisodeFromWatchState() {
        if (!hasBingeWatchSeriesContext()) {
            return;
        }
        SeriesWatchState state = SeriesWatchStateService.getInstance()
                .getSeriesLastWatched(currentAccount.getDbId(), currentChannel.getChannelId());
        if (state != null && isNotBlank(state.getEpisodeId())) {
            activeBingeWatchEpisodeId = state.getEpisodeId();
        }
    }

    private boolean hasBingeWatchSeriesContext() {
        return isNotBlank(activeBingeWatchToken)
                && currentAccount != null
                && isNotBlank(currentAccount.getDbId())
                && currentChannel != null
                && isNotBlank(currentChannel.getChannelId());
    }

    private boolean matchesActiveBingeWatchSeries(String accountId, String seriesId) {
        return hasBingeWatchSeriesContext()
                && currentAccount.getDbId().equals(accountId)
                && currentChannel.getChannelId().equals(seriesId);
    }

    private String extractBingeWatchQueryValue(String uri, String key) {
        if (!isNotBlank(uri) || (!uri.contains("bingwatch") && !uri.contains("bingewatch"))) {
            return "";
        }
        int queryIndex = uri.indexOf('?');
        if (queryIndex < 0 || queryIndex >= uri.length() - 1) {
            return "";
        }
        String query = uri.substring(queryIndex + 1);
        for (String pair : query.split("&")) {
            int separator = pair.indexOf('=');
            if (separator <= 0) {
                continue;
            }
            if (key.equals(pair.substring(0, separator))) {
                return URLDecoder.decode(pair.substring(separator + 1), StandardCharsets.UTF_8);
            }
        }
        return "";
    }

    // --- Protected Utility Methods ---
    /**
     * Resolve HLS playlist chain (Master -> Variant) to an absolute URL that embedded players can handle.
     * Workaround for CDNs with relative paths or strict header requirements on manifest files.
     * Returns a playable .m3u8 URL (Media Playlist).
     */
    protected String resolveHlsPlaylistChain(String uri) {
        if (uri == null || !uri.toLowerCase().contains(".m3u8")) {
            return uri;
        }

        try {
            java.util.Map<String, String> headers = createBrowserHeaders();
            com.uiptv.util.HttpUtil.HttpResult result = com.uiptv.util.HttpUtil.sendRequest(uri, headers, "GET");
            if (result != null && result.statusCode() == 200 && result.body() != null) {
                String body = result.body();
                if (body.contains("#EXT-X-STREAM-INF")) {
                    String variantUrl = extractBestVariantUrl(uri, body);
                    if (variantUrl != null) {
                        com.uiptv.util.AppLog.addInfoLog(BaseVideoPlayer.class, "Resolved HLS master manifest to variant: " + variantUrl);
                        // Recursively resolve in case of multiple master levels, but stop at Media Playlist
                        return resolveHlsPlaylistChain(variantUrl);
                    }
                }
            }
        } catch (Exception e) {
            com.uiptv.util.AppLog.addWarningLog(BaseVideoPlayer.class, "Optional HLS resolution failed for: " + uri + " (" + e.getMessage() + ")");
        }
        return uri;
    }

    private String extractBestVariantUrl(String baseUrl, String playlistContent) {
        String bestUrl = null;
        long maxBandwidth = -1;

        String[] lines = playlistContent.split("\\r?\\n");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.startsWith("#EXT-X-STREAM-INF:")) {
                long bandwidth = parseBandwidth(line);
                if (i + 1 < lines.length) {
                    String urlLine = lines[i + 1].trim();
                    if (!urlLine.isEmpty() && !urlLine.startsWith("#")) {
                        if (bandwidth > maxBandwidth) {
                            maxBandwidth = bandwidth;
                            bestUrl = urlLine;
                        }
                    }
                }
            }
        }

        if (bestUrl != null) {
            try {
                java.net.URI base = java.net.URI.create(baseUrl);
                java.net.URI resolved = base.resolve(bestUrl);

                // Propagate query parameters from base URL if variant doesn't have its own
                if (baseUrl.contains("?") && !bestUrl.contains("?")) {
                    String query = baseUrl.substring(baseUrl.indexOf('?'));
                    return resolved.toString() + query;
                }
                return resolved.toString();
            } catch (Exception _) {
                // ignore resolution errors
            }
        }
        return null;
    }

    private long parseBandwidth(String line) {
        try {
            int index = line.toUpperCase().indexOf("BANDWIDTH=");
            if (index != -1) {
                String sub = line.substring(index + 10);
                int commaIndex = sub.indexOf(',');
                if (commaIndex != -1) {
                    sub = sub.substring(0, commaIndex);
                }
                return Long.parseLong(sub.trim());
            }
        } catch (Exception _) {
            // ignore
        }
        return 0;
    }

    private java.util.Map<String, String> createBrowserHeaders() {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (com.uiptv.service.ConfigurationService.getInstance().isVlcHttpUserAgentEnabled()) {
            headers.put("User-Agent", CHROME_USER_AGENT);
        }
        headers.put("Accept", "application/vnd.apple.mpegurl, */*");
        headers.put("Accept-Language", "en-US,en;q=0.9");
        return headers;
    }

    private static void markHiddenBarMessageShown() {
        hasShownHiddenBarMessage = true;
    }
}
