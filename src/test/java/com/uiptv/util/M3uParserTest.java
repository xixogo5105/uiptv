package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive test suite for M3U Playlist parsing and M3U to Xtreme conversion.
 * Tests various M3U URL formats with and without credentials.
 */
class M3uParserTest {

    /**
     * Test parsing simple M3U playlist URLs (without credentials).
     */
    @Test
    void testParseSimpleM3uUrls() {
        String testData = """
                http://lorem.example.com:8080/playlist.m3u8
                http://ipsum.example.com:8080/playlist.m3u8
                http://dolor.example.com:8080/playlist.m3u8
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, false);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 M3U URLs");

        // Verify account types are M3U8_URL (not Xtreme)
        parsedAccounts.forEach(account ->
                assertEquals(AccountType.M3U8_URL, account.getType(),
                        "Account type should be M3U8_URL when conversion is disabled")
        );

        // Verify URLs are extracted correctly
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("lorem.example.com")),
                "Should have lorem.example.com URL");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("ipsum.example.com")),
                "Should have ipsum.example.com URL");
    }

    /**
     * Test parsing M3U URLs with embedded Xtreme credentials (query params).
     * These can optionally be converted to Xtreme accounts.
     */
    @Test
    void testParseM3uUrlsWithCredentials() {
        String testData = """
                http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&type=m3u_plus&output=ts
                http://cdn.ipsum.com:8080/get.php?username=ipsumuser&password=ipsumpass&type=m3u_plus&output=ts
                http://cdn.dolor.com:8080/get.php?username=doloruser&password=dolorpass&type=m3u_plus&output=ts
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, false);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 M3U URLs with credentials");

        // When conversion is OFF, all should be M3U8_URL type
        parsedAccounts.forEach(account ->
                assertEquals(AccountType.M3U8_URL, account.getType(),
                        "Account type should be M3U8_URL when conversion is disabled")
        );

