package com.uiptv.widget;

import com.uiptv.ui.LogDisplayUI;
import javafx.scene.control.ButtonType;

public class UIptvAlert {
    public static void showMessageAlert(String contents) {
        ThemedDialogs.showInfo("Info", contents);
    }

    public static void showWarningAlert(String contents) {
        ThemedDialogs.showInfo("Warning", contents);
    }

    public static boolean showConfirmationAlert(String contents) {
        ButtonType result = ThemedDialogs.showConfirm("Confirm", contents, ButtonType.OK, ButtonType.CLOSE);
        return result == ButtonType.OK;
    }

    public static boolean showYesNoConfirmation(String title, String contents) {
        ButtonType result = ThemedDialogs.showConfirm(title == null ? "Confirm" : title, contents, ButtonType.YES, ButtonType.NO);
        return result == ButtonType.YES;
    }

    public static void showMessage(String contents) {
        LogDisplayUI.addLog(contents);
    }
    public static void showError(String contents) {
        showError(contents, null);
    }
    public static void showError(String contents, Exception ex) {
        if (ex != null) {
            LogDisplayUI.addLog(contents);
            LogDisplayUI.addLog(ex.getMessage());
        }
    }

    public static void showErrorAlert(String contents) {
        showErrorAlert(contents, null);
    }
    public static void showErrorAlert(String contents, Exception ex) {
        if (ex != null) {
            LogDisplayUI.addLog(ex.getMessage());
        }
        ThemedDialogs.showError("Error", contents);
    }
}
