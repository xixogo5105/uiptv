package com.uiptv.server.api.json;

import com.uiptv.application.EpgProgramme;
import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.testsupport.DbBackedTest;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EpgSettingsApiTest extends DbBackedTest {
    @Test void programmeDetailResponsesRespectClientLimitAndHardMaximum() {
        var programmes = java.util.stream.IntStream.range(0, 250)
                .mapToObj(index -> new EpgProgramme("Programme " + index, "", index, index + 1))
                .toList();

        assertEquals(24, HttpPlayerEpgJsonServer.boundedSchedule(programmes, "24").size());
        assertEquals(100, HttpPlayerEpgJsonServer.boundedSchedule(programmes, "1000").size());
        assertEquals(100, HttpPlayerEpgJsonServer.boundedSchedule(programmes, "invalid").size());
        assertEquals(1, HttpPlayerEpgJsonServer.boundedSchedule(programmes, "0").size());
    }

    @Test void configReportsDefaultOffAndNeverExposesSourceOrMappings() throws Exception {
        var request = new TestHttpExchange("/config", "GET"); new HttpConfigJsonServer().handle(request);
        assertFalse(new JSONObject(request.getResponseBodyText()).getBoolean("epgEnabled"));
        Configuration config = ConfigurationService.getInstance().read(); config.setEpgEnabled(true);
        config.setGlobalEpgSource("/private/account-guide.xml.gz"); config.setEpgMappingsJson("{\"private\":{}}"); ConfigurationService.getInstance().save(config);
        request = new TestHttpExchange("/config", "GET"); new HttpConfigJsonServer().handle(request);
        JSONObject json = new JSONObject(request.getResponseBodyText()); assertTrue(json.getBoolean("epgEnabled"));
        assertFalse(json.has("globalEpgSource")); assertFalse(json.has("epgMappingsJson")); assertFalse(request.getResponseBodyText().contains("private"));
    }
    @Test void webConfigCannotChangeTheDesktopEpgSetting() throws Exception {
        for (boolean enabled : new boolean[]{false, true}) {
            Configuration config = ConfigurationService.getInstance().read();
            config.setEpgEnabled(enabled); ConfigurationService.getInstance().save(config);
            var request = new TestHttpExchange("/config", "POST", "{\"epgEnabled\":" + !enabled + "}");
            new HttpConfigJsonServer().handle(request);
            assertEquals(enabled, ConfigurationService.getInstance().read().isEpgEnabled());
            var read = new TestHttpExchange("/config", "GET"); new HttpConfigJsonServer().handle(read);
            assertEquals(enabled, new JSONObject(read.getResponseBodyText()).getBoolean("epgEnabled"));
            assertEquals("no-store", read.getResponseHeaders().getFirst("Cache-Control"));
        }
    }
    @Test void directEndpointsRespectDefaultOffEvenWithMalformedIds() throws Exception {
        var request = new TestHttpExchange("/liveGuide?accountId=missing&visibleIds=1,2", "GET");
        new HttpLiveGuideJsonServer().handle(request); assertEquals(0, new JSONArray(request.getResponseBodyText()).length());
        request = new TestHttpExchange("/playerEpg?accountId=missing&channelId=local-row&categoryId=invalid", "GET");
        new HttpPlayerEpgJsonServer().handle(request); JSONObject json = new JSONObject(request.getResponseBodyText());
        assertTrue(json.isNull("now")); assertTrue(json.isNull("next"));
    }
}
