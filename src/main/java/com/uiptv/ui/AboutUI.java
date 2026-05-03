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
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Screen;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.geometry.Rectangle2D;

public class AboutUI {
    private static final String FALLBACK_PROJECT_URL = "https://github.com/xixogo5105/uiptv";
    private static final double BASE_SCENE_WIDTH = 760;
    private static final double BASE_SCENE_HEIGHT = 536;
    private static final double MIN_SCENE_WIDTH = 560;
    private static final double MIN_SCENE_HEIGHT = 420;
    private static final double CONTENT_MAX_WIDTH = 720;

    private AboutUI(HostServices hostServices) {
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle(I18n.tr("autoAboutUIPTV"));
        stage.setMinWidth(MIN_SCENE_WIDTH);
        stage.setMinHeight(MIN_SCENE_HEIGHT);
        stage.setResizable(true);

        Image image = new Image(getClass().getResourceAsStream("/icon.png"));
        stage.getIcons().add(image);

        ImageView imageView = new ImageView(image);
        imageView.setFitHeight(72);
        imageView.setFitWidth(72);
        imageView.setPreserveRatio(true);
        imageView.setSmooth(true);
        imageView.getStyleClass().add("about-hero-image");

        StackPane imageShell = new StackPane(imageView);
        imageShell.getStyleClass().add("about-hero-icon-shell");

        Label titleLabel = new Label(I18n.tr("autoAboutUiptvTitle", VersionManager.getCurrentVersion()));
        titleLabel.setWrapText(true);
        titleLabel.getStyleClass().add("about-title");

        Label subtitleLabel = createBodyLabel(I18n.tr("autoAboutUiptvTagline"));
        subtitleLabel.getStyleClass().add("about-subtitle");

        Label desktopBadge = new Label("DESKTOP APP");
        desktopBadge.getStyleClass().add("about-badge");

        Label versionChip = new Label("v" + VersionManager.getCurrentVersion());
        versionChip.getStyleClass().add("about-version-chip");

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
        link.getStyleClass().add("about-link");

        Region badgeSpacer = new Region();
        HBox.setHgrow(badgeSpacer, Priority.ALWAYS);
        HBox badgeRow = new HBox(8, desktopBadge, badgeSpacer, versionChip);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        FlowPane creditsRow = new FlowPane(10, 4, authorLabel, platformLabel);
        creditsRow.setAlignment(Pos.CENTER_LEFT);
        creditsRow.setRowValignment(javafx.geometry.VPos.CENTER);
        creditsRow.setPrefWrapLength(500);
        creditsRow.getStyleClass().add("about-inline-row");

        FlowPane footerRow = new FlowPane(10, 4, poweredByLabel, link);
        footerRow.setAlignment(Pos.CENTER_LEFT);
        footerRow.setRowValignment(javafx.geometry.VPos.CENTER);
        footerRow.setPrefWrapLength(500);
        footerRow.getStyleClass().add("about-inline-row");

        VBox heroText = new VBox(6, badgeRow, titleLabel, subtitleLabel);
        heroText.setAlignment(Pos.TOP_LEFT);
        HBox.setHgrow(heroText, Priority.ALWAYS);

        HBox heroBox = new HBox(14, imageShell, heroText);
        heroBox.setAlignment(Pos.TOP_LEFT);
        heroBox.getStyleClass().add("about-hero");

        VBox infoBox = new VBox(8);
        infoBox.setAlignment(Pos.TOP_LEFT);
        infoBox.setFillWidth(true);
        infoBox.setMaxWidth(Double.MAX_VALUE);
        infoBox.getStyleClass().add("about-card");
        infoBox.getChildren().addAll(
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
        updateButton.getStyleClass().add("prominent");
        updateButton.setOnAction(e -> UpdateChecker.checkForUpdates(hostServices));

        Button closeButton = new Button(I18n.tr("autoClose"));
        closeButton.setCancelButton(true);
        closeButton.setOnAction(e -> stage.close());

        VBox content = new VBox(14, heroBox, infoBox);
        content.setAlignment(Pos.TOP_LEFT);
        content.setFillWidth(true);
        content.setMaxWidth(CONTENT_MAX_WIDTH);

        HBox actions = new HBox(8, closeButton, updateButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.getStyleClass().add("about-actions");

        ScrollPane scrollPane = new ScrollPane(content);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("transparent-scroll-pane");

        BorderPane root = new BorderPane();
        root.setPadding(new Insets(18));
        root.getStyleClass().add("about-root");
        root.setCenter(scrollPane);
        root.setBottom(actions);

        Scene scene = new Scene(root, resolveSceneWidth(), resolveSceneHeight());
        I18n.applySceneOrientation(scene);
        if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        stage.setScene(scene);
        stage.showAndWait();
    }

    public static void show(HostServices hostServices) {
        new AboutUI(hostServices);
    }

    private static Label createBodyLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMinWidth(0);
        label.getStyleClass().add("about-body");
        return label;
    }

    private static Label createDescriptionLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.setMinWidth(0);
        label.getStyleClass().add("about-description");
        return label;
    }

    private static Label createMetaLabel(String text) {
        Label label = new Label(text);
        label.setWrapText(true);
        label.setTextOverrun(OverrunStyle.CLIP);
        label.setMaxWidth(Double.MAX_VALUE);
        label.setMinHeight(Region.USE_PREF_SIZE);
        label.getStyleClass().add("about-meta");
        return label;
    }

    private static Region spacer(double height) {
        Region spacer = new Region();
        spacer.setMinHeight(height);
        spacer.setPrefHeight(height);
        return spacer;
    }

    private static double resolveSceneWidth() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return clamp(bounds.getWidth() - 80, MIN_SCENE_WIDTH, BASE_SCENE_WIDTH);
    }

    private static double resolveSceneHeight() {
        Rectangle2D bounds = Screen.getPrimary().getVisualBounds();
        return clamp(bounds.getHeight() - 56, MIN_SCENE_HEIGHT, BASE_SCENE_HEIGHT);
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
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
