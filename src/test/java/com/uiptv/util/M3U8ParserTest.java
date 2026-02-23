package com.uiptv.util;

import com.uiptv.shared.PlaylistEntry;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class M3U8ParserTest {

    @Test
    void parsesStandardM3uEntry() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 tvg-id="a" group-title="G",Alpha
                https://example.com/live/alpha.m3u8
                """;
        List<PlaylistEntry> entries = parseContent(content);
        assertEquals(1, entries.size());
        assertEquals("Alpha", entries.get(0).getTitle());
        assertEquals("https://example.com/live/alpha.m3u8", entries.get(0).getPlaylistEntry());
    }

    @Test
    void skipsSeparatorAndUsesFollowingValidUrl() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 group-title="Sports",WWE Network
                🔹🔹🔹 [LINE] 🔹🔹🔹
                https://example.com/wwe/master.m3u8
                """;
        List<PlaylistEntry> entries = parseContent(content);
        assertEquals(1, entries.size());
        assertEquals("https://example.com/wwe/master.m3u8", entries.get(0).getPlaylistEntry());
    }

    @Test
    void skipsHeaderTextAndUsesFollowingValidUrl() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 group-title="X",Anwar TV2 (720p)
                Astro Go:
                https://example.com/anwar/stream.m3u8
                """;
        List<PlaylistEntry> entries = parseContent(content);
        assertEquals(1, entries.size());
        assertEquals("https://example.com/anwar/stream.m3u8", entries.get(0).getPlaylistEntry());
    }

    @Test
    void normalizesEscapedUrlForms() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 group-title="Radio",SYOK Classic Rock
                https:\\u002F\\u002Fplayerservices.streamtheworld.com\\u002Fapi\\u002Flivestream-redirect\\u002FMYXFMAAC_48.aac
                """;
        List<PlaylistEntry> entries = parseContent(content);
        assertEquals(1, entries.size());
        assertEquals(
                "https://playerservices.streamtheworld.com/api/livestream-redirect/MYXFMAAC_48.aac",
                entries.get(0).getPlaylistEntry()
        );
    }

    @Test
    void mapsComClearKeyAlphaAndParsesClearKeys() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 group-title="Sports",NFL
                #KODIPROP:inputstream.adaptive.license_type=com.clearkey.alpha
                #KODIPROP:inputstream.adaptive.license_key=002007110c69a23803173b50eab05f23:590d6e8f4ca81319f9bb29104f571990
                http://cors.tundracast.com:2000/https://fsly.stream.peacocktv.com/Content/CMAF_CTR-4s/Live/channel(lc107a1ddy)/master.mpd
                """;
        List<PlaylistEntry> entries = parseContent(content);
        assertEquals(1, entries.size());
        PlaylistEntry entry = entries.get(0);
        assertEquals("org.w3.clearkey", entry.getDrmType());
        Map<String, String> keys = entry.getClearKeys();
        assertNotNull(keys);
        assertEquals("590d6e8f4ca81319f9bb29104f571990", keys.get("002007110c69a23803173b50eab05f23"));
    }

    @Test
    void doesNotCreateEntryWhenNoValidUrlExists() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 group-title="Sports",Broken Channel
                -----
                Section Header
                🔹🔹🔹
                #EXTINF:-1 group-title="Sports",Next Channel
                https://example.com/next.m3u8
                """;
        List<PlaylistEntry> entries = parseContent(content);
        assertEquals(1, entries.size());
        assertEquals("Next Channel", entries.get(0).getTitle());
    }

    private List<PlaylistEntry> parseContent(String content) throws IOException {
        Path temp = Files.createTempFile("m3u8-parser-test-", ".m3u");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        try {
            return M3U8Parser.parseChannelPathM3U8(temp.toString());
        } finally {
            Files.deleteIfExists(temp);
        }
    }
}
