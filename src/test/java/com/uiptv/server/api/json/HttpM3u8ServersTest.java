package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.model.Account;
import com.uiptv.model.Bookmark;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.M3U8PublicationService;
import com.uiptv.test.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpM3u8ServersTest extends DbBackedTest {

    @BeforeEach
    void setUpSelection() {
        M3U8PublicationService.getInstance().setSelectedAccountIds(Set.of());
    }

    @AfterEach
    void tearDownSelection() {
        M3U8PublicationService.getInstance().setSelectedAccountIds(Set.of());
    }

    @Test
    void iptvM3u8Server_returnsM3u8ForM3u8PathAndM3uPath() throws Exception {
        Path localM3u8 = tempDir.resolve("local.m3u8");
        Files.writeString(localM3u8, "#EXTM3U\n#EXTINF:-1,Local Channel\nhttp://local/stream.ts\n");

        Account account = new Account("m3u8-account", "user", "pass", "http://unused", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_LOCAL, null, localM3u8.toString(), false);
        AccountService accountService = AccountService.getInstance();
        accountService.save(account);
        Account saved = accountService.getByName("m3u8-account");
        M3U8PublicationService.getInstance().setSelectedAccountIds(Set.of(saved.getDbId()));

        HttpIptvM3u8Server handler = new HttpIptvM3u8Server();

        StubHttpExchange m3u8Exchange = new StubHttpExchange("/iptv.m3u8", "GET");
        handler.handle(m3u8Exchange);
        assertEquals(200, m3u8Exchange.getResponseCode());
        assertTrue(m3u8Exchange.getResponseHeaders().getFirst("Content-Type").contains("vnd.apple.mpegurl"));
        assertTrue(m3u8Exchange.getResponseBodyText().contains("#EXTM3U"));
        assertTrue(m3u8Exchange.getResponseBodyText().contains("Local Channel"));

        StubHttpExchange m3uExchange = new StubHttpExchange("/iptv.m3u", "GET");
        handler.handle(m3uExchange);
        assertEquals(200, m3uExchange.getResponseCode());
        assertTrue(m3uExchange.getResponseBodyText().contains("#EXTM3U"));
    }

    @Test
    void m3u8BookmarkEntry_whenBookmarkIdMissing_doesNothing() throws Exception {
        HttpM3u8BookmarkEntry handler = new HttpM3u8BookmarkEntry();
        StubHttpExchange exchange = new StubHttpExchange("/m3u8BookmarkEntry?x=1", "GET");
        handler.handle(exchange);
        assertEquals(-1, exchange.getResponseCode());
        assertTrue(exchange.getResponseBodyText().isEmpty());
    }

    @Test
    void m3u8BookmarkEntry_whenBookmarkNotFound_doesNothing() throws Exception {
        HttpM3u8BookmarkEntry handler = new HttpM3u8BookmarkEntry();
        StubHttpExchange exchange = new StubHttpExchange("/m3u8BookmarkEntry?bookmarkId=404", "GET");
        handler.handle(exchange);
        assertEquals(-1, exchange.getResponseCode());
        assertTrue(exchange.getResponseBodyText().isEmpty());
    }

    @Test
    void m3u8BookmarkEntry_coversSeriesChannelVodAndFallbackBranches() throws Exception {
        Account account = new Account("bookmark-account", "user", "pass", "http://unused", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://unused/list.m3u8", false);
        AccountService accountService = AccountService.getInstance();
        accountService.save(account);

        BookmarkService bookmarkService = BookmarkService.getInstance();

        Bookmark seriesBookmark = baseBookmark();
        seriesBookmark.setSeriesJson("{\"id\":\"ep-7\",\"title\":\"Episode 7\",\"cmd\":\"ffmpeg http://series/7.ts\",\"info\":{\"movie_image\":\"http://img/7.png\"}}");
        bookmarkService.save(seriesBookmark);
        Bookmark savedSeries = bookmarkService.getBookmark(seriesBookmark);

        Bookmark channelBookmark = baseBookmark();
        channelBookmark.setChannelId("ch-2");
        channelBookmark.setChannelName("Channel Two");
        channelBookmark.setChannelJson("{\"channelId\":\"json-2\",\"name\":\"Json Name\",\"cmd\":\"ffmpeg http://channel/2.ts\"}");
        bookmarkService.save(channelBookmark);
        Bookmark savedChannel = bookmarkService.getBookmark(channelBookmark);

        Bookmark vodBookmark = baseBookmark();
        vodBookmark.setChannelId("ch-3");
        vodBookmark.setChannelName("Channel Three");
        vodBookmark.setVodJson("{\"channelId\":\"vod-3\",\"name\":\"Vod Name\",\"cmd\":\"ffmpeg http://vod/3.ts\"}");
        bookmarkService.save(vodBookmark);
        Bookmark savedVod = bookmarkService.getBookmark(vodBookmark);

        Bookmark legacyBookmark = baseBookmark();
        legacyBookmark.setChannelId("ch-4");
        legacyBookmark.setChannelName("Legacy");
        legacyBookmark.setCmd("ffmpeg%20http%3A%2F%2Flegacy%2F4.ts");
        legacyBookmark.setDrmType("widevine");
        legacyBookmark.setDrmLicenseUrl("http://license/4");
        legacyBookmark.setClearKeysJson("{\"kid\":\"key\"}");
        legacyBookmark.setInputstreamaddon("addon-4");
        legacyBookmark.setManifestType("hls");
        bookmarkService.save(legacyBookmark);
        Bookmark savedLegacy = bookmarkService.getBookmark(legacyBookmark);

        HttpM3u8BookmarkEntry handler = new HttpM3u8BookmarkEntry();

        StubHttpExchange seriesExchange = new StubHttpExchange("/m3u8BookmarkEntry?bookmarkId=" + savedSeries.getDbId(), "GET");
        handler.handle(seriesExchange);
        assertValidTsResponse(seriesExchange, "http://series/7.ts");

        StubHttpExchange channelExchange = new StubHttpExchange("/m3u8BookmarkEntry?bookmarkId=" + savedChannel.getDbId(), "GET");
        handler.handle(channelExchange);
        assertValidTsResponse(channelExchange, "http://channel/2.ts");

        StubHttpExchange vodExchange = new StubHttpExchange("/m3u8BookmarkEntry?bookmarkId=" + savedVod.getDbId(), "GET");
        handler.handle(vodExchange);
        assertValidTsResponse(vodExchange, "http://vod/3.ts");

        StubHttpExchange legacyExchange = new StubHttpExchange("/m3u8BookmarkEntry?bookmarkId=" + savedLegacy.getDbId(), "GET");
        handler.handle(legacyExchange);
        assertValidTsResponse(legacyExchange, "http://legacy/4.ts");

        Bookmark legacyAfterHandle = bookmarkService.getBookmark(savedLegacy.getDbId());
        assertEquals("ffmpeg%20http%3A%2F%2Flegacy%2F4.ts", legacyAfterHandle.getCmd());
        assertFalse(legacyExchange.getResponseBodyText().isEmpty());
    }

    private void assertValidTsResponse(StubHttpExchange exchange, String expectedUrl) {
        assertEquals(200, exchange.getResponseCode());
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Type").contains("video/mp2t"));
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Disposition").contains(".ts"));
        assertTrue(exchange.getResponseBodyText().contains("#EXTM3U"));
        assertTrue(exchange.getResponseBodyText().contains(expectedUrl));
    }

    private Bookmark baseBookmark() {
        Bookmark bookmark = new Bookmark("bookmark-account", "Sports", "ch-1", "Channel One", "ffmpeg%20http%3A%2F%2Foriginal%2Fstream.ts", "http://portal", "cat-1");
        bookmark.setDbId("b-ignore");
        return bookmark;
    }

    private static class StubHttpExchange extends HttpExchange {
        private final URI requestUri;
        private final String method;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private int responseCode = -1;

        StubHttpExchange(String uri, String method) {
            this.requestUri = URI.create(uri);
            this.method = method;
        }

        String getResponseBodyText() {
            return responseBody.toString(StandardCharsets.UTF_8);
        }

        @Override
        public Headers getRequestHeaders() {
            return requestHeaders;
        }

        @Override
        public Headers getResponseHeaders() {
            return responseHeaders;
        }

        @Override
        public URI getRequestURI() {
            return requestUri;
        }

        @Override
        public String getRequestMethod() {
            return method;
        }

        @Override
        public HttpContext getHttpContext() {
            return null;
        }

        @Override
        public void close() {
        }

        @Override
        public InputStream getRequestBody() {
            return new ByteArrayInputStream(new byte[0]);
        }

        @Override
        public OutputStream getResponseBody() {
            return responseBody;
        }

        @Override
        public void sendResponseHeaders(int rCode, long responseLength) {
            this.responseCode = rCode;
        }

        @Override
        public InetSocketAddress getRemoteAddress() {
            return null;
        }

        @Override
        public int getResponseCode() {
            return responseCode;
        }

        @Override
        public InetSocketAddress getLocalAddress() {
            return null;
        }

        @Override
        public String getProtocol() {
            return "HTTP/1.1";
        }

        @Override
        public Object getAttribute(String name) {
            return null;
        }

        @Override
        public void setAttribute(String name, Object value) {
        }

        @Override
        public void setStreams(InputStream i, OutputStream o) {
        }

        @Override
        public HttpPrincipal getPrincipal() {
            return null;
        }
    }
}
