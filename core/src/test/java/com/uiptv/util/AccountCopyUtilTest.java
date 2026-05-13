package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountCopyUtilTest {

    @Test
    void copyForMac_buildsTransientCloneForRequestedMac() {
        Account source = new Account(
                "Account One",
                "user-1",
                "pass-1",
                "http://example.com/c/",
                "00:1A:79:AA:AA:AA",
                "00:1A:79:AA:AA:AA,00:1A:79:BB:BB:BB",
                "serial-1",
                "device-1",
                "device-2",
                "signature-1",
                AccountType.STALKER_PORTAL,
                "epg-1",
                "playlist-1",
                true
        );
        source.setAction(Account.AccountAction.series);
        source.setHttpMethod("POST");
        source.setTimezone("Europe/Amsterdam");
        source.setResolveChainAndDeepRedirects(true);
        source.setServerPortalUrl("http://example.com/stalker_portal/server/load.php");
        source.setDbId("account-db-id");
        source.setToken("session-token");

        Account copy = AccountCopyUtil.copyForMac(source, "00:1A:79:CC:CC:CC");

        assertEquals("00:1A:79:CC:CC:CC", copy.getMacAddress());
        assertEquals(source.getAccountName(), copy.getAccountName());
        assertEquals(source.getUsername(), copy.getUsername());
        assertEquals(source.getPassword(), copy.getPassword());
        assertEquals(source.getUrl(), copy.getUrl());
        assertEquals("00:1A:79:AA:AA:AA,00:1A:79:BB:BB:BB,00:1A:79:CC:CC:CC", copy.getMacAddressList());
        assertEquals(source.getSerialNumber(), copy.getSerialNumber());
        assertEquals(source.getDeviceId1(), copy.getDeviceId1());
        assertEquals(source.getDeviceId2(), copy.getDeviceId2());
        assertEquals(source.getSignature(), copy.getSignature());
        assertEquals(source.getAction(), copy.getAction());
        assertEquals(source.getHttpMethod(), copy.getHttpMethod());
        assertEquals(source.getTimezone(), copy.getTimezone());
        assertEquals(source.getServerPortalUrl(), copy.getServerPortalUrl());
        assertTrue(copy.isNotConnected());
        assertNull(copy.getDbId());
        assertNull(copy.getToken());
    }
}
