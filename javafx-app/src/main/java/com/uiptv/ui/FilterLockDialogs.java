package com.uiptv.ui;

import com.uiptv.service.FilterLockService;
import com.uiptv.util.I18n;
import com.uiptv.widget.ThemedDialogSupport;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.*;
import javafx.scene.layout.VBox;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

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

        ButtonType unlockButtonType = new ButtonType(I18n.tr("filterLockUnlockAction"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = cancelButtonType();

        Optional<ButtonType> result = showPopupChoice(
                owner,
                "filterLockUnlockTitle",
                createDialogContent(description, passwordField),
                List.of(cancelButtonType, unlockButtonType),
                cancelButtonType,
                buttons -> bindButtonDisabled(buttons, unlockButtonType, passwordField.textProperty().isEmpty())
        );
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

        ButtonType saveButtonType = new ButtonType(I18n.tr("commonSave"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = cancelButtonType();

        Optional<ButtonType> result = showPopupChoice(
                owner,
                passwordAlreadySet ? "filterLockChangePasswordTitle" : "filterLockSetPasswordTitle",
                content,
                List.of(cancelButtonType, saveButtonType),
                cancelButtonType,
                buttons -> bindButtonDisabled(buttons, saveButtonType,
                        newPassword.textProperty().isEmpty().or(confirmPassword.textProperty().isEmpty()))
        );
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

        ButtonType saveButtonType = new ButtonType(I18n.tr("commonSave"), ButtonBar.ButtonData.OK_DONE);
        ButtonType cancelButtonType = cancelButtonType();

        Optional<ButtonType> result = showPopupChoice(
                owner,
                "filterLockDisablePasswordTitle",
                createDialogContent(warning, prompt, currentPassword),
                List.of(cancelButtonType, saveButtonType),
                cancelButtonType,
                buttons -> bindButtonDisabled(buttons, saveButtonType, currentPassword.textProperty().isEmpty())
        );
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

    private static Optional<ButtonType> showPopupChoice(Node owner,
                                                        String titleKey,
                                                        Node content,
                                                        List<ButtonType> buttons,
                                                        ButtonType fallbackButton,
                                                        Consumer<Map<ButtonType, Button>> buttonConfigurer) {
        return ThemedDialogSupport.showChoice(
                I18n.tr(titleKey),
                content,
                buttons,
                fallbackButton,
                buttonConfigurer,
                ThemedDialogSupport.ownerWindowOf(owner),
                "uiptv-alert-dialog"
        );
    }

    private static ButtonType cancelButtonType() {
        return new ButtonType(I18n.tr("autoCancel"), ButtonBar.ButtonData.CANCEL_CLOSE);
    }

    private static void bindButtonDisabled(Map<ButtonType, Button> buttons,
                                           ButtonType buttonType,
                                           javafx.beans.value.ObservableBooleanValue disabled) {
        Button button = buttons.get(buttonType);
        if (button != null) {
            button.disableProperty().bind(disabled);
        }
    }
}
