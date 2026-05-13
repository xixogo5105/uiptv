package com.uiptv.player;

import com.uiptv.model.Configuration;
import org.junit.jupiter.api.Test;

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

        List<String> args = VlcVideoPlayer.buildVlcArgs(configuration);

        assertFalse(args.contains("--network-caching=1000"));
        assertEquals(List.of("--live-caching=30000"), args);
    }
}
