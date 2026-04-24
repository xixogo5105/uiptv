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
        Bookmark bookmark1 = new Bookmark("acc", "News", "ch-1", "Channel One", "cmd1", "http://portal", "cat-1");
        Bookmark bookmark2 = new Bookmark("acc", "Sports", "ch-2", "Channel Two", "cmd2", "http://portal", "cat-1");
        BookmarkService.getInstance().save(bookmark1);
        BookmarkService.getInstance().save(bookmark2);
        Bookmark saved1 = BookmarkService.getInstance().getBookmark(bookmark1);
        Bookmark saved2 = BookmarkService.getInstance().getBookmark(bookmark2);

        HttpM3u8BookmarkPlayListServer handler = new HttpM3u8BookmarkPlayListServer();
        TestHttpExchange exchange = new TestHttpExchange("/bookmarks.m3u8", "GET");
        exchange.getRequestHeaders().add("Host", "localhost:9000");
        handler.handle(exchange);

        assertEquals(200, exchange.getResponseCode());
        String body = exchange.getResponseBodyText();
        
        // Verify #EXTM3U appears exactly once
        int firstIndex = body.indexOf("#EXTM3U");
        assertTrue(firstIndex >= 0);
        assertEquals(-1, body.indexOf("#EXTM3U", firstIndex + 1), "Should only contain #EXTM3U once");
        
        assertTrue(body.contains(saved1.getDbId()));
        assertTrue(body.contains(saved2.getDbId()));
        assertTrue(body.contains("http://localhost:9000/bookmarkEntry.ts?bookmarkId=" + saved1.getDbId()));
        assertTrue(body.contains("http://localhost:9000/bookmarkEntry.ts?bookmarkId=" + saved2.getDbId()));
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Type").contains("vnd.apple.mpegurl"));
    }
}
