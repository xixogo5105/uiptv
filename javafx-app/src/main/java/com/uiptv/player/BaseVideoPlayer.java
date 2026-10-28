package com.uiptv.player;

import com.uiptv.player.api.VideoPlayerInterface;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.model.Configuration;
import com.uiptv.model.PlayerResponse;
import com.uiptv.model.SeriesWatchState;
import com.uiptv.service.BingeWatchService;
import com.uiptv.service.ConfigurationChangeListener;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.PlayerService;
import com.uiptv.service.SeriesWatchStateChangeListener;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.ui.RootApplication;
import com.uiptv.ui.util.StyleClassDecorator;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.HlsPlaylistResolver;
import com.uiptv.util.PlayerUrlUtils;
import javafx.animation.PauseTransition;
import javafx.application.Platform;
import javafx.event.EventHandler;
import javafx.event.EventTarget;
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
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;
import javafx.scene.shape.SVGPath;
import javafx.scene.text.Text;
import javafx.scene.text.TextFlow;
import javafx.stage.Screen;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.beans.property.ReadOnlyBooleanProperty;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.IntConsumer;

import static com.uiptv.util.StringUtils.isNotBlank;

public abstract class BaseVideoPlayer implements VideoPlayerInterface {
    private static final String STYLE_CLASS_PLAYER_STREAM_INFO_TEXT = "player-stream-info-text";
    private static final String I18N_PLAYER_PREVIOUS_ITEM = "playerPreviousItem";
    private static final String I18N_PLAYER_NEXT_ITEM = "playerNextItem";
    private static final int MAX_HLS_RESOLUTION_DEPTH = 8;
    private static final String STYLE_CLASS_PLAYER_ROUND_CONTROL_BUTTON = "player-round-control-button";
    private static final String STYLE_CLASS_PLAYER_PIP_OVERLAY_BUTTON = "player-pip-overlay-button";
    private static final String STYLE_CLASS_PLAYER_LAYOUT_MODE_BUTTON = "player-layout-mode-button";
    private static final String STYLE_CLASS_PLAYER_HIDDENBAR_CLOSE_BUTTON = "player-hiddenbar-close-button";
    public static final String PLAYER_ICON_BUTTON = "player-icon-button";
    public static final String PLAYER_TRACKS_MENU_ITEM = "player-tracks-menu-item";
    private static final String WIDE_LAYOUT_ICON = "M3 5H21V19H3V5ZM5 7V17H11V7H5ZM13 7V17H19V7H13Z";
    private static final String NARROW_LAYOUT_ICON = "M3 5H21V19H3V5ZM5 7V17H14V7H5ZM16 7V17H19V7H16Z";

    // State
    protected boolean isMuted = true;
    protected Account currentAccount;
    protected Channel currentChannel;
    protected boolean isRepeating = false;
    protected int retryCount = 0;
    protected final AtomicBoolean isRetrying = new AtomicBoolean(false);
    protected final AtomicBoolean isDisposed = new AtomicBoolean(false);
    protected String currentMediaUri;
    protected static final int ASPECT_RATIO_FIT = 0;
    protected static final int ASPECT_RATIO_FILL = 1;
    protected static final int ASPECT_RATIO_STRETCH = 2;
    protected int aspectRatioMode = ASPECT_RATIO_FIT; // 0=Fit, 1=Crop, 2=Stretch
    protected boolean isUserSeeking = false;

    protected final BooleanProperty pip = new SimpleBooleanProperty(false);
    protected final BooleanProperty fullscreen = new SimpleBooleanProperty(false);

    @Override
    public boolean isPip() {
        return pip.get();
    }

    @Override
    public boolean isFullscreen() {
        return fullscreen.get();
    }

    @Override
    public ReadOnlyBooleanProperty pipProperty() {
        return pip;
    }

    @Override
    public ReadOnlyBooleanProperty fullscreenProperty() {
        return fullscreen;
    }

    // UI Components
    protected Slider timeSlider;
    protected Slider volumeSlider;
    protected Label timeLabel;
    protected VBox controlsContainer;
    protected ProgressIndicator loadingSpinner;
    private final java.util.concurrent.atomic.AtomicLong refreshVersion = new java.util.concurrent.atomic.AtomicLong();
    private java.util.concurrent.FutureTask<Void> refreshTask;
    private final PauseTransition startupTimeout = new PauseTransition(Duration.seconds(45));
    private static final int MAX_AUTOMATIC_LIVE_RECOVERY_ATTEMPTS = 5;
    private static final int[] LIVE_RECOVERY_DELAY_SECONDS = {1, 2, 5, 10, 20};
    private PauseTransition liveRecoveryTimer;
    protected Label errorLabel;
    protected TextFlow guideInfoFlow;
    protected HBox nowShowingFlow;
    protected TextFlow headerDetailsFlow;
    protected Text streamInfoText;
    protected HBox timeRow;
    protected StackPane playerContainer = new StackPane();

