package com.uiptv.ui;

import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class AccountInfoUiUtilTest {

    @Test
    void parseDateValue_formatsKnownDate() {
        AccountInfoUiUtil.ParsedDate parsed = AccountInfoUiUtil.parseDateValue("2027-02-16 00:00:00");
        assertEquals("2027-02-16 00:00:00", parsed.display());
        assertNotNull(parsed.instant());
    }

    @Test
    void resolveExpiryState_handlesThresholds() {
        assertEquals(AccountInfoUiUtil.ExpiryState.EXPIRED,
                AccountInfoUiUtil.resolveExpiryState(Instant.now().minus(1, ChronoUnit.DAYS)));
        assertEquals(AccountInfoUiUtil.ExpiryState.WARNING,
                AccountInfoUiUtil.resolveExpiryState(Instant.now().plus(3, ChronoUnit.DAYS)));
        assertEquals(AccountInfoUiUtil.ExpiryState.OK,
                AccountInfoUiUtil.resolveExpiryState(Instant.now().plus(10, ChronoUnit.DAYS)));
    }

    @Test
    void resolveStatusState_mapsKnownValues() {
        assertEquals(AccountInfoUiUtil.StatusState.ACTIVE, AccountInfoUiUtil.resolveStatusState("active"));
        assertEquals(AccountInfoUiUtil.StatusState.SUSPENDED, AccountInfoUiUtil.resolveStatusState("suspended"));
        assertEquals(AccountInfoUiUtil.StatusState.BLOCKED, AccountInfoUiUtil.resolveStatusState("blocked"));
        assertEquals(AccountInfoUiUtil.StatusState.UNKNOWN, AccountInfoUiUtil.resolveStatusState("other"));
    }
}
