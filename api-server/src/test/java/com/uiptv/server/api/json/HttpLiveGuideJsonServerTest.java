package com.uiptv.server.api.json;

import com.uiptv.application.*;
import com.uiptv.model.Channel;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.testsupport.DbBackedTest;
import org.json.*;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class HttpLiveGuideJsonServerTest extends DbBackedTest {
    @Test void exposesProgrammeTimesAndKeepsLocalAndProviderIdsSeparate() throws Exception {
        Channel channel = new Channel(); channel.setDbId("local-17"); channel.setChannelId("BBCOne.uk"); channel.setName("BBC One");
        LiveGuideApplicationService.Row row = new LiveGuideApplicationService.Row("local-account", "Test", "", channel,
                List.of(new EpgProgramme("News", "Details", 1000, 2000)));
        try (var construction = mockConstruction(LiveGuideApplicationService.class, (mock, context) -> when(mock.list("local-account")).thenReturn(List.of(row)))) {
            TestHttpExchange exchange = new TestHttpExchange("/liveGuide?accountId=local-account", "GET");
            new HttpLiveGuideJsonServer().handle(exchange);
            assertEquals(200, exchange.getResponseCode());
            JSONObject response = new JSONArray(exchange.getResponseBodyText()).getJSONObject(0);
            assertEquals("local-17", response.getJSONObject("channel").getString("dbId"));
            assertEquals("BBCOne.uk", response.getJSONObject("channel").getString("channelId"));
            JSONObject programme = response.getJSONArray("programmes").getJSONObject(0);
            assertEquals("News", programme.getString("title")); assertEquals(1000, programme.getLong("start"));
            assertEquals(2000, programme.getLong("stop"));
        }
    }
    @Test void rejectsMutationMethods() throws Exception {
        TestHttpExchange exchange = new TestHttpExchange("/liveGuide", "POST");
        new HttpLiveGuideJsonServer().handle(exchange);
        assertEquals(405, exchange.getResponseCode());
    }
}
