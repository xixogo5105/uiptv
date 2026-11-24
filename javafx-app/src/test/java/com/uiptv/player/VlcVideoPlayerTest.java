package com.uiptv.player;

import com.uiptv.model.Configuration;
import org.junit.jupiter.api.Test;
import uk.co.caprica.vlcj.media.TrackType;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VlcVideoPlayerTest {

    @Test
    void buildVlcArgs_usesDefaultsWhenConfigurationIsEmpty() {
        List<String> args = VlcVideoPlayer.buildVlcArgs(new Configuration());

        assertTrue(args.contains("--network-caching=1000"));
        assertTrue(args.contains("--live-caching=1000"));
        assertTrue(args.contains("--http-forward-cookies"));
        assertTrue(args.stream().anyMatch(arg -> arg.startsWith("--http-user-agent=")));
    }

    @Test
    void buildVlcArgs_skipsDisabledEntriesAndUsesConfiguredCaching() {
        Configuration configuration = new Configuration();
        configuration.setVlcNetworkCachingMs("");
        configuration.setVlcLiveCachingMs("30000");
        configuration.setEnableVlcHttpUserAgent(false);
        configuration.setEnableVlcHttpForwardCookies(false);
        // Enable the callback-compatible VLC 3.x options.
        configuration.setVlcVout("true");
        configuration.setVlcAvcodecHw("true");

        List<String> args = VlcVideoPlayer.buildVlcArgs(configuration);

        assertFalse(args.contains("--network-caching=1000"));
        assertTrue(args.contains("--live-caching=30000"));
        assertTrue(args.contains("--vout=vmem"));
        assertTrue(args.contains("--avcodec-hw=any"));
    }

    @Test
    void buildVlcArgs_containsCommonFlags() {
        Configuration configuration = new Configuration();
        List<String> args = VlcVideoPlayer.buildVlcArgs(configuration);

        assertTrue(args.contains("--no-video-title-show"));
        assertTrue(args.contains("--quiet"));
        assertTrue(args.contains("--http-reconnect"));
        assertTrue(args.contains("--adaptive-use-access"));
    }

    @Test
    void buildVlcArgs_addsCallbackCompatibleVlc3Arguments() {
        Configuration configuration = new Configuration();
        configuration.setVlcNetworkCachingMs("");
        configuration.setVlcLiveCachingMs("1000");
        configuration.setEnableVlcHttpUserAgent(false);
        configuration.setEnableVlcHttpForwardCookies(false);
        // Enable vout and avcodec-hw to test callback-compatible arguments.
        configuration.setVlcVout("true");
        configuration.setVlcAvcodecHw("true");

        List<String> args = VlcVideoPlayer.buildVlcArgs(configuration);

        assertTrue(args.contains("--vout=vmem"));
        assertTrue(args.contains("--avcodec-hw=any"));
        assertFalse(args.contains("--vout=direct3d11"));
        assertFalse(args.contains("--vout=gl"));
        assertFalse(args.contains("--vout=macosx"));
        assertFalse(args.contains("--avcodec-hw=d3d11va"));
        assertFalse(args.contains("--avcodec-hw=videotoolbox"));
    }

    @Test
    void buildStreamInfoLabel_matchesMobileFormat() {
        assertEquals(
                "1080p · H.264/AVC · AAC · HW",
                VlcVideoPlayer.buildStreamInfoLabel(1920, 1080, "H.264/AVC", "AAC", "HW")
        );
        assertEquals(
                "4K UHD · H.265/HEVC · E-AC-3 · SW",
                VlcVideoPlayer.buildStreamInfoLabel(3840, 2160, "H.265/HEVC", "E-AC-3", "SW")
        );
        assertEquals(
                "720p · H.264/AVC",
                VlcVideoPlayer.buildStreamInfoLabel(1280, 720, "H.264/AVC", "", "")
        );
        assertEquals("1080p", VlcVideoPlayer.buildStreamInfoLabel(1920, 1080, "", "", ""));
    }

    @Test
    void normalizeCodecLabel_normalizesVlcFourccAndDescriptionsLikeMobile() {
        assertEquals("H.264/AVC", VlcVideoPlayer.normalizeCodecLabel("h264", ""));
        assertEquals("H.265/HEVC", VlcVideoPlayer.normalizeCodecLabel("hevc", ""));
        assertEquals("MPEG-2", VlcVideoPlayer.normalizeCodecLabel("mpgv", ""));
        assertEquals("AV1", VlcVideoPlayer.normalizeCodecLabel("av01", ""));
        assertEquals("AAC", VlcVideoPlayer.normalizeCodecLabel("mp4a", "MPEG AAC Audio"));
        assertEquals("AC-3", VlcVideoPlayer.normalizeCodecLabel("a52 ", "A52 Audio (aka AC3)"));
        assertEquals("E-AC-3", VlcVideoPlayer.normalizeCodecLabel("eac3", ""));
        assertEquals("Opus", VlcVideoPlayer.normalizeCodecLabel("opus", ""));
        assertEquals("TrueHD", VlcVideoPlayer.normalizeCodecLabel("mlp ", "MLP/TrueHD Audio"));
    }

    @Test
    void detectDecoderModeFromLog_identifiesHardwareAndSoftwareDecoding() {
        assertEquals("HW", VlcVideoPlayer.detectDecoderModeFromLog("avcodec", "Using VideoToolbox for hardware decoding"));
        assertEquals("HW", VlcVideoPlayer.detectDecoderModeFromLog("avcodec", "Using D3D11VA (Intel) for hardware decoding"));
        assertEquals("SW", VlcVideoPlayer.detectDecoderModeFromLog("avcodec", "Hardware decoding failed, falling back to software"));
        assertEquals("", VlcVideoPlayer.detectDecoderModeFromLog("main", "creating demux"));
    }

    @Test
    void metadataRefreshIncludesVideoAndAudioTrackEvents() {
        assertTrue(VlcVideoPlayer.isMetadataTrackType(TrackType.VIDEO));
        assertTrue(VlcVideoPlayer.isMetadataTrackType(TrackType.AUDIO));
    }
}
