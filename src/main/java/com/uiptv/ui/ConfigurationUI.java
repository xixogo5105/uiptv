package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.SeriesWatchStateService;
import com.uiptv.util.ServerUrlUtil;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.PopupDecorator;
import com.uiptv.widget.UIptvAlert;
import com.uiptv.widget.UIptvText;
import com.uiptv.widget.UIptvTextArea;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ConfigurationUI extends VBox {
    private static final String WEB_BROWSER_PLAYER_PATH = "__web_browser_player__";
    private String dbId;
    private final VBox contentContainer = new VBox();
    final ToggleGroup group = new ToggleGroup();
    final Button browserButtonPlayerPath1 = new Button("...");
    final Button browserButtonPlayerPath2 = new Button("...");
    final Button browserButtonPlayerPath3 = new Button("...");
    final FileChooser fileChooser = new FileChooser();
    private final RadioButton defaultPlayer1 = new RadioButton("");
    private final RadioButton defaultPlayer2 = new RadioButton("");
    private final RadioButton defaultPlayer3 = new RadioButton("");
    private final RadioButton defaultEmbedPlayer = new RadioButton();
    private final RadioButton defaultWebBrowserPlayer = new RadioButton("Web Browser Player");
    private boolean ignorePlayerSelectionPrompt = false;

    private final UIptvText playerPath1 = new UIptvText("playerPath1", "Enter your favorite player's Path here.", 5);
    private final UIptvText playerPath2 = new UIptvText("playerPath2", "Enter your second favorite player's Path here.", 5);
    private final UIptvText playerPath3 = new UIptvText("playerPath3", "Enter your third favorite player's Path here.", 5);
    private final UIptvTextArea filterCategoriesWithTextContains = new UIptvTextArea("filterCategoriesWithTextContains", "Enter comma separated list. All categories containing this would be filtered out.", 5);
    private final UIptvTextArea filterChannelWithTextContains = new UIptvTextArea("filterChannelWithTextContains", "Enter comma separated list. All Channels containing this would be filtered out.", 5);
    private final CheckBox filterPausedCheckBox = new CheckBox("Pause filtering");
    private final CheckBox darkThemeCheckBox = new CheckBox("Use Dark Theme");
    private final CheckBox enableFfmpegCheckBox = new CheckBox("Enable FFmpeg Transcoding (High CPU Usage)");
    private final CheckBox enableThumbnailsCheckBox = new CheckBox("Enable thumbnails");
    private final CheckBox wideViewCheckBox = new CheckBox("Wide View");
    private final UIptvText fontFamily = new UIptvText("fontFamily", "Font family. e.g. 'Helvetica', Arial, sans-serif.", 5);
    private final UIptvText fontSize = new UIptvText("fontSize", "Font size. e.g. 13pt", 5);
    private final UIptvText fontWeight = new UIptvText("fontWeight", "Font weight. e.g. bold", 5);
    private final UIptvText serverPort = new UIptvText("serverPort", "e.g. 8888", 3);
    private final UIptvText cacheExpiryDays = new UIptvText("cacheExpiryDays", "Cache expiry in days (numbers only, default 30)", 5);

    private final Button startServerButton = new Button("Start Server");
    private final Hyperlink openServerLink = new Hyperlink("Open Web App");
    private final Button publishM3u8Button = new Button("Publish M3U8");
    private final Button clearCacheButton = new Button("Clear Cache");
    private final Button clearWatchingNowButton = new Button("Clear Watching Now");
    private final Button reloadCacheButton = new Button("Reload Accounts Cache");
    private final ProminentButton saveButton = new ProminentButton("Save");
    private final Callback onSaveCallback;
    private final ConfigurationService service = ConfigurationService.getInstance();
    private final CacheService cacheService = new CacheServiceImpl();
    private Timeline serverStatusTimeline;
    private Timeline saveSuccessTimeline;

    public ConfigurationUI(Callback onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
        initWidgets();
    }

    private void initWidgets() {
        setPadding(Insets.EMPTY);
        setSpacing(0);
        startServerButton.getStyleClass().add("no-dim-disabled");
        contentContainer.setPadding(new Insets(5));
        contentContainer.setSpacing(10);

        ScrollPane scrollPane = new ScrollPane(contentContainer);
        scrollPane.setFitToWidth(true);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.setPannable(true);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(scrollPane);

        Configuration configuration = service.read();
        defaultPlayer1.setToggleGroup(group);
        defaultPlayer2.setToggleGroup(group);
        defaultPlayer3.setToggleGroup(group);
        defaultEmbedPlayer.setToggleGroup(group);
        defaultWebBrowserPlayer.setToggleGroup(group);

        updateEmbeddedPlayerTitle();

        defaultPlayer1.setUserData("defaultPlayer1");
        defaultPlayer2.setUserData("defaultPlayer2");
        defaultPlayer3.setUserData("defaultPlayer3");
        defaultEmbedPlayer.setUserData("defaultEmbedPlayer");
        defaultWebBrowserPlayer.setUserData("defaultWebBrowserPlayer");
        defaultEmbedPlayer.setSelected(true);
        if (configuration != null) {
            this.dbId = configuration.getDbId();
            playerPath1.setText(configuration.getPlayerPath1());
            playerPath2.setText(configuration.getPlayerPath2());
            playerPath3.setText(configuration.getPlayerPath3());
            filterCategoriesWithTextContains.setText(configuration.getFilterCategoriesList());
            filterChannelWithTextContains.setText(configuration.getFilterChannelsList());
            if (playerPath1.getText() != null && playerPath1.getText().equals(configuration.getDefaultPlayerPath())) {
                defaultPlayer1.setSelected(true);
            } else if (playerPath2.getText() != null && playerPath2.getText().equals(configuration.getDefaultPlayerPath())) {
                defaultPlayer2.setSelected(true);
            } else if (playerPath3.getText() != null && playerPath3.getText().equals(configuration.getDefaultPlayerPath())) {
                defaultPlayer3.setSelected(true);
            } else if (WEB_BROWSER_PLAYER_PATH.equals(configuration.getDefaultPlayerPath())) {
                defaultWebBrowserPlayer.setSelected(true);
            } else {
                defaultEmbedPlayer.setSelected(true);
            }
            filterPausedCheckBox.setSelected(configuration.isPauseFiltering());
            fontFamily.setText(configuration.getFontFamily());
            fontWeight.setText(configuration.getFontWeight());
            fontSize.setText(configuration.getFontSize());
            darkThemeCheckBox.setSelected(configuration.isDarkTheme());
            enableThumbnailsCheckBox.setSelected(configuration.isEnableThumbnails());
            wideViewCheckBox.setSelected(configuration.isWideView());
            serverPort.setText(configuration.getServerPort());
            enableFfmpegCheckBox.setSelected(configuration.isEnableFfmpegTranscoding());
            cacheExpiryDays.setText(String.valueOf(service.normalizeCacheExpiryDays(configuration.getCacheExpiryDays())));
        }
        if (cacheExpiryDays.getText() == null || cacheExpiryDays.getText().isBlank()) {
            cacheExpiryDays.setText(String.valueOf(ConfigurationService.DEFAULT_CACHE_EXPIRY_DAYS));
        }
        cacheExpiryDays.textProperty().addListener((obs, oldVal, newVal) -> {
            if (newVal == null) {
                return;
            }
            String normalized = newVal.replaceAll("[^0-9]", "");
            if (!newVal.equals(normalized)) {
                cacheExpiryDays.setText(normalized);
            }
        });
        playerPath1.setMinWidth(295);
        playerPath2.setMinWidth(295);
        playerPath3.setMinWidth(295);
        playerPath1.setPrefWidth(295);
        playerPath2.setPrefWidth(295);
        playerPath3.setPrefWidth(295);
        filterCategoriesWithTextContains.setMinWidth(250);
        filterChannelWithTextContains.setMinWidth(250);

        filterPausedCheckBox.setMinWidth(250);
        cacheExpiryDays.setPrefColumnCount(4);
        cacheExpiryDays.setMaxWidth(70);
        Label cacheExpiryLabel = new Label("Cache Expires in days:");
        HBox cacheExpiryRow = new HBox(8, cacheExpiryLabel, cacheExpiryDays);
        saveButton.setMinWidth(40);
        saveButton.setPrefWidth(440);
        saveButton.setMinHeight(50);
        saveButton.setPrefHeight(50);
        fileChooser.setTitle("Select your favorite streaming player");
        HBox box1 = new HBox(6, defaultPlayer1, playerPath1, browserButtonPlayerPath1);
        HBox box2 = new HBox(6, defaultPlayer2, playerPath2, browserButtonPlayerPath2);
        HBox box3 = new HBox(6, defaultPlayer3, playerPath3, browserButtonPlayerPath3);
        HBox box4 = new HBox(6, defaultEmbedPlayer, wideViewCheckBox);
        HBox box5 = new HBox(6, defaultWebBrowserPlayer);
        VBox playersGroup = new VBox(10, box1, box2, box3, box4, box5);

        VBox filtersGroup = new VBox(10, filterCategoriesWithTextContains, filterChannelWithTextContains);

        VBox fontGroup = new VBox(10, fontFamily, fontSize, fontWeight, darkThemeCheckBox, enableThumbnailsCheckBox);

        HBox clearButtons = new HBox(10, clearCacheButton, clearWatchingNowButton);
        reloadCacheButton.setMaxWidth(Double.MAX_VALUE);
        VBox cacheGroup = new VBox(10, filterPausedCheckBox, cacheExpiryRow, clearButtons, reloadCacheButton);

        openServerLink.setVisible(false);
        openServerLink.setManaged(false);
        HBox serverButtonWrapper = new HBox(10, serverPort, startServerButton, openServerLink);
        publishM3u8Button.setMaxWidth(Double.MAX_VALUE);
        publishM3u8Button.setPrefWidth(440);
        VBox serverGroup = new VBox(10, enableFfmpegCheckBox, serverButtonWrapper, publishM3u8Button);

        contentContainer.getChildren().addAll(
                createCollapsibleGroupPane("Players", "Add player paths and select the matching radio button to set the default player.", playersGroup, false),
                createCollapsibleGroupPane("Filters", null, filtersGroup, true),
                createCollapsibleGroupPane("Font & Theme", null, fontGroup, false),
                createCollapsibleGroupPane("Cache & Filtering", null, cacheGroup, false),
                createCollapsibleGroupPane("FFmpeg & Web Server", null, serverGroup, false),
                saveButton
        );
        addSaveButtonClickHandler();
        addBrowserButton1ClickHandler();
        addBrowserButton2ClickHandler();
        addBrowserButton3ClickHandler();
        addStartServerButtonClickHandler();
        addClearCacheButtonClickHandler();
        addClearWatchingNowButtonClickHandler();
        addPublishM3u8ButtonClickHandler();
        addReloadCacheButtonClickHandler();
        addOpenServerLinkClickHandler();
        installPlayerSelectionConfirmationHandler();
        installServerStatusMonitor();
    }

    private BorderPane createCollapsibleGroupPane(String title, String description, Node content, boolean collapsedByDefault) {
        BorderPane pane = new BorderPane(content);
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("strong-label");
        VBox titleContainer = new VBox(4, titleLabel);
        titleContainer.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleContainer, Priority.ALWAYS);
        final Label descriptionLabel;
        if (description != null && !description.isBlank()) {
            Label label = new Label(description);
            label.setWrapText(true);
            label.getStyleClass().add("dim-label");
            titleContainer.getChildren().add(label);
            descriptionLabel = label;
        } else {
            descriptionLabel = null;
        }

        Hyperlink toggleLink = new Hyperlink();
        toggleLink.setMinWidth(Region.USE_PREF_SIZE);
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        HBox header = new HBox(8, titleContainer, spacer, toggleLink);

        final Runnable refreshToggleLabel = () -> {
            boolean expanded = content.isVisible() && content.isManaged();
            toggleLink.setText(expanded ? "Hide" : "Show");
            if (descriptionLabel != null) {
                descriptionLabel.setVisible(expanded);
                descriptionLabel.setManaged(expanded);
            }
        };
        content.setVisible(!collapsedByDefault);
        content.setManaged(!collapsedByDefault);
        refreshToggleLabel.run();
        toggleLink.setOnAction(event -> {
            boolean expand = !(content.isVisible() && content.isManaged());
            content.setVisible(expand);
            content.setManaged(expand);
            refreshToggleLabel.run();
        });

        BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
        pane.setTop(header);
        pane.setPadding(new Insets(10));
        pane.getStyleClass().add("uiptv-card");
        return pane;
    }

    private void addReloadCacheButtonClickHandler() {
        reloadCacheButton.setOnAction(event -> ReloadCachePopup.showPopup((Stage) getScene().getWindow(), null, this::notifyAccountsChanged));
    }

    private void notifyAccountsChanged() {
        if (onSaveCallback != null) {
            onSaveCallback.call(null);
        }
    }

    private void updateEmbeddedPlayerTitle() {
        VideoPlayerInterface.PlayerType playerType = MediaPlayerFactory.getPlayerType();
        String title = "Embedded Player";
        if (playerType == VideoPlayerInterface.PlayerType.VLC) {
            title = "Embedded Player (Using VLC)";
        } else if (playerType == VideoPlayerInterface.PlayerType.LITE) {
            title = "Embedded Player (Using Lite)";
        }
        defaultEmbedPlayer.setText(title);
    }

    private void addClearCacheButtonClickHandler() {
        clearCacheButton.setOnAction(event -> {
            if (UIptvAlert.showConfirmationAlert("Are you sure you want to clear the cache?")) {
                try {
                    cacheService.clearAllCache();
                    showMessageAlert("Cache cleared");
                } catch (Exception ignored) {
                    showMessageAlert("Error has occurred while clearing cache");
                }
            }
        });
    }

    private void addClearWatchingNowButtonClickHandler() {
        clearWatchingNowButton.setOnAction(event -> {
            if (UIptvAlert.showConfirmationAlert("Are you sure you want to clear Watching Now data?")) {
                try {
                    SeriesWatchStateService.getInstance().clearAllSeriesLastWatched();
                    showMessageAlert("Watching Now data cleared");
                } catch (Exception ignored) {
                    showMessageAlert("Error has occurred while clearing Watching Now data");
                }
            }
        });
    }


    private void addStartServerButtonClickHandler() {
        startServerButton.setOnAction(event -> {
            try {
                if (UIptvServer.isRunning()) {
                    UIptvServer.stop();
                } else {
                    UIptvServer.start();
                }
                refreshServerStatusUI();
                // showMessageAlert("Server started at " + ConfigurationService.getInstance().read().getServerPort()); // Removed alert
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void addPublishM3u8ButtonClickHandler() {
        publishM3u8Button.setOnAction(event -> {
            Stage popupStage = new Stage();
            popupStage.initStyle(StageStyle.TRANSPARENT);
            M3U8PublicationPopup popup = new M3U8PublicationPopup(popupStage);
            VBox decoratedRoot = PopupDecorator.wrap(popupStage, "Publish M3U8", popup);
            Scene scene = new Scene(decoratedRoot, 400, 300);
            scene.setFill(Color.TRANSPARENT);
            scene.getStylesheets().add(RootApplication.currentTheme);
            popupStage.setScene(scene);
            popupStage.showAndWait();
        });
    }

    private void installServerStatusMonitor() {
        refreshServerStatusUI();
        serverStatusTimeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> refreshServerStatusUI()));
        serverStatusTimeline.setCycleCount(Timeline.INDEFINITE);
        serverStatusTimeline.play();

        sceneProperty().addListener((observable, oldScene, newScene) -> {
            if (newScene == null) {
                if (serverStatusTimeline != null) {
                    serverStatusTimeline.stop();
                }
            } else if (serverStatusTimeline != null) {
                serverStatusTimeline.play();
                refreshServerStatusUI();
            }
        });
    }

    private void refreshServerStatusUI() {
        boolean running = UIptvServer.isRunning();
        if (running) {
            if (!startServerButton.getStyleClass().contains("dangerous")) {
                startServerButton.getStyleClass().add("dangerous");
            }
        } else {
            startServerButton.getStyleClass().remove("dangerous");
        }
        startServerButton.setText(running ? "Stop Server" : "Start Server");
        openServerLink.setVisible(running);
        openServerLink.setManaged(running);
    }

    private void addOpenServerLinkClickHandler() {
        openServerLink.setOnAction(event -> {
            ServerUrlUtil.openInBrowser(ServerUrlUtil.getLocalServerUrl() + "/");
        });
    }

    private String resolveServerPort() {
        String port = serverPort.getText();
        if (port == null || port.isBlank()) {
            Configuration configuration = service.read();
            if (configuration != null) {
                port = configuration.getServerPort();
            }
        }
        return (port == null || port.isBlank()) ? "8888" : port.trim();
    }

    private void addSaveButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                if (saveButton.isDisable()) {
                    return;
                }
                saveButton.setDisable(true);

                Configuration previous = service.read();
                boolean previousThumbnailsEnabled = previous != null && previous.isEnableThumbnails();
                boolean previousEmbeddedPlayer = previous != null && previous.isEmbeddedPlayer();
                boolean previousWideView = previous != null && previous.isWideView();

                String defaultPlayer = defaultEmbedPlayer.getText();
                if (defaultPlayer1.isSelected()) {
                    defaultPlayer = playerPath1.getText();
                } else if (defaultPlayer2.isSelected()) {
                    defaultPlayer = playerPath2.getText();
                } else if (defaultPlayer3.isSelected()) {
                    defaultPlayer = playerPath3.getText();
                } else if (defaultWebBrowserPlayer.isSelected()) {
                    defaultPlayer = WEB_BROWSER_PLAYER_PATH;
                }
                Configuration newConfiguration = new Configuration(
                        playerPath1.getText(), playerPath2.getText(), playerPath3.getText(), defaultPlayer,
                        filterCategoriesWithTextContains.getText(), filterChannelWithTextContains.getText(),
                        filterPausedCheckBox.isSelected(),
                        fontFamily.getText(), fontSize.getText(), fontWeight.getText(),
                        darkThemeCheckBox.isSelected(), serverPort.getText(),

                        defaultEmbedPlayer.isSelected(),
                        enableFfmpegCheckBox.isSelected(),
                        sanitizeCacheExpiryDaysText(),
                        enableThumbnailsCheckBox.isSelected()
                );
                newConfiguration.setDbId(dbId);
                newConfiguration.setWideView(wideViewCheckBox.isSelected());
                service.save(newConfiguration);
                if (onSaveCallback != null) {
                    onSaveCallback.call(null);
                }
                showSaveSuccessAnimation();
                if (previousThumbnailsEnabled != newConfiguration.isEnableThumbnails()) {
                    ThumbnailAwareUI.notifyThumbnailModeChanged(newConfiguration.isEnableThumbnails());
                }

                boolean restartRequired = previousEmbeddedPlayer != newConfiguration.isEmbeddedPlayer()
                        || previousWideView != newConfiguration.isWideView();
                if (restartRequired) {
                    showMessageAlert("Please restart the application for Embedded Player/Wide View changes to take effect.");
                }
            } catch (Exception e) {
                showErrorAlert("Failed to save configuration. Please try again!");
                saveButton.setDisable(false);
            }
        });
    }

    private void showSaveSuccessAnimation() {
        String originalText = saveButton.getText();
        saveButton.setText("✅");

        if (saveSuccessTimeline != null) {
            saveSuccessTimeline.stop();
        }

        saveSuccessTimeline = new Timeline(new KeyFrame(
                Duration.seconds(3),
                event -> {
                    saveButton.setText(originalText);
                    saveButton.setDisable(false);
                }
        ));
        saveSuccessTimeline.setCycleCount(1);
        saveSuccessTimeline.setOnFinished(event -> {
            saveButton.setText(originalText);
            saveButton.setDisable(false);
        });
        saveSuccessTimeline.play();
    }

    private void addBrowserButton1ClickHandler() {
        browserButtonPlayerPath1.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(RootApplication.primaryStage);
            playerPath1.setText(file.getAbsolutePath());
        });
    }

    private void addBrowserButton2ClickHandler() {
        browserButtonPlayerPath2.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(RootApplication.primaryStage);
            playerPath2.setText(file.getAbsolutePath());
        });
    }

    private void addBrowserButton3ClickHandler() {
        browserButtonPlayerPath3.setOnAction(actionEvent -> {
            File file = fileChooser.showOpenDialog(RootApplication.primaryStage);
            playerPath3.setText(file.getAbsolutePath());
        });
    }

    private void installPlayerSelectionConfirmationHandler() {
        group.selectedToggleProperty().addListener((observable, oldToggle, newToggle) -> {
            if (ignorePlayerSelectionPrompt || newToggle == null) {
                return;
            }
            if (newToggle == defaultWebBrowserPlayer) {
                boolean proceed = UIptvAlert.showConfirmationAlert(
                        "Browser playback may not support every stream. Enabling FFmpeg transcoding can improve compatibility. Continue?"
                );
                if (!proceed) {
                    restorePreviousPlayerSelection(oldToggle);
                }
                return;
            }
            if (newToggle == defaultEmbedPlayer) {
                boolean proceed = UIptvAlert.showConfirmationAlert(
                        "For best embedded playback, install the standard VLC desktop app. Without VLC, a limited built-in player is used and many codecs may fail. Continue?"
                );
                if (!proceed) {
                    restorePreviousPlayerSelection(oldToggle);
                }
            }
        });
    }

    private void restorePreviousPlayerSelection(Toggle oldToggle) {
        ignorePlayerSelectionPrompt = true;
        try {
            if (oldToggle != null) {
                group.selectToggle(oldToggle);
            } else {
                group.selectToggle(null);
            }
        } finally {
            ignorePlayerSelectionPrompt = false;
        }
    }

    private String sanitizeCacheExpiryDaysText() {
        int normalized = service.normalizeCacheExpiryDays(cacheExpiryDays.getText());
        String normalizedText = String.valueOf(normalized);
        if (!normalizedText.equals(cacheExpiryDays.getText())) {
            cacheExpiryDays.setText(normalizedText);
        }
        return normalizedText;
    }
}
