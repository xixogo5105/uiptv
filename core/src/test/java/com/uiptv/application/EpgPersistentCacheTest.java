package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.sql.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.LockSupport;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EpgPersistentCacheTest extends DbBackedTest {
    private final EpgCacheDb db=new EpgCacheDb();
    private final EpgCacheRefreshService refresh=new EpgCacheRefreshService();
    private final EpgCacheApplicationService cache=new EpgCacheApplicationService();
    @BeforeEach void enable() {
        EpgCacheRefreshService.stop();
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(true); config.setGlobalEpgSource("");
        config.setEpgMappingsJson("{}"); ConfigurationService.getInstance().save(config);
    }
    @AfterEach void stop() { EpgCacheRefreshService.stop(); }
    private Account account(String id) { var a=new Account(); a.setDbId(id); a.setAccountName(id); a.setType(AccountType.M3U8_LOCAL); a.setAction(Account.AccountAction.itv); return a; }
    private Channel channel(String id,String name) { var c=new Channel(); c.setChannelId(id); c.setName(name); return c; }
    private EpgProgramme p(String title,long start,long stop) { return new EpgProgramme(title,"Full details",start,stop); }
    private EpgCacheDb.Match match(EpgCacheDb.Identity id,String guide,List<String> sources) { return new EpgCacheDb.Match(id,guide,"","",true,sources,""); }
    private String title(EpgCacheDb.Identity id,long now) { return db.summaries(List.of(id),now).get(id).getFirst().title(); }
    private void source(String id,String channel,List<EpgProgramme> programmes,long now) { db.replaceSource(id,Map.of(channel,programmes),Map.of(),Map.of(),now,now+7_200_000); }
    private Path xml(String filename,String title) throws Exception {
        Path path=tempDir.resolve(filename); long now=System.currentTimeMillis();
        var format=DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC);
        Files.writeString(path,"<tv><channel id='station'><display-name>Station</display-name></channel><programme channel='station' start='"+format.format(Instant.ofEpochMilli(now-60_000))+"' stop='"+format.format(Instant.ofEpochMilli(now+3_600_000))+"'><title>"+title+"</title><desc>Full details</desc></programme></tv>");
        return path;
    }

    @Test void sourceAndMappingsRollbackTogetherWhenMappingPublicationFails() throws Exception {
        long now = System.currentTimeMillis();
        var previousIdentity = new EpgCacheDb.Identity("atomic-account", "atomic-channel", "old-signature");
        var updatedIdentity = new EpgCacheDb.Identity("atomic-account", "atomic-channel", "new-signature");
        source("atomic-source", "old-guide", List.of(p("Last good programme", now - 60_000, now + 3_600_000)), now);
        assertTrue(db.mapChannels(List.of(match(previousIdentity, "old-guide", List.of("atomic-source"))), now));
        var originalState = db.sourceState("atomic-source");
        var originalRevision = EpgChangeNotifications.snapshot();
        try (Connection connection = SQLConnection.connect(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TRIGGER fail_atomic_mapping BEFORE INSERT ON epg_channel_mapping "
                    + "WHEN NEW.account_id='atomic-account' BEGIN SELECT RAISE(ABORT,'forced mapping failure'); END");
        }

        try {
            assertThrows(RuntimeException.class, () -> db.replaceSourceAndMap("atomic-source",
                    Map.of("new-guide", List.of(p("Uncommitted programme", now - 60_000, now + 3_600_000))),
                    Map.of(), Map.of(), Map.of(), now + 1, now + 7_200_000, Set.of(),
                    List.of(match(updatedIdentity, "new-guide", List.of("atomic-source"))), _ -> { }, _ -> { }));
        } finally {
            try (Connection connection = SQLConnection.connect(); Statement statement = connection.createStatement()) {
                statement.execute("DROP TRIGGER IF EXISTS fail_atomic_mapping");
            }
        }

        assertEquals("Last good programme", title(previousIdentity, now));
        assertEquals(originalState, db.sourceState("atomic-source"));
        assertEquals(originalRevision.revision(), EpgChangeNotifications.snapshot().revision(),
                "A rolled-back source and mapping pair must not emit a change notification.");
    }

    @Test void sourceAndMappingsCommitTogetherAndPublishBothChanges() {
        long now = System.currentTimeMillis();
        var identity = new EpgCacheDb.Identity("atomic-success-account", "atomic-success-channel", "signature");
        var before = EpgChangeNotifications.snapshot();
        int matched = db.replaceSourceAndMap("atomic-success-source",
                Map.of("guide-id", List.of(p("Published programme", now - 60_000, now + 3_600_000))),
                Map.of(), Map.of(), Map.of(), now, now + 7_200_000, Set.of(),
                List.of(match(identity, "guide-id", List.of("atomic-success-source"))), _ -> { }, _ -> { });

        assertEquals(1, matched);
        assertEquals("Published programme", title(identity, now));
        var events = EpgChangeNotifications.replay(before.revision(), before.databaseGeneration());
        assertTrue(events.stream().anyMatch(event -> event.kind() == EpgChangeNotifications.Kind.SOURCE_IMPORT
                && event.sourceIds().contains("atomic-success-source")));
        assertTrue(events.stream().anyMatch(event -> event.kind() == EpgChangeNotifications.Kind.MAPPING
                && event.accountProviders().contains(new EpgChangeNotifications.ProviderIdentity(
                        "atomic-success-account", "atomic-success-channel"))));
    }

    @Test void oldSpacedAliasesMatchAnnotatedChannelsWithoutDownloadingAgain() {
        long now = System.currentTimeMillis();
        var identity = new EpgCacheDb.Identity("account", "BBCTwo.uk", "signature");
        db.replaceSource("uk",Map.of("BBC.Two.HD.uk",List.of(p("Real UK programme",now-60000,now+3600000))),
                Map.of("bbc two hd","BBC.Two.HD.uk","bbc two","empty-sd"),Map.of(),now,now+7200000);
        assertTrue(db.mapChannels(List.of(new EpgCacheDb.Match(identity,"BBCTwo.uk",
                XmltvGuide.normalize("BBC Two Ⓖ"),XmltvGuide.normalizeQuality("BBC Two Ⓖ"),false,List.of("uk"),"")),now));
        assertEquals("Real UK programme",title(identity,now));
    }

    @Test void catalogueReloadPausesEpgWithoutClearingItsCacheAndResumesIndependently() throws Exception {
        var account = account("catalogue-pause");
        var channel = channel("station", "Station");
        account.setEpg(xml("pause.xml", "Cached title").toString());
        refresh.refreshAccount(account, List.of(channel));
        xml("pause.xml", "New title");
        try (var _ = EpgCacheRefreshService.pauseForCatalogueReload()) {
            try (var _ = EpgCacheRefreshService.pauseForCatalogueReload()) {
                assertEquals("Cached title", cache.schedule(account, channel).getFirst().title());
            }
            try (var http = mockStatic(HttpUtil.class)) {
                refresh.refreshAccount(account, List.of(channel));
                http.verifyNoInteractions();
            }
            assertEquals("Cached title", cache.schedule(account, channel).getFirst().title());
        }
        assertEquals("Cached title", cache.schedule(account, channel).getFirst().title());
    }

    @Test void visibleNewAccountChannelsLoadWithoutWaitingForBulkCatalogueRotation() throws Exception {
        var account = account("visible-new-account");
        account.setEpg(xml("visible-new.xml", "Visible programme").toString());
        AccountDb.get().save(account);
        account = AccountService.getInstance().getByName("visible-new-account");
        account.setAction(Account.AccountAction.itv);
        var channel = channel("station", "Station");
        // This channel is deliberately absent from the cached catalogue and bookmarks.
        // Only the visible summary request can make the importer visit it.
        try (var _ = EpgCacheRefreshService.pauseForCatalogueReload()) {
            EpgCacheRefreshService.start();
            assertTrue(new LiveGuideApplicationService().cachedSummaries(List.of(
                    new LiveGuideApplicationService.Row(account.getDbId(), account.getAccountName(), "", channel, List.of())))
                    .getFirst().programmes().isEmpty());
            assertTrue(cache.schedule(account, channel).isEmpty());
        }
        refresh.refreshDue();
        assertEquals("Visible programme", cache.schedule(account, channel).getFirst().title());
    }

    @Test void scheduledRefreshDoesNotFanOutNativeStalkerEpgRequests() {
        var account = account("scheduled-stalker");
        account.setType(AccountType.STALKER_PORTAL);
        AccountDb.get().save(account);
        account = AccountService.getInstance().getByName("scheduled-stalker");
        var category = new Category("stalker-live", "Live", "Live", false, 0);
        CategoryDb.get().saveAll(List.of(category), account);
        category = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(List.of(channel("provider-channel", "Station")), category.getDbId(), account);

        try (var nativeService = mockConstruction(NativeEpgApplicationService.class)) {
            refresh.refreshDue();
            assertTrue(nativeService.constructed().isEmpty(),
                    "The scheduled account refresh must not call Stalker EPG per channel");
        }
    }

    @Test void ordinaryAccountRefreshDoesNotFetchNativeStalkerEpgRegardlessOfPlaybackFlag() {
        var account = account("ordinary-stalker");
        account.setType(AccountType.STALKER_PORTAL);
        var channel = channel("provider-channel", "Station");
        try (var nativeService = mockConstruction(NativeEpgApplicationService.class)) {
            var config = ConfigurationService.getInstance().read();
            for (boolean playbackOnly : List.of(false, true)) {
                config.setEpgPlaybackOnly(playbackOnly);
                ConfigurationService.getInstance().save(config);
                refresh.refreshAccount(account, List.of(channel));
            }
            assertTrue(nativeService.constructed().isEmpty(),
                    "General account refreshes must not perform native portal EPG calls under either playback flag");
        }
    }

    @Test void activePlaybackFetchesOnlyItsStalkerChannelEvenWhenFullGuideModeIsEnabled() {
        var account = account("playing-stalker");
        account.setType(AccountType.STALKER_PORTAL);
        var channel = channel("playing-channel", "Playing station");
        var config = ConfigurationService.getInstance().read();
        config.setEpgPlaybackOnly(false);
        ConfigurationService.getInstance().save(config);

        try (var nativeService = mockConstruction(NativeEpgApplicationService.class)) {
            new PlayerEpgApplicationService().currentForPlayback(account, channel);
            var constructed = nativeService.constructed();
            assertEquals(1, constructed.size());
            verify(constructed.getFirst()).schedule(account, channel);
        }
    }

    @Test void androidSyncedCacheSignatureGoldenMatchesDesktop() {
        Account account = account("987654321");
        account.setUrl(""); account.setUsername(""); account.setPassword(""); account.setMacAddress("");
        account.setEpg(""); account.setM3u8Path(""); account.setServerPortalUrl("");
        Channel channel = channel("provider-stream", "Synced station");
        channel.setExtraJson("{\"xmltv_id\":\"station\"}");
        var match = EpgCacheApplicationService.matches(account, List.of(channel), List.of()).getFirst();
        assertEquals("6c5946350f540c3a39861441107fbca5da02de06bc25b0b1e2175b100a6d48ad", match.identity().signature());
    }

    @Test void missingCacheReadsDoNotCreateFilesOrPerformNetworkCalls() {
        var account=account("a"); var channel=channel("station","Station");
        try(var http=mockStatic(HttpUtil.class)) {
            assertTrue(cache.schedule(account,channel).isEmpty());
            assertTrue(cache.summaries(account,List.of(channel)).get(channel).isEmpty());
            assertEquals(testDbFile.toPath(),EpgCacheDb.path());
            assertFalse(Files.exists(Path.of(testDbFile+".epg-cache.db"))); http.verifyNoInteractions();
        }
    }
    @Test void parsedRecordsPersistAcrossServicesAndRemainAvailableWithoutSourceFile() throws Exception {
        var account=account("a"); var channel=channel("station","Station"); var file=xml("guide.xml","Specific film"); account.setEpg(file.toString());
        refresh.refreshAccount(account,List.of(channel)); Files.delete(file);
        try(var http=mockStatic(HttpUtil.class)) {
            assertEquals("Specific film",new EpgCacheApplicationService().schedule(account,channel).getFirst().title());
            assertEquals("Full details",cache.schedule(account,channel).getFirst().description());
            assertEquals("",cache.summaries(account,List.of(channel)).get(channel).getFirst().description());
            http.verifyNoInteractions();
        }
        try(var connection=DriverManager.getConnection("jdbc:sqlite:"+EpgCacheDb.path());var statement=connection.createStatement();var tables=statement.executeQuery("SELECT count(*) FROM sqlite_master WHERE type='table' AND name LIKE 'epg_%'")) {
            assertTrue(tables.next()); assertEquals(9,tables.getInt(1));
        }
        channel.setDbId("rebuilt-local-row"); assertFalse(cache.schedule(account,channel).isEmpty(),"Provider identity survives catalog row replacement");
    }
    @Test void nowNextChangesAtUtcBoundariesWithoutAnyCacheWrites() {
        long now=System.currentTimeMillis(); var id=new EpgCacheDb.Identity("a","provider","signature");
        source("source","guide",List.of(p("First",now-1000,now+1000),p("Second",now+1000,now+2000)),now);
        db.mapChannels(List.of(match(id,"guide",List.of("source"))),now);
        assertEquals("First",title(id,now)); assertEquals("Second",title(id,now+1000));
        assertTrue(db.summaries(List.of(id),now+2000).get(id).isEmpty());
        assertEquals(2,db.schedule(id,now-1000,now+2000).size());
    }
    @Test void genericTitlesSearchLowerPrioritySourcesAndSpecificTitlesRespectPriority() {
        long now=System.currentTimeMillis(); var id=new EpgCacheDb.Identity("a","provider","signature");
        for(String generic:List.of("Movie","Drama","TBA","T.B.A.","To be announced","Programme","Music","фильм","电影")) {
            source("embedded","guide",List.of(p(generic,now-1000,now+1000),p("Embedded specific",now+1000,now+2000)),now);
            source("account","guide",List.of(p("TBC",now-1000,now+1000),p("Account specific",now+1000,now+2000)),now);
            source("global","guide",List.of(p("Actual programme title",now-1000,now+1000),p("Global specific",now+1000,now+2000)),now);
            db.mapChannels(List.of(match(id,"guide",List.of("embedded","account","global"))),now);
            assertEquals("Actual programme title",title(id,now),generic);
            assertEquals("Embedded specific",title(id,now+1000),"Time changes source selection without remapping");
        }
        source("embedded","guide",List.of(p("Movie: A Real Title",now-1000,now+1000)),now);
        assertEquals("Movie: A Real Title",title(id,now));
        source("embedded","guide",List.of(p("Drama",now-1000,now+1000)),now);
        source("account","guide",List.of(p("An account programme",now-1000,now+1000)),now);
        assertEquals("An account programme",title(id,now));
    }
    @Test void genericFallbackIsKeptAndFutureSpecificTitleCannotHideCurrentProgramme() {
        long now=System.currentTimeMillis(); var id=new EpgCacheDb.Identity("a","provider","signature");
        source("first","guide",List.of(p("Movie",now-1000,now+1000)),now);
        source("second","guide",List.of(p("A future show",now+1000,now+2000)),now);
        db.mapChannels(List.of(match(id,"guide",List.of("first","second"))),now);
        assertEquals("Movie",title(id,now));
    }
    @Test void declaredM3uSourcePrecedesAccountAndGlobalSources() throws Exception {
        var account=account("a"); var channel=channel("station","Station");
        var declared=xml("declared.xml","Playlist programme"); var accountGuide=xml("account.xml","Account programme"); var global=xml("global.xml","Global programme");
        Path playlist=tempDir.resolve("list.m3u"); Files.writeString(playlist,"#EXTM3U x-tvg-url=\""+declared.toUri()+"\"\n");
        account.setM3u8Path(playlist.toString()); account.setEpg(accountGuide.toString());
        var config=ConfigurationService.getInstance().read(); config.setGlobalEpgSource(global.toString()); ConfigurationService.getInstance().save(config);
        refresh.refreshAccount(account,List.of(channel)); assertEquals("Playlist programme",cache.schedule(account,channel).getFirst().title());
    }
    @Test void providerScheduleUsesProviderIdEvenWhenXmltvIdAndOverrideDiffer() throws Exception {
        var account=account("a"); account.setType(AccountType.XTREME_API);
        var channel=channel("provider-123","Station"); channel.setExtraJson("{\"epg_channel_id\":\"station\"}"); account.setEpg(xml("guide.xml","Account programme").toString());
        long now=System.currentTimeMillis();
        try(var nativeService=mockConstruction(NativeEpgApplicationService.class,(service,context) -> when(service.schedule(any(),any())).thenReturn(List.of(p("Native programme",now-1000,now+100_000))))) {
            refresh.refreshPlaybackChannel(account,channel); assertEquals("Native programme",cache.summaries(account,List.of(channel)).get(channel).getFirst().title());
            verify(nativeService.constructed().getFirst()).schedule(account,channel);
        }
    }
    @Test void ambiguousNamesDoNotGuessAndExplicitIdsAndAccountsStayIsolated() {
        long now=System.currentTimeMillis(); var first=new EpgCacheDb.Identity("first","provider","signature"); var second=new EpgCacheDb.Identity("second","provider","signature");
        source("one","guide",List.of(p("Account one",now-1000,now+1000)),now);
        source("two","guide",List.of(p("Account two",now-1000,now+1000)),now);
        db.mapChannels(List.of(match(first,"guide",List.of("one")),match(second,"guide",List.of("two"))),now);
        assertEquals("Account one",title(first,now)); assertEquals("Account two",title(second,now));
        var invalid=new EpgCacheDb.Identity("first","provider","new-signature"); assertTrue(db.summaries(List.of(invalid),now).get(invalid).isEmpty());
        var missing=new EpgCacheDb.Identity("third","provider","signature");
        db.mapChannels(List.of(new EpgCacheDb.Match(missing,"unmatched","ambiguous","ambiguous",false,List.of("one"),"")),now);
        assertTrue(db.summaries(List.of(missing),now).get(missing).isEmpty());
    }
    @Test void failedRefreshRetainsLastGoodRecordsAndUsesRetryBackoff() throws Exception {
        var account=account("a"); var channel=channel("station","Station"); Path file=xml("guide.xml","Last good programme"); account.setEpg(file.toString());
        refresh.refreshAccount(account,List.of(channel)); String source=EpgCacheApplicationService.sourceId(file.toString()); long success=db.sourceState(source).refreshedAt();
        db.failed(source,0,""); Files.writeString(file,"<tv><broken"); refresh.refreshAccount(account,List.of(channel));
        assertEquals("Last good programme",cache.schedule(account,channel).getFirst().title());
        assertEquals(success,db.sourceState(source).refreshedAt()); assertTrue(db.sourceState(source).retryAt()>System.currentTimeMillis());
        var config = ConfigurationService.getInstance().read();
        var identity = EpgCacheApplicationService.identities(account, List.of(channel), config).getFirst();
        assertTrue(db.sourceWarnings(List.of(identity)).contains(identity), "A failed refresh should mark its retained guide mapping stale.");
        Files.writeString(file,"unreadable"); refresh.refreshAccount(account,List.of(channel));
        assertEquals("Last good programme",cache.schedule(account,channel).getFirst().title());
    }
    @Test void playerStaleStatusUsesTheCatalogueChannelIdentityForPlaybackSnapshots() throws Exception {
        Account account = account("player-stale-status");
        Path file = xml("player-stale-status.xml", "Current programme");
        account.setEpg(file.toString());
        AccountDb.get().save(account);
        account = AccountService.getInstance().getByName("player-stale-status");
        account.setAction(Account.AccountAction.itv);

        Category category = new Category("live-category", "Live", "Live", false, 0);
        CategoryDb.get().saveAll(List.of(category), account);
        category = CategoryDb.get().getCategories(account).getFirst();
        Channel catalogChannel = channel("station", "Station");
        catalogChannel.setExtraJson("{\"tvg-id\":\"station\"}");
        ChannelDb.get().saveAll(List.of(catalogChannel), category.getDbId(), account);
        refresh.refreshAccount(account, List.of(catalogChannel));
        String source = EpgCacheApplicationService.sourceId(file.toString());
        db.failed(source, System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(5), "temporary failure");

        // The active player may retain an older Channel snapshot without the catalogue metadata.
        Channel playbackSnapshot = channel("station", "Station");
        var result = new PlayerEpgApplicationService().current(account, playbackSnapshot);
        assertEquals("STALE", result.guideStatus());
        assertEquals("Current programme", result.now().title());
    }
    @Test void invalidImportRollsBackWholeSourceAndClearDoesNotTouchCatalog() {
        long now=System.currentTimeMillis(); var id=new EpgCacheDb.Identity("a","provider","signature");
        source("source","guide",List.of(p("Good",now-1000,now+1000)),now); db.mapChannels(List.of(match(id,"guide",List.of("source"))),now);
        List<EpgProgramme> duplicateProgrammes = List.of(
                p("Duplicate one", now - 1000, now + 1000),
                p("Duplicate two", now - 1000, now + 1000));
        assertThrows(DatabaseAccessException.class, () -> source("source", "guide", duplicateProgrammes, now));
        assertEquals("Good",title(id,now));
        List<String> clearSteps = new ArrayList<>();
        new EpgApplicationService().clearCache(clearSteps::add);
        assertEquals(List.of("epgClearStopping", "epgClearMapping", "epgClearProgrammes",
                "epgClearFinishing", "epgClearPreparing"), clearSteps);
        assertTrue(db.summaries(List.of(id),now).get(id).isEmpty()); assertEquals(0,db.sourceState("source").refreshedAt());
        assertTrue(Files.exists(testDbFile.toPath()));
    }
    @Test void clearDuringDownloadCannotRepopulateCacheFromObsoleteImport() throws Exception {
        var entered=new CountDownLatch(1); var resume=new CountDownLatch(1);
        var account=account("a"); var channel=channel("station","Station"); account.setEpg("http://example.invalid/guide.xml");
        var xml=Files.readAllBytes(xml("guide.xml","Obsolete import"));
        try(var _=mockConstruction(EpgApplicationService.class,(loader,context) -> {
            when(loader.isEnabled()).thenReturn(true); when(loader.cacheSources(account)).thenReturn(List.of(account.getEpg()));
            when(loader.downloadSource(anyString())).thenAnswer(_ -> { entered.countDown(); assertTrue(resume.await(5,TimeUnit.SECONDS)); return XmltvGuide.read(new java.io.ByteArrayInputStream(xml),Instant.now()); });
        })) {
            // Construction mocking is thread-local, so run the clear on another thread while this thread imports.
            var clearer=Thread.ofVirtual().start(() -> {
                try { assertTrue(entered.await(5,TimeUnit.SECONDS)); EpgCacheRefreshService.invalidate(); }
                catch(InterruptedException error) { throw new RuntimeException(error); }
                finally { resume.countDown(); }
            });
            refresh.refreshAccount(account,List.of(channel)); clearer.join(5000);
            assertTrue(cache.schedule(account,channel).isEmpty());
        }
    }
    @Test void disabledEpgNeverReturnsOrRefreshesRecords() throws Exception {
        var account=account("a"); var channel=channel("station","Station"); account.setEpg(xml("guide.xml","Cached").toString()); refresh.refreshAccount(account,List.of(channel));
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(false); ConfigurationService.getInstance().save(config);
        try(var http=mockStatic(HttpUtil.class)) {
            refresh.refreshAccount(account,List.of(channel)); assertTrue(cache.schedule(account,channel).isEmpty()); assertTrue(cache.summaries(account,List.of(channel)).isEmpty()); http.verifyNoInteractions();
        }
    }
    @Test void largeGuideReadsStayBoundedAndDoNotLoadDescriptions() throws Exception {
        long now=System.currentTimeMillis();
        Map<String,List<EpgProgramme>> programmes=new HashMap<>();
        List<EpgCacheDb.Identity> ids=new ArrayList<>(); List<EpgCacheDb.Match> mappings=new ArrayList<>();
        for(int channel=0;channel<500;channel++) {
            String name="channel-"+channel;
            List<EpgProgramme> schedule=new ArrayList<>();
            for(int hour=0;hour<96;hour++) schedule.add(new EpgProgramme("Programme "+hour,"Description ".repeat(100),now+(hour-1)*1_800_000L,now+hour*1_800_000L));
            programmes.put(name,List.copyOf(schedule));
            var id=new EpgCacheDb.Identity("account",name,"signature"); ids.add(id); mappings.add(match(id,name,List.of("large")));
        }
        db.replaceSource("large",programmes,Map.of(),Map.of(),now,now+7_200_000);
        db.mapChannels(mappings,now);
        List<Long> samples=new ArrayList<>();
        for(int run=0;run<12;run++) {
            long started=System.nanoTime(); var summaries=db.summaries(ids.subList(0,32),now);
            samples.add((System.nanoTime()-started)/1_000_000);
            assertEquals(32,summaries.size());
            summaries.values().forEach(entries -> { assertEquals(2,entries.size()); assertTrue(entries.stream().allMatch(p -> p.description().isEmpty())); });
        }
        long started=System.nanoTime(); assertEquals(500,db.summaries(ids,now).size()); long catalogMs=(System.nanoTime()-started)/1_000_000;
        samples.sort(Long::compareTo);
        Files.createDirectories(Path.of("target"));
        Files.writeString(Path.of("target/epg-cache-benchmark.txt"),"48,000 programmes / 500 channels; 32-card read median="+samples.get(6)+"ms, max="+samples.getLast()+"ms; 500-channel read="+catalogMs+"ms; cache bytes="+Files.size(EpgCacheDb.path())+"\n");
    }

    @Test void settingsRefreshReplacesSourceInBackgroundWithoutClearingLastGoodGuide() throws Exception {
        Account account=account("refresh-account"); account.setEpg(xml("refresh.xml","Old title").toString());
        AccountDb.get().save(account); account=AccountService.getInstance().getByName("refresh-account"); account.setAction(Account.AccountAction.itv);
        CategoryDb.get().saveAll(List.of(new Category("provider-category","Live","Live",false,0)),account);
        String category=CategoryDb.get().getCategories(account).getFirst().getDbId();
        Channel channel=channel("station","Station"); channel.setCmd("http://example.test/live"); ChannelDb.get().saveAll(List.of(channel),category,account);
        refresh.refreshAccount(account,List.of(channel)); assertEquals("Old title",cache.schedule(account,channel).getFirst().title());
        xml("refresh.xml","New title");
        var job=EpgRefreshJobService.start();
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
        EpgRefreshJobService.Snapshot snapshot;
        do {
            snapshot=EpgRefreshJobService.get(job.id());
            if (snapshot != null && (snapshot.status().equals("COMPLETED") || snapshot.status().equals("COMPLETED_WITH_WARNINGS")
                    || snapshot.status().equals("FAILED") || snapshot.status().equals("CANCELLED"))) break;
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        } while(System.nanoTime()<deadline);
        assertNotNull(snapshot);
        assertEquals("COMPLETED", snapshot.status(), snapshot.message());
        assertEquals("New title", cache.schedule(account,channel).getFirst().title(), "Manual refresh must replace the source without clearing the previous guide first");
        assertEquals(1,ChannelDb.get().getCachedLiveChannels(account.getDbId()).size(),"Catalog stays intact");
    }

    @Test void manualRefreshReportsAccountPreparationProgress() throws Exception {
        Path playlist = tempDir.resolve("preflight-visible.m3u");
        Files.writeString(playlist, "#EXTM3U\n");
        Account account = account("preflight-visible");
        account.setM3u8Path(playlist.toString());
        AccountService.getInstance().save(account);

        var started = EpgRefreshJobService.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var snapshot = EpgRefreshJobService.get(started.id());
        while (snapshot != null && !Set.of("COMPLETED", "COMPLETED_WITH_WARNINGS", "FAILED", "CANCELLED").contains(snapshot.status())
                && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            snapshot = EpgRefreshJobService.get(started.id());
        }
        assertNotNull(snapshot);
        assertEquals("NO_SOURCES", snapshot.status(), snapshot.message());
        assertEquals(1, snapshot.preparationTotal());
        assertEquals(1, snapshot.preparationCompleted());
        assertEquals(0, snapshot.total(), "A configured account without an EPG source should finish without source work.");
        assertTrue(snapshot.message().contains("No XMLTV sources"));
        assertTrue(snapshot.message().contains("Provider-native schedules"));
    }

    @Test void refreshWithNoAccountsOrGlobalSourcesReportsNoSources() throws Exception {
        var started = EpgRefreshJobService.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var snapshot = EpgRefreshJobService.get(started.id());
        while (snapshot != null && !Set.of("NO_SOURCES", "FAILED", "CANCELLED").contains(snapshot.status())
                && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            snapshot = EpgRefreshJobService.get(started.id());
        }
        assertNotNull(snapshot);
        assertEquals("NO_SOURCES", snapshot.status(), snapshot.message());
        assertEquals(0, snapshot.preparationTotal());
        assertEquals(0, snapshot.total());
    }

    @Test void refreshSourceLabelsDoNotExposeUrlsOrLocalPaths() {
        String remote = EpgRefreshJobService.sourceLabel(
                "https://private-user:private-pass@guide.example/private/secret-token.xml.gz?token=query-secret");
        String local = EpgRefreshJobService.sourceLabel("/Users/private-user/Downloads/private-guide.xml.gz");
        assertEquals("guide.example", remote);
        assertEquals("Local XMLTV file", local);
        assertFalse(remote.contains("private"));
        assertFalse(remote.contains("secret"));
        assertFalse(local.contains("/Users"));
        assertFalse(local.contains("private-guide"));
    }

    @Test void successfullyParsedFeedWithNoCatalogueMatchesIsReportedAsWarning() throws Exception {
        Path file = xml("unmatched-feed.xml", "Unrelated programme");
        Files.writeString(file, Files.readString(file)
                .replace("id='station'", "id='unrelated'")
                .replace(">Station</display-name>", ">Unrelated channel</display-name>")
                .replace("channel='station'", "channel='unrelated'"));
        Account account = account("unmatched-feed-account");
        account.setEpg(file.toString());
        AccountDb.get().save(account);
        account = AccountService.getInstance().getByName("unmatched-feed-account");
        var category = new Category("unmatched-category", "Live", "Live", false, 0);
        CategoryDb.get().saveAll(List.of(category), account);
        category = CategoryDb.get().getCategories(account).getFirst();
        ChannelDb.get().saveAll(List.of(channel("local-station", "Local station")), category.getDbId(), account);

        var started = EpgRefreshJobService.start();
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        var snapshot = EpgRefreshJobService.get(started.id());
        while (snapshot != null && !Set.of("COMPLETED", "COMPLETED_WITH_WARNINGS", "FAILED", "CANCELLED", "NO_SOURCES")
                .contains(snapshot.status()) && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            snapshot = EpgRefreshJobService.get(started.id());
        }
        assertNotNull(snapshot);
        assertEquals("COMPLETED_WITH_WARNINGS", snapshot.status(), snapshot.message());
        assertEquals(1, snapshot.sources().size());
        assertEquals("No matches", snapshot.sources().getFirst().status());
        assertTrue(snapshot.sources().getFirst().detail().contains("none matched"));
        assertTrue(snapshot.message().contains("no matching channels"));
    }

    @Test void sourceImportReportsBytesReadAndProgrammesProcessed() throws Exception {
        Path file = xml("progress.xml", "Progress title");
        List<String> phases = new ArrayList<>();
        List<Long> bytesRead = new ArrayList<>();
        XmltvGuide guide = new EpgApplicationService().downloadSource(file.toString(), phases::add, bytesRead::add);
        assertEquals(List.of("Downloading", "Parsing"), phases);
        assertFalse(bytesRead.isEmpty());
        assertEquals(Files.size(file), bytesRead.getLast());

        long now = System.currentTimeMillis();
        List<EpgProgramme> programmes = new ArrayList<>();
        for (int index = 0; index < 501; index++) {
            long start = now + index * 60_000L;
            programmes.add(p("Programme " + index, start, start + 60_000L));
        }
        List<Long> processed = new ArrayList<>();
        db.replaceSource("progress-source", Map.of("station", programmes), Map.of(), Map.of(), Map.of(),
                now, now + 7_200_000L, Set.of(), processed::add);
        assertEquals(List.of(500L, 501L), processed);
        List<Long> matched = new ArrayList<>();
        db.mapChannels(List.of(match(new EpgCacheDb.Identity("account", "station", "signature"),
                "station", List.of("progress-source"))), now, false, matched::add);
        assertEquals(List.of(1L), matched);
    }

    @Test void manualRefreshShowsAndCanCancelWhileWaitingForAnotherRefresh() {
        EpgCacheRefreshService.REFRESH_LOCK.lock();
        EpgRefreshJobService.Snapshot started;
        try {
            started = EpgRefreshJobService.start();
            var joined = EpgRefreshJobService.start();
            assertEquals(started.id(), joined.id(), "Concurrent manual refresh requests join the active job.");
            long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            var snapshot = EpgRefreshJobService.get(started.id());
            while (snapshot != null && !snapshot.stage().equals("Waiting for another EPG refresh to stop")
                    && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                snapshot = EpgRefreshJobService.get(started.id());
            }
            assertNotNull(snapshot);
            assertEquals("Waiting for another EPG refresh to stop", snapshot.stage());
            assertTrue(snapshot.startedAt() > 0);
            assertTrue(EpgRefreshJobService.cancel(started.id()));

            deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
            while (snapshot != null && !snapshot.status().equals("CANCELLED") && System.nanoTime() < deadline) {
                LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                snapshot = EpgRefreshJobService.get(started.id());
            }
            assertNotNull(snapshot);
            assertEquals("CANCELLED", snapshot.status());
        } finally {
            EpgCacheRefreshService.REFRESH_LOCK.unlock();
        }

        var restarted = EpgRefreshJobService.start();
        assertNotEquals(started.id(), restarted.id(), "A new refresh can start after the previous job is terminal.");
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(3);
        var snapshot = EpgRefreshJobService.get(restarted.id());
        while (snapshot != null && !Set.of("NO_SOURCES", "FAILED", "CANCELLED", "SKIPPED").contains(snapshot.status())
                && System.nanoTime() < deadline) {
            LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
            snapshot = EpgRefreshJobService.get(restarted.id());
        }
        assertNotNull(snapshot);
        assertEquals("NO_SOURCES", snapshot.status(), snapshot.message());
    }

    @Test void uniqueNormalizedAliasesMatchButConflictingAliasesRemainUnmatched() throws Exception {
        var account=account("aliases");
        Path file=xml("aliases.xml","Known programme");
        String contents=Files.readString(file).replace("</tv>","<channel id='other'><display-name>Station</display-name></channel></tv>");
        Files.writeString(file,contents); account.setEpg(file.toString());
        var ambiguous=channel("unknown","Station"); var explicit=channel("station","Something else");
        refresh.refreshAccount(account,List.of(ambiguous,explicit));
        assertTrue(cache.schedule(account,ambiguous).isEmpty()); assertFalse(cache.schedule(account,explicit).isEmpty());
        var unique=channel("unknown","Station HD");
        account.setEpg(xml("unique.xml","Matched by name").toString());
        refresh.refreshAccount(account,List.of(unique)); assertEquals("Matched by name",cache.schedule(account,unique).getFirst().title());
    }

    @Test void epgOffOwnsNoTimerAndTurningItOffStopsTheRunningScheduler() throws Exception {
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(false); ConfigurationService.getInstance().save(config);
        EpgCacheRefreshService.start();
        var scheduler=EpgCacheRefreshService.class.getDeclaredField("scheduler"); scheduler.setAccessible(true);
        LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(30)); assertNull(scheduler.get(null),"EPG off must not create even an idle scheduled executor");
        config.setEpgEnabled(true); ConfigurationService.getInstance().save(config);
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(3);
        while(scheduler.get(null)==null && System.nanoTime()<deadline) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        assertNotNull(scheduler.get(null));
        var running=(ScheduledExecutorService)scheduler.get(null);
        config.setEpgEnabled(false); ConfigurationService.getInstance().save(config);
        assertTrue(running.isShutdown()); assertNull(scheduler.get(null));
    }

    @Test void enablingFullEpgRunsImmediatelyThenUsesConfiguredInterval() throws Exception {
        var account = account("immediate-refresh");
        account.setEpg(xml("immediate.xml", "Immediate programme").toString());
        AccountDb.get().save(account);
        account = AccountService.getInstance().getByName("immediate-refresh");
        var category = new Category("immediate-category", "Live", "Live", false, 0);
        CategoryDb.get().saveAll(List.of(category), account);
        category = CategoryDb.get().getCategories(account).getFirst();
        var channel = channel("station", "Station");
        ChannelDb.get().saveAll(List.of(channel), category.getDbId(), account);

        var config = ConfigurationService.getInstance().read();
        config.setEpgEnabled(false);
        config.setEpgPlaybackOnly(false);
        config.setEpgSchedulerIntervalMinutes("60");
        ConfigurationService.getInstance().save(config);
        EpgCacheRefreshService.start();

        config.setEpgEnabled(true);
        ConfigurationService.getInstance().save(config);

        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (cache.schedule(account, channel).isEmpty() && System.nanoTime() < deadline) LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
        assertEquals("Immediate programme", cache.schedule(account, channel).getFirst().title(),
                "Leaving EPG off must not wait for the configured hourly interval");
    }

    @Test void refreshIntervalPersistsAndShorteningItMakesOldSourceDue() throws Exception {
        var config=ConfigurationService.getInstance().read(); assertEquals(2,config.normalizedEpgRefreshHours());
        config.setEpgRefreshIntervalHours("1"); ConfigurationService.getInstance().save(config);
        assertEquals(1,ConfigurationService.getInstance().read().normalizedEpgRefreshHours());
        config.setEpgSchedulerIntervalMinutes("5"); ConfigurationService.getInstance().save(config);
        assertEquals(5,ConfigurationService.getInstance().read().normalizedEpgSchedulerIntervalMinutes());
        config.setEpgSchedulerIntervalMinutes("4"); assertEquals(3,config.normalizedEpgSchedulerIntervalMinutes());
        Account account=account("a"); Channel channel=channel("station","Station"); Path file=xml("interval.xml","Refreshed title"); account.setEpg(file.toString());
        long now=System.currentTimeMillis(); String id=EpgCacheApplicationService.sourceId(file.toString());
        db.replaceSource(id,Map.of("station",List.of(p("Old title",now-1000,now+100_000))),Map.of(),Map.of(),now-90*60_000,now+30*60_000);
        refresh.refreshAccount(account,List.of(channel)); assertEquals("Refreshed title",cache.schedule(account,channel).getFirst().title());
        config.setEpgRefreshIntervalHours("invalid"); assertEquals(2,config.normalizedEpgRefreshHours());
        config.setEpgRefreshIntervalHours("999"); assertEquals(24,config.normalizedEpgRefreshHours());
    }

    @Test void xmltvChannelIconsPersistWithoutProgrammesAndResolveRelativeUrls() throws Exception {
        Account account=account("icons"); Channel channel=channel("station","Station");
        Path file=tempDir.resolve("icons.xml");
        Files.writeString(file,"<tv><channel id='station'><display-name>Station</display-name><icon src='logos/station.png'/></channel></tv>");
        account.setEpg(file.toString()); refresh.refreshAccount(account,List.of(channel));
        assertEquals(file.getParent().resolve("logos/station.png"),Path.of(java.net.URI.create(cache.icons(account,List.of(channel)).get(channel))));
        assertTrue(cache.schedule(account,channel).isEmpty());
        assertNull(channel.getLogo(),"EPG artwork must not overwrite the catalog logo");
        Files.delete(file); assertFalse(new EpgCacheApplicationService().icons(account,List.of(channel)).get(channel).isBlank());
        var config=ConfigurationService.getInstance().read(); config.setEpgEnabled(false); ConfigurationService.getInstance().save(config);
        assertTrue(cache.icons(account,List.of(channel)).isEmpty());
    }

    @Test void programmeArtworkCannotBeMistakenForChannelIcon() throws Exception {
        String body="<tv><channel id='station'><icon src='https://example.test/channel.png'/></channel><programme channel='station' start='20261015000000 +0000' stop='20261015010000 +0000'><title>A programme</title><icon src='https://example.test/programme.png'/></programme></tv>";
        XmltvGuide guide=XmltvGuide.read(new java.io.ByteArrayInputStream(body.getBytes(java.nio.charset.StandardCharsets.UTF_8)),Instant.parse("2026-10-15T00:30:00Z"));
        assertEquals(Map.of("station","https://example.test/channel.png"),guide.icons());
    }

}
