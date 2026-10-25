package com.uiptv.util;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class ChannelExtraJsonCompactorTest {
    @Test void retainsConsumedFieldsAndTypesButNotCommandsAndProviderInternals() throws Exception {
        Properties policy = new Properties();
        try (var input = getClass().getResourceAsStream("/metadata/channel-extra-fields.properties")) { policy.load(input); }
        JSONObject source = new JSONObject();
        for (String field : policy.getProperty("channel").split(",")) source.put(field, "value-" + field);
        source.put("year", 2026).put("rating", 4.5).put("category", true)
                .put("cmd", "keep-in-column").put("cmd_1", "alternate").put("cmd_2", "alternate2").put("cmd_3", "alternate3")
                .put("cmds", new JSONArray().put("x".repeat(100_000)))
                .put("series", new JSONArray().put(1).put(2))
                .put("provider_internal", new JSONObject().put("huge", "x".repeat(100_000)));
        JSONObject compact = new JSONObject(ChannelExtraJsonCompactor.compact(source));
        for (String field : policy.getProperty("channel").split(",")) {
            if (source.get(field) instanceof Number expected) {
                assertInstanceOf(Number.class, compact.get(field));
                assertEquals(expected.doubleValue(), compact.getNumber(field).doubleValue(), field);
            } else assertEquals(source.get(field), compact.get(field), field);
        }
        for (String field : List.of("cmd", "cmd_1", "cmd_2", "cmd_3", "cmds", "series", "provider_internal")) assertFalse(compact.has(field));
        assertTrue(compact.toString().length() < 2000);
        assertTrue(compact.similar(new JSONObject(ChannelExtraJsonCompactor.compact(compact))));
        assertEquals("keep-in-column", source.getString("cmd"));
    }

    @Test void absentNullMalformedAndUnexpectedValuesCannotRestoreRawPayload() {
        for (String source : new String[]{null, "", " ", "not-json", "[]", "42", "{bad"}) {
            assertEquals("{}", ChannelExtraJsonCompactor.compact(source));
        }
        assertEquals("{}", ChannelExtraJsonCompactor.compact((JSONObject) null));
        JSONObject source = new JSONObject().put("plot", JSONObject.NULL)
                .put("genre", new JSONArray().put("unhandled"))
                .put("rating", new JSONObject().put("unexpected", true));
        assertEquals("{}", ChannelExtraJsonCompactor.compact(source));
    }

    @Test void m3uIsOpaqueIncludingUnknownNestedOptions() {
        JSONObject options = new JSONObject("""
                {"tvg-shift":"-2.5","http-user-agent":"Agent","http-referrer":"https://example.test",
                 "extvlcopt":{"http-cookie":"cookie","http-referrer":"referrer"},
                 "custom":{"array":[1,false,{"unknown":"keep"}]}}
                """);
        JSONObject source = new JSONObject().put("m3u", options);
        JSONObject result = new JSONObject(ChannelExtraJsonCompactor.compact(source));
        assertTrue(source.similar(result));
        assertTrue(result.similar(new JSONObject(ChannelExtraJsonCompactor.compact(result))));
        result.getJSONObject("m3u").put("new", true);
        assertFalse(options.has("new"));
    }
}
