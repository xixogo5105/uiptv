package com.uiptv.server.api.json;

import com.uiptv.model.Bookmark;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.I18n;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpM3u8BookmarkPlayListServerTest extends DbBackedTest {

    @Test
    void handle_buildsPlaylistUsingMiscForUncategorizedBookmarksAndCategoryTabsInBookmarkOrder() throws Exception {
        BookmarkService.getInstance().addCategory(new BookmarkCategory(null, "Movies"));
        BookmarkService.getInstance().addCategory(new BookmarkCategory(null, "Kids"));
        List<BookmarkCategory> categories = BookmarkService.getInstance().getAllCategories();
        String moviesId = categories.stream().filter(c -> "Movies".equals(c.getName())).findFirst().orElseThrow().getId();
        String kidsId = categories.stream().filter(c -> "Kids".equals(c.getName())).findFirst().orElseThrow().getId();

        Bookmark bookmark1 = new Bookmark("acc", "Movies", "ch-1", "Channel One", "cmd1", "http://portal", moviesId);
        Bookmark bookmark2 = new Bookmark("acc", "Kids", "ch-2", "Channel Two", "cmd2", "http://portal", kidsId);
        Bookmark bookmark3 = new Bookmark("acc", "", "ch-3", "Channel Three", "cmd3", "http://portal", null);
        BookmarkService.getInstance().save(bookmark1);
        BookmarkService.getInstance().save(bookmark2);
        BookmarkService.getInstance().save(bookmark3);
        Bookmark saved1 = BookmarkService.getInstance().getBookmark(bookmark1);
        Bookmark saved2 = BookmarkService.getInstance().getBookmark(bookmark2);
        Bookmark saved3 = BookmarkService.getInstance().getBookmark(bookmark3);

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
        assertTrue(body.contains(saved3.getDbId()));
        assertTrue(body.contains("http://localhost:9000/bookmarkEntry.ts?bookmarkId=" + saved1.getDbId()));
        assertTrue(body.contains("http://localhost:9000/bookmarkEntry.ts?bookmarkId=" + saved2.getDbId()));
        assertTrue(body.contains("http://localhost:9000/bookmarkEntry.ts?bookmarkId=" + saved3.getDbId()));
        assertTrue(exchange.getResponseHeaders().getFirst("Content-Type").contains("vnd.apple.mpegurl"));

        String miscEntry = extInfLine(saved3, HttpM3u8BookmarkPlayListServer.MISC_GROUP_TITLE);
        String moviesEntry = extInfLine(saved1, "Movies");
        String kidsEntry = extInfLine(saved2, "Kids");
        assertTrue(body.contains(miscEntry));
        assertTrue(body.contains(moviesEntry));
        assertTrue(body.contains(kidsEntry));

        String allTab = I18n.tr("commonAll");
        assertTrue(!body.contains("group-title=\"" + allTab + "\""));
        assertTrue(!body.contains(extInfLine(saved1, HttpM3u8BookmarkPlayListServer.MISC_GROUP_TITLE)));
        assertTrue(!body.contains(extInfLine(saved2, HttpM3u8BookmarkPlayListServer.MISC_GROUP_TITLE)));
        assertTrue(body.indexOf(miscEntry) < body.indexOf(moviesEntry));
        assertTrue(body.indexOf(kidsEntry) > body.indexOf(moviesEntry));
    }

    private String extInfLine(Bookmark bookmark, String groupTitle) {
        return "#EXTINF:-1 tvg-id=\"" + bookmark.getDbId()
                + "\" tvg-name=\"" + bookmark.getChannelName()
                + "\" group-title=\"" + groupTitle + "\"," + bookmark.getChannelName();
    }
}
