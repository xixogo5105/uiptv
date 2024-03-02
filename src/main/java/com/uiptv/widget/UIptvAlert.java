package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;

public class UIptvAlert {
    public static void showMessageAlert(String contents) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, contents, ButtonType.CLOSE);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.initOwner(RootApplication.primaryStage);
        alert.showAndWait();
    }

    public static void showMessage(String contents) {
            System.out.println(contents);
    }
    public static void showError(String contents) {
        showError(contents, null);
    }
    public static void showError(String contents, Exception ex) {
        if (ex != null) {
            System.out.println(contents);
            System.out.print(ex.getMessage());
        }
    }

    public static void showErrorAlert(String contents) {
        showErrorAlert(contents, null);
    }
    public static void showErrorAlert(String contents, Exception ex) {
        if (ex != null) {
            System.out.print(ex.getMessage());
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, contents, ButtonType.CLOSE);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.initOwner(RootApplication.primaryStage);
        alert.showAndWait();
    }
}
