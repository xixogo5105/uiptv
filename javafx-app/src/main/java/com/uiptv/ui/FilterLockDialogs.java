package com.uiptv.ui;

import com.uiptv.service.FilterLockService;
import com.uiptv.util.I18n;
import javafx.geometry.Insets;
import javafx.geometry.NodeOrientation;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
import javafx.stage.Modality;
import javafx.stage.Window;

import java.util.Optional;

import static com.uiptv.widget.UIptvAlert.showErrorAlert;
import static com.uiptv.widget.UIptvAlert.showMessageAlert;

public final class FilterLockDialogs {
    private FilterLockDialogs() {
    }

    public static boolean ensureUnlocked(Node owner, String reasonKey) {
        FilterLockService lockService = FilterLockService.getInstance();
        if (!lockService.hasPasswordConfigured() || lockService.isUnlocked()) {
            return true;
        }

        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(I18n.tr("filterLockPasswordPrompt"));
        Label description = new Label(I18n.tr(reasonKey));
        description.setWrapText(true);

        Dialog<ButtonType> dialog = createDialog(owner, "filterLockUnlockTitle");
        dialog.getDialogPane().setContent(new VBox(10, description, passwordField));
        ButtonType unlockButtonType = new ButtonType(I18n.tr("filterLockUnlockAction"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(unlockButtonType, ButtonType.CANCEL);

        Node unlockButton = dialog.getDialogPane().lookupButton(unlockButtonType);
        unlockButton.disableProperty().bind(passwordField.textProperty().isEmpty());
        dialog.setResultConverter(buttonType -> buttonType);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != unlockButtonType) {
            return false;
        }
        boolean unlocked = lockService.unlockWithPassword(passwordField.getText());
        if (!unlocked) {
            showErrorAlert(I18n.tr("filterLockPasswordInvalid"));
        }
        return unlocked;
    }

    public static void openPasswordChangeDialog(Node owner) {
        FilterLockService lockService = FilterLockService.getInstance();
        boolean passwordAlreadySet = lockService.hasPasswordConfigured();

        PasswordField currentPassword = new PasswordField();
        currentPassword.setPromptText(I18n.tr("filterLockCurrentPasswordPrompt"));
        PasswordField newPassword = new PasswordField();
        newPassword.setPromptText(I18n.tr("filterLockNewPasswordPrompt"));
        PasswordField confirmPassword = new PasswordField();
        confirmPassword.setPromptText(I18n.tr("filterLockConfirmPasswordPrompt"));

        VBox content = new VBox(10);
        content.getChildren().add(new Label(I18n.tr("filterLockPasswordRequirements",
                lockService.getMinPasswordLength(),
                lockService.getUnlockWindowMinutes())));
        if (passwordAlreadySet) {
            content.getChildren().add(currentPassword);
        }
        content.getChildren().addAll(newPassword, confirmPassword);

        Dialog<ButtonType> dialog = createDialog(owner, passwordAlreadySet ? "filterLockChangePasswordTitle" : "filterLockSetPasswordTitle");
        dialog.getDialogPane().setContent(content);
        ButtonType saveButtonType = new ButtonType(I18n.tr("commonSave"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, ButtonType.CANCEL);
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(newPassword.textProperty().isEmpty().or(confirmPassword.textProperty().isEmpty()));

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButtonType) {
            return;
        }
        if (!newPassword.getText().equals(confirmPassword.getText())) {
            showErrorAlert(I18n.tr("filterLockPasswordConfirmMismatch"));
            return;
        }

        try {
            var configurationService = com.uiptv.service.ConfigurationService.getInstance();
            var configuration = configurationService.read();
            if (passwordAlreadySet) {
                lockService.applyPasswordChange(configuration, currentPassword.getText(), newPassword.getText());
            } else {
                lockService.applyInitialPassword(configuration, newPassword.getText());
            }
            configurationService.save(configuration);
            showMessageAlert(I18n.tr(passwordAlreadySet ? "filterLockPasswordChangeSuccess" : "filterLockPasswordSetSuccess"));
        } catch (IllegalArgumentException ex) {
            showErrorAlert(I18n.tr(ex.getMessage(), lockService.getMinPasswordLength()));
        } catch (Exception _) {
            showErrorAlert(I18n.tr("filterLockPasswordSaveFailed"));
        }
    }

    public static boolean openDisablePasswordDialog(Node owner) {
        FilterLockService lockService = FilterLockService.getInstance();
        if (!lockService.hasPasswordConfigured()) {
            showErrorAlert(I18n.tr("filterLockPasswordNotSet"));
            return false;
        }

        PasswordField currentPassword = new PasswordField();
        currentPassword.setPromptText(I18n.tr("filterLockCurrentPasswordPrompt"));
        Label warning = new Label(I18n.tr("filterLockDisablePasswordWarning"));
        warning.setWrapText(true);
        Label prompt = new Label(I18n.tr("filterLockDisablePasswordPrompt"));
        prompt.setWrapText(true);

        Dialog<ButtonType> dialog = createDialog(owner, "filterLockDisablePasswordTitle");
        dialog.getDialogPane().setContent(new VBox(10, warning, prompt, currentPassword));
        ButtonType saveButtonType = new ButtonType(I18n.tr("commonSave"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, ButtonType.CANCEL);
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(currentPassword.textProperty().isEmpty());

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() != saveButtonType) {
            return false;
        }

        try {
            var configurationService = com.uiptv.service.ConfigurationService.getInstance();
            var configuration = configurationService.read();
            lockService.clearPassword(configuration, currentPassword.getText());
            configurationService.save(configuration);
            showMessageAlert(I18n.tr("filterLockPasswordDisableSuccess"));
            return true;
        } catch (IllegalArgumentException ex) {
            showErrorAlert(I18n.tr(ex.getMessage(), lockService.getMinPasswordLength()));
            return false;
        } catch (Exception _) {
            showErrorAlert(I18n.tr("filterLockPasswordSaveFailed"));
            return false;
        }
    }

    private static Dialog<ButtonType> createDialog(Node owner, String titleKey) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle(I18n.tr(titleKey));
        dialog.setHeaderText(null);
        dialog.initModality(Modality.APPLICATION_MODAL);
        Window ownerWindow = owner != null && owner.getScene() != null ? owner.getScene().getWindow() : null;
        if (ownerWindow != null) {
            dialog.initOwner(ownerWindow);
        }
        if (RootApplication.getCurrentTheme() != null) {
            dialog.getDialogPane().getStylesheets().add(RootApplication.getCurrentTheme());
        }
        dialog.getDialogPane().setNodeOrientation(
                I18n.isCurrentLocaleRtl() ? NodeOrientation.RIGHT_TO_LEFT : NodeOrientation.LEFT_TO_RIGHT
        );
        dialog.getDialogPane().setPadding(new Insets(12));
        return dialog;
    }
}
