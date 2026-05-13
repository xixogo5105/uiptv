package com.uiptv.server.api.json;

import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogSeriesDetailsQuery;
import com.uiptv.application.CatalogSeriesDetailsResult;
import com.uiptv.server.TestHttpExchange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpSeriesDetailsJsonServerTest {

    @Test
    void handle_serializesFacadeResult() throws Exception {
        JSONObject seasonInfo = new JSONObject().put("name", "Series Title").put("releaseDate", "2021");
        JSONArray episodes = new JSONArray().put(new JSONObject().put("channelId", "ep-1").put("name", "Episode 1"));
        JSONArray episodesMeta = new JSONArray().put(new JSONObject().put("season", "1").put("episodeNum", "1"));

        try (MockedStatic<CatalogApplicationService> facadeStatic = Mockito.mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = Mockito.mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            Mockito.when(facade.getSeriesDetails(new CatalogSeriesDetailsQuery("acc-1", "cat-1", "series-1", "Show (2021)")))
                    .thenReturn(new CatalogSeriesDetailsResult(seasonInfo, episodes, episodesMeta));

            TestHttpExchange exchange = new TestHttpExchange(
                    "/seriesDetails?accountId=acc-1&seriesId=series-1&categoryId=cat-1&seriesName=Show%20(2021)",
                    "GET"
            );
            new HttpSeriesDetailsJsonServer().handle(exchange);

            JSONObject response = new JSONObject(exchange.getResponseBodyText());
            assertEquals("Series Title", response.getJSONObject("seasonInfo").getString("name"));
            assertEquals("2021", response.getJSONObject("seasonInfo").getString("releaseDate"));
            assertEquals(1, response.getJSONArray("episodes").length());
            assertTrue(response.getJSONArray("episodesMeta").length() > 0);
        }
    }
}
