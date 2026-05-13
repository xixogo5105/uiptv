package com.uiptv.service.remotesync;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertFalse;

class DefaultRemoteSyncUiBridgeTest {

    @Test
    void requestApprovalRejectsByDefaultAndLoggingMethodsDoNotThrow() {
        DefaultRemoteSyncUiBridge bridge = new DefaultRemoteSyncUiBridge();
        AtomicReference<Boolean> decision = new AtomicReference<>();

        bridge.requestApproval(null, decision::set);
        bridge.showInfo("info");
        bridge.showError("error");

        assertFalse(decision.get());
    }
}
