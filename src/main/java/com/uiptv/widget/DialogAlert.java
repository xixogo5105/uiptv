package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import com.uiptv.util.I18n;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;

public class DialogAlert {
    private DialogAlert() {
    }

    public static ButtonType showDialog(String contents) {
        return showDialog(I18n.tr("commonConfirm"), contents);
    }

    public static ButtonType showDialog(String title, String contents) {
        Alert confirmDialogue = new Alert(Alert.AlertType.CONFIRMATION, I18n.tr(contents), ButtonType.YES, ButtonType.NO);
        if (title != null) {
            confirmDialogue.setTitle(I18n.tr(title));
            confirmDialogue.setHeaderText(null);
        }
        Button yesButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.YES);
        yesButton.setDefaultButton(false);
        Button noButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.NO);
        noButton.setDefaultButton(true);
        confirmDialogue.initModality(Modality.NONE);
        if (RootApplication.currentTheme != null) {
            confirmDialogue.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
        }
        confirmDialogue.getDialogPane().setNodeOrientation(
                I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT
        );
        return confirmDialogue.showAndWait().orElse(ButtonType.NO);
    }
}
