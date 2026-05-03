package com.uiptv.ui;

import com.uiptv.util.I18n;

import com.uiptv.util.VersionManager;
import com.uiptv.widget.UIptvAlert;
import javafx.application.HostServices;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.Scene;
import javafx.scene.image.Image;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.shape.SVGPath;
import javafx.stage.Modality;
import javafx.stage.Stage;
import org.json.JSONObject;
import com.uiptv.util.HttpUtil;

import java.util.concurrent.atomic.AtomicBoolean;

public class UpdateChecker {
    static final String LATEST_RELEASE_API_URL = "https://api.github.com/repos/xixogo5105/uiptv/releases/latest";
    private static final String LATEST_RELEASE_PAGE_URL = "https://github.com/xixogo5105/uiptv/releases/latest";

    private UpdateChecker() {
    }

    public static void checkForUpdates(HostServices hostServices) {
        new Thread(() -> {
            try {
                HttpUtil.HttpResult response = HttpUtil.sendRequest(LATEST_RELEASE_API_URL, githubHeaders(), "GET");
                String content = response.body();

                UpdateInfo updateInfo = parseUpdateInfo(new JSONObject(content));

                if (isUpdateAvailable(updateInfo.getVersion())) {
                    Platform.runLater(() -> {
                        if (showUpdateAvailableDialog(updateInfo)) {
                            openWebpage(hostServices, updateInfo.getUrl());
                        }
                    });
                } else {
                    Platform.runLater(() -> UIptvAlert.showMessageAlert(I18n.tr("autoYouAreCurrentlyOnTheLatestVersion")));
                }
            } catch (Exception e) {
                UIptvAlert.showError(I18n.tr("autoUpdateCheckFailed", e.getMessage()), e);
                Platform.runLater(() -> UIptvAlert.showErrorAlert(I18n.tr("autoUpdateCheckFailed", e.getMessage())));
            }
        }).start();
    }

    static UpdateInfo parseUpdateInfo(JSONObject json) {
        String latestVersion = normalizeVersionValue(json.optString("tag_name", json.optString("name", "")));
        String url = json.optString("html_url", LATEST_RELEASE_PAGE_URL);
        String description = json.optString("body", "").trim();
        if (description.isBlank()) {
            description = json.optString("name", latestVersion);
        }
        if (latestVersion.isBlank()) {
            latestVersion = "0";
        }
        return new UpdateInfo(latestVersion, url, description);
    }

    static boolean isUpdateAvailable(String latestVersion) {
        String currentVersion = VersionManager.getCurrentVersion();
        if (latestVersion == null || currentVersion == null) {
            return false;
        }

        try {
            String[] current = normalizeVersionValue(currentVersion).split("\\.");
            String[] latest = normalizeVersionValue(latestVersion).split("\\.");
            int length = Math.max(current.length, latest.length);
            for (int i = 0; i < length; i++) {
                int currentPart = i < current.length ? Integer.parseInt(current[i]) : 0;
                int latestPart = i < latest.length ? Integer.parseInt(latest[i]) : 0;
                if (latestPart > currentPart) {
                    return true;
                }
                if (latestPart < currentPart) {
                    return false;
                }
            }
        } catch (NumberFormatException e) {
            UIptvAlert.showError("Failed to compare versions. currentVersion='" + currentVersion + "', latestVersion='" + latestVersion + "'", e);
            return false;
        }

        return false;
    }

    private static void openWebpage(HostServices hostServices, String url) {
        hostServices.showDocument(url);
    }

