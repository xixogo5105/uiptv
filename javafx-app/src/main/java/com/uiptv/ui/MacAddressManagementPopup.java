package com.uiptv.ui;

import com.uiptv.model.Account;
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
        this(createStage(owner), baseAccount, initialMacs, currentDefaultMac, onSave, true);
    }

    private MacAddressManagementPopup(Stage stage,
                                      Account baseAccount,
                                      List<String> initialMacs,
                                      String currentDefaultMac,
                                      BiConsumer<List<String>, String> onSave,
                                      boolean ignored) {
        super(baseAccount, initialMacs, currentDefaultMac, onSave, closeHandler(stage, ignored));
        this.stage = stage;
        stage.setTitle(I18n.tr("autoManageMACAddresses"));
        stage.setScene(createScene());
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

    private Scene createScene() {
        Scene scene = new Scene(this, SCENE_WIDTH, SCENE_HEIGHT);
        RootApplication.applyThemeToPopupScene(scene);
        return scene;
    }

    private static Runnable closeHandler(Stage stage, boolean managed) {
        return managed ? stage::close : stage::hide;
    }
}
