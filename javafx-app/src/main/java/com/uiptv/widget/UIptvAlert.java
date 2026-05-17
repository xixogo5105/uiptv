package com.uiptv.widget;

import com.uiptv.ui.RootApplication;
import com.uiptv.util.AppLog;
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
        String message = I18n.tr(contents);
        AppLog.addInfoLog(UIptvAlert.class, message);
        if (isHeadlessMode()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.INFORMATION, message, closeButtonType());
        alert.setTitle(I18n.tr("commonInfo"));
        prepareAlert(alert);
        alert.showAndWait();
    }

    public static boolean showConfirmationAlert(String contents) {
        String message = I18n.tr(contents);
        AppLog.addInfoLog(UIptvAlert.class, "Confirmation requested: " + message);
        if (isHeadlessMode()) {
            AppLog.addWarningLog(UIptvAlert.class, "Confirmation skipped in headless mode.");
            return false;
        }
        ButtonType okButton = okButtonType();
        ButtonType closeButton = closeButtonType();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, okButton, closeButton);
        alert.setTitle(I18n.tr("commonConfirm"));
        prepareAlert(alert);
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == okButton;
    }

    public static void showMessage(String contents) {
        AppLog.addInfoLog(UIptvAlert.class, contents);
    }

    public static void showMessageKey(String key, Object... args) {
        AppLog.addInfoLog(UIptvAlert.class, I18n.trEnglish(key, args));
    }

    public static void showError(String contents) {
        showError(contents, null);
    }

    public static void showErrorKey(String key, Exception ex, Object... args) {
        logError(I18n.trEnglish(key, args), ex);
    }

    public static void showErrorKey(String key, Object... args) {
        showErrorKey(key, null, args);
    }

    public static void showError(String contents, Exception ex) {
        logError(contents, ex);
    }

    public static void showErrorAlert(String contents) {
        showErrorAlert(contents, null);
    }
    public static void showErrorAlert(String contents, Exception ex) {
        String message = I18n.tr(contents);
        logError(message, ex);
        if (isHeadlessMode()) {
            return;
        }
        Alert alert = new Alert(Alert.AlertType.ERROR, message, closeButtonType());
        alert.setTitle(I18n.tr("commonError"));
        prepareAlert(alert);
        alert.showAndWait();
    }

    private static void prepareAlert(Alert alert) {
        alert.setHeaderText(null);
        alert.getDialogPane().setNodeOrientation(I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT);
        alert.initModality(Modality.NONE);
        String theme = RootApplication.getCurrentTheme();
        if (theme != null) {
            alert.getDialogPane().getStylesheets().add(theme);
        }
    }

    private static boolean isHeadlessMode() {
        return Boolean.getBoolean("uiptv.headless");
    }

    private static void logError(String message, Exception ex) {
        if (ex == null) {
            AppLog.addErrorLog(UIptvAlert.class, message);
            return;
        }
        AppLog.addErrorLog(UIptvAlert.class, message + ": " + ex.getMessage(), ex);
    }
}
