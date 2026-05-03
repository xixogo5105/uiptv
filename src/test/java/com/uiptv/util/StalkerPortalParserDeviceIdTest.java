package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StalkerPortalParserDeviceIdTest {

    @Test
    void testDeviceId1Slash2StillCreatesSeparateAccountWhenExtraParamsExist() {
        String hex = "584188650E91CD5F6EAB7D60352086D92F85ACA8AD2DF33E4FC37893D8A90ABB";
        String input = "http://www.abctesturl.io/stalker_portal/c/\n"
                + "00:1A:79:00:00:00\n"
                + "00:1A:79:00:00:01\n"
                + "DEVICE ID 1/2 : " + hex + "\n";

        List<Account> savedAccounts = new ArrayList<>();
        StalkerPortalParser parser = new StalkerPortalParser(name -> null, savedAccounts::add);

        parser.parseAndSave(input, true, false);

        assertEquals(2, savedAccounts.size(), "Expected the extra-param MAC to stay separate");

        Account simple = savedAccounts.stream()
                .filter(account -> "00:1A:79:00:00:00".equals(account.getMacAddress()))
                .findFirst()
                .orElseThrow();
        assertEquals("00:1A:79:00:00:00", simple.getMacAddressList());
        assertNull(simple.getDeviceId1());
        assertNull(simple.getDeviceId2());

        Account extra = savedAccounts.stream()
                .filter(account -> "00:1A:79:00:00:01".equals(account.getMacAddress()))
                .findFirst()
                .orElseThrow();
        assertEquals("00:1A:79:00:00:01", extra.getMacAddressList());
        assertEquals(hex, extra.getDeviceId1());
        assertEquals(hex, extra.getDeviceId2());
    }

    @Test
    void testMatchingExtraParamAccountsGroupMacsIntoSingleSeparateAccount() {
        String serial = "ABCDEF123456";
        String device1 = "584188650E91CD5F6EAB7D60352086D92F85ACA8AD2DF33E4FC37893D8A90ABB";
        String device2 = "112288650E91CD5F6EAB7D60352086D92F85ACA8AD2DF33E4FC37893D8A90ABB";
        String signature = "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA";
        String input = "http://www.abctesturl.io/stalker_portal/c/\n"
                + "00:1A:79:00:00:00\n"
                + "SN: " + serial + "\n"
                + "DEVICE ID 1 : " + device1 + "\n"
                + "DEVICE ID 2 : " + device2 + "\n"
                + "SIGNATURE : " + signature + "\n"
                + "00:1A:79:00:00:01\n"
                + "SN: " + serial + "\n"
                + "DEVICE ID 1 : " + device1 + "\n"
                + "DEVICE ID 2 : " + device2 + "\n"
                + "SIGNATURE : " + signature + "\n";

        List<Account> savedAccounts = new ArrayList<>();
        StalkerPortalParser parser = new StalkerPortalParser(name -> null, savedAccounts::add);

        parser.parseAndSave(input, true, false);

        assertEquals(1, savedAccounts.size(), "Expected matching extra-param accounts to group their MACs");
        Account acct = savedAccounts.get(0);
        assertEquals("00:1A:79:00:00:00,00:1A:79:00:00:01", acct.getMacAddressList());
        assertEquals(serial, acct.getSerialNumber());
        assertEquals(device1, acct.getDeviceId1());
        assertEquals(device2, acct.getDeviceId2());
        assertEquals(signature, acct.getSignature());
    }
}
