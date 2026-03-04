package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;

public class DialogAlert {
    public static ButtonType showDialog(String contents) {
        return showDialog("Confirm", contents);
    }

    public static ButtonType showDialog(String title, String contents) {
        Alert confirmDialogue = new Alert(Alert.AlertType.CONFIRMATION, contents, ButtonType.YES, ButtonType.NO);
        if (title != null) {
            confirmDialogue.setTitle(title);
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
        return confirmDialogue.showAndWait().orElse(ButtonType.NO);
    }
}
