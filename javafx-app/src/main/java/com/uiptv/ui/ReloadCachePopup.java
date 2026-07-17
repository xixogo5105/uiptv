package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.ui.util.UiI18n;
import com.uiptv.util.I18n;
import javafx.scene.Scene;
import javafx.stage.Modality;
import javafx.stage.Stage;

import java.util.List;

public final class ReloadCachePopup {
    private static final double SCENE_WIDTH = 1368;
    private static final double SCENE_HEIGHT = 720;

    private ReloadCachePopup() {
    }

    public static void showPopup(Stage owner) {
        showPopup(owner, null);
    }

    public static void showPopup(Stage owner, List<Account> preselectedAccounts) {
        showPopup(owner, preselectedAccounts, null);
    }

    public static void showPopup(Stage owner, List<Account> preselectedAccounts, Runnable onAccountsDeleted) {
        Stage popupStage = createStage(owner);
        ReloadCacheInline popupContent = new ReloadCacheInline(preselectedAccounts, onAccountsDeleted);
        popupContent.setExternalCloseHandler(popupStage::close);

        Scene scene = new Scene(popupContent, SCENE_WIDTH, SCENE_HEIGHT);
        UiI18n.applySceneOrientation(scene);
        if (owner != null && owner.getScene() != null) {
            scene.getStylesheets().addAll(owner.getScene().getStylesheets());
        } else if (RootApplication.getCurrentTheme() != null) {
            scene.getStylesheets().add(RootApplication.getCurrentTheme());
        }

        popupStage.setTitle(I18n.tr("autoReloadAccountsCache"));
        popupStage.setScene(scene);
        popupStage.setOnCloseRequest(event -> popupContent.disposeExternal());
        popupStage.setOnHidden(event -> popupContent.disposeExternal());
        popupStage.setMaximized(true);
        popupStage.showAndWait();
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
}
