package com.uiptv.server.api.json;

import org.junit.jupiter.api.Test;
import com.uiptv.model.Account;
import com.uiptv.util.AccountType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

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
    void identifiesHlsAndRejectsContainersTheBrowserCannotOpenDirectly() {
        assertEquals("hls", PlaybackMediaInspector.classifyResponse("application/vnd.apple.mpegurl", new byte[0]).strategy());
        byte[] matroska = {(byte) 0x1a, 0x45, (byte) 0xdf, (byte) 0xa3};
        assertEquals("unsupported", PlaybackMediaInspector.classifyResponse("application/octet-stream", matroska).strategy());
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

}
