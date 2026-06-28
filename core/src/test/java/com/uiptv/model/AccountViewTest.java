package com.uiptv.model;

import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountViewTest {

    @Test
    void accountViewSnapshotsMutableAccountAndBuildsModeAdapter() {
        Account source = sampleAccount();
        source.setAction(Account.AccountAction.itv);

        AccountView view = AccountView.from(source);

        source.setAccountName("Changed");
        source.setAction(Account.AccountAction.vod);
        source.setToken("changed-token");

        Account adapter = view.toAccount(Account.AccountAction.series);

        assertEquals("Original", adapter.getAccountName());
        assertEquals("account-1", adapter.getDbId());
        assertEquals("token-1", adapter.getToken());
        assertEquals(Account.AccountAction.series, adapter.getAction());
        assertEquals(Account.AccountAction.vod, source.getAction());
    }

    @Test
    void accountMediaContextPreservesSnapshotAndCreatesIndependentModeAdapters() {
        Account source = sampleAccount();
        source.setAction(Account.AccountAction.itv);

        AccountMediaContext context = AccountMediaContext.from(source, Account.AccountAction.vod);

        source.setServerPortalUrl("http://changed.example");
        source.setAction(Account.AccountAction.series);

        Account vodAdapter = context.toAccount();
        Account seriesAdapter = context.withAction(Account.AccountAction.series).toAccount();

        assertEquals("http://portal.example", vodAdapter.getServerPortalUrl());
        assertEquals(Account.AccountAction.vod, vodAdapter.getAction());
        assertEquals(Account.AccountAction.series, seriesAdapter.getAction());
        assertEquals(Account.AccountAction.series, source.getAction());
    }

    @Test
    void nullAccountProducesNoMediaContext() {
        assertNull(AccountMediaContext.from(null));
    }

    private Account sampleAccount() {
        Account account = new Account(
                "Original",
                "user",
                "pass",
                "http://playlist.example",
                "00:1A:79:00:00:01",
                null,
                "serial",
                "device1",
                "device2",
                "signature",
                AccountType.XTREME_API,
                "epg",
                "http://m3u.example/",
                true
        );
        account.setDbId("account-1");
        account.setToken("token-1");
        account.setServerPortalUrl("http://portal.example");
        account.setResolveChainAndDeepRedirects(true);
        account.setHttpMethod("POST");
        account.setTimezone("UTC");
        return account;
    }
}