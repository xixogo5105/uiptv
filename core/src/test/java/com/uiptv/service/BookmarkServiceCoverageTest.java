package com.uiptv.service;

import com.uiptv.model.Bookmark;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BookmarkServiceCoverageTest extends DbBackedTest {

    @Test
    void listenersAndTimestamps_updateOnMutations_andIgnoreFailingListeners() {
        BookmarkService service = BookmarkService.getInstance();
        long beforeTimestamp = service.getLastUpdatedEpochMs();
        long beforeRevision = service.getChangeRevision();
        AtomicLong observedRevision = new AtomicLong();
        AtomicLong observedTimestamp = new AtomicLong();

        BookmarkChangeListener good = (revision, timestamp) -> {
            observedRevision.set(revision);
            observedTimestamp.set(timestamp);
        };
        BookmarkChangeListener bad = (revision, timestamp) -> {
            throw new IllegalStateException("boom");
        };

        service.addChangeListener(good);
        service.addChangeListener(bad);
        service.addChangeListener(null);

        Bookmark bookmark = new Bookmark("listener-acc", "Fav", "ch-1", "One", "cmd://1", "http://portal", "cat-a");
        service.save(bookmark);
        Bookmark saved = service.getBookmark(bookmark);

        assertTrue(service.getChangeRevision() > beforeRevision);
        assertEquals(service.getChangeRevision(), observedRevision.get());
        assertTrue(service.getLastUpdatedEpochMs() >= beforeTimestamp);
        assertEquals(service.getLastUpdatedEpochMs(), observedTimestamp.get());

        service.remove(saved.getDbId());
        long afterRemoveRevision = service.getChangeRevision();
        assertTrue(afterRemoveRevision > beforeRevision);

        service.removeChangeListener(good);
        service.removeChangeListener(bad);
        service.removeChangeListener(null);
    }
}