    private static boolean showUpdateAvailableDialog(UpdateInfo updateInfo) {
        AtomicBoolean shouldDownload = new AtomicBoolean(false);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Update Available");
        stage.setResizable(false);
        Image icon = loadAppIcon();
        if (icon != null) {
            stage.getIcons().add(icon);
        }

        StackPane accentIcon = new StackPane();
        accentIcon.getStyleClass().add("update-dialog-icon");
        SVGPath accentIconGlyph = new SVGPath();
        accentIconGlyph.setContent("M6 2 H10 V10 H6 Z M6 12 H10 V16 H6 Z");
        accentIconGlyph.setScaleX(1.4);
        accentIconGlyph.setScaleY(1.4);
        accentIconGlyph.getStyleClass().add("update-dialog-icon-glyph");
        accentIcon.getChildren().add(accentIconGlyph);

        Label badgeLabel = new Label("NEW RELEASE");
        badgeLabel.getStyleClass().add("update-dialog-badge");

        Label versionChip = new Label("v" + updateInfo.getVersion());
        versionChip.getStyleClass().add("update-dialog-version-chip");

        Region titleSpacer = new Region();
        HBox.setHgrow(titleSpacer, Priority.ALWAYS);

        HBox badgeRow = new HBox(8, badgeLabel, titleSpacer, versionChip);
        badgeRow.setAlignment(Pos.CENTER_LEFT);

        Label titleLabel = new Label("A new UIPTV version is ready");
        titleLabel.getStyleClass().addAll("strong-label", "update-dialog-title");
        titleLabel.setWrapText(true);

        Label introLabel = new Label("Review the release notes below and click Download to open the release page.");
        introLabel.getStyleClass().add("dim-label");
        introLabel.setWrapText(true);

        VBox titleBlock = new VBox(6, badgeRow, titleLabel, introLabel);
        titleBlock.setAlignment(Pos.CENTER_LEFT);

        HBox heroRow = new HBox(12, accentIcon, titleBlock);
        heroRow.getStyleClass().add("update-dialog-hero");
        heroRow.setAlignment(Pos.TOP_LEFT);
        heroRow.setPrefWidth(748);
        heroRow.setMaxWidth(748);

        Label notesLabel = new Label("Release notes");
        notesLabel.getStyleClass().addAll("strong-label", "update-dialog-notes-title");

        TextArea releaseNotesArea = new TextArea(updateInfo.getDescription());
        releaseNotesArea.getStyleClass().add("update-dialog-notes-area");
        releaseNotesArea.setEditable(false);
        releaseNotesArea.setWrapText(true);
        releaseNotesArea.setFocusTraversable(false);
        releaseNotesArea.setPrefWidth(748);
        releaseNotesArea.setPrefHeight(322);
        releaseNotesArea.setMinHeight(220);
        releaseNotesArea.setMaxWidth(Double.MAX_VALUE);

        VBox notesCard = new VBox(10, notesLabel, new Separator(), releaseNotesArea);
        notesCard.getStyleClass().add("update-dialog-notes-card");
        notesCard.setPrefWidth(748);
        notesCard.setMaxWidth(748);

        VBox content = new VBox(14, heroRow, notesCard);
        content.getStyleClass().add("update-dialog-root");
        content.setAlignment(Pos.TOP_CENTER);
        content.setFillWidth(false);
        content.setPrefWidth(748);
        content.setMaxWidth(748);

        Button closeButton = new Button(I18n.tr("commonClose"));
        closeButton.setCancelButton(true);
        closeButton.setOnAction(event -> stage.close());

        Button downloadButton = new Button("Download");
        downloadButton.getStyleClass().add("prominent");
        downloadButton.setDefaultButton(true);
        downloadButton.setOnAction(event -> {
            shouldDownload.set(true);
            stage.close();
        });

        HBox actions = new HBox(10, closeButton, downloadButton);
        actions.getStyleClass().add("update-dialog-actions");
        actions.setAlignment(Pos.CENTER_RIGHT);

        BorderPane root = new BorderPane();
        root.getStyleClass().add("update-dialog-stage-root");
        root.setPadding(new Insets(18, 24, 18, 24));
        root.setCenter(content);
        root.setBottom(actions);
        BorderPane.setMargin(actions, new Insets(14, 0, 0, 0));

        Scene scene = new Scene(root, 820, 612);
        scene.setFill(null);
        I18n.applySceneOrientation(scene);
        if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        stage.getScene();
        stage.setScene(scene);
        stage.showAndWait();
        return shouldDownload.get();
    }

    private static Image loadAppIcon() {
        try {
            java.io.InputStream stream = UpdateChecker.class.getResourceAsStream("/icon.png");
            return stream == null ? null : new Image(stream);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static java.util.Map<String, String> githubHeaders() {
        java.util.Map<String, String> headers = new java.util.LinkedHashMap<>();
        headers.put("Accept", "application/vnd.github+json");
        headers.put("X-GitHub-Api-Version", "2022-11-28");
        headers.put("User-Agent", "UIPTV/" + VersionManager.getCurrentVersion());
        return headers;
    }

    static String normalizeVersionValue(String value) {
        if (value == null || value.isBlank()) {
            return "0";
        }
        String normalized = value.trim();
        if (normalized.startsWith("v") || normalized.startsWith("V")) {
            normalized = normalized.substring(1);
        }
        normalized = normalized.replace("-SNAPSHOT", "");
        return normalized;
    }
}
