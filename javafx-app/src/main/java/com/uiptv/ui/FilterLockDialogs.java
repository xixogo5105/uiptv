package com.uiptv.ui;

import com.uiptv.service.FilterLockService;
import com.uiptv.util.I18n;
import com.uiptv.widget.ThemedDialogSupport;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;
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

        PasswordField passwordField = createPasswordField("filterLockPasswordPrompt");
        Label description = createDialogDescription(reasonKey);

        Dialog<ButtonType> dialog = createDialog(owner, "filterLockUnlockTitle");
        dialog.getDialogPane().setContent(createDialogContent(description, passwordField));
        ButtonType unlockButtonType = new ButtonType(I18n.tr("filterLockUnlockAction"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(unlockButtonType, ButtonType.CANCEL);

        Node unlockButton = dialog.getDialogPane().lookupButton(unlockButtonType);
        unlockButton.disableProperty().bind(passwordField.textProperty().isEmpty());
        styleDialogButtons(dialog, unlockButtonType);
        dialog.setResultConverter(buttonType -> buttonType);

        Optional<ButtonType> result = ThemedDialogSupport.showAndWait(dialog, ownerWindow(owner));
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

        PasswordField currentPassword = createPasswordField("filterLockCurrentPasswordPrompt");
        PasswordField newPassword = createPasswordField("filterLockNewPasswordPrompt");
        PasswordField confirmPassword = createPasswordField("filterLockConfirmPasswordPrompt");

        VBox content = new VBox(10);
        content.getStyleClass().add("uiptv-dialog-content");
        content.getChildren().add(createDialogDescription("filterLockPasswordRequirements",
                lockService.getMinPasswordLength(),
                lockService.getUnlockWindowMinutes()));
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
        styleDialogButtons(dialog, saveButtonType);

        Optional<ButtonType> result = ThemedDialogSupport.showAndWait(dialog, ownerWindow(owner));
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

        PasswordField currentPassword = createPasswordField("filterLockCurrentPasswordPrompt");
        Label warning = createDialogDescription("filterLockDisablePasswordWarning");
        Label prompt = createDialogDescription("filterLockDisablePasswordPrompt");

        Dialog<ButtonType> dialog = createDialog(owner, "filterLockDisablePasswordTitle");
        dialog.getDialogPane().setContent(createDialogContent(warning, prompt, currentPassword));
        ButtonType saveButtonType = new ButtonType(I18n.tr("commonSave"), ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().setAll(saveButtonType, ButtonType.CANCEL);
        Node saveButton = dialog.getDialogPane().lookupButton(saveButtonType);
        saveButton.disableProperty().bind(currentPassword.textProperty().isEmpty());
        styleDialogButtons(dialog, saveButtonType);

        Optional<ButtonType> result = ThemedDialogSupport.showAndWait(dialog, ownerWindow(owner));
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
        ThemedDialogSupport.prepare(dialog, ownerWindow(owner), "uiptv-password-dialog");
        return dialog;
    }

    private static Window ownerWindow(Node owner) {
        return ThemedDialogSupport.ownerWindowOf(owner);
    }

    private static PasswordField createPasswordField(String promptKey) {
        PasswordField passwordField = new PasswordField();
        passwordField.setPromptText(I18n.tr(promptKey));
        passwordField.getStyleClass().add("uiptv-dialog-text-field");
        passwordField.setMaxWidth(Double.MAX_VALUE);
        return passwordField;
    }

    private static Label createDialogDescription(String key, Object... args) {
        Label label = new Label(I18n.tr(key, args));
        label.getStyleClass().add("uiptv-dialog-description");
        label.setWrapText(true);
        return label;
    }

    private static VBox createDialogContent(Node... nodes) {
        VBox content = new VBox(10, nodes);
        content.getStyleClass().add("uiptv-dialog-content");
        return content;
    }

    private static void styleDialogButtons(Dialog<ButtonType> dialog, ButtonType primaryButtonType) {
        Node primaryButton = dialog.getDialogPane().lookupButton(primaryButtonType);
        if (primaryButton != null) {
            primaryButton.getStyleClass().add("uiptv-dialog-primary-button");
        }
        Node cancelButton = dialog.getDialogPane().lookupButton(ButtonType.CANCEL);
        if (cancelButton != null) {
            cancelButton.getStyleClass().add("uiptv-dialog-secondary-button");
        }
    }
}
