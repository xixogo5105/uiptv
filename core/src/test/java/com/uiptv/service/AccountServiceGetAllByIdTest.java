package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static com.uiptv.model.Account.AccountAction.itv;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Coverage for {@link AccountService#getAllById()}, the lossless account view.
 *
 * <p>{@code getAll()} is keyed by account name because bookmarks and playback requests resolve
 * accounts that way, and {@code accountName} is {@code UNIQUE} so those entries cannot collide.
 * {@code getAllById()} keys by primary key and is what bulk enumeration should use, so identity is
 * stated directly rather than inferred from a display name.
 */
class AccountServiceGetAllByIdTest extends DbBackedTest {

    @Test
    void getAllById_keysByPrimaryKey_soBulkOperationsCanAddressAccountsUniquely() {
        Account first = save("alpha", "user-a");
        Account second = save("beta", "user-b");
        Account third = save("gamma", "user-c");

        Map<String, Account> byId = AccountService.getInstance().getAllById();

        assertEquals(3, byId.size());
        assertEquals("alpha", byId.get(first.getDbId()).getAccountName());
        assertEquals("beta", byId.get(second.getDbId()).getAccountName());
        assertEquals("gamma", byId.get(third.getDbId()).getAccountName());
    }

    @Test
    void getAllById_isKeyedById_notByName_soIdentityIsExplicit() {
        Account first = save("alpha", "user-a");
        Account second = save("beta", "user-b");

        Map<String, Account> byId = AccountService.getInstance().getAllById();
        Map<String, Account> byName = AccountService.getInstance().getAll();

        assertEquals(2, byId.size());
        assertEquals(2, byName.size());
        assertEquals("alpha", byName.get("alpha").getAccountName(),
                "Name-keyed lookup stays available for bookmark joins");
        assertEquals("beta", byId.get(second.getDbId()).getAccountName(),
                "Id-keyed lookup is the one bulk callers should use");
        assertEquals("alpha", byId.get(first.getDbId()).getAccountName());
    }

    @Test
    void getAllById_appliesCachedSessionTokens_likeGetAll() {
        Account account = save("token-carrier", "user-t");

        Account forTokenSync = AccountService.getInstance().getById(account.getDbId());
        forTokenSync.setToken("session-token-x");
        AccountService.getInstance().syncSessionToken(forTokenSync);

        Account persisted = AccountDbProbe.readFresh(account.getDbId());
        assertNull(persisted.getToken(), "Token lives only in the session cache, not the DB row");

        assertEquals("session-token-x", AccountService.getInstance().getAllById().get(account.getDbId()).getToken());
    }

    @Test
    void getAllById_returnsEmptyMap_whenNoAccountsExist() {
        assertTrue(AccountService.getInstance().getAllById().isEmpty());
    }

    private Account save(String name, String username) {
        Account account = new Account(
                name,
                username,
                "pass",
                "http://portal.example/",
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
        AccountService.getInstance().save(account);
        return AccountService.getInstance().getByName(name);
    }

    /** Reads straight from the DB, bypassing AccountService's session-token cache. */
    private static final class AccountDbProbe {
        private AccountDbProbe() {
        }

        static Account readFresh(String dbId) {
            return com.uiptv.db.AccountDb.get().getAccountById(dbId);
        }
    }
}
