package com.uiptv.ui.dialog;

import com.uiptv.ui.RootApplication;
import com.uiptv.ui.UpdateInfo;
import com.uiptv.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Separator;
import javafx.scene.control.TextArea;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

public final class UpdateAvailableDialog {
    private static final String APP_ICON_RESOURCE = "/icon.png";

    private UpdateAvailableDialog() {
    }

    public static boolean show(UpdateInfo updateInfo) {
        AtomicBoolean shouldDownload = new AtomicBoolean(false);
        Stage stage = new Stage();
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setTitle("Update Available");
        stage.setResizable(false);

        Image appIcon = loadAppIcon();
        if (appIcon != null) {
            stage.getIcons().add(appIcon);
        }

        BorderPane root = new BorderPane();
        root.getStyleClass().add("update-dialog-stage-root");
        root.setPadding(new Insets(18, 24, 18, 24));
        root.setCenter(createContent(updateInfo, appIcon));
        root.setBottom(createActions(stage, shouldDownload));
        BorderPane.setMargin(root.getBottom(), new Insets(14, 0, 0, 0));

        Scene scene = new Scene(root, 820, 612);
        I18n.applySceneOrientation(scene);
        if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }

        stage.setScene(scene);
        stage.showAndWait();
        return shouldDownload.get();
    }

    private static VBox createContent(UpdateInfo updateInfo, Image appIcon) {
        VBox content = new VBox(14, createHero(updateInfo, appIcon), createNotesCard(updateInfo));
        content.getStyleClass().add("update-dialog-root");
        content.setAlignment(Pos.TOP_CENTER);
        content.setFillWidth(false);
        content.setPrefWidth(748);
        content.setMaxWidth(748);
        return content;
    }

    private static HBox createHero(UpdateInfo updateInfo, Image appIcon) {
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

        HBox heroRow = new HBox(14, createHeroIcon(appIcon), titleBlock);
        heroRow.getStyleClass().add("update-dialog-hero");
        heroRow.setAlignment(Pos.TOP_LEFT);
        heroRow.setPrefWidth(748);
        heroRow.setMaxWidth(748);
        return heroRow;
    }

    private static VBox createNotesCard(UpdateInfo updateInfo) {
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
        return notesCard;
    }

    private static StackPane createHeroIcon(Image appIcon) {
        StackPane imageShell = new StackPane();
        imageShell.getStyleClass().add("update-dialog-icon-shell");

        if (appIcon != null) {
            ImageView imageView = new ImageView(appIcon);
            imageView.setFitHeight(72);
            imageView.setFitWidth(72);
            imageView.setPreserveRatio(true);
            imageView.setSmooth(true);
            imageView.getStyleClass().add("update-dialog-icon-image");
            imageShell.getChildren().add(imageView);
        }

        return imageShell;
    }

    private static HBox createActions(Stage stage, AtomicBoolean shouldDownload) {
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
        return actions;
    }

    private static Image loadAppIcon() {
        try (InputStream stream = UpdateAvailableDialog.class.getResourceAsStream(APP_ICON_RESOURCE)) {
            return stream == null ? null : new Image(stream);
        } catch (Exception ignored) {
            return null;
        }
    }
}
