package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import java.util.ArrayList;
import java.util.List;

import static com.uiptv.util.UiptUtils.*;

/**
 * Handles parsing of M3U playlist links.
 */
public class M3uParser implements AccountParser {
    @Override
    public List<Account> parseAndSave(String text, boolean groupAccountsByMac, boolean convertM3uToXtreme) {
        List<Account> createdAccounts = new ArrayList<>();
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
                AccountService.getInstance().save(account);
                createdAccounts.add(account);
            }
        }
        return createdAccounts;
    }
}
