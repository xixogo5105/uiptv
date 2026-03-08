package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class StalkerPortalParserTest {

    @Test
    void testParseAndSave() throws IOException {
        Path path = Paths.get("src/test/resources/stalker_codes.txt");
        String text = new String(Files.readAllBytes(path));

        List<Account> savedAccounts = new ArrayList<>();
        StalkerPortalParser parser = new StalkerPortalParser(
                name -> null, // Mock: always return null (no existing account)
                savedAccounts::add // Mock: add to list instead of saving to DB
        );

        parser.parseAndSave(text, false, false);

        // Assertions
        assertNotNull(savedAccounts);

        Path expectedPath = Paths.get("src/test/resources/stalker_expected.txt");
        List<String> expectedLines = Files.readAllLines(expectedPath);

        List<String[]> expected = new ArrayList<>();
        for (String line : expectedLines) {
            if (line.trim().isEmpty()) continue;
            String[] parts = line.split(",", -1); // -1 to keep empty trailing strings if any, though we expect "null" string
            // Convert "null" string to actual null and trim
            for (int i = 0; i < parts.length; i++) {
                parts[i] = parts[i].trim();
                if ("null".equals(parts[i])) {
                    parts[i] = null;
                }
            }
            expected.add(parts);
        }

        assertEquals(expected.size(), savedAccounts.size());

        // Build a lookup map by MAC (normalized to upper case) to avoid relying on insertion order.
        Map<String, Account> byMac = savedAccounts.stream()
                .collect(Collectors.toMap(a -> a.getMacAddress() == null ? "" : a.getMacAddress().toUpperCase(), a -> a, (a, b) -> a));

        for (int i = 0; i < expected.size(); i++) {
            String[] row = expected.get(i);
            String expectedUrl = row[0];
            String expectedMac = row[1];
            String expectedSerial = row[2];
            String expectedDev1 = row[3];
            String expectedDev2 = row[4];
            String expectedSig = row[5];

            String lookupKey = expectedMac == null ? "" : expectedMac.toUpperCase();
            Account actual = byMac.get(lookupKey);
            assertNotNull(actual, "No account found for MAC " + expectedMac + " (expected index " + i + ")");

            assertEquals(expectedUrl, actual.getUrl(), "URL mismatch for MAC " + expectedMac);
            assertEquals(expectedMac, actual.getMacAddress(), "MAC mismatch for MAC " + expectedMac);

            if (expectedSerial == null) {
                assertNull(actual.getSerialNumber(), "Serial expected null for MAC " + expectedMac);
            } else {
                assertEquals(expectedSerial, actual.getSerialNumber(), "Serial mismatch for MAC " + expectedMac);
            }

            if (expectedDev1 == null) {
                assertNull(actual.getDeviceId1(), "Device ID 1 expected null for MAC " + expectedMac);
            } else {
                assertEquals(expectedDev1, actual.getDeviceId1(), "Device ID 1 mismatch for MAC " + expectedMac);
            }

            if (expectedDev2 == null) {
                assertNull(actual.getDeviceId2(), "Device ID 2 expected null for MAC " + expectedMac);
            } else {
                assertEquals(expectedDev2, actual.getDeviceId2(), "Device ID 2 mismatch for MAC " + expectedMac);
            }

            if (expectedSig == null) {
                assertNull(actual.getSignature(), "Signature expected null for MAC " + expectedMac);
            } else {
                assertEquals(expectedSig, actual.getSignature(), "Signature mismatch for MAC " + expectedMac);
            }
        }
    }

    @Test
    void helperMethods_groupMacs_andDetectExtraParams() throws Exception {
        Account existing = new Account();
        existing.setAccountName("portal.example");
        existing.setUrl("http://portal.example/c");
        existing.setMacAddress("00:11:22:33:44:55");
        existing.setMacAddressList("00:11:22:33:44:55");

        StalkerPortalParser parser = new StalkerPortalParser(
                name -> "portal.example".equals(name) ? existing : null,
                account -> {}
        );

        Method hasExtraParams = StalkerPortalParser.class.getDeclaredMethod("hasExtraParams", Account.class);
        hasExtraParams.setAccessible(true);
        Method appendMacAddress = StalkerPortalParser.class.getDeclaredMethod("appendMacAddress", Account.class, String.class);
        appendMacAddress.setAccessible(true);
        Method saveGroupedAccount = StalkerPortalParser.class.getDeclaredMethod(
                "saveGroupedAccount", Account.class, Map.class, List.class, java.util.Set.class);
        saveGroupedAccount.setAccessible(true);
        Method detectTimezone = StalkerPortalParser.class.getDeclaredMethod("detectTimezone", String.class);
        detectTimezone.setAccessible(true);
        Method applyLineMetadata = StalkerPortalParser.class.getDeclaredMethod("applyLineMetadata", Account.class, String.class);
        applyLineMetadata.setAccessible(true);

        Account simple = new Account();
        simple.setUrl("http://portal.example/c");
        simple.setMacAddress("00:11:22:33:44:66");
        assertFalse((Boolean) hasExtraParams.invoke(parser, simple));

        Account extra = new Account();
        extra.setUrl("http://portal.example/c");
        extra.setMacAddress("00:11:22:33:44:77");
        extra.setSerialNumber("SERIAL77");
        assertTrue((Boolean) hasExtraParams.invoke(parser, extra));

        appendMacAddress.invoke(parser, existing, "00:11:22:33:44:66");
        assertTrue(existing.getMacAddressList().contains("00:11:22:33:44:55"));
        assertTrue(existing.getMacAddressList().contains("00:11:22:33:44:66"));

        Map<String, Account> groupedAccounts = new LinkedHashMap<>();
        List<Account> createdAccounts = new ArrayList<>();
        HashSet<String> processedNames = new HashSet<>();
        saveGroupedAccount.invoke(parser, simple, groupedAccounts, createdAccounts, processedNames);
        assertEquals(1, groupedAccounts.size());
        assertEquals(0, createdAccounts.size());
        assertEquals("00:11:22:33:44:55,00:11:22:33:44:66", groupedAccounts.get("portal.example").getMacAddressList());

        Account metadata = new Account();
        applyLineMetadata.invoke(parser, metadata, "POST");
        applyLineMetadata.invoke(parser, metadata, "Europe/Paris");
        assertEquals("POST", metadata.getHttpMethod());
        assertEquals("Europe/Paris", metadata.getTimezone());
        assertEquals("Europe/Paris", detectTimezone.invoke(parser, "timezone Europe/Paris"));
    }
}