        // Verify full URLs are preserved
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("username=loremuser") &&
                              a.getUrl().contains("password=lorempass")),
                "Full URL with credentials should be preserved");
    }

    /**
     * Test conversion of M3U URLs to Xtreme accounts.
     * When convertM3uToXtreme is enabled, URLs with embedded username/password
     * should be converted to XTREME_API type accounts.
     */
    @Test
    void testConvertM3uToXtremeAccounts() {
        String testData = """
                http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&type=m3u_plus&output=ts
                http://cdn.ipsum.com:8080/get.php?username=ipsumuser&password=ipsumpass&type=m3u_plus&output=ts
                http://cdn.dolor.com:8080/get.php?username=doloruser&password=dolorpass&type=m3u_plus&output=ts
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 URLs");

        // When conversion is ON, all should be XTREME_API type
        parsedAccounts.forEach(account -> {
            assertEquals(AccountType.XTREME_API, account.getType(),
                    "Account type should be XTREME_API when conversion is enabled");
            assertNotNull(account.getUsername(), "Username should be extracted");
            assertNotNull(account.getPassword(), "Password should be extracted");
        });

        // Verify credentials are correctly extracted
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("loremuser") &&
                              a.getPassword().equals("lorempass")),
                "Should extract lorem credentials");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("ipsumuser") &&
                              a.getPassword().equals("ipsumpass")),
                "Should extract ipsum credentials");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("doloruser") &&
                              a.getPassword().equals("dolorpass")),
                "Should extract dolor credentials");

        // Verify URL is base URL (without query params)
        parsedAccounts.forEach(account -> {
            assertTrue(account.getUrl().endsWith("/get.php") || !account.getUrl().contains("?"),
                    "URL should be base URL without query parameters");
        });
    }

    /**
     * Test parsing mixed M3U URLs (some with credentials, some without).
     */
    @Test
    void testParseMixedM3uUrls() {
        String testData = """
                http://simple.example.com:8080/playlist.m3u8
                http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&type=m3u_plus&output=ts
                http://another.example.com:8080/playlist.m3u8
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 URLs");

        // Count by type
        long m3u8Count = parsedAccounts.stream()
                .filter(a -> a.getType() == AccountType.M3U8_URL)
                .count();
        long xtremeCount = parsedAccounts.stream()
                .filter(a -> a.getType() == AccountType.XTREME_API)
                .count();

        assertEquals(2, m3u8Count, "Should have 2 M3U8_URL accounts (simple URLs without credentials)");
        assertEquals(1, xtremeCount, "Should have 1 XTREME_API account (URL with credentials)");

        // Verify the converted one has credentials
        Account xtremeAccount = parsedAccounts.stream()
                .filter(a -> a.getType() == AccountType.XTREME_API)
                .findFirst()
                .orElse(null);

        assertNotNull(xtremeAccount, "Should have at least one Xtreme account");
        assertEquals("loremuser", xtremeAccount.getUsername());
        assertEquals("lorempass", xtremeAccount.getPassword());
    }

    /**
     * Test parsing multiple M3U URLs on same line (space-separated).
     */
    @Test
    void testParseMultipleM3uUrlsOnSameLine() {
        String testData = "http://lorem.example.com:8080/playlist.m3u8 http://ipsum.example.com:8080/playlist.m3u8 http://dolor.example.com:8080/playlist.m3u8";

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, false);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 space-separated URLs");

        // Verify all URLs extracted
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("lorem.example.com")));
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("ipsum.example.com")));
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("dolor.example.com")));
    }

    /**
     * Test parsing M3U URLs with various query parameters (not just username/password).
     */
    @Test
    void testParseM3uUrlsWithVariousParameters() {
        String testData = """
                http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass&output=ts&type=m3u_plus
                http://cdn.ipsum.com:8080/get.php?username=ipsumuser&password=ipsumpass&output=m3u&type=m3u_plus&format=json
                http://cdn.dolor.com:8080/get.php?type=m3u_plus&username=doloruser&password=dolorpass
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 URLs with various parameters");

        // All should be converted to Xtreme (they have username and password)
        parsedAccounts.forEach(account ->
                assertEquals(AccountType.XTREME_API, account.getType(),
                        "All should be XTREME_API when they have username and password")
        );

        // Verify credentials extracted despite different parameter order
        parsedAccounts.forEach(account -> {
            assertNotNull(account.getUsername(), "Username should be extracted");
            assertNotNull(account.getPassword(), "Password should be extracted");
            assertFalse(account.getUsername().isEmpty(), "Username should not be empty");
            assertFalse(account.getPassword().isEmpty(), "Password should not be empty");
        });
    }

    /**
     * Test parsing M3U URLs with special characters in credentials.
     */
    @Test
    void testParseM3uUrlsWithSpecialCharactersInCredentials() {
        String testData = """
                http://cdn.lorem.com:8080/get.php?username=lorem.user.123&password=pass%40word123
                http://cdn.ipsum.com:8080/get.php?username=ipsum_user_456&password=pass%23word456
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(2, parsedAccounts.size(), "Should parse both URLs");

        // Verify URLs with special characters are handled
        parsedAccounts.forEach(account -> {
            assertNotNull(account.getUsername(), "Username should be extracted even with special chars");
            assertNotNull(account.getPassword(), "Password should be extracted even with special chars");
        });
    }

    /**
     * Test that M3U accounts without credentials cannot be converted to Xtreme.
     */
    @Test
    void testM3uUrlsWithoutCredentialsNotConverted() {
        String testData = """
                http://simple.example.com:8080/playlist.m3u8
                http://another.example.com:8080/get.php?type=m3u_plus&output=ts
                http://third.example.com:8080/playlist.m3u8
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 URLs");

        // All should remain as M3U8_URL (none have username/password)
        parsedAccounts.forEach(account ->
                assertEquals(AccountType.M3U8_URL, account.getType(),
                        "Should remain M3U8_URL when no username/password present")
        );
    }

    /**
     * Test parsing M3U URLs with HTTPS protocol.
     */
    @Test
    void testParseM3uUrlsWithHttps() {
        String testData = """
                https://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass
                https://cdn.ipsum.com:8080/playlist.m3u8
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(2, parsedAccounts.size(), "Should parse both HTTPS URLs");

        // Verify HTTPS URLs are preserved
        parsedAccounts.forEach(account ->
                assertTrue(account.getUrl().startsWith("https://"),
                        "HTTPS protocol should be preserved")
        );
    }

    /**
     * Test parsing M3U URLs with custom ports.
     */
    @Test
    void testParseM3uUrlsWithCustomPorts() {
        String testData = """
                http://cdn.lorem.com:8080/get.php?username=loremuser&password=lorempass
                http://cdn.ipsum.com:9090/get.php?username=ipsumuser&password=ipsumpass
                http://cdn.dolor.com:3000/get.php?username=doloruser&password=dolorpass
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 URLs with custom ports");

        // Verify port numbers are preserved in URLs
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains(":8080")));
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains(":9090")));
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains(":3000")));
    }

    /**
     * Test M3U to Xtreme conversion generates correct account names.
     */
    @Test
    void testConvertedXtremeAccountsHaveCorrectNames() {
        String testData = """
                http://cdn.lorem.com:8080/get.php?username=user1&password=pass1
                http://cdn.ipsum.com:8080/get.php?username=user2&password=pass2
                http://cdn.dolor.com:8080/get.php?username=user3&password=pass3
                """;

        List<Account> parsedAccounts = parseM3uWithoutDatabase(testData, true);

        assertEquals(3, parsedAccounts.size());

        // Verify account names are generated from hostnames
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getAccountName().contains("lorem")));
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getAccountName().contains("ipsum")));
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getAccountName().contains("dolor")));
    }

    /**
     * Helper method to parse M3U URLs without database interaction.
     * Simulates the parseAndSave behavior for testing.
     */
    private List<Account> parseM3uWithoutDatabase(String text, boolean convertM3uToXtreme) {
        List<Account> accounts = new ArrayList<>();

        for (String line : text.split("\\R")) {
            for (final String potentialUrl : UiptUtils.replaceAllNonPrintableChars(line).split(UiptUtils.SPACER)) {
                if (!UiptUtils.isValidURL(potentialUrl)) continue;

                String m3uPlaylistUrl = potentialUrl;
                String username = null;
                String password = null;
                AccountType accountType = AccountType.M3U8_URL;

                // Check if URL is a valid Xtreme link and conversion is enabled
                if (convertM3uToXtreme && UiptUtils.isUrlValidXtremeLink(potentialUrl)) {
                    accountType = AccountType.XTREME_API;
                    username = UiptUtils.getUserNameFromUrl(potentialUrl);
                    password = UiptUtils.getPasswordNameFromUrl(potentialUrl);
                    m3uPlaylistUrl = potentialUrl.split("get.php?")[0];
                }

                String uniqueName = UiptUtils.getUniqueNameFromUrl(m3uPlaylistUrl);
                accounts.add(new Account(uniqueName, username, password, m3uPlaylistUrl, null, null, null,
                        null, null, null, accountType, null, m3uPlaylistUrl, false));
            }
        }

        return accounts;
    }
}

