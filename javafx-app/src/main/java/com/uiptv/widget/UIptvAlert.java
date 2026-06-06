package com.uiptv.widget;

import com.uiptv.util.AppLog;
import com.uiptv.util.I18n;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.stage.Window;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

public class UIptvAlert {
    private static final Set<String> ACTIVE_VOID_ALERTS = ConcurrentHashMap.newKeySet();

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
        String alertKey = "INFO:" + message;
        if (!ACTIVE_VOID_ALERTS.add(alertKey)) {
            AppLog.addInfoLog(UIptvAlert.class, "Message alert coalesced: " + message);
            return;
        }
        Runnable cleanup = () -> ACTIVE_VOID_ALERTS.remove(alertKey);
        if (!AppNotificationCenter.showInfo(message, cleanup)) {
            showVoidAlertLater(() -> {
                Alert alert = new Alert(Alert.AlertType.INFORMATION, message, closeButtonType());
                alert.setTitle(I18n.tr("commonInfo"));
                alert.setHeaderText(I18n.tr("commonInfo"));
                prepareAlert(alert, null, alert.getButtonTypes().getFirst());
                return alert;
            }, cleanup);
        }
    }

    public static boolean showConfirmationAlert(String contents) {
        String message = I18n.tr(contents);
        AppLog.addInfoLog(UIptvAlert.class, "Confirmation requested: " + message);
        if (isHeadlessMode()) {
            AppLog.addWarningLog(UIptvAlert.class, "Confirmation skipped in headless mode.");
            return false;
        }
        if (!Platform.isFxApplicationThread()) {
            AtomicBoolean confirmed = new AtomicBoolean(false);
            CountDownLatch latch = new CountDownLatch(1);
            Platform.runLater(() -> {
                try {
                    confirmed.set(showConfirmationAlertNow(message));
                } finally {
                    latch.countDown();
                }
            });
            awaitLatch(latch);
            return confirmed.get();
        }
        return showConfirmationAlertNow(message);
    }

    private static boolean showConfirmationAlertNow(String message) {
        ButtonType okButton = okButtonType();
        ButtonType closeButton = closeButtonType();
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, message, okButton, closeButton);
        alert.setTitle(I18n.tr("commonConfirm"));
        alert.setHeaderText(I18n.tr("commonConfirm"));
        prepareAlert(alert, okButton, closeButton);
        java.util.Optional<ButtonType> result = ThemedDialogSupport.showAndWait(alert, alertOwnerWindow());
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
        String alertKey = "ERROR:" + message;
        if (!ACTIVE_VOID_ALERTS.add(alertKey)) {
            AppLog.addInfoLog(UIptvAlert.class, "Error alert coalesced: " + message);
            return;
        }
        Runnable cleanup = () -> ACTIVE_VOID_ALERTS.remove(alertKey);
        if (!AppNotificationCenter.showError(message, cleanup)) {
            showVoidAlertLater(() -> {
                Alert alert = new Alert(Alert.AlertType.ERROR, message, closeButtonType());
                alert.setTitle(I18n.tr("commonError"));
                alert.setHeaderText(I18n.tr("commonError"));
                prepareAlert(alert, null, alert.getButtonTypes().getFirst());
                return alert;
            }, cleanup);
        }
    }

    private static void showVoidAlertLater(Supplier<Alert> alertSupplier, Runnable cleanup) {
        Platform.runLater(() -> Platform.runLater(() -> {
            try {
                Alert alert = alertSupplier.get();
                ThemedDialogSupport.showAndWait(alert, alertOwnerWindow());
            } finally {
                cleanup.run();
            }
        }));
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
        if (button != null && !button.getStyleClass().contains(styleClass)) {
            button.getStyleClass().add(styleClass);
        }
    }

    private static Window alertOwnerWindow() {
        return ThemedDialogSupport.primaryOwnerWindow();
    }

    private static boolean isHeadlessMode() {
        return Boolean.getBoolean("uiptv.headless");
    }

    private static void awaitLatch(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException _) {
            Thread.currentThread().interrupt();
        }
    }

    private static void logError(String message, Exception ex) {
        if (ex == null) {
            AppLog.addErrorLog(UIptvAlert.class, message);
            return;
        }
        AppLog.addErrorLog(UIptvAlert.class, message + ": " + ex.getMessage(), ex);
    }
}
