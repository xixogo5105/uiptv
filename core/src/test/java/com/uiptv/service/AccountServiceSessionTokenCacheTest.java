package com.uiptv.service;

import com.uiptv.db.AccountDb;
import com.uiptv.db.SeriesChannelDb;
import com.uiptv.model.Account;
import com.uiptv.model.Channel;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import java.util.List;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountServiceSessionTokenCacheTest extends DbBackedTest {

    @Override
    protected void afterDatabaseSetup() throws Exception {
        // Ensure singleton in-memory token cache is clean per test.
        AccountService.getInstance().deleteAll();
    }

    @Test
    void syncSessionToken_hydratesTokenOnAllReadPaths() {
        AccountService accountService = AccountService.getInstance();
        Account account = createStalkerAccount("token-cache-hydration");
        accountService.save(account);

        Account persisted = accountService.getByName(account.getAccountName());
        assertNotNull(persisted);
        assertTrue(persisted.isNotConnected());

        persisted.setToken("session-token-1");
        accountService.syncSessionToken(persisted);

        Account byName = accountService.getByName(account.getAccountName());
        assertEquals("session-token-1", byName.getToken());

        Account byId = accountService.getById(byName.getDbId());
        assertEquals("session-token-1", byId.getToken());

        Account fromGetAll = accountService.getAll().get(account.getAccountName());
        assertNotNull(fromGetAll);
        assertEquals("session-token-1", fromGetAll.getToken());
    }

    @Test
    void syncSessionToken_withBlankToken_removesCachedSession() {
        AccountService accountService = AccountService.getInstance();
        Account account = createStalkerAccount("token-cache-clear");
        accountService.save(account);

        Account persisted = accountService.getByName(account.getAccountName());
        persisted.setToken("session-token-2");
        accountService.syncSessionToken(persisted);
        assertEquals("session-token-2", accountService.getByName(account.getAccountName()).getToken());

        persisted.setToken(null);
        accountService.syncSessionToken(persisted);

        Account reloaded = accountService.getByName(account.getAccountName());
        assertTrue(reloaded.isNotConnected());
    }

    @Test
    void save_andDeleteAll_invalidateSessionTokenCache() {
        AccountService accountService = AccountService.getInstance();
        Account account = createStalkerAccount("token-cache-invalidate");
        accountService.save(account);

        Account persisted = accountService.getByName(account.getAccountName());
        persisted.setToken("session-token-3");
        accountService.syncSessionToken(persisted);
        assertEquals("session-token-3", accountService.getByName(account.getAccountName()).getToken());

        persisted.setUsername("updated-user");
        accountService.save(persisted);

        Account afterSave = accountService.getByName(account.getAccountName());
        assertTrue(afterSave.isNotConnected(), "Save should invalidate cached in-memory token");

        afterSave.setToken("session-token-4");
        accountService.syncSessionToken(afterSave);
        assertEquals("session-token-4", accountService.getByName(account.getAccountName()).getToken());

        accountService.deleteAll();

        accountService.save(createStalkerAccount("token-cache-invalidate"));
        Account recreated = accountService.getByName("token-cache-invalidate");
        assertNotNull(recreated);
        assertTrue(recreated.isNotConnected(), "deleteAll should clear in-memory token cache");
    }

    @Test
    void pinChangeKeepsResolvedPortalSessionAndLoadsFreshSeriesFromCache() throws Exception {
        AccountService accountService = AccountService.getInstance();
        Account account = createStalkerAccount("token-cache-pin-change");
        accountService.save(account);

        Account connected = accountService.getById(account.getDbId());
        connected.setServerPortalUrl("http://example.com/stalker_portal/server/load.php");
        AccountDb.get().saveServerPortalUrl(connected);
        connected.setAction(Account.AccountAction.series);
        connected.setToken("session-token-pin-change");
        accountService.syncSessionToken(connected);

        String categoryDbId = "series-category-db-id";
        Channel cachedSeries = new Channel();
        cachedSeries.setChannelId("series-1");
        cachedSeries.setName("Cached Series");
        cachedSeries.setCmd("cmd://cached-series");
        SeriesChannelDb.get().saveAll(List.of(cachedSeries), categoryDbId, connected);

        Account accountForm = accountService.getById(account.getDbId());
        accountForm.setPinToTop(true);
        // The account form does not display this internally resolved endpoint.
        accountForm.setServerPortalUrl(null);
        accountService.save(accountForm);

        Account reloaded = accountService.getById(account.getDbId());
        reloaded.setAction(Account.AccountAction.series);
        assertEquals("http://example.com/stalker_portal/server/load.php", reloaded.getServerPortalUrl());
        assertEquals("session-token-pin-change", reloaded.getToken());

        HandshakeService handshake = mock(HandshakeService.class);
        List<Channel> channels;
        try (MockedStatic<HandshakeService> handshakeStatic = mockStatic(HandshakeService.class)) {
            handshakeStatic.when(HandshakeService::getInstance).thenReturn(handshake);
            channels = ChannelService.getInstance().get("provider-category-id", reloaded, categoryDbId);
        }

        assertEquals(List.of("series-1"), channels.stream().map(Channel::getChannelId).toList());
        verify(handshake, never()).connect(any(Account.class));
    }

    private Account createStalkerAccount(String accountName) {
        return new Account(
                accountName,
                "user",
                "pass",
                "http://example.com/portal.php",
                "00:11:22:33:44:55",
                null,
                null,
                null,
                null,
                null,
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
    }
}
