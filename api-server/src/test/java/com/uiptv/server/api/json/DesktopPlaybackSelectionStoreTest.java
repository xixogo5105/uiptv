package com.uiptv.server.api.json;

import com.uiptv.model.Channel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;

class DesktopPlaybackSelectionStoreTest {
    @Test
    void resolvesOpaqueSelectionOnlyForItsAccountAndCategory() {
        Channel channel = new Channel();
        channel.setChannelId("provider-channel-id");
        channel.setCmd("https://provider.test/private-stream");

        DesktopPlaybackSelectionStore store = DesktopPlaybackSelectionStore.getInstance();
        String selectionId = store.register("account-db-id", "category-db-id", channel);

        assertFalse(selectionId.isBlank());
        assertEquals(channel, store.resolve(selectionId, "account-db-id", "category-db-id"));
        assertNull(store.resolve(selectionId, "other-account", "category-db-id"));
        assertNull(store.resolve(selectionId, "account-db-id", "other-category"));
    }
}
