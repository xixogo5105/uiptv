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

    @Test
    void commandHelpers_coverCopyTranscodeOriginAndInputReuseNormalization() throws Exception {
        List<String> copy = AbstractFfmpegHlsService.buildCopyHlsCommand(
                "https://media.example:8443/live/stream.ts?token=abc&stable=1",
                "http://127.0.0.1/hls/stream.m3u8",
                false
        );
        assertTrue(copy.contains("-rw_timeout"));
        assertTrue(copy.contains("-reconnect"));
        assertTrue(copy.contains("-user_agent"));
        assertTrue(copy.contains("copy"));
        assertTrue(copy.stream().anyMatch(value -> value.contains("Origin: https://media.example:8443")));

        List<String> transcode = AbstractFfmpegHlsService.buildTranscodeHlsCommand(
                "file:///tmp/input.ts",
                "http://127.0.0.1/hls/stream.m3u8",
                true,
                2500L
        );
        assertTrue(transcode.contains("libx264"));
        assertTrue(transcode.contains("2.500"));
        assertFalse(transcode.contains("-reconnect"));

        assertEquals("https://media.example:8443", invoke("originOf", new Class[]{String.class}, "https://media.example:8443/live/stream.ts"));
        assertEquals("", invoke("originOf", new Class[]{String.class}, "not a uri"));
        assertEquals("http://host/path?stable=1", invoke("normalizeInputForReuse", new Class[]{String.class}, "http://host/path?token=abc&stable=1&expires=99"));
        assertEquals("raw|bad", invoke("normalizeInputForReuse", new Class[]{String.class}, "raw|bad"));
        assertEquals("", invoke("filterStableReuseQuery", new Class[]{String.class}, "token=abc&signature=sig&expires=1"));

        StringBuilder filtered = new StringBuilder("first=1");
        invoke("appendStableReuseParam", new Class[]{StringBuilder.class, String.class}, filtered, "cacheReset=true");
        invoke("appendStableReuseParam", new Class[]{StringBuilder.class, String.class}, filtered, "second=2");
        assertEquals("first=1&second=2", filtered.toString());

        assertEquals("", invoke("extractInputUrl", new Class[]{List.class}, List.of("ffmpeg")));
        assertEquals("http://input", invoke("extractInputUrl", new Class[]{List.class}, List.of("ffmpeg", "-i", "http://input")));
    }

    @Test
    void managedStreamLifecycle_coversProcessStartStopAndMissingPlaylist() throws Exception {
        Method start = AbstractFfmpegHlsService.class.getDeclaredMethod("startManagedHlsStream", List.class);
        start.setAccessible(true);
        Method stop = AbstractFfmpegHlsService.class.getDeclaredMethod("stopManagedHlsStream");
        stop.setAccessible(true);

        try {
            boolean started = (Boolean) start.invoke(FfmpegService.getInstance(), List.of("/bin/sh", "-c", "sleep 1"));
            assertFalse(started);
        } finally {
            stop.invoke(FfmpegService.getInstance());
        }
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = AbstractFfmpegHlsService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