    // Buttons and Icons
    protected Button btnPlayPause;
    protected Button btnMute;
    protected Button btnPreviousChannel;
    protected Button btnNextChannel;
    protected Button btnRepeat;
    protected Button btnFullscreen;
    protected Button btnReload;
    protected Button btnPip;
    protected Button btnLayoutMode;
    protected Button btnStop;
    protected Button btnRewind;
    protected Button btnFastForward;
    protected Button btnAspectRatio;
    protected Button btnHideBar;
    protected Button btnTracks;
    protected Button btnGuide;
    protected com.uiptv.ui.PlayerGuideOverlay guideOverlay;
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
    protected SVGPath layoutModeIcon;
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
    protected boolean isPointerInsideControlBar = false;
    private boolean keepControlBarOpenForNavigation;
    protected StackPane hiddenBarMessage; // Changed from HBox to StackPane
    protected PauseTransition hiddenBarMessageHideTimer;
    protected static boolean hasShownHiddenBarMessage = false;
    protected boolean isTracksMenuOpen = false;
    protected Scene activeInputRecoveryScene;
    protected Scene pipInputRecoveryScene;
    protected boolean isLiveLikeContent = true;
    protected String activeBingeWatchToken = "";
    protected String activeBingeWatchEpisodeId = "";
    private boolean primaryStageAlwaysOnTopBeforeVideoOverlay;
    private boolean primaryStageAlwaysOnTopSuppressedForVideoOverlay;
    private boolean fullscreenExitPending;
    private SeriesWatchStateChangeListener bingeWatchStateChangeListener;
    private final EventHandler<InputEvent> sceneInputRecoveryHandler = event -> handleSceneInputRecovery(event);
    private final ConfigurationChangeListener layoutModeConfigurationChangeListener =
            _ -> Platform.runLater(this::updateLayoutModeButton);
    private final javafx.beans.value.ChangeListener<com.uiptv.application.PlayerEpgApplicationService.Result> guideSummaryChangeListener =
            (_, _, _) -> Platform.runLater(this::refreshNowShowingHeader);
    private double lastMouseEventScreenX = Double.NaN;
    private double lastMouseEventScreenY = Double.NaN;

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
        PlayerChannelNavigation.queueProperty().addListener((_, _, _) -> updateChannelNavigationButtons());
        updateChannelNavigationButtons();
        ConfigurationService.getInstance().addChangeListener(layoutModeConfigurationChangeListener);
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
        guideInfoFlow = new TextFlow();
        guideInfoFlow.getStyleClass().add(STYLE_CLASS_PLAYER_STREAM_INFO_TEXT);
        nowShowingFlow = new HBox(5);
        nowShowingFlow.setAlignment(Pos.CENTER_LEFT);
        nowShowingFlow.setPadding(new Insets(0, 0, 5, 0));
        streamInfoText = new Text();
        streamInfoText.getStyleClass().add(STYLE_CLASS_PLAYER_STREAM_INFO_TEXT);
        headerDetailsFlow = new TextFlow();
        headerDetailsFlow.getStyleClass().add("player-header-details");
        applyFixedControlBarOrientation(nowShowingFlow, headerDetailsFlow, streamInfoText);

        btnPlayPause = createIconButton(pauseIcon);
        btnStop = createIconButton(stopIcon);
        btnPreviousChannel = createIconButton(createChannelNavigationIcon(false));
        btnPreviousChannel.setTooltip(new Tooltip(I18n.tr(I18N_PLAYER_PREVIOUS_ITEM)));
        btnPreviousChannel.setAccessibleText(I18n.tr(I18N_PLAYER_PREVIOUS_ITEM));
        btnPreviousChannel.setVisible(false);
        btnPreviousChannel.setManaged(false);
        btnNextChannel = createIconButton(createChannelNavigationIcon(true));
        btnNextChannel.setTooltip(new Tooltip(I18n.tr(I18N_PLAYER_NEXT_ITEM)));
        btnNextChannel.setAccessibleText(I18n.tr(I18N_PLAYER_NEXT_ITEM));
        btnNextChannel.setVisible(false);
        btnNextChannel.setManaged(false);
        btnRewind = createTransportButton(rewindIcon, "Rewind 15s");
        btnFastForward = createTransportButton(fastForwardIcon, "Fast forward 15s");
        btnRepeat = createIconButton(repeatOffIcon);
        btnRepeat.setOpacity(0.7);
        btnReload = createIconButton(reloadIcon);
        btnFullscreen = createIconButton(fullscreenIcon);
        btnPip = createIconButton(pipIcon);
        btnLayoutMode = createIconButton(layoutModeIcon);
        btnLayoutMode.getStyleClass().add(STYLE_CLASS_PLAYER_LAYOUT_MODE_BUTTON);
        btnLayoutMode.setFocusTraversable(false);
        btnAspectRatio = createIconButton(aspectRatioIcon);
        btnAspectRatio.setTooltip(new Tooltip(I18n.tr("autoFit")));
        updateLayoutModeButton();

        btnHideBar = createIconButton(hideBarIcon);
        btnHideBar.setTooltip(new Tooltip(I18n.tr("autoHideThisBar")));

        btnTracks = createTrackRootButton();
        tracksContextMenu = new ContextMenu();
        UiI18n.preparePopupControl(tracksContextMenu, btnTracks);
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
        SVGPath guideIcon = new SVGPath();
        guideIcon.setContent("M2 2H14V14H2Z M5 5H11 M5 8H11 M5 11H9");
        guideIcon.setScaleX(1.15);
        guideIcon.setScaleY(1.15);
        guideIcon.getStyleClass().add("player-guide-icon");
        btnGuide = createIconButton(guideIcon);
        btnGuide.getStyleClass().add("player-guide-button");
        btnGuide.setText(I18n.tr("epgOpenGuide"));
        // Keep the guide as the final control, after channel navigation.
        // Narrow mode already shows EPG inline beside the player, so the overlay
        // controls its visibility from the active layout mode.
        buttonRow.getChildren().addAll(btnPlayPause, btnStop, btnPreviousChannel, btnNextChannel, btnRewind, btnFastForward,
                btnRepeat, btnReload, btnFullscreen, btnPip, spacer, btnMute,
                volumeSlider, btnLayoutMode, btnAspectRatio, btnTracks, btnHideBar, btnGuide);

        timeSlider = new Slider(0, 1, 0);
        timeSlider.getStyleClass().add("video-player-slider");
        HBox.setHgrow(timeSlider, Priority.ALWAYS);
        timeLabel = new Label(I18n.tr("auto00000000"));
        timeLabel.getStyleClass().add("player-time-label");

        timeRow = new HBox(2.5);
        timeRow.setAlignment(Pos.CENTER_LEFT);
        timeRow.getChildren().addAll(timeSlider, timeLabel);
        applyFixedControlBarOrientation(buttonRow, timeRow, volumeSlider, timeSlider);

        controlsContainer = new VBox(4);
        controlsContainer.setPadding(new Insets(7));
        controlsContainer.getStyleClass().add("player-controls-container");
        controlsContainer.getChildren().addAll(guideInfoFlow, nowShowingFlow, headerDetailsFlow, buttonRow, timeRow);
        controlsContainer.setMaxWidth(640);
        controlsContainer.setPrefWidth(Region.USE_COMPUTED_SIZE);
        controlsContainer.setMaxHeight(Region.USE_PREF_SIZE);
        applyFixedControlBarOrientation(controlsContainer, guideInfoFlow, nowShowingFlow, headerDetailsFlow, buttonRow, timeRow, timeLabel, volumeSlider, timeSlider);

