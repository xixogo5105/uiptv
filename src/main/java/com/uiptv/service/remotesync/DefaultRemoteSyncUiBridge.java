package com.uiptv.service.remotesync;

import com.uiptv.util.I18n;
import com.uiptv.widget.UIptvAlert;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.Optional;
import java.util.function.Consumer;

public class DefaultRemoteSyncUiBridge implements RemoteSyncApprovalPrompt, RemoteSyncNotifier {
    @Override
    public void requestApproval(RemoteSyncApprovalRequest request, Consumer<Boolean> decisionConsumer) {
        try {
            Platform.runLater(() -> decisionConsumer.accept(showApprovalDialog(request)));
        } catch (IllegalStateException _) {
            decisionConsumer.accept(false);
        }
    }

    @Override
    public void showInfo(String message) {
        runAlert(() -> UIptvAlert.showMessageAlert(message));
    }

    @Override
    public void showError(String message) {
        runAlert(() -> UIptvAlert.showErrorAlert(message));
    }

    private boolean showApprovalDialog(RemoteSyncApprovalRequest request) {
        ButtonType allowButton = new ButtonType(I18n.tr("remoteSyncAllow"), ButtonBar.ButtonData.OK_DONE);
        ButtonType rejectButton = new ButtonType(I18n.tr("remoteSyncReject"), ButtonBar.ButtonData.CANCEL_CLOSE);
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, buildApprovalMessage(request), allowButton, rejectButton);
        alert.setTitle(I18n.tr("remoteSyncApprovalTitle"));
        alert.setHeaderText(I18n.tr("remoteSyncApprovalHeader"));
        Optional<ButtonType> result = alert.showAndWait();
        return result.isPresent() && result.get() == allowButton;
    }

    private String buildApprovalMessage(RemoteSyncApprovalRequest request) {
        String directionKey = request.direction().uploadsToRemote()
                ? "remoteSyncDirectionExportToRemote"
                : "remoteSyncDirectionImportFromRemote";
        String configurationKey = request.options().syncConfiguration()
                ? "remoteSyncOptionYes"
                : "remoteSyncOptionNo";
        String playerPathsKey = request.options().syncExternalPlayerPaths()
                ? "remoteSyncOptionYes"
                : "remoteSyncOptionNo";
        return I18n.tr(
                "remoteSyncApprovalMessage",
                I18n.tr(directionKey),
                request.requesterName(),
                request.requesterAddress(),
                request.verificationCode(),
                I18n.tr(configurationKey),
                I18n.tr(playerPathsKey)
        );
    }

    private void runAlert(Runnable runnable) {
        try {
            Platform.runLater(runnable);
        } catch (IllegalStateException _) {
            // No JavaFX runtime; logging-only environments can ignore UI popups.
        }
    }
}
