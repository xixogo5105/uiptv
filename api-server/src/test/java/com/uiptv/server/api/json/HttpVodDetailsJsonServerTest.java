package com.uiptv.server.api.json;

import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogVodDetailsQuery;
import com.uiptv.application.CatalogVodDetailsResult;
import com.uiptv.server.TestHttpExchange;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class HttpVodDetailsJsonServerTest {

    @Test
    void handle_serializesFacadeResult() throws Exception {
        CatalogVodDetailsResult result = new CatalogVodDetailsResult(
                "Movie Nine",
                "https://img/provider.png",
                "IMDB Plot",
                "",
                "",
                "",
                "2024-06-01",
                "8.7",
                "",
                "https://www.imdb.com/title/tt1234567/",
                "120"
        );

        try (MockedStatic<CatalogApplicationService> facadeStatic = Mockito.mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = Mockito.mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            Mockito.when(facade.getVodDetails(new CatalogVodDetailsQuery("1", "cat-1", "vod-9", "Movie Nine")))
                    .thenReturn(result);

            TestHttpExchange exchange = new TestHttpExchange("/vodDetails?accountId=1&categoryId=cat-1&channelId=vod-9&vodName=Movie+Nine", "GET");
            new HttpVodDetailsJsonServer().handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            JSONObject response = new JSONObject(exchange.getResponseBodyText()).getJSONObject("vodInfo");
            assertEquals("Movie Nine", response.getString("name"));
            assertEquals("https://img/provider.png", response.getString("cover"));
            assertEquals("IMDB Plot", response.getString("plot"));
            assertEquals("8.7", response.getString("rating"));
            assertEquals("2024-06-01", response.getString("releaseDate"));
            assertEquals("https://www.imdb.com/title/tt1234567/", response.getString("imdbUrl"));
        }
    }
}
