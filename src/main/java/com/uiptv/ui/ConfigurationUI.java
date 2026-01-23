package com.uiptv.ui;

import com.uiptv.api.Callback;
import com.uiptv.model.Configuration;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.ConfigurationService;
import com.uiptv.widget.ProminentButton;
import com.uiptv.widget.UIptvText;
import com.uiptv.widget.UIptvTextArea;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.RadioButton;
import javafx.scene.control.ToggleGroup;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public class ConfigurationUI extends VBox {
    private String dbId;
    final ToggleGroup group = new ToggleGroup();
    final Button browserButtonPlayerPath1 = new Button("Browse...");
    final Button browserButtonPlayerPath2 = new Button("Browse...");
    final Button browserButtonPlayerPath3 = new Button("Browse...");
    final FileChooser fileChooser = new FileChooser();
    private final RadioButton defaultPlayer1 = new RadioButton("");
    private final RadioButton defaultPlayer2 = new RadioButton("");
    private final RadioButton defaultPlayer3 = new RadioButton("");
    private final RadioButton defaultEmbedPlayer = new RadioButton("Use embedded player as default");

    private final UIptvText playerPath1 = new UIptvText("playerPath1", "Enter your favorite player's Path here.", 5);
    private final UIptvText playerPath2 = new UIptvText("playerPath2", "Enter your second favorite player's Path here.", 5);
    private final UIptvText playerPath3 = new UIptvText("playerPath3", "Enter your third favorite player's Path here.", 5);
    private final UIptvTextArea filterCategoriesWithTextContains = new UIptvTextArea("filterCategoriesWithTextContains", "Enter comma separated list. All categories containing this would be filtered out.", 5);
    private final UIptvTextArea filterChannelWithTextContains = new UIptvTextArea("filterChannelWithTextContains", "Enter comma separated list. All Channels containing this would be filtered out.", 5);
    private final Hyperlink showHideFilters = new Hyperlink("Show Filters");
    private final CheckBox filterPausedCheckBox = new CheckBox("Pause filtering");
    private final CheckBox pauseCachingCheckBox = new CheckBox("Pause Caching");
    private final CheckBox darkThemeCheckBox = new CheckBox("Use Dark Theme");
    private final UIptvText fontFamily = new UIptvText("fontFamily", "Font family. e.g. 'Helvetica', Arial, sans-serif.", 5);
    private final UIptvText fontSize = new UIptvText("fontSize", "Font size. e.g. 13pt", 5);
    private final UIptvText fontWeight = new UIptvText("fontWeight", "Font weight. e.g. bold", 5);
    private final UIptvText serverPort = new UIptvText("serverPort", "e.g. 8888", 3);

    private final Button startServerButton = new Button("Start Server");
    private final Button stopServerButton = new Button("Stop Server");
    private final Button publishM3u8Button = new Button("Publish M3U8");
    private final Button clearCacheButton = new Button("Clear Cache");
    private final ProminentButton saveButton = new ProminentButton("Save");
    private final Callback onSaveCallback;
    private final ConfigurationService service = ConfigurationService.getInstance();

    public ConfigurationUI(Callback onSaveCallback) {
        this.onSaveCallback = onSaveCallback;
        initWidgets();
    }

    private void initWidgets() {
        setPadding(new Insets(5));
        setSpacing(5);
        Configuration configuration = service.read();
        defaultPlayer1.setToggleGroup(group);
        defaultPlayer2.setToggleGroup(group);
        defaultPlayer3.setToggleGroup(group);
        defaultEmbedPlayer.setToggleGroup(group);

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
            pauseCachingCheckBox.setSelected(configuration.isPauseCaching());
            fontFamily.setText(configuration.getFontFamily());
            fontWeight.setText(configuration.getFontWeight());
            fontSize.setText(configuration.getFontSize());
            darkThemeCheckBox.setSelected(configuration.isDarkTheme());
            serverPort.setText(configuration.getServerPort());
        }
        playerPath1.setMinWidth(315);
        playerPath2.setMinWidth(315);
        playerPath3.setMinWidth(315);
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
//        pauseCachingCheckBox.setMinWidth(250);
        saveButton.setMinWidth(40);
        saveButton.setPrefWidth(440);
        saveButton.setMinHeight(50);
        saveButton.setPrefHeight(50);
        fileChooser.setTitle("Select your favorite streaming player");
        HBox box1 = new HBox(10, defaultPlayer1, playerPath1, browserButtonPlayerPath1);
        HBox box2 = new HBox(10, defaultPlayer2, playerPath2, browserButtonPlayerPath2);
        HBox box3 = new HBox(10, defaultPlayer3, playerPath3, browserButtonPlayerPath3);
        HBox box4 = new HBox(10, defaultEmbedPlayer);
        HBox serverButtonWrapper = new HBox(10, serverPort, startServerButton, stopServerButton, publishM3u8Button);
        getChildren().addAll(box1, box2, box3, box4, showHideFilters, filterCategoriesWithTextContains, filterChannelWithTextContains,
                fontFamily, fontSize, fontWeight, darkThemeCheckBox, filterPausedCheckBox,
                new HBox(10, pauseCachingCheckBox, clearCacheButton),
                serverButtonWrapper, saveButton);
        addSaveButtonClickHandler();
        addBrowserButton1ClickHandler();
        addBrowserButton2ClickHandler();
        addBrowserButton3ClickHandler();
        addStartServerButtonClickHandler();
        addStopServerButtonClickHandler();
        addClearCacheButtonClickHandler();
        addPublishM3u8ButtonClickHandler();
    }

    private void addStopServerButtonClickHandler() {
        stopServerButton.setOnAction(event -> {
            try {
                UIptvServer.stop();
                startServerButton.getStyleClass().remove("dangerous");
                // showMessageAlert("Server stopped"); // Removed alert
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });
    }

    private void addClearCacheButtonClickHandler() {
        clearCacheButton.setOnAction(event -> {
            try {
                ConfigurationService.getInstance().clearCache();
                showMessageAlert("Cache cleared");
            } catch (Exception ignored) {
                showMessageAlert("Error has occurred while clearing cache");
            }
        });
    }


    private void addStartServerButtonClickHandler() {
        startServerButton.setOnAction(event -> {
            try {
                UIptvServer.start();
                startServerButton.getStyleClass().add("dangerous");
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
            popupStage.setTitle("Publish M3U8");
            popupStage.setScene(scene);
            popupStage.showAndWait();
        });
    }

    private void addSaveButtonClickHandler() {
        saveButton.setOnAction(actionEvent -> {
            try {
                Configuration oldConfiguration = service.read();
                boolean oldUseEmbeddedPlayer = oldConfiguration != null && oldConfiguration.isEmbeddedPlayer();

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
                        pauseCachingCheckBox.isSelected(),
                        defaultEmbedPlayer.isSelected()
                );
                newConfiguration.setDbId(dbId);
                service.save(newConfiguration);
                onSaveCallback.call(null);
                showMessageAlert("Configurations saved!");
            } catch (Exception e) {
                showErrorAlert("Failed to save configuration. Please try again!");
            }
        });
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
}
