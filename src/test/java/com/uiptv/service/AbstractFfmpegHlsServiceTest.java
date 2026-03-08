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
        List<String> prefix = (List<String>) invoke("buildHlsCommandPrefix", new Class[]{String.class, boolean.class}, "http://example/live.ts", true);
        assertTrue(prefix.contains("ffmpeg"));
        assertTrue(prefix.contains("+genpts"));

        List<String> liveArgs = new ArrayList<>();
        invoke("addHlsOutputArgs", new Class[]{List.class, String.class, boolean.class, boolean.class}, liveArgs, "http://127.0.0.1/hls/stream.m3u8", false, false);
        assertTrue(liveArgs.contains("delete_segments"));
        assertFalse(liveArgs.contains("vod"));

        List<String> transcodeArgs = new ArrayList<>();
        invoke("addHlsOutputArgs", new Class[]{List.class, String.class, boolean.class, boolean.class}, transcodeArgs, "http://127.0.0.1/hls/stream.m3u8", false, true);
        assertTrue(transcodeArgs.contains("delete_segments+independent_segments"));

        List<String> vodArgs = new ArrayList<>();
        invoke("addHlsOutputArgs", new Class[]{List.class, String.class, boolean.class, boolean.class}, vodArgs, "http://127.0.0.1/hls/stream.m3u8", true, true);
        assertTrue(vodArgs.contains("vod"));
        assertTrue(vodArgs.contains("independent_segments"));
    }

    @Test
    void managedPlaybackUrl_usesExpectedPath() throws Exception {
        Method method = AbstractFfmpegHlsService.class.getDeclaredMethod("getLocalHlsPlaybackUrl");
        method.setAccessible(true);
        String url = (String) method.invoke(FfmpegService.getInstance());
        assertTrue(url.endsWith("/hls/stream.m3u8"));
        assertEquals(url, method.invoke(LitePlayerFfmpegService.getInstance()));
    }

    private static Object invoke(String name, Class<?>[] types, Object... args) throws Exception {
        Method method = AbstractFfmpegHlsService.class.getDeclaredMethod(name, types);
        method.setAccessible(true);
        return method.invoke(null, args);
    }
}
