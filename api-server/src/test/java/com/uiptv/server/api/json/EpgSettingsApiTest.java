package com.uiptv.server.api.json;

import com.uiptv.model.Configuration;
import com.uiptv.service.ConfigurationService;
import com.uiptv.server.TestHttpExchange;
import com.uiptv.testsupport.DbBackedTest;
import org.json.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EpgSettingsApiTest extends DbBackedTest {
    @Test void configReportsDefaultOffAndNeverExposesSourceOrMappings() throws Exception {
        var request = new TestHttpExchange("/config", "GET"); new HttpConfigJsonServer().handle(request);
        assertFalse(new JSONObject(request.getResponseBodyText()).getBoolean("epgEnabled"));
        Configuration config = ConfigurationService.getInstance().read(); config.setEpgEnabled(true);
        config.setGlobalEpgSource("/private/account-guide.xml.gz"); config.setEpgMappingsJson("{\"private\":{}}"); ConfigurationService.getInstance().save(config);
        request = new TestHttpExchange("/config", "GET"); new HttpConfigJsonServer().handle(request);
        JSONObject json = new JSONObject(request.getResponseBodyText()); assertTrue(json.getBoolean("epgEnabled"));
        assertFalse(json.has("globalEpgSource")); assertFalse(json.has("epgMappingsJson")); assertFalse(request.getResponseBodyText().contains("private"));
    }
    @Test void refreshRequiresPostAndPreservesSavedConfiguration() throws Exception {
        Configuration config = ConfigurationService.getInstance().read();
        config.setGlobalEpgSource("https://example.test/india.xml.gz\n/local/uk.xml");
        config.setEpgMappingsJson("{\"account\":{\"channel\":\"guide-id\"}}");
        ConfigurationService.getInstance().save(config);
        var get = new TestHttpExchange("/epgCache", "GET");
        new HttpEpgCacheJsonServer().handle(get);
        assertEquals(405, get.getResponseCode());
        var post = new TestHttpExchange("/epgCache", "POST");
        new HttpEpgCacheJsonServer().handle(post);
        assertEquals(200, post.getResponseCode());
        assertTrue(new JSONObject(post.getResponseBodyText()).getBoolean("cleared"));
        var saved = ConfigurationService.getInstance().read();
        assertFalse(saved.isEpgEnabled());
        assertEquals(config.getGlobalEpgSource(), saved.getGlobalEpgSource());
        assertEquals(config.getEpgMappingsJson(), saved.getEpgMappingsJson());
    }
    @Test void directEndpointsRespectDefaultOffEvenWithMalformedIds() throws Exception {
        var request = new TestHttpExchange("/liveGuide?accountId=missing&visibleIds=1,2", "GET");
        new HttpLiveGuideJsonServer().handle(request); assertEquals(0, new JSONArray(request.getResponseBodyText()).length());
        request = new TestHttpExchange("/playerEpg?accountId=missing&channelId=local-row&categoryId=invalid", "GET");
        new HttpPlayerEpgJsonServer().handle(request); JSONObject json = new JSONObject(request.getResponseBodyText());
        assertTrue(json.isNull("now")); assertTrue(json.isNull("next"));
    }
}
