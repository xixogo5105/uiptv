package com.uiptv.player;

import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.ReadOnlyObjectWrapper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;

/** Navigation actions for the visible list that launched embedded playback. */
public final class PlayerChannelNavigation {
    public record Queue(List<Runnable> actions, List<String> itemNames, int currentIndex) {
        public Queue {
            actions = List.copyOf(actions);
            itemNames = normalizedNames(itemNames);
            if (itemNames.size() != actions.size()) {
                throw new IllegalArgumentException("itemNames must match queue actions");
            }
            if (currentIndex < 0 || currentIndex >= actions.size()) {
                throw new IllegalArgumentException("currentIndex must refer to a queue action");
            }
        }

        private static List<String> normalizedNames(List<String> itemNames) {
            if (itemNames == null) return List.of();
            return itemNames.stream().map(Queue::normalizedName).toList();
        }

        private static String normalizedName(String name) {
            return name == null ? "" : name.trim();
        }

        public boolean canGoPrevious() {
            return currentIndex > 0;
        }

        public boolean canGoNext() {
            return currentIndex < actions.size() - 1;
        }

        public String previousItemName() {
            return canGoPrevious() ? itemNames.get(currentIndex - 1) : "";
        }

        public String nextItemName() {
            return canGoNext() ? itemNames.get(currentIndex + 1) : "";
        }

        private Queue at(int index) {
            return new Queue(actions, itemNames, index);
        }
    }

    private static final ReadOnlyObjectWrapper<Queue> ACTIVE_QUEUE = new ReadOnlyObjectWrapper<>();
    private static boolean navigationInProgress;

    private PlayerChannelNavigation() {
    }

    public static ReadOnlyObjectProperty<Queue> queueProperty() {
        return ACTIVE_QUEUE.getReadOnlyProperty();
    }

    public static Queue getQueue() {
        return ACTIVE_QUEUE.get();
    }

    public static void setQueue(Queue queue) {
        ACTIVE_QUEUE.set(queue);
    }

    public static void clear() {
        setQueue(null);
    }

    public static boolean isNavigationInProgress() {
        return navigationInProgress;
    }

    public static <T> Queue createQueue(
            List<T> visibleItems,
            T currentItem,
            Predicate<T> isPlayable,
            Consumer<T> play
    ) {
        return createQueue(visibleItems, currentItem, isPlayable, _ -> "", play);
    }

    public static <T> Queue createQueue(
            List<T> visibleItems,
            T currentItem,
            Predicate<T> isPlayable,
            Function<T, String> itemName,
            Consumer<T> play
    ) {
        if (visibleItems == null || visibleItems.isEmpty() || currentItem == null
                || isPlayable == null || itemName == null || play == null) {
            return null;
        }
        List<T> playableItems = visibleItems.stream()
                .filter(item -> item != null && isPlayable.test(item))
                .toList();
        int currentIndex = playableItems.indexOf(currentItem);
        if (playableItems.size() < 2 || currentIndex < 0) {
            return null;
        }
        List<Runnable> actions = new ArrayList<>(playableItems.size());
        List<String> itemNames = new ArrayList<>(playableItems.size());
        for (T item : playableItems) {
            actions.add(() -> play.accept(item));
            itemNames.add(itemName.apply(item));
        }
        return new Queue(actions, itemNames, currentIndex);
    }

    public static boolean previous() {
        return move(-1);
    }

    public static boolean next() {
        return move(1);
    }

    private static boolean move(int offset) {
        Queue queue = ACTIVE_QUEUE.get();
        if (queue == null) {
            return false;
        }
        int nextIndex = queue.currentIndex() + offset;
        if (nextIndex < 0 || nextIndex >= queue.actions().size()) {
            return false;
        }
        ACTIVE_QUEUE.set(queue.at(nextIndex));
        boolean previousNavigationState = navigationInProgress;
        navigationInProgress = true;
        try {
            queue.actions().get(nextIndex).run();
            return true;
        } finally {
            navigationInProgress = previousNavigationState;
        }
    }
}
