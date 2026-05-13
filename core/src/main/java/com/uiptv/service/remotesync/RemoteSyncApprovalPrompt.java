package com.uiptv.service.remotesync;

import java.util.function.Consumer;

@FunctionalInterface
public interface RemoteSyncApprovalPrompt {
    void requestApproval(RemoteSyncApprovalRequest request, Consumer<Boolean> decisionConsumer);
}
