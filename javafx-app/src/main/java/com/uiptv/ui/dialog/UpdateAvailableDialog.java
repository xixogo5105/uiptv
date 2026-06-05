package com.uiptv.ui.dialog;

import com.uiptv.ui.UpdateInfo;
import com.uiptv.util.I18n;
import com.uiptv.widget.InlinePanelService;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
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
import javafx.stage.Screen;
import javafx.geometry.Rectangle2D;

import java.io.InputStream;
import java.util.List;

public final class UpdateAvailableDialog {
    private static final String APP_ICON_RESOURCE = "/icon.png";
    private static final double BASE_SCENE_WIDTH = 820;
    private static final double BASE_SCENE_HEIGHT = 648;
    private static final double MIN_SCENE_WIDTH = 560;
    private static final double MIN_SCENE_HEIGHT = 440;

    private UpdateAvailableDialog() {
    }

    public static boolean show(UpdateInfo updateInfo) {
        Image appIcon = loadAppIcon();

        BorderPane root = new BorderPane();
        root.getStyleClass().add("update-dialog-stage-root");
        root.setPadding(new Insets(18, 24, 18, 24));
        root.setCenter(createScrollContent(updateInfo, appIcon));
        root.setPrefSize(resolveSceneWidth(), resolveSceneHeight());
        root.setMaxWidth(Double.MAX_VALUE);
        root.setMaxHeight(Double.MAX_VALUE);

        ButtonType closeButton = new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE);
        ButtonType downloadButton = new ButtonType("Download", ButtonBar.ButtonData.OK_DONE);
        return InlinePanelService.showChoice(
                        "Update Available",
                        root,
                        List.of(closeButton, downloadButton),
                        closeButton
                )
                .filter(downloadButton::equals)
                .isPresent();
    }

    private static ScrollPane createScrollContent(UpdateInfo updateInfo, Image appIcon) {
        ScrollPane scrollPane = new ScrollPane(createContent(updateInfo, appIcon));
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(false);
        scrollPane.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        scrollPane.getStyleClass().add("transparent-scroll-pane");
        return scrollPane;
    }

    private static VBox createContent(UpdateInfo updateInfo, Image appIcon) {
        VBox content = new VBox(14, createHero(updateInfo, appIcon), createNotesCard(updateInfo));
        content.getStyleClass().add("update-dialog-root");
        content.setAlignment(Pos.TOP_LEFT);
        content.setFillWidth(true);
        content.setMaxWidth(Double.MAX_VALUE);
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
        titleBlock.setMaxWidth(Double.MAX_VALUE);
        HBox.setHgrow(titleBlock, Priority.ALWAYS);

        HBox heroRow = new HBox(14, createHeroIcon(appIcon), titleBlock);
        heroRow.getStyleClass().add("update-dialog-hero");
        heroRow.setAlignment(Pos.TOP_LEFT);
        heroRow.setMaxWidth(Double.MAX_VALUE);
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
        releaseNotesArea.setPrefHeight(322);
        releaseNotesArea.setMinHeight(220);
        releaseNotesArea.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(releaseNotesArea, Priority.ALWAYS);

        VBox notesCard = new VBox(10, notesLabel, new Separator(), releaseNotesArea);
        notesCard.getStyleClass().add("update-dialog-notes-card");
        notesCard.setFillWidth(true);
        notesCard.setMaxWidth(Double.MAX_VALUE);
        VBox.setVgrow(notesCard, Priority.ALWAYS);
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

    private static Image loadAppIcon() {
        try (InputStream stream = UpdateAvailableDialog.class.getResourceAsStream(APP_ICON_RESOURCE)) {
            return stream == null ? null : new Image(stream);
        } catch (Exception _) {
            return null;
        }
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
        return Math.clamp(value, min, max);
    }
}
