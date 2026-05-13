package com.uiptv.service;

import com.uiptv.model.Account;
import com.uiptv.util.TextParserService;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

class TextParserServiceIntegrationTest extends DbBackedTest {

    @Test
    void saveBulkAccounts_stalkerGroupingSeparatesMacOnlyAndExtraParamAccounts() {
        String url = "http://portal.example/c";
        String serialA = "AABBCCDDEE";
        String device1A = "AABBCCDDEEFF001122334455667788AA";
        String device2A = "AABBCCDDEEFF001122334455667788BB";
        String signatureA = "AABBCCDDEEFF00112233445566778899AABBCCDDEEFF00112233445566778899";
        String serialB = "1122334455";
        String device1B = "11223344556677889900AABBCCDDEEFF";
        String device2B = "FFEEDDCCBBAA00998877665544332211";
        String signatureB = "99887766554433221100FFEEDDCCBBAA99887766554433221100FFEEDDCCBBAA";

        String initialImport = """
                %s
                00:11:22:33:44:10
                00:11:22:33:44:11

                %s
                00:11:22:33:44:20
                serial: %s
                device id 1: %s
                device id 2: %s
                signature: %s

                %s
                00:11:22:33:44:21
                serial: %s
                device id 1: %s
                device id 2: %s
                signature: %s

                %s
                00:11:22:33:44:30
                serial: %s
                device id 1: %s
                device id 2: %s
                signature: %s
                """.formatted(url, url, serialA, device1A, device2A, signatureA,
                url, serialA, device1A, device2A, signatureA,
                url, serialB, device1B, device2B, signatureB);

        List<Account> created = TextParserService.saveBulkAccounts(initialImport, TextParserService.MODE_STALKER, true, false);
        assertEquals(3, created.size());

        String followUpImport = """
                %s
                00:11:22:33:44:12
                00:11:22:33:44:13

                %s
                00:11:22:33:44:22
                serial: %s
                device id 1: %s
                device id 2: %s
                signature: %s

                %s
                00:11:22:33:44:31
                serial: %s
                device id 1: %s
                device id 2: %s
                signature: %s
                """.formatted(url, url, serialA, device1A, device2A, signatureA,
                url, serialB, device1B, device2B, signatureB);

        List<Account> updated = TextParserService.saveBulkAccounts(followUpImport, TextParserService.MODE_STALKER, true, false);
        assertEquals(0, updated.size());

        List<Account> stalkerAccounts = new ArrayList<>(AccountService.getInstance().getAll().values()).stream()
                .filter(account -> account.getUrl() != null && account.getUrl().startsWith(url))
                .sorted(Comparator.comparing(Account::getAccountName))
                .toList();

        assertEquals(3, stalkerAccounts.size());

        Account macOnly = stalkerAccounts.stream()
                .filter(account -> account.getSerialNumber() == null)
                .findFirst()
                .orElseThrow();
        assertMacListEquals(macOnly.getMacAddressList(), "00:11:22:33:44:10", "00:11:22:33:44:11", "00:11:22:33:44:12", "00:11:22:33:44:13");
        assertNull(macOnly.getDeviceId1());
        assertNull(macOnly.getDeviceId2());
        assertNull(macOnly.getSignature());

        Account extraA = stalkerAccounts.stream()
                .filter(account -> serialA.equals(account.getSerialNumber()))
                .findFirst()
                .orElseThrow();
        assertMacListEquals(extraA.getMacAddressList(), "00:11:22:33:44:20", "00:11:22:33:44:21", "00:11:22:33:44:22");
        assertEquals(device1A, extraA.getDeviceId1());
        assertEquals(device2A, extraA.getDeviceId2());
        assertEquals(signatureA, extraA.getSignature());

        Account extraB = stalkerAccounts.stream()
                .filter(account -> serialB.equals(account.getSerialNumber()))
                .findFirst()
                .orElseThrow();
        assertMacListEquals(extraB.getMacAddressList(), "00:11:22:33:44:30", "00:11:22:33:44:31");
        assertEquals(device1B, extraB.getDeviceId1());
        assertEquals(device2B, extraB.getDeviceId2());
        assertEquals(signatureB, extraB.getSignature());

        assertNotNull(macOnly.getAccountName());
        assertNotNull(extraA.getAccountName());
        assertNotNull(extraB.getAccountName());
    }

    private void assertMacListEquals(String actual, String... expected) {
        List<String> actualMacs = Stream.of(actual.split(","))
                .sorted()
                .toList();
        List<String> expectedMacs = Stream.of(expected)
                .sorted()
                .toList();
        assertEquals(expectedMacs, actualMacs);
    }
}
