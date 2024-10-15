package com.uiptv.util;

import com.uiptv.model.Account;
import com.uiptv.service.AccountService;

import static com.uiptv.util.UiptUtils.*;

public class StalkerPortalTextParserService {
    public static void saveBulkAccounts(String text, boolean pauseCaching, boolean collateAccounts, boolean parsePlaylist, boolean findXtremeAccountsPlaylist) {
        if (parsePlaylist) {
            parseM3uPlaylist(text, pauseCaching, collateAccounts, findXtremeAccountsPlaylist);
        } else {
            parseStalkerPortal(text, pauseCaching, collateAccounts);
        }
    }

    private static void parseM3uPlaylist(String text, boolean pauseCaching, boolean collateAccounts, boolean findXtremeAccountsPlaylist) {
        for (String line : text.split("\\R")) {
            for (final String potentialUrl : replaceAllNonPrintableChars(line).split(SPACER)) {
                String m3uPlayLIstUrl;
                String username = null;
                String password = null;
                if (isValidURL(potentialUrl)) {
                    AccountType accountType = AccountType.M3U8_URL;
                    if (findXtremeAccountsPlaylist && isUrlValidXtremeLink(potentialUrl)) {
                        accountType = AccountType.XTREME_API;
                        username = getUserNameFromUrl(potentialUrl);
                        password = getPasswordNameFromUrl(potentialUrl);
                        m3uPlayLIstUrl = potentialUrl.split("get.php?")[0];
                    } else {
                        m3uPlayLIstUrl = potentialUrl;
                    }
                    String name = getNameFromUrl(m3uPlayLIstUrl);
                    Account account = AccountService.getInstance().getByName(name);
                    boolean accountExist = collateAccounts && account != null && accountType.equals(account.getType());
                    if (accountExist) {
                        AccountService.getInstance().save(new Account(name, username, password, m3uPlayLIstUrl, account.getMacAddress(), account.getMacAddressList(), account.getSerialNumber(), account.getDeviceId1(), account.getDeviceId2(), account.getSignature(),
                                accountType, account.getEpg(), m3uPlayLIstUrl, pauseCaching, account.isPinToTop()));
                    } else {
                        String uniqueName = collateAccounts ? name : getUniqueNameFromUrl(m3uPlayLIstUrl);
                        AccountService.getInstance().save(new Account(uniqueName, username, password, m3uPlayLIstUrl, null, null, null, null, null, null,
                                accountType, null, m3uPlayLIstUrl, pauseCaching, false));
                    }
                }
            }

        }
    }


    private static void parseStalkerPortal(String text, boolean pauseCaching, boolean collateAccounts) {
        String currentUrl = null;
        for (String line : text.split("\\R")) {
            for (String potentialUrlOrMac : replaceAllNonPrintableChars(line).split(SPACER)) {
                if (isValidURL(potentialUrlOrMac)) {
                    currentUrl = potentialUrlOrMac;
                } else if (currentUrl != null && isValidMACAddress(potentialUrlOrMac)) {

                    String name = getNameFromUrl(currentUrl);
                    Account account = AccountService.getInstance().getByName(name);
                    boolean accountExist = collateAccounts && account != null;
                    if (accountExist) {
                        AccountService.getInstance().save(new Account(name, account.getUsername(), account.getPassword(), currentUrl, account.getMacAddress(), account.getMacAddressList() + "," + potentialUrlOrMac, account.getSerialNumber(), account.getDeviceId1(), account.getDeviceId2(), account.getSignature(),
                                AccountType.STALKER_PORTAL, account.getEpg(), account.getM3u8Path(), pauseCaching, account.isPinToTop()));
                    } else {
                        String uniqueName = collateAccounts ? name : getUniqueNameFromUrl(currentUrl);
                        AccountService.getInstance().save(new Account(uniqueName, null, null, currentUrl, potentialUrlOrMac, potentialUrlOrMac, null, null, null, null,
                                AccountType.STALKER_PORTAL, null, null, pauseCaching, false));
                    }

                }
            }
        }
    }
}