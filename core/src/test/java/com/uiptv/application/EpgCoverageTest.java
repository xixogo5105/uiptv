package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.ConfigurationService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import com.uiptv.util.FetchAPI;
import com.uiptv.util.XtremeApiParser;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class EpgCoverageTest extends DbBackedTest {
    @Test
    void nameRulesNormalizePresentationAndRejectAmbiguousAliases() {
        EpgNameRules rules = new EpgNameRules(List.of(
                new EpgNameRules.Rule("HD", "quality"),
                new EpgNameRules.Rule("HD", "quality"),
                new EpgNameRules.Rule("Sports", "annotation"),
                new EpgNameRules.Rule("UHD", "quality")));

        assertEquals(3, rules.rules().size());
        assertFalse(rules.fingerprint().isBlank());
        assertEquals("one", rules.display("  One (Sports)  "));
        assertEquals("one+", rules.quality("One HD Ⓐ+"));
        assertEquals("", rules.quality("### Sports"));
        assertEquals(Map.of("one", "1"), rules.aliases(Map.of("One HD", "1", "One UHD", "1"), true));
        assertTrue(rules.aliases(Map.of("One HD", "1", "One UHD", "2"), true).isEmpty());
        assertTrue(rules.aliases(Map.of("", "1"), false).isEmpty());
    }

    @Test
    void schedulesDiscardInvalidEntriesAndPreferSpecificSourcePrograms() {
        assertTrue(EpgSchedules.isGenericTitle(null));
        assertTrue(EpgSchedules.isGenericTitle(" News! "));
        assertFalse(EpgSchedules.isGenericTitle("The News Hour"));

        EpgProgramme invalid = new EpgProgramme("bad", "", 20, 10);
        EpgProgramme generic = new EpgProgramme("News", "generic", 0, 20);
        EpgProgramme specific = new EpgProgramme("The News Hour", "specific", 10, 30);
        EpgProgramme later = new EpgProgramme("Late", "", 30, 40);
        List<EpgProgramme> normalized = EpgSchedules.normalize(List.of(invalid, generic, specific, specific, later));
        assertEquals(List.of(new EpgProgramme("News", "generic", 0, 10),
                new EpgProgramme("The News Hour", "specific", 10, 30), later), normalized);

        List<EpgProgramme> preferred = EpgSchedules.preferSources(List.of(
                List.of(new EpgProgramme("News", "generic", 0, 20)),
                List.of(new EpgProgramme("The News Hour", "specific", 10, 30))));
        assertEquals("News", preferred.getFirst().title());
        assertEquals("The News Hour", preferred.get(1).title());
    }

    @Test
    void nativeParserHandlesProviderShapesEncodingsAndTimestampVariants() {
        long now = System.currentTimeMillis();
        long start = now + 60_000;
        String startSeconds = Long.toString(start / 1000);
        String stopSeconds = Long.toString((start + 60_000) / 1000);
        String plain = "{\"data\":[{\"channel_id\":\"ch\",\"start_timestamp\":\"" + startSeconds
                + "\",\"duration\":60,\"title\":\"News\",\"description\":\"Details\"}]}";
        assertEquals("News", NativeEpgApplicationService.parse(plain, false, "UTC", "ch").getFirst().title());
        assertTrue(NativeEpgApplicationService.parse(null, false, "UTC", "ch").isEmpty());
        assertTrue(NativeEpgApplicationService.parse(" ", false, "UTC", "ch").isEmpty());
        assertTrue(NativeEpgApplicationService.parse("not-json", false, "UTC", "ch").isEmpty());

        String encodedTitle = java.util.Base64.getEncoder().encodeToString("Encoded".getBytes(StandardCharsets.UTF_8));
        String encoded = "{\"js\":{\"epg_listings\":[{\"ch_id\":\"ch\",\"start\":\""
                + Instant.ofEpochMilli(start) + "\",\"stop\":\"" + Instant.ofEpochMilli(start + 60_000)
                + "\",\"title\":\"" + encodedTitle + "\"}]}}";
        assertEquals("Encoded", NativeEpgApplicationService.parse(encoded, true, "UTC", "ch").getFirst().title());
        assertTrue(NativeEpgApplicationService.parse("{\"ch\":[]}", false, "UTC", "ch").isEmpty());
        assertTrue(NativeEpgApplicationService.parse("{\"data\":[{\"ch_id\":\"other\"}]}", false, "UTC", "ch").isEmpty());
        assertEquals(0, NativeEpgApplicationService.time(null, ZoneOffset.UTC));
        assertEquals(0, NativeEpgApplicationService.time("bad", ZoneOffset.UTC));
        assertTrue(NativeEpgApplicationService.time(startSeconds, ZoneOffset.UTC) > 0);
        assertTrue(NativeEpgApplicationService.time(stopSeconds, ZoneOffset.UTC) > 0);
        assertTrue(NativeEpgApplicationService.time("2025-01-01T00:00:00Z", ZoneOffset.UTC) > 0);
        assertTrue(NativeEpgApplicationService.time("2025-01-01 00:00:00+01:00", ZoneOffset.UTC) > 0);
        assertTrue(NativeEpgApplicationService.time("2025-01-01 00:00:00", ZoneOffset.UTC) > 0);
    }

    @Test
    void xmltvReaderNormalizesNamesIconsAndOverlappingProgrammeEnds() throws Exception {
        Instant now = Instant.now();
        DateTimeFormatter format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss Z").withZone(ZoneOffset.UTC);
        String xml = "<tv>"
                + "<channel id='one'><display-name>One HD (1080p)</display-name><icon src=' https://img/one '/></channel>"
                + "<channel id='two'><display-name>One HD</display-name></channel>"
                + "<programme channel='one' start='" + format.format(now.minusSeconds(60)) + "' stop='" + format.format(now.plusSeconds(60)) + "'><title>First</title><desc>A</desc></programme>"
                + "<programme channel='one' start='" + format.format(now.plusSeconds(60)) + "'><title>Second</title></programme>"
                + "<programme channel='one' start='" + format.format(now.plusSeconds(120)) + "'><title>Third</title></programme>"
                + "<programme channel='two' start='" + format.format(now) + "' stop='" + format.format(now.plusSeconds(60)) + "'><title>Ambiguous</title></programme>"
                + "</tv>";
        XmltvGuide guide = XmltvGuide.read(new ByteArrayInputStream(xml.getBytes(StandardCharsets.UTF_8)), now);
        assertEquals(2, guide.forChannel("one", "ignored").size());
        assertEquals(1, guide.forChannel(null, "One HD").size());
        assertTrue(guide.icons().get("one").contains("https://img/one"));
        assertTrue(guide.estimatedBytes() > 0);
        assertEquals("one", XmltvGuide.normalizeQuality("One HD (1080p)"));
        assertEquals("onehdin", XmltvGuide.normalizeIdentifier(" One.HD/in "));
        assertEquals("one hd", XmltvGuide.normalizeDisplayName("One HD (1080p) [geo-blocked]"));
        assertNull(XmltvGuide.time("bad"));
        assertNotNull(XmltvGuide.time("202501010000 +0100"));
        guide.close();
    }

    @Test
    void mappingServiceValidatesIdentitiesAndPersistsAndRemovesMappings() {
        var config = ConfigurationService.getInstance().read();
        config.setEpgMappingsJson("not-json");
        ConfigurationService.getInstance().save(config);
        var service = new EpgMappingApplicationService();
        assertEquals("", service.read(null, "channel"));
        assertEquals("", service.read("account", null));
        assertEquals("", service.read("account", "channel"));
        assertThrows(IllegalArgumentException.class, () -> service.save("", "channel", "guide"));
        assertThrows(IllegalArgumentException.class, () -> service.save("account", "", "guide"));
        service.save("account", "channel", " guide ");
        assertEquals("guide", service.read("account", "channel"));
        service.save("account", "channel", "");
        assertEquals("", service.read("account", "channel"));
    }

    @Test
    void nativeScheduleGuardsInvalidAccountsAndUsesStalkerAndXtremeActions() {
        Channel channel = new Channel();
        channel.setChannelId("channel");
        long start = System.currentTimeMillis() + 1_000;
        String body = "{\"data\":[{\"ch_id\":\"channel\",\"start_timestamp\":\"" + start / 1000
                + "\",\"duration\":60,\"title\":\"Specific programme\"}]}";
        Account stalker = new Account();
        stalker.setType(AccountType.STALKER_PORTAL);
        stalker.setAction(Account.AccountAction.itv);
        stalker.setToken("token");
        Account xtreme = new Account();
        xtreme.setType(AccountType.XTREME_API);
        xtreme.setAction(Account.AccountAction.itv);
        Account invalid = new Account();
        invalid.setType(AccountType.M3U8_URL);
        invalid.setAction(Account.AccountAction.itv);

        try (MockedStatic<FetchAPI> fetch = org.mockito.Mockito.mockStatic(FetchAPI.class);
             MockedStatic<XtremeApiParser> xtremeParser = org.mockito.Mockito.mockStatic(XtremeApiParser.class);
             var constructions = org.mockito.Mockito.mockConstruction(EpgApplicationService.class,
                     (mock, context) -> org.mockito.Mockito.when(mock.isEnabled()).thenReturn(true))) {
            fetch.when(() -> FetchAPI.fetchWithDiagnostics(org.mockito.ArgumentMatchers.anyMap(),
                    org.mockito.ArgumentMatchers.any(Account.class), org.mockito.ArgumentMatchers.any()))
                    .thenReturn(FetchAPI.FetchResult.success(body, 200));
            xtremeParser.when(() -> XtremeApiParser.fetchEpg(xtreme, "get_simple_data_table", "channel"))
                    .thenReturn(body);
            NativeEpgApplicationService service = new NativeEpgApplicationService();
            assertTrue(service.schedule(stalker, channel).size() >= 1);
            assertTrue(service.schedule(xtreme, channel).size() >= 1);
            assertTrue(service.schedule(null, channel).isEmpty());
            assertTrue(service.schedule(invalid, channel).isEmpty());
        }
    }
}
