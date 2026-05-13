package com.uiptv.server.api.json;

import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogSeriesEpisodesQuery;
import com.uiptv.model.Channel;
import com.uiptv.server.TestHttpExchange;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSeriesEpisodesJsonServerTest {

    @Test
    void handle_serializesEpisodesFromFacade() throws Exception {
        Channel episode = new Channel();
        episode.setChannelId("ep-21");
        episode.setName("Episode 21");
        episode.setSeason("2");
        episode.setEpisodeNum("1");
        episode.setWatched(true);

        try (MockedStatic<CatalogApplicationService> facadeStatic = Mockito.mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = Mockito.mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            Mockito.when(facade.listSeriesEpisodes(new CatalogSeriesEpisodesQuery("1", "cat-1", "series-21")))
                    .thenReturn(List.of(episode));

            TestHttpExchange exchange = new TestHttpExchange("/seriesEpisodes?accountId=1&categoryId=cat-1&seriesId=series-21", "GET");
            new HttpSeriesEpisodesJsonServer().handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            assertTrue(exchange.getResponseBodyText().contains("Episode 21"));
            assertTrue(exchange.getResponseBodyText().contains("\"watched\":\"1\""));
        }
    }

    @Test
    void handle_returnsEmptyArrayWhenFacadeReturnsNoEpisodes() throws Exception {
        try (MockedStatic<CatalogApplicationService> facadeStatic = Mockito.mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = Mockito.mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            Mockito.when(facade.listSeriesEpisodes(new CatalogSeriesEpisodesQuery("missing", "", "")))
                    .thenReturn(List.of());

            TestHttpExchange exchange = new TestHttpExchange("/seriesEpisodes?accountId=missing&categoryId=&seriesId=", "GET");
            new HttpSeriesEpisodesJsonServer().handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            assertEquals("[]", exchange.getResponseBodyText());
        }
    }
}
