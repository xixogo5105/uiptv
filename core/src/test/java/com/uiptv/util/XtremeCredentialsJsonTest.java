package com.uiptv.util;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class XtremeCredentialsJsonTest {

    @Test
    void parseAndResolveDefaultEntry() {
        String json = """
                [
                  {"username":"user1","password":"pass1","default":true},
                  {"username":"user2","password":"pass2"}
                ]
                """;
        List<XtremeCredentialsJson.Entry> entries = XtremeCredentialsJson.parse(json);
        assertEquals(2, entries.size());
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(entries);
        assertNotNull(defaultEntry);
        assertEquals("user1", defaultEntry.username());
        assertEquals("pass1", defaultEntry.password());
    }

    @Test
    void normalizeAppliesDefaultUsernameWhenProvided() {
        List<XtremeCredentialsJson.Entry> entries = new ArrayList<>();
        entries.add(new XtremeCredentialsJson.Entry("alpha", "p1", false));
        entries.add(new XtremeCredentialsJson.Entry("beta", "p2", false));

        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(entries, "beta");
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(normalized);
        assertNotNull(defaultEntry);
        assertEquals("beta", defaultEntry.username());
    }

    @Test
    void toJsonKeepsSingleDefault() {
        List<XtremeCredentialsJson.Entry> entries = List.of(
                new XtremeCredentialsJson.Entry("first", "p1", false),
                new XtremeCredentialsJson.Entry("second", "p2", false)
        );
        String json = XtremeCredentialsJson.toJson(entries);
        List<XtremeCredentialsJson.Entry> parsed = XtremeCredentialsJson.parse(json);
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(parsed);
        assertNotNull(defaultEntry);
        assertFalse(defaultEntry.username().isBlank());
    }
}
