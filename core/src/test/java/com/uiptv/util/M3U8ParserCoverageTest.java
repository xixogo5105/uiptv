package com.uiptv.util;

import com.uiptv.shared.PlaylistEntry;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class M3U8ParserCoverageTest {
    @Test
    void parsesDirectivesDrmHeadersAndRelativeStreamForms() throws Exception {
        String playlist = """
                #EXTM3U
                #EXTGRP:News|Sports
                #EXTINF:-1 tvg-id="one" tvg-name="One" tvg-logo="http://logo" group-title="News",One
                #EXTVLCOPT:http-referrer=http://ref
                #WEBPROP:poster=http://poster
                #PLAYLIST-TYPE:VOD
                #EXT-X-KEY:METHOD=AES-128,KEYFORMAT="com.widevine.alpha",URI="http://license"
                #KODIPROP:inputstreamaddon=inputstream.adaptive
                #KODIPROP:inputstream.adaptive.manifest_type=mpd
                #KODIPROP:inputstream.adaptive.license_type=clearkey
                #KODIPROP:inputstream.adaptive.license_key={"kid":"key"}
                http://stream/one.m3u8|User-Agent=Agent&Referer=http://ref&Origin=http://origin&Cookie=sid=1&Unknown=x
                #EXTINF:-1 group-title="",Two
                /relative/two.ts
                #EXTINF:-1,Three
                #EXTGRP:Misc
                ./three.mp4
                #EXTINF:-1 tvg-name="Fallback"
                ignored-without-url
                """;
        Path source = Files.createTempFile("uiptv-m3u8-", ".m3u");
        Files.writeString(source, playlist);
        List<PlaylistEntry> entries = M3U8Parser.parseChannelSourceM3U8(source.toString());
        assertEquals(3, entries.size());
        PlaylistEntry first = entries.getFirst();
        assertEquals("one", first.getId());
        assertEquals("http://stream/one.m3u8", first.getPlaylistEntry());
        assertEquals("org.w3.clearkey", first.getDrmType());
        assertEquals("mpd", first.getManifestType());
        assertNotNull(first.getMetadata());
        assertTrue(first.getMetadata().containsKey("urlHeaders"));
        assertEquals("relative/two.ts", entries.get(1).getPlaylistEntry().replaceFirst("^/", ""));
        assertEquals("Misc", entries.get(2).getGroupTitle());
        Files.deleteIfExists(source);
    }

    @Test
    void parsesCategoriesAndHandlesEmptyOrMalformedInputs() throws Exception {
        Path source = Files.createTempFile("uiptv-m3u8-", ".m3u");
        Files.writeString(source, "#EXTM3U\n#EXTGRP:News\n#EXTINF:-1,Name\nstream.ts");
        var categories = M3U8Parser.parseChannelSourceM3U8(source.toString());
        assertEquals(1, categories.size());
        assertTrue(M3U8Parser.parseChannelSourceM3U8(null).isEmpty());
        assertTrue(M3U8Parser.parseChannelSourceM3U8(source.toString()).size() >= 1);
        Files.deleteIfExists(source);
    }
}
