package com.uiptv.player;

import com.uiptv.model.Configuration;
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
}
