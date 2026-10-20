package com.uiptv.application;

import com.uiptv.db.EpgCacheDb;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.jupiter.api.Assertions.*;

class EpgChangeNotificationsTest extends DbBackedTest {
    @Test
    void committedSourceWritesAdvanceRevisionAndReplayBoundedTypedEvents() {
        long before = EpgChangeNotifications.snapshot().revision();
        var received = new CopyOnWriteArrayList<EpgChangeNotifications.Notification>();
        try (var _ = EpgChangeNotifications.subscribe(before, received::add)) {
            long now = System.currentTimeMillis();
            new EpgCacheDb().replaceSource("test-source",
                    java.util.Map.of("guide", List.of(new EpgProgramme("Title", "", now, now + 3_600_000))),
                    java.util.Map.of(), java.util.Map.of(), now, now + 7_200_000);
        }

        assertEquals(before + 1, EpgChangeNotifications.snapshot().revision());
        assertEquals(1, received.size());
        var event = received.getFirst();
        assertEquals(EpgChangeNotifications.Kind.SOURCE_IMPORT, event.kind());
        assertEquals(java.util.Set.of("test-source"), event.sourceIds());
        assertEquals(java.util.Set.of("guide"), event.guideIds());
        assertTrue(EpgChangeNotifications.replay(before, event.databaseGeneration()).stream()
                .anyMatch(replayed -> replayed.revision() == event.revision()));
    }

    @Test
    void accountScopedMappingDoesNotClaimToInvalidateOtherAccounts() {
        long now = System.currentTimeMillis();
        var identity = new EpgCacheDb.Identity("account-a", "provider-a", "signature");
        new EpgCacheDb().replaceSource("source", java.util.Map.of("guide", List.of(
                new EpgProgramme("Title", "", now, now + 3_600_000))), java.util.Map.of(), java.util.Map.of(), now, now + 7_200_000);
        var received = new CopyOnWriteArrayList<EpgChangeNotifications.Notification>();
        try (var _ = EpgChangeNotifications.subscribe(EpgChangeNotifications.snapshot().revision(), received::add)) {
            new EpgCacheDb().mapChannels(List.of(new EpgCacheDb.Match(identity, "guide", "", "", true,
                    List.of("source"), "")), now);
        }
        var event = received.getLast();
        assertEquals(EpgChangeNotifications.Kind.MAPPING, event.kind());
        assertTrue(event.affects("account-a", "provider-a"));
        assertFalse(event.affects("account-b", "provider-b"));
    }

    @Test
    void restorationPublishesResyncEventAndGenerationMismatchRequestsResync() {
        EpgChangeNotifications.Snapshot before = EpgChangeNotifications.snapshot();
        var received = new CopyOnWriteArrayList<EpgChangeNotifications.Notification>();
        try (var subscription = EpgChangeNotifications.subscribe(before.revision(), received::add)) {
            EpgChangeNotifications.databaseRestored();
        }

        assertEquals(1, received.size());
        assertEquals(EpgChangeNotifications.Kind.DATABASE_RESTORED, received.getFirst().kind());
        assertEquals(EpgChangeNotifications.Kind.DATABASE_RESTORED,
                EpgChangeNotifications.replay(before.revision(), before.databaseGeneration()).getFirst().kind());
    }

    @Test
    void publishIsolatesListenerFailuresAndClosedSubscriptionsStopReceivingEvents() {
        EpgChangeNotifications.Snapshot snapshot = EpgChangeNotifications.snapshot();
        var received = new CopyOnWriteArrayList<EpgChangeNotifications.Notification>();
        EpgChangeNotifications.Subscription throwing = EpgChangeNotifications.subscribe(snapshot.revision(), _ -> {
            throw new IllegalStateException("subscriber failed");
        });
        EpgChangeNotifications.Subscription recording = EpgChangeNotifications.subscribe(snapshot.revision(), received::add);
        EpgChangeNotifications.Notification notification = new EpgChangeNotifications.Notification(
                snapshot.revision() + 1, snapshot.databaseGeneration(), EpgChangeNotifications.Kind.SETTINGS,
                Set.of(), Set.of(), Set.of());
        EpgChangeNotifications.publish(notification);
        assertEquals(List.of(notification), received);

        recording.close();
        throwing.close();
        EpgChangeNotifications.publish(new EpgChangeNotifications.Notification(
                notification.revision() + 1, notification.databaseGeneration(), EpgChangeNotifications.Kind.SETTINGS,
                Set.of(), Set.of(), Set.of()));
        assertEquals(1, received.size());
        assertTrue(notification.affects("any-account", "any-provider"));
    }
}
