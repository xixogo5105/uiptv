package com.uiptv.server.api.json;

import com.uiptv.application.*;
import com.uiptv.server.TestHttpExchange;
import org.junit.jupiter.api.Test;
import org.json.JSONObject;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpPlayerEpgJsonServerTest {
    @Test void oneLiveRequestReturnsSummaryAndDetailsWithoutCaching() throws Exception {
        var current = new EpgProgramme("Current", "Description", 100, 200);
        var next = new EpgProgramme("Next", "Next description", 200, 300);
        var guide = new PlayerEpgApplicationService.GuideResult(
                new PlayerEpgApplicationService.Result("Test", current, next), java.util.List.of(current, next));
        try (var mocks = mockConstruction(PlayerEpgApplicationService.class, (service, context) -> {
            when(service.lookupGuide(any())).thenReturn(guide);
        })) {
            var exchange = new TestHttpExchange("/playerEpg?bookmarkId=bookmark-one&providerChannelId=provider-one&channelName=Test%20TV&epgId=xml-one&details=1", "GET");
            new HttpPlayerEpgJsonServer().handle(exchange);
            var json = new JSONObject(exchange.getResponseBodyText());
            assertEquals("Description", json.getJSONObject("now").getString("description"));
            assertEquals(2, json.getJSONArray("programmes").length());
            assertEquals("no-store", exchange.getResponseHeaders().getFirst("Cache-Control"));
            var captured = org.mockito.ArgumentCaptor.forClass(PlayerEpgApplicationService.Query.class);
            verify(mocks.constructed().getFirst(), times(1)).lookupGuide(captured.capture());
            assertEquals("bookmark-one", captured.getValue().bookmarkId());
            assertEquals("provider-one", captured.getValue().providerChannelId());
            assertEquals("Test TV", captured.getValue().channelName());
            assertEquals("xml-one", captured.getValue().epgId());
            verifyNoMoreInteractions(mocks.constructed().getFirst());
        }
    }
}
