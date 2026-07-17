package com.uiptv.widget;

import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

public class DialogAlert {
    private DialogAlert() {
    }

    public static ButtonType showDialog(String contents) {
        return showDialog(I18n.tr("commonConfirm"), contents);
    }

    public static ButtonType showDialog(String title, String contents) {
        String message = I18n.tr(contents);
        AppLog.addInfoLog(DialogAlert.class, "Dialog requested: " + message);
        if (Boolean.getBoolean("uiptv.headless")) {
            AppLog.addWarningLog(DialogAlert.class, "Dialog skipped in headless mode.");
            return ButtonType.NO;
        }
        Alert confirmDialogue = new Alert(Alert.AlertType.CONFIRMATION, message, ButtonType.YES, ButtonType.NO);
        if (title != null) {
            String localizedTitle = I18n.tr(title);
            confirmDialogue.setTitle(localizedTitle);
            confirmDialogue.setHeaderText(localizedTitle);
        }
        Button yesButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.YES);
        yesButton.setDefaultButton(false);
        Button noButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.NO);
        noButton.setDefaultButton(true);
        Window ownerWindow = ThemedDialogSupport.activeOwnerWindow();
        ThemedDialogSupport.prepare(confirmDialogue, ownerWindow, "uiptv-alert-dialog");
        yesButton.getStyleClass().add("uiptv-dialog-primary-button");
        noButton.getStyleClass().add("uiptv-dialog-secondary-button");
        return ThemedDialogSupport.showAndWait(confirmDialogue, ownerWindow)
                .orElse(ButtonType.NO);
    }
}
