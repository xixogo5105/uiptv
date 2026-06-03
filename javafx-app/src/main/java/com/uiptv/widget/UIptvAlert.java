package com.uiptv.widget;

import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

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
        prepareAlert(alert, null, alert.getButtonTypes().getFirst());
        ThemedDialogSupport.showAndWait(alert, alertOwnerWindow());
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
        prepareAlert(alert, okButton, closeButton);
        Optional<ButtonType> result = ThemedDialogSupport.showAndWait(alert, alertOwnerWindow());
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
        prepareAlert(alert, null, alert.getButtonTypes().getFirst());
        ThemedDialogSupport.showAndWait(alert, alertOwnerWindow());
    }

    private static void prepareAlert(Alert alert, ButtonType primaryButtonType, ButtonType secondaryButtonType) {
        ThemedDialogSupport.prepare(alert, alertOwnerWindow(), "uiptv-alert-dialog");
        styleAlertButton(alert, primaryButtonType, "uiptv-dialog-primary-button");
        styleAlertButton(alert, secondaryButtonType, "uiptv-dialog-secondary-button");
    }

    private static void styleAlertButton(Alert alert, ButtonType buttonType, String styleClass) {
        if (buttonType == null) {
            return;
        }
        Button button = (Button) alert.getDialogPane().lookupButton(buttonType);
        if (button != null) {
            button.getStyleClass().add(styleClass);
        }
    }

    private static Window alertOwnerWindow() {
        return ThemedDialogSupport.primaryOwnerWindow();
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
