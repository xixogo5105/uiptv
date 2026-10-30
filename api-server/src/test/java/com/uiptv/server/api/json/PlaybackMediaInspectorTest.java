package com.uiptv.server.api.json;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import com.uiptv.util.HttpUtil;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class PlaybackMediaInspectorTest {
    @Test
    void identifiesTransportStreamAndReadsCodecsAndDuration() {
        byte[] transportStream = new byte[377];
        transportStream[0] = transportStream[188] = transportStream[376] = 0x47;
        PlaybackMediaInspector.MediaInfo media = PlaybackMediaInspector.classifyResponse("application/octet-stream", transportStream);
        assertNotNull(media);
        assertEquals("mpegts", media.strategy());
        assertEquals("mpegts", media.formatName());
        assertEquals("", media.videoCodec());
        assertEquals("", media.audioCodec());
        assertEquals(0L, media.durationMs());
    }

    @Test
    void refusesCodecsThatNeedTranscodingAndRecognizesBrowserNativeMp4() {
        PlaybackMediaInspector.MediaInfo hevc = PlaybackMediaInspector.classifyResponse("video/mp4; codecs=\"hvc1.1.6.L93.B0,mp4a.40.2\"", new byte[0]);
        PlaybackMediaInspector.MediaInfo mp4 = PlaybackMediaInspector.classifyResponse("video/mp4; codecs=\"avc1.640028,mp4a.40.2\"", new byte[0]);
        assertEquals("unsupported", hevc.strategy());
        assertEquals("native", mp4.strategy());
        assertEquals("hvc1.1.6.l93.b0", hevc.videoCodec());
        assertEquals("mp4a.40.2", mp4.audioCodec());
    }

    @Test
    void identifiesHlsAndRoutesMatroskaToBrowserRemuxer() {
        assertEquals("hls", PlaybackMediaInspector.classifyResponse("application/vnd.apple.mpegurl", new byte[0]).strategy());
        byte[] matroska = {(byte) 0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};
        assertEquals("mkv", PlaybackMediaInspector.classifyResponse("application/octet-stream", matroska).strategy());
        assertEquals("mkv", PlaybackMediaInspector.classifyResponse("video/x-matroska", new byte[0]).strategy());
        assertEquals("mkv", PlaybackMediaInspector.inferUrlFormatHint("http://provider/episode.mkv?token=abc").strategy());
        assertEquals("unsupported", PlaybackMediaInspector.inferUrlFormatHint("http://provider/movie.avi").strategy());
        assertEquals("dash", PlaybackMediaInspector.classifyResponse("application/dash+xml", new byte[0]).strategy());
    }

    @Test
    void stalkerProbeMatchesExistingPortalHeadersAndOnDemandFallback() {
        Account account = new Account();
        account.setType(AccountType.STALKER_PORTAL);
        account.setTimezone("Europe/London");
        String url = "http://portal.example/play/movie.php?type=series&mac=00%3A1A%3A79%3A00%3A00%3A01&play_token=abc";

        var headers = PlaybackMediaInspector.buildProbeHeaders(url, account);

        assertEquals("Model: MAG250; Link: WiFi", headers.get("X-User-Agent"));
        assertEquals("mac=00:1A:79:00:00:01; stb_lang=en; timezone=Europe/London;", headers.get("Cookie"));
        assertEquals("http://portal.example", headers.get("Origin"));
        assertEquals("mpegts", PlaybackMediaInspector.inferStalkerOnDemandFormat(url, account).strategy());
    }

    @Test
    void extensionHintsIgnoreFileNamesEmbeddedInProviderQueryCommands() {
        Account account = new Account();
        account.setType(AccountType.XTREME_API);
        PlaybackMediaInspector.MediaInfo media = PlaybackMediaInspector.inferUrlFormatHint(
                "http://portal.example/play/movie.php?cmd=ffmpeg%20http://cdn.example/title.mp4");
        assertEquals(null, media);
    }

    @Test
    void coversAllExtensionHintsAndHeaderFallbacks() {
        assertNull(PlaybackMediaInspector.inferUrlFormatHint(null));
        assertNull(PlaybackMediaInspector.inferUrlFormatHint("  "));
        assertEquals("hls", PlaybackMediaInspector.inferUrlFormatHint("http://p/live?extension=m3u8").strategy());
        assertEquals("dash", PlaybackMediaInspector.inferUrlFormatHint("http://p/live?manifest_type=mpd").strategy());
        assertEquals("mpegts", PlaybackMediaInspector.inferUrlFormatHint("http://p/live.m2ts").strategy());
        assertEquals("mpegts", PlaybackMediaInspector.inferUrlFormatHint("http://p/live?extension=ts").strategy());
        assertEquals("native", PlaybackMediaInspector.inferUrlFormatHint("http://p/movie.m4v").strategy());
        assertEquals("mkv", PlaybackMediaInspector.inferUrlFormatHint("http://p/movie.mkv").strategy());
        assertEquals("unsupported", PlaybackMediaInspector.inferUrlFormatHint("http://p/movie.wmv").strategy());

        Account account = new Account();
        account.setType(AccountType.STALKER_PORTAL);
        account.setTimezone("");
        var stalkerHeaders = PlaybackMediaInspector.buildProbeHeaders("http://portal.example/live/play/1?mac=abc", account);
        assertTrue(stalkerHeaders.get("Cookie").contains("Europe/London"));
        assertEquals("Mozilla/5.0", PlaybackMediaInspector.buildProbeHeaders("http://cdn.example/live", null).get("User-Agent"));
        assertNull(PlaybackMediaInspector.inferStalkerOnDemandFormat("http://cdn.example/movie.mp4", null));
        assertNull(PlaybackMediaInspector.inferStalkerOnDemandFormat("http://cdn.example/movie.mp4", account));
    }

    @Test
    void classifiesSignaturesCodecListsAndUnknownInputs() {
        assertEquals("hls", PlaybackMediaInspector.classifyResponse("text/plain", " #EXTM3U".getBytes()).strategy());
        byte[] mp4 = new byte[8]; mp4[4] = 'f'; mp4[5] = 't'; mp4[6] = 'y'; mp4[7] = 'p';
        assertEquals("native", PlaybackMediaInspector.classifyResponse("application/octet-stream", mp4).strategy());
        byte[] mpeg = {0, 0, 1, (byte) 0xba};
        assertEquals("unsupported", PlaybackMediaInspector.classifyResponse("application/octet-stream", mpeg).strategy());
        assertEquals("unknown", PlaybackMediaInspector.classifyResponse(null, null).strategy());
        var codecs = PlaybackMediaInspector.classifyResponse("video/mp4; codecs=avc1.4d401f,aac.40.2;profile=x", mp4);
        assertEquals("avc1.4d401f", codecs.videoCodec());
        assertEquals("aac.40.2", codecs.audioCodec());
        assertEquals("native", codecs.strategy());
    }

    @Test
    void inspectUsesProbeResponseAndFallsBackForErrors() {
        Account account = new Account();
        account.setType(AccountType.STALKER_PORTAL);
        PlaybackMediaInspector.MediaInfo hinted = PlaybackMediaInspector.inspect("http://cdn/live.m3u8", account);
        assertEquals("hls", hinted.strategy());

        HttpUtil.StreamResult response = mock(HttpUtil.StreamResult.class);
        when(response.statusCode()).thenReturn(200);
        when(response.responseHeaders()).thenReturn(java.util.Map.of("Content-Type", java.util.List.of("video/mp4; codecs=avc1.4d401f,aac.40.2")));
        when(response.bodyStream()).thenReturn(new java.io.ByteArrayInputStream(new byte[]{0, 0, 0, 0, 'f', 't', 'y', 'p'}));
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.openStream(anyString(), anyMap(), eq("GET"), isNull(), any(HttpUtil.RequestOptions.class)))
                    .thenReturn(response);
            PlaybackMediaInspector.MediaInfo inspected = PlaybackMediaInspector.inspect("http://cdn/live", account);
            assertEquals("native", inspected.strategy());
            assertEquals("avc1.4d401f", inspected.videoCodec());
            assertEquals("aac.40.2", inspected.audioCodec());
        }
        HttpUtil.StreamResult failed = mock(HttpUtil.StreamResult.class);
        when(failed.statusCode()).thenReturn(500);
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.openStream(anyString(), anyMap(), eq("GET"), isNull(), any(HttpUtil.RequestOptions.class)))
                    .thenReturn(failed);
            assertEquals("mpegts", PlaybackMediaInspector.inspect(
                    "http://portal.example/play/movie.php?mac=AA", account).strategy());
        }
        try (MockedStatic<HttpUtil> http = mockStatic(HttpUtil.class)) {
            http.when(() -> HttpUtil.openStream(anyString(), anyMap(), eq("GET"), isNull(), any(HttpUtil.RequestOptions.class)))
                    .thenThrow(new java.io.IOException("offline"));
            assertEquals("unknown", PlaybackMediaInspector.inspect("http://cdn/live", null).strategy());
        }
    }

}
