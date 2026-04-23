package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.uiptv.util.StringUtils.isBlank;
import static com.uiptv.util.StringUtils.isNotBlank;
import static com.uiptv.util.UiptUtils.replaceAllNonPrintableChars;

/**
 * Handles parsing of Xtreme accounts.
 */
public class XtremeParser implements AccountParser {
    private static final Pattern URL_PATTERN = Pattern.compile("(https?://\\S+)");
    private static final Pattern LABELED_USER = Pattern.compile("(?i)\\b(user|username|u|name|id)\\b\\s*[:=]?\\s*(\\S+)");
    private static final Pattern LABELED_PASS = Pattern.compile("(?i)\\b(pass(word)?|p|pw)\\b\\s*[:=]?\\s*(\\S+)");

    private final Function<String, Account> accountProvider;
    private final Consumer<Account> accountSaver;

    public XtremeParser() {
        this(AccountService.getInstance()::getByName, AccountService.getInstance()::save);
    }

    public XtremeParser(Function<String, Account> accountProvider, Consumer<Account> accountSaver) {
        this.accountProvider = accountProvider;
        this.accountSaver = accountSaver;
    }

    @Override
    public List<Account> parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        List<String> lines = Arrays.asList(text.split("\\R"));
        List<String> currentBlock = new ArrayList<>();
        List<ParsedAccount> parsedAccounts = new ArrayList<>();

        for (String line : lines) {
            String trimmed = replaceAllNonPrintableChars(line).trim();
            if (trimmed.isEmpty()) {
                if (!currentBlock.isEmpty()) {
                    ParsedAccount parsed = processBlock(currentBlock);
                    if (parsed != null) {
                        parsedAccounts.add(parsed);
                    }
                    currentBlock.clear();
                }
                continue;
            }
            currentBlock.add(trimmed);
        }
        if (!currentBlock.isEmpty()) {
            ParsedAccount parsed = processBlock(currentBlock);
            if (parsed != null) {
                parsedAccounts.add(parsed);
            }
        }
        return saveAccounts(parsedAccounts, groupAccountsByMac);
    }

    private ParsedAccount processBlock(List<String> block) {
        String joinedBlock = String.join(" ", block);
        String url = extractFirstMatch(URL_PATTERN, joinedBlock, 1);
        if (url == null) {
            return null;
        }
        Credentials credentials = extractCredentials(joinedBlock, url);
        if (!credentials.isComplete()) {
            return null;
        }
        return new ParsedAccount(url, credentials);
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
                .collect(java.util.stream.Collectors.toCollection(ArrayList::new));
    }

    private String stripKnownCredential(String remaining, String value, String prefixPattern) {
        if (value == null) {
            return remaining;
        }
        return remaining.replaceAll(prefixPattern + Pattern.quote(value), "");
    }

    private List<Account> saveAccounts(List<ParsedAccount> parsedAccounts, boolean groupAccounts) {
        if (parsedAccounts.isEmpty()) {
            return List.of();
        }
        if (!groupAccounts) {
            return saveIndividualAccounts(parsedAccounts);
        }
        return saveGroupedAccounts(parsedAccounts);
    }

    private List<Account> saveIndividualAccounts(List<ParsedAccount> parsedAccounts) {
        List<Account> createdAccounts = new ArrayList<>();
        Set<String> processedNames = new java.util.HashSet<>();
        for (ParsedAccount parsed : parsedAccounts) {
            String name = nextUniqueAccountName(parsed.url(), processedNames);
            Account account = buildAccount(name, parsed.url(), parsed.credentials());
            accountSaver.accept(account);
            createdAccounts.add(account);
            processedNames.add(name);
        }
        return createdAccounts;
    }

    private List<Account> saveGroupedAccounts(List<ParsedAccount> parsedAccounts) {
        Map<String, Account> groupedAccounts = new LinkedHashMap<>();
        List<Account> createdAccounts = new ArrayList<>();
        for (ParsedAccount parsed : parsedAccounts) {
            String name = accountNameFromUrl(parsed.url());
            Account existing = groupedAccounts.get(name);
            if (existing != null) {
                mergeCredentials(existing, parsed.credentials());
            } else {
                Account existingInDb = accountProvider.apply(name);
                if (existingInDb != null) {
                    mergeCredentials(existingInDb, parsed.credentials());
                    groupedAccounts.put(name, existingInDb);
                } else {
                    Account account = buildAccount(name, parsed.url(), parsed.credentials());
                    groupedAccounts.put(name, account);
                    createdAccounts.add(account);
                }
            }
        }
        groupedAccounts.values().forEach(accountSaver);
        return createdAccounts;
    }

    private Account buildAccount(String name, String url, Credentials credentials) {
        Account account = new Account(name, credentials.username, credentials.password, url, null, null, null, null, null, null,
                AccountType.XTREME_API, null, url, false);
        mergeCredentials(account, credentials);
        return account;
    }

    private void mergeCredentials(Account account, Credentials credentials) {
        List<XtremeCredentialsJson.Entry> entries = XtremeCredentialsJson.parse(account.getXtremeCredentialsJson());
        if (entries.isEmpty() && isNotBlank(account.getUsername()) && isNotBlank(account.getPassword())) {
            entries.add(new XtremeCredentialsJson.Entry(account.getUsername(), account.getPassword(), true));
        }
        boolean exists = entries.stream().anyMatch(entry ->
                entry.username().equals(credentials.username) && entry.password().equals(credentials.password));
        if (!exists) {
            entries.add(new XtremeCredentialsJson.Entry(credentials.username, credentials.password, entries.isEmpty()));
        }
        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(entries, account.getUsername());
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(normalized);
        if (defaultEntry != null) {
            account.setUsername(defaultEntry.username());
            account.setPassword(defaultEntry.password());
        }
        account.setXtremeCredentialsJson(XtremeCredentialsJson.toJson(normalized));
    }

    private String nextUniqueAccountName(String url, Set<String> processedNames) {
        String baseName = accountNameFromUrl(url);
        if (accountProvider.apply(baseName) == null && !processedNames.contains(baseName)) {
            return baseName;
        }
        int counter = 1;
        String candidate = baseName + " (" + counter + ")";
        while (accountProvider.apply(candidate) != null || processedNames.contains(candidate)) {
            counter++;
            candidate = baseName + " (" + counter + ")";
        }
        return candidate;
    }

    private String accountNameFromUrl(String urlString) {
        if (isBlank(urlString)) {
            return urlString;
        }
        try {
            return URI.create(urlString).getHost();
        } catch (Exception _) {
            return urlString;
        }
    }

    private record Credentials(String username, String password) {
        private boolean isComplete() {
            return username != null && password != null;
        }
    }

    private record ParsedAccount(String url, Credentials credentials) {
    }
}
