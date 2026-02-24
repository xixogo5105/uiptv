package com.uiptv.server.api.json;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpContext;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpPrincipal;
import com.uiptv.db.CategoryDb;
import com.uiptv.model.Account;
import com.uiptv.model.BookmarkCategory;
import com.uiptv.model.Category;
import com.uiptv.service.AccountService;
import com.uiptv.service.BookmarkService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpBookmarksJsonServerTest extends DbBackedTest {

    @Test
    void bookmarksServer_postGetDelete_roundTripWorks() throws Exception {
        Account account = createAccount("bookmark-api");
        CategoryDb.get().saveAll(List.of(new Category("10", "Sports", "sports", false, 0)), account);
        Category sports = CategoryDb.get().getCategories(account).get(0);

        HttpBookmarksJsonServer handler = new HttpBookmarksJsonServer();

        JSONObject payload = new JSONObject();
        payload.put("accountId", account.getDbId());
        payload.put("categoryId", sports.getDbId());
        payload.put("mode", "itv");
        payload.put("channelId", "ch-100");
        payload.put("name", "Sports One");
        payload.put("cmd", "ffmpeg http://stream/100.ts");
        payload.put("logo", "http://img/100.png");

        StubHttpExchange postExchange = new StubHttpExchange("/bookmarks", "POST", payload.toString());
        handler.handle(postExchange);

        assertEquals(200, postExchange.getResponseCode());
        JSONObject postResponse = new JSONObject(postExchange.getResponseBodyText());
        assertEquals("ok", postResponse.getString("status"));
        assertTrue(postResponse.getString("action").equals("saved") || postResponse.getString("action").equals("exists"));
        String bookmarkId = postResponse.optString("bookmarkId", "");
        assertTrue(!bookmarkId.isBlank());

        StubHttpExchange getExchange = new StubHttpExchange("/bookmarks", "GET", null);
        handler.handle(getExchange);
        assertEquals(200, getExchange.getResponseCode());
        JSONArray afterPost = new JSONArray(getExchange.getResponseBodyText());
        assertEquals(1, afterPost.length());
        assertEquals("Sports One", afterPost.getJSONObject(0).optString("channelName", ""));

        StubHttpExchange deleteExchange = new StubHttpExchange("/bookmarks?bookmarkId=" + bookmarkId, "DELETE", null);
        handler.handle(deleteExchange);
        assertEquals(200, deleteExchange.getResponseCode());
        JSONObject deleteResponse = new JSONObject(deleteExchange.getResponseBodyText());
        assertEquals("ok", deleteResponse.getString("status"));

        StubHttpExchange getAfterDelete = new StubHttpExchange("/bookmarks", "GET", null);
        handler.handle(getAfterDelete);
        assertEquals(200, getAfterDelete.getResponseCode());
        JSONArray afterDelete = new JSONArray(getAfterDelete.getResponseBodyText());
        assertEquals(0, afterDelete.length());
    }

    @Test
    void bookmarksServer_getCategories_andPutOrder_updatesBookmarkOrder() throws Exception {
        Account account = createAccount("bookmark-api-order");
        CategoryDb.get().saveAll(List.of(new Category("10", "Sports", "sports", false, 0)), account);
        Category sports = CategoryDb.get().getCategories(account).get(0);

        BookmarkService.getInstance().addCategory(new BookmarkCategory(null, "Favorites A"));
        BookmarkService.getInstance().addCategory(new BookmarkCategory(null, "Favorites B"));

        HttpBookmarksJsonServer handler = new HttpBookmarksJsonServer();

        StubHttpExchange categoriesExchange = new StubHttpExchange("/bookmarks?view=categories", "GET", null);
        handler.handle(categoriesExchange);
        assertEquals(200, categoriesExchange.getResponseCode());
        JSONArray categories = new JSONArray(categoriesExchange.getResponseBodyText());
        assertEquals(2, categories.length());

        String[] channelNames = {"Sports One", "Sports Two", "Sports Three"};
        for (int i = 0; i < channelNames.length; i++) {
            JSONObject payload = new JSONObject();
            payload.put("accountId", account.getDbId());
            payload.put("categoryId", sports.getDbId());
            payload.put("mode", "itv");
            payload.put("channelId", "ch-" + (200 + i));
            payload.put("name", channelNames[i]);
            payload.put("cmd", "ffmpeg http://stream/" + (200 + i) + ".ts");

            StubHttpExchange postExchange = new StubHttpExchange("/bookmarks", "POST", payload.toString());
            handler.handle(postExchange);
            assertEquals(200, postExchange.getResponseCode());
        }

        StubHttpExchange getBeforeReorder = new StubHttpExchange("/bookmarks", "GET", null);
        handler.handle(getBeforeReorder);
        JSONArray before = new JSONArray(getBeforeReorder.getResponseBodyText());
        assertEquals(3, before.length());

        JSONArray orderedIds = new JSONArray();
        orderedIds.put(before.getJSONObject(2).getString("dbId"));
        orderedIds.put(before.getJSONObject(1).getString("dbId"));
        orderedIds.put(before.getJSONObject(0).getString("dbId"));

        JSONObject reorderPayload = new JSONObject();
        reorderPayload.put("categoryId", sports.getDbId());
        reorderPayload.put("orderedBookmarkDbIds", orderedIds);

        StubHttpExchange putExchange = new StubHttpExchange("/bookmarks", "PUT", reorderPayload.toString());
        handler.handle(putExchange);
        assertEquals(200, putExchange.getResponseCode());
        assertEquals("reordered", new JSONObject(putExchange.getResponseBodyText()).optString("action"));

        StubHttpExchange getAfterReorder = new StubHttpExchange("/bookmarks", "GET", null);
        handler.handle(getAfterReorder);
        JSONArray after = new JSONArray(getAfterReorder.getResponseBodyText());
        assertEquals(List.of("Sports Three", "Sports Two", "Sports One"), List.of(
                after.getJSONObject(0).optString("channelName", ""),
                after.getJSONObject(1).optString("channelName", ""),
                after.getJSONObject(2).optString("channelName", "")
        ));
    }

    private Account createAccount(String name) {
        Account account = new Account(name, "user", "pass", "http://test.com", "00:11:22:33:44:55", null, null, null, null, null, AccountType.M3U8_URL, null, "http://test.com/playlist.m3u8", false);
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    private static class StubHttpExchange extends HttpExchange {
        private final URI requestUri;
        private final String method;
        private final Headers requestHeaders = new Headers();
        private final Headers responseHeaders = new Headers();
        private final ByteArrayOutputStream responseBody = new ByteArrayOutputStream();
        private final byte[] requestBodyBytes;
        private int responseCode = -1;

        StubHttpExchange(String uri, String method, String body) {
            this.requestUri = URI.create(uri);
            this.method = method;
            this.requestBodyBytes = body == null ? new byte[0] : body.getBytes(StandardCharsets.UTF_8);
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
            return new ByteArrayInputStream(requestBodyBytes);
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
