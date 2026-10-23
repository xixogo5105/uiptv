package com.uiptv.application;

import com.uiptv.db.*;
import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.*;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EpgResolutionTest extends DbBackedTest {
    private Account account;
    private Channel channel;
    private Configuration config;
    @BeforeEach void setup() {
        account = new Account(); account.setDbId(UUID.randomUUID().toString()); account.setType(AccountType.M3U8_LOCAL); account.setAction(Account.AccountAction.itv);
        channel = new Channel(); channel.setChannelId("provider-stream"); channel.setDbId("local-row"); channel.setName("Station HD");
        config = ConfigurationService.getInstance().read(); config.setEpgEnabled(true); ConfigurationService.getInstance().save(config);
    }
    private String source(String name, String id, String title) throws Exception {
        var format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss xx").withZone(ZoneOffset.UTC);
        var now = Instant.now(); Path path = tempDir.resolve(name + ".xml");
        Files.writeString(path, "<tv><programme channel='" + id + "' start='" + format.format(now.minusSeconds(60)) + "' stop='" + format.format(now.plusSeconds(3600)) + "'><title>" + title + "</title></programme></tv>");
        return path.toString();
    }
    private List<EpgProgramme> schedule(Account account, Channel channel) {
        var refresh = new EpgCacheRefreshService();
        // Native provider EPG is available only through the active-playback path.
        if (EpgCacheApplicationService.nativeProvider(account)) refresh.refreshPlaybackChannel(account, channel);
        else refresh.refreshAccount(account, List.of(channel));
        return new EpgCacheApplicationService().summaries(account, List.of(channel)).getOrDefault(channel, List.of());
    }
    @Test void globalGuideMatchesPlaylistResolutionLabelsWithoutLosingHdSdIdentity() throws Exception {
        String global = source("resolution-guide", "B4U.Kadak.in", "Kadak programme");
        config.setGlobalEpgSource(global); ConfigurationService.getInstance().save(config);
        long now = System.currentTimeMillis();
        new EpgCacheDb().replaceSource(EpgCacheApplicationService.sourceId(global),
                Map.of("B4U.Kadak.in", List.of(new EpgProgramme("SD programme", "", now-60_000, now+3_600_000)),
                        "B4U.Kadak.HD.in", List.of(new EpgProgramme("HD programme", "", now-60_000, now+3_600_000))),
                Map.of("b4u kadak", "B4U.Kadak.in", "b4u kadak hd", "B4U.Kadak.HD.in"),
                Map.of(), now, now+3_600_000);
        channel.setChannelId("B4UKadak.in@SD"); channel.setName("B4U Kadak (576p)");
        assertEquals("SD programme", schedule(account,channel).getFirst().title());
        channel.setName("B4U Kadak HD (1080p) [Geo-blocked]");
        assertEquals("HD programme", schedule(account,channel).getFirst().title());
        channel.setName("B4U Kadak International (576p)");
        assertTrue(schedule(account,channel).isEmpty());
        channel.setName("B4U Kadak +1 (576p)");
        assertTrue(schedule(account,channel).isEmpty());
    }

    @Test void namedProgrammesKeepTheirDurationWhenGenericPlaceholdersOverlap() {
        var godzilla = new EpgProgramme("Godzilla", "", 1000, 3000);
        var movie = new EpgProgramme("Movie", "", 1500, 4000);
        var normalized = EpgSchedules.normalize(List.of(movie, godzilla, godzilla));
        assertEquals(List.of(godzilla, new EpgProgramme("Movie", "", 3000, 4000)), normalized);
        assertEquals(1, EpgSchedules.normalize(List.of(godzilla, new EpgProgramme("Movie", "", 1500, 2000))).size());
        assertTrue(EpgSchedules.isGenericTitle("No details available"));
    }
    @Test void nativeCatalogueDoesNotDownloadXmlBeforeChannelNativeResolution() {
        account.setType(AccountType.XTREME_API); account.setEpg("https://example.invalid/account.xml");
        config.setGlobalEpgSource("https://example.invalid/global.xml"); ConfigurationService.getInstance().save(config);
        try (var http = mockStatic(HttpUtil.class)) {
            var service = new EpgApplicationService(); service.enrichCached(account, List.of(channel));
            assertTrue(new EpgCacheApplicationService().summaries(account, List.of(channel)).get(channel).isEmpty());
            http.verifyNoInteractions();
        }
    }

    @Test void desktopGuideSizeLimitsAreBoundedAndComprehensiveBundleGetsASeparateLimit() throws Exception {
        assertEquals(200L * 1024 * 1024, EpgApplicationService.sourceSizeLimit());
        assertEquals(4L * 1024 * 1024 * 1024,
                EpgApplicationService.sourceSizeLimit("https://epgshare01.online/epgshare01/epg_ripper_ALL_SOURCES1.xml.gz"));
        assertEquals(200L * 1024 * 1024,
                EpgApplicationService.sourceSizeLimit("https://epgshare01.online/epgshare01/epg_ripper_IN2.xml.gz"));
        try (var limited = new EpgApplicationService.LimitedInputStream(new java.io.ByteArrayInputStream(new byte[5]), 4)) {
            assertEquals(4, limited.read(new byte[4]));
            assertThrows(java.io.IOException.class, limited::read);
        }
        try (var limited = new EpgApplicationService.LimitedInputStream(new java.io.ByteArrayInputStream(new byte[5]), 4)) {
            assertThrows(java.io.IOException.class, () -> limited.read(new byte[5]));
        }
    }

    @Test void xmlProcessingLimitsAndIoFailuresAreNotMisreportedAsMalformedXml() throws Exception {
        var warning = EpgApplicationService.class.getDeclaredMethod("warning", Exception.class); warning.setAccessible(true);
        assertEquals("epgSourceXmlLimit", warning.invoke(null, new javax.xml.stream.XMLStreamException("JAXP00010004: entity budget exceeded")));
        assertEquals("epgSourceUnavailable", warning.invoke(null, new javax.xml.stream.XMLStreamException("Read error", new java.io.IOException("Guide loading deadline exceeded"))));
        assertEquals("epgSourceInvalid", warning.invoke(null, new javax.xml.stream.XMLStreamException("Malformed XML")));
    }

    @Test void wrappedSizeLimitReportsOversizeInsteadOfInvalidXml() throws Exception {
        var warning = EpgApplicationService.class.getDeclaredMethod("warning", Exception.class);
        warning.setAccessible(true);
        assertEquals("epgSourceTooLarge", warning.invoke(null,
                new javax.xml.stream.XMLStreamException("Parse error", new java.io.IOException("Guide exceeds size limit"))));
    }
    @Test void globalWarningsComeFromPersistedSourceStateAndRespectEnableSwitch() {
        String source = "https://example.invalid/guide.xml";
        config.setGlobalEpgSource(source); ConfigurationService.getInstance().save(config);
        var service = new EpgApplicationService();
        assertEquals("", service.globalWarning());
        new EpgCacheDb().failed(EpgCacheApplicationService.sourceId(source), System.currentTimeMillis() + 60_000, "epgSourceTooLarge");
        assertEquals("epgSourceTooLarge", new EpgApplicationService().globalWarning());
        config.setEpgEnabled(false); ConfigurationService.getInstance().save(config);
        assertEquals("", service.globalWarning());
        config.setEpgEnabled(true); ConfigurationService.getInstance().save(config);
        service.clearCache();
        assertEquals("", service.globalWarning());
    }

    @Test void elevenConfiguredFeedsAreNotEvictedDuringChannelMatching() throws Exception {
        java.util.ArrayList<String> sources = new java.util.ArrayList<>();
        for (int i = 0; i < 11; i++) sources.add(source("regional-" + i, "other-" + i, "Other"));
        config.setGlobalEpgSource(String.join("\n", sources)); ConfigurationService.getInstance().save(config);
        var service = new EpgApplicationService(); service.clearCache();
        assertTrue(schedule(account, channel).isEmpty());
        var cache = new EpgCacheDb();
        Map<String, EpgCacheDb.SourceState> first = new HashMap<>();
        for (String source : sources) {
            var state = cache.sourceState(EpgCacheApplicationService.sourceId(source));
            assertTrue(state.refreshedAt() > 0); first.put(source, state);
            Files.delete(Path.of(source));
        }
        schedule(account, channel);
        for (String source : sources) assertEquals(first.get(source), cache.sourceState(EpgCacheApplicationService.sourceId(source)), "Feed was unnecessarily reloaded");
    }

    @Test void multipleGlobalSourcesFallThroughAndRefreshReplacesCachedSchedules() throws Exception {
        String first = source("global-first", "other", "Unmatched");
        String second = source("global-second", channel.getChannelId(), "Second");
        config.setGlobalEpgSource(first + "\n\n" + second + "\n" + first);
        ConfigurationService.getInstance().save(config);
        assertEquals(List.of(first, second), EpgApplicationService.globalSources(config.getGlobalEpgSource()));
        var service = new EpgApplicationService();
        assertEquals("Second", schedule(account, channel).getFirst().title());
        source("global-second", channel.getChannelId(), "Updated");
        assertEquals("Second", schedule(account, channel).getFirst().title());
        service.clearCache();
        assertEquals("Updated", schedule(account, channel).getFirst().title());
        assertEquals(config.getGlobalEpgSource(), ConfigurationService.getInstance().read().getGlobalEpgSource());
    }
    @Test void unmatchedAndInvalidSourcesFallThroughPerChannel() throws Exception {
        Path playlist = tempDir.resolve("playlist.m3u");
        Files.writeString(playlist, "#EXTM3U x-tvg-url=\"" + source("embedded", "other", "Wrong") + "\"\n");
        account.setM3u8Path(playlist.toString());
        account.setEpg(source("account", channel.getChannelId(), "Account"));
        config.setGlobalEpgSource(source("global", channel.getChannelId(), "Global")); ConfigurationService.getInstance().save(config);
        assertEquals("Account", schedule(account, channel).getFirst().title());
        account.setEpg(source("unmatched", "other", "Other"));
        assertEquals("Global", schedule(account, channel).getFirst().title());
        Path broken = tempDir.resolve("broken.xml"); Files.writeString(broken, "not XML"); account.setEpg(broken.toString());
        assertEquals("Global", schedule(account, channel).getFirst().title());
    }
    @Test void disabledClearsTransientDataAndDoesNotOpenSourcesOrProviders() throws Exception {
        config.setEpgEnabled(false); ConfigurationService.getInstance().save(config);
        account.setType(AccountType.XTREME_API); account.setEpg("https://example.invalid/never-request");
        channel.setEpgNow(new EpgProgramme("Old", "", 1, 2));
        try (var provider = mockStatic(XtremeApiParser.class)) {
            EpgApplicationService service = new EpgApplicationService(); service.enrichCached(account, List.of(channel));
            assertNull(channel.getEpgNow()); assertTrue(schedule(account, channel).isEmpty());
            assertTrue(new LiveGuideApplicationService().list(account.getDbId()).isEmpty());
            assertNull(new PlayerEpgApplicationService().lookup(null).now()); provider.verifyNoInteractions();
        }
    }
    @Test void xtreamGuideIdentityIsNotStreamOrLocalIdentityAndNativeWins() throws Exception {
        account.setType(AccountType.XTREME_API); channel.setExtraJson("{\"epg_channel_id\":\"xmltv-station\"}");
        assertEquals("xmltv-station", EpgApplicationService.guideId(account, channel));
        account.setEpg(source("manual", "xmltv-station", "Manual"));
        long now = System.currentTimeMillis();
        String json = "{\"epg_listings\":[{\"title\":\"TmF0aXZl\",\"start_timestamp\":" + (now / 1000 - 60) + ",\"stop_timestamp\":" + (now / 1000 + 3600) + "}]}";
        try (var provider = mockStatic(XtremeApiParser.class)) {
            provider.when(() -> XtremeApiParser.fetchEpg(account, "get_simple_data_table", "provider-stream")).thenThrow(new java.io.IOException("unsupported"));
            provider.when(() -> XtremeApiParser.fetchEpg(account, "get_short_epg", "provider-stream")).thenReturn(json);
            assertEquals("Native", schedule(account, channel).getFirst().title());
            assertEquals("Native", schedule(account, channel).getFirst().title());
            provider.verify(() -> XtremeApiParser.fetchEpg(account, "get_short_epg", "provider-stream"), times(1));
        }
    }
    @Test void unsupportedNativeUsesAccountXmltvAndMappingsSurviveCacheReplacement() throws Exception {
        account.setAccountName("Mapping"); account.setDbId(null); AccountDb.get().save(account); account = AccountService.getInstance().getByName("Mapping");
        account.setAction(Account.AccountAction.itv);
        CategoryDb.get().saveAll(List.of(new Category("genre", "Group", "Group", false, 0)), account);
        var category = CategoryDb.get().getCategories(account).getFirst();
        channel.setExtraJson("{\"xmltv_id\":\"wrong\"}");
        ChannelDb.get().saveAll(List.of(channel), category.getDbId(), account);
        new EpgMappingApplicationService().save(account.getDbId(), channel.getChannelId(), "correct-guide");
        ChannelDb.get().saveAll(List.of(channel), category.getDbId(), account);
        Channel cached = ChannelDb.get().getChannels(category.getDbId()).getFirst();
        assertEquals(channel.getExtraJson(), cached.getExtraJson());
        assertEquals("correct-guide", new EpgMappingApplicationService().read(account.getDbId(), cached.getChannelId()));
        account.setEpg(source("mapped", "correct-guide", "Mapped"));
        assertEquals("Mapped", schedule(account, cached).getFirst().title());
        assertEquals("correct-guide", new org.json.JSONObject(ConfigurationDb.get().getConfiguration().getEpgMappingsJson()).getJSONObject(account.getDbId()).getString("provider-stream"));
    }
    @Test void stalkerUsesAuthenticatedSessionAndUnsupportedActionsFallBackWithoutChangingCommand() throws Exception {
        account.setType(AccountType.STALKER_PORTAL); account.setToken("existing-token"); account.setMacAddress("00:11:22:33:44:55");
        channel.setCmd("original-playback-command"); account.setEpg(source("stalker-account", channel.getChannelId(), "XMLTV fallback"));
        long now = System.currentTimeMillis();
        String payload = "{\"js\":{\"data\":[{\"name\":\"Portal current\",\"time\":" + (now / 1000 - 60) + ",\"duration\":3600}]}}";
        var response = FetchAPI.FetchResult.success(payload, 200);
        try (var portal = mockStatic(FetchAPI.class); var handshakes = mockStatic(HandshakeService.class)) {
            portal.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), any(Account.class), any())).thenReturn(response);
            assertEquals("Portal current", schedule(account, channel).getFirst().title());
            handshakes.verifyNoInteractions();
            var request = org.mockito.ArgumentCaptor.forClass(Map.class);
            var requestAccount = org.mockito.ArgumentCaptor.forClass(Account.class);
            portal.verify(() -> FetchAPI.fetchWithDiagnostics(request.capture(), requestAccount.capture(), any()));
            assertEquals("UTC", requestAccount.getValue().getTimezone());
            assertEquals(account.getDbId(), requestAccount.getValue().getDbId());
            assertEquals(account.getToken(), requestAccount.getValue().getToken());
            assertEquals("Europe/London", account.getTimezone(), "EPG requests must not mutate account settings");
            assertEquals("provider-stream", request.getValue().get("ch_id"));
            assertEquals("get_short_epg", request.getValue().get("action"));
            assertEquals("original-playback-command", channel.getCmd());
        }
        // A different account context cannot reuse the numeric/provider channel's cached schedule.
        account.setDbId(UUID.randomUUID().toString());
        try (var portal = mockStatic(FetchAPI.class)) {
            portal.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), any(Account.class), any())).thenReturn(FetchAPI.FetchResult.success("{\"js\":false}", 200));
            assertEquals("XMLTV fallback", schedule(account, channel).getFirst().title());
            assertEquals("original-playback-command", channel.getCmd());
        }
    }
    @Test void failedStalkerSessionFallsBackSoftly() throws Exception {
        account.setType(AccountType.STALKER_PORTAL); account.setToken("expired");
        account.setEpg(source("stalker-auth", channel.getChannelId(), "Account fallback"));
        HandshakeService handshake = mock(HandshakeService.class);
        doAnswer(invocation -> { account.setToken(null); return null; }).when(handshake).connect(account);
        try (var portal = mockStatic(FetchAPI.class); var handshakes = mockStatic(HandshakeService.class)) {
            handshakes.when(HandshakeService::getInstance).thenReturn(handshake);
            portal.when(() -> FetchAPI.fetchWithDiagnostics(anyMap(), any(Account.class), any())).thenReturn(FetchAPI.FetchResult.transportFailure("", 401, "Unauthorized"));
            assertEquals("Account fallback", schedule(account, channel).getFirst().title());
            verify(handshake).connect(account);
        }
    }
    @Test void configurationIsDefaultOffAndRoundTrips() {
        assertFalse(new Configuration().isEpgEnabled());
        config.setGlobalEpgSource("/private/guide.xml.gz"); ConfigurationDb.get().save(config);
        Configuration loaded = ConfigurationDb.get().getConfiguration(); assertTrue(loaded.isEpgEnabled()); assertEquals("/private/guide.xml.gz", loaded.getGlobalEpgSource());
    }
    @Test void providerTimesAndStalkerShapesAreDefensive() {
        long now = System.currentTimeMillis();
        String json = "{\"js\":{\"data\":[{\"name\":\"News\",\"time\":" + now + ",\"duration\":3600,\"ch_id\":\"42\"}]}}";
        assertEquals("News", NativeEpgApplicationService.parse(json, false, "Europe/London", "42").getFirst().title());
        assertTrue(NativeEpgApplicationService.parse(json, false, "UTC", "local-row").isEmpty());
        assertTrue(NativeEpgApplicationService.parse("{\"js\":false}", false, "UTC", "42").isEmpty());
        assertTrue(NativeEpgApplicationService.parse("invalid", false, "UTC", "42").isEmpty());
        assertEquals(Instant.parse("2026-10-25T00:30:00Z").toEpochMilli(), NativeEpgApplicationService.time("2026-10-25T01:30:00+01:00", ZoneId.of("Europe/London")));
        assertEquals(Instant.parse("2026-10-25T01:30:00Z").toEpochMilli(), NativeEpgApplicationService.time("2026-10-25T01:30:00Z", ZoneId.of("Europe/London")));
    }
}
