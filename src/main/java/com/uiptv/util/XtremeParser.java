package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static com.uiptv.util.UiptUtils.getUniqueNameFromUrl;
import static com.uiptv.util.UiptUtils.replaceAllNonPrintableChars;

/**
 * Handles parsing of Xtreme accounts.
 */
public class XtremeParser implements AccountParser {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    private static final Pattern LABELED_USER = Pattern.compile("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*(\\S+)");
    private static final Pattern LABELED_PASS = Pattern.compile("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*(\\S+)");

    @Override
    public List<Account> parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        List<String> lines = Arrays.asList(text.split("\\R"));
        List<String> currentBlock = new ArrayList<>();
        List<Account> createdAccounts = new ArrayList<>();

        for (String line : lines) {
            String trimmed = replaceAllNonPrintableChars(line).trim();
            if (trimmed.isEmpty()) {
                if (!currentBlock.isEmpty()) {
                    Account savedAccount = processBlock(currentBlock);
                    if (savedAccount != null) {
                        createdAccounts.add(savedAccount);
                    }
                    currentBlock.clear();
                }
                continue;
            }
            currentBlock.add(trimmed);
        }
        if (!currentBlock.isEmpty()) {
            Account savedAccount = processBlock(currentBlock);
            if (savedAccount != null) {
                createdAccounts.add(savedAccount);
            }
        }
        return createdAccounts;
    }

    private Account processBlock(List<String> block) {
        String joinedBlock = String.join(" ", block);
        String url = extractFirstMatch(URL_PATTERN, joinedBlock, 1);
        if (url == null) {
            return null;
        }
        Credentials credentials = extractCredentials(joinedBlock, url);
        if (!credentials.isComplete()) {
            return null;
        }
        return saveAccount(url, credentials);
    }

    private Credentials extractCredentials(String joinedBlock, String url) {
        String username = extractFirstMatch(LABELED_USER, joinedBlock, 2);
        String password = extractFirstMatch(LABELED_PASS, joinedBlock, 3);
        if (username != null && password != null) {
            return new Credentials(username, password);
        }
        List<String> unlabeled = extractUnlabeledTokens(joinedBlock, url, username, password);
        if (username == null && !unlabeled.isEmpty()) {
            username = unlabeled.remove(0);
        }
        if (password == null && !unlabeled.isEmpty()) {
            password = unlabeled.remove(0);
        }
        return new Credentials(username, password);
    }

    private String extractFirstMatch(Pattern pattern, String text, int group) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(group) : null;
    }

    private List<String> extractUnlabeledTokens(String joinedBlock, String url, String username, String password) {
        String remaining = joinedBlock.replace(url, "");
        remaining = stripKnownCredential(remaining, username, "(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*");
        remaining = stripKnownCredential(remaining, password, "(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*");
        String[] tokens = remaining.trim().split("\\s+");
        return Arrays.stream(tokens)
                .filter(s -> !s.isEmpty() && s.length() > 1)
                .collect(Collectors.toList());
    }

    private String stripKnownCredential(String remaining, String value, String prefixPattern) {
        if (value == null) {
            return remaining;
        }
        return remaining.replaceAll(prefixPattern + Pattern.quote(value), "");
    }

    private Account saveAccount(String url, Credentials credentials) {
        String name = getUniqueNameFromUrl(url);
        Account account = new Account(name, credentials.username, credentials.password, url, null, null, null, null, null, null,
                AccountType.XTREME_API, null, url, false);
        AccountService.getInstance().save(account);
        return account;
    }

    private record Credentials(String username, String password) {
        private boolean isComplete() {
            return username != null && password != null;
        }
    }
}
