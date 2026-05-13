package com.uiptv.service.remotesync;

import com.uiptv.util.AppLog;
import java.util.function.Consumer;

public class DefaultRemoteSyncUiBridge implements RemoteSyncApprovalPrompt, RemoteSyncNotifier {
    @Override
    public void requestApproval(RemoteSyncApprovalRequest request, Consumer<Boolean> decisionConsumer) {
        decisionConsumer.accept(false);
    }

    @Override
    public void showInfo(String message) {
        AppLog.addInfoLog(DefaultRemoteSyncUiBridge.class, message);
    }

    @Override
    public void showError(String message) {
        AppLog.addErrorLog(DefaultRemoteSyncUiBridge.class, message);
    }
}
