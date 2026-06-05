package com.uiptv.ui;

import com.uiptv.util.I18n;
import com.uiptv.util.VersionManager;
import com.uiptv.widget.InlinePanelService;
import com.uiptv.widget.InlinePanelService.InlinePanelHandle;
import javafx.application.HostServices;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.OverrunStyle;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.concurrent.atomic.AtomicReference;

public class AboutUI {
    private static final String FALLBACK_PROJECT_URL = "https://github.com/xixogo5105/uiptv";
    private static final double CONTENT_MAX_WIDTH = 720;

    private AboutUI(HostServices hostServices) {
        AtomicReference<InlinePanelHandle> handleRef = new AtomicReference<>();
        Runnable closeAction = () -> {
            InlinePanelHandle handle = handleRef.get();
            if (handle != null) {
                handle.close();
            }
        };

        Image image = new Image(getClass().getResourceAsStream("/icon.png"));

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
        closeButton.setOnAction(e -> closeAction.run());

        VBox content = new VBox(14, heroBox, infoBox);
        content.setAlignment(Pos.TOP_LEFT);
        content.setFillWidth(true);
        content.setMinWidth(0);
        content.setMaxWidth(Double.MAX_VALUE);

        HBox actions = new HBox(8, closeButton, updateButton);
        actions.setAlignment(Pos.CENTER_RIGHT);
        actions.setMinWidth(0);
        actions.setMaxWidth(Double.MAX_VALUE);
        actions.getStyleClass().add("about-actions");

        VBox dialogCard = new VBox(14, content, actions);
        dialogCard.setAlignment(Pos.TOP_LEFT);
        dialogCard.setFillWidth(true);
        dialogCard.setMinWidth(0);
        dialogCard.setPrefWidth(CONTENT_MAX_WIDTH);
        dialogCard.setMaxWidth(CONTENT_MAX_WIDTH);
        dialogCard.getStyleClass().add("about-dialog-card");

        InlinePanelService.open(I18n.tr("autoAboutUIPTV"), dialogCard, I18n.tr("commonClose"), null)
                .ifPresent(handleRef::set);
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

    private static String resolveReleaseSummary() {
        String currentVersion = VersionManager.getCurrentVersion();
        String releaseDescription = VersionManager.RELEASE_DESCRIPTION;
        if (VersionManager.NOT_AVAILABLE.equals(releaseDescription) || releaseDescription.isBlank()) {
            return currentVersion;
        }
        String language = I18n.getCurrentLocale().getLanguage();
        if (!"en".equalsIgnoreCase(language)) {
            return currentVersion;
        }
        return releaseDescription;
    }
}
