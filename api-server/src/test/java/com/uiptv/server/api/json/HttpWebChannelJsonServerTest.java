package com.uiptv.server.api.json;

import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogPagedChannelsResult;
import com.uiptv.application.CatalogWebChannelsQuery;
import com.uiptv.model.Channel;
import com.uiptv.server.TestHttpExchange;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpWebChannelJsonServerTest {

    @Test
    void handle_serializesPagedFacadeResult() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("series-a");
        channel.setName("Series A");

        try (MockedStatic<CatalogApplicationService> facadeStatic = Mockito.mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = Mockito.mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            Mockito.when(facade.listWebChannels(Mockito.any(CatalogWebChannelsQuery.class)))
                    .thenReturn(new CatalogPagedChannelsResult(List.of(channel), 1, true, 0));

            TestHttpExchange exchange = new TestHttpExchange("/channels?accountId=1&mode=series&categoryId=cat-1&page=0&pageSize=120&prefetchPages=3&apiOffset=0", "GET");
            new HttpWebChannelJsonServer().handle(exchange);

            JSONObject response = new JSONObject(exchange.getResponseBodyText());
            assertEquals(1, response.getJSONArray("items").length());
            assertEquals("Series A", response.getJSONArray("items").getJSONObject(0).getString("name"));
            assertEquals(1, response.getInt("nextPage"));
            assertTrue(response.getBoolean("hasMore"));
        }
    }
}
