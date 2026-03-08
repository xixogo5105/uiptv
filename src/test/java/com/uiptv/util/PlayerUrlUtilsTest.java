package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PlayerUrlUtilsTest {

    @Test
    void extractPlayableUrl_stripsKnownFfmpegPrefixes_andFallsBackToLastToken() {
        assertEquals("https://stream.test/a.m3u8", PlayerUrlUtils.extractPlayableUrl("ffmpeg https://stream.test/a.m3u8"));
        assertEquals("https://stream.test/b.m3u8", PlayerUrlUtils.extractPlayableUrl("ffmpeg+https://stream.test/b.m3u8"));
        assertEquals("https://stream.test/c.m3u8", PlayerUrlUtils.extractPlayableUrl("ffmpeg%20https://stream.test/c.m3u8"));
        assertEquals("https://stream.test/d.m3u8", PlayerUrlUtils.extractPlayableUrl("cmd --flag https://stream.test/d.m3u8"));
    }

    @Test
    void normalizeStreamUrl_rewritesRelativeAndPortalScopedUrls_forStalkerAccounts() {
        Account account = stalkerAccount("http://portal.example:8080/c/");

        assertEquals("http://cdn.example/live/play/1", PlayerUrlUtils.normalizeStreamUrl(account, "https://cdn.example/live/play/1"));
        assertEquals("http://portal.example:8080/stream/path.m3u8", PlayerUrlUtils.normalizeStreamUrl(account, "/stream/path.m3u8"));
        assertEquals("http://portal.example:8080/live/2", PlayerUrlUtils.normalizeStreamUrl(account, "//portal.example:8080/live/2"));
        assertEquals("http://portal.example:9000/live/3", PlayerUrlUtils.normalizeStreamUrl(account, "portal.example:9000/live/3"));
        assertEquals("stream-token", PlayerUrlUtils.normalizeStreamUrl(account, "stream-token"));
    }

    @Test
    void resolveBestChannelCmd_prefersFirstUsableLiveCmd_forStalkerLiveChannels() {
        Account account = stalkerAccount("http://portal.example/c/");
        Channel channel = new Channel();
        channel.setCmd("ffmpeg http://portal.example/live/stream=&token=bad");
        channel.setCmd_1("ffmpeg http://portal.example/live/12345.ts");
        channel.setCmd_2("http://portal.example/live/67890.ts");

        assertEquals("ffmpeg http://portal.example/live/12345.ts", PlayerUrlUtils.resolveBestChannelCmd(account, channel));
    }

    @Test
    void isUsableLiveCmd_rejectsEmptyStreamParameter() {
        assertFalse(PlayerUrlUtils.isUsableLiveCmd("ffmpeg http://portal.example/live/stream=&token=x"));
        assertTrue(PlayerUrlUtils.isUsableLiveCmd("ffmpeg http://portal.example/live/stream=1&token=x"));
    }

    private Account stalkerAccount(String serverPortalUrl) {
        Account account = new Account();
        account.setType(AccountType.STALKER_PORTAL);
        account.setAction(Account.AccountAction.itv);
        account.setServerPortalUrl(serverPortalUrl);
        return account;
    }
}
