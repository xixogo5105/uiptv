package com.uiptv.ui;

import com.uiptv.application.EpgRefreshJobService;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpgRefreshCompletionTest {
    @Test
    void cardsRefreshOnlyAfterSuccessfulNonCancelledGuidePublication() {
        var staged = snapshot("RUNNING", "Staged", source("one", "Staged"));
        var noMatches = snapshot("COMPLETED_WITH_WARNINGS", "Warnings", source("one", "No matches"));
        var cancelledAfterEarlierPublication = snapshot("CANCELLED", "Cancelled",
                source("one", "Completed"), source("two", "Pending"));
        var completedWithWarnings = snapshot("COMPLETED_WITH_WARNINGS", "Warnings",
                source("one", "Completed"), source("two", "Failed"));
        var completed = snapshot("COMPLETED", "Completed", source("one", "Completed"));

        assertFalse(EpgRefreshProgressDialog.hasPublishedGuide(staged));
        assertFalse(EpgRefreshProgressDialog.hasPublishedGuide(noMatches));
        assertFalse(EpgRefreshProgressDialog.hasPublishedGuide(cancelledAfterEarlierPublication));
        assertTrue(EpgRefreshProgressDialog.hasPublishedGuide(completedWithWarnings));
        assertTrue(EpgRefreshProgressDialog.hasPublishedGuide(completed));
    }

    private static EpgRefreshJobService.SourceProgress source(String id, String status) {
        return new EpgRefreshJobService.SourceProgress(id, id, id + ".xml.gz", status, "", 10, 1);
    }

    private static EpgRefreshJobService.Snapshot snapshot(String status, String stage,
                                                           EpgRefreshJobService.SourceProgress... sources) {
        return new EpgRefreshJobService.Snapshot("job", status, stage, 1, sources.length,
                "", "", List.of(sources), 0, 0, "", 0, 0);
    }
}
