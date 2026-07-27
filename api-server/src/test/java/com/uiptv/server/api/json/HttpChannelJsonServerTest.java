package com.uiptv.server.api.json;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;
import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogChannelsQuery;
import com.uiptv.application.CatalogMode;
import com.uiptv.model.Channel;
import com.uiptv.server.TestHttpExchange;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpChannelJsonServerTest {

    @Test
    void handle_serializesFacadeChannels() throws Exception {
        Channel channel = new Channel();
        channel.setChannelId("ch-1");
        channel.setName("News");

        try (MockedStatic<CatalogApplicationService> facadeStatic = mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            when(facade.listChannels(new CatalogChannelsQuery("1", CatalogMode.VOD, "cat-1", "")))
                    .thenReturn(List.of(channel));

            TestHttpExchange exchange = new TestHttpExchange("/channels?accountId=1&mode=vod&categoryId=cat-1&movieId=", "GET");
            new HttpChannelJsonServer().handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            assertTrue(exchange.getResponseBodyText().contains("News"));
            assertTrue(exchange.getResponseBodyText().contains("ch-1"));
        }
    }
}
