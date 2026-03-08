package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import com.uiptv.util.I18n;
import javafx.geometry.NodeOrientation;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Modality;

import java.util.Optional;

public class UIptvAlert {
    private UIptvAlert() {
    }

    public static ButtonType okButtonType() {
        return new ButtonType(I18n.tr("commonOk"), ButtonBar.ButtonData.OK_DONE);
    }

    public static ButtonType closeButtonType() {
        return new ButtonType(I18n.tr("commonClose"), ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    public static void showMessageAlert(String contents) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION, I18n.tr(contents), closeButtonType());
        alert.setTitle(I18n.tr("commonInfo"));
        alert.setHeaderText(null);
        alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        alert.initModality(Modality.NONE);
        alert.getDialogPane().getStylesheets().add(RootApplication.getCurrentTheme());
        alert.showAndWait();
    }

    public static boolean showConfirmationAlert(String contents) {
        ButtonType okButton = okButtonType();
        ButtonType closeButton = closeButtonType();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, I18n.tr(contents), okButton, closeButton);
        alert.setTitle(I18n.tr("commonConfirm"));
        alert.setHeaderText(null);
        alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        alert.initModality(Modality.NONE);
        alert.getDialogPane().getStylesheets().add(RootApplication.getCurrentTheme());
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == okButton;
    }

    public static void showMessage(String contents) {
        com.uiptv.util.AppLog.addLog(contents);
    }

    public static void showMessageKey(String key, Object... args) {
        com.uiptv.util.AppLog.addLog(I18n.trEnglish(key, args));
    }

    public static void showError(String contents) {
        showError(contents, null);
    }

    public static void showErrorKey(String key, Exception ex, Object... args) {
        com.uiptv.util.AppLog.addLog(I18n.trEnglish(key, args));
        if (ex != null) {
            com.uiptv.util.AppLog.addLog(ex.getMessage());
        }
    }

    public static void showErrorKey(String key, Object... args) {
        showErrorKey(key, null, args);
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
        Alert alert = new Alert(Alert.AlertType.ERROR, I18n.tr(contents), closeButtonType());
        alert.setTitle(I18n.tr("commonError"));
        alert.setHeaderText(null);
        alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        alert.initModality(Modality.NONE);
        alert.getDialogPane().getStylesheets().add(RootApplication.getCurrentTheme());
        alert.showAndWait();
    }
}
