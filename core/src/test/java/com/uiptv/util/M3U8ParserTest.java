package com.uiptv.util;

import com.uiptv.model.CategoryType;
import com.uiptv.shared.PlaylistEntry;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.BufferedReader;
import java.io.Reader;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
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

    @Test
    void parsesUrlSourcesForHttpAndFileProtocols() throws Exception {
        String remotePlaylist = """
                #EXTM3U
                #EXTINF:-1 tvg-id="http-id" tvg-logo="https://img/logo.png" group-title="Remote",Remote Channel
                https://stream.test/remote.m3u8
                """;
        try (MockedStatic<HttpUtil> httpUtil = Mockito.mockStatic(HttpUtil.class)) {
            httpUtil.when(() -> HttpUtil.openStream(
                            Mockito.eq("https://playlist.test/live.m3u"),
                            Mockito.isNull(),
                            Mockito.eq("GET"),
                            Mockito.isNull(),
                            Mockito.any(HttpUtil.RequestOptions.class)))
                    .thenAnswer(_ -> streamResult(remotePlaylist));

            List<PlaylistEntry> channels = M3U8Parser.parseChannelUrlM3U8(URI.create("https://playlist.test/live.m3u").toURL());
            Set<PlaylistEntry> categories = M3U8Parser.parseUrlCategory(URI.create("https://playlist.test/live.m3u").toURL());

            assertEquals(1, channels.size());
            assertEquals("http-id", channels.getFirst().getId());
            assertEquals("https://img/logo.png", channels.getFirst().getLogo());
            assertTrue(categories.stream().anyMatch(entry -> "Remote".equals(entry.getGroupTitle())));
        }

        Path temp = writeTempPlaylist("""
                #EXTM3U
                #EXTINF:-1 group-title="File",File Channel
                ./relative.m3u8
                """);
        try {
            List<PlaylistEntry> channels = M3U8Parser.parseChannelUrlM3U8(temp.toUri().toURL());
            Set<PlaylistEntry> categories = M3U8Parser.parseUrlCategory(temp.toUri().toURL());
            assertEquals("./relative.m3u8", channels.getFirst().getPlaylistEntry());
            assertTrue(categories.stream().anyMatch(entry -> "File".equals(entry.getGroupTitle())));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private static HttpUtil.StreamResult streamResult(String body) {
        return new HttpUtil.StreamResult(
                "GET",
                "https://playlist.test/live.m3u",
                HttpUtil.STATUS_OK,
                Map.of(),
                Map.of(),
                new ByteArrayInputStream(body.getBytes(StandardCharsets.UTF_8)),
                Mockito.mock(org.apache.hc.client5.http.impl.classic.CloseableHttpResponse.class)
        );
    }

    @Test
    void streamingChannelParsersMatchCollectingParsers() throws Exception {
        Path temp = writeTempPlaylist("""
                #EXTM3U
                #EXTINF:-1 tvg-id="shared" tvg-logo="https://img/shared.png" group-title="News;Sports",Shared Channel
                https://stream.test/shared.m3u8
                #EXTINF:-1 tvg-id="drm" group-title="Movies",DRM Channel
                #KODIPROP:inputstream.adaptive.license_type=clearkey
                #KODIPROP:inputstream.adaptive.license_key=abc:def
                https://stream.test/drm.mpd
                #EXTINF:-1,No Group
                relative.ts
                """);
        try {
            List<PlaylistEntry> collected = M3U8Parser.parseChannelPathM3U8(temp.toString());
            List<PlaylistEntry> streamedFromPath = new ArrayList<>();
            List<PlaylistEntry> streamedFromUrl = new ArrayList<>();

            M3U8Parser.forEachChannelPathM3U8(temp.toString(), streamedFromPath::add);
            M3U8Parser.forEachChannelUrlM3U8(temp.toUri().toURL(), streamedFromUrl::add);

            assertEquals(entrySignatures(collected), entrySignatures(streamedFromPath));
            assertEquals(entrySignatures(collected), entrySignatures(streamedFromUrl));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void wrapsIoFailuresForInvalidPathsAndUrls() throws Exception {
        assertThrows(UncheckedIOException.class, () -> M3U8Parser.parseChannelPathM3U8("missing-file.m3u"));
        assertThrows(UncheckedIOException.class, () -> M3U8Parser.parsePathCategory("missing-file.m3u"));

        var missingFileUrl = Path.of("missing-url-file.m3u").toUri().toURL();
        assertThrows(UncheckedIOException.class, () -> M3U8Parser.parseChannelUrlM3U8(missingFileUrl));
        assertThrows(UncheckedIOException.class, () -> M3U8Parser.parseUrlCategory(missingFileUrl));
    }

    @Test
    void coversDrmMetadataAndUrlHeuristics() throws IOException {
        String content = """
                #EXTM3U
                #EXTINF:-1 group-title="",No Group
                movie.mp4?token=1
                #EXTINF:-1 tvg-id="all" group-title="All",All Category Is Skipped
                /absolute/path/channel.ts
                #EXTINF:-1 group-title="Dash",Widevine Dash
                #EXT-X-KEY:METHOD=SAMPLE-AES,URI="https://license.test/wv",KEYFORMAT="com.widevine.alpha"
                #KODIPROP:inputstreamaddon=inputstream.adaptive
                #KODIPROP:inputstream.adaptive.manifest_type=mpd
                #KODIPROP:inputstream.adaptive.license_key=https://license.test/k
                stream.mpd
                #EXTINF:-1 group-title="Clear",Clear Key Json
                #KODIPROP:inputstream.adaptive.license_type=clearkey
                #KODIPROP:inputstream.adaptive.license_key={"kid":"key"}
                C:\\\\media\\\\local.ts
                #EXTINF:-1 group-title="Clear",Malformed Clear Key Falls Back
                #KODIPROP:inputstream.adaptive.license_type=org.w3.clearkey
                #KODIPROP:inputstream.adaptive.license_key={bad}
                ../up/stream.aac
                #EXTINF:-1 group-title="Widevine",Widevine License Type
                #KODIPROP:inputstream.adaptive.license_type=com.widevine.alpha
                #KODIPROP:inputstream.adaptive.license_key=https://license.test/widevine
                folder/stream.m3u8
                #EXTINF:-1 group-title="Unknown",Unknown License Type
                #KODIPROP:inputstream.adaptive.license_type=unknown
                #KODIPROP:inputstream.adaptive.license_key=https://license.test/unknown
                ../up/stream.aac
                """;

        List<PlaylistEntry> entries = parseContent(content);

        assertEquals(7, entries.size());
        assertEquals(CategoryType.UNCATEGORIZED.displayName(), entries.get(0).getGroupTitle());
        assertEquals("movie.mp4?token=1", entries.get(0).getPlaylistEntry());
        assertEquals("/absolute/path/channel.ts", entries.get(1).getPlaylistEntry());

        PlaylistEntry widevine = entries.get(2);
        assertEquals("com.widevine.alpha", widevine.getDrmType());
        assertEquals("https://license.test/k", widevine.getDrmLicenseUrl());
        assertEquals("inputstream.adaptive", widevine.getInputstreamaddon());
        assertEquals("mpd", widevine.getManifestType());

        assertEquals("key", entries.get(3).getClearKeys().get("kid"));
        assertTrue(entries.get(4).getClearKeys().isEmpty());
        assertEquals("com.widevine.alpha", entries.get(5).getDrmType());
        assertEquals("https://license.test/widevine", entries.get(5).getDrmLicenseUrl());
        assertNull(entries.get(6).getDrmType());
        assertEquals("https://license.test/unknown", entries.get(6).getDrmLicenseUrl());
    }

    @Test
    void categoryParsingAddsUncategorizedAndSkipsInvalidGroups() throws IOException {
        Path temp = writeTempPlaylist("""
                #EXTM3U
                #EXTINF:-1 tvg-id="blank"
                no-group.mp4
                #EXTINF:-1 tvg-id="all" group-title="All",All
                all.mp4
                #EXTINF:-1 tvg-id="explicit" group-title="Uncategorized",Explicit
                explicit.mp4
                """);
        try {
            Set<PlaylistEntry> categories = M3U8Parser.parsePathCategory(temp.toString());

            assertTrue(categories.stream().anyMatch(entry -> CategoryType.ALL.displayName().equals(entry.getGroupTitle())));
            assertTrue(categories.stream().anyMatch(entry -> CategoryType.UNCATEGORIZED.displayName().equals(entry.getGroupTitle())));
            assertFalse(categories.stream().anyMatch(entry -> "All".equals(entry.getId()) && "All".equals(entry.getTitle())));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void categoryParsingAddsUncategorizedWhenOnlyBlankGroupsExist() throws IOException {
        Path temp = writeTempPlaylist("""
                #EXTM3U
                #EXTINF:-1 tvg-id="blank"
                no-group.mp4
                """);
        try {
            Set<PlaylistEntry> categories = M3U8Parser.parsePathCategory(temp.toString());

            assertTrue(categories.stream().anyMatch(entry -> CategoryType.UNCATEGORIZED.displayName().equals(entry.getGroupTitle())));
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    @Test
    void privateParserBranchesCoverErrorsAndLowLevelHelpers() throws Exception {
        @SuppressWarnings("unchecked")
        Set<PlaylistEntry> categories = (Set<PlaylistEntry>) invoke("parseCategory", new Class[]{BufferedReader.class}, new ThrowingBufferedReader());
        assertTrue(categories.stream().anyMatch(entry -> CategoryType.ALL.displayName().equals(entry.getGroupTitle())));

        assertFalse((Boolean) invoke("processCategoryLineSafely", new Class[]{Set.class, String.class}, null, "#EXTINF:-1 group-title=\"Bad\",Bad"));
        assertEquals("", invoke("parseTitle", new Class[]{String.class}, "#EXTINF:-1 group-title=\"NoTitle\","));
        assertNull(invoke("parseDrmType", new Class[]{String.class}, "#EXT-X-KEY:KEYFORMAT=\"other\""));

        @SuppressWarnings("unchecked")
        Map<String, String> blankKeys = (Map<String, String>) invoke("parseClearKeys", new Class[]{String.class}, "");
        assertTrue(blankKeys.isEmpty());

        assertEquals("", invoke("normalizePotentialUrl", new Class[]{String.class}, ""));
        assertFalse((Boolean) invoke("isLikelyStreamUrl", new Class[]{String.class}, ""));
        assertFalse((Boolean) invoke("isLikelyStreamUrl", new Class[]{String.class}, "# comment"));
        assertTrue((Boolean) invoke("isLikelyStreamUrl", new Class[]{String.class}, "folder/channel"));
        assertFalse((Boolean) invoke("isLikelyStreamUrl", new Class[]{String.class}, "plain text"));
        assertEquals(List.of(CategoryType.UNCATEGORIZED.displayName()), invoke("effectiveGroupTitles", new Class[]{List.class}, (Object) null));
        assertNull(invoke("buildCategoryEntry", new Class[]{String.class, String.class}, "id", ""));
    }

    private List<PlaylistEntry> parseContent(String content) throws IOException {
        Path temp = writeTempPlaylist(content);
        try {
            return M3U8Parser.parseChannelPathM3U8(temp.toString());
        } finally {
            Files.deleteIfExists(temp);
        }
    }

    private Path writeTempPlaylist(String content) throws IOException {
        Path temp = Files.createTempFile("m3u8-parser-test-", ".m3u");
        Files.writeString(temp, content, StandardCharsets.UTF_8);
        return temp;
    }

    private DynamicTest urlCase(String name, String content, String expectedTitle, String expectedUrl) {
        return DynamicTest.dynamicTest(name, () -> {
            List<PlaylistEntry> entries = parseContent(content);
            assertEquals(1, entries.size());
            assertEquals(expectedTitle, entries.get(0).getTitle());
            assertEquals(expectedUrl, entries.get(0).getPlaylistEntry());
        });
    }

    private List<String> entrySignatures(List<PlaylistEntry> entries) {
        return entries.stream()
                .map(entry -> String.join("|",
                        String.valueOf(entry.getId()),
                        String.valueOf(entry.getGroupTitle()),
                        String.valueOf(entry.getTitle()),
                        String.valueOf(entry.getPlaylistEntry()),
                        String.valueOf(entry.getLogo()),
                        String.valueOf(entry.getDrmType()),
                        String.valueOf(entry.getDrmLicenseUrl()),
                        String.valueOf(entry.getClearKeys()),
                        String.valueOf(entry.getInputstreamaddon()),
                        String.valueOf(entry.getManifestType())))
                .toList();
    }

    private Object invoke(String name, Class<?>[] parameterTypes, Object... args) throws Exception {
        Method method = M3U8Parser.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, args);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Exception exception) {
                throw exception;
            }
            throw e;
        }
    }

    private static final class ThrowingBufferedReader extends BufferedReader {
        private ThrowingBufferedReader() {
            super(Reader.nullReader());
        }

        @Override
        public String readLine() throws IOException {
            throw new IOException("boom");
        }
    }
}