        playerContainer.getStyleClass().add("player-container");
        playerContainer.setFocusTraversable(true);
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);
        Rectangle playerClip = new Rectangle();
        playerClip.widthProperty().bind(playerContainer.widthProperty());
        playerClip.heightProperty().bind(playerContainer.heightProperty());
        playerContainer.setClip(playerClip);

        StackPane overlayWrapper = new StackPane(controlsContainer);
        overlayWrapper.setAlignment(Pos.BOTTOM_CENTER);
        overlayWrapper.setPadding(new Insets(0, 10, 10, 10));
        overlayWrapper.setManaged(false);
        overlayWrapper.setMinSize(0, 0);
        overlayWrapper.setMaxSize(Double.MAX_VALUE, Double.MAX_VALUE);

        Runnable layoutControlOverlay = () -> overlayWrapper.resizeRelocate(0, 0,
                playerContainer.getWidth(),
                playerContainer.getHeight());
        playerContainer.widthProperty().addListener((obs, oldVal, newVal) -> {
            updateVideoSize();
            layoutControlOverlay.run();
        });
        playerContainer.heightProperty().addListener((obs, oldVal, newVal) -> {
            updateVideoSize();
            layoutControlOverlay.run();
        });

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
        msgCloseBtn.getStyleClass().add(STYLE_CLASS_PLAYER_HIDDENBAR_CLOSE_BUTTON);
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
        guideOverlay = new com.uiptv.ui.PlayerGuideOverlay(playerContainer, btnGuide, this::isLiveGuideContent);
        MediaPlayerFactory.guideSummaryProperty().addListener(guideSummaryChangeListener);
        StackPane.setAlignment(guideOverlay, Pos.CENTER);
        playerContainer.getChildren().add(guideOverlay);
        Platform.runLater(layoutControlOverlay);
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
        btnPreviousChannel.setOnAction(e -> navigateToAdjacentChannel(false));
        btnNextChannel.setOnAction(e -> navigateToAdjacentChannel(true));
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
        btnLayoutMode.setOnAction(e -> toggleWideViewPreference());
        btnAspectRatio.setOnAction(e -> toggleAspectRatio());
        btnHideBar.setOnAction(e -> hideControlBarByUser());
        btnMute.setOnAction(e -> {
            isMuted = !isMuted;
            setMute(isMuted);
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        });
        volumeSlider.valueProperty().addListener((e, t, newVal) -> setVolume(newVal.doubleValue()));
    }

    private void navigateToAdjacentChannel(boolean next) {
        boolean previousKeepOpenRequest = keepControlBarOpenForNavigation;
        keepControlBarOpenForNavigation = true;
        boolean moved = next ? PlayerChannelNavigation.next() : PlayerChannelNavigation.previous();
        if (!moved) {
            keepControlBarOpenForNavigation = previousKeepOpenRequest;
        }
    }

    private void wireSliderInteractions() {
        timeSlider.setOnMousePressed(e -> {
            restoreVisibleCursor();
            isUserSeeking = true;
            if (isFullscreen) idleTimer.stop();
        });
        timeSlider.setOnMouseReleased(e -> {
            restoreVisibleCursor();
            if (isOnDemandContent()) {
                seek((float) timeSlider.getValue());
            }
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
        rememberMouseEventPosition(event);
        onPlayerInteraction();
        if (isControlBarEvent(event)) {
            return;
        }
        if (event.getButton() == MouseButton.PRIMARY) {
            handlePrimaryPlayerClick(event);
            return;
        }
        if (event.getButton() == MouseButton.SECONDARY) {
            showControlBar();
        }
    }

    private boolean isControlBarEvent(MouseEvent event) {
        EventTarget target = event.getTarget();
        if (!(target instanceof Node node)) {
            return false;
        }
        for (Node current = node; current != null; current = current.getParent()) {
            if (current == controlsContainer) {
                return true;
            }
        }
        return false;
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
        lastMouseEventScreenX = Double.NaN;
        lastMouseEventScreenY = Double.NaN;
        onPlayerInteraction();
    }

    private void handlePlayerScroll(ScrollEvent event) {
        if (Math.abs(event.getDeltaY()) < 0.01) {
            return;
        }
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
        playerContainer.addEventFilter(MouseEvent.MOUSE_MOVED, this::handlePlayerMouseMoved);
        playerContainer.addEventFilter(MouseEvent.MOUSE_ENTERED, this::handlePlayerMouseEntered);
        playerContainer.setOnMouseExited(this::handlePlayerMouseExited);
        controlsContainer.setOnMouseEntered(this::handleControlBarMouseEntered);
        controlsContainer.setOnMouseExited(this::handleControlBarMouseExited);
        controlsContainer.setOnMouseMoved(this::handleControlBarMouseMoved);
    }

    private void handlePlayerMouseMoved(MouseEvent event) {
        if (isRepeatedMousePosition(event)) return;
        isPointerInsidePlayer = true;
        handlePlayerInteraction();
    }

    private void handlePlayerMouseEntered(MouseEvent event) {
        rememberMouseEventPosition(event);
        isPointerInsidePlayer = true;
        handlePlayerInteraction();
    }

    private void handlePlayerMouseExited(MouseEvent event) {
        rememberMouseEventPosition(event);
        if (isPointerOverControlBar(event)) {
            isPointerInsidePlayer = true;
            isPointerInsideControlBar = true;
            if (!isControlBarHiddenByUser) controlsContainer.setVisible(true);
            idleTimer.stop();
            return;
        }
        isPointerInsidePlayer = false;
        isPointerInsideControlBar = false;
        idleTimer.stop();
        restoreVisibleCursor();
        if (!isTracksMenuOpen) controlsContainer.setVisible(false);
    }

    private void handleControlBarMouseEntered(MouseEvent event) {
        rememberMouseEventPosition(event);
        isPointerInsideControlBar = true;
        isPointerInsidePlayer = true;
        handlePlayerInteraction();
    }

    private void handleControlBarMouseExited(MouseEvent event) {
        rememberMouseEventPosition(event);
        if (isPointerOverControlBar(event)) {
            isPointerInsideControlBar = true;
            isPointerInsidePlayer = true;
            idleTimer.stop();
            return;
        }
        isPointerInsideControlBar = false;
        if (isPointerInsidePlayer) restartIdleTimerForActivePlayer();
        else idleTimer.stop();
    }

    private void handleControlBarMouseMoved(MouseEvent event) {
        if (isRepeatedMousePosition(event)) {
            event.consume();
            return;
        }
        isPointerInsideControlBar = true;
        isPointerInsidePlayer = true;
        handlePlayerInteraction();
        event.consume();
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
        if (isPointerOverControlBar(lastMouseEventScreenX, lastMouseEventScreenY)) {
            isPointerInsideControlBar = true;
            if (!isControlBarHiddenByUser) {
                controlsContainer.setVisible(true);
            }
            idleTimer.stop();
            restoreVisibleCursor();
            return;
        }
        isPointerInsideControlBar = false;
        if (!playerContainer.isVisible() || !playerContainer.isManaged() || !isPointerInsidePlayer
                || isTracksMenuOpen) {
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

    private boolean isPointerOverControlBar(MouseEvent event) {
        return event != null && isPointerOverControlBar(event.getScreenX(), event.getScreenY());
    }

    private boolean isPointerOverControlBar(double screenX, double screenY) {
        if (controlsContainer == null || !controlsContainer.isVisible()) {
            return false;
        }
        if (Double.isFinite(screenX) && Double.isFinite(screenY)) {
            var screenBounds = controlsContainer.localToScreen(controlsContainer.getBoundsInLocal());
            if (screenBounds != null) {
                return screenBounds.contains(screenX, screenY);
            }
        }
        return isPointerInsideControlBar;
    }

    private void restoreVisibleCursor() {
        if (playerContainer != null) {
            playerContainer.setCursor(null);
            if (playerContainer.getScene() != null) {
                playerContainer.getScene().setCursor(null);
            }
        }
        if (fullscreenStage != null && fullscreenStage.getScene() != null) {
            fullscreenStage.getScene().setCursor(null);
        }
        if (originalParent != null && originalParent.getScene() != null) {
            originalParent.getScene().setCursor(null);
        }
        if (pipStage != null && pipStage.getScene() != null) {
            pipStage.getScene().setCursor(null);
        }
    }

    private void handleSceneInputRecovery(InputEvent event) {
        if (event instanceof MouseEvent mouseEvent) {
            javafx.event.EventType<? extends MouseEvent> eventType = mouseEvent.getEventType();
            if (eventType == MouseEvent.MOUSE_MOVED
                    || eventType == MouseEvent.MOUSE_ENTERED
                    || eventType == MouseEvent.MOUSE_ENTERED_TARGET
                    || eventType == MouseEvent.MOUSE_EXITED
                    || eventType == MouseEvent.MOUSE_EXITED_TARGET) {
                return;
            }
        }
        if (event instanceof ScrollEvent scrollEvent && Math.abs(scrollEvent.getDeltaY()) < 0.01) {
            return;
        }
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
        if (isPointerInsideControlBar) {
            idleTimer.stop();
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

    private boolean isRepeatedMousePosition(MouseEvent event) {
        if (event == null) {
            return false;
        }
        double screenX = event.getScreenX();
        double screenY = event.getScreenY();
        boolean repeated = Math.abs(screenX - lastMouseEventScreenX) < 0.01
                && Math.abs(screenY - lastMouseEventScreenY) < 0.01;
        rememberMouseEventPosition(event);
        return repeated;
    }

    private void rememberMouseEventPosition(MouseEvent event) {
        if (event == null) {
            return;
        }
        lastMouseEventScreenX = event.getScreenX();
        lastMouseEventScreenY = event.getScreenY();
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
        appendTrackSection(items, I18n.tr("autoAudioTracksSection"), getAudioTrackOptions(),
                getSelectedAudioTrackId(), this::selectAudioTrack, I18n.tr("autoNoAudioTracks"));

        List<TrackOption> playlistOptions = getPlaylistTrackOptions();
        if (!playlistOptions.isEmpty()) {
            if (!items.isEmpty()) {
                items.add(new SeparatorMenuItem());
            }
            appendTrackSection(items, I18n.tr("autoPlaylistTracksSection"), playlistOptions,
                    getSelectedPlaylistTrackId(), this::selectPlaylistTrack, I18n.tr("autoNoPlaylistItems"));
        }
        if (items.isEmpty()) {
            MenuItem noneItem = new MenuItem(I18n.tr("autoNoOptions"));
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
        if (isDisposed.get()) {
            return;
        }
        if (!isInternalRetry) {
            cancelStreamRefresh();
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
            guideOverlay.updateAvailability();
        } else {
            uri = null;
        }

        if (isNotBlank(uri)) {
            // Only request native provider EPG after link resolution produced a
            // playable URI. updateChannelInfo runs before that network work.
            MediaPlayerFactory.updateGuideContext(currentAccount, currentChannel);
            this.currentMediaUri = uri;
            updateBingeWatchContext(uri);
            syncBingeWatchEpisodeFromWatchState();
            updateBingeWatchWatchStateListener();
            preparePlayerUiForPlayback(true);

            setMute(isMuted);
            btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
            setVolume(volumeSlider.getValue());
            updateVideoSize();

            armStartupTimeout();
            playMedia(uri);
        } else {
            MediaPlayerFactory.updateGuideContext(null, null);
            showLoading();
            showPlaybackError();
        }
    }

    private void preparePlayerUiForPlayback(boolean consumeNavigationIntent) {
        boolean keepControlBarOpen = keepControlBarOpenForNavigation || isPointerInsideControlBar;
        if (consumeNavigationIntent) {
            keepControlBarOpenForNavigation = false;
        }
        playerContainer.setVisible(true);
        playerContainer.setManaged(true);
        playerContainer.setMinHeight(275);
        isPointerInsidePlayer = keepControlBarOpen;
        isPointerInsideControlBar = keepControlBarOpen;
        isLiveLikeContent = true;
        loadingSpinner.setVisible(true);
        errorLabel.setVisible(false);
        controlsContainer.setVisible(keepControlBarOpen && !isControlBarHiddenByUser);
        updatePlaybackTimeUi(0, 0);
        restoreVisibleCursor();
        updateTrackButtonVisibility();
        updateTransportButtonsVisibility();
        updateRepeatButtonVisibility();

        streamInfoText.setText("");
        refreshNowShowingHeader();
    }

    @Override
    public void stop() {
        PlayerChannelNavigation.clear();
        cancelStreamRefresh();
        cancelLiveRecovery();
        com.uiptv.ui.PlaybackUIService.cancelPendingPlayback();
        startupTimeout.stop();
        if (pipStage != null) {
            exitPip();
        }
        exitFullscreen();
        retryCount = 0;
        isRetrying.set(false);
        idleTimer.stop();
        isPointerInsidePlayer = false;
        isPointerInsideControlBar = false;
        activeBingeWatchToken = "";
        activeBingeWatchEpisodeId = "";
        removeBingeWatchWatchStateListener();
        isLiveLikeContent = true;
        guideOverlay.updateAvailability();
        updateTrackButtonVisibility();
        updateRepeatButtonVisibility();
        com.uiptv.widget.ResponsiveCardGrid.clearActivePlayingIndicator();
        stopMedia();
        playerContainer.setMinHeight(0);
        playerContainer.setVisible(false);
        playerContainer.setManaged(false);
        btnMute.setGraphic(isMuted ? muteOnIcon : muteOffIcon);
        restoreVisibleCursor();
    }

    @Override
    public void showLoading() {
        if (isDisposed.get()) return;
        preparePlayerUiForPlayback(false);
        armStartupTimeout();
    }

    private void armStartupTimeout() {
        startupTimeout.setOnFinished(_ -> {
            com.uiptv.ui.PlaybackUIService.cancelPendingPlayback();
            stopForReload();
            showPlaybackError();
        });
        startupTimeout.playFromStart();
    }

    protected void playbackStarted() {
        startupTimeout.stop();
    }

    /**
     * A live provider can end or corrupt a transport connection without the user
     * asking to stop playback. Clear any pending recovery once a new stream has
     * actually reached the playing state.
     */
    protected void playbackRecovered() {
        cancelLiveRecovery();
        // Keep the live recovery budget across short-lived "playing" sessions;
        // a provider that fails every 20-30 seconds must not retry forever.
        if (isOnDemandContent()) {
            retryCount = 0;
        }
    }

    /**
     * Schedules a fresh provider URL after an unexpected live-stream failure.
     * Returns true when the event was handled, including when the retry budget is
     * exhausted and the terminal playback error is shown.
     */
    protected boolean scheduleLivePlaybackRecovery() {
        if (isDisposed.get() || !isRetrying.get() || isOnDemandContent()
                || !isLiveLikeContent || currentAccount == null || currentChannel == null) {
            return false;
        }
        if (liveRecoveryTimer != null) {
            return true;
        }
        if (retryCount >= MAX_AUTOMATIC_LIVE_RECOVERY_ATTEMPTS) {
            isRetrying.set(false);
            stopForReload();
            showLoading();
            showPlaybackError();
            errorLabel.setText(I18n.tr("autoFailedToReconnectAfter5Attempts"));
            return true;
        }

        int attempt = ++retryCount;
        int delaySeconds = LIVE_RECOVERY_DELAY_SECONDS[attempt - 1];
        com.uiptv.util.AppLog.addWarningLog(BaseVideoPlayer.class,
                "Scheduling automatic live playback recovery attempt=" + attempt
                        + " delaySeconds=" + delaySeconds);
        showLoading();
        PauseTransition recovery = new PauseTransition(Duration.seconds(delaySeconds));
        liveRecoveryTimer = recovery;
        recovery.setOnFinished(_ -> {
            if (liveRecoveryTimer != recovery) {
                return;
            }
            liveRecoveryTimer = null;
            if (isRetrying.get() && !isDisposed.get()) {
                refreshAndPlay();
            }
        });
        recovery.playFromStart();
        return true;
    }

    private void cancelLiveRecovery() {
        if (liveRecoveryTimer != null) {
            liveRecoveryTimer.stop();
            liveRecoveryTimer = null;
        }
    }

    @Override
    public void showPlaybackError() {
        keepControlBarOpenForNavigation = false;
        startupTimeout.stop();
        loadingSpinner.setVisible(false);
        errorLabel.setText(I18n.tr("autoCouldNotPlayVideoUnsupportedFormatOrNetworkError"));
        errorLabel.setVisible(true);
        controlsContainer.setVisible(true);
    }

    @Override
    public void updateChannelInfo(Channel channel, Account account) {
        this.currentChannel = channel;
        this.currentAccount = account;
        guideOverlay.updateAvailability();
        // This is called before playback resolves a provider link. Clear any
        // old guide context without triggering a fetch for a stream that may
        // never be opened.
        MediaPlayerFactory.updateGuideContext(null, null);
        streamInfoText.setText("");
        refreshNowShowingHeader();
    }

    @Override
    public void stopForReload() {
        cancelStreamRefresh();
        cancelLiveRecovery();
        startupTimeout.stop();
        removeBingeWatchWatchStateListener();
        if (Platform.isFxApplicationThread()) {
            disposeMedia();
        } else {
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    disposeMedia();
                } finally {
                    latch.countDown();
                }
            });
            try {
                if (!latch.await(10, TimeUnit.SECONDS)) {
                    com.uiptv.util.AppLog.addWarningLog(BaseVideoPlayer.class,
                            "Timed out waiting for JavaFX player cleanup during reload");
                }
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
        com.uiptv.ui.PlaybackUIService.cancelPendingPlayback();
        startupTimeout.stop();
        if (isDisposed.getAndSet(true)) {
            return;
        }
        try {
            ConfigurationService.getInstance().removeChangeListener(layoutModeConfigurationChangeListener);
            MediaPlayerFactory.guideSummaryProperty().removeListener(guideSummaryChangeListener);
            // First stop any current playback and dispose current media
            stopForReload();
        } catch (Exception _) {
            // Best-effort shutdown; continue disposing the remaining player resources.
        }

        try {
            disposeUiResources();
        } catch (Exception _) {
            // Best-effort shutdown; media disposal failures should not leave stale UI around.
        }
    }

    private void disposeUiResources() {
        if (guideOverlay != null) guideOverlay.close();
        clearNodeChildren(playerContainer);
        clearNodeChildren(controlsContainer);
        if (tracksContextMenu != null) tracksContextMenu.getItems().clear();
        clearNodeChildren(nowShowingFlow);
        closeOverlayStage(fullscreenStage, fullscreen);
        fullscreenStage = null;
        closeOverlayStage(pipStage, pip);
        pipStage = null;
        restorePrimaryStageAlwaysOnTopAfterVideoOverlay();
        if (idleTimer != null) idleTimer.stop();
        if (hiddenBarMessageHideTimer != null) hiddenBarMessageHideTimer.stop();
        restoreVisibleCursor();
    }

    private void clearNodeChildren(javafx.scene.layout.Pane pane) {
        if (pane != null) pane.getChildren().clear();
    }

    private void closeOverlayStage(Stage stage, BooleanProperty active) {
        if (stage == null) return;
        try {
            uninstallSceneInputRecovery(stage.getScene());
            stage.close();
        } catch (Exception _) {
            // Ignore stage teardown issues during shutdown.
        }
        active.set(false);
    }

    protected void handleRepeat() {
        if (!isRetrying.get()) return;
        if (retryCount < 5) {
            retryCount++;
            if (retryCount == 1) {
                refreshAndPlay();
            } else {
                long request = refreshVersion.get();
                PauseTransition delay = new PauseTransition(Duration.seconds(10));
                delay.setOnFinished(e -> {
                    if (isRetrying.get() && request == refreshVersion.get() && !isDisposed.get()) refreshAndPlay();
                });
                delay.play();
            }
        } else {
            isRetrying.set(false);
            stopForReload();
            showLoading();
            showPlaybackError();
            errorLabel.setText(I18n.tr("autoFailedToReconnectAfter5Attempts"));
        }
    }

    private void cancelStreamRefresh() {
        refreshVersion.incrementAndGet();
        if (refreshTask != null) {
            refreshTask.cancel(true);
            refreshTask = null;
        }
    }

    protected void refreshAndPlay() {
        stopForReload();
        showLoading();
        long request = refreshVersion.get();
        Account account = currentAccount;
        Channel channel = currentChannel;
        String uri = currentMediaUri;
        var ready = playbackReady();
        refreshTask = new java.util.concurrent.FutureTask<>(() -> runRefreshTask(ready, request, account, channel, uri));
        Thread.ofVirtual().name("vlc-stream-refresh").start(refreshTask);
    }

    private Void runRefreshTask(java.util.concurrent.CompletionStage<?> ready, long request,
                                Account account, Channel channel, String uri) {
        try {
            ready.toCompletableFuture().get();
            if (request != refreshVersion.get() || isDisposed.get()) return null;
            PlayerResponse response = account != null && channel != null
                    ? PlayerService.getInstance().get(account, channel) : new PlayerResponse(uri);
            Platform.runLater(() -> {
                if (request == refreshVersion.get() && !isDisposed.get()) play(response, true);
            });
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        } catch (Exception _) {
            Platform.runLater(() -> {
                if (request == refreshVersion.get() && !isDisposed.get()) showPlaybackError();
            });
        }
        return null;
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

    private void toggleWideViewPreference() {
        Configuration current = readConfigurationSafely();
        if (current == null || !current.isEmbeddedPlayer()) {
            updateLayoutModeButton();
            return;
        }
        boolean nextWideView = !current.isWideView();
        applyLayoutModeButtonState(true, nextWideView);
        saveWideViewPreferenceAsync(nextWideView);
    }

    protected void saveWideViewPreferenceAsync(boolean wideView) {
        Thread saveThread = new Thread(() -> saveWideViewPreference(wideView), "embedded-player-wide-view-save");
        saveThread.setDaemon(true);
        saveThread.start();
    }

    private void saveWideViewPreference(boolean wideView) {
        Configuration configuration = readConfigurationSafely();
        if (configuration == null || !configuration.isEmbeddedPlayer() || configuration.isWideView() == wideView) {
            return;
        }
        configuration.setWideView(wideView);
        try {
            ConfigurationService.getInstance().save(configuration);
        } catch (RuntimeException _) {
            Platform.runLater(this::updateLayoutModeButton);
        }
    }

    private void updateLayoutModeButton() {
        Configuration configuration = readConfigurationSafely();
        boolean available = configuration != null && configuration.isEmbeddedPlayer();
        applyLayoutModeButtonState(available, available && configuration.isWideView());
    }

    private Configuration readConfigurationSafely() {
        try {
            return ConfigurationService.getInstance().read();
        } catch (RuntimeException _) {
            return null;
        }
    }

    private void applyLayoutModeButtonState(boolean available, boolean wideView) {
        if (btnLayoutMode == null || layoutModeIcon == null) {
            return;
        }
        btnLayoutMode.setVisible(available);
        btnLayoutMode.setManaged(available);
        layoutModeIcon.setContent(wideView ? WIDE_LAYOUT_ICON : NARROW_LAYOUT_ICON);
        layoutModeIcon.setOpacity(wideView ? 1.0 : 0.72);
        String tooltipText = I18n.tr("configWideView") + ": " + I18n.tr(wideView ? "commonEnabled" : "commonDisabled");
        if (btnLayoutMode.getTooltip() == null) {
            btnLayoutMode.setTooltip(new Tooltip(tooltipText));
        } else {
            btnLayoutMode.getTooltip().setText(tooltipText);
        }
        btnLayoutMode.setAccessibleText(tooltipText);
    }

    private void updateAspectRatioButtonState() {
        String tooltipText = switch (aspectRatioMode) {
            case ASPECT_RATIO_FILL -> I18n.tr("playerScaleCrop");
            case ASPECT_RATIO_STRETCH -> I18n.tr("playerScaleStretch");
            default -> I18n.tr("autoFit");
        };
        ImageView icon = switch (aspectRatioMode) {
            case ASPECT_RATIO_FILL -> aspectRatioFillIcon;
            case ASPECT_RATIO_STRETCH -> aspectRatioStretchIcon;
            default -> aspectRatioIcon;
        };
        btnAspectRatio.setGraphic(icon);
        btnAspectRatio.setAccessibleText(tooltipText);
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

    protected void updatePlaybackTimeUi(long currentTimeMs, long totalTimeMs) {
        long safeCurrent = Math.max(0, currentTimeMs);
        boolean hasKnownTotal = totalTimeMs > 0;
        boolean isLiveLike = !isOnDemandContent();
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

        timeLabel.setText(I18n.tr("playerLive"));
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
            suppressPrimaryStageAlwaysOnTopForVideoOverlay();
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
            UiI18n.applySceneOrientation(scene);
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
            fullscreen.set(true);
            if (!isControlBarHiddenByUser) controlsContainer.setVisible(true);
            restoreVisibleCursor();
            if (isPointerInsidePlayer) {
                idleTimer.playFromStart();
            }
        });
    }

    public void exitFullscreen() {
        if (fullscreenStage == null || fullscreenExitPending) return;
        fullscreenExitPending = true;
        if (Platform.isFxApplicationThread()) {
            exitFullscreenOnFxThread();
        } else {
            Platform.runLater(this::exitFullscreenOnFxThread);
        }
    }

    private void exitFullscreenOnFxThread() {
        try {
            Stage stageToClose = fullscreenStage;
            StackPane rootToDetach = fullscreenRoot;
            fullscreenStage = null;
            fullscreenRoot = null;
            playerContainer.prefWidthProperty().unbind();
            playerContainer.prefHeightProperty().unbind();
            if (stageToClose != null) {
                uninstallSceneInputRecovery(stageToClose.getScene());
                stageToClose.setOnCloseRequest(null);
                stageToClose.close();
            }
            if (rootToDetach != null) {
                rootToDetach.getChildren().remove(playerContainer);
            }
            if (originalParent != null) {
                if (playerContainer.getParent() != originalParent) {
                    if (playerContainer.getParent() instanceof Pane currentParent) {
                        currentParent.getChildren().remove(playerContainer);
                    }
                    int safeIndex = Math.clamp(originalIndex, 0, originalParent.getChildren().size());
                    originalParent.getChildren().add(safeIndex, playerContainer);
                }
                if (originalParent.getScene() != null) {
                    originalParent.getScene().setCursor(null);
                }
            }
            originalParent = null;
            originalIndex = -1;
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
            fullscreen.set(false);
            isPointerInsidePlayer = true;
            if (isControlBarHiddenByUser) {
                controlsContainer.setVisible(false);
                idleTimer.stop();
            } else {
                controlsContainer.setVisible(true);
                restartIdleTimerForActivePlayer();
            }
            restoreVisibleCursor();
        } finally {
            restorePrimaryStageAlwaysOnTopAfterVideoOverlay();
            fullscreenExitPending = false;
        }
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
            pip.set(true);
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
            showVideoOverlayStage(pipStage, false);
            applyPipUiState();
        });
    }

    public void exitPip() {
        if (pipStage == null) return;
        Platform.runLater(() -> {
            uninstallSceneInputRecovery(pipStage.getScene());
            pipStage.close();
            pipStage = null;
            pip.set(false);

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

    protected Stage createPipStage() {
        Stage stage = new Stage(StageStyle.UNDECORATED);
        configureVideoOverlayStage(stage);
        return stage;
    }

    protected void configureVideoOverlayStage(Stage stage) {
        if (stage == null) {
            return;
        }
        Stage primaryStage = RootApplication.getPrimaryStage();
        if (primaryStage != null && primaryStage != stage && !stage.isShowing()) {
            stage.initOwner(primaryStage);
        }
        stage.setAlwaysOnTop(true);
    }

    protected void suppressPrimaryStageAlwaysOnTopForVideoOverlay() {
        Stage primaryStage = RootApplication.getPrimaryStage();
        if (primaryStage == null || primaryStageAlwaysOnTopSuppressedForVideoOverlay) {
            return;
        }
        primaryStageAlwaysOnTopBeforeVideoOverlay = primaryStage.isAlwaysOnTop();
        primaryStageAlwaysOnTopSuppressedForVideoOverlay = primaryStageAlwaysOnTopBeforeVideoOverlay;
        if (primaryStageAlwaysOnTopSuppressedForVideoOverlay) {
            primaryStage.setAlwaysOnTop(false);
        }
    }

    protected void restorePrimaryStageAlwaysOnTopAfterVideoOverlay() {
        if (!primaryStageAlwaysOnTopSuppressedForVideoOverlay) {
            return;
        }
        Stage primaryStage = RootApplication.getPrimaryStage();
        if (primaryStage != null) {
            primaryStage.setAlwaysOnTop(primaryStageAlwaysOnTopBeforeVideoOverlay);
        }
        primaryStageAlwaysOnTopBeforeVideoOverlay = false;
        primaryStageAlwaysOnTopSuppressedForVideoOverlay = false;
    }

    private void showVideoOverlayStage(Stage stage, boolean requestFocus) {
        if (stage == null) {
            return;
        }
        stage.show();
        stage.setAlwaysOnTop(true);
        stage.toFront();
        if (requestFocus) {
            stage.requestFocus();
        }
        Platform.runLater(() -> {
            if (stage.isShowing()) {
                stage.setAlwaysOnTop(true);
                stage.toFront();
            }
        });
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
        // PIP icons are supplied at different native sizes. Without preserving
        // the ratio, JavaFX stretches the graphic whenever its target box is
        // square, which is especially visible on the restore and overlay
        // controls.
        iconView.setPreserveRatio(true);
        iconView.setSmooth(true);
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
            imageView.setScaleX(1.0);
            imageView.setScaleY(1.0);
        }
    }

    private Scene createPipScene(StackPane pipRoot, Scene originalScene) {
        Scene scene = new Scene(pipRoot, 480, 270);
        UiI18n.applySceneOrientation(scene);
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
        Cursor cursor = null;
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
        pipStage.getScene().setCursor(null);
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
        layoutModeIcon = createLayoutModeIcon();
        aspectRatioIcon = createIconView("video-scale-fit.png", false);
        aspectRatioFillIcon = createIconView("video-scale-crop.png", false);
        aspectRatioStretchIcon = createIconView("video-scale-stretch.png", false);
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

            // Keep the source resolution intact and let the ImageView perform the
            // final 20px fit. Pre-decoding every control icon to 40x40 introduced
            // visible rasterisation artifacts on the desktop control bar.
            Image image = new Image(resourceUrl.toExternalForm());
            ImageView imageView = new ImageView(image);
            imageView.setFitHeight(20);
            imageView.setFitWidth(20);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);

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

    private Button createIconButton(SVGPath icon) {
        Button btn = new Button();
        btn.setGraphic(icon);
        btn.setPadding(new Insets(3));
        btn.getStyleClass().add(PLAYER_ICON_BUTTON);
        return btn;
    }

    private SVGPath createChannelNavigationIcon(boolean next) {
        SVGPath icon = new SVGPath();
        icon.setContent(next
                ? "M16 6h2v12h-2zM6 6v12l8-6z"
                : "M6 6h2v12H6zm4 6 8 6V6z");
        icon.setFill(Color.WHITE);
        icon.setScaleX(0.9);
        icon.setScaleY(0.9);
        return icon;
    }

    private void updateChannelNavigationButtons() {
        if (btnPreviousChannel == null || btnNextChannel == null) {
            return;
        }
        PlayerChannelNavigation.Queue queue = PlayerChannelNavigation.getQueue();
        boolean available = queue != null && queue.actions().size() > 1;
        btnPreviousChannel.setVisible(available);
        btnPreviousChannel.setManaged(available);
        btnPreviousChannel.setDisable(!available || !queue.canGoPrevious());
        String previousTooltip = navigationTooltip(I18N_PLAYER_PREVIOUS_ITEM, "playerPreviousItemWithName",
                available ? queue.previousItemName() : "");
        btnPreviousChannel.setTooltip(new Tooltip(previousTooltip));
        btnPreviousChannel.setAccessibleText(previousTooltip);
        btnNextChannel.setVisible(available);
        btnNextChannel.setManaged(available);
        btnNextChannel.setDisable(!available || !queue.canGoNext());
        String nextTooltip = navigationTooltip(I18N_PLAYER_NEXT_ITEM, "playerNextItemWithName",
                available ? queue.nextItemName() : "");
        btnNextChannel.setTooltip(new Tooltip(nextTooltip));
        btnNextChannel.setAccessibleText(nextTooltip);
    }

    private String navigationTooltip(String fallbackKey, String namedKey, String itemName) {
        return isNotBlank(itemName) ? I18n.tr(namedKey, itemName) : I18n.tr(fallbackKey);
    }

    private SVGPath createLayoutModeIcon() {
        SVGPath icon = new SVGPath();
        icon.setContent(NARROW_LAYOUT_ICON);
        icon.setFill(Color.WHITE);
        icon.setScaleX(0.9);
        icon.setScaleY(0.9);
        return icon;
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

    private boolean isLiveGuideContent() {
        return currentChannel != null && currentAccount != null
                && currentAccount.getAction() == Account.AccountAction.itv;
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
        if (guideInfoFlow == null || nowShowingFlow == null || headerDetailsFlow == null || streamInfoText == null) {
            return;
        }
        guideInfoFlow.getChildren().clear();
        nowShowingFlow.getChildren().clear();
        headerDetailsFlow.getChildren().clear();
        String displayTitle = buildDisplayTitle();
        boolean live = isLiveGuideContent();
        refreshGuideSummary(live);
        guideInfoFlow.setVisible(!guideInfoFlow.getChildren().isEmpty());
        guideInfoFlow.setManaged(!guideInfoFlow.getChildren().isEmpty());
        refreshNowShowingTitle(displayTitle, live);
        if (isNotBlank(streamInfoText.getText())) {
            headerDetailsFlow.getChildren().add(streamInfoText);
        }
        headerDetailsFlow.setVisible(!headerDetailsFlow.getChildren().isEmpty());
        headerDetailsFlow.setManaged(!headerDetailsFlow.getChildren().isEmpty());
        updateTimelineVisibility();
        nowShowingFlow.setVisible(true);
        nowShowingFlow.setManaged(true);
    }

    private void refreshGuideSummary(boolean live) {
        if (!live) return;
        var summary = MediaPlayerFactory.guideSummaryProperty().get();
        if (summary == null || (summary.now() == null && summary.next() == null)) return;
        List<String> programmes = new ArrayList<>(2);
        if (summary.now() != null && isNotBlank(summary.now().title())) {
            programmes.add(I18n.tr("epgNow") + ": " + summary.now().title());
        }
        if (summary.next() != null && isNotBlank(summary.next().title())) {
            programmes.add(I18n.tr("epgNext") + ": " + summary.next().title());
        }
        if (programmes.isEmpty()) return;
        Text programmeText = new Text(String.join(" · ", programmes));
        programmeText.getStyleClass().add(STYLE_CLASS_PLAYER_STREAM_INFO_TEXT);
        guideInfoFlow.getChildren().add(programmeText);
    }

    private void refreshNowShowingTitle(String displayTitle, boolean live) {
        if (!isNotBlank(displayTitle)) return;
        Text channelNameText = new Text(displayTitle);
        channelNameText.getStyleClass().add("player-channel-title");
        applyFixedControlBarOrientation(channelNameText);
        if (!live) {
            nowShowingFlow.getChildren().add(channelNameText);
            return;
        }
        com.uiptv.widget.PlayingCardIndicator liveDot = new com.uiptv.widget.PlayingCardIndicator();
        liveDot.getStyleClass().add("player-live-indicator");
        liveDot.setVisible(true);
        liveDot.setManaged(true);
        Text liveLabel = new Text("  " + I18n.tr("playerLive"));
        liveLabel.getStyleClass().add("player-live-label");
        applyFixedControlBarOrientation(liveDot, liveLabel);
        nowShowingFlow.getChildren().addAll(liveDot, channelNameText, liveLabel);
    }

    private void updateTimelineVisibility() {
        if (timeRow == null) return;
        boolean visible = isOnDemandContent();
        timeRow.setVisible(visible);
        timeRow.setManaged(visible);
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

    protected String resolveHlsPlaylistChain(String uri) {
        if (!ConfigurationService.getInstance().isResolveChainAndDeepRedirectsEnabled(currentAccount)) {
            return uri;
        }
        return HlsPlaylistResolver.resolveHlsPlaylistChain(uri, createBrowserHeaders(), MAX_HLS_RESOLUTION_DEPTH);
    }

    protected java.util.Map<String, String> createBrowserHeaders() {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        if (ConfigurationService.getInstance().isVlcHttpUserAgentEnabled()) {
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
