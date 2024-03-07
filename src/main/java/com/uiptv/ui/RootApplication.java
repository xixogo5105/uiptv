package com.uiptv.ui;


import com.uiptv.model.Account;
import com.uiptv.model.Configuration;
import com.uiptv.server.UIptvServer;
import com.uiptv.service.ConfigurationService;
import com.uiptv.widget.UIptvAlert;
import com.uiptv.widget.CollapsedTitledPane;
import com.uiptv.widget.ExpendedTitledPane;
import javafx.application.Application;
import javafx.beans.binding.Bindings;
import javafx.beans.property.SimpleStringProperty;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Arrays;

import static com.uiptv.util.StringUtils.isNotBlank;

public class RootApplication extends Application {
    public final static int GUIDED_MAX_WIDTH_PIXELS = 1368;
    public final static int GUIDED_MAX_HEIGHT_PIXELS = 1920;
    public static Stage primaryStage;
    private final ConfigurationService configurationService = ConfigurationService.getInstance();

    public static void main(String[] args) {
        if (args != null && Arrays.stream(args).anyMatch(s -> s.toLowerCase().contains("headless"))) {
            try {
                UIptvServer.start();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            launch();
        }
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try {
                UIptvServer.stop();
                UIptvAlert.showMessage("UIPTV Shutting down");
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }));
    }

    @Override
    public final void start(Stage primaryStage) throws IOException {
        RootApplication.primaryStage = primaryStage;

        ManageAccountUI manageAccountUI = new ManageAccountUI();
        ParseMultipleAccountUI parseMultipleAccountUI = new ParseMultipleAccountUI();
        BookmarkChannelListUI bookmarkChannelListUI = new BookmarkChannelListUI();
        AccountListUI accountListUI = new AccountListUI(bookmarkChannelListUI);
        accountListUI.addCallbackHandler(param -> manageAccountUI.editAccount((Account) param));
        ConfigurationUI configurationUI = new ConfigurationUI(param -> {
            try {
                configureFontStyles(RootApplication.primaryStage.getScene());
                accountListUI.refresh();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        manageAccountUI.addCallbackHandler(param -> {
            try {
                accountListUI.refresh();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        parseMultipleAccountUI.addCallbackHandler(param -> {
            try {
                accountListUI.refresh();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });
        configurationUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        parseMultipleAccountUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        manageAccountUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        bookmarkChannelListUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        accountListUI.setMinWidth((double) GUIDED_MAX_WIDTH_PIXELS / 4);
        ExpendedTitledPane bookmarkUI = new ExpendedTitledPane("Favorite", bookmarkChannelListUI);
        bookmarkUI.setPrefHeight(GUIDED_MAX_HEIGHT_PIXELS);
        HBox sceneBox = new HBox(10,
                new VBox(5,
                        new CollapsedTitledPane("Configuration", configurationUI),
                        new CollapsedTitledPane("Account", manageAccountUI),
                        new CollapsedTitledPane("Import Bulk Accounts", parseMultipleAccountUI),
                        bookmarkUI
                ), accountListUI);
        sceneBox.setPadding(new Insets(10, 0, 0, 0));
        sceneBox.setPrefHeight(GUIDED_MAX_HEIGHT_PIXELS);
        Scene scene = new Scene(sceneBox, GUIDED_MAX_WIDTH_PIXELS, GUIDED_MAX_HEIGHT_PIXELS);

        configureFontStyles(scene);
        primaryStage.setTitle("UIPTV");
        primaryStage.setMaximized(true);
        primaryStage.setScene(scene);
        primaryStage.getIcons().add(new Image("file:icon.ico"));
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        try {
            UIptvServer.stop();
            UIptvAlert.showMessage("UIPTV Shutting down");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        super.stop();
    }

    private void configureFontStyles(Scene scene) {
        Configuration configuration = configurationService.read();
        String customStylesheet = "";
        if (isNotBlank(configuration.getFontFamily())) {
            customStylesheet += Bindings.format("-fx-font-family: %s;", new SimpleStringProperty(configuration.getFontFamily())).getValueSafe();
        }
        if (isNotBlank(configuration.getFontSize())) {
            customStylesheet += Bindings.format("-fx-font-size: %s;", new SimpleStringProperty(configuration.getFontSize())).getValueSafe();
        }
        if (isNotBlank(configuration.getFontWeight())) {
            customStylesheet += Bindings.format("-fx-font-weight: %s;", new SimpleStringProperty(configuration.getFontWeight())).getValueSafe();
        }
        scene.getStylesheets().clear();
//        scene.setDa(scene, configuration.isDarkTheme());
        scene.getStylesheets().add(configuration.isDarkTheme() ? "dark-application.css" : "application.css");
        scene.getRoot().styleProperty().bind(Bindings.format(customStylesheet));
    }
}