package com.uiptv.server.api.json;

import com.uiptv.model.Bookmark;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpM3u8BookmarkPlayListServerTest extends DbBackedTest {

    @Test
    void handle_buildsPlaylistFromBookmarks() throws Exception {
        Bookmark bookmark = new Bookmark("acc", "News", "ch-1", "Channel One", "cmd", "http://portal", "cat-1");
        BookmarkService.getInstance().save(bookmark);
        Bookmark saved = BookmarkService.getInstance().getBookmark(bookmark);

        HttpM3u8BookmarkPlayListServer handler = new HttpM3u8BookmarkPlayListServer();
        TestHttpExchange exchange = new TestHttpExchange("/bookmarks.m3u8", "GET");
        exchange.getRequestHeaders().add("Host", "localhost:9000");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        String body = exchange.getResponseBodyText();
        assertTrue(body.contains(saved.getDbId()));
        assertTrue(body.contains("http://localhost:9000/bookmarkEntry.ts"));
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Type").contains("vnd.apple.mpegurl"));
    }
}
