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
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class AboutUI {
    private static final String FALLBACK_PROJECT_URL = "https://github.com/xixogo5105/uiptv";

    public AboutUI(HostServices hostServices) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.tr("autoAboutUIPTV"));
        stage.setResizable(false);

        Image image = new Image(getClass().getResourceAsStream("/icon.png"));
        stage.getIcons().add(image);

        ImageView imageView = new ImageView(image);
        imageView.setFitHeight(84);
        imageView.setFitWidth(84);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);

        Label titleLabel = new Label(I18n.tr("autoAboutUiptvTitle", VersionManager.getCurrentVersion()));
        titleLabel.setWrapText(true);
        titleLabel.setStyle("-fx-font-size: 1.8em; -fx-font-weight: bold;");

        Label subtitleLabel = createBodyLabel(I18n.tr("autoAboutUiptvTagline"));
        subtitleLabel.setStyle("-fx-font-size: 1.05em; -fx-opacity: 0.92;");

        Label overviewLabel = createDescriptionLabel(I18n.tr("autoAboutUiptvOverview"));
        Label webSyncLabel = createDescriptionLabel(I18n.tr("autoAboutUiptvWebSync"));

        String releaseDescription = resolveReleaseSummary();
        Label releaseNotesLabel = createDescriptionLabel(I18n.tr("autoAboutUiptvReleaseNotes", releaseDescription));

        Label authorLabel = createMetaLabel(I18n.tr("autoAuthorXixogo5105"));
        Label runtimeLabel = createBodyLabel(I18n.tr(
                "autoAboutUiptvRuntime",
                System.getProperty("java.version", "N/A"),
                System.getProperty("java.vm.name", "N/A")
        ));
        Label platformLabel = createMetaLabel(I18n.tr(
                "autoAboutUiptvPlatform",
                System.getProperty("os.name", "N/A"),
                System.getProperty("os.arch", "N/A")
        ));
        Label poweredByLabel = createMetaLabel(I18n.tr("autoAboutUiptvPoweredBy"));
        Label copyrightLabel = createMetaLabel(I18n.tr("autoCopyright2024Xixogo5105"));

        Hyperlink link = new Hyperlink(I18n.tr("autoHttpsGithubComXixogo5105Uiptv"));
        link.setOnAction(e -> hostServices.showDocument(FALLBACK_PROJECT_URL));
        link.setStyle("-fx-padding: 0;");

        FlowPane creditsRow = new FlowPane(14, 4, authorLabel, platformLabel);
        creditsRow.setAlignment(Pos.CENTER_LEFT);
        creditsRow.setRowValignment(javafx.geometry.VPos.CENTER);
        creditsRow.setPrefWrapLength(520);

        FlowPane footerRow = new FlowPane(10, 4, poweredByLabel, link);
        footerRow.setAlignment(Pos.CENTER_LEFT);
        footerRow.setRowValignment(javafx.geometry.VPos.CENTER);
        footerRow.setPrefWrapLength(520);

        VBox infoBox = new VBox(8);
        infoBox.setAlignment(Pos.TOP_LEFT);
        infoBox.setFillWidth(true);
        infoBox.setPrefWidth(520);
        infoBox.setMinWidth(520);
        infoBox.getChildren().addAll(
                titleLabel,
                subtitleLabel,
                spacer(2),
                overviewLabel,
                webSyncLabel,
                releaseNotesLabel,
                spacer(4),
                creditsRow,
                runtimeLabel,
                footerRow,
                copyrightLabel
        );
        HBox.setHgrow(infoBox, Priority.ALWAYS);

        Button updateButton = new Button(I18n.tr("autoCheckForUpdates"));
        updateButton.setDefaultButton(true);
        updateButton.setOnAction(e -> UpdateChecker.checkForUpdates(hostServices));

        Button closeButton = new Button(I18n.tr("autoClose"));
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> stage.close());

        HBox content = new HBox(18, imageView, infoBox);
        content.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(content, Priority.ALWAYS);

        HBox actions = new HBox(8, closeButton, updateButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setPadding(new Insets(14, 0, 0, 0));

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(20));
        root.setStyle("-fx-background-color: -fx-base;");
        root.setCenter(content);
        root.setBottom(actions);

        Scene scene = new Scene(root, 700, 420);
        I18n.applySceneOrientation(scene);
        if (RootApplication.currentTheme != null) {
            scene.getStylesheets().add(RootApplication.currentTheme);
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    private static Label createBodyLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(520);
        return label;
    }

    private static Label createDescriptionLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMaxWidth(520);
        label.setStyle("-fx-opacity: 0.96;");
        return label;
    }

    private static Label createMetaLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setStyle("-fx-opacity: 0.88;");
        return label;
    }

    private static Region spacer(double height) {
        Region spacer = new Region();
        spacer.setMinHeight(height);
        spacer.setPrefHeight(height);
        return spacer;
    }

    private static String resolveReleaseSummary() {
        String currentVersion = VersionManager.getCurrentVersion();
        String releaseDescription = VersionManager.getReleaseDescription();
        if ("N/A".equals(releaseDescription) || releaseDescription.isBlank()) {
            return currentVersion;
        }
        String language = I18n.getCurrentLocale().getLanguage();
        if (!"en".equalsIgnoreCase(language)) {
            return currentVersion;
        }
        return releaseDescription;
    }
}
