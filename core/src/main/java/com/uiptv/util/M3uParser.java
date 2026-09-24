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
        List<Account> createdAccounts = new ArrayList<>();
        Map<String, Account> groupedXtremeAccounts = new LinkedHashMap<>();
        for (String line : text.split("\\R")) {
            for (final String potentialUrl : replaceAllNonPrintableChars(line).split(SPACER)) {
                if (!isValidURL(potentialUrl)) continue;

                String m3uPlayLIstUrl = potentialUrl;
                String username = null;
                String password = null;
                AccountType accountType = AccountType.M3U8_URL;

                if (convertM3uToXtreme && isUrlValidXtremeLink(potentialUrl)) {
                    accountType = AccountType.XTREME_API;
                    username = getUserNameFromUrl(potentialUrl);
                    password = getPasswordNameFromUrl(potentialUrl);
                    m3uPlayLIstUrl = potentialUrl.split("get.php?")[0];
                }

                String uniqueName = getUniqueNameFromUrl(m3uPlayLIstUrl);
                Account account = new Account(uniqueName, username, password, m3uPlayLIstUrl, null, null, null, null, null, null,
                        accountType, null, m3uPlayLIstUrl, false);
                if (accountType == AccountType.XTREME_API && isNotBlank(username) && isNotBlank(password)) {
                    if (groupConvertedXtremeAccounts) {
                        Account groupedAccount = groupedXtremeAccounts.computeIfAbsent(m3uPlayLIstUrl,
                                endpoint -> findExistingXtremeAccount(endpoint));
                        if (groupedAccount != null) {
                            mergeCredentials(groupedAccount, username, password);
                            AccountService.getInstance().save(groupedAccount);
                            continue;
                        }
                        groupedXtremeAccounts.put(m3uPlayLIstUrl, account);
                    }
                    mergeCredentials(account, username, password);
                }
                AccountService.getInstance().save(account);
                createdAccounts.add(account);
            }
        }
        return createdAccounts;
    }

    private Account findExistingXtremeAccount(String endpoint) {
        return AccountService.getInstance().getAll().values().stream()
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
