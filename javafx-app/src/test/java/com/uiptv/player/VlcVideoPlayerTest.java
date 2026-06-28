package com.uiptv.player;

import com.uiptv.model.Configuration;
import com.uiptv.util.SystemUtils;
import org.junit.jupiter.api.Test;

import java.util.List;

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
        configuration.setVlcVout("enabled");
        configuration.setVlcAvcodecHw("enabled");

        List<String> args = VlcVideoPlayer.buildVlcArgs(configuration);

        assertFalse(args.contains("--network-caching=1000"));
        assertTrue(args.contains("--live-caching=30000"));
        // Verify OS-specific arguments are present
        verifyOsSpecificArguments(args);
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
    void buildVlcArgs_addsOsSpecificArguments() {
        Configuration configuration = new Configuration();
        configuration.setVlcNetworkCachingMs("");
        configuration.setVlcLiveCachingMs("1000");
        configuration.setEnableVlcHttpUserAgent(false);
        configuration.setEnableVlcHttpForwardCookies(false);
        configuration.setVlcVout("enabled");
        configuration.setVlcAvcodecHw("enabled");

        List<String> args = VlcVideoPlayer.buildVlcArgs(configuration);

        // Verify that the correct OS-specific vout argument is present
        if (SystemUtils.IS_OS_WINDOWS) {
            assertTrue(args.contains("--vout=direct3d11"), "Windows should have --vout=direct3d11");
            assertTrue(args.contains("--avcodec-hw=d3d11va"), "Windows should have --avcodec-hw=d3d11va");
        } else if (SystemUtils.IS_OS_LINUX) {
            assertTrue(args.contains("--vout=gl"), "Linux should have --vout=gl");
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            assertTrue(args.contains("--vout=macosx"), "macOS should have --vout=macosx");
            assertTrue(args.contains("--avcodec-hw=videotoolbox"), "macOS should have --avcodec-hw=videotoolbox");
        }
    }

    private void verifyOsSpecificArguments(List<String> args) {
        // Verify that the correct OS-specific vout argument is present
        if (SystemUtils.IS_OS_WINDOWS) {
            assertTrue(args.contains("--vout=direct3d11"));
            assertTrue(args.contains("--avcodec-hw=d3d11va"));
            assertFalse(args.contains("--vout=gl"));
            assertFalse(args.contains("--vout=macosx"));
        } else if (SystemUtils.IS_OS_LINUX) {
            assertTrue(args.contains("--vout=gl"));
            assertFalse(args.contains("--vout=direct3d11"));
            assertFalse(args.contains("--vout=macosx"));
        } else if (SystemUtils.IS_OS_MAC_OSX) {
            assertTrue(args.contains("--vout=macosx"));
            assertTrue(args.contains("--avcodec-hw=videotoolbox"));
            assertFalse(args.contains("--vout=direct3d11"));
            assertFalse(args.contains("--vout=gl"));
        }
    }
}
