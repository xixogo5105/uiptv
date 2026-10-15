package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import org.json.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class EpgBuilderApplicationServiceTest extends DbBackedTest {
    private final EpgBuilderApplicationService service = new EpgBuilderApplicationService();
    private Account account;
    private Channel channel;
    private long now;

    @BeforeEach void setup() {
        EpgCacheRefreshService.stop();
        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(true); config.setEpgMappingsJson("{}");
        ConfigurationService.getInstance().save(config);
        account = new Account(); account.setAccountName("builder-test"); account.setType(AccountType.M3U8_LOCAL);
        account.setAction(Account.AccountAction.itv); account.setPassword("PRIVATE_PASSWORD");
        account.setUrl("https://private.invalid/PRIVATE_URL");
        AccountDb.get().save(account); account = AccountService.getInstance().getByName("builder-test");
        account.setAction(Account.AccountAction.itv);
        CategoryDb.get().saveAll(List.of(new Category("provider-category", "Live", "Live", false, 0)), account);
        String category = CategoryDb.get().getCategories(account).getFirst().getDbId();
        channel = new Channel(); channel.setChannelId("provider-channel"); channel.setName("Station");
        channel.setExtraJson("{\"xmltv_id\":\"guide\",\"cmd\":\"PRIVATE_STREAM\"}");
        ChannelDb.get().saveAll(List.of(channel), category, account);
        channel = ChannelDb.get().getCachedLiveChannels(account.getDbId()).getFirst();
        now = System.currentTimeMillis();
    }
    @AfterEach void stop() { EpgCacheRefreshService.stop(); }

    private JSONObject bundle() throws Exception {
        JSONObject inventory = service.exportInventory(testDbFile.toString());
        JSONObject target = inventory.getJSONArray("channels").getJSONObject(0);
        return new JSONObject().put("schema_version", 1).put("database_identity", inventory.getString("database_identity"))
                .put("config_signature", inventory.getString("config_signature"))
                .put("inventory_revision", inventory.getString("revision")).put("inventory_generation", inventory.getInt("generation"))
                .put("sources", new JSONArray().put(new JSONObject().put("source_id", "public")))
                .put("programmes", new JSONArray().put(new JSONObject().put("source_id", "public").put("guide_channel_id", "guide")
                        .put("start", now - 60000).put("stop", now + 3600000).put("title", "Public programme")))
                .put("mappings", new JSONArray().put(new JSONObject().put("account_id", target.getString("account_id"))
                        .put("provider_channel_id", target.getString("provider_channel_id")).put("signature", target.getString("signature"))
                        .put("source_id", "public").put("guide_channel_id", "guide")));
    }
    private Path save(JSONObject bundle) throws Exception {
        Path file = tempDir.resolve("bundle.json"); Files.writeString(file, bundle.toString()); return file;
    }
    private void apply(JSONObject bundle) throws Exception { service.importBundle(testDbFile.toString(), save(bundle).toString()); }

    @Test void inventoryUsesCoreIdentitiesAndDoesNotExportPrivateProviderData() throws Exception {
        JSONObject inventory = service.exportInventory(testDbFile.toString());
        assertEquals(EpgCacheApplicationService.identity(account, channel, ConfigurationService.getInstance().read()).signature(),
                inventory.getJSONArray("channels").getJSONObject(0).getString("signature"));
        assertFalse(inventory.toString().contains("PRIVATE_"));
        assertEquals(1, inventory.getJSONArray("channels").length());
    }
    @Test void streamedInventoryMatchesMaterializedIdentitiesAndRevision() throws Exception {
        JSONObject expected = service.exportInventory(testDbFile.toString());
        var output = new java.io.StringWriter();
        service.writeInventory(testDbFile.toString(), output);
        var lines = output.toString().lines().map(JSONObject::new).toList();
        assertEquals("header", lines.getFirst().getString("record_type"));
        assertEquals(expected.getString("database_identity"), lines.getFirst().getString("database_identity"));
        assertEquals(expected.getString("revision"), lines.getLast().getString("revision"));
        assertEquals(expected.getJSONArray("channels").length(), lines.getLast().getInt("channel_count"));
        JSONObject record = lines.get(1);
        assertEquals("channel", record.remove("record_type"));
        assertTrue(expected.getJSONArray("channels").getJSONObject(0).similar(record));
        assertFalse(output.toString().contains("PRIVATE_"));
        var jsonOutput = new java.io.StringWriter();
        service.writeInventoryJson(testDbFile.toString(), jsonOutput);
        assertTrue(expected.similar(new JSONObject(jsonOutput.toString())));
    }

    @Test void parsedSourceSchedulesDiscardInvalidIntervalsBeforeBundleConstruction() throws Exception {
        String start = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
                .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(now));
        String stop = java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss Z")
                .withZone(java.time.ZoneOffset.UTC).format(java.time.Instant.ofEpochMilli(now + 3_600_000));
        Path xml = tempDir.resolve("intervals.xml");
        Files.writeString(xml, "<tv><channel id=\"guide\"><display-name>Station</display-name></channel>"
                + "<programme channel=\"guide\" start=\"" + start + "\" stop=\"" + start + "\"><title>Invalid</title></programme>"
                + "<programme channel=\"guide\" start=\"" + start + "\" stop=\"" + stop + "\"><title>Valid</title></programme></tv>");
        Path request = tempDir.resolve("parse.json");
        Files.writeString(request, new JSONObject().put("path", xml.toString()).put("now", now).toString());
        JSONArray programmes = service.parseGuide(request.toString()).getJSONObject("programmes").getJSONArray("guide");
        assertEquals(1, programmes.length());
        assertEquals("Valid", programmes.getJSONObject(0).getString("title"));
        JSONObject bundle = bundle();
        JSONObject programme = programmes.getJSONObject(0).put("source_id", "public").put("guide_channel_id", "guide");
        bundle.put("programmes", new JSONArray().put(programme));
        assertTrue(service.validateBundle(testDbFile.toString(), save(bundle).toString()).getBoolean("valid"));
    }

    @Test void programmeAgingOutDuringValidationDoesNotInvalidateUsableBundle() throws Exception {
        JSONObject bundle = bundle().put("created_at", java.time.Instant.ofEpochMilli(now - 60_000).toString());
        bundle.getJSONArray("programmes").put(new JSONObject().put("source_id", "public").put("guide_channel_id", "guide")
                .put("start", now - 7 * 3_600_000L).put("stop", now - 6 * 3_600_000L - 30_000)
                .put("title", "Aged out during processing"));
        apply(bundle);
        assertEquals(1, service.showStatus(testDbFile.toString()).getInt("programme_count"));
        bundle.getJSONArray("programmes").getJSONObject(1).put("stop", now - 6 * 3_600_000L - 120_000);
        assertThrows(IllegalArgumentException.class, () -> apply(bundle));
    }

    @Test void importsAreVisibleIdempotentAndSurviveApplicationRemapping() throws Exception {
        JSONObject bundle = bundle(); apply(bundle); apply(bundle);
        assertEquals("Public programme", new EpgCacheApplicationService().schedule(account, channel).getFirst().title());
        new EpgCacheDb().mapChannels(EpgCacheApplicationService.matches(account, List.of(channel), List.of()), now);
        assertEquals("Public programme", new EpgCacheApplicationService().schedule(account, channel).getFirst().title());
        assertEquals(1, service.showStatus(testDbFile.toString()).getInt("programme_count"));
        assertEquals(bundle.getString("database_identity"), service.exportInventory(testDbFile.toString()).getString("database_identity"));
    }
    @Test void nativeAndAccountMappingsRetainPriority() throws Exception {
        var db = new EpgCacheDb();
        var identity = EpgCacheApplicationService.identity(account, channel, ConfigurationService.getInstance().read());
        db.replaceSource("account-source", Map.of("guide", List.of(new EpgProgramme("User programme", "", now-60000, now+3600000))), Map.of(), Map.of(), now, now+1000);
        db.mapChannels(List.of(new EpgCacheDb.Match(identity, "guide", "", "", true, List.of("account-source"), "")), now);
        apply(bundle());
        assertEquals("User programme", new EpgCacheApplicationService().schedule(account, channel).getFirst().title());
    }
    @Test void validationAndImportRejectStaleSignaturesAndMalformedProgrammesWithoutWrites() throws Exception {
        JSONObject invalid = bundle(); invalid.getJSONArray("mappings").getJSONObject(0).put("signature", "stale");
        Path file = save(invalid);
        assertThrows(IllegalArgumentException.class, () -> service.validateBundle(testDbFile.toString(), file.toString()));
        assertThrows(IllegalArgumentException.class, () -> apply(invalid));
        JSONObject bad = bundle(); bad.getJSONArray("programmes").getJSONObject(0).put("stop", 0);
        assertThrows(IllegalArgumentException.class, () -> apply(bad));
        assertEquals(0, service.showStatus(testDbFile.toString()).getInt("programme_count"));
    }
    @Test void mappingFailureRollsBackReplacedSource() throws Exception {
        JSONObject good = bundle(); apply(good);
        try (var c = SQLConnection.connect(); var statement = c.createStatement()) {
            statement.execute("CREATE TRIGGER reject_builder BEFORE INSERT ON epg_channel_mapping BEGIN SELECT RAISE(ABORT, 'test failure'); END");
        }
        JSONObject changed = bundle(); changed.getJSONArray("programmes").getJSONObject(0).put("title", "Must roll back");
        assertThrows(DatabaseAccessException.class, () -> apply(changed));
        assertEquals("Public programme", new EpgCacheApplicationService().schedule(account, channel).getFirst().title());
    }
    @Test void missingDatabaseIsNotCreated() {
        Path missing = tempDir.resolve("missing.db");
        assertThrows(IllegalArgumentException.class, () -> service.exportInventory(missing.toString()));
        assertFalse(Files.exists(missing));
    }
}
