package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import com.uiptv.util.XtremeCredentialsJson;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccountServiceCredentialsGuardTest extends DbBackedTest {

    @Test
    void xtremeSeedsSingleCredentialWhenMissingJson() {
        Account account = new Account(
                "xtreme-guard-1",
                "user1",
                "pass1",
                "http://xtreme.test",
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.XTREME_API,
                null,
                "http://xtreme.test",
                false
        );
        AccountService.getInstance().save(account);

        Account reloaded = AccountService.getInstance().getByName("xtreme-guard-1");
        List<XtremeCredentialsJson.Entry> entries = XtremeCredentialsJson.parse(reloaded.getXtremeCredentialsJson());
        assertEquals(1, entries.size());
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(entries);
        assertNotNull(defaultEntry);
        assertEquals("user1", defaultEntry.username());
        assertEquals("pass1", defaultEntry.password());
    }

    @Test
    void xtremeNormalizesDefaultToUsernameWhenJsonProvided() {
        Account account = new Account(
                "xtreme-guard-2",
                "beta",
                "passB",
                "http://xtreme.test",
                null,
                null,
                null,
                null,
                null,
                null,
                AccountType.XTREME_API,
                null,
                "http://xtreme.test",
                false
        );
        account.setXtremeCredentialsJson(XtremeCredentialsJson.toJson(List.of(
                new XtremeCredentialsJson.Entry("alpha", "passA", false),
                new XtremeCredentialsJson.Entry("beta", "passB", false)
        )));
        AccountService.getInstance().save(account);

        Account reloaded = AccountService.getInstance().getByName("xtreme-guard-2");
        List<XtremeCredentialsJson.Entry> entries = XtremeCredentialsJson.parse(reloaded.getXtremeCredentialsJson());
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(entries);
        assertNotNull(defaultEntry);
        assertEquals("beta", defaultEntry.username());
        assertEquals("passB", defaultEntry.password());
    }

    @Test
    void stalkerMacGuardKeepsSingleEntryAndDefault() {
        Account account = new Account(
                "stalker-guard-1",
                "user",
                "pass",
                "http://stalker.test",
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
        AccountService.getInstance().save(account);

        Account reloaded = AccountService.getInstance().getByName("stalker-guard-1");
        assertEquals("00:11:22:33:44:55", reloaded.getMacAddress());
        assertEquals("00:11:22:33:44:55", reloaded.getMacAddressList());
    }

    @Test
    void stalkerMacGuardSetsDefaultWhenMissing() {
        Account account = new Account(
                "stalker-guard-2",
                "user",
                "pass",
                "http://stalker.test",
                null,
                "00:11:22:33:44:56,00:11:22:33:44:57",
                null,
                null,
                null,
                null,
                AccountType.STALKER_PORTAL,
                null,
                null,
                false
        );
        AccountService.getInstance().save(account);

        Account reloaded = AccountService.getInstance().getByName("stalker-guard-2");
        assertNotNull(reloaded.getMacAddress());
        assertFalse(reloaded.getMacAddress().isBlank());
        assertFalse(reloaded.getMacAddressList().isBlank());
    }
}
