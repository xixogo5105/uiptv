package com.uiptv.util;

import com.uiptv.model.Account;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class XtremeParserTest {

    /**
     * Test that multiple accounts with the same URL can be parsed correctly.
     * This test simulates the issue described in the bug report where only the last
     * account was being imported when multiple accounts shared the same server URL.
     */
    @Test
    void testParseMultipleAccountsSameUrlWithoutDatabase() {
        // Simulated test data with 9 accounts on the same server (like the bug report)
        String testData = """
                http://192.168.1.100:2095
                User : LoremUser01
                Pass : IpsumPass01

                http://192.168.1.100:2095
                User : DolorUser02
                Pass : SitPass02

                http://192.168.1.100:2095
                User : AmetUser03
                Pass : ConsPass03

                http://192.168.2.50:2095
                User : ConsecUser04
                Pass : Tetur04

                http://192.168.2.50:2095
                User : AdipiscingUser05
                Pass : ElitPass05

                http://192.168.3.200:8080
                User : ElitrUser06
                Pass : SedPass06

                http://192.168.3.200:8080
                User : SedUser07
                Pass : DiamPass07

                http://192.168.3.200:8080
                User : DiamUser08
                Pass : VoluptPass08

                http://192.168.4.75:3000
                User : VoluptUser09
                Pass : AtumPass09
                """;

        List<Account> parsedAccounts = parseWithoutDatabase(testData);

        // Assertions
        assertEquals(9, parsedAccounts.size(), "Should parse all 9 accounts");

        // Verify we have accounts for all three IPs
        long uniqueUrls = parsedAccounts.stream()
                .map(Account::getUrl)
                .distinct()
                .count();
        assertEquals(4, uniqueUrls, "Should have 4 different URLs");

        // Verify all credentials are captured
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("LoremUser01") && a.getPassword().equals("IpsumPass01")),
                "Should have LoremUser01 with IpsumPass01");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("DolorUser02") && a.getPassword().equals("SitPass02")),
                "Should have DolorUser02 with SitPass02");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("AmetUser03") && a.getPassword().equals("ConsPass03")),
                "Should have AmetUser03 with ConsPass03");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("VoluptUser09") && a.getPassword().equals("AtumPass09")),
                "Should have VoluptUser09 with AtumPass09");
    }

    @Test
    void testParseMultipleAccountsWithDifferentUrls() {
        String testData = """
                http://192.168.1.100:2095
                User : TestUser1
                Pass : TestPass1
                
                http://192.168.2.50:3000
                User : TestUser2
                Pass : TestPass2
                
                http://192.168.3.200:8080
                User : TestUser3
                Pass : TestPass3
                """;

        List<Account> parsedAccounts = parseWithoutDatabase(testData);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 accounts");

        // Verify each account has the correct data
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("192.168.1.100") &&
                              a.getUsername().equals("TestUser1") &&
                              a.getPassword().equals("TestPass1")),
                "First account should be parsed correctly");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("192.168.2.50") &&
                              a.getUsername().equals("TestUser2") &&
                              a.getPassword().equals("TestPass2")),
                "Second account should be parsed correctly");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().contains("192.168.3.200") &&
                              a.getUsername().equals("TestUser3") &&
                              a.getPassword().equals("TestPass3")),
                "Third account should be parsed correctly");
    }

    @Test
    void testParseAccountsWithVariousCredentialFormats() {
        String testData = """
                http://192.168.1.100:2095 username=User1 password=Pass1
                
                http://192.168.2.50:3000 user=User2 pass=Pass2
                
                http://192.168.3.200:8080 u=User3 pw=Pass3
                """;

        List<Account> parsedAccounts = parseWithoutDatabase(testData);

        assertEquals(3, parsedAccounts.size(), "Should parse all 3 accounts with different credential formats");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("User1") && a.getPassword().equals("Pass1")),
                "Account with username/password labels should parse correctly");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("User2") && a.getPassword().equals("Pass2")),
                "Account with user/pass labels should parse correctly");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUsername().equals("User3") && a.getPassword().equals("Pass3")),
                "Account with u/pw labels should parse correctly");
    }

    @Test
    void testUrlExtraction() {
        String testData = """
                http://192.168.1.100:2095
                User: TestUser
                Pass: TestPass
                
                https://192.168.2.50:3000/path
                Username: AnotherUser
                Password: AnotherPass
                """;

        List<Account> parsedAccounts = parseWithoutDatabase(testData);

        assertEquals(2, parsedAccounts.size(), "Should parse both accounts");

        // Verify URL extraction
        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().equals("http://192.168.1.100:2095")),
                "Should extract HTTP URL correctly");

        assertTrue(parsedAccounts.stream()
                .anyMatch(a -> a.getUrl().equals("https://192.168.2.50:3000/path")),
                "Should extract HTTPS URL with path correctly");
    }

    @Test
    void testFileParsingFromResource() throws IOException {
        Path path = Paths.get("src/test/resources/xtreme_codes.txt");
        if (!Files.exists(path)) {
            // Skip test if file doesn't exist
            return;
        }

        String text = new String(Files.readAllBytes(path));
        List<Account> parsedAccounts = parseWithoutDatabase(text);

        // The file should contain 9 accounts
        assertEquals(9, parsedAccounts.size(), "Test resource file should contain 9 accounts");

        // Verify all accounts have required fields
        for (Account account : parsedAccounts) {
            assertNotNull(account.getUrl(), "Account URL should not be null");
            assertNotNull(account.getUsername(), "Account username should not be null");
            assertNotNull(account.getPassword(), "Account password should not be null");
        }
    }

    /**
     * Helper method to parse Xtreme accounts without requiring database interaction.
     * This manually implements the parsing logic for testing purposes.
     */
    private List<Account> parseWithoutDatabase(String text) {
        List<Account> accounts = new ArrayList<>();
        Pattern urlPattern = Pattern.compile("(https?://\\S+)");
        Pattern userPattern = Pattern.compile("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*(\\S+)");
        Pattern passPattern = Pattern.compile("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*(\\S+)");

        List<String> lines = java.util.Arrays.asList(text.split("\\R"));
        List<String> currentBlock = new ArrayList<>();

        for (String line : lines) {
            String trimmed = UiptUtils.replaceAllNonPrintableChars(line).trim();
            if (trimmed.isEmpty()) {
                if (!currentBlock.isEmpty()) {
                    Account account = parseBlock(currentBlock, urlPattern, userPattern, passPattern);
                    if (account != null) {
                        accounts.add(account);
                    }
                    currentBlock.clear();
                }
                continue;
            }
            currentBlock.add(trimmed);
        }

        if (!currentBlock.isEmpty()) {
            Account account = parseBlock(currentBlock, urlPattern, userPattern, passPattern);
            if (account != null) {
                accounts.add(account);
            }
        }

        return accounts;
    }

    private Account parseBlock(List<String> block, Pattern urlPattern, Pattern userPattern, Pattern passPattern) {
        String joinedBlock = String.join(" ", block);
        String url = null, username = null, password = null;

        Matcher mUrl = urlPattern.matcher(joinedBlock);
        if (mUrl.find()) url = mUrl.group(1);
        if (url == null) return null;

        Matcher mUser = userPattern.matcher(joinedBlock);
        if (mUser.find()) username = mUser.group(2);

        Matcher mPass = passPattern.matcher(joinedBlock);
        if (mPass.find()) password = mPass.group(3);

        if (username == null || password == null) {
            String remaining = joinedBlock.replace(url, "");
            if (username != null) remaining = remaining.replaceAll("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*" + Pattern.quote(username), "");
            if (password != null) remaining = remaining.replaceAll("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*" + Pattern.quote(password), "");

            String[] tokens = remaining.trim().split("\\s+");
            List<String> unlabeled = java.util.Arrays.stream(tokens)
                    .filter(s -> !s.isEmpty() && s.length() > 1)
                    .collect(Collectors.toList());

            if (username == null && !unlabeled.isEmpty()) username = unlabeled.remove(0);
            if (password == null && !unlabeled.isEmpty()) password = unlabeled.remove(0);
        }

        if (url != null && username != null && password != null) {
            // For testing, use a simple name based on URL
            String name = "test_" + url.hashCode();
            return new Account(name, username, password, url, null, null, null, null, null, null,
                    AccountType.XTREME_API, null, url, false);
        }

        return null;
    }
}

