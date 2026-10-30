package com.uiptv.application;

import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import org.junit.jupiter.api.Test;

import java.util.List;

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
}
