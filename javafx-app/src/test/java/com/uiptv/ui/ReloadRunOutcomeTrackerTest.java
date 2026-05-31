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

    @Test
    void censoringMessages_areAggregatedPerAccountAndOverall() {
        ReloadRunOutcomeTracker tracker = new ReloadRunOutcomeTracker();

        tracker.recordMessage("acc-1", "Censored Categories 2", "Censored categories: 2");
        tracker.recordMessage("acc-1", "Censored Channels 5", "Censored channels: 5");
        tracker.recordMessage("acc-1", "Censored Categories 1", "Censored categories: 1");
        tracker.recordMessage("acc-2", "Censored Channels 4", "Censored channels: 4");
        tracker.recordMessage("acc-2", "Censored Categories 0", "Censored categories: 0");

        assertEquals(3, tracker.getCensoredCategories("acc-1"));
        assertEquals(5, tracker.getCensoredChannels("acc-1"));
        assertEquals(0, tracker.getCensoredCategories("acc-2"));
        assertEquals(4, tracker.getCensoredChannels("acc-2"));
        assertEquals(3, tracker.getTotalCensoredCategories());
        assertEquals(9, tracker.getTotalCensoredChannels());
    }
}
