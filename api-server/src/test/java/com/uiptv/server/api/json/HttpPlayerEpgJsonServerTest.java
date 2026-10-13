package com.uiptv.server.api.json;

import com.uiptv.application.*;
import com.uiptv.server.TestHttpExchange;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpPlayerEpgJsonServerTest {
    @Test void returnsCurrentAndNextDetailsForThePlayingBookmark() throws Exception {
        var query = new PlayerEpgApplicationService.Query("", "", "", "bookmark-one");
        try (var mocks = mockConstruction(PlayerEpgApplicationService.class, (service, context) -> when(service.lookup(any())).thenReturn(
                new PlayerEpgApplicationService.Result("Test", new EpgProgramme("Current", "Description", 100, 200), new EpgProgramme("Next", "Next description", 200, 300))))) {
            TestHttpExchange exchange = new TestHttpExchange("/playerEpg?bookmarkId=bookmark-one", "GET");
            new HttpPlayerEpgJsonServer().handle(exchange);
            JSONObject json = new JSONObject(exchange.getResponseBodyText());
            assertEquals("Description", json.getJSONObject("now").getString("description")); assertEquals("Next", json.getJSONObject("next").getString("title"));
            var captured = org.mockito.ArgumentCaptor.forClass(PlayerEpgApplicationService.Query.class);
            verify(mocks.constructed().getFirst()).lookup(captured.capture()); assertEquals(query.bookmarkId(), captured.getValue().bookmarkId());
        }
    }
}
