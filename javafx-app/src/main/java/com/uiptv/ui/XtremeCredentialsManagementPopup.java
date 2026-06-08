package com.uiptv.ui;

import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import com.uiptv.util.XtremeCredentialsJson;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.BiConsumer;

public class XtremeCredentialsManagementPopup extends XtremeCredentialsManagementInline {
    private static final double SCENE_WIDTH = 560;
    private static final double SCENE_HEIGHT = 620;

    private final Stage stage;

    public XtremeCredentialsManagementPopup(Stage owner,
                                            List<XtremeCredentialsJson.Entry> initialEntries,
                                            String currentDefaultUsername,
                                            BiConsumer<List<XtremeCredentialsJson.Entry>, String> onSave) {
        this(createStage(owner), owner, initialEntries, currentDefaultUsername, onSave);
    }

    private XtremeCredentialsManagementPopup(Stage stage,
                                             Stage owner,
                                             List<XtremeCredentialsJson.Entry> initialEntries,
                                             String currentDefaultUsername,
                                             BiConsumer<List<XtremeCredentialsJson.Entry>, String> onSave) {
        super(initialEntries, currentDefaultUsername, onSave, stage::close);
        this.stage = stage;
        stage.setTitle(I18n.tr("autoManage") + " Xtreme");
        stage.setScene(createScene(owner));
    }

    public void show() {
        stage.show();
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
