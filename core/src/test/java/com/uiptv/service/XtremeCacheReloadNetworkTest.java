package com.uiptv.service;

import com.sun.net.httpserver.HttpServer;
import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.json.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class XtremeCacheReloadNetworkTest extends DbBackedTest {
    @Test void categoryOnlyProviderLoadsAll62CategoriesBeyondFormerRequestLimit() throws Exception {
        AtomicInteger calls = new AtomicInteger();
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/player_api.php", exchange -> {
            calls.incrementAndGet();
            String query = exchange.getRequestURI().getQuery();
            String body = "[]";
            if (query.contains("get_live_categories")) {
                JSONArray categories = new JSONArray();
                for (int i = 1; i <= 62; i++) categories.put(new JSONObject().put("category_id", i).put("category_name", "Category " + i));
                body = categories.toString();
            } else if (query.contains("get_live_streams")) {
                if (!query.contains("category_id=")) { exchange.sendResponseHeaders(503, -1); exchange.close(); return; }
                String id = query.substring(query.indexOf("category_id=") + 12);
                body = new JSONArray().put(new JSONObject().put("stream_id", id).put("name", "Channel " + id)
                        .put("epg_channel_id", "guide-" + id).put("cmds", new JSONArray().put("unused"))).toString();
            }
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, bytes.length); exchange.getResponseBody().write(bytes); exchange.close();
        });
        server.start();
        try {
            Account account = new Account(); account.setAccountName("Category-only fixture"); account.setType(AccountType.XTREME_API);
            account.setUrl("http://127.0.0.1:" + server.getAddress().getPort() + "/");
            account.setUsername("fixture"); account.setPassword("fixture"); account.setAction(Account.AccountAction.itv);
            AccountDb.get().save(account); account = AccountService.getInstance().getByName(account.getAccountName()); account.setAction(Account.AccountAction.itv);
            new CacheServiceImpl().reloadCache(account, _ -> {});
            assertEquals(62, ChannelDb.get().getChannelCountForAccount(account.getDbId()));
            Channel saved = ChannelDb.get().getCachedLiveChannels(account.getDbId()).getFirst();
            JSONObject metadata = new JSONObject(saved.getExtraJson());
            assertFalse(metadata.has("cmds"));
            assertTrue(metadata.getString("epg_channel_id").startsWith("guide-"));
            assertEquals(66, calls.get()); // Live categories, bulk, 62 categories, VOD and series categories.
        } finally { server.stop(0); }
    }
}
