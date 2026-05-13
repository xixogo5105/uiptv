package com.uiptv.util;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HlsPlaylistResolverTest {

    @Test
    void resolveHlsPlaylistChain_followsHighestBandwidthVariantAndPreservesQuery() {
        try (MockedStatic<HttpUtil> http = Mockito.mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.sendRequest(
                    Mockito.eq("http://cdn.test/master.m3u8?token=abc"),
                    Mockito.anyMap(),
                    Mockito.eq("GET")
            )).thenReturn(new HttpUtil.HttpResult("GET", "http://cdn.test/path/master.m3u8?token=abc", 200, """
                    #EXTM3U
                    #EXT-X-STREAM-INF:BANDWIDTH=1000
                    low/index.m3u8
                    #EXT-X-STREAM-INF:BANDWIDTH=5000
                    high/index.m3u8
                    """, Map.of(), Map.of()));
            http.when(() -> HttpUtil.sendRequest(
                    Mockito.eq("http://cdn.test/path/high/index.m3u8?token=abc"),
                    Mockito.anyMap(),
                    Mockito.eq("GET")
            )).thenReturn(new HttpUtil.HttpResult(200, "#EXTM3U\n#EXTINF:1,\nsegment.ts", Map.of(), Map.of()));

            String resolved = HlsPlaylistResolver.resolveHlsPlaylistChain(
                    "http://cdn.test/master.m3u8?token=abc",
                    Map.of("User-Agent", "test"),
                    4
            );

            assertEquals("http://cdn.test/path/high/index.m3u8?token=abc", resolved);
        }
    }

    @Test
    void resolveHlsPlaylistChain_returnsOriginalForTerminalInvalidOrLoopingInputs() {
        assertEquals(null, HlsPlaylistResolver.resolveHlsPlaylistChain(null, Map.of(), 2));
        assertEquals("   ", HlsPlaylistResolver.resolveHlsPlaylistChain("   ", Map.of(), 2));
        assertEquals("http://cdn.test/video.mp4", HlsPlaylistResolver.resolveHlsPlaylistChain("http://cdn.test/video.mp4", Map.of(), 2));

        try (MockedStatic<HttpUtil> http = Mockito.mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.sendRequest(Mockito.anyString(), Mockito.anyMap(), Mockito.eq("GET")))
                    .thenReturn(new HttpUtil.HttpResult(500, "", Map.of(), Map.of()));

            assertEquals("http://cdn.test/live", HlsPlaylistResolver.resolveHlsPlaylistChain("http://cdn.test/live", Map.of(), 2));
        }
    }
}
