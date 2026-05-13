package com.uiptv.service;

import com.uiptv.model.Channel;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class StalkerPortalPlayerServiceTest {

    private final StalkerPortalPlayerService service = new StalkerPortalPlayerService();

    @Test
    void helperMethods_mergeAndNormalizeCreateLinkResults() throws Exception {
        assertEquals("ffmpeg http://origin/live.php?stream=1001&play_token=abc&extension=ts",
                StalkerPortalPlayerService.mergeMissingQueryParams(
                        "ffmpeg http://origin/live.php?stream=&play_token=&extension=ts",
                        "ffmpeg http://origin/live.php?stream=1001&play_token=abc"
                ));

        assertEquals("http://origin/path/live.php",
                invokeStatic("normalizeResolvedBase", new Class[]{String.class, String.class}, "live.php", "http://origin/path/file.php").toString());
        assertEquals("1001", invoke("extractStreamToken", new Class[]{String.class}, "1001:extra"));
        assertEquals("ffmpeg http://x/live.php?stream=77", invoke("normalizeSeriesStreamPlaceholder", new Class[]{String.class, String.class}, "ffmpeg http://x/live.php?stream=.", "77:1"));
        assertEquals("", invoke("normalizeSeriesStreamPlaceholder", new Class[]{String.class, String.class}, "", "77"));
    }

    @Test
    void parseAndQueryHelpers_coverFallbackPaths() throws Exception {
        assertEquals("ffmpeg http://example/live.ts",
                invoke("parseUrl", new Class[]{String.class}, "{\"js\":{\"cmd\":\"ffmpeg http://example/live.ts\"}}"));
        assertEquals("http://example/live.ts",
                invoke("parseUrl", new Class[]{String.class}, "{\"js\":{\"url\":\"http://example/live.ts\"}}"));
        assertEquals("ffmpeg http://example/live.ts",
                invoke("parseUrl", new Class[]{String.class}, "{\"cmd\":\"ffmpeg http://example/live.ts\"}"));
        assertNull(invoke("parseUrl", new Class[]{String.class}, "bad json"));

        @SuppressWarnings("unchecked")
        Map<String, String> params = (Map<String, String>) invokeStatic("parseQueryParams", new Class[]{String.class}, "a=1&b=hello%20world&flag=");
        assertEquals("1", params.get("a"));
        assertEquals("hello world", params.get("b"));
        assertEquals("", params.get("flag"));
        assertTrue(invokeStatic("toQueryString", new Class[]{Map.class}, params).toString().contains("hello+world"));
    }

    @Test
    void liveCandidateHelpers_chooseUsableUrls() throws Exception {
        Channel channel = new Channel();
        channel.setCmd("ffmpeg http://origin/live.php?stream=1001&play_token=abc");
        channel.setCmd_1("ffmpeg http://origin/live.php?stream=1001&play_token=abc");
        channel.setCmd_2("ffmpeg http://origin/live.php?stream=2002&play_token=def");

        @SuppressWarnings("unchecked")
        List<String> candidates = (List<String>) invokeStatic("getLiveCmdCandidates", new Class[]{Channel.class}, channel);
        assertEquals(2, candidates.size());
        assertFalse((Boolean) invokeStatic("isUsableResolvedLiveUrl", new Class[]{String.class}, "ffmpeg http://origin/live.php?stream=&play_token="));
        assertTrue((Boolean) invokeStatic("isUsableResolvedLiveUrl", new Class[]{String.class}, "ffmpeg http://origin/live.php?stream=1001&play_token=abc"));
        assertTrue(invokeStatic("rescueResolvedLiveUrlWithCandidates", new Class[]{String.class, List.class}, "ffmpeg http://origin/live.php?stream=&play_token=", candidates).toString().contains("stream=1001"));
        assertEquals("http://origin/live.php?stream=1001&play_token=abc", invokeStatic("extractCmdUrl", new Class[]{String.class}, "ffmpeg http://origin/live.php?stream=1001&play_token=abc"));
        assertEquals("ffmpeg", invokeStatic("extractCmdPrefix", new Class[]{String.class}, "ffmpeg http://origin/live.php"));
    }

    private Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = StalkerPortalPlayerService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(service, args);
    }

    private static Object invokeStatic(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = StalkerPortalPlayerService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
