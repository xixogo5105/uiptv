package com.uiptv.model;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class AccountStatusTest {

    @Test
    void fromValue_parsesEnumIgnoringCase() {
        assertEquals(AccountStatus.ACTIVE, AccountStatus.fromValue("active"));
        assertEquals(AccountStatus.SUSPENDED, AccountStatus.fromValue("SUSPENDED"));
    }

    @Test
    void fromValue_returnsNullForUnknown() {
        assertNull(AccountStatus.fromValue("disabled"));
        assertNull(AccountStatus.fromValue(""));
    }

    @Test
    void toDisplay_returnsLowerCase() {
        assertEquals("active", AccountStatus.ACTIVE.toDisplay());
        assertEquals("suspended", AccountStatus.SUSPENDED.toDisplay());
    }
}
