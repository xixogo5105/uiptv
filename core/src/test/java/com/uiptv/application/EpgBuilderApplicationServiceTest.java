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

    private JSONObject sharedBundle() throws Exception {
        JSONObject bundle = bundle().put("shared_guides", true).put("mappings", new JSONArray());
        bundle.getJSONArray("sources").getJSONObject(0)
                .put("names", new JSONObject().put("Star Sports 1 HD", "guide"))
                .put("countries", new JSONArray().put("IN"));
        return bundle;
    }

    @Test void sharedGuideWorksForAccountsAndTypesCreatedAfterImportWithoutRemapping() throws Exception {
        JSONObject sourceOnly = sharedBundle();
        AccountService.getInstance().deleteAll();
        apply(sourceOnly);
        for (AccountType type : List.of(AccountType.M3U8_LOCAL, AccountType.XTREME_API, AccountType.STALKER_PORTAL)) {
            var added = new Account(); added.setAccountName("later-" + type); added.setType(type);
            AccountDb.get().save(added); added = AccountService.getInstance().getByName(added.getAccountName());
            added.setAction(Account.AccountAction.itv);
            var station = new Channel(); station.setChannelId("different-provider-id"); station.setName("┃WC┃ STAR SPORTS 1 HD");
            var cache = new EpgCacheApplicationService();
            assertEquals("Public programme", cache.summaries(added, List.of(station)).get(station).getFirst().title());
            assertEquals("Public programme", cache.schedule(added, station).getFirst().title());
        }
        try (var c = SQLConnection.connect(); var st = c.createStatement(); var r = st.executeQuery("SELECT count(*) FROM epg_channel_mapping")) {
            assertTrue(r.next()); assertEquals(0,r.getInt(1), "Shared reads must not need or write account mappings");
        }
    }

    @Test void accountChangesDuringCollectionDoNotInvalidateSharedSourcePublication() throws Exception {
        JSONObject bundle = sharedBundle();
        var later = new Account(); later.setAccountName("added-during-download"); later.setType(AccountType.XTREME_API);
        AccountDb.get().save(later);
        bundle.put("inventory_revision", "old-account-inventory");
        bundle.put("config_signature", "old-account-settings");
        apply(bundle);
        var station = new Channel(); station.setChannelId("new"); station.setName("Star Sports 1 HD");
        assertEquals("Public programme",new EpgCacheApplicationService().schedule(account,station).getFirst().title());
    }

    @Test void publicationUsesBindingsRevalidatedAfterTakingTheWriterLock() throws Exception {
        JSONObject data = sharedBundle();
        data.put("mappings",bundle().getJSONArray("mappings"));
        Map<String,Account> original = AccountService.getInstance().getAllById();
        var accountService = org.mockito.Mockito.mock(AccountService.class);
        org.mockito.Mockito.when(accountService.getAllById()).thenReturn(original,original,Map.of(),Map.of());
        try (var singleton = org.mockito.Mockito.mockStatic(AccountService.class)) {
            singleton.when(AccountService::getInstance).thenReturn(accountService);
            apply(data);
        }
        try (var c = SQLConnection.connect(); var st = c.createStatement(); var rows = st.executeQuery("SELECT count(*) FROM epg_channel_mapping")) {
            assertTrue(rows.next()); assertEquals(0,rows.getInt(1),"Bindings invalidated before publication must not be inserted");
        }
        assertEquals(1,service.showStatus(testDbFile.toString()).getInt("programme_count"),"Shared guide publication remains valid");
    }

    @Test void sharedGuidesRespectManualOverridesRegionsAndTimeshifts() throws Exception {
        apply(sharedBundle());
        var station = new Channel(); station.setChannelId("new"); station.setName("┃USA┃ Star Sports 1 HD");
        var cache = new EpgCacheApplicationService();
        assertTrue(cache.schedule(account,station).isEmpty(), "A known country prefix cannot match another country's feed");
        station.setName("Star Sports 1 HD +1");
        assertTrue(cache.schedule(account,station).isEmpty());
        station.setName("Star Sports 1 HD");
        var config = ConfigurationService.getInstance().read();
        config.setEpgMappingsJson(new JSONObject().put(account.getDbId(),new JSONObject().put("new","missing-guide")).toString());
        ConfigurationService.getInstance().save(config);
        assertTrue(cache.schedule(account,station).isEmpty(), "Explicit overrides must not fall through to a name match");
    }

    @Test void ambiguousSharedAliasesStayUnmatchedAndNativeMappingsKeepPrecedence() throws Exception {
        JSONObject bundle = sharedBundle();
        bundle.getJSONArray("sources").getJSONObject(0).getJSONObject("names").put("Star Sports 1 SD","other");
        bundle.getJSONArray("programmes").put(new JSONObject().put("source_id","public").put("guide_channel_id","other")
                .put("start",now-60000).put("stop",now+3600000).put("title","Different station"));
        apply(bundle);
        var station = new Channel(); station.setChannelId("new"); station.setName("Star Sports 1");
        assertTrue(new EpgCacheApplicationService().schedule(account,station).isEmpty());
        station.setName("Star Sports 1 HD");
        var id = EpgCacheApplicationService.identity(account,station,ConfigurationService.getInstance().read());
        var db = new EpgCacheDb();
        db.replaceSource("native-test",Map.of("new",List.of(new EpgProgramme("Native programme","",now-60000,now+3600000))),Map.of(),Map.of(),now,now+3600000);
        db.mapChannels(List.of(new EpgCacheDb.Match(id,"new","","",true,List.of("native-test"),"native-test")),now);
        assertEquals("Native programme",new EpgCacheApplicationService().schedule(account,station).getFirst().title());
    }

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
        JSONObject channelRecord = lines.get(1);
        assertEquals("channel", channelRecord.remove("record_type"));
        assertTrue(expected.getJSONArray("channels").getJSONObject(0).similar(channelRecord));
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
        String databasePath = testDbFile.toString();
        String bundlePath = file.toString();
        assertThrows(IllegalArgumentException.class, () -> service.validateBundle(databasePath, bundlePath));
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
        String missingPath = missing.toString();
        assertThrows(IllegalArgumentException.class, () -> service.exportInventory(missingPath));
        assertFalse(Files.exists(missing));
    }

    @Test void mergeSchedulesCombinesOverlappingSourcesFromRequestFile() throws Exception {
        long start = now - 60_000;
        Path request = tempDir.resolve("merge-schedules.json");
        JSONObject first = new JSONObject().put("title", "First").put("description", "").put("start", start)
                .put("stop", start + 1_800_000);
        JSONObject second = new JSONObject().put("title", "Second").put("description", "details").put("start", start + 1_200_000)
                .put("stop", start + 3_600_000);
        JSONArray sourceOne = new JSONArray().put(first);
        JSONArray sourceTwo = new JSONArray().put(second);
        Files.writeString(request, new JSONObject().put("schedules", new JSONArray()
                .put(new JSONArray().put(sourceOne).put(sourceTwo))).toString());

        JSONArray schedules = service.mergeSchedules(request.toString()).getJSONArray("schedules");
        assertEquals(1, schedules.length());
        assertFalse(schedules.getJSONArray(0).isEmpty());
        assertEquals("First", schedules.getJSONArray(0).getJSONObject(0).getString("title"));
    }
}
