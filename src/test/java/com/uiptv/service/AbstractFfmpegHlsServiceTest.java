package com.uiptv.service;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AbstractFfmpegHlsServiceTest {

    @Test
    void privateCommandBuilders_coverLiveAndVodBranches() throws Exception {
        @SuppressWarnings("unchecked")
        List<String> prefix = (List<String>) invoke("buildHlsCommandPrefix",
                new Class[]{String.class, boolean.class, long.class},
                "http://example/live.ts", true, 1500L);
        assertTrue(prefix.contains("ffmpeg"));
        assertTrue(prefix.contains("+genpts"));
        assertTrue(prefix.contains("-ss"));
        assertTrue(prefix.contains("1.500"));

        List<String> liveArgs = new ArrayList<>();
        invoke("addHlsOutputArgs", new Class[]{List.class, String.class, boolean.class, boolean.class}, liveArgs, "http://127.0.0.1/hls/stream.m3u8", false, false);
        assertTrue(liveArgs.contains("delete_segments"));
        assertFalse(liveArgs.contains("vod"));

        List<String> transcodeArgs = new ArrayList<>();
        invoke("addHlsOutputArgs", new Class[]{List.class, String.class, boolean.class, boolean.class}, transcodeArgs, "http://127.0.0.1/hls/stream.m3u8", false, true);
        assertTrue(transcodeArgs.contains("delete_segments+independent_segments"));

        List<String> vodArgs = new ArrayList<>();
        invoke("addHlsOutputArgs", new Class[]{List.class, String.class, boolean.class, boolean.class}, vodArgs, "http://127.0.0.1/hls/stream.m3u8", true, true);
        assertTrue(vodArgs.contains("event"));
        assertTrue(vodArgs.stream().anyMatch(token -> token.contains("independent_segments")));
    }

    @Test
    void managedPlaybackUrl_usesExpectedPath() throws Exception {
        Method method = AbstractFfmpegHlsService.class.getDeclaredMethod("getLocalHlsPlaybackUrl");
        method.setAccessible(true);
        String url = (String) method.invoke(FfmpegService.getInstance());
        assertTrue(url.endsWith("/hls/stream.m3u8"));
        assertEquals(url, method.invoke(LitePlayerFfmpegService.getInstance()));
    }

    @Test
    void idleViewerHelpers_stopOnlyAfterThresholdExpires() throws Exception {
        long idleFromClient = (Long) invoke("calculateViewerIdleAgeMillis",
                new Class[]{long.class, long.class, long.class},
                10_000L, 1_000L, 9_000L);
        assertEquals(1_000L, idleFromClient);

        long idleFromProcessStart = (Long) invoke("calculateViewerIdleAgeMillis",
                new Class[]{long.class, long.class, long.class},
                10_000L, 4_000L, 0L);
        assertEquals(6_000L, idleFromProcessStart);

        assertFalse((Boolean) invoke("shouldStopForViewerIdle",
                new Class[]{long.class, long.class},
                29_999L, 30_000L));
        assertTrue((Boolean) invoke("shouldStopForViewerIdle",
                new Class[]{long.class, long.class},
                30_000L, 30_000L));
        assertFalse((Boolean) invoke("shouldStopForViewerIdle",
                new Class[]{long.class, long.class},
                30_000L, 0L));
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = AbstractFfmpegHlsService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
