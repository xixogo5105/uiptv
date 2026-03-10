package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.AccountType;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class AccountResolverTest extends DbBackedTest {

    @Test
    void resolveAccounts_includesPinMetadata() {
        Account account = new Account("acc-pin", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.M3U8_URL, null, "http://test/list.m3u8", true);
        AccountService.getInstance().save(account);

        AccountResolver resolver = new AccountResolver();
        List<AccountResolver.AccountRow> rows = resolver.resolveAccounts();

        AccountResolver.AccountRow row = rows.stream()
                .filter(r -> "acc-pin".equals(r.getAccountName()))
                .findFirst()
                .orElseThrow();

        assertTrue(row.isPinToTop());
        assertEquals(AccountResolver.PIN_SVG_HEAD_PATH, row.getPinSvgHeadPath());
        assertEquals(AccountResolver.PIN_SVG_STEM_PATH, row.getPinSvgStemPath());
        assertEquals(AccountResolver.PIN_SVG_HEAD_FILL, row.getPinSvgHeadFill());
        assertEquals(AccountResolver.PIN_SVG_STEM_FILL, row.getPinSvgStemFill());
        assertEquals(AccountResolver.PIN_SVG_VIEW_BOX, row.getPinSvgViewBox());
        assertEquals(AccountResolver.PIN_SVG_SCALE, row.getPinSvgScale());
    }

    @Test
    void resolveAccounts_preservesUnpinnedFlag() {
        Account account = new Account("acc-unpinned", "user", "pass", "http://test", null, null, null, null, null, null,
                AccountType.XTREME_API, null, "http://test/list.m3u8", false);
        AccountService.getInstance().save(account);

        AccountResolver resolver = new AccountResolver();
        List<AccountResolver.AccountRow> rows = resolver.resolveAccounts();

        AccountResolver.AccountRow row = rows.stream()
                .filter(r -> "acc-unpinned".equals(r.getAccountName()))
                .findFirst()
                .orElseThrow();

        assertFalse(row.isPinToTop());
    }
}
