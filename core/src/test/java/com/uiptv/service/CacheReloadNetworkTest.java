package com.uiptv.service;

import com.sun.net.httpserver.HttpServer;
import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import java.net.InetSocketAddress;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CacheReloadNetworkTest extends DbBackedTest {
    @Test void failedCategoryEndpointStopsImmediatelyAndRetainsExistingCache() throws Exception { exercise(false, false); }
    @Test void bulkRefreshOfTwoThousandChannelsUsesFourRequestsAndNoArtworkRequests() throws Exception { exercise(true, false); }
    @Test void bulkResponseWithOverstatedTotalStillRefreshesUsingFourRequests() throws Exception { exercise(true, true); }
    @Test void rejectedDefaultBulkShapeRetriesWithExplicitPagination() throws Exception { exercise(true, false, true); }
    private void exercise(boolean successfulBulk, boolean overstatedTotal) throws Exception {
        exercise(successfulBulk, overstatedTotal, false);
    }
    @Test void catalogueLargerThanFormerResponseCapStillLoadsInFourRequests() throws Exception { exercise(true, false, false, true); }
    private void exercise(boolean successfulBulk, boolean overstatedTotal, boolean rejectDefaultShape) throws Exception {
        exercise(successfulBulk, overstatedTotal, rejectDefaultShape, false);
    }
    private void exercise(boolean successfulBulk, boolean overstatedTotal, boolean rejectDefaultShape, boolean large) throws Exception {
        List<String> logs = new ArrayList<>();
        AtomicInteger calls = new AtomicInteger(), pages = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/portal.php", exchange -> {
            calls.incrementAndGet(); String query = exchange.getRequestURI().getQuery();
            String body = "{\"js\":{\"data\":[]}}";
            if (query.contains("get_genres")) {
                var categories = new org.json.JSONArray();
                for (int i = 1; i <= 568; i++) categories.put(new org.json.JSONObject().put("id", "" + i).put("title", "Category " + i).put("alias", "category-" + i));
                body = new org.json.JSONObject().put("js", categories).toString();
            }
            if (rejectDefaultShape && query.contains("get_all_channels") && !query.contains("p=")) {
                exchange.sendResponseHeaders(400, -1);
                exchange.close();
                return;
            }
            if (large && query.contains("get_all_channels")) {
                try {
                    exchange.getResponseHeaders().set("Content-Encoding", "gzip");
                    exchange.sendResponseHeaders(200, 0);
                    var writer = new java.io.OutputStreamWriter(new java.util.zip.GZIPOutputStream(exchange.getResponseBody()), java.nio.charset.StandardCharsets.UTF_8);
                    writer.write("{\"js\":{\"data\":[");
                    for (int i = 1; i <= 2000; i++) {
                        if (i > 1) writer.write(",");
                        writer.write(new org.json.JSONObject().put("id", "stream-" + i).put("name", "Fixture channel " + i)
                            .put("number", "" + i).put("cmd", "http://example.invalid/stream").put("cmd_1", "")
                            .put("cmd_2", "").put("cmd_3", "").put("logo", "").put("tv_genre_id", "1")
                            .put("provider_metadata", "x".repeat(12000)).toString());
                    }
                    writer.write("]},\"metadata\":{\"ignored\":[1,2,3]}}"); writer.close();
                } finally { exchange.close(); }
                return;
            }
            if (successfulBulk && query.contains("get_all_channels")) {
                var channels = new org.json.JSONArray();
                for (int i = 1; i <= 2000; i++) channels.put(new org.json.JSONObject()
                        .put("id", "stream-" + i).put("name", "Fixture channel " + i).put("number", "" + i)
                        .put("cmd", "http://example.invalid/stream").put("cmd_1", "").put("cmd_2", "").put("cmd_3", "")
                        .put("logo", "").put("tv_genre_id", "1"));
                body = new org.json.JSONObject().put("js", new org.json.JSONObject().put("data", channels).put("total_items", overstatedTotal ? 4000 : 2000)).toString();
            }
            if (query.contains("get_ordered_list")) {
                pages.incrementAndGet();
                if (!successfulBulk) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
            }
            byte[] bytes = body.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        }); server.start();
        try {
            Account account = new Account(); account.setAccountName("Local reload fixture"); account.setType(AccountType.STALKER_PORTAL);
            account.setUrl("http://127.0.0.1:" + server.getAddress().getPort()); account.setServerPortalUrl(account.getUrl() + "/portal.php");
            account.setUsername("fixture"); account.setPassword("fixture"); account.setMacAddress("00:11:22:33:44:55");
            account.setAction(Account.AccountAction.itv); AccountDb.get().save(account);
            account = AccountService.getInstance().getByName("Local reload fixture"); account.setAction(Account.AccountAction.itv);
            CategoryDb.get().saveAll(List.of(new Category("old", "Existing", "", false, 0)), account);
            String categoryId = CategoryDb.get().getCategories(account).getFirst().getDbId();
            Channel channel = new Channel(); channel.setChannelId("existing"); channel.setName("Existing channel");
            ChannelDb.get().saveAll(List.of(channel), categoryId, account);
            try (var handshake = mockStatic(HandshakeService.class)) {
                var service = mock(HandshakeService.class); handshake.when(HandshakeService::getInstance).thenReturn(service);
                doAnswer(call -> { ((Account) call.getArgument(0)).setToken("fixture"); return null; }).when(service).connect(any());
                Account target = account;
                if (successfulBulk) new CacheServiceImpl().reloadCache(target, logs::add);
                else assertThrows(IllegalStateException.class, () -> new CacheServiceImpl().reloadCache(target, logs::add));
            }
            assertEquals(!successfulBulk || rejectDefaultShape ? 5 : 4, calls.get(), "Bulk refresh must use a fixed number of calls, never one per category/channel");
            assertFalse(logs.stream().anyMatch(line -> line.startsWith("Catalogue request ")
                    || line.startsWith("Catalogue network requests:")), "Routine network counters must not reach the user log");
            assertEquals(successfulBulk ? 0 : 1, pages.get());
            assertEquals(successfulBulk ? 2000 : 1, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
            if (!successfulBulk) assertEquals("Existing", CategoryDb.get().getCategories(account).getFirst().getTitle());
        } finally { server.stop(0); }
    }
}
