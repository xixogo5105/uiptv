package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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
}