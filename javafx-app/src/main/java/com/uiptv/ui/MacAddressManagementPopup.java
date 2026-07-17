package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;
import java.util.function.BiConsumer;

public class MacAddressManagementPopup extends MacAddressManagementInline {
    private static final double SCENE_WIDTH = 560;
    private static final double SCENE_HEIGHT = 620;

    private final Stage stage;

    public MacAddressManagementPopup(Stage owner,
                                     Account baseAccount,
                                     List<String> initialMacs,
                                     String currentDefaultMac,
                                     BiConsumer<List<String>, String> onSave) {
        this(createStage(owner), owner, baseAccount, initialMacs, currentDefaultMac, onSave);
    }

    private MacAddressManagementPopup(Stage stage,
                                      Stage owner,
                                      Account baseAccount,
                                      List<String> initialMacs,
                                      String currentDefaultMac,
                                      BiConsumer<List<String>, String> onSave) {
        super(baseAccount, initialMacs, currentDefaultMac, onSave, stage::close);
        this.stage = stage;
        stage.setTitle(I18n.tr("autoManageMACAddresses"));
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
