package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;

import static com.uiptv.util.UiptUtils.*;
import static com.uiptv.util.StringUtils.isNotBlank;

/**
 * Handles parsing of M3U playlist links.
 */
public class M3uParser implements AccountParser {
    private final Function<String, Account> accountProvider;
    private final Supplier<Collection<Account>> allAccountsProvider;
    private final Consumer<Account> accountSaver;
    private final AccountNameAllocator accountNameAllocator;

    public M3uParser() {
        this(
                AccountService.getInstance()::getByName,
                () -> AccountService.getInstance().getAllById().values(),
                AccountService.getInstance()::save
        );
    }

    public M3uParser(Function<String, Account> accountProvider,
                     Supplier<Collection<Account>> allAccountsProvider,
                     Consumer<Account> accountSaver) {
        this.accountProvider = accountProvider;
        this.allAccountsProvider = allAccountsProvider;
        this.accountSaver = accountSaver;
        this.accountNameAllocator = new AccountNameAllocator(accountProvider);
    }

    @Override
    public List<Account> parseAndSave(String text, boolean groupConvertedXtremeAccounts, boolean convertM3uToXtreme) {
        List<ParsedUrl> parsedUrls = new ArrayList<>();
        for (String line : text.split("\\R")) {
            String cleanedLine = replaceAllNonPrintableChars(line).trim();
            ParsedUrl localPlaylist = parseLocalPlaylist(cleanedLine);
            if (localPlaylist != null) {
                parsedUrls.add(localPlaylist);
                continue;
            }
            for (final String potentialUrl : replaceAllNonPrintableChars(line).split(SPACER)) {
                if (potentialUrl.regionMatches(true, 0, "file:", 0, "file:".length())) continue;
                if (!isValidURL(potentialUrl)) continue;
                parsedUrls.add(parseUrl(potentialUrl, convertM3uToXtreme));
            }
        }
        return saveParsedUrls(parsedUrls, groupConvertedXtremeAccounts);
    }

    private List<Account> saveParsedUrls(List<ParsedUrl> parsedUrls, boolean groupConvertedXtremeAccounts) {
        Map<String, Account> savedAccounts = new LinkedHashMap<>();
        Map<String, Account> groupedXtremeAccounts = new LinkedHashMap<>();
        Set<String> reservedNames = new HashSet<>();

        for (ParsedUrl parsed : parsedUrls) {
            if (parsed.isConvertedXtreme() && groupConvertedXtremeAccounts) {
                Account groupedAccount = groupedXtremeAccounts.get(parsed.playlistUrl());
                if (groupedAccount == null) {
                    groupedAccount = findExistingXtremeAccount(parsed.playlistUrl());
                    if (groupedAccount == null) {
                        String name = accountNameAllocator.next(parsed.playlistUrl(), reservedNames);
                        groupedAccount = buildAccount(name, parsed);
                    } else {
                        reservedNames.add(groupedAccount.getAccountName());
                    }
                    groupedXtremeAccounts.put(parsed.playlistUrl(), groupedAccount);
                }
                mergeCredentials(groupedAccount, parsed.username(), parsed.password());
                savedAccounts.put(groupedAccount.getAccountName(), groupedAccount);
                reservedNames.add(groupedAccount.getAccountName());
                continue;
            }

            String name = accountNameAllocator.next(parsed.nameInput(), reservedNames);
            Account account = buildAccount(name, parsed);
            if (parsed.isConvertedXtreme()) {
                mergeCredentials(account, parsed.username(), parsed.password());
            }
            savedAccounts.put(account.getAccountName(), account);
            reservedNames.add(account.getAccountName());
        }

        savedAccounts.values().forEach(accountSaver);
        return new ArrayList<>(savedAccounts.values());
    }

    private Account buildAccount(String name, ParsedUrl parsed) {
        String accountUrl = parsed.accountType() == AccountType.M3U8_LOCAL ? "" : parsed.playlistUrl();
        return new Account(name, parsed.username(), parsed.password(), accountUrl,
                null, null, null, null, null, null, parsed.accountType(), null,
                parsed.playlistUrl(), false);
    }

    private ParsedUrl parseLocalPlaylist(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }
        try {
            Path path;
            if (input.regionMatches(true, 0, "file:", 0, "file:".length())) {
                path = Path.of(URI.create(input));
            } else {
                String lower = input.toLowerCase(java.util.Locale.ROOT);
                if (!lower.endsWith(".m3u") && !lower.endsWith(".m3u8")) {
                    return null;
                }
                path = Path.of(input);
            }
            String filename = path.getFileName() == null ? "" : path.getFileName().toString().toLowerCase(java.util.Locale.ROOT);
            if ((!filename.endsWith(".m3u") && !filename.endsWith(".m3u8")) || !Files.isRegularFile(path)) {
                return null;
            }
            Path absolutePath = path.toAbsolutePath().normalize();
            return new ParsedUrl(absolutePath.toUri().toString(), absolutePath.toString(), null, null, AccountType.M3U8_LOCAL);
        } catch (Exception _) {
            return null;
        }
    }

    private ParsedUrl parseUrl(String potentialUrl, boolean convertM3uToXtreme) {
        if (convertM3uToXtreme && isUrlValidXtremeLink(potentialUrl)) {
            String endpoint = potentialUrl.split("get.php?")[0];
            return new ParsedUrl(endpoint, endpoint, getUserNameFromUrl(potentialUrl),
                    getPasswordNameFromUrl(potentialUrl), AccountType.XTREME_API);
        }
        return new ParsedUrl(potentialUrl, potentialUrl, null, null, AccountType.M3U8_URL);
    }

    private record ParsedUrl(String nameInput, String playlistUrl, String username, String password, AccountType accountType) {
        private boolean isConvertedXtreme() {
            return accountType == AccountType.XTREME_API && isNotBlank(username) && isNotBlank(password);
        }
    }

    private Account findExistingXtremeAccount(String endpoint) {
        return allAccountsProvider.get().stream()
                .filter(account -> account.getType() == AccountType.XTREME_API)
                .filter(account -> endpoint.equals(account.getUrl()))
                .findFirst()
                .orElse(null);
    }

    private void mergeCredentials(Account account, String username, String password) {
        List<XtremeCredentialsJson.Entry> entries = new ArrayList<>(XtremeCredentialsJson.parse(account.getXtremeCredentialsJson()));
        if (entries.isEmpty() && isNotBlank(account.getUsername()) && isNotBlank(account.getPassword())) {
            entries.add(new XtremeCredentialsJson.Entry(account.getUsername(), account.getPassword(), true));
        }
        boolean exists = entries.stream().anyMatch(entry -> entry.username().equals(username) && entry.password().equals(password));
        if (!exists) {
            entries.add(new XtremeCredentialsJson.Entry(username, password, entries.isEmpty()));
        }
        List<XtremeCredentialsJson.Entry> normalized = XtremeCredentialsJson.normalize(entries, account.getUsername());
        XtremeCredentialsJson.Entry defaultEntry = XtremeCredentialsJson.resolveDefault(normalized);
        if (defaultEntry != null) {
            account.setUsername(defaultEntry.username());
            account.setPassword(defaultEntry.password());
        }
        account.setXtremeCredentialsJson(XtremeCredentialsJson.toJson(normalized));
    }
}
