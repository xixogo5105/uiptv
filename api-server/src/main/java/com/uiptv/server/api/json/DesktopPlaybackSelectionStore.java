package com.uiptv.server.api.json;

import com.uiptv.model.Channel;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

final class DesktopPlaybackSelectionStore {
    private static final int MAX_SELECTIONS = 2_000;
    private static final long TTL_MILLIS = 15 * 60 * 1_000L;
    private static final DesktopPlaybackSelectionStore INSTANCE = new DesktopPlaybackSelectionStore();

    private final LinkedHashMap<String, Selection> selections = new LinkedHashMap<>();

    static DesktopPlaybackSelectionStore getInstance() {
        return INSTANCE;
    }

    synchronized String register(String accountId, String categoryId, Channel channel) {
        purgeExpired();
        while (selections.size() >= MAX_SELECTIONS) {
            Iterator<String> oldest = selections.keySet().iterator();
            oldest.next();
            oldest.remove();
        }
        String id = UUID.randomUUID().toString();
        selections.put(id, new Selection(accountId, categoryId, channel, System.currentTimeMillis() + TTL_MILLIS));
        return id;
    }

    synchronized Channel resolve(String id, String accountId, String categoryId) {
        purgeExpired();
        Selection selection = selections.get(id);
        if (selection == null
                || !selection.accountId().equals(accountId)
                || !selection.categoryId().equals(categoryId)) {
            return null;
        }
        return selection.channel();
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        selections.entrySet().removeIf(entry -> entry.getValue().expiresAt() <= now);
    }

    private record Selection(String accountId, String categoryId, Channel channel, long expiresAt) {
    }
}
