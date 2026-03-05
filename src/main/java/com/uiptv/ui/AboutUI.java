package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.util.VersionManager;
import javafx.application.HostServices;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AboutUI {

    public AboutUI(HostServices hostServices) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.tr("autoAboutUIPTV"));

        VBox vbox = new VBox();
        vbox.setAlignment(Pos.CENTER);
        vbox.setSpacing(10);
        vbox.setPadding(new Insets(20));

        Image image = new Image(getClass().getResourceAsStream("/icon.png"));
        ImageView imageView = new ImageView(image);
        imageView.setFitHeight(128);
        imageView.setFitWidth(128);

        Label titleLabel = new Label(I18n.tr("autoUiptvVersion", VersionManager.getCurrentVersion()));
        Label authorLabel = new Label(I18n.tr("autoAuthorXixogo5105"));
        Label copyrightLabel = new Label(I18n.tr("autoCopyright2024Xixogo5105"));

        Hyperlink link = new Hyperlink(I18n.tr("autoHttpsGithubComXixogo5105Uiptv"));
        link.setOnAction(e -> hostServices.showDocument("https://github.com/xixogo5105/uiptv"));

        Button updateButton = new Button(I18n.tr("autoCheckForUpdates"));
        updateButton.setOnAction(e -> UpdateChecker.checkForUpdates(hostServices));

        vbox.getChildren().addAll(imageView, titleLabel, authorLabel, copyrightLabel, link, updateButton);

        Scene scene = new Scene(vbox);
        I18n.applySceneOrientation(scene);
        scene.getStylesheets().add(RootApplication.currentTheme);
        stage.setScene(scene);
        stage.showAndWait();
    }
}
