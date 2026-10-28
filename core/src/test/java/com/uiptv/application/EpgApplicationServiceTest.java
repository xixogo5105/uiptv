package com.uiptv.application;

import com.uiptv.model.*;
import com.uiptv.service.*;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EpgApplicationServiceTest extends DbBackedTest {
    @BeforeEach void enable() {
        var config = ConfigurationService.getInstance().read(); config.setEpgEnabled(true);
        ConfigurationService.getInstance().save(config);
    }
    private Account account(String source) {
        var account = new Account(); account.setDbId("live-account"); account.setType(AccountType.M3U8_URL);
        account.setAction(Account.AccountAction.itv); account.setEpg(source); return account;
    }
    private Channel channel() {
        var channel = new Channel(); channel.setChannelId("provider-channel"); channel.setName("One HD");
        channel.setExtraJson("{\"tvg-id\":\"one\"}"); return channel;
    }
    private String xml(String title) {
        var format = DateTimeFormatter.ofPattern("uuuuMMddHHmmss Z").withZone(ZoneOffset.UTC);
        var now = Instant.now();
        return "<tv><channel id='one'><display-name>One HD</display-name></channel><channel id='other'><display-name>Other</display-name></channel>"
                + "<programme channel='other' start='"+format.format(now.minusSeconds(60))+"' stop='"+format.format(now.plusSeconds(3600))+"'><title>Other programme</title></programme>"
                + "<programme channel='one' start='"+format.format(now.minusSeconds(60))+"' stop='"+format.format(now.plusSeconds(3600))+"'><title>"+title+"</title><desc>Details</desc></programme></tv>";
    }
    @Test void eachExplicitRequestReadsFreshAccountSourceAndRetainsOnlyTheChannel() throws Exception {
        Path source = tempDir.resolve("guide.xml"); Files.writeString(source, xml("First programme"));
        var service = new EpgApplicationService(); var account = account(source.toString());
        assertEquals("First programme", service.schedule(account, channel()).getFirst().title());
        Files.writeString(source, xml("Changed live programme"));
        var programmes = service.schedule(account, channel());
        assertEquals(1, programmes.size()); assertEquals("Changed live programme", programmes.getFirst().title());
    }
    @Test void globalSourceAndLegacyPlaybackModeAreIgnoredAndCataloguesNeverFetch() throws Exception {
        Path global = tempDir.resolve("global.xml"); Files.writeString(global, xml("Global programme"));
        var config = ConfigurationService.getInstance().read(); config.setGlobalEpgSource(global.toString()); config.setEpgPlaybackOnly(false);
        ConfigurationService.getInstance().save(config);
        var service = new EpgApplicationService(); var channel = channel();
        channel.setEpgNow(new EpgProgramme("Old card data", "", 1, 2));
        try (var nativeRequests = mockConstruction(NativeEpgApplicationService.class, (mock, context) -> when(mock.schedule(any(), any())).thenReturn(List.of()))) {
            service.enrichCached(List.of(channel));
            assertNull(channel.getEpgNow()); assertNull(channel.getEpgNext()); assertTrue(nativeRequests.constructed().isEmpty());
            assertTrue(service.schedule(account(""), channel).isEmpty());
        }
        assertTrue(service.isPlaybackOnly()); assertFalse(service.isBackgroundRefreshEnabled());
    }
    @Test void nativeGuideIsRequestedAgainForEveryExplicitCallWithProviderIdentity() {
        var account = account(""); account.setType(AccountType.XTREME_API);
        var channel = channel(); long now = System.currentTimeMillis();
        try (var requests = mockConstruction(NativeEpgApplicationService.class, (mock, context) ->
                when(mock.schedule(any(), any())).thenReturn(List.of(new EpgProgramme("Live news", "", now-1000, now+60000))))) {
            var service = new EpgApplicationService();
            assertEquals("Live news", service.schedule(account, channel).getFirst().title());
            assertEquals("Live news", service.schedule(account, channel).getFirst().title());
            assertEquals(2, requests.constructed().size());
            requests.constructed().forEach(request -> verify(request).schedule(account, channel));
        }
    }
    @Test void disabledEpgDoesNotOpenSourceOrProvider() {
        var config = ConfigurationService.getInstance().read(); config.setEpgEnabled(false); ConfigurationService.getInstance().save(config);
        try (var requests = mockConstruction(NativeEpgApplicationService.class)) {
            assertTrue(new EpgApplicationService().schedule(account("missing.xml"), channel()).isEmpty());
            assertTrue(requests.constructed().isEmpty());
        }
    }
    @Test void playerGuideTranslatesLocalIdsAndRejectsAnotherAccountsCategory() throws Exception {
        var account = account(""); account.setDbId(null); account.setAccountName("Live identity"); account.setType(AccountType.XTREME_API);
        com.uiptv.db.AccountDb.get().save(account);
        account = AccountService.getInstance().getByName("Live identity");
        account.setAction(Account.AccountAction.itv);
        com.uiptv.db.CategoryDb.get().saveAll(List.of(new Category("provider-category", "Live", "Live", false, 0)), account);
        var category = com.uiptv.db.CategoryDb.get().getCategories(account).getFirst();
        com.uiptv.db.ChannelDb.get().saveAll(List.of(channel()), category.getDbId(), account);
        var saved = ChannelService.getInstance().getChannelByChannelIdAndAccount("provider-channel", account.getDbId(), false);
        long now = System.currentTimeMillis();
        try (var requests = mockConstruction(NativeEpgApplicationService.class, (mock, context) -> {
            when(mock.schedule(any(), any())).thenAnswer(invocation -> {
                Channel requested = invocation.getArgument(1);
                assertEquals("provider-channel", requested.getChannelId());
                assertNotEquals(requested.getDbId(), requested.getChannelId());
                return List.of(new EpgProgramme("Live identity news", "", now-1000, now+60000));
            });
        })) {
            var service = new PlayerEpgApplicationService();
            assertEquals("Live identity news", service.lookup(new PlayerEpgApplicationService.Query(account.getDbId(), saved.getDbId(), category.getDbId(), "")).now().title());
            assertNull(service.lookup(new PlayerEpgApplicationService.Query(account.getDbId(), saved.getChannelId(), category.getDbId(), "")).now());
            assertNull(service.lookup(new PlayerEpgApplicationService.Query("another-account", saved.getDbId(), category.getDbId(), "")).now());
            assertEquals(1, requests.constructed().size());
        }
    }
    @Test void readerDoesNotGuessBetweenAmbiguousNames() throws Exception {
        String xml = xml("One").replace("<display-name>Other</display-name>", "<display-name>One HD</display-name>");
        assertTrue(LiveXmltvReader.read(new java.io.ByteArrayInputStream(xml.getBytes(java.nio.charset.StandardCharsets.UTF_8)), "missing", "One HD", Instant.now()).isEmpty());
    }
}
