package com.uiptv.service;

import com.sun.net.httpserver.HttpServer;
import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.json.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class StalkerCategoryPagingTest extends DbBackedTest {
    @Test void categoryOnlyPortalLoadsEveryPageBeyondFormer24RequestLimit() throws Exception { exercise(false); }
    @Test void failedLaterPagePreservesExistingCache() throws Exception { exercise(true); }
    private void exercise(boolean failLater) throws Exception {
        AtomicInteger pages = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/portal.php", exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String body = "{\"js\":{\"data\":[]}}";
            if (query.contains("get_genres")) body = "{\"js\":[{\"id\":\"10\",\"title\":\"News\",\"alias\":\"news\"}]}";
            if (query.contains("get_ordered_list")) {
                pages.incrementAndGet();
                int page = 0;
                for (String pair : query.split("&")) if (pair.startsWith("p=")) page = Integer.parseInt(pair.substring(2));
                if (failLater && page == 25) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
                int id = Math.max(1, page); // p=0 aliases p=1, as on many real portals.
                JSONObject row = new JSONObject().put("id", id).put("name", "Channel " + id).put("number", "" + id)
                        .put("cmd", "http://example.invalid/stream").put("cmd_1", "").put("cmd_2", "").put("cmd_3", "")
                        .put("logo", "").put("tv_genre_id", "10");
                body = new JSONObject().put("js", new JSONObject().put("total_items", 30).put("max_page_items", 1)
                        .put("data", new JSONArray().put(row))).toString();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            Account account = new Account(); account.setAccountName("Paged Stalker fixture"); account.setType(AccountType.STALKER_PORTAL);
            account.setUrl("http://127.0.0.1:" + server.getAddress().getPort()); account.setServerPortalUrl(account.getUrl() + "/portal.php");
            account.setMacAddress("00:11:22:33:44:55"); account.setAction(Account.AccountAction.itv);
            AccountDb.get().save(account); account = AccountService.getInstance().getByName(account.getAccountName()); account.setAction(Account.AccountAction.itv);
            CategoryDb.get().saveAll(List.of(new Category("old", "Existing", "", false, 0)), account);
            Channel old = new Channel(); old.setChannelId("old"); old.setName("Existing channel");
            ChannelDb.get().saveAll(List.of(old), CategoryDb.get().getCategories(account).getFirst().getDbId(), account);
            try (var handshake = mockStatic(HandshakeService.class)) {
                var service = mock(HandshakeService.class); handshake.when(HandshakeService::getInstance).thenReturn(service);
                doAnswer(call -> { ((Account)call.getArgument(0)).setToken("fixture"); return null; }).when(service).connect(any());
                Account target = account;
                if (failLater) assertThrows(IllegalStateException.class, () -> new CacheServiceImpl().reloadCache(target, _ -> {}));
                else new CacheServiceImpl().reloadCache(target, _ -> {});
            }
            assertEquals(failLater ? 1 : 30, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
            assertEquals(failLater ? 26 : 31, pages.get());
            if (failLater) assertEquals("Existing", CategoryDb.get().getCategories(account).getFirst().getTitle());
        } finally { server.stop(0); }
    }
}
