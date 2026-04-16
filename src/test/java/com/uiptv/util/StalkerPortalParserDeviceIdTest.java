package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class StalkerPortalParserDeviceIdTest {

    @Test
    void testDeviceId1Slash2AndGroupingCreatesSingleAccountWithBothMacsAndDeviceIds() {
        String hex = "584188650E91CD5F6EAB7D60352086D92F85ACA8AD2DF33E4FC37893D8A90A44";
        String input = "http://watch.yupptv.io/stalker_portal/c/\n"
                + "00:1A:79:38:42:00\n"
                + "00:1A:79:61:35:32\n"
                + "DEVICE ID 1/2 : " + hex + "\n";

        List<Account> savedAccounts = new ArrayList<>();
        StalkerPortalParser parser = new StalkerPortalParser(name -> null, savedAccounts::add);

        parser.parseAndSave(input, true, false);

        assertEquals(1, savedAccounts.size(), "Expected a single grouped account to be created");
        Account acct = savedAccounts.get(0);

        assertEquals("00:1A:79:38:42:00,00:1A:79:61:35:32", acct.getMacAddressList());
        assertEquals(hex, acct.getDeviceId1());
        assertEquals(hex, acct.getDeviceId2());
    }
}
