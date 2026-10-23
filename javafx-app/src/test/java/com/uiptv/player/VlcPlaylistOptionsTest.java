package com.uiptv.player;

import com.uiptv.model.Channel;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class VlcPlaylistOptionsTest {
    @Test
    void appliesKodiHttpPlaybackOptionsAndIgnoresUnsupportedOptions() {
        Channel channel = new Channel();
        channel.setExtraJson("""
                {"m3u":{"extvlcopt":{"http-user-agent":"Playlist Agent","http-referrer":"https://referrer.test/","network-caching":"1500","sout":"unsafe"}}}
                """);

        assertEquals(List.of(
                ":http-user-agent=Playlist Agent",
                ":http-referrer=https://referrer.test/",
                ":network-caching=1500"
        ), VlcVideoPlayer.playlistVlcOptions(channel));
    }
}
