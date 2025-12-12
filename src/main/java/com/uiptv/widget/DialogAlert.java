package com.uiptv.widget;

import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;

public class DialogAlert {
    public static Alert showDialog(String contents) {
        Alert confirmDialogue = new Alert(Alert.AlertType.CONFIRMATION, contents, ButtonType.YES, ButtonType.NO);
        Button yesButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.YES);
        yesButton.setDefaultButton(false);
        Button noButton = (Button) confirmDialogue.getDialogPane().lookupButton(ButtonType.NO);
        noButton.setDefaultButton(true);
        confirmDialogue.initModality(Modality.NONE);
        confirmDialogue.showAndWait();
        return confirmDialogue;
    }
}
