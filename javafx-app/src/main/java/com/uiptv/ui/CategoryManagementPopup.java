package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

public class CategoryManagementPopup extends CategoryManagementInline {
    private static final double SCENE_WIDTH = 480;
    private static final double SCENE_HEIGHT = 520;

    private final Stage stage;

    public CategoryManagementPopup(Stage owner, BookmarkChannelListUI parent) {
        this(createStage(owner), owner, parent);
    }

    private CategoryManagementPopup(Stage stage, Stage owner, BookmarkChannelListUI parent) {
        super(parent);
        this.stage = stage;
        stage.setTitle(I18n.tr("autoManageCategories"));
        stage.setScene(createScene(owner));
    }

    public static void showPopup(Stage owner, BookmarkChannelListUI parent, Runnable onClose) {
        CategoryManagementPopup popup = new CategoryManagementPopup(owner, parent);
        popup.showAndWait();
        if (onClose != null) {
            onClose.run();
        }
    }

    public void showAndWait() {
        stage.showAndWait();
    }

    private static Stage createStage(Stage owner) {
        Stage popupStage = new Stage();
        if (owner != null) {
            popupStage.initOwner(owner);
            popupStage.initModality(Modality.WINDOW_MODAL);
        } else {
            popupStage.initModality(Modality.APPLICATION_MODAL);
        }
        return popupStage;
    }

    private Scene createScene(Stage owner) {
        Scene scene = new Scene(this, SCENE_WIDTH, SCENE_HEIGHT);
        UiI18n.applySceneOrientation(scene);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        } else if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }
        return scene;
    }
}
