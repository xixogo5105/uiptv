package com.uiptv.application;

import com.uiptv.service.ChannelService;
import com.uiptv.util.ReloadRequestScope;
import org.json.*;
import org.junit.jupiter.api.Test;
import java.io.StringReader;
import java.time.Duration;
import static org.junit.jupiter.api.Assertions.*;

class StalkerCatalogueReaderTest {
    private JSONObject row(String id) {
        return new JSONObject().put("id", id).put("name", "Station " + id).put("number", id)
                .put("cmd", "http://example.invalid/" + id).put("cmd_1", "").put("cmd_2", "")
                .put("cmd_3", "").put("logo", "").put("tv_genre_id", "provider-category")
                .put("provider_extra", new JSONObject().put("preserved", true));
    }
    @Test void streamingKeepsProviderMappingAndDeduplicationAndSkipsEnvelopeMetadata() {
        String json = new JSONObject().put("before", new JSONObject().put("escaped", "quote\\\"},]"))
                .put("js", new JSONObject().put("data", new JSONArray().put(row("7")).put(row("7")).put(row("8"))))
                .put("after", new JSONArray().put(new JSONObject().put("nested", new JSONArray().put("a,b")))).toString();
        try (var _ = new ReloadRequestScope(Duration.ofSeconds(5), Duration.ofSeconds(2), 4, null)) {
            var expected = ChannelService.getInstance().parseItvChannels(json, false);
            var actual = new StalkerCatalogueReader().read(new StringReader(json));
            assertEquals(2, actual.size());
            for (int i = 0; i < actual.size(); i++) {
                assertEquals(expected.get(i).getChannelId(), actual.get(i).getChannelId());
                assertEquals(expected.get(i).getCategoryId(), actual.get(i).getCategoryId());
                assertEquals(expected.get(i).getCmd(), actual.get(i).getCmd());
                assertEquals(expected.get(i).getExtraJson(), actual.get(i).getExtraJson());
            }
        }
    }
    @Test void malformedLaterRowDoesNotReturnAPartialCatalogue() {
        String json = "{\"js\":{\"data\":[" + row("1") + ",{}]}}";
        try (var _ = new ReloadRequestScope(Duration.ofSeconds(5), Duration.ofSeconds(2), 4, null)) {
            assertThrows(JSONException.class, () -> new StalkerCatalogueReader().read(new StringReader(json)));
        }
    }
}
