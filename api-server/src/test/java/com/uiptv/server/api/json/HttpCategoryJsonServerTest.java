package com.uiptv.server.api.json;

import com.uiptv.application.CatalogApplicationService;
import com.uiptv.application.CatalogMode;
import com.uiptv.model.Category;
import com.uiptv.server.TestHttpExchange;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HttpCategoryJsonServerTest {

    @Test
    void handle_returnsEmptyArrayWhenFacadeReturnsNoCategories() throws Exception {
        try (MockedStatic<CatalogApplicationService> facadeStatic = Mockito.mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = Mockito.mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            Mockito.when(facade.listCategories("404", CatalogMode.ITV)).thenReturn(List.of());

            TestHttpExchange exchange = new TestHttpExchange("/category?accountId=404", "GET");
            new HttpCategoryJsonServer().handle(exchange);

            assertEquals(200, exchange.getResponseCode());
            assertEquals("[]", exchange.getResponseBodyText());
        }
    }

    @Test
    void handle_parsesModeAndPassesItToFacade() throws Exception {
        try (MockedStatic<CatalogApplicationService> facadeStatic = Mockito.mockStatic(CatalogApplicationService.class)) {
            CatalogApplicationService facade = Mockito.mock(CatalogApplicationService.class);
            facadeStatic.when(CatalogApplicationService::getInstance).thenReturn(facade);
            Mockito.when(facade.listCategories("1", CatalogMode.SERIES)).thenReturn(List.of(new Category("10", "Series", "series", false, 0)));
            Mockito.when(facade.listCategories("1", CatalogMode.ITV)).thenReturn(List.of(new Category("20", "Live", "live", false, 0)));

            TestHttpExchange validMode = new TestHttpExchange("/category?accountId=1&mode=SERIES", "GET");
            new HttpCategoryJsonServer().handle(validMode);
            Mockito.verify(facade).listCategories("1", CatalogMode.SERIES);
            assertTrue(validMode.getResponseBodyText().contains("Series"));

            TestHttpExchange invalidMode = new TestHttpExchange("/category?accountId=1&mode=not-a-mode", "GET");
            new HttpCategoryJsonServer().handle(invalidMode);
            Mockito.verify(facade).listCategories("1", CatalogMode.ITV);
            assertTrue(invalidMode.getResponseBodyText().contains("Live"));
        }
    }
}
