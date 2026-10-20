package com.uiptv.application;

import com.uiptv.db.EpgCacheDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EpgBuilderApplicationServiceCoverageTest {

    @Test
    void schemaAndInventoryValidationRejectIncompatibleBundles() throws Exception {
        JSONObject valid = new JSONObject().put("schema_version", 1);
        invokeStatic("validateSchema", new Class<?>[]{JSONObject.class}, valid);
        JSONObject unsupportedSchema = new JSONObject().put("schema_version", 2);
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("validateSchema",
                new Class<?>[]{JSONObject.class}, unsupportedSchema));

        JSONObject inventory = new JSONObject().put("database_identity", "db").put("config_signature", "config")
                .put("revision", "revision").put("generation", 1);
        JSONObject bundle = new JSONObject().put("database_identity", "db").put("config_signature", "config")
                .put("inventory_revision", "revision").put("inventory_generation", 1);
        assertTrue((Boolean) invokeStatic("mappingsCurrent", new Class<?>[]{JSONObject.class, JSONObject.class}, bundle, inventory));
        assertFalse((Boolean) invokeStatic("mappingsCurrent", new Class<?>[]{JSONObject.class, JSONObject.class},
                new JSONObject(bundle.toString()).put("inventory_revision", "changed"), inventory));
        invokeStatic("validateInventory", new Class<?>[]{JSONObject.class, JSONObject.class, boolean.class}, bundle, inventory, false);
        JSONObject wrongIdentity = new JSONObject(bundle.toString()).put("database_identity", "other");
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("validateInventory",
                new Class<?>[]{JSONObject.class, JSONObject.class, boolean.class},
                wrongIdentity, inventory, true));
    }

    @Test
    void timestampAndPriorityValidationRejectUnsafeBundles() throws Exception {
        long now = System.currentTimeMillis();
        assertEquals(now, (long) invokeStatic("bundleCreatedAt", new Class<?>[]{JSONObject.class, long.class}, new JSONObject(), now));
        JSONObject future = new JSONObject().put("created_at", java.time.Instant.ofEpochMilli(now + 301_000).toString());
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("bundleCreatedAt",
                new Class<?>[]{JSONObject.class, long.class},
                future, now));
        JSONObject stale = new JSONObject().put("created_at", java.time.Instant.ofEpochMilli(now - 86_401_000).toString());
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("bundleCreatedAt",
                new Class<?>[]{JSONObject.class, long.class},
                stale, now));

        JSONArray sources = new JSONArray().put(new JSONObject().put("source_id", "one"))
                .put(new JSONObject().put("source_id", "two").put("priority", 3));
        @SuppressWarnings("unchecked")
        Map<String, Integer> priorities = (Map<String, Integer>) invokeStatic("declaredPriorities",
                new Class<?>[]{JSONArray.class}, sources);
        assertEquals(Map.of("one", 100, "two", 3), priorities);
        JSONArray duplicateSources = new JSONArray().put(new JSONObject().put("source_id", "one"))
                .put(new JSONObject().put("source_id", "one"));
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("declaredPriorities",
                new Class<?>[]{JSONArray.class}, duplicateSources));
        JSONArray negativePriority = new JSONArray().put(new JSONObject().put("source_id", "one").put("priority", -1));
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("declaredPriorities",
                new Class<?>[]{JSONArray.class}, negativePriority));
        JSONArray emptyProgrammes = new JSONArray();
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("validateProgrammeCount",
                new Class<?>[]{JSONArray.class}, emptyProgrammes));
    }

    @Test
    void programmeReaderFiltersExpiredEntriesAndRejectsInvalidRecords() throws Exception {
        long now = System.currentTimeMillis();
        long created = now - 3_600_000L;
        Map<String, Integer> declared = Map.of("public", 100);
        Map<String, Map<String, List<EpgProgramme>>> sources = new LinkedHashMap<>();
        JSONArray programmes = new JSONArray()
                .put(new JSONObject().put("source_id", "public").put("guide_channel_id", "guide")
                        .put("start", now - 60_000).put("stop", now + 3_600_000).put("title", "Current"))
                .put(new JSONObject().put("source_id", "public").put("guide_channel_id", "guide")
                        .put("start", now - 7 * 3_600_000L).put("stop", now - 6 * 3_600_000L - 30_000).put("title", "Expired"));
        invokeStatic("readProgrammes", new Class<?>[]{JSONArray.class, Map.class, long.class, long.class, Map.class},
                programmes, declared, created, now, sources);
        assertEquals(1, sources.get("builder:" + EpgCacheApplicationService.hash("public")).get("guide").size());
        assertEquals("Current", sources.get("builder:" + EpgCacheApplicationService.hash("public")).get("guide").getFirst().title());

        JSONObject invalid = new JSONObject().put("source_id", "missing").put("guide_channel_id", "guide")
                .put("start", now).put("stop", now + 1_000).put("title", "Invalid");
        JSONArray invalidProgrammes = new JSONArray().put(invalid);
        Map<String, Map<String, List<EpgProgramme>>> invalidSources = new LinkedHashMap<>();
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("readProgrammes",
                new Class<?>[]{JSONArray.class, Map.class, long.class, long.class, Map.class},
                invalidProgrammes, declared, created, now, invalidSources));
        JSONObject reversed = new JSONObject().put("source_id", "public").put("guide_channel_id", "guide")
                .put("start", now + 1_000).put("stop", now).put("title", "Invalid");
        JSONArray reversedProgrammes = new JSONArray().put(reversed);
        Map<String, Map<String, List<EpgProgramme>>> reversedSources = new LinkedHashMap<>();
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("readProgrammes",
                new Class<?>[]{JSONArray.class, Map.class, long.class, long.class, Map.class},
                reversedProgrammes, declared, created, now, reversedSources));
    }

    @Test
    void sharedNamesAndIconsOnlyExposeValidKnownGuides() throws Exception {
        Map<String, List<EpgProgramme>> schedules = new LinkedHashMap<>();
        schedules.put("guide", List.of(new EpgProgramme("Title", "", 1, 2)));
        JSONObject aliases = new JSONObject().put("Station HD", "guide").put("Unknown", "missing");
        @SuppressWarnings("unchecked")
        Map<String, String> names = (Map<String, String>) invokeStatic("sharedNames",
                new Class<?>[]{JSONObject.class, Map.class}, aliases, schedules);
        assertEquals("guide", names.values().iterator().next());
        assertTrue(names.size() >= 2);

        @SuppressWarnings("unchecked")
        Map<String, String> icons = (Map<String, String>) invokeStatic("sharedIcons",
                new Class<?>[]{JSONObject.class, Map.class},
                new JSONObject().put("guide", "https://img.test/logo.png")
                        .put("missing", "https://img.test/missing.png").put("bad", "file:///private"), schedules);
        assertEquals(Map.of("guide", "https://img.test/logo.png"), icons);
        JSONObject oversizedAlias = new JSONObject().put("x".repeat(513), "guide");
        assertThrows(IllegalArgumentException.class, () -> invokeStatic("sharedNames",
                new Class<?>[]{JSONObject.class, Map.class}, oversizedAlias, schedules));
    }

    @Test
    void channelInventoryRedactsPrivateDataAndHandlesProviderMetadata() throws Exception {
        Account account = new Account();
        account.setDbId("account");
        account.setType(AccountType.M3U8_LOCAL);
        Channel channel = new Channel();
        channel.setDbId("row");
        channel.setChannelId("provider");
        channel.setName("Station");
        channel.setExtraJson("{\"xmltv_id\":\"guide\",\"epg_id\":\"legacy\",\"tvg-id\":\"tvg\"}");
        JSONObject result = (JSONObject) invokeStatic("channelInventory",
                new Class<?>[]{Account.class, Channel.class, boolean.class, String.class, String.class, String.class},
                account, channel, true, "bookmark", "manual", "signature");
        assertEquals("guide", result.getString("xmltv_id"));
        assertEquals("manual", result.getString("manual_override"));
        assertTrue(result.getBoolean("is_manual_override"));
        assertEquals("", result.getString("extra_json"));

        account.setType(AccountType.XTREME_API);
        channel.setExtraJson("not-json");
        JSONObject xtreme = (JSONObject) invokeStatic("channelInventory",
                new Class<?>[]{Account.class, Channel.class, boolean.class, String.class, String.class, String.class},
                account, channel, false, "", "", "signature");
        assertEquals("", xtreme.getString("epg_channel_id"));
        assertFalse(xtreme.getBoolean("is_manual_override"));
    }

    @Test
    void sharedSourceReaderHonoursDisabledFlagAndSourceMetadata() throws Exception {
        Map<String, List<EpgProgramme>> schedule = new LinkedHashMap<>();
        schedule.put("guide", new ArrayList<>(List.of(new EpgProgramme("Title", "", 1, 2))));
        Map<String, Map<String, List<EpgProgramme>>> sources = new LinkedHashMap<>();
        String id = "builder:" + EpgCacheApplicationService.hash("public");
        sources.put(id, schedule);
        JSONObject disabled = new JSONObject().put("shared_guides", false);
        @SuppressWarnings("unchecked")
        Map<String, EpgCacheDb.SharedSource> empty = (Map<String, EpgCacheDb.SharedSource>) invokeStatic("readSharedSources",
                new Class<?>[]{JSONObject.class, Map.class}, disabled, sources);
        assertTrue(empty.isEmpty());

        JSONObject enabled = new JSONObject().put("shared_guides", true).put("sources", new JSONArray().put(
                new JSONObject().put("source_id", "public").put("priority", 7).put("countries", new JSONArray().put("GB"))
                        .put("languages", new JSONArray().put("en")).put("names", new JSONObject().put("Station", "guide"))));
        @SuppressWarnings("unchecked")
        Map<String, EpgCacheDb.SharedSource> shared = (Map<String, EpgCacheDb.SharedSource>) invokeStatic("readSharedSources",
                new Class<?>[]{JSONObject.class, Map.class}, enabled, sources);
        assertEquals(1, shared.size());
        assertEquals(7, shared.get(id).priority());
        assertEquals("GB", shared.get(id).countries());
        assertEquals("en", shared.get(id).languages());
    }

    private static Object invokeStatic(String name, Class<?>[] parameterTypes, Object... arguments) throws Exception {
        Method method = EpgBuilderApplicationService.class.getDeclaredMethod(name, parameterTypes);
        method.setAccessible(true);
        try {
            return method.invoke(null, arguments);
        } catch (InvocationTargetException error) {
            if (error.getCause() instanceof Exception exception) throw exception;
            if (error.getCause() instanceof Error fatal) throw fatal;
            throw error;
        }
    }
}
