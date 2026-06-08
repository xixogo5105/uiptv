package com.uiptv.ui;

import com.uiptv.service.remotesync.RemoteSyncApprovalPrompt;
import com.uiptv.service.remotesync.RemoteSyncApprovalRequest;
import com.uiptv.service.remotesync.RemoteSyncNotifier;
import com.uiptv.util.I18n;
import com.uiptv.widget.ThemedDialogSupport;
import com.uiptv.widget.UIptvAlert;
import javafx.application.Platform;
import javafx.scene.control.Alert;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.Optional;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

public class FxRemoteSyncUiBridge implements RemoteSyncApprovalPrompt, RemoteSyncNotifier {
    private final ExecutorService approvalDecisionExecutor = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "uiptv-remote-sync-approval");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void requestApproval(RemoteSyncApprovalRequest request, Consumer<Boolean> decisionConsumer) {
        try {
            Platform.runLater(() -> {
                boolean approved = showApprovalDialog(request);
                Platform.runLater(() -> approvalDecisionExecutor.execute(() -> decisionConsumer.accept(approved)));
            });
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
        javafx.stage.Window ownerWindow = ThemedDialogSupport.activeOwnerWindow();
        ThemedDialogSupport.prepare(alert, ownerWindow, "uiptv-alert-dialog");
        Optional<ButtonType> result = ThemedDialogSupport.showAndWait(alert, ownerWindow);
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
