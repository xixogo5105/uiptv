package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.lang.reflect.Method;
import com.uiptv.model.Bookmark;
import com.uiptv.service.BookmarkResolver;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class LiveGuideCoverageTest extends DbBackedTest {
    @Test
    void listAndHydrateGuardDisabledAndMissingAccounts() throws Exception {
        LiveGuideApplicationService service = new LiveGuideApplicationService();
        try (var constructions = mockConstruction(EpgApplicationService.class,
                (mock, context) -> when(mock.isEnabled()).thenReturn(false))) {
            assertTrue(service.list(null).isEmpty());
            assertFalse(constructions.constructed().isEmpty());
        }

        Account account = new Account();
        account.setDbId("missing");
        Channel channel = new Channel();
        channel.setChannelId("channel");
        LiveGuideApplicationService.Row row = new LiveGuideApplicationService.Row("missing", "Missing", "bookmark", channel, List.of());
        try (var accounts = mockStatic(AccountService.class)) {
            var accountService = mock(AccountService.class);
            accounts.when(AccountService::getInstance).thenReturn(accountService);
            when(accountService.getById("missing")).thenReturn(null);
            assertSame(row, service.hydrate(row));
            assertTrue(service.list("missing").isEmpty());
        }
    }

    @Test
    void hydrateSchedulesLiveAccountAndVisibleFiltersRows() {
        Account account = new Account();
        account.setDbId("account");
        account.setAccountName("Account");
        account.setAction(Account.AccountAction.vod);
        Channel channel = new Channel();
        channel.setChannelId("channel");
        channel.setDbId("channel-db");
        List<EpgProgramme> schedule = List.of(new EpgProgramme("Now", "", 0, 100));
        LiveGuideApplicationService.Row row = new LiveGuideApplicationService.Row("account", "Account", "", channel, List.of());
        try (var accounts = mockStatic(AccountService.class);
             var constructions = mockConstruction(EpgApplicationService.class,
                     (mock, context) -> when(mock.schedule(any(), any())).thenReturn(schedule))) {
            var accountService = mock(AccountService.class);
            accounts.when(AccountService::getInstance).thenReturn(accountService);
            when(accountService.getById("account")).thenReturn(account);
            var hydrated = new LiveGuideApplicationService().hydrate(row);
            assertEquals(1, hydrated.programmes().size());
            assertEquals(Account.AccountAction.itv, account.getAction());
            assertFalse(constructions.constructed().isEmpty());
        }
    }

    @Test
    void listAccountMapsCatalogChannelsAndPublishesReadyRows() throws Exception {
        Account account = new Account();
        account.setDbId("account");
        account.setAccountName("Account");
        account.setType(com.uiptv.util.AccountType.XTREME_API);
        Channel channel = new Channel();
        channel.setChannelId("channel");
        List<Channel> channels = List.of(channel);
        try (var accounts = mockStatic(AccountService.class);
             var catalog = mockStatic(CatalogApplicationService.class);
             var constructions = mockConstruction(EpgApplicationService.class,
                     (mock, context) -> when(mock.isEnabled()).thenReturn(true))) {
            var accountService = mock(AccountService.class);
            accounts.when(AccountService::getInstance).thenReturn(accountService);
            when(accountService.getById("account")).thenReturn(account);
            var catalogService = mock(CatalogApplicationService.class);
            catalog.when(CatalogApplicationService::getInstance).thenReturn(catalogService);
            when(catalogService.listChannels(any(CatalogChannelsQuery.class), eq(false))).thenReturn(channels);
            var ready = new java.util.ArrayList<List<LiveGuideApplicationService.Row>>();
            var rows = new LiveGuideApplicationService().list("account", ready::add);
            assertEquals(1, rows.size());
            assertEquals(1, ready.getFirst().size());
            assertTrue(rows.getFirst().nativeProvider());
            assertFalse(constructions.constructed().isEmpty());
        }
    }

    @Test
    void bookmarkAndProviderHelpersHandleFallbackSnapshotsAndProviderTypes() throws Exception {
        LiveGuideApplicationService service = new LiveGuideApplicationService();
        Bookmark bookmark = new Bookmark("Account", "Live", "provider-channel", "Fallback name", "cmd", "http://portal", "cat");
        bookmark.setLogo("logo");
        bookmark.setChannelJson("not-json");
        Method bookmarkChannel = LiveGuideApplicationService.class.getDeclaredMethod("bookmarkChannel", Bookmark.class);
        bookmarkChannel.setAccessible(true);
        Channel channel = (Channel) bookmarkChannel.invoke(service, bookmark);
        assertEquals("provider-channel", channel.getChannelId());
        assertEquals("Fallback name", channel.getName());
        assertEquals("logo", channel.getLogo());

        Method nativeProvider = LiveGuideApplicationService.class.getDeclaredMethod("nativeProvider", Account.class);
        nativeProvider.setAccessible(true);
        Account account = new Account();
        account.setType(com.uiptv.util.AccountType.STALKER_PORTAL);
        assertTrue((Boolean) nativeProvider.invoke(service, account));
        account.setType(com.uiptv.util.AccountType.M3U8_URL);
        assertFalse((Boolean) nativeProvider.invoke(service, account));

        BookmarkResolver.ResolvedBookmark resolved = mock(BookmarkResolver.ResolvedBookmark.class);
        when(resolved.getBookmark()).thenReturn(bookmark);
        when(resolved.getAccount()).thenReturn(null);
        when(resolved.getAccountAction()).thenReturn(Account.AccountAction.vod);
        Method rowMethod = LiveGuideApplicationService.class.getDeclaredMethod("bookmarkRow", BookmarkResolver.ResolvedBookmark.class, java.util.Set.class);
        rowMethod.setAccessible(true);
        assertNull(rowMethod.invoke(service, resolved, new java.util.HashSet<>()));
    }
}
