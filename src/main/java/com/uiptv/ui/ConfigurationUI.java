package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.api.VideoPlayerInterface;
import com.uiptv.model.Configuration;
import com.uiptv.player.MediaPlayerFactory;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.CacheService;
import com.uiptv.service.CacheServiceImpl;
import com.uiptv.service.ConfigurationService;
import com.uiptv.widget.ProminentButton;
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
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.io.File;
import java.io.IOException;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ConfigurationUI extends VBox {
    private String dbId;
    private final VBox contentContainer = new VBox();
    final ToggleGroup group = new ToggleGroup();
    final Button browserButtonPlayerPath1 = new Button("Browse...");
    final Button browserButtonPlayerPath2 = new Button("Browse...");
    final Button browserButtonPlayerPath3 = new Button("Browse...");
    final FileChooser fileChooser = new FileChooser();
    private final RadioButton defaultPlayer1 = new RadioButton("");
    private final RadioButton defaultPlayer2 = new RadioButton("");
    private final RadioButton defaultPlayer3 = new RadioButton("");
    private final RadioButton defaultEmbedPlayer = new RadioButton();

    private final UIptvText playerPath1 = new UIptvText("playerPath1", "Enter your favorite player's Path here.", 5);
    private final UIptvText playerPath2 = new UIptvText("playerPath2", "Enter your second favorite player's Path here.", 5);
    private final UIptvText playerPath3 = new UIptvText("playerPath3", "Enter your third favorite player's Path here.", 5);
    private final UIptvTextArea filterCategoriesWithTextContains = new UIptvTextArea("filterCategoriesWithTextContains", "Enter comma separated list. All categories containing this would be filtered out.", 5);
    private final UIptvTextArea filterChannelWithTextContains = new UIptvTextArea("filterChannelWithTextContains", "Enter comma separated list. All Channels containing this would be filtered out.", 5);
    private final Hyperlink showHideFilters = new Hyperlink("Show Filters");
    private final CheckBox filterPausedCheckBox = new CheckBox("Pause filtering");
    private final CheckBox darkThemeCheckBox = new CheckBox("Use Dark Theme");
    private final CheckBox enableFfmpegCheckBox = new CheckBox("Enable FFmpeg Transcoding (High CPU Usage)");
    private final UIptvText fontFamily = new UIptvText("fontFamily", "Font family. e.g. 'Helvetica', Arial, sans-serif.", 5);
    private final UIptvText fontSize = new UIptvText("fontSize", "Font size. e.g. 13pt", 5);
    private final UIptvText fontWeight = new UIptvText("fontWeight", "Font weight. e.g. bold", 5);
    private final UIptvText serverPort = new UIptvText("serverPort", "e.g. 8888", 3);
    private final UIptvText cacheExpiryDays = new UIptvText("cacheExpiryDays", "Cache expiry in days (numbers only, default 30)", 5);

    private final Button startServerButton = new Button("Start Server");
    private final Button stopServerButton = new Button("Stop Server");
    private final Hyperlink openServerLink = new Hyperlink("open");
    private final Button publishM3u8Button = new Button("Publish M3U8");
    private final Button clearCacheButton = new Button("Clear Cache");
    private final Button reloadCacheButton = new Button("Reload Accounts Cache");
    private final ProminentButton saveButton = new ProminentButton("Save");
    private final Callback onSaveCallback;
    private final ConfigurationService service = ConfigurationService.getInstance();
    private final CacheService cacheService = new CacheServiceImpl();
    private Timeline serverStatusTimeline;

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
        VBox.setVgrow(scrollPane, Priority.ALWAYS);
        getChildren().setAll(scrollPane);

        Configuration configuration = service.read();
        defaultPlayer1.setToggleGroup(group);
        defaultPlayer2.setToggleGroup(group);
        defaultPlayer3.setToggleGroup(group);
        defaultEmbedPlayer.setToggleGroup(group);

        updateEmbeddedPlayerTitle();

        defaultPlayer1.setUserData("defaultPlayer1");
        defaultPlayer2.setUserData("defaultPlayer2");
        defaultPlayer3.setUserData("defaultPlayer3");
        defaultEmbedPlayer.setUserData("defaultEmbedPlayer");
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
            } else {
                defaultEmbedPlayer.setSelected(true);
            }
            filterPausedCheckBox.setSelected(configuration.isPauseFiltering());
            fontFamily.setText(configuration.getFontFamily());
            fontWeight.setText(configuration.getFontWeight());
            fontSize.setText(configuration.getFontSize());
            darkThemeCheckBox.setSelected(configuration.isDarkTheme());
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
        playerPath1.setMinWidth(275);
        playerPath2.setMinWidth(275);
        playerPath3.setMinWidth(275);
        playerPath1.setPrefWidth(275);
        playerPath2.setPrefWidth(275);
        playerPath3.setPrefWidth(275);
        filterCategoriesWithTextContains.setMinWidth(250);
        filterChannelWithTextContains.setMinWidth(250);

        filterCategoriesWithTextContains.setVisible(false);
        filterCategoriesWithTextContains.setManaged(false);
        filterChannelWithTextContains.setVisible(false);
        filterChannelWithTextContains.setManaged(false);

        showHideFilters.setOnAction(event -> {
            boolean visible = !filterCategoriesWithTextContains.isVisible();
            filterCategoriesWithTextContains.setVisible(visible);
            filterCategoriesWithTextContains.setManaged(visible);
            filterChannelWithTextContains.setVisible(visible);
            filterChannelWithTextContains.setManaged(visible);
            showHideFilters.setText(visible ? "Hide Filters" : "Show Filters");
        });

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
        HBox box4 = new HBox(6, defaultEmbedPlayer);
        VBox playersGroup = new VBox(10, box1, box2, box3, box4);

        VBox filtersGroup = new VBox(10, showHideFilters, filterCategoriesWithTextContains, filterChannelWithTextContains);

        VBox fontGroup = new VBox(10, fontFamily, fontSize, fontWeight, darkThemeCheckBox);

        HBox cacheButtons = new HBox(10, clearCacheButton, reloadCacheButton);
        VBox cacheGroup = new VBox(10, filterPausedCheckBox, cacheButtons, cacheExpiryRow);

        openServerLink.setVisible(false);
        openServerLink.setManaged(false);
        HBox serverButtonWrapper = new HBox(10, serverPort, startServerButton, stopServerButton, openServerLink);
        publishM3u8Button.setMaxWidth(Double.MAX_VALUE);
        publishM3u8Button.setPrefWidth(440);
        VBox serverGroup = new VBox(10, enableFfmpegCheckBox, serverButtonWrapper, publishM3u8Button);

        contentContainer.getChildren().addAll(
                createGroupPane("Players", "Add player paths and select the matching radio button to set the default player.", playersGroup),
                createGroupPane("Filters", filtersGroup),
                createGroupPane("Font & Theme", fontGroup),
                createGroupPane("Cache & Filtering", cacheGroup),
                createGroupPane("FFmpeg & Web Server", serverGroup),
                saveButton
        );
        addSaveButtonClickHandler();
        addBrowserButton1ClickHandler();
        addBrowserButton2ClickHandler();
        addBrowserButton3ClickHandler();
        addStartServerButtonClickHandler();
        addStopServerButtonClickHandler();
        addClearCacheButtonClickHandler();
        addPublishM3u8ButtonClickHandler();
        addReloadCacheButtonClickHandler();
        addOpenServerLinkClickHandler();
        installServerStatusMonitor();
    }

    private BorderPane createGroupPane(String title, Node content) {
        BorderPane pane = new BorderPane(content);
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold;");
        BorderPane.setMargin(titleLabel, new Insets(0, 0, 8, 0));
        pane.setTop(titleLabel);
        pane.setPadding(new Insets(10));
        pane.setStyle("-fx-background-color: -fx-control-inner-background-alt;"
                + "-fx-border-color: -fx-box-border;"
                + "-fx-background-radius: 8;"
                + "-fx-border-radius: 8;");
        return pane;
    }

    private BorderPane createGroupPane(String title, String description, Node content) {
        Label titleLabel = new Label(title);
        titleLabel.setStyle("-fx-font-weight: bold;");

        Label descriptionLabel = new Label(description);
        descriptionLabel.setWrapText(true);
        descriptionLabel.setStyle("-fx-opacity: 0.85;");

        VBox header = new VBox(4, titleLabel, descriptionLabel);
        BorderPane pane = createGroupPane("", content);
        pane.setTop(header);
        BorderPane.setMargin(header, new Insets(0, 0, 8, 0));
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

    private void addStopServerButtonClickHandler() {
        stopServerButton.setOnAction(event -> {
            try {
                UIptvServer.stop();
                refreshServerStatusUI();
                // showMessageAlert("Server stopped"); // Removed alert
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
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


    private void addStartServerButtonClickHandler() {
        startServerButton.setOnAction(event -> {
            try {
                UIptvServer.start();
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
            M3U8PublicationPopup popup = new M3U8PublicationPopup(popupStage);
            Scene scene = new Scene(popup, 400, 300);
            scene.getStylesheets().add(RootApplication.currentTheme);
            popupStage.setTitle("Publish M3U8");
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
        startServerButton.setDisable(running);
        stopServerButton.setDisable(!running);
        openServerLink.setVisible(running);
        openServerLink.setManaged(running);
    }

    private void addOpenServerLinkClickHandler() {
        openServerLink.setOnAction(event -> {
            String port = resolveServerPort();
            RootApplication.openInBrowser("http://localhost:" + port + "/");
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

                String defaultPlayer = defaultEmbedPlayer.getText();
                if (defaultPlayer1.isSelected()) {
                    defaultPlayer = playerPath1.getText();
                } else if (defaultPlayer2.isSelected()) {
                    defaultPlayer = playerPath2.getText();
                } else if (defaultPlayer3.isSelected()) {
                    defaultPlayer = playerPath3.getText();
                }
                Configuration newConfiguration = new Configuration(
                        playerPath1.getText(), playerPath2.getText(), playerPath3.getText(), defaultPlayer,
                        filterCategoriesWithTextContains.getText(), filterChannelWithTextContains.getText(),
                        filterPausedCheckBox.isSelected(),
                        fontFamily.getText(), fontSize.getText(), fontWeight.getText(),
                        darkThemeCheckBox.isSelected(), serverPort.getText(),

                        defaultEmbedPlayer.isSelected(),
                        enableFfmpegCheckBox.isSelected()
                );
                newConfiguration.setCacheExpiryDays(sanitizeCacheExpiryDaysText());
                newConfiguration.setDbId(dbId);
                service.save(newConfiguration);
                onSaveCallback.call(null);
                showSaveSuccessAnimation();

                if (newConfiguration.isEmbeddedPlayer() && MediaPlayerFactory.getPlayerType() == VideoPlayerInterface.PlayerType.DUMMY) {
                    showMessageAlert("Please restart the application for the embedded player to be initialized.");
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

        Timeline timeline = new Timeline(new KeyFrame(
                Duration.seconds(10),
                event -> {
                    saveButton.setText(originalText);
                    saveButton.setDisable(false);
                }
        ));
        timeline.setCycleCount(1);
        timeline.play();
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

    private String sanitizeCacheExpiryDaysText() {
        int normalized = service.normalizeCacheExpiryDays(cacheExpiryDays.getText());
        String normalizedText = String.valueOf(normalized);
        if (!normalizedText.equals(cacheExpiryDays.getText())) {
            cacheExpiryDays.setText(normalizedText);
        }
        return normalizedText;
    }
}
