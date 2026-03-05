package com.uiptv.widget;

import com.uiptv.ui.LogDisplayUI;
import com.uiptv.ui.RootApplication;
import com.uiptv.util.I18n;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;

import java.util.Optional;

public class UIptvAlert {
    public static void showMessageAlert(String contents) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, I18n.tr(contents), ButtonType.CLOSE);
        alert.setTitle(I18n.tr("commonInfo"));
        alert.setHeaderText(null);
        alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        alert.initModality(Modality.NONE);
        alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
        alert.showAndWait();
    }

    public static boolean showConfirmationAlert(String contents) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.tr(contents), ButtonType.OK, ButtonType.CLOSE);
        alert.setTitle(I18n.tr("commonConfirm"));
        alert.setHeaderText(null);
        alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        alert.initModality(Modality.NONE);
        alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == ButtonType.OK;
    }

    public static void showMessage(String contents) {
        com.uiptv.util.AppLog.addLog(contents);
    }
    public static void showError(String contents) {
        showError(contents, null);
    }
    public static void showError(String contents, Exception ex) {
        if (ex != null) {
            com.uiptv.util.AppLog.addLog(contents);
            com.uiptv.util.AppLog.addLog(ex.getMessage());
        }
    }

    public static void showErrorAlert(String contents) {
        showErrorAlert(contents, null);
    }
    public static void showErrorAlert(String contents, Exception ex) {
        if (ex != null) {
            com.uiptv.util.AppLog.addLog(ex.getMessage());
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, I18n.tr(contents), ButtonType.CLOSE);
        alert.setTitle(I18n.tr("commonError"));
        alert.setHeaderText(null);
        alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        alert.initModality(Modality.NONE);
        alert.getDialogPane().getStylesheets().add(RootApplication.currentTheme);
        alert.showAndWait();
    }
}
