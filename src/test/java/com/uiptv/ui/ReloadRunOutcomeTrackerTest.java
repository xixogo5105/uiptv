package com.uiptv.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ReloadRunOutcomeTrackerTest {

    @Test
    void handshakeFailure_marksAccountAsCriticalFailure_evenWhenFetchedCountExists() {
        ReloadRunOutcomeTracker tracker = new ReloadRunOutcomeTracker();
        String accountId = "acc-stalker-1";

        tracker.recordMessage(accountId, "Found Channels 120. Found 0 Orphaned channels.", "ITV Channels: 120");
        tracker.recordMessage(accountId, "Handshake failed for: test-account", "Failed: handshake.");

        assertEquals(120, tracker.getFetchedChannels(accountId));
        assertTrue(tracker.hasCriticalFailure(accountId), "Handshake failure must override positive channel count");
    }

    @Test
    void lastResortCollectedMessage_updatesFetchedCount() {
        ReloadRunOutcomeTracker tracker = new ReloadRunOutcomeTracker();
        String accountId = "acc-stalker-2";

        tracker.recordMessage(accountId, "Last-resort fetch succeeded. Collected 42 channels.", "Fallback fetch succeeded: 42 channels.");

        assertEquals(42, tracker.getFetchedChannels(accountId));
        assertFalse(tracker.hasCriticalFailure(accountId));
    }
}
