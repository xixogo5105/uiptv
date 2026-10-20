package com.uiptv.service;

import static org.mockito.Mockito.mockStatic;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import com.uiptv.util.ServerUrlUtil;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PlayerServiceTest {

    @Test
    void mergeMissingQueryParams_fillsMissingStreamAndTokenFromOriginal() {
        String resolved = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=&extension=ts&play_token=";
        String original = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=1470604&extension=ts&play_token=abc";
        String merged = StalkerPortalPlayerService.mergeMissingQueryParams(resolved, original);

        assertTrue(merged.contains("stream=1470604"));
        assertTrue(merged.contains("play_token=abc"));
    }

    @Test
    void mergeMissingQueryParams_keepsResolvedValuesWhenPresent() {
        String resolved = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=999&extension=ts&play_token=xyz";
        String original = "ffmpeg http://example.com/play/live.php?mac=00:11:22:33:44:55&stream=1470604&extension=ts&play_token=abc";
        String merged = StalkerPortalPlayerService.mergeMissingQueryParams(resolved, original);

        assertTrue(merged.contains("stream=999"));
        assertTrue(merged.contains("play_token=xyz"));
    }

    @Test
    void mergeMissingQueryParams_returnsResolvedWhenNoQuery() {
        String resolved = "ffmpeg http://example.com/play/live.php";
        String original = "ffmpeg http://example.com/play/live.php?stream=1470604";
        String merged = StalkerPortalPlayerService.mergeMissingQueryParams(resolved, original);

        assertEquals(resolved, merged);
    }

    @Test
    void liveCandidateResolution_skipsTimeoutsAndSelectsUsableCandidate() {
        List<String> candidates = List.of("first", "second");
        AtomicInteger calls = new AtomicInteger();

        String resolved = StalkerPortalPlayerService.resolveLiveCandidates(candidates, cmd -> {
            calls.incrementAndGet();
            if ("first".equals(cmd)) {
                throw new StalkerPortalPlayerService.PlaybackResolutionTimeoutException();
            }
            return "http://stream.test/live?stream=22";
        });

        assertEquals("http://stream.test/live?stream=22", resolved);
        assertEquals(2, calls.get());
    }

    @Test
    void liveCandidateResolution_recoversMissingStreamFromAlternateCandidate() {
        String resolved = StalkerPortalPlayerService.resolveLiveCandidates(
                List.of("ffmpeg http://provider.test/live?stream=22", "http://provider.test/live?stream=33"),
                ignored -> "ffmpeg http://provider.test/live?stream=&token=abc");

        assertTrue(resolved.contains("stream=22"));
        assertTrue(resolved.contains("token=abc"));
    }

    @Test
    void liveCandidateResolution_reportsTimeoutWhenEveryCandidateTimesOut() {
        assertThrows(StalkerPortalPlayerService.PlaybackResolutionTimeoutException.class,
                () -> StalkerPortalPlayerService.resolveLiveCandidates(
                        List.of("one", "two"),
                        ignored -> { throw new StalkerPortalPlayerService.PlaybackResolutionTimeoutException(); }));
    }

    @Test
    void liveCandidateResolution_refreshesTokenOnceBeforeFallback() {
        AtomicInteger calls = new AtomicInteger();
        AtomicInteger refreshes = new AtomicInteger();

        String resolved = StalkerPortalPlayerService.resolveLiveCandidatesWithTokenRefresh(
                List.of("cmd"),
                ignored -> calls.incrementAndGet() == 2 ? "http://stream.test/live?stream=1" : null,
                refreshes::incrementAndGet,
                "fallback",
                "channel");

        assertEquals("http://stream.test/live?stream=1", resolved);
        assertEquals(1, refreshes.get());
        assertEquals(2, calls.get());
    }

    @Test
    void liveCandidateResolution_usesFallbackAndOriginalChannelCommand() {
        assertEquals("fallback", StalkerPortalPlayerService.resolveLiveCandidatesWithTokenRefresh(
                List.of("cmd"), ignored -> null, () -> { }, "fallback", "channel"));
        assertEquals("channel", StalkerPortalPlayerService.resolveLiveCandidatesWithTokenRefresh(
                List.of("cmd"), ignored -> null, () -> { }, "", "channel"));
    }

    @Test
    void liveCandidateResolution_honoursInterruptBeforeAndAfterRefresh() {
        Thread.currentThread().interrupt();
        assertThrows(StalkerPortalPlayerService.PlaybackResolutionCancelledException.class,
                () -> StalkerPortalPlayerService.resolveLiveCandidates(List.of("cmd"), ignored -> "url"));
        assertTrue(Thread.interrupted());

        Thread.currentThread().interrupt();
        assertThrows(StalkerPortalPlayerService.PlaybackResolutionCancelledException.class,
                () -> StalkerPortalPlayerService.resolveLiveCandidatesWithTokenRefresh(
                        List.of("cmd"), ignored -> null, () -> { }, "fallback", "channel"));
        assertTrue(Thread.interrupted());
    }

    @Test
    void stalkerPrivateUrlHelpers_coverJsonPlaceholderAndQueryBranches() throws Exception {
        StalkerPortalPlayerService service = new StalkerPortalPlayerService();
        assertEquals("http://stream.test/one", invoke(service, "parseUrl", String.class, "{\"js\":{\"cmd\":\"http://stream.test/one\"}}"));
        assertEquals("http://stream.test/two", invoke(service, "parseUrl", String.class, "{\"js\":{\"url\":\"http://stream.test/two\"}}"));
        assertEquals("http://stream.test/three", invoke(service, "parseUrl", String.class, "{\"cmd\":\"http://stream.test/three\"}"));
        assertNull(invoke(service, "parseUrl", String.class, "{\"js\":{\"cmd\":\" \"}}"));
        assertNull(invoke(service, "parseUrl", String.class, "not-json"));

        assertEquals("http://stream.test/live?stream=123&x=1", invoke(service, "normalizeSeriesStreamPlaceholder", String.class,
                "http://stream.test/live?stream=.&x=1", String.class, "123:2"));
        assertEquals("http://stream.test/live?stream=123", invoke(service, "normalizeSeriesStreamPlaceholder", String.class,
                "http://stream.test/live?stream=.", String.class, "123:2"));
        assertEquals("http://stream.test/live?stream=123&x=1", invoke(service, "normalizeSeriesStreamPlaceholder", String.class,
                "http://stream.test/live?stream=&x=1", String.class, "123:2"));
        assertEquals("http://stream.test/live?stream=123", invoke(service, "normalizeSeriesStreamPlaceholder", String.class,
                "http://stream.test/live?stream=", String.class, "123:2"));
        assertEquals("unchanged", invoke(service, "normalizeSeriesStreamPlaceholder", String.class, "unchanged", String.class, "not-a-stream"));

        assertEquals("123", invoke(service, "extractStreamToken", String.class, "123:2"));
        assertEquals("", invoke(service, "extractStreamToken", String.class, "abc"));
        assertEquals("", invoke(service, "extractStreamToken", String.class, " "));

        assertTrue((Boolean) invoke(service, "isUsableResolvedLiveUrl", String.class, "ffmpeg http://stream.test/live?stream=1"));
        assertFalse((Boolean) invoke(service, "isUsableResolvedLiveUrl", String.class, "http://stream.test/live?stream=&x=1"));
        assertFalse((Boolean) invoke(service, "isUsableResolvedLiveUrl", String.class, " "));
    }

    @Test
    void stalkerPrivateUrlHelpers_normalizeRelativeAndMalformedBases() throws Exception {
        StalkerPortalPlayerService service = new StalkerPortalPlayerService();
        assertEquals("http://provider.test/path/stream.m3u8", invoke(service, "normalizeResolvedBase", String.class,
                "stream.m3u8", String.class, "http://provider.test/path/live.php"));
        assertEquals("https://cdn.test/stream.m3u8", invoke(service, "normalizeResolvedBase", String.class,
                "https://cdn.test/stream.m3u8", String.class, "http://provider.test/live.php"));
        assertEquals("stream.m3u8", invoke(service, "normalizeResolvedBase", String.class,
                "stream.m3u8", String.class, "relative-base"));
        assertEquals("bad url", invoke(service, "normalizeResolvedBase", String.class,
                "bad url", String.class, "http://provider.test/live.php"));
        assertEquals("", invoke(service, "extractCmdPrefix", String.class, " "));
        assertEquals("ffmpeg", invoke(service, "extractCmdPrefix", String.class, "ffmpeg http://stream.test"));
        assertEquals("http://stream.test", invoke(service, "extractCmdUrl", String.class, "ffmpeg http://stream.test"));
        assertEquals("", invoke(service, "extractCmdUrl", String.class, null));
    }

    @Test
    void stalkerPrivateHelpers_buildCreateLinkParametersForLiveAndSeries() throws Exception {
        StalkerPortalPlayerService service = new StalkerPortalPlayerService();
        Account account = new Account("stalker", "u", "p", "http://provider.test", "mac", null, null, null, null,
                null, AccountType.STALKER_PORTAL, null, null, false);
        account.setAction(Account.AccountAction.series);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> params = (java.util.Map<String, String>) invoke(service, "getParams",
                Account.class, account, String.class, "cmd", String.class, "7:2");
        assertEquals("vod", params.get("type"));
        assertEquals("7:2", params.get("series"));
        assertEquals("create_link", params.get("action"));
        assertTrue(params.get("JsHttpRequest").endsWith("-xml"));

        account.setAction(Account.AccountAction.itv);
        @SuppressWarnings("unchecked")
        java.util.Map<String, String> liveParams = (java.util.Map<String, String>) invoke(service, "getParams",
                Account.class, account, String.class, "cmd", String.class, "ignored");
        assertEquals("itv", liveParams.get("type"));
        assertEquals("", liveParams.get("series"));
    }

    @Test
    void stalkerPrivateHelpers_deduplicateLiveCommandsAndRescueCandidates() throws Exception {
        StalkerPortalPlayerService service = new StalkerPortalPlayerService();
        Channel channel = new Channel();
        channel.setCmd("one");
        channel.setCmd_1("one");
        channel.setCmd_2(" ");
        channel.setCmd_3("two");
        @SuppressWarnings("unchecked")
        List<String> candidates = (List<String>) invoke(service, "getLiveCmdCandidates", Channel.class, channel);
        assertEquals(List.of("one", "two"), candidates);
        assertEquals("http://stream.test/live?stream=7", invoke(service, "rescueResolvedLiveUrlWithCandidates",
                String.class, "http://stream.test/live?stream=", List.class, List.of("http://stream.test/live?stream=7")));
        assertEquals("url", invoke(service, "rescueResolvedLiveUrlWithCandidates",
                String.class, "url", List.class, List.of()));
    }

    private static Object invoke(Object target, String methodName, Object... signatureAndArguments) throws Exception {
        int argumentCount = signatureAndArguments.length / 2;
        Class<?>[] parameterTypes = new Class<?>[argumentCount];
        Object[] arguments = new Object[argumentCount];
        for (int i = 0; i < argumentCount; i++) {
            parameterTypes[i] = (Class<?>) signatureAndArguments[i * 2];
            arguments[i] = signatureAndArguments[i * 2 + 1];
        }
        Method method = StalkerPortalPlayerService.class.getDeclaredMethod(methodName, parameterTypes);
        method.setAccessible(true);
        return method.invoke(target, arguments);
    }

    @Test
    void sanitizeAndEncodeUrl_encodesInvalidCharacters() {
        PlayerService service = PlayerService.getInstance();

        // Case 1: URL with unencoded brackets
        String urlWithBrackets = "http://host/path?ads.deviceid=[DEVICE_ID]&coppa=0";
        String expectedEncodedUrl = "http://host/path?ads.deviceid=%5BDEVICE_ID%5D&coppa=0";
        assertEquals(expectedEncodedUrl, service.sanitizeAndEncodeUrl(urlWithBrackets));

        // Case 2: URL with spaces and other characters
        // Note: 'hello world' becomes 'hello+world'
        // 'a@b' becomes 'a%40b' (using @ to verify encoding without +/space ambiguity)
        String urlWithSpaces = "http://host/path?q=hello world&filter=a@b";
        String expectedUrlWithSpaces = "http://host/path?q=hello+world&filter=a%40b";
        assertEquals(expectedUrlWithSpaces, service.sanitizeAndEncodeUrl(urlWithSpaces));

        // Case 3: URL that is already partially encoded
        String partiallyEncodedUrl = "http://host/path?data=%5B1,2%5D&q=test";
        String expectedPartiallyEncodedUrl = "http://host/path?data=%5B1%2C2%5D&q=test";
        assertEquals(expectedPartiallyEncodedUrl, service.sanitizeAndEncodeUrl(partiallyEncodedUrl));

        // Case 4: The full user-provided URL
        String userUrl = "https://cdn-uw2-prod.tsv2.amagi.tv/linear/amg01605-ndtvconvergence-ndtvindia-lgin/playlist.m3u8?ads.deviceid=[DEVICE_ID]&ads.ifa=[IFA]&coppa=0";
        String expectedUserUrl = "https://cdn-uw2-prod.tsv2.amagi.tv/linear/amg01605-ndtvconvergence-ndtvindia-lgin/playlist.m3u8?ads.deviceid=%5BDEVICE_ID%5D&ads.ifa=%5BIFA%5D&coppa=0";
        assertEquals(expectedUserUrl, service.sanitizeAndEncodeUrl(userUrl));

        // Case 5: No query string
        String noQueryUrl = "http://host/path";
        assertEquals(noQueryUrl, service.sanitizeAndEncodeUrl(noQueryUrl));
    }

    @Test
    void buildDrmBrowserPlaybackUrl_usesLoopbackOriginForBrowserEme() {
        PlayerService service = PlayerService.getInstance();
        Account account = new Account(
                "M3U",
                "",
                "",
                "http://playlist.test",
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.M3U8_LOCAL,
                null,
                "playlist.m3u",
                false
        );
        account.setDbId("1439");
        Channel channel = new Channel();
        channel.setChannelId("Fancode 24/7 CH1");
        channel.setName("Fancode 1");
        channel.setCmd("https://stream.test/cenc.mpd");
        channel.setDrmType("org.w3.clearkey");
        channel.setClearKeysJson("{\"kid\":\"key\"}");

        try (MockedStatic<ServerUrlUtil> serverUrlUtil = mockStatic(ServerUrlUtil.class)) {
            serverUrlUtil.when(ServerUrlUtil::getLoopbackServerUrl).thenReturn("http://127.0.0.1:9090");

            String url = service.buildDrmBrowserPlaybackUrl(account, channel, "Sports", "itv");

            assertTrue(url.startsWith("http://127.0.0.1:9090/index.html?launch="));
            assertTrue(url.contains("&playerOnly=1"));
            assertTrue(url.contains("&v=" + PlayerService.WEB_PLAYER_VERSION));
        }
    }
}
