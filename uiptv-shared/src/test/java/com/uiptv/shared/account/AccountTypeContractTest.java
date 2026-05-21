package com.uiptv.shared.account;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountTypeContractTest {
    @Test
    void displayNamesMatchSharedUiContracts() {
        assertEquals("Stalker Portal", AccountType.STALKER_PORTAL.getDisplay());
        assertEquals("Xtreme API", AccountType.XTREME_API.getDisplay());
        assertEquals("M3U8 URL", AccountType.M3U8_URL.getDisplay());
        assertEquals("M3U8 Local", AccountType.M3U8_LOCAL.getDisplay());
    }

    @Test
    void capabilitySetsDocumentSupportedAccountFeatures() {
        assertAccountTypes(AccountType.CACHE_REFRESH_SUPPORTED,
                AccountType.STALKER_PORTAL,
                AccountType.XTREME_API,
                AccountType.M3U8_URL,
                AccountType.M3U8_LOCAL
        );

        assertAccountTypes(AccountType.VOD_AND_SERIES_SUPPORTED,
                AccountType.STALKER_PORTAL, AccountType.XTREME_API);

        assertAccountTypes(AccountType.PREDEFINED_URL_SUPPORTED,
                AccountType.M3U8_URL, AccountType.M3U8_LOCAL);

        assertFalse(AccountType.VOD_AND_SERIES_SUPPORTED.contains(AccountType.M3U8_URL));
        assertTrue(AccountType.PREDEFINED_URL_SUPPORTED.contains(AccountType.M3U8_URL));
    }

    @Test
    void capabilitySetsAreImmutable() {
        assertThrows(UnsupportedOperationException.class,
                () -> AccountType.CACHE_REFRESH_SUPPORTED.add(AccountType.STALKER_PORTAL));
    }

    private static void assertAccountTypes(Set<AccountType> actual, AccountType... expected) {
        assertEquals(Set.of(expected), actual);
    }
}
