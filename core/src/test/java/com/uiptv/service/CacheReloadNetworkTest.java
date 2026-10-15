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
    @Test void hundredsOfGenresDoNotCauseHundredsOfFallbackRequestsOrDestroyExistingCache() throws Exception { exercise(false, false); }
    @Test void bulkRefreshOfTwoThousandChannelsUsesFourRequestsAndNoArtworkRequests() throws Exception { exercise(true, false); }
    @Test void partialBulkResponseCannotReplaceExistingCatalogue() throws Exception { exercise(false, true); }
    private void exercise(boolean successfulBulk, boolean partialBulk) throws Exception {
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
            if ((successfulBulk || partialBulk) && query.contains("get_all_channels")) {
                var channels = new org.json.JSONArray();
                for (int i = 1; i <= 2000; i++) channels.put(new org.json.JSONObject()
                        .put("id", "stream-" + i).put("name", "Fixture channel " + i).put("number", "" + i)
                        .put("cmd", "http://example.invalid/stream").put("cmd_1", "").put("cmd_2", "").put("cmd_3", "")
                        .put("logo", "").put("tv_genre_id", "1"));
                body = new org.json.JSONObject().put("js", new org.json.JSONObject().put("data", channels).put("total_items", partialBulk ? 4000 : 2000)).toString();
            }
            if (query.contains("get_ordered_list")) pages.incrementAndGet();
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
            assertEquals(4, calls.get(), "Bulk refresh must use a fixed number of calls, never one per category/channel");
            if (successfulBulk) assertTrue(logs.contains("Catalogue network requests: 4"));
            assertEquals(0, pages.get());
            assertEquals(successfulBulk ? 2000 : 1, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
            if (!successfulBulk) assertEquals("Existing", CategoryDb.get().getCategories(account).getFirst().getTitle());
        } finally { server.stop(0); }
    }
}
