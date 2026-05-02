package com.uiptv.util;

import com.uiptv.shared.PlaylistEntry;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class M3U8ParserTest {

    @TestFactory
    Stream<DynamicTest> resolvesChannelUrlVariants() {
        return Stream.of(
                urlCase("parses standard entry", """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="a" group-title="G",Alpha
                        https://example.com/live/alpha.m3u8
                        """, "Alpha", "https://example.com/live/alpha.m3u8"),
                urlCase("skips separator and uses following valid url", """
                        #EXTM3U
                        #EXTINF:-1 group-title="Sports",WWE Network
                        🔹🔹🔹 [LINE] 🔹🔹🔹
                        https://example.com/wwe/master.m3u8
                        """, "WWE Network", "https://example.com/wwe/master.m3u8"),
                urlCase("skips header text and uses following valid url", """
                        #EXTM3U
                        #EXTINF:-1 group-title="X",Anwar TV2 (720p)
                        Astro Go:
                        https://example.com/anwar/stream.m3u8
                        """, "Anwar TV2 (720p)", "https://example.com/anwar/stream.m3u8")
        );
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

    @Test
    void splitsSemicolonSeparatedGroupTitlesForChannelEntries() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 tvg-id="shared" group-title="News; Sports ;News",Shared Channel
                https://example.com/shared.m3u8
                """;

        List<PlaylistEntry> entries = parseContent(content);

        assertEquals(2, entries.size());
        assertEquals(Set.of("News", "Sports"), entries.stream().map(PlaylistEntry::getGroupTitle).collect(java.util.stream.Collectors.toSet()));
        assertTrue(entries.stream().allMatch(entry -> "Shared Channel".equals(entry.getTitle())));
    }

    @Test
    void splitsSemicolonSeparatedGroupTitlesForCategories() throws IOException {
        Path temp = Files.createTempFile("m3u8-parser-categories-", ".m3u");
        Files.writeString(temp, """
                #EXTM3U
                #EXTINF:-1 tvg-id="shared" group-title="News;Sports",Shared Channel
                https://example.com/shared.m3u8
                """, StandardCharsets.UTF_8);
        try {
            Set<PlaylistEntry> categories = M3U8Parser.parsePathCategory(temp.toString());
            assertTrue(categories.stream().anyMatch(entry -> "News".equals(entry.getGroupTitle())));
            assertTrue(categories.stream().anyMatch(entry -> "Sports".equals(entry.getGroupTitle())));
        } finally {
            Files.deleteIfExists(temp);
        }
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

    private DynamicTest urlCase(String name, String content, String expectedTitle, String expectedUrl) {
        return DynamicTest.dynamicTest(name, () -> {
            List<PlaylistEntry> entries = parseContent(content);
            assertEquals(1, entries.size());
            assertEquals(expectedTitle, entries.get(0).getTitle());
            assertEquals(expectedUrl, entries.get(0).getPlaylistEntry());
        });
    }
}
