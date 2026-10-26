package com.uiptv.player;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerChannelNavigationTest {
    @AfterEach
    void clearQueue() {
        PlayerChannelNavigation.clear();
    }

    @Test
    void navigationUsesOnlyPlayableItemsAndStopsAtVisibleListEdges() {
        List<String> played = new ArrayList<>();
        List<String> visibleItems = List.of("one", "", "two", "three");
        PlayerChannelNavigation.Queue queue = PlayerChannelNavigation.createQueue(
                visibleItems,
                "two",
                item -> !item.isBlank(),
                item -> {
                    assertTrue(PlayerChannelNavigation.isNavigationInProgress());
                    played.add(item);
                }
        );

        assertEquals(1, queue.currentIndex());
        PlayerChannelNavigation.setQueue(queue);
        assertTrue(PlayerChannelNavigation.getQueue().canGoPrevious());
        assertTrue(PlayerChannelNavigation.getQueue().canGoNext());

        assertTrue(PlayerChannelNavigation.previous());
        assertEquals(List.of("one"), played);
        assertFalse(PlayerChannelNavigation.isNavigationInProgress());
        assertEquals(0, PlayerChannelNavigation.getQueue().currentIndex());
        assertFalse(PlayerChannelNavigation.previous());

        assertTrue(PlayerChannelNavigation.next());
        assertEquals("two", played.getLast());
        assertTrue(PlayerChannelNavigation.next());
        assertEquals("three", played.getLast());
        assertFalse(PlayerChannelNavigation.next());
    }

    @Test
    void queueIsUnavailableWhenVisibleListHasNoPreviousOrNextItem() {
        assertNull(PlayerChannelNavigation.createQueue(
                List.of("current", "unplayable"),
                "current",
                item -> !item.equals("unplayable"),
                _ -> { }
        ));
        assertNull(PlayerChannelNavigation.createQueue(
                List.of("first", "second"),
                "outside-filtered-list",
                _ -> true,
                _ -> { }
        ));
    }

    @Test
    void queueExposesTheNamesOfTheAdjacentItems() {
        PlayerChannelNavigation.Queue queue = PlayerChannelNavigation.createQueue(
                List.of("BBC One", "BBC Two", "BBC Three"),
                "BBC Two",
                _ -> true,
                item -> item,
                _ -> { }
        );

        assertEquals("BBC One", queue.previousItemName());
        assertEquals("BBC Three", queue.nextItemName());

        PlayerChannelNavigation.setQueue(queue);
        assertTrue(PlayerChannelNavigation.next());
        assertEquals("BBC Two", PlayerChannelNavigation.getQueue().previousItemName());
        assertEquals("", PlayerChannelNavigation.getQueue().nextItemName());
    }
}
