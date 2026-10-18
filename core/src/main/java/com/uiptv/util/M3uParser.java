package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.uiptv.util.UiptUtils.*;
import static com.uiptv.util.StringUtils.isNotBlank;

/**
 * Handles parsing of M3U playlist links.
 */
public class M3uParser implements AccountParser {
    @Override
    public List<Account> parseAndSave(String text, boolean groupConvertedXtremeAccounts, boolean convertM3uToXtreme) {
        Map<String, Account> savedAccounts = new LinkedHashMap<>();
        Map<String, Account> groupedXtremeAccounts = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            for (final String potentialUrl : replaceAllNonPrintableChars(line).split(SPACER)) {
                if (!isValidURL(potentialUrl)) continue;
                processPotentialUrl(potentialUrl, groupConvertedXtremeAccounts, convertM3uToXtreme,
                        savedAccounts, groupedXtremeAccounts);
            }
        }
        return new ArrayList<>(savedAccounts.values());
    }

    private void processPotentialUrl(String potentialUrl, boolean groupConvertedXtremeAccounts,
                                     boolean convertM3uToXtreme, Map<String, Account> savedAccounts,
                                     Map<String, Account> groupedXtremeAccounts) {
        ParsedUrl parsed = parseUrl(potentialUrl, convertM3uToXtreme);
        Account account = new Account(getUniqueNameFromUrl(parsed.playlistUrl()), parsed.username(), parsed.password(),
                parsed.playlistUrl(), null, null, null, null, null, null, parsed.accountType(), null,
                parsed.playlistUrl(), false);
        if (parsed.isConvertedXtreme()) {
            if (groupConvertedXtremeAccounts && mergeIntoExistingGroup(parsed.playlistUrl(), parsed.username(),
                    parsed.password(), savedAccounts, groupedXtremeAccounts)) {
                return;
            }
            if (groupConvertedXtremeAccounts) {
                groupedXtremeAccounts.put(parsed.playlistUrl(), account);
            }
            mergeCredentials(account, parsed.username(), parsed.password());
        }
        AccountService.getInstance().save(account);
        savedAccounts.put(account.getAccountName(), account);
    }

    private boolean mergeIntoExistingGroup(String playlistUrl, String username, String password,
                                           Map<String, Account> savedAccounts,
                                           Map<String, Account> groupedXtremeAccounts) {
        Account groupedAccount = groupedXtremeAccounts.computeIfAbsent(playlistUrl, this::findExistingXtremeAccount);
        if (groupedAccount == null) {
            return false;
        }
        mergeCredentials(groupedAccount, username, password);
        AccountService.getInstance().save(groupedAccount);
        savedAccounts.put(groupedAccount.getAccountName(), groupedAccount);
        return true;
    }

    private ParsedUrl parseUrl(String potentialUrl, boolean convertM3uToXtreme) {
        if (convertM3uToXtreme && isUrlValidXtremeLink(potentialUrl)) {
            return new ParsedUrl(potentialUrl.split("get.php?")[0], getUserNameFromUrl(potentialUrl),
                    getPasswordNameFromUrl(potentialUrl), AccountType.XTREME_API);
        }
        return new ParsedUrl(potentialUrl, null, null, AccountType.M3U8_URL);
    }

    private record ParsedUrl(String playlistUrl, String username, String password, AccountType accountType) {
        private boolean isConvertedXtreme() {
            return accountType == AccountType.XTREME_API && isNotBlank(username) && isNotBlank(password);
        }
    }

    private Account findExistingXtremeAccount(String endpoint) {
        return AccountService.getInstance().getAllById().values().stream()
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
