package com.uiptv.ui;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlaybackUIServiceTest {

    @Test
    void playbackRequest_defaultsAreApplied() throws Exception {
        PlaybackUIService.PlaybackRequest request = new PlaybackUIService.PlaybackRequest(sampleAccount(), sampleChannel(), null);

        assertEquals("", readStringField(request, "categoryId"));
        assertEquals("", readStringField(request, "channelId"));
        assertEquals("", readStringField(request, "seriesId"));
        assertEquals("", readStringField(request, "seriesCategoryId"));
        assertTrue(readBooleanField(request, "allowDrmBrowserFallback"));
        assertEquals("Playback failed: ", readStringField(request, "errorPrefix"));
    }

    @Test
    void playbackRequest_builderMethodsOverrideDefaults() throws Exception {
        PlaybackUIService.PlaybackRequest request = new PlaybackUIService.PlaybackRequest(sampleAccount(), sampleChannel(), "embedded")
                .categoryId("cat-7")
                .channelId("ep-3")
                .series("episode-3", "series-12")
                .allowDrmBrowserFallback(false)
                .errorPrefix("Error playing episode: ");

        assertEquals("cat-7", readStringField(request, "categoryId"));
        assertEquals("ep-3", readStringField(request, "channelId"));
        assertEquals("episode-3", readStringField(request, "seriesId"));
        assertEquals("series-12", readStringField(request, "seriesCategoryId"));
        assertFalse(readBooleanField(request, "allowDrmBrowserFallback"));
        assertEquals("Error playing episode: ", readStringField(request, "errorPrefix"));
    }

    private String readStringField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        Object value = field.get(target);
        return value == null ? "" : value.toString();
    }

    private boolean readBooleanField(Object target, String name) throws Exception {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        return field.getBoolean(target);
    }

    private Account sampleAccount() {
        Account account = new Account(
                "test-account",
                "user",
                "pass",
                "http://127.0.0.1/mock",
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.XTREME_API,
                null,
                "http://127.0.0.1/mock",
                false
        );
        account.setAction(Account.AccountAction.series);
        return account;
    }

    private Channel sampleChannel() {
        Channel channel = new Channel();
        channel.setChannelId("episode-1");
        channel.setName("Episode 1");
        channel.setCmd("http://127.0.0.1/media/episode-1.m3u8");
        return channel;
    }
}
