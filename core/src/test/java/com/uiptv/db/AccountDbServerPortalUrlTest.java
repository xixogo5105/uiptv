package com.uiptv.db;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;
import com.uiptv.service.DbBackedTest;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import static com.uiptv.model.Account.AccountAction.itv;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Regression coverage for {@link AccountDb#saveServerPortalUrl(Account)}.
 *
 * <p>The previous implementation delegated to {@code save()}, a full 20-column upsert resolved by
 * {@code accountName}. That made a cache reload persist every field from its in-memory account
 * object, silently reverting any concurrent edit made after that object was materialised.
 *
 * <p>Note: {@code accountName} carries a {@code UNIQUE} constraint, so duplicate names cannot
 * actually occur; the cross-account overwrite risk is a stale-copy clobber, not a name collision.
 */
class AccountDbServerPortalUrlTest extends DbBackedTest {

    @Test
    void saveServerPortalUrl_updatesOnlyThatColumn_andLeavesOtherFieldsIntact() {
        Account account = newAccount("portal-url-single", "user-original", "pass-original");
        AccountService.getInstance().save(account);
        Account persisted = AccountService.getInstance().getByName("portal-url-single");

        persisted.setServerPortalUrl("http://portal.example/load.php");
        AccountDb.get().saveServerPortalUrl(persisted);

        Account reloaded = AccountService.getInstance().getById(persisted.getDbId());
        assertEquals("http://portal.example/load.php", reloaded.getServerPortalUrl());
        assertEquals("user-original", reloaded.getUsername());
        assertEquals("pass-original", reloaded.getPassword());
        assertEquals("http://example.com/", reloaded.getUrl());
        assertEquals("00:11:22:33:44:55", reloaded.getMacAddress());
    }

    @Test
    void saveServerPortalUrl_doesNotClobberConcurrentEdits_madeAfterTheAccountWasLoaded() {
        Account account = newAccount("portal-url-stale", "user-original", "pass-original");
        AccountService.getInstance().save(account);
        Account staleInMemoryCopy = AccountService.getInstance().getByName("portal-url-stale");

        // Simulate a concurrent edit (account dialog, web API, sync) that lands after the
        // in-memory copy used by the reload was materialised.
        Account concurrent = AccountService.getInstance().getById(staleInMemoryCopy.getDbId());
        concurrent.setUsername("user-edited-concurrently");
        concurrent.setUrl("http://edited.example/");
        AccountDb.get().save(concurrent);

        // The reload now persists the endpoint using its older, stale in-memory object.
        staleInMemoryCopy.setServerPortalUrl("http://portal.example/load.php");
        AccountDb.get().saveServerPortalUrl(staleInMemoryCopy);

        Account reloaded = AccountService.getInstance().getById(staleInMemoryCopy.getDbId());
        assertEquals("http://portal.example/load.php", reloaded.getServerPortalUrl());
        assertEquals("user-edited-concurrently", reloaded.getUsername(),
                "Persisting the endpoint must not revert a concurrent username edit");
        assertEquals("http://edited.example/", reloaded.getUrl(),
                "Persisting the endpoint must not revert a concurrent URL edit");
    }

    @Test
    void saveServerPortalUrl_isNoOp_whenAccountIsNullOrHasNoDbId() {
        Account noDbId = newAccount("portal-url-no-id", "u", "p");
        noDbId.setDbId("");
        AccountDb.get().saveServerPortalUrl(null);
        AccountDb.get().saveServerPortalUrl(noDbId);
    }

    private Account newAccount(String name, String username, String password) {
        Account account = new Account(
                name,
                username,
                password,
                "http://example.com/",
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
        account.setAction(itv);
        account.setServerPortalUrl("");
        return account;
    }
}
